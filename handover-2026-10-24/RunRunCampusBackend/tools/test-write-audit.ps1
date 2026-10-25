#requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'run-load-probe.ps1')
$audit = [pscustomobject]@{ orders=10; accepted=8; couponOrders=3; membershipOrders=4
    creationEvents=10; acceptEvents=8; acceptMessages=8; usedCoupons=3; membershipTripsConsumed=4
    invalidOrders=0; duplicateKeys=0; invalidCoupons=0; invalidMemberships=0 }
$metrics = [pscustomobject]@{
    write_orders_created=@{values=@{count=10}}; write_orders_accepted=@{values=@{count=8}}
    write_coupon_orders=@{values=@{count=3}}; write_membership_orders=@{values=@{count=4}}
}
$passed = 0
if (-not (Test-WriteAudit $audit $metrics)) { throw 'Valid audit rejected' }; $passed++
foreach ($name in $audit.PSObject.Properties.Name) {
    $changed = $audit | ConvertTo-Json | ConvertFrom-Json
    $changed.$name++
    if (Test-WriteAudit $changed $metrics) { throw "Incorrect $name accepted" }; $passed++
    $changed.PSObject.Properties.Remove($name)
    if (Test-WriteAudit $changed $metrics) { throw "Missing $name accepted" }; $passed++
}
foreach ($name in $metrics.PSObject.Properties.Name) {
    $changed = $metrics | ConvertTo-Json -Depth 5 | ConvertFrom-Json
    $changed.PSObject.Properties.Remove($name)
    if (Test-WriteAudit $audit $changed) { throw "Missing metric $name accepted" }; $passed++
}
if (Test-WriteAudit $null $metrics) { throw 'Null audit accepted' }; $passed++
Write-Host "Write audit tests: $passed passed"
