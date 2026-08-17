# Infrastructure

P0는 Docker Compose, P1은 Kubernetes hardening입니다. 초기 Compose는 계약/정책 개발을 위한 PostgreSQL과 OPA를 제공합니다. 서비스별 Dockerfile이 추가되면 Core API, Gateway, Agent, Mock Finance, AI Risk, Frontend를 이 파일에 연결합니다.

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d
docker compose -f infrastructure/docker-compose.yml config
```

`infrastructure/kubernetes`는 P0 Release Gate가 아닙니다.
