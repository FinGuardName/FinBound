# FinBound AI Risk Engine

Frontend & AI 담당 영역입니다. FastAPI는 Prompt `promptRisk`·`riskLevel`과 `behaviorRisk`를
반환하며 `ALLOW/BLOCK`을 반환하지 않습니다.

## 구조

- `app/prompt`: Rule + Prompt 분류 모델
- `app/behavior`: Isolation Forest 추론과 Calibration
- `app/feature_builder`: 학습/Runtime 공용 Feature Builder
- `app/schemas`: camelCase 서비스 계약
- `datasets`: 합성 데이터 생성 결과와 메타데이터
- `train`, `evaluate`: 재현 가능한 학습·평가
- `models`: 모델 Artifact, Metadata 및 Model Card 관리

```bash
cd ai-risk
uv sync --extra dev
uv run uvicorn app.main:app --reload --port 8000
uv run pytest
```

Prompt Runtime은 고정 리비전의 다국어 사전학습 모델, 한국어·영어 Rule, FinBound Development
Set으로 학습한 소형 문자 n-gram Domain Adapter를 AI-primary 방식으로 결합합니다. Rule은 설명
가능한 보조 증거이며 단독으로 `CRITICAL`을 만들지 않습니다. Adapter는 권한 결정을 만들지 않고
사전학습 모델의 한국어 금융 문맥 부족을 보정하는 risk signal만 제공합니다.

```bash
cd ai-risk
uv sync --extra prompt --extra dev
uv run python -m models.download_prompt_model
uv run python -m train.train_prompt_adapter
uv run python -m evaluate.prompt_runtime --mode validation --output evaluate/prompt_runtime_validation.json
uv run python -m evaluate.prompt_runtime --mode held-out --output evaluate/prompt_runtime_held_out.json
```

Runtime Endpoint:

```http
POST /internal/v1/risk/prompt
POST /internal/v1/risk/behavior
```

Prompt 모델은 `models/prompt_detector.json`에서 Model ID·Revision·Artifact SHA-256과 모든
Threshold를 단일 관리합니다. 큰 사전학습 ONNX Artifact는 Git에 넣지 않고 다운로드 스크립트가
고정 Revision과 SHA-256을 검증합니다. Domain Adapter는 승인 데이터 SHA-256을 Bundle 안에서도
검증합니다. 경로를 바꿀 때는 `FINGUARD_PROMPT_MODEL_DIR`과
`FINGUARD_PROMPT_DOMAIN_ADAPTER_PATH`를 사용합니다.

Behavior Independent Mock artifact는 고정 Seed의 합성 데이터로 재현할 수 있습니다.

```bash
cd ai-risk
uv run python -m train.train_behavior
```

운영 Probe는 프로세스 생존 확인용 `GET /health`와 모델 Artifact 및 내부 Credential 설정을
확인하는 `GET /ready`로 분리합니다. Docker Compose와 배포 환경은 트래픽 전달 전에 `/ready`가
성공하는지 확인해야 합니다. 모델은 기본적으로 `models/behavior_iforest.joblib`을 사용하며,
배포 이미지의 위치가 다르면 `FINGUARD_BEHAVIOR_MODEL_PATH`로 명시합니다.

요청에는 `X-FinGuard-Service-Credential` Header가 필요하며 서버는
`FINGUARD_INTERNAL_CREDENTIAL` 환경변수와 상수 시간 비교합니다. 서버 Credential이 없거나 모델
Artifact가 유효하지 않으면 낮은 Risk로 대체하지 않고 `503 BEHAVIOR_RISK_UNAVAILABLE`로
fail-closed 처리합니다.

학습 데이터는 합성 Agent Event Sequence를 생성한 뒤 Runtime과 동일한
`app/feature_builder` 코드로 변환합니다. 학습 결과는
`models/behavior_iforest.joblib`, Metadata는 `models/behavior_iforest.json`, 평가 지표는
`evaluate/behavior_metrics.json`에 기록합니다. `behaviorRisk`는 Validation 정상 분포에서 보정한
상대 위험 점수이며 공격 확률로 해석하지 않습니다.

`requestedAt`은 Timezone이 필수이며 `afterHoursAccess`는 `Asia/Seoul` 업무시간을 기준으로
계산합니다. 최근 5분의 유효한 완료 이력이 5개 미만이면 Isolation Forest 판단에 필요한 근거가
부족한 `COLD_START`로 분류하고 중립 `LOW` 신호를 반환합니다. 이는 모델 장애를 낮은 Risk로
대체하는 동작이 아니며, Scope·Prompt Risk·Hard Limit 정책은 계속 적용됩니다.

Synthetic Behavior 데이터는 Agent Session을 Group으로 묶어 Train/Validation/Held-out Test로
분리합니다. Validation 정상 분포로 Calibration하고 Validation FPR 제약 안에서 Critical
Threshold를 선택합니다. 선택한 Alert/Critical Threshold는 모델 Bundle에 한 번만 저장하고 Runtime은
동일 값을 사용합니다. Held-out Test는 모델과 Threshold를 고정한 뒤 최종 평가에만 사용합니다.

정상 데이터에는 표준 업무시간, 마감 전 요청 증가, 정상 야간 초과근무, 고액대출 Case 추가 조회,
짧은 순간 Spike 및 Tool별 latency 차이를 포함합니다. 핵심 이상 데이터는 동일 Agent·Case·Consumer와
허용 Tool/Data를 유지한 채 Hard Request Limit 이하의 빠른 반복과 야간 누적 호출을 생성합니다.
Case/Consumer 전환과 같은 Scope Violation은 Behavior 모델 학습·Calibration·핵심 성능평가에
포함하지 않습니다.

정상과 이상은 동일한 Warm-up Event 수를 사용하고, 요청 수·평균 간격·금융 데이터 요청 수의 생성
범위가 서로 겹치도록 구성합니다. 따라서 특정 Label만 갖는 요청 수나 간격으로 정답을 바로 구분할 수
없습니다. Session 분리 외에도 Scenario 계층 분할을 적용해 모든 정상·이상 Scenario가 Validation과
Held-out Test에 포함되도록 합니다.

동일 생성 분포의 Held-out Test만으로 일반화 성능을 판단하지 않습니다. 간격·오류율·정상 변동성을
다르게 설정한 `shifted` Profile을 5개 별도 Seed로 평가하고 평균·표준편차·최악값을
`evaluate/behavior_metrics.json`에 기록합니다. Alert Threshold는 정상 Validation 분위수로 정하고,
Critical Threshold는 야간 누적 시나리오를 양성, 업무시간 빠른 반복 시나리오를 Alert-only 음성으로
두어 등급을 보정합니다. 이 합성 데이터와 평가지표는 P0 feasibility 검증용이며 실제 금융사 환경에
대한 일반화 성능을 의미하지 않습니다.

재학습 재현성은 joblib 파일 바이트의 SHA-256이 아니라 고정 평가 Feature 전체의 추론 결과,
Scaler, Calibration, Threshold 및 평가 JSON의 의미적 동일성으로 검증합니다. 파일 SHA-256은 특정
배포 Artifact의 무결성 확인 용도이며 서로 다른 직렬화 환경의 재학습 동일성 기준으로 사용하지 않습니다.

모델의 목적, 평가 결과와 알려진 한계는
[`models/behavior_iforest_model_card.md`](models/behavior_iforest_model_card.md)에 기록합니다.

현재 `X-FinGuard-Service-Credential`, `FINGUARD_INTERNAL_CREDENTIAL`,
`FINGUARD_BEHAVIOR_MODEL_PATH` 이름은 `docs/04-api-contract.md`와
`docs/06-common-conventions.md`의 공유 계약을 따르는 호환성 키입니다. 제품명은 FinBound지만 이 키는
계약 문서와 모든 소비자를 함께 변경하기 전까지 유지합니다.

`requirements.lock`은 검증된 개발 환경의 정확한 버전을 기록합니다. pip 사용 시 `pip install -r requirements.lock` 후 `pip install -e . --no-deps`로 동일 환경을 재현할 수 있습니다.

원문 Prompt를 로그나 DB에 저장하지 않으며 모델/Feature 오류를 낮은 Risk로 대체하지 않습니다.

Prompt Detector는 새 Prompt, Document 또는 외부 비신뢰 입력이 생성·변경될 때 호출합니다. 동일 입력의 Tool Call은 `inputHash`에 연결된 `PromptRiskSnapshot`을 재사용하며, Behavior Risk는 행동 이력이 변하므로 Tool Call마다 계산합니다.

Prompt 평가 데이터의 공개 Source Revision, License, 한국어·영어·혼합어 자체 Seed와 Held-out
정책은 [`datasets/prompt/README.md`](datasets/prompt/README.md)에서 관리합니다.
`reviewStatus=DRAFT`인 문장은 최종 성능 수치에 사용하지 않습니다. 기본은 서로 다른 팀원 2명의
Blind Review이며, 팀이 명시적으로 합의한 P0 예외에서는 AI 전수 검수와 Dataset Owner 승인을
Manifest에 투명하게 기록한 `APPROVED` Set만 최종 평가에 사용합니다.

Prompt Detector의 후보 비교, 고정 Threshold, 최종 Held-out 지표와 한계는
[`models/prompt_detector_model_card.md`](models/prompt_detector_model_card.md)에 기록합니다.
