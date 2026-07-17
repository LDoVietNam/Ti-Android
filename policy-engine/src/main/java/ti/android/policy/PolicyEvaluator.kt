package ti.android.policy

import ti.android.model.task.DeviceAction
import ti.android.model.task.TaskIntent

// ─── Risk Levels ──────────────────────────────────────────────────

enum class RiskLevel(val level: Int) {
    R0(0), // Read UI, sanitized tree
    R1(1), // Open app, navigate, scroll
    R2(2), // Fill draft, select file (unsigned)
    R3(3), // Send message, publish, share file
    R4(4), // Delete data, change account, bulk
    R5(5), // Payment, transfer, OTP, credential
}

enum class PolicyDecision { ALLOW, CONFIRM, DENY }

// ─── Policy Engine ────────────────────────────────────────────────

class PolicyEvaluator(private val policy: Policy = Policy()) {

    fun evaluate(action: DeviceAction): PolicyResult {
        val risk = classifyRisk(action)
        val decision = when (risk) {
            RiskLevel.R0, RiskLevel.R1 -> PolicyDecision.ALLOW
            RiskLevel.R2 -> if (policy.requirePreview) PolicyDecision.CONFIRM else PolicyDecision.ALLOW
            RiskLevel.R3 -> if (policy.requireConfirmation) PolicyDecision.CONFIRM else PolicyDecision.ALLOW
            RiskLevel.R4 -> PolicyDecision.DENY
            RiskLevel.R5 -> PolicyDecision.DENY
        }
        return PolicyResult(decision, risk, risk.name)
    }

    fun evaluateIntent(intent: TaskIntent): PolicyResult {
        return when (intent.type) {
            "read_ui", "open_app", "navigate" -> PolicyResult(PolicyDecision.ALLOW, RiskLevel.R1, intent.type)
            "type_draft" -> PolicyResult(PolicyDecision.ALLOW, RiskLevel.R2, intent.type)
            "send_message", "publish_content", "share_file" -> {
                if (policy.requireConfirmation) PolicyResult(PolicyDecision.CONFIRM, RiskLevel.R3, intent.type)
                else PolicyResult(PolicyDecision.ALLOW, RiskLevel.R3, intent.type)
            }
            "delete_content", "account_change", "bulk_message" -> PolicyResult(PolicyDecision.DENY, RiskLevel.R4, intent.type)
            "financial_action", "credential_action", "otp_action" -> PolicyResult(PolicyDecision.DENY, RiskLevel.R5, intent.type)
            else -> PolicyResult(PolicyDecision.CONFIRM, RiskLevel.R2, intent.type)
        }
    }

    private fun classifyRisk(action: DeviceAction): RiskLevel = when (action) {
        is DeviceAction.Back, is DeviceAction.Home -> RiskLevel.R1
        is DeviceAction.OpenApp, is DeviceAction.OpenDeepLink -> RiskLevel.R1
        is DeviceAction.FindTarget -> RiskLevel.R0
        is DeviceAction.ClickTarget -> RiskLevel.R1
        is DeviceAction.SetText -> RiskLevel.R2
        is DeviceAction.Scroll -> RiskLevel.R1
    }
}

data class Policy(
    val requireConfirmation: Boolean = true,
    val requirePreview: Boolean = true,
    val maxActionsPerTask: Int = 30,
    val maxRetriesPerAction: Int = 2,
    val maxTaskDurationSeconds: Int = 120,
)

data class PolicyResult(
    val decision: PolicyDecision,
    val risk: RiskLevel,
    val reason: String,
)

// ─── Approval Gateway ─────────────────────────────────────────────

data class ApprovalRequest(
    val taskId: String,
    val intentType: String,
    val description: String,
    val appPackage: String? = null,
    val content: String? = null,
    val recipient: String? = null,
    val expiresAt: Long = System.currentTimeMillis() + 60000,
)

data class ApprovalResult(
    val approved: Boolean,
    val approvedBy: String = "local_user",
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String? = null,
)

class ApprovalGateway {
    private val pendingApprovals = mutableMapOf<String, ApprovalRequest>()

    fun requestApproval(request: ApprovalRequest): String {
        val id = "appr_${System.currentTimeMillis()}"
        pendingApprovals[id] = request
        return id
    }

    fun resolve(id: String, approved: Boolean): ApprovalResult? {
        pendingApprovals.remove(id)
        return ApprovalResult(approved = approved)
    }

    fun getPending(id: String): ApprovalRequest? = pendingApprovals[id]
}

// ─── Sensitive Data Guard ─────────────────────────────────────────

class SensitiveDataGuard {
    private val sensitivePatterns = listOf(
        Regex(".*password.*", RegexOption.IGNORE_CASE),
        Regex(".*otp.*", RegexOption.IGNORE_CASE),
        Regex(".*token.*", RegexOption.IGNORE_CASE),
        Regex(".*secret.*", RegexOption.IGNORE_CASE),
        Regex(".*key.*", RegexOption.IGNORE_CASE),
    )

    fun isSensitive(field: String): Boolean =
        sensitivePatterns.any { it.matches(field) }

    fun redact(text: String): String = "█".repeat(text.length.coerceIn(4, 20))
}
