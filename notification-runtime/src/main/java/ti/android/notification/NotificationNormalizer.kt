package ti.android.notification

import kotlinx.serialization.Serializable

@Serializable
data class NormalizedNotification(
    val id: Int,
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String? = null,
    val priority: Int = 0,
    val isGroup: Boolean = false,
    val groupKey: String? = null,
)

class NotificationPolicy {
    private val blockedPackages = mutableSetOf<String>()
    private val allowedCategories = mutableSetOf<String>()

    fun blockPackage(packageName: String) { blockedPackages.add(packageName) }
    fun allowCategory(category: String) { allowedCategories.add(category) }
    fun isAllowed(pkg: String, category: String?): Boolean =
        pkg !in blockedPackages && (category == null || category in allowedCategories || allowedCategories.isEmpty())
}

/**
 * TiNotificationListener — Android service for reading notifications.
 * Must be declared in AndroidManifest.xml with BIND_NOTIFICATION_LISTENER_SERVICE permission.
 *
 * In Ti Android, this service captures incoming notifications, normalizes them,
 * and forwards to the task system for processing.
 *
 * To enable: Settings → Accessibility → Notification Access → Ti Android
 */
class TiNotificationListener /* extends NotificationListenerService */ {
    // Full implementation requires Android framework dependency.
    // For MVP, this class documents the contract.
}
