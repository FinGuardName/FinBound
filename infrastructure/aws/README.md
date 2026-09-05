# AWS IAM 정책

배포에 필요한 IAM 정책을 파일로 둔다. 대화나 콘솔 화면에만 있으면 다음 사람이 같은 것을
처음부터 다시 만들고, 무엇이 왜 허용됐는지 리뷰할 수도 없다.

**계정 번호와 리전은 자리표시자다.** 저장소가 public이라 계정 번호를 적어 두지 않는다.

```
ACCOUNT_ID   →  실제 AWS 계정 번호
AWS_REGION   →  ap-northeast-2
```

## 파일

| 파일 | 붙는 곳 |
|---|---|
| `gha-trust-policy.json` | 역할 `finbound-gha-deploy` 의 **신뢰 정책** |
| `gha-permissions-policy.json` | 같은 역할의 **인라인 권한 정책** (`finbound-deploy`) |

인스턴스 역할(`finbound-ec2-ssm`)은 AWS 관리형 정책 두 개만 쓰므로 여기에 파일이 없다.

```
AmazonSSMManagedInstanceCore
AmazonEC2ContainerRegistryReadOnly
```

## 쓰는 법

CloudShell에서 자리표시자를 채워 그대로 적용한다.

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=ap-northeast-2
BASE=https://raw.githubusercontent.com/FinGuardName/FinBound/develop/infrastructure/aws

for f in gha-trust-policy gha-permissions-policy; do
  curl -fsSL "$BASE/$f.json" \
    | sed -e "s/ACCOUNT_ID/$ACCOUNT_ID/g" -e "s/AWS_REGION/$REGION/g" \
    > "/tmp/$f.json"
done

aws iam create-role --role-name finbound-gha-deploy \
  --assume-role-policy-document file:///tmp/gha-trust-policy.json

aws iam put-role-policy --role-name finbound-gha-deploy \
  --policy-name finbound-deploy \
  --policy-document file:///tmp/gha-permissions-policy.json
```

> **긴 JSON을 CloudShell에 직접 붙여넣지 않는다.** 터미널이 긴 줄 중간에 줄바꿈을 끼워 넣어
> 조용히 깨진다. 실제로 겪었다. 파일로 받아서 쓰거나 IAM 콘솔의 JSON 편집기를 쓴다.

## 무엇을 허용하는가

**신뢰 정책** — `FinGuardName/FinBound` 저장소의 워크플로만 이 역할을 맡을 수 있다.
`sub` 를 와일드카드로 둔 이유는 발행이 브랜치 push(`ref:refs/heads/...`)에서, 배포가
`production` 환경(`environment:production`)에서 돌아 값이 서로 다르기 때문이다.

**권한 정책** — 넷이다.

| Sid | 허용 |
|---|---|
| `EcrAuth` | `ecr:GetAuthorizationToken`. AWS가 리소스 지정을 허용하지 않는 액션이라 `*` |
| `EcrPush` | `finbound-*` 리포지터리에만 push |
| `SsmDeploy` | `AWS-RunShellScript` 문서로 이 계정 인스턴스에 명령 전송 |
| `SsmRead` | 명령 결과 조회 |

## 좁힐 수 있는 곳

**`SsmDeploy` 의 `instance/*` 를 실제 인스턴스 ARN으로 바꾼다.** 정책을 처음 만들 때는
인스턴스가 없어서 넓게 뒀다. 인스턴스를 만든 뒤 좁히는 것이 맞다.

```
arn:aws:ec2:AWS_REGION:ACCOUNT_ID:instance/i-0123456789abcdef0
```

**발행용과 배포용 역할이 하나로 합쳐져 있다.** 엄밀히는 나누는 편이 최소 권한에 맞지만,
시연용 구성에서 IAM 설정을 두 배로 늘리는 값어치가 없다고 판단했다. 의도한 단순화다.
