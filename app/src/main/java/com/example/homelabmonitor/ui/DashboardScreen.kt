package com.example.homelabmonitor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.homelabmonitor.data.model.HealthState
import com.example.homelabmonitor.data.model.MonitorUiState
import com.example.homelabmonitor.data.model.formatBytes
import com.example.homelabmonitor.data.model.formatDuration
import com.example.homelabmonitor.data.model.formatObservedAt
import com.example.homelabmonitor.data.model.formatPercent
import com.example.homelabmonitor.data.model.healthState
import com.example.homelabmonitor.data.model.label
import com.example.homelabmonitor.data.model.ramUsagePercent
import com.example.homelabmonitor.data.model.usagePercent
import com.example.homelabmonitor.update.AppUpdateState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(
    state: MonitorUiState,
    updateState: AppUpdateState,
    onRefresh: () -> Unit,
    onSaveSettings: (endpoint: String, token: String, useMockData: Boolean) -> Unit,
    onChangeServer: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Homelab Monitor", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.settings.useMockData) "modo demonstração" else "monitoramento privado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Text(if (state.isRefreshing) "Atualizando…" else "Atualizar")
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroCard(state, onRefresh) }
            item { SignalStrip(state) }
            item { SummaryCard(state) }
            item { CpuMemoryCard(state) }
            item { StorageCard(state) }
            item { SensorsCard(state) }
            item { ContainersCard(state) }
            if (state.snapshot.immich.enabled) item { ImmichCard(state) }
            item { AppUpdateCard(updateState, onCheckForUpdate, onInstallUpdate) }
            item { SettingsCard(state, onSaveSettings, onChangeServer) }
        }
    }
}

@Composable
private fun HeroCard(state: MonitorUiState, onRefresh: () -> Unit) {
    val health = state.snapshot.healthState(state.lastError)
    val accent by animateColorAsState(healthColor(health), label = "health accent")
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PAINEL DE BORDO", style = MaterialTheme.typography.labelMedium, color = accent)
                    Spacer(Modifier.height(4.dp))
                    Text(state.snapshot.host, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (state.settings.useMockData) "Dados de demonstração" else "Conectado pelo Tailscale",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HealthBadge(health)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat("Uptime", formatDuration(state.snapshot.uptimeSeconds))
                HeroStat("Atualizado", formatObservedAt(state.snapshot.observedAtEpochMs))
            }
            state.lastError?.let {
                Text("Última coleta: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onRefresh, enabled = !state.isRefreshing, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isRefreshing) "Sincronizando…" else "Sincronizar agora")
            }
        }
    }
}

@Composable
private fun SignalStrip(state: MonitorUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SignalChip("CPU", formatPercent(state.snapshot.cpu.usagePercent), Modifier.weight(1f))
        SignalChip("RAM", formatPercent((state.snapshot.ramUsagePercent() * 100).toDouble()), Modifier.weight(1f))
        SignalChip("Docker", "${state.snapshot.containers.running} ativos", Modifier.weight(1f))
    }
}

@Composable
private fun SignalChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HealthBadge(health: HealthState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = healthColor(health).copy(alpha = 0.16f)),
    ) {
        Text(
            health.label(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = healthColor(health),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SummaryCard(state: MonitorUiState) {
    val snapshot = state.snapshot
    MonitorCard {
        CardTitle("Resumo geral", "O que merece atenção neste momento")
        SummaryRow("Estado", snapshot.healthState(state.lastError).label())
        SummaryRow("CPU", formatPercent(snapshot.cpu.usagePercent))
        SummaryRow("RAM", "${formatBytes(snapshot.memory.usedBytes)} usados")
        SummaryRow("Containers", "${snapshot.containers.running} ativos · ${snapshot.containers.error} em erro")
        if (snapshot.immich.enabled) SummaryRow("Immich", "${snapshot.immich.server} · DB ${snapshot.immich.database}")
    }
}

@Composable
private fun CpuMemoryCard(state: MonitorUiState) {
    val snapshot = state.snapshot
    MonitorCard {
        CardTitle("CPU e RAM", "Uso atual dos recursos")
        MetricProgress("CPU", snapshot.cpu.usagePercent.toFloat() / 100f, formatPercent(snapshot.cpu.usagePercent))
        Spacer(Modifier.height(12.dp))
        MetricProgress("RAM", snapshot.ramUsagePercent(), "${formatBytes(snapshot.memory.usedBytes)} / ${formatBytes(snapshot.memory.totalBytes)}")
        snapshot.cpu.load1m?.let { load ->
            Spacer(Modifier.height(8.dp))
            Text("Load de 1 minuto: %.2f".format(load), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StorageCard(state: MonitorUiState) {
    MonitorCard {
        CardTitle("Armazenamento", "Barras proporcionais ao uso de cada volume")
        if (state.snapshot.volumes.isEmpty()) {
            EmptyHint("Nenhum volume foi informado pelo agente.")
        } else {
            state.snapshot.volumes.forEach { volume ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(volume.name, style = MaterialTheme.typography.titleSmall)
                        Text(formatPercent((volume.usagePercent() * 100).toDouble()), style = MaterialTheme.typography.labelLarge)
                    }
                    LinearProgressIndicator(
                        progress = { volume.usagePercent() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    Text(
                        "${formatBytes(volume.freeBytes)} livres de ${formatBytes(volume.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SensorsCard(state: MonitorUiState) {
    MonitorCard {
        CardTitle("Temperatura e sensores", "Leituras disponíveis no host")
        if (state.snapshot.sensors.isEmpty()) {
            EmptyHint("Nenhum sensor disponível.")
        } else {
            state.snapshot.sensors.forEach { sensor ->
                SummaryRow(sensor.name, if (sensor.available && sensor.value != null) "${sensor.value} ${sensor.unit}" else "indisponível")
            }
        }
    }
}

@Composable
private fun ContainersCard(state: MonitorUiState) {
    val containers = state.snapshot.containers
    MonitorCard {
        CardTitle("Containers Docker", "Saúde do conjunto de serviços")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalChip("Ativos", containers.running.toString(), Modifier.weight(1f))
            SignalChip("Parados", containers.stopped.toString(), Modifier.weight(1f))
            SignalChip("Erro", containers.error.toString(), Modifier.weight(1f))
        }
        if (containers.items.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            containers.items.take(8).forEach { item ->
                SummaryRow(item.name, listOfNotNull(item.state, item.health).joinToString(" · "))
            }
        }
    }
}

@Composable
private fun ImmichCard(state: MonitorUiState) {
    val immich = state.snapshot.immich
    MonitorCard {
        CardTitle("Immich", "Recurso opcional do agente")
        SummaryRow("Servidor", immich.server)
        SummaryRow("Banco de dados", immich.database)
        immich.version?.let { SummaryRow("Versão", it) }
    }
}

@Composable
private fun AppUpdateCard(
    state: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    MonitorCard {
        CardTitle("Atualizações", "Mantenha o app em dia sem acumular APKs")
        Text("Versão instalada: ${state.currentVersionName} (${state.currentVersionCode})", style = MaterialTheme.typography.bodyMedium)
        state.available?.let { manifest ->
            Spacer(Modifier.height(8.dp))
            Text("Nova versão: ${manifest.versionName}", style = MaterialTheme.typography.titleSmall)
            manifest.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onInstallUpdate, enabled = !state.isDownloading, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isDownloading) "Baixando…" else "Baixar e instalar")
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = onCheckForUpdate, enabled = !state.isChecking && !state.isDownloading, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isChecking) "Verificando…" else "Verificar atualizações")
        }
    }
}

@Composable
private fun SettingsCard(
    state: MonitorUiState,
    onSaveSettings: (endpoint: String, token: String, useMockData: Boolean) -> Unit,
    onChangeServer: () -> Unit,
) {
    var endpoint by remember(state.settings.endpoint) { mutableStateOf(state.settings.endpoint) }
    var token by remember(state.settings.token) { mutableStateOf(state.settings.token) }
    var useMockData by remember(state.settings.useMockData) { mutableStateOf(state.settings.useMockData) }

    MonitorCard {
        CardTitle("Configurações", "Servidor, segurança e modo de dados")
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Host ou URL do monitor") },
            supportingText = { Text("HTTP sem porta usa 8099; HTTPS sem porta usa 443.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dados de demonstração", style = MaterialTheme.typography.titleSmall)
                Text("Não acessa o homelab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = useMockData, onCheckedChange = { useMockData = it })
        }
        Button(onClick = { onSaveSettings(endpoint, token, useMockData) }, modifier = Modifier.fillMaxWidth()) {
            Text("Salvar e sincronizar")
        }
        OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
            Text("Trocar homelab")
        }
        Text(
            "Endpoint e token ficam cifrados no Android Keystore. O app nunca recebe o Docker socket.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonitorCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricProgress(label: String, progress: Float, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
}

@Composable
private fun EmptyHint(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun healthColor(health: HealthState): Color = when (health) {
    HealthState.HEALTHY -> Color(0xFF18794E)
    HealthState.WARNING -> Color(0xFF9A6700)
    HealthState.ERROR -> Color(0xFFBA1A1A)
    HealthState.UNKNOWN -> Color(0xFF5F6368)
}
