# AI Risk Engine

Frontend & AI 담당 영역입니다. FastAPI는 `promptRisk`와 `behaviorRisk`를 반환하며 `ALLOW/BLOCK`을 반환하지 않습니다.

## 구조

- `app/prompt`: Rule + Prompt 분류 모델
- `app/behavior`: Isolation Forest 추론과 Calibration
- `app/feature_builder`: 학습/Runtime 공용 Feature Builder
- `app/schemas`: camelCase 서비스 계약
- `datasets`: 합성 데이터 생성 결과와 메타데이터
- `train`, `evaluate`: 재현 가능한 학습·평가
- `models`: Git에 올려도 되는 작은 메타데이터/Calibration artifact만 관리

```bash
cd ai-risk
uv sync --extra dev
uv run uvicorn app.main:app --reload --port 8000
uv run pytest
```

Behavior Independent Mock artifact는 고정 Seed의 합성 데이터로 재현할 수 있습니다.

```bash
cd ai-risk
uv run python -m train.train_behavior
```

Runtime Endpoint:

```http
POST /internal/v1/risk/behavior
```

학습과 Runtime은 `app/feature_builder`의 동일한 Feature Schema를 사용합니다. 학습 결과는
`models/behavior_iforest.joblib`, Metadata는 `models/behavior_iforest.json`, 평가 지표는
`evaluate/behavior_metrics.json`에 기록합니다. `behaviorRisk`는 Validation 정상 분포에서 보정한
상대 위험 점수이며 공격 확률로 해석하지 않습니다.

Synthetic Behavior 데이터는 Agent Session을 Group으로 묶어 Train/Validation/Held-out Test로
분리합니다. Validation 정상 분포로 Calibration과 Threshold를 확인하고, Held-out Test는 모델과
Threshold를 고정한 뒤 최종 평가에만 사용합니다.

`requirements.lock`은 검증된 개발 환경의 정확한 버전을 기록합니다. pip 사용 시 `pip install -r requirements.lock` 후 `pip install -e . --no-deps`로 동일 환경을 재현할 수 있습니다.

원문 Prompt를 로그나 DB에 저장하지 않으며 모델/Feature 오류를 낮은 Risk로 대체하지 않습니다.

Prompt Detector는 새 Prompt, Document 또는 외부 비신뢰 입력이 생성·변경될 때 호출합니다. 동일 입력의 Tool Call은 `inputHash`에 연결된 `PromptRiskSnapshot`을 재사용하며, Behavior Risk는 행동 이력이 변하므로 Tool Call마다 계산합니다.
