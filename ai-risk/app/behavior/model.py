from dataclasses import dataclass

import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler


@dataclass
class BehaviorModelBundle:
    model: IsolationForest
    scaler: StandardScaler
    calibration_scores: np.ndarray
    feature_names: tuple[str, ...]
    feature_version: str
    model_version: str
    dataset_version: str
    random_seed: int
    alert_threshold: float
    critical_threshold: float

    def risk_from_raw_score(self, raw_score: float) -> float:
        rank = np.searchsorted(self.calibration_scores, raw_score, side="right")
        return float(rank / len(self.calibration_scores))
