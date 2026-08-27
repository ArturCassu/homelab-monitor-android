package com.example.homelabmonitor.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import androidx.glance.unit.ColorProvider
import com.example.homelabmonitor.data.model.AccentTheme
import com.example.homelabmonitor.data.model.ContainerItem
import com.example.homelabmonitor.data.model.HomelabSnapshot
import com.example.homelabmonitor.data.model.SensorGroup
import com.example.homelabmonitor.data.model.formatBytes
import com.example.homelabmonitor.data.model.formatDuration
import com.example.homelabmonitor.data.model.formatObservedAt
import com.example.homelabmonitor.data.model.formatPercent
import com.example.homelabmonitor.data.model.formatTemperature
import com.example.homelabmonitor.data.model.healthState
import com.example.homelabmonitor.data.model.label
import com.example.homelabmonitor.data.model.ramUsagePercent
import com.example.homelabmonitor.data.model.sensorGroups
import com.example.homelabmonitor.data.model.usagePercent
import com.example.homelabmonitor.data.repository.SecureSettingsStore
import com.example.homelabmonitor.data.repository.SnapshotStore

private data class WidgetPalette(
    val background: Color,
    val surface: Color,
    val track: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val muted: Color,
    val positive: Color,
    val warning: Color,
    val error: Color,
)

private fun widgetPalette(accentTheme: AccentTheme): WidgetPalette = when (accentTheme) {
    AccentTheme.GRAPHITE -> WidgetPalette(
        background = Color(0xFF0D0F11),
        surface = Color(0xFF181B1F),
        track = Color(0xFF343A41),
        primary = Color(0xFFE0E4E8),
        secondary = Color(0xFFAEB7C1),
        text = Color(0xFFF1F3F5),
        muted = Color(0xFFA9B1BA),
        positive = Color(0xFF72D6A2),
        warning = Color(0xFFE8B96C),
        error = Color(0xFFF18B8B),
    )
    AccentTheme.MINT -> WidgetPalette(
        background = Color(0xFF0A1110),
        surface = Color(0xFF12231D),
        track = Color(0xFF2C4940),
        primary = Color(0xFFA2E6C1),
        secondary = Color(0xFFBFDCCB),
        text = Color(0xFFECF8F0),
        muted = Color(0xFFA6BDB0),
        positive = Color(0xFFA2E6C1),
        warning = Color(0xFFF0C878),
        error = Color(0xFFF39A91),
    )
    AccentTheme.AMBER -> WidgetPalette(
        background = Color(0xFF120E08),
        surface = Color(0xFF261C10),
        track = Color(0xFF4E3A22),
        primary = Color(0xFFFFD18A),
        secondary = Color(0xFFE8C99C),
        text = Color(0xFFFFF4E3),
        muted = Color(0xFFCDB99A),
        positive = Color(0xFF91D5A9),
        warning = Color(0xFFFFD18A),
        error = Color(0xFFF39A91),
    )
    AccentTheme.VIOLET -> WidgetPalette(
        background = Color(0xFF100D14),
        surface = Color(0xFF21182B),
        track = Color(0xFF453553),
        primary = Color(0xFFD9BFFF),
        secondary = Color(0xFFD7C8E8),
        text = Color(0xFFF8F0FF),
        muted = Color(0xFFC5B9D0),
        positive = Color(0xFF91D5A9),
        warning = Color(0xFFF0C878),
        error = Color(0xFFF39A91),
    )
}

private fun widgetColors(palette: WidgetPalette): ColorProviders = colorProviders(
    primary = ColorProvider(palette.primary),
    onPrimary = ColorProvider(palette.background),
    primaryContainer = ColorProvider(palette.surface),
    onPrimaryContainer = ColorProvider(palette.text),
    secondary = ColorProvider(palette.secondary),
    onSecondary = ColorProvider(palette.background),
    secondaryContainer = ColorProvider(palette.surface),
    onSecondaryContainer = ColorProvider(palette.text),
    tertiary = ColorProvider(palette.warning),
    onTertiary = ColorProvider(palette.background),
    tertiaryContainer = ColorProvider(palette.surface),
    onTertiaryContainer = ColorProvider(palette.text),
    error = ColorProvider(palette.error),
    errorContainer = ColorProvider(palette.surface),
    onError = ColorProvider(palette.background),
    onErrorContainer = ColorProvider(palette.text),
    background = ColorProvider(palette.background),
    onBackground = ColorProvider(palette.text),
    surface = ColorProvider(palette.surface),
    onSurface = ColorProvider(palette.text),
    surfaceVariant = ColorProvider(palette.track),
    onSurfaceVariant = ColorProvider(palette.muted),
    outline = ColorProvider(palette.muted),
    inverseOnSurface = ColorProvider(palette.background),
    inverseSurface = ColorProvider(palette.text),
    inversePrimary = ColorProvider(palette.primary),
    widgetBackground = ColorProvider(palette.background),
)

private fun readSnapshot(context: Context): HomelabSnapshot? = SnapshotStore(context).read()

private fun readPalette(context: Context): WidgetPalette = widgetPalette(
    SecureSettingsStore(context).load().accentTheme,
)

private data class WidgetSize(val width: Dp, val height: Dp) {
    val isCompact: Boolean
        get() = width < 160.dp || height < 92.dp

    val isExpanded: Boolean
        get() = width >= 230.dp && height >= 145.dp

    val maxContentItems: Int
        get() = when {
            isExpanded -> 4
            isCompact -> 1
            else -> 2
        }
}

@Composable
private fun currentWidgetSize(): WidgetSize = WidgetSize(LocalSize.current.width, LocalSize.current.height)

@Composable
private fun titleStyle(compact: Boolean = false) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = if (compact) 13.sp else 15.sp,
    fontWeight = FontWeight.Bold,
)

@Composable
private fun valueStyle(compact: Boolean = false) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = if (compact) 12.sp else 14.sp,
)

@Composable
private fun strongValueStyle(compact: Boolean = false) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = if (compact) 15.sp else 18.sp,
    fontWeight = FontWeight.Bold,
)

@Composable
private fun captionStyle() = TextStyle(
    color = GlanceTheme.colors.onSurfaceVariant,
    fontSize = 10.sp,
)

@Composable
private fun WidgetShell(context: Context, content: @Composable (WidgetSize) -> Unit) {
    val palette = readPalette(context)
    GlanceTheme(colors = widgetColors(palette)) {
        val size = currentWidgetSize()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(if (size.isCompact) 16.dp else 20.dp)
                .padding(if (size.isCompact) 9.dp else 13.dp),
        ) {
            content(size)
        }
    }
}

@Composable
private fun EmptyWidget(size: WidgetSize) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sem conexão", style = titleStyle(size.isCompact))
        Text(
            "Abra o app e conecte um homelab",
            style = captionStyle(),
            maxLines = 2,
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, size: WidgetSize) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = GlanceModifier.defaultWeight(), style = captionStyle(), maxLines = 1)
        Text(value, style = valueStyle(size.isCompact), maxLines = 1)
    }
}

@Composable
private fun StatusPill(online: Boolean, compact: Boolean) {
    val background = if (online) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.errorContainer
    val foreground = if (online) GlanceTheme.colors.primary else GlanceTheme.colors.error
    Box(
        modifier = GlanceModifier
            .background(background)
            .cornerRadius(13.dp)
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 5.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

private fun donutBitmap(context: Context, fraction: Float, accent: Color, track: Color, sizeDp: Int): Bitmap {
    val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = sizePx * 0.16f
    val inset = stroke / 2f + sizePx * 0.03f
    val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = track.toArgb()
    canvas.drawArc(bounds, -90f, 360f, false, paint)
    if (fraction > 0f) {
        paint.color = accent.toArgb()
        canvas.drawArc(bounds, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
    }
    return bitmap
}

private fun thermometerBitmap(context: Context, accent: Color, sizeDp: Int): Bitmap {
    val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = sizePx / 2f
    val bulbRadius = sizePx * 0.18f
    val tubeWidth = sizePx * 0.18f
    val tubeTop = sizePx * 0.16f
    val tubeBottom = sizePx * 0.72f
    val tube = RectF(centerX - tubeWidth / 2f, tubeTop, centerX + tubeWidth / 2f, tubeBottom)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9CA6B0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.06f
    }
    canvas.drawRoundRect(tube, tubeWidth / 2f, tubeWidth / 2f, outline)
    canvas.drawCircle(centerX, sizePx * 0.78f, bulbRadius + outline.strokeWidth / 2f, outline)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent.toArgb()
        style = Paint.Style.FILL
    }
    val fillTube = RectF(centerX - tubeWidth * 0.28f, sizePx * 0.43f, centerX + tubeWidth * 0.28f, tubeBottom)
    canvas.drawRoundRect(fillTube, tubeWidth * 0.28f, tubeWidth * 0.28f, fill)
    canvas.drawCircle(centerX, sizePx * 0.78f, bulbRadius, fill)
    return bitmap
}

@Composable
private fun RowScope.DonutMetric(
    context: Context,
    label: String,
    percent: Double,
    accent: Color,
    compact: Boolean,
) {
    val chartSize = if (compact) 47.dp else 62.dp
    Column(
        modifier = GlanceModifier.defaultWeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(GlanceModifier.size(chartSize), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(
                    donutBitmap(context, (percent / 100.0).toFloat(), accent, GlanceTheme.colors.surfaceVariant.getColor(context), if (compact) 48 else 64),
                ),
                contentDescription = "$label ${formatPercent(percent)}",
                modifier = GlanceModifier.size(chartSize),
            )
            Text(formatPercent(percent), style = strongValueStyle(compact))
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(label, style = captionStyle(), maxLines = 1)
    }
}

@Composable
private fun StorageBar(
    volumeName: String,
    usage: Float,
    size: WidgetSize,
) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            shortLabel(volumeName, if (size.isCompact) 12 else 22),
            modifier = GlanceModifier.defaultWeight(),
            style = captionStyle(),
            maxLines = 1,
        )
        Text(formatPercent((usage * 100).toDouble()), style = valueStyle(size.isCompact), maxLines = 1)
    }
    LinearProgressIndicator(
        progress = usage.coerceIn(0f, 1f),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 5.dp)
            .height(if (size.isCompact) 5.dp else 7.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
}

@Composable
private fun RowScope.SensorMetric(context: Context, group: SensorGroup, size: WidgetSize) {
    val value = group.value
    val accent = when {
        value == null -> GlanceTheme.colors.onSurfaceVariant.getColor(context)
        value >= 80 -> GlanceTheme.colors.error.getColor(context)
        value >= 65 -> GlanceTheme.colors.tertiary.getColor(context)
        else -> GlanceTheme.colors.primary.getColor(context)
    }
    Column(
        modifier = GlanceModifier.defaultWeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(thermometerBitmap(context, accent, if (size.isCompact) 25 else 30)),
            contentDescription = "Temperatura ${group.name}",
            modifier = GlanceModifier.size(if (size.isCompact) 25.dp else 30.dp),
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(formatTemperature(value, group.unit), style = strongValueStyle(size.isCompact), maxLines = 1)
        Text(group.name, style = captionStyle(), maxLines = 1)
        if (!size.isCompact && group.readingCount > 1) {
            Text("${group.readingCount} leituras", style = captionStyle(), maxLines = 1)
        }
    }
}

@Composable
private fun ContainerLine(item: ContainerItem, size: WidgetSize) {
    val status = listOfNotNull(containerStateLabel(item.state), item.health).joinToString(" · ")
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            GlanceModifier
                .size(7.dp)
                .background(containerColor(status))
                .cornerRadius(7.dp),
        )
        Text(shortLabel(item.name, 18), modifier = GlanceModifier.defaultWeight().padding(start = 6.dp), style = captionStyle(), maxLines = 1)
        Text(status, style = captionStyle(), maxLines = 1)
    }
}

private fun shortLabel(value: String, maxLength: Int): String {
    if (value.length <= maxLength) return value
    return "…" + value.takeLast(maxLength - 1)
}

private fun containerStateLabel(state: String): String = when (state.lowercase()) {
    "running" -> "ativo"
    "exited", "created", "paused" -> "parado"
    "dead" -> "erro"
    else -> state
}

@Composable
private fun containerColor(status: String): androidx.compose.ui.graphics.Color = when {
    status.contains("erro", ignoreCase = true) || status.contains("unhealthy", ignoreCase = true) -> GlanceTheme.colors.error.getColor(androidx.glance.LocalContext.current)
    status.contains("ativo", ignoreCase = true) || status.contains("healthy", ignoreCase = true) -> GlanceTheme.colors.primary.getColor(androidx.glance.LocalContext.current)
    else -> GlanceTheme.colors.tertiary.getColor(androidx.glance.LocalContext.current)
}

object StatusWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell(context) { size ->
                if (snapshot == null) {
                    EmptyWidget(size)
                } else {
                    StatusPill(snapshot.online, size.isCompact)
                    Spacer(GlanceModifier.height(8.dp))
                    MetricRow("Sincronizado", formatObservedAt(snapshot.observedAtEpochMs), size)
                    if (!size.isCompact) {
                        Spacer(GlanceModifier.height(4.dp))
                        MetricRow("Uptime", formatDuration(snapshot.uptimeSeconds), size)
                    }
                }
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
        val palette = readPalette(context)
        provideContent {
            WidgetShell(context) { size ->
                if (snapshot == null) {
                    EmptyWidget(size)
                } else {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        DonutMetric(context, "CPU", snapshot.cpu.usagePercent, palette.primary, size.isCompact)
                        Spacer(GlanceModifier.width(if (size.isCompact) 4.dp else 10.dp))
                        DonutMetric(context, "RAM", snapshot.ramUsagePercent() * 100.0, palette.secondary, size.isCompact)
                    }
                    if (size.isExpanded) {
                        Spacer(GlanceModifier.height(8.dp))
                        snapshot.cpu.load1m?.let { MetricRow("Load 1m", "%.2f".format(it), size) }
                        MetricRow("Memória", formatBytes(snapshot.memory.usedBytes), size)
                    }
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
            WidgetShell(context) { size ->
                if (snapshot == null) {
                    EmptyWidget(size)
                } else if (snapshot.volumes.isEmpty()) {
                    EmptyWidget(size)
                } else {
                    snapshot.volumes.take(size.maxContentItems).forEachIndexed { index, volume ->
                        if (index > 0) Spacer(GlanceModifier.height(if (size.isCompact) 6.dp else 10.dp))
                        StorageBar(volume.name, volume.usagePercent(), size)
                    }
                    if (snapshot.volumes.size > size.maxContentItems) {
                        Spacer(GlanceModifier.height(5.dp))
                        Text("+${snapshot.volumes.size - size.maxContentItems} volumes", style = captionStyle(), maxLines = 1)
                    }
                }
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
            WidgetShell(context) { size ->
                val groups = snapshot?.sensorGroups().orEmpty()
                if (groups.isEmpty()) {
                    EmptyWidget(size)
                } else {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        groups.take(if (size.isExpanded) 3 else 2).forEach { group ->
                            SensorMetric(context, group, size)
                            Spacer(GlanceModifier.width(if (size.isCompact) 3.dp else 7.dp))
                        }
                    }
                    if (!size.isCompact && groups.size > 3) {
                        Spacer(GlanceModifier.height(5.dp))
                        Text("+${groups.size - 3} grupos", style = captionStyle(), maxLines = 1)
                    }
                }
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
            WidgetShell(context) { size ->
                if (snapshot == null) {
                    EmptyWidget(size)
                } else {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetCount("Ativos", snapshot.containers.running, GlanceTheme.colors.primary)
                        Spacer(GlanceModifier.width(5.dp))
                        WidgetCount("Parados", snapshot.containers.stopped, GlanceTheme.colors.tertiary)
                        Spacer(GlanceModifier.width(5.dp))
                        WidgetCount("Erro", snapshot.containers.error, GlanceTheme.colors.error)
                    }
                    if (size.isExpanded && snapshot.containers.items.isNotEmpty()) {
                        Spacer(GlanceModifier.height(9.dp))
                        snapshot.containers.items.take(3).forEach { ContainerLine(it, size) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.WidgetCount(label: String, value: Int, color: ColorProvider) {
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = TextStyle(color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold))
        Text(label, style = captionStyle(), maxLines = 1)
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
            WidgetShell(context) { size ->
                if (snapshot == null) {
                    EmptyWidget(size)
                } else if (!snapshot.immich.enabled) {
                    Text("Immich desativado", style = titleStyle(size.isCompact))
                    Text("Habilite no agente para monitorar", style = captionStyle(), maxLines = 2)
                } else {
                    StatusMetric("Servidor", snapshot.immich.server, size)
                    Spacer(GlanceModifier.height(6.dp))
                    StatusMetric("Banco", snapshot.immich.database, size)
                    if (size.isExpanded) {
                        snapshot.immich.version?.let {
                            Spacer(GlanceModifier.height(6.dp))
                            MetricRow("Versão", it, size)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String, size: WidgetSize) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            GlanceModifier
                .size(8.dp)
                .background(statusColor(value))
                .cornerRadius(8.dp),
        )
        Text(label, modifier = GlanceModifier.defaultWeight().padding(start = 7.dp), style = captionStyle(), maxLines = 1)
        Text(value, style = valueStyle(size.isCompact), maxLines = 1)
    }
}

@Composable
private fun statusColor(value: String): Color = when (value.lowercase()) {
    "healthy", "ok", "online", "running" -> GlanceTheme.colors.primary.getColor(androidx.glance.LocalContext.current)
    "starting", "attention", "warning" -> GlanceTheme.colors.tertiary.getColor(androidx.glance.LocalContext.current)
    "offline", "unhealthy", "error", "dead" -> GlanceTheme.colors.error.getColor(androidx.glance.LocalContext.current)
    else -> GlanceTheme.colors.onSurfaceVariant.getColor(androidx.glance.LocalContext.current)
}

class ImmichWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ImmichWidget
}

object SummaryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell(context) { size ->
                if (snapshot == null) {
                    EmptyWidget(size)
                } else {
                    if (size.isCompact) {
                        StatusPill(snapshot.online, compact = true)
                        Spacer(GlanceModifier.height(5.dp))
                        MetricRow("Atualizado", formatObservedAt(snapshot.observedAtEpochMs), size)
                    } else {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                snapshot.host,
                                modifier = GlanceModifier.defaultWeight(),
                                style = titleStyle(compact = !size.isExpanded),
                                maxLines = 1,
                            )
                            StatusPill(snapshot.online, compact = !size.isExpanded)
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        if (size.isExpanded || size.height >= 120.dp) {
                            MetricRow("CPU", formatPercent(snapshot.cpu.usagePercent), size)
                            MetricRow("RAM", formatBytes(snapshot.memory.usedBytes), size)
                        }
                        MetricRow("Atualizado", formatObservedAt(snapshot.observedAtEpochMs), size)
                        if (size.isExpanded) {
                            MetricRow("Docker", "${snapshot.containers.running} ativos", size)
                            if (snapshot.immich.enabled) {
                                MetricRow("Immich", snapshot.immich.server, size)
                            }
                            MetricRow("Uptime", formatDuration(snapshot.uptimeSeconds), size)
                        }
                    }
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
