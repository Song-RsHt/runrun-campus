param(
  [string]$MigrationRoot = (Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent)
)
$ErrorActionPreference = 'Stop'
$up = Join-Path $MigrationRoot 'server/db/migrations/up'
$down = Join-Path $MigrationRoot 'server/db/migrations/down'
$ordered = @(
  'rrc_0001__create_v2_schema.sql', 'rrc_0002__create_identity_foundation.sql',
  'rrc_0003__identity_runtime.sql', 'rrc_0100__orders_and_service_config.sql',
  'rrc_0200__mock_payments.sql', 'rrc_0300__fulfillment.sql',
  'rrc_0004__adjustments_after_sales.sql', 'rrc_0006__courier_evidence_governance.sql',
  'rrc_0101__evidence_order_constraints.sql', 'rrc_0500__settlement_freeze.sql',
  'rrc_0600__user_notifications.sql', 'rrc_0700__notification_outbox_runtime.sql'
)
foreach ($name in $ordered) {
  if (!(Test-Path (Join-Path $up $name))) { throw "missing migration: $name" }
  $base = [IO.Path]::GetFileNameWithoutExtension($name)
  if (!(Test-Path (Join-Path $down "$base.rollback.sql"))) { throw "missing rollback: $base" }
}
$container = 'rrc-v2-chain-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
function Docker([string[]]$Arguments) {
  $result = & docker.exe @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw 'isolated Docker operation failed; raw diagnostics suppressed' }
  return $result
}
function Sql([string]$Database, [string]$Statement, [switch]$MustFail) {
  $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $result = $Statement | & docker.exe exec -i $container psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d $Database 2>$null
  $code = $LASTEXITCODE; $ErrorActionPreference = $old
  if ($MustFail) { if ($code -eq 0) { throw 'expected SQL failure did not occur' } }
  elseif ($code -ne 0) { throw 'isolated SQL failed; raw diagnostics suppressed' }
  return ($result -join "`n")
}
function Assert([bool]$Condition, [string]$Label) {
  if (!$Condition) { throw "FAIL: $Label" }
  Write-Output "PASS: $Label"
}
function Apply([string]$Database, [string]$File) {
  Sql $Database (Get-Content -Raw -Encoding UTF8 (Join-Path $up $File)) | Out-Null
}
try {
  Docker @('run','--detach','--name',$container,'--network','none','--label','runrun.module=M00-M08','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine') | Out-Null
  $ready = $false
  for ($i = 0; $i -lt 60; $i++) {
    & docker.exe exec $container pg_isready -U postgres *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
  }
  Assert $ready 'temporary PostgreSQL 16 ready'
  foreach ($database in @('verify','legacy','restored','empty')) { Docker @('exec',$container,'createdb','-U','postgres',$database) | Out-Null }

  $m00 = Get-Content -Raw -Encoding UTF8 (Join-Path $up $ordered[0])
  Sql legacy 'CREATE TABLE public.flyway_schema_history(version text); CREATE TABLE public.campus_orders(id bigint);' | Out-Null
  Sql legacy $m00 -MustFail | Out-Null
  Assert ((Sql legacy "SELECT count(*) FROM pg_class WHERE oid IN ('public.flyway_schema_history'::regclass,'public.campus_orders'::regclass)") -eq '2') 'legacy public sentinels preserved'

  foreach ($name in $ordered) { Apply verify $name }
  Assert ((Sql verify "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '1') 'M00 creates isolated schema'
  Assert ((Sql verify "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('flyway_schema_history','campus_orders')") -eq '0') 'clean v2 database has no legacy public objects'
  Assert ((Sql verify "SELECT to_regclass('runrun_v2.user_notifications')||':'||to_regclass('runrun_v2.notification_outbox')||':'||to_regprocedure('runrun_v2.claim_notification_outbox(text,integer)')") -eq 'runrun_v2.user_notifications:runrun_v2.notification_outbox:runrun_v2.claim_notification_outbox(text,integer)') 'M07 notification table and M08 claim function exist'
  Assert ((Sql verify "SELECT count(*) FROM information_schema.columns WHERE table_schema='runrun_v2' AND table_name='notification_outbox' AND column_name IN ('claimed_at','claimed_by','sent_at','last_error_code')") -eq '4') 'M08 extends existing outbox columns'
  Assert ((Sql verify "SELECT count(*) FROM pg_class WHERE relname='notification_outbox_claim_idx' AND relnamespace='runrun_v2'::regnamespace") -eq '1') 'M08 outbox claim index exists'
  foreach ($name in $ordered) { Sql verify (Get-Content -Raw -Encoding UTF8 (Join-Path $up $name)) -MustFail | Out-Null }
  Write-Output 'PASS: every migration rejects a second execution'

  Sql verify @'
INSERT INTO runrun_v2.app_users(phone,role) VALUES ('chain-user','USER'),('chain-courier','COURIER');
INSERT INTO runrun_v2.service_points(code,name) VALUES ('CHAIN_POINT','synthetic point');
INSERT INTO runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline)
VALUES ('chain-order',1,'PACKAGE_SMALL',2,1,'{"destination":"synthetic","zoneCode":"MEIYUAN","servicePointCode":"CHAIN_POINT","coordinateSystem":"GCJ02"}', '{"serviceType":"PACKAGE_SMALL","baseCents":100,"zoneCode":"MEIYUAN","zoneSurchargeCents":200,"totalCents":300,"configVersion":0}', '{"configVersion":0,"pendingPaymentMinutes":30,"deliveryConfirmMinutes":10,"platformSharePercent":30,"courierSharePercent":70}',300,300,now()+interval '30 minutes');
INSERT INTO runrun_v2.payment_orders(order_id,purpose,amount_cents,deadline) VALUES (1,'ORIGINAL',300,now()+interval '30 minutes');
INSERT INTO runrun_v2.fulfillment_tasks(order_id,courier_id,status) VALUES (1,2,'ACTIVE');
INSERT INTO runrun_v2.v2_order_proofs(order_id,task_id,uploaded_by,kind,storage_key,storage_status) VALUES (1,1,2,'PICKUP','chain-proof','AVAILABLE');
INSERT INTO runrun_v2.location_events(order_id,task_id,courier_id,event_type,latitude,longitude,coordinate_system,received_at,request_id,accepted) VALUES (1,1,2,'ARRIVAL',31.2,121.4,'GCJ02',now(),'chain-location',true);
INSERT INTO runrun_v2.evidence_holds(order_id,source_type,source_id,reason,owner_id,review_due_at) VALUES (1,'AFTER_SALES','chain-case','retain synthetic evidence',2,now()+interval '1 day');
INSERT INTO runrun_v2.evidence_deletion_audits(order_id,resource_type,resource_id,delete_after,policy_snapshot,status) VALUES (1,'ORDER_PROOF','chain-proof',now()+interval '7 days','{}','REQUESTED');
INSERT INTO runrun_v2.settlement_entries(order_id,beneficiary_type,source_type,source_id,kind,delta_cents,reason) VALUES (1,'PLATFORM','ORIGINAL_PAYMENT','chain-payment','EARNED',90,'chain test');
INSERT INTO runrun_v2.user_notifications(user_id,order_id,type,content) VALUES (1,1,'DELIVERY','{"synthetic":true}');
INSERT INTO runrun_v2.notification_outbox(recipient_id,event_key,channel,payload) VALUES (1,'chain-event','IN_APP','{"synthetic":true}');
'@ | Out-Null
  Assert ((Sql verify "SELECT (SELECT count(*)::text FROM runrun_v2.v2_orders)||(SELECT count(*)::text FROM runrun_v2.v2_order_proofs)||(SELECT count(*)::text FROM runrun_v2.evidence_holds)||(SELECT count(*)::text FROM runrun_v2.user_notifications)") -eq '1111') 'business and evidence fixtures persisted'
  Docker @('exec',$container,'pg_dump','-Fc','-U','postgres','-d','verify','-f','/tmp/m00-m08.dump') | Out-Null
  Docker @('exec',$container,'pg_restore','-U','postgres','-d','restored','--exit-on-error','/tmp/m00-m08.dump') | Out-Null
  Assert ((Sql restored "SELECT (SELECT count(*)::text FROM runrun_v2.v2_orders)||(SELECT count(*)::text FROM runrun_v2.evidence_deletion_audits)||(SELECT count(*)::text FROM runrun_v2.user_notifications)") -eq '111') 'pg_dump/pg_restore preserves business, evidence and notification data'
  Assert ((Sql restored "SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='runrun_v2' AND p.proname='claim_notification_outbox'") -eq '1') 'pg_restore preserves M08 function'
  Assert ((Sql verify "SELECT count(*) FROM runrun_v2.claim_notification_outbox('chain-worker',1)") -eq '1') 'M08 claim function claims one outbox row'
  Sql verify (Get-Content -Raw -Encoding UTF8 (Join-Path $down 'rrc_0700__notification_outbox_runtime.rollback.sql')) -MustFail | Out-Null
  Assert ((Sql verify "SELECT count(*) FROM runrun_v2.v2_order_proofs") -eq '1') 'M08 rollback rejection preserves evidence'
  Sql verify "UPDATE runrun_v2.notification_outbox SET status='PENDING',claimed_at=NULL,claimed_by=NULL,attempt_count=0 WHERE event_key='chain-event';" | Out-Null
  Sql verify (Get-Content -Raw -Encoding UTF8 (Join-Path $down 'rrc_0700__notification_outbox_runtime.rollback.sql')) | Out-Null
  Sql verify (Get-Content -Raw -Encoding UTF8 (Join-Path $down 'rrc_0600__user_notifications.rollback.sql')) -MustFail | Out-Null
  Sql verify (Get-Content -Raw -Encoding UTF8 (Join-Path $down 'rrc_0300__fulfillment.rollback.sql')) -MustFail | Out-Null
  Sql verify (Get-Content -Raw -Encoding UTF8 (Join-Path $down 'rrc_0006__courier_evidence_governance.rollback.sql')) -MustFail | Out-Null
  Write-Output 'PASS: notification, fulfillment and evidence rollback protections reject retained records'

  foreach ($name in $ordered) { Apply empty $name }
  for ($i = $ordered.Length - 1; $i -ge 0; $i--) {
    $base = [IO.Path]::GetFileNameWithoutExtension($ordered[$i])
    Sql empty (Get-Content -Raw -Encoding UTF8 (Join-Path $down "$base.rollback.sql")) | Out-Null
  }
  Assert ((Sql empty "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'complete empty-chain reverse rollback succeeds'
  Write-Output 'PASS: complete M00-M08 chain and reverse rollback'
} finally {
  & docker.exe rm -f -v $container *> $null
  if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $container" }
}
