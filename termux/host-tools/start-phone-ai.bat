@echo off
chcp 65001 >nul
title TiRouter Phone AI Service
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
for %%I in ("%ROOT%..\..\..\Tirouter") do set "TIROUTER_ROOT=%%~fI"
set "PHONE_AI_PORT=5000"
set "PHONE_MCP_PORT=5100"
set "PHONE_SSH_PORT=8022"
set "CACHE_FILE=%ROOT%cache\phone-ai-config.json"
set "CONFIG_FILE=%TIROUTER_ROOT%\CLIProxyAPI\config.yaml"

:: ─── Parse flags ───
if /I "%~1"=="--help" goto :help
if /I "%~1"=="-h" goto :help
if /I "%~1"=="--connect" set "ACTION=connect" & set "PHONE_IP=%~2" & goto :main
if /I "%~1"=="--disconnect" set "ACTION=disconnect" & goto :main
if /I "%~1"=="--status" set "ACTION=status" & goto :main
if /I "%~1"=="--scan" set "ACTION=scan" & goto :main
if /I "%~1"=="--config-write" set "ACTION=config_write" & set "PHONE_IP=%~2" & goto :main
if /I "%~1"=="--start-local" set "ACTION=start_local" & goto :main
if /I "%~1"=="--stop" set "ACTION=stop" & goto :main

:: Default: show menu if no action
if "%1"=="" goto :interactive
echo Unknown option: %1
goto :help

:interactive
cls
echo ================================================
echo      TiRouter Phone AI Service Manager
echo      On-Device LLM via Android Termux
echo ================================================
echo.
echo  ── Actions ──
echo  [1] Connect to Phone AI
echo  [2] Disconnect Phone AI
echo  [3] Show Status
echo  [4] Scan LAN for Phone AI
echo  [5] Write Config (provider to config.yaml)
echo  [6] Start Local Test (Ollama on this PC)
echo.
echo  [0] Back to Menu
echo.
choice /c 1234560 /n /m "Select option: "
set "opt=%errorlevel%"
if "%opt%"=="1" set "ACTION=connect" & goto :ask_ip
if "%opt%"=="2" set "ACTION=disconnect" & goto :main
if "%opt%"=="3" set "ACTION=status" & goto :main
if "%opt%"=="4" set "ACTION=scan" & goto :main
if "%opt%"=="5" set "ACTION=config_write" & goto :ask_ip
if "%opt%"=="6" set "ACTION=start_local" & goto :main
if "%opt%"=="7" goto :eof
goto :interactive

:ask_ip
set /p "PHONE_IP=Enter Phone IP (e.g. 192.168.1.100): "
if "%PHONE_IP%"=="" (
    echo Invalid IP
    pause
    goto :interactive
)
goto :main

:main
echo.
echo ════════════════════════════════════════════
echo   TiRouter Phone AI — %ACTION%
echo ════════════════════════════════════════════

if "%ACTION%"=="connect" goto :connect
if "%ACTION%"=="disconnect" goto :disconnect
if "%ACTION%"=="status" goto :status
if "%ACTION%"=="scan" goto :scan
if "%ACTION%"=="config_write" goto :config_write
if "%ACTION%"=="start_local" goto :start_local
if "%ACTION%"=="stop" goto :stop
goto :eof


:: ════════════════════════════════════════════
::  CONNECT — Connect to phone AI service
:: ════════════════════════════════════════════
:connect
echo.
echo [📱] Connecting to Phone AI at %PHONE_IP%:%PHONE_AI_PORT%...
echo.

:: Save config
if not exist "%ROOT%cache" mkdir "%ROOT%cache"
echo {"phone_ip":"%PHONE_IP%","phone_port":%PHONE_AI_PORT%,"mcp_port":%PHONE_MCP_PORT%,"connected_at":"%date% %time%","model":"llama3.2:1b"} > "%CACHE_FILE%"

:: Test connection
curl -s --connect-timeout 5 "http://%PHONE_IP%:%PHONE_AI_PORT%/health" >nul 2>&1
if !errorlevel! neq 0 (
    echo [✗] Cannot reach Phone AI at %PHONE_IP%:%PHONE_AI_PORT%!
    echo.
    echo  Possible issues:
    echo  1. Phone not on same Wi-Fi network
    echo  2. Phone AI service not running
    echo  3. Firewall blocking port %PHONE_AI_PORT%
    echo.
    echo  Run: start-phone-ai.bat --scan    (to find phone)
    echo  Or check: curl http://%PHONE_IP%:%PHONE_AI_PORT%/health
    echo.
    pause
    exit /b 1
)

:: Get phone info
for /f "tokens=*" %%a in ('curl -s "http://%PHONE_IP%:%PHONE_AI_PORT%/health"') do set "HEALTH_JSON=%%a"
for /f "tokens=*" %%a in ('curl -s "http://%PHONE_IP%:%PHONE_AI_PORT%/v1/models"') do set "MODELS_JSON=%%a"

echo [✅] Connected to Phone AI!
echo.
echo  ─── Phone Info ───
powershell -Command "$h = '%HEALTH_JSON%' | ConvertFrom-Json; Write-Output ('  Status:   ' + $h.status); Write-Output ('  Ollama:   ' + $h.ollama_connected); Write-Output ('  Battery:  ' + $h.battery.percent + '%'); Write-Output ('  Memory:   ' + $h.memory_percent + '%'); Write-Output ('  Hostname: ' + $h.hostname)"
echo.
echo  ─── Available Models ───
powershell -Command "$m = '%MODELS_JSON%' | ConvertFrom-Json; foreach($d in $m.data) { Write-Output ('  📦 ' + $d.id) }"
echo.
echo  ─── Integration Endpoints ───
echo   Direct REST:  http://%PHONE_IP%:%PHONE_AI_PORT%/v1/chat/completions
echo   Direct WS:    ws://%PHONE_IP%:%PHONE_AI_PORT%/ws/chat
echo   MCP Server:   http://%PHONE_IP%:%PHONE_MCP_PORT%/mcp
echo   SSH:          ssh user@%PHONE_IP% -p %PHONE_SSH_PORT%
echo.
echo  ─── Quick Test ───
echo   curl -X POST http://%PHONE_IP%:%PHONE_AI_PORT%/api/chat -H "Content-Type: application/json" -d "{\"prompt\":\"Hello\"}"
echo.

:: Write config to config.yaml automatically
echo  [⚙] Writing provider to config.yaml...
call :config_write %PHONE_IP%

echo.
echo  [✅] Phone AI is ready! Use through TiRouter proxy:
echo   curl http://localhost:20128/v1/chat/completions ^
echo     -H "Content-Type: application/json" ^
echo     -d "{\"model\":\"phone/llama3.2:1b\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"
echo.
pause
goto :eof


:: ════════════════════════════════════════════
::  DISCONNECT — Remove phone AI config
:: ════════════════════════════════════════════
:disconnect
echo.
echo [📱] Disconnecting Phone AI...

:: Remove cache
if exist "%CACHE_FILE%" (
    del "%CACHE_FILE%"
    echo [✓] Cache cleared.
)

:: Remove provider from config.yaml (powershell)
if exist "%CONFIG_FILE%" (
    powershell -Command "
        \$yaml = Get-Content '%CONFIG_FILE%' -Raw
        \$lines = \$yaml -split '`n'
        \$newLines = @()
        \$inPhoneSection = 0
        foreach (\$line in \$lines) {
            if (\$line -match '# Phone AI - On-Device LLM') { \$inPhoneSection = 1 }
            if (\$inPhoneSection -and \$line -match '^\s*$') { \$inPhoneSection = 0; continue }
            if (\$inPhoneSection -and \$line -match '^- name: \"phone-ai\"') { \$inPhoneSection = 2; continue }
            if (\$inPhoneSection -eq 2) { if (\$line -match '^\s*$') { \$inPhoneSection = 0 } else { continue } }
            if (\$inPhoneSection -eq 1) { continue }
            if (\$inPhoneSection) { continue }
            \$newLines += \$line
        }
        \$newLines -join \"`n\" | Set-Content '%CONFIG_FILE%'
        Write-Output 'Phone AI provider removed from config.yaml'
    "
)

echo [✅] Disconnected.
pause
goto :eof


:: ════════════════════════════════════════════
::  STATUS — Show phone AI status
:: ════════════════════════════════════════════
:status
echo.
if not exist "%CACHE_FILE%" (
    echo [📱] Phone AI: NOT CONNECTED
    echo.
    echo  To connect: start-phone-ai.bat --connect ^<phone-ip^>
    echo  Or scan:    start-phone-ai.bat --scan
    echo.
    pause
    goto :eof
)

:: Read cache
for /f "usebackq tokens=*" %%a in ("%CACHE_FILE%") do set "CACHE=%%a"
for /f "tokens=2 delims=:" %%a in ('echo %CACHE% ^| findstr "phone_ip"') do set "IP_PART=%%a"
set "PHONE_IP=%IP_PART:"=%
set "PHONE_IP=%PHONE_IP:,=%"
set "PHONE_IP=%PHONE_IP: =%"

echo [📱] Phone AI Connection:
echo.
echo  IP:     %PHONE_IP%
echo  Port:   %PHONE_AI_PORT%
echo  Cached: YES
echo.

:: Check if still alive
curl -s --connect-timeout 3 "http://%PHONE_IP%:%PHONE_AI_PORT%/health" >nul 2>&1
if !errorlevel! equ 0 (
    echo  Status:  ✅ CONNECTED
    echo.
    for /f "tokens=*" %%a in ('curl -s --connect-timeout 3 "http://%PHONE_IP%:%PHONE_AI_PORT%/health"') do (
        powershell -Command "$h = '%%a' | ConvertFrom-Json; Write-Output ('  Battery: ' + $h.battery.percent + '%'); Write-Output ('  Ollama:  ' + $h.ollama_connected); Write-Output ('  Memory:  ' + $h.memory_percent + '%'); Write-Output ('  CPU:     ' + $h.cpu_percent + '%')"
    )
) else (
    echo  Status:  ❌ DISCONNECTED
    echo  Phone may be offline or service stopped.
)

:: Check if config.yaml has phone provider
if exist "%CONFIG_FILE%" (
    findstr "phone-ai" "%CONFIG_FILE%" >nul
    if !errorlevel! equ 0 (
        echo  Config:  ✅ Provider in config.yaml
    ) else (
        echo  Config:  ⚠️  Not in config.yaml (run --config-write)
    )
)

echo.
pause
goto :eof


:: ════════════════════════════════════════════
::  SCAN — Scan LAN for phone AI service
:: ════════════════════════════════════════════
:scan
echo.
echo [🔍] Scanning LAN for Phone AI services...
echo  (Checking ports %PHONE_AI_PORT% and %PHONE_MCP_PORT%)
echo.

:: Try common subnets
set "FOUND=0"
for %%s in ("192.168.1." "192.168.0." "10.0.0." "172.16.0." "192.168.2." "192.168.10.") do (
    set "SUBNET=%%~s"
    echo  Scanning %SUBNET%x ...
    for /l %%i in (1,1,254) do (
        curl -s --connect-timeout 0.3 "http://!SUBNET!%%i:%PHONE_AI_PORT%/health" >nul 2>&1
        if !errorlevel! equ 0 (
            echo.
            echo  [✅] FOUND at !SUBNET!%%i:%PHONE_AI_PORT%!
            set "PHONE_IP=!SUBNET!%%i"
            set "FOUND=1"
            
            :: Get more info
            echo.
            for /f "tokens=*" %%a in ('curl -s --connect-timeout 2 "http://!SUBNET!%%i:%PHONE_AI_PORT%/health"') do (
                powershell -Command "$h = '%%a' | ConvertFrom-Json; Write-Output ('  Hostname: ' + $h.hostname); Write-Output ('  Ollama:   ' + $h.ollama_connected); Write-Output ('  Battery:  ' + $h.battery.percent + '%'); Write-Output ('  Platform: ' + $h.platform)"
            )
            
            :: Auto-save
            echo.
            choice /c YN /n /m "Connect to this phone? (Y/N): "
            if !errorlevel! equ 1 (
                call :connect
            )
            goto :scan_done
        )
    )
)

:scan_done
if !FOUND! equ 0 (
    echo.
    echo  [✗] No Phone AI found on LAN.
    echo.
    echo  Make sure:
    echo   1. Phone is on same Wi-Fi network
    echo   2. Phone AI service is running: python phone_ai_service.py --port 5000
    echo   3. Phone firewall allows port %PHONE_AI_PORT%
    echo.
    echo  Or try: start-phone-ai.bat --connect ^<specific-ip^>
)

echo.
pause
goto :eof


:: ════════════════════════════════════════════
::  CONFIG_WRITE — Add phone AI to config.yaml
:: ════════════════════════════════════════════
:config_write
if "%PHONE_IP%"=="" set "PHONE_IP=%~1"
if "%PHONE_IP%"=="" (
    echo Error: Phone IP required
    echo Usage: start-phone-ai.bat --config-write ^<phone-ip^>
    pause
    goto :eof
)

echo.
echo [⚙] Writing Phone AI provider to config.yaml...

:: Build provider config block
set "PHONE_MODEL=llama3.2:1b"

:: Backup config
copy "%CONFIG_FILE%" "%CONFIG_FILE%.bak" >nul 2>&1

:: Append provider config — uses PowerShell to inject before last line
powershell -Command "
    \$yaml = Get-Content '%CONFIG_FILE%' -Raw
    \$providerBlock = @'

# ▸ Phone AI — On-Device LLM (Android Termux)
# Connected via: start-phone-ai.bat --connect %PHONE_IP%
- name: \"phone-ai\"
  prefix: \"phone\"
  base-url: \"http://%PHONE_IP%:%PHONE_AI_PORT%\"
  models:
    - name: \"llama3.2:1b\"
      alias: \"phone-llama-1b\"
      capabilities:
        streaming: true
        contextWindow: 8192
    - name: \"llama3.2:3b\"
      alias: \"phone-llama-3b\"
      capabilities:
        streaming: true
        contextWindow: 8192
    - name: \"phi3:mini\"
      alias: \"phone-phi3\"
      capabilities:
        streaming: true
        contextWindow: 4096
    - name: \"qwen2.5:1.5b\"
      alias: \"phone-qwen\"
      capabilities:
        streaming: true
        contextWindow: 8192
    - name: \"gemma2:2b\"
      alias: \"phone-gemma2\"
      capabilities:
        streaming: true
        contextWindow: 8192

'@

    :: Insert before 'openai-compatibility:' section
    if (\$yaml -match 'openai-compatibility:') {
        \$yaml = \$yaml -replace 'openai-compatibility:', \$providerBlock + 'openai-compatibility:'
        \$yaml | Set-Content '%CONFIG_FILE%'
        Write-Output 'Phone AI provider written before OpenAI section'
    } else {
        \$yaml + \"`n\" + \$providerBlock | Set-Content '%CONFIG_FILE%'
        Write-Output 'Phone AI provider appended at end of config'
    }
"

echo [✅] Provider 'phone/*' added to config.yaml!
echo.
echo  Use model prefix: phone/llama3.2:1b
echo  Example:
echo    curl http://localhost:20128/v1/chat/completions ^
echo      -H \"Content-Type: application/json\" ^
echo      -d \"{\\\"model\\\":\\\"phone/llama3.2:1b\\\",\\\"messages\\\":[{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"Hi\\\"}]}\"
echo.
echo  Note: Restart TiRouter to apply config changes.
echo.
pause
goto :eof


:: ════════════════════════════════════════════
::  START LOCAL — Run Ollama locally for dev
:: ════════════════════════════════════════════
:start_local
echo.
echo [💻] Starting local Phone AI test environment...
echo  (Use this if you don't have an Android phone yet)
echo.

:: Check if Ollama is installed
where ollama >nul 2>&1
if !errorlevel! neq 0 (
    echo [✗] Ollama not found!
    echo  Download from: https://ollama.com/download
    echo.
    echo  Or if already installed, add to PATH:
    echo    set PATH=C:\Program Files\Ollama;%PATH%
    echo.
    pause
    goto :eof
)

:: Check if already running
curl -s http://localhost:11434/api/tags >nul 2>&1
if !errorlevel! neq 0 (
    echo [🔄] Starting Ollama server...
    start "Ollama" /min ollama serve
    timeout /t 3 /nobreak >nul
)

:: Check if model exists
ollama list 2>nul | findstr "llama3.2:1b" >nul
if !errorlevel! neq 0 (
    echo [📥] Pulling llama3.2:1b model (first time, ~800MB)...
    start "Ollama Pull" /min cmd /c "ollama pull llama3.2:1b"
    echo  Downloading in background. Check with: ollama list
    timeout /t 5 /nobreak >nul
)

:: Start the Python AI service
echo [🐍] Starting Phone AI Python service locally...
start "Phone AI Service" /min python "%ROOT%..\services\phone_ai_service.py" --port 5000 --ollama-url http://localhost:11434

timeout /t 3 /nobreak >nul

:: Check if started
curl -s http://localhost:5000/health >nul 2>&1
if !errorlevel! equ 0 (
    echo [✅] Phone AI Service running on http://localhost:5000!
    echo.
    echo  Connect TiRouter: start-phone-ai.bat --connect localhost
    echo  Or use config:    start-phone-ai.bat --config-write localhost
) else (
    echo [⚠] Service may still be starting... check http://localhost:5000/health
)

echo.
pause
goto :eof


:: ════════════════════════════════════════════
::  STOP — Stop phone AI service
:: ════════════════════════════════════════════
:stop
echo.
echo [⏹] Stopping Phone AI service...
taskkill /f /fi "WINDOWTITLE eq Phone AI Service*" 2>nul
echo [✓] Stopped.
pause
goto :eof


:: ════════════════════════════════════════════
::  HELP
:: ════════════════════════════════════════════
:help
echo.
echo ════════════════════════════════════════════
echo  TiRouter Phone AI Service Manager
echo  On-Device LLM via Android Termux
echo ════════════════════════════════════════════
echo.
echo  Usage: start-phone-ai.bat [options]
echo.
echo  Options:
echo    --connect ^<ip^>      Connect to Phone AI at IP
echo    --disconnect          Remove Phone AI config
echo    --status              Show connection status
echo    --scan                Scan LAN for Phone AI
echo    --config-write ^<ip^> Write provider to config.yaml
echo    --start-local         Start local Ollama test env
echo    --stop                Stop local Phone AI service
echo    --help, -h            Show this help
echo.
echo  Examples:
echo    start-phone-ai.bat --connect 192.168.1.100
echo    start-phone-ai.bat --scan
echo    start-phone-ai.bat --config-write 192.168.1.100
echo    start-phone-ai.bat --start-local
echo.
echo  Integration:
echo    After connecting, use models with prefix: phone/
echo    e.g. \"phone/llama3.2:1b\" or \"phone/phi3:mini\"
echo.
echo  Requires:
echo    - curl (built-in Windows 10/11)
echo    - Phone AI service running on phone
echo    - Same LAN network
echo.
exit /b 0

endlocal
