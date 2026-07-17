package ti.android.testing

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import ti.android.model.task.*
import java.net.ServerSocket

/**
 * Fake Gateway Server for testing WSS communication.
 * Simulates the Device Gateway for development/testing.
 */
class FakeGatewayServer(private val port: Int = 9999) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _receivedMessages = MutableStateFlow<List<String>>(emptyList())
    val receivedMessages: StateFlow<List<String>> = _receivedMessages.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun start() {
        scope.launch {
            serverSocket = ServerSocket(port)
            // Simple TCP echo server for testing
            while (isActive) {
                try {
                    val client = serverSocket?.accept() ?: break
                    launch {
                        val reader = client.getInputStream().bufferedReader()
                        val writer = client.getOutputStream().bufferedWriter()
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            _receivedMessages.value = _receivedMessages.value + line
                            // Echo back registration confirmation
                            if (line.contains("device.register")) {
                                writer.write("""{"type":"device.registered","deviceId":"test-device","sessionToken":"test-token"}""")
                                writer.newLine()
                                writer.flush()
                            }
                            // Echo back heartbeat ACK
                            if (line.contains("device.heartbeat")) {
                                writer.write("""{"type":"device.heartbeat_ack"}""")
                                writer.newLine()
                                writer.flush()
                            }
                        }
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        scope.cancel()
    }

    fun sendTask(envelope: TaskEnvelope) {
        // Send to connected clients
    }
}
