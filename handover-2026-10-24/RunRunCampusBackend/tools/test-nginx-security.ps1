#requires -Version 7.2
param([Parameter(Mandatory)][string]$ReportDirectory)
$ErrorActionPreference = 'Stop'
$backend = Split-Path -Parent $PSScriptRoot
$admin = Join-Path (Split-Path -Parent $backend) 'RunRunCampusAdmin'
$runtime = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node'
$node = Join-Path $runtime 'bin/node.exe'
$oldNodePath = $env:NODE_PATH
$env:NODE_PATH = Join-Path $runtime 'node_modules'
$report = [IO.Path]::GetFullPath($ReportDirectory)
if (Test-Path -LiteralPath $report) { throw 'Choose a new report directory' }
$null = New-Item -ItemType Directory -Path $report
$name = 'runrun-nginx-security-qa'
$owner = [guid]::NewGuid().ToString()
$created = $false
$result = [ordered]@{success=$false; cleanupComplete=$false}
function Invoke-QaDocker([string[]]$Arguments) {
    $output = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: $($Arguments[0])" }
    return $output
}
$before = Invoke-QaDocker @('ps','--format','{{.ID}}|{{.Names}}')
$before | Set-Content (Join-Path $report 'live-before.txt')
Push-Location $admin
try {
    & $node node_modules/typescript/bin/tsc -b
    if ($LASTEXITCODE -ne 0) { throw 'TypeScript check failed' }
    & $node node_modules/vite/bin/vite.js build --outDir (Join-Path $report 'admin-dist') *> (Join-Path $report 'frontend-build.log')
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    & docker build -t runrun-nginx-security-qa -f (Join-Path $backend 'deploy/tests/nginx-security.Dockerfile') (Join-Path $backend 'deploy/tests') *> (Join-Path $report 'image-build.log')
    if ($LASTEXITCODE -ne 0) { throw 'QA image build failed' }
    $result.imageId = (Invoke-QaDocker @('image','inspect','runrun-nginx-security-qa','--format','{{.Id}}')).Trim()
    $deploy = (Join-Path $backend 'deploy').Replace('\','/')
    $build = (Join-Path $report 'admin-dist').Replace('\','/')
    Invoke-QaDocker @('run','-d','--name',$name,'--label',"runrun.nginxqa=$owner",'-p','127.0.0.1:28443:8443',
        '--mount',"type=bind,source=$deploy,target=/source,readonly",'--mount',"type=bind,source=$build,target=/admin,readonly",
        'runrun-nginx-security-qa','sh','/source/tests/nginx-security-start.sh') | Out-Null
    $created = $true
    $ready = $false
    for ($attempt=0; $attempt -lt 15; $attempt++) {
        try { $ready = (Invoke-WebRequest 'https://127.0.0.1:28443' -SkipCertificateCheck -TimeoutSec 2).StatusCode -eq 200 } catch {}
        if ($ready) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'QA Nginx did not become ready' }
    & docker exec $name sh /source/tests/nginx-security-test.sh *> (Join-Path $report 'security-tests.log')
    $result.securityTestsPassed = $LASTEXITCODE -eq 0
    & $node (Join-Path $PSScriptRoot 'test-nginx-browser.cjs') $report *> (Join-Path $report 'browser-tests.log')
    $result.browserTestsPassed = $LASTEXITCODE -eq 0
    $result.success = $result.securityTestsPassed -and $result.browserTestsPassed
} catch {
    $result.error = $_.Exception.Message
} finally {
    try {
        if ($created) {
            Invoke-QaDocker @('logs',$name) *> (Join-Path $report 'nginx.log')
            $actualOwner = (Invoke-QaDocker @('inspect','-f','{{index .Config.Labels "runrun.nginxqa"}}',$name)).Trim()
            if ($actualOwner -ne $owner) { throw 'QA container ownership mismatch' }
            Invoke-QaDocker @('rm','-f','-v',$name) | Out-Null
        }
        $after = Invoke-QaDocker @('ps','--format','{{.ID}}|{{.Names}}')
        $after | Set-Content (Join-Path $report 'live-after.txt')
        if (Compare-Object $before $after) { throw 'Live container inventory changed' }
        $result.cleanupComplete = $true
    } catch { $result.success=$false; $result.cleanupError=$_.Exception.Message }
    Pop-Location
    $env:NODE_PATH = $oldNodePath
    $result | ConvertTo-Json | Set-Content (Join-Path $report 'run-summary.json')
}
Get-Content (Join-Path $report 'run-summary.json')
if (-not $result.success) { exit 1 }
