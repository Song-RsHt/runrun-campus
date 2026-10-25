#requires -Version 7.2
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
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
$key = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
$path = Join-Path $directory 'ADMIN_MFA_KEY'
[IO.File]::WriteAllText($path, [Convert]::ToBase64String($key))
[Array]::Clear($key, 0, $key.Length)
Write-Output "MFA key saved to protected file: $path"
