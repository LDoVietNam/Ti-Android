package ti.android.adapters

import ti.android.adapter.*
import ti.android.model.task.DeviceAction
import ti.android.model.task.TaskIntent
import ti.android.model.task.UiNode

/**
 * Telegram App Adapter
 *
 * Package: org.telegram.messenger
 *
 * Workflow:
 *   1. Open app (deep link or package)
 *   2. Find conversation list → tap target conversation
 *   3. Find input field → type draft
 *   4. Request approval
 *   5. Tap send button
 *   6. Verify message appears in chat
 */
class TelegramAdapter : AppAdapter {
    override val id = "telegram"
    override val version = "0.1.0"
    override val supportedPackages = setOf("org.telegram.messenger", "org.telegram.plus")

    override suspend fun probe(context: AdapterContext): CompatibilityResult {
        val hasTelegram = context.packageName in supportedPackages
        return CompatibilityResult(
            compatible = hasTelegram,
            reason = if (hasTelegram) null else "Telegram not in foreground"
        )
    }

    override suspend fun observe(context: AdapterContext): AdapterObservation {
        return AdapterObservation(
            nodes = context.accessibilityNodes,
            foregroundActivity = context.packageName,
        )
    }

    override suspend fun plan(intent: TaskIntent, observation: AdapterObservation): AdapterPlan {
        val actions = mutableListOf<DeviceAction>()
        var requiresApproval = false

        when (intent.type) {
            "open_conversation" -> {
                intent.parameters["title"]?.let { title ->
                    actions.add(DeviceAction.OpenApp(actionId = "open_1", packageName = "org.telegram.messenger"))
                    actions.add(DeviceAction.FindTarget(
                        actionId = "find_conv",
                        target = UiTarget(textPatterns = listOf(title))
                    ))
                    actions.add(DeviceAction.ClickTarget(
                        actionId = "click_conv",
                        target = UiTarget(textPatterns = listOf(title))
                    ))
                }
            }
            "compose_message" -> {
                val content = intent.parameters["content"] ?: ""
                actions.add(DeviceAction.FindTarget(
                    actionId = "find_input",
                    target = UiTarget(resourceIds = listOf("org.telegram.messenger:id/chat_input"))
                ))
                actions.add(DeviceAction.SetText(
                    actionId = "type_msg",
                    target = UiTarget(resourceIds = listOf("org.telegram.messenger:id/chat_input")),
                    text = content,
                ))
                requiresApproval = true
            }
            "send_message" -> {
                actions.add(DeviceAction.FindTarget(
                    actionId = "find_send",
                    target = UiTarget(resourceIds = listOf("org.telegram.messenger:id/send_button"))
                ))
                actions.add(DeviceAction.ClickTarget(
                    actionId = "click_send",
                    target = UiTarget(resourceIds = listOf("org.telegram.messenger:id/send_button"))
                ))
                requiresApproval = true
            }
        }

        return AdapterPlan(
            actions = actions,
            requiresApproval = requiresApproval,
            description = "Telegram: ${intent.type}",
        )
    }

    override suspend fun verify(plan: AdapterPlan, before: AdapterObservation, after: AdapterObservation): VerificationResult {
        val lastAction = plan.actions.lastOrNull()
        return when {
            lastAction is DeviceAction.SetText -> {
                val hasText = after.nodes.any {
                    it.resourceId?.contains("chat_input") == true && it.text?.isNotEmpty() == true
                }
                VerificationResult(success = hasText, confidence = if (hasText) 0.9 else 0.0)
            }
            lastAction is DeviceAction.ClickTarget -> {
                val uiChanged = before.nodes.hashCode() != after.nodes.hashCode()
                VerificationResult(success = uiChanged, confidence = if (uiChanged) 0.8 else 0.0)
            }
            else -> VerificationResult(success = true, confidence = 1.0)
        }
    }
}
