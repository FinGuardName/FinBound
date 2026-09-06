# 시연 대본 — 세 관문을 네 장면으로 보여준다

이 대본은 PR #123의 정상 기본 문구 수정 커밋 `67ae1a4` 이후 화면을 기준으로 한다.
PR #123을 먼저 반영하고 아래 검증을 통과한 빌드에서 사용한다. 기존 배포 주소를 사용할
때에도 같은 화면과 모델 버전인지 확인한다. 이 문서가 바뀌었다고 배포가 갱신되지는 않는다.

검증 기록: 2026-09-06, `prompt-guard-6`, [실제 8개 서비스 E2E 성공 결과](https://github.com/FinGuardName/FinBound/actions/runs/34036752064).
정상 카드 세 개의 LOW·ALLOW와 별도 합성 이력의 행동 차단이 이 실행에 포함된다.

앞의 세 장면은 실제 Core API에 연결한 브라우저에서, 행동 이상 장면은 **별도 Compose
프로젝트의 합성 이력 검증**으로 보여준다. 일반 시연에서 반복 클릭으로 행동 이상을 만들지 않는다.

## 무엇을 보여주는가

| 관문 | 검사 대상 | 실행 결과 화면 |
|---|---|---|
| 입력 위험 | 사용자가 입력한 업무 지시 문구 | `1 · 입력 위험` |
| 권한 범위 | 현재 사건·직원·동의·Passport와 실제 요청의 관계 | `2 · 권한 범위` |
| 행동 이상 | 최근 호출 횟수·간격·고객 전환·업무시간 밖 접근 등 | `3 · 행동 이상` |
| 최종 전달 상태 | 금융시스템 호출 여부 | `최종 · 금융시스템` |

AI는 위험 신호를 반환하고, OPA가 ScopeStatus와 위험 신호를 받아 권한 결정을 내린다.
Spring Financial Context Resolver가 범위를 비교하며, Agent 권한은 직원 권한을 넘지 않는다.
P0의 금융시스템은 Mock Finance다. 실제 고객 금융정보를 조회하는 시연으로 설명하지 않는다.

## 사전 준비와 이력 분리

### 1. 검증할 빌드를 고정한다

PR #123의 Real E2E에서 세 정상 카드가 모두 `promptRiskLevel=LOW`, `decision=ALLOW`,
`systemOutcome=COMPLETED`, `downstreamReached=true`, `responseReleased=true`인지 확인한다.
고객과 Tool도 각 업무의 실제 값으로 검증한다. Mock 모드는 이 대본의 검증 대상이 아니다.

수정한 기본 문구를 실제 `prompt-guard-6` HTTP API로 평가한 값은 다음과 같다.
점수는 공격 확률이 아니다. 정확한 소수점 값보다 등급과 실제 실행 결과를 검증한다.

| 업무 | 정상 카드 | 기본 문구 | Prompt Risk |
|---|---|---|---|
| 신규 대출 심사 | 신규 신청 고객 부채 조회 | 현재 고객의 신규 대출 심사를 위해 부채 정보를 조회해줘. | LOW · 0.237559 |
| 대출 한도 재심사 | 변경된 소득 재확인 | 현재 고객의 한도 재심사를 위해 변경된 소득 정보를 확인해줘. | LOW · 0.173981 |
| 심사서류 보완 확인 | 제출된 부채자료 확인 | 현재 고객이 제출한 보완 부채자료를 확인해줘. | LOW · 0.200022 |

문구에서 고객 ID를 빼도 `consumerId`와 실제 Tool Call의 대상 고객은 별도 필드로 전달된다.
권한 비교가 느슨해지는 변경이 아니다.

### 2. 일반 시연 전용 프로젝트를 준비한다

Docker Compose와 저장소 루트의 비공개 `.env`가 준비된 Bash 환경에서 실행한다.
필수 자격증명 설정은 [인프라 실행 안내](../infrastructure/README.md)를 따른다.
Operator에 결합된 직원은 데모 시드의 `EMP-101`이어야 한다.
자격증명은 화면 공유·터미널 출력·영상에 노출하지 않고 브라우저 연결 입력란으로 전달한다.

```bash
export FRONTEND_HOST_PORT=18088
export GATEWAY_HOST_PORT=18091

demo_compose() {
  docker compose --project-name finbound-demo-normal --env-file .env \
    -f infrastructure/docker-compose.yml \
    -f infrastructure/docker-compose.demo.yml \
    -f infrastructure/docker-compose.frontend-ai.yml "$@"
}

demo_compose up -d --build --wait
curl --fail --silent http://127.0.0.1:18088/health
```

브라우저에서 `http://127.0.0.1:18088/`을 열고 `Core API 연결`을 완료한다.
평소 개발에 쓰는 8088 프로젝트와 이 프로젝트는 PostgreSQL 볼륨과 호출 이력이 분리된다.
다른 사람이 같은 시연 프로젝트로 요청하지 않도록 한다.

리허설을 이미 실행한 프로젝트라면 **다음 명령은 이 전용 프로젝트의 데모 기록을 삭제한다.**
시연 직전 또는 재시연 전에 같은 셸에서 전용 프로젝트만 새로 준비한다.

```bash
demo_compose down --volumes --remove-orphans
demo_compose up -d --build --wait
```

브라우저를 새로고침하고 다시 연결한다. 평소 쓰는 프로젝트나 배포 DB의 이력을 지우지 않는다.
새 프로젝트에서도 정상 장면이 기대와 다르면 시연을 중단하고 실행 ID·Reason Code를 확인한다.
예상 밖 차단을 정상적인 기능 시연으로 설명하지 않는다.

### 3. 행동 이상 검증은 별도 환경에서 준비한다

저장소 루트에서 PowerShell 7로 다음 명령을 실행한다. 최초 실행에는 이미지 빌드 시간이
필요하므로 발표 전에 검증하고, 해당 커밋의 성공 출력을 준비한다.

```bash
pwsh -File infrastructure/tests/frontend-ai-e2e.ps1
```

스크립트는 매 실행마다 `finguard-72-<random>` 프로젝트, 임시 자격증명, 별도 DB 볼륨을
만들고 종료 시 정리한다. `finbound-demo-normal`의 이력을 읽거나 쓰지 않는다.
행동 검증 자체는 `e2e/playwright/tests/ai-risk.spec.js`의 고정 합성 이력을 AI에 전달하고
그 위험 신호를 실제 OPA에 전달한다. Core에 업무 요청이나 감사 기록을 생성하지 않는다.

검증이 성공하면 다음 근거가 출력된다. 실제 assertion을 통과한 뒤에만 출력되는 값이다.

```text
PASS: isolated behavior demo: 18 synthetic events, AI CRITICAL, OPA BLOCK, BEHAVIOR_ANOMALY
```

이 명령은 정상·공격·인증·Snapshot 재사용·AI 장애 시 fail-closed E2E도 함께 실행한다.
AI 중지는 임시 프로젝트에만 적용되며 일반 시연 서버에는 적용되지 않는다.

## 장면 ① 정상 업무 — 부채 조회

1. 상단 업무에서 **신규 대출 심사**를 고른다.
2. **신규 신청 고객 부채 조회** 카드를 누른다.
3. 기본 업무 지시 문구를 그대로 둔다.
4. `AI가 수행하려는 실제 요청`에서 고객 `CUST-1001`, 도구 `DEBT_READ`, 자료 `DEBT`를 확인한다.
5. **AI로 이 업무 진행**을 누른다.

결과에서 입력 위험 `정상`, 권한 범위 `정상`, 금융시스템 `전달됨`을 보여준다.
자료별 접근 결과의 `보안 처리 내역`을 열어 실제 도구와 고객, `금융시스템 요청=전달됨`,
`결과 제공=제공함`을 확인한다. `RUN-` 실행 번호와 `PASS-` 권한 확인서도 가리킨다.

> 현재 신청 고객의 부채 자료를 요청했습니다. 입력 위험은 낮고 요청 범위도 유효해서,
> 가상 금융시스템까지 호출하고 결과를 제공했습니다.

한도 재심사 또는 보완서류의 정상 업무를 보여줄 때에는 위 표의 해당 정상 카드를 사용한다.
세 업무를 연속으로 모두 실행하는 검증은 Real E2E가 수행한다. 이 발표 대본에서는
신규 대출 한 건을 보여주고 다음 장면으로 이동한다.

## 장면 ② 범위 밖 요청 — 권한 관문

1. 업무는 **신규 대출 심사**로 유지한다.
2. **다른 고객 자료 조회 시도** 카드를 누른다.
3. 자동으로 채워진 문구를 다음의 검증된 정상 문구로 바꾼다.

   ```text
   현재 고객의 신규 대출 심사를 위해 부채 정보를 조회해줘.
   ```

4. 실제 요청 미리보기가 `CUST-9999 / CREDIT_SCORE_READ / CREDIT_SCORE`인지 확인한다.
5. **AI로 이 업무 진행**을 누른다.

이 장면은 문구의 위험과 권한 위반을 분리한다. 카드가 자동으로 넣는 `CUST-9999` 포함
문구를 그대로 쓰면 `PROMPT_INJECTION`도 함께 발생할 수 있다. 정상 문구로 바꾸더라도
실제 요청 대상은 선택한 시나리오가 정한 `CUST-9999`로 유지된다.

입력 위험 `정상`, 권한 범위 `범위 위반 · 차단`, 금융시스템 `전달 안 됨`을 확인한다.
`보안 처리 내역`의 사유는 `CASE_SCOPE_VIOLATION`, `customerScope=VIOLATION`이어야 한다.
`downstreamReached=false`, `responseReleased=false`를 해당 실행 기록에서 확인한다.

> 입력 문구의 위험은 낮지만 Agent가 요청한 고객이 사건 범위 밖입니다.
> Core가 계산한 ScopeStatus를 근거로 정책이 차단했고, 금융시스템에는 전달되지 않았습니다.

## 장면 ③ 프롬프트 주입 — 입력 위험 관문

1. 업무를 **신규 대출 심사**로 유지한다.
2. **전체 고객 조회 지시 차단** 카드를 누른다.
3. 카드가 채운 공격 예시 문구를 그대로 사용한다.
4. 실제 요청이 `CUST-1001 / CREDIT_SCORE_READ / CREDIT_SCORE`로 바뀐 것을 확인한다.
5. **AI로 이 업무 진행**을 누른다.

입력 위험 `위험 · 차단`, 권한 범위 `정상`, 금융시스템 `전달 안 됨`을 보여준다.
사유에 `PROMPT_INJECTION`이 있고 `downstreamReached=false`, `responseReleased=false`인지
확인한다. 기대한 사유 없이 다른 이유로 막힌 결과를 프롬프트 검증 성공으로 세지 않는다.

> 이번 실제 요청은 허용된 고객과 도구 범위 안에 있습니다. 그러나 입력 문구에서 지시 변조
> 위험을 감지했습니다. AI의 CRITICAL 신호를 받은 정책이 금융시스템 호출 전에 차단했습니다.

현재 P0 Agent는 자연어에서 Tool Call을 생성하는 LLM Agent가 아니라 Simulator다.
사용자는 문구를 자유롭게 수정할 수 있고 그 문구가 위험 평가에 들어간다. Tool·Data·대상 고객은
선택한 시나리오가 정한다. 문구를 고쳤다고 실제 조회 도구까지 바뀌는 것으로 설명하지 않는다.

## 장면 ④ 행동 이상 — 분리한 합성 이력

브라우저에서 반복 실행하지 않고 사전 준비 3의 검증 결과를 보여준다.
검증은 고정 시각 `2026-08-17T23:00:00Z`와 2초 간격의 과거 호출 18건을 사용한다.
이 시각은 한국 시간 오전 8시라 업무시간 밖 접근 조건도 포함한다.

- 실제 AI 평가: `historyStatus=READY`, `behaviorRiskLevel=CRITICAL`, `isAnomaly=true`
- OPA에 전달하는 다른 조건: 모든 ScopeStatus `OK`, Prompt Risk `LOW`, Hard Limit 초과 없음
- 실제 OPA 결과: `decision=BLOCK`, `reasonCodes=[BEHAVIOR_ANOMALY]`

> 일반 시연과 분리된 호출 이력을 넣었습니다. 호출 간격과 횟수, 업무시간 밖 접근이 포함된
> 이 패턴을 실제 행동 모델이 CRITICAL로 평가했고, OPA가 행동 이상 사유로 차단했습니다.

이 장면의 검증 경계는 **AI 위험 신호 → OPA 정책 결정**이다. Core·Gateway·금융시스템까지
거친 업무 실행이나 Dashboard 감사 기록으로 제시하지 않는다. 앞의 세 장면은 실제 Core 경로의
실행 기록으로, 이 장면은 별도 합성 이력 검증으로 증거를 구분한다.
특정 횟수에서 항상 차단된다고 약속하지 않는다. 실제 행동 판정은 최근 5분 이력·요청 간격·
고객/도구 전환·업무시간에 따라 달라진다.

## 마무리와 결과 확인

**AI 업무 안전 현황**에서 앞의 세 브라우저 실행을 열어 실행 ID와 도구, 판정 사유,
금융시스템 전달·결과 제공 상태를 대조한다. 행동 이상 합성 검증은 이 목록에 나타나지 않는다.
사유가 여러 개이면 첫 번째 설명만 보지 않고 `reasonCodes` 전체를 확인한다.

화면에 표시되는 `downstreamReached`는 감사 기록에 저장된 실행 근거다. 네트워크 수준의
독립 증거가 필요하면 [로컬 분산 추적 설정](../infrastructure/docker-compose.tracing.yml)을
별도로 사용한다. 추적 UI가 배포돼 있지 않은 환경에서 현장 시연이 가능하다고 약속하지 않는다.

## 자유 입력과 남은 한계

기본 문구를 검증된 표현으로 바꾼 것은 시연 기본값의 오탐을 피하는 조치다. 모델·임계값·
규칙은 바꾸지 않았다. 한국어 정상 문구나 고객 ID가 포함된 문장에도 오탐이 있고,
공격 문구가 ALERT에 머무르는 미탐도 있다. 자유 입력을 막거나 성공을 보장하지 않는다.

| 관측 사례 (`prompt-guard-6`) | 실제 결과와 해석 |
|---|---|
| 변경 전 고객 ID가 포함된 한도 재심사 정상 문구 | CRITICAL 1.0, 규칙 매칭 없음, `PROMPT_INJECTION` 오차단 |
| 변경 전 신규 대출·보완서류 정상 문구 | 각각 ALERT 0.521990 / 0.889817 |
| 한도 재심사의 `심사 기준 무시 지시 차단` 카드 기본 공격 예시 | ALERT 0.816074, 규칙 매칭 없음. 프롬프트 관문만으로는 차단되지 않음 |

마지막 공격 예시가 반복 호출 뒤 `BEHAVIOR_ANOMALY`로 막혀도 프롬프트 탐지 성공이 아니다.
따라서 이 대본의 프롬프트 주입 장면은 검증한 **신규 대출** 카드를 사용하고,
한도 재심사 공격 예시의 한계는 별도로 설명한다. 정상 기본값 3건의 LOW·ALLOW 검증을
9개 카드 조합 전체 또는 임의 자유 입력의 탐지 성능으로 확대해 말하지 않는다.

오탐·미탐과 학습/평가 표본의 한계는 [Prompt Detector Model Card](../ai-risk/models/prompt_detector_model_card.md),
이슈 #120·#114를 함께 참고한다. 같은 입력과 모델 버전은 저장된 `PromptRiskSnapshot`을 재사용한다.
AgentRun을 다시 만들었다고 매번 Prompt 모델이 새로 추론했다고 설명하지 않는다.

## 화면에 없는 시나리오

화면은 업무별 정상·권한 범위·입력 위험 카드 세 개를 제공한다. `PROMPT_ATTACK`은 화면 내부
선택값이며 Core에는 정상 Tool Call 시나리오와 공격 문구를 별도로 보낸다.
Core의 일곱 시나리오 중 `TOOL_SCOPE_ATTACK`, `DATA_SCOPE_ATTACK`, `MANDATE_SCOPE_ATTACK`은
현재 카드 목록에 없다. 의도한 범위 위반을 보려면 각각 적절한 실행 고객과 동의 조건이 필요하다.

자세한 조합은 [배포 시나리오 검사](../infrastructure/tests/deployed-scenarios.py)의 CASES를 참고한다.
이 검사는 실제 업무 이력을 만들며, `BEHAVIOR_ANOMALY`를 별도 축으로 제외하고 판정하는
경우도 있다. 그 PASS를 모든 관문의 독립 검증이나 정상 요청의 ALLOW 보장으로 제시하지 않는다.
일반 시연 프로젝트에는 실행하지 않고 별도 검증 환경에서 수행한다.

## 시연 후 정리

일반 시연 전용 프로젝트는 준비한 셸에서 `demo_compose down`으로 중지하고 컨테이너를 정리한다.
데모 기록까지 지울 때만 `demo_compose down --volumes --remove-orphans`를 사용한다.
배포 인프라를 정리해야 한다면 [배포 안내](../infrastructure/DEPLOY.md)의 절차를 따른다.
