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
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.view.Window
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import io.github.dimidrol.BYTES_IN_MB
import io.github.dimidrol.DeviceMonitor
import io.github.dimidrol.DeviceMonitorConfig
import io.github.dimidrol.WorkloadReport
import io.github.dimidrol.WorkloadSession
import io.github.dimidrol.WorkloadType
import io.github.dimidrol.common.DEFAULT_INT
import io.github.dimidrol.common.DEFAULT_LONG
import io.github.dimidrol.common.orDefault
import io.github.dimidrol.models.BatteryDrainSnapshot
import io.github.dimidrol.models.BatteryHealth
import io.github.dimidrol.models.BatteryPowerSnapshot
import io.github.dimidrol.models.DeviceHealth
import io.github.dimidrol.models.DeviceRecommendation
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.DeviceWarningEvent
import io.github.dimidrol.models.NetworkTrafficSnapshot
import io.github.dimidrol.models.NetworkType
import io.github.dimidrol.models.PowerSource
import io.github.dimidrol.models.RecommendationReason
import io.github.dimidrol.models.RecommendationSeverity
import io.github.dimidrol.models.ThermalHeadroomSnapshot
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
import kotlin.math.abs

private const val CPU_DIR = "/sys/devices/system/cpu/"
private const val CPU_FREQ = "cpufreq/scaling_cur_freq"
private const val CPU_STAT = "/proc/stat"
private const val CPU_PREFIX = "cpu"
private const val PROC_SELF_STAT = "/proc/self/stat"
private const val UPTIME_PATH = "/proc/uptime"
private val THERMAL_ROOTS = listOf("/sys/class/thermal", "/sys/devices/virtual/thermal")
private const val HWMON_ROOT = "/sys/class/hwmon"
private const val TEMP_TENTH_DIVIDER = 10f
private const val CPU_JIFFY_FALLBACK = 100L
private const val PROCESS_STAT_UTIME_INDEX = 13
private const val PROCESS_STAT_STIME_INDEX = 14
private const val PROCESS_STAT_CUTIME_INDEX = 15
private const val PROCESS_STAT_CSTIME_INDEX = 16
private const val PROCESS_STAT_SPLIT_LIMIT = PROCESS_STAT_CSTIME_INDEX + 2
private const val THERMAL_HEADROOM_LOW_THRESHOLD = 0.2f
private const val POWER_WATTS_DIVIDER = 1_000_000_000f
private const val MILLIS_IN_HOUR = 3_600_000L
private const val MAX_THERMAL_HEADROOM_FORECAST_SECONDS = 60
private const val DEFAULT_VALUE = -1

private data class CpuTimes(val total: Long, val idle: Long)

private data class DrainSample(
    val chargeCounterUaH: Long?,
    val capacityPercent: Int?,
    val timestampMs: Long
)

private data class BatteryReadings(
    val currentMicroAmps: Long?,
    val chargeCounterUaH: Long?,
    val capacityPercent: Int?,
    val timestampMs: Long
)

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
        replay = DEFAULT_INT,
        extraBufferCapacity = 16
    )
    override val warningEvents: SharedFlow<DeviceWarningEvent> = _warningEvents.asSharedFlow()

    private val _recommendations = MutableSharedFlow<DeviceRecommendation>(
        replay = DEFAULT_INT,
        extraBufferCapacity = 16
    )
    override val recommendations: SharedFlow<DeviceRecommendation> = _recommendations.asSharedFlow()

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
    private val cpuCoreCount = runCatching {
        Os.sysconf(OsConstants._SC_NPROCESSORS_CONF).toInt()
    }.getOrDefault(Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
    private val cpuTicksPerSecond = runCatching {
        Os.sysconf(OsConstants._SC_CLK_TCK)
    }.getOrDefault(CPU_JIFFY_FALLBACK).coerceAtLeast(1L)
    private var lastProcessCpuTimeSec = 0.0
    private var lastProcessElapsedSec = 0.0

    private var lastTxBytes: Long? = null
    private var lastRxBytes: Long? = null
    private var lastTrafficTimestampMs = System.currentTimeMillis()
    private var frameMetricsTracker: FrameMetricsTracker? = null

    private var previousRecommendationHealth: DeviceHealth? = null
    private var lastDrainSample: DrainSample? = null

    private val warningEventThrottle = WarningEventThrottle()
    private val recommendationEventThrottle = RecommendationEventThrottle(currentConfig.recommendationCooldownMs)

    private val workloadSessionsLock = Any()
    private val activeWorkloadSessions = linkedSetOf<MonitorWorkloadSession>()

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
        recommendationEventThrottle.updateCooldown(config.recommendationCooldownMs)
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

        val period = samplePeriodMs.takeIf { it > DEFAULT_LONG } ?: currentConfig.samplePeriodMs

        pollJob?.cancel()
        warningEventThrottle.reset()
        recommendationEventThrottle.reset()
        previousRecommendationHealth = null

        pollJob = scope.launch {
            while (isActive) {
                val snapshot = collectSnapshot()
                recordSnapshotForSessions(snapshot)
                emitEvents(snapshot)
                emitRecommendation(snapshot)
                _snapshots.tryEmit(snapshot)
                delay(period)
            }
        }
    }

    override fun snapshotNow(): DeviceSnapshot {
        val snapshot = collectSnapshot()
        recordSnapshotForSessions(snapshot)
        emitRecommendation(snapshot)
        return snapshot
    }

    override suspend fun snapshotNowAwait(timeoutMs: Long): DeviceSnapshot {
        val requestTimeout = timeoutMs.takeIf { it > DEFAULT_LONG } ?: currentConfig.samplePeriodMs
        return withContext(Dispatchers.Default) {
            withTimeout(requestTimeout) {
                val snapshot = collectSnapshot()
                recordSnapshotForSessions(snapshot)
                emitRecommendation(snapshot)
                snapshot
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        warningEventThrottle.reset()
        recommendationEventThrottle.reset()
        previousRecommendationHealth = null
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

    override fun createWorkloadSession(name: String, type: WorkloadType): WorkloadSession {
        val sessionName = name.takeIf { it.isNotBlank() } ?: "workload"
        return MonitorWorkloadSession(sessionName, type)
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
                emitWarningEvent(DeviceWarningEvent.ThermalChanged(previous, lastThermal))
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
                        .takeIf { it >= DEFAULT_INT }
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
                    intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, DEFAULT_VALUE).takeIf { it >= DEFAULT_INT }
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
            readThermalLevel()
        } else {
            ThermalLevel.UNKNOWN
        }
        val thermalHeadroom = readThermalHeadroom(thermal)
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
        val batteryReadings = if (currentConfig.enableBattery) {
            readBatteryReadings()
        } else {
            null
        }
        val batteryPower = batteryReadings?.toBatteryPowerSnapshot()
        val batteryDrain = readBatteryDrainSnapshot(batteryReadings)

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
            uptimeMs = uptime,
            thermalHeadroom = thermalHeadroom,
            batteryDrain = batteryDrain
        )
    }

    private fun emitEvents(snapshot: DeviceSnapshot) {
        val memoryRisk = currentConfig.enableMemory && snapshot.memLow == true && snapshot.memAvailBytes != null
        if (warningEventThrottle.shouldEmitMemoryLow(isRiskActive = memoryRisk)) {
            emitWarningEvent(
                DeviceWarningEvent.MemoryLow(
                    requireNotNull(snapshot.memAvailBytes),
                    memoryThresholdBytes
                )
            )
        } else {
            warningEventThrottle.shouldEmitMemoryLow(isRiskActive = false)
        }

        val storageRisk = currentConfig.enableStorage &&
            snapshot.storageFreeBytes != null &&
            snapshot.storageFreeBytes < storageLowThresholdBytes
        if (warningEventThrottle.shouldEmitStorageLow(isRiskActive = storageRisk)) {
            emitWarningEvent(
                DeviceWarningEvent.StorageLow(
                    requireNotNull(snapshot.storageFreeBytes),
                    storageLowThresholdBytes,
                    snapshot.storageTotalBytes
                )
            )
        } else {
            warningEventThrottle.shouldEmitStorageLow(isRiskActive = false)
        }

        if (currentConfig.enableBattery) {
            val level = snapshot.batteryLevel
            val batteryLowRisk = level != null &&
                level <= batteryLowThresholdPercent &&
                snapshot.isCharging != true
            if (warningEventThrottle.shouldEmitBatteryLow(isRiskActive = batteryLowRisk)) {
                emitWarningEvent(
                    DeviceWarningEvent.BatteryLow(
                        requireNotNull(level),
                        batteryLowThresholdPercent,
                        snapshot.isCharging
                    )
                )
            } else {
                warningEventThrottle.shouldEmitBatteryLow(isRiskActive = false)
            }

            val batteryTemp = snapshot.batteryTempC
            val batteryTempRisk = batteryTemp != null && batteryTemp >= batteryTempThresholdC
            if (warningEventThrottle.shouldEmitBatteryTempHigh(isRiskActive = batteryTempRisk)) {
                emitWarningEvent(
                    DeviceWarningEvent.BatteryTemperatureHigh(
                        requireNotNull(batteryTemp),
                        batteryTempThresholdC
                    )
                )
            } else {
                warningEventThrottle.shouldEmitBatteryTempHigh(isRiskActive = false)
            }

            val drainPercentPerHour = snapshot.batteryDrain?.drainPercentPerHour
            val batteryDrainRisk = currentConfig.enableBatteryDrain &&
                drainPercentPerHour != null &&
                drainPercentPerHour >= currentConfig.batteryDrainHighThresholdPercentPerHour &&
                snapshot.isCharging != true
            if (warningEventThrottle.shouldEmitBatteryDrainHigh(isRiskActive = batteryDrainRisk)) {
                emitWarningEvent(
                    DeviceWarningEvent.BatteryDrainHigh(
                        drainPercentPerHour = requireNotNull(drainPercentPerHour),
                        thresholdPercentPerHour = currentConfig.batteryDrainHighThresholdPercentPerHour
                    )
                )
            } else {
                warningEventThrottle.shouldEmitBatteryDrainHigh(isRiskActive = false)
            }
        } else {
            warningEventThrottle.shouldEmitBatteryLow(isRiskActive = false)
            warningEventThrottle.shouldEmitBatteryTempHigh(isRiskActive = false)
            warningEventThrottle.shouldEmitBatteryDrainHigh(isRiskActive = false)
        }

        if (currentConfig.enableCpu) {
            val usage = snapshot.cpuUsagePercent
            val cpuOverloadRisk = usage != null && usage >= cpuOverloadThresholdPercent
            if (warningEventThrottle.shouldEmitCpuOverload(isRiskActive = cpuOverloadRisk)) {
                emitWarningEvent(
                    DeviceWarningEvent.CpuOverload(
                        requireNotNull(usage),
                        cpuOverloadThresholdPercent,
                        snapshot.cpuUsagePerCore?.size.orDefault()
                    )
                )
            } else {
                warningEventThrottle.shouldEmitCpuOverload(isRiskActive = false)
            }
        } else {
            warningEventThrottle.shouldEmitCpuOverload(isRiskActive = false)
        }

        val thermalHeadroom = snapshot.thermalHeadroom
        val thermalHeadroomRisk = currentConfig.enableThermalHeadroom &&
            thermalHeadroom != null &&
            listOfNotNull(thermalHeadroom.currentHeadroom, thermalHeadroom.forecastHeadroom)
                .any { it <= THERMAL_HEADROOM_LOW_THRESHOLD }
        if (warningEventThrottle.shouldEmitThermalHeadroomLow(isRiskActive = thermalHeadroomRisk)) {
            emitWarningEvent(
                DeviceWarningEvent.ThermalHeadroomLow(
                    currentHeadroom = thermalHeadroom?.currentHeadroom,
                    forecastHeadroom = thermalHeadroom?.forecastHeadroom,
                    threshold = THERMAL_HEADROOM_LOW_THRESHOLD,
                    status = thermalHeadroom?.status ?: snapshot.thermalStatus
                )
            )
        } else {
            warningEventThrottle.shouldEmitThermalHeadroomLow(isRiskActive = false)
        }
    }

    private fun emitRecommendation(snapshot: DeviceSnapshot) {
        val currentHealth = snapshot.health()

        if (!currentConfig.enableRecommendations) {
            previousRecommendationHealth = currentHealth
            recommendationEventThrottle.shouldEmit(key = null, nowMs = snapshot.tsMs)
            return
        }

        val recommendation = recommendationForHealth(
            currentHealth = currentHealth,
            previousHealth = previousRecommendationHealth
        )

        val key = recommendation?.throttleKey()
        if (recommendation != null && recommendationEventThrottle.shouldEmit(key, snapshot.tsMs)) {
            _recommendations.tryEmit(recommendation)
            recordRecommendationForSessions(recommendation)
        } else if (recommendation == null) {
            recommendationEventThrottle.shouldEmit(key = null, nowMs = snapshot.tsMs)
        }

        previousRecommendationHealth = currentHealth
    }

    private fun emitWarningEvent(event: DeviceWarningEvent) {
        _warningEvents.tryEmit(event)
        recordWarningForSessions()
    }

    private fun readThermalLevel(): ThermalLevel {
        val fromSystem = readSystemThermalStatus()?.let(ThermalLevel::fromInt)
        return fromSystem ?: inferThermalLevelFromBatteryTemp(lastBatteryTempC)
    }

    private fun readSystemThermalStatus(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val pm = powerManager
            ?: appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return null

        return runCatching { pm.currentThermalStatus }.getOrNull()
    }

    private fun readThermalHeadroom(status: ThermalLevel): ThermalHeadroomSnapshot? {
        if (!currentConfig.enableThermalHeadroom) return null

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ThermalHeadroomSnapshot(
                currentHeadroom = null,
                forecastHeadroom = null,
                forecastSeconds = null,
                status = status
            )
        }

        val pm = powerManager
            ?: appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return ThermalHeadroomSnapshot(
                currentHeadroom = null,
                forecastHeadroom = null,
                forecastSeconds = null,
                status = status
            )

        val forecastSeconds = currentConfig.thermalHeadroomForecastSeconds
            .coerceIn(0, MAX_THERMAL_HEADROOM_FORECAST_SECONDS)

        val currentHeadroom = safeThermalHeadroomValue(runCatching { pm.getThermalHeadroom(0) }.getOrNull())
        val forecastHeadroom = if (forecastSeconds > 0) {
            safeThermalHeadroomValue(runCatching { pm.getThermalHeadroom(forecastSeconds) }.getOrNull())
        } else {
            null
        }

        return ThermalHeadroomSnapshot(
            currentHeadroom = currentHeadroom,
            forecastHeadroom = forecastHeadroom,
            forecastSeconds = forecastSeconds.takeIf { it > 0 },
            status = status
        )
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
            }?.sortedBy { it.name.drop(CPU_PREFIX.length).toIntOrNull().orDefault() } ?: return null

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
        return readCpuUsageFromProcStat() ?: readProcessCpuUsageFallback()
    }

    private fun readCpuUsageFromProcStat(): Float? {
        return try {
            val line = File(CPU_STAT).useLines { it.firstOrNull() } ?: return null
            val values = line.split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }
            if (values.size < 5) return null

            val total = values.sum()
            val idle = values[3] + values.getOrElse(4) { 0L }

            if (lastCpuTotal == DEFAULT_LONG) {
                lastCpuTotal = total
                lastCpuIdle = idle
                return null
            }

            val diffTotal = total - lastCpuTotal
            val diffIdle = idle - lastCpuIdle

            lastCpuTotal = total
            lastCpuIdle = idle

            if (diffTotal <= DEFAULT_INT) return null

            (100f * (diffTotal - diffIdle) / diffTotal)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readProcessCpuUsageFallback(): Float? {
        return try {
            val line = File(PROC_SELF_STAT).useLines { it.firstOrNull() } ?: return null
            val statData = line.split("\\s+".toRegex(), limit = PROCESS_STAT_SPLIT_LIMIT)
            if (statData.size <= PROCESS_STAT_CSTIME_INDEX) return null

            val processCpuTicks = statData[PROCESS_STAT_UTIME_INDEX].toDouble() +
                statData[PROCESS_STAT_STIME_INDEX].toDouble() +
                statData[PROCESS_STAT_CUTIME_INDEX].toDouble() +
                statData[PROCESS_STAT_CSTIME_INDEX].toDouble()

            val currentCpuTimeSec = processCpuTicks / cpuTicksPerSecond
            val currentElapsedSec = SystemClock.elapsedRealtime() / 1000.0

            if (lastProcessCpuTimeSec <= 0.0 || lastProcessElapsedSec <= 0.0) {
                lastProcessCpuTimeSec = currentCpuTimeSec
                lastProcessElapsedSec = currentElapsedSec
                return null
            }

            val cpuDeltaSec = currentCpuTimeSec - lastProcessCpuTimeSec
            val elapsedDeltaSec = currentElapsedSec - lastProcessElapsedSec

            lastProcessCpuTimeSec = currentCpuTimeSec
            lastProcessElapsedSec = currentElapsedSec

            if (cpuDeltaSec <= 0.0 || elapsedDeltaSec <= 0.0) return null

            val usageRatio = (cpuDeltaSec / elapsedDeltaSec) / cpuCoreCount
            (usageRatio * 100.0).coerceIn(0.0, 100.0).toFloat()
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
                val label = parts.getOrNull(DEFAULT_INT) ?: return@forEach
                if (label == CPU_PREFIX) return@forEach
                if (!label.firstOrNull()?.isLetter().orDefault()) return@forEach

                val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
                if (values.size < 5) return@forEach

                val total = values.sum()
                val idle = values[3] + values.getOrElse(4) { 0L }
                val previous = lastCpuPerCoreTimes[label]
                lastCpuPerCoreTimes[label] = CpuTimes(total, idle)

                if (previous != null) {
                    val diffTotal = total - previous.total
                    val diffIdle = idle - previous.idle
                    if (diffTotal > DEFAULT_INT) {
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
        val collected = linkedMapOf<String, ThermalZoneReading>()

        readClassicThermalZones().forEach { zone ->
            collected[zone.name] = zone
        }
        readHwmonThermalZones().forEach { zone ->
            collected.putIfAbsent(zone.name, zone)
        }

        if (collected.isEmpty()) {
            lastBatteryTempC?.let { batteryTemp ->
                collected["battery"] = ThermalZoneReading(
                    name = "battery",
                    type = "battery",
                    temperatureC = batteryTemp
                )
            }
        }

        return collected.values.toList().ifEmpty { null }
    }

    private fun readClassicThermalZones(): List<ThermalZoneReading> {
        return runCatching {
            THERMAL_ROOTS.flatMap { rootPath ->
                val root = File(rootPath)
                val zones = root.listFiles { file ->
                    file.isDirectory && file.name.startsWith("thermal_zone")
                } ?: return@flatMap emptyList()

                zones.mapNotNull { zone ->
                    val typeFile = File(zone, "type")
                    val tempFile = File(zone, "temp")
                    val type = typeFile.takeIf { it.exists() }?.readText()?.trim()
                    val rawTemp = tempFile.takeIf { it.exists() }?.readText()?.trim()?.toFloatOrNull()
                    val temp = rawTemp?.let { normalizeThermalTemp(it) }

                    if (type == null && temp == null) {
                        null
                    } else {
                        ThermalZoneReading(
                            name = zone.name,
                            type = type,
                            temperatureC = temp
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readHwmonThermalZones(): List<ThermalZoneReading> {
        return runCatching {
            val hwmonRoot = File(HWMON_ROOT)
            val hwmonDirs = hwmonRoot.listFiles { file ->
                file.isDirectory && file.name.startsWith("hwmon")
            } ?: return@runCatching emptyList()

            hwmonDirs.flatMap { dir ->
                val sensorName = File(dir, "name").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                val inputFiles = dir.listFiles { file ->
                    file.isFile && file.name.startsWith("temp") && file.name.endsWith("_input")
                } ?: return@flatMap emptyList()

                inputFiles.mapNotNull { input ->
                    val key = input.name.removeSuffix("_input")
                    val rawTemp = input.readText().trim().toFloatOrNull() ?: return@mapNotNull null
                    val label = File(dir, "${key}_label").takeIf { it.exists() }?.readText()?.trim()
                    val type = listOf(sensorName.takeIf { it.isNotBlank() }, label)
                        .filterNotNull()
                        .joinToString(":")
                        .ifBlank { null }

                    ThermalZoneReading(
                        name = "${dir.name}/$key",
                        type = type,
                        temperatureC = normalizeThermalTemp(rawTemp)
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun normalizeThermalTemp(value: Float): Float {
        return if (value > 200f) value / 1000f else value
    }

    private fun readNetworkTraffic(): NetworkTrafficSnapshot {
        val now = System.currentTimeMillis()
        val txTotal = safeTrafficValue(TrafficStats.getTotalTxBytes())
        val rxTotal = safeTrafficValue(TrafficStats.getTotalRxBytes())
        val deltaTx = if (txTotal != null && lastTxBytes != null) {
            (txTotal - lastTxBytes.orDefault()).coerceAtLeast(DEFAULT_LONG)
        } else {
            null
        }

        val deltaRx = if (rxTotal != null && lastRxBytes != null) {
            (rxTotal - lastRxBytes.orDefault()).coerceAtLeast(DEFAULT_LONG)
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

    private fun safeTrafficValue(value: Long): Long? = value.takeIf { it >= DEFAULT_INT }

    private fun readBatteryReadings(): BatteryReadings? {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null
        return BatteryReadings(
            currentMicroAmps = safeLongProperty(
                batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ),
            chargeCounterUaH = safeLongProperty(
                batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ),
            capacityPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it >= DEFAULT_INT },
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun BatteryReadings.toBatteryPowerSnapshot(): BatteryPowerSnapshot {
        return BatteryPowerSnapshot(
            currentMicroAmps = currentMicroAmps,
            chargeCounter = chargeCounterUaH,
            capacityPercent = capacityPercent,
            timestampMs = timestampMs
        )
    }

    private fun readBatteryDrainSnapshot(readings: BatteryReadings?): BatteryDrainSnapshot? {
        if (!currentConfig.enableBattery || !currentConfig.enableBatteryDrain) return null
        if (readings == null) {
            return BatteryDrainSnapshot(
                currentMicroAmps = null,
                voltageMv = lastBatteryVoltageMv,
                estimatedPowerWatts = null,
                drainPercentPerHour = null,
                estimatedTimeToEmptyMs = null
            )
        }

        val capacityPercent = readings.capacityPercent ?: lastBatteryLevel
        val drainPercentPerHour = calculateDrainPercentPerHour(
            previous = lastDrainSample,
            currentChargeCounterUaH = readings.chargeCounterUaH,
            currentCapacityPercent = capacityPercent,
            timestampMs = readings.timestampMs
        )

        val estimatedTimeToEmptyMs = estimateTimeToEmptyMs(
            drainPercentPerHour = drainPercentPerHour,
            currentBatteryPercent = capacityPercent,
            chargeCounterUaH = readings.chargeCounterUaH,
            currentMicroAmps = readings.currentMicroAmps
        )

        val estimatedPowerWatts = if (readings.currentMicroAmps != null && lastBatteryVoltageMv != null) {
            (abs(readings.currentMicroAmps.toDouble()) * lastBatteryVoltageMv.orDefault() / POWER_WATTS_DIVIDER).toFloat()
        } else {
            null
        }

        lastDrainSample = DrainSample(
            chargeCounterUaH = readings.chargeCounterUaH,
            capacityPercent = capacityPercent,
            timestampMs = readings.timestampMs
        )

        return BatteryDrainSnapshot(
            currentMicroAmps = readings.currentMicroAmps,
            voltageMv = lastBatteryVoltageMv,
            estimatedPowerWatts = estimatedPowerWatts,
            drainPercentPerHour = drainPercentPerHour,
            estimatedTimeToEmptyMs = estimatedTimeToEmptyMs
        )
    }

    private fun calculateDrainPercentPerHour(
        previous: DrainSample?,
        currentChargeCounterUaH: Long?,
        currentCapacityPercent: Int?,
        timestampMs: Long
    ): Float? {
        val previousSample = previous ?: return null
        return calculateBatteryDrainPercentPerHour(
            previousChargeCounterUaH = previousSample.chargeCounterUaH,
            previousCapacityPercent = previousSample.capacityPercent,
            previousTimestampMs = previousSample.timestampMs,
            currentChargeCounterUaH = currentChargeCounterUaH,
            capacityPercent = currentCapacityPercent,
            currentTimestampMs = timestampMs
        )
    }

    private fun estimateTimeToEmptyMs(
        drainPercentPerHour: Float?,
        currentBatteryPercent: Int?,
        chargeCounterUaH: Long?,
        currentMicroAmps: Long?
    ): Long? {
        if (drainPercentPerHour != null && drainPercentPerHour > 0f && currentBatteryPercent != null && currentBatteryPercent > 0) {
            return ((currentBatteryPercent / drainPercentPerHour) * MILLIS_IN_HOUR)
                .toLong()
                .coerceAtLeast(0L)
        }

        val current = currentMicroAmps ?: return null
        val chargeCounter = chargeCounterUaH ?: return null
        if (current >= 0) return null

        val drainMicroAmps = abs(current.toDouble())
        if (drainMicroAmps <= 0.0) return null

        return ((chargeCounter / drainMicroAmps) * MILLIS_IN_HOUR)
            .toLong()
            .coerceAtLeast(0L)
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

    private fun registerWorkloadSession(session: MonitorWorkloadSession) {
        synchronized(workloadSessionsLock) {
            activeWorkloadSessions.add(session)
        }
    }

    private fun unregisterWorkloadSession(session: MonitorWorkloadSession) {
        synchronized(workloadSessionsLock) {
            activeWorkloadSessions.remove(session)
        }
    }

    private fun recordSnapshotForSessions(snapshot: DeviceSnapshot) {
        synchronized(workloadSessionsLock) {
            activeWorkloadSessions.forEach { it.recordSnapshot(snapshot) }
        }
    }

    private fun recordWarningForSessions() {
        synchronized(workloadSessionsLock) {
            activeWorkloadSessions.forEach { it.recordWarning() }
        }
    }

    private fun recordRecommendationForSessions(recommendation: DeviceRecommendation) {
        synchronized(workloadSessionsLock) {
            activeWorkloadSessions.forEach { it.recordRecommendation(recommendation) }
        }
    }

    internal class MonitorWorkloadSession(
        override val name: String,
        override val type: WorkloadType
    ) : WorkloadSession {

        private val lock = Any()
        private var running = false
        private var startedAtMs = 0L
        private var startSnapshot: DeviceSnapshot? = null
        private var maxRiskScore = 0
        private var warningCount = 0
        private val recommendations = mutableListOf<DeviceRecommendation>()

        override fun start() {
            val initialSnapshot = runCatching { DeviceMonitorImpl.snapshotNow() }.getOrNull()
            val startedAt = System.currentTimeMillis()

            synchronized(lock) {
                if (running) return
                running = true
                startedAtMs = startedAt
                startSnapshot = initialSnapshot
                maxRiskScore = initialSnapshot?.riskScore() ?: 0
                warningCount = 0
                recommendations.clear()
            }

            DeviceMonitorImpl.registerWorkloadSession(this)
        }

        override fun stop(): WorkloadReport {
            val finalSnapshot = runCatching { DeviceMonitorImpl.snapshotNow() }.getOrNull()
            val stoppedAtMs = System.currentTimeMillis()

            DeviceMonitorImpl.unregisterWorkloadSession(this)

            return synchronized(lock) {
                val effectiveStart = startedAtMs.takeIf { it > 0L } ?: stoppedAtMs
                val durationMs = (stoppedAtMs - effectiveStart).coerceAtLeast(0L)

                val report = WorkloadReport(
                    name = name,
                    type = type,
                    durationMs = durationMs,
                    startSnapshot = startSnapshot,
                    endSnapshot = finalSnapshot,
                    maxRiskScore = maxRiskScore,
                    warningCount = warningCount,
                    recommendations = recommendations.toList()
                )

                running = false
                startedAtMs = 0L
                startSnapshot = null
                maxRiskScore = 0
                warningCount = 0
                recommendations.clear()

                report
            }
        }

        fun recordSnapshot(snapshot: DeviceSnapshot) {
            synchronized(lock) {
                if (!running) return
                maxRiskScore = maxOf(maxRiskScore, snapshot.riskScore())
            }
        }

        fun recordWarning() {
            synchronized(lock) {
                if (!running) return
                warningCount += 1
            }
        }

        fun recordRecommendation(recommendation: DeviceRecommendation) {
            synchronized(lock) {
                if (!running) return
                recommendations += recommendation
            }
        }
    }
}

internal fun recommendationForHealth(
    currentHealth: DeviceHealth,
    previousHealth: DeviceHealth?
): DeviceRecommendation? {
    return when (currentHealth) {
        DeviceHealth.WARM -> DeviceRecommendation.ReduceWorkload(
            reason = RecommendationReason.HEALTH_WARM,
            severity = RecommendationSeverity.MEDIUM,
            suggestedScale = 0.8f
        )

        DeviceHealth.DEGRADED -> DeviceRecommendation.ReduceWorkload(
            reason = RecommendationReason.HEALTH_DEGRADED,
            severity = RecommendationSeverity.HIGH,
            suggestedScale = 0.55f
        )

        DeviceHealth.CRITICAL -> DeviceRecommendation.PauseWorkload(
            reason = RecommendationReason.HEALTH_CRITICAL,
            severity = RecommendationSeverity.CRITICAL
        )

        DeviceHealth.NORMAL -> {
            if (previousHealth == DeviceHealth.DEGRADED || previousHealth == DeviceHealth.CRITICAL) {
                DeviceRecommendation.ResumeWorkload(
                    reason = RecommendationReason.HEALTH_RECOVERED
                )
            } else {
                null
            }
        }
    }
}

private fun DeviceRecommendation.throttleKey(): String {
    return when (this) {
        is DeviceRecommendation.ReduceWorkload -> {
            "reduce:${reason.name}:${severity.name}:${suggestedScale}"
        }

        is DeviceRecommendation.PauseWorkload -> {
            "pause:${reason.name}:${severity.name}"
        }

        is DeviceRecommendation.ResumeWorkload -> {
            "resume:${reason.name}"
        }

        is DeviceRecommendation.DelayHeavyTask -> {
            "delay:${reason.name}:${retryAfterMs}"
        }
    }
}

internal fun safeThermalHeadroomValue(value: Float?): Float? {
    return value?.takeIf { it.isFinite() && it >= 0f }
}

internal fun calculateBatteryDrainPercentPerHour(
    previousChargeCounterUaH: Long?,
    previousCapacityPercent: Int?,
    previousTimestampMs: Long,
    currentChargeCounterUaH: Long?,
    capacityPercent: Int?,
    currentTimestampMs: Long = System.currentTimeMillis()
): Float? {
    val previousChargeCounter = previousChargeCounterUaH ?: return null
    val currentChargeCounter = currentChargeCounterUaH ?: return null
    val elapsedMs = currentTimestampMs - previousTimestampMs
    if (elapsedMs <= 0L) return null

    val drainedUaH = previousChargeCounter - currentChargeCounter
    if (drainedUaH <= 0L) return null

    val currentFullCapacityUaH = estimateBatteryFullCapacityUaH(
        chargeCounterUaH = currentChargeCounter,
        capacityPercent = capacityPercent
    )
    val previousFullCapacityUaH = estimateBatteryFullCapacityUaH(
        chargeCounterUaH = previousChargeCounter,
        capacityPercent = previousCapacityPercent
    )

    val fullCapacityUaH = currentFullCapacityUaH ?: previousFullCapacityUaH ?: return null
    if (fullCapacityUaH <= 0f) return null

    val drainRatePerHourUaH = drainedUaH.toFloat() * MILLIS_IN_HOUR / elapsedMs.toFloat()
    if (drainRatePerHourUaH <= 0f) return null

    return (drainRatePerHourUaH / fullCapacityUaH * 100f).coerceAtLeast(0f)
}

private fun estimateBatteryFullCapacityUaH(
    chargeCounterUaH: Long?,
    capacityPercent: Int?
): Float? {
    val chargeCounter = chargeCounterUaH ?: return null
    val clampedCapacity = capacityPercent?.coerceIn(1, 100) ?: return null
    val ratio = clampedCapacity / 100f
    if (ratio <= 0f) return null
    return chargeCounter / ratio
}
