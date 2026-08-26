package com.example.homelabmonitor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.homelabmonitor.ui.DashboardScreen
import com.example.homelabmonitor.ui.MainViewModel
import com.example.homelabmonitor.ui.SetupScreen
import com.example.homelabmonitor.ui.theme.HomelabMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomelabMonitorTheme {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val state = viewModel.uiState.collectAsStateWithLifecycle().value
                    val updateState = viewModel.updateState.collectAsStateWithLifecycle().value
                    LaunchedEffect(updateState.installerUri) {
                        updateState.installerUri?.let { uri ->
                            runCatching {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                )
                            }.onFailure { error ->
                                viewModel.reportUpdateError(
                                    error.message ?: "Não foi possível abrir o instalador do Android.",
                                )
                            }
                            viewModel.clearInstallerUri()
                        }
                    }
                    if (!state.settings.setupComplete) {
                        SetupScreen(
                            initialEndpoint = state.settings.endpoint,
                            initialToken = state.settings.token,
                            isConnecting = state.isConnecting,
                            error = state.setupError,
                            onConnect = viewModel::connect,
                            onDemo = viewModel::enterDemo,
                        )
                    } else {
                        DashboardScreen(
                            state = state,
                            updateState = updateState,
                            onRefresh = viewModel::refresh,
                            onSaveSettings = viewModel::saveSettings,
                            onChangeServer = viewModel::changeServer,
                            onCheckForUpdate = viewModel::checkForAppUpdate,
                            onInstallUpdate = viewModel::downloadAndPrepareUpdate,
                        )
                    }
                }
            }
        }
    }
}
