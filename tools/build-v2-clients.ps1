param([string]$ApiBaseUrl='http://localhost:8082')
$ErrorActionPreference='Stop'
if ($ApiBaseUrl -notin @('http://localhost:8082','http://127.0.0.1:8082')) { throw 'Local builds require the isolated loopback API on 8082.' }
$root=Split-Path $PSScriptRoot -Parent
$previous=$env:VITE_API_BASE_URL
$previousLocal=$env:VITE_V2_LOCAL_TEST
Push-Location (Join-Path $root 'handover-2026-10-24/RunRunCampusAdmin')
try {
  $env:VITE_API_BASE_URL=$ApiBaseUrl
  $env:VITE_V2_LOCAL_TEST='true'
  & pnpm install --frozen-lockfile
  if ($LASTEXITCODE -ne 0) { throw 'Admin dependency installation failed.' }
  & pnpm build
  if ($LASTEXITCODE -ne 0) { throw 'Admin build failed.' }
  $compiled=Get-ChildItem 'dist/assets/*.js' | Select-String -SimpleMatch $ApiBaseUrl -Quiet
  if (!($compiled -contains $true)) { throw 'Admin output is missing the isolated API address.' }
} finally { $env:VITE_API_BASE_URL=$previous; $env:VITE_V2_LOCAL_TEST=$previousLocal; Pop-Location }
& (Join-Path $root 'RunRunCampusMiniProgram/scripts/build-miniapp.ps1') -Environment local -ApiBaseUrl $ApiBaseUrl -AllowedHost ([Uri]$ApiBaseUrl).Host -MockPayment
if (!$?) { throw 'Mini-program metadata build failed.' }
$metadata=Get-Content -Raw (Join-Path $root 'RunRunCampusMiniProgram/.build/environment.json') | ConvertFrom-Json
if ($metadata.apiBaseUrl -ne $ApiBaseUrl -or !$metadata.mockPayment) { throw 'Mini-program API mismatch.' }
Write-Output "PASS: both client builds use $ApiBaseUrl; mini-program output is environment metadata, not a WeChat upload package."
