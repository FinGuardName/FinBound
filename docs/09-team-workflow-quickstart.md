# FinGuard 협업 워크플로 실행 가이드

> 목적: Issue 하나를 `develop`에 Merge하기까지 실제로 무엇을 클릭하고 무엇을 입력하는지 순서대로 안내한다.

이 문서는 **실행 가이드**다. 규칙의 근거와 전체 규범은 `08-git-collaboration-convention.md`에 있다.
둘이 어긋나면 `08`이 기준이다.

작업 한 건의 전체 모습은 다음과 같다.

```text
1. Issue 생성          Label · Milestone · Project 지정
2. Branch 생성         Issue 화면에서, source = develop
3. 작업 · Commit · Push
4. PR 생성             base = develop, Label · Milestone · Project 지정
5. 게이트 통과         CI 6개 + Review 1명
6. Squash Merge
7. Issue 수동 Close    ← 자동으로 닫히지 않는다
```

`main`과 `develop`에는 직접 Push할 수 없다. 저장소 설정으로 막혀 있다.

---

# 0. 시작 전 1분

```bash
git checkout develop
git pull origin develop
```

`develop`이 모든 작업의 출발점이다. `main`에서 분기하지 않는다.

---

# 1. Issue 생성

## 1.1 템플릿 선택

저장소 → **Issues** → **New issue**. 템플릿 3개 중 하나를 고른다.

| 템플릿 | 쓰는 때 | 자동 Label |
|---|---|---|
| Feature | 새 기능 추가 | `feat` |
| Bug | 결함 수정 | `fix` |
| Task | 그 외 (`chore` `refactor` `test` `style`) | `chore` |

Task 템플릿에서 작업 유형을 `chore`가 아닌 값으로 골랐다면, **등록 후 Label을 그에 맞게 직접 바꾼다.**
템플릿은 Label을 하나만 자동으로 붙일 수 있다.

## 1.2 오른쪽 사이드바 3개를 채운다

Issue 작성 화면 오른쪽에 있다. **템플릿이 대신 채워 주지 않으므로 직접 선택한다.**

```text
Labels     작업 유형 (feat / fix / docs / chore / refactor / test / style)
Milestone  현재 진행 중인 Phase
Projects   FinGuard 보드
Assignees  본인
```

- **Label**은 Commit Type과 같은 어휘를 쓴다. `08` §5.2 참고.
- **Milestone**은 그 작업이 어느 Phase 마감에 속하는지를 나타낸다. 비워 두면 Phase 진척도에서 그 작업이 통째로 빠진다.
- **Project**는 보드에서 작업 상태(Todo / In Progress / Done)를 추적하는 용도다.

## 1.3 본문

템플릿의 항목을 지우지 않는다. 최소한 다음이 채워져야 한다.

```text
목적          왜 이 작업이 필요한가
작업 내용     체크박스 목록
완료 조건     무엇이 되면 끝인가
관련 기능 ID  F03, AC-02 같은 식별자
```

---

# 2. Branch 생성

## 2.1 Issue 화면에서 만든다

생성된 Issue 화면 오른쪽 아래 **Development** → **Create a branch**.

이렇게 만들면 Issue와 Branch가 GitHub 내부에서 연결되고, Issue 화면에 Branch가 표시된다.
로컬에서 `git checkout -b`로 만들면 이 연결이 생기지 않는다.

## 2.2 반드시 source를 `develop`으로 바꾼다

다이얼로그에서 **Change branch source**를 눌러 `develop`을 선택한다.

> 기본값은 `main`이다. 이 저장소의 Default Branch가 `main`이기 때문이다.
> 그대로 두면 `main`에서 분기하게 되고, PR에 `develop`에 없는 변경이 섞여 들어간다.

## 2.3 이름

GitHub이 제안하는 이름을 다음 형식으로 고친다.

```text
{type}/{issue-number}-{short-description}
```

```text
feat/12-financial-case
fix/27-policy-decision
docs/40-api-contract
```

영문 소문자와 `-`만 쓴다. `{type}`은 Issue의 Label과 같은 값이다.

## 2.4 로컬로 가져온다

```bash
git fetch origin
git checkout feat/12-financial-case
```

---

# 3. 작업 · Commit · Push

## 3.1 Commit 메시지

```text
{type}: {제목}

{본문}
```

```text
feat: Financial Case 생성 API 구현

LoanAgent 실행 전 대상 고객과 목적을 고정하기 위해 필요하다.
ACTIVE 상태 검증을 함께 추가했다.
```

제목은 50자 이내, 마침표 없이, 명령형으로 쓴다. 자세한 규칙은 `08` §4~§7.

## 3.2 Push 전에 로컬에서 확인한다

CI에서 처음 실패를 보는 것보다 로컬에서 먼저 보는 편이 빠르다.

| 담당 영역 | 명령 |
|---|---|
| backend | `./gradlew check` |
| ai-risk | `pytest` |
| frontend | `pnpm build` |
| policy | `docker run --rm -v "$PWD:/workspace" openpolicyagent/opa:1.17.0-static test /workspace/policy -v` |

`./gradlew check`는 테스트 + Checkstyle + Coverage 검증을 한 번에 돌린다. CI의 `backend` 잡과 같은 기준이다.

> **JDK 21이 필요하다.** Gradle 데몬 JVM 자체가 17 이상이어야 한다.
> `java -version`이 낮은 버전을 가리키면 `JAVA_HOME`을 JDK 21로 지정한 뒤 실행한다.

## 3.3 Push

```bash
git push -u origin feat/12-financial-case
```

---

# 4. PR 생성

## 4.1 base가 `develop`인지 확인한다

```text
base: develop  ←  compare: feat/12-financial-case
```

> GitHub은 base를 Default Branch인 `main`으로 먼저 제안한다. **매번 `develop`으로 바꿔야 한다.**
> `main`은 Release PR만 받는다.

## 4.2 제목

```text
[TYPE] 작업 요약
```

```text
[FEAT] Financial Case 생성 기능 구현
```

## 4.3 본문

`.github/pull_request_template.md`가 자동으로 채워진다. 항목을 지우지 않고 채운다.

첫 줄의 Issue 번호를 반드시 넣는다.

```text
closes #12
```

## 4.4 오른쪽 사이드바 3개를 또 채운다

**여기가 가장 자주 누락되는 지점이다.** PR 템플릿은 본문만 채울 수 있고
Label · Milestone · Project는 자동으로 붙지 않는다.

```text
Labels     Issue와 같은 값
Milestone  Issue와 같은 값
Projects   Issue와 같은 값
```

Issue에 붙인 값을 PR에도 똑같이 붙인다고 기억하면 된다.

## 4.5 Reviewer 지정

**Reviewers**에서 팀원 최소 1명을 지정한다. 지정하지 않으면 아무도 PR이 올라온 걸 모른다.
본인은 Reviewer가 될 수 없다.

---

# 5. 통과해야 하는 게이트

`main`과 `develop`은 Branch Ruleset `protect main and develop`으로 보호된다.
아래는 저장소에 **실제로 적용된 설정**이다.

## 5.1 필수 CI 체크 6개

전부 초록이어야 Merge 버튼이 열린다.

```text
backend                   Gradle test + Checkstyle + Coverage + SonarQube 분석 실행
ai-risk                   pytest
frontend                  pnpm build
policy                    opa test
repository-contract       필수 파일 존재 · .env 커밋 여부
SonarCloud Code Analysis  Quality Gate
```

Coverage는 **LINE 80%** 이며 `backend` 잡 안에서 검증된다. 미달이면 그 잡이 실패한다.

## 5.2 `SonarCloud Code Analysis`는 `backend` 잡에 딸려 있다

체크 목록에서는 6개가 나란히 보이지만, `SonarCloud Code Analysis`는 독립된 잡이 아니다.
`backend` 잡의 마지막 스텝에서 `./gradlew sonar`가 실행되고, 그 결과를 SonarCloud가 받아
별도 체크로 올리는 구조다.

```text
backend 잡 시작
  ./gradlew check          테스트 · Checkstyle · Coverage
  ./gradlew sonar          분석 결과 전송
                             └→ SonarCloud Code Analysis 체크 생성
backend 잡 종료
```

여기서 나오는 결과는 다음과 같다.

- **`backend`가 실패하면 `SonarCloud Code Analysis`는 아예 뜨지 않는다.** 실패가 아니라 부재다.
  체크 5개만 보이고 하나가 계속 안 나타난다면 Sonar가 느린 게 아니라 `backend`를 먼저 고쳐야 한다.
- `backend`보다 `SonarCloud Code Analysis`가 조금 늦게 끝난다. `backend`가 초록이 된 뒤 잠시 기다린다.

## 5.3 Review

```text
승인 1건 이상
```

## 5.4 자주 걸리는 4가지

| 설정 | 실제로 겪게 되는 일 |
|---|---|
| 승인 후 Push하면 승인이 무효화된다 | 리뷰를 받은 뒤 커밋을 추가하면 승인이 사라진다. **다시 받아야 한다.** |
| 마지막 Push한 사람은 그 PR을 승인할 수 없다 | 리뷰어가 직접 코드를 고쳐 주면 그 사람의 승인이 무효가 된다. 수정은 작성자가 한다. |
| Review 대화를 전부 Resolve해야 한다 | 답글만 달고 넘어가면 Merge 버튼이 열리지 않는다. **Resolve conversation**을 눌러야 한다. |
| `develop`이 최신이어야 한다 | 다른 PR이 먼저 Merge되면 내 PR에 **Update branch** 버튼이 뜬다. 누르면 CI가 다시 돈다. |

## 5.5 Merge 방식

**Squash and merge만 허용된다.** Create a merge commit과 Rebase and merge 버튼은 비활성 상태다.
Branch의 중간 Commit은 `develop`에 남지 않고 하나로 합쳐진다.

Force push와 Branch 삭제도 `main` · `develop`에서는 차단된다.

---

# 6. Merge 후: Issue를 직접 닫는다

**PR 본문에 `closes #12`를 썼어도 Issue는 자동으로 닫히지 않는다.**

GitHub의 자동 Close는 PR이 **Default Branch(`main`)에 Merge될 때만** 동작한다.
FinGuard의 모든 작업 PR은 `develop`을 base로 하므로 이 조건을 만족하지 않는다.

같은 이유로 Issue 화면에 "이 PR이 이 Issue를 닫습니다" 표시도 뜨지 않는다. 단순 참조로만 잡힌다.

## 할 일

```text
1. Squash Merge 완료
2. Issue 화면으로 이동
3. Close issue 클릭
```

Branch는 직접 지우지 않아도 된다. 저장소가 `delete_branch_on_merge`로 설정돼 있어
Merge와 동시에 원격 Branch가 삭제된다. 로컬에 남은 Branch만 정리한다.

```bash
git checkout develop
git pull origin develop
git branch -d feat/12-financial-case
```

`closes #12` 표기는 그대로 유지한다. 자동 Close가 안 될 뿐, Issue와 PR을 잇는 표시로는 계속 쓰인다.

> `develop`이 Release PR로 `main`에 Merge될 때 자동 Close가 뒤늦게 동작할 수 있다.
> 그때까지 열어 두지 않는다. 작업이 끝난 시점에 닫는다.

---

# 7. 체크리스트

작업 한 건을 시작하고 끝낼 때 이것만 확인하면 된다.

**Issue를 만들 때**

- [ ] 템플릿을 골랐다
- [ ] Label · Milestone · Project · Assignee를 지정했다
- [ ] 완료 조건을 적었다

**Branch를 만들 때**

- [ ] Issue 화면의 Create a branch로 만들었다
- [ ] source를 `develop`으로 바꿨다
- [ ] 이름이 `{type}/{issue-number}-{description}` 형식이다

**PR을 올릴 때**

- [ ] base가 `develop`이다
- [ ] 본문에 `closes #{issue-number}`가 있다
- [ ] Label · Milestone · Project를 지정했다
- [ ] Reviewer를 1명 이상 지정했다
- [ ] 로컬 검증을 돌렸다

**Merge한 뒤**

- [ ] Issue를 직접 Close했다
- [ ] 로컬 Branch를 정리했다 (원격은 자동 삭제된다)

---

# 8. 더 알아야 할 때

| 궁금한 것 | 문서 |
|---|---|
| Commit Type · Label 어휘 전체 | `08-git-collaboration-convention.md` §5 |
| PR 본문 항목별 작성 예시 | `08-git-collaboration-convention.md` §8.3 |
| Review에서 무엇을 볼 것인가 | `08-git-collaboration-convention.md` §10 |
| Coverage · SonarQube 기준의 근거 | `08-git-collaboration-convention.md` §12 · §13 |
| Code Convention | `08-git-collaboration-convention.md` §14 |
| 어떤 모듈을 누가 맡는가 | `05-development-guide.md` |
| API · DTO 계약 | `04-api-contract.md` |
