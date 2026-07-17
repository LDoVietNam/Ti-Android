package ti.android.adapter

import ti.android.model.task.DeviceAction
import ti.android.model.task.TaskIntent
import ti.android.model.task.UiNode

// ─── AppAdapter Interface ─────────────────────────────────────────

interface AppAdapter {
    val id: String
    val version: String
    val supportedPackages: Set<String>

    /** Check if adapter works with the current app version */
    suspend fun probe(context: AdapterContext): CompatibilityResult

    /** Observe current UI state and return structured observation */
    suspend fun observe(context: AdapterContext): AdapterObservation

    /** Generate a plan of actions to fulfill the intent */
    suspend fun plan(intent: TaskIntent, observation: AdapterObservation): AdapterPlan

    /** Verify action was successful */
    suspend fun verify(plan: AdapterPlan, before: AdapterObservation, after: AdapterObservation): VerificationResult
}

// ─── Data Classes ─────────────────────────────────────────────────

data class AdapterContext(
    val packageName: String,
    val appVersion: String? = null,
    val accessibilityNodes: List<UiNode> = emptyList(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)

data class CompatibilityResult(
    val compatible: Boolean,
    val reason: String? = null,
    val appVersion: String? = null,
)

data class AdapterObservation(
    val nodes: List<UiNode> = emptyList(),
    val foregroundActivity: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

data class AdapterPlan(
    val actions: List<DeviceAction>,
    val confidence: Double = 1.0,
    val requiresApproval: Boolean = false,
    val description: String = "",
)

data class VerificationResult(
    val success: Boolean,
    val evidence: String = "",
    val confidence: Double = 0.0,
)

// ─── Registry ─────────────────────────────────────────────────────

class AdapterRegistry {
    private val adapters = mutableMapOf<String, AppAdapter>()

    fun register(adapter: AppAdapter) {
        adapters[adapter.id] = adapter
    }

    fun get(id: String): AppAdapter? = adapters[id]

    fun findForPackage(packageName: String): AppAdapter? =
        adapters.values.firstOrNull { packageName in it.supportedPackages }

    fun all(): List<AppAdapter> = adapters.values.toList()
}
