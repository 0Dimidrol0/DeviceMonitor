package io.github.dimidrol

import android.content.Context
import android.view.Window
import io.github.dimidrol.impl.DeviceMonitorImpl
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.DeviceWarningEvent
import kotlinx.coroutines.flow.SharedFlow

interface DeviceMonitor {
    val snapshots: SharedFlow<DeviceSnapshot>
    val warningEvents: SharedFlow<DeviceWarningEvent>

    /**
     * @param  [samplePeriodMs] - how often to sample device state
     */
    fun start(samplePeriodMs: Long = DEFAULT_SAMPLE_PERIOD_MS)
    fun stop()

    fun snapshotNow(): DeviceSnapshot
    suspend fun snapshotNowAwait(timeoutMs: Long = DEFAULT_SAMPLE_PERIOD_MS): DeviceSnapshot

    fun registerFrameMetrics(window: Window)
    fun unregisterFrameMetrics()

    companion object {
        /**
         * @param  [context] - better be AppContext
         */
        @JvmStatic
        fun init(context: Context) {
            DeviceMonitorImpl.init(context)
        }

        @JvmStatic
        fun init(context: Context, config: DeviceMonitorConfig) {
            DeviceMonitorImpl.init(context, config)
        }

        @JvmStatic
        fun getInstance(): DeviceMonitor = DeviceMonitorImpl

        /**
         * @param  [threshold] - value in Megabytes
         */
        @JvmStatic
        fun setMemoryLowThreshold(threshold: Long) {
            DeviceMonitorImpl.setMemoryLowThreshold(threshold)
        }

        /**
         * @param  [threshold] - value in Megabytes
         */
        @JvmStatic
        fun setStorageLowThreshold(threshold: Long) {
            DeviceMonitorImpl.setStorageLowThreshold(threshold)
        }

        @JvmStatic
        fun configure(config: DeviceMonitorConfig) {
            DeviceMonitorImpl.configure(config)
        }

        @JvmStatic
        fun getConfig(): DeviceMonitorConfig = DeviceMonitorImpl.getConfig()
    }
}
