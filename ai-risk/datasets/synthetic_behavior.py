from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from math import ceil

import numpy as np
from sklearn.model_selection import GroupShuffleSplit

from app.feature_builder import FEATURE_NAMES, build_feature_vector
from app.schemas.behavior import (
    CompletedBehaviorEvent,
    CurrentToolCallAttempt,
    Decision,
    FinancialDataType,
    FinancialTool,
)

DATASET_VERSION = "synthetic-agent-log-1"
SAMPLES_PER_SESSION = 8
KST = timezone(timedelta(hours=9))
TOOLS = tuple(FinancialTool)
TOOL_DATA = {
    FinancialTool.CREDIT_SCORE_READ: FinancialDataType.CREDIT_SCORE,
    FinancialTool.INCOME_READ: FinancialDataType.INCOME,
    FinancialTool.DEBT_READ: FinancialDataType.DEBT,
}


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


def _tool_for(rng: np.random.Generator) -> FinancialTool:
    return TOOLS[int(rng.integers(0, len(TOOLS)))]


def _session_vectors(
    rng: np.random.Generator,
    session_index: int,
    anomalous: bool,
) -> np.ndarray:
    mode = session_index % 3
    warmup_events = 24 if anomalous else 7
    start_hour = 22 if anomalous else int(rng.integers(10, 16))
    current_time = datetime(2026, 8, 17, start_hour, 0, tzinfo=KST)
    history: list[CompletedBehaviorEvent] = []
    vectors: list[np.ndarray] = []
    base_case_id = f"CASE-{session_index:03d}"
    base_consumer_id = f"CUST-{session_index:04d}"
    session_tool = _tool_for(rng)

    for event_index in range(warmup_events + SAMPLES_PER_SESSION):
        interval_seconds = int(rng.integers(2, 9)) if anomalous else int(rng.integers(20, 51))
        current_time += timedelta(seconds=interval_seconds)
        tool = session_tool if not anomalous and mode == 1 else _tool_for(rng)

        if anomalous and mode == 1:
            case_id = f"{base_case_id}-{event_index % 4}"
            consumer_id = f"{base_consumer_id}-{event_index % 4}"
        else:
            case_id = base_case_id
            consumer_id = base_consumer_id

        current_attempt = CurrentToolCallAttempt(
            caseId=case_id,
            targetConsumerId=consumer_id,
            tool=tool,
            requestedData=[TOOL_DATA[tool]],
            requestedAt=current_time,
        )
        if event_index >= warmup_events:
            vectors.append(build_feature_vector(history, current_attempt))

        if anomalous and mode == 2:
            success = bool(rng.random() >= 0.65)
            decision = Decision.ALLOW
        elif anomalous and mode == 1:
            decision = Decision.BLOCK if rng.random() < 0.35 else Decision.ALLOW
            success = decision is Decision.ALLOW
        else:
            decision = Decision.BLOCK if rng.random() < 0.02 else Decision.ALLOW
            success = decision is Decision.ALLOW and rng.random() >= 0.02

        history.append(
            CompletedBehaviorEvent(
                requestId=f"REQ-{session_index:03d}-{event_index:03d}",
                caseId=case_id,
                targetConsumerId=consumer_id,
                tool=tool,
                requestedData=[TOOL_DATA[tool]],
                requestedAt=current_time,
                decision=decision,
                success=success,
                latencyMs=int(rng.integers(40, 800)),
            )
        )

    return np.vstack(vectors)


def _generate_grouped_vectors(
    rng: np.random.Generator,
    prefix: str,
    count: int,
    anomalous: bool,
) -> tuple[np.ndarray, np.ndarray]:
    vectors: list[np.ndarray] = []
    groups: list[str] = []
    for session_index in range(ceil(count / SAMPLES_PER_SESSION)):
        session_id = f"{prefix}-{session_index:03d}"
        session_vectors = _session_vectors(rng, session_index, anomalous)
        vectors.extend(session_vectors)
        groups.extend([session_id] * len(session_vectors))
    return np.vstack(vectors[:count]), np.asarray(groups[:count])


def generate_behavior_samples(
    random_seed: int = 42,
    normal_count: int = 800,
    anomaly_count: int = 160,
) -> SyntheticBehaviorSamples:
    if normal_count <= 0 or anomaly_count <= 0:
        raise ValueError("Behavior sample counts must be positive")
    rng = np.random.default_rng(random_seed)
    normal, normal_sessions = _generate_grouped_vectors(
        rng, "normal-session", normal_count, anomalous=False
    )
    anomaly, anomaly_sessions = _generate_grouped_vectors(
        rng, "anomaly-session", anomaly_count, anomalous=True
    )

    if normal.shape[1] != len(FEATURE_NAMES):
        raise ValueError("Synthetic dataset does not match the feature schema")
    return SyntheticBehaviorSamples(
        normal=normal,
        anomaly=anomaly,
        normal_sessions=normal_sessions,
        anomaly_sessions=anomaly_sessions,
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
    validation_anomaly, test_anomaly, validation_anomaly_groups, test_anomaly_groups = _group_split(
        samples.anomaly,
        samples.anomaly_sessions,
        test_size=0.50,
        random_seed=random_seed + 2,
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
