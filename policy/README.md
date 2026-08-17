# OPA Policy

Backend 2 소유 영역입니다. Rego는 Core API가 계산한 `ScopeStatus`와 AI Risk, Hard Limit만 조합합니다. raw Case/Consumer/Tool/Data 비교는 금지합니다.

```bash
opa test policy -v
```

기본 원칙은 Fail-closed이며 모든 BLOCK은 하나 이상의 Reason Code를 반환합니다.
