param()
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$name = 'rrc-m01-' + [guid]::NewGuid().ToString('N').Substring(0,12)
function Invoke-Docker([string[]]$Arguments) {
  $result = & docker.exe @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw 'Docker operation failed; raw output suppressed.' }
  return $result
}
function Sql([string]$Database, [string]$Query, [switch]$MustFail) {
  $result = $Query | & docker.exe exec -i $name psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d $Database 2>&1
  $code = $LASTEXITCODE
  if ($MustFail) {
    if ($code -eq 0) { throw 'Expected SQL rejection did not occur.' }
  } elseif ($code -ne 0) { throw 'SQL verification failed; raw output suppressed.' }
  return ($result -join "`n")
}
function Assert([bool]$Condition, [string]$Label) {
  if (-not $Condition) { throw "FAIL: $Label" }
  Write-Output "PASS: $Label"
}
$m00up = Get-Content -Raw (Join-Path $root 'server/db/migrations/up/rrc_0001__create_v2_schema.sql')
$m00down = Get-Content -Raw (Join-Path $root 'server/db/migrations/down/rrc_0001__create_v2_schema.rollback.sql')
$m01up = Get-Content -Raw (Join-Path $root 'server/db/migrations/up/rrc_0002__create_identity_foundation.sql')
$m01down = Get-Content -Raw (Join-Path $root 'server/db/migrations/down/rrc_0002__create_identity_foundation.rollback.sql')
try {
  $null = Invoke-Docker @('run','--detach','--name',$name,'--network','none','--label','runrun.module=M01','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')
  $ready = $false
  for ($i=0; $i -lt 40; $i++) {
    & docker.exe exec $name pg_isready -U postgres *> $null
    if ($LASTEXITCODE -eq 0) { $ready=$true; break }
    Start-Sleep -Milliseconds 500
  }
  Assert $ready 'temporary PostgreSQL ready'
  $null = Sql postgres 'CREATE DATABASE rrc_m01; CREATE DATABASE rrc_restore; CREATE DATABASE rrc_legacy;'
  $null = Sql rrc_m01 $m00up
  $null = Sql rrc_m01 $m01up
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relkind='r'") -eq '8') 'M01 creates exactly eight tables'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')") -eq '0') 'legacy public objects remain absent'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM information_schema.columns WHERE table_schema='runrun_v2' AND table_name='courier_certifications' AND column_name IN ('real_name','campus','student_id','expires_at')") -eq '0') 'v2 certification does not collect legacy identity fields'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM information_schema.columns WHERE table_schema='runrun_v2' AND table_name='courier_certifications' AND column_name IN ('eligible','version','submitted_photo_resource_id','approved_photo_resource_id')") -eq '4') 'certification eligibility/version/photo references present'
  $null = Sql rrc_m01 $m01up -MustFail
  Write-Output 'PASS: repeated M01 migration rejected'
  $null = Sql rrc_m01 "INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','COURIER'); INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_ciphertext,openid_hash) VALUES(1,'test','cipher','hash'); INSERT INTO runrun_v2.courier_certifications(courier_id) VALUES(1);"
  $null = Sql rrc_m01 "INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_ciphertext,openid_hash) VALUES(1,'test','cipher2','hash');" -MustFail
  Write-Output 'PASS: duplicate WeChat identity rejected'
  $null = Sql rrc_m01 "INSERT INTO runrun_v2.courier_certifications(courier_id,submitted_photo_resource_id) VALUES(1,'resource-without-key');" -MustFail
  Write-Output 'PASS: certification photo pair rejected'
  $null = Sql rrc_m01 "CREATE TABLE runrun_v2.m02_synthetic(id bigint PRIMARY KEY);" 
  $null = Sql rrc_m01 $m01down -MustFail
  Write-Output 'PASS: rollback rejects later objects without CASCADE'
  $null = Sql rrc_m01 'DROP TABLE runrun_v2.m02_synthetic;'
  $null = Invoke-Docker @('exec',$name,'pg_dump','-U','postgres','-d','rrc_m01','-Fc','-f','/tmp/m01.dump')
  $null = Invoke-Docker @('exec',$name,'pg_restore','-U','postgres','-d','rrc_restore','--exit-on-error','/tmp/m01.dump')
  Assert ((Sql rrc_restore "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relname='app_users'") -eq '1') 'M01 backup restore preserves identity table'
  $null = Sql rrc_m01 $m01down
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relkind IN ('r','p','v','m','S','f')") -eq '0') 'M01 rollback leaves empty schema'
  $null = Sql rrc_m01 $m00down
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'M00 rollback removes schema only after M01 rollback'
} finally {
  & docker.exe rm -f -v $name *> $null
  if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $name" }
}
