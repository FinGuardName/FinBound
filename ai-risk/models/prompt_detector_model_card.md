# Prompt Detector Model Card

## 목적과 경계

`prompt-guard-2`는 새 Prompt, Document, 외부 비신뢰 입력에서 Prompt Injection 위험 신호를
생성합니다. `promptRisk`, 탐지 여부와 공격 유형 근거만 반환하며 `ALLOW/BLOCK` 권한 결정은 만들지
않습니다. 동일 `inputHash + modelVersion` 결과는 Core의 `PromptRiskSnapshot`으로 재사용합니다.

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

## 최종 평가

의미상 같은 인용·분석 Hard Negative를 Group 단위로 재배치한 승인 데이터
`finbound-prompt-eval-korean-primary-5`의 120건 Held-out Test에서 Adapter와 Validation Threshold를
다시 고정한 뒤 평가했습니다.

| 계층 | Precision | Recall | F1 | FPR |
|---|---:|---:|---:|---:|
| Rule only | 0.8750 | 0.2333 | 0.3684 | 0.0333 |
| Model only | 0.9273 | 0.8500 | 0.8870 | 0.0667 |
| Rule + Model | 0.8947 | 0.8500 | 0.8718 | 0.1000 |

결합 Recall은 한국어 0.925, 혼합어 0.80, 영어 0.60입니다. 공격 유형별 Recall은 Cross-customer
0.70, Instruction override 0.90, Policy bypass 0.70, System prompt extraction 0.80,
Unauthorized tool 1.00, Unknown prompt attack 1.00입니다. 상세 지표와 Wilson 95% 신뢰구간은
`evaluate/prompt_runtime_held_out.json`에 저장합니다.

## 한계와 해석

- 최종 결합 FPR 10.00%는 Validation 목표 5%보다 높습니다. 영어 FPR은 20%, 한국어 FPR은 10%이고
  한국어 Hard Negative FPR은 20%입니다. 작은 표본의 변동성이 크므로 영어는 보조 평가로만
  해석하고, 한국어 인용·분석 문맥의 오탐도 운영 전 추가 검증해야 합니다.
- 데이터는 216건의 가상·자체 작성 문장이라 실제 금융 환경 일반화 성능을 뜻하지 않습니다.
- Held-out을 먼저 실행한 사전학습 단독 후보가 낮은 성능으로 폐기된 이력이 있습니다. 최종 Hybrid의
  Adapter와 Threshold는 그 원문이나 Sample별 결과를 사용하지 않고 Development/Validation에서만
  고정했지만, 엄격한 단일 시도 프로토콜 관점에서는 이 선행 집계 평가가 한계입니다.
- 공격 미탐은 Financial Context Resolver, Hard Limit, OPA 정책으로 방어하고 Prompt Risk가 권한을
  확대하도록 사용해서는 안 됩니다.
- 운영 배포 전 실제 트래픽과 별도 외부 Benchmark에서 Drift와 FPR을 다시 검증해야 합니다.

## 재현성

- 승인 데이터 SHA-256:
  `5a58eb00887c9f44f32138e52a65ae1f56d193456a0cefd2c280afd0e5371c13`
- 사전학습 ONNX SHA-256:
  `151fa3a17cc4c11a5fa86173c9d5cf63846c74389e528ed420f28cc203cd9aaa`
- Domain Adapter SHA-256:
  `fb070a4c93503aca5e41a5a9266815afd1fe64dc0b6d19090d999b4738013958`
- 학습 Random Seed: `42`
- Threshold와 Artifact 경로: `models/prompt_detector.json`
- 의존성: `requirements.lock`

평가 Report에는 원문을 넣지 않고 오탐·미탐 `sampleId`만 기록합니다.
