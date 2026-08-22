from pathlib import Path

import numpy as np
import pytest

from app.behavior.service import BehaviorModelError, BehaviorRiskService
from train.train_behavior import train_bundle


def test_training_is_reproducible_for_fixed_seed() -> None:
    first, first_metrics = train_bundle(42)
    second, second_metrics = train_bundle(42)
    sample = np.ones((1, len(first.feature_names)))

    first_score = first.model.decision_function(first.scaler.transform(sample))
    second_score = second.model.decision_function(second.scaler.transform(sample))

    assert np.array_equal(first_score, second_score)
    assert first_metrics == second_metrics
    assert first_metrics["falsePositiveRateAtCritical"] <= 0.11


def test_missing_model_is_an_explicit_error(tmp_path: Path) -> None:
    service = BehaviorRiskService(model_path=tmp_path / "missing.joblib")

    with pytest.raises(BehaviorModelError, match="artifact not found"):
        service._load_bundle()
