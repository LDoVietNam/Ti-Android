package ti.android.model.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Task Protocol ────────────────────────────────────────────────

@Serializable
data class TaskEnvelope(
    @SerialName("protocolVersion") val protocolVersion: String = "1.0",
    @SerialName("messageId") val messageId: String,
    @SerialName("taskId") val taskId: String,
    @SerialName("attempt") val attempt: Int = 1,
    @SerialName("idempotencyKey") val idempotencyKey: String? = null,
    @SerialName("correlationId") val correlationId: String? = null,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("target") val target: TaskTarget,
    @SerialName("intent") val intent: TaskIntent,
    @SerialName("policy") val taskPolicy: TaskPolicy? = null,
)

@Serializable
data class TaskTarget(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("packageName") val packageName: String? = null,
    @SerialName("adapter") val adapter: String? = null,
    @SerialName("minimumAdapterVersion") val minimumAdapterVersion: String? = null,
)

@Serializable
data class TaskIntent(
    @SerialName("type") val type: String,
    @SerialName("parameters") val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class TaskPolicy(
    @SerialName("allowVisionFallback") val allowVisionFallback: Boolean = true,
    @SerialName("sendRequiresApproval") val sendRequiresApproval: Boolean = true,
    @SerialName("maximumActions") val maximumActions: Int = 20,
    @SerialName("timeoutMs") val timeoutMs: Long = 60000,
)

@Serializable
data class TaskResult(
    @SerialName("taskId") val taskId: String,
    @SerialName("attempt") val attempt: Int = 1,
    @SerialName("status") val status: TaskStatus,
    @SerialName("startedAt") val startedAt: String? = null,
    @SerialName("finishedAt") val finishedAt: String? = null,
    @SerialName("result") val result: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("metrics") val metrics: TaskMetrics? = null,
    @SerialName("traceId") val traceId: String? = null,
)

@Serializable
data class TaskMetrics(
    @SerialName("actions") val actions: Int = 0,
    @SerialName("retries") val retries: Int = 0,
    @SerialName("visionCalls") val visionCalls: Int = 0,
)

@Serializable
enum class TaskStatus {
    RECEIVED, VALIDATING, REJECTED, ACCEPTED, PREPARING,
    OBSERVING, PLANNING, POLICY_CHECK, BLOCKED, WAITING_APPROVAL,
    CANCELLED, EXECUTING, VERIFYING, COMPLETED, RECOVERING, FAILED
}

@Serializable
enum class TaskError {
    INVALID_TASK, UNSUPPORTED_CAPABILITY, APP_NOT_INSTALLED,
    ADAPTER_NOT_FOUND, ADAPTER_INCOMPATIBLE, PERMISSION_MISSING,
    DEVICE_LOCKED, TARGET_NOT_FOUND, UI_CHANGED, ACTION_REJECTED,
    ACTION_BLOCKED, USER_CANCELLED, APP_CRASHED, NETWORK_UNAVAILABLE,
    TIMEOUT, VERIFICATION_FAILED, RETRY_EXHAUSTED, INTERNAL_ERROR
}

// ─── Actions ──────────────────────────────────────────────────────

@Serializable
sealed interface DeviceAction {
    @SerialName("actionId") val actionId: String
    @SerialName("timeoutMs") val timeoutMs: Long

    @Serializable data class OpenApp(
        override val actionId: String, @SerialName("packageName") val packageName: String,
        override val timeoutMs: Long = 10000
    ) : DeviceAction

    @Serializable data class OpenDeepLink(
        override val actionId: String, @SerialName("uri") val uri: String,
        override val timeoutMs: Long = 10000
    ) : DeviceAction

    @Serializable data class FindTarget(
        override val actionId: String, @SerialName("target") val target: UiTarget,
        override val timeoutMs: Long = 5000
    ) : DeviceAction

    @Serializable data class ClickTarget(
        override val actionId: String, @SerialName("target") val target: UiTarget,
        override val timeoutMs: Long = 5000
    ) : DeviceAction

    @Serializable data class SetText(
        override val actionId: String, @SerialName("target") val target: UiTarget,
        @SerialName("text") val text: String, override val timeoutMs: Long = 5000
    ) : DeviceAction

    @Serializable data class Scroll(
        override val actionId: String, @SerialName("direction") val direction: ScrollDirection,
        @SerialName("amount") val amount: Float = 0.5f, override val timeoutMs: Long = 3000
    ) : DeviceAction

    @Serializable data object Back : DeviceAction {
        override val actionId get() = "back"
        override val timeoutMs get() = 2000L
    }

    @Serializable data object Home : DeviceAction {
        override val actionId get() = "home"
        override val timeoutMs get() = 2000L
    }
}

@Serializable enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

// ─── UI Target / Locator ──────────────────────────────────────────

@Serializable
data class UiTarget(
    @SerialName("resourceIds") val resourceIds: List<String> = emptyList(),
    @SerialName("textPatterns") val textPatterns: List<String> = emptyList(),
    @SerialName("contentDescriptions") val contentDescriptions: List<String> = emptyList(),
    @SerialName("classNames") val classNames: List<String> = emptyList(),
    @SerialName("requiredActions") val requiredActions: Set<String> = emptySet(),
    @SerialName("minimumConfidence") val minimumConfidence: Double = 0.85,
)

// ─── Observation ──────────────────────────────────────────────────

@Serializable
data class AccessibilitySnapshot(
    @SerialName("packageName") val packageName: String? = null,
    @SerialName("windowId") val windowId: Int = 0,
    @SerialName("orientation") val orientation: String? = null,
    @SerialName("screen") val screenInfo: ScreenInfo? = null,
    @SerialName("nodes") val nodes: List<UiNode> = emptyList(),
)

@Serializable
data class ScreenInfo(
    @SerialName("width") val width: Int = 0, @SerialName("height") val height: Int = 0,
    @SerialName("density") val density: Float = 1.0f,
)

@Serializable
data class UiNode(
    @SerialName("nodeId") val nodeId: String,
    @SerialName("className") val className: String? = null,
    @SerialName("resourceId") val resourceId: String? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("contentDescription") val contentDescription: String? = null,
    @SerialName("clickable") val clickable: Boolean = false,
    @SerialName("editable") val editable: Boolean = false,
    @SerialName("visible") val visible: Boolean = true,
    @SerialName("bounds") val bounds: String? = null,
    @SerialName("depth") val depth: Int = 0,
    @SerialName("children") val children: List<UiNode> = emptyList(),
)

// ─── Capability ───────────────────────────────────────────────────

@Serializable
data class DeviceCapability(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("reportedAt") val reportedAt: String,
    @SerialName("capabilities") val capabilities: CapabilityMap,
    @SerialName("runtime") val runtimeInfo: RuntimeInfo? = null,
)

@Serializable
data class CapabilityMap(
    @SerialName("accessibility") val accessibility: AccessibilityCap? = null,
    @SerialName("mediaProjection") val mediaProjection: MediaProjectionCap? = null,
    @SerialName("notifications") val notifications: NotificationCap? = null,
    @SerialName("shareTarget") val shareTarget: ShareTargetCap? = null,
    @SerialName("ocr") val ocrCap: OcrCap? = null,
)

@Serializable data class AccessibilityCap(@SerialName("enabled") val enabled: Boolean = false)
@Serializable data class MediaProjectionCap(@SerialName("available") val available: Boolean = false, @SerialName("active") val active: Boolean = false)
@Serializable data class NotificationCap(@SerialName("enabled") val enabled: Boolean = false)
@Serializable data class ShareTargetCap(@SerialName("enabled") val enabled: Boolean = false)
@Serializable data class OcrCap(@SerialName("mode") val mode: String = "none", @SerialName("languages") val languages: List<String> = emptyList())

@Serializable
data class RuntimeInfo(
    @SerialName("deviceLocked") val deviceLocked: Boolean = false,
    @SerialName("batteryPercent") val batteryPercent: Int = 0,
    @SerialName("network") val network: String? = null,
    @SerialName("foregroundApp") val foregroundApp: String? = null,
)
