#requires -Version 7.2
[CmdletBinding()]
param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '..\secrets\production'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot ('..\secrets\production\rotations\' + (Get-Date -Format 'yyyyMMdd-HHmmssfff')))
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$sourcePath = [System.IO.Path]::GetFullPath($SourceDirectory)
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputPath) { throw '轮换输出目录必须是新目录，未执行覆盖。' }

$names = @('database-password.txt', 'admin-password.txt')
$originals = @{}
foreach ($name in $names) {
    $path = Join-Path $sourcePath $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "源密钥文件不存在：$name" }
    $originals[$name] = [System.IO.File]::ReadAllBytes($path)
}
$databasePassword = [System.Text.Encoding]::UTF8.GetString($originals[$names[0]]).Trim()
$adminPassword = [System.Text.Encoding]::UTF8.GetString($originals[$names[1]]).Trim()
if ($databasePassword.Length -ne 32 -or $adminPassword.Length -lt 16 -or $adminPassword.Length -gt 72 -or
        $databasePassword -ceq $adminPassword) {
    throw '源密钥不符合现有长度或独立性要求，未生成候选密码。'
}

# The generator protects the output directory before writing any secret.
& (Join-Path $PSScriptRoot 'new-production-secrets.ps1') -OutputDirectory $outputPath *> $null
$beforePath = Join-Path $outputPath 'before'
New-Item -ItemType Directory -Path $beforePath | Out-Null
foreach ($name in $names) {
    [System.IO.File]::WriteAllBytes((Join-Path $beforePath $name), $originals[$name])
    $sourceHash = (Get-FileHash -LiteralPath (Join-Path $sourcePath $name) -Algorithm SHA256).Hash
    $backupHash = (Get-FileHash -LiteralPath (Join-Path $beforePath $name) -Algorithm SHA256).Hash
    if ($sourceHash -ne $backupHash) { throw '源密钥在准备过程中发生变化，请重新准备；本目录不可用于切换。' }
    $candidate = [System.IO.File]::ReadAllText((Join-Path $outputPath $name)).Trim()
    if ($candidate -ceq $databasePassword -or $candidate -ceq $adminPassword) {
        throw '候选密码与原密码重复，请重新准备；本目录不可用于切换。'
    }
}

# Written last: without this marker, a partial preparation is not ready for use.
[ordered]@{
    status = 'prepared_not_applied'
    preparedAt = [DateTimeOffset]::Now.ToString('o')
    sourceDirectory = $sourcePath
    source = 'local_files_not_verified_against_live_services'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputPath 'rotation.json') -Encoding utf8NoBOM

Write-Host "轮换材料已准备：$outputPath"
Write-Host '目录根部为候选密码，before 为原文件备份；现用文件未改动，密码未回显。'
Write-Host '尚未修改线上数据库或管理员密码。切换前须核对线上当前凭据并按 deploy/PRODUCTION_SECRET_ROTATION.md 执行。'
