#requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('runrun-secret-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$generator = Join-Path $PSScriptRoot 'new-production-secrets.ps1'
$preparer = Join-Path $PSScriptRoot 'prepare-production-secret-rotation.ps1'
$failures = 0
$passed = 0

function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Expect-Failure([scriptblock]$Action) {
    $failed = $false
    try { & $Action *> $null } catch { $failed = $true }
    Assert $failed 'Expected operation to be rejected'
}

function Test-Case([string]$Name, [scriptblock]$Action) {
    try { & $Action; $script:passed++; Write-Host "PASS: $Name" }
    catch { $script:failures++; Write-Host "FAIL: $Name ($($_.Exception.Message))" }
}

function New-Source([string]$Name) {
    $path = Join-Path $testRoot $Name
    New-Item -ItemType Directory -Path $path | Out-Null
    [IO.File]::WriteAllText((Join-Path $path 'database-password.txt'), ('Db!9' + ('a' * 28)))
    [IO.File]::WriteAllText((Join-Path $path 'admin-password.txt'), 'Admin!9-fixture-only-not-a-real-password')
    return $path
}

function Assert-Restricted([string]$Path) {
    $allowed = @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value, 'S-1-5-18', 'S-1-5-32-544')
    foreach ($rule in (Get-Acl -LiteralPath $Path).Access) {
        if ($rule.AccessControlType -eq 'Allow') {
            Assert ($rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -in $allowed) 'Unexpected secret reader'
        }
    }
}

try {
    foreach ($scriptPath in @($generator, $preparer)) {
        $parseErrors = $null
        [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$null, [ref]$parseErrors) | Out-Null
        Assert ($parseErrors.Count -eq 0) 'Script missing or invalid; negative tests must not pass on syntax errors'
    }
    Test-Case 'Force cannot overwrite existing secrets' {
        $source = New-Source 'force'
        $before = (Get-FileHash -LiteralPath (Join-Path $source 'database-password.txt')).Hash
        Expect-Failure { & $generator -OutputDirectory $source -Force }
        Assert ((Get-FileHash -LiteralPath (Join-Path $source 'database-password.txt')).Hash -eq $before) 'Existing password changed'
    }
    Test-Case 'Partial existing pair cannot be overwritten or completed' {
        $source = New-Source 'partial'
        Remove-Item -LiteralPath (Join-Path $source 'admin-password.txt')
        Expect-Failure { & $generator -OutputDirectory $source }
        Assert (-not (Test-Path -LiteralPath (Join-Path $source 'admin-password.txt'))) 'Unexpected partial write'
    }
    Test-Case 'Generated secrets have valid format, private ACL and no output disclosure' {
        $output = Join-Path $testRoot 'generated'
        $log = (& $generator -OutputDirectory $output *>&1 | Out-String)
        $db = [IO.File]::ReadAllText((Join-Path $output 'database-password.txt'))
        $admin = [IO.File]::ReadAllText((Join-Path $output 'admin-password.txt'))
        Assert ($db -cmatch '^Db!9[0-9a-f]{28}$') 'Invalid database secret'
        Assert ($admin -cmatch '^Admin!9-[0-9a-f]{48}$') 'Invalid admin secret'
        Assert ($db -ne $admin) 'Reused secret'
        Assert (-not $log.Contains($db) -and -not $log.Contains($admin)) 'Secret leaked to output'
        Assert-Restricted $output
        Assert-Restricted (Join-Path $output 'database-password.txt')
    }
    Test-Case 'Rotation backs up originals, preserves sources and generates different candidates' {
        $source = New-Source 'rotate-source'
        $output = Join-Path $testRoot 'rotation'
        $originals = @{}
        foreach ($name in @('database-password.txt', 'admin-password.txt')) {
            $originals[$name] = [IO.File]::ReadAllText((Join-Path $source $name))
        }
        $log = (& $preparer -SourceDirectory $source -OutputDirectory $output *>&1 | Out-String)
        foreach ($name in $originals.Keys) {
            $candidate = [IO.File]::ReadAllText((Join-Path $output $name))
            Assert ([IO.File]::ReadAllText((Join-Path $source $name)) -ceq $originals[$name]) 'Live secret changed'
            Assert ([IO.File]::ReadAllText((Join-Path $output ('before\' + $name))) -ceq $originals[$name]) 'Backup mismatch'
            Assert ($candidate -cne $originals[$name]) 'Password was not rotated'
            Assert (-not $log.Contains($candidate) -and -not $log.Contains($originals[$name])) 'Secret leaked to output'
            Assert-Restricted (Join-Path $output ('before\' + $name))
        }
        $state = Get-Content -LiteralPath (Join-Path $output 'rotation.json') -Raw | ConvertFrom-Json
        Assert ($state.status -eq 'prepared_not_applied') 'Incorrect rotation state'
        Assert ($state.sourceDirectory -eq $source) 'Wrong source directory'
        Expect-Failure { & $preparer -SourceDirectory $source -OutputDirectory $output }
    }
    Test-Case 'Missing source secret fails before creating candidates' {
        $source = New-Source 'missing-source'
        Remove-Item -LiteralPath (Join-Path $source 'admin-password.txt')
        $output = Join-Path $testRoot 'missing-output'
        Expect-Failure { & $preparer -SourceDirectory $source -OutputDirectory $output }
        Assert (-not (Test-Path -LiteralPath $output)) 'Candidates created without complete source'
    }
    Test-Case 'Invalid source secret fails without leaking its content' {
        $source = New-Source 'invalid-source'
        [IO.File]::WriteAllText((Join-Path $source 'database-password.txt'), 'invalid-fixture-value')
        $output = Join-Path $testRoot 'invalid-output'
        Expect-Failure { & $preparer -SourceDirectory $source -OutputDirectory $output }
        Assert (-not (Test-Path -LiteralPath $output)) 'Candidates created from invalid source'
    }
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\runrun-secret-tests-'
    if (-not $resolved.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe test cleanup path' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
Write-Host "Tests: $($passed + $failures), Passed: $passed, Failed: $failures"
if ($failures -gt 0) { exit 1 }
