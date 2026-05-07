# DeviceMonitor

![Maven Central](https://img.shields.io/maven-central/v/io.github.0dimidrol0/DeviceMonitor)
![Build](https://img.shields.io/github/actions/workflow/status/0Dimidrol0/DeviceMonitor/publish.yml?branch=master&label=build)
![Android](https://img.shields.io/badge/Android-library-3DDC84)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-Modern-7F52FF)
[![Docs](https://img.shields.io/badge/docs-GitHub%20Pages-brightgreen)](https://0dimidrol0.github.io/DeviceMonitor/)

Lightweight Android telemetry library for high-load sessions such as media processing, streaming, exports, archiving, and long-running background work.

---

## Features

DeviceMonitor tracks:

- CPU usage and per-core usage
- CPU frequencies
- RAM and storage pressure
- Battery temperature, level, voltage, charge source, and health
- Thermal status and thermal zones
- Network type and traffic deltas
- Frame metrics (duration/jank)
- Battery power and battery drain estimation
- Thermal headroom snapshots

Data streams:

```kotlin
SharedFlow<DeviceSnapshot>
SharedFlow<DeviceWarningEvent>
SharedFlow<DeviceRecommendation>
```

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.0dimidrol0:DeviceMonitor:0.4.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.0dimidrol0:DeviceMonitor:0.4.0'
}
```

---

## Quick Start

```kotlin
DeviceMonitor.init(applicationContext)

val monitor = DeviceMonitor.getInstance()
monitor.start()

val snapshot = monitor.snapshotNow()

monitor.stop()
```

Custom configuration:

```kotlin
DeviceMonitor.init(
    appContext,
    DeviceMonitorConfig.builder()
        .samplePeriodMs(5_000L)
        .memoryThresholdMb(512)
        .batteryTemperatureThresholdC(48f)
        .enableRecommendations(true)
        .recommendationCooldownMs(15_000L)
        .enableThermalHeadroom(true)
        .enableBatteryDrain(true)
        .thermalHeadroomForecastSeconds(10)
        .batteryDrainHighThresholdPercentPerHour(20f)
        .build()
)
```

---

## Adaptive Workload Guard (0.4.0)

Recommendations are emitted from `monitor.recommendations` and can be applied directly to bitrate/FPS/concurrency controls.

```kotlin
lifecycleScope.launch {
    monitor.recommendations.collect { recommendation ->
        when (recommendation) {
            is DeviceRecommendation.ReduceWorkload -> {
                encoder.setBitrate((baseBitrate * recommendation.suggestedScale).toInt())
                camera.setTargetFps((baseFps * recommendation.suggestedScale).toInt())
                workerPool.setMaxConcurrency(
                    (baseConcurrency * recommendation.suggestedScale).toInt().coerceAtLeast(1)
                )
            }
            is DeviceRecommendation.PauseWorkload -> encoder.pause()
            is DeviceRecommendation.ResumeWorkload -> encoder.resume()
            is DeviceRecommendation.DelayHeavyTask -> scheduler.retryAfter(recommendation.retryAfterMs)
        }
    }
}
```

Built-in behavior:

- `WARM` health -> `ReduceWorkload(..., suggestedScale = 0.8f)`
- `DEGRADED` health -> `ReduceWorkload(..., suggestedScale = 0.55f)`
- `CRITICAL` health -> `PauseWorkload(...)`
- return from `DEGRADED`/`CRITICAL` to `NORMAL` -> `ResumeWorkload(...)`

Recommendation duplicates are throttled using `recommendationCooldownMs`.

---

## Warning Events

```kotlin
sealed class DeviceWarningEvent {
    data class ThermalChanged(...)
    data class MemoryLow(...)
    data class StorageLow(...)
    data class BatteryLow(...)
    data class BatteryTemperatureHigh(...)
    data class CpuOverload(...)
    data class ThermalHeadroomLow(...)
    data class BatteryDrainHigh(...)
}
```

---

## Extended Telemetry

`DeviceSnapshot` includes:

- `thermalZones: List<ThermalZoneReading>`
- `networkTraffic: NetworkTrafficSnapshot?`
- `batteryPower: BatteryPowerSnapshot?`
- `thermalHeadroom: ThermalHeadroomSnapshot?`
- `batteryDrain: BatteryDrainSnapshot?`

`thermalHeadroom` and `batteryDrain` fields are nullable to stay safe on unsupported Android/OEM implementations.

---

## Workload Session API

Track a workload execution and receive a summarized report:

```kotlin
val session = monitor.createWorkloadSession(
    name = "video-export",
    type = WorkloadType.MEDIA_PROCESSING
)

session.start()
// run heavy operation
val report = session.stop()
```

`WorkloadReport` includes:

- `durationMs`
- `startSnapshot`
- `endSnapshot`
- `maxRiskScore`
- `warningCount`
- `recommendations`

---

## Testing

```bash
./gradlew :device_monitor:test
```

Coverage includes:

- recommendation generation and throttling
- battery drain math
- thermal headroom null safety
- config builder limits/defaults
- workload session report aggregation

---

## Roadmap

- CPU throttling detection
- GPU monitoring
- per-device recommendation tuning profiles
- recommendation policy plug-ins
- richer workload session analytics

---

## Changelog

### 0.4.0

- Added Adaptive Workload Guard with `DeviceRecommendation` flow.
- Added `ThermalHeadroomSnapshot` and `BatteryDrainSnapshot`.
- Added warning events `ThermalHeadroomLow` and `BatteryDrainHigh`.
- Added workload APIs: `createWorkloadSession`, `WorkloadSession`, `WorkloadReport`, `WorkloadType`.
- Extended `DeviceMonitorConfig` with recommendation/headroom/drain controls.

### 0.3.0

- Added extended telemetry (`thermalZones`, `networkTraffic`, `batteryPower`, frame metrics support).
- Added `riskScore()` / `health()` helpers and warning transition throttling.

---

## License

Apache License 2.0

---

## Author

Eric Shvets  
https://github.com/0dimidrol0
