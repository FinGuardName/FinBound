# Prompt Detector Model Card

## 목적과 경계

`prompt-guard-4`는 새 Prompt, Document, 외부 비신뢰 입력에서 Prompt Injection 위험 신호를
생성합니다. `promptRisk`, 탐지 여부와 공격 유형 근거만 반환하며 `ALLOW/BLOCK` 권한 결정은 만들지
않습니다. 다만 현재 OPA 정책은 `detected=true`를 `PROMPT_INJECTION` 차단 사유로 직접 사용하므로,
이 출력은 단순 참고 정보가 아니라 실제 BLOCK을 유발할 수 있는 정책 입력입니다. 동일
`inputHash + modelVersion` 결과는 Core의 `PromptRiskSnapshot`으로 재사용합니다.

## 최종 구조

- 한국어·영어 명시 공격 Rule과 인용·분석 문맥 예외
- `HikmaAI/hikmaai-mdeberta-v3-base-prompt-injection` 고정 Revision
  `ad81120116b9ec21bc5f47ffd5a2e0dccc803fe8`의 MIT License INT8 ONNX Artifact
- 승인 Development 48건으로 학습한 문자 2~5-gram Logistic Regression Domain Adapter
- Validation FPR 5% 제약 안에서 각 모델 Threshold를 고정한 최대 증거 결합
- Rule과 결합된 risk가 `promptBlockThreshold=0.9` 이상이면 탐지

사전학습 Artifact와 Domain Adapter는 시작 시 SHA-256을 검증합니다. 모델 또는 Feature 추론이
실패하면 낮은 Risk로 대체하지 않고 `PROMPT_RISK_UNAVAILABLE`로 fail-closed 처리합니다.

## 후보 선택

| 후보 | Validation 결과/판단 |
|---|---|
| ProtectAI DeBERTa v2 (Apache-2.0) | 영어 중심 후보로 한국어 정상문장 오탐이 커서 제외 |
| HikmaAI mDeBERTa v3 INT8 (MIT) | 단독 Recall 4.17%, FPR 4.17%; 다국어 보조 신호로 유지 |
| 한국어 KLUE RoBERTa (Apache-2.0) | 단독 Recall 20.83%, FPR 4.17%; 배포 용량 대비 개선이 작아 제외 |
| HikmaAI + Domain Adapter | 모델 계층 Recall 83.33%, FPR 4.17%; 최종 구조로 선택 |

Domain Adapter는 새 Prompt Detector를 대체하는 대규모 학습 모델이 아니라, 한국어 금융 문맥의
부족을 보정하는 P0용 소형 계층입니다. 학습에는 `development`, Threshold 선정에는 `validation`만
사용합니다.

## 최종 회귀 평가

의미상 같은 인용·분석·난독화 Hard Negative를 Group 단위로 재배치한 승인 데이터
`finbound-prompt-eval-korean-primary-6`의 120건 Held-out Test에서 Adapter와 Validation Threshold를
고정한 뒤 평가했습니다. Threshold 계산에는 Development/Validation만 사용했습니다. 다만 Held-out의
대부분은 이전 모델 평가에서 이미 관측됐으므로 완전히 새로운 독립 Blind Set이 아닌 최종 회귀
검증으로 해석합니다.

| 계층 | Precision | Recall | F1 | FPR |
|---|---:|---:|---:|---:|
| Rule only | 0.8750 | 0.2333 | 0.3684 | 0.0333 |
| Model only | 0.9153 | 0.9000 | 0.9076 | 0.0833 |
| Rule + Model | 0.8852 | 0.9000 | 0.8926 | 0.1167 |

결합 Recall은 한국어 0.95, 혼합어 0.90, 영어 0.70입니다. 공격 유형별 Recall은 Cross-customer
0.70, Instruction override 1.00, Policy bypass 0.80, System prompt extraction 0.90,
Unauthorized tool 1.00, Unknown prompt attack 1.00입니다. 상세 지표와 Wilson 95% 신뢰구간은
`evaluate/prompt_runtime_held_out.json`에 저장합니다.

### 사용 가능성 판단

- AI 서비스가 권한 결정을 직접 만들지는 않지만 OPA가 `detected=true`를 차단 조건으로 사용합니다.
- Held-out 결합 F1 0.8926/Recall 0.90은 P0 탐지 가능성을 보여주지만, FPR 11.67%와 한국어 Hard
  Negative FPR 25%는 운영 자동 차단 품질로 충분하지 않습니다.
- 현재 정책을 유지하는 MVP에서는 오탐 가능성을 팀이 명시적으로 수용하고 통제된 시나리오에서만
  사용해야 합니다. 운영 전에는 신규 Blind Set과 실제 트래픽으로 오탐을 재검증하고, 필요하면
  Rule 근거 또는 높은 신뢰도 신호만 직접 차단하는 정책을 별도로 결정해야 합니다.

## 한계와 해석

- 최종 결합 FPR 11.67%는 Validation 목표 5%보다 높습니다. 영어 FPR은 20%, 한국어 FPR은 12.5%이고
  한국어 Hard Negative FPR은 25%입니다. 작은 표본의 변동성이 크므로 영어는 보조 평가로만
  해석하고, 한국어 인용·분석 문맥의 오탐도 운영 전 추가 검증해야 합니다.
- 데이터는 216건의 가상·자체 작성 문장이라 실제 금융 환경 일반화 성능을 뜻하지 않습니다.
- 동일 Held-out의 이전 평가 이력이 있습니다. 최종 Adapter와 Threshold 계산에는 Held-out 원문이나
  Sample별 결과를 사용하지 않았지만, 엄격한 단일 시도 프로토콜의 독립 최종 성능으로 주장하지
  않습니다. 운영 전 별도 외부 Blind Set이 필요합니다.
- 공격 미탐은 Financial Context Resolver와 Hard Limit로 추가 방어합니다. Prompt Risk는 권한을
  확대하지 않지만 현재 OPA에서 직접 차단될 수 있으므로 오탐 영향도 함께 관리해야 합니다.
- 운영 배포 전 실제 트래픽과 별도 외부 Benchmark에서 Drift와 FPR을 다시 검증해야 합니다.

## 재현성

- 승인 데이터 SHA-256:
  `c2b1d5137a50270f4fbc58efe1107ab91612a6a4537d4910bd318ac717aaf5cf`
- 사전학습 ONNX SHA-256:
  `151fa3a17cc4c11a5fa86173c9d5cf63846c74389e528ed420f28cc203cd9aaa`
- Domain Adapter SHA-256:
  `e233b167811cee0aa99cd419723288dea10e2af6ebb6e32cf30e8acbce56d25d`
- 학습 Random Seed: `42`
- Threshold와 Artifact 경로: `models/prompt_detector.json`
- 의존성: `requirements.lock`

평가 Report에는 원문을 넣지 않고 오탐·미탐 `sampleId`만 기록합니다.
