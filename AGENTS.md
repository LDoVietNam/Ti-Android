# Ti-Android — Chính sách Đại lý (Agent Policy)

> **Mobile Execution Node cho hệ sinh thái Ti.**
> Ngôn ngữ: **Kotlin** | UI: **Jetpack Compose** | Build: **Gradle Kotlin DSL**
>
> Kế thừa toàn bộ quy tắc từ `Z:\AGENTS.md` và `apps/AGENTS.md`.

---

## 1. Tổng quan & Vai trò

**Ti Android Runtime Node** là ứng dụng Android đóng vai trò **mobile execution node** trong hệ sinh thái Ti. Ứng dụng nhận tác vụ từ Ti Device Gateway, quan sát giao diện Android, đề xuất hành động, yêu cầu xác nhận khi cần, thực thi và gửi kết quả về hệ thống trung tâm.

### Vai trò trong Ti Ecosystem

| Khía cạnh | Chi tiết |
|---|---|
| **Loại** | Native Android App (APK/AAB) |
| **Vai trò** | Mobile execution node, UI automation |
| **Kết nối** | WSS → Device Gateway → OmniRoute/TiBrain |
| **Giao tiếp** | WSS persistent + HTTPS, protocol version `1.0` |
| **App ID** | `ti.android.runtime` |
| **Min SDK** | API 26+ (Android 8.0) |
| **Target SDK** | API 35+ (Android 15) |

### Nguyên tắc thiết kế

| Nguyên tắc | Mô tả |
|---|---|
| **Accessibility-first** | Dùng accessibility tree làm nguồn chính |
| **Vision fallback** | MediaProjection + OCR/VLM khi accessibility không đủ |
| **Policy-controlled** | Mọi action qua Policy Engine |
| **Capability-driven** | Gateway chỉ gửi task phù hợp capability |
| **Human-in-the-loop** | Action nguy hiểm cần approval |
| **Observer pattern** | Observe → Plan → Approve → Execute → Verify |

---

## 2. Kiến trúc & Thành phần

### 2.1 Flow kiến trúc

```
┌─────────────────────────────────────────────────────────────┐
│                    Ti Orchestrator / TiBrain                  │
│                              │                                │
│                    OmniRoute / Task Router                   │
│                              │                                │
│                      Ti Device Gateway                       │
│                          WSS │                                │
└──────────────────────────────┼───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                   Ti Android Runtime Node                     │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │  Transport   │  │  Policy      │  │  Runtime Core     │   │
│  │  (WSS+HTTP)  │◄─┤  Engine      │◄─┤  (TaskRuntime)   │   │
│  └──────┬───────┘  └──────────────┘  └────────┬─────────┘   │
│         │                                      │              │
│  ┌──────▼──────────────────────────────────────▼──────────┐  │
│  │              Execution Runtime                          │  │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌──────┐ ┌───────┐  │  │
│  │  │Access. │ │Intent  │ │Notif.  │ │Vision│ │App    │  │  │
│  │  │Runtime │ │Runtime │ │Runtime │ │Runtime│ │Adapter│  │  │
│  │  └────────┘ └────────┘ └────────┘ └──────┘ └───────┘  │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌────────────┐  ┌────────────┐  ┌──────────────────────┐   │
│  │ Telemetry  │  │ Secure     │  │  Persistence (Room)  │   │
│  │ + Audit    │  │ Storage    │  │  + DataStore         │   │
│  └────────────┘  └────────────┘  └──────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

### 2.2 Core Subsystems

| Subsystem | Vị trí | Mô tả |
|---|---|---|
| **Transport** | `transport/` | WSS client, heartbeat, reconnect, outbox |
| **Runtime Core** | `runtime-core/` | TaskRuntime, state machine, action pipeline |
| **Accessibility Runtime** | `accessibility-runtime/` | TiAccessibilityService, node snapshot/normalize |
| **Vision Runtime** | `vision-runtime/` | MediaProjection, OCR, VLM grounding |
| **Intent Runtime** | `intent-runtime/` | App launch, deep link, share send/receive |
| **Notification Runtime** | `notification-runtime/` | TiNotificationListener, policy |
| **Adapter SDK** | `adapter-sdk/` | AppAdapter interface, UiTarget, registry |
| **Policy Engine** | `policy-engine/` | Risk classification, approval gateway |
| **Secure Storage** | `secure-storage/` | Keystore, session token, device identity |
| **Persistence** | `persistence/` | Room DB (Task, Event, Outbox DAOs) |
| **Telemetry** | `telemetry/` | Trace, event, metrics, audit, snapshot |
| **Core Model** | `core-model/` | Task, capability, event, action data classes |

### 2.3 Module Dependencies

```
app (Application + Compose UI)
├── core-model (Task, Capability, Event, Action)
├── runtime-core (TaskRuntime, StateMachine)
├── transport (GatewayClient, WSS)
├── accessibility-runtime (AccessibilityService)
├── vision-runtime (MediaProjection, OCR)
├── intent-runtime (AppLauncher, Share)
├── notification-runtime (NotificationListener)
├── adapter-sdk (AppAdapter interface)
├── policy-engine (PolicyEvaluator, Approval)
├── secure-storage (Keystore, Session)
├── persistence (Room Database)
└── telemetry (Trace, Audit)

> **Termux services port mapping:** Ollama :11434, Phone AI :5000, File Transfer :5001, Voice Input :5002 (xem Section 8.3)
```

---

## 3. Cấu hình

### 3.1 Environment / Build Config

File: `config/dev.properties.example` → `dev.properties`

```properties
TI_ENV=dev
TI_DEVICE_GATEWAY_WSS=wss://gateway.example.local/device
TI_DEVICE_GATEWAY_HTTPS=https://gateway.example.local
TI_ROUTER_BASE_URL=http://192.168.1.100:20128
TI_MCP_BASE_URL=http://192.168.1.100:1919
TI_DEVICE_ID=ti-android-dev-01
TI_LOG_LEVEL=DEBUG
TI_ALLOW_REMOTE_VISION=false
TI_REQUIRE_APPROVAL_FOR_SEND=true
TI_MAX_TASK_DURATION_SECONDS=120
```

### 3.2 Policy Config

File: `config/policy-default.yaml`

```yaml
# Phân loại rủi ro và yêu cầu approval cho từng action type
# Các mức: allow, confirm, deny
actions:
  compose_message: confirm
  send_message: confirm
  read_notification: allow
  take_screenshot: confirm
  open_app: allow
  click: allow
  type_text: allow
  scroll: allow
```

### 3.3 Build Config Fields

| Field | Debug | Release |
|---|---|---|
| `TI_ENV` | `"dev"` | `"production"` |
| `TI_DEFAULT_GATEWAY_URL` | `wss://gateway.example.local/device` | (configurable) |
| `TI_PROTOCOL_VERSION` | `"1.0"` | `"1.0"` |

---

## 4. Build, Run & Development

### 4.1 Commands

```bash
# === BUILD APK ===
./gradlew assembleDebug                              # Debug APK
./gradlew assembleRelease                             # Release APK (signed)

# === INSTALL ===
./gradlew installDebug                                # Install trên device/emulator

# === TEST ===
./gradlew test                                        # Unit tests
./gradlew connectedCheck                              # Instrumentation tests

# === LINT ===
./gradlew lint                                        # Android lint
./gradlew ktlintCheck                                 # Kotlin lint

# === CLEAN ===
./gradlew clean                                       # Clean build

# === TERMUX SETUP (Android Termux) ===
termux/setup-android-node.sh                          # Full Termux stack setup
```

### 4.2 Yêu cầu Build

| Tool | Mô tả |
|---|---|
| **Android SDK** | API 35+ |
| **JDK** | 17+ |
| **Gradle** | 8.x (wrapper included) |
| **Kotlin** | 2.0+ |
| **Android Studio** | Hedgehog+ (recommended) |

### 4.3 Termux Node Setup

Android Node có thể chạy service qua Termux:

| Service | Port | Mô tả |
|---|---|---|
| **Ollama** | `127.0.0.1:11434` | Local LLM inference |
| **Phone AI** | `127.0.0.1:5000` | Phone AI automation service |
| **File Transfer** | `127.0.0.1:5001` | LAN file transfer |
| **Voice Input** | `127.0.0.1:5002` | Voice recording + transcription |

Script setup: `termux/setup-android-node.sh`

### 4.4 Agent Workflow

Khi làm việc với `Ti-Android/`:

1. **Đọc PLAN.md** — Hiểu roadmap và architecture
2. **Xác định config** — `config/dev.properties.example` cho dev
3. **Build** — `./gradlew assembleDebug`
4. **Chạy test** — `./gradlew test`
5. **Modify module** — Tuân thủ dependency boundary
6. **Ghi log handoff** — Ghi nhận vào `handoff.json`

### 4.5 Code Rules

| Rule | Mô tả |
|---|---|
| **No hard-coded endpoints** | Dùng build config fields hoặc dev.properties |
| **Kotlin-first** | Java chỉ dùng khi thực sự cần |
| **Coroutines** | Async bằng Kotlin Coroutines + Flow |
| **DI** | Dùng Hilt cho dependency injection |
| **Compose UI** | Jetpack Compose cho mọi UI mới |
| **Accessibility-first** | Locator priority: resourceId → role → text → coordinates |
| **No coordinate hardcode** | Không dùng tọa độ cố định làm locator chính |
| **Room** | Local DB qua Room, không SQL raw |
| **Keystore** | Secret storage qua Android Keystore |
| **Timber** | Logging qua Timber (structured) |

---

## 5. Task Protocol

### 5.1 Task Envelope

```json
{
  "protocolVersion": "1.0",
  "messageId": "msg_01",
  "taskId": "task_01",
  "attempt": 1,
  "idempotencyKey": "action:app:conversation-id:hash",
  "target": {
    "deviceId": "ti-android-01",
    "packageName": "org.telegram.messenger",
    "adapter": "telegram",
    "minimumAdapterVersion": "0.1.0"
  },
  "intent": {
    "type": "compose_message",
    "parameters": {
      "conversation": "Dev Team",
      "content": "Bản build đã hoàn thành."
    }
  },
  "policy": {
    "sendRequiresApproval": true,
    "maximumActions": 20,
    "timeoutMs": 60000
  }
}
```

### 5.2 Task States

```
RECEIVED → VALIDATING → ACCEPTED(→ REJECTED)
    ↓
PREPARING → OBSERVING → PLANNING → POLICY_CHECK
    ↓                        ↓
BLOCKED                  WAITING_APPROVAL
                            ↓(→ CANCELLED)
                        EXECUTING → VERIFYING → COMPLETED
                                                  ↓(→ FAILED)
```

### 5.3 Message Types

| Direction | Type | Mô tả |
|---|---|---|
| **Device → Gateway** | `device.register`, `device.capabilities`, `device.heartbeat` | Registration |
| **Device → Gateway** | `task.accept`, `task.reject`, `task.started` | Task lifecycle |
| **Device → Gateway** | `task.progress`, `task.approval_required` | In-progress |
| **Device → Gateway** | `task.completed`, `task.failed` | Result |
| **Gateway → Device** | `task.offer` | New task |
| **Gateway → Device** | `task.cancel` | Cancellation |
| **Gateway → Device** | `session.rotate`, `session.revoke` | Session management |

### 5.4 Capability Report

```json
{
  "deviceId": "ti-android-01",
  "capabilities": {
    "accessibility": {
      "enabled": true,
      "retrieveWindowContent": true,
      "performNodeAction": true,
      "dispatchGesture": true
    },
    "mediaProjection": {
      "available": true,
      "active": false,
      "requiresUserConsent": true
    },
    "notifications": { "enabled": false },
    "shareTarget": { "enabled": true },
    "ocr": { "mode": "local", "languages": ["vi", "en"] },
    "visionGrounding": { "mode": "remote", "enabled": false }
  },
  "runtime": {
    "deviceLocked": false,
    "batteryPercent": 72,
    "network": "wifi",
    "foregroundApp": "org.telegram.messenger"
  }
}
```

---

## 6. Cấu trúc dự án

### 6.1 Source vs Runtime

| Loại | Đường dẫn | Ghi chú |
|---|---|---|
| **Source** | `Z:\01_PROJECTS\apps\Ti-Android\` | Mã nguồn Kotlin/Gradle |
| **APK output** | `app/build/outputs/apk/` | Debug/Release APK |
| **Config** | `config/dev.properties` | Development config (gitignored) |
| **Termux runtime** | `termux/` | Scripts và service cho Termux |

### 6.2 File tree

```
Ti-Android/
├── app/                              # Application module
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/.../app/             # App code (Compose UI)
│   │   └── res/                      # Resources
│   └── build.gradle.kts
├── accessibility-runtime/            # Accessibility Service
├── adapter-sdk/                      # App adapter interface
├── adapters/                         # App-specific adapters
│   ├── generic/                      # Generic UI adapter
│   └── telegram/                     # Telegram adapter (P0)
├── config/
│   ├── dev.properties.example        # Dev config template
│   ├── staging.properties.example    # Staging config template
│   └── policy-default.yaml           # Default policy rules
├── core-model/                       # Data classes
│   ├── task/                         # Task model
│   ├── capability/                   # Capability model
│   ├── event/                        # Event model
│   ├── action/                       # Action model
│   └── observation/                  # Observation model
├── docs/                             # Documentation
├── intent-runtime/                   # App launch, deep link, share
├── notification-runtime/             # Notification listener
├── persistence/                      # Room database
├── policy-engine/                    # Policy evaluation
├── runtime-core/                     # Task runtime, state machine
├── secure-storage/                   # Keystore, session
├── telemetry/                        # Audit, metrics, trace
├── termux/                           # Termux setup & services
│   ├── services/
│   │   ├── file_transfer.py          # LAN file transfer
│   │   ├── voice_input.py            # Voice recording
│   │   └── phone_ai_service.py       # Phone AI automation
│   ├── setup-android-node.sh         # Full stack setup
│   ├── patch-dashboard.js            # Dashboard integration
│   └── fix-dashboard.js              # Dashboard fix script
├── testing/                          # Test utilities
├── transport/                        # WSS client, gateway
├── vision-runtime/                   # MediaProjection, OCR
├── build.gradle.kts                  # Root build config
├── settings.gradle.kts               # Module settings
├── gradle.properties                 # Gradle properties
├── PLAN.md                           # Implementation plan
└── AGENTS.md                         # This file
```

### 6.3 Key Dependencies

| Library | Mục đích |
|---|---|
| Jetpack Compose (BOM) | UI framework |
| Hilt | Dependency Injection |
| OkHttp + Retrofit | Networking / WebSocket |
| Room | Local database |
| DataStore Preferences | Key-value storage |
| Kotlinx Serialization | JSON serialization |
| Coroutines + Flow | Async programming |
| Timber | Structured logging |
| Android Keystore | Secure credential storage |
| ML Kit Text Recognition | Local OCR (P1) |

---

## 7. Operations

### 7.1 Troubleshooting

| Vấn đề | Nguyên nhân | Fix |
|---|---|---|
| **WSS disconnect** | Mạng mất hoặc heartbeat timeout | Reconnect policy tự động |
| **Accessibility not working** | Service tắt hoặc permission bị thu hồi | Yêu cầu bật lại |
| **Task rejected** | Capability không phù hợp | Kiểm tra capability report |
| **Vision not available** | MediaProjection chưa được consent | Yêu cầu user đồng ý |
| **Room DB lock** | Concurrent write | Dùng Outbox pattern |
| **Device not registered** | Chưa pair code | Scan QR trên dashboard |

### 7.2 Security

| Khu vực | Lưu ý |
|---|---|
| **Endpoint** | Không hard-code; đọc từ `dev.properties` hoặc build config |
| **Device identity** | Key pair trong Android Keystore |
| **Session tokens** | Short-lived, refresh qua device credential |
| **Replay protection** | Sequence number + nonce/signed envelope |
| **Sensitive data** | Policy Engine chặn OTP, password, credentials |
| **Snapshot data** | Sanitizer xóa thông tin nhạy cảm trước khi gửi |
| **Remote vision** | `TI_ALLOW_REMOTE_VISION=false` mặc định |
| **Approval required** | `sendRequiresApproval: true` cho action nguy hiểm |

### 7.3 Known Issues & Blockers

| Issue | Priority | Mô tả | Workaround |
|---|---|---|---|
| **MVP chưa hoàn thành** | 🔴 High | Dự án đang trong giai đoạn MVP (PLAN.md có roadmap chi tiết) | Theo dõi PLAN.md |
| **Adapters trống** | 🟡 Medium | Chỉ có generic adapter; Telegram adapter chưa implement | Test với generic |
| **Termux setup** | 🟡 Medium | Cần Android device thật (Termux) | Dùng emulator + ADB |
| **Vision offline** | 🟢 Low | ML Kit OCR chưa tích hợp | Dùng remote OCR |
| **Device farm** | 🟢 Low | Chưa support multi-device | Chạy từng node riêng |

---

## 8. Phụ lục

### 8.1 Task Protocol Details

```text
Task lifecycle:
  Gateway → task.offer → Node → task.accept/reject
  → task.started → task.progress (optional)
  → task.approval_required → user approves/rejects
  → task.completed/task.failed

Idempotency:
  Mỗi task attempt có idempotencyKey
  Gateway dedup nếu nhận task.accept trùng key

Timeout:
  Task có expiresAt
  Node có TI_MAX_TASK_DURATION_SECONDS
```

### 8.2 Device Registration Flow

```
1. Cài APK → Mở ứng dụng
2. Tạo device key pair trong Android Keystore
3. Hiển thị pairing code hoặc QR
4. Người dùng xác nhận trên Ti Dashboard
5. Gateway cấp session credential ngắn hạn
6. Node đăng ký capability
```

### 8.3 Termux Service Port Mapping

| Service | Port | Protocol | File |
|---|---|---|---|
| Ollama | 11434 | HTTP | termux setup |
| Phone AI | 5000 | HTTP | `phone_ai_service.py` |
| File Transfer | 5001 | HTTP | `file_transfer.py` |
| Voice Input | 5002 | HTTP | `voice_input.py` |

### 8.4 Documents

| File | Mô tả |
|---|---|
| `PLAN.md` | Kế hoạch triển khai chi tiết (architecture, state machine, protocol) |
| `README.md` | Tổng quan về dự án |
| `termux/README.md` | Hướng dẫn Termux Node setup |
| `docs/ARCHITECTURE.md` | Kiến trúc chi tiết (planned) |
| `docs/PROTOCOL.md` | Task protocol spec (planned) |
| `docs/SECURITY.md` | Security model (planned) |
| `docs/ADAPTER_GUIDE.md` | Hướng dẫn viết adapter (planned) |

### 8.5 Handoff Logging

Mọi thay đổi quan trọng ở folder `Ti-Android/` phải ghi vào:

```
Z:\02_CORE\_cli\.config\shared\context\handoff.json
```

Format:
```json
{
  "timestamp": "2026-07-15T10:00:00.000Z",
  "agent": "Buffy",
  "action": "Modified Telegram adapter — fixed compose_message flow",
  "details": "Cập nhật locator priority theo accessibility-first principle"
}
```

---

*Đây là execution node độc lập, giao tiếp với TiRouter qua WSS. Không hard-code API Key hay Logic AI nặng trong mã nguồn Android.*
