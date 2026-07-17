package ti.android.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ti.android.adapter.AppAdapter
import ti.android.adapter.AdapterContext
import ti.android.model.task.AccessibilitySnapshot
import ti.android.model.task.UiNode

/**
 * Replay Runner for testing adapter changes against recorded accessibility dumps.
 * Useful for regression testing when app UI changes.
 */
class ReplayRunner {
    private val _results = MutableStateFlow<List<ReplayResult>>(emptyList())
    val results: StateFlow<List<ReplayResult>> = _results

    suspend fun run(adapter: AppAdapter, testCases: List<TestCase>): List<ReplayResult> {
        val outcomes = testCases.map { tc ->
            try {
                val context = AdapterContext(
                    packageName = tc.packageName,
                    appVersion = tc.appVersion,
                    accessibilityNodes = tc.snapshot.nodes,
                    screenWidth = tc.snapshot.screenInfo?.width ?: 1080,
                    screenHeight = tc.snapshot.screenInfo?.height ?: 2400,
                )
                val probe = adapter.probe(context)
                val observation = adapter.observe(context)
                val plan = adapter.plan(tc.intent, observation)
                val verify = adapter.verify(plan, observation, observation)

                ReplayResult(
                    testName = tc.name,
                    passed = probe.compatible && plan.actions.isNotEmpty(),
                    adapterVersion = adapter.version,
                    planActions = plan.actions.size,
                    verificationSuccess = verify.success,
                    error = null,
                )
            } catch (e: Exception) {
                ReplayResult(testName = tc.name, passed = false, error = e.message)
            }
        }
        _results.value = outcomes
        return outcomes
    }
}

data class TestCase(
    val name: String,
    val packageName: String,
    val appVersion: String?,
    val snapshot: AccessibilitySnapshot,
    val intent: ti.android.model.task.TaskIntent,
)

data class ReplayResult(
    val testName: String,
    val passed: Boolean,
    val adapterVersion: String? = null,
    val planActions: Int = 0,
    val verificationSuccess: Boolean = false,
    val error: String? = null,
)
