package com.example.homelabmonitor.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.homelabmonitor.data.model.ContainerItem
import com.example.homelabmonitor.data.model.HomelabSnapshot
import com.example.homelabmonitor.data.model.MockSnapshotFactory
import com.example.homelabmonitor.data.model.SensorReading
import com.example.homelabmonitor.data.model.formatBytes
import com.example.homelabmonitor.data.model.formatDuration
import com.example.homelabmonitor.data.model.formatObservedAt
import com.example.homelabmonitor.data.model.formatPercent
import com.example.homelabmonitor.data.model.healthState
import com.example.homelabmonitor.data.model.label
import com.example.homelabmonitor.data.model.ramUsagePercent
import com.example.homelabmonitor.data.model.usagePercent
import com.example.homelabmonitor.data.repository.SnapshotStore

private fun readSnapshot(context: Context): HomelabSnapshot =
    SnapshotStore(context).read() ?: MockSnapshotFactory.create()

private data class WidgetSize(val width: Dp, val height: Dp) {
    val isCompact: Boolean
        get() = width < 150.dp || height < 90.dp

    val isExpanded: Boolean
        get() = width >= 220.dp && height >= 150.dp
}

@Composable
private fun currentWidgetSize(): WidgetSize = WidgetSize(
    width = LocalSize.current.width,
    height = LocalSize.current.height,
)

@Composable
private fun titleStyle(compact: Boolean = false) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = if (compact) 14.sp else 16.sp,
    fontWeight = FontWeight.Bold,
)

@Composable
private fun valueStyle(compact: Boolean = false) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = if (compact) 12.sp else 14.sp,
)

@Composable
private fun captionStyle() = TextStyle(
    color = GlanceTheme.colors.onSurfaceVariant,
    fontSize = 11.sp,
)

@Composable
private fun WidgetShell(title: String, content: @Composable (WidgetSize) -> Unit) {
    GlanceTheme {
        val size = currentWidgetSize()
        val padding = if (size.isCompact) 8.dp else 12.dp
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(if (size.isCompact) 16.dp else 20.dp)
                .padding(padding),
        ) {
            Text(title, style = titleStyle(size.isCompact))
            Spacer(GlanceModifier.height(if (size.isCompact) 3.dp else 6.dp))
            content(size)
        }
    }
}

@Composable
private fun WidgetRow(label: String, value: String, size: WidgetSize) {
    Row(GlanceModifier.fillMaxWidth()) {
        Text(label, style = valueStyle(size.isCompact))
        Spacer(GlanceModifier.width(8.dp))
        Text(value, style = valueStyle(size.isCompact))
    }
}

@Composable
private fun StatusPill(online: Boolean, compact: Boolean) {
    val background = if (online) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.errorContainer
    val foreground = if (online) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onErrorContainer
    Box(
        modifier = GlanceModifier
            .background(background)
            .cornerRadius(12.dp)
            .padding(if (compact) 5.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row {
            Spacer(
                GlanceModifier
                    .size(if (compact) 7.dp else 8.dp)
                    .background(foreground)
                    .cornerRadius(8.dp),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = if (online) "ONLINE" else "OFFLINE",
                style = TextStyle(
                    color = foreground,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

private fun pieChartBitmap(context: Context, fraction: Float, accentColor: Int, sizeDp: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f
    val radius = center - (sizePx * 0.06f)
    val bounds = RectF(center - radius, center - radius, center + radius, center + radius)

    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF536277.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radius, track)

    val used = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    canvas.drawArc(bounds, -90f, 360f * fraction.coerceIn(0f, 1f), true, used)
    return bitmap
}

private fun thermometerBitmap(context: Context, accentColor: Int, sizeDp: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = sizePx / 2f
    val bulbRadius = sizePx * 0.18f
    val tubeWidth = sizePx * 0.18f
    val tubeTop = sizePx * 0.16f
    val tubeBottom = sizePx * 0.72f
    val tube = RectF(
        centerX - tubeWidth / 2f,
        tubeTop,
        centerX + tubeWidth / 2f,
        tubeBottom,
    )

    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB7C3D6.toInt()
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.06f
    }
    canvas.drawRoundRect(tube, tubeWidth / 2f, tubeWidth / 2f, outline)
    canvas.drawCircle(centerX, sizePx * 0.78f, bulbRadius + outline.strokeWidth / 2f, outline)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    val fillTube = RectF(
        centerX - tubeWidth * 0.28f,
        sizePx * 0.43f,
        centerX + tubeWidth * 0.28f,
        tubeBottom,
    )
    canvas.drawRoundRect(fillTube, tubeWidth * 0.28f, tubeWidth * 0.28f, fill)
    canvas.drawCircle(centerX, sizePx * 0.78f, bulbRadius, fill)
    return bitmap
}

@Composable
private fun PieMetric(
    label: String,
    percent: Double,
    chart: Bitmap,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier.width(if (compact) 62.dp else 78.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Image(
                provider = ImageProvider(chart),
                contentDescription = "$label ${formatPercent(percent)}",
                modifier = GlanceModifier.size(if (compact) 42.dp else 56.dp),
            )
            Text(label, style = captionStyle())
            Text(formatPercent(percent), style = valueStyle(compact))
        }
    }
}

@Composable
private fun Thermometer(context: Context, sensor: SensorReading, compact: Boolean) {
    val value = sensor.value
    val accentColor = when {
        !sensor.available || value == null -> 0xFF8291A8.toInt()
        value >= 80.0 -> 0xFFFF8A80.toInt()
        else -> 0xFF62D6FF.toInt()
    }
    Box(
        modifier = GlanceModifier.width(if (compact) 64.dp else 78.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Image(
                provider = ImageProvider(thermometerBitmap(context, accentColor, if (compact) 26 else 32)),
                contentDescription = "Sensor de temperatura",
                modifier = GlanceModifier.size(if (compact) 26.dp else 32.dp),
            )
            Text(
                if (sensor.available && value != null) "$value ${sensor.unit}" else "--",
                style = valueStyle(compact),
            )
            Text(sensor.name, style = captionStyle())
        }
    }
}

@Composable
private fun StorageBar(volumeName: String, usage: Float, compact: Boolean) {
    Row(GlanceModifier.fillMaxWidth()) {
        Text(volumeName, style = captionStyle())
        Spacer(GlanceModifier.width(6.dp))
        Text(formatPercent((usage * 100).toDouble()), style = valueStyle(compact))
    }
    LinearProgressIndicator(
        progress = usage,
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(if (compact) 5.dp else 7.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
    if (!compact) Spacer(GlanceModifier.height(3.dp))
}

@Composable
private fun ContainerLine(item: ContainerItem, size: WidgetSize) {
    val state = listOfNotNull(item.state, item.health).joinToString(" · ")
    WidgetRow(item.name, state, size)
}

object StatusWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Status") { size ->
                StatusPill(snapshot.online, size.isCompact)
                WidgetRow("Sincronização", formatObservedAt(snapshot.observedAtEpochMs), size)
                if (size.isExpanded) WidgetRow("Uptime", formatDuration(snapshot.uptimeSeconds), size)
            }
        }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget
}

object ResourcesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        val cpuPercent = snapshot.cpu.usagePercent.coerceIn(0.0, 100.0)
        val ramPercent = (snapshot.ramUsagePercent() * 100f).toDouble().coerceIn(0.0, 100.0)
        val cpuChart = pieChartBitmap(context, (cpuPercent / 100.0).toFloat(), 0xFF62D6FF.toInt(), 56)
        val ramChart = pieChartBitmap(context, (ramPercent / 100.0).toFloat(), 0xFFA899FF.toInt(), 56)
        provideContent {
            WidgetShell("CPU e RAM") { size ->
                Row(GlanceModifier.fillMaxWidth()) {
                    PieMetric("CPU", cpuPercent, cpuChart, size.isCompact)
                    Spacer(GlanceModifier.width(if (size.isCompact) 2.dp else 8.dp))
                    PieMetric("RAM", ramPercent, ramChart, size.isCompact)
                }
                if (size.isExpanded) {
                    snapshot.cpu.load1m?.let { WidgetRow("Load 1m", "%.2f".format(it), size) }
                    WidgetRow("RAM", formatBytes(snapshot.memory.usedBytes), size)
                }
            }
        }
    }
}

class ResourcesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResourcesWidget
}

object StorageWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Armazenamento") { size ->
                val maxVolumes = when {
                    size.isExpanded -> 5
                    size.isCompact -> 2
                    else -> 3
                }
                snapshot.volumes.take(maxVolumes).forEach { volume ->
                    StorageBar(volume.name, volume.usagePercent(), size.isCompact)
                }
                if (snapshot.volumes.isEmpty()) Text("Sem volumes", style = valueStyle(size.isCompact))
            }
        }
    }
}

class StorageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StorageWidget
}

object SensorsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Sensores") { size ->
                val maxSensors = when {
                    size.isExpanded -> 4
                    size.isCompact -> 2
                    else -> 3
                }
                Row(GlanceModifier.fillMaxWidth()) {
                    snapshot.sensors.take(maxSensors).forEachIndexed { index, sensor ->
                        if (index > 0) Spacer(GlanceModifier.width(if (size.isCompact) 2.dp else 6.dp))
                        Thermometer(context, sensor, size.isCompact)
                    }
                }
                if (snapshot.sensors.isEmpty()) Text("Sem sensores", style = valueStyle(size.isCompact))
            }
        }
    }
}

class SensorsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SensorsWidget
}

object ContainersWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Docker") { size ->
                WidgetRow("Ativos", snapshot.containers.running.toString(), size)
                WidgetRow("Parados", snapshot.containers.stopped.toString(), size)
                WidgetRow("Erro", snapshot.containers.error.toString(), size)
                if (!size.isCompact) {
                    val maxItems = if (size.isExpanded) 5 else 2
                    snapshot.containers.items.take(maxItems).forEach { ContainerLine(it, size) }
                    if (snapshot.containers.items.isEmpty()) {
                        Text("Sem detalhes dos containers", style = captionStyle())
                    }
                }
            }
        }
    }
}

class ContainersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContainersWidget
}

object ImmichWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Immich") { size ->
                if (!snapshot.immich.enabled) {
                    Text("Desativado no agente", style = valueStyle(size.isCompact))
                } else {
                    WidgetRow("Servidor", snapshot.immich.server, size)
                    WidgetRow("Banco", snapshot.immich.database, size)
                    if (size.isExpanded) snapshot.immich.version?.let { WidgetRow("Versão", it, size) }
                }
            }
        }
    }
}

class ImmichWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ImmichWidget
}

object SummaryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Geral") { size ->
                WidgetRow("Estado", snapshot.healthState().label(), size)
                WidgetRow("CPU", formatPercent(snapshot.cpu.usagePercent), size)
                WidgetRow("RAM", formatBytes(snapshot.memory.usedBytes), size)
                WidgetRow("Docker", "${snapshot.containers.running} ativos", size)
                if (!size.isCompact && snapshot.immich.enabled) WidgetRow("Immich", snapshot.immich.server, size)
                if (size.isExpanded) {
                    WidgetRow("Host", snapshot.host, size)
                    WidgetRow("Uptime", formatDuration(snapshot.uptimeSeconds), size)
                    WidgetRow("Atualizado", formatObservedAt(snapshot.observedAtEpochMs), size)
                }
            }
        }
    }
}

class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget
}

object HomelabWidgetUpdater {
    suspend fun updateAll(context: Context) {
        StatusWidget.updateAll(context)
        ResourcesWidget.updateAll(context)
        StorageWidget.updateAll(context)
        SensorsWidget.updateAll(context)
        ContainersWidget.updateAll(context)
        ImmichWidget.updateAll(context)
        SummaryWidget.updateAll(context)
    }
}
