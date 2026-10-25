#requires -Version 7.2
param([string]$PreviousImage, [string]$CandidateImage, [string]$BackupFile, [string]$ReportFile,
      [switch]$LoadTest, [switch]$WriteLoadTest)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'check-local-operations.ps1')

function Test-ImageOnlyRollback($Before, $After) {
    if (-not $Before.schemaHash -or -not $After.schemaHash -or
        @($Before.migrations).Count -eq 0 -or @($After.migrations).Count -eq 0) { return $false }
    return $Before.schemaHash -eq $After.schemaHash -and
        ($Before.migrations | ConvertTo-Json -Compress) -ceq ($After.migrations | ConvertTo-Json -Compress)
}

function Remove-DrillContainer([string]$ContainerId, [string]$RunId) {
    $owner = Invoke-Docker @('inspect', '--format', '{{index .Config.Labels "runrun.recovery.id"}}', $ContainerId)
    if ($owner.Trim() -ne $RunId) { throw 'Cleanup refused: container owner mismatch' }
    $null = Invoke-Docker @('rm', '-f', '-v', $ContainerId)
}

function Invoke-RecoveryDrill([string]$PreviousImage, [string]$CandidateImage, [string]$BackupFile, [string]$ReportFile,
                              [switch]$LoadTest, [switch]$WriteLoadTest) {
    if ($LoadTest -and $WriteLoadTest) { throw 'Select only one load mode per rehearsal' }
    if (-not $PreviousImage -or -not $CandidateImage) { throw 'PreviousImage and CandidateImage are required' }
    $backend = Split-Path -Parent $PSScriptRoot
    $adminDirectory = [IO.Path]::GetFullPath((Join-Path $backend '../RunRunCampusAdmin/dist'))
    if (-not (Test-Path -LiteralPath (Join-Path $adminDirectory 'index.html'))) { throw 'Local admin build is missing' }
    if (-not $BackupFile) {
        $latest = Get-ChildItem -LiteralPath (Join-Path $backend 'backups') -Filter 'runrun-campus-*.dump' -File |
            Where-Object Name -match '^runrun-campus-\d{8}T\d{6}Z\.dump$' | Sort-Object Name -Descending | Select-Object -First 1
        if ($null -eq $latest) { throw 'No completed backup found' }
        $BackupFile = $latest.FullName
    }
    $backup = Get-Item -LiteralPath $BackupFile
    if ($backup.PSIsContainer -or $backup.Length -eq 0 -or $backup.Extension -ne '.dump') { throw 'A nonempty .dump file is required' }
    $previousId = (Invoke-Docker @('image', 'inspect', '--format', '{{.Id}}', $PreviousImage)).Trim()
    $candidateId = (Invoke-Docker @('image', 'inspect', '--format', '{{.Id}}', $CandidateImage)).Trim()
    $postgresId = (Invoke-Docker @('image', 'inspect', '--format', '{{.Id}}', 'postgres:16-alpine')).Trim()
    if ($previousId -eq $candidateId) { throw 'Previous and candidate must be different image IDs' }
    $runId = [Guid]::NewGuid().ToString('N')
    if (-not $ReportFile) { $ReportFile = Join-Path $backend "target/recovery-$runId.json" }
    $ReportFile = [IO.Path]::GetFullPath($ReportFile)
    if (Test-Path -LiteralPath $ReportFile) { throw 'Report file already exists' }
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $ReportFile) -Force
    $report = [ordered]@{
        runId = $runId; startedAt = [DateTime]::UtcNow.ToString('o'); success = $false
        backup = $backup.Name; backupSha256 = (Get-FileHash -LiteralPath $backup.FullName -Algorithm SHA256).Hash
        previousImageId = $previousId; candidateImageId = $candidateId; postgresImageId = $postgresId
        adminBuild = $adminDirectory
        stage = 'prepare'; candidatePassed = $false; rollbackVerified = $false; cleanupComplete = $false
    }
    $containers = [Collections.Generic.List[string]]::new()
    $networkId = $null
    $dbId = $null
    $appId = $null
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $appLimits = if ($LoadTest -or $WriteLoadTest) { @('--cpus', '2', '--memory', '1g', '-e', 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60') } else { @() }
    $dbLimits = if ($LoadTest -or $WriteLoadTest) { @('--cpus', '1', '--memory', '1g') } else { @() }
    function Sql([string]$Database, [string]$Statement) {
        Invoke-Docker @('exec', $dbId, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'runrun', '-d', $Database, '-c', $Statement)
    }
    function Manifest([string]$Database) {
        $json = Invoke-Docker @('exec', $dbId, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'runrun', '-d', $Database, '-f', '/recovery-manifest.sql')
        $manifest = $json | ConvertFrom-Json
        foreach ($name in @('app_users', 'campus_orders', 'user_sessions', 'flyway_schema_history')) {
            if ($name -notin $manifest.tables.name) { throw 'Required table missing' }
        }
        if ($manifest.invalidConstraints -ne 0 -or @($manifest.migrations | Where-Object { -not $_.success }).Count -gt 0) {
            throw 'Database constraint or migration validation failed'
        }
        return $manifest
    }
    function Restore([string]$Database, [string]$Archive) {
        $null = Invoke-Docker @('exec', $dbId, 'pg_restore', '--single-transaction', '--exit-on-error', '--no-owner', '--no-acl',
            '-U', 'runrun', '-d', $Database, $Archive)
    }
    function Start-App([string]$ImageId, [string]$Database) {
        $id = (Invoke-Docker (@('run', '-d', '--pull=never', '--label', "runrun.recovery.id=$runId", '--network', $networkId,
            '--network-alias', 'api', '--read-only', '--tmpfs', '/tmp:rw,nosuid,size=128m',
            '--mount', "type=bind,source=$adminDirectory,target=/app/admin,readonly",
            '--tmpfs', '/app/uploads:rw,nosuid,uid=100,gid=101,size=32m', '--security-opt', 'no-new-privileges:true',
            '-e', 'SPRING_PROFILES_ACTIVE=dev', '-e', "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/$Database",
            '-e', 'SPRING_DATASOURCE_USERNAME=runrun', '-e', 'SPRING_DATASOURCE_PASSWORD=drill-only',
            '-e', 'APP_ADMIN_PHONE=', '-e', 'APP_ADMIN_PASSWORD=', '-e', 'PUSH_ENABLED=false',
            '-e', 'SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/admin/,classpath:/static/',
            '-e', 'PROOF_STORAGE_TYPE=local', '-e', 'PROOF_STORAGE_DIR=/app/uploads/order-proofs') + $appLimits + @($ImageId))).Trim()
        $containers.Add($id)
        return $id
    }
    function Wait-App([string]$ContainerId) {
        $watch = [Diagnostics.Stopwatch]::StartNew()
        while ($watch.Elapsed.TotalSeconds -lt 90) {
            if ((Invoke-Docker @('inspect', '--format', '{{.State.Running}}', $ContainerId)).Trim() -ne 'true') { throw 'Application exited' }
            try {
                $health = Invoke-Docker @('exec', $ContainerId, 'wget', '-q', '-T', '3', '-O', '-', 'http://127.0.0.1:8080/actuator/health')
                if (($health | ConvertFrom-Json).status -eq 'UP') { return }
            } catch { }
            Start-Sleep -Seconds 2
        }
        throw 'Application readiness timed out'
    }
    function Smoke([string]$Database) {
        $null = Invoke-Docker @('exec', $dbId, 'sh', '/recovery-smoke.sh', $Database)
    }
    function Stop-App {
        Remove-DrillContainer $appId $runId
        $null = $containers.Remove($appId)
    }
    try {
        $networkId = (Invoke-Docker @('network', 'create', '--internal', '--label', "runrun.recovery.id=$runId", "runrun-recovery-$runId")).Trim()
        $dbId = (Invoke-Docker (@('run', '-d', '--pull=never', '--label', "runrun.recovery.id=$runId", '--network', $networkId,
            '--network-alias', 'postgres', '--tmpfs', '/var/lib/postgresql/data:rw,nosuid,size=1g',
            '--tmpfs', '/tmp:rw,nosuid,size=128m', '-e', 'POSTGRES_USER=runrun', '-e', 'POSTGRES_DB=recovery',
            '-e', 'POSTGRES_HOST_AUTH_METHOD=trust') + $dbLimits + @($postgresId))).Trim()
        $containers.Add($dbId)
        $null = Invoke-Docker @('cp', $backup.FullName, "${dbId}:/source.dump")
        foreach ($file in @('recovery-manifest.sql', 'recovery-smoke.sh')) {
            $null = Invoke-Docker @('cp', (Join-Path $backend "deploy/$file"), "${dbId}:/$file")
        }
        $report.stage = 'restore'
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            try { $null = Sql 'recovery' 'SELECT 1'; $ready = $true; break } catch { Start-Sleep -Seconds 1 }
        }
        if (-not $ready) { throw 'Isolated database readiness timed out' }
        Restore 'recovery' '/source.dump'
        $report.stage = 'restore_manifest'
        $report.restored = Manifest 'recovery'
        $report.stage = 'baseline'
        $appId = Start-App $previousId 'recovery'
        Wait-App $appId
        Smoke 'recovery'
        Stop-App
        $before = Manifest 'recovery'
        $report.beforeUpgrade = $before
        $null = Invoke-Docker @('exec', $dbId, 'pg_dump', '-U', 'runrun', '-d', 'recovery', '--format=custom', '--file=/tmp/before-upgrade.dump')
        $report.stage = 'candidate'
        $appId = $null
        try {
            $appId = Start-App $candidateId 'recovery'
            Wait-App $appId
            Smoke 'recovery'
            if ($LoadTest -or $WriteLoadTest) {
                . (Join-Path $PSScriptRoot 'run-load-probe.ps1')
                $beforeLoad = Manifest 'recovery'
                $mode = if ($WriteLoadTest) { 'write' } else { 'read' }
                $report.loadTest = Invoke-LoadProbe $networkId $dbId $appId $runId ($ReportFile + '.load') -Mode $mode
                $afterLoad = Manifest 'recovery'
                if ($WriteLoadTest) {
                    # Sequence values may advance; all original rows, constraints and migrations must remain unchanged.
                    $report.writeExistingDataUnchanged = ($beforeLoad.tables | ConvertTo-Json -Depth 8 -Compress) -ceq
                        ($afterLoad.tables | ConvertTo-Json -Depth 8 -Compress) -and (Test-ImageOnlyRollback $beforeLoad $afterLoad)
                    if (-not $report.writeExistingDataUnchanged) { throw 'Write load changed original data or schema' }
                } else {
                    $report.loadDataUnchanged = ($beforeLoad | ConvertTo-Json -Depth 8 -Compress) -ceq
                        ($afterLoad | ConvertTo-Json -Depth 8 -Compress)
                    if (-not $report.loadDataUnchanged) { throw 'Read-only load changed database state' }
                }
                if (-not $report.loadTest.success) { throw 'Load test thresholds failed' }
            }
            $report.candidatePassed = $true
        } catch { $report.candidatePassed = $false; $report.candidateError = $_.Exception.Message }
        if ($appId) { Stop-App }
        try { $after = Manifest 'recovery' } catch { $after = $null }
        $report.afterUpgrade = $after
        $report.stage = 'rollback'
        if ($null -ne $after -and (Test-ImageOnlyRollback $before $after)) {
            $report.rollbackMode = 'image_only'
            $database = 'recovery'
            $expected = $after
        } else {
            $report.rollbackMode = 'image_and_pre_upgrade_snapshot'
            $database = 'rollback'
            $null = Sql 'postgres' 'CREATE DATABASE rollback TEMPLATE template0'
            Restore $database '/tmp/before-upgrade.dump'
            $expected = $before
        }
        $appId = Start-App $previousId $database
        Wait-App $appId
        Smoke $database
        Stop-App
        $actual = Manifest $database
        $report.afterRollback = $actual
        if (($expected | ConvertTo-Json -Depth 8 -Compress) -cne ($actual | ConvertTo-Json -Depth 8 -Compress)) { throw 'Rollback database manifest mismatch' }
        $report.rollbackVerified = $true
        $report.success = $report.candidatePassed
        $report.stage = if ($report.success) { 'complete' } else { 'candidate_failed_rollback_verified' }
    } catch {
        $report.success = $false
        $report.error = "Recovery rehearsal failed at stage: $($report.stage); $($_.Exception.Message)"
    } finally {
        $cleanupErrors = 0
        foreach ($id in $containers.ToArray()) {
            try { Remove-DrillContainer $id $runId } catch { $cleanupErrors++ }
        }
        if ($networkId) {
            try {
                $owner = (Invoke-Docker @('network', 'inspect', '--format', '{{index .Labels "runrun.recovery.id"}}', $networkId)).Trim()
                if ($owner -ne $runId) { throw 'Cleanup refused: network owner mismatch' }
                $null = Invoke-Docker @('network', 'rm', $networkId)
            } catch { $cleanupErrors++ }
        }
        $report.cleanupComplete = $cleanupErrors -eq 0
        $report.sourceBackupUnchanged = (Get-FileHash -LiteralPath $backup.FullName -Algorithm SHA256).Hash -eq $report.backupSha256
        if (-not $report.cleanupComplete -or -not $report.sourceBackupUnchanged) { $report.success = $false }
        $report.elapsedSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 1)
        $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $ReportFile -Encoding utf8
    }
    return [pscustomobject]$report
}

if ($MyInvocation.InvocationName -ne '.') {
    $result = Invoke-RecoveryDrill $PreviousImage $CandidateImage $BackupFile $ReportFile -LoadTest:$LoadTest -WriteLoadTest:$WriteLoadTest
    $result | Select-Object runId, success, stage, candidatePassed, rollbackMode, rollbackVerified, cleanupComplete, elapsedSeconds |
        ConvertTo-Json
    if (-not $result.success) { exit 1 }
    exit 0
}
