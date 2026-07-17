package ti.android.app.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ti.android.app.BuildConfig
import javax.inject.Inject

data class SettingsUiState(
    val gatewayUrl: String = BuildConfig.TI_DEFAULT_GATEWAY_URL,
    val deviceId: String = "ti-android-001",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val accessibilityGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val screenCaptureGranted: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateGatewayUrl(url: String) {
        _uiState.value = _uiState.value.copy(gatewayUrl = url)
    }

    fun pairDevice() {
        // TODO: Start pairing flow → generate keys → show QR/pairing code
    }
}
