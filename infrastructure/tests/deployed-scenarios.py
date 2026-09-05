#!/usr/bin/env python3
"""배포된 데모에서 Agent Simulation Scenario 일곱 개를 전부 돌려 판정을 대조한다.

CI 의 Playwright E2E 는 소스에서 띄운 토폴로지를 검증한다. 이 스크립트는 **실제로
배포된 주소**를 검증한다. 둘은 다른 것을 본다 — ADR 0004 의 "CI E2E 형상 = 배포 형상"
이 과장이었다는 기록 참조.

    sudo python3 deployed-scenarios.py https://<host>

자격증명은 /opt/finbound/.env 에서 읽고 화면에 찍지 않는다. 그래서 sudo 가 필요하다.
"""
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

ENV_PATH = "/opt/finbound/.env"

# 값은 backend/agent 의 AgentSimulationScenario 와 같아야 한다.
# 정상 셋은 ALLOW, 공격 넷은 BLOCK 이 나와야 한다.
SCENARIOS = [
    ("NORMAL_CREDIT_SCORE", "ALLOW"),
    ("NORMAL_INCOME", "ALLOW"),
    ("NORMAL_DEBT", "ALLOW"),
    ("CASE_SCOPE_ATTACK", "BLOCK"),
    ("TOOL_SCOPE_ATTACK", "BLOCK"),
    ("DATA_SCOPE_ATTACK", "BLOCK"),
    ("MANDATE_SCOPE_ATTACK", "BLOCK"),
]


def read_credential():
    with open(ENV_PATH, encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("OPERATOR_CREDENTIAL="):
                return line.split("=", 1)[1].strip()
    sys.exit(f"{ENV_PATH} 에 OPERATOR_CREDENTIAL 이 없다")


def call(base, path, credential, body=None, timeout=30):
    request = urllib.request.Request(
        base + path,
        data=json.dumps(body).encode() if body is not None else None,
        method="POST" if body is not None else "GET",
        headers={
            "Authorization": f"Bearer {credential}",
            "Content-Type": "application/json",
            "X-Request-Id": str(uuid.uuid4()),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        return error.code, {"body": error.read().decode("utf-8", "replace")[:200]}


def audit_for(base, credential, agent_run_id, deadline=90):
    """감사 기록이 COMPLETED 로 확정될 때까지 기다린다."""
    end = time.time() + deadline
    while time.time() < end:
        status, page = call(
            base, "/core-api/api/v1/audit-events?page=1&pageSize=100", credential
        )
        if status == 200:
            for item in page.get("items", []):
                if item.get("agentRunId") == agent_run_id:
                    if item.get("status") == "COMPLETED" or item.get("decision"):
                        return item
        time.sleep(3)
    return None


def main():
    if len(sys.argv) != 2:
        sys.exit("사용법: deployed-scenarios.py https://<host>")
    base = sys.argv[1].rstrip("/")
    credential = read_credential()

    failures = 0
    print(f"{'시나리오':<22} {'기대':<6} {'실제':<8} {'판정'}")
    print("-" * 52)

    for scenario, expected in SCENARIOS:
        status, created = call(
            base,
            "/core-api/api/v1/agent-runs",
            credential,
            {
                "employeeId": "EMP-101",
                "consumerId": "CUST-1001",
                "taskType": "LOAN_REVIEW",
                "scenario": scenario,
                "inputText": f"deployed scenario check {scenario}",
            },
        )
        if status != 201:
            print(f"{scenario:<22} {expected:<6} {'HTTP ' + str(status):<8} FAIL")
            failures += 1
            continue

        agent_run_id = created.get("agentRunId", "")
        audit = audit_for(base, credential, agent_run_id)
        if audit is None:
            print(f"{scenario:<22} {expected:<6} {'(없음)':<8} FAIL  감사 기록 미확정")
            failures += 1
            continue

        actual = audit.get("decision") or "(없음)"
        ok = actual == expected
        failures += 0 if ok else 1
        note = "" if ok else "  " + ",".join(audit.get("reasonCodes") or [])
        print(f"{scenario:<22} {expected:<6} {actual:<8} {'PASS' if ok else 'FAIL'}{note}")

    print("-" * 52)
    if failures:
        sys.exit(f"{failures}개 시나리오가 기대와 다르다")
    print("일곱 개 시나리오 전부 기대대로 판정됐다")


if __name__ == "__main__":
    main()
