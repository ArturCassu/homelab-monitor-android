package com.example.homelabmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.homelabmonitor.data.repository.EndpointConfig

@Composable
fun SetupScreen(
    initialEndpoint: String,
    initialToken: String,
    isConnecting: Boolean,
    error: String?,
    onConnect: (String, String) -> Unit,
    onDemo: () -> Unit,
) {
    var endpoint by remember { mutableStateOf(initialEndpoint) }
    var token by remember { mutableStateOf(initialToken) }
    val parsedEndpoint = remember(endpoint) { EndpointConfig.parse(endpoint).getOrNull() }
    val canConnect = parsedEndpoint != null && token.trim().isNotEmpty() && !isConnecting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("HOMELAB MONITOR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Seu homelab, num relance.", style = MaterialTheme.typography.displaySmall)
            Text(
                "Conecte o app ao agente do servidor. O endereço fica salvo com segurança e pode ser trocado depois.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Conectar ao homelab", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "O Tailscale precisa estar conectado no celular. Em HTTP, a porta é opcional e usa 8099; em HTTPS, usa 443.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Host ou URL do monitor") },
                        placeholder = { Text("homelab ou http://100.x.y.z:8099") },
                        supportingText = {
                            Text(
                                parsedEndpoint?.let { "Será usado: ${it.baseUrl}" }
                                    ?: "Aceita nome DNS, IP Tailscale e porta opcional.",
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
                        label = { Text("Token do agente") },
                        supportingText = { Text("O token fica cifrado no Android Keystore.") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    error?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = { onConnect(endpoint, token) },
                        enabled = canConnect,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("Testando conexão…")
                        } else {
                            Text("Testar e entrar")
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  ou  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
            TextButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
                Text("Explorar com dados de demonstração")
            }
            Text(
                "A demonstração não acessa o servidor e pode ser trocada por uma conexão real nas configurações.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
