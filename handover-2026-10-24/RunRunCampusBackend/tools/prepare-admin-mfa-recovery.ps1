#requires -Version 7.2
param(
    [Parameter(Mandatory)][ValidatePattern('^1\d{10}$')][string]$Phone,
    [Parameter(Mandatory)][ValidateLength(1,200)][string]$Reason,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Reason)) { throw 'An identity verification record is required' }
$directory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $directory) { throw 'Choose a new protected output directory' }
$null = New-Item -ItemType Directory -Path $directory
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,
    [Security.Principal.SecurityIdentifier]::new('S-1-5-18'), [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
Set-Acl -LiteralPath $directory -AclObject $acl
$grant = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).TrimEnd('=').Replace('+','-').Replace('/','_')
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($grant))).ToLowerInvariant()
$sql = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '../deploy/admin-mfa-recovery.sql')
$sql = $sql.Replace(":'phone'", "'$Phone'").Replace(":'reason'", "'" + $Reason.Replace("'", "''") + "'").Replace(":'grant_hash'", "'$hash'")
[IO.File]::WriteAllText((Join-Path $directory 'recovery.sql'), $sql)
[IO.File]::WriteAllText((Join-Path $directory 'one-time-reset-grant.txt'), $grant)
Write-Output "Recovery SQL and one-time grant prepared in: $directory. No database was modified."
