# Security Policy

## 민감정보 원칙

다음 값은 Issue, Pull Request, 로그, Audit, 테스트 Fixture에 올리지 않습니다.

- 실제 개인정보와 금융 데이터 원문
- 원본 Prompt와 금융 API 응답 Payload
- Agent Service Credential, 내부 Credential, API Key
- 운영 환경의 URL이나 Secret

MVP는 가상 금융 데이터만 사용합니다. 노출을 발견하면 공개 Issue 대신 조직 관리자에게 비공개로 알리고, 해당 Credential을 즉시 폐기·교체합니다.

## 보안 불변식

- Agent 권한은 Employee Authority를 초과할 수 없습니다.
- 인증·Context·Risk·Policy 필수 서비스 오류는 Fail-closed 처리합니다.
- BLOCK 요청은 Mock Financial API에 도달하지 않아야 합니다.
- Dashboard는 PostgreSQL에 직접 연결하지 않습니다.
