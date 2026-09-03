#!/usr/bin/env bash

set -euo pipefail
umask 077

# Prevent Git Bash on Windows from rewriting Linux paths passed to Docker,
# while leaving temporary host paths portable for curl and Python.
docker() {
    MSYS_NO_PATHCONV=1 command docker "$@"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
image="finbound-ai-risk:issue-68-smoke"
suffix="$$"
container="finbound-ai-risk-issue-68-${suffix}"
missing_container="${container}-missing"
response_dir="$(mktemp -d -t finbound-ai-risk-smoke.XXXXXX)"
canary_path="$repo_root/ai-risk/.container-smoke-${suffix}.key"

cleanup() {
    docker rm -f "$container" "$missing_container" >/dev/null 2>&1 || true
    rm -f -- "$canary_path"
    if [[ -d "$response_dir" && "$(basename "$response_dir")" == finbound-ai-risk-smoke.* ]]; then
        rm -rf -- "$response_dir"
    fi
}
trap cleanup EXIT

fail() {
    echo "AI Risk container smoke test failed: $1" >&2
    exit 1
}

host_port() {
    docker port "$1" 8000/tcp | awk -F: 'NR == 1 {print $NF}' | tr -d '\r'
}

wait_for_http() {
    local port="$1"
    for _attempt in $(seq 1 90); do
        if curl --fail --silent --output "$response_dir/health.json" "http://127.0.0.1:${port}/health"; then
            return
        fi
        sleep 1
    done
    fail "service did not start"
}

wait_for_healthy() {
    for _attempt in $(seq 1 180); do
        local status
        status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container")"
        if [[ "$status" == "healthy" ]]; then
            return
        fi
        if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == "false" ]]; then
            fail "service exited before becoming healthy"
        fi
        sleep 1
    done
    fail "service did not become ready"
}

cd "$repo_root/ai-risk"
export DOCKER_BUILDKIT=1
docker buildx version >/dev/null || fail "Docker Buildx/BuildKit is required"
python -c 'import secrets; print("finbound-build-canary-" + secrets.token_hex(32))' > "$canary_path"
docker build --tag "$image" .
image_archive="$response_dir/image.tar"
docker_archive_path="$image_archive"
if command -v cygpath >/dev/null 2>&1; then
    docker_archive_path="$(cygpath -w "$image_archive")"
fi
docker image save --output "$docker_archive_path" "$image"
python tests/image_security.py "$image_archive" "$canary_path"

[[ "$(docker image inspect --format '{{.Config.User}}' "$image")" == "10001:10001" ]] \
    || fail "runtime user is not UID/GID 10001"
docker image inspect --format '{{json .Config.Healthcheck.Test}}' "$image" \
    | grep -Fq '127.0.0.1:8000/ready' \
    || fail "image healthcheck does not use the readiness endpoint"

image_metadata="$(docker image inspect "$image")"
image_history="$(docker history --no-trunc "$image")"
if grep -Eq 'FINGUARD_INTERNAL_CREDENTIAL|X-FinGuard-Service-Credential' <<<"$image_metadata$image_history"; then
    fail "image metadata or history contains credential material"
fi

docker run --detach \
    --name "$missing_container" \
    --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --security-opt no-new-privileges:true \
    --cap-drop=ALL \
    --publish 127.0.0.1::8000 \
    "$image" >/dev/null
missing_port="$(host_port "$missing_container")"
wait_for_http "$missing_port"
missing_status="$(curl --silent --output "$response_dir/missing-ready.json" --write-out '%{http_code}' "http://127.0.0.1:${missing_port}/ready")"
[[ "$missing_status" == "503" ]] || fail "readiness did not fail closed without a credential"

internal_credential="$(python -c 'import secrets; print(secrets.token_hex(32))')"
docker run --detach \
    --name "$container" \
    --memory 1536m \
    --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --security-opt no-new-privileges:true \
    --cap-drop=ALL \
    --publish 127.0.0.1::8000 \
    --env FINGUARD_INTERNAL_CREDENTIAL="$internal_credential" \
    "$image" >/dev/null
wait_for_healthy
port="$(host_port "$container")"

[[ "$(docker exec "$container" id -u)" == "10001" ]] || fail "running process is not UID 10001"
[[ "$(docker exec "$container" stat -c '%u:%g' /app/app)" == "0:0" ]] \
    || fail "application files are not root-owned"
[[ "$(docker exec "$container" stat -c '%u:%g' /app/models)" == "0:0" ]] \
    || fail "model files are not root-owned"
if docker exec "$container" sh -c 'touch /app/app/.write-test || touch /app/models/.write-test'; then
    fail "runtime user can modify application or model files"
fi

prompt='Review the current customer credit score.'
prompt_hash="$(printf '%s' "$prompt" | sha256sum | cut -d ' ' -f 1)"
curl --fail --silent --show-error \
    --output "$response_dir/prompt.json" \
    --header 'Content-Type: application/json' \
    --header "X-FinGuard-Service-Credential: $internal_credential" \
    --data "{\"agentRunId\":\"RUN-CI\",\"inputRef\":\"INPUT-CI\",\"inputText\":\"$prompt\",\"inputHash\":\"sha256:$prompt_hash\",\"contentLanguage\":\"en\"}" \
    "http://127.0.0.1:${port}/internal/v1/risk/prompt"

curl --fail --silent --show-error \
    --output "$response_dir/behavior.json" \
    --header 'Content-Type: application/json' \
    --header "X-FinGuard-Service-Credential: $internal_credential" \
    --data '{"requestId":"REQ-CI","agentId":"AGENT-CI","agentRunId":"RUN-CI","history":[],"currentAttempt":{"caseId":"CASE-CI","targetConsumerId":"CUST-CI","tool":"CREDIT_SCORE_READ","requestedData":["CREDIT_SCORE"],"requestedAt":"2026-09-04T09:00:00+09:00"}}' \
    "http://127.0.0.1:${port}/internal/v1/risk/behavior"

python - "$response_dir/prompt.json" "$response_dir/behavior.json" <<'PY'
import json
import sys

prompt = json.load(open(sys.argv[1], encoding="utf-8"))
behavior = json.load(open(sys.argv[2], encoding="utf-8"))
assert prompt["modelVersion"] == "prompt-guard-6"
assert isinstance(prompt["detected"], bool)
assert "decision" not in prompt and "inputText" not in prompt
assert behavior["modelVersion"] == "iforest-1"
assert behavior["historyStatus"] == "COLD_START"
assert "decision" not in behavior
PY

logs="$(docker logs "$container" 2>&1)"
if grep -Fq "$internal_credential" <<<"$logs" \
    || grep -Fq "$prompt" <<<"$logs" \
    || grep -Eq 'CASE-CI|CUST-CI' <<<"$logs"; then
    fail "service logs contain a credential, raw prompt or financial payload"
fi

echo "AI Risk container smoke tests passed"
