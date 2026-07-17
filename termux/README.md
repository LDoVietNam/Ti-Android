# 📱 Ti-Android Termux Node

> **Chạy TiRouter + Phone AI + File Transfer + Voice Input trên Android Termux**

## 📁 Cấu trúc

```
termux/
├── setup-android-node.sh        ← 🚀 Full setup: Ollama + services
├── setup-test-env.sh            ← 🧪 Test environment (Telegram + accessibility)
├── bin/
│   └── tirouter-arm64-linux     ← Android/Termux ARM64 executor artifact
├── build-tools/
│   └── cliproxy-android-setup.sh ← Build/install CLIProxyAPI trong Termux proot
├── host-tools/
│   ├── start-phone-ai.bat       ← Windows Phone AI manager
│   ├── start-phone-ai.ps1       ← PowerShell Phone AI manager
│   └── connect-android-router.bat ← Windows Android node manager
├── services/
│   ├── phone_ai_service.py      ← 🤖 Ollama facade + device health (port 5000)
│   ├── file_transfer.py         ← 📁 LAN file transfer service (port 5001)
│   └── voice_input.py           ← 🎤 Voice input + Cohere transcribe (port 5002)
├── models/
│   └── gemma4-setup.sh          ← 🤖 Gemma 4 download + benchmark
├── toolchains/
│   ├── go-linux-arm64.tar.gz    ← ARM64 Go toolchain cache
│   └── go1.22.5-linux-arm64.tar.gz
└── README.md
```

## 🚀 Quick Start trên Android Termux

```bash
# 1. Clone/copy repository Ti-Android lên Termux
pkg install -y git curl wget python
cd ~
TI_ANDROID_REPOSITORY_URL="https://<git-host>/<owner>/Ti-Android.git"
git clone "$TI_ANDROID_REPOSITORY_URL" Ti-Android
cd Ti-Android/termux

# 2. Full setup (Ollama + models lightweight)
bash setup-android-node.sh --models light

# 3. Start all services
bash ~/ti-android-node/ti-node.sh start

# 4. Check status
bash ~/ti-android-node/ti-node.sh status
```

## 🔌 Các services

| Service | Port | Mô tả | Giao thức |
|---------|------|-------|-----------|
| **Ollama** | 11434 | On-device LLM server | OpenAI-compatible |
| **Phone AI** | 5000 | Chat completion + health | OpenAI-compatible |
| **File Transfer** | 5001 | Upload/download/list files | HTTP REST |
| **Voice Input** | 5002 | Record + transcribe audio | HTTP REST |
| **TiRouter** | 20128 | Router gateway | OpenAI-compatible |

## 🤖 Models

| Model | RAM | Quality | Lệnh cài |
|-------|:---:|:-------:|---------|
| llama3.2:1b | 1GB | ⚡ Fast | `ollama pull llama3.2:1b` |
| llama3.2:3b | 2GB | 👍 Good | `ollama pull llama3.2:3b` |
| Gemma 4 12B | 8GB | 🚀 Best | `bash models/gemma4-setup.sh download` |

## 📡 Kết nối với TiRouter (Windows)

Sau khi Android node chạy, từ Windows:

```batch
# Auto-connect
Z:\01_PROJECTS\apps\Ti-Android\termux\host-tools\start-phone-ai.bat --scan

# Hoặc trực tiếp
Z:\01_PROJECTS\apps\Ti-Android\termux\host-tools\start-phone-ai.bat --connect <android-ip>
```

## 📁 File Transfer

```bash
# Upload từ PC lên Android
curl -X POST http://<android-ip>:5001/upload \
  -F "file=@document.pdf"

# Download từ Android về PC
curl -O http://<android-ip>:5001/download/document.pdf

# List files
curl http://<android-ip>:5001/files
```

## 🎤 Voice Input

```bash
# Set Cohere API key (free tier available)
export COHERE_API_KEY="your-key"

# Transcribe audio file
curl -X POST http://localhost:5002/transcribe \
  -F "audio=@recording.wav" \
  -F "language=vi"

# Record + transcribe (requires termux-api)
curl -X POST http://localhost:5002/record-and-transcribe \
  -d "duration=5&language=vi"
```

## 🧪 Test

```bash
bash ~/ti-android-node/test-ti-android.sh
```

## 📊 Dashboard

Mở `Z:\01_PROJECTS\apps\Tirouter\dashboard.html` trên Windows để xem trạng thái các executor.
