param(
  [string]$MigrationRoot = (Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent),
  [string]$M08Migration = ''
)
$ErrorActionPreference = 'Stop'
$up = Join-Path $MigrationRoot 'server/db/migrations/up'
$down = Join-Path $MigrationRoot 'server/db/migrations/down'
$ordered = @(
  'rrc_0001__create_v2_schema.sql',
  'rrc_0002__create_identity_foundation.sql',
  'rrc_0003__identity_runtime.sql',
  'rrc_0100__orders_and_service_config.sql',
  'rrc_0200__mock_payments.sql',
  'rrc_0300__fulfillment.sql',
  'rrc_0004__adjustments_after_sales.sql',
  'rrc_0006__courier_evidence_governance.sql',
  'rrc_0101__evidence_order_constraints.sql',
  'rrc_0500__settlement_freeze.sql'
)
foreach ($name in $ordered) {
  if (!(Test-Path (Join-Path $up $name))) { throw "missing migration: $name" }
}
if ($M08Migration -and !(Test-Path $M08Migration)) { throw "missing M08 migration: $M08Migration" }
$container = 'rrc-v2-chain-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
function Docker([string[]]$Arguments) {
  $result = & docker.exe @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
  $result
}
function Sql([string]$Statement, [switch]$MustFail) {
  $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $result = $Statement | & docker.exe exec -i $container psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d verify 2>$null
  $code = $LASTEXITCODE; $ErrorActionPreference = $old
  if ($MustFail) { if ($code -eq 0) { throw 'expected SQL failure did not occur' } }
  elseif ($code -ne 0) { throw 'isolated SQL failed' }
  $result
}
try {
  Docker @('run','--detach','--name',$container,'--network','none','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine') | Out-Null
  $ready = $false
  for ($i = 0; $i -lt 60; $i++) {
    & docker.exe exec $container pg_isready -U postgres *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
  }
  if (!$ready) { throw 'postgres is not ready' }
  Docker @('exec',$container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-c','CREATE DATABASE verify;') | Out-Null
  foreach ($name in $ordered) {
    Sql (Get-Content -Raw -Encoding UTF8 (Join-Path $up $name)) | Out-Null
  }
  if ($M08Migration) { Sql (Get-Content -Raw -Encoding UTF8 $M08Migration) | Out-Null }
  $duplicate = if ($M08Migration) { $M08Migration } else { Join-Path $up 'rrc_0500__settlement_freeze.sql' }
  Sql (Get-Content -Raw -Encoding UTF8 $duplicate) -MustFail | Out-Null
  $objects = (Sql "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2';").Trim()
  if ($objects -ne '1') { throw 'runrun_v2 schema missing' }
  $oldPublic = (Sql "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname='flyway_schema_history';").Trim()
  if ($oldPublic -ne '0') { throw 'legacy public Flyway history was touched' }
  Docker @('exec',$container,'pg_dump','-Fc','-U','postgres','-d','verify','-f','/tmp/v2-chain.dump') | Out-Null
  Docker @('exec',$container,'createdb','-U','postgres','restored') | Out-Null
  Docker @('exec',$container,'pg_restore','-U','postgres','-d','restored','/tmp/v2-chain.dump') | Out-Null
  Write-Output 'PASS: complete v2 migration chain, duplicate rejection, legacy isolation and backup restore'
} finally {
  & docker.exe rm -f -v $container *> $null
}
