$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$name='rrc-local-migrations-'+[guid]::NewGuid().ToString('N').Substring(0,10)
function Docker([string[]]$arguments) {
  $result=& docker.exe @arguments
  if ($LASTEXITCODE -ne 0) { throw 'Temporary migration verification container operation failed.' }
  return $result
}
function Sql([string]$database,[string]$sql,[switch]$MustFail) {
  $psi=New-Object Diagnostics.ProcessStartInfo
  $psi.FileName='docker.exe'; $psi.UseShellExecute=$false
  $psi.RedirectStandardInput=$true; $psi.RedirectStandardOutput=$true; $psi.RedirectStandardError=$true
  $psi.Arguments="exec -i $name psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d $database"
  $p=New-Object Diagnostics.Process; $p.StartInfo=$psi
  if (!$p.Start()) { throw 'Cannot start SQL verification.' }
  $bytes=[Text.Encoding]::UTF8.GetBytes($sql)
  $p.StandardInput.BaseStream.Write($bytes,0,$bytes.Length); $p.StandardInput.Close()
  $output=$p.StandardOutput.ReadToEnd(); $errorText=$p.StandardError.ReadToEnd(); $p.WaitForExit(); $code=$p.ExitCode; $p.Dispose()
  if ($MustFail) { if ($code -eq 0) { throw 'Expected migration/rollback refusal did not occur.' }; return }
  if ($code -ne 0) { throw "Synthetic migration verification failed: $errorText" }
  return $output.Trim()
}
try {
  Docker @('run','-d','--name',$name,'--network','none','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine') | Out-Null
  $ready=$false
  for ($i=0;$i -lt 60;$i++) {
    & docker.exe exec $name pg_isready -U postgres *> $null
    if ($LASTEXITCODE -eq 0) { $ready=$true; break }; Start-Sleep -Milliseconds 500
  }
  if (!$ready) { throw 'Temporary PostgreSQL not ready.' }
  foreach ($db in @('verify','empty_chain','legacy','restored')) { Sql postgres "CREATE DATABASE $db" | Out-Null }
  $chain=Get-Content (Join-Path $root 'deploy/v2-test/migrations.txt') | Where-Object { $_ }
  foreach ($file in $chain) {
    $sql=Get-Content -Raw (Join-Path $root "server/db/migrations/up/$file")
    Sql verify $sql | Out-Null
    Sql verify $sql -MustFail
    Sql empty_chain $sql | Out-Null
    Write-Output "PASS: apply and duplicate refusal $file"
  }
  Sql legacy "CREATE TABLE public.flyway_schema_history(version text); INSERT INTO public.flyway_schema_history VALUES('synthetic-V43'); CREATE TABLE public.campus_orders(id integer PRIMARY KEY); INSERT INTO public.campus_orders VALUES(43);" | Out-Null
  Sql legacy (Get-Content -Raw (Join-Path $root 'server/db/migrations/up/rrc_0001__create_v2_schema.sql')) -MustFail
  if ((Sql legacy "SELECT (SELECT version FROM public.flyway_schema_history)||':'||(SELECT id FROM public.campus_orders)") -ne 'synthetic-V43:43') { throw 'Legacy sentinel changed.' }
  Write-Output 'PASS: old public database rejected and sentinels unchanged'
  $down=Join-Path $root 'server/db/migrations/down'
  Sql empty_chain 'CREATE TABLE runrun_v2.local_later_sentinel(id integer)' | Out-Null
  Sql empty_chain (Get-Content -Raw (Join-Path $down 'rrc_0007__notifications.rollback.sql')) -MustFail
  Sql empty_chain 'DROP TABLE runrun_v2.local_later_sentinel' | Out-Null
  Write-Output 'PASS: later objects still prevent M08 rollback'
  Sql verify "INSERT INTO runrun_v2.app_users(phone,role) VALUES('synthetic-user','USER'); INSERT INTO runrun_v2.user_notifications(user_id,type,content) VALUES(1,'DELIVERY_COMPLETED','{}');" | Out-Null
  Sql verify (Get-Content -Raw (Join-Path $down 'rrc_0007__notifications.rollback.sql')) -MustFail
  if ((Sql verify 'SELECT count(*) FROM runrun_v2.user_notifications') -ne '1') { throw 'Rollback failed to preserve notification.' }
  Write-Output 'PASS: retained notification prevents rollback and remains intact'
  Sql verify "INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason) VALUES(1,'V2_DEPLOYER_CREATED','APP_USER',1,'synthetic-audit-check','synthetic audit protection check')" | Out-Null
  Sql verify (Get-Content -Raw (Join-Path $down 'rrc_0701__restore_identity_audit_actions.rollback.sql')) -MustFail
  if ((Sql verify "SELECT count(*) FROM runrun_v2.admin_audit_logs WHERE request_id='synthetic-audit-check'") -ne '1') { throw 'M09 rollback lost audit evidence.' }
  Write-Output 'PASS: repaired audit action is accepted and prevents rollback with retained audit evidence'
  Docker @('exec',$name,'pg_dump','-Fc','-U','postgres','-d','verify','-f','/tmp/v2-local.dump') | Out-Null
  Docker @('exec',$name,'pg_restore','--exit-on-error','-U','postgres','-d','restored','/tmp/v2-local.dump') | Out-Null
  if ((Sql restored "SELECT (SELECT count(*) FROM runrun_v2.user_notifications)::text||':'||(SELECT count(*) FROM runrun_v2.admin_audit_logs)::text||':'||(SELECT count(*) FROM pg_constraint WHERE conrelid='runrun_v2.admin_audit_logs'::regclass AND conname='admin_audit_action_m09_ck')::text") -ne '1:1:1') { throw 'Synthetic backup/restore failed to preserve notification, audit or repaired constraint.' }
  Write-Output 'PASS: container-only pg_dump/pg_restore preserves synthetic notification, audit and M09 constraint'
  for ($i=$chain.Count-1;$i -ge 0;$i--) {
    $rollback=$chain[$i].Replace('.sql','.rollback.sql')
    Sql empty_chain (Get-Content -Raw (Join-Path $down $rollback)) | Out-Null
    Write-Output "PASS: empty rollback $rollback"
  }
  if ((Sql empty_chain "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -ne '0') { throw 'Empty rollback incomplete.' }
  Write-Output 'PASS: exact local M00-M08 chain, duplicate refusal, legacy isolation and rollback protection'
} finally { & docker.exe rm -f -v $name *> $null }
