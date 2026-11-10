param()
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$name = 'rrc-m02-' + [guid]::NewGuid().ToString('N').Substring(0,12)
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
$migrations = @('rrc_0001__create_v2_schema','rrc_0002__create_identity_foundation','rrc_0003__identity_runtime','rrc_0100__orders_and_service_config')
try {
  $null = Docker @('run','--detach','--name',$name,'--network','none','--label','runrun.module=M02','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')
  $ready = $false
  for ($i=0; $i -lt 40; $i++) {
    & docker.exe exec $name pg_isready -h 127.0.0.1 -U postgres *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
  }
  Assert $ready 'isolated PostgreSQL ready'
  $null = Sql postgres 'CREATE DATABASE orders; CREATE DATABASE restored;'
  foreach ($migration in $migrations) { $null = Sql orders (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$migration.sql")) }
  Assert ((Sql orders "SELECT count(*) FROM pg_tables WHERE schemaname='runrun_v2'") -eq '15') '0001-0100 chain creates exactly 15 owned tables; no payment table'
  Assert ((Sql orders "SELECT new_order_enabled::text||':'||business_timezone||':'||business_open||':'||business_close FROM runrun_v2.platform_settings") -eq 'false:Asia/Shanghai:09:00:00:23:00:00') 'defaults closed with structured Beijing schedule'
  Assert ((Sql orders "SELECT string_agg(code||':'||package_surcharge_cents,',' ORDER BY code) FROM runrun_v2.delivery_zones") -eq 'MEIYUAN:200,STANDARD:0') 'two initial structured zones'
  Assert ((Sql orders 'SELECT count(*) FROM runrun_v2.service_points') -eq '0') 'no invented pickup coordinates seeded'
  $null = Sql orders (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/up/rrc_0100__orders_and_service_config.sql')) -MustFail
  Write-Output 'PASS: repeated initialization rejected'
  for ($i=$migrations.Length-1; $i -ge 0; $i--) { $null = Sql orders (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/down/$($migrations[$i]).rollback.sql")) }
  Assert ((Sql orders "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'empty reverse chain 0100-0003-0002-0001 succeeds'
  foreach ($migration in $migrations) { $null = Sql orders (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$migration.sql")) }
  $null = Sql orders @'
CREATE INDEX later_module_idx ON runrun_v2.v2_orders(order_no);
ALTER TABLE runrun_v2.v2_orders ADD COLUMN later_module_col TEXT;
ALTER TABLE runrun_v2.v2_orders ADD CONSTRAINT later_module_ck CHECK (later_module_col IS NULL OR length(later_module_col) < 100);
CREATE FUNCTION runrun_v2.later_module_trigger_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$;
CREATE TRIGGER later_module_trigger BEFORE UPDATE ON runrun_v2.v2_orders FOR EACH ROW EXECUTE FUNCTION runrun_v2.later_module_trigger_fn();
'@
  $null = Sql orders (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/down/rrc_0100__orders_and_service_config.rollback.sql')) -MustFail
  Assert ((Sql orders "SELECT count(*) FROM pg_class WHERE relname='later_module_idx'") -eq '1') 'rollback rejects later-module index'
  Assert ((Sql orders "SELECT count(*) FROM information_schema.columns WHERE table_schema='runrun_v2' AND table_name='v2_orders' AND column_name='later_module_col'") -eq '1') 'rollback preserves later-module column'
  Assert ((Sql orders "SELECT count(*) FROM pg_trigger WHERE tgname='later_module_trigger'") -eq '1') 'rollback preserves later-module trigger'
  Assert ((Sql orders "SELECT count(*) FROM pg_constraint WHERE conname='later_module_ck'") -eq '1') 'rollback preserves later-module constraint'
  $null = Sql orders @'
DROP TRIGGER later_module_trigger ON runrun_v2.v2_orders;
DROP FUNCTION runrun_v2.later_module_trigger_fn();
ALTER TABLE runrun_v2.v2_orders DROP CONSTRAINT later_module_ck;
ALTER TABLE runrun_v2.v2_orders DROP COLUMN later_module_col;
DROP INDEX runrun_v2.later_module_idx;
'@
  $null = Sql orders @'
INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','USER');
INSERT INTO runrun_v2.service_points(code,name) VALUES('TEST_POINT','synthetic pickup point');
INSERT INTO runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline)
VALUES('synthetic-order',1,'PACKAGE_SMALL',2,1,'{"destination":"synthetic","zoneCode":"MEIYUAN","servicePointCode":"TEST_POINT","latitude":null,"longitude":null,"coordinateSystem":"GCJ02"}',
'{"serviceType":"PACKAGE_SMALL","baseCents":100,"zoneCode":"MEIYUAN","zoneSurchargeCents":200,"totalCents":300,"configVersion":0}',
'{"configVersion":0,"pendingPaymentMinutes":30,"deliveryConfirmMinutes":10,"platformSharePercent":30,"courierSharePercent":70}',300,300,now()+interval '30 minutes');
INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload) VALUES(1,1,'ORDER_CREATED','synthetic-request','{}');
'@
  $null = Sql orders 'UPDATE runrun_v2.v2_orders SET order_flow_version=1,version=1 WHERE id=1' -MustFail
  $null = Sql orders 'UPDATE runrun_v2.v2_orders SET version=2 WHERE id=1' -MustFail
  $null = Sql orders "UPDATE runrun_v2.v2_orders SET pricing_snapshot='{}',version=1 WHERE id=1" -MustFail
  Write-Output 'PASS: database rejects flow mutation, skipped version and snapshot overwrite'
  $null = Sql orders (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/down/rrc_0100__orders_and_service_config.rollback.sql')) -MustFail
  Assert ((Sql orders 'SELECT count(*) FROM runrun_v2.v2_order_events') -eq '1') 'rollback refuses and preserves business evidence'
  Assert ((Sql orders "SELECT order_flow_version||':'||version||':'||original_fee_cents FROM runrun_v2.v2_orders WHERE id=1") -eq '2:0:300') 'failed rollback preserves order structure and data'
  Assert ((Sql orders "SELECT count(*) FROM pg_trigger WHERE tgname='v2_orders_guard'") -eq '1') 'failed rollback preserves M02 trigger'
  $null = Docker @('exec',$name,'pg_dump','-U','postgres','-d','orders','-Fc','-f','/tmp/m02.dump')
  $null = Docker @('exec',$name,'pg_restore','-U','postgres','-d','restored','--exit-on-error','/tmp/m02.dump')
  Assert ((Sql restored "SELECT order_flow_version||':'||version||':'||original_fee_cents||':'||(pricing_snapshot->>'totalCents') FROM runrun_v2.v2_orders") -eq '2:0:300:300') 'backup restores original order facts and snapshot'
  $null = Sql restored 'UPDATE runrun_v2.v2_orders SET version=0 WHERE id=1' -MustFail
  $null = Sql restored 'UPDATE runrun_v2.v2_orders SET effective_fee_cents=400,version=version+1 WHERE id=1'
  Assert ((Sql restored 'SELECT version FROM runrun_v2.v2_orders WHERE id=1') -eq '1') 'restored trigger enforces exactly one version increment'
} finally {
  & docker.exe rm -f -v $name *> $null
  if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $name" }
}
