import os
from pathlib import Path

import joblib
import numpy as np

from app.behavior.model import BehaviorModelBundle
from app.feature_builder import FEATURE_NAMES, FEATURE_VERSION, build_feature_vector
from app.schemas.behavior import (
    BehaviorRiskLevel,
    BehaviorRiskRequest,
    BehaviorRiskResponse,
    HistoryStatus,
)

DEFAULT_MODEL_PATH = Path(__file__).resolve().parents[2] / "models" / "behavior_iforest.joblib"
MODEL_PATH_ENV = "FINGUARD_BEHAVIOR_MODEL_PATH"
COLD_START_MIN_EVENTS = 5


class BehaviorModelError(RuntimeError):
    pass


class BehaviorRiskService:
    def __init__(
        self,
        model_path: Path | None = None,
        alert_threshold: float | None = None,
        critical_threshold: float | None = None,
    ) -> None:
        configured_path = os.getenv(MODEL_PATH_ENV)
        self._model_path = model_path or (Path(configured_path) if configured_path else DEFAULT_MODEL_PATH)
        self._alert_threshold = alert_threshold or float(
            os.getenv("BEHAVIOR_ALERT_THRESHOLD", "0.70")
        )
        self._critical_threshold = critical_threshold or float(
            os.getenv("BEHAVIOR_CRITICAL_THRESHOLD", "0.90")
        )
        self._bundle: BehaviorModelBundle | None = None

    def _load_bundle(self) -> BehaviorModelBundle:
        if self._bundle is None:
            if not self._model_path.is_file():
                raise BehaviorModelError(f"Behavior model artifact not found: {self._model_path}")
            bundle = joblib.load(self._model_path)
            if not isinstance(bundle, BehaviorModelBundle):
                raise BehaviorModelError("Behavior model artifact has an invalid type")
            if bundle.feature_version != FEATURE_VERSION or bundle.feature_names != FEATURE_NAMES:
                raise BehaviorModelError("Behavior model and runtime feature schema do not match")
            if len(bundle.calibration_scores) == 0:
                raise BehaviorModelError("Behavior calibration scores are empty")
            self._bundle = bundle
        return self._bundle

    def evaluate(self, request: BehaviorRiskRequest) -> BehaviorRiskResponse:
        bundle = self._load_bundle()
        vector = build_feature_vector(request.history, request.current_attempt)
        if not np.all(np.isfinite(vector)):
            raise BehaviorModelError("Behavior feature vector contains a non-finite value")

        scaled = bundle.scaler.transform(vector.reshape(1, -1))
        raw_score = float(-bundle.model.decision_function(scaled)[0])
        if not np.isfinite(raw_score):
            raise BehaviorModelError("Behavior model returned a non-finite score")
        behavior_risk = bundle.risk_from_raw_score(raw_score)

        if behavior_risk >= self._critical_threshold:
            level = BehaviorRiskLevel.CRITICAL
        elif behavior_risk >= self._alert_threshold:
            level = BehaviorRiskLevel.ALERT
        else:
            level = BehaviorRiskLevel.LOW

        return BehaviorRiskResponse(
            behavior_risk=behavior_risk,
            behavior_risk_level=level,
            is_anomaly=bool(bundle.model.predict(scaled)[0] == -1),
            raw_score=raw_score,
            history_status=(
                HistoryStatus.READY
                if len(request.history) >= COLD_START_MIN_EVENTS
                else HistoryStatus.COLD_START
            ),
            feature_version=bundle.feature_version,
            model_version=bundle.model_version,
        )
