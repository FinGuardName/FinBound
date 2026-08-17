## 관련 Issue

- closes #<!-- issue number -->

## 작업 내용

<!-- 같은 기능 Issue의 완료에 필요한 구현·테스트·문서·Contract 변경을 적어 주세요. -->

## 변경 이유

<!-- 무엇을 왜 변경했는지 설명해 주세요. -->

## 관련 범위

- 기능/인수 조건 ID: <!-- 예: F11, AC-02 -->
- 담당 영역: <!-- core-api / gateway / agent / mock-finance / audit / ai-risk / frontend / policy / infrastructure -->

## 검증

- [ ] Issue 기반 Branch(`{type}/{issue-number}-{description}`)에서 작업했습니다.
- [ ] 관련 Unit/Contract/Integration/E2E 테스트를 추가하거나 갱신했습니다.
- [ ] Local Test와 적용 가능한 CI가 모두 통과했습니다.
- [ ] ALLOW/BLOCK/ERROR 의미를 구분했습니다.
- [ ] BLOCK 요청의 downstream 미도달을 확인했습니다.
- [ ] Credential, 원본 Prompt, 금융 Payload를 로그/Audit에 남기지 않습니다.
- [ ] Contract 변경 시 기준 문서를 먼저 갱신했습니다.
- [ ] Scope 비교를 Context Resolver와 Rego에 중복 구현하지 않았습니다.
- [ ] Gateway에서 PostgreSQL에 직접 접근하지 않습니다.
- [ ] 관련 없는 변경은 별도 Issue/Branch/PR로 분리했습니다.

## 리뷰 전 확인

- [ ] Reviewer가 확인할 핵심 파일 또는 판단 지점을 설명했습니다.
- [ ] 모든 Review 대화를 해결한 뒤 Merge합니다.
- [ ] Coverage/SonarQube는 해당 CI가 구성된 영역에서 기준을 충족했습니다.

## 테스트 결과

```text
실행한 명령과 결과를 적어 주세요.
```
