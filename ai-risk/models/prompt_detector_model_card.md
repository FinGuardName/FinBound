# Prompt Detector Model Card

## 목적과 경계

`prompt-guard-3`는 새 Prompt, Document, 외부 비신뢰 입력에서 Prompt Injection 위험 신호를
생성합니다. `promptRisk`, 탐지 여부와 공격 유형 근거만 반환하며 `ALLOW/BLOCK` 권한 결정은 만들지
않습니다. 동일 `inputHash + modelVersion` 결과는 Core의 `PromptRiskSnapshot`으로 재사용합니다.

## 최종 구조

- 한국어·영어 명시 공격 Rule과 인용·분석 문맥 예외
- `HikmaAI/hikmaai-mdeberta-v3-base-prompt-injection` 고정 Revision
  `ad81120116b9ec21bc5f47ffd5a2e0dccc803fe8`의 MIT License INT8 ONNX Artifact
- 승인 Development 48건으로 학습한 문자 2~5-gram Logistic Regression Domain Adapter
  (`C=1.0`; Validation에서 기존 `C=10`보다 강한 정규화를 선택)
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
| HikmaAI + Domain Adapter | 모델 계층 Recall 91.67%, F1 93.62%, FPR 4.17%; 최종 구조로 선택 |

Domain Adapter는 새 Prompt Detector를 대체하는 대규모 학습 모델이 아니라, 한국어 금융 문맥의
부족을 보정하는 P0용 소형 계층입니다. 학습에는 `development`, Threshold 선정에는 `validation`만
사용합니다.

## 반복 진단 평가

의미상 같은 인용·분석 Hard Negative를 Group 단위로 재배치한 승인 데이터
`finbound-prompt-eval-korean-primary-5`의 120건 Held-out Test에서 Adapter와 Validation Threshold를
고정한 뒤 진단했습니다. 이 Split은 이전 모델 평가에서 이미 관측됐으므로 아래 수치는 새로운
독립 최종 평가가 아니라 동일 Split의 반복 회귀 결과입니다.

| 계층 | Precision | Recall | F1 | FPR |
|---|---:|---:|---:|---:|
| Rule only | 0.8750 | 0.2333 | 0.3684 | 0.0333 |
| Model only | 0.8889 | 0.9333 | 0.9106 | 0.1167 |
| Rule + Model | 0.8750 | 0.9333 | 0.9032 | 0.1333 |

결합 Recall은 한국어 0.975, 혼합어 1.00, 영어 0.70입니다. 공격 유형별 Recall은 Cross-customer
0.80, Instruction override 1.00, Policy bypass 0.90, System prompt extraction 0.90,
Unauthorized tool 1.00, Unknown prompt attack 1.00입니다. 상세 지표와 Wilson 95% 신뢰구간은
`evaluate/prompt_runtime_held_out.json`에 저장합니다.

직전 `prompt-guard-2` 대비 결합 Recall은 0.8500에서 0.9333, F1은 0.8718에서 0.9032로
개선됐습니다. 반면 FPR은 10.00%에서 13.33%로 증가했습니다. AI가 권한 결정을 내리지 않는 Risk
Signal 계층이라는 경계와 Validation FPR 5% 제약을 만족한다는 조건에서 Recall을 우선한 결과입니다.

## 한계와 해석

- 반복 진단 결합 FPR 13.33%는 Validation 목표 5%보다 높습니다. 영어 FPR은 40%, 한국어 FPR은 10%이고
  한국어 Hard Negative FPR은 20%입니다. 작은 표본의 변동성이 크므로 영어는 보조 평가로만
  해석하고, 한국어 인용·분석 문맥의 오탐도 운영 전 추가 검증해야 합니다.
- 데이터는 216건의 가상·자체 작성 문장이라 실제 금융 환경 일반화 성능을 뜻하지 않습니다.
- 이 Held-out은 `prompt-guard-2`와 개선 후보 진단에서 반복 관측됐습니다. Adapter 정규화와 Threshold
  계산 자체에는 Development/Validation만 사용했지만, 엄격한 단일 시도 프로토콜의 독립 최종
  성능으로 주장하지 않습니다. 최종 배포 판정 전 새 외부 Blind Set이 필요합니다.
- 공격 미탐은 Financial Context Resolver, Hard Limit, OPA 정책으로 방어하고 Prompt Risk가 권한을
  확대하도록 사용해서는 안 됩니다.
- 운영 배포 전 실제 트래픽과 별도 외부 Benchmark에서 Drift와 FPR을 다시 검증해야 합니다.

## 재현성

- 승인 데이터 SHA-256:
  `5a58eb00887c9f44f32138e52a65ae1f56d193456a0cefd2c280afd0e5371c13`
- 사전학습 ONNX SHA-256:
  `151fa3a17cc4c11a5fa86173c9d5cf63846c74389e528ed420f28cc203cd9aaa`
- Domain Adapter SHA-256:
  `f46a81d612effae54be256c9568cc12586c3c32cf33f1dcb4b03ea02ba5b5290`
- 학습 Random Seed: `42`
- Threshold와 Artifact 경로: `models/prompt_detector.json`
- 의존성: `requirements.lock`

평가 Report에는 원문을 넣지 않고 오탐·미탐 `sampleId`만 기록합니다.
