package com.example.homelabmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(
    state: MonitorUiState,
    onRefresh: () -> Unit,
    onSaveSettings: (endpoint: String, token: String, useMockData: Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Homelab Monitor") },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Text(if (state.isRefreshing) "Atualizando…" else "Atualizar")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderCard(state)
            SummaryCard(state)
            CpuMemoryCard(state)
            StorageCard(state)
            SensorsCard(state)
            ContainersCard(state)
            ImmichCard(state)
            SettingsCard(state, onSaveSettings)
        }
    }
}

@Composable
private fun HeaderCard(state: MonitorUiState) {
    val health = state.snapshot.healthState(state.lastError)
    MonitorCard {
        Text(state.snapshot.host, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusText(health)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.settings.useMockData) "dados mockados" else "API Tailscale",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Última atualização: ${formatObservedAt(state.snapshot.observedAtEpochMs)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        state.lastError?.let { error ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Falha na coleta: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SummaryCard(state: MonitorUiState) {
    val snapshot = state.snapshot
    val health = snapshot.healthState(state.lastError)
    MonitorCard {
        CardTitle("Resumo geral")
        SummaryRow("Estado", health.label())
        SummaryRow("Uptime", formatDuration(snapshot.uptimeSeconds))
        SummaryRow("CPU", formatPercent(snapshot.cpu.usagePercent))
        SummaryRow("RAM", "${formatBytes(snapshot.memory.usedBytes)} / ${formatBytes(snapshot.memory.totalBytes)}")
        SummaryRow("Containers", "${snapshot.containers.running} ativos · ${snapshot.containers.error} erro")
        SummaryRow("Immich", "${snapshot.immich.server} / DB ${snapshot.immich.database}")
    }
}

@Composable
private fun CpuMemoryCard(state: MonitorUiState) {
    val snapshot = state.snapshot
    MonitorCard {
        CardTitle("CPU e RAM")
        MetricProgress("CPU", snapshot.cpu.usagePercent.toFloat() / 100f, formatPercent(snapshot.cpu.usagePercent))
        Spacer(Modifier.height(10.dp))
        MetricProgress(
            "RAM",
            snapshot.ramUsagePercent(),
            "${formatBytes(snapshot.memory.usedBytes)} / ${formatBytes(snapshot.memory.totalBytes)}",
        )
        snapshot.cpu.load1m?.let { load ->
            Spacer(Modifier.height(6.dp))
            Text("Load 1m: %.2f".format(load), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StorageCard(state: MonitorUiState) {
    MonitorCard {
        CardTitle("Armazenamento por volume")
        if (state.snapshot.volumes.isEmpty()) {
            Text("Nenhum volume informado.")
        } else {
            state.snapshot.volumes.forEach { volume ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(volume.name, style = MaterialTheme.typography.titleSmall)
                        Text("${formatBytes(volume.usedBytes)} usados", style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { volume.usagePercent() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        "${formatBytes(volume.freeBytes)} livres de ${formatBytes(volume.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SensorsCard(state: MonitorUiState) {
    MonitorCard {
        CardTitle("Temperatura e sensores")
        if (state.snapshot.sensors.isEmpty()) {
            Text("Nenhum sensor disponível.")
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
        CardTitle("Containers Docker")
        SummaryRow("Ativos", containers.running.toString())
        SummaryRow("Parados", containers.stopped.toString())
        SummaryRow("Com erro", containers.error.toString())
        if (containers.items.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            containers.items.take(5).forEach { item ->
                SummaryRow(item.name, listOfNotNull(item.state, item.health).joinToString(" · "))
            }
        }
    }
}

@Composable
private fun ImmichCard(state: MonitorUiState) {
    val immich = state.snapshot.immich
    MonitorCard {
        CardTitle("Immich")
        SummaryRow("Servidor", immich.server)
        SummaryRow("Banco de dados", immich.database)
        immich.version?.let { SummaryRow("Versão", it) }
    }
}

@Composable
private fun SettingsCard(
    state: MonitorUiState,
    onSaveSettings: (endpoint: String, token: String, useMockData: Boolean) -> Unit,
) {
    // Do not use rememberSaveable here: endpoint/token must not enter Activity saved state.
    var endpoint by remember { mutableStateOf(state.settings.endpoint) }
    var token by remember { mutableStateOf(state.settings.token) }
    var useMockData by remember { mutableStateOf(state.settings.useMockData) }

    MonitorCard {
        CardTitle("Configuração")
        Text(
            "O token é cifrado no Android Keystore. Use um endereço alcançável pelo Tailscale; o app nunca acessa o Docker socket.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endpoint base") },
            placeholder = { Text("http://homelab:8099") },
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useMockData, onCheckedChange = { useMockData = it })
            Text("Usar dados mockados (MVP)")
        }
        Button(
            onClick = { onSaveSettings(endpoint, token, useMockData) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar e atualizar")
        }
    }
}

@Composable
private fun MonitorCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricProgress(label: String, progress: Float, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@Composable
private fun StatusText(health: HealthState) {
    Text(
        text = health.label(),
        style = MaterialTheme.typography.labelLarge,
        color = when (health) {
            HealthState.HEALTHY -> MaterialTheme.colorScheme.primary
            HealthState.WARNING -> MaterialTheme.colorScheme.tertiary
            HealthState.ERROR -> MaterialTheme.colorScheme.error
            HealthState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
