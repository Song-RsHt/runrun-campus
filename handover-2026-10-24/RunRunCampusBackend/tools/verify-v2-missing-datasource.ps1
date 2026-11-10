$ErrorActionPreference='Stop'
$jar=Join-Path $PSScriptRoot '..\target\runrun-campus-backend-*.jar'
$artifact=Get-ChildItem $jar | Select-Object -First 1
if(-not $artifact){ throw 'build the backend first' }
$names='RRC_V2_JDBC_URL','RRC_V2_DB_USERNAME','RRC_V2_DB_PASSWORD'
$old=@{}; foreach($n in $names){$old[$n]=[Environment]::GetEnvironmentVariable($n); [Environment]::SetEnvironmentVariable($n,$null)}
try { $p=Start-Process java -ArgumentList '-jar', $artifact.FullName -PassThru -WindowStyle Hidden; if(-not $p.WaitForExit(15000)){Stop-Process $p -Force; throw 'application did not fail fast' }; if($p.ExitCode -eq 0){throw 'application started without isolated datasource'}; Write-Output 'PASS: missing RRC_V2 datasource variables fail startup' } finally { foreach($n in $names){[Environment]::SetEnvironmentVariable($n,$old[$n])} }
