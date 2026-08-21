package com.example.homelabmonitor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.appwidget.updateAll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.homelabmonitor.data.model.HomelabSnapshot
import com.example.homelabmonitor.data.model.MockSnapshotFactory
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

private fun titleStyle() = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)

private fun valueStyle() = TextStyle(fontSize = 14.sp)

@Composable
private fun WidgetShell(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Text(title, style = titleStyle())
        Spacer(GlanceModifier.height(6.dp))
        content()
    }
}

@Composable
private fun WidgetRow(label: String, value: String) {
    Row(GlanceModifier.fillMaxWidth()) {
        Text(label, style = valueStyle())
        Spacer(GlanceModifier.width(8.dp))
        Text(value, style = valueStyle())
    }
}

object StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Status geral") {
                Text(if (snapshot.online) "ONLINE" else "OFFLINE", style = titleStyle())
                WidgetRow("Uptime", formatDuration(snapshot.uptimeSeconds))
                WidgetRow("Atualizado", formatObservedAt(snapshot.observedAtEpochMs))
            }
        }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget
}

object ResourcesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("CPU e RAM") {
                WidgetRow("CPU", formatPercent(snapshot.cpu.usagePercent))
                WidgetRow("RAM", "${formatPercent((snapshot.ramUsagePercent() * 100).toDouble())} · ${formatBytes(snapshot.memory.usedBytes)}")
                snapshot.cpu.load1m?.let { WidgetRow("Load 1m", "%.2f".format(it)) }
            }
        }
    }
}

class ResourcesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResourcesWidget
}

object StorageWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Armazenamento") {
                snapshot.volumes.take(3).forEach { volume ->
                    WidgetRow(volume.name, "${formatPercent((volume.usagePercent() * 100).toDouble())} usado")
                }
                if (snapshot.volumes.isEmpty()) Text("Sem volumes")
            }
        }
    }
}

class StorageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StorageWidget
}

object SensorsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Sensores") {
                snapshot.sensors.take(3).forEach { sensor ->
                    WidgetRow(sensor.name, if (sensor.value == null) "--" else "${sensor.value} ${sensor.unit}")
                }
                if (snapshot.sensors.isEmpty()) Text("Sem sensores")
            }
        }
    }
}

class SensorsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SensorsWidget
}

object ContainersWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Docker") {
                WidgetRow("Ativos", snapshot.containers.running.toString())
                WidgetRow("Parados", snapshot.containers.stopped.toString())
                WidgetRow("Erro", snapshot.containers.error.toString())
            }
        }
    }
}

class ContainersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContainersWidget
}

object ImmichWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Immich") {
                WidgetRow("Servidor", snapshot.immich.server)
                WidgetRow("Banco", snapshot.immich.database)
                snapshot.immich.version?.let { WidgetRow("Versão", it) }
            }
        }
    }
}

class ImmichWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ImmichWidget
}

object SummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        provideContent {
            WidgetShell("Resumo · ${snapshot.host}") {
                WidgetRow("Estado", snapshot.healthState().label())
                WidgetRow("CPU", formatPercent(snapshot.cpu.usagePercent))
                WidgetRow("RAM", formatBytes(snapshot.memory.usedBytes))
                WidgetRow("Docker", "${snapshot.containers.running} ativos")
                WidgetRow("Immich", snapshot.immich.server)
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
