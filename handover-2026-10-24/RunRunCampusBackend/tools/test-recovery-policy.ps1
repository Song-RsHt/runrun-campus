#requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'rehearse-recovery.ps1')
$passed = 0
$failed = 0
function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Test-Case([string]$Name, [scriptblock]$Action) {
    try { & $Action; $script:passed++; Write-Host "PASS: $Name" }
    catch { $script:failed++; Write-Host "FAIL: $Name ($($_.Exception.Message))" }
}
function Manifest([string]$Schema, [string]$Version, [int]$Checksum = 123) {
    [pscustomobject]@{ schemaHash = $Schema; migrations = @([pscustomobject]@{version=$Version; checksum=$Checksum; success=$true}) }
}
Test-Case 'unchanged schema and migrations allow image-only rehearsal' {
    Assert (Test-ImageOnlyRollback (Manifest 'abc' '15') (Manifest 'abc' '15')) 'Expected compatibility'
}
Test-Case 'schema change requires pre-upgrade snapshot' {
    Assert (-not (Test-ImageOnlyRollback (Manifest 'abc' '15') (Manifest 'def' '15'))) 'Schema drift accepted'
}
Test-Case 'data-only migration also requires pre-upgrade snapshot' {
    Assert (-not (Test-ImageOnlyRollback (Manifest 'abc' '15') (Manifest 'abc' '16'))) 'New migration accepted'
}
Test-Case 'changed migration checksum requires pre-upgrade snapshot' {
    Assert (-not (Test-ImageOnlyRollback (Manifest 'abc' '15') (Manifest 'abc' '15' 456))) 'Changed checksum accepted'
}
Test-Case 'missing migration evidence never permits image-only rollback' {
    $rejected = $false
    try { $rejected = -not (Test-ImageOnlyRollback (Manifest 'abc' '15') ([pscustomobject]@{schemaHash='abc'})) } catch { $rejected = $true }
    Assert $rejected 'Missing evidence accepted'
}
Test-Case 'empty migration evidence never permits image-only rollback' {
    $empty = [pscustomobject]@{schemaHash='abc'; migrations=@()}
    Assert (-not (Test-ImageOnlyRollback $empty $empty)) 'Empty evidence accepted'
}
Test-Case 'cleanup rejects resources with another owner label' {
    function Invoke-Docker([string[]]$DockerArguments) {
        if ($DockerArguments[0] -eq 'inspect') { return 'other-run' }
        throw 'Deletion must not be attempted'
    }
    $rejected = $false
    try { Remove-DrillContainer 'fixture-id' 'this-run' } catch { $rejected = $true }
    Assert $rejected 'Cleanup accepted foreign resource'
}
Test-Case 'cleanup only deletes the verified container ID' {
    $script:deleted = $false
    function Invoke-Docker([string[]]$DockerArguments) {
        if ($DockerArguments[0] -eq 'inspect') { return 'this-run' }
        Assert (($DockerArguments -join ' ') -eq 'rm -f -v fixture-id') 'Wrong cleanup target'
        $script:deleted = $true
    }
    Remove-DrillContainer 'fixture-id' 'this-run'
    Assert $script:deleted 'Owned resource not removed'
}
Test-Case 'read and write load modes cannot run in one rehearsal' {
    $rejected = $false
    try { Invoke-RecoveryDrill -LoadTest -WriteLoadTest | Out-Null }
    catch { $rejected = $_.Exception.Message -eq 'Select only one load mode per rehearsal' }
    Assert $rejected 'Conflicting load modes accepted'
}
Write-Host "Tests: $($passed + $failed), Passed: $passed, Failed: $failed"
if ($failed -gt 0) { exit 1 }
