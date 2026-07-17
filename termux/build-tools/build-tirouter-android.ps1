param(
    [string]$GoRoot = "Z:\09_TOOLS\go-new",
    [string]$RouterSource = "Z:\01_PROJECTS\apps\Tirouter\CLIProxyAPI",
    [string]$Output = "$PSScriptRoot\..\bin\tirouter-android-arm64"
)

$ErrorActionPreference = "Stop"
$go = Join-Path $GoRoot "bin\go.exe"
if (-not (Test-Path -LiteralPath $go)) {
    throw "Không tìm thấy Go tại $go"
}
if (-not (Test-Path -LiteralPath (Join-Path $RouterSource "go.mod"))) {
    throw "Không tìm thấy source CLIProxyAPI tại $RouterSource"
}

$Output = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Output) | Out-Null

$env:GOROOT = [IO.Path]::GetFullPath($GoRoot)
$env:GOOS = "android"
$env:GOARCH = "arm64"
$env:CGO_ENABLED = "0"
$env:GOTOOLCHAIN = "local"
$bundledGit = "Z:\02_CORE\_cli\.config\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd"
if (Test-Path -LiteralPath (Join-Path $bundledGit "git.exe")) {
    $env:Path = "$bundledGit;$env:Path"
}

Push-Location $RouterSource
try {
    & $go build -p 2 -trimpath -mod=mod -ldflags="-s -w" -o $Output ./cmd/server
    if ($LASTEXITCODE -ne 0) {
        throw "Go build thất bại với exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$bytes = [IO.File]::ReadAllBytes($Output)
$magic = ($bytes[0..3] | ForEach-Object { $_.ToString("X2") }) -join " "
if ($magic -ne "7F 45 4C 46") {
    throw "Artifact không phải ELF Android/Linux: $magic"
}
if ($bytes[18] -ne 0xB7 -or $bytes[19] -ne 0x00) {
    throw "Artifact không phải ARM64 ELF"
}

$item = Get-Item -LiteralPath $Output
Write-Output "Đã build TiRouter Android ARM64: $($item.FullName) ($($item.Length) bytes)"
