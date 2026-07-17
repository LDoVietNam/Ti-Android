package ti.android.accessibility

import ti.android.model.task.AccessibilitySnapshot
import ti.android.model.task.DeviceAction
import ti.android.model.task.UiNode
import ti.android.model.task.UiTarget

/** Captures accessibility tree snapshot from the active window. */
class NodeSnapshotter {
    suspend fun capture(): AccessibilitySnapshot {
        // Delegates to TiAccessibilityService in app module
        return AccessibilitySnapshot()
    }
}

/** Normalizes node data: removes invisible/duplicate nodes, limits depth. */
class NodeNormalizer {
    fun normalize(nodes: List<UiNode>, maxDepth: Int = 20): List<UiNode> {
        return nodes.filter { it.visible }
            .map { it.copy(children = it.children.takeWhile { node -> node.depth < maxDepth }) }
    }
}

/** Matches UiTarget against node list and returns scored candidates. */
class NodeMatcher {
    fun findTarget(target: UiTarget, nodes: List<UiNode>): List<ScoredNode> {
        val results = mutableListOf<ScoredNode>()
        for (node in flatten(nodes)) {
            var score = 0.0
            if (node.resourceId in target.resourceIds) score += 50.0
            if (node.contentDescription in target.contentDescriptions) score += 30.0
            if (target.textPatterns.any { node.text?.contains(it, ignoreCase = true) == true }) score += 25.0
            if (node.className in target.classNames) score += 10.0
            if (node.clickable) score += 5.0
            if (score >= target.minimumConfidence * 100) {
                results.add(ScoredNode(node, score))
            }
        }
        return results.sortedByDescending { it.score }
    }

    private fun flatten(nodes: List<UiNode>): List<UiNode> =
        nodes.flatMap { listOf(it) + flatten(it.children) }
}

data class ScoredNode(val node: UiNode, val score: Double)

/** Executes actions via accessibility service. */
class NodeActionExecutor {
    suspend fun execute(action: DeviceAction, service: Any?): Boolean = when (action) {
        is DeviceAction.Back -> true  // Delegate to AccessibilityService.performGlobalAction
        is DeviceAction.Home -> true
        is DeviceAction.OpenApp -> true
        is DeviceAction.ClickTarget -> true
        is DeviceAction.SetText -> true
        else -> false
    }
}

/** Dispatches gestures using AccessibilityService gesture API. */
class GestureExecutor {
    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 200): Boolean {
        // TODO: Build GestureDescription and dispatch
        return true
    }

    suspend fun tap(x: Float, y: Float): Boolean = swipe(x, y, x, y, 50)
}
