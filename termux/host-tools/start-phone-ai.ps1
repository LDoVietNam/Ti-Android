<#
.SYNOPSIS
    TiRouter Phone AI Service Manager (PowerShell)
    On-Device LLM via Android Termux — Advanced Management

.DESCRIPTION
    Manages Phone AI service connection and integration with TiRouter.
    Supports connect, disconnect, status, scan, config-write, and auto-reconnect.

.PARAMETER Connect
    Connect to Phone AI at specified IP address
.PARAMETER Disconnect
    Remove Phone AI configuration
.PARAMETER Status
    Show detailed connection status
.PARAMETER Scan
    Scan LAN for Phone AI services (with discovery)
.PARAMETER ConfigWrite
    Write Phone AI provider to config.yaml
.PARAMETER StartLocal
    Start local Ollama test environment
.PARAMETER Watch
    Watch mode: auto-reconnect if connection drops

.EXAMPLE
    .\start-phone-ai.ps1 -Connect 192.168.1.100
    .\start-phone-ai.ps1 -Scan
    .\start-phone-ai.ps1 -Watch -Connect 192.168.1.100
#>

#requires -Version 7

param(
    [string]$Connect,
    [switch]$Disconnect,
    [switch]$Status,
    [switch]$Scan,
    [switch]$ConfigWrite,
    [switch]$StartLocal,
    [switch]$Watch,
    [switch]$Help
)

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$TIROUTER_ROOT = (Resolve-Path (Join-Path $ROOT "..\..\..\Tirouter")).Path
$CACHE_FILE = Join-Path $ROOT "cache" "phone-ai-config.json"
$CONFIG_FILE = Join-Path $TIROUTER_ROOT "CLIProxyAPI" "config.yaml"
$PHONE_AI_PORT = 5000
$PHONE_MCP_PORT = 5100
$PHONE_SSH_PORT = 8022

# ─── Help ───
if ($Help -or ($MyInvocation.Arguments.Count -eq 0 -and !$Connect -and !$Disconnect -and !$Status -and !$Scan -and !$ConfigWrite -and !$StartLocal -and !$Watch)) {
    Write-Host @"

╔══════════════════════════════════════════════╗
║    TiRouter Phone AI Service Manager (PS)    ║
║    On-Device LLM via Android Termux          ║
╚══════════════════════════════════════════════╝

Usage: .\start-phone-ai.ps1 [options]

Options:
  -Connect <ip>       Connect to Phone AI at IP address
  -Disconnect         Remove Phone AI config
  -Status             Show detailed connection status
  -Scan               Scan LAN for Phone AI (multi-threaded)
  -ConfigWrite        Write provider to config.yaml (uses last connected IP)
  -StartLocal         Start local Ollama test environment
  -Watch              Auto-reconnect if connection drops (with -Connect)
  -Help               Show this help

Examples:
  .\start-phone-ai.ps1 -Connect 192.168.1.100
  .\start-phone-ai.ps1 -Scan
  .\start-phone-ai.ps1 -Watch -Connect 192.168.1.100

"@
    exit
}


# ═══════════════════════════════════════════════
#  CONNECT
# ═══════════════════════════════════════════════
function Connect-PhoneAI {
    param([string]$PhoneIP)

    Write-Host "`n[📱] Connecting to Phone AI at $PhoneIP`:$PHONE_AI_PORT..." -ForegroundColor Cyan

    # Test connection
    try {
        $health = Invoke-RestMethod -Uri "http://${PhoneIP}:${PHONE_AI_PORT}/health" -TimeoutSec 5
    } catch {
        Write-Host "[✗] Cannot reach Phone AI at ${PhoneIP}:${PHONE_AI_PORT}!" -ForegroundColor Red
        Write-Host "`n  Possible issues:" -ForegroundColor Yellow
        Write-Host "  1. Phone not on same Wi-Fi network"
        Write-Host "  2. Phone AI service not running"
        Write-Host "  3. Firewall blocking port ${PHONE_AI_PORT}"
        return $false
    }

    # Get models
    $models = @()
    try {
        $modelResp = Invoke-RestMethod -Uri "http://${PhoneIP}:${PHONE_AI_PORT}/v1/models" -TimeoutSec 5
        $models = $modelResp.data | ForEach-Object { $_.id }
    } catch {}

    # Save cache
    $null = New-Item -ItemType Directory -Path (Split-Path $CACHE_FILE -Parent) -Force
    $cache = @{
        phone_ip     = $PhoneIP
        phone_port   = $PHONE_AI_PORT
        mcp_port     = $PHONE_MCP_PORT
        connected_at = (Get-Date).ToString("o")
        model        = if ($models.Count -gt 0) { $models[0] } else { "llama3.2:1b" }
        all_models   = $models
    }
    $cache | ConvertTo-Json | Set-Content $CACHE_FILE

    Write-Host "[✅] Connected to Phone AI!" -ForegroundColor Green
    Write-Host "`n  ─── Phone Info ───" -ForegroundColor Cyan
    Write-Host "  Status:   $($health.status)"
    Write-Host "  Ollama:   $($health.ollama_connected)"
    Write-Host "  Battery:  $($health.battery.percent)%" -ForegroundColor $(if ($health.battery.percent -lt 20) { "Red" } else { "White" })
    Write-Host "  Memory:   $($health.memory_percent)%"
    Write-Host "  Platform: $($health.platform)"

    Write-Host "`n  ─── Available Models ($($models.Count)) ───" -ForegroundColor Cyan
    $models | ForEach-Object { Write-Host "  📦 $_" -ForegroundColor Green }

    Write-Host "`n  ─── Integration Endpoints ───" -ForegroundColor Cyan
    Write-Host "  Direct REST:  http://${PhoneIP}:${PHONE_AI_PORT}/v1/chat/completions"
    Write-Host "  Direct WS:    ws://${PhoneIP}:${PHONE_AI_PORT}/ws/chat"
    Write-Host "  MCP Server:   http://${PhoneIP}:${PHONE_MCP_PORT}/mcp"
    Write-Host "  SSH:          ssh user@${PhoneIP} -p ${PHONE_SSH_PORT}"

    # Write config
    Write-Host "`n[⚙] Writing provider to config.yaml..." -ForegroundColor Yellow
    Write-PhoneAIConfig -PhoneIP $PhoneIP

    Write-Host "`n[✅] Phone AI ready!" -ForegroundColor Green
    Write-Host "  Use model prefix: phone/llama3.2:1b" -ForegroundColor Green
    Write-Host "  curl http://localhost:20128/v1/chat/completions -H 'Content-Type: application/json' -d '{"""model""":"""phone/llama3.2:1b""","""messages""":[{"role":"user","content":"Hi"}]}'" -ForegroundColor Gray

    return $true
}


# ═══════════════════════════════════════════════
#  DISCONNECT
# ═══════════════════════════════════════════════
function Disconnect-PhoneAI {
    Write-Host "`n[📱] Disconnecting Phone AI..." -ForegroundColor Yellow

    if (Test-Path $CACHE_FILE) {
        Remove-Item $CACHE_FILE -Force
        Write-Host "[✓] Cache cleared." -ForegroundColor Green
    }

    # Remove from config.yaml
    if (Test-Path $CONFIG_FILE) {
        $yaml = Get-Content $CONFIG_FILE -Raw
        # Remove phone-ai section
        $pattern = '# ▸ Phone AI — On-Device LLM.*?\n(?:  .*?\n)*'
        $newYaml = $yaml -replace $pattern, ''
        if ($newYaml -ne $yaml) {
            $newYaml | Set-Content $CONFIG_FILE
            Write-Host "[✓] Phone AI provider removed from config.yaml" -ForegroundColor Green
        }
    }

    Write-Host "[✅] Disconnected." -ForegroundColor Green
}


# ═══════════════════════════════════════════════
#  STATUS
# ═══════════════════════════════════════════════
function Get-PhoneAIStatus {
    if (!(Test-Path $CACHE_FILE)) {
        Write-Host "`n[📱] Phone AI: NOT CONNECTED" -ForegroundColor Yellow
        Write-Host "`n  To connect: .\start-phone-ai.ps1 -Connect <phone-ip>"
        Write-Host "  Or scan:    .\start-phone-ai.ps1 -Scan"
        return
    }

    $cache = Get-Content $CACHE_FILE | ConvertFrom-Json
    $phoneIP = $cache.phone_ip

    Write-Host "`n[📱] Phone AI Connection Status" -ForegroundColor Cyan
    Write-Host "`n  IP:       $phoneIP"
    Write-Host "  Port:     $PHONE_AI_PORT"
    Write-Host "  Cached:   $($cache.connected_at)"

    # Check if alive
    try {
        $health = Invoke-RestMethod -Uri "http://${phoneIP}:${PHONE_AI_PORT}/health" -TimeoutSec 3
        Write-Host "  Status:   ✅ CONNECTED" -ForegroundColor Green
        Write-Host "  Battery:  $($health.battery.percent)%" -ForegroundColor $(if ($health.battery.percent -lt 20) { "Red" } else { "White" })
        Write-Host "  Ollama:   $($health.ollama_connected)"
        Write-Host "  Memory:   $($health.memory_percent)%"
        Write-Host "  CPU:      $($health.cpu_percent)%"
        Write-Host "  Platform: $($health.platform)"
    } catch {
        Write-Host "  Status:   ❌ DISCONNECTED" -ForegroundColor Red
        Write-Host "  Phone may be offline or service stopped."
    }

    # Config check
    if (Test-Path $CONFIG_FILE) {
        $hasProvider = (Get-Content $CONFIG_FILE -Raw) -match 'phone-ai'
        if ($hasProvider) {
            Write-Host "  Config:   ✅ Provider in config.yaml" -ForegroundColor Green
        } else {
            Write-Host "  Config:   ⚠️  Not in config.yaml" -ForegroundColor Yellow
        }
    }
}


# ═══════════════════════════════════════════════
#  SCAN LAN (multi-threaded)
# ═══════════════════════════════════════════════
function Scan-LAN {
    Write-Host "`n[🔍] Scanning LAN for Phone AI services..." -ForegroundColor Cyan
    Write-Host "  (Checking port $PHONE_AI_PORT)" -ForegroundColor Gray

    # Get local IP and determine subnet
    $localIP = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch "Loopback|Virtual|Bluetooth" -and $_.PrefixOrigin -eq "Dhcp" }).IPAddress
    if (!$localIP) { $localIP = "192.168.1.1" }
    $subnet = $localIP -replace '\.\d+$', ''

    Write-Host "  Local IP: $localIP" -ForegroundColor Gray
    Write-Host "  Subnet:   $subnet.x" -ForegroundColor Gray
    Write-Host "`n  Scanning (this may take 30-60 seconds)..."

    $found = $false
    $tasks = @()

    # Scan last octet 1-254
    1..254 | ForEach-Object -Parallel {
        $ip = "$using:subnet.$_"
        $port = $using:PHONE_AI_PORT
        try {
            $tcpClient = New-Object System.Net.Sockets.TcpClient
            $connect = $tcpClient.BeginConnect($ip, $port, $null, $null)
            $wait = $connect.AsyncWaitHandle.WaitOne(300, $false)
            if ($wait -and $tcpClient.Connected) {
                $tcpClient.EndConnect($connect)
                Write-Host "  [✅] Found at $ip`:$port!" -ForegroundColor Green
                # Get health info
                try {
                    $health = Invoke-RestMethod -Uri "http://${ip}:${port}/health" -TimeoutSec 3
                    Write-Host "  Battery: $($health.battery.percent)%  Ollama: $($health.ollama_connected)" -ForegroundColor Gray
                } catch {}
                $ip
            }
            $tcpClient.Close()
        } catch {}
    } -ThrottleLimit 50 | ForEach-Object {
        if ($_) {
            $found = $true
            Write-Host "`n[✅] Phone AI detected at $_" -ForegroundColor Green
            $choice = Read-Host "`nConnect to this phone? (Y/N)"
            if ($choice -eq 'Y' -or $choice -eq 'y') {
                Connect-PhoneAI -PhoneIP $_
            }
        }
    }

    if (!$found) {
        Write-Host "`n[✗] No Phone AI found on $subnet.x/24" -ForegroundColor Red
        Write-Host "`n  Make sure:" -ForegroundColor Yellow
        Write-Host "  1. Phone is on same Wi-Fi network"
        Write-Host "  2. Phone AI service is running"
        Write-Host "  3. Phone firewall allows port $PHONE_AI_PORT"
    }
}


# ═══════════════════════════════════════════════
#  CONFIG WRITE
# ═══════════════════════════════════════════════
function Write-PhoneAIConfig {
    param([string]$PhoneIP)

    if ([string]::IsNullOrEmpty($PhoneIP)) {
        if (Test-Path $CACHE_FILE) {
            $cache = Get-Content $CACHE_FILE | ConvertFrom-Json
            $PhoneIP = $cache.phone_ip
        }
    }

    if ([string]::IsNullOrEmpty($PhoneIP)) {
        Write-Host "[✗] No Phone IP available. Connect first or specify -Connect <ip>" -ForegroundColor Red
        return
    }

    if (!(Test-Path $CONFIG_FILE)) {
        Write-Host "[✗] config.yaml not found at $CONFIG_FILE" -ForegroundColor Red
        return
    }

    $providerBlock = @"

# ▸ Phone AI — On-Device LLM (Android Termux)
# Connected: $(Get-Date)
- name: "phone-ai"
  prefix: "phone"
  base-url: "http://${PhoneIP}:${PHONE_AI_PORT}"
  models:
    - name: "llama3.2:1b"
      alias: "phone-llama-1b"
      capabilities:
        streaming: true
        contextWindow: 8192
    - name: "llama3.2:3b"
      alias: "phone-llama-3b"
      capabilities:
        streaming: true
        contextWindow: 8192
    - name: "phi3:mini"
      alias: "phone-phi3"
      capabilities:
        streaming: true
        contextWindow: 4096
    - name: "qwen2.5:1.5b"
      alias: "phone-qwen"
      capabilities:
        streaming: true
        contextWindow: 8192
    - name: "gemma2:2b"
      alias: "phone-gemma2"
      capabilities:
        streaming: true
        contextWindow: 8192

"@

    $yaml = Get-Content $CONFIG_FILE -Raw

    # Insert before openai-compatibility section
    if ($yaml -match 'openai-compatibility:') {
        $yaml = $yaml -replace 'openai-compatibility:', "$providerBlock`openai-compatibility:"
    } else {
        $yaml += "`n$providerBlock"
    }

    # Backup
    Copy-Item $CONFIG_FILE "$CONFIG_FILE.bak" -Force

    $yaml | Set-Content $CONFIG_FILE
    Write-Host "[✅] Provider 'phone/*' written to config.yaml!" -ForegroundColor Green
    Write-Host "`n  Use model prefix: phone/llama3.2:1b" -ForegroundColor Cyan
    Write-Host "  Restart TiRouter to apply." -ForegroundColor Yellow
}


# ═══════════════════════════════════════════════
#  START LOCAL
# ═══════════════════════════════════════════════
function Start-LocalTest {
    Write-Host "`n[💻] Starting local Phone AI test environment..." -ForegroundColor Cyan

    # Check Ollama
    $ollamaPath = Get-Command "ollama" -ErrorAction SilentlyContinue
    if (!$ollamaPath) {
        Write-Host "[✗] Ollama not found!" -ForegroundColor Red
        Write-Host " Download from: https://ollama.com/download"
        return
    }

    # Start Ollama if not running
    try {
        Invoke-RestMethod -Uri "http://localhost:11434/api/tags" -TimeoutSec 2 | Out-Null
        Write-Host "[✅] Ollama already running" -ForegroundColor Green
    } catch {
        Write-Host "[🔄] Starting Ollama server..." -ForegroundColor Yellow
        Start-Process ollama -ArgumentList "serve" -WindowStyle Minimized
        Start-Sleep 3
    }

    # Pull model
    $ollamaList = ollama list 2>$null
    if ($ollamaList -notmatch "llama3.2:1b") {
        Write-Host "[📥] Pulling llama3.2:1b (background downloading)..." -ForegroundColor Yellow
        Start-Process ollama -ArgumentList "pull llama3.2:1b" -WindowStyle Minimized
    }

    # Start Python service
    $pythonScript = Join-Path $ROOT "..\services\phone_ai_service.py"
    if (Test-Path $pythonScript) {
        Write-Host "[🐍] Starting Phone AI Python service..." -ForegroundColor Yellow
        $ps = Start-Process python -ArgumentList "`"$pythonScript`" --port 5000 --ollama-url http://localhost:11434" -WindowStyle Minimized -PassThru
        Start-Sleep 3

        try {
            Invoke-RestMethod -Uri "http://localhost:5000/health" -TimeoutSec 2 | Out-Null
            Write-Host "[✅] Phone AI Service running on http://localhost:5000!" -ForegroundColor Green
            Write-Host "`n  Connect TiRouter: .\start-phone-ai.ps1 -Connect localhost" -ForegroundColor Cyan
            Write-Host "  Or:              .\start-phone-ai.ps1 -ConfigWrite" -ForegroundColor Cyan
        } catch {
            Write-Host "[⚠] Service may still be starting..." -ForegroundColor Yellow
        }
    } else {
        Write-Host "[✗] phone_ai_service.py not found at $pythonScript" -ForegroundColor Red
    }
}


# ═══════════════════════════════════════════════
#  WATCH — Auto-reconnect
# ═══════════════════════════════════════════════
function Watch-PhoneAI {
    param([string]$PhoneIP)

    Write-Host "`n[👁] Watch mode enabled!" -ForegroundColor Cyan
    Write-Host "  Will check connection every 30 seconds.`n"

    $iteration = 0
    while ($true) {
        $iteration++
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Check #$iteration..." -NoNewline

        try {
            $health = Invoke-RestMethod -Uri "http://${PhoneIP}:${PHONE_AI_PORT}/health" -TimeoutSec 5
            Write-Host " ✅ Online (battery: $($health.battery.percent)%, mem: $($health.memory_percent)%)" -ForegroundColor Green
        } catch {
            Write-Host " ❌ Disconnected!" -ForegroundColor Red
            Write-Host "  [🔄] Attempting reconnect..." -ForegroundColor Yellow
            $result = Connect-PhoneAI -PhoneIP $PhoneIP
            if ($result) {
                Write-Host "  [✅] Reconnected successfully!" -ForegroundColor Green
            } else {
                Write-Host "  [✗] Reconnect failed. Will retry in 30s." -ForegroundColor Red
            }
        }

        # Update dashboard if running
        $dashFile = Join-Path $ROOT "cache" "phone-ai-live.json"
        try {
            $health = Invoke-RestMethod -Uri "http://${PhoneIP}:${PHONE_AI_PORT}/health" -TimeoutSec 3
            @{
                timestamp = (Get-Date).ToString("o")
                battery = $health.battery.percent
                memory = $health.memory_percent
                cpu = $health.cpu_percent
                ollama = $health.ollama_connected
                uptime = $health.uptime_seconds
            } | ConvertTo-Json | Set-Content $dashFile
        } catch {}

        Start-Sleep 30
    }
}


# ═══════════════════════════════════════════════
#  MAIN DISPATCH
# ═══════════════════════════════════════════════
Clear-Host
Write-Host @"
╔══════════════════════════════════════════════╗
║      TiRouter Phone AI Service Manager       ║
║      On-Device LLM via Android Termux        ║
╚══════════════════════════════════════════════╝
"@ -ForegroundColor Cyan

if ($Connect) {
    Connect-PhoneAI -PhoneIP $Connect

    if ($Watch) {
        Watch-PhoneAI -PhoneIP $Connect
    }
} elseif ($Disconnect) {
    Disconnect-PhoneAI
} elseif ($Status) {
    Get-PhoneAIStatus
} elseif ($Scan) {
    Scan-LAN
} elseif ($ConfigWrite) {
    Write-PhoneAIConfig
} elseif ($StartLocal) {
    Start-LocalTest
} else {
    # Interactive menu
    do {
        Write-Host "`n  ─── Actions ───" -ForegroundColor Cyan
        Write-Host "  [1] Connect to Phone AI"
        Write-Host "  [2] Disconnect"
        Write-Host "  [3] Show Status"
        Write-Host "  [4] Scan LAN"
        Write-Host "  [5] Write Config"
        Write-Host "  [6] Start Local Test"
        Write-Host "  [7] Watch (auto-reconnect)"
        Write-Host "  [0] Exit`n"

        $choice = Read-Host "Select option"
        switch ($choice) {
            "1" { $ip = Read-Host "Enter Phone IP"; Connect-PhoneAI -PhoneIP $ip }
            "2" { Disconnect-PhoneAI }
            "3" { Get-PhoneAIStatus }
            "4" { Scan-LAN }
            "5" { Write-PhoneAIConfig }
            "6" { Start-LocalTest }
            "7" { $ip = Read-Host "Enter Phone IP"; Watch-PhoneAI -PhoneIP $ip }
        }
    } while ($choice -ne "0")
}
