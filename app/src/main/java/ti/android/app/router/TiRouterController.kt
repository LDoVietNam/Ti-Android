package ti.android.app.router

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TiRouterController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val DEFAULT_PORT = 1870
        private const val BINARY_ASSET = "tirouter-android-arm64"
        private const val BINARY_NAME = "ti-router"
        private const val CONFIG_NAME = "tirouter.yaml"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val _state = MutableStateFlow(TiRouterState())
    val state: StateFlow<TiRouterState> = _state.asStateFlow()

    private var process: Process? = null
    private var monitorJob: Job? = null

    fun startProcess() {
        if (process?.isAlive == true) return

        scope.launch {
            _state.value = _state.value.copy(status = Status.STARTING, lastError = null)
            try {
                require(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "aarch64" }) {
                    "TiRouter hiện cần thiết bị ARM64"
                }

                val binary = installBinary()
                val config = installConfig()
                val started = ProcessBuilder(
                    binary.absolutePath,
                    "--config",
                    config.absolutePath,
                )
                    .directory(context.filesDir)
                    .redirectErrorStream(true)
                    .redirectOutput(File(context.filesDir, "ti-router.log"))
                    .start()

                process = started
                _state.value = _state.value.copy(
                    status = Status.STARTING,
                    pid = started.pid(),
                )
                monitorJob?.cancel()
                monitorJob = scope.launch { monitor(started) }
            } catch (error: Throwable) {
                Timber.e(error, "Không thể khởi động TiRouter")
                _state.value = _state.value.copy(
                    status = Status.FAILED,
                    pid = null,
                    lastError = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    fun stopProcess() {
        monitorJob?.cancel()
        monitorJob = null
        process?.let { running ->
            if (running.isAlive) running.destroy()
            if (running.isAlive) running.destroyForcibly()
        }
        process = null
        _state.value = _state.value.copy(status = Status.STOPPED, pid = null, modelCount = 0)
    }

    fun restartProcess() {
        stopProcess()
        startProcess()
    }

    private suspend fun monitor(running: Process) {
        repeat(30) {
            if (!running.isAlive) {
                _state.value = _state.value.copy(
                    status = Status.FAILED,
                    pid = null,
                    lastError = "TiRouter đã thoát với mã ${running.exitValue()}",
                )
                return
            }
            if (probe("/health") || probe("/v1/models")) {
                val models = readModelCount()
                _state.value = _state.value.copy(
                    status = Status.RUNNING,
                    modelCount = models,
                    lastError = null,
                )
                return
            }
            delay(500)
        }

        if (running.isAlive) {
            _state.value = _state.value.copy(status = Status.DEGRADED)
            while (isActive && running.isAlive) {
                delay(5_000)
                if (probe("/health") || probe("/v1/models")) {
                    _state.value = _state.value.copy(
                        status = Status.RUNNING,
                        modelCount = readModelCount(),
                    )
                }
            }
        }
    }

    private fun probe(path: String): Boolean = runCatching {
        (URL("http://127.0.0.1:$DEFAULT_PORT$path").openConnection() as HttpURLConnection).run {
            connectTimeout = 1_500
            readTimeout = 1_500
            requestMethod = "GET"
            responseCode in 200..499
        }
    }.getOrDefault(false)

    private fun readModelCount(): Int = runCatching {
        val connection = URL("http://127.0.0.1:$DEFAULT_PORT/v1/models").openConnection() as HttpURLConnection
        connection.connectTimeout = 1_500
        connection.readTimeout = 1_500
        connection.inputStream.bufferedReader().use { body ->
            Regex("\\\"id\\\"").findAll(body.readText()).count()
        }
    }.getOrDefault(0)

    private fun installBinary(): File {
        val target = File(context.filesDir, "bin/$BINARY_NAME")
        if (!target.exists() || target.length() == 0L) {
            target.parentFile?.mkdirs()
            context.assets.open(BINARY_ASSET).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            check(isElfArm64(target)) { "Artifact TiRouter không phải ELF Android ARM64" }
            check(target.setExecutable(true, true)) { "Không thể cấp quyền execute cho TiRouter" }
        }
        return target
    }

    private fun isElfArm64(file: File): Boolean {
        file.inputStream().use { input ->
            val header = ByteArray(20)
            if (input.read(header) != header.size) return false
            return header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[18] == 0xB7.toByte() && header[19] == 0x00.toByte()
        }
    }

    private fun installConfig(): File {
        val target = File(context.filesDir, "config/$CONFIG_NAME")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            target.writeText(
                """
                port: $DEFAULT_PORT
                host: 127.0.0.1
                """.trimIndent(),
            )
        }
        return target
    }
}
