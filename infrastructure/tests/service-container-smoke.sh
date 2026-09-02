#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
agent_image="finguard-agent:issue-61-smoke"
mock_image="finguard-mock-finance:issue-61-smoke"
suffix="$$"
agent_container="finguard-agent-issue-61-${suffix}"
mock_container="finguard-mock-finance-issue-61-${suffix}"
agent_missing_container="${agent_container}-missing"
mock_missing_container="${mock_container}-missing"
response_dir="$(mktemp -d)"

cleanup() {
    docker rm -f \
        "$agent_container" \
        "$mock_container" \
        "$agent_missing_container" \
        "$mock_missing_container" >/dev/null 2>&1 || true
    rm -rf "$response_dir"
}
trap cleanup EXIT

fail() {
    echo "service container smoke test failed: $1" >&2
    exit 1
}

wait_for_exit() {
    local container="$1"
    local attempts=30

    for ((attempt = 1; attempt <= attempts; attempt++)); do
        if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == "false" ]]; then
            local exit_code
            exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$container")"
            [[ "$exit_code" != "0" ]] || fail "$container exited successfully without credentials"
            return
        fi
        sleep 1
    done

    fail "$container did not fail closed without credentials"
}

wait_for_healthy() {
    local container="$1"
    local attempts=90

    for ((attempt = 1; attempt <= attempts; attempt++)); do
        local status
        status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container")"
        if [[ "$status" == "healthy" ]]; then
            return
        fi
        if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == "false" ]]; then
            fail "$container exited before becoming healthy"
        fi
        sleep 1
    done

    fail "$container did not become healthy"
}

verify_image() {
    local image="$1"
    local expected_port="$2"
    local java_version
    local uid
    local configured_user
    local image_metadata

    java_version="$(docker run --rm --entrypoint java "$image" -version 2>&1)"
    grep -Eq 'version "21([.]|")' <<<"$java_version" || fail "$image does not use Java 21"

    uid="$(docker run --rm --entrypoint id "$image" -u)"
    [[ "$uid" != "0" ]] || fail "$image runs as root"

    configured_user="$(docker image inspect --format '{{.Config.User}}' "$image")"
    [[ -n "$configured_user" && "$configured_user" != "root" && "$configured_user" != "0" ]] \
        || fail "$image does not declare a non-root runtime user"

    image_metadata="$(docker image inspect "$image")"
    if grep -Eq 'AGENT_SERVICE_CREDENTIAL|FINGUARD_INTERNAL_CREDENTIAL' <<<"$image_metadata"; then
        fail "$image metadata contains a credential variable"
    fi
    if docker history --no-trunc "$image" | grep -Eq 'AGENT_SERVICE_CREDENTIAL|FINGUARD_INTERNAL_CREDENTIAL'; then
        fail "$image history contains a credential variable"
    fi

    docker image inspect --format '{{json .Config.Healthcheck.Test}}' "$image" \
        | grep -Fq "localhost:${expected_port}/actuator/health" \
        || fail "$image does not declare the expected healthcheck"
    docker image inspect --format '{{json .Config.Entrypoint}}' "$image" \
        | grep -Fq -- '-XX:MaxRAMPercentage=75.0' \
        || fail "$image does not cap heap relative to container memory"
}

host_port() {
    docker port "$1" "$2/tcp" | awk -F: 'NR == 1 {print $NF}'
}

assert_internal_rejected() {
    local container="$1"
    local container_port="$2"
    local endpoint="$3"
    local request_body="$4"
    local response_file="$response_dir/${container}.json"
    local port
    local status

    port="$(host_port "$container" "$container_port")"
    status="$(curl --silent --show-error \
        --output "$response_file" \
        --write-out '%{http_code}' \
        --header 'Content-Type: application/json' \
        --data "$request_body" \
        "http://127.0.0.1:${port}${endpoint}")"

    [[ "$status" == "401" ]] || fail "$container accepted an internal request without a credential"
    grep -Fq '"errorCode":"INTERNAL_CREDENTIAL_INVALID"' "$response_file" \
        || fail "$container returned an unexpected credential error"
}

assert_health_up() {
    local container="$1"
    local container_port="$2"
    local port

    port="$(host_port "$container" "$container_port")"
    curl --fail --silent --show-error "http://127.0.0.1:${port}/actuator/health" \
        | grep -Fq '"status":"UP"' \
        || fail "$container health endpoint is not UP"
}

cd "$repo_root"

docker build --file backend/agent/Dockerfile --tag "$agent_image" .
docker build --file backend/mock-finance/Dockerfile --tag "$mock_image" .

verify_image "$agent_image" 8082
verify_image "$mock_image" 8083

docker run --detach --name "$agent_missing_container" "$agent_image" >/dev/null
docker run --detach --name "$mock_missing_container" "$mock_image" >/dev/null
wait_for_exit "$agent_missing_container"
wait_for_exit "$mock_missing_container"

agent_service_credential="$(openssl rand -hex 32)"
internal_credential="$(openssl rand -hex 32)"

if docker history --no-trunc "$agent_image" | grep -Fq "$agent_service_credential"; then
    fail "Agent image history contains its runtime service credential"
fi
if docker history --no-trunc "$agent_image" | grep -Fq "$internal_credential"; then
    fail "Agent image history contains the runtime internal credential"
fi
if docker history --no-trunc "$mock_image" | grep -Fq "$internal_credential"; then
    fail "Mock Finance image history contains the runtime internal credential"
fi

docker run --detach \
    --name "$agent_container" \
    --memory 512m \
    --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --security-opt no-new-privileges:true \
    --publish 127.0.0.1::8082 \
    --env AGENT_SERVICE_CREDENTIAL="$agent_service_credential" \
    --env FINGUARD_INTERNAL_CREDENTIAL="$internal_credential" \
    "$agent_image" >/dev/null

docker run --detach \
    --name "$mock_container" \
    --memory 512m \
    --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --security-opt no-new-privileges:true \
    --publish 127.0.0.1::8083 \
    --env FINGUARD_INTERNAL_CREDENTIAL="$internal_credential" \
    "$mock_image" >/dev/null

wait_for_healthy "$agent_container"
wait_for_healthy "$mock_container"
assert_health_up "$agent_container" 8082
assert_health_up "$mock_container" 8083

assert_internal_rejected \
    "$agent_container" \
    8082 \
    /internal/v1/agent-simulations \
    '{"agentRunId":"RUN-001","passportId":"PASS-001","scenario":"NORMAL_CREDIT_SCORE"}'
assert_internal_rejected \
    "$mock_container" \
    8083 \
    /internal/v1/finance/tool-calls \
    '{"requestId":"REQ-001","tool":"CREDIT_SCORE_READ","targetConsumerId":"CUST-1001"}'

if docker logs "$agent_container" 2>&1 | grep -Fq "$agent_service_credential"; then
    fail "Agent log contains its service credential"
fi
if docker logs "$agent_container" 2>&1 | grep -Fq "$internal_credential"; then
    fail "Agent log contains the internal credential"
fi
if docker logs "$mock_container" 2>&1 | grep -Fq "$internal_credential"; then
    fail "a service log contains the internal credential"
fi

echo "Agent and Mock Finance container smoke tests passed"
