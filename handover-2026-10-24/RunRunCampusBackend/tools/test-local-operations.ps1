#requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'check-local-operations.ps1')

$passed = 0
$failed = 0
$scenario = ''
function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Invoke-Docker([string[]]$DockerArguments) {
    $command = $DockerArguments -join ' '
    if ($scenario -eq 'docker-down') { throw 'DOCKER_FAILED' }
    if ($command -match 'ps --all') {
        foreach ($service in @('api', 'postgres', 'backup')) {
            if ($scenario -eq 'missing-service' -and $service -eq 'backup') { continue }
            $state = if ($scenario -eq 'stopped-service' -and $service -eq 'api') { 'exited' } else { 'running' }
            $health = if ($scenario -eq 'unhealthy-service' -and $service -eq 'backup') { 'unhealthy' } else { 'healthy' }
            @{ Service = $service; State = $state; Health = $health } | ConvertTo-Json -Compress
        }
        return
    }
    if ($command -match 'psql') {
        if ($scenario -eq 'database-down') { throw 'DOCKER_FAILED' }
        return '1'
    }
    if ($command -match 'df -Pk') {
        if ($scenario -eq 'database-disk-full') {
            return "Filesystem 1024-blocks Used Available Capacity Mounted on`n/dev/sdd 100000000 99000000 1000000 99% /data"
        }
        return "Filesystem 1024-blocks Used Available Capacity Mounted on`n/dev/sdd 100000000 10000000 90000000 10% /data"
    }
    if ($command -match 'pg_restore') {
        Assert ($command -match '--file=/dev/null') 'Archive data must be read, not only its table of contents'
        Assert ($command -notmatch '--dbname|--clean') 'Check must not restore into a database'
        if ($scenario -eq 'corrupt-backup') { throw 'DOCKER_FAILED' }
        return ''
    }
    throw 'Unexpected Docker command'
}
function Invoke-WebRequest {
    param($Uri, $TimeoutSec, $MaximumRedirection)
    Assert ($TimeoutSec -gt 0 -and $MaximumRedirection -eq 0) 'HTTP check must be bounded and reject redirects'
    if ($scenario -eq 'api-down' -and $Uri -like '*/actuator/health') { throw 'offline' }
    if ($Uri -like '*/actuator/health') {
        $content = if ($scenario -eq 'api-down-body') { '{"status":"DOWN"}' } else { '{"status":"UP"}' }
        if ($scenario -eq 'healthy-byte-response') { $content = [Text.Encoding]::UTF8.GetBytes($content) }
    } else {
        $content = if ($scenario -eq 'wrong-admin-page') { '<html>proxy error</html>' } else { '<title>跑跑代取运营后台</title>' }
    }
    return [pscustomobject]@{ StatusCode = 200; Content = $content }
}
function Get-PSDrive {
    param($PSProvider)
    $free = if ($scenario -eq 'host-disk-full') { 1GB } else { 50GB }
    [pscustomobject]@{ Name = 'D'; Root = 'D:\'; Free = $free; Used = 50GB }
}
function Get-ChildItem {
    param($LiteralPath, $Filter, [switch]$File)
    if ($scenario -eq 'missing-backup') { return }
    $age = if ($scenario -eq 'stale-backup') { -30 } elseif ($scenario -eq 'future-backup') { 24 } else { -1 }
    $date = [DateTime]::UtcNow.AddHours($age)
    $length = if ($scenario -eq 'empty-backup') { 0 } else { 1000 }
    [pscustomobject]@{ Name = ('runrun-campus-' + $date.ToString('yyyyMMddTHHmmssZ') + '.dump'); Length = $length }
    # An unfinished dump must never make the check healthy.
    [pscustomobject]@{ Name = ('runrun-campus-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '.dump.tmp'); Length = 1000 }
}

$cases = [ordered]@{
    healthy = $null
    'healthy-byte-response' = $null
    'docker-down' = 'containers'
    'missing-service' = 'containers'
    'stopped-service' = 'containers'
    'unhealthy-service' = 'containers'
    'api-down' = 'api'
    'api-down-body' = 'api'
    'wrong-admin-page' = 'admin'
    'database-down' = 'database'
    'host-disk-full' = 'host_disk'
    'database-disk-full' = 'database_disk'
    'missing-backup' = 'backup'
    'stale-backup' = 'backup'
    'future-backup' = 'backup'
    'empty-backup' = 'backup'
    'corrupt-backup' = 'backup'
}
foreach ($case in $cases.GetEnumerator()) {
    $scenario = $case.Key
    try {
        $report = Test-LocalOperations
        Assert ($report.checks.Count -eq 7) 'Every check must produce a result even after a failure'
        if ($null -eq $case.Value) {
            Assert $report.healthy 'Healthy fixtures should pass'
        } else {
            Assert (-not $report.healthy) 'Failure was reported healthy'
            $check = @($report.checks | Where-Object name -eq $case.Value)
            Assert ($check.Count -eq 1 -and -not $check[0].healthy) 'Wrong failure classification'
        }
        $passed++
        Write-Host "PASS: $scenario"
    } catch {
        $failed++
        Write-Host "FAIL: $scenario ($($_.Exception.Message))"
    }
}
Write-Host "Tests: $($passed + $failed), Passed: $passed, Failed: $failed"
if ($failed -gt 0) { exit 1 }
