param()
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$jarPath = Join-Path $root 'handover-2026-10-24/RunRunCampusBackend/target/runrun-campus-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build the v2-identity package first.' }
$name = 'rrc-identity-smoke-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$logDirectory = Join-Path $root "work/$name"
$null = New-Item -ItemType Directory -Path $logDirectory
$serverProcess = $null
function Docker([string[]]$Arguments) {
  $result = & docker.exe @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw 'Temporary Docker operation failed; raw diagnostics suppressed.' }
  return $result
}
function Assert([bool]$Condition, [string]$Label) {
  if (-not $Condition) { throw "FAIL: $Label" }
  Write-Output "PASS: $Label"
}
try {
  # Only loopback exposes this synthetic trust-auth database. It has no host mounts or shared volumes.
  $null = Docker @('run','--detach','--name',$name,'--publish','127.0.0.1::5432','--env','POSTGRES_HOST_AUTH_METHOD=trust','--env','POSTGRES_DB=identity_smoke','postgres:16-alpine')
  $databaseReady = $false
  for ($i=0; $i -lt 40; $i++) {
    & docker.exe exec $name psql -h 127.0.0.1 -U postgres -d identity_smoke -c 'SELECT 1' *> $null
    if ($LASTEXITCODE -eq 0) { $databaseReady = $true; break }
    Start-Sleep -Milliseconds 500
  }
  Assert $databaseReady 'synthetic database completes initialization'
  foreach ($migration in @('rrc_0001__create_v2_schema','rrc_0002__create_identity_foundation','rrc_0003__identity_runtime')) {
    Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$migration.sql") | & docker.exe exec -i $name psql -X -q -v ON_ERROR_STOP=1 -U postgres -d identity_smoke 2>&1 | Set-Content -Encoding UTF8 (Join-Path $logDirectory "$migration.log")
    if ($LASTEXITCODE -ne 0) { throw "Synthetic initialization failed: $migration; inspect ignored smoke logs." }
  }
  $mapping = Docker @('port',$name,'5432/tcp')
  $databasePort = [int](($mapping -join '').Split(':')[-1])
  $portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,0)
  $portProbe.Start()
  $applicationPort = $portProbe.LocalEndpoint.Port
  $portProbe.Stop()
  $env:RRC_V2_JDBC_URL = "jdbc:postgresql://127.0.0.1:$databasePort/identity_smoke"
  $env:RRC_V2_DB_USERNAME = 'postgres'
  $env:RRC_V2_DB_PASSWORD = 'synthetic-isolated-only'
  $env:RRC_V2_ENVIRONMENT = 'test'
  $env:RRC_WECHAT_APP_ID = 'synthetic-app'
  $env:RRC_WECHAT_APP_SECRET = 'synthetic-secret'
  $env:RRC_IDENTITY_ENCRYPTION_KEY = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
  $env:RRC_IDENTITY_HASH_KEY = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
  $env:RRC_V2_PORT = "$applicationPort"
  $serverProcess = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar',$jarPath) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'stdout.log') -RedirectStandardError (Join-Path $logDirectory 'stderr.log')
  $base = "http://127.0.0.1:$applicationPort"
  $ready = $false
  for ($i=0; $i -lt 60; $i++) {
    if ($serverProcess.HasExited) { throw 'Packaged process exited; inspect ignored local smoke logs.' }
    try {
      $health = Invoke-WebRequest -Uri "$base/actuator/health" -TimeoutSec 1 -SkipHttpErrorCheck
      if ($health.StatusCode -eq 200) { $ready=$true; break }
    } catch { }
    Start-Sleep -Milliseconds 500
  }
  Assert $ready 'packaged v2 entrypoint starts with isolated configuration'
  $unauth = Invoke-WebRequest -Uri "$base/api/v2/auth/session" -SkipHttpErrorCheck
  Assert ($unauth.StatusCode -eq 401 -and ($unauth.Content | ConvertFrom-Json).code -eq 'AUTHENTICATION_FAILED') 'real HTTP session guard rejects unauthenticated request'
  Assert ($unauth.Headers.'Cache-Control' -contains 'no-store') 'real HTTP authentication response disables caching'
  $invalid = Invoke-WebRequest -Method Post -Uri "$base/api/v2/auth/wechat/login" -ContentType 'application/json' -Body '{"code":""}' -SkipHttpErrorCheck
  Assert ($invalid.StatusCode -eq 400 -and ($invalid.Content | ConvertFrom-Json).code -eq 'VALIDATION_ERROR') 'real HTTP validates login before upstream exchange'
  $legacy = Invoke-WebRequest -Method Post -Uri "$base/api/v1/auth/login" -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck
  Assert ($legacy.StatusCode -eq 404) 'legacy login controller absent from packaged runtime'
  $recovery = Invoke-WebRequest -Method Post -Uri "$base/api/v2/admin/account-recoveries/1/resolve" -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck
  Assert ($recovery.StatusCode -eq 403) 'no recovery handler becomes reachable'
} finally {
  if ($null -ne $serverProcess -and -not $serverProcess.HasExited) { Stop-Process -Id $serverProcess.Id }
  & docker.exe rm -f -v $name *> $null
  if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $name" }
}
