param(
    [ValidateRange(1,65535)][int]$Port = 5173,
    [ValidateSet('127.0.0.1','localhost')][string]$BindHost = '127.0.0.1',
    [ValidateSet('dev','preview')][string]$Mode = 'dev'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$previousPort = $env:RRC_ADMIN_PORT
Push-Location (Join-Path $root 'handover-2026-10-24/RunRunCampusAdmin')
try {
    $env:RRC_ADMIN_PORT = [string]$Port
    Write-Output "Admin URL: http://${BindHost}:$Port/v2-admin"
    Write-Output "Set RRC_LOCAL_ADMIN_ORIGIN=http://${BindHost}:$Port in your existing local env file, then recreate backend with Compose."
    # Vite 的 strictPort 同时覆盖 dev 和 preview，占用时失败，不擅自切换 Origin。
    & pnpm $Mode --host $BindHost
    $result = $LASTEXITCODE
} finally {
    $env:RRC_ADMIN_PORT = $previousPort
    Pop-Location
}
exit $result
