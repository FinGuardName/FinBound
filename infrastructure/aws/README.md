# AWS IAM 정책

배포에 필요한 IAM 정책을 파일로 둔다. 대화나 콘솔 화면에만 있으면 다음 사람이 같은 것을
처음부터 다시 만들고, 무엇이 왜 허용됐는지 리뷰할 수도 없다.

**계정 번호와 리전은 자리표시자다.** 저장소가 public이라 계정 번호를 적어 두지 않는다.

```
ACCOUNT_ID       →  실제 AWS 계정 번호
AWS_REGION       →  ap-northeast-2
GITHUB_ORG_ID    →  조직의 불변 숫자 ID
GITHUB_REPO_ID   →  저장소의 불변 숫자 ID
```

조직·저장소 ID는 이렇게 얻는다.

```bash
gh api orgs/FinGuardName --jq .id
gh api repos/FinGuardName/FinBound --jq .id
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
`sub` 끝을 와일드카드로 둔 이유는 발행이 브랜치 push(`ref:refs/heads/...`)에서, 배포가
`production` 환경(`environment:production`)에서 돌아 값이 서로 다르기 때문이다.

### ⚠️ `sub` 에 불변 ID가 붙는다 — 여기서 한 번 막혔다

GitHub 가 발급하는 `sub` 는 이름만 쓰지 않는다. **조직과 저장소의 불변 숫자 ID를 함께 넣는다.**

```
repo:FinGuardName@316611907/FinBound@1337110461:ref:refs/heads/chore/aws-iam-policies
```

처음에 `repo:FinGuardName/FinBound:*` 로만 조건을 걸었더니 매칭이 안 돼
`Not authorized to perform sts:AssumeRoleWithWebIdentity` 가 났다. **공급자·대상·역할이
전부 정상인데도 나는 에러라 원인을 짐작하기 어렵다.**

그래서 두 형식을 배열로 함께 둔다. 배열은 OR 이고, 범위는 여전히 이 저장소 하나다.

막히면 추측하지 말고 토큰이 실제로 주장하는 값을 본다. 워크플로에서 이렇게 꺼낸다
(**토큰 자체는 절대 출력하지 않는다 — 자격증명이다**).

```bash
token=$(curl -sS -H "Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN"   "$ACTIONS_ID_TOKEN_REQUEST_URL&audience=sts.amazonaws.com"   | python3 -c 'import sys,json; print(json.load(sys.stdin)["value"])')
printf '%s' "$token" | python3 -c 'import sys,base64,json; p=sys.stdin.read().split(".")[1]; p+="="*(-len(p)%4); print(json.loads(base64.urlsafe_b64decode(p)).get("sub"))'
```

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
