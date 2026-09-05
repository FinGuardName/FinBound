# Prompt Detector Model Card

## 목적과 경계

`prompt-guard-6`는 새 Prompt, Document, 외부 비신뢰 입력에서 Prompt Injection 위험 신호를
생성합니다. `promptRisk`, `LOW | ALERT | CRITICAL` 등급, 탐지 여부와 공격 유형 근거만 반환하며
`ALLOW/BLOCK` 권한 결정은 만들지 않습니다. OPA는 `CRITICAL`만 직접 차단하고 `ALERT`는 허용하되
감사 신호로 기록합니다. `detected=true`는 `riskLevel=CRITICAL`과 정확히 같은 하위 호환 표현입니다. 동일
`inputHash + modelVersion` 결과는 Core의 `PromptRiskSnapshot`으로 재사용합니다.

## 최종 구조

- 전체 정규화 텍스트를 평가하는 AI 모델을 주 판단 신호로 사용
- 한국어·영어 명시 공격 Rule과 인용·분석 문맥 예외를 설명 가능한 보조 증거로 사용
- `HikmaAI/hikmaai-mdeberta-v3-base-prompt-injection` 고정 Revision
  `ad81120116b9ec21bc5f47ffd5a2e0dccc803fe8`의 MIT License INT8 ONNX Artifact
- 승인 Development 48건으로 학습한 문자 2~5-gram Logistic Regression Domain Adapter
- Validation FPR 5% 제약 안에서 모델 고신뢰 Threshold와 보강 Threshold를 고정
- Rule 단독은 `ALERT`, AI 고신뢰 또는 AI 중간신뢰+Rule만 `CRITICAL`

사전학습 Artifact와 Domain Adapter는 시작 시 SHA-256을 검증합니다. 모델 또는 Feature 추론이
실패하거나 Classifier가 유한하지 않거나 0~1 범위를 벗어난 점수를 반환하면 낮은 Risk로
대체하지 않고 `PROMPT_RISK_UNAVAILABLE`로 fail-closed 처리합니다.

이 구조를 선택한 이유는 기존 `max(ruleRisk, modelRisk)`가 어휘 Rule 하나만으로 AI 판단을
우회해 즉시 차단할 수 있었기 때문입니다. 금융 권한·Scope·Hard Limit은 계속 결정적 정책으로
강제하지만, Prompt Injection의 의미 판단은 AI가 주도해야 AI의 독립 가치와 오탐 통제를 함께
설명할 수 있습니다.

| 모델 증거 | Rule 증거 | 등급 |
|---|---|---|
| `modelScore >= 1.0` | 무관 | `CRITICAL` |
| `modelScore >= 0.6353324782` | 있음 | `CRITICAL` |
| `modelScore >= 0.6353324782` | 없음 | `ALERT` |
| 낮음 | 있음 | `ALERT` |
| 낮음 | 없음 | `LOW` |

`modelScore`는 공격 확률이 아니라 사전학습 모델과 Domain Adapter 각각의 Validation Threshold 대비
증거비를 정규화하고 1.0에서 상한 처리한 값입니다. 따라서 `modelHighThreshold=1.0`은 확률 100%가
아니라 선택된 AI 고신뢰 경계에 도달했다는 뜻입니다.

Rule 보강 Threshold는 이미 반복 관측된 Held-out이 아니라 Validation에서만 선택했습니다. AI 증거가
사실상 0인 상태에서 Rule이 다시 단독 차단 신호가 되지 않도록 후보 하한을 0.5로 고정했습니다.

## 후보 선택

| 후보 | Validation 결과/판단 |
|---|---|
| ProtectAI DeBERTa v2 (Apache-2.0) | 영어 중심 후보로 한국어 정상문장 오탐이 커서 제외 |
| HikmaAI mDeBERTa v3 INT8 (MIT) | 단독 Recall 4.17%, FPR 4.17%; 다국어 보조 신호로 유지 |
| 한국어 KLUE RoBERTa (Apache-2.0) | 단독 Recall 20.83%, FPR 4.17%; 배포 용량 대비 개선이 작아 제외 |
| HikmaAI + Domain Adapter | v7 재학습 후 모델 계층 Recall 79.17%, FPR 4.17%; 기존 최종 구조 유지 |

Domain Adapter는 새 Prompt Detector를 대체하는 대규모 학습 모델이 아니라, 한국어 금융 문맥의
부족을 보정하는 P0용 소형 계층입니다. 학습에는 `development`, Threshold 선정에는 `validation`만
사용합니다.

Validation 48건에서 Model-only는 Precision 0.9500, Recall 0.7917, F1 0.8636, FPR 0.0417이고,
선택된 AI-primary gated 결합은 Precision/Recall/F1 0.9583, FPR 0.0417입니다.

## 최종 회귀 평가

의미상 같은 인용·분석·난독화 Hard Negative를 Group 단위로 재배치한 승인 데이터
`finbound-prompt-eval-korean-primary-7`의 120건 Held-out Test에서 Adapter와 Validation Threshold를
고정한 뒤 평가했습니다. Threshold 계산에는 Development/Validation만 사용했습니다. 다만 Held-out의
대부분은 이전 모델 평가에서 이미 관측됐으므로 완전히 새로운 독립 Blind Set이 아닌 최종 회귀
검증으로 해석합니다.

| 계층 | Precision | Recall | F1 | FPR |
|---|---:|---:|---:|---:|
| Rule only | 0.8750 | 0.2333 | 0.3684 | 0.0333 |
| Model only | 0.9412 | 0.8000 | 0.8649 | 0.0500 |
| 기존 Rule OR Model | 0.9074 | 0.8167 | 0.8596 | 0.0833 |
| AI-primary gated | 0.9074 | 0.8167 | 0.8596 | 0.0833 |

결합 Recall은 한국어 0.90, 혼합어 0.80, 영어 0.50입니다. 공격 유형별 Recall은 Cross-customer
0.50, Instruction override 1.00, Policy bypass 0.70, System prompt extraction 0.80,
Unauthorized tool 1.00, Unknown prompt attack 0.90입니다. 상세 지표와 Wilson 95% 신뢰구간은
`evaluate/prompt_runtime_held_out.json`에 저장합니다.

이번 반복 Held-out에서는 기존 OR과 AI-primary gated의 수치가 우연히 같습니다. 이는 성능 향상을
주장할 근거가 아니며, Rule 단독 차단을 제거하고 AI 증거를 필수화한 정책 의미의 변경입니다.

## 외부 Post-freeze 평가

모델·Adapter·Threshold를 변경하지 않은 상태에서, 이전 FinBound 평가 Set과 분리된
`deepset/prompt-injections` 고정 Test Split 116건을 한 번 평가했습니다. Source와 Parquet Revision,
Artifact SHA-256 및 평가 JSONL SHA-256은 `evaluate/prompt_external_blind_deepset.json`에 기록합니다.
평가 결과를 보고 Threshold를 다시 선택하지 않습니다.

| 판정 경계 | Precision | Recall | F1 | FPR |
|---|---:|---:|---:|---:|
| `CRITICAL` 정책 차단 | 0.6818 | 0.2500 | 0.3659 | 0.1250 |
| `ALERT` 이상 위험 신호 | 0.5172 | 1.0000 | 0.6818 | 1.0000 |

이 공개 자료는 영어 일반 도메인의 넓은 Prompt Injection 정의를 사용하여 FinBound의 금융 업무
정책과 Label 경계가 다릅니다. 또한 사전학습 모델의 학습 데이터와 겹쳤을 가능성을 배제할 수
없습니다. 따라서 독립된 Post-freeze 외부 회귀 결과로만 해석하며 한국어 금융 성능을 주장하는
근거로 사용하지 않습니다. 특히 외부 정상문장 전부가 최소 `ALERT`였고 `CRITICAL` FPR도 12.5%라,
현재 모델을 운영 자동 차단 품질로 주장할 수 없다는 한계를 확인했습니다.

### 인용문-only 경계

공격 문구가 따옴표 안에만 있고 따옴표 밖에 실행 요청이 없으면 Rule은 일치시키지 않습니다. 이 예외는
정상적인 보안 분석·인용 요청의 False Block을 줄이기 위해 유지합니다. 대신 AI 모델은 인용부를 포함한
전체 텍스트를 평가하므로 의미상 공격성이 높으면 `ALERT` 또는 `CRITICAL`을 만들 수 있습니다. 따옴표
밖에 실행·요청 동사가 있으면 Rule 보조 증거도 유지합니다. 이 경계는 Runtime 회귀 테스트로 고정합니다.

### 사용 가능성 판단

- AI 서비스가 권한 결정을 직접 만들지는 않지만 OPA가 `riskLevel=CRITICAL`을 차단 조건으로 사용합니다.
- Held-out 결합 F1 0.8596/Recall 0.8167은 P0 탐지 가능성을 보여주지만, 전체 FPR 8.33%, 한국어
  Hard Negative FPR 20%와 영어 Recall 50%는 운영 자동 차단 품질로 충분하지 않습니다.
- MVP에서는 `ALERT`를 감사·표시하고 `CRITICAL`만 차단합니다. 운영 전에는 별도의 한국어 금융
  도메인 Blind Set과 실제 트래픽으로 False Block과 공격 Recall을 재검증해야 합니다.

## 한계와 해석

- 최종 결합 FPR 8.33%는 Validation 목표 5%보다 높습니다. 영어와 한국어 FPR은 각각 10%이고
  한국어 Hard Negative FPR은 20%입니다. 작은 표본의 변동성이 크므로 영어는 보조 평가로만
  해석하고, 한국어 인용·분석 문맥의 오탐도 운영 전 추가 검증해야 합니다.
- 데이터는 216건의 가상·자체 작성 문장이라 실제 금융 환경 일반화 성능을 뜻하지 않습니다.
- 동일 Held-out의 이전 평가 이력이 있습니다. 최종 Adapter와 Threshold 계산에는 Held-out 원문이나
  Sample별 결과를 사용하지 않았지만, 엄격한 단일 시도 프로토콜의 독립 최종 성능으로 주장하지
  않습니다. 별도 외부 Post-freeze 평가는 일반 영어 도메인이라 이를 대체하지 않습니다.
- 공격 미탐은 Financial Context Resolver와 Hard Limit로 추가 방어합니다. 이 결정적 제약은 AI의
  역할을 대체하지 않고 권한 경계를 보장합니다. Prompt Rule은 단독 차단하지 않습니다.
- 운영 배포 전 실제 트래픽과 별도 외부 Benchmark에서 Drift와 FPR을 다시 검증해야 합니다.

## 재현성

- 승인 데이터 SHA-256:
  `c402b408aa97ab34e31b5df6223ba9f85b5a32efb5f6a5a2466cd22efa17db8a`
- 사전학습 ONNX SHA-256:
  `151fa3a17cc4c11a5fa86173c9d5cf63846c74389e528ed420f28cc203cd9aaa`
- Domain Adapter SHA-256:
  `cf82500ce5c0bfd9929416a7c82c827f61da6eb627ff313164b2840a182ef85f`
- 외부 Post-freeze 평가 Set SHA-256:
  `de0996d15cabd838d50a4925a8493062ba70c29f845601cd6a17412236614486`
- 학습 Random Seed: `42`
- Threshold와 Artifact 경로: `models/prompt_detector.json`
- 의존성: `requirements.lock`

평가 Report에는 원문을 넣지 않고 오탐·미탐 `sampleId`만 기록합니다.
