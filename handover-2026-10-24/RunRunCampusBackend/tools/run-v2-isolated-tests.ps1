param(
  [string]$Test='IdentityIntegrationTest,OrderIntegrationTest,PaymentIntegrationTest,FulfillmentIntegrationTest,CourierCertificationV2DatabaseE2ETest,NotificationRuntimeTest,PaymentEnvironmentTest,WechatHttpGatewayTest,OrderRequestJsonContractTest',
  [switch]$KeepDatabase
)
$ErrorActionPreference='Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$container = 'rrc-v2-test-' + [guid]::NewGuid().ToString('N').Substring(0,12)
function Invoke-Docker([string[]]$Arguments) {
  $result = & docker.exe @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw 'Docker operation failed; raw diagnostics suppressed.' }
  return $result
}
function Invoke-Sql([string]$Database,[string]$Sql) {
  $result = $Sql | & docker.exe exec -i $container psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d $Database 2>&1
  if ($LASTEXITCODE -ne 0) { throw 'v2 migration failed; raw SQL diagnostics suppressed.' }
  return $result
}
try {
  $null = Invoke-Docker @('run','--detach','--name',$container,'--network','none','--label','runrun.scope=v2-test','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')
  $ready = $false
  for ($i=0; $i -lt 60; $i++) {
    & docker.exe exec $container pg_isready -h 127.0.0.1 -U postgres *> $null
    if ($LASTEXITCODE -eq 0) { $ready=$true; break }
    Start-Sleep -Milliseconds 500
  }
  if (-not $ready) { throw 'PostgreSQL 16 did not become ready.' }
  $null = Invoke-Sql postgres 'CREATE DATABASE rrc_v2_test;'
  $rootPath = Split-Path (Split-Path $root -Parent) -Parent
  $migrationRoot = Join-Path $rootPath 'server/db/migrations/up'
  $chain = @(
    'rrc_0001__create_v2_schema.sql','rrc_0002__create_identity_foundation.sql','rrc_0003__identity_runtime.sql',
    'rrc_0100__orders_and_service_config.sql','rrc_0200__mock_payments.sql','rrc_0300__fulfillment.sql',
    'rrc_0004__adjustments_after_sales.sql','rrc_0006__courier_evidence_governance.sql',
    'rrc_0101__evidence_order_constraints.sql','rrc_0500__settlement_freeze.sql'
  )
  foreach ($file in $chain) { Invoke-Sql rrc_v2_test (Get-Content -Raw -Encoding UTF8 (Join-Path $migrationRoot $file)) | Out-Null }
  $null = Invoke-Sql rrc_v2_test 'CREATE TABLE public.v2_test_sentinel(id bigint);'
  $tables = Invoke-Sql rrc_v2_test "SELECT count(*) FROM pg_tables WHERE schemaname='runrun_v2'"
  if (($tables -join '').Trim() -eq '0') { throw 'runrun_v2 initialization produced no tables.' }
  $publicCount = Invoke-Sql rrc_v2_test "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')"
  if (([string]$publicCount).Trim() -ne '1') { throw 'Unexpected public schema state.' }
  Write-Output ('PASS: PostgreSQL 16 v2 chain initialized in runrun_v2; public sentinel isolated (' + ([string]$tables).Trim() + ' tables)')
  $env:SPRING_CONFIG_NAME='application-v2-test'
  $env:RRC_V2_TEST_RUNTIME='isolated'
  & mvn -B -ntp "-Dtest=$Test" test
  if ($LASTEXITCODE -ne 0) { throw "v2 isolated test suite failed ($LASTEXITCODE)" }
} finally {
  if (-not $KeepDatabase) { & docker.exe rm -f -v $container *> $null }
  else { Write-Output "Kept isolated container: $container" }
}
