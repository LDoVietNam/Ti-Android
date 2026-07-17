package ti.android.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber

/**
 * Low-level WebSocket session handler
 * Manages the WebSocket connection and message queuing
 */
class WebSocketSession(
    private val webSocket: WebSocket,
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionClosed: (Int, String) -> Unit,
    private val onConnectionFailed: (Throwable) -> Unit
) {

    // Channels for sending/receiving messages
    private val sendChannel = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val job = Job()
    private var isClosed = false

    init {
        // Start message sender and receiver coroutines
        scope.launch {
            messageSender()
        }
        scope.launch {
            messageReceiver()
        }
    }

    /**
     * Send a message through the WebSocket
     */
    suspend fun send(message: String) {
        if (!isClosed) {
            sendChannel.send(message)
        }
    }

    /**
     * Close the WebSocket connection
     */
    fun close() {
        if (!isClosed) {
            isClosed = true
            webSocket.close(1000, "Normal closure")
            job.cancel()
            sendChannel.close()
        }
    }

    /**
     * Coroutine to send messages from the channel to WebSocket
     */
    private fun messageSender() {
        scope.launch {
            while (isActive && !isClosed) {
                try {
                    val message = sendChannel.receive()
                    webSocket.send(message)
                    Timber.d("WebSocket sent: $message")
                } catch (e: Exception) {
                    if (!isClosed) {
                        Timber.e(e, "Error sending WebSocket message")
                        onConnectionFailed(e)
                    }
                }
            }
        }
    }

    /**
     * Coroutine to receive messages from WebSocket and pass to channel
     */
    private fun messageReceiver() {
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                // Send received message to the onMessageReceived callback
                // Using launch to avoid blocking the WebSocket thread
                CoroutineScope(Dispatchers.Main).launch {
                    onMessageReceived(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                // Don't propagate close here, let onClosed handle it
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                if (!isClosed) {
                    isClosed = true
                    onConnectionClosed(code, reason)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                super.onFailure(webSocket, t, response)
                if (!isClosed) {
                    isClosed = true
                    onConnectionFailed(t)
                }
            }
        }

        // Note: The WebSocket already has its listener set when created
        // This is just to show the pattern - in practice we'd use the existing listener
    }
}