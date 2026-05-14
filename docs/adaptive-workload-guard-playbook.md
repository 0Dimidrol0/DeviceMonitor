# Adaptive Workload Guard Playbook

This guide contains practical integration patterns for applying `DeviceRecommendation` in real workloads.

## Common Collector Pattern

```kotlin
class WorkloadGuard(
    private val monitor: DeviceMonitor,
    private val scope: CoroutineScope
) {
    fun start(
        onReduce: (scale: Float) -> Unit,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onDelay: (retryAfterMs: Long) -> Unit
    ): Job {
        return scope.launch {
            monitor.recommendations.collect { recommendation ->
                when (recommendation) {
                    is DeviceRecommendation.ReduceWorkload -> onReduce(recommendation.suggestedScale)
                    is DeviceRecommendation.PauseWorkload -> onPause()
                    is DeviceRecommendation.ResumeWorkload -> onResume()
                    is DeviceRecommendation.DelayHeavyTask -> onDelay(recommendation.retryAfterMs)
                }
            }
        }
    }
}
```

## ExoPlayer (Bitrate + Optional FPS Tier)

```kotlin
// Pseudocode. Adapt to your player graph and track selector setup.
class ExoGuardController(
    private val trackSelector: DefaultTrackSelector
) {
    private val baseMaxBitrate = 4_000_000
    private var appliedScale = 1f

    fun onReduce(scale: Float) {
        appliedScale = min(appliedScale, scale)
        val targetBitrate = (baseMaxBitrate * appliedScale).toInt()
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setMaxVideoBitrate(targetBitrate)
            .build()
    }

    fun onPause(player: ExoPlayer) {
        player.playWhenReady = false
    }

    fun onResume(player: ExoPlayer) {
        appliedScale = 1f
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setMaxVideoBitrate(baseMaxBitrate)
            .build()
        player.playWhenReady = true
    }
}
```

## CameraX (Target FPS + Quality Tier)

```kotlin
// Pseudocode. Rebind use-cases for substantial profile changes.
class CameraXGuardController {
    private val baseFps = 30
    private var currentFps = baseFps

    fun targetFpsFor(scale: Float): Int {
        return (baseFps * scale).toInt().coerceIn(12, baseFps)
    }

    fun onReduce(scale: Float) {
        currentFps = min(currentFps, targetFpsFor(scale))
        // Reconfigure Camera2Interop / encoder pipeline with currentFps.
    }

    fun onPause() {
        // Pause recording/streaming path if UX allows, keep preview alive.
    }

    fun onResume() {
        currentFps = baseFps
        // Rebind with base profile (fps + quality).
    }
}
```

## WebRTC (Sender Bitrate + Capture Downscale)

```kotlin
// Pseudocode for org.webrtc.
class WebRtcGuardController(
    private val sender: RtpSender,
    private val capturer: VideoCapturer
) {
    private val baseBitrateBps = 1_800_000
    private val baseFps = 30
    private val baseWidth = 1280
    private val baseHeight = 720

    fun onReduce(scale: Float) {
        val params = sender.parameters
        params.encodings.forEach { encoding ->
            encoding.maxBitrateBps = (baseBitrateBps * scale).toInt()
            encoding.scaleResolutionDownBy = (1f / scale).coerceAtMost(2.0)
        }
        sender.parameters = params

        val fps = (baseFps * scale).toInt().coerceIn(12, baseFps)
        capturer.changeCaptureFormat(baseWidth, baseHeight, fps)
    }

    fun onPause() {
        val params = sender.parameters
        params.encodings.forEach { it.active = false }
        sender.parameters = params
    }

    fun onResume() {
        val params = sender.parameters
        params.encodings.forEach {
            it.active = true
            it.maxBitrateBps = baseBitrateBps
            it.scaleResolutionDownBy = 1.0
        }
        sender.parameters = params
        capturer.changeCaptureFormat(baseWidth, baseHeight, baseFps)
    }
}
```

## Concurrency Control For Background Work

```kotlin
class ConcurrencyGuard(
    private val dispatcher: LimitedDispatcher
) {
    private val baseParallelism = 4

    fun onReduce(scale: Float) {
        val target = (baseParallelism * scale).toInt().coerceAtLeast(1)
        dispatcher.setParallelism(target)
    }

    fun onResume() {
        dispatcher.setParallelism(baseParallelism)
    }
}
```

## Rollout Checklist

1. Start with telemetry-only mode and collect `DeviceSnapshot` + `warningEvents`.
2. Enable recommendations and log all applied actions.
3. Roll out `ReduceWorkload` first, keep `PauseWorkload` gated by feature flag.
4. Compare `WorkloadReport` baselines (`maxRiskScore`, `avgRiskScore`, `warningCount`).
5. Tune thresholds per workload type with `DeviceMonitorConfig`.
