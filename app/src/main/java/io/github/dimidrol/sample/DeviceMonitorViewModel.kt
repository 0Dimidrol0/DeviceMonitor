package io.github.dimidrol.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dimidrol.DeviceMonitor
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.DeviceWarningEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHOWCASE_SAMPLE_PERIOD_MS = 1_500L
private const val WARNING_LOG_LIMIT = 30

data class WarningUiItem(
    val id: Long,
    val title: String,
    val details: String,
    val timestampMs: Long
)

data class MonitorUiState(
    val isMonitoring: Boolean = false,
    val lastSnapshot: DeviceSnapshot? = null,
    val warningItems: List<WarningUiItem> = emptyList(),
    val sampleCount: Int = 0,
    val lastUpdatedAtMs: Long? = null,
    val sessionStartedAtMs: Long? = null
)

class DeviceMonitorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    private var monitor: DeviceMonitor? = null
    private var monitorBound = false

    fun bindMonitor(monitor: DeviceMonitor) {
        if (monitorBound) return
        this.monitor = monitor
        monitorBound = true

        observeSnapshots(monitor)
        observeWarnings(monitor)
    }

    fun startMonitoring() {
        val activeMonitor = monitor ?: return
        activeMonitor.start(SHOWCASE_SAMPLE_PERIOD_MS)
        _uiState.update { previous ->
            previous.copy(
                isMonitoring = true,
                sessionStartedAtMs = previous.sessionStartedAtMs ?: System.currentTimeMillis()
            )
        }
    }

    fun stopMonitoring() {
        monitor?.stop()
        _uiState.update { it.copy(isMonitoring = false) }
    }

    fun takeSnapshotNow() {
        val activeMonitor = monitor ?: return
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.Default) {
                activeMonitor.snapshotNow()
            }
            _uiState.update { previous ->
                previous.copy(
                    lastSnapshot = snapshot,
                    sampleCount = previous.sampleCount + 1,
                    lastUpdatedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    private fun observeSnapshots(monitor: DeviceMonitor) {
        viewModelScope.launch {
            monitor.snapshots.collect { snapshot ->
                _uiState.update { previous ->
                    previous.copy(
                        lastSnapshot = snapshot,
                        sampleCount = previous.sampleCount + 1,
                        lastUpdatedAtMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun observeWarnings(monitor: DeviceMonitor) {
        viewModelScope.launch {
            monitor.warningEvents.collect { event ->
                val now = System.currentTimeMillis()
                val newItem = WarningUiItem(
                    id = now,
                    title = event.toTitle(),
                    details = event.toDetails(),
                    timestampMs = now
                )

                _uiState.update { previous ->
                    previous.copy(
                        warningItems = listOf(newItem)
                            .plus(previous.warningItems)
                            .take(WARNING_LOG_LIMIT)
                    )
                }
            }
        }
    }
}

private fun DeviceWarningEvent.toTitle(): String = when (this) {
    is DeviceWarningEvent.ThermalChanged -> "Thermal status changed"
    is DeviceWarningEvent.MemoryLow -> "Low memory"
    is DeviceWarningEvent.StorageLow -> "Low storage"
    is DeviceWarningEvent.BatteryLow -> "Battery low"
    is DeviceWarningEvent.BatteryTemperatureHigh -> "Battery hot"
    is DeviceWarningEvent.CpuOverload -> "CPU overload"
}

private fun DeviceWarningEvent.toDetails(): String = when (this) {
    is DeviceWarningEvent.ThermalChanged -> "${from.name} -> ${to.name}"
    is DeviceWarningEvent.MemoryLow -> "Available RAM: ${availBytes.toReadableBytes()}"
    is DeviceWarningEvent.StorageLow -> "Free storage: ${freeBytes.toReadableBytes()}"
    is DeviceWarningEvent.BatteryLow -> "Level: ${levelPercent}%"
    is DeviceWarningEvent.BatteryTemperatureHigh -> "Temperature: ${"%.1f".format(temperatureC)} C"
    is DeviceWarningEvent.CpuOverload -> "Usage: ${"%.1f".format(usagePercent)}% on $coreCount cores"
}

private fun Long.toReadableBytes(): String {
    if (this < 1024L) return "$this B"
    val kb = this / 1024f
    if (kb < 1024f) return "%.1f KB".format(kb)
    val mb = kb / 1024f
    if (mb < 1024f) return "%.1f MB".format(mb)
    val gb = mb / 1024f
    return "%.2f GB".format(gb)
}
