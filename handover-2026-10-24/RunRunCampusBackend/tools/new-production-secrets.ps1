#requires -Version 7.2
[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\secrets\production'),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $IsWindows) { throw '此脚本使用 Windows ACL，请在 Windows PowerShell 7 中运行。' }
if ($Force) { throw '不再支持 -Force 覆盖现用密钥。请使用 prepare-production-secret-rotation.ps1 准备轮换。' }

$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$databasePasswordPath = Join-Path $outputPath 'database-password.txt'
$adminPasswordPath = Join-Path $outputPath 'admin-password.txt'
$secretPaths = @($databasePasswordPath, $adminPasswordPath)

$existing = @($secretPaths | Where-Object { Test-Path -LiteralPath $_ })
if ($existing.Count -gt 0) {
    throw '密钥文件已存在，未执行覆盖。请使用 prepare-production-secret-rotation.ps1 准备轮换。'
}

New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$acl = [System.Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
$readers = @(
    [System.Security.Principal.WindowsIdentity]::GetCurrent().User,
    [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'),
    [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
)
foreach ($reader in $readers) {
    $acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new(
        $reader, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
Set-Acl -LiteralPath $outputPath -AclObject $acl

function New-RandomSecret([string]$Prefix, [int]$ByteCount) {
    $bytes = [byte[]]::new($ByteCount)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return $Prefix + [Convert]::ToHexString($bytes).ToLowerInvariant()
}

$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
$createdPaths = [System.Collections.Generic.List[string]]::new()
try {
    foreach ($entry in @(
        @{ Path = $databasePasswordPath; Prefix = 'Db!9'; ByteCount = 14 },
        @{ Path = $adminPasswordPath; Prefix = 'Admin!9-'; ByteCount = 24 }
    )) {
        $stream = [System.IO.File]::Open($entry.Path, 'CreateNew', 'Write', 'None')
        $createdPaths.Add($entry.Path)
        try {
            $bytes = $utf8WithoutBom.GetBytes((New-RandomSecret $entry.Prefix $entry.ByteCount))
            $stream.Write($bytes, 0, $bytes.Length)
        } finally {
            $stream.Dispose()
        }
    }
} catch {
    foreach ($path in $createdPaths) { Remove-Item -LiteralPath $path -Force }
    throw
}

Write-Host '已生成两个相互独立的生产密钥，内容未回显：'
Write-Host "- $databasePasswordPath"
Write-Host "- $adminPasswordPath"
Write-Host '文件仅允许当前用户、SYSTEM 和本机管理员访问。生成文件不会修改数据库或已有管理员账号的密码。'
