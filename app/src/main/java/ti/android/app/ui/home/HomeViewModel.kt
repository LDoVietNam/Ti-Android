package ti.android.app.ui.home

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ti.android.transport.ConnectionStatus
import ti.android.transport.DeviceGatewayClient
import ti.android.app.router.TiRouterController
import ti.android.app.router.TiRouterService
import javax.inject.Inject

data class HomeUiState(
    val connectionStatus: String = "disconnected",
    val deviceId: String = "ti-android-001",
    val gatewayUrl: String = "wss://gateway.example.local/device", // Default, will be overridden
    val accessibilityEnabled: Boolean = false,
    val batteryLevel: String = "--",
    val agentActive: Boolean = false,
    val activeTask: String? = null,
    val lastMessage: String = "",
    val routerStatus: String = "stopped",
    val routerModelCount: Int = 0,
    val routerError: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val deviceGatewayClient: DeviceGatewayClient,
    private val tiRouterController: TiRouterController,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDeviceId()
        observeDeviceGateway()
        viewModelScope.launch {
            tiRouterController.state.collect { router ->
                _uiState.update { it.copy(
                    routerStatus = router.status.name.lowercase(),
                    routerModelCount = router.modelCount,
                    routerError = router.lastError,
                ) }
            }
        }
    }

    private fun loadDeviceId() {
        // TODO: Load from DeviceIdentityStore
        viewModelScope.launch {
            val storedId = "ti-android-001" // Placeholder - replace with actual storage
            _uiState.update { it.copy(deviceId = storedId) }
        }
    }

    private fun observeDeviceGateway() {
        viewModelScope.launch {
            // Observe connection status
            deviceGatewayClient.connectionStatus.collect { status ->
                val statusString = when (status) {
                    ConnectionStatus.DISCONNECTED -> "disconnected"
                    ConnectionStatus.CONNECTING -> "connecting"
                    ConnectionStatus.CONNECTED -> "connected"
                    ConnectionStatus.FAILED -> "failed"
                    ConnectionStatus.FAILED_PERMANENTLY -> "failed permanently"
                }
                _uiState.update { it.copy(connectionStatus = statusString) }
            }
            
            // Observe received messages
            deviceGatewayClient.messageReceived.collect { message ->
                _uiState.update { it.copy(lastMessage = message) }
                // Update active task if this is a task-related message
                if (message.contains("task")) {
                    _uiState.update { it.copy(activeTask = message) }
                }
            }
        }
    }

    fun toggleAgent() {
        viewModelScope.launch {
            val current = _uiState.value
            if (current.agentActive) {
                stopAgent()
            } else {
                startAgent()
            }
        }
    }

    private suspend fun startAgent() {
        _uiState.update { it.copy(agentActive = true) }
        deviceGatewayClient.connect()
    }

    private suspend fun stopAgent() {
        _uiState.update { it.copy(agentActive = false) }
        deviceGatewayClient.disconnect()
    }

    fun emergencyStop() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                agentActive = false,
                connectionStatus = "disconnected",
                activeTask = null,
                lastMessage = "Emergency stop activated"
            ) }
            deviceGatewayClient.disconnect()
            // TODO: Emergency stop - cancel all tasks, stop services
        }
    }

    /**
     * Send a test message to verify connectivity
     */
    fun sendTestMessage() {
        viewModelScope.launch {
            deviceGatewayClient.sendMessage("Test message from app at ${System.currentTimeMillis()}")
        }
    }

    fun startRouter() {
        val intent = Intent(getApplication(), TiRouterService::class.java)
            .setAction(TiRouterService.ACTION_START)
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopRouter() {
        val intent = Intent(getApplication(), TiRouterService::class.java)
            .setAction(TiRouterService.ACTION_STOP)
        getApplication<Application>().startService(intent)
    }
}
