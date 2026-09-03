# Real six-service topology smoke test. Requires the #61 images and #77 Gateway PR.
# Uses a uniquely named disposable project; never operates on the normal finguard stack.
[CmdletBinding()]
param([string]$RepositoryRoot = (Resolve-Path "$PSScriptRoot/../..").Path)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/compose-contract.ps1" -RepositoryRoot $RepositoryRoot

foreach ($path in @('backend/agent/Dockerfile', 'backend/mock-finance/Dockerfile',
    'backend/gateway/src/main/java/io/finguard/gateway/client/impl/CoreClientImpl.java')) {
    if (-not (Test-Path (Join-Path $RepositoryRoot $path))) {
        throw "Missing prerequisite: $path. Integrate PR #83/#77 or use an isolated -RepositoryRoot."
    }
}

$project = "finguard-62-$([guid]::NewGuid().ToString('N').Substring(0, 12))"
$compose = @('compose', '--project-name', $project, '--env-file', "$RepositoryRoot/.env.example",
    '-f', "$RepositoryRoot/infrastructure/docker-compose.yml")
$saved = @{}
$secretNames = @('POSTGRES_PASSWORD', 'FINGUARD_INTERNAL_CREDENTIAL', 'AGENT_SERVICE_CREDENTIAL', 'VIEWER_CREDENTIAL', 'OPERATOR_CREDENTIAL')
function Run-Docker([string[]]$Arguments) {
    $output = (& docker @Arguments 2>&1) -join "`n"
    foreach ($name in $secretNames) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ($value -and $output.Contains($value)) { throw 'Docker output leaked a test credential (output withheld)' }
    }
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed after secret scan: $output" }
    return $output
}
function Exec-Service([string]$Service, [string]$Command) {
    Run-Docker ($compose + @('exec', '-T', $Service, 'sh', '-c', $Command))
}
try {
    foreach ($name in $secretNames + @('OPERATOR_EMPLOYEE_ID', 'GATEWAY_HOST_PORT')) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name)
    }
    foreach ($name in $secretNames) {
        [Environment]::SetEnvironmentVariable($name, [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)))
    }
    $env:OPERATOR_EMPLOYEE_ID = 'EMP-101'
    $env:GATEWAY_HOST_PORT = '0'
    Write-Output 'Building and starting the isolated Compose project...'
    $null = Run-Docker ($compose + @('up', '-d', '--build', '--wait', '--wait-timeout', '180'))
    $ids = @{}
    foreach ($service in @('postgres', 'core-api', 'opa', 'gateway', 'agent', 'mock-finance')) {
        $ids[$service] = (Run-Docker ($compose + @('ps', '-q', $service))).Trim()
        $health = Run-Docker @('inspect', '--format', '{{.State.Health.Status}}', $ids[$service])
        Assert-Contract ($health.Trim() -eq 'healthy') "$service unhealthy"
    }
    Write-Output 'PASS: six services healthy'

    foreach ($pair in @(@('agent', 'http://core-api:8080'), @('agent', 'http://gateway:8081'), @('gateway', 'http://mock-finance:8083'))) {
        $null = Exec-Service $pair[0] ('wget -qO- {0}/actuator/health | grep -q ''"status":"UP"''' -f $pair[1])
    }
    $null = Exec-Service 'core-api' 'echo | nc -w 2 postgres 5432 >/dev/null 2>&1'
    $dbNetwork = "$($project)_data-zone"
    $dbIp = (Run-Docker @('inspect', '--format', "{{(index .NetworkSettings.Networks `"$dbNetwork`").IPAddress}}", $ids.postgres)).Trim()
    Assert-Contract ($dbIp -match '^\d+\.\d+\.\d+\.\d+$') 'DB IP not found'
    foreach ($service in @('agent', 'gateway', 'mock-finance')) {
        $null = Exec-Service $service "if echo | nc -w 2 postgres 5432 >/dev/null 2>&1; then exit 1; fi; if echo | nc -w 2 $dbIp 5432 >/dev/null 2>&1; then exit 1; fi"
    }
    $financeNetwork = "$($project)_finance-zone"
    $financeIp = (Run-Docker @('inspect', '--format', "{{(index .NetworkSettings.Networks `"$financeNetwork`").IPAddress}}", $ids['mock-finance'])).Trim()
    Assert-Contract ($financeIp -match '^\d+\.\d+\.\d+\.\d+$') 'Mock Finance IP not found'
    $null = Exec-Service 'agent' "if echo | nc -w 2 mock-finance 8083 >/dev/null 2>&1; then exit 1; fi; if echo | nc -w 2 $financeIp 8083 >/dev/null 2>&1; then exit 1; fi"
    Write-Output 'PASS: service HTTP connectivity; PostgreSQL and Agent-to-finance DNS/direct-IP isolation'

    # wget prints headers only on rejected calls; bodies stay inside the container.
    foreach ($test in @(@('agent', '8082', '/internal/v1/agent-simulations'), @('mock-finance', '8083', '/internal/v1/finance/tool-calls'))) {
        foreach ($header in @('', '--header="X-FinGuard-Internal-Credential: deliberately-invalid"')) {
            $command = "result=`$(wget -S -O /dev/null $header --header='Content-Type: application/json' --post-data='{}' http://localhost:$($test[1])$($test[2]) 2>&1 || true); echo `"`$result`" | grep -q '401'"
            $null = Exec-Service $test[0] $command
        }
    }
    Write-Output 'PASS: missing/invalid internal credentials rejected'

    # Positive credential wiring controls. Financial response stays in /dev/null.
    $null = Exec-Service 'gateway' @'
credential="$(cat /run/secrets/FINGUARD_CREDENTIALS_INTERNALSERVICE)"
wget -q -O /dev/null --header="X-FinGuard-Internal-Credential: $credential" \
  --header='Content-Type: application/json' \
  --post-data='{"requestId":"REQ-COMPOSE-SMOKE","tool":"CREDIT_SCORE_READ","targetConsumerId":"CUST-1001"}' \
  http://mock-finance:8083/internal/v1/finance/tool-calls
'@
    # Unknown run references must fail closed through the real Core client. This
    # verifies Agent -> Gateway credential wiring without fabricating a Passport.
    $null = Exec-Service 'agent' @'
credential="$(cat /run/secrets/FINGUARD_INTERNAL_CREDENTIAL)"
wget -qO- --header="X-FinGuard-Internal-Credential: $credential" \
  --header='Content-Type: application/json' \
  --post-data='{"agentRunId":"RUN-COMPOSE-MISSING","passportId":"PASS-COMPOSE-MISSING","scenario":"NORMAL_CREDIT_SCORE"}' \
  http://localhost:8082/internal/v1/agent-simulations | grep -q '"decision":"BLOCK"'
'@
    Write-Output 'PASS: Gateway credential accepted by Mock Finance; Agent -> Gateway unknown-run fail-closed BLOCK'

    # Exercise the wrapper independently of application validation. A denied startup
    # must never execute the supplied downstream command or echo a present credential.
    foreach ($required in @('MISSING_SECRET', 'EMPTY_SECRET', 'BLANK_SECRET', 'SHORT_SECRET')) {
        $setup = 'mkdir -p /run/secrets; : > /run/secrets/EMPTY_SECRET; printf "   " > /run/secrets/BLANK_SECRET; printf short > /run/secrets/SHORT_SECRET; '
        $script = $setup + '/bin/sh /opt/finguard/with-secrets.sh echo DOWNSTREAM_REACHED'
        $arguments = @('run', '--rm', '--network', 'none', '--entrypoint', 'sh',
            '--env', "FINGUARD_REQUIRED_SECRETS=$required",
            '--mount', "type=bind,source=$RepositoryRoot/infrastructure/docker/with-secrets.sh,target=/opt/finguard/with-secrets.sh,readonly",
            'eclipse-temurin:21-jre-alpine', '-c', $script)
        $result = (& docker @arguments 2>&1) -join "`n"
        Assert-Contract ($LASTEXITCODE -eq 64) 'missing/blank/short credential did not fail closed'
        Assert-Contract (-not $result.Contains('DOWNSTREAM_REACHED')) 'startup rejection reached downstream'
    }
    # Missing host secret must fail container creation (not just `config`).
    $originalAgentSecret = $env:AGENT_SERVICE_CREDENTIAL
    Remove-Item -LiteralPath Env:AGENT_SERVICE_CREDENTIAL
    $missing = (& docker @compose run --rm --no-deps agent 2>&1) -join "`n"
    $missingExit = $LASTEXITCODE
    $env:AGENT_SERVICE_CREDENTIAL = $originalAgentSecret
    Assert-Contract ($missingExit -ne 0) 'missing host secret accepted'
    Assert-Contract (-not $missing.Contains($originalAgentSecret)) 'missing-secret error leaked a credential'
    $logs = Run-Docker ($compose + @('logs', '--no-color'))
    Assert-Contract (-not $logs.Contains('"creditScore"')) 'financial response payload entered logs'
    foreach ($service in $ids.Keys) {
        $metadata = Run-Docker @('inspect', $ids[$service])
        foreach ($name in $secretNames) {
            $value = [Environment]::GetEnvironmentVariable($name)
            Assert-Contract (-not $metadata.Contains($value)) "$service metadata leaked a credential"
        }
    }
    Write-Output 'PASS: fail-closed startup, downstream non-execution, secret-free logs and container metadata'
    Write-Output 'NOTE: health/connectivity is not an ALLOW/BLOCK business E2E. See README dependency gates.'
} finally {
    # Only remove resources belonging to this script-created, uniquely named project.
    if ($project -notmatch '^finguard-62-[a-f0-9]{12}$') { throw 'Unsafe cleanup project' }
    $null = & docker @compose down --volumes --remove-orphans 2>&1
    foreach ($name in $saved.Keys) {
        if ($null -eq $saved[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $saved[$name])
        }
    }
}
