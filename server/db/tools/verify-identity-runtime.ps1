param()
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$name = 'rrc-identity-' + [guid]::NewGuid().ToString('N').Substring(0,12)
function Docker([string[]]$Arguments) {
  $result = & docker.exe @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw 'Temporary Docker operation failed; raw diagnostics suppressed.' }
  return $result
}
function Sql([string]$Database, [string]$Query, [switch]$MustFail) {
  $result = $Query | & docker.exe exec -i $name psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d $Database 2>&1
  if ($MustFail) {
    if ($LASTEXITCODE -eq 0) { throw 'Expected SQL rejection did not occur.' }
  } elseif ($LASTEXITCODE -ne 0) { throw 'SQL check failed; raw diagnostics suppressed.' }
  return ($result -join "`n")
}
function Assert([bool]$Condition, [string]$Label) {
  if (-not $Condition) { throw "FAIL: $Label" }
  Write-Output "PASS: $Label"
}
$migrationFiles = @('rrc_0001__create_v2_schema','rrc_0002__create_identity_foundation','rrc_0003__identity_runtime')
try {
  $null = Docker @('run','--detach','--name',$name,'--network','none','--label','runrun.module=M01-runtime','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')
  $ready = $false
  for ($i=0; $i -lt 40; $i++) {
    & docker.exe exec $name pg_isready -h 127.0.0.1 -U postgres *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
  }
  Assert $ready 'temporary isolated PostgreSQL ready'
  $null = Sql postgres 'CREATE DATABASE runtime; CREATE DATABASE restored;'
  foreach ($migration in $migrationFiles) {
    $null = Sql runtime (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$migration.sql"))
  }
  Assert ((Sql runtime "SELECT count(*) FROM pg_tables WHERE schemaname='runrun_v2'") -eq '10') 'runtime chain creates ten v2 tables'
  $null = Sql runtime (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/up/rrc_0003__identity_runtime.sql')) -MustFail
  Write-Output 'PASS: repeated runtime migration rejected'
  $null = Sql runtime "INSERT INTO runrun_v2.app_users(phone,role,v2_admin_enabled) VALUES('13800000001','USER',true);" -MustFail
  $null = Sql runtime "INSERT INTO runrun_v2.app_users(phone,role,controlled_identity,v2_admin_enabled) VALUES('deployment:synthetic','ADMIN',true,true);" -MustFail
  Write-Output 'PASS: non-admin enable and deployer business access rejected by database'
  # Empty reverse chain must work without deleting unrelated objects.
  for ($i=$migrationFiles.Length-1; $i -ge 0; $i--) {
    $null = Sql runtime (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/down/$($migrationFiles[$i]).rollback.sql"))
  }
  Assert ((Sql runtime "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'empty reverse chain 0003-0002-0001 succeeds'
  foreach ($migration in $migrationFiles) {
    $null = Sql runtime (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$migration.sql"))
  }
  $null = Sql runtime "INSERT INTO runrun_v2.app_users(phone,role,controlled_identity) VALUES('deployment:synthetic','ADMIN',true),('13800000001','USER',false); INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata) VALUES(1,'V2_DEPLOYER_CREATED','APP_USER',1,'synthetic-bootstrap','synthetic reason','{`"actorKind`":`"CONTROLLED_DEPLOYMENT`"}'); INSERT INTO runrun_v2.identity_tickets(token_hash,kind,app_id,openid_hash,openid_ciphertext,expires_at) VALUES('synthetic-ticket-digest','WECHAT_LOGIN','synthetic-app','synthetic-digest','synthetic-ciphertext',now()+interval '5 minutes'); INSERT INTO runrun_v2.identity_audit_events(actor_id,event_type,subject_id) VALUES(2,'WECHAT_BOUND',2);"
  $null = Sql runtime (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/down/rrc_0003__identity_runtime.rollback.sql')) -MustFail
  Assert ((Sql runtime 'SELECT count(*) FROM runrun_v2.identity_audit_events') -eq '1') 'rollback refuses runtime evidence and preserves it'
  $null = Docker @('exec',$name,'pg_dump','-U','postgres','-d','runtime','-Fc','-f','/tmp/identity-runtime.dump')
  $null = Docker @('exec',$name,'pg_restore','-U','postgres','-d','restored','--exit-on-error','/tmp/identity-runtime.dump')
  Assert ((Sql restored "SELECT (SELECT count(*) FROM runrun_v2.identity_tickets)||':'||(SELECT count(*) FROM runrun_v2.identity_audit_events)||':'||(SELECT count(*) FROM runrun_v2.admin_audit_logs)") -eq '1:1:1') 'backup restore preserves runtime tickets and both audit streams'
  $null = Sql restored "UPDATE runrun_v2.app_users SET v2_admin_enabled=true WHERE controlled_identity" -MustFail
  Assert ((Sql restored "SELECT count(*) FROM pg_constraint WHERE conname IN ('app_users_controlled_ck','app_users_admin_enabled_ck')") -eq '2') 'backup restore preserves authorization constraints'
} finally {
  & docker.exe rm -f -v $name *> $null
  if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $name" }
}
