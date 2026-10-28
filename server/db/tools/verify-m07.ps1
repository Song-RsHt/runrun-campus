param()
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$name='rrc-m07-'+[guid]::NewGuid().ToString('N').Substring(0,10)
function Run([string[]]$a){$o=& docker.exe @a 2>&1;if($LASTEXITCODE-ne 0){throw 'isolated docker operation failed'};return $o}
function Sql([string]$q,[switch]$MustFail){$old=$ErrorActionPreference;$ErrorActionPreference='Continue';$o=$q|& docker.exe exec -i $name psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d verify 2>$null;$code=$LASTEXITCODE;$ErrorActionPreference=$old;if($MustFail){if($code-eq 0){throw 'expected migration rejection did not occur'}}elseif($code-ne 0){throw 'isolated SQL failed'};return $o}
try {
 Run @('run','--detach','--name',$name,'--network','none','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')|Out-Null
 $ready=$false;for($i=0;$i-lt 40;$i++){& docker.exe exec $name pg_isready -U postgres *> $null;if($LASTEXITCODE-eq 0){$ready=$true;break};Start-Sleep -Milliseconds 500};if(!$ready){throw 'postgres not ready'}
 Run @('exec',$name,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-c','CREATE DATABASE verify;')|Out-Null
 foreach($f in @('rrc_0001__create_v2_schema.sql','rrc_0002__create_identity_foundation.sql','rrc_0003__identity_runtime.sql','rrc_0006__courier_evidence_governance.sql','rrc_0100__orders_and_service_config.sql','rrc_0101__evidence_order_constraints.sql')){Sql (Get-Content -Raw -Encoding UTF8 (Join-Path $root "server/db/migrations/up/$f"))|Out-Null}
 Sql (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'server/db/migrations/up/rrc_0006__courier_evidence_governance.sql')) -MustFail|Out-Null
 Write-Output 'PASS: repeated M07 migration rejected'
 Sql "INSERT INTO runrun_v2.app_users(phone,role) VALUES('13800000001','COURIER'); INSERT INTO runrun_v2.courier_certifications(courier_id,status,eligible) VALUES(1,'PENDING',false);"|Out-Null
 if((Sql "SELECT to_regclass('runrun_v2.evidence_holds')||':'||to_regclass('runrun_v2.evidence_deletion_audits')||':'||(SELECT count(*) FROM runrun_v2.courier_certifications);") -ne 'runrun_v2.evidence_holds:runrun_v2.evidence_deletion_audits:1'){throw 'v2 evidence schema assertion failed'}
 if((Sql "SELECT count(*) FROM pg_constraint WHERE conname IN ('evidence_holds_order_fk','evidence_deletion_order_fk');") -ne '2'){throw 'evidence order foreign keys missing'}
 Write-Output 'PASS: isolated v2 certification/evidence schema'
} finally {& docker.exe rm -f -v $name *> $null}
