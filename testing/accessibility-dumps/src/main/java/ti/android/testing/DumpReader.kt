package ti.android.testing

import kotlinx.serialization.json.Json
import ti.android.model.task.AccessibilitySnapshot
import java.io.File

/**
 * Reads accessibility dump JSON files for regression testing.
 * Dumps are sanitized snapshots saved during adapter testing.
 */
class DumpReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(filePath: String): AccessibilitySnapshot {
        val content = File(filePath).readText()
        return json.decodeFromString(content)
    }

    fun listAvailable(dumpDir: String): List<String> {
        val dir = File(dumpDir)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.name }
            ?.sorted() ?: emptyList()
    }
}
