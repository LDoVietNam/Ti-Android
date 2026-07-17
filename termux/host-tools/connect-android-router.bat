@echo off
chcp 65001 >nul
title TiRouter ARM64 - Android Connection Manager
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
for %%I in ("%ROOT%..\..\..\Tirouter") do set "TIROUTER_ROOT=%%~fI"
set "CACHE_FILE=%ROOT%cache\android-router.json"
set "CONFIG_FILE=%TIROUTER_ROOT%\CLIProxyAPI\config.yaml"

:: ─── Parse flags ───
if /I "%~1"=="--help" goto :help
if /I "%~1"=="--connect" set "ACTION=connect" & set "PHONE_IP=%~2" & goto :main
if /I "%~1"=="--scan" set "ACTION=scan" & goto :main
if /I "%~1"=="--disconnect" set "ACTION=disconnect" & goto :main
if /I "%~1"=="--status" set "ACTION=status" & goto :main
if /I "%~1"=="--mint" set "ACTION=mint" & set "MINT_COUNT=%~2" & if "!MINT_COUNT!"=="" set "MINT_COUNT=5" & goto :main
if /I "%~1"=="--mint-and-connect" set "ACTION=mint-and-connect" & set "PHONE_IP=%~3" & set "MINT_COUNT=%~2" & if "!MINT_COUNT!"=="" set "MINT_COUNT=5" & goto :main

:: Default interactive
if "%1"=="" goto :interactive
echo Unknown option: %1
goto :help

:interactive
cls
echo ================================================
echo   TiRouter ARM64 — Android Connection Manager
echo ================================================
echo.
echo  [1] Connect to Android TiRouter (enter IP)
echo  [2] Scan LAN for Android TiRouter
echo  [3] Grok Mint — register + inject OAuth tokens
echo  [4] Show Status
echo  [5] Disconnect
echo  [0] Exit
echo.
choice /c 123450 /n /m "Select: "
if errorlevel 6 exit /b
if errorlevel 5 goto :disconnect
if errorlevel 4 goto :status
if errorlevel 3 set "ACTION=mint" & goto :main
if errorlevel 2 goto :scan
if errorlevel 1 set /p "PHONE_IP=Enter Android IP: " & goto :main
goto :interactive

:main
if "%ACTION%"=="connect" goto :connect
if "%ACTION%"=="scan" goto :scan
if "%ACTION%"=="disconnect" goto :disconnect
if "%ACTION%"=="status" goto :status
if "%ACTION%"=="mint" goto :mint
if "%ACTION%"=="mint-and-connect" goto :mint-and-connect
goto :eof

:: ════════════════════════════════════════════════
::  GROK MINT — register Grok accounts + mint OAuth tokens
:: ════════════════════════════════════════════════
:mint
echo.
echo [🤖] Grok Mint — Auto-register Grok accounts + OAuth tokens
echo.
echo  This will:
echo   1. Register %MINT_COUNT% Grok accounts (via Chrome browser)
echo   2. Mint OAuth tokens for Grok Build API
echo   3. Inject tokens into TiRouter CLIProxyAPI config
echo.
echo  ⚠️  Requires: Python 3.13, Google Chrome, Internet access
echo     Chrome windows will open for Turnstile verification.
echo.
choice /c YN /n /m "Start Grok registration + mint? (Y/N): "
if !errorlevel! neq 1 (
    echo [ℹ]  Cancelled.
    goto :eof
)

set "GROK_DIR=%~dp0grok-register-mint"

:: Check if setup exists
if not exist "%GROK_DIR%\config.json" (
    echo [⏳] First-time setup: cloning grok-register-mint...
    call "%~dp0setup-grok-mint.bat"
    if !errorlevel! neq 0 (
        echo [✗] Setup failed. Check your config.
        pause
        exit /b 1
    )
)

:: Run registration
cd /d "%GROK_DIR%"
echo [⏳] Starting Grok registration (%MINT_COUNT% accounts, 2 threads)...
echo.
echo  🖥️  Chrome browsers will open. Let them run in background.
echo  ⏱️  Estimated time: ~%MINT_COUNT%0 seconds (varies by email/Turnstile)
echo.
uv run python -u register_cli.py --count %MINT_COUNT% --threads 2 --mint-workers 2 --fast
set "MINT_EXIT=%errorlevel%"
cd /d "%~dp0"

if !MINT_EXIT! neq 0 (
    echo [⚠️]  Registration completed with warnings.
    echo    Check logs: %GROK_DIR%\logs\
) else (
    echo [✅] Registration completed successfully!
)

:: Inject tokens into config
if exist "%GROK_DIR%\output\cpa_auths" (
    echo [⏳] Injecting OAuth tokens into TiRouter config...
    powershell -ExecutionPolicy Bypass -File "%~dp0inject-grok-tokens.ps1" -GrokMintDir "%GROK_DIR%"
    if !errorlevel! equ 0 (
        echo [✅] Tokens injected into CLIProxyAPI config.yaml
        echo.
        echo  Restart TiRouter to apply: start-tirouter.bat restart
        echo  Or use --mint-and-connect to auto-connect to Android
    ) else (
        echo [ℹ]  Token injection completed with notes above
    )
) else (
    echo [⚠️]  No CPA auth files found at %GROK_DIR%\output\cpa_auths\
    echo    Grok Build tokens may not have been minted.
    echo    Check: cpa_export_enabled = true in %GROK_DIR%\config.json
)

echo.
echo  📊 Token count:
dir /b "%GROK_DIR%\output\cpa_auths\*.json" 2>nul | find /c /v ""
echo  files in %GROK_DIR%\output\cpa_auths\
pause
goto :eof

:: ════════════════════════════════════════════════
::  MINT + CONNECT — full auto flow
:: ════════════════════════════════════════════════
:mint-and-connect
echo [🤖] Mint + Connect: Register Grok, inject tokens, connect to Android
echo.

:: Step 1: Mint
set "MINT_COUNT=%MINT_COUNT%"
call :mint

:: Step 2: Connect (if IP provided)
if not "%PHONE_IP%"=="" (
    echo [⏳] Connecting to Android TiRouter at %PHONE_IP%...
    call :connect
) else (
    echo [ℹ]  No Android IP provided. Use --scan to find it.
    echo    connect-android-router.bat --scan
)
goto :eof

:: ════════════════════════════════════════════════
::  CONNECT
:: ════════════════════════════════════════════════
:connect
echo.
echo [📱] Connecting to Android TiRouter at %PHONE_IP%:20128...
echo.

:: Test connection
curl -s --connect-timeout 5 "http://%PHONE_IP%:20128/v1/models" >nul 2>&1
if !errorlevel! neq 0 (
    echo [✗] Cannot reach TiRouter at %PHONE_IP%:20128!
    echo   Make sure Android node is running: bash ti-node.sh start
    echo   Check IP: ip -4 addr show wlan0 ^| grep inet
    pause
    exit /b 1
)

:: Save cache
if not exist "%~dp0cache" mkdir "%~dp0cache"
echo {"phone_ip":"%PHONE_IP%","phone_port":20128,"connected_at":"%date% %time%"} > "%CACHE_FILE%"

:: Get model list
echo [ℹ] Fetching model list...
for /f "tokens=*" %%a in ('curl -s "http://%PHONE_IP%:20128/v1/models"') do set "MODEL_JSON=%%a"
echo.
echo [✅] Connected to Android TiRouter!
echo.
echo  IP:      %PHONE_IP%
echo  Port:    20128
echo  Models:  (list below)
echo.
powershell -Command "$m = '%MODEL_JSON%' | ConvertFrom-Json; foreach($d in $m.data) { Write-Host ('  📦 ' + $d.id) }"
echo.
echo  ─── Test Commands ───
echo.
echo  curl http://localhost:20128/v1/chat/completions ^
echo    -H \"Content-Type: application/json\" ^
echo    -d \"{\\\"model\\\":\\\"phone/llama3.2:1b\\\",\\\"messages\\\":[{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"Hi\\\"}]}\"
echo.
echo  curl http://localhost:20128/v1/models
echo.

:: Write to config.yaml
echo [⚙] Writing Android TiRouter provider to config.yaml...
powershell -Command "
    \$providerBlock = @'

# ▸ TiRouter ARM64 — Android Termux (auto-connected)
# IP: %PHONE_IP%
- name: \"android-tirouter\"
  prefix: \"phone\"
  base-url: \"http://%PHONE_IP%:20128\"
  models:
    - name: \"*\"  # proxy all models
      alias: \"phone-*\"

'@
    \$yaml = Get-Content '%CONFIG_FILE%' -Raw
    if (\$yaml -match 'openai-compatibility:') {
        \$yaml = \$yaml -replace 'openai-compatibility:', \"\$providerBlock`nopenai-compatibility:\"
    } else {
        \$yaml += \"`n\$providerBlock\"
    }
    Copy-Item '%CONFIG_FILE%' '%CONFIG_FILE%.bak' -Force
    \$yaml | Set-Content '%CONFIG_FILE%'
    Write-Host '[✅] Provider written to config.yaml'
"
echo.
echo  Access via localhost:20128 with prefix: phone/
echo  Restart TiRouter if already running to apply config.
pause
goto :eof

:: ════════════════════════════════════════════════
::  SCAN LAN
:: ════════════════════════════════════════════════
:scan
echo.
echo [🔍] Scanning LAN for TiRouter ARM64 (port 20128)...
echo.

set "FOUND=0"
for %%s in ("192.168.1." "192.168.0." "10.0.0." "192.168.2.") do (
    set "SUBNET=%%~s"
    echo  Scanning !SUBNET!x ...
    for /l %%i in (1,1,254) do (
        curl -s --connect-timeout 0.3 "http://!SUBNET!%%i:20128/v1/models" >nul 2>&1
        if !errorlevel! equ 0 (
            echo.
            echo  [✅] FOUND at !SUBNET!%%i:20128!
            set "PHONE_IP=!SUBNET!%%i"
            set "FOUND=1"
            choice /c YN /n /m "Connect? (Y/N): "
            if !errorlevel! equ 1 call :connect
            goto :scan_done
        )
    )
)

:scan_done
if !FOUND! equ 0 (
    echo.
    echo  [✗] No TiRouter ARM64 found on LAN.
    echo.
    echo  Make sure:
    echo   1. Android Termux is running: bash ti-node.sh start
    echo   2. TiRouter is on port 20128
    echo   3. Phone is on same Wi-Fi
)
pause
goto :eof

:: ════════════════════════════════════════════════
::  DISCONNECT
:: ════════════════════════════════════════════════
:disconnect
if exist "%CACHE_FILE%" (
    del "%CACHE_FILE%"
    echo [✅] Cache cleared.
)
echo [✅] Disconnected.
pause
goto :eof

:: ════════════════════════════════════════════════
::  STATUS
:: ════════════════════════════════════════════════
:status
echo.
if not exist "%CACHE_FILE%" (
    echo [📱] Android TiRouter: NOT CONNECTED
    echo   Use: connect-android-router.bat --connect ^<ip^>
    pause
    goto :eof
)

for /f "usebackq tokens=*" %%a in ("%CACHE_FILE%") do set "JSON=%%a"
echo [📱] Status: Checking...
echo.

for /f "tokens=2 delims=:" %%a in ('echo %JSON% ^| findstr "phone_ip"') do set "IP=%%a"
set "IP=%IP: =%"
set "IP=%IP:"=%"
set "IP=%IP:,=%"

curl -s --connect-timeout 3 "http://%IP%:20128/v1/models" >nul 2>&1
if !errorlevel! equ 0 (
    echo  Status:  ✅ CONNECTED
    echo  IP:      %IP%
    echo  Endpoint: http://%IP%:20128/v1
    echo.
    for /f "tokens=*" %%a in ('curl -s --connect-timeout 3 "http://%IP%:20128/v1/models"') do (
        powershell -Command "$m = '%%a' | ConvertFrom-Json; Write-Host ('  Models: ' + $m.data.Count)"
    )
) else (
    echo  Status:  ❌ DISCONNECTED
    echo  Device at %IP% may be offline.
)
pause
goto :eof

:: ════════════════════════════════════════════════
::  HELP
:: ════════════════════════════════════════════════
:help
echo.
echo TiRouter ARM64 - Android Connection Manager
echo.
echo Usage: connect-android-router.bat [options]
echo.
echo Options:
echo   --connect ^<ip^>          Connect to Android TiRouter at IP
echo   --scan                   Scan LAN for TiRouter ARM64
echo   --mint [N]               Mint N Grok OAuth tokens (default: 5)
echo   --mint-and-connect [N] [IP]  Mint tokens + connect to Android
echo   --disconnect             Remove connection
echo   --status                 Show connection status
echo   --help                   Show this help
echo.
echo Examples:
echo   connect-android-router.bat --connect 192.168.1.100
echo   connect-android-router.bat --scan
echo   connect-android-router.bat --mint 10
echo   connect-android-router.bat --mint-and-connect 5 192.168.1.100
echo.
exit /b 0
