package ti.android.transport

import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Manages heartbeat mechanism for WebSocket connection
 * Sends periodic pings and expects pongs to verify connection health
 */
class HeartbeatManager(
    private val sendHeartbeat: () -> Unit,
    private val onMissedPong: () -> Unit,
    private val intervalMillis: Long = 30000, // 30 seconds
    private val timeoutMillis: Long = 10000   // 10 seconds
) {

    private var job: Job? = null
    private var timeoutJob: Job? = null
    private var isActive = false

    /**
     * Start the heartbeat mechanism
     */
    fun start() {
        if (isActive) return
        isActive = true
        
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(intervalMillis)
                if (!isActive) break
                
                sendHeartbeat()
                
                // Set up timeout for pong response
                timeoutJob?.cancel()
                timeoutJob = CoroutineScope(Dispatchers.IO).launch {
                    delay(timeoutMillis)
                    if (isActive) {
                        // No pong received within timeout
                        Timber.w("Heartbeat timeout - no pong received")
                        onMissedPong()
                    }
                }
            }
        }
    }

    /**
     * Stop the heartbeat mechanism
     */
    fun stop() {
        isActive = false
        job?.cancel()
        timeoutJob?.cancel()
        job = null
        timeoutJob = null
    }

    /**
     * Call when a pong is received to reset the timeout
     */
    fun onPongReceived() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
}