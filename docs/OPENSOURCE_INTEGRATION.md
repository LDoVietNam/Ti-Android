# 🤖 Tổng hợp Open-Source Android Repo — Tích hợp vào Ti-Android

> **Mục tiêu:** Tìm kiếm, phân tích và đề xuất tích hợp các repo mã nguồn mở mạnh nhất vào Ti-Android APK
> **Ngày:** 2026-07-14
> **Trạng thái:** Research & Recommendation

---

## 📊 Tổng quan các repo được nghiên cứu

| # | Repo | Stars | Tác giả | Chức năng chính | Mức độ tích hợp |
|---|------|-------|---------|-----------------|:---:|
| 1 | [AppAgent](https://github.com/TencentQQGYLab/AppAgent) | ~6k | Tencent | Multimodal GUI Agent | ⚡ Cao |
| 2 | [CogAgent](https://github.com/THUDM/CogAgent) | ~5k | Tsinghua/Zhipu AI | VLM GUI Agent | ⚡ Cao |
| 3 | [AutoDroid](https://github.com/MobileLLM/AutoDroid) | ~2k | MobileLLM | LLM + Widget Tree | ⚡ Cao |
| 4 | [DigiRL](https://github.com/DigiRL-agent/digirl) | ~1.5k | UC Berkeley/DeepMind | Offline-to-Online RL | 🟡 Trung bình |
| 5 | [AndroidEnv](https://github.com/google-deepmind/android_env) | ~1k | Google DeepMind | RL Environment | 🟡 Trung bình |
| 6 | [UIAutomator2](https://github.com/openatx/uiautomator2) | ~7k | OpenATX | Python Android automation | 🔧 Tham khảo |
| 7 | [Scrcpy](https://github.com/Genymobile/scrcpy) | ~115k | Genymobile | Screen mirroring + input | 🔧 Tham khảo |
| 8 | [ML Kit OCR](https://developers.google.com/ml-kit/vision/text-recognition) | SDK | Google | On-device OCR | ✅ **Tích hợp ngay** |
| 9 | [PaddleOCR Android](https://github.com/PaddlePaddle/PaddleOCR) | ~45k | Baidu | Document OCR | 🟡 Có thể |
| 10 | [Tesseract Android](https://github.com/adaptech-cz/tesseract4android) | ~2k | Adaptech | OCR engine | 🟡 Có thể |
| 11 | [Octopus v2](https://github.com/NexaAI/octopus-v2) | ~1k | NexaAI | On-device function calling | 🟡 Trung bình |
| 12 | [Ollama Android](https://github.com/ollama/ollama) | ~110k | Ollama | On-device LLM (Termux) | 🟡 Trung bình |
| 13 | [DroidTask](https://github.com/Stanford-Mobisocial-IoT-Lab/DroidTask) | ~500 | Stanford | Task-oriented mobile agent | ⚡ Cao |
| 14 | [Mobile-Eyes](https://github.com/ChihoonLee93/Mobile-Eyes) | ~300 | ChihoonLee | Accessibility + Vision agent | 🟡 Trung bình |
| 15 | [MCP Android](https://modelcontextprotocol.io) | Standard | Anthropic | Model Context Protocol cho mobile | ⚡ Cao |

---

## 🎯 Đề xuất tích hợp theo module Ti-Android

### 1. `accessibility-runtime/` — Cốt lõi automation

#### ⚡ AppAgent — Key Learnings

**Repo:** https://github.com/TencentQQGYLab/AppAgent

**Ý tưởng tích hợp:**

```
AppAgent Exploration Phase → Ti-Android Learning Mode
  → Agent tự khám phá UI app → ghi lại locator catalog
  → Tạo regression snapshots tự động
  
AppAgent Deployment Phase → Ti-Android Task Execution  
  → Multi-step planning với reflection
  → Grid-based click cho UI element không có resourceId
```

**Code mẫu tích hợp NodeMatcher:**

```kotlin
// Lấy cảm hứng từ AppAgent's grid-based interaction
class GridBasedClicker {
    /**
     * Grid overlay strategy:
     * Chia màn hình thành grid 8×12 ô
     * Dùng LLM/VLM để chọn ô chứa target
     * Click vào tâm ô đó
     */
    fun getGridPosition(xRatio: Float, yRatio: Float): GridCell {
        val cols = 12; val rows = 8
        val col = (xRatio * cols).toInt().coerceIn(0, cols - 1)
        val row = (yRatio * rows).toInt().coerceIn(0, rows - 1)
        return GridCell(row, col)
    }
}
```

**Giá trị:** Bổ sung **grid-based interaction** cho node không có resourceId.

---

#### ⚡ AutoDroid — Widget Tree Integration

**Repo:** https://github.com/MobileLLM/AutoDroid

**Ý tưởng:** Kết hợp accessibility tree với screenshot và gửi **cả hai** lên LLM để grounding chính xác hơn.

```kotlin
// AutoDroid-inspired dual-input observation
data class AutoDroidObservation(
    val accessibilityTree: AccessibilitySnapshot,
    val screenshotHash: String,         // Chỉ gửi hash để cache
    val widgetMap: Map<String, Rect>,   // ResourceId → bounds
    val hierarchySummary: String        // Tree dạng text cho LLM prompt
)
```

**Cải tiến cho Ti-Android:**
- Khi match node không đủ confidence → fallback sang **widget tree + coordinate** style AutoDroid
- Tạo `hierarchySummary` dạng text ngắn gọn để gửi lên LLM, giảm tokens

---

### 2. `vision-runtime/` — OCR & Visual Grounding

#### ✅ ML Kit OCR — Tích hợp NGAY

**SDK:** Google ML Kit Text Recognition

**Tại sao chọn:**
- ✅ On-device, không cần internet
- ✅ API 21+, phủ hầu hết thiết bị
- ✅ Chữ Việt Nam (Latin), Nhật, Trung, Hàn
- ✅ 2 mode: **Regular** (chữ in) và **ML Kit Document** (layout phức tạp)
- ✅ Miễn phí, không giới hạn request

**Code mẫu tích hợp vào OcrEngine.kt:**

```kotlin
// ML Kit Text Recognition Integration
class MlkOcrEngine(private val context: Context) {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.Builder()
            .setLanguage("vi")  // Hỗ trợ tiếng Việt
            .build()
        )
    }

    suspend fun recognize(inputImage: InputImage): OcrResult {
        return withContext(Dispatchers.Default) {
            val task = recognizer.process(inputImage)
            Tasks.await(task).let { visionText ->
                OcrResult(
                    fullText = visionText.text,
                    blocks = visionText.textBlocks.map { block ->
                        OcrBlock(
                            text = block.text,
                            bounds = Rect(
                                block.boundingBox.left,
                                block.boundingBox.top,
                                block.boundingBox.right,
                                block.boundingBox.bottom
                            ),
                            lines = block.lines.map { line ->
                                OcrLine(line.text, line.elements.map { it.text })
                            }
                        )
                    },
                    confidence = 0.85f  // ML Kit không trả confidence, dùng default
                )
            }
        }
    }
}
```

**Thêm dependency vào `app/build.gradle.kts`:**
```kotlin
implementation("com.google.mlkit:text-recognition-vietnamese:16.0.2")
```

---

#### 🔄 PaddleOCR Android — Document OCR cho use case nặng

**Repo:** https://github.com/PaddlePaddle/PaddleOCR

**Khi nào dùng:**
- Khi ML Kit không đủ cho layout phức tạp (hóa đơn, biểu mẫu)
- Khi cần multilingual mạnh hơn (Việt + Trung + Nhật cùng lúc)
- Khi cần bounding box chính xác hơn

**Cách tích hợp (P1 - sau MVP):**
```kotlin
// PaddleOCR Lite integration
// Sử dụng PaddleLite + PaddleOCR model quantized
class PaddleOcrEngine(private val context: Context) {
    private var predictor: PaddlePredictor? = null

    suspend fun init() {
        // Load model từ assets/
        val config = PaddlePredictor.PaddleConfigBuilder()
            .setModelDir("models/paddle_ocr")
            .setThreadNum(4)
            .build()
        predictor = PaddlePredictor.createPaddlePredictor(config)
    }

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        // Preprocess, run inference, postprocess
        // ...
    }
}
```

---

### 3. `policy-engine/` & `runtime-core/` — RL & Planning

#### ⚡ DigiRL — Error Recovery & Learning

**Repo:** https://github.com/DigiRL-agent/digirl

**Ý tưởng:** Dùng **Offline-to-Online RL** để:
1. Học từ trajectory thành công trước đó
2. Tự động điều chỉnh policy theo thiết bị thật

```kotlin
// DigiRL-inspired Recovery Learning
class DigiRLRecovery(
    private val trajectoryDb: TrajectoryDatabase
) {
    /**
     * Khi action thất bại, tra cứu trajectory tương tự 
     * đã thành công trước đó để học cách recover
     */
    suspend fun findRecoveryStrategy(
        failedAction: DeviceAction,
        currentObservation: AccessibilitySnapshot
    ): RecoveryStrategy? {
        val similarTrajectories = trajectoryDb.findSimilar(
            action = failedAction,
            topK = 3
        )
        // Phân tích pattern thành công → đề xuất recovery
        return similarTrajectories
            .filter { it.outcome == "success" }
            .map { it.recoveryStrategy }
            .firstOrNull()
    }
}
```

**Giá trị:** Ti-Android học từ lỗi, không lặp lại cùng một sai lầm.

---

### 4. `transport/` & `adapter-sdk/` — MCP Integration

#### ⚡ MCP (Model Context Protocol) cho Mobile

**Docs:** https://modelcontextprotocol.io

**Mobile MCP Architecture đề xuất:**

```
┌─────────────────────────────┐
│  Ti-Android MCP Client      │
│  ┌───────────────────────┐  │
│  │ System Tools          │  │
│  │  - get_screen_tree    │  │
│  │  - click_element      │  │
│  │  - input_text         │  │
│  │  - scroll_view        │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ App Adapter MCP       │  │
│  │  - telegram_send_msg  │  │
│  │  - telegram_read_chat │  │
│  │  - zalo_read_notif    │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
           │ WSS/MCP
           ▼
┌─────────────────────────────┐
│  MCP Gateway (Server)       │
│  Trên OmniRoute/TiBrain    │
└─────────────────────────────┘
```

**Cách tích hợp:**
- Sử dụng MCP để expose capability của Android dưới dạng **tools**
- LLM trên server có thể gọi `click_element`, `input_text`, `read_screen` qua MCP
- Policy Engine vẫn là gatekeeper trước khi action thực thi

---

### 5. `runtime-core/` — Task Planning & Reflection

#### ⚡ CogAgent — Visual Planning

**Repo:** https://github.com/THUDM/CogAgent

**Ý tưởng:** CogAgent là VLM 18B được fine-tune riêng cho GUI automation.

```
CogAgent Visual Understanding
  → Hiểu icon, button, layout qua pixel (không cần XML)
  → Grounding chính xác ở 1120×1120 resolution
  
Tích hợp vào Ti-Android:
  → Khi accessibility tree không đủ (ảnh, canvas, WebView)
  → Fallback gửi screenshot region lên CogAgent qua Vision API
  → Nhận về action + tọa độ chính xác
```

**Code mẫu VisionGroundingClient:**

```kotlin
// CogAgent-inspired visual grounding
class CogAgentGroundingClient(
    private val routerApi: RouterApi
) {
    data class GroundingResult(
        val actionType: String,       // "tap", "type", "swipe"
        val targetRegion: Rect,       // Bounding box
        val confidence: Float,
        val reasoning: String
    )

    /**
     * Gửi screenshot region lên VLM để grounding
     * Fallback khi accessibility tree không đủ
     */
    suspend fun groundAction(
        intent: String,
        screenshotRegion: Bitmap
    ): GroundingResult {
        val response = routerApi.vlmQuery(
            model = "cogagent-9b",  // Hoặc CogAgent-18B
            systemPrompt = """
                You are a GUI agent. Given a screenshot region,
                find the element for: "$intent"
                Return format: {"action":"tap","x":0.5,"y":0.3,"reasoning":"..."}
            """.trimIndent(),
            image = screenshotRegion
        )
        return parseResponse(response)
    }
}
```

---

### 6. `persistence/` & `telemetry/` — Trajectory Database

#### 🔄 AutoDroid Trajectory Format

Học từ AutoDroid cách lưu trajectory để replay và fine-tune:

```kotlin
// AutoDroid-inspired trajectory schema (Room Entity)
@Entity(
    tableName = "trajectories",
    indices = [
        Index("taskType", "outcome"),
        Index("packageName"),
        Index(value = ["taskType", "outcome", "appVersion"])
    ]
)
data class TrajectoryEntity(
    @PrimaryKey val id: String,
    val taskType: String,           // "compose_message", "send_message"
    val packageName: String,        // "org.telegram.messenger"
    val appVersion: String,
    val steps: String,              // JSON: List<ActionStep>
    val outcome: String,            // "success", "failure", "ambiguous"
    val durationMs: Long,
    val metadata: String,           // JSON: device info, policy used
    val createdAt: Long
)

data class ActionStep(
    val stepIndex: Int,
    val action: DeviceAction,
    val observationBefore: String,  // Hash of snapshot
    val locatorScore: Double,
    val observationAfter: String,
    val success: Boolean,
    val errorCode: String?
)
```

---

### 7. `testing/` — Replay & Benchmark

#### ⚡ AndroidEnv + Fixture Framework

**Repo:** https://github.com/google-deepmind/android_env

**Ý tưởng:** Dùng AndroidEnv-style environment để:
1. Test adapter regression tự động
2. Benchmark success rate qua các app version
3. So sánh locator strategies

```kotlin
// AndroidEnv-inspired test harness
class TiAndroidTestEnv(
    private val deviceId: String,
    private val fixtureApp: String
) {
    suspend fun runBenchmark(
        testCases: List<TestCase>,
        maxRetries: Int = 3
    ): BenchmarkReport {
        val results = testCases.map { tc ->
            val attempts = (1..maxRetries).map { attempt ->
                val outcome = runSingle(tc)
                outcome
            }
            TestResult(tc, attempts)
        }
        return BenchmarkReport(
            totalTests = testCases.size,
            successRate = results.count { it.passed } / testCases.size.toDouble(),
            averageDuration = results.map { it.durationMs }.average(),
            details = results
        )
    }
}
```

---

## 🗺️ Roadmap tích hợp

### Phase 1 — Ngay lập tức (P0)

| Repo | Module | Việc cần làm |
|------|--------|-------------|
| **ML Kit OCR** | `vision-runtime/OcrEngine.kt` | Thay thế OCR stub hiện tại bằng ML Kit thật |
| **UIAutomator2 patterns** | `accessibility-runtime/NodeMatcher.kt` | Cải tiến locator scoring (tham khảo UIAutomator predicate) |

### Phase 2 — Sau MVP (P1)

| Repo | Module | Việc cần làm |
|------|--------|-------------|
| **AppAgent** | `accessibility-runtime/` + `adapter-sdk/` | Grid-based click + Exploration phase |
| **AutoDroid** | `runtime-core/TaskRuntime.kt` | Dual-input observation (tree + widget map) |
| **MCP** | `transport/` | MCP tool bridge cho Android capabilities |
| **Octopus v2** | `adapter-sdk/` | On-device function calling cho app adapter |

### Phase 3 — Mở rộng (P2)

| Repo | Module | Việc cần làm |
|------|--------|-------------|
| **DigiRL** | `policy-engine/` + `persistence/` | Trajectory learning + auto recovery |
| **CogAgent** | `vision-runtime/VisionGroundingClient.kt` | VLM-based visual grounding |
| **AndroidEnv** | `testing/` | Benchmark framework + regression suite |
| **PaddleOCR** | `vision-runtime/OcrEngine.kt` | Document OCR cho use case nặng |

---

## ⚖️ So sánh chi tiết và quyết định

### OCR Engine Decision

| Tiêu chí | ML Kit OCR | PaddleOCR | Tesseract Android |
|----------|:----------:|:---------:|:-----------------:|
| On-device | ✅ | ✅ | ✅ |
| Tiếng Việt | ✅ | ✅ | ✅ |
| Real-time | ✅ | 🟡 Trung bình | ❌ Chậm |
| Layout complex | 🟡 Trung bình | ✅ Tốt | ❌ Kém |
| APK size impact | +1MB | +15MB | +8MB |
| API Level | 21+ | 21+ | 21+ |
| Google Play policy | ✅ OK | ✅ OK | ✅ OK |
| **Decision** | **✅ Phase 1** | 🟡 Phase 3 | ❌ Không dùng |

→ **Kết luận:** Dùng **ML Kit OCR** cho Phase 1, **PaddleOCR** cho Phase 3 nếu cần

### GUI Agent Architecture Decision

| Tiêu chí | AppAgent | AutoDroid | CogAgent |
|----------|:--------:|:---------:|:--------:|
| Visual hiểu icon/button | ✅ | 🟡 Cần tree | ✅ Tốt nhất |
| Accessibility tree | ✅ Dùng | ✅ Dùng chính | 🟡 Phụ |
| Widget không resourceId | ✅ Grid | 🟡 Ko rõ | ✅ Pixel |
| On-device | ❌ GPT-4V | 🟡 LLM/Tiny | ❌ 18B VLM |
| Latency | ⚡ Nhanh | 🟡 Trung bình | 🐢 Chậm |
| **Mức độ ưu tiên** | **P1** | **P1** | **P3** |

→ **Kết luận:** AppAgent pattern cho P1, CogAgent cho P3 khi có server-side VLM

---

## 💻 Tổng kết ưu tiên

```
NGAY (tuần này)
├── ML Kit OCR → thay OcrEngine stub
├── UIAutomator2 locator patterns → cải tiến NodeMatcher
└── Tham khảo AppAgent action reflection → cải tiến TaskStateMachine

TUẦN SAU
├── AppAgent grid-based click
├── AutoDroid dual-input observation
└── MCP exposure cho Android tools

THÁNG SAU
├── DigiRL trajectory learning
├── CogAgent visual grounding
├── AndroidEnv regression benchmark
└── PaddleOCR document use case
```

---

## 📚 Link tham khảo

| Repo | Link |
|------|------|
| AppAgent | https://github.com/TencentQQGYLab/AppAgent |
| CogAgent | https://github.com/THUDM/CogAgent |
| AutoDroid | https://github.com/MobileLLM/AutoDroid |
| DigiRL | https://github.com/DigiRL-agent/digirl |
| AndroidEnv | https://github.com/google-deepmind/android_env |
| UIAutomator2 | https://github.com/openatx/uiautomator2 |
| Scrcpy | https://github.com/Genymobile/scrcpy |
| ML Kit OCR | https://developers.google.com/ml-kit/vision/text-recognition |
| PaddleOCR | https://github.com/PaddlePaddle/PaddleOCR |
| Octopus v2 | https://github.com/NexaAI/octopus-v2 |
| Ollama Android | https://github.com/ollama/ollama |
| MCP | https://modelcontextprotocol.io |
| AppAgent Paper | https://arxiv.org/abs/2312.13771 |
| CogAgent Paper | https://arxiv.org/abs/2312.08914 |
| DigiRL Paper | https://arxiv.org/abs/2406.11896 |
| AutoDroid Paper | https://arxiv.org/abs/2308.16175 |
