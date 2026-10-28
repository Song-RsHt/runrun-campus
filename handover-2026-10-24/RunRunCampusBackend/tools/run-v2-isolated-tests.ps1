param(
  [Parameter(Mandatory=$true)][string]$JdbcUrl,
  [Parameter(Mandatory=$true)][string]$Username,
  [Parameter(Mandatory=$true)][string]$Password,
  [string]$Test='IdentityIntegrationTest,OrderIntegrationTest,PaymentIntegrationTest,FulfillmentIntegrationTest,CourierCertificationV2DatabaseE2ETest,OrderRequestJsonContractTest'
)
$ErrorActionPreference='Stop'
if ($JdbcUrl -match '(?i)(public|v1)') { throw 'Refusing non-v2 database URL' }
$env:RRC_V2_JDBC_URL=$JdbcUrl
$env:RRC_V2_DB_USERNAME=$Username
$env:RRC_V2_DB_PASSWORD=$Password
$env:SPRING_CONFIG_NAME='application-v2-test'
& mvn -B "-Dtest=$Test" test
if ($LASTEXITCODE -ne 0) { throw "v2 isolated test suite failed ($LASTEXITCODE)" }
