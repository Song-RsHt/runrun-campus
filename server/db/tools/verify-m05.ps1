param()
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$up=Join-Path $root 'server/db/migrations/up'; $down=Join-Path $root 'server/db/migrations/down'
$required=@('rrc_0001__create_v2_schema.sql','rrc_0002__create_identity_foundation.sql','rrc_0003__identity_runtime.sql','rrc_0100__orders_and_service_config.sql','rrc_0200__mock_payments.sql','rrc_0300__fulfillment.sql','rrc_0004__adjustments_after_sales.sql')
foreach($f in $required){if(!(Test-Path (Join-Path $up $f))){throw "missing migration: $f"}}
$name='rrc-m05-'+[guid]::NewGuid().ToString('N').Substring(0,8)
function Invoke-Db([string]$sql){$sql | docker exec -i $name psql -X -q -v ON_ERROR_STOP=1 -U postgres -d postgres 2>&1;if($LASTEXITCODE-ne 0){throw 'database check failed'}}
try{
 docker run --detach --name $name --env POSTGRES_HOST_AUTH_METHOD=trust postgres:16-alpine|Out-Null
 for($i=0;$i-lt 40;$i++){docker exec $name pg_isready -U postgres *> $null;if($LASTEXITCODE-eq 0){break};Start-Sleep -Milliseconds 400}
 foreach($f in $required){Get-Content -Raw -Encoding UTF8 (Join-Path $up $f)|docker exec -i $name psql -X -q -v ON_ERROR_STOP=1 -U postgres -d postgres;if($LASTEXITCODE-ne 0){throw "migration failed: $f"}}
 Invoke-Db "SELECT to_regclass('runrun_v2.order_adjustments'),to_regclass('runrun_v2.after_sales_cases');"
 $before=(docker exec $name psql -At -U postgres -d postgres -c "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relname IN ('v2_orders','payment_orders','refund_orders','fulfillment_tasks','v2_order_proofs','location_events','v2_order_events');")
 $rollback=Get-Content -Raw -Encoding UTF8 (Join-Path $down 'rrc_0004__adjustments_after_sales.rollback.sql'); Invoke-Db $rollback
 $after=(docker exec $name psql -At -U postgres -d postgres -c "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='runrun_v2' AND c.relname IN ('v2_orders','payment_orders','refund_orders','fulfillment_tasks','v2_order_proofs','location_events','v2_order_events');")
 if($before.Trim() -ne $after.Trim()){throw 'rollback removed M02-M04 objects'}
 $dump='/tmp/m05.dump'; docker exec $name pg_dump -Fc -U postgres -d postgres -f $dump;if($LASTEXITCODE-ne 0){throw 'backup failed'}; docker exec $name createdb restored;if($LASTEXITCODE-ne 0){throw 'restore db create failed'};docker exec $name pg_restore -U postgres -d restored $dump;if($LASTEXITCODE-ne 0){throw 'restore failed'}
 Write-Output 'PASS: full M00-M05 migration, dependency-safe rollback, and backup restore'
}finally{docker rm -f -v $name *> $null}
