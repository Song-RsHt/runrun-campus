param()
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$name = 'rrc-m03-' + [guid]::NewGuid().ToString('N').Substring(0,12)
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
  $null = Docker @('run','--detach','--name',$name,'--network','none','--label','runrun.module=M03','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')
  $ready=$false
  for($i=0;$i -lt 40;$i++) {
    & docker.exe exec $name pg_isready -h 127.0.0.1 -U postgres *> $null
    if($LASTEXITCODE -eq 0) { $ready=$true; break }
    Start-Sleep -Milliseconds 500
  }
  Assert $ready 'isolated PostgreSQL ready'
  $null=Sql postgres 'CREATE DATABASE payments; CREATE DATABASE restored;'
  foreach($migration in @('rrc_0001__create_v2_schema','rrc_0002__create_identity_foundation','rrc_0003__identity_runtime','rrc_0100__orders_and_service_config','rrc_0200__mock_payments')) {
    $null=Sql payments (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$migration.sql"))
  }
  $up=Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/up/rrc_0200__mock_payments.sql')
  $down=Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/down/rrc_0200__mock_payments.rollback.sql')
  Assert ((Sql payments "SELECT count(*) FROM runrun_v2.payment_schema_manifest") -eq '10') 'migration creates payment schema'
  $null=Sql payments $up -MustFail
  Write-Output 'PASS: repeated migration refuses overwrite'
  $null=Sql payments 'CREATE INDEX synthetic_later_idx ON runrun_v2.payment_orders(status);'
  $null=Sql payments $down -MustFail
  Assert ((Sql payments "SELECT count(*) FROM pg_class WHERE relname='synthetic_later_idx'") -eq '1') 'rollback preserves later attached index'
  $null=Sql payments 'DROP INDEX runrun_v2.synthetic_later_idx;'
  $null=Sql payments $down
  Assert ((Sql payments "SELECT to_regclass('runrun_v2.payment_orders') IS NULL") -eq 't') 'pristine rollback removes only M03'
  Assert ((Sql payments "SELECT to_regclass('runrun_v2.v2_orders') IS NOT NULL") -eq 't') 'rollback preserves M02'
  $null=Sql payments $up
  $null=Sql payments @'
INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','USER');
INSERT INTO runrun_v2.service_points(code,name) VALUES('SYNTHETIC','synthetic');
INSERT INTO runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline)
 SELECT 'SYNTHETIC',u.id,'LOBBY',z.id,p.id,'{}','{}','{}',200,200,now()+interval '30 minutes'
 FROM runrun_v2.app_users u CROSS JOIN runrun_v2.delivery_zones z CROSS JOIN runrun_v2.service_points p WHERE z.code='STANDARD';
INSERT INTO runrun_v2.payment_orders(order_id,purpose,amount_cents,deadline) SELECT id,'ORIGINAL',200,pending_payment_deadline FROM runrun_v2.v2_orders;
INSERT INTO runrun_v2.payment_attempts(payment_order_id,attempt_no,status,channel_reference) SELECT id,1,'SUCCESS','mock-synthetic' FROM runrun_v2.payment_orders;
UPDATE runrun_v2.payment_orders SET status='PAID',credited_attempt_id=(SELECT id FROM runrun_v2.payment_attempts),paid_at=now(),version=version+1;
UPDATE runrun_v2.v2_orders SET status='PENDING_ASSIGNMENT',version=version+1;
INSERT INTO runrun_v2.refund_orders(order_id,payment_order_id,source_attempt_id,amount_cents,purpose,reason,business_key,channel_reference)
 SELECT p.order_id,p.id,a.id,200,'CANCELLATION','synthetic','synthetic-refund','mock-refund-synthetic' FROM runrun_v2.payment_orders p JOIN runrun_v2.payment_attempts a ON a.payment_order_id=p.id;
'@
  $null=Sql payments $down -MustFail
  Assert ((Sql payments 'SELECT count(*) FROM runrun_v2.refund_orders') -eq '1') 'rollback refuses payment/refund evidence'
  $null=Docker @('exec',$name,'pg_dump','-U','postgres','-d','payments','-Fc','-f','/tmp/m03.dump')
  $null=Docker @('exec',$name,'pg_restore','-U','postgres','-d','restored','--exit-on-error','/tmp/m03.dump')
  Assert ((Sql restored "SELECT amount_cents||':'||status FROM runrun_v2.payment_orders") -eq '200:PAID') 'backup restores payment receipt'
  Assert ((Sql restored "SELECT amount_cents||':'||status FROM runrun_v2.refund_orders") -eq '200:REQUESTED') 'backup restores pending refund'
  Assert ((Sql restored "SELECT count(*) FROM pg_constraint WHERE conname='payment_credited_attempt_fk'") -eq '1') 'backup restores receipt foreign key'
  Assert ((Sql restored "SELECT runrun_v2.payment_schema_signature('runrun_v2.payment_orders'::regclass)=signature FROM runrun_v2.payment_schema_manifest WHERE relation_name='payment_orders'") -eq 't') 'schema fingerprint survives restore'
} finally {
  & docker.exe rm -f -v $name *> $null
}
