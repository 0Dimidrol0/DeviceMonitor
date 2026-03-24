package io.github.dimidrol

import android.content.Context
import android.view.Window
import io.github.dimidrol.impl.DeviceMonitorImpl
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.DeviceWarningEvent
import kotlinx.coroutines.flow.SharedFlow

interface DeviceMonitor {
    /**
     * Hot stream of collected device snapshots.
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     DeviceMonitor.getInstance().snapshots.collect { snapshot ->
     *         Log.d("DeviceMonitor", snapshot.toLogLine())
     *     }
     * }
     * ```
     */
    val snapshots: SharedFlow<DeviceSnapshot>

    /**
     * Stream of warning events emitted when risk conditions are detected.
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     DeviceMonitor.getInstance().warningEvents.collect { event ->
     *         Log.w("DeviceMonitor", event.toString())
     *     }
     * }
     * ```
     */
    val warningEvents: SharedFlow<DeviceWarningEvent>

    /**
     * Starts periodic device telemetry sampling.
     *
     * Example:
     * ```kotlin
     * DeviceMonitor.getInstance().start(samplePeriodMs = 5_000L)
     * ```
     *
     * @param samplePeriodMs sampling interval in milliseconds.
     */
    fun start(samplePeriodMs: Long = DEFAULT_SAMPLE_PERIOD_MS)

    /**
     * Stops periodic monitoring and detaches listeners.
     *
     * Example:
     * ```kotlin
     * DeviceMonitor.getInstance().stop()
     * ```
     */
    fun stop()

    /**
     * Collects and returns a snapshot immediately on the caller side.
     *
     * Example:
     * ```kotlin
     * val snapshot = DeviceMonitor.getInstance().snapshotNow()
     * ```
     */
    fun snapshotNow(): DeviceSnapshot

    /**
     * Suspends until a snapshot is collected or timeout is reached.
     *
     * Example:
     * ```kotlin
     * val snapshot = DeviceMonitor.getInstance().snapshotNowAwait(timeoutMs = 2_000L)
     * ```
     *
     * @param timeoutMs timeout in milliseconds for one-shot snapshot collection.
     */
    suspend fun snapshotNowAwait(timeoutMs: Long = DEFAULT_SAMPLE_PERIOD_MS): DeviceSnapshot

    /**
     * Attaches frame metrics tracker to the provided [window].
     *
     * Example:
     * ```kotlin
     * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
     *     DeviceMonitor.getInstance().registerFrameMetrics(window)
     * }
     * ```
     */
    fun registerFrameMetrics(window: Window)

    /**
     * Detaches frame metrics tracker previously attached with [registerFrameMetrics].
     *
     * Example:
     * ```kotlin
     * DeviceMonitor.getInstance().unregisterFrameMetrics()
     * ```
     */
    fun unregisterFrameMetrics()

    companion object {
        /**
         * Initializes monitor with default configuration.
         *
         * Example:
         * ```kotlin
         * DeviceMonitor.init(applicationContext)
         * ```
         *
         * @param context app context recommended.
         */
        @JvmStatic
        fun init(context: Context) {
            DeviceMonitorImpl.init(context)
        }

        /**
         * Initializes monitor with a custom [config].
         *
         * Example:
         * ```kotlin
         * DeviceMonitor.init(
         *     applicationContext,
         *     DeviceMonitorConfig.builder()
         *         .samplePeriodMs(3_000L)
         *         .enableCpu(true)
         *         .build()
         * )
         * ```
         */
        @JvmStatic
        fun init(context: Context, config: DeviceMonitorConfig) {
            DeviceMonitorImpl.init(context, config)
        }

        /**
         * Returns singleton monitor instance.
         *
         * Example:
         * ```kotlin
         * val monitor = DeviceMonitor.getInstance()
         * ```
         */
        @JvmStatic
        fun getInstance(): DeviceMonitor = DeviceMonitorImpl

        /**
         * Updates memory low threshold in megabytes.
         *
         * Example:
         * ```kotlin
         * DeviceMonitor.setMemoryLowThreshold(512L)
         * ```
         *
         * @param threshold threshold value in megabytes.
         */
        @JvmStatic
        fun setMemoryLowThreshold(threshold: Long) {
            DeviceMonitorImpl.setMemoryLowThreshold(threshold)
        }

        /**
         * Updates storage low threshold in megabytes.
         *
         * Example:
         * ```kotlin
         * DeviceMonitor.setStorageLowThreshold(2_048L)
         * ```
         *
         * @param threshold threshold value in megabytes.
         */
        @JvmStatic
        fun setStorageLowThreshold(threshold: Long) {
            DeviceMonitorImpl.setStorageLowThreshold(threshold)
        }

        /**
         * Applies full monitor configuration.
         *
         * Example:
         * ```kotlin
         * DeviceMonitor.configure(
         *     DeviceMonitorConfig.builder()
         *         .enableNetwork(false)
         *         .build()
         * )
         * ```
         */
        @JvmStatic
        fun configure(config: DeviceMonitorConfig) {
            DeviceMonitorImpl.configure(config)
        }

        /**
         * Returns currently active monitor configuration.
         *
         * Example:
         * ```kotlin
         * val config = DeviceMonitor.getConfig()
         * ```
         */
        @JvmStatic
        fun getConfig(): DeviceMonitorConfig = DeviceMonitorImpl.getConfig()
    }
}
