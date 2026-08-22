from pathlib import Path

import numpy as np
import pytest

from app.behavior.service import BehaviorModelError, BehaviorRiskService
from datasets.synthetic_behavior import generate_behavior_samples, split_behavior_samples
from train.train_behavior import train_bundle


def test_training_is_reproducible_for_fixed_seed() -> None:
    first, first_metrics = train_bundle(42)
    second, second_metrics = train_bundle(42)
    sample = np.ones((1, len(first.feature_names)))

    first_score = first.model.decision_function(first.scaler.transform(sample))
    second_score = second.model.decision_function(second.scaler.transform(sample))

    assert np.array_equal(first_score, second_score)
    assert first_metrics == second_metrics
    assert first_metrics["heldOutTest"]["falsePositiveRateAtCritical"] <= 0.15


def test_behavior_splits_do_not_share_agent_sessions() -> None:
    samples = generate_behavior_samples(random_seed=42)
    splits = split_behavior_samples(samples, random_seed=42)

    assert splits.train_sessions.isdisjoint(splits.validation_sessions)
    assert splits.train_sessions.isdisjoint(splits.test_sessions)
    assert splits.validation_sessions.isdisjoint(splits.test_sessions)
    assert splits.train_normal.size > 0
    assert splits.validation_anomaly.size > 0
    assert splits.test_anomaly.size > 0


def test_missing_model_is_an_explicit_error(tmp_path: Path) -> None:
    service = BehaviorRiskService(model_path=tmp_path / "missing.joblib")

    with pytest.raises(BehaviorModelError, match="artifact not found"):
        service._load_bundle()
