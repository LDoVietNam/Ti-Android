#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════════
#  Ti-Android Node — Termux Full Setup Script
#  Cài đặt toàn bộ stack: Ollama + Phone AI + File Transfer + Voice
# ═══════════════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_VERSION="1.0.0"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TERMUX_HOME="/data/data/com.termux/files/home"
NODE_DIR="$TERMUX_HOME/ti-android-node"
LOG_FILE="$NODE_DIR/setup.log"
BUNDLED_PHONE_AI="$SCRIPT_DIR/services/phone_ai_service.py"
BUNDLED_TIROUTER_BIN="$SCRIPT_DIR/bin/tirouter-arm64-linux"

mkdir -p "$NODE_DIR"/{models,config,data,logs}

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"; }
error() { log "❌ $*"; }
ok()    { log "✅ $*"; }
info()  { log "ℹ️  $*"; }
warn()  { log "⚠️  $*"; }

# ─────────────────────────────────────────────
# PHASE 0: System updates & dependencies
# ─────────────────────────────────────────────
phase0() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Ti-Android Node Setup v$SCRIPT_VERSION       ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    info "Phase 0: Updating Termux packages..."
    pkg update -y && pkg upgrade -y
    pkg install -y \
        python python-pip \
        git curl wget \
        openssh termux-services \
        tmux htop \
        android-tools \
        build-essential cmake \
        which jq
    ok "System packages installed"
}

# ─────────────────────────────────────────────
# PHASE 1: Ollama + Gemma 4 (on-device LLM)
# ─────────────────────────────────────────────
phase1() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Phase 1: Installing Ollama + Models        ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    # Install Ollama via tur-repo
    pkg install -y tur-repo
    pkg install -y ollama

    # Create Ollama models directory
    mkdir -p "$TERMUX_HOME/.ollama/models"
    export OLLAMA_MODELS="$TERMUX_HOME/.ollama/models"

    # Start Ollama server
    ollama serve &
    sleep 3

    # Pull models (user can choose which to download)
    info "Available models (choose with --models flag):"
    echo "  light:  llama3.2:1b  (800MB) — fastest, lowest RAM"
    echo "  medium: llama3.2:3b  (2GB)   — balanced"
    echo "  heavy:  gemma-4-12B  (7GB)   — best quality (needs 8GB+ RAM)"
    echo ""

    MODELS="${1:-light}"
    case "$MODELS" in
        light)
            ollama pull llama3.2:1b
            ok "llama3.2:1b installed"
            ;;
        medium)
            ollama pull llama3.2:1b
            ollama pull llama3.2:3b
            ok "llama3.2 models installed"
            ;;
        heavy)
            ollama pull llama3.2:1b
            ollama pull llama3.2:3b
            # Gemma 4 — Google's mobile-optimized model
            info "Downloading Gemma 4 (may take 10-20 minutes)..."
            ollama pull hf.co/google/gemma-4-12B-it-qat-q4_0-gguf
            ok "Gemma 4 installed"
            ;;
    esac

    # Create Ollama systemd/service script
    cat > "$NODE_DIR/start-ollama.sh" << 'OLLAMAEOF'
#!/data/data/com.termux/files/usr/bin/bash
# Start Ollama server for Ti-Android Node
export OLLAMA_HOST="0.0.0.0"
export OLLAMA_PORT=11434
export OLLAMA_MODELS="$HOME/.ollama/models"
export OLLAMA_KEEP_ALIVE="5m"

echo "[⏳] Starting Ollama server..."
ollama serve
OLLAMAEOF
    chmod +x "$NODE_DIR/start-ollama.sh"

    ok "Ollama ready. Start with: $NODE_DIR/start-ollama.sh"
}

# ─────────────────────────────────────────────
# PHASE 2: Phone AI Service
# ─────────────────────────────────────────────
phase2() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Phase 2: Phone AI Service + TiRouter       ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    pip install fastapi uvicorn psutil aiofiles httpx

    if [ ! -f "$BUNDLED_PHONE_AI" ]; then
        error "Bundled Phone AI service not found: $BUNDLED_PHONE_AI"
        return 1
    fi
    cp "$BUNDLED_PHONE_AI" "$NODE_DIR/phone_ai_service.py"

    # Create config
    cat > "$NODE_DIR/config/phone-ai.yaml" << YAMLEOF
# Phone AI Service Configuration
server:
  host: "0.0.0.0"
  port: 5000
  workers: 2

ollama:
  url: "http://localhost:11434"
  default_model: "llama3.2:1b"
  keep_alive: "5m"

file_transfer:
  enabled: true
  port: 5001
  storage_dir: "$NODE_DIR/data/files"
  max_file_size_mb: 100

voice:
  enabled: true
  port: 5002
  provider: "cohere"
  cohere_api_key: ""
  sample_rate: 16000
  language: "vi"
YAMLEOF

    # NOTE: phone_ai_service.py reads OLLAMA_URL env var (not OLLAMA_HOST)
    cat > "$NODE_DIR/start-phone-ai.sh" << 'SRVEOF'
#!/data/data/com.termux/files/usr/bin/bash
# Start Phone AI Service
cd "$(dirname "$0")"
export OLLAMA_URL="http://localhost:11434"
export PHONE_AI_PORT=5000

echo "[📱] Starting Phone AI Service..."
python phone_ai_service.py 2>&1 | tee -a logs/phone-ai.log
SRVEOF
    chmod +x "$NODE_DIR/start-phone-ai.sh"

    ok "Phone AI Service configured"
}

# ─────────────────────────────────────────────
# PHASE 3: LAN File Transfer
# ─────────────────────────────────────────────
phase3() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Phase 3: LAN File Transfer Service         ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    pip install fastapi uvicorn python-multipart aiofiles

    cat > "$NODE_DIR/file-transfer.py" << 'PYEOF'
#!/usr/bin/env python3
"""
Ti-Android LAN File Transfer Service
- HTTP REST API for file upload/download/list
- For use with Phone AI and TiRouter
"""
import os, sys, json, hashlib, mimetypes
from pathlib import Path
from datetime import datetime
from typing import Optional
import uvicorn
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
import aiofiles

app = FastAPI(title="Ti-Android File Transfer", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

STORAGE_DIR = os.environ.get("TI_FILE_STORAGE",
    "/data/data/com.termux/files/home/ti-android-node/data/files")
MAX_FILE_SIZE = int(os.environ.get("TI_MAX_FILE_SIZE_MB", "100")) * 1024 * 1024
os.makedirs(STORAGE_DIR, exist_ok=True)

def fmt_size(bytes_val):
    for unit in ['B', 'KB', 'MB', 'GB']:
        if bytes_val < 1024: return f"{bytes_val:.1f}{unit}"
        bytes_val /= 1024
    return f"{bytes_val:.1f}TB"

@app.get("/health")
async def health():
    stat = os.statvfs(STORAGE_DIR)
    return {
        "status": "ok", "service": "file-transfer",
        "storage_free_gb": round(stat.f_bavail * stat.f_frsize / (1024**3), 1),
        "max_file_mb": MAX_FILE_SIZE // (1024*1024)
    }

@app.get("/files")
async def list_files():
    files = []
    for f in sorted(Path(STORAGE_DIR).iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
        if f.is_file():
            files.append({
                "name": f.name, "size": f.stat().st_size, "size_str": fmt_size(f.stat().st_size),
                "type": mimetypes.guess_type(f.name)[0] or "application/octet-stream",
                "modified": datetime.fromtimestamp(f.stat().st_mtime).isoformat(),
                "hash": hashlib.md5(f.read_bytes()).hexdigest()[:16]
            })
    return {"count": len(files), "files": files}

@app.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    safe_name = os.path.basename(file.filename or "unknown")
    path = os.path.join(STORAGE_DIR, safe_name)
    size = 0
    async with aiofiles.open(path, "wb") as f:
        while chunk := await file.read(8192):
            size += len(chunk)
            if size > MAX_FILE_SIZE:
                os.remove(path)
                raise HTTPException(413, f"File too large (max {MAX_FILE_SIZE // (1024*1024)}MB)")
            await f.write(chunk)
    return {"status": "ok", "filename": safe_name, "size": size, "size_str": fmt_size(size)}

@app.get("/download/{filename}")
async def download_file(filename: str):
    path = os.path.join(STORAGE_DIR, os.path.basename(filename))
    if not os.path.exists(path):
        raise HTTPException(404, "File not found")
    return FileResponse(path, filename=os.path.basename(path))

@app.delete("/files/{filename}")
async def delete_file(filename: str):
    path = os.path.join(STORAGE_DIR, os.path.basename(filename))
    if os.path.exists(path):
        os.remove(path)
        return {"status": "deleted", "filename": filename}
    raise HTTPException(404, "File not found")

@app.post("/receive")
async def receive_from(url: str, filename: str = None):
    """Pull file from URL (phone downloads from PC)"""
    import httpx
    if not filename: filename = url.split("/")[-1]
    path = os.path.join(STORAGE_DIR, filename)
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        async with aiofiles.open(path, "wb") as f:
            await f.write(resp.content)
    return {"status": "ok", "filename": filename, "size": len(resp.content), "source": url}

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5001
    print(f"[📁] File Transfer Service on port {port}")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
PYEOF
    chmod +x "$NODE_DIR/file-transfer.py"

    cat > "$NODE_DIR/start-file-transfer.sh" << 'FTSRV'
#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
echo "[📁] Starting File Transfer on port 5001..."
python file-transfer.py 5001 2>&1 | tee -a logs/file-transfer.log
FTSRV
    chmod +x "$NODE_DIR/start-file-transfer.sh"
    ok "File Transfer Service created (port 5001)"
}

# ─────────────────────────────────────────────
# PHASE 4: Voice Input Service
# ─────────────────────────────────────────────
phase4() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Phase 4: Voice Input Service               ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    # Install dependencies: httpx for Cohere API, termux-api for microphone
    pkg install -y termux-api
    pip install fastapi uvicorn httpx numpy

    COHERE_KEY="${COHERE_API_KEY:-}"
    if [ -z "$COHERE_KEY" ]; then
        warn "COHERE_API_KEY not set. Voice will use offline mode (record only)."
    fi

    cat > "$NODE_DIR/voice-input.py" << 'PYEOF'
#!/usr/bin/env python3
"""
Ti-Android Voice Input Service
- Record audio via Termux mic
- Transcribe via Cohere API or offline
"""
import os, sys, json, base64, tempfile, subprocess
from datetime import datetime
from typing import Optional
import uvicorn
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import httpx

app = FastAPI(title="Ti-Android Voice Input", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

COHERE_API_KEY = os.environ.get("COHERE_API_KEY", "")
COHERE_TRANSCRIBE_URL = "https://api.cohere.com/v1/transcribe"
SUPPORTED_LANGS = ["vi", "en", "ar", "zh", "ja", "ko", "th"]

@app.get("/health")
async def health():
    has_key = bool(COHERE_API_KEY)
    has_mic = subprocess.run(["which", "termux-microphone-record"],
        capture_output=True).returncode == 0
    return {
        "status": "ok", "service": "voice-input",
        "provider": "cohere" if has_key else "offline",
        "features": {"transcribe": has_key, "record": has_mic}
    }

@app.get("/languages")
async def list_languages():
    names = {"vi":"Tiếng Việt","en":"English","ar":"العربية","zh":"中文","ja":"日本語","ko":"한국어","th":"ไทย"}
    return {"languages": [{"code": lang, "name": names.get(lang, lang)} for lang in SUPPORTED_LANGS]}

@app.post("/transcribe")
async def transcribe(
    audio: UploadFile = File(None),
    audio_base64: str = Form(None),
    language: str = Form("vi")
):
    if not audio and not audio_base64:
        raise HTTPException(400, "Provide either audio file or audio_base64")
    if not COHERE_API_KEY:
        return {"status": "offline", "text": "", "note": "Set COHERE_API_KEY"}
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        content = await audio.read() if audio else base64.b64decode(audio_base64)
        tmp.write(content)
        tmp_path = tmp.name
    try:
        async with httpx.AsyncClient(timeout=60) as client:
            with open(tmp_path, "rb") as f:
                resp = await client.post(COHERE_TRANSCRIBE_URL,
                    headers={"Authorization": f"Bearer {COHERE_API_KEY}"},
                    files={"audio": (os.path.basename(tmp_path), f, "audio/wav")},
                    data={"language": language})
            resp.raise_for_status()
            result = resp.json()
        return {"status": "ok", "text": result.get("text",""), "confidence": result.get("confidence",0),
                "language": language, "provider": "cohere"}
    except Exception as e:
        raise HTTPException(500, f"Transcription failed: {e}")
    finally:
        os.unlink(tmp_path)

@app.post("/record")
async def record(duration: int = Form(5), language: str = Form("vi")):
    has_mic = subprocess.run(["which","termux-microphone-record"],capture_output=True).returncode==0
    if not has_mic:
        raise HTTPException(400, "termux-api not installed: pkg install termux-api")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp_path = tmp.name
    try:
        subprocess.run(["termux-microphone-record","-l",str(duration),"-f",tmp_path,"-e","aac"],
            check=True, timeout=duration+10)
        if not COHERE_API_KEY:
            return {"status":"recorded","file":tmp_path,"duration":duration,"note":"Need Cohere key"}
        async with httpx.AsyncClient(timeout=60) as client:
            with open(tmp_path, "rb") as f:
                resp = await client.post(COHERE_TRANSCRIBE_URL,
                    headers={"Authorization": f"Bearer {COHERE_API_KEY}"},
                    files={"audio":(os.path.basename(tmp_path),f,"audio/wav")})
            resp.raise_for_status()
            result = resp.json()
        return {"status":"ok","text":result.get("text",""),"confidence":result.get("confidence",0),
                "duration":duration,"provider":"cohere"}
    except Exception as e:
        raise HTTPException(500, f"Recording failed: {e}")
    finally:
        if os.path.exists(tmp_path): os.unlink(tmp_path)

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5002
    print(f"[🎤] Voice Input Service on port {port}")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
PYEOF
    chmod +x "$NODE_DIR/voice-input.py"

    cat > "$NODE_DIR/start-voice.sh" << 'VSRV'
#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
export COHERE_API_KEY="${COHERE_API_KEY:-}"
[ -z "$COHERE_API_KEY" ] && echo "[⚠️] COHERE_API_KEY not set — offline mode"
echo "[🎤] Starting Voice Input on port 5002..."
python voice-input.py 5002 2>&1 | tee -a logs/voice.log
VSRV
    chmod +x "$NODE_DIR/start-voice.sh"
    ok "Voice Input Service created (port 5002)"
}

# ─────────────────────────────────────────────
# PHASE 5: TiRouter ARM64 for Termux
# ─────────────────────────────────────────────
phase5() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Phase 5: TiRouter ARM64 + Auto-Connect     ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    TIROUTER_BIN="tirouter-arm64-linux"
    if [ ! -f "$BUNDLED_TIROUTER_BIN" ]; then
        error "Bundled TiRouter ARM64 binary not found: $BUNDLED_TIROUTER_BIN"
        return 1
    fi
    cp "$BUNDLED_TIROUTER_BIN" "$NODE_DIR/$TIROUTER_BIN"
    chmod +x "$NODE_DIR/$TIROUTER_BIN"
    ok "TiRouter ARM64 installed from the Ti-Android bundle"

    cat > "$NODE_DIR/config/tirouter.yaml" << YAMLEOF
server:
  port: 20128
  host: "0.0.0.0"
  debug: true
providers:
  - name: "ollama-local"
    prefix: "phone"
    base-url: "http://localhost:11434"
    models:
      - name: "llama3.2:1b"
        alias: "phone-llama-1b"
      - name: "llama3.2:3b"
        alias: "phone-llama-3b"
      - name: "gemma-4-12B"
        alias: "phone-gemma-4"
        capabilities: { contextWindow: 32768 }
  - name: "orcarouter"
    prefix: "orca"
    base-url: "http://192.168.1.100:20128"
    api-key: "\${ORCA_API_KEY}"
    models:
      - name: "orca-gpt4o-mini"
        alias: "orca-gpt4o-mini"
services:
  file_transfer: { enabled: true, url: "http://localhost:5001" }
  voice_input: { enabled: true, url: "http://localhost:5002" }
capabilities:
  accessibility: true
  on_device_llm: true
  file_transfer: true
  voice_input: true
YAMLEOF

    cat > "$NODE_DIR/start-tirouter.sh" << 'TSEOF'
#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
BIN="tirouter-arm64-linux"
if [ ! -f "$BIN" ]; then
    echo "[✗] TiRouter binary not found!"; exit 1
fi
echo "[🚀] Starting TiRouter on port 20128..."
export ORCA_API_KEY="${ORCA_API_KEY:-}"
./"$BIN" --config config/tirouter.yaml 2>&1 | tee -a logs/tirouter.log
TSEOF
    chmod +x "$NODE_DIR/start-tirouter.sh"
    ok "TiRouter ARM64 configured"
}

# ─────────────────────────────────────────────
# PHASE 6: Master Controller
# ─────────────────────────────────────────────
phase6() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Phase 6: Master Controller Script          ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    cat > "$NODE_DIR/ti-node.sh" << 'NODEEOF'
#!/data/data/com.termux/files/usr/bin/bash
NODE_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$NODE_DIR/logs"
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

services=(
    "ollama:11434:start-ollama.sh"
    "phone-ai:5000:start-phone-ai.sh"
    "file-transfer:5001:start-file-transfer.sh"
    "voice-input:5002:start-voice.sh"
    "tirouter:20128:start-tirouter.sh"
)

get_pid() { ss -tlnp 2>/dev/null | grep ":$1 " | grep -oP 'pid=\K[0-9]+' | head -1; }

status() {
    echo ""; echo "╔══════════════════════════════════════╗"
    echo "║      Ti Android Node — Status        ║"
    echo "╚══════════════════════════════════════╝"
    for svc in "${services[@]}"; do
        IFS=':' read -r name port script <<< "$svc"
        pid=$(get_pid "$port")
        if [ -n "$pid" ]; then
            printf "  ${GREEN}%-15s${NC} :%-5s ${GREEN}✅ pid=%s${NC}\n" "$name" "$port" "$pid"
        else
            printf "  ${RED}%-15s${NC} :%-5s ${RED}❌ Stopped${NC}\n" "$name" "$port"
        fi
    done
}

start() {
    local target=${1:-all}
    for svc in "${services[@]}"; do
        IFS=':' read -r name port script <<< "$svc"
        [ "$target" != "all" ] && [ "$target" != "$name" ] && continue
        pid=$(get_pid "$port")
        [ -n "$pid" ] && { echo "  [✓] $name running (pid=$pid)"; continue; }
        echo "  [⏳] Starting $name..."
        if [ -f "$NODE_DIR/$script" ]; then
            tmux new-session -d -s "ti-$name" "bash $NODE_DIR/$script"
            sleep 2
            pid=$(get_pid "$port")
            [ -n "$pid" ] && echo "  [✅] $name started (pid=$pid)" || echo "  [❌] $name failed"
        else echo "  [⚠️]  $script not found"; fi
    done
}

stop() {
    local target=${1:-all}
    for svc in "${services[@]}"; do
        IFS=':' read -r name port script <<< "$svc"
        [ "$target" != "all" ] && [ "$target" != "$name" ] && continue
        pid=$(get_pid "$port")
        [ -n "$pid" ] && { kill "$pid" 2>/dev/null; tmux kill-session -t "ti-$name" 2>/dev/null; echo "  [✅] $name stopped"; }
    done
}

case "${1:-status}" in
    start) shift; start "$@" ;;
    stop) shift; stop "$@" ;;
    restart) stop; sleep 1; start ;;
    status) status ;;
    *) echo "Usage: ti-node.sh {start|stop|restart|status} [service]";;
esac
NODEEOF
    chmod +x "$NODE_DIR/ti-node.sh"
    ok "Master controller created: ti-node.sh"
}

# ─────────────────────────────────────────────
# PHASE 7: Device Registration
# ─────────────────────────────────────────────
phase7() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║   Phase 7: Device Registration               ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""

    DEVICE_ID="ti-android-$(cat /sys/class/net/wlan0/address 2>/dev/null | md5sum | cut -c1-8 || echo "termux-$(date +%s)")"
    LOCAL_IP=$(ifconfig wlan0 2>/dev/null | grep 'inet ' | awk '{print $2}' || echo "unknown")

    cat > "$NODE_DIR/config/device.json" << DEOF
{
  "deviceId": "$DEVICE_ID",
  "deviceName": "Android Termux Node",
  "platform": "android",
  "ip": "$LOCAL_IP",
  "services": {
    "phoneAi": "http://$LOCAL_IP:5000",
    "fileTransfer": "http://$LOCAL_IP:5001",
    "voiceInput": "http://$LOCAL_IP:5002",
    "tirouter": "http://$LOCAL_IP:20128",
    "ollama": "http://$LOCAL_IP:11434"
  },
  "capabilities": ["on_device_llm","file_transfer","voice_input","accessibility_tree"]
}
DEOF

    ok "Device registered: $DEVICE_ID"
    echo "  ID:   $DEVICE_ID"
    echo "  IP:   $LOCAL_IP"
    echo "  Phone AI:       http://$LOCAL_IP:5000"
    echo "  File Transfer:  http://$LOCAL_IP:5001"
    echo "  Voice Input:    http://$LOCAL_IP:5002"
    echo "  TiRouter:       http://$LOCAL_IP:20128"
}

# ═══════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════

MODELS="${MODELS:-light}"
while [ $# -gt 0 ]; do
    case "$1" in
        --models) MODELS="$2"; shift 2 ;;
        --cohere-key) COHERE_API_KEY="$2"; shift 2 ;;
        --orca-key) ORCA_API_KEY="$2"; shift 2 ;;
        *) break ;;
    esac
done

phase0
phase1 "$MODELS"
phase2
phase3
phase4
phase5
phase6
phase7

echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║       ✅ Ti Android Node Setup Complete!                  ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""
echo "  🚀 Start: cd $NODE_DIR && bash ti-node.sh start"
echo "  📊 Status: bash ti-node.sh status"
echo "  📁 Files:  curl http://localhost:5001/health"
echo "  🎤 Voice:  COHERE_API_KEY=key bash start-voice.sh"
echo "  💬 Chat:   curl http://localhost:5000/v1/chat/completions"
echo "  🔄 PC:     Ti-Android\termux\host-tools\start-phone-ai.bat --connect $LOCAL_IP"
echo ""
