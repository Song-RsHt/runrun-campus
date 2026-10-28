param([Parameter(Mandatory=$true)][string]$PrerequisiteRoot)
$ErrorActionPreference='Stop'
if(Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue){$PSNativeCommandUseErrorActionPreference=$false}
$name='rrc-m08-'+[guid]::NewGuid().ToString('N').Substring(0,10)
function Run([string[]]$a){$o=& docker.exe @a 2>&1;if($LASTEXITCODE-ne 0){throw ($o -join "`n")};return $o}
function Sql([string]$q,[switch]$MustFail){
  $outFile=[IO.Path]::GetTempFileName(); $errFile=[IO.Path]::GetTempFileName()
  $previous=$ErrorActionPreference; $ErrorActionPreference='Continue'
  try {
    $q | & docker.exe exec -i $name psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d verify 1> $outFile 2> $errFile
    $code=$LASTEXITCODE
    $o=@(Get-Content -ErrorAction SilentlyContinue $outFile) + @(Get-Content -ErrorAction SilentlyContinue $errFile)
  } finally { $ErrorActionPreference=$previous; Remove-Item -Force -ErrorAction SilentlyContinue $outFile,$errFile }
  if($MustFail){
    if($code -eq 0){throw 'expected SQL failure did not occur'}
    return $o
  }
  if($code -ne 0){throw ($o -join "`n")}
  return $o
}
try {
 Run @('run','--detach','--name',$name,'--network','none','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')|Out-Null
 for($i=0;$i-lt 40;$i++){& docker.exe exec $name pg_isready -U postgres *> $null;if($LASTEXITCODE-eq 0){break};Start-Sleep -Milliseconds 500}
 Run @('exec',$name,'psql','-X','-qAt','-U','postgres','-c','CREATE DATABASE verify;')|Out-Null
 $up=Join-Path $PrerequisiteRoot 'server/db/migrations/up'
$ordered=@('rrc_0001__create_v2_schema.sql','rrc_0002__create_identity_foundation.sql','rrc_0003__identity_runtime.sql','rrc_0100__orders_and_service_config.sql','rrc_0200__mock_payments.sql','rrc_0300__fulfillment.sql','rrc_0004__adjustments_after_sales.sql','rrc_0006__courier_evidence_governance.sql','rrc_0101__evidence_order_constraints.sql','rrc_0500__settlement_freeze.sql')
$files=$ordered | ForEach-Object { Get-Item (Join-Path $up $_) }
 foreach($file in $files){$text=Get-Content -Raw -Encoding UTF8 $file.FullName; Sql $text|Out-Null}
 $m08=Get-Content -Raw -Encoding UTF8 (Join-Path (Get-Location) 'server/db/migrations/up/rrc_0007__notifications.sql'); Sql $m08|Out-Null
 Sql $m08 -MustFail
 $objects=(Sql "SELECT to_regclass('runrun_v2.user_notifications')||':'||to_regclass('runrun_v2.notification_templates')||':'||to_regclass('runrun_v2.notification_outbox');") -join ''
 if($objects -ne 'runrun_v2.user_notifications:runrun_v2.notification_templates:runrun_v2.notification_outbox'){throw 'M08 object ownership assertion failed'}
 Sql "INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','USER'); INSERT INTO runrun_v2.service_points(code,name) VALUES('TEST','Test'); INSERT INTO runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline) VALUES('M08-TEST',1,'LOBBY',1,1,'{}','{}','{}',200,200,now()+interval '30 minutes'); SELECT runrun_v2.enqueue_notification(1,(SELECT max(id) FROM runrun_v2.v2_orders),'PAYMENT_SUCCEEDED','payment:9001:ok','{}'::jsonb,'{}'::jsonb);"|Out-Null
 if(((Sql "SELECT count(*) FROM runrun_v2.user_notifications; SELECT count(*) FROM runrun_v2.notification_outbox;") -join "`n") -ne "1`n2"){throw 'atomic enqueue assertion failed'}
 Sql "INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000002','COURIER'); INSERT INTO runrun_v2.courier_certifications(courier_id,status) VALUES(2,'PENDING');"|Out-Null
 if(((Sql "SELECT count(*) FROM runrun_v2.user_notifications; SELECT count(*) FROM runrun_v2.notification_outbox;") -join "`n") -ne "2`n4"){throw 'certification business trigger assertion failed'}
 if(((Sql "SELECT count(*) FROM runrun_v2.claim_notification_outbox(50);") -join '') -ne '2'){throw 'outbox claim assertion failed'}
 if(((Sql "SELECT count(*) FROM runrun_v2.claim_notification_outbox(50);") -join '') -ne '0'){throw 'claim lease isolation assertion failed'}
 Sql "UPDATE runrun_v2.notification_outbox SET locked_until=now()-interval '1 second' WHERE status='SENDING';"|Out-Null
 if(((Sql "SELECT count(*) FROM runrun_v2.claim_notification_outbox(50);") -join '') -ne '2'){throw 'expired lease recovery assertion failed'}
 $claimed=(Sql "SELECT id FROM runrun_v2.notification_outbox WHERE status='SENDING' ORDER BY id LIMIT 1") -join ''
 Sql "SELECT runrun_v2.complete_notification_outbox($claimed,false,'wechat timeout');"|Out-Null
 if(((Sql "SELECT status FROM runrun_v2.notification_outbox WHERE id=$claimed;") -join '') -ne 'FAILED'){throw 'failure backoff assertion failed'}
 Sql "CREATE TABLE runrun_v2.m08_later_sentinel(id BIGINT);"|Out-Null
 $down=Get-Content -Raw -Encoding UTF8 (Join-Path (Get-Location) 'server/db/migrations/down/rrc_0007__notifications.rollback.sql'); Sql $down -MustFail
 Write-Output 'PASS: M00→M07→M08 strict migration, duplicate rejection, ownership and rollback guard'
} finally {& docker.exe rm -f -v $name *> $null}
