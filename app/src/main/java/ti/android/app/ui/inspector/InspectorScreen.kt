package ti.android.app.ui.inspector

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorScreen(
    onBack: () -> Unit,
    viewModel: InspectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility Inspector") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.refreshTree() }) {
                Text("⟳")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Current app info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Foreground App: ${uiState.foregroundApp}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Nodes: ${uiState.nodeCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search nodes by text, resource-id, class...") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Node tree
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.visibleNodes) { node ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = node.text ?: "(no text)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "resId: ${node.resourceId ?: "-"} | class: ${node.className ?: "-"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (node.clickable) {
                                    SuggestionChip(
                                        onClick = { viewModel.testClick(node.nodeId) },
                                        label = { Text("Click", fontSize = 10.sp) }
                                    )
                                }
                                if (node.editable) {
                                    SuggestionChip(
                                        onClick = { viewModel.testSetText(node.nodeId) },
                                        label = { Text("SetText", fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
