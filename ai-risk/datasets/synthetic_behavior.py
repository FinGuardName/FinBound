from dataclasses import dataclass

import numpy as np
from sklearn.model_selection import GroupShuffleSplit

from app.feature_builder import FEATURE_NAMES

DATASET_VERSION = "synthetic-agent-log-1"
SAMPLES_PER_SESSION = 8


@dataclass(frozen=True)
class SyntheticBehaviorSamples:
    normal: np.ndarray
    anomaly: np.ndarray
    normal_sessions: np.ndarray
    anomaly_sessions: np.ndarray


@dataclass(frozen=True)
class BehaviorDataSplits:
    train_normal: np.ndarray
    validation_normal: np.ndarray
    validation_anomaly: np.ndarray
    test_normal: np.ndarray
    test_anomaly: np.ndarray
    train_sessions: frozenset[str]
    validation_sessions: frozenset[str]
    test_sessions: frozenset[str]


def _session_ids(prefix: str, count: int) -> np.ndarray:
    return np.asarray(
        [f"{prefix}-{index // SAMPLES_PER_SESSION:03d}" for index in range(count)]
    )


def generate_behavior_samples(
    random_seed: int = 42,
    normal_count: int = 800,
    anomaly_count: int = 160,
) -> SyntheticBehaviorSamples:
    rng = np.random.default_rng(random_seed)

    normal = np.column_stack(
        [
            rng.integers(1, 6, normal_count),
            rng.integers(2, 16, normal_count),
            rng.integers(1, 3, normal_count),
            rng.integers(1, 4, normal_count),
            rng.beta(1, 15, normal_count),
            rng.beta(1, 20, normal_count),
            rng.lognormal(np.log(45_000), 0.45, normal_count),
            rng.integers(0, 2, normal_count),
            rng.integers(2, 18, normal_count),
            rng.binomial(1, 0.08, normal_count),
        ]
    ).astype(np.float64)

    anomaly = np.column_stack(
        [
            rng.integers(10, 25, anomaly_count),
            rng.integers(20, 55, anomaly_count),
            np.ones(anomaly_count),
            rng.integers(1, 4, anomaly_count),
            rng.beta(2, 8, anomaly_count),
            rng.beta(2, 8, anomaly_count),
            rng.lognormal(np.log(1_200), 0.35, anomaly_count),
            np.zeros(anomaly_count),
            rng.integers(20, 60, anomaly_count),
            rng.binomial(1, 0.85, anomaly_count),
        ]
    ).astype(np.float64)

    if normal.shape[1] != len(FEATURE_NAMES):
        raise ValueError("Synthetic dataset does not match the feature schema")
    return SyntheticBehaviorSamples(
        normal=normal,
        anomaly=anomaly,
        normal_sessions=_session_ids("normal-session", normal_count),
        anomaly_sessions=_session_ids("anomaly-session", anomaly_count),
    )


def _group_split(
    values: np.ndarray,
    groups: np.ndarray,
    test_size: float,
    random_seed: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    first, second = next(
        GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=random_seed).split(
            values, groups=groups
        )
    )
    return values[first], values[second], groups[first], groups[second]


def split_behavior_samples(
    samples: SyntheticBehaviorSamples, random_seed: int = 42
) -> BehaviorDataSplits:
    train_normal, remainder_normal, train_groups, remainder_groups = _group_split(
        samples.normal, samples.normal_sessions, test_size=0.30, random_seed=random_seed
    )
    validation_normal, test_normal, validation_normal_groups, test_normal_groups = _group_split(
        remainder_normal, remainder_groups, test_size=0.50, random_seed=random_seed + 1
    )
    validation_anomaly, test_anomaly, validation_anomaly_groups, test_anomaly_groups = (
        _group_split(
            samples.anomaly,
            samples.anomaly_sessions,
            test_size=0.50,
            random_seed=random_seed + 2,
        )
    )

    return BehaviorDataSplits(
        train_normal=train_normal,
        validation_normal=validation_normal,
        validation_anomaly=validation_anomaly,
        test_normal=test_normal,
        test_anomaly=test_anomaly,
        train_sessions=frozenset(train_groups),
        validation_sessions=frozenset(validation_normal_groups)
        | frozenset(validation_anomaly_groups),
        test_sessions=frozenset(test_normal_groups) | frozenset(test_anomaly_groups),
    )
