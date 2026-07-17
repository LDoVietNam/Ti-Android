package ti.android.telemetry

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ─── Trace Context ────────────────────────────────────────────────

data class TraceContext(
    val traceId: String,
    val taskId: String? = null,
    val actionId: String? = null,
    val deviceId: String = "",
    val adapterId: String? = null,
    val attempt: Int = 1,
)

// ─── Event Schema ─────────────────────────────────────────────────

@Serializable
data class TelemetryEvent(
    val event: String,
    val timestamp: String,
    val traceId: String? = null,
    val taskId: String? = null,
    val actionId: String? = null,
    val deviceId: String? = null,
    val adapterId: String? = null,
    val durationMs: Long? = null,
    val outcome: String? = null,
    val metadata: Map<String, JsonElement>? = null,
)

// ─── Event Logger ─────────────────────────────────────────────────

class EventLogger {
    private val events = mutableListOf<TelemetryEvent>()
    private val maxEvents = 1000

    fun log(event: TelemetryEvent) {
        synchronized(events) {
            events.add(event)
            if (events.size > maxEvents) events.removeAt(0)
        }
    }

    fun flush(): List<TelemetryEvent> = synchronized(events) {
        val batch = events.toList()
        events.clear()
        batch
    }
}

// ─── Metrics ──────────────────────────────────────────────────────

data class DeviceMetrics(
    val onlineDevices: Int = 0,
    val reconnectCount: Int = 0,
    val taskSuccessRate: Double = 0.0,
    val taskDurationP50: Long = 0,
    val taskDurationP95: Long = 0,
    val actionSuccessRate: Double = 0.0,
    val locatorAmbiguityRate: Double = 0.0,
    val visionFallbackRate: Double = 0.0,
    val approvalLatencyMs: Long = 0,
    val userRejectionRate: Double = 0.0,
    val retryRate: Double = 0.0,
    val verificationFailureRate: Double = 0.0,
    val batteryImpact: Float = 0f,
    val crashFreeSessions: Long = 0,
)

// ─── Snapshot Sanitizer ───────────────────────────────────────────

class SnapshotSanitizer {
    private val sensitivePattern = Regex("(?i)(password|otp|token|secret|key|credit_card|ssn)")

    fun sanitize(text: String): String =
        text.replace(sensitivePattern) { "█".repeat(it.value.length) }

    fun sanitizeFields(data: Map<String, String>): Map<String, String> =
        data.mapValues { (key, value) ->
            if (sensitivePattern.matches(key)) "█".repeat(value.length.coerceIn(4, 20))
            else value
        }
}
