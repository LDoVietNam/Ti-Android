#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════════
#  Gemma 4 — Google's Mobile-Optimized LLM
#  Setup + Benchmark cho Android Termux
# ═══════════════════════════════════════════════════════════════════
set -euo pipefail

NODE_DIR="$HOME/ti-android-node"
MODEL_NAME="hf.co/google/gemma-4-12B-it-qat-q4_0-gguf"
MODEL_TAG="gemma-4-12b"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $*"; }
warn() { echo -e "${YELLOW}[⚠️]${NC} $*"; }
error(){ echo -e "${RED}[❌]${NC} $*"; }
info() { echo -e "${CYAN}[ℹ️]${NC} $*"; }

mkdir -p "$NODE_DIR"/{models,benchmarks}

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   🤖 Gemma 4 — Android LLM Setup             ║"
echo "║   google/gemma-4-12B-it-qat-q4_0-gguf        ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ─────────────────────────────────────────
# Phase 1: Check requirements
# ─────────────────────────────────────────
phase1() {
    info "Phase 1: Checking requirements..."
    
    # RAM check
    total_ram=$(free -m | awk '/^Mem:/{print $2}')
    info "  Total RAM: ${total_ram}MB"
    if [ "$total_ram" -lt 6000 ]; then
        warn "  Less than 6GB RAM! Gemma 4 may not run well."
        warn "  Recommend 8GB+ for smooth operation"
    fi
    
    # Ollama check
    if command -v ollama &>/dev/null; then
        log "  ✅ Ollama installed"
    else
        error "Ollama not installed! Run setup-android-node.sh first"
        exit 1
    fi
    
    # Storage check
    available=$(df -h "$NODE_DIR" | awk 'NR==2{print $4}')
    info "  Available storage: $available"
    warn "  Gemma 4 needs ~7GB for download + model files"
}

# ─────────────────────────────────────────
# Phase 2: Download Gemma 4
# ─────────────────────────────────────────
phase2() {
    echo ""
    info "Phase 2: Downloading Gemma 4..."
    info "  This may take 10-30 minutes depending on network"
    info "  Model size: ~7GB (4-bit quantized)"
    echo ""
    
    # Check if already exists
    if ollama list 2>/dev/null | grep -q "$MODEL_TAG"; then
        log "✅ Gemma 4 already downloaded!"
        return
    fi
    
    # Pull model
    log "Starting download..."
    ollama pull "$MODEL_NAME" 2>&1 | while IFS= read -r line; do
        if [[ "$line" == *"pulling"* ]]; then
            echo -ne "\r  📥 $line"
        elif [[ "$line" == *"success"* ]]; then
            echo -e "\r  ✅ $line"
        fi
    done
    
    # Create alias
    ollama cp "$MODEL_NAME" "$MODEL_TAG" 2>/dev/null || true
    
    log "✅ Gemma 4 ready!"
}

# ─────────────────────────────────────────
# Phase 3: Benchmark
# ─────────────────────────────────────────
phase3() {
    echo ""
    info "Phase 3: Running Gemma 4 benchmarks..."
    echo ""
    
    BENCH_FILE="$NODE_DIR/benchmarks/gemma4-results.txt"
    
    cat > "/tmp/bench_gemma.py" << 'PYEOF'
import subprocess, json, time, sys

questions = [
    {
        "name": "Basic Q&A",
        "prompt": "What is machine learning? Explain in 2 sentences.",
        "max_tokens": 100
    },
    {
        "name": "Vietnamese",
        "prompt": "Giải thích học máy là gì? Trả lời ngắn gọn.",
        "max_tokens": 100
    },
    {
        "name": "Code Generation",
        "prompt": "Write Python code to reverse a linked list.",
        "max_tokens": 200
    },
    {
        "name": "Reasoning",
        "prompt": "If you have 3 apples and give 1 away, then buy 5 more, how many do you have?",
        "max_tokens": 100
    }
]

results = []
for q in questions:
    print(f"\n  Testing: {q['name']}...", end=" ", flush=True)
    
    payload = json.dumps({
        "model": "gemma-4-12b",
        "messages": [{"role": "user", "content": q["prompt"]}],
        "max_tokens": q["max_tokens"],
        "stream": False
    })
    
    start = time.time()
    try:
        resp = subprocess.run(
            ["curl", "-s", "-X", "POST",
             "http://localhost:11434/v1/chat/completions",
             "-H", "Content-Type: application/json",
             "-d", payload],
            capture_output=True, text=True, timeout=60
        )
        elapsed = round(time.time() - start, 2)
        
        data = json.loads(resp.stdout)
        content = data["choices"][0]["message"]["content"]
        tokens = len(content.split())
        tps = round(tokens / (elapsed or 0.01), 1)
        
        results.append({
            "name": q["name"],
            "time": elapsed,
            "tokens": tokens,
            "tps": tps,
            "response": content[:100] + "..."
        })
        print(f"✅ {elapsed}s ({tps} tok/s)")
        
    except Exception as e:
        print(f"❌ {e}")
        results.append({"name": q["name"], "error": str(e)})

# Summary
print("\n" + "═" * 50)
print("  📊 Gemma 4 Benchmark Results")
print("═" * 50)
total_tps = 0
count = 0
for r in results:
    if "tps" in r:
        print(f"  {r['name']:20s}  {r['time']:6.1f}s  {r['tps']:6.1f} tok/s")
        total_tps += r["tps"]
        count += 1
    else:
        print(f"  {r['name']:20s}  ❌ {r.get('error', '')}")

if count > 0:
    avg = round(total_tps / count, 1)
    print(f"\n  {'Average':20s}  {'':6s}  {avg:6.1f} tok/s")
    print(f"\n  Performance rating:")
    if avg > 20:
        print(f"  🚀 Excellent! Great for on-device LLM")
    elif avg > 10:
        print(f"  👍 Good. Usable for most tasks")
    elif avg > 5:
        print(f"  🐢 Slow but usable for simple tasks")
    else:
        print(f"  🐌 Very slow. Consider smaller model (llama3.2:1b)")
print("═" * 50)

with open(sys.argv[1], "w") as f:
    json.dump({"results": results, "average_tps": avg if count > 0 else 0}, f, indent=2)
PYEOF

    python3 "/tmp/bench_gemma.py" "$BENCH_FILE"
    
    if [ -f "$BENCH_FILE" ]; then
        log "✅ Benchmark saved to $BENCH_FILE"
    fi
}

# ─────────────────────────────────────────
# Phase 4: Create Phone AI config for Gemma 4
# ─────────────────────────────────────────
phase4() {
    echo ""
    info "Phase 4: Creating Phone AI config for Gemma 4..."
    
    # Add to phone-ai.yaml
    CONFIG_FILE="$NODE_DIR/config/phone-ai.yaml"
    if [ -f "$CONFIG_FILE" ]; then
        # Check if Gemma already in config
        if grep -q "gemma-4" "$CONFIG_FILE" 2>/dev/null; then
            log "✅ Gemma 4 already in config"
        else
            cat >> "$CONFIG_FILE" << 'CEOF'

  # Gemma 4 — Google mobile-optimized model
  - name: "gemma-4-12B"
    alias: "phone-gemma-4"
    provider: "ollama"
    capabilities:
      streaming: true
      contextWindow: 32768
      languages: ["en", "vi", "zh", "ja", "ar"]
    requirements:
      min_ram_gb: 6
      recommended_ram_gb: 8
CEOF
            log "✅ Gemma 4 added to phone-ai.yaml"
        fi
    fi
    
    # Also add to TiRouter config
    TIROUTER_CONFIG="$NODE_DIR/config/tirouter.yaml"
    if [ -f "$TIROUTER_CONFIG" ]; then
        python3 -c "
import yaml
with open('$TIROUTER_CONFIG') as f:
    config = yaml.safe_load(f)

for p in config.get('providers', []):
    if p.get('prefix') == 'phone':
        models = p.get('models', [])
        if not any(m['name'] == 'gemma-4-12B' for m in models):
            models.append({
                'name': 'gemma-4-12B',
                'alias': 'phone-gemma-4',
                'capabilities': {'contextWindow': 32768}
            })
            print('✅ Gemma 4 added to tirouter.yaml')

with open('$TIROUTER_CONFIG', 'w') as f:
    yaml.dump(config, f, default_flow_style=False)
" 2>/dev/null || warn "Could not update TiRouter config"
    fi
    
    log "✅ Config updated"
}

# ─────────────────────────────────────────
# Phase 5: Quick test
# ─────────────────────────────────────────
phase5() {
    echo ""
    info "Phase 5: Quick test..."
    
    curl -s -X POST http://localhost:11434/v1/chat/completions \
        -H "Content-Type: application/json" \
        -d '{
            "model": "gemma-4-12b",
            "messages": [{"role": "user", "content": "Hello! Say 2 sentences about yourself."}],
            "max_tokens": 100
        }' \
        --connect-timeout 30 2>/dev/null | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    content = d['choices'][0]['message']['content']
    print(f'✅ Gemma 4 response: {content[:150]}...')
except Exception as e:
    print(f'❌ Test failed: {e}')
" || warn "Quick test failed — model may still be loading"
}

# ═══════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════

case "${1:-all}" in
    download|1)  phase1; phase2 ;;
    benchmark|2) phase3 ;;
    config|3)    phase4 ;;
    quick|4)     phase5 ;;
    all)
        phase1
        phase2
        phase3
        phase4
        phase5
        ;;
    *)
        echo "Usage: $0 {all|download|benchmark|config|quick}"
        echo ""
        echo "  all        Full setup (default)"
        echo "  download   Download only (skip benchmark)"
        echo "  benchmark  Run benchmark only"
        echo "  config     Update config files only"
        echo "  quick      Quick test only"
        exit 1
        ;;
esac

echo ""
log "══════════════════════════════════════════════"
log "  ✅ Gemma 4 setup complete!"
log "══════════════════════════════════════════════"
echo ""
echo "  Test:"
echo "    curl http://localhost:11434/v1/chat/completions \\"
echo "      -d '{\"model\":\"gemma-4-12b\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}],\"stream\":false}'"
echo ""
echo "  Via Phone AI:"
echo "    curl http://localhost:5000/v1/chat/completions \\"
echo "      -d '{\"model\":\"phone/gemma-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}'"
echo ""
echo "  Via TiRouter:"
echo "    curl http://localhost:20128/v1/chat/completions \\"
echo "      -d '{\"model\":\"phone/gemma-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}'"
echo ""
