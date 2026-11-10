param([switch]$BuildClients, [string]$ApiBaseUrl='http://localhost:8082', [ValidateRange(0,600)][int]$WaitSeconds=180)
$ErrorActionPreference='Stop'
if ($ApiBaseUrl -notin @('http://localhost:8082','http://127.0.0.1:8082')) { throw 'Verifier only permits the isolated loopback API on port 8082.' }
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
  function Check([bool]$ok,[string]$label) { if (!$ok) { throw "FAIL: $label" }; Write-Output "PASS: $label" }
  function Compose([string[]]$arguments) {
    $result=& docker compose --env-file .env.v2-test -f compose.v2-test.yml @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Compose failed. Check postgres/migrate/backend logs locally; do not share secrets.' }
    return $result
  }
  & docker info --format '{{.ServerVersion}}' | Out-Null
  Check ($LASTEXITCODE -eq 0) 'Docker engine available'
  & docker compose version | Out-Null
  Check ($LASTEXITCODE -eq 0) 'Compose available'
  # Keep resolved configuration in memory: it contains local secrets.
  $config=(Compose @('config','--format','json')) -join "`n" | ConvertFrom-Json
  $e=$config.services.backend.environment
  Check ($e.RRC_V2_JDBC_URL -eq 'jdbc:postgresql://postgres:5432/rrc_v2_test' -and $e.RRC_V2_ENVIRONMENT -eq 'test') 'only isolated database and test environment'
  Check ($e.RRC_PAYMENT_MOCK_ENABLED -eq 'true' -and $e.SPRING_PROFILES_ACTIVE -eq 'local-v2-test') 'mock payment and dedicated v2 profile enabled'
  Check ($e.SERVER_ADDRESS -eq '0.0.0.0' -and $e.SERVER_PORT -eq '8082') 'backend container listens on 0.0.0.0:8082'
  Check ($config.networks.v2_isolated.driver -eq 'bridge' -and !$config.networks.v2_isolated.external) 'dedicated Compose bridge network (no shared external network)'
  Check (!$config.services.postgres.ports) 'database has no published port'
  Check ($e.RRC_V2_ADMIN_PHONE -eq 'test-admin' -and $e.RRC_V2_BOOTSTRAP_CONFIRM -eq 'CREATE_ISOLATED_TEST_ADMIN') 'synthetic administrator explicitly authorized'
  Check (@($e.PSObject.Properties.Name | Where-Object { $_ -match 'WECHAT|MERCHANT|WECHATPAY|SPRING_DATASOURCE|SPRING_CONFIG' }).Count -eq 0) 'no injected real WeChat/payment or legacy datasource overrides'
  foreach ($service in @('postgres','backend')) {
    $id=(Compose @('ps','-q',$service)) -join ''
    Check (![string]::IsNullOrWhiteSpace($id)) "$service exists"
    $deadline=[DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
      $state=& docker inspect --format '{{.State.Status}}|{{.State.Health.Status}}' $id
      if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $service" }
      if ($state -eq 'running|healthy') { break }
      if ($state -match 'exited|dead|unhealthy' -or [DateTime]::UtcNow -ge $deadline) {
        throw "$service failed readiness ($state). Inspect compose logs $service locally."
      }
      Start-Sleep -Seconds 1
    } while ($true)
    Check ($state -eq 'running|healthy') "$service healthy"
  }
  Compose @('exec','-T','backend','test','-w','/app/evidence') | Out-Null
  $uid=(Compose @('exec','-T','backend','id','-u')) -join ''
  Check ($uid -match '^[1-9][0-9]*$') 'non-root backend can write isolated evidence volume'
  function Sql([string]$database,[string]$query) {
    (Compose @('exec','-T','postgres','psql','-X','-Atq','-v','ON_ERROR_STOP=1','-U',$e.RRC_V2_DB_USERNAME,'-d',$database,'-c',$query)) -join ''
  }
  Check ((Sql rrc_v2_test "SELECT to_regnamespace('runrun_v2') IS NOT NULL") -eq 't') 'runrun_v2 schema exists'
  Check ((Sql rrc_v2_test "SELECT count(*) FROM pg_tables WHERE schemaname='runrun_v2'") -eq '44') 'all 44 M00-M08 v2 tables exist'
  Check ((Sql rrc_v2_test "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')") -eq '0') 'v2 public schema has no legacy tables/Flyway history'
  Check ((Sql rrc_legacy_sentinel "SELECT id::text||':'||value FROM public.v2_test_sentinel") -eq '1:synthetic-untouched') 'separate legacy public sentinel unchanged'
  Check ((Sql rrc_v2_test "SELECT to_regprocedure('runrun_v2.claim_notification_outbox(integer)') IS NOT NULL AND to_regprocedure('runrun_v2.complete_notification_outbox(bigint,boolean,text)') IS NOT NULL AND to_regprocedure('runrun_v2.run_notification_review_timers()') IS NOT NULL") -eq 't') 'M08 notification runtime functions exist'
  $receipts=Compose @('run','--rm','--no-deps','--entrypoint','sh','migrate','/scripts/verify-receipts.sh')
  Check (($receipts -join '').Trim() -eq 'PASS') 'all 12 M00-M08 plus M09 repair receipts match source checksums'
  Check ((Sql rrc_v2_test "SELECT count(*) FROM runrun_v2.app_users WHERE phone='test-admin' AND role='ADMIN' AND v2_admin_enabled AND NOT controlled_identity") -eq '1') 'isolated administrator enabled'
  Check ((Sql rrc_v2_test "SELECT count(*) FROM runrun_v2.admin_audit_logs WHERE request_id='local-test-enable' AND reason='Isolated synthetic local test fixture'") -eq '1') 'controlled administrator initialization audited once'
  Add-Type -AssemblyName System.Net.Http
  $client=New-Object Net.Http.HttpClient
  $client.Timeout=[TimeSpan]::FromSeconds(15)
  function Http([string]$method,[string]$path,[string]$body='', [string]$token='') {
    $request=New-Object Net.Http.HttpRequestMessage((New-Object Net.Http.HttpMethod($method)),($ApiBaseUrl+$path))
    if ($body -and $method -notin @('GET','HEAD')) { $request.Content=New-Object Net.Http.StringContent($body,[Text.Encoding]::UTF8,'application/json') }
    if ($token) { $request.Headers.Authorization=New-Object Net.Http.Headers.AuthenticationHeaderValue('Bearer',$token) }
    $request.Headers.Add('Idempotency-Key',('environment-'+[guid]::NewGuid().ToString('N')))
    try { $response=$client.SendAsync($request).GetAwaiter().GetResult(); $result=@{ Code=[int]$response.StatusCode; Body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult() }; $response.Dispose(); return $result }
    finally { $request.Dispose() }
  }
  $preflight=New-Object Net.Http.HttpRequestMessage([Net.Http.HttpMethod]::Options,($ApiBaseUrl+'/api/v2/auth/session'))
  $adminOrigin=$e.RRC_LOCAL_ADMIN_ORIGIN
  if (!$adminOrigin) { $adminOrigin='http://127.0.0.1:5173' }
  $preflight.Headers.Add('Origin',$adminOrigin)
  $preflight.Headers.Add('Access-Control-Request-Method','GET')
  $preflight.Headers.Add('Access-Control-Request-Headers','authorization,idempotency-key')
  $preflightResponse=$client.SendAsync($preflight).GetAwaiter().GetResult()
  Check ([int]$preflightResponse.StatusCode -eq 200 -and $preflightResponse.Headers.GetValues('Access-Control-Allow-Origin') -contains $adminOrigin) 'local admin browser CORS preflight accepted'
  $preflightResponse.Dispose(); $preflight.Dispose()
  foreach ($route in @(@('GET','/api/v2/auth/session'),@('POST','/api/v2/auth/logout'),@('GET','/api/v2/notifications'),@('GET','/api/v2/courier/certification'))) {
    $r=Http $route[0] $route[1] '{}'
    Check ($r.Code -eq 401) "$($route[0]) $($route[1]) anonymous => 401 (never 404)"
  }
  $login=Http POST '/api/v2/auth/password/login' (@{phone=$e.RRC_V2_ADMIN_PHONE;password=$e.RRC_V2_ADMIN_PASSWORD}|ConvertTo-Json -Compress)
  Check ($login.Code -eq 200) 'synthetic administrator password login succeeds'
  $loginPayload=$login.Body|ConvertFrom-Json
  Check ($loginPayload.account.permissions -is [array] -and $loginPayload.account.permissions -contains 'COURIER_VIEW') 'login account contains server-issued permissions array'
  $policy=Http GET '/api/v2/privacy/policy'
  Check ($policy.Code -eq 200) 'anonymous v2 privacy policy reachable'
  $policyPayload=$policy.Body|ConvertFrom-Json
  Check ($policyPayload.version -eq '2026-10-24' -and ![string]::IsNullOrWhiteSpace($policyPayload.content)) 'approved privacy version and content returned'
  $token=$loginPayload.token
  Check (![string]::IsNullOrWhiteSpace($token)) 'independent v2 session issued (not printed)'
  $restored=Http GET '/api/v2/auth/session' '' $token
  Check ($restored.Code -eq 200) 'v2 session authenticates'
  $restoredAccount=$restored.Body|ConvertFrom-Json
  Check ($restoredAccount.permissions -is [array] -and ($restoredAccount.permissions -join ',') -eq ($loginPayload.account.permissions -join ',')) 'session permissions match login account'
  Check ((Http GET '/api/v2/notifications' '' $token).Code -eq 200) 'authenticated notifications reachable'
  foreach ($suffix in @('?page=0&size=20','?status=PENDING&page=0&size=20','?query=test-courier&page=0&size=20')) {
    $list=Http GET ('/api/v2/courier/certification/admin'+$suffix) '' $token
    Check ($list.Code -eq 200) "ADMIN certification list $suffix => 200"
    $listPage=$list.Body|ConvertFrom-Json
    Check ($null -ne $listPage.items -and $null -ne $listPage.totalElements) 'certification list returns a page'
  }
  Check ((Http GET '/api/v2/courier/certification' '' $token).Code -eq 403) 'ADMIN cannot use COURIER-only certification query'
  Check ((Http POST '/api/v2/auth/wechat/login' '{"code":"synthetic-rejected"}').Code -eq 401) 'real WeChat gateway disabled'
  Check ((Http POST '/api/v2/auth/logout' '{"allSessions":false}' $token).Code -eq 200) 'logout revokes v2 session'
  Check ((Http GET '/api/v2/auth/session' '' $token).Code -eq 401) 'revoked session rejected'
  $client.Dispose(); $token=$null; $login=$null
  if ($BuildClients) {
    & (Join-Path $PSScriptRoot 'build-v2-clients.ps1') -ApiBaseUrl $ApiBaseUrl
    Check ($?) 'admin and mini-program build with the same isolated API'
  } else { Write-Output 'NOT RUN: client builds; rerun with -BuildClients (Node/pnpm required).' }
} finally { Pop-Location }
