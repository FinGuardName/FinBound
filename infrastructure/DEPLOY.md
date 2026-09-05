# 배포 절차

EC2 한 대에 여덟 서비스를 올린다. 이미지는 GitHub Actions가 GHCR에 굽고, SSM Send-Command가
EC2에서 받아 실행하고, Caddy가 HTTPS를 씌운다. **왜 이 방식인지는 [ADR 0004](../docs/adr/0004-deploy-to-one-ec2-with-actions-and-ssm.md)에 있다.**

이 문서는 "무엇을 어떤 순서로 누르는가"만 다룬다.

---

## 0. 먼저 읽을 것 — 아키텍처 제약

**인스턴스는 반드시 x86_64여야 한다.** `publish-images.yml`이 `linux/amd64` 이미지만 만든다.
**Graviton(`t4g`, arm64)에서는 pull은 되고 실행이 안 된다.** 20% 싸서 눈에 띄지만 지금은 못 쓴다.

## 1. IAM 역할 두 개

### ① 인스턴스용 — SSM이 명령을 넣을 수 있게 한다

```
신뢰할 수 있는 엔터티 : AWS 서비스 → EC2
정책                 : AmazonSSMManagedInstanceCore
이름                 : finbound-ec2-ssm
```

### ② GitHub Actions용 — OIDC로 맡는다

GitHub OIDC 공급자(`token.actions.githubusercontent.com`)를 IAM에 만들고, 이 저장소에서만
맡을 수 있는 역할을 만든다. 권한은 SSM 명령 전송과 결과 조회면 충분하다.

```
ssm:SendCommand           대상 인스턴스 + AWS-RunShellScript 문서
ssm:GetCommandInvocation  전체
ssm:ListCommandInvocations 전체
```

**신뢰 정책의 `sub`를 저장소와 브랜치로 좁힌다.** 넓게 두면 다른 저장소가 이 역할을 맡는다.
역할 ARN은 뒤에서 저장소 시크릿에 넣는다.

## 2. EC2 인스턴스

| 항목 | 값 |
|---|---|
| 리전 | `ap-northeast-2` |
| AMI | **Amazon Linux 2023, x86_64** |
| 유형 | **`t3.medium`** |
| 스토리지 | 40GB gp3 |
| 키 페어 | **없음** (접속은 Session Manager) |
| IAM 인스턴스 프로필 | `finbound-ec2-ssm` |
| 서브넷 | 인터넷 게이트웨이가 있는 퍼블릭 서브넷 |

**크기 근거는 실측이다.** 여덟 서비스를 띄우고 데모 경로로 AgentRun 60건을 만든 뒤 합계
**1.82 GiB**였다(`ai-risk` 904MiB가 지배적). Caddy와 OS를 더해 약 2.3GiB, `t3.medium`에서
1.7GiB가 남는다. 자세한 표는 ADR 0004.

### 보안 그룹

인바운드는 **둘뿐이다.**

| 유형 | 포트 | 소스 |
|---|---|---|
| HTTP | 80 | `0.0.0.0/0` |
| HTTPS | 443 | `0.0.0.0/0` |

**22번을 열지 않는다.** 80이 필요한 이유는 Let's Encrypt HTTP-01 challenge다 — 닫으면
인증서를 못 받는다.

**아웃바운드는 전체 허용(기본값)을 유지한다.** GHCR pull, Let's Encrypt, 그리고 **SSM 에이전트가
AWS 엔드포인트에 붙는 데** 필요하다. 막으면 배포 경로 자체가 죽는다.

### 탄력적 IP

할당하고 연결한다. `sslip.io`로 HTTPS를 쓰려면 주소가 고정이어야 한다 — 중지·시작하면
일반 퍼블릭 IP는 바뀌고 인증서가 깨진다.

주소가 `3.35.1.2`면 호스트명은 `3-35-1-2.sslip.io`다.

### 사용자 데이터

```bash
#!/bin/bash
set -euxo pipefail

dnf install -y docker git
systemctl enable --now docker

# Docker Compose — 체크섬을 확인하고 설치한다.
COMPOSE_VERSION=v5.5.1
BASE="https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}"
cd /tmp
curl -fsSL -O "${BASE}/docker-compose-linux-x86_64"
curl -fsSL -O "${BASE}/docker-compose-linux-x86_64.sha256"
sha256sum -c docker-compose-linux-x86_64.sha256
install -D -m 0755 docker-compose-linux-x86_64 \
  /usr/local/lib/docker/cli-plugins/docker-compose
rm -f /tmp/docker-compose-linux-x86_64*

# 저장소가 public 이라 자격증명 없이 받는다.
git clone https://github.com/FinGuardName/FinBound.git /opt/finbound

docker compose version
```

## 3. 비밀값 — 사용자 데이터에 넣지 않는다

**사용자 데이터는 인스턴스 안에서 메타데이터로 다시 읽을 수 있다.** 비밀값을 두면 그 자체가
유출 경로가 된다. 인스턴스가 뜬 뒤 Session Manager로 들어가 직접 놓는다.

```bash
sudo install -m 600 /dev/null /opt/finbound/.env
sudo vi /opt/finbound/.env
```

`.env.example`이 요구하는 값 다섯 개를 채운다. **`POSTGRES_PASSWORD`는 한 번 정하면 바꾸기
어렵다** — 기존 볼륨에는 반영되지 않는다(README의 같은 경고 참조).

## 4. 저장소 설정

**Settings → Secrets and variables → Actions**

| 종류 | 이름 | 예시 |
|---|---|---|
| Variable | `AWS_REGION` | `ap-northeast-2` |
| Variable | `EC2_INSTANCE_ID` | `i-0123456789abcdef0` |
| Variable | `FINBOUND_PUBLIC_HOST` | `3-35-1-2.sslip.io` |
| Secret | `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::…:role/finbound-gha-deploy` |

**Settings → Environments** 에서 `production` 환경을 만든다. `deploy-ec2.yml`이 이 이름을 쓴다.

**Packages** — GHCR 패키지 여섯 개를 public으로 두면 EC2가 토큰 없이 pull한다.
private으로 두면 EC2에 장기 토큰이 필요해지고, "OIDC라 장기 자격증명이 없다"는 전제가 깨진다.

## 5. 배포

이미지는 `main`·`develop` push마다 자동으로 올라간다. 배포는 수동 실행이다.

```
Actions → Deploy to EC2 → Run workflow
  image_tag: 배포할 commit SHA (비우면 현재 커밋)
```

워크플로가 하는 일은 넷이다.

1. OIDC로 AWS 역할을 맡는다
2. SSM Send-Command로 EC2에서 해당 SHA를 checkout하고 `compose pull && up -d --wait`
3. SSM 명령 상태가 `Success`가 아니면 실패시킨다
4. `https://<호스트>/health`가 200을 줄 때까지 확인한다

## 6. 배포 후 확인

```bash
# 화면이 뜨는가
curl -sS -o /dev/null -w '%{http_code}\n' https://<호스트>/

# allowlist가 배포에서도 살아 있는가 — 둘 다 404 여야 한다
curl -sS -o /dev/null -w '%{http_code}\n' https://<호스트>/core-api/internal/v1/probe
curl -sS -o /dev/null -w '%{http_code}\n' https://<호스트>/core-api/actuator/health
```

시연 대본은 `e2e/playwright/tests/real-flow.spec.js`를 그대로 따른다.

## 되돌리기

**이미지만 되돌리는 롤백은 충분하지 않다.** Flyway가 기동 시 자동으로 migrate하므로
(`application.yml`), 실패한 배포가 스키마를 이미 올렸을 수 있다. 스키마를 건드리는 배포
전에는 DB 백업을 먼저 뜬다.

이미지만 문제라면 이전 SHA로 `Deploy to EC2`를 다시 실행하면 된다.

## 알려진 제약

- **단일 호스트다.** 이중화하지 않는다. 인스턴스가 죽으면 서비스가 죽는다.
- **`ai-risk`가 자기 메모리 한도의 88%를 쓴다.** 인스턴스에 여유가 있어도 컨테이너 한도에
  걸리면 OOM으로 죽는다. `.env`에 `AI_RISK_MEM_LIMIT=1280m`을 넣어 올린다.
- **`RateLimitFilter`가 `X-Forwarded-For`를 그대로 믿는다.** 인터넷에 노출되면 Rate Limit을
  헤더로 우회할 수 있다. 별도 이슈 대상이다.
