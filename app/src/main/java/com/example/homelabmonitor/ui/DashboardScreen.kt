package com.example.homelabmonitor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.homelabmonitor.data.model.AccentTheme
import com.example.homelabmonitor.data.model.HealthState
import com.example.homelabmonitor.data.model.MonitorUiState
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
import com.example.homelabmonitor.data.repository.EndpointConfig
import com.example.homelabmonitor.update.AppUpdateState

private enum class DashboardTab(val title: String) {
    OVERVIEW("Visão geral"),
    METRICS("Métricas"),
    SETTINGS("Configurações"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(
    state: MonitorUiState,
    updateState: AppUpdateState,
    onRefresh: () -> Unit,
    onSaveSettings: (endpoint: String, token: String) -> Unit,
    onAccentThemeChange: (AccentTheme) -> Unit,
    onChangeServer: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.OVERVIEW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Homelab Monitor", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${state.snapshot.host} · Tailscale",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                DashboardTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                    )
                }
            }
            when (selectedTab) {
                DashboardTab.OVERVIEW -> OverviewTab(state, onRefresh)
                DashboardTab.METRICS -> MetricsTab(state)
                DashboardTab.SETTINGS -> SettingsTab(
                    state = state,
                    updateState = updateState,
                    onSaveSettings = onSaveSettings,
                    onAccentThemeChange = onAccentThemeChange,
                    onChangeServer = onChangeServer,
                    onCheckForUpdate = onCheckForUpdate,
                    onInstallUpdate = onInstallUpdate,
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(state: MonitorUiState, onRefresh: () -> Unit) {
    DashboardList {
        item { OverviewHero(state, onRefresh) }
        item { QuickStats(state) }
        item { SectionHeading("Agora", "Os sinais mais importantes do host") }
        item {
            ResponsivePair(
                first = { ResourceSummaryCard(state) },
                second = { ServicesSummaryCard(state) },
            )
        }
        item {
            ResponsivePair(
                first = { StorageSummaryCard(state) },
                second = { SensorsSummaryCard(state) },
            )
        }
        state.lastError?.let { error ->
            item { ErrorBanner(error) }
        }
    }
}

@Composable
private fun MetricsTab(state: MonitorUiState) {
    DashboardList {
        item { SectionHeading("Recursos", "Uso atual e capacidade disponível") }
        item { ResourceDetailsCard(state) }
        item { StorageDetailsCard(state) }
        item { SensorDetailsCard(state) }
        item { ContainersDetailsCard(state) }
        if (state.snapshot.immich.enabled) item { ImmichDetailsCard(state) }
    }
}

@Composable
private fun SettingsTab(
    state: MonitorUiState,
    updateState: AppUpdateState,
    onSaveSettings: (endpoint: String, token: String) -> Unit,
    onAccentThemeChange: (AccentTheme) -> Unit,
    onChangeServer: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    DashboardList {
        item {
            ConnectionSettingsCard(
                state = state,
                onSaveSettings = onSaveSettings,
                onChangeServer = onChangeServer,
            )
        }
        item {
            AppearanceCard(
                selectedTheme = state.settings.accentTheme,
                onThemeChange = onAccentThemeChange,
            )
        }
        item { AppUpdateCard(updateState, onCheckForUpdate, onInstallUpdate) }
        item { SecurityCard() }
    }
}

@Composable
private fun DashboardList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun OverviewHero(state: MonitorUiState, onRefresh: () -> Unit) {
    val health = state.snapshot.healthState(state.lastError)
    val accent by animateColorAsState(healthColor(health), label = "health accent")
    MonitorCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("PAINEL DE BORDO", style = MaterialTheme.typography.labelMedium, color = accent)
                Spacer(Modifier.height(5.dp))
                Text(state.snapshot.host, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Monitoramento privado pelo Tailscale",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HealthBadge(health)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStat("Status", health.label(), Modifier.weight(1f))
            HeroStat("Sincronizado", formatObservedAt(state.snapshot.observedAtEpochMs), Modifier.weight(1f))
            HeroStat("Uptime", formatDuration(state.snapshot.uptimeSeconds), Modifier.weight(1f))
        }
        state.lastError?.let {
            Text(
                "Última tentativa: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = onRefresh, enabled = !state.isRefreshing, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isRefreshing) "Sincronizando…" else "Sincronizar agora")
        }
    }
}

@Composable
private fun QuickStats(state: MonitorUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickStat("CPU", formatPercent(state.snapshot.cpu.usagePercent), Modifier.weight(1f))
        QuickStat("RAM", formatPercent((state.snapshot.ramUsagePercent() * 100).toDouble()), Modifier.weight(1f))
        QuickStat("Docker", "${state.snapshot.containers.running} ativos", Modifier.weight(1f))
    }
}

@Composable
private fun QuickStat(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ResourceSummaryCard(state: MonitorUiState) {
    val snapshot = state.snapshot
    MonitorCard {
        CardTitle("Recursos", "Uso neste instante")
        ProgressRow("CPU", snapshot.cpu.usagePercent.toFloat() / 100f, formatPercent(snapshot.cpu.usagePercent))
        Spacer(Modifier.height(12.dp))
        ProgressRow(
            "RAM",
            snapshot.ramUsagePercent(),
            "${formatPercent((snapshot.ramUsagePercent() * 100).toDouble())} · ${formatBytes(snapshot.memory.usedBytes)}",
        )
    }
}

@Composable
private fun ServicesSummaryCard(state: MonitorUiState) {
    val containers = state.snapshot.containers
    MonitorCard {
        CardTitle("Serviços", "Docker e integrações")
        SummaryRow("Containers ativos", containers.running.toString())
        SummaryRow("Parados", containers.stopped.toString())
        SummaryRow("Com erro", containers.error.toString())
        if (state.snapshot.immich.enabled) {
            SummaryRow("Immich", state.snapshot.immich.server)
        }
    }
}

@Composable
private fun StorageSummaryCard(state: MonitorUiState) {
    MonitorCard {
        CardTitle("Armazenamento", "Uso por volume")
        if (state.snapshot.volumes.isEmpty()) {
            EmptyHint("Nenhum volume disponível.")
        } else {
            state.snapshot.volumes.take(2).forEach { volume ->
                ProgressRow(
                    label = volume.name,
                    progress = volume.usagePercent(),
                    value = formatPercent((volume.usagePercent() * 100).toDouble()),
                )
                Spacer(Modifier.height(10.dp))
            }
            if (state.snapshot.volumes.size > 2) {
                Text(
                    "+${state.snapshot.volumes.size - 2} volumes em Métricas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SensorsSummaryCard(state: MonitorUiState) {
    val groups = state.snapshot.sensorGroups()
    MonitorCard {
        CardTitle("Temperaturas", "Famílias de sensores agrupadas")
        if (groups.isEmpty()) {
            EmptyHint("Nenhum sensor disponível.")
        } else {
            groups.take(3).forEach { SensorSummaryRow(it) }
            if (groups.size > 3) {
                Text(
                    "+${groups.size - 3} grupos em Métricas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResourceDetailsCard(state: MonitorUiState) {
    val snapshot = state.snapshot
    MonitorCard {
        CardTitle("CPU e memória", "Percentual usado")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 330.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    MetricRing("CPU", snapshot.cpu.usagePercent.toFloat() / 100f)
                    Spacer(Modifier.height(16.dp))
                    MetricRing("RAM", snapshot.ramUsagePercent())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MetricRing("CPU", snapshot.cpu.usagePercent.toFloat() / 100f)
                    MetricRing("RAM", snapshot.ramUsagePercent())
                }
            }
        }
        snapshot.cpu.load1m?.let {
            Spacer(Modifier.height(12.dp))
            SummaryRow("Load de 1 minuto", "%.2f".format(it))
        }
    }
}

@Composable
private fun StorageDetailsCard(state: MonitorUiState) {
    MonitorCard {
        CardTitle("Armazenamento", "Capacidade usada e livre")
        if (state.snapshot.volumes.isEmpty()) {
            EmptyHint("Nenhum volume foi informado pelo agente.")
        } else {
            state.snapshot.volumes.forEach { volume ->
                Column {
                    ProgressRow(
                        label = volume.name,
                        progress = volume.usagePercent(),
                        value = formatPercent((volume.usagePercent() * 100).toDouble()),
                    )
                    Text(
                        "${formatBytes(volume.freeBytes)} livres de ${formatBytes(volume.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SensorDetailsCard(state: MonitorUiState) {
    val groups = state.snapshot.sensorGroups()
    MonitorCard {
        CardTitle("Temperaturas", "Leituras agrupadas por componente")
        if (groups.isEmpty()) {
            EmptyHint("Nenhum sensor disponível.")
        } else {
            groups.forEach { group ->
                SensorDetailRow(group)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ContainersDetailsCard(state: MonitorUiState) {
    val containers = state.snapshot.containers
    MonitorCard {
        CardTitle("Containers Docker", "Estado dos serviços monitorados")
        ResponsivePair(
            first = { QuickStat("Ativos", containers.running.toString(), Modifier.fillMaxWidth()) },
            second = { QuickStat("Parados", containers.stopped.toString(), Modifier.fillMaxWidth()) },
        )
        Spacer(Modifier.height(8.dp))
        QuickStat("Com erro", containers.error.toString(), Modifier.fillMaxWidth())
        if (containers.items.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            containers.items.take(12).forEach { item ->
                SummaryRow(item.name, listOfNotNull(containerStateLabel(item.state), item.health).joinToString(" · "))
            }
        }
    }
}

@Composable
private fun ImmichDetailsCard(state: MonitorUiState) {
    val immich = state.snapshot.immich
    MonitorCard {
        CardTitle("Immich", "Integração opcional do agente")
        StatusSummaryRow("Servidor", immich.server)
        StatusSummaryRow("Banco de dados", immich.database)
        immich.version?.let { SummaryRow("Versão", it) }
    }
}

@Composable
private fun ConnectionSettingsCard(
    state: MonitorUiState,
    onSaveSettings: (endpoint: String, token: String) -> Unit,
    onChangeServer: () -> Unit,
) {
    var endpoint by remember(state.settings.endpoint) { mutableStateOf(state.settings.endpoint) }
    var token by remember { mutableStateOf("") }
    val parsedEndpoint = remember(endpoint) { EndpointConfig.parse(endpoint).getOrNull() }
    val canSave = parsedEndpoint != null && !state.isConnecting

    MonitorCard {
        CardTitle("Conexão", "Altere o homelab sem perder os widgets")
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Host ou URL") },
            supportingText = {
                Text(
                    parsedEndpoint?.let { "Será usado: ${it.baseUrl}" }
                        ?: "HTTP sem porta usa 8099; HTTPS sem porta usa 443.",
                )
            },
            isError = endpoint.isNotBlank() && parsedEndpoint == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Novo token, se necessário") },
            placeholder = { Text("Deixe vazio para manter o token atual") },
            supportingText = { Text("O token existente nunca é exibido nem substituído por acidente.") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        state.setupError?.let { ErrorBanner(it) }
        Button(
            onClick = { onSaveSettings(endpoint, token) },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isConnecting) "Validando…" else "Salvar e sincronizar")
        }
        OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
            Text("Trocar homelab")
        }
    }
}

@Composable
private fun AppearanceCard(selectedTheme: AccentTheme, onThemeChange: (AccentTheme) -> Unit) {
    MonitorCard {
        CardTitle("Aparência", "A mesma paleta vale para o app e os widgets")
        AccentTheme.entries.chunked(2).forEach { rowThemes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowThemes.forEach { theme ->
                    FilterChip(
                        selected = selectedTheme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(theme.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            "Grafite é o padrão: fundo preto, contraste alto e acentos discretos. A escolha é salva no aparelho.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppUpdateCard(
    state: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    MonitorCard {
        CardTitle("Atualizações", "Uma cópia temporária por vez")
        Text("Versão instalada: ${state.currentVersionName} (${state.currentVersionCode})", style = MaterialTheme.typography.bodyMedium)
        state.available?.let { manifest ->
            Spacer(Modifier.height(6.dp))
            Text("Nova versão: ${manifest.versionName}", style = MaterialTheme.typography.titleSmall)
            manifest.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onInstallUpdate, enabled = !state.isDownloading, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isDownloading) "Baixando…" else "Baixar e instalar")
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(
            onClick = onCheckForUpdate,
            enabled = !state.isChecking && !state.isDownloading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isChecking) "Verificando…" else "Verificar atualizações")
        }
    }
}

@Composable
private fun SecurityCard() {
    MonitorCard {
        CardTitle("Privacidade", "Limites do monitor")
        Text(
            "O endpoint e o token ficam cifrados no Android Keystore. O app chama apenas GET /v1/metrics pelo Tailscale e nunca recebe o Docker socket.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricRing(label: String, progress: Float) {
    val normalized = progress.coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                drawArc(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * normalized,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                formatPercent((normalized * 100).toDouble()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SensorSummaryRow(group: SensorGroup) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(sensorColor(group.value))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(group.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (group.readingCount > 1) {
                Text(
                    "${group.readingCount} leituras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(formatTemperature(group.value, group.unit), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SensorDetailRow(group: SensorGroup) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val range = if (group.minimum != null && group.maximum != null && group.readingCount > 1) {
                "Faixa ${formatTemperature(group.minimum, group.unit)} – ${formatTemperature(group.maximum, group.unit)}"
            } else {
                "Uma leitura disponível"
            }
            Text(
                range,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatTemperature(group.value, group.unit),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgressRow(label: String, progress: Float, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.9f),
        )
    }
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

@Composable
private fun StatusSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(statusColor(value))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.46f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.54f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun HealthBadge(health: HealthState) {
    val color = healthColor(health)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color)
            Text(
                health.label(),
                modifier = Modifier.padding(start = 7.dp),
                color = color,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyHint(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MonitorCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
    }
}

@Composable
private fun ResponsivePair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first()
                second()
            }
        }
    }
}

private fun healthColor(health: HealthState): Color = when (health) {
    HealthState.HEALTHY -> Color(0xFF72D6A2)
    HealthState.WARNING -> Color(0xFFE8B96C)
    HealthState.ERROR -> Color(0xFFF18B8B)
    HealthState.UNKNOWN -> Color(0xFFA8B0BA)
}

private fun sensorColor(value: Double?): Color = when {
    value == null -> Color(0xFFA8B0BA)
    value >= 80 -> Color(0xFFF18B8B)
    value >= 65 -> Color(0xFFE8B96C)
    else -> Color(0xFF72D6A2)
}

private fun statusColor(value: String): Color = when (value.lowercase()) {
    "healthy", "ok", "online", "running" -> Color(0xFF72D6A2)
    "starting", "attention", "warning" -> Color(0xFFE8B96C)
    "offline", "unhealthy", "error", "dead" -> Color(0xFFF18B8B)
    else -> Color(0xFFA8B0BA)
}

private fun containerStateLabel(state: String): String = when (state.lowercase()) {
    "running" -> "ativo"
    "exited", "created", "paused" -> "parado"
    "dead" -> "erro"
    else -> state
}
