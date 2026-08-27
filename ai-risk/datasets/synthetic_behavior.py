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

DATASET_VERSION = "synthetic-agent-log-2"
SAMPLES_PER_SESSION = 8
HARD_REQUEST_LIMIT_1M = 30
KST = timezone(timedelta(hours=9))
TOOLS = tuple(FinancialTool)
NORMAL_SCENARIOS = (
    "STANDARD_BUSINESS_HOURS",
    "END_OF_DAY_INCREASE",
    "LEGITIMATE_OVERTIME",
    "HIGH_VALUE_CASE_EXTRA_READS",
    "MOMENTARY_SPIKE",
)
ANOMALY_SCENARIOS = (
    "RAPID_REPETITION",
    "AFTER_HOURS_ACCUMULATION",
    "ERROR_BURST",
)
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
    normal_scenarios: np.ndarray
    anomaly_scenarios: np.ndarray


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
    validation_normal_scenarios: np.ndarray
    validation_anomaly_scenarios: np.ndarray
    test_normal_scenarios: np.ndarray
    test_anomaly_scenarios: np.ndarray


def _tool_for(rng: np.random.Generator) -> FinancialTool:
    return TOOLS[int(rng.integers(0, len(TOOLS)))]


def _normal_interval_seconds(rng: np.random.Generator, scenario: str, event_index: int) -> int:
    if scenario == "END_OF_DAY_INCREASE":
        return int(rng.integers(10, 21))
    if scenario == "HIGH_VALUE_CASE_EXTRA_READS":
        return int(rng.integers(15, 31))
    if scenario == "MOMENTARY_SPIKE" and 5 <= event_index % 12 <= 7:
        return int(rng.integers(8, 16))
    return int(rng.integers(30, 71))


def _start_hour(rng: np.random.Generator, scenario: str) -> int:
    if scenario == "END_OF_DAY_INCREASE":
        return 17
    if scenario in {"LEGITIMATE_OVERTIME", "AFTER_HOURS_ACCUMULATION"}:
        return 20
    return int(rng.integers(10, 16))


def _latency_ms(rng: np.random.Generator, tool: FinancialTool) -> int:
    ranges = {
        FinancialTool.CREDIT_SCORE_READ: (80, 220),
        FinancialTool.INCOME_READ: (180, 480),
        FinancialTool.DEBT_READ: (350, 800),
    }
    low, high = ranges[tool]
    return int(rng.integers(low, high + 1))


def _session_vectors(
    rng: np.random.Generator,
    session_index: int,
    scenario: str,
    anomalous: bool,
) -> np.ndarray:
    warmup_events = 24 if anomalous else 9
    start_hour = _start_hour(rng, scenario)
    current_time = datetime(2026, 8, 17, start_hour, 0, tzinfo=KST)
    history: list[CompletedBehaviorEvent] = []
    vectors: list[np.ndarray] = []
    base_case_id = f"CASE-{session_index:03d}"
    base_consumer_id = f"CUST-{session_index:04d}"
    session_tool = _tool_for(rng)

    for event_index in range(warmup_events + SAMPLES_PER_SESSION):
        if anomalous:
            interval_seconds = {
                "RAPID_REPETITION": int(rng.integers(3, 7)),
                "AFTER_HOURS_ACCUMULATION": int(rng.integers(4, 10)),
                "ERROR_BURST": int(rng.integers(5, 13)),
            }[scenario]
        else:
            interval_seconds = _normal_interval_seconds(rng, scenario, event_index)
        current_time += timedelta(seconds=interval_seconds)
        stable_tool_scenarios = {
            "STANDARD_BUSINESS_HOURS",
            "LEGITIMATE_OVERTIME",
            "MOMENTARY_SPIKE",
        }
        if (
            anomalous
            or scenario in stable_tool_scenarios
            or scenario == "END_OF_DAY_INCREASE"
            and event_index % 3
        ):
            tool = session_tool
        else:
            tool = _tool_for(rng)
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

        if anomalous and scenario == "ERROR_BURST":
            success = bool(rng.random() >= 0.65)
            decision = Decision.ALLOW
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
                latencyMs=_latency_ms(rng, tool),
            )
        )

    return np.vstack(vectors)


def _generate_grouped_vectors(
    rng: np.random.Generator,
    prefix: str,
    count: int,
    anomalous: bool,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    vectors: list[np.ndarray] = []
    groups: list[str] = []
    scenarios: list[str] = []
    scenario_catalog = ANOMALY_SCENARIOS if anomalous else NORMAL_SCENARIOS
    for session_index in range(ceil(count / SAMPLES_PER_SESSION)):
        session_id = f"{prefix}-{session_index:03d}"
        scenario = scenario_catalog[session_index % len(scenario_catalog)]
        session_vectors = _session_vectors(rng, session_index, scenario, anomalous)
        vectors.extend(session_vectors)
        groups.extend([session_id] * len(session_vectors))
        scenarios.extend([scenario] * len(session_vectors))
    return np.vstack(vectors[:count]), np.asarray(groups[:count]), np.asarray(scenarios[:count])


def generate_behavior_samples(
    random_seed: int = 42,
    normal_count: int = 1600,
    anomaly_count: int = 320,
) -> SyntheticBehaviorSamples:
    if normal_count <= 0 or anomaly_count <= 0:
        raise ValueError("Behavior sample counts must be positive")
    rng = np.random.default_rng(random_seed)
    normal, normal_sessions, normal_scenarios = _generate_grouped_vectors(
        rng, "normal-session", normal_count, anomalous=False
    )
    anomaly, anomaly_sessions, anomaly_scenarios = _generate_grouped_vectors(
        rng, "anomaly-session", anomaly_count, anomalous=True
    )

    if normal.shape[1] != len(FEATURE_NAMES):
        raise ValueError("Synthetic dataset does not match the feature schema")
    return SyntheticBehaviorSamples(
        normal=normal,
        anomaly=anomaly,
        normal_sessions=normal_sessions,
        anomaly_sessions=anomaly_sessions,
        normal_scenarios=normal_scenarios,
        anomaly_scenarios=anomaly_scenarios,
    )


def _group_split_indices(
    values: np.ndarray,
    groups: np.ndarray,
    scenarios: np.ndarray,
    test_size: float,
    random_seed: int,
) -> tuple[np.ndarray, np.ndarray]:
    first_indices: list[int] = []
    second_indices: list[int] = []
    for scenario_offset, scenario in enumerate(np.unique(scenarios)):
        scenario_indices = np.flatnonzero(scenarios == scenario)
        first, second = next(
            GroupShuffleSplit(
                n_splits=1,
                test_size=test_size,
                random_state=random_seed + scenario_offset,
            ).split(values[scenario_indices], groups=groups[scenario_indices])
        )
        first_indices.extend(scenario_indices[first])
        second_indices.extend(scenario_indices[second])
    return np.asarray(first_indices), np.asarray(second_indices)


def split_behavior_samples(
    samples: SyntheticBehaviorSamples, random_seed: int = 42
) -> BehaviorDataSplits:
    train_indices, remainder_indices = _group_split_indices(
        samples.normal,
        samples.normal_sessions,
        samples.normal_scenarios,
        test_size=0.30,
        random_seed=random_seed,
    )
    remainder_normal = samples.normal[remainder_indices]
    remainder_groups = samples.normal_sessions[remainder_indices]
    remainder_scenarios = samples.normal_scenarios[remainder_indices]
    validation_normal_indices, test_normal_indices = _group_split_indices(
        remainder_normal,
        remainder_groups,
        remainder_scenarios,
        test_size=0.50,
        random_seed=random_seed + 1,
    )
    validation_anomaly_indices, test_anomaly_indices = _group_split_indices(
        samples.anomaly,
        samples.anomaly_sessions,
        samples.anomaly_scenarios,
        test_size=0.50,
        random_seed=random_seed + 2,
    )

    return BehaviorDataSplits(
        train_normal=samples.normal[train_indices],
        validation_normal=remainder_normal[validation_normal_indices],
        validation_anomaly=samples.anomaly[validation_anomaly_indices],
        test_normal=remainder_normal[test_normal_indices],
        test_anomaly=samples.anomaly[test_anomaly_indices],
        train_sessions=frozenset(samples.normal_sessions[train_indices]),
        validation_sessions=frozenset(remainder_groups[validation_normal_indices])
        | frozenset(samples.anomaly_sessions[validation_anomaly_indices]),
        test_sessions=frozenset(remainder_groups[test_normal_indices])
        | frozenset(samples.anomaly_sessions[test_anomaly_indices]),
        validation_normal_scenarios=remainder_scenarios[validation_normal_indices],
        validation_anomaly_scenarios=samples.anomaly_scenarios[validation_anomaly_indices],
        test_normal_scenarios=remainder_scenarios[test_normal_indices],
        test_anomaly_scenarios=samples.anomaly_scenarios[test_anomaly_indices],
    )
