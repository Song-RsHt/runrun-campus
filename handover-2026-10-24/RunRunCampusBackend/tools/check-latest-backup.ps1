$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$backendDirectory = Split-Path -Parent $PSScriptRoot
$backupDirectory = Join-Path $backendDirectory 'backups'
$latestBackup = Get-ChildItem -LiteralPath $backupDirectory -Filter 'runrun-campus-*.dump' -File |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

if ($null -eq $latestBackup) {
    throw '没有找到数据库备份文件'
}

Push-Location $backendDirectory
try {
    docker compose exec -T backup pg_restore --list "/backups/$($latestBackup.Name)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "备份完整性检查失败，退出码：$LASTEXITCODE"
    }
    Write-Output "备份可读取：$($latestBackup.FullName)"
} finally {
    Pop-Location
}
