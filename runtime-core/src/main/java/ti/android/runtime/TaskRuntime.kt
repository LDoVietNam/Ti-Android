package ti.android.runtime

import ti.android.model.task.*
import ti.android.policy.*
import ti.android.adapter.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─── Task State Machine ───────────────────────────────────────────

class TaskStateMachine {
    private val _currentState = MutableStateFlow(TaskStatus.RECEIVED)
    val currentState: StateFlow<TaskStatus> = _currentState.asStateFlow()

    private val validTransitions = mapOf(
        TaskStatus.RECEIVED to listOf(TaskStatus.VALIDATING, TaskStatus.REJECTED),
        TaskStatus.VALIDATING to listOf(TaskStatus.ACCEPTED, TaskStatus.REJECTED),
        TaskStatus.ACCEPTED to listOf(TaskStatus.PREPARING),
        TaskStatus.PREPARING to listOf(TaskStatus.OBSERVING),
        TaskStatus.OBSERVING to listOf(TaskStatus.PLANNING),
        TaskStatus.PLANNING to listOf(TaskStatus.POLICY_CHECK),
        TaskStatus.POLICY_CHECK to listOf(TaskStatus.WAITING_APPROVAL, TaskStatus.EXECUTING, TaskStatus.BLOCKED),
        TaskStatus.WAITING_APPROVAL to listOf(TaskStatus.EXECUTING, TaskStatus.CANCELLED),
        TaskStatus.EXECUTING to listOf(TaskStatus.VERIFYING, TaskStatus.CANCELLED),
        TaskStatus.VERIFYING to listOf(TaskStatus.COMPLETED, TaskStatus.RECOVERING, TaskStatus.FAILED),
        TaskStatus.RECOVERING to listOf(TaskStatus.OBSERVING, TaskStatus.FAILED),
        TaskStatus.REJECTED to emptyList(),
        TaskStatus.BLOCKED to emptyList(),
        TaskStatus.CANCELLED to emptyList(),
        TaskStatus.COMPLETED to emptyList(),
        TaskStatus.FAILED to emptyList(),
    )

    fun transitionTo(newState: TaskStatus): Boolean {
        val current = _currentState.value
        val allowed = validTransitions[current] ?: emptyList()
        if (newState in allowed) {
            _currentState.value = newState
            return true
        }
        return false
    }

    fun isTerminal(): Boolean = _currentState.value in listOf(
        TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.BLOCKED, TaskStatus.REJECTED
    )
}

// ─── Action Pipeline ──────────────────────────────────────────────

class ActionPipeline {
    private val preconditions = mutableListOf<suspend (DeviceAction) -> Boolean>()
    private val postconditions = mutableListOf<suspend (DeviceAction, Boolean) -> Unit>()

    fun addPrecondition(check: suspend (DeviceAction) -> Boolean) {
        preconditions.add(check)
    }

    fun addPostcondition(handler: suspend (DeviceAction, Boolean) -> Unit) {
        postconditions.add(handler)
    }

    suspend fun execute(action: DeviceAction, executor: suspend (DeviceAction) -> Boolean): Boolean {
        // Check preconditions
        for (check in preconditions) {
            if (!check(action)) return false
        }
        // Execute
        val result = executor(action)
        // Run postconditions
        for (handler in postconditions) {
            handler(action, result)
        }
        return result
    }
}

// ─── Recovery Coordinator ─────────────────────────────────────────

class RecoveryCoordinator(private val maxRetries: Int = 2) {
    private val retryCounts = mutableMapOf<String, Int>()

    fun shouldRetry(actionId: String): Boolean {
        val count = retryCounts.getOrDefault(actionId, 0)
        return count < maxRetries
    }

    fun recordRetry(actionId: String) {
        retryCounts[actionId] = retryCounts.getOrDefault(actionId, 0) + 1
    }

    fun reset() { retryCounts.clear() }
}
