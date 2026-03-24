package io.github.dimidrol.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dimidrol.DeviceMonitor
import io.github.dimidrol.DeviceMonitorConfig
import io.github.dimidrol.sample.ui.DeviceMonitorShowcaseTheme
import io.github.dimidrol.sample.ui.MonitorDashboardScreen

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<DeviceMonitorViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        DeviceMonitor.init(
            applicationContext,
            DeviceMonitorConfig.builder()
                .samplePeriodMs(1_500L)
                .memoryThresholdMb(512L)
                .storageThresholdMb(1_536L)
                .cpuOverloadThresholdPercent(85f)
                .batteryLowThresholdPercent(20)
                .batteryTemperatureThresholdC(44f)
                .build()
        )

        val monitor = DeviceMonitor.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            monitor.registerFrameMetrics(window)
        }
        viewModel.bindMonitor(monitor)

        setContent {
            DeviceMonitorShowcaseTheme {
                val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

                Surface {
                    MonitorDashboardScreen(
                        uiState = uiState,
                        onStartClick = viewModel::startMonitoring,
                        onStopClick = viewModel::stopMonitoring,
                        onSnapshotClick = viewModel::takeSnapshotNow
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startMonitoring()
    }

    override fun onStop() {
        viewModel.stopMonitoring()
        super.onStop()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DeviceMonitor.getInstance().unregisterFrameMetrics()
        }
        super.onDestroy()
    }
}
