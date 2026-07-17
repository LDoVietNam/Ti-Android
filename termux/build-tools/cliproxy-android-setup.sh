#!/data/data/com.termux/files/usr/bin/bash
# Auto-install CLIProxyAPI on Termux Android
# Run: bash build-tools/cliproxy-android-setup.sh

set -e

echo "=== Step 1: Update Termux ==="
pkg update && pkg upgrade -y

echo "=== Step 2: Install proot-distro and packages ==="
pkg install -y proot-distro curl wget git tmux nodejs

echo "=== Step 3: Install Ubuntu (proot) ==="
proot-distro install ubuntu

echo "=== Step 4: Login to Ubuntu and install Go ==="
proot-distro login ubuntu --shared-tmp -- bash -c "
    apt update
    apt install -y golang-go curl git wget
    
    # Clone CLIProxyAPI
    cd /tmp
    git clone https://github.com/router-for-me/CLIProxyAPI.git 2>/dev/null || echo 'Already cloned'
    
    # Build ARM64 binary
    cd CLIProxyAPI
    go mod download
    go build -ldflags='-s -w' -o cli-proxy-api-linux-arm64 ./cmd/server
    
    # Copy binary and matching config template to Termux home
    cp cli-proxy-api-linux-arm64 /data/data/com.termux/files/home/
    cp config.example.yaml /data/data/com.termux/files/home/config.yaml
"

echo "=== DONE ==="
echo "Binary available at: ~/cli-proxy-api-linux-arm64"
echo "To run: cd ~ && tmux new-session -d -s cliproxy './cli-proxy-api-linux-arm64 --port 8317' && tmux attach -t cliproxy"
