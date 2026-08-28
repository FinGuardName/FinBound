# Mock Financial API

Backend 3 소유 영역입니다.

지원 Tool은 `CREDIT_SCORE_READ`, `INCOME_READ`, `DEBT_READ`입니다. 가상 데이터만 사용하며 `X-FinGuard-Internal-Credential`이 없는 직접 호출을 거부해야 합니다. Integration Test에서 Tool별 호출 횟수를 확인할 수 있어야 합니다.

## 현재 구현 상태

> **팀 합의 전 임시 계약입니다.** Gateway와의 정식 Contract가 확정되면 Endpoint와 DTO를
> `docs/04-api-contract.md` 기준으로 함께 수정합니다.

```http
POST /internal/v1/finance/tool-calls
X-FinGuard-Internal-Credential: <internal-service-credential>
Content-Type: application/json
```

```json
{
  "requestId": "REQ-001",
  "tool": "CREDIT_SCORE_READ",
  "targetConsumerId": "CUST-1001"
}
```

```json
{
  "requestId": "REQ-001",
  "tool": "CREDIT_SCORE_READ",
  "consumerId": "CUST-1001",
  "result": {
    "creditScore": 812
  }
}
```

현재 임시 구현은 다음 필드를 반환합니다.

| Tool | `result` Field |
|---|---|
| `CREDIT_SCORE_READ` | `creditScore` |
| `INCOME_READ` | `annualIncome` |
| `DEBT_READ` | `totalDebt` |

Mock Finance는 Scope 비교나 `ALLOW/BLOCK` 판단을 하지 않습니다. Gateway가 인가를 마친
요청의 가상 금융 Tool 실행만 담당합니다.

## 가상 데이터

| Consumer | 신용점수 | 연 소득 | 총 부채 |
|---|---:|---:|---:|
| `CUST-1001` | 812 | 85,000,000 | 25,000,000 |
| `CUST-9999` | 735 | 62,000,000 | 41,000,000 |

실제 개인정보나 금융 데이터는 사용하지 않습니다.

## 로컬 실행

PowerShell에서 실제 운영 Credential이 아닌 로컬 개발용 값을 설정합니다.

```powershell
$env:FINGUARD_INTERNAL_CREDENTIAL = "local-development-only"
./gradlew :backend:mock-finance:bootRun
```

기본 Port는 `8083`입니다. `/actuator/health`는 Credential 없이 조회할 수 있지만,
`/internal/v1/finance/**`는 내부 Credential이 필요합니다.

## 검증

```powershell
./gradlew :backend:mock-finance:check
```

테스트는 정상 Tool 3종, Credential 누락·불일치, 지원하지 않는 Tool, 존재하지 않는
Consumer, Tool별 호출 횟수를 검증합니다. 호출 계수는 테스트용 Bean에서 확인하며 외부
운영 Endpoint로 노출하지 않습니다.
