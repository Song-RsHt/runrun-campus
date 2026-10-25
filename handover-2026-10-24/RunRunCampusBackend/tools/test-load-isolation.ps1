#requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'run-load-probe.ps1')
$passed = 0
foreach ($mode in @('read', 'write')) {
foreach ($case in @('foreign-container', 'public-network', 'foreign-network')) {
    function Invoke-Docker([string[]]$DockerArguments) {
        if ($DockerArguments[0] -eq 'inspect') {
            if ($case -eq 'foreign-container') { return 'someone-else' }
            return 'fixture-run'
        }
        if ($DockerArguments[0] -eq 'network') {
            $owner = if ($case -eq 'foreign-network') { 'someone-else' } else { 'fixture-run' }
            return (@{Internal=($case -ne 'public-network'); Labels=@{'runrun.recovery.id'=$owner}} | ConvertTo-Json -Compress)
        }
        throw 'Test attempted a Docker mutation'
    }
    $rejected = $false
    try { Invoke-LoadProbe 'network' 'db' 'app' 'fixture-run' 'unused-output' -Mode $mode | Out-Null }
    catch { $rejected = $_.Exception.Message -match 'not owned|requires this rehearsal internal network' }
    if (-not $rejected) { throw "Isolation case failed: $case" }
    $passed++
    Write-Host "PASS: $mode $case"
}
}
Write-Host "Isolation tests: $passed passed"
