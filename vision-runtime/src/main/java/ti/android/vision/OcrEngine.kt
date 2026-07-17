package ti.android.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

// ─── OCR Result ───────────────────────────────────────────────────

@Serializable
data class OcrResult(
    val text: String,
    val confidence: Double,
    val boundingBox: BoundingBox? = null,
    val lines: List<OcrLine> = emptyList(),
)

@Serializable
data class OcrLine(
    val text: String,
    val confidence: Double,
    val boundingBox: BoundingBox,
)

@Serializable
data class BoundingBox(
    val left: Int, val top: Int, val right: Int, val bottom: Int,
) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    fun relativeTo(width: Int, height: Int) = BoundingBox(
        left = (left.toFloat() / width * 1000).toInt(),
        top = (top.toFloat() / height * 1000).toInt(),
        right = (right.toFloat() / width * 1000).toInt(),
        bottom = (bottom.toFloat() / height * 1000).toInt(),
    )
}

// ─── OCR Engine ───────────────────────────────────────────────────

class OcrEngine {
    private var isInitialized = false

    suspend fun initialize() = withContext(Dispatchers.Default) {
        // TODO: Initialize ML Kit Text Recognition
        isInitialized = true
    }

    suspend fun recognize(imageData: ByteArray, width: Int, height: Int): OcrResult {
        // TODO: Run ML Kit OCR on image
        return OcrResult(text = "", confidence = 0.0)
    }
}

// ─── Image Redactor ───────────────────────────────────────────────

class ImageRedactor {
    private val sensitiveRegions = mutableListOf<BoundingBox>()

    fun addRedactionRegion(box: BoundingBox) {
        sensitiveRegions.add(box)
    }

    fun redact(imageData: ByteArray): ByteArray {
        // TODO: Apply redaction to image
        return imageData
    }
}

// ─── Frame Sampler ────────────────────────────────────────────────

class FrameSampler {
    private var lastCaptureTime = 0L
    private val minIntervalMs = 500L

    fun shouldCapture(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < minIntervalMs) return false
        lastCaptureTime = now
        return true
    }
}

// ─── Vision Grounding Client ──────────────────────────────────────

class VisionGroundingClient(private val baseUrl: String = "") {

    suspend fun ground(imageData: ByteArray, targetDescription: String): BoundingBox? {
        if (baseUrl.isBlank()) return null
        // TODO: Send image + description to remote VLM for coordinate grounding
        return null
    }
}
