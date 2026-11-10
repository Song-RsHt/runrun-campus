param(
  [string]$Test='',
  [switch]$KeepDatabase
)
$ErrorActionPreference='Stop'
# Run in a child process so the backend runner's test environment stays scoped to this invocation.
$backendRunner=Join-Path (Split-Path $PSScriptRoot -Parent) 'handover-2026-10-24/RunRunCampusBackend/tools/run-v2-isolated-tests.ps1'
& pwsh -NoProfile -File $backendRunner -Test $Test -KeepDatabase:$KeepDatabase
exit $LASTEXITCODE
