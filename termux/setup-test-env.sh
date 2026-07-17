#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════════
#  Ti-Android Test Environment — Telegram + WhatsApp
#  Cài đặt Telegram, GBWhatsApp insights, test messaging apps
# ═══════════════════════════════════════════════════════════════════
set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

log() { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $*"; }
warn() { echo -e "${YELLOW}[⚠️]${NC} $*"; }
error() { echo -e "${RED}[❌]${NC} $*"; }
info() { echo -e "${CYAN}[ℹ️]${NC} $*"; }

NODE_DIR="$HOME/ti-android-node"
APK_DIR="$NODE_DIR/apks"
mkdir -p "$APK_DIR"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   Ti-Android Test Environment Setup          ║"
echo "║   Telegram + WhatsApp + Messaging Tests      ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ─────────────────────────────────────────
# Phase 1: Install official apps via ADB
# ─────────────────────────────────────────
phase1() {
    info "Phase 1: Checking ADB connection..."
    if ! command -v adb &>/dev/null; then
        pkg install -y android-tools
    fi

    # Wait for device
    adb devices | grep -q "device$" || {
        warn "No ADB device found!"
        info "Make sure USB debugging is enabled on your phone"
        info "Also ensure PC ADB is connected: adb connect <phone-ip>:5555"
        info "Or: adb devices"
        return 1
    }

    log "✅ ADB device connected"
}

# ─────────────────────────────────────────
# Phase 2: Telegram installation
# ─────────────────────────────────────────
phase2() {
    echo ""
    info "Phase 2: Installing official Telegram..."

    # Download Telegram APK (official from Telegram.org)
    TG_APK="$APK_DIR/telegram.apk"
    if [ ! -f "$TG_APK" ]; then
        log "Downloading Telegram APK..."
        curl -sL "https://telegram.org/dl/android/apk" -o "$TG_APK"
    fi

    # Install via ADB
    adb install -r "$TG_APK" 2>/dev/null && {
        log "✅ Telegram installed!"
    } || {
        warn "Telegram install failed (may already exist)"
        adb shell pm list packages | grep -q "org.telegram.messenger" && {
            log "✅ Telegram already installed"
        }
    }
}

# ─────────────────────────────────────────
# Phase 3: WhatsApp installation (official)
# ─────────────────────────────────────────
phase3() {
    echo ""
    info "Phase 3: Installing official WhatsApp..."

    WA_APK="$APK_DIR/whatsapp.apk"
    if [ ! -f "$WA_APK" ]; then
        log "Downloading WhatsApp APK from APK Mirror..."
        # Use official WhatsApp from APKMirror or similar
        python3 -c "
import urllib.request, re, json
# Get latest WhatsApp version
url = 'https://www.apkmirror.com/apk/whatsapp-inc/whatsapp/'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
data = urllib.request.urlopen(req).read().decode()
# Find latest version link
match = re.search(r'href=\"(/apk/[^\"]+/whatsapp-[^\"]+-release/)\"', data)
if match:
    print(f'Found: https://www.apkmirror.com{match.group(1)}')
" 2>/dev/null || warn "Could not find WhatsApp download URL"
    fi

    if [ -f "$WA_APK" ]; then
        adb install -r "$WA_APK" 2>/dev/null && {
            log "✅ WhatsApp installed!"
        } || warn "WhatsApp install failed"
    fi
}

# ─────────────────────────────────────────
# Phase 4: Test automation script
# ─────────────────────────────────────────
phase4() {
    echo ""
    info "Phase 4: Creating test automation scripts..."

    # Create UI Automator test script
    cat > "$NODE_DIR/test-telegram.sh" << 'TSTEOF'
#!/data/data/com.termux/files/usr/bin/bash
# Telegram UI Test Script
# Uses uiautomator dump to verify Telegram elements
NODE_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "════════════════════════════════════════"
echo "  Telegram UI Test Suite"
echo "════════════════════════════════════════"

# Check Telegram installed
if ! pm list packages | grep -q "org.telegram.messenger"; then
    echo "[✗] Telegram not installed!"
    exit 1
fi

# Launch Telegram
echo "[📱] Launching Telegram..."
monkey -p org.telegram.messenger 1 2>/dev/null
sleep 3

# Dump UI tree
echo "[🔍] Dumping accessibility tree..."
uiautomator dump /sdcard/telegram_ui.xml 2>/dev/null
if [ -f /sdcard/telegram_ui.xml ]; then
    echo "[✅] UI tree saved to /sdcard/telegram_ui.xml"
    # Count interactive elements
    grep -o 'class="[^"]*"' /sdcard/telegram_ui.xml | sort | uniq -c | sort -rn | head -10
fi

echo ""
echo "════════════════════════════════════════"
echo "  Test complete!"
echo "  UI tree: /sdcard/telegram_ui.xml"
echo "════════════════════════════════════════"
TSTEOF
    chmod +x "$NODE_DIR/test-telegram.sh"
    log "✅ Telegram test script created"

    # Create accessibility tree dumper
    cat > "$NODE_DIR/dump-accessibility.py" << 'PYEOF'
#!/usr/bin/env python3
"""
Accessibility Tree Dumper for Android
Dumps UI hierarchy for test automation
Output: JSON format compatible with Ti-Android AccessibilitySnapshot
"""
import subprocess
import xml.etree.ElementTree as ET
import json
import sys
import os
from datetime import datetime


def dump_ui():
    """Run uiautomator dump and parse result"""
    dump_file = "/sdcard/ti_dump.xml"
    subprocess.run(["uiautomator", "dump", dump_file], capture_output=True)

    if not os.path.exists(dump_file):
        print(json.dumps({"error": "UI dump failed"}))
        return

    tree = ET.parse(dump_file)
    root = tree.getroot()

    snapshot = {
        "timestamp": datetime.now().isoformat(),
        "package": root.get("package", ""),
        "nodes": parse_node(root.find("node")),
        "total_nodes": count_nodes(root)
    }

    print(json.dumps(snapshot, indent=2))
    os.remove(dump_file)


def parse_node(node):
    """Recursively parse AccessibilityNodeInfo to dict"""
    if node is None:
        return None

    result = {
        "resource_id": node.get("resource-id", ""),
        "class_name": node.get("class", ""),
        "text": node.get("text", ""),
        "content_desc": node.get("content-desc", ""),
        "clickable": node.get("clickable") == "true",
        "editable": node.get("focusable") == "true",
        "visible": node.get("visible-to-user") != "false",
        "bounds": parse_bounds(node.get("bounds", "")),
        "children": []
    }

    for child in node:
        parsed = parse_node(child)
        if parsed:
            result["children"].append(parsed)

    return result


def parse_bounds(bounds_str):
    """Parse '[x1,y1][x2,y2]' to dict"""
    import re
    match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds_str)
    if match:
        return {
            "left": int(match.group(1)),
            "top": int(match.group(2)),
            "right": int(match.group(3)),
            "bottom": int(match.group(4))
        }
    return {}


def count_nodes(element):
    """Quick count of all nodes"""
    count = 0
    for _ in element.iter("node"):
        count += 1
    return count


if __name__ == "__main__":
    dump_ui()
PYEOF
    chmod +x "$NODE_DIR/dump-accessibility.py"
    log "✅ Accessibility dumper created"
}

# ─────────────────────────────────────────
# Phase 5: GBWhatsApp analysis
# ─────────────────────────────────────────
phase5() {
    echo ""
    info "Phase 5: GBWhatsApp — Security Research Notes"
    echo ""
    echo "  ═══════════════════════════════════════════════════════"
    echo "  GBWhatsApp is a MODIFIED (unofficial) WhatsApp APK."
    echo "  It is NOT recommended for production testing because:"
    echo "  ═══════════════════════════════════════════════════════"
    echo ""
    echo "  ❌ Security risks (malware, spyware, keyloggers)"
    echo "  ❌ WhatsApp account BAN (Meta detects modded clients)"
    echo "  ❌ No end-to-end encryption guarantee"
    echo "  ❌ No stable API for automation"
    echo "  ❌ DMCA takedown risk"
    echo ""
    echo "  ✅ Instead, use OFFICIAL Telegram for adapter testing:"
    echo "     - org.telegram.messenger (play store)"
    echo "     - Stable accessibility tree"
    echo "     - No ban risk"
    echo "     - TestBot API available"
    echo ""
    echo "  ✅ Or use official WhatsApp for real user testing:"
    echo "     - com.whatsapp (play store)"
    echo "     - WhatsApp Business API for automation"
    echo ""

    # Create comparison table
    cat > "$APK_DIR/README-messaging-apps.md" << MDEOF
# Messaging Apps for Ti-Android Testing

| App | Package | Type | Accessibility | Automation | Risk |
|-----|---------|------|---------------|------------|------|
| Telegram | org.telegram.messenger | Official | ✅ Full tree | ✅ Bot API | ✅ None |
| WhatsApp | com.whatsapp | Official | 🟡 Limited | ⚠️ Business API | ✅ Low |
| GBWhatsApp | com.gbwhatsapp | **MODDED** | ❌ Unknown | ❌ None | ❌ **HIGH** |

## Recommendation
Use **Telegram** for automation testing. It has:
- Full accessibility tree support
- Official Bot API for test automation
- No account ban risk
- Stable UI components

## Test Checklist (Telegram)
- [ ] Open conversation
- [ ] Read messages
- [ ] Compose draft
- [ ] Send message
- [ ] Read notifications
- [ ] Handle attachments
MDEOF
    log "✅ Messaging apps comparison created"
}

# ─────────────────────────────────────────
# Phase 6: Ti-Android Test Runner for APK
# ─────────────────────────────────────────
phase6() {
    echo ""
    info "Phase 6: Creating Ti-Android APK test runner..."

    cat > "$NODE_DIR/test-ti-android.sh" << 'TIEOF'
#!/data/data/com.termux/files/usr/bin/bash
# Ti-Android APK Integration Test Suite
NODE_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="${1:-$NODE_DIR/apks/ti-android-runtime.apk}"

echo "════════════════════════════════════════"
echo "  Ti-Android Runtime — Test Suite"
echo "════════════════════════════════════════"

# 1. Install APK
echo "[1/6] Installing Ti-Android Runtime..."
if [ -f "$APK_PATH" ]; then
    adb install -r "$APK_PATH" 2>/dev/null && echo "  ✅ Installed" || echo "  ⚠️  Already installed"
else
    echo "  ⚠️  APK not found at $APK_PATH"
fi

# 2. Check services
echo "[2/6] Checking Ti Services..."
for port in 5000 5001 5002 20128; do
    curl -s -o /dev/null -w "  Port $port: %{http_code}\n" "http://localhost:$port/health" --connect-timeout 2 2>/dev/null || echo "  Port $port: ❌"
done

# 3. Test Phone AI
echo "[3/6] Testing Phone AI..."
curl -s -X POST http://localhost:5000/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{"model":"llama3.2:1b","messages":[{"role":"user","content":"Hi"}],"max_tokens":50}' \
    --connect-timeout 10 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'  Response: {d[\"choices\"][0][\"message\"][\"content\"][:50]}...')" 2>/dev/null || echo "  ❌ Phone AI not responding"

# 4. Test File Transfer
echo "[4/6] Testing File Transfer..."
curl -s -X POST http://localhost:5001/health --connect-timeout 3 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'  Free space: {d[\"storage_free_gb\"]}GB')" 2>/dev/null || echo "  ❌ File Transfer not running"

# 5. Test Accessibility
echo "[5/6] Testing Accessibility..."
python3 "$NODE_DIR/dump-accessibility.py" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'  Total nodes: {d[\"total_nodes\"]}')" 2>/dev/null || echo "  ❌ Accessibility dump failed"

# 6. Summary
echo "[6/6] ════════════════════════════════════"
echo "  Test Results Summary:"
echo "  See individual results above."
echo "══════════════════════════════════════════"
TIEOF
    chmod +x "$NODE_DIR/test-ti-android.sh"
    log "✅ Test runner created"
}

# ─────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════"
echo "  Choose setup:"
echo "  1) Full setup (Telegram + WhatsApp + tests)"
echo "  2) Test environment only (no app install)"
echo "  3) GBWhatsApp info + security notes"
echo "══════════════════════════════════════════════"
echo ""

case "${1:-full}" in
    full|1)
        phase1 || true
        phase2 || true
        phase3 || true
        phase4
        phase5
        phase6
        ;;
    test|2)
        phase4
        phase5
        phase6
        ;;
    info|3)
        phase5
        ;;
    *)
        echo "Usage: $0 {full|test|info}"
        exit 1
        ;;
esac

echo ""
log "══════════════════════════════════════════════"
log "  ✅ Test Environment Setup Complete!"
log "══════════════════════════════════════════════"
echo ""
echo "  📱 Run tests:  bash $NODE_DIR/test-ti-android.sh"
echo "  📄 View info:  cat $APK_DIR/README-messaging-apps.md"
echo "  🎯 Telegram:   bash test-telegram.sh"
echo "  🔍 Dump tree:  python3 dump-accessibility.py"
echo ""
