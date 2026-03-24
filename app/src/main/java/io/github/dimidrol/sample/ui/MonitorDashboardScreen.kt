package io.github.dimidrol.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dimidrol.models.DeviceHealth
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.sample.MonitorUiState
import io.github.dimidrol.sample.WarningUiItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun MonitorDashboardScreen(
    uiState: MonitorUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onSnapshotClick: () -> Unit
) {
    val snapshot = uiState.lastSnapshot
    val riskScore = snapshot?.riskScore() ?: 0
    val animatedRisk = animateFloatAsState(
        targetValue = riskScore / 100f,
        animationSpec = spring(),
        label = "risk-score"
    ).value

    val background = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroCard(
                    uiState = uiState,
                    riskScore = riskScore,
                    riskProgress = animatedRisk
                )
            }

            item {
                ActionRow(
                    isMonitoring = uiState.isMonitoring,
                    onStartClick = onStartClick,
                    onStopClick = onStopClick,
                    onSnapshotClick = onSnapshotClick
                )
            }

            item {
                MetricsGrid(snapshot = snapshot)
            }

            item {
                WarningFeed(items = uiState.warningItems)
            }
        }
    }
}

@Composable
private fun HeroCard(
    uiState: MonitorUiState,
    riskScore: Int,
    riskProgress: Float
) {
    val snapshot = uiState.lastSnapshot
    val health = snapshot?.health() ?: DeviceHealth.NORMAL
    val healthColor = healthColor(health)
    val updatedAt = formatTime(uiState.lastUpdatedAtMs)
    val sessionSince = formatTime(uiState.sessionStartedAtMs)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "DeviceMonitor Showcase",
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = "Live health dashboard powered by DeviceMonitor",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Health: ${health.name}") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = healthColor
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = healthColor.copy(alpha = 0.14f),
                        labelColor = healthColor
                    )
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Samples: ${uiState.sampleCount}") }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (uiState.isMonitoring) "Status: ACTIVE" else "Status: PAUSED") }
                )
            }

            Text(
                text = "Risk score: $riskScore/100",
                style = MaterialTheme.typography.titleLarge
            )
            LinearProgressIndicator(
                progress = { riskProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                color = healthColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Last update: $updatedAt",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Session: $sessionSince",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun healthColor(health: DeviceHealth): Color {
    return when (health) {
        DeviceHealth.NORMAL -> Color(0xFF1DAD74)
        DeviceHealth.WARM -> Color(0xFFBA8A00)
        DeviceHealth.DEGRADED -> Color(0xFFD76515)
        DeviceHealth.CRITICAL -> Color(0xFFC62828)
    }
}

@Composable
private fun ActionRow(
    isMonitoring: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onSnapshotClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = onStartClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Start")
        }

        FilledTonalButton(
            onClick = onStopClick,
            modifier = Modifier.weight(1f),
            enabled = isMonitoring
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Stop")
        }

        OutlinedButton(
            onClick = onSnapshotClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Now")
        }
    }
}

@Composable
private fun MetricsGrid(snapshot: DeviceSnapshot?) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Live Metrics",
                style = MaterialTheme.typography.headlineMedium
            )

            if (snapshot == null) {
                Text(
                    text = "No snapshot yet. Tap Start to begin monitoring.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Speed,
                    title = "CPU",
                    value = "${formatPercent(snapshot.cpuUsagePercent)}%",
                    subtitle = "Avg per core ${formatPercent(snapshot.averageCpuUsage())}%",
                    progress = (snapshot.cpuUsagePercent ?: 0f) / 100f
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Memory,
                    title = "RAM",
                    value = snapshot.memAvailBytes.toReadableBytes(),
                    subtitle = if (snapshot.memLow == true) "Pressure: HIGH" else "Pressure: stable",
                    progress = memoryPressure(snapshot.memAvailBytes, snapshot.memThresholdBytes)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Storage,
                    title = "Storage",
                    value = snapshot.storageFreeBytes.toReadableBytes(),
                    subtitle = "Used ${formatPercent(snapshot.storageUsedPercent())}%",
                    progress = (snapshot.storageUsedPercent() ?: 0f) / 100f
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Battery",
                    value = snapshot.batteryLevel?.let { "$it%" } ?: "--",
                    subtitle = snapshot.batteryTempC?.let { "Temp ${formatPercent(it)} C" } ?: "No temp data",
                    progress = (snapshot.batteryLevel ?: 0) / 100f
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Thermostat,
                    title = "Thermal",
                    value = snapshot.thermalStatus.name,
                    subtitle = "Zones: ${snapshot.thermalZones.size}",
                    progress = thermalProgress(snapshot.thermalStatus.name)
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.NetworkCheck,
                    title = "Network",
                    value = snapshot.networkType?.name ?: "UNKNOWN",
                    subtitle = "RX/TX ${snapshot.networkTraffic?.rxBytesDelta.toReadableBytes()}/${snapshot.networkTraffic?.txBytesDelta.toReadableBytes()}",
                    progress = networkDeltaProgress(snapshot.networkTraffic?.rxBytesDelta, snapshot.networkTraffic?.txBytesDelta)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    progress: Float
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WarningFeed(items: List<WarningUiItem>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Warning Events",
                style = MaterialTheme.typography.headlineMedium
            )

            AnimatedVisibility(visible = items.isEmpty()) {
                Text(
                    text = "No warnings so far. Keep this screen open under load to collect events.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }

            items.take(8).forEach { event ->
                WarningItemRow(item = event)
            }
        }
    }
}

@Composable
private fun WarningItemRow(item: WarningUiItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = item.details,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = formatTime(item.timestampMs),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun memoryPressure(availBytes: Long?, thresholdBytes: Long?): Float {
    if (availBytes == null || thresholdBytes == null || thresholdBytes <= 0L) return 0f
    return (thresholdBytes.toFloat() / availBytes.toFloat()).coerceIn(0f, 1f)
}

private fun thermalProgress(statusName: String): Float {
    return when (statusName) {
        "NONE" -> 0.1f
        "LIGHT" -> 0.25f
        "MODERATE" -> 0.45f
        "SEVERE" -> 0.65f
        "CRITICAL" -> 0.85f
        "EMERGENCY", "SHUTDOWN" -> 1f
        else -> 0.2f
    }
}

private fun networkDeltaProgress(rxDelta: Long?, txDelta: Long?): Float {
    val total = (rxDelta ?: 0L) + (txDelta ?: 0L)
    return (total / (1024f * 1024f * 3f)).coerceIn(0f, 1f)
}

private fun formatPercent(value: Float?): String {
    if (value == null) return "--"
    return value.roundToInt().toString()
}

private fun Long?.toReadableBytes(): String {
    if (this == null) return "--"
    if (this < 1024L) return "$this B"

    val kb = this / 1024f
    if (kb < 1024f) return "%.1f KB".format(kb)

    val mb = kb / 1024f
    if (mb < 1024f) return "%.1f MB".format(mb)

    val gb = mb / 1024f
    return "%.2f GB".format(gb)
}

private fun formatTime(timestampMs: Long?): String {
    if (timestampMs == null) return "--:--:--"
    val formatter = rememberFormatter()
    return formatter.format(
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalTime()
    )
}

private fun rememberFormatter(): DateTimeFormatter {
    return DateTimeFormatter.ofPattern("HH:mm:ss")
}
