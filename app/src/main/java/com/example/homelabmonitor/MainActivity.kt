package com.example.homelabmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.homelabmonitor.ui.DashboardScreen
import com.example.homelabmonitor.ui.MainViewModel
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
                    DashboardScreen(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onSaveSettings = viewModel::saveSettings,
                    )
                }
            }
        }
    }
}
