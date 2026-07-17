package ti.android.app.router

data class TiRouterState(
    val status: Status = Status.STOPPED,
    val baseUrl: String = "http://127.0.0.1:20128",
    val pid: Long? = null,
    val modelCount: Int = 0,
    val lastError: String? = null,
)

enum class Status {
    STOPPED,
    STARTING,
    RUNNING,
    DEGRADED,
    FAILED,
}
