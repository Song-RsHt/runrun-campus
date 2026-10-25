[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env.production')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = [System.IO.Path]::GetFullPath($EnvFile)
$composePath = Join-Path $projectDirectory 'compose.prod.yml'

if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
    throw "生产变量文件不存在：$envPath"
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $envPath) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) { throw "生产变量文件存在无效行（未显示内容）。" }
    $key = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()
    $values[$key] = $value
}

function Require-Value([string]$Name) {
    if (-not $values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($values[$Name])) {
        throw "缺少生产变量：$Name"
    }
    return [string]$values[$Name]
}

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "生产配置校验失败：$Message" }
}

function Require-RealValue([string]$Name) {
    $value = Require-Value $Name
    Require ($value -notmatch '(?i)replace-with|change-this|example|database-host') "$Name 仍是示例值"
    return $value
}

function Resolve-ProjectPath([string]$Value) {
    if ([System.IO.Path]::IsPathRooted($Value)) {
        return [System.IO.Path]::GetFullPath($Value)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $projectDirectory $Value))
}

$databaseUrl = Require-RealValue 'SPRING_DATASOURCE_URL'
$databaseUsername = Require-RealValue 'SPRING_DATASOURCE_USERNAME'
$adminPhone = Require-RealValue 'APP_ADMIN_PHONE'
$ossRegion = Require-RealValue 'OSS_REGION'
$ossBucket = Require-RealValue 'OSS_BUCKET'
$ossRoleName = Require-RealValue 'OSS_ECS_ROLE_NAME'
$databasePasswordPath = Resolve-ProjectPath (Require-Value 'SPRING_DATASOURCE_PASSWORD_FILE')
$adminPasswordPath = Resolve-ProjectPath (Require-Value 'APP_ADMIN_PASSWORD_FILE')
$rdsCaCertificatePath = Resolve-ProjectPath (Require-Value 'RDS_CA_CERTIFICATE_FILE')
$adminMfaKeyPath = Resolve-ProjectPath (Require-Value 'ADMIN_MFA_KEY_FILE')

Require ($databaseUrl -match '^jdbc:postgresql://') '数据库地址必须使用 PostgreSQL JDBC'
Require ($databaseUrl -match '[?&]sslmode=verify-full(?:&|$)') '数据库连接必须启用 sslmode=verify-full'
Require ($databaseUrl -match '[?&]sslrootcert=/run/secrets/RDS_CA_CERTIFICATE(?:&|$)') '数据库连接必须使用挂载的 RDS CA 证书'
Require ($databaseUsername -ne 'postgres') '数据库账号不能使用 postgres 超级用户'
Require ($adminPhone -match '^1\d{10}$') '管理员手机号格式无效'
Require ($ossRegion -match '^[a-z0-9-]+$') 'OSS Region 格式无效'
Require ($ossBucket -match '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$') 'OSS Bucket 格式无效'
Require ($ossRoleName -match '^[A-Za-z0-9@._-]{1,64}$') 'ECS RAM 角色名格式无效'
Require ((Require-Value 'OSS_USE_INTERNAL_ENDPOINT') -eq 'true') '生产环境必须使用 OSS 内网端点'

Require (Test-Path -LiteralPath $databasePasswordPath -PathType Leaf) '数据库密钥文件不存在'
Require (Test-Path -LiteralPath $adminPasswordPath -PathType Leaf) '管理员密钥文件不存在'
Require (Test-Path -LiteralPath $rdsCaCertificatePath -PathType Leaf) 'RDS CA 证书文件不存在'
Require (Test-Path -LiteralPath $adminMfaKeyPath -PathType Leaf) '管理员 MFA 密钥文件不存在'
try { $adminMfaKey = [Convert]::FromBase64String((Get-Content -LiteralPath $adminMfaKeyPath -Raw).Trim()) }
catch { throw '管理员 MFA 密钥不是有效 Base64，未显示内容' }
Require ($adminMfaKey.Length -eq 32 -and @($adminMfaKey | Where-Object { $_ -ne 0 }).Count -gt 0) '管理员 MFA 密钥必须为独立随机生成的 32 字节'
$databasePassword = (Get-Content -LiteralPath $databasePasswordPath -Raw).Trim()
$adminPassword = (Get-Content -LiteralPath $adminPasswordPath -Raw).Trim()
Require ($databasePassword.Length -eq 32) '数据库密码必须为 32 位，以符合 RDS PostgreSQL 账号密码限制'
Require ($adminPassword.Length -ge 16) '管理员密码不能少于 16 位'
Require ($databasePassword -ne $adminPassword) '数据库密码与管理员密码不能相同'
Require ($databasePassword -notmatch '(?i)replace-with|change-this|example') '数据库密码仍是示例值'
Require ($adminPassword -notmatch '(?i)replace-with|change-this|example') '管理员密码仍是示例值'
$rdsCaCertificate = Get-Content -LiteralPath $rdsCaCertificatePath -Raw
Require ($rdsCaCertificate -match '-----BEGIN CERTIFICATE-----[\s\S]+-----END CERTIFICATE-----') 'RDS CA 证书不是有效的 PEM 文件'

Get-Command docker -ErrorAction Stop | Out-Null
& docker compose --env-file $envPath -f $composePath config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose 生产配置校验失败' }

Write-Host '生产配置静态校验通过：必填项、TLS、账号、Secret 文件、OSS 内网设置和 Compose 结构均有效。'
