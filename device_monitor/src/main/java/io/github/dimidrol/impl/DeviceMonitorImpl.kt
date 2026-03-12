package io.github.dimidrol.impl

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.net.TrafficStats
import androidx.annotation.RequiresPermission
import android.view.Window
import androidx.core.content.ContextCompat
import io.github.dimidrol.DeviceMonitor
import io.github.dimidrol.DeviceMonitorConfig
import io.github.dimidrol.BYTES_IN_MB
import io.github.dimidrol.common.DEFAULT_INT
import io.github.dimidrol.common.DEFAULT_LONG
import io.github.dimidrol.common.orDefault
import io.github.dimidrol.models.BatteryHealth
import io.github.dimidrol.models.BatteryPowerSnapshot
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.DeviceWarningEvent
import io.github.dimidrol.models.NetworkTrafficSnapshot
import io.github.dimidrol.models.NetworkType
import io.github.dimidrol.models.PowerSource
import io.github.dimidrol.models.ThermalLevel
import io.github.dimidrol.models.ThermalZoneReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

private const val CPU_DIR = "/sys/devices/system/cpu/"
private const val CPU_FREQ = "cpufreq/scaling_cur_freq"
private const val CPU_STAT = "/proc/stat"
private const val CPU_PREFIX = "cpu"
private const val UPTIME_PATH = "/proc/uptime"
private const val THERMAL_ROOT = "/sys/class/thermal"
private const val TEMP_TENTH_DIVIDER = 10f
private const val DEFAULT_VALUE = -1

private data class CpuTimes(val total: Long, val idle: Long)

internal object DeviceMonitorImpl : DeviceMonitor {

    private lateinit var appContext: Context
    private var currentConfig = DeviceMonitorConfig()
    private var memoryThresholdBytes = currentConfig.memoryThresholdMb * BYTES_IN_MB
    private var storageLowThresholdBytes = currentConfig.storageThresholdMb * BYTES_IN_MB
    private var cpuOverloadThresholdPercent = currentConfig.cpuOverloadThresholdPercent
    private var batteryLowThresholdPercent = currentConfig.batteryLowThresholdPercent
    private var batteryTempThresholdC = currentConfig.batteryTemperatureThresholdC

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshots = MutableSharedFlow<DeviceSnapshot>(
        replay = 1,
        extraBufferCapacity = 8
    )
    override val snapshots: SharedFlow<DeviceSnapshot> = _snapshots.asSharedFlow()

    private val _warningEvents = MutableSharedFlow<DeviceWarningEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )
    override val warningEvents: SharedFlow<DeviceWarningEvent> = _warningEvents.asSharedFlow()

    private var pollJob: Job? = null

    private var lastThermal: ThermalLevel = ThermalLevel.UNKNOWN
    private var powerManager: PowerManager? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private var batteryReceiver: BroadcastReceiver? = null
    private var lastBatteryTempC: Float? = null
    private var lastBatteryLevel: Int? = null
    private var lastIsCharging: Boolean? = null
    private var lastBatteryVoltageMv: Int? = null
    private var lastBatteryHealth: BatteryHealth? = null
    private var lastBatteryPlugType: PowerSource? = null

    private var lastCpuTotal = DEFAULT_LONG
    private var lastCpuIdle = DEFAULT_LONG
    private val lastCpuPerCoreTimes = mutableMapOf<String, CpuTimes>()

    private var lastTxBytes: Long? = null
    private var lastRxBytes: Long? = null
    private var lastTrafficTimestampMs = System.currentTimeMillis()
    private var frameMetricsTracker: FrameMetricsTracker? = null

    private var batteryLowAlerted = false
    private var batteryTempAlerted = false
    private var cpuOverloadAlerted = false

    fun init(context: Context, config: DeviceMonitorConfig = DeviceMonitorConfig()) {
        appContext = context.applicationContext
        applyConfig(config)
    }

    fun configure(config: DeviceMonitorConfig) {
        applyConfig(config)
    }

    fun getConfig(): DeviceMonitorConfig = currentConfig

    private fun applyConfig(config: DeviceMonitorConfig) {
        currentConfig = config
        memoryThresholdBytes = config.memoryThresholdMb * BYTES_IN_MB
        storageLowThresholdBytes = config.storageThresholdMb * BYTES_IN_MB
        cpuOverloadThresholdPercent = config.cpuOverloadThresholdPercent
        batteryLowThresholdPercent = config.batteryLowThresholdPercent
        batteryTempThresholdC = config.batteryTemperatureThresholdC
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun start(samplePeriodMs: Long) {
        check(DeviceMonitorImpl::appContext.isInitialized) {
            "DeviceMonitor.init(context) must be called first"
        }

        if (currentConfig.enableThermal) {
            attachThermalListener()
        } else {
            detachThermalListener()
        }

        if (currentConfig.enableBattery) {
            attachBatteryReceiver()
        } else {
            detachBatteryReceiver()
        }

        val period = samplePeriodMs.takeIf { it > 0 } ?: currentConfig.samplePeriodMs

        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val snapshot = collectSnapshot()
                emitEvents(snapshot)
                _snapshots.tryEmit(snapshot)
                delay(period)
            }
        }
    }

    override fun snapshotNow() = collectSnapshot()

    override suspend fun snapshotNowAwait(timeoutMs: Long): DeviceSnapshot {
        val requestTimeout = timeoutMs.takeIf { it > 0 } ?: currentConfig.samplePeriodMs
        return withContext(Dispatchers.Default) {
            withTimeout(requestTimeout) {
                collectSnapshot()
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        detachThermalListener()
        detachBatteryReceiver()
    }

    override fun registerFrameMetrics(window: Window) {
        frameMetricsTracker?.dispose()
        frameMetricsTracker = FrameMetricsTracker(window)
    }

    override fun unregisterFrameMetrics() {
        frameMetricsTracker?.dispose()
        frameMetricsTracker = null
    }

    private fun attachThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        powerManager = pm

        lastThermal = ThermalLevel.fromInt(pm.currentThermalStatus)

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            val previous = lastThermal
            lastThermal = ThermalLevel.fromInt(status)

            if (previous != lastThermal) {
                _warningEvents.tryEmit(DeviceWarningEvent.ThermalChanged(previous, lastThermal))
            }
        }

        thermalListener = listener
        pm.addThermalStatusListener(listener)
    }

    private fun detachThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val listener = thermalListener
        val pm = powerManager

        if (pm != null && listener != null) {
            pm.removeThermalStatusListener(listener)
        }

        thermalListener = null
        powerManager = null
    }

    private fun attachBatteryReceiver() {
        if (batteryReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lastBatteryTempC =
                    intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, DEFAULT_VALUE)
                        .takeIf { it >= 0 }
                        ?.div(TEMP_TENTH_DIVIDER)

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, DEFAULT_VALUE)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, DEFAULT_VALUE)
                lastBatteryLevel = if (level >= DEFAULT_INT && scale > DEFAULT_INT) {
                    level * 100 / scale
                } else {
                    null
                }

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, DEFAULT_VALUE)
                lastIsCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                lastBatteryVoltageMv =
                    intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, DEFAULT_VALUE).takeIf { it >= 0 }
                lastBatteryHealth = BatteryHealth.fromAndroid(
                    intent.getIntExtra(BatteryManager.EXTRA_HEALTH, DEFAULT_VALUE)
                )
                lastBatteryPlugType = PowerSource.fromAndroid(
                    intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, DEFAULT_VALUE)
                )
            }
        }

        batteryReceiver = receiver
        appContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun detachBatteryReceiver() {
        batteryReceiver?.let {
            runCatching { appContext.unregisterReceiver(it) }
        }
        batteryReceiver = null
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun collectSnapshot(): DeviceSnapshot {
        val timestamp = System.currentTimeMillis()
        val thermal = if (currentConfig.enableThermal) {
            ThermalLevel.fromInt(getThermalStatusFallback())
        } else {
            ThermalLevel.UNKNOWN
        }
        val (availMem, thresholdMem, lowMem) = readMemInfo()
        val (freeStorage, totalStorage) = readStorageInfo()
        val cpuFreqs = readCpuFreqKHzPerCore()
        val cpuUsage = readCpuUsage()
        val cpuUsagePerCore = readCpuUsagePerCore()
        val network = if (currentConfig.enableNetwork) {
            readNetworkType()
        } else {
            NetworkType.UNKNOWN
        }
        val uptime = readUptimeMs()
        val thermalZones = if (currentConfig.enableThermal) {
            readThermalZones() ?: emptyList()
        } else {
            emptyList()
        }
        val frameMetrics = frameMetricsTracker?.snapshot()
        val networkTraffic = if (currentConfig.enableNetwork) {
            readNetworkTraffic()
        } else {
            null
        }
        val batteryPower = if (currentConfig.enableBattery) {
            readBatteryPower()
        } else {
            null
        }

        return DeviceSnapshot(
            tsMs = timestamp,
            thermalStatus = thermal,
            batteryTempC = lastBatteryTempC,
            batteryLevel = lastBatteryLevel,
            isCharging = lastIsCharging,
            cpuUsagePercent = cpuUsage,
            cpuFreqKHz = cpuFreqs,
            memAvailBytes = availMem,
            memThresholdBytes = thresholdMem,
            memLow = lowMem,
            storageFreeBytes = freeStorage,
            networkType = network,
            cpuUsagePerCore = cpuUsagePerCore,
            storageTotalBytes = totalStorage,
            thermalZones = thermalZones,
            frameMetrics = frameMetrics,
            networkTraffic = networkTraffic,
            batteryPower = batteryPower,
            batteryVoltageMv = lastBatteryVoltageMv,
            batteryHealth = lastBatteryHealth,
            batteryPlugType = lastBatteryPlugType,
            uptimeMs = uptime
        )
    }

    private fun emitEvents(snapshot: DeviceSnapshot) {
        if (currentConfig.enableMemory && snapshot.memLow == true && snapshot.memAvailBytes != null) {
            _warningEvents.tryEmit(
                DeviceWarningEvent.MemoryLow(
                    snapshot.memAvailBytes,
                    memoryThresholdBytes
                )
            )
        }

        if (currentConfig.enableStorage &&
            snapshot.storageFreeBytes != null &&
            snapshot.storageFreeBytes < storageLowThresholdBytes
        ) {
            _warningEvents.tryEmit(
                DeviceWarningEvent.StorageLow(
                    snapshot.storageFreeBytes,
                    storageLowThresholdBytes,
                    snapshot.storageTotalBytes
                )
            )
        }

        if (currentConfig.enableBattery) {
            val level = snapshot.batteryLevel
            if (level != null && level <= batteryLowThresholdPercent && snapshot.isCharging != true) {
                if (!batteryLowAlerted) {
                    _warningEvents.tryEmit(
                        DeviceWarningEvent.BatteryLow(
                            level,
                            batteryLowThresholdPercent,
                            snapshot.isCharging
                        )
                    )
                    batteryLowAlerted = true
                }
            } else {
                batteryLowAlerted = false
            }

            val batteryTemp = snapshot.batteryTempC
            if (batteryTemp != null && batteryTemp >= batteryTempThresholdC) {
                if (!batteryTempAlerted) {
                    _warningEvents.tryEmit(
                        DeviceWarningEvent.BatteryTemperatureHigh(
                            batteryTemp,
                            batteryTempThresholdC
                        )
                    )
                    batteryTempAlerted = true
                }
            } else {
                batteryTempAlerted = false
            }
        }

        if (currentConfig.enableCpu) {
            val usage = snapshot.cpuUsagePercent
            if (usage != null && usage >= cpuOverloadThresholdPercent) {
                if (!cpuOverloadAlerted) {
                    _warningEvents.tryEmit(
                        DeviceWarningEvent.CpuOverload(
                            usage,
                            cpuOverloadThresholdPercent,
                            snapshot.cpuUsagePerCore?.size ?: 0
                        )
                    )
                    cpuOverloadAlerted = true
                }
            } else {
                cpuOverloadAlerted = false
            }
        }
    }

    private fun getThermalStatusFallback(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val pm = powerManager
            ?: appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return null

        return runCatching { pm.currentThermalStatus }.getOrNull()
    }

    private fun readMemInfo(): Triple<Long?, Long?, Boolean?> {
        return try {
            val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            Triple(info.availMem, info.threshold, info.lowMemory)
        } catch (_: Throwable) {
            Triple(null, null, null)
        }
    }

    private fun readStorageInfo(): Pair<Long?, Long?> {
        return try {
            val path = Environment.getDataDirectory()
            val stats = StatFs(path.absolutePath)
            Pair(stats.availableBytes, stats.blockCountLong * stats.blockSizeLong)
        } catch (_: Throwable) {
            Pair(null, null)
        }
    }

    private fun readCpuFreqKHzPerCore(): List<Int>? {
        return try {
            val cpuDir = File(CPU_DIR)
            val cores = cpuDir.listFiles { f ->
                f.isDirectory && f.name.startsWith(CPU_PREFIX) &&
                        f.name.drop(CPU_PREFIX.length).all { it.isDigit() }
            }?.sortedBy { it.name.drop(CPU_PREFIX.length).toIntOrNull() ?: 0 } ?: return null

            cores.map { core ->
                val freqFile = File(core, CPU_FREQ)
                if (freqFile.exists()) {
                    freqFile.readText().trim().toIntOrNull() ?: DEFAULT_VALUE
                } else {
                    DEFAULT_VALUE
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readCpuUsage(): Float? {
        return try {
            val line = File(CPU_STAT).useLines { it.firstOrNull() } ?: return null
            val values = line.split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }
            if (values.size < 8) return null

            val total = values.subList(0, 7).sum()
            val idle = values[3]

            if (lastCpuTotal == DEFAULT_LONG) {
                lastCpuTotal = total
                lastCpuIdle = idle
                return null
            }

            val diffTotal = total - lastCpuTotal
            val diffIdle = idle - lastCpuIdle

            lastCpuTotal = total
            lastCpuIdle = idle

            if (diffTotal <= 0) return null

            (100f * (diffTotal - diffIdle) / diffTotal)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readCpuUsagePerCore(): List<Float>? {
        return try {
            val stats = File(CPU_STAT).readLines()
            val usage = mutableListOf<Float>()

            stats.forEach { line ->
                if (!line.startsWith(CPU_PREFIX)) return@forEach
                val parts = line.split("\\s+".toRegex())
                val label = parts.getOrNull(0) ?: return@forEach
                if (label == CPU_PREFIX) return@forEach
                if (!label.firstOrNull()?.isLetter().orDefault()) return@forEach

                val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
                if (values.size < 7) return@forEach

                val total = values.subList(0, 7).sum()
                val idle = values[3]
                val previous = lastCpuPerCoreTimes[label]
                lastCpuPerCoreTimes[label] = CpuTimes(total, idle)

                if (previous != null) {
                    val diffTotal = total - previous.total
                    val diffIdle = idle - previous.idle
                    if (diffTotal > 0) {
                        usage += 100f * (diffTotal - diffIdle) / diffTotal
                    }
                }
            }

            usage.ifEmpty { null }
        } catch (_: Throwable) {
            null
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun readNetworkType(): NetworkType {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_NETWORK_STATE
            ) == PackageManager.PERMISSION_DENIED
        ) return NetworkType.UNKNOWN

        return try {
            val connectivity =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivity.activeNetwork ?: return NetworkType.NONE
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return NetworkType.NONE

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                else -> NetworkType.OTHER
            }
        } catch (_: Throwable) {
            NetworkType.UNKNOWN
        }
    }

    private fun readThermalZones(): List<ThermalZoneReading>? {
        return try {
            val root = File(THERMAL_ROOT)
            val zones = root.listFiles { file ->
                file.isDirectory && file.name.startsWith("thermal_zone")
            } ?: return null

            zones.mapNotNull { zone ->
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")
                val type = typeFile.takeIf { it.exists() }?.readText()?.trim()
                val rawTemp = tempFile.takeIf { it.exists() }?.readText()?.trim()?.toFloatOrNull()
                val temp = rawTemp?.let { normalizeThermalTemp(it) }
                ThermalZoneReading(
                    name = zone.name,
                    type = type,
                    temperatureC = temp
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizeThermalTemp(value: Float): Float {
        return if (value > 200f) value / 1000f else value
    }

    private fun readNetworkTraffic(): NetworkTrafficSnapshot {
        val now = System.currentTimeMillis()
        val txTotal = safeTrafficValue(TrafficStats.getTotalTxBytes())
        val rxTotal = safeTrafficValue(TrafficStats.getTotalRxBytes())
        val deltaTx = if (txTotal != null && lastTxBytes != null) {
            (txTotal - lastTxBytes.orDefault()).coerceAtLeast(0L)
        } else {
            null
        }

        val deltaRx = if (rxTotal != null && lastRxBytes != null) {
            (rxTotal - lastRxBytes.orDefault()).coerceAtLeast(0L)
        } else {
            null
        }
        val period = (now - lastTrafficTimestampMs).coerceAtLeast(1L)

        lastTxBytes = txTotal
        lastRxBytes = rxTotal
        lastTrafficTimestampMs = now

        return NetworkTrafficSnapshot(
            txBytes = txTotal,
            rxBytes = rxTotal,
            txBytesDelta = deltaTx,
            rxBytesDelta = deltaRx,
            periodMs = period
        )
    }

    private fun safeTrafficValue(value: Long): Long? = value.takeIf { it >= 0 }

    private fun readBatteryPower(): BatteryPowerSnapshot? {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null
        val currentNow = safeLongProperty(batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        val chargeCounter = safeLongProperty(batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
        val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it >= 0 }

        return BatteryPowerSnapshot(
            currentMicroAmps = currentNow,
            chargeCounter = chargeCounter,
            capacityPercent = capacity,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun safeLongProperty(value: Long): Long? = value.takeIf { it != Long.MIN_VALUE }

    private fun readUptimeMs(): Long? {
        return try {
            val uptimeLine = File(UPTIME_PATH).readLines().firstOrNull() ?: return null
            val seconds = uptimeLine.split("\\s+".toRegex()).firstOrNull()?.toDoubleOrNull() ?: return null
            (seconds * 1000).toLong()
        } catch (_: Throwable) {
            null
        }
    }

    fun setMemoryLowThreshold(threshold: Long) {
        configure(currentConfig.copy(memoryThresholdMb = threshold))
    }

    fun setStorageLowThreshold(threshold: Long) {
        configure(currentConfig.copy(storageThresholdMb = threshold))
    }
}
