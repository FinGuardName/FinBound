# FinGuard Git Collaboration Convention

> 목적: FinGuard 팀의 Issue, Branch, Commit, Pull Request, Review, Merge, CI 규칙을 통일한다.

이 문서는 규칙의 **기준**이다.
작업 중에 무엇을 클릭하고 무엇을 입력하는지는 `09-team-workflow-quickstart.md`를 본다.
두 문서가 어긋나면 이 문서가 우선한다.

---

# 1. 기본 협업 흐름

FinGuard는 `main` 중심의 GitHub Flow를 사용한다.

```text
Issue 생성
↓
Issue 기반 Branch 생성
↓
개발 + 테스트
↓
Commit / Push
↓
Pull Request 생성
↓
CI / Test / SonarQube 검사
↓
팀원 Code Review
↓
승인
↓
main Merge
↓
Issue Close
```

핵심 원칙:

> **모든 작업은 Issue에서 시작한다.**

> **main 브랜치에는 직접 Push하지 않는다.**

> **모든 변경은 Pull Request와 Review를 거쳐 main에 Merge한다.**

---

# 2. Issue Convention

## 2.1 작업 전 Issue 생성

기능 개발, 버그 수정, 리팩토링, 문서 수정 등 모든 작업은 시작 전에 Issue를 생성한다.

Issue에는 최소 다음 내용을 작성한다.

```text
제목
작업 목적
작업 내용
완료 조건
관련 기능 ID
담당자
```

예:

```text
[FEAT] Financial Case 생성 기능 구현
```

본문 예:

```markdown
## 목적
LoanAgent 실행 전에 현재 대출심사 Case를 생성한다.

## 작업 내용
- FinancialCase Entity 구현
- Case 생성 API 구현
- ACTIVE 상태 검증
- 단위 테스트 작성

## 관련 기능
- F03 Financial Case 생성

## 완료 조건
- 정상 Case 생성 테스트 통과
- 잘못된 Employee ID 요청 실패 테스트 통과
- API Contract 준수
```

---

## 2.2 Milestone

모든 Issue는 Milestone에 속한다.

Milestone은 개발 Phase 단위로 만들고, 마감일을 지정한다.

```text
Phase 0-1 — Contract Freeze & Independent Mock
```

Milestone을 비워 두면 해당 작업이 Phase 진척도 집계에서 빠진다.
남은 작업량과 마감 임박도를 Milestone 하나로 판단하므로, 누락되면 일정 판단이 실제와 어긋난다.

Milestone은 Issue와 PR **양쪽에 모두** 지정한다.

## 2.3 Project

모든 Issue는 FinGuard Project 보드에 등록한다.

Project는 작업의 **상태**를 추적한다.

```text
Todo → In Progress → Done
```

Label이 작업의 종류를, Milestone이 작업의 마감을, Project가 작업의 현재 상태를 나타낸다.
셋은 서로를 대체하지 않는다.

Project도 Issue와 PR **양쪽에 모두** 등록한다.

## 2.4 Issue 사이드바 정리

Issue 생성 시 지정하는 항목은 다음 네 가지다.

```text
Labels     작업 종류    §5.2
Milestone  작업 마감    §2.2
Projects   작업 상태    §2.3
Assignees  담당자
```

Issue Template은 Label만 자동으로 붙인다. 나머지는 작성자가 지정한다.

---

# 3. Branch Convention

## 3.1 Branch 생성 기준

Branch는 반드시 생성된 Issue를 기준으로 만든다.

```text
main
└─ issue 기반 feature branch
```

장기간 유지하는 `develop` 브랜치는 사용하지 않는다.

## 3.2 Branch 이름

형식:

```text
{type}/{issue-number}-{short-description}
```

예:

```text
feat/12-financial-case
fix/27-policy-decision
refactor/31-risk-service
docs/40-api-contract
test/45-case-scope
chore/52-docker-compose
```

Branch 이름은 영문 소문자와 `-`를 사용한다.

## 3.3 Branch 생성 예

Issue `#12` 기준:

```bash
git checkout main
git pull origin main
git checkout -b feat/12-financial-case
```

작업 완료 후:

```bash
git push -u origin feat/12-financial-case
```

이후 Pull Request를 생성한다.

---

# 4. Commit Convention

## 4.1 Commit Format

```text
type: subject

body
```

예:

```text
feat: Financial Case 생성 기능 추가

- FinancialCase Entity와 Repository 추가
- ACTIVE Case 생성 로직 구현
- 존재하지 않는 Employee 요청 예외 처리
- F03 기능명세와 API Contract를 기준으로 구현
```

---

# 5. Commit Type

| Type | 의미 |
|---|---|
| `fix` | Bug Fix |
| `feat` | 새로운 기능 추가 |
| `refactor` | Bug Fix나 기능 추가가 없는 코드 구조 개선 |
| `docs` | 문서만 변경 |
| `style` | 코드 의미가 변하지 않는 포맷팅, 띄어쓰기, 줄바꿈 등의 변경 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | Build, Dependency, Package Manager, 환경설정 등 Production Code 변경이 없는 작업 |

## 5.1 하나의 Commit에 여러 Type이 포함되는 경우

원칙적으로 하나의 Commit에는 하나의 목적만 담는다.

가능하면 다음처럼 Commit을 분리한다.

```text
feat: Financial Case 생성 기능 추가
test: Financial Case 테스트 추가
docs: Financial Case API 문서 수정
```

불가피하게 하나의 Commit에 여러 Type이 포함되는 경우 다음 우선순위를 사용한다.

```text
fix
>
feat
>
refactor
>
docs
>
style
>
test
>
chore
```

단, **Commit을 분리할 수 있다면 분리하는 것을 우선한다.**

---

# 6. Commit Subject

- 50자를 넘지 않도록 한다.
- 개조식 구문을 사용한다.
- 중요하고 핵심적인 변경사항만 표현한다.
- 마지막에 `.`, `!`, `?` 등 특수문자를 넣지 않는다.
- 불필요한 조사나 장황한 문장을 피한다.

좋은 예:

```text
feat: Financial Case 생성 기능 추가
fix: Case Scope 검증 오류 수정
refactor: Authorization Context 생성 로직 분리
test: Prompt Risk 정책 테스트 추가
```

피해야 할 예:

```text
feat: Financial Case 생성 기능을 추가했습니다.
fix: 오류 수정!!!
update: 코드 수정
```

---

# 7. Commit Body

Body는 선택사항이지만 의미 있는 변경에서는 작성을 권장한다.

규칙:

- 각 항목을 Bullet List로 작성한다.
- 가능하면 한 줄당 72자를 넘지 않는다.
- 필요한 내용을 충분히 작성한다.
- **어떻게 구현했는지보다 무엇을, 왜 변경했는지 설명한다.**

예:

```text
feat: Agent Effective Permission 계산 추가

- Employee Authority보다 Agent 권한이 넓어지는 문제 방지
- Task, Case, Consumer Mandate 교집합으로 Task Passport 생성
- F05 기능명세의 Core Invariant를 기준으로 구현
```

---

# 8. Pull Request Convention

## 8.1 PR 생성 조건

다음 조건을 만족해야 PR을 생성한다.

- Issue가 존재한다.
- Issue 기반 Branch에서 작업했다.
- 기능 구현이 완료됐다.
- 관련 테스트를 작성했다.
- Local Test가 통과한다.
- API Contract를 확인했다.
- Code Convention을 확인했다.

PR 생성 시에도 Issue와 동일하게 사이드바를 지정한다.

```text
Labels     Issue와 같은 값    §5.2
Milestone  Issue와 같은 값    §2.2
Projects   Issue와 같은 값    §2.3
Reviewers  팀원 1명 이상      §10
```

PR Template은 본문만 채운다. Label, Milestone, Project는 자동으로 붙지 않으므로 작성자가 직접 지정한다.
Issue에만 지정하고 PR에 지정하지 않으면, Merge 시점의 진척도와 작업 상태가 보드에 반영되지 않는다.

## 8.2 PR 제목

형식:

```text
[type] 작업 요약
```

예:

```text
[FEAT] Financial Case 생성 기능 구현
[FIX] Case Scope 검증 오류 수정
[REFACTOR] Authorization Service 구조 개선
```

## 8.3 PR 본문 Template

```markdown
## 관련 Issue
- closes #12

## 작업 내용
- FinancialCase Entity 추가
- Case 생성 API 구현
- ACTIVE 상태 검증 추가

## 변경 이유
- LoanAgent 실행 전에 현재 금융업무의 대상 고객과 목적을
  명확하게 고정하기 위해 필요

## 테스트
- [x] 정상 Case 생성
- [x] 존재하지 않는 Employee
- [x] 잘못된 Consumer
- [x] 지원하지 않는 Purpose

## 기능명세
- F03 Financial Case 생성

## API Contract
- [x] `04-api-contract.md` 준수

## 체크리스트
- [x] 테스트 작성
- [x] 전체 테스트 통과
- [x] Code Convention 준수
- [x] SonarQube Major Issue 없음
- [x] Coverage 기준 충족
```

---

# 9. Issue와 PR 연결

PR에는 반드시 관련 Issue를 연결한다.

예:

```text
closes #12
```

또는:

```text
fixes #12
```

PR이 main에 Merge되면 Issue가 자동으로 닫히도록 한다.

---

# 10. Review Convention

모든 PR은 최소 1명 이상의 팀원 Review를 받아야 한다.

```text
작성자 1명
+
Reviewer 1명 이상
```

작성자가 자신의 PR을 승인하고 바로 Merge하는 방식은 사용하지 않는다.

Review 시 확인 항목:

- 기능명세 준수
- API Contract 준수
- Core Invariant 위반 여부
- 테스트 존재 여부
- 예외 처리
- 보안상 문제
- 중복 코드
- Code Smell
- Naming
- 불필요한 변경 포함 여부

---

# 11. Merge Convention

`main`은 Protected Branch로 설정한다.

권장 설정:

```text
Require a pull request before merging
Require approvals
Require status checks to pass
Require conversation resolution
Block force pushes
```

Merge는 PR Review와 CI 통과 후 수행한다.

권장 방식:

```text
Squash and Merge
```

이유:

- Feature Branch의 중간 Commit을 main에 모두 남기지 않는다.
- Issue/PR 단위로 main History를 깔끔하게 유지한다.

팀에서 개별 Commit History 보존이 더 중요하다면 Merge Commit을 사용해도 되지만,
프로젝트 전체에서 하나의 방식을 통일한다.

---

# 12. Test Requirement

## 12.1 매 Step마다 테스트 작성

기능을 모두 구현한 뒤 테스트를 몰아서 작성하지 않는다.

권장 흐름:

```text
기능 Step 구현
↓
해당 Step 테스트 작성
↓
테스트 통과
↓
다음 Step 진행
```

## 12.2 Coverage

PR 기준 테스트 Coverage는 **80% 이상**을 목표로 한다.

```text
Coverage < 80%
→ PR Merge 제한
```

Generated Code, Configuration, 단순 DTO 등 Coverage 제외 대상은 팀이 사전에 합의한다.

권장 도구:

```text
JaCoCo
```

---

# 13. SonarQube Requirement

Code Smell을 최소화한다.

PR 과정에서 SonarQube 분석을 수행한다.

```text
SonarQube Major Issue 발견
→ PR Merge 제한
```

권장 Quality Gate:

- New Bugs = 0
- New Vulnerabilities = 0
- Major 이상 Issue = 0
- Coverage >= 80%
- Duplicated Code 기준 충족

실제 Quality Gate 수치는 팀 합의 후 CI 설정에 반영한다.

---

# 14. Code Convention

Java 코드는 **NAVER HackDay Java Convention**을 기준으로 작성한다.

출처:

```text
https://naver.github.io/hackday-conventions-java/
```

가능하면 Formatter 또는 Checkstyle을 CI에 연결해 자동 검사한다.

---

# 15. FinGuard Additional Requirement

## 15.1 Feature Specification

PR에는 관련 기능 ID를 명시한다.

예:

```text
F11 Financial Case Scope 검사
```

## 15.2 API Contract

DTO / Endpoint / Enum 변경 시 먼저 Contract 변경을 제안한다.

```text
변경 필요
↓
Contract 변경 제안
↓
팀 확인
↓
04-api-contract.md 수정
OpenAPI Freeze 이후에는 api-contract.yaml도 함께 수정
↓
구현 수정
```

## 15.3 Core Invariant

다음 규칙을 위반하는 코드는 Merge하지 않는다.

```text
Agent Effective Permission
⊆
Employee Authority
```

AI Risk가 권한을 새로 부여하거나 확대해서도 안 된다.

---

# 16. 권장 CI Pipeline

```text
Pull Request
↓
Build
↓
Unit Test
↓
Integration Test
↓
Coverage Check
↓
Code Convention Check
↓
SonarQube
↓
Quality Gate
↓
Code Review
↓
main Merge
```

Spring 예:

```text
./gradlew clean test
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
SonarQube Analysis
```

---

# 17. 전체 협업 예시

Issue:

```text
#12 [FEAT] Financial Case 생성 기능 구현
```

Branch:

```text
feat/12-financial-case
```

Commit:

```text
feat: Financial Case 생성 기능 추가

- Employee와 Consumer 기반 Case 생성
- ACTIVE 상태 기본값 적용
- F03 기능명세와 API Contract 기준으로 구현
```

PR:

```text
[FEAT] Financial Case 생성 기능 구현

closes #12
```

검사:

```text
Build              PASS
Test               PASS
Coverage           84%
SonarQube          PASS
Code Convention    PASS
```

Review:

```text
APPROVED
```

최종:

```text
Squash and Merge
↓
main
↓
Issue #12 Close
```

---

# 18. 최종 원칙

```text
Issue 없는 작업 금지

main 직접 Push 금지

테스트 없는 기능 PR 금지

Review 없는 Merge 금지

CI 실패 PR Merge 금지

Contract 임의 변경 금지
```

> **Issue → Branch → Commit → Test → PR → CI → Review → main Merge**
