import numpy as np

from app.feature_builder import FEATURE_NAMES

DATASET_VERSION = "synthetic-agent-log-1"


def generate_behavior_samples(
    random_seed: int = 42,
    normal_count: int = 800,
    anomaly_count: int = 160,
) -> tuple[np.ndarray, np.ndarray]:
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
    return normal, anomaly
