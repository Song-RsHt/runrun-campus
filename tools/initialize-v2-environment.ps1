param([switch]$ConfirmIsolatedTest)
$ErrorActionPreference='Stop'
if (!$ConfirmIsolatedTest) { throw 'Use -ConfirmIsolatedTest to authorize synthetic local test fixtures only.' }
$root=Split-Path $PSScriptRoot -Parent
$file=Join-Path $root '.env.v2-test'
if (!(Test-Path $file)) { throw 'First Copy-Item .env.v2-test.example .env.v2-test' }
$content=[IO.File]::ReadAllText($file)
if ($content -notmatch 'RRC_V2_DB_PASSWORD=REPLACE_LOCALLY') { throw 'Existing local values will not be overwritten.' }
function New-LocalRandom {
  $bytes=New-Object byte[] 32
  $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  [Convert]::ToBase64String($bytes)
}
foreach ($name in @('RRC_V2_DB_PASSWORD','RRC_IDENTITY_ENCRYPTION_KEY','RRC_IDENTITY_HASH_KEY','RRC_V2_ADMIN_PASSWORD')) {
  $content=$content.Replace("$name=REPLACE_LOCALLY", "$name=$(New-LocalRandom)")
}
$content=$content.Replace('REPLACE_WITH_CREATE_ISOLATED_TEST_ADMIN','CREATE_ISOLATED_TEST_ADMIN')
[IO.File]::WriteAllText($file,$content,(New-Object Text.UTF8Encoding $false))
Write-Output 'Local values generated in ignored .env.v2-test; no credentials printed. Do not share or commit this file.'
