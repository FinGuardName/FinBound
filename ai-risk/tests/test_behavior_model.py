import json
from pathlib import Path

import joblib
import numpy as np
import pytest

from app.behavior.model import BehaviorModelBundle
from app.behavior.service import BehaviorModelError, BehaviorRiskService
from app.feature_builder import FEATURE_NAMES
from datasets import synthetic_behavior
from datasets.synthetic_behavior import (
    ANOMALY_SCENARIOS,
    HARD_REQUEST_LIMIT_1M,
    NORMAL_SCENARIOS,
    generate_behavior_samples,
    split_behavior_samples,
)
from train.train_behavior import train_bundle

AI_RISK_ROOT = Path(__file__).resolve().parents[1]


def test_training_is_reproducible_for_fixed_seed() -> None:
    first, first_metrics = train_bundle(42)
    second, second_metrics = train_bundle(42)
    samples = generate_behavior_samples(random_seed=42)
    shifted = generate_behavior_samples(
        random_seed=2026, normal_count=80, anomaly_count=40, profile="shifted"
    )
    reference_features = np.vstack(
        [samples.normal, samples.anomaly, shifted.normal, shifted.anomaly]
    )

    first_scores = first.model.decision_function(first.scaler.transform(reference_features))
    second_scores = second.model.decision_function(second.scaler.transform(reference_features))

    np.testing.assert_array_equal(first_scores, second_scores)
    assert first_metrics == second_metrics
    assert first_metrics["validation"]["falsePositiveRateAtAlert"] <= 0.10
    assert first_metrics["heldOutTest"]["falsePositiveRateAtAlert"] <= 0.15
    assert first_metrics["heldOutTest"]["falsePositiveRateAtCritical"] <= 0.05
    held_out_scenarios = first_metrics["heldOutTest"]["scenarioMetrics"]
    assert held_out_scenarios["RAPID_REPETITION"]["recallAtAlert"] >= 0.90
    assert held_out_scenarios["RAPID_REPETITION"]["recallAtCritical"] <= 0.15
    assert held_out_scenarios["AFTER_HOURS_ACCUMULATION"]["recallAtCritical"] >= 0.90
    assert 0 <= first.alert_threshold < first.critical_threshold <= 1
    assert first.alert_threshold == first_metrics["alertThreshold"]
    assert first.critical_threshold == first_metrics["criticalThreshold"]
    assert set(first_metrics["heldOutTest"]["scenarioMetrics"]) == set(NORMAL_SCENARIOS) | set(
        ANOMALY_SCENARIOS
    )
    stress = first_metrics["distributionShiftStressTest"]
    assert stress["aggregate"]["rocAuc"]["minimum"] >= 0.90
    assert stress["aggregate"]["recallAtAlert"]["minimum"] >= 0.95
    assert stress["aggregate"]["falsePositiveRateAtCritical"]["maximum"] <= 0.10
    assert all(
        metrics["maxFalsePositiveRateAtCritical"] <= 0.15
        for metrics in stress["normalScenarioWorstCase"].values()
    )
    assert all(
        metrics["minRecallAtExpectedLevel"] >= 0.50
        for metrics in stress["anomalyScenarioWorstCase"].values()
    )
    assert stress["anomalyScenarioWorstCase"]["RAPID_REPETITION"]["maxRecallAtCritical"] <= 0.25


def test_behavior_splits_do_not_share_agent_sessions() -> None:
    samples = generate_behavior_samples(random_seed=42)
    splits = split_behavior_samples(samples, random_seed=42)

    assert splits.train_sessions.isdisjoint(splits.validation_sessions)
    assert splits.train_sessions.isdisjoint(splits.test_sessions)
    assert splits.validation_sessions.isdisjoint(splits.test_sessions)
    assert splits.train_normal.size > 0
    assert splits.validation_anomaly.size > 0
    assert splits.test_anomaly.size > 0
    assert set(splits.validation_normal_scenarios) == set(NORMAL_SCENARIOS)
    assert set(splits.test_normal_scenarios) == set(NORMAL_SCENARIOS)
    assert set(splits.validation_anomaly_scenarios) == set(ANOMALY_SCENARIOS)
    assert set(splits.test_anomaly_scenarios) == set(ANOMALY_SCENARIOS)


def test_core_anomaly_samples_keep_scope_and_hard_limit_separate() -> None:
    samples = generate_behavior_samples(random_seed=42)
    unique_customers = FEATURE_NAMES.index("uniqueCustomers5m")
    case_switches = FEATURE_NAMES.index("caseSwitchCount5m")
    requests_1m = FEATURE_NAMES.index("requestCount1m")

    assert np.all(samples.anomaly[:, unique_customers] == 1)
    assert np.all(samples.anomaly[:, case_switches] == 0)
    assert np.all(samples.anomaly[:, requests_1m] <= HARD_REQUEST_LIMIT_1M)
    assert set(samples.anomaly_scenarios) == set(ANOMALY_SCENARIOS)


def test_normal_samples_include_documented_variability() -> None:
    samples = generate_behavior_samples(random_seed=42)
    after_hours = FEATURE_NAMES.index("afterHoursAccess")

    assert set(samples.normal_scenarios) == set(NORMAL_SCENARIOS)
    assert np.any(samples.normal[:, after_hours] == 1)
    assert np.any(samples.normal[:, after_hours] == 0)


@pytest.mark.parametrize("profile", ["baseline", "shifted"])
def test_normal_and_anomaly_feature_ranges_overlap(profile: str) -> None:
    samples = generate_behavior_samples(random_seed=42, profile=profile)

    for feature_name in (
        "requestCount1m",
        "requestCount5m",
        "averageRequestIntervalMs",
        "financialDataRequestCount5m",
    ):
        feature_index = FEATURE_NAMES.index(feature_name)
        normal_min = np.min(samples.normal[:, feature_index])
        normal_max = np.max(samples.normal[:, feature_index])
        anomaly_min = np.min(samples.anomaly[:, feature_index])
        anomaly_max = np.max(samples.anomaly[:, feature_index])

        assert normal_min <= anomaly_max
        assert anomaly_min <= normal_max


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


def test_committed_artifact_and_metadata_are_consistent() -> None:
    bundle = joblib.load(AI_RISK_ROOT / "models" / "behavior_iforest.joblib")
    regenerated, regenerated_metrics = train_bundle(42)
    metadata = json.loads(
        (AI_RISK_ROOT / "models" / "behavior_iforest.json").read_text(encoding="utf-8")
    )
    metrics = json.loads(
        (AI_RISK_ROOT / "evaluate" / "behavior_metrics.json").read_text(encoding="utf-8")
    )

    assert metadata["modelVersion"] == bundle.model_version == metrics["modelVersion"]
    assert metadata["featureVersion"] == bundle.feature_version == metrics["featureVersion"]
    assert metadata["datasetVersion"] == bundle.dataset_version == metrics["datasetVersion"]
    assert tuple(metadata["featureNames"]) == bundle.feature_names
    assert metadata["alertThreshold"] == bundle.alert_threshold == metrics["alertThreshold"]
    assert (
        metadata["criticalThreshold"] == bundle.critical_threshold == metrics["criticalThreshold"]
    )
    assert metrics == regenerated_metrics

    np.testing.assert_array_equal(bundle.scaler.mean_, regenerated.scaler.mean_)
    np.testing.assert_array_equal(bundle.scaler.scale_, regenerated.scaler.scale_)
    # 커밋된 아티팩트는 개발자 PC에서, 재학습본은 CI 러너에서 만들어진다. BLAS 구현과 SIMD 경로가
    # 달라 모델 점수의 마지막 비트가 흔들린다(관측값 1.11e-16, double 1 ULP). 아래 decision_function
    # 비교와 같은 종류의 값이므로 같은 허용 오차를 쓴다. 정확히 같기를 요구하면 어느 기계에서
    # 학습했는지에 따라 통과 여부가 갈린다.
    np.testing.assert_allclose(
        bundle.calibration_scores, regenerated.calibration_scores, rtol=0, atol=1e-12
    )

    samples = generate_behavior_samples(random_seed=42)
    reference_features = np.vstack([samples.normal, samples.anomaly])
    committed_scores = bundle.model.decision_function(bundle.scaler.transform(reference_features))
    regenerated_scores = regenerated.model.decision_function(
        regenerated.scaler.transform(reference_features)
    )
    # 원시 점수가 여기까지 맞으면 커밋된 아티팩트가 이 학습 코드의 산물이라는 것은 증명된다.
    # 여기서 파생값인 risk 순위까지 대조하지 않는다. 순위는 calibration_scores 안에서의
    # searchsorted 결과라 동점 구간에서 불연속이고, 마지막 비트 하나가 달라지면 순위가 통째로
    # 몇 칸 뛴다(CI에서 1920개 중 21개, 최대 0.0125 = 3칸). 재현성 검증에 잡음만 보탠다.
    # 순위 계산 자체는 test_risk_from_raw_score_ranks_by_calibration_position 이 고정 데이터로 본다.
    np.testing.assert_allclose(committed_scores, regenerated_scores, rtol=0, atol=1e-12)


def test_risk_from_raw_score_ranks_by_calibration_position() -> None:
    bundle = BehaviorModelBundle(
        model=None,
        scaler=None,
        calibration_scores=np.array([0.1, 0.2, 0.2, 0.2, 0.5]),
        feature_names=("f0",),
        feature_version="test",
        model_version="test",
        dataset_version="test",
        random_seed=0,
        alert_threshold=0.70,
        critical_threshold=0.90,
    )

    assert bundle.risk_from_raw_score(0.05) == 0.0
    assert bundle.risk_from_raw_score(0.10) == 0.2
    assert bundle.risk_from_raw_score(0.15) == 0.2
    assert bundle.risk_from_raw_score(0.50) == 1.0
    assert bundle.risk_from_raw_score(9.90) == 1.0

    # 동점 세 개를 사이에 두고 마지막 비트 하나 차이로 순위가 3칸 뛴다.
    # 이 불연속 때문에 재학습본과 순위를 대조하면 기계마다 결과가 갈린다.
    assert bundle.risk_from_raw_score(float(np.nextafter(0.2, 0.0))) == 0.2
    assert bundle.risk_from_raw_score(0.2) == 0.8
