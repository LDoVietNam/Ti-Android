package ti.android.transport

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random
import timber.log.Timber

/**
 * Handles reconnection logic with exponential backoff and jitter
 */
class ReconnectPolicy(
    private val maxAttempts: Int = 5,
    private val baseDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000,
    private val jitterFactor: Double = 0.1
) {

    private var attemptCount = 0

    /**
     * Calculate delay for next reconnection attempt
     * @return delay in milliseconds, or -1 if max attempts exceeded
     */
    fun getNextDelay(): Long {
        if (attemptCount >= maxAttempts) {
            return -1 // Indicates max attempts reached
        }

        // Exponential backoff: base * 2^attempt
        val exponentialDelay = baseDelayMs * (2.0.pow(attemptCount))
        
        // Apply jitter to prevent thundering herd
        val jitter = (Math.random() * 2 - 1) * jitterFactor * exponentialDelay
        var delayed = (exponentialDelay + jitter).toLong()
        
        // Ensure within bounds
        delayed = max(baseDelayMs, min(delayed, maxDelayMs))
        
        attemptCount++
        return delayed
    }

    /**
     * Reset the attempt counter (called on successful connection)
     */
    fun reset() {
        attemptCount = 0
    }

    /**
     * Check if we can still attempt reconnection
     */
    fun canAttempt(): Boolean {
        return attemptCount < maxAttempts
    }

    /**
     * Get current attempt count
     */
    fun getAttemptCount(): Int {
        return attemptCount
    }
}

/**
 * Extension for power operation
 */
private fun Double.pow(exponent: Double): Double {
    return Math.pow(this, exponent)
}