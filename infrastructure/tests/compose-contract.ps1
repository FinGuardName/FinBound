# Requires PowerShell 7 and Docker Compose. Does not need a running Docker engine.
[CmdletBinding()]
param([string]$RepositoryRoot = (Resolve-Path "$PSScriptRoot/../..").Path)
$ErrorActionPreference = 'Stop'

function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Compose contract: $Message" }
}

$secretNames = @('POSTGRES_PASSWORD', 'FINGUARD_INTERNAL_CREDENTIAL',
    'AGENT_SERVICE_CREDENTIAL', 'VIEWER_CREDENTIAL', 'OPERATOR_CREDENTIAL')
$saved = @{}
try {
    foreach ($name in $secretNames + @('OPERATOR_EMPLOYEE_ID')) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name)
        [Environment]::SetEnvironmentVariable($name, "contract-$([guid]::NewGuid().ToString('N'))")
    }
    $compose = @('compose', '--env-file', "$RepositoryRoot/.env.example",
        '-f', "$RepositoryRoot/infrastructure/docker-compose.yml")
    # Capture even full config; assertions must never include its contents on failure.
    $raw = (& docker @compose config --format json 2>&1) -join "`n"
    Assert-Contract ($LASTEXITCODE -eq 0) 'config failed (output withheld)'
    foreach ($name in $secretNames) {
        Assert-Contract (-not $raw.Contains([Environment]::GetEnvironmentVariable($name))) 'config leaked a secret'
    }
    $config = $raw | ConvertFrom-Json -AsHashtable
    $services = $config.services
    $names = @(& docker @compose config --services 2>&1)
    Assert-Contract ($LASTEXITCODE -eq 0) 'service listing failed'
    foreach ($service in @('postgres', 'core-api', 'opa', 'gateway', 'agent', 'mock-finance')) {
        Assert-Contract ($names -contains $service) "missing $service"
        Assert-Contract ($services[$service].healthcheck.test.Count -gt 1) "$service lacks a healthcheck"
        Assert-Contract ($services[$service].restart -eq 'unless-stopped') "$service restart policy missing"
        foreach ($dependency in $services[$service].depends_on.Keys) {
            Assert-Contract ($services[$service].depends_on[$dependency].condition -eq 'service_healthy') 'dependency is not health-gated'
        }
    }
    foreach ($service in @('agent', 'mock-finance', 'gateway')) {
        Assert-Contract (-not $services[$service].networks.ContainsKey('data-zone')) "$service joined data-zone"
        Assert-Contract (($services[$service].environment.Keys -match 'POSTGRES|DATABASE|DATASOURCE').Count -eq 0) "$service has DB settings"
        Assert-Contract (@($services[$service].secrets | Where-Object source -eq 'postgres-password').Count -eq 0) "$service has DB secret"
    }
    Assert-Contract ($config.networks['data-zone'].internal -eq $true) 'data-zone must be internal'
    Assert-Contract ($config.networks['finance-zone'].internal -eq $true) 'finance-zone must be internal'
    Assert-Contract (-not $services.postgres.ports) 'PostgreSQL must not publish a host bypass'
    foreach ($service in @('agent', 'mock-finance', 'core-api', 'opa')) {
        Assert-Contract (-not $services[$service].ports) "$service exposes an internal port"
        Assert-Contract (-not $services[$service].networks.ContainsKey('public-zone')) "$service joined public-zone"
    }
    Assert-Contract ($services.gateway.networks.ContainsKey('public-zone')) 'Gateway public zone missing'
    Assert-Contract ($services.gateway.networks.ContainsKey('finance-zone')) 'Gateway finance zone missing'
    Assert-Contract ($services['mock-finance'].networks.ContainsKey('finance-zone')) 'Mock Finance finance zone missing'
    Assert-Contract (-not $services['mock-finance'].networks.ContainsKey('internal-zone')) 'Mock Finance joined Agent network'
    Assert-Contract (-not $services.agent.networks.ContainsKey('finance-zone')) 'Agent joined finance zone'
    $sharedAgentFinanceNetworks = @($services.agent.networks.Keys | Where-Object {
        $services['mock-finance'].networks.ContainsKey($_)
    })
    Assert-Contract ($sharedAgentFinanceNetworks.Count -eq 0) 'Agent can share a network with Mock Finance'
    Assert-Contract ($services.gateway.environment.MOCK_FINANCE_URL -eq 'http://mock-finance:8083') 'wrong Mock Finance URL'
    Assert-Contract ($services.agent.environment.CORE_API_BASE_URL -eq 'http://core-api:8080') 'wrong Agent Core URL'
    Assert-Contract ($services.agent.environment.GATEWAY_BASE_URL -eq 'http://gateway:8081') 'wrong Agent Gateway URL'
    Assert-Contract ($services['core-api'].environment.AGENT_URL -eq 'http://agent:8082') 'wrong Core Agent URL'
    Assert-Contract ($services.gateway.environment.SPRING_PROFILES_ACTIVE -eq 'real-core,real-downstream') 'Gateway base profiles are unsafe'
    Assert-Contract (-not $services.gateway.environment.ContainsKey('AI_URL')) 'base stack points to absent AI service'
    Assert-Contract ($services.gateway.build.dockerfile -eq 'infrastructure/docker/spring-service.Dockerfile') 'wrong Gateway Dockerfile'
    Assert-Contract ($services['core-api'].build.dockerfile -eq 'infrastructure/docker/spring-service.Dockerfile') 'wrong Core Dockerfile'
    Assert-Contract (-not (Test-Path (Join-Path $RepositoryRoot 'backend/gateway/Dockerfile'))) 'legacy Gateway Dockerfile remains'
    $exampleEnv = Get-Content -LiteralPath (Join-Path $RepositoryRoot '.env.example') -Raw
    Assert-Contract ($exampleEnv -match '(?m)^GATEWAY_BASE_URL=http://localhost:8091$') 'wrong host Gateway URL example'
    Assert-Contract (-not $services['core-api'].depends_on.ContainsKey('agent')) 'Core/Agent startup cycle'
    foreach ($dependency in @('core-api', 'gateway')) {
        Assert-Contract ($services.agent.depends_on[$dependency].condition -eq 'service_healthy') 'Agent readiness ordering missing'
    }
    foreach ($dependency in @('core-api', 'opa', 'mock-finance')) {
        Assert-Contract ($services.gateway.depends_on[$dependency].condition -eq 'service_healthy') 'Gateway readiness ordering missing'
    }
    $bindings = @{
        agent = @{ AGENT_SERVICE_CREDENTIAL = 'agent-credential'; FINGUARD_INTERNAL_CREDENTIAL = 'internal-credential' }
        'mock-finance' = @{ FINGUARD_INTERNAL_CREDENTIAL = 'internal-credential' }
        gateway = @{ FINGUARD_CREDENTIALS_VALIDAGENTTOKENS = 'agent-credential'; FINGUARD_CREDENTIALS_INTERNALSERVICE = 'internal-credential' }
        'core-api' = @{ POSTGRES_PASSWORD = 'postgres-password'; FINGUARD_INTERNAL_CREDENTIAL = 'internal-credential'; FINGUARD_API_VIEWERCREDENTIAL = 'viewer-credential'; FINGUARD_API_OPERATORCREDENTIAL = 'operator-credential' }
    }
    foreach ($service in $bindings.Keys) {
        foreach ($target in $bindings[$service].Keys) {
            $binding = @($services[$service].secrets | Where-Object target -eq $target)
            Assert-Contract ($binding.Count -eq 1 -and $binding[0].source -eq $bindings[$service][$target]) 'credential producer/consumer mismatch'
            Assert-Contract ($services[$service].environment.FINGUARD_REQUIRED_SECRETS.Split(' ') -contains $target) 'credential not required at startup'
        }
        Assert-Contract ($services[$service].entrypoint -contains '/opt/finguard/with-secrets.sh') 'secret validation bypassed'
        Assert-Contract ($services[$service].mem_limit -gt 0) 'runtime memory limit missing'
    }
    foreach ($secret in $config.secrets.Values) {
        Assert-Contract ($secretNames -contains $secret.environment) 'unexpected secret source'
    }
    # Overrides must remain composable and must not add Agent/Mock Finance host ports.
    $combined = (& docker @compose -f "$RepositoryRoot/infrastructure/docker-compose.demo.yml" -f "$RepositoryRoot/infrastructure/docker-compose.expose.yml" config --format json 2>&1) -join "`n"
    Assert-Contract ($LASTEXITCODE -eq 0) 'demo/expose override failed'
    $overrides = $combined | ConvertFrom-Json -AsHashtable
    Assert-Contract ($overrides.services['core-api'].environment.SPRING_PROFILES_ACTIVE -eq 'local') 'demo seed must remain opt-in'
    Assert-Contract ($overrides.services['core-api'].ports[0].host_ip -eq '127.0.0.1') 'Core exposure must be loopback only'
    Write-Output 'PASS: Compose services, URLs, credentials, readiness, network isolation and redacted config'
} finally {
    foreach ($name in $saved.Keys) {
        if ($null -eq $saved[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $saved[$name])
        }
    }
}
