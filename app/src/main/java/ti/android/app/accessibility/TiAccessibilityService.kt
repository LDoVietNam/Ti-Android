package ti.android.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

class TiAccessibilityService : AccessibilityService() {

    private var lastRootNode: AccessibilityNodeInfo? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lastRootNode?.recycle()
                lastRootNode = rootInActiveWindow
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("Accessibility service interrupted")
    }

    /**
     * Get current root node from active window.
     */
    fun captureRootNode(): AccessibilityNodeInfo? {
        lastRootNode?.let {
            if (it.isVisibleToUser) return it
        }
        lastRootNode = rootInActiveWindow
        return lastRootNode
    }

    /**
     * Find node by resource ID.
     */
    fun findNodeByResourceId(resourceId: String): AccessibilityNodeInfo? {
        val root = captureRootNode() ?: return null
        return findNode(root) { node ->
            node.viewIdResourceName == resourceId
        }
    }

    /**
     * Find node by text content.
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = captureRootNode() ?: return null
        return findNode(root) { node ->
            node.text?.toString()?.contains(text, ignoreCase = true) == true
        }
    }

    /**
     * Perform click on a node found by resource ID.
     */
    fun performClick(resourceId: String): Boolean {
        val node = findNodeByResourceId(resourceId) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK).also {
            node.recycle()
        }
    }

    /**
     * Set text on an editable node found by resource ID.
     */
    fun performSetText(resourceId: String, text: String): Boolean {
        val node = findNodeByResourceId(resourceId) ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args).also {
            node.recycle()
        }
    }

    /**
     * Perform global action (BACK, HOME, etc).
     */
    fun performGlobalAction(action: Int): Boolean {
        return super.performGlobalAction(action)
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNode(child, predicate)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    override fun onDestroy() {
        lastRootNode?.recycle()
        super.onDestroy()
    }

    companion object {
        const val ACTION_BACK = GLOBAL_ACTION_BACK
    }
}
