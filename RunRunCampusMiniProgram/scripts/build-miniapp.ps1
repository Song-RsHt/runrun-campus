param(
  [ValidateSet('local', 'test', 'staging')][string]$Environment = 'test',
  [Parameter(Mandatory = $true)][string]$ApiBaseUrl,
  [string]$AllowedHost,
  [switch]$MockPayment
)

$ErrorActionPreference = 'Stop'
if ($ApiBaseUrl -notmatch '^https?://') { throw 'ApiBaseUrl must be an HTTPS/HTTP URL' }
if ($Environment -ne 'local' -and $ApiBaseUrl -match 'localhost|127\.0\.0\.1|0\.0\.0\.0') {
  throw 'Localhost is only valid for local builds'
}
if ($ApiBaseUrl -match 'prod|production') { throw 'Production API hosts are not allowed in mini-program builds' }
$resolvedHost = ([Uri]$ApiBaseUrl).Host.ToLowerInvariant()
if ($Environment -ne 'local' -and [string]::IsNullOrWhiteSpace($AllowedHost)) { throw 'AllowedHost is required for test/staging builds' }
if ($AllowedHost -and $resolvedHost -ne $AllowedHost.Trim().ToLowerInvariant()) { throw 'AllowedHost must match ApiBaseUrl host' }

$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root '.build/environment.json'
New-Item -ItemType Directory -Force (Split-Path -Parent $output) | Out-Null
@{
  environment = $Environment
  apiBaseUrl = $ApiBaseUrl.TrimEnd('/')
  allowedHosts = @($resolvedHost)
  mockPayment = [bool]$MockPayment
  generatedAt = (Get-Date).ToUniversalTime().ToString('o')
} | ConvertTo-Json | Set-Content -Encoding utf8 $output
Write-Output "Generated isolated $Environment build metadata at $output"
Write-Output 'Configure the same values in WeChat DevTools storage: rrc_api_environment, rrc_api_base_url, rrc_mock_payment_enabled.'
