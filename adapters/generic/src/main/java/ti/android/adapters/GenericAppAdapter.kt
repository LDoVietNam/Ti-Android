package ti.android.adapters

import ti.android.adapter.*
import ti.android.model.task.DeviceAction
import ti.android.model.task.TaskIntent
import ti.android.model.task.UiNode

class GenericAppAdapter : AppAdapter {
    override val id = "generic"
    override val version = "0.1.0"
    override val supportedPackages = setOf("*") // Matches any app

    override suspend fun probe(context: AdapterContext): CompatibilityResult {
        return CompatibilityResult(compatible = true, reason = "Generic adapter")
    }

    override suspend fun observe(context: AdapterContext): AdapterObservation {
        return AdapterObservation(nodes = context.accessibilityNodes)
    }

    override suspend fun plan(intent: TaskIntent, observation: AdapterObservation): AdapterPlan {
        val actions = mutableListOf<DeviceAction>()

        when (intent.type) {
            "open_app" -> intent.parameters["package"]?.let { pkg ->
                actions.add(DeviceAction.OpenApp(actionId = "open_1", packageName = pkg))
            }
            "click" -> {
                val targetText = intent.parameters["text"]
                val targetResId = intent.parameters["resourceId"]
                val target = UiTarget(
                    textPatterns = if (targetText != null) listOf(targetText) else emptyList(),
                    resourceIds = if (targetResId != null) listOf(targetResId) else emptyList(),
                )
                actions.add(DeviceAction.ClickTarget(actionId = "click_1", target = target))
            }
            "type_text" -> {
                val targetResId = intent.parameters["resourceId"]
                val text = intent.parameters["text"] ?: ""
                val target = UiTarget(resourceIds = if (targetResId != null) listOf(targetResId) else emptyList())
                actions.add(DeviceAction.SetText(actionId = "type_1", target = target, text = text))
            }
            "navigate" -> {
                if (intent.parameters["direction"] == "back") actions.add(DeviceAction.Back)
                if (intent.parameters["direction"] == "home") actions.add(DeviceAction.Home)
            }
        }

        return AdapterPlan(actions = actions, description = "Generic: ${intent.type}")
    }

    override suspend fun verify(plan: AdapterPlan, before: AdapterObservation, after: AdapterObservation): VerificationResult {
        val changed = before.nodes.hashCode() != after.nodes.hashCode()
        return VerificationResult(success = changed, evidence = "UI changed: $changed")
    }
}
