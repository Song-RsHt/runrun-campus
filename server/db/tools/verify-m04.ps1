param()
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$name = 'rrc-m04-' + [guid]::NewGuid().ToString('N').Substring(0,12)
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
function Assert([bool]$Condition,[string]$Label) {
  if (-not $Condition) { throw "FAIL: $Label" }
  Write-Output "PASS: $Label"
}
try {
  $null=Docker @('run','--detach','--name',$name,'--network','none','--label','runrun.module=M04','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')
  $ready=$false
  for($i=0;$i -lt 40;$i++) {
    & docker.exe exec $name pg_isready -h 127.0.0.1 -U postgres *> $null
    if($LASTEXITCODE -eq 0) { $ready=$true; break }
    Start-Sleep -Milliseconds 500
  }
  Assert $ready 'isolated PostgreSQL ready'
  $null=Sql postgres 'CREATE DATABASE fulfillment; CREATE DATABASE restored;'
  foreach($migration in @('rrc_0001__create_v2_schema','rrc_0002__create_identity_foundation','rrc_0003__identity_runtime','rrc_0100__orders_and_service_config','rrc_0200__mock_payments','rrc_0300__fulfillment')) {
    $null=Sql fulfillment (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$migration.sql"))
  }
  $up=Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/up/rrc_0300__fulfillment.sql')
  $down=Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/down/rrc_0300__fulfillment.rollback.sql')
  Assert ((Sql fulfillment 'SELECT count(*) FROM runrun_v2.fulfillment_schema_manifest') -eq '7') 'M04 manifest created'
  $null=Sql fulfillment $up -MustFail
  Write-Output 'PASS: repeated migration refuses overwrite'
  $null=Sql fulfillment 'CREATE INDEX synthetic_later_idx ON runrun_v2.fulfillment_tasks(status);'
  $null=Sql fulfillment $down -MustFail
  Assert ((Sql fulfillment "SELECT count(*) FROM pg_class WHERE relname='synthetic_later_idx'") -eq '1') 'rollback preserves later module index'
  $null=Sql fulfillment 'DROP INDEX runrun_v2.synthetic_later_idx;'
  $null=Sql fulfillment $down
  Assert ((Sql fulfillment "SELECT to_regclass('runrun_v2.fulfillment_tasks') IS NULL") -eq 't') 'pristine M04 rollback succeeds'
  Assert ((Sql fulfillment "SELECT to_regclass('runrun_v2.payment_orders') IS NOT NULL") -eq 't') 'rollback preserves M03'
  $null=Sql fulfillment $up
  $null=Sql fulfillment @'
INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','USER'),('13800000002','COURIER');
INSERT INTO runrun_v2.service_points(code,name) VALUES('SYNTHETIC','synthetic');
INSERT INTO runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline)
 SELECT 'SYNTHETIC',u.id,'LOBBY',z.id,p.id,'{}','{}','{}',200,200,now()+interval '30 minutes'
 FROM runrun_v2.app_users u CROSS JOIN runrun_v2.delivery_zones z CROSS JOIN runrun_v2.service_points p WHERE u.role='USER' AND z.code='STANDARD';
INSERT INTO runrun_v2.fulfillment_tasks(order_id,courier_id,status) SELECT o.id,u.id,'OFFERED' FROM runrun_v2.v2_orders o CROSS JOIN runrun_v2.app_users u WHERE u.role='COURIER';
INSERT INTO runrun_v2.location_events(order_id,task_id,courier_id,event_type,latitude,longitude,coordinate_system,received_at,request_id,accepted)
 SELECT order_id,id,courier_id,'ARRIVAL',30,120,'GCJ02',now(),'synthetic-gps',true FROM runrun_v2.fulfillment_tasks;
'@
  $null=Sql fulfillment $down -MustFail
  Assert ((Sql fulfillment 'SELECT count(*) FROM runrun_v2.location_events') -eq '1') 'rollback refuses evidence'
  $null=Sql fulfillment "INSERT INTO runrun_v2.location_events(order_id,task_id,courier_id,event_type,latitude,longitude,coordinate_system,received_at,request_id,accepted) SELECT order_id,id,courier_id,'ARRIVAL',NULL,120,'GCJ02',now(),'invalid-gps',true FROM runrun_v2.fulfillment_tasks" -MustFail
  Write-Output 'PASS: invalid coordinate pair rejected'
  $null=Sql fulfillment 'UPDATE runrun_v2.location_events SET order_id=999' -MustFail
  Write-Output 'PASS: cross-order task reference rejected'
  $null=Docker @('exec',$name,'pg_dump','-U','postgres','-d','fulfillment','-Fc','-f','/tmp/m04.dump')
  $null=Docker @('exec',$name,'pg_restore','-U','postgres','-d','restored','--exit-on-error','/tmp/m04.dump')
  Assert ((Sql restored "SELECT count(*) FROM runrun_v2.location_events WHERE request_id='synthetic-gps' AND latitude=30 AND longitude=120") -eq '1') 'backup restores location evidence'
  Assert ((Sql restored "SELECT count(*) FROM pg_constraint WHERE conname='order_event_task_fk'") -eq '1') 'backup restores deferred task association FK'
  Assert ((Sql restored "SELECT bool_and(runrun_v2.payment_schema_signature(('runrun_v2.'||relation_name)::regclass)=signature) FROM runrun_v2.fulfillment_schema_manifest") -eq 't') 'backup restores schema signatures'
} finally {
  & docker.exe rm -f -v $name *> $null
}
