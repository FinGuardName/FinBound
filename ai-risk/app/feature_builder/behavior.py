from collections.abc import Iterable
from datetime import timedelta
from itertools import pairwise
from zoneinfo import ZoneInfo

import numpy as np

from app.schemas.behavior import CompletedBehaviorEvent, CurrentToolCallAttempt, Decision

FEATURE_VERSION = "behavior-features-1"
BEHAVIOR_WINDOW = timedelta(minutes=5)
BUSINESS_TIMEZONE = ZoneInfo("Asia/Seoul")
FEATURE_NAMES = (
    "requestCount1m",
    "requestCount5m",
    "uniqueCustomers5m",
    "uniqueTools5m",
    "blockRatio5m",
    "errorRatio5m",
    "averageRequestIntervalMs",
    "caseSwitchCount5m",
    "financialDataRequestCount5m",
    "afterHoursAccess",
)


def completed_events_in_window(
    history: Iterable[CompletedBehaviorEvent], current: CurrentToolCallAttempt
) -> list[CompletedBehaviorEvent]:
    window_start = current.requested_at - BEHAVIOR_WINDOW
    return sorted(
        (event for event in history if window_start <= event.requested_at < current.requested_at),
        key=lambda event: event.requested_at,
    )


def build_feature_vector(
    history: Iterable[CompletedBehaviorEvent], current: CurrentToolCallAttempt
) -> np.ndarray:
    recent_5m = completed_events_in_window(history, current)
    one_minute_ago = current.requested_at - timedelta(minutes=1)
    recent_1m = [event for event in recent_5m if event.requested_at >= one_minute_ago]

    completed_count = len(recent_5m)
    block_count = sum(event.decision is Decision.BLOCK for event in recent_5m)
    error_count = sum(not event.success for event in recent_5m)

    timeline = [event.requested_at for event in recent_5m] + [current.requested_at]
    intervals = [(right - left).total_seconds() * 1000 for left, right in pairwise(timeline)]
    average_interval = float(np.mean(intervals)) if intervals else 300_000.0

    cases = [event.case_id for event in recent_5m] + [current.case_id]
    case_switches = sum(left != right for left, right in pairwise(cases))
    financial_requests = sum(max(1, len(event.requested_data)) for event in recent_5m)
    financial_requests += len(current.requested_data)
    business_hour = current.requested_at.astimezone(BUSINESS_TIMEZONE).hour

    values = (
        len(recent_1m) + 1,
        len(recent_5m) + 1,
        len({event.target_consumer_id for event in recent_5m} | {current.target_consumer_id}),
        len({event.tool for event in recent_5m} | {current.tool}),
        block_count / completed_count if completed_count else 0.0,
        error_count / completed_count if completed_count else 0.0,
        average_interval,
        case_switches,
        financial_requests,
        float(business_hour < 9 or business_hour >= 18),
    )
    return np.asarray(values, dtype=np.float64)
