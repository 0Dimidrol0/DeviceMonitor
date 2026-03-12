package io.github.dimidrol.impl

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import io.github.dimidrol.models.FrameMetricsSnapshot
import java.util.concurrent.atomic.AtomicInteger

internal class FrameMetricsTracker(
    private val window: Window,
    handler: Handler = Handler(Looper.getMainLooper())
) {

    private val frameCounter = AtomicInteger()
    private val jankCounter = AtomicInteger()

    @Volatile
    private var latest: FrameMetricsSnapshot? = null

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val totalNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (totalNs < 0) return@OnFrameMetricsAvailableListener

        val durationMs = totalNs / 1_000_000f
        val frames = frameCounter.incrementAndGet()

        val isJank = isJankyFrame(metrics, totalNs)
        val janks = if (isJank) jankCounter.incrementAndGet() else jankCounter.get()

        val jankPercent = if (frames == 0) {
            0f
        } else {
            (janks.toFloat() / frames.toFloat()) * 100f
        }

        latest = FrameMetricsSnapshot(
            frameDurationMs = durationMs,
            jankPercent = jankPercent.coerceIn(0f, 100f),
            sampleCount = frames
        )
    }

    init {
        window.addOnFrameMetricsAvailableListener(listener, handler)
    }

    fun snapshot(): FrameMetricsSnapshot? = latest

    fun dispose() {
        window.removeOnFrameMetricsAvailableListener(listener)
    }

    private fun isJankyFrame(metrics: FrameMetrics, totalNs: Long): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val deadlineNs = metrics.getMetric(FrameMetrics.DEADLINE)
            deadlineNs in 1..<totalNs
        } else {
            val refreshRate = window.windowManager?.defaultDisplay?.refreshRate ?: 60f
            val frameBudgetNs = (1_000_000_000f / refreshRate).toLong()
            totalNs > frameBudgetNs
        }
    }
}