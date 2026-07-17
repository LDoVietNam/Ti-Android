package ti.android.transport

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber
import java.util.UUID
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
    FAILED_PERMANENTLY,
}

/**
 * Client for connecting to Ti Device Gateway via WebSocket
 * Handles connection lifecycle, message sending/receiving, and reconnection
 */
class DeviceGatewayClient(
    private val context: Context,
    private val gatewayUrl: String,
    private val deviceId: String,
    lifecycleOwner: LifecycleOwner? = null
) : DefaultLifecycleObserver {

    // State flows for observing connection status
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _messageReceived = MutableStateFlow<String>("")
    val messageReceived: StateFlow<String> = _messageReceived.asStateFlow()

    // Internal state
    private var webSocket: WebSocket? = null
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val messageSendChannel = Channel<String>(Channel.UNLIMITED)
    private val reconnectPolicy = ReconnectPolicy()
    private val okHttpClient = OkHttpClient.Builder()
        .build()

    // Dependencies
    private val messageOutbox: MessageOutbox

    init {
        // Initialize dependencies
        messageOutbox = MessageOutbox.getInstance(context) { message ->
            // This callback will attempt to send the message
            sendMessageInternal(message)
        }
        
        // Observe lifecycle if provided
        lifecycleOwner?.lifecycle.addObserver(this)
        
        // Start background tasks
        startMessageSender()
        startConnectionMonitor()
        
        // Process any pending outbox messages when we connect
        startOutboxProcessor()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
        super.onDestroy(owner)
    }

    /**
     * Start the WebSocket connection
     */
    fun connect() {
        if (connectionStatus.value == ConnectionStatus.CONNECTED ||
            connectionStatus.value == ConnectionStatus.CONNECTING) {
            return
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING
        reconnectPolicy.reset()
        establishConnection()
    }

    /**
     * Disconnect from the WebSocket
     */
    fun disconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        reconnectPolicy.reset()
        webSocket?.cancel()
        webSocket = null
    }

    /**
     * Send a message through the WebSocket
     * Messages are queued if not connected and sent when connection is available
     */
    fun sendMessage(message: String) {
        // Try to send immediately if we think we're connected
        if (connectionStatus.value == ConnectionStatus.CONNECTED) {
            sendMessageInternal(message)
        } else {
            // Store in outbox for later delivery
            messageOutbox.addMessage(message)
            Timber.d("Message stored in outbox for later delivery")
        }
    }

    /**
     * Internal method to send message via WebSocket
     */
    private fun sendMessageInternal(message: String) {
        webSocket?.send(message)
        Timber.tag("WebSocket").d("Sent message: $message")
    }

    /**
     * Establish the WebSocket connection
     */
    private fun establishConnection() {
        val request = Request.Builder()
            .url("$gatewayUrl/device-session")
            .header("X-Ti-Device-Id", deviceId)
            // In a real implementation, we'd get a proper auth token from secure storage
            .header("Authorization", "Bearer ${getAuthTokenPlaceholder()}")
            .build()

        webSocket = okHttpClient.newWebSocket(request, websocketListener)
    }

    /**
     * WebSocket listener for handling connection events
     */
    private val websocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            super.onOpen(webSocket, response)
            _connectionStatus.value = ConnectionStatus.CONNECTED
            reconnectPolicy.reset()
            Timber.tag("WebSocket").i("Connected to Device Gateway")
            
            // Process outbox messages when connected
            processOutboxMessages()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            _messageReceived.value = text
            Timber.tag("WebSocket").d("Received message: $text")
            // TODO: Handle incoming messages (process task offers, etc.)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            super.onMessage(webSocket, bytes)
            val text = bytes.utf8()
            _messageReceived.value = text
            Timber.tag("WebSocket").d("Received binary message (${bytes.size} bytes): $text")
            // TODO: Handle binary messages if needed
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) {
            super.onClosing(webSocket, code, reason)
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Timber.tag("WebSocket").i("WebSocket closing: code=$code, reason=$reason")
            webSocket.close(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            super.onFailure(webSocket, t, response)
            _connectionStatus.value = ConnectionStatus.FAILED
            Timber.tag("WebSocket").e(t, "WebSocket failed")
            scheduleReconnection()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Timber.tag("WebSocket").i("WebSocket closed: code=$code, reason=$reason")
            scheduleReconnection()
        }
    }

    /**
     * Schedule a reconnection attempt
     */
    private fun scheduleReconnection() {
        if (!reconnectPolicy.canAttempt()) {
            _connectionStatus.value = ConnectionStatus.FAILED_PERMANENTLY
            Timber.w("Max reconnection attempts reached")
            return
        }

        val delay = reconnectPolicy.getNextDelay()
        if (delay == -1) {
            _connectionStatus.value = ConnectionStatus.FAILED_PERMANENTLY
            return
        }

        Timber.d("Scheduling reconnection in $delay ms (attempt ${reconnectPolicy.getAttemptCount()})")
        
        scope.launch {
            delay(delay)
            if (connectionStatus.value != ConnectionStatus.CONNECTING &&
                connectionStatus.value != ConnectionStatus.CONNECTED) {
                establishConnection()
            }
        }
    }

    /**
     * Process messages from the outbox when we have a connection
     */
    private fun processOutboxMessages() {
        scope.launch {
            while (connectionStatus.value == ConnectionStatus.CONNECTED && isActive) {
                // Check if there are messages in the outbox
                if (messageOutbox.getPendingMessageCount() > 0) {
                    // Get one message from outbox and send it
                    val message = messageOutbox.getNextMessage()
                    message?.let {
                        sendMessageInternal(it)
                        // Note: The MessageOutbox handles removing sent messages internally
                    }
                }
                
                // Wait before checking again
                delay(1000)
            }
        }
    }

    /**
     * Start the message sender coroutine for real-time messages
     */
    private fun startMessageSender() {
        scope.launch {
            while (isActive) {
                // Check for immediate messages to send
                val message = messageSendChannel.receiveCatching().getOrNull()
                if (message != null && connectionStatus.value == ConnectionStatus.CONNECTED) {
                    sendMessageInternal(message)
                }
                // Small delay to prevent tight loop
                delay(10)
            }
        }
    }

    /**
     * Monitor connection state and handle reconnections
     */
    private fun startConnectionMonitor() {
        scope.launch {
            while (isActive) {
                // Simple heuristic: try to stay connected if we're supposed to be active
                // In a real app, this would check if the agent should be running
                val shouldMaintainConnection = /* TODO: Implement proper logic */ true
                
                if (shouldMaintainConnection && 
                    connectionStatus.value != ConnectionStatus.CONNECTING &&
                    connectionStatus.value != ConnectionStatus.CONNECTED) {
                    connect()
                }
                
                delay(5000) // Check every 5 seconds
            }
        }
    }

    /**
     * Get a placeholder auth token - in real implementation this would come from secure storage
     */
    private fun getAuthTokenPlaceholder(): String {
        // TODO: Implement proper token retrieval from Secure Storage
        return "dev-token-placeholder"
    }

    /**
     * Cleanup resources
     */
    private fun cleanup() {
        disconnect()
        job.cancel()
        messageSendChannel.close()
    }

    /**
     * Check if coroutine is still active
     */
    private val isActive: Boolean
        get() = !job.isCancelled

}
