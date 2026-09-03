# Mock Financial API

Backend 3 소유 영역입니다.

지원 Tool은 `CREDIT_SCORE_READ`, `INCOME_READ`, `DEBT_READ`입니다. 가상 데이터만 사용하며 `X-FinGuard-Internal-Credential`이 없는 직접 호출을 거부해야 합니다. Integration Test에서 Tool별 호출 횟수를 확인할 수 있어야 합니다.

## 현재 구현 상태

P0 Runtime Contract입니다. Gateway는 Compose 내부 주소 `http://mock-finance:8083`과
`X-FinGuard-Internal-Credential`을 사용합니다.

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

## 컨테이너 실행

이미지는 저장소 루트를 Build Context로 사용합니다. 이미지 빌드에는 Credential을 전달하지
않으며, `Dockerfile.dockerignore`가 개인용 `gradle.properties`, `.env`, 다른 모듈과 빌드
산출물을 Context에서 제외합니다.

Docker Buildx/BuildKit이 필수입니다. Legacy Builder는 지원하지 않으며,
`Dockerfile.dockerignore`가 적용되도록 `DOCKER_BUILDKIT=1`로 빌드합니다.

```bash
DOCKER_BUILDKIT=1 docker build -f backend/mock-finance/Dockerfile -t finguard-mock-finance:local .

export FINGUARD_INTERNAL_CREDENTIAL="$(openssl rand -base64 32)"
docker run --rm --name finguard-mock-finance \
  --memory=512m \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --security-opt no-new-privileges:true \
  --cap-drop=ALL \
  -p 127.0.0.1:8083:8083 \
  -e FINGUARD_INTERNAL_CREDENTIAL \
  finguard-mock-finance:local
```

`FINGUARD_INTERNAL_CREDENTIAL`은 Runtime에만 주입하며, 없으면 설정 검증 단계에서 기동이
실패합니다. `/actuator/health`는 Credential 없이 조회할 수 있지만
`/internal/v1/finance/tool-calls`는 Credential이 없으면
`401 INTERNAL_CREDENTIAL_INVALID`로 거부됩니다.

```bash
curl -fsS http://localhost:8083/actuator/health
docker run --rm --entrypoint java finguard-mock-finance:local -version
docker run --rm --entrypoint id finguard-mock-finance:local -u
docker history --no-trunc finguard-mock-finance:local
```

Runtime은 Java 21, UID/GID `10001`, 컨테이너 메모리 상한의 75% 이하 Heap으로 동작합니다.
위 `docker run` 예시는 512 MiB 상한을 명시합니다. Compose 서비스 연결은 Issue #62에서
담당합니다.

Dockerfile Frontend와 JDK/JRE 베이스는 digest로 고정하고, Gradle 8.14.3 배포본은
Wrapper의 `distributionSha256Sum`으로 새 다운로드를 검증합니다. 베이스 업데이트 시
두 Dockerfile의 digest를 함께 갱신하고 스모크를 재실행합니다. Maven 의존성 전체의
잠금·검증은 별도 공통 빌드 작업 범위입니다.

#62에서 Gateway를 연결할 때 주소는 `http://mock-finance:8083`을 사용합니다.
Agent의 `8082`와 혼동하지 않아야 합니다.

## 검증

```powershell
./gradlew :backend:mock-finance:check
```

테스트는 정상 Tool 3종, Credential 누락·불일치, 지원하지 않는 Tool, 존재하지 않는
Consumer, Tool별 호출 횟수를 검증합니다. 호출 계수는 테스트용 Bean에서 확인하며 외부
운영 Endpoint로 노출하지 않습니다.

이미지 전체 스모크 테스트는 저장소 루트에서 다음과 같이 실행합니다.

```bash
bash infrastructure/tests/service-container-smoke.sh
```

Git, Bash, Python 3.12 이상(표준 라이브러리만 사용), curl, OpenSSL이 필요합니다.
스모크는 Internal Credential을 보낸 `CUST-1001`의 `CREDIT_SCORE_READ` 요청이 `200`을
반환하고, `requestId`, `tool`, `consumerId`, `result.creditScore=812`가 계약과 일치하는지
확인합니다. 응답 원문이나 Credential은 출력하지 않습니다.

또한 빌드 전 임시 Context에 심은 가짜 자격증명이 모든 이미지 Layer와 압축된 JAR 내부에
없는지 확인합니다. 나중 Layer에서 삭제된 파일도 검사하며, 원래 `.env`나 개인 Gradle
설정은 변경하지 않습니다. Canary 유입 방지 검사이므로 임의의 모든 Secret 탐지를 보장하지
않습니다. 자세한 검증 범위와 Windows Git Bash 실행 방법은 `backend/agent/README.md`를
참고합니다.
