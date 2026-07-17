# 🚀 Hướng dẫn chạy Ti-Android Node trên Termux (thực tế)

> **Yêu cầu:** Android 10+, 4GB RAM (khuyên 6GB+), Wi-Fi, Termux từ F-Droid

---

## 📱 Bước 1: Cài Termux

```bash
# Cài từ F-Droid (không dùng Google Play - bản cũ)
# 1. Tải F-Droid: https://f-droid.org
# 2. Tìm "Termux" và cài đặt
# 3. Mở Termux, chạy:

termux-setup-storage   # Cấp quyền storage (YES)
pkg update && pkg upgrade -y   # ~2 phút
```

---

## 📦 Bước 2: Tải scripts

### Cách A: Qua ADB (PC → Phone)

```powershell
# Trên PC (Windows PowerShell - Administrator)
cd Z:\01_PROJECTS\apps\Ti-Android\termux
adb push setup-android-node.sh /sdcard/
adb push services/ /sdcard/services/
adb push models/ /sdcard/models/

# Trên Termux
cp /sdcard/setup-android-node.sh ~/ti-android-node/
cp -r /sdcard/services ~/ti-android-node/
cp -r /sdcard/models ~/ti-android-node/
chmod +x ~/ti-android-node/setup-android-node.sh
```

### Cách B: Qua Git (trực tiếp trên Termux)

```bash
pkg install git
git clone https://github.com/trepremium/tirouter

# TẠO SYMLINK để đồng bộ đường dẫn
ln -s ~/ti-android/termux ~/ti-android-node
# HOẶC export NODE_DIR=~/ti-android/termux
```

> **📌 Lưu ý đường dẫn:** 
> - Cách ADB: files ở `~/ti-android-node/`
> - Cách Git: files ở `~/ti-android/termux/`  
> - **Luôn chạy lệnh từ thư mục chứa scripts: `cd ~/ti-android-node`**

---

## ⚙️ Bước 3: Chạy setup (tự động)

```bash
cd ~/ti-android-node
bash setup-android-node.sh --models light
```

Script chạy 7 phases, mỗi phase ~2-5 phút. Tổng ~15-30 phút.

| Flag | Model | RAM | Dung lượng |
|------|-------|:---:|:----------:|
| `--models light` | llama3.2:1b | 1.2GB | 800MB |
| `--models medium` | + llama3.2:3b | 2.5GB | 2.8GB |
| `--models heavy` | + Gemma 4 12B | 7.5GB | 10GB |

---

## 🔬 Bước 4: Kiểm tra từng service

### Ollama
```bash
# Đợi Ollama start (~10s)
sleep 5
curl http://localhost:11434/api/tags
# Output: {"models":["llama3.2:1b", ...]}

# Test chat
curl -X POST http://localhost:11434/api/chat \
  -d '{"model":"llama3.2:1b","messages":[{"role":"user","content":"Xin chào"}],"stream":false}'
```

### Phone AI
```bash
curl http://localhost:5000/health
# Output: {"status":"ok","ollama_connected":true,"battery":{...}}
```

### File Transfer
```bash
curl http://localhost:5001/health
# Output: {"status":"ok","storage_free_gb":12.5}

# Upload test
echo "Hello from PC" > /tmp/test.txt
curl -X POST http://localhost:5001/upload -F "file=@/tmp/test.txt"
```

### Voice Input
```bash
# Nếu có Cohere API key
curl http://localhost:5002/health
```

---

## 📡 Bước 5: Kết nối từ PC

### Lấy IP Android:
```bash
ip -4 addr show wlan0 | grep inet
# Output: inet 192.168.1.xxx/24
```

### Trên PC (Windows PowerShell):
```powershell
# Test Phone AI
curl http://192.168.1.xxx:5000/v1/chat/completions `
  -H "Content-Type: application/json" `
  -d '{"model":"llama3.2:1b","messages":[{"role":"user","content":"Hello"}]}'

# Test File Transfer
curl http://192.168.1.xxx:5001/health

# Upload file từ PC lên Android
curl -X POST http://192.168.1.xxx:5001/upload -F "file=@document.pdf"

# Download từ Android về PC
curl -O http://192.168.1.xxx:5001/download/document.pdf
```

---

## 🔄 Bước 6: Auto-connect với Windows

```batch
:: Trên Windows (Command Prompt)
Z:\01_PROJECTS\apps\Ti-Android\termux\host-tools\start-phone-ai.bat --connect 192.168.1.xxx

:: Sau đó test qua TiRouter control-plane (cổng hiện tại: 1870)
curl http://localhost:1870/v1/chat/completions ^
  -H "Content-Type: application/json" ^
  -d "{\"model\":\"phone/llama3.2:1b\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}"
```

---

## 🎤 Bước 7: Voice Input (optional)

**Trên Termux:**
```bash
# Cài mic
pkg install termux-api

# Record + transcribe (cần COHERE_API_KEY)
export COHERE_API_KEY="your-key"
curl -X POST http://localhost:5002/record -d "duration=5&language=vi"
```

**Từ PC:**
```bash
# Upload audio + transcribe
curl -X POST http://192.168.1.xxx:5002/transcribe \
  -F "audio=@recording.mp3" -F "language=vi"
```

---

## 🧪 Bước 8: Kiểm tra toàn bộ

```bash
bash ~/ti-android-node/ti-node.sh status

# Output mẫu:
# ╔══════════════════════════════════════╗
# ║      Ti Android Node — Status        ║
# ╚══════════════════════════════════════╝
#   ollama        :11434  ✅ pid=12345
#   phone-ai      :5000   ✅ pid=12346
#   file-transfer :5001   ✅ pid=12347
#   voice-input   :5002   ✅ pid=12348
```

---

## ❌ Troubleshooting

| Vấn đề | Nguyên nhân | Fix |
|--------|-------------|-----|
| `ollama: command not found` | Chưa cài tur-repo | `pkg install tur-repo ollama` |
| `Connection refused` port 11434 | Ollama chưa start | `cd ~/ti-android-node && bash start-ollama.sh` |
| `curl: (28) Connection timeout` | Sai IP hoặc firewall | `ip -4 addr show wlan0` kiểm tra IP |
| `ModuleNotFoundError: httpx` | Thiếu pip package | `pip install httpx` |
| PID không hiện trong `status` | `ss -p` cần root | Dùng `lsof -i :$port` thay thế |
| `bash: setup-android-node.sh: No such file` | Sai thư mục | `cd ~/ti-android-node` rồi thử lại |

---

## 📈 Performance

| Model | RAM | Tok/s | Use case |
|-------|:---:|:-----:|----------|
| llama3.2:1b (Q4) | 1.2GB | 25-35 | Chat nhanh, Việt Nam |
| llama3.2:3b (Q4) | 2.5GB | 12-18 | Code, reasoning |
| Gemma 4 12B (Q4) | 7.5GB | 5-8 | Chất lượng cao nhất |
