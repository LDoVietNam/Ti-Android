package ti.android.app.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionState(
    val isConnected: Boolean = false,
    val url: String = "",
    val lastHeartbeat: Long = 0L,
    val reconnectAttempt: Int = 0,
)

@Singleton
class DeviceGatewayClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var deviceId: String = ""
    private var sessionToken: String = ""
    private var gatewayUrl: String = ""

    /**
     * Connect to Device Gateway via WSS.
     */
    fun connect(url: String, deviceId: String, token: String) {
        this.gatewayUrl = url
        this.deviceId = deviceId
        this.sessionToken = token

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Ti-Device-Id", deviceId)
            .addHeader("X-Ti-Protocol-Version", "1.0")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.i("WSS connected to $url")
                _connectionState.value = _connectionState.value.copy(
                    isConnected = true,
                    url = url,
                    reconnectAttempt = 0
                )
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("WSS message received: ${text.take(100)}")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.w("WSS closing: $code $reason")
                webSocket.close(1000, "Client closing")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("WSS closed: $code $reason")
                _connectionState.value = _connectionState.value.copy(isConnected = false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WSS failure")
                _connectionState.value = _connectionState.value.copy(isConnected = false)
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = _connectionState.value.copy(isConnected = false)
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive && _connectionState.value.isConnected) {
                delay(15_000) // 15 seconds
                send("""{"type":"device.heartbeat","deviceId":"$deviceId","timestamp":${System.currentTimeMillis()}}""")
                _connectionState.value = _connectionState.value.copy(
                    lastHeartbeat = System.currentTimeMillis()
                )
            }
        }
    }

    private fun scheduleReconnect() {
        val attempt = _connectionState.value.reconnectAttempt + 1
        val delay = calculateBackoff(attempt)

        _connectionState.value = _connectionState.value.copy(
            reconnectAttempt = attempt
        )

        Timber.i("Scheduling reconnect #$attempt in ${delay}ms")

        scope.launch {
            delay(delay)
            if (!_connectionState.value.isConnected && gatewayUrl.isNotBlank()) {
                connect(gatewayUrl, deviceId, sessionToken)
            }
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val base = listOf(1000L, 2000L, 5000L, 10_000L, 30_000L, 60_000L)
        val jitter = (0..1000).random()
        return (base.getOrElse(attempt - 1) { 60_000L }) + jitter
    }

    private fun handleMessage(message: String) {
        // TODO: Parse message and route to appropriate handler
    }
}
