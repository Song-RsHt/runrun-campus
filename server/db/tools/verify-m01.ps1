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
function SqlConstraint([string]$Database, [string]$Query, [string]$Constraint) {
  $result = $Query | & docker.exe exec -i $name psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d $Database 2>&1
  if ($LASTEXITCODE -eq 0) { throw "Expected constraint rejection did not occur: $Constraint" }
  $text = $result -join "`n"
  if ($text -notmatch [regex]::Escape($Constraint)) { throw "Expected constraint was not reported: $Constraint" }
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
  $null = Sql rrc_legacy 'CREATE TABLE public.flyway_schema_history(version text); CREATE TABLE public.campus_orders(id bigint);'
  $null = Sql rrc_legacy $m00up -MustFail
  Assert ((Sql rrc_legacy "SELECT count(*) FROM pg_class WHERE oid IN ('public.flyway_schema_history'::regclass,'public.campus_orders'::regclass)") -eq '2') 'legacy public sentinels preserved and rejected'
  $null = Sql rrc_m01 $m00up
  $null = Sql rrc_m01 $m01up
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relkind='r'") -eq '8') 'M01 creates exactly eight tables'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')") -eq '0') 'legacy public objects remain absent'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM information_schema.columns WHERE table_schema='runrun_v2' AND table_name='courier_certifications' AND column_name IN ('real_name','campus','student_id','expires_at')") -eq '0') 'v2 certification does not collect legacy identity fields'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM information_schema.columns WHERE table_schema='runrun_v2' AND table_name='courier_certifications' AND column_name IN ('eligible','version','submitted_photo_resource_id','approved_photo_resource_id')") -eq '4') 'certification eligibility/version/photo references present'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_index i WHERE i.indrelid='runrun_v2.wechat_identities'::regclass AND i.indkey[0]=(SELECT attnum FROM pg_attribute WHERE attrelid=i.indrelid AND attname='user_id')") -ge '1') 'wechat user foreign key has leading index'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_index i WHERE i.indrelid='runrun_v2.courier_certification_events'::regclass AND i.indkey[0]=(SELECT attnum FROM pg_attribute WHERE attrelid=i.indrelid AND attname='actor_id')") -ge '1') 'certification event actor foreign key has leading index'
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_index i WHERE i.indrelid='runrun_v2.notification_outbox'::regclass AND i.indkey[0]=(SELECT attnum FROM pg_attribute WHERE attrelid=i.indrelid AND attname='recipient_id')") -ge '1') 'outbox recipient foreign key has leading index'
  $null = Sql rrc_m01 $m01up -MustFail
  Write-Output 'PASS: repeated M01 migration rejected'
  $null = Sql rrc_m01 "INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','COURIER'),('13800000002','COURIER'),('13800000003','ADMIN'); INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_ciphertext,openid_hash) VALUES(1,'test','cipher','hash'); INSERT INTO runrun_v2.courier_certifications(courier_id,submitted_photo_resource_id,submitted_photo_storage_key) VALUES(1,'resource-1','storage-1'); INSERT INTO runrun_v2.courier_certification_events(certification_id,actor_id,actor_kind,event_type,request_id,certification_version) VALUES(1,1,'COURIER','COURIER_CERTIFICATION_SUBMITTED','request-1',0); INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason) VALUES(3,'V2_ADMIN_ACCESS_CHANGED','APP_USER',3,'audit-1','synthetic test'); INSERT INTO runrun_v2.idempotency_requests(actor_id,scope,key,request_hash,status) VALUES(1,'test-scope','test-key','hash-1','SUCCEEDED');"
  SqlConstraint rrc_m01 "INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_ciphertext,openid_hash) VALUES(1,'test','cipher2','hash');" 'wechat_identities_app_openid_uq'
  Write-Output 'PASS: duplicate WeChat identity rejected'
  $null = Sql rrc_m01 "INSERT INTO runrun_v2.courier_certifications(courier_id,submitted_photo_resource_id,submitted_photo_storage_key) VALUES(2,'resource-2','storage-2');"
  SqlConstraint rrc_m01 "UPDATE runrun_v2.courier_certifications SET submitted_photo_resource_id='resource-2b', submitted_photo_storage_key=NULL WHERE courier_id=2;" 'courier_certifications_submitted_photo_pair_ck'
  Write-Output 'PASS: submitted certification photo pair rejected'
  SqlConstraint rrc_m01 "UPDATE runrun_v2.courier_certifications SET approved_photo_resource_id='approved-1', approved_photo_storage_key=NULL WHERE courier_id=1;" 'courier_certifications_approved_photo_pair_ck'
  Write-Output 'PASS: approved certification photo pair rejected'
  SqlConstraint rrc_m01 "INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,request_id,reason) VALUES(3,'UNKNOWN_ACTION','APP_USER','audit-unknown','synthetic test');" 'admin_audit_action_ck'
  SqlConstraint rrc_m01 "INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,request_id,reason) VALUES(3,'V2_ADMIN_ACCESS_CHANGED','UNKNOWN_TARGET','audit-target','synthetic test');" 'admin_audit_target_ck'
  SqlConstraint rrc_m01 "INSERT INTO runrun_v2.courier_certification_events(certification_id,actor_id,actor_kind,event_type,request_id,certification_version) VALUES(1,1,'SYSTEM','COURIER_CERTIFICATION_SUBMITTED','request-system',0);" 'certification_events_system_actor_ck'
  SqlConstraint rrc_m01 "INSERT INTO runrun_v2.idempotency_requests(actor_id,scope,key,request_hash,status) VALUES(1,'test-scope','test-key','hash-2','SUCCEEDED');" 'idempotency_requests_uq'
  Write-Output 'PASS: audit, actor and idempotency negative constraints rejected'
  $null = Sql rrc_m01 "CREATE SEQUENCE runrun_v2.m02_synthetic_sequence; CREATE TABLE runrun_v2.m02_synthetic(id bigint PRIMARY KEY);"
  $null = Sql rrc_m01 $m01down -MustFail
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relname='app_users'") -eq '1') 'failed rollback preserves M01 tables'
  Write-Output 'PASS: rollback rejects later objects without CASCADE'
  $null = Sql rrc_m01 'DROP TABLE runrun_v2.m02_synthetic; DROP SEQUENCE runrun_v2.m02_synthetic_sequence;'
  $null = Invoke-Docker @('exec',$name,'pg_dump','-U','postgres','-d','rrc_m01','-Fc','-f','/tmp/m01.dump')
  $null = Invoke-Docker @('exec',$name,'pg_restore','-U','postgres','-d','rrc_restore','--exit-on-error','/tmp/m01.dump')
  Assert ((Sql rrc_restore "SELECT (SELECT count(*) FROM runrun_v2.app_users) || ':' || (SELECT count(*) FROM runrun_v2.wechat_identities) || ':' || (SELECT count(*) FROM runrun_v2.courier_certifications)") -eq '3:1:2') 'M01 backup restore preserves synthetic data'
  Assert ((Sql rrc_restore "SELECT count(*) FROM pg_constraint WHERE conname IN ('app_users_phone_uq','wechat_identities_app_openid_uq','courier_certifications_submitted_photo_pair_ck','admin_audit_action_ck')") -eq '4') 'M01 backup restore preserves constraints'
  SqlConstraint rrc_restore "INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','USER');" 'app_users_phone_uq'
  SqlConstraint rrc_restore "INSERT INTO runrun_v2.courier_certifications(courier_id,submitted_photo_resource_id) VALUES(2,'restore-bad');" 'courier_certifications_submitted_photo_pair_ck'
  $null = Sql rrc_m01 $m01down
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relkind IN ('r','p','v','m','S','f')") -eq '0') 'M01 rollback leaves empty schema'
  $null = Sql rrc_m01 $m00down
  Assert ((Sql rrc_m01 "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'M00 rollback removes schema only after M01 rollback'
} finally {
  & docker.exe rm -f -v $name *> $null
  if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $name" }
}
