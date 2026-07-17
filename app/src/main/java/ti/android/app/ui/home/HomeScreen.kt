package ti.android.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToInspector: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Ti Android Runtime", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Text("⚙", fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (uiState.connectionStatus) {
                                "connected" -> "●"
                                "connecting" -> "⟳"
                                else -> "○"
                            },
                            color = when (uiState.connectionStatus) {
                                "connected" -> MaterialTheme.colorScheme.primary
                                "connecting" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            },
                            fontSize = 16.sp
                        )
                        Text(
                            text = when (uiState.connectionStatus) {
                                "connected" -> "Connected to Gateway"
                                "connecting" -> "Connecting..."
                                "disconnected" -> "Disconnected"
                                else -> "Not configured"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Device: ${uiState.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Gateway: ${uiState.gatewayUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Device Info Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoCard("Accessibility", if (uiState.accessibilityEnabled) "✅ Enabled" else "❌ Disabled", Modifier.weight(1f))
                InfoCard("Battery", uiState.batteryLevel, Modifier.weight(1f))
            }

            // Task Status
            if (uiState.activeTask != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Task Running", fontWeight = FontWeight.Bold)
                        Text(
                            text = uiState.activeTask!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action Buttons
            Button(
                onClick = onNavigateToInspector,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Accessibility Inspector")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.toggleAgent() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.agentActive) "Pause Agent" else "Start Agent")
                }
                Button(
                    onClick = { viewModel.emergencyStop() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Emergency Stop")
                }
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TiRouter ARM64", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("127.0.0.1:1870/v1 — ${uiState.routerStatus}")
                    Text("Models: ${uiState.routerModelCount}", style = MaterialTheme.typography.bodySmall)
                    uiState.routerError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.startRouter() }) { Text("Start TiRouter") }
                        OutlinedButton(onClick = { viewModel.stopRouter() }) { Text("Stop") }
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
