param()
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$name = 'rrc-m00-' + [guid]::NewGuid().ToString('N').Substring(0,12)
function Invoke-Docker([string[]]$Arguments) {
    $result = & docker.exe @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Docker operation failed; credentials and raw output suppressed.' }
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
function Assert([bool]$Condition, [string]$Label) {
    if (-not $Condition) { throw "FAIL: $Label" }
    Write-Output "PASS: $Label"
}
$up = Get-Content -Raw (Join-Path $root 'server/db/migrations/up/rrc_0001__create_v2_schema.sql')
$down = Get-Content -Raw (Join-Path $root 'server/db/migrations/down/rrc_0001__create_v2_schema.rollback.sql')
try {
    # Trust authentication exists only inside this disposable container: no ports, host mounts or persistent volumes.
    $null = Invoke-Docker @('run','--detach','--name',$name,'--network','none','--label','runrun.module=M00','--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine')
    $ready = $false
    for ($i=0; $i -lt 40; $i++) {
        & docker.exe exec $name pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Milliseconds 500
    }
    Assert $ready 'temporary PostgreSQL ready'
    $null=Sql postgres 'CREATE DATABASE rrc_m00; CREATE DATABASE rrc_legacy; CREATE DATABASE rrc_restore;'
    $null=Sql rrc_m00 $up
    Assert ((Sql rrc_m00 "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '1') 'empty initialization'
    $null=Sql rrc_m00 $up -MustFail
    Write-Output 'PASS: repeated initialization rejected'
    $null=Sql rrc_legacy 'CREATE TABLE public.flyway_schema_history(version text); CREATE TABLE public.campus_orders(id bigint);'
    $null=Sql rrc_legacy $up -MustFail
    Assert ((Sql rrc_legacy "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'legacy sentinels rejected without changes'
    Assert ((Sql rrc_legacy "SELECT count(*) FROM pg_class WHERE oid IN ('public.flyway_schema_history'::regclass,'public.campus_orders'::regclass)") -eq '2') 'legacy sentinels preserved'
    $null=Sql postgres 'CREATE DATABASE rrc_enum; CREATE DATABASE rrc_other;'
    $null=Sql rrc_enum "CREATE TYPE public.test_enum AS ENUM ('SYNTHETIC');"
    $null=Sql rrc_enum $up -MustFail
    Assert ((Sql rrc_enum "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'public enum rejected atomically'
    $null=Sql rrc_other 'CREATE SCHEMA pgx_synthetic;'
    $null=Sql rrc_other $up -MustFail
    Assert ((Sql rrc_other "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'other schema rejected atomically'
    # Synthetic fixture is test-only; not an M00 application table.
    $null=Sql rrc_m00 "CREATE TABLE runrun_v2.m00_synthetic(id bigint PRIMARY KEY, note text NOT NULL); INSERT INTO runrun_v2.m00_synthetic VALUES(1,'synthetic-only');"
    $null=Sql rrc_m00 $down -MustFail
    Assert ((Sql rrc_m00 'SELECT count(*) FROM runrun_v2.m00_synthetic') -eq '1') 'nonempty rollback rejected atomically'
    $null=Invoke-Docker @('exec',$name,'pg_dump','-U','postgres','-d','rrc_m00','-Fc','-f','/tmp/m00.dump')
    $null=Invoke-Docker @('exec',$name,'pg_restore','-U','postgres','-d','rrc_restore','--exit-on-error','/tmp/m00.dump')
    Assert ((Sql rrc_restore "SELECT count(*) FROM pg_constraint WHERE conrelid='runrun_v2.m00_synthetic'::regclass AND contype='p'") -eq '1') 'restored primary key'
    Assert ((Sql rrc_restore 'SELECT id || chr(58) || note FROM runrun_v2.m00_synthetic') -eq '1:synthetic-only') 'restored synthetic row'
    $null=Sql rrc_m00 'DROP TABLE runrun_v2.m00_synthetic;'
    $null=Sql rrc_m00 $down
    Assert ((Sql rrc_m00 "SELECT count(*) FROM pg_namespace WHERE nspname='runrun_v2'") -eq '0') 'empty rollback'
    $null=Sql rrc_m00 $up
    Write-Output 'PASS: reinitialize after rollback'
} finally {
    # Remove only this invocation's explicitly named disposable container and anonymous volume.
    & docker.exe rm -f -v $name *> $null
    if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $name" }
}
