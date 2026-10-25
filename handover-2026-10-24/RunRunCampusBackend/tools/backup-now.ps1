$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$backendDirectory = Split-Path -Parent $PSScriptRoot
Push-Location $backendDirectory
try {
    docker compose exec -T backup /bin/sh /scripts/backup-now.sh
    if ($LASTEXITCODE -ne 0) {
        throw "数据库备份失败，退出码：$LASTEXITCODE"
    }
} finally {
    Pop-Location
}
