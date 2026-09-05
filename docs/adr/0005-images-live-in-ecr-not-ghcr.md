---
status: accepted
date: 2026-09-05
---

# 컨테이너 이미지는 GHCR이 아니라 Amazon ECR에 둔다

배포 이미지를 **Amazon ECR**에 올린다. **GHCR로 먼저 구현했고 실제로 동작했지만 버렸다.**
이유는 비용도 성능도 아니고 **자격증명이 어디에 남는가** 하나다.

[ADR 0004](0004-deploy-to-one-ec2-with-actions-and-ssm.md)가 SSH 대신 SSM을 고른 근거는
*"저장소에도 호스트에도 수명이 긴 자격증명을 두지 않는다"* 였다. 레지스트리 선택이 그 근거를
지키느냐 무너뜨리느냐의 문제가 됐다.

## 무슨 일이 있었나

GHCR로 먼저 만들었다. 저장소가 public이니 이미지도 public으로 두면 EC2가 **인증 없이** 받을 수
있고, 그러면 호스트에 아무 자격증명도 안 남는다는 계산이었다.

**구현은 성공했다.** `publish-images.yml`이 develop 머지에서 처음 돌아 이미지 여섯 개를 전부
빌드해 GHCR에 올렸다. 막힌 것은 그다음이다.

```
$ docker manifest inspect ghcr.io/finguardname/finbound-core-api:<sha>
denied
```

**GHCR 패키지는 기본이 private이다.** 그리고 공개로 바꾸려니 조직 정책이 막았다.

```
Public   — Setting is disabled by organization administrators.
Internal — Setting is disabled by organization administrators.
Private  — (유일하게 선택 가능)
```

## 검토한 길 다섯

| # | 방법 | 결과 |
|---|---|---|
| 1 | 조직 정책을 풀어 GHCR을 public으로 | 가능했지만 **선택하지 않음** — 아래 |
| 2 | GHCR private + 호스트에 PAT 저장 | ❌ 수명이 긴 자격증명이 호스트에 남는다 |
| 3 | GHCR private + SSM으로 단기 토큰 전달 | ❌ 토큰이 SSM 명령 이력에 남아 사후 조회된다 |
| 4 | **ECR** | ✅ **채택** |
| 5 | 레지스트리 없이 EC2에서 소스 빌드 | ❌ t3.medium에서 Gradle 빌드 + ML 모델 내려받기 |

**1번은 실제로 가능했다.** 저장소 소유자가 조직 admin이라 클릭 몇 번이면 풀렸고, 코드 변경도
0줄이었다. 그런데도 4번을 골랐다. 이유는 1번이 **이미지를 인터넷 전체에 공개하는 대가로**
자격증명 문제를 피하는 방식이기 때문이다. ECR은 **공개하지 않고도** 같은 성질을 얻는다.

## 왜 ECR이 이기나 — 인증 주체가 다르다

**EC2가 자기 인스턴스 역할로 ECR에 인증한다.** 저장할 것이 없다.

```bash
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"
```

이 토큰은 12시간짜리이고 배포 시점에 만들어진다. **사람이 만들지도, 저장소에 넣지도, 호스트에
심어 두지도 않는다.** 인스턴스 역할에 `AmazonEC2ContainerRegistryReadOnly`를 붙이는 것이 전부다.

밀어 넣는 쪽도 같다. GitHub Actions가 **OIDC로 AWS 역할을 맡아** ECR에 push한다. AWS 장기 키를
저장소 시크릿에 두지 않는다.

| | GHCR public | GHCR private | **ECR** |
|---|---|---|---|
| EC2가 인증하는 법 | 인증 없음 | 저장된 토큰 | **인스턴스 역할** |
| 호스트에 남는 자격증명 | 없음 | **있음** | **없음** |
| 이미지 공개 여부 | **전체 공개** | 비공개 | 비공개 |
| 조직 정책 | **막힘** | 허용 | 해당 없음 |

## 대가

- **AWS에 묶인다.** 다른 클라우드로 옮기면 레지스트리도 옮겨야 한다. 이미 EC2에 배포하기로
  했으므로 새로 생기는 종속은 아니다.
- **월 약 $1.** 이미지 6~8GB 기준 스토리지 요금이다. 같은 리전 EC2로의 전송은 무료다.
- **리포지터리를 미리 만들어야 한다.** ECR은 push 시 자동 생성되지 않는다. 여섯 개를 먼저
  만든다(절차는 `infrastructure/DEPLOY.md`).
- **호스트에 AWS CLI가 필요하다.** Amazon Linux 2023에는 기본 포함이라 추가 작업은 없다.
- **발행과 배포가 IAM 역할 하나를 공유한다.** 엄밀히는 나누는 편이 최소 권한에 맞지만, 시연용
  구성에서 IAM 설정을 두 배로 늘리는 값어치가 없다고 판단했다. **의도한 단순화다.**

## 버리지 않은 것

GHCR로 만든 워크플로의 **형태는 그대로 살렸다** — 서비스 여섯 개 매트릭스, commit SHA 태그
(`latest` 없음), buildx 캐시. 바뀐 것은 로그인 단계와 태그 접두사뿐이다.

**그리고 GHCR 시도는 헛되지 않았다.** 그 실행에서 이미지 여섯 개가 전부 성공적으로 빌드된 것을
확인했다 — Dockerfile과 빌드 인자가 옳다는 사실은 이미 검증된 상태로 ECR에 넘어왔다.
