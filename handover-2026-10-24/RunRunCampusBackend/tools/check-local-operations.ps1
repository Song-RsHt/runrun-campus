#requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Docker([string[]]$DockerArguments) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new('docker')
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $DockerArguments) { $startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(15000)) {
            $process.Kill($true)
            throw 'Docker check timed out'
        }
        if ($process.ExitCode -ne 0) { throw 'Docker check failed' }
        return $stdout.GetAwaiter().GetResult()
    } finally {
        $process.Dispose()
    }
}

function Test-LocalOperations {
    $backendDirectory = Split-Path -Parent $PSScriptRoot
    $composeArguments = @('compose', '--project-directory', $backendDirectory,
        '-f', (Join-Path $backendDirectory 'compose.yml'))
    $checks = [Collections.Generic.List[object]]::new()
    function Add-Check([string]$Name, [string]$FailureMessage, [scriptblock]$Action) {
        try {
            $message = & $Action
            $checks.Add([pscustomobject]@{ name = $Name; healthy = $true; message = $message })
        } catch {
            $checks.Add([pscustomobject]@{ name = $Name; healthy = $false; message = $FailureMessage })
        }
    }

    Add-Check 'containers' 'Docker 不可用，或 API、数据库、备份容器未全部就绪。' {
        $output = Invoke-Docker ($composeArguments + @('ps', '--all', '--format', 'json'))
        $containers = @($output -split '\r?\n' | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
        foreach ($service in @('api', 'postgres', 'backup')) {
            $container = @($containers | Where-Object Service -eq $service)
            if ($container.Count -ne 1 -or $container[0].State -ne 'running' -or $container[0].Health -ne 'healthy') {
                throw 'Container not ready'
            }
        }
        'API、数据库、备份容器均运行且健康。'
    }
    Add-Check 'api' 'API 健康接口无响应或状态不是 UP。' {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/actuator/health' -TimeoutSec 5 -MaximumRedirection 0
        $content = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { $response.Content }
        if ($response.StatusCode -ne 200 -or ($content | ConvertFrom-Json).status -ne 'UP') { throw 'API unhealthy' }
        'API 健康接口返回 UP。'
    }
    Add-Check 'admin' '管理后台无响应或返回了错误页面。' {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -TimeoutSec 5 -MaximumRedirection 0
        if ($response.StatusCode -ne 200 -or $response.Content -notmatch '<title>跑跑代取运营后台</title>') { throw 'Admin unavailable' }
        '管理后台页面返回 HTTP 200。'
    }
    Add-Check 'database' '本地 PostgreSQL 无法执行只读查询。' {
        $result = Invoke-Docker ($composeArguments + @('exec', '-T', 'postgres', 'psql', '-U', 'runrun',
            '-d', 'runrun_campus', '-Atqc', 'SELECT 1'))
        if ($result.Trim() -ne '1') { throw 'Database query failed' }
        'PostgreSQL SELECT 1 通过。'
    }
    Add-Check 'host_disk' '系统盘或工程盘剩余空间不足 5 GiB / 10%，或无法读取容量。' {
        $roots = @([IO.Path]::GetPathRoot($backendDirectory), "$env:SystemDrive\")
        $drives = @(Get-PSDrive -PSProvider FileSystem | Where-Object Root -in $roots | Sort-Object Root -Unique)
        if ($drives.Count -eq 0) { throw 'Disk information unavailable' }
        foreach ($drive in $drives) {
            if ($drive.Free -lt 5GB -or $drive.Free / ($drive.Free + $drive.Used) -lt 0.1) { throw 'Low disk space' }
        }
        ($drives | ForEach-Object { "$($_.Name): 剩余 $([math]::Round($_.Free / 1GB, 1)) GiB" }) -join '；'
    }
    Add-Check 'database_disk' '数据库容器所在文件系统剩余空间不足 5 GiB / 10%，或无法读取容量。' {
        $output = Invoke-Docker ($composeArguments + @('exec', '-T', 'postgres', 'df', '-Pk', '/var/lib/postgresql/data'))
        $line = @($output -split '\r?\n' | Where-Object { $_.Trim() })[-1]
        $columns = $line.Trim() -split '\s+'
        $total = [double]$columns[1] * 1KB
        $free = [double]$columns[3] * 1KB
        if ($free -lt 5GB -or $free / $total -lt 0.1) { throw 'Low database disk space' }
        "数据库文件系统剩余 $([math]::Round($free / 1GB, 1)) GiB。"
    }
    Add-Check 'backup' '最新备份缺失、为空、超过 26 小时、时间异常或无法完整读取。' {
        $latest = Get-ChildItem -LiteralPath (Join-Path $backendDirectory 'backups') -Filter 'runrun-campus-*.dump' -File |
            Where-Object Name -match '^runrun-campus-\d{8}T\d{6}Z\.dump$' | Sort-Object Name -Descending | Select-Object -First 1
        if ($null -eq $latest -or $latest.Length -eq 0) { throw 'Backup missing or empty' }
        $timestamp = [DateTime]::ParseExact($latest.Name.Substring(14, 16), 'yyyyMMddTHHmmssZ',
            [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AssumeUniversal).ToUniversalTime()
        $age = [DateTime]::UtcNow - $timestamp
        if ($age.TotalHours -gt 26 -or $age.TotalMinutes -lt -5) { throw 'Backup timestamp invalid' }
        # Read schema and data without connecting to or modifying a database.
        $null = Invoke-Docker ($composeArguments + @('exec', '-T', 'backup', 'pg_restore', '--file=/dev/null', "/backups/$($latest.Name)"))
        "$($latest.Name)，距今 $([math]::Round($age.TotalHours, 1)) 小时，完整读取通过。"
    }
    [pscustomobject]@{
        checkedAt = [DateTime]::UtcNow.ToString('o')
        healthy = (@($checks | Where-Object { -not $_.healthy }).Count -eq 0)
        checks = $checks.ToArray()
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    $report = Test-LocalOperations
    $report | ConvertTo-Json -Depth 4
    if (-not $report.healthy) { exit 1 }
    exit 0
}
