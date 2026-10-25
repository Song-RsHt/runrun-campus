#requires -Version 7.2
# Invoked by the recovery rehearsal after its candidate becomes healthy.
function Test-WriteAudit($Audit, $Metrics) {
    try {
        $expected = @{
            orders = $Metrics.write_orders_created.values.count
            accepted = $Metrics.write_orders_accepted.values.count
            couponOrders = $Metrics.write_coupon_orders.values.count
            membershipOrders = $Metrics.write_membership_orders.values.count
        }
        foreach ($name in $expected.Keys) {
            if ($null -eq $expected[$name] -or $expected[$name] -le 0 -or [long]$expected[$name] -ne $expected[$name] -or
                $Audit.$name -ne $expected[$name]) { return $false }
        }
        foreach ($name in @('invalidOrders', 'duplicateKeys', 'invalidCoupons', 'invalidMemberships')) {
            if ($null -eq $Audit.$name -or $Audit.$name -ne 0) { return $false }
        }
        return $Audit.creationEvents -eq $Audit.orders -and $Audit.acceptEvents -eq $Audit.accepted -and
            $Audit.acceptMessages -eq $Audit.accepted -and $Audit.usedCoupons -eq $Audit.couponOrders -and
            $Audit.membershipTripsConsumed -eq $Audit.membershipOrders
    } catch { return $false }
}

function Invoke-LoadProbe([string]$NetworkId, [string]$DbId, [string]$AppId, [string]$RunId, [string]$ReportDirectory,
                          [ValidateSet('read', 'write')][string]$Mode = 'read') {
    foreach ($id in @($DbId, $AppId)) {
        $owner = (Invoke-Docker @('inspect', '--format', '{{index .Config.Labels "runrun.recovery.id"}}', $id)).Trim()
        if ($owner -ne $RunId) { throw 'Load test refused: container is not owned by this rehearsal' }
    }
    $network = Invoke-Docker @('network', 'inspect', '--format', '{{json .}}', $NetworkId) | ConvertFrom-Json
    if (-not $network.Internal -or $network.Labels.'runrun.recovery.id' -ne $RunId) { throw 'Load test requires this rehearsal internal network' }
    if (Test-Path -LiteralPath $ReportDirectory) { throw 'Load report directory already exists' }
    $null = New-Item -ItemType Directory -Path $ReportDirectory
    $k6Id = (Invoke-Docker @('image', 'inspect', '--format', '{{.Id}}', 'grafana/k6:2.2.0')).Trim()
    $deploy = Join-Path (Split-Path -Parent $PSScriptRoot) 'deploy'
    $samples = [Collections.Generic.List[object]]::new()
    $runner = $null
    $fixturesCreated = $false
    $result = [ordered]@{ success = $false; mode = $Mode; k6ImageId = $k6Id; k6ExitCode = $null
        samples = 0; sessionsRemoved = $false; runnerRemoved = $false; fixturesRemoved = $false }
    function Load-Sql([string]$Statement) {
        Invoke-Docker @('exec', $DbId, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'runrun', '-d', 'recovery', '-c', $Statement)
    }
    try {
        if ($Mode -eq 'write') {
            $null = Load-Sql (Get-Content -LiteralPath (Join-Path $deploy 'order-write-fixtures.sql') -Raw)
            $fixturesCreated = $true
        } else {
        $null = Load-Sql @'
INSERT INTO user_sessions(id, expires_at, token, user_id)
SELECT -1000-i, now()+interval '10 minutes', 'load-probe-session-' || i, admin.id
FROM generate_series(0,19) i CROSS JOIN
(SELECT id FROM app_users WHERE role='ADMIN' AND admin_role='SUPER_ADMIN' AND enabled ORDER BY id LIMIT 1) admin
'@
        if ((Load-Sql "SELECT count(*) FROM user_sessions WHERE id BETWEEN -1019 AND -1000").Trim() -ne '20') { throw 'Load sessions were not created' }
        }
        $scriptName = if ($Mode -eq 'write') { 'order-write-test.js' } else { 'load-test.js' }
        $runner = (Invoke-Docker @('run', '-d', '--pull=never', '--label', "runrun.recovery.id=$RunId", '--network', $NetworkId,
            '--cpus', '1', '--memory', '256m', '--read-only', '--tmpfs', '/tmp:rw,size=32m', '--cap-drop', 'ALL',
            '--security-opt', 'no-new-privileges:true', '--mount', "type=bind,source=$deploy,target=/scripts,readonly",
            '--mount', "type=bind,source=$ReportDirectory,target=/results", $k6Id,
            'run', '--quiet', '--no-usage-report', "/scripts/$scriptName")).Trim()
        $clock = [Diagnostics.Stopwatch]::StartNew()
        $nextProgress = 20
        while ((Invoke-Docker @('inspect', '--format', '{{.State.Running}}', $runner)).Trim() -eq 'true') {
            $deadline = if ($Mode -eq 'write') { 210 } else { 150 }
            if ($clock.Elapsed.TotalSeconds -gt $deadline) { throw 'Load generator deadline exceeded' }
            $stats = Invoke-Docker @('stats', '--no-stream', '--format', '{{json .}}', $AppId, $DbId, $runner)
            foreach ($line in ($stats -split '\r?\n' | Where-Object { $_.Trim() })) {
                $stat = $line | ConvertFrom-Json
                $service = if ($AppId.StartsWith($stat.ID)) { 'api' } elseif ($DbId.StartsWith($stat.ID)) { 'database' } else { 'generator' }
                $samples.Add([pscustomobject]@{ seconds = [math]::Round($clock.Elapsed.TotalSeconds, 1); service = $service
                    cpuPercent = $stat.CPUPerc; memory = $stat.MemUsage; memoryPercent = $stat.MemPerc; pids = $stat.PIDs })
            }
            if ($clock.Elapsed.TotalSeconds -ge $nextProgress) {
                Write-Host "Load test running: $([int]$clock.Elapsed.TotalSeconds) seconds"
                $nextProgress += 20
            }
            Start-Sleep -Seconds 3
        }
        $result.k6ExitCode = [int](Invoke-Docker @('inspect', '--format', '{{.State.ExitCode}}', $runner)).Trim()
        $summary = Get-Content -LiteralPath (Join-Path $ReportDirectory 'k6-summary.json') -Raw | ConvertFrom-Json
        $result.success = $result.k6ExitCode -eq 0 -and $summary.metrics.probe_requests.values.count -gt 0 -and $samples.Count -gt 0
        if ($Mode -eq 'write') {
            $audit = Load-Sql (Get-Content -LiteralPath (Join-Path $deploy 'order-write-audit.sql') -Raw) | ConvertFrom-Json
            $audit | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ReportDirectory 'database-audit.json') -Encoding utf8
            $result.dataVerified = Test-WriteAudit $audit $summary.metrics
            $result.success = $result.success -and $result.dataVerified
        }
    } finally {
        if ($runner) {
            try {
                Remove-DrillContainer $runner $RunId
                $result.runnerRemoved = $true
            } catch { $result.success = $false }
        }
        try {
            if ($Mode -eq 'write') {
                if ($fixturesCreated) { $null = Load-Sql (Get-Content -LiteralPath (Join-Path $deploy 'order-write-cleanup.sql') -Raw) }
                $result.sessionsRemoved = (Load-Sql "SELECT count(*) FROM user_sessions WHERE token LIKE 'write-probe-%'").Trim() -eq '0'
                $result.fixturesRemoved = $fixturesCreated -and $result.sessionsRemoved -and
                    (Load-Sql 'SELECT count(*) FROM app_users WHERE id BETWEEN -3039 AND -3000 OR id BETWEEN -4039 AND -4000').Trim() -eq '0'
            } else {
                $null = Load-Sql "DELETE FROM user_sessions WHERE id BETWEEN -1019 AND -1000 AND token = 'load-probe-session-' || (-id-1000)"
                $result.sessionsRemoved = (Load-Sql "SELECT count(*) FROM user_sessions WHERE id BETWEEN -1019 AND -1000").Trim() -eq '0'
                $result.fixturesRemoved = $result.sessionsRemoved
            }
        } catch { $result.success = $false }
        if (-not $result.fixturesRemoved -or -not $result.runnerRemoved) {
            $result.success = $false
        }
        $result.samples = $samples.Count
        $samples.ToArray() | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $ReportDirectory 'resources.json') -Encoding utf8
        $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ReportDirectory 'result.json') -Encoding utf8
    }
    return [pscustomobject]$result
}
