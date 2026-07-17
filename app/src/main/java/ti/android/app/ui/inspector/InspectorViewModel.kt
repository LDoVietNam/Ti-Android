package ti.android.app.ui.inspector

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AccessibilityNodeInfo(
    val nodeId: String,
    val resourceId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val bounds: String? = null,
    val depth: Int = 0,
    val children: List<AccessibilityNodeInfo> = emptyList(),
)

data class InspectorUiState(
    val foregroundApp: String = "N/A",
    val nodeCount: Int = 0,
    val searchQuery: String = "",
    val visibleNodes: List<AccessibilityNodeInfo> = emptyList(),
    val allNodes: List<AccessibilityNodeInfo> = emptyList(),
)

@HiltViewModel
class InspectorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(InspectorUiState())
    val uiState: StateFlow<InspectorUiState> = _uiState.asStateFlow()

    init {
        refreshTree()
    }

    fun refreshTree() {
        // TODO: Get accessibility tree from TiAccessibilityService
        val mockNodes = listOf(
            AccessibilityNodeInfo(
                nodeId = "n-001",
                resourceId = "com.example:id/button",
                className = "android.widget.Button",
                text = "Submit",
                clickable = true,
                depth = 2,
            ),
            AccessibilityNodeInfo(
                nodeId = "n-002",
                resourceId = "com.example:id/input",
                className = "android.widget.EditText",
                text = "Hello",
                editable = true,
                depth = 2,
            ),
        )
        _uiState.value = _uiState.value.copy(
            foregroundApp = "com.example.app",
            nodeCount = mockNodes.size,
            allNodes = mockNodes,
            visibleNodes = mockNodes,
        )
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(visibleNodes = _uiState.value.allNodes)
        } else {
            val filtered = _uiState.value.allNodes.filter { node ->
                (node.text?.contains(query, ignoreCase = true) == true) ||
                (node.resourceId?.contains(query, ignoreCase = true) == true) ||
                (node.className?.contains(query, ignoreCase = true) == true)
            }
            _uiState.value = _uiState.value.copy(visibleNodes = filtered)
        }
    }

    fun testClick(nodeId: String) {
        // TODO: Execute click via accessibility service
    }

    fun testSetText(nodeId: String) {
        // TODO: Execute setText via accessibility service
    }
}
