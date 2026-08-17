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

`requirements.lock`은 검증된 개발 환경의 정확한 버전을 기록합니다. pip 사용 시 `pip install -r requirements.lock` 후 `pip install -e . --no-deps`로 동일 환경을 재현할 수 있습니다.

원문 Prompt를 로그나 DB에 저장하지 않으며 모델/Feature 오류를 낮은 Risk로 대체하지 않습니다.
