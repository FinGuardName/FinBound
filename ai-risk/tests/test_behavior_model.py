from pathlib import Path

import joblib
import numpy as np
import pytest

from app.behavior.service import BehaviorModelError, BehaviorRiskService
from app.feature_builder import FEATURE_NAMES
from datasets import synthetic_behavior
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
    assert first_metrics["validation"]["falsePositiveRateAtAlert"] <= 0.10
    assert first_metrics["heldOutTest"]["falsePositiveRateAtAlert"] <= 0.15
    assert first_metrics["heldOutTest"]["falsePositiveRateAtCritical"] <= 0.15
    assert 0 <= first.alert_threshold < first.critical_threshold <= 1
    assert first.alert_threshold == first_metrics["alertThreshold"]
    assert first.critical_threshold == first_metrics["criticalThreshold"]


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


def test_training_samples_use_runtime_feature_builder(monkeypatch: pytest.MonkeyPatch) -> None:
    calls = 0
    runtime_builder = synthetic_behavior.build_feature_vector

    def tracking_builder(*args: object, **kwargs: object) -> np.ndarray:
        nonlocal calls
        calls += 1
        return runtime_builder(*args, **kwargs)

    monkeypatch.setattr(synthetic_behavior, "build_feature_vector", tracking_builder)

    samples = generate_behavior_samples(normal_count=16, anomaly_count=8)

    assert calls == 24
    assert samples.normal.shape == (16, len(FEATURE_NAMES))
    assert samples.anomaly.shape == (8, len(FEATURE_NAMES))


def test_invalid_model_thresholds_are_rejected(tmp_path: Path) -> None:
    bundle, _ = train_bundle(42)
    bundle.alert_threshold = 0.95
    bundle.critical_threshold = 0.90
    model_path = tmp_path / "invalid-thresholds.joblib"
    joblib.dump(bundle, model_path)

    service = BehaviorRiskService(model_path=model_path)

    with pytest.raises(BehaviorModelError, match="thresholds are invalid"):
        service._load_bundle()
