import os
from pathlib import Path

import joblib
import numpy as np

from app.behavior.model import BehaviorModelBundle
from app.feature_builder import (
    FEATURE_NAMES,
    FEATURE_VERSION,
    build_feature_vector,
    completed_events_in_window,
)
from app.schemas.behavior import (
    BehaviorRiskLevel,
    BehaviorRiskRequest,
    BehaviorRiskResponse,
    HistoryStatus,
)

DEFAULT_MODEL_PATH = Path(__file__).resolve().parents[2] / "models" / "behavior_iforest.joblib"
MODEL_PATH_ENV = "FINGUARD_BEHAVIOR_MODEL_PATH"
COLD_START_MIN_EVENTS = 5
COLD_START_RISK = 0.0


class BehaviorModelError(RuntimeError):
    pass


class BehaviorRiskService:
    def __init__(
        self,
        model_path: Path | None = None,
    ) -> None:
        configured_path = os.getenv(MODEL_PATH_ENV)
        self._model_path = model_path or (
            Path(configured_path) if configured_path else DEFAULT_MODEL_PATH
        )
        self._bundle: BehaviorModelBundle | None = None

    def _load_bundle(self) -> BehaviorModelBundle:
        if self._bundle is None:
            if not self._model_path.is_file():
                raise BehaviorModelError(f"Behavior model artifact not found: {self._model_path}")
            try:
                bundle = joblib.load(self._model_path)
            except Exception as error:
                raise BehaviorModelError("Behavior model artifact could not be loaded") from error
            if not isinstance(bundle, BehaviorModelBundle):
                raise BehaviorModelError("Behavior model artifact has an invalid type")
            if bundle.feature_version != FEATURE_VERSION or bundle.feature_names != FEATURE_NAMES:
                raise BehaviorModelError("Behavior model and runtime feature schema do not match")
            if (
                len(bundle.calibration_scores) == 0
                or not np.all(np.isfinite(bundle.calibration_scores))
                or np.any(np.diff(bundle.calibration_scores) < 0)
            ):
                raise BehaviorModelError("Behavior calibration scores are empty")
            try:
                valid_thresholds = 0 <= bundle.alert_threshold < bundle.critical_threshold <= 1
                valid_feature_counts = bundle.scaler.n_features_in_ == len(
                    FEATURE_NAMES
                ) and bundle.model.n_features_in_ == len(FEATURE_NAMES)
            except (AttributeError, TypeError) as error:
                raise BehaviorModelError("Behavior model metadata is incomplete") from error
            if not valid_thresholds:
                raise BehaviorModelError("Behavior model thresholds are invalid")
            if not valid_feature_counts:
                raise BehaviorModelError("Behavior model feature count is invalid")
            self._bundle = bundle
        return self._bundle

    def check_ready(self) -> None:
        self._load_bundle()

    def evaluate(self, request: BehaviorRiskRequest) -> BehaviorRiskResponse:
        bundle = self._load_bundle()
        valid_history = completed_events_in_window(request.history, request.current_attempt)
        vector = build_feature_vector(valid_history, request.current_attempt)
        if not np.all(np.isfinite(vector)):
            raise BehaviorModelError("Behavior feature vector contains a non-finite value")

        if len(valid_history) < COLD_START_MIN_EVENTS:
            return BehaviorRiskResponse(
                behavior_risk=COLD_START_RISK,
                behavior_risk_level=BehaviorRiskLevel.LOW,
                is_anomaly=False,
                raw_score=0.0,
                history_status=HistoryStatus.COLD_START,
                feature_version=bundle.feature_version,
                model_version=bundle.model_version,
            )

        try:
            scaled = bundle.scaler.transform(vector.reshape(1, -1))
            raw_score = float(-bundle.model.decision_function(scaled)[0])
        except Exception as error:
            raise BehaviorModelError("Behavior model inference failed") from error
        if not np.isfinite(raw_score):
            raise BehaviorModelError("Behavior model returned a non-finite score")
        behavior_risk = bundle.risk_from_raw_score(raw_score)

        if behavior_risk >= bundle.critical_threshold:
            level = BehaviorRiskLevel.CRITICAL
        elif behavior_risk >= bundle.alert_threshold:
            level = BehaviorRiskLevel.ALERT
        else:
            level = BehaviorRiskLevel.LOW
        is_anomaly = behavior_risk >= bundle.alert_threshold

        return BehaviorRiskResponse(
            behavior_risk=behavior_risk,
            behavior_risk_level=level,
            is_anomaly=is_anomaly,
            raw_score=raw_score,
            history_status=HistoryStatus.READY,
            feature_version=bundle.feature_version,
            model_version=bundle.model_version,
        )
