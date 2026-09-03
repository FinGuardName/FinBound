# Unit-level tests of the POSIX startup wrapper, run with no container network.
[CmdletBinding()]
param([string]$RepositoryRoot = (Resolve-Path "$PSScriptRoot/../..").Path)
$ErrorActionPreference = 'Stop'
$cases = @(
    @{ Name = 'valid'; Required = 'TEST_CREDENTIAL'; Setup = 'printf credential-marker > /run/secrets/TEST_CREDENTIAL'; Exit = 0 },
    @{ Name = 'missing'; Required = 'TEST_CREDENTIAL'; Setup = ':'; Exit = 64 },
    @{ Name = 'empty'; Required = 'TEST_CREDENTIAL'; Setup = ': > /run/secrets/TEST_CREDENTIAL'; Exit = 64 },
    @{ Name = 'whitespace'; Required = 'TEST_CREDENTIAL'; Setup = 'printf "   \n" > /run/secrets/TEST_CREDENTIAL'; Exit = 64 },
    @{ Name = 'missing second secret'; Required = 'TEST_CREDENTIAL SECOND_CREDENTIAL'; Setup = 'printf credential-marker > /run/secrets/TEST_CREDENTIAL'; Exit = 64 },
    @{ Name = 'invalid variable'; Required = '1INVALID'; Setup = 'printf credential-marker > /run/secrets/1INVALID'; Exit = 64 },
    @{ Name = 'path traversal'; Required = '../TEST_CREDENTIAL'; Setup = ':'; Exit = 64 },
    @{ Name = 'no declared requirements'; Required = ''; Setup = ':'; Exit = 64 },
    @{ Name = 'equal API credentials'; Required = 'FINGUARD_API_VIEWERCREDENTIAL FINGUARD_API_OPERATORCREDENTIAL';
        Setup = 'printf credential-marker > /run/secrets/FINGUARD_API_VIEWERCREDENTIAL; printf credential-marker > /run/secrets/FINGUARD_API_OPERATORCREDENTIAL'; Exit = 64 }
)
foreach ($case in $cases) {
    $command = 'mkdir -p /run/secrets; ' + $case.Setup + '; /bin/sh /opt/finguard/with-secrets.sh sh -c ''test "$TEST_CREDENTIAL" = credential-marker && echo DOWNSTREAM_REACHED'''
    $arguments = @('run', '--rm', '--network', 'none', '--entrypoint', 'sh',
        '--env', "FINGUARD_REQUIRED_SECRETS=$($case.Required)",
        '--mount', "type=bind,source=$RepositoryRoot/infrastructure/docker/with-secrets.sh,target=/opt/finguard/with-secrets.sh,readonly",
        'eclipse-temurin:21-jre-alpine', '-c', $command)
    $result = (& docker @arguments 2>&1) -join "`n"
    if ($LASTEXITCODE -ne $case.Exit -or $result.Contains('credential-marker')) {
        throw "Secret wrapper failed: $($case.Name) (output withheld)"
    }
    if ($result.Contains('DOWNSTREAM_REACHED') -ne ($case.Exit -eq 0)) {
        throw "Wrong downstream reachability: $($case.Name)"
    }
}
Write-Output "PASS: $($cases.Count) startup wrapper unit cases; no credential logging or denied downstream execution"
# The last case intentionally exits 64; do not make a pwsh CI runner report failure.
$global:LASTEXITCODE = 0
