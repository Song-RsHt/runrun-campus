#requires -Version 7.2
param([Parameter(Mandatory)][string]$ReportDirectory,
      [string]$BackendImage = 'runrun-campus-backend:admin-mfa-20260903')
$ErrorActionPreference = 'Stop'
$admin = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node'
$node = Join-Path $runtime 'bin/node.exe'
$env:NODE_PATH = Join-Path $runtime 'node_modules'
$report = [IO.Path]::GetFullPath($ReportDirectory)
if (Test-Path -LiteralPath $report) { throw 'Choose a new report directory' }
$null = New-Item -ItemType Directory -Path $report
function Invoke-QaDocker([string[]]$Arguments) {
    $output = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: $($Arguments[0])" }
    return $output
}
$api = 'runrun-adminqa-api-20260903'
$db = 'runrun-adminqa-db-20260903'
$network = 'runrun-adminqa-20260903'
$created = @()
$networkCreated = $false
$before = Invoke-QaDocker @('ps','--format','{{.ID}}|{{.Names}}')
$before | Set-Content (Join-Path $report 'live-before.txt')
$result = [ordered]@{ success=$false; cleanupComplete=$false }
Push-Location $admin
try {
    & $node node_modules/typescript/bin/tsc -b
    if ($LASTEXITCODE -ne 0) { throw 'TypeScript check failed' }
    & $node node_modules/vite/bin/vite.js build --outDir (Join-Path $report 'admin-dist') *> (Join-Path $report 'frontend-build.log')
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    $result.imageId = (Invoke-QaDocker @('image','inspect',$BackendImage,'--format','{{.Id}}')).Trim()
    Invoke-QaDocker @('network','create','--label','runrun.adminqa=20260903',$network) | Out-Null
    $networkCreated = $true
    Invoke-QaDocker @('run','-d','--name',$db,'--label','runrun.adminqa=20260903','--network',$network,'--network-alias','db',
        '-e','POSTGRES_DB=qa','-e','POSTGRES_USER=qa','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:16-alpine') | Out-Null
    $created += $db
    $build = (Join-Path $report 'admin-dist').Replace('\','/')
    $mfaKeyFile = (Join-Path $report 'synthetic-mfa-key').Replace('\','/')
    [IO.File]::WriteAllText($mfaKeyFile, 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=')
    Invoke-QaDocker @('run','-d','--name',$api,'--label','runrun.adminqa=20260903','--network',$network,'-p','127.0.0.1:28180:8080',
        '--mount',"type=bind,source=$mfaKeyFile,target=/run/secrets/ADMIN_MFA_KEY,readonly",'-e','SPRING_CONFIG_IMPORT=configtree:/run/secrets/',
        '--mount',"type=bind,source=$build,target=/app/admin,readonly",'-e','SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/admin/',
        '-e','SPRING_PROFILES_ACTIVE=dev','-e','SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/qa',
        '-e','SPRING_DATASOURCE_USERNAME=qa','-e','SPRING_DATASOURCE_PASSWORD=qa','-e','APP_PUSH_ENABLED=false',
        '-e','APP_ADMIN_PHONE=19903000000','-e','APP_ADMIN_PASSWORD=AdminRoleQA2026','-e','APP_ADMIN_DISPLAY_NAME=QA_SUPER_ADMIN',
        $BackendImage) | Out-Null
    $created += $api
    $healthy = $false
    for ($attempt=0; $attempt -lt 25; $attempt++) {
        try { $healthy = (Invoke-RestMethod 'http://127.0.0.1:28180/actuator/health' -TimeoutSec 2).status -eq 'UP' } catch {}
        if ($healthy) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $healthy) { throw 'Isolated API did not become healthy' }
    & $node tools/admin-role-qa.cjs $report *> (Join-Path $report 'browser-test.log')
    if ($LASTEXITCODE -ne 0) { throw 'Browser acceptance failed; see browser-test.log' }
    $result.success = $true
} catch {
    $result.error = $_.Exception.Message
} finally {
    try {
        foreach ($name in ($created | Sort-Object)) {
            $owner = (Invoke-QaDocker @('inspect','-f','{{index .Config.Labels "runrun.adminqa"}}',$name)).Trim()
            if ($owner -ne '20260903') { throw 'Container ownership mismatch' }
            Invoke-QaDocker @('rm','-f','-v',$name) | Out-Null
        }
        if ($networkCreated) {
            $owner = (Invoke-QaDocker @('network','inspect','-f','{{index .Labels "runrun.adminqa"}}',$network)).Trim()
            if ($owner -ne '20260903') { throw 'Network ownership mismatch' }
            Invoke-QaDocker @('network','rm',$network) | Out-Null
        }
        $after = Invoke-QaDocker @('ps','--format','{{.ID}}|{{.Names}}')
        $after | Set-Content (Join-Path $report 'live-after.txt')
        if (Compare-Object $before $after) { throw 'Live container inventory changed' }
        $result.cleanupComplete = $true
    } catch { $result.success=$false; $result.cleanupError=$_.Exception.Message }
    Pop-Location
    $result | ConvertTo-Json | Set-Content (Join-Path $report 'run-summary.json')
}
Get-Content (Join-Path $report 'run-summary.json')
if (-not $result.success) { exit 1 }

