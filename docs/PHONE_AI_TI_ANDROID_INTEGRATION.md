# Phone AI + Ti-Android Integration Guide

## Kiến trúc tổng thể

```
TiRouter / OmniRoute
    │
    ├── WSS ──► Ti-Android (Native APK)
    │              ├── Accessibility Service → UI Automation
    │              ├── Vision Backend → OCR/VLM fallback
    │              └── Task Runtime → Policy + Approval
    │
    └── HTTP ──► Phone AI (Termux Python)
                   ├── Ollama → Local LLM
                   ├── REST API → Chat completion
                   └── Live data → Dashboard widget
```

## Cách 2 node bổ sung cho nhau

| Tình huống | Phone AI (Termux) | Ti-Android (Native) |
|------------|-------------------|---------------------|
| Chat với LLM local | ✅ Chính | ❌ Không làm |
| Điều khiển UI app | ❌ Không làm | ✅ Chính |
| Soạn tin nhắn | ✅ Suy luận nội dung | ✅ Gửi lệnh click/gõ |
| Đọc notification | ❌ Không làm | ✅ NotificationListener |
| Scan màn hình | ❌ Không làm | ✅ MediaProjection + OCR |
| Dashboard live data | ✅ Battery, latency, model | ✅ Task status, capability |

## Luồng xử lý mẫu: "Soạn và gửi tin nhắn Telegram"

```text
1. Orchestrator gửi task → Ti-Android
2. Ti-Android AccessibilityService mở Telegram
3. Ti-Android chụp UI tree → tìm ô nhập liệu
4. Ti-Android gửi HTTP tới Phone AI:
      POST /v1/chat/completions
      {"model":"llama3.2:1b","messages":[{"role":"user","content":"Soạn tin nhắn cho Dev Team về bản build"}]}
5. Phone AI (Ollama) → trả về nội dung draft
6. Ti-Android PolicyEngine → xác định R3 (cần approval)
7. Người dùng xác nhận trên điện thoại
8. Ti-Android AccessibilityService → click send
9. Ti-Android xác minh tin nhắn đã gửi
10. Ti-Android trả kết quả về Orchestrator
```

## Cấu hình Tirouter

### Config.yaml — thêm cả 2 providers

```yaml
openai-compatibility:
  # Phone AI (Termux + Ollama)
  - name: "phone-ai"
    prefix: "phone"
    base-url: "http://<phone-ip>:5000"
    models:
      - name: "llama3.2:1b"
        alias: "phone-llama-1b"
      - name: "llama3.2:3b"
        alias: "phone-llama-3b"
      - name: "phi3:mini"
        alias: "phone-phi3"

  # Ti-Android (Native APK) — khi có Gateway
  - name: "ti-android"
    prefix: "ti"
    base-url: "http://<device-gateway>:PORT"
    models: []
```

### Dashboard.html — cả 2 card

```
┌─────────────────────┬─────────────────────┐
│  Phone AI           │  Ti-Android         │
│  ● Running          │  ● Connected        │
│  ───                │  ───                │
│  🔋 85%  ⚡ 45ms    │  Accessibility:  ✅  │
│  📦 llama3.2:1b     │  Task Running:   ✅  │
│  💾 Memory: 62%     │  Agent:         ▶️   │
└─────────────────────┴─────────────────────┘
```

## Setup nhanh

### Phone AI (đã có)
```bash
# Trên Android Termux
cd ~/Ti-Android/termux
python services/phone_ai_service.py --port 5000

# Trên Windows (kết nối)
Z:\01_PROJECTS\apps\Ti-Android\termux\host-tools\start-phone-ai.bat --connect 192.168.1.100
```

### Ti-Android (cần setup)
```bash
# Yêu cầu: Android Studio, Java 17, Gradle
cd Z:\01_PROJECTS\apps\Ti-Android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Kiểm tra
```bash
# Phone AI
curl http://192.168.1.100:5000/health

# Ti-Android (qua Gateway)
wscat -c wss://gateway.local/device -H "Authorization: Bearer <token>"
```

## Files liên quan

| File | Vai trò |
|------|---------|
| `Ti-Android/termux/services/phone_ai_service.py` | Python service chạy trên Termux |
| `Ti-Android/termux/host-tools/start-phone-ai.bat` | Script Windows kết nối Phone AI |
| `Ti-Android/termux/host-tools/connect-android-router.bat` | Script Windows quản lý Android ARM64 node |
| `Ti-Android/termux/bin/tirouter-arm64-linux` | Artifact executor ARM64 cho Termux |
| `Tirouter/dashboard.html` | Dashboard + live widget |
| `Ti-Android/app/.../*.kt` | Native Android app source |
| `Ti-Android/PLAN.md` | Kế hoạch chi tiết 8 phase |
