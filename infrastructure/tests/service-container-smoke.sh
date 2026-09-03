#!/usr/bin/env bash

set -euo pipefail
umask 077

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
agent_image="finguard-agent:issue-61-smoke"
mock_image="finguard-mock-finance:issue-61-smoke"
suffix="$$"
agent_container="finguard-agent-issue-61-${suffix}"
mock_container="finguard-mock-finance-issue-61-${suffix}"
agent_missing_container="${agent_container}-missing"
mock_missing_container="${mock_container}-missing"
response_dir="$(mktemp -d -t finguard-service-smoke.XXXXXX)"
python_bin="${PYTHON_BIN:-python3}"
security_helper="$repo_root/infrastructure/tests/service_image_security.py"

cleanup() {
    docker rm -f \
        "$agent_container" \
        "$mock_container" \
        "$agent_missing_container" \
        "$mock_missing_container" >/dev/null 2>&1 || true
    # Delete only the private temporary directory created by this invocation.
    if [[ -d "$response_dir" && "$(basename "$response_dir")" == finguard-service-smoke.* ]]; then
        rm -rf -- "$response_dir"
    fi
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
    local image_history

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
    image_history="$(docker history --no-trunc "$image")"
    if grep -Eq 'AGENT_SERVICE_CREDENTIAL|FINGUARD_INTERNAL_CREDENTIAL' <<<"$image_history"; then
        fail "$image history contains a credential variable"
    fi

    local archive_file="$response_dir/image.tar"
    docker image save --output "$archive_file" "$image"
    "$python_bin" "$security_helper" verify "$archive_file" "$response_dir/canary"

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

assert_mock_finance_success() {
    local port
    local status
    local response_file="$response_dir/mock-success.json"
    port="$(host_port "$mock_container" 8083)"
    status="$(curl --silent --show-error --max-time 10 \
        --output "$response_file" --write-out '%{http_code}' \
        --header 'Content-Type: application/json' \
        --header "X-FinGuard-Internal-Credential: $internal_credential" \
        --data '{"requestId":"REQ-001","tool":"CREDIT_SCORE_READ","targetConsumerId":"CUST-1001"}' \
        "http://127.0.0.1:${port}/internal/v1/finance/tool-calls")"
    [[ "$status" == "200" ]] || fail "Mock Finance rejected a valid authenticated request"
    "$python_bin" "$security_helper" response "$response_file"
}

cd "$repo_root"

[[ "${DOCKER_BUILDKIT:-1}" == "1" ]] || fail "BuildKit is required; set DOCKER_BUILDKIT=1"
export DOCKER_BUILDKIT=1
docker buildx version >/dev/null || fail "Docker Buildx/BuildKit is required"
"$python_bin" --version >/dev/null
"$python_bin" -m unittest discover -s infrastructure/tests -p test_service_image_security.py

build_context="$response_dir/context"
"$python_bin" "$security_helper" prepare "$repo_root" "$build_context" "$response_dir/canary"
docker build --file "$build_context/backend/agent/Dockerfile" --tag "$agent_image" "$build_context"
docker build --file "$build_context/backend/mock-finance/Dockerfile" --tag "$mock_image" "$build_context"

verify_image "$agent_image" 8082
verify_image "$mock_image" 8083

docker run --detach --cap-drop=ALL --name "$agent_missing_container" "$agent_image" >/dev/null
docker run --detach --cap-drop=ALL --name "$mock_missing_container" "$mock_image" >/dev/null
wait_for_exit "$agent_missing_container"
wait_for_exit "$mock_missing_container"

agent_service_credential="$(openssl rand -hex 32)"
internal_credential="$(openssl rand -hex 32)"

docker run --detach \
    --name "$agent_container" \
    --memory 512m \
    --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --security-opt no-new-privileges:true \
    --cap-drop=ALL \
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
    --cap-drop=ALL \
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

assert_mock_finance_success

agent_logs="$(docker logs "$agent_container" 2>&1)"
mock_logs="$(docker logs "$mock_container" 2>&1)"
if grep -Fq "$agent_service_credential" <<<"$agent_logs"; then
    fail "Agent log contains its service credential"
fi
if grep -Fq "$internal_credential" <<<"$agent_logs"; then
    fail "Agent log contains the internal credential"
fi
if grep -Fq "$internal_credential" <<<"$mock_logs"; then
    fail "a service log contains the internal credential"
fi

echo "Agent and Mock Finance container smoke tests passed"
