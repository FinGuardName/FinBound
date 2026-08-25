import argparse
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.metrics import precision_recall_fscore_support, roc_auc_score
from sklearn.preprocessing import StandardScaler

from app.behavior.model import BehaviorModelBundle
from app.feature_builder import FEATURE_NAMES, FEATURE_VERSION
from datasets.synthetic_behavior import (
    DATASET_VERSION,
    BehaviorDataSplits,
    generate_behavior_samples,
    split_behavior_samples,
)

MODEL_VERSION = "iforest-1"
MAX_VALIDATION_FALSE_POSITIVE_RATE = 0.10
ALERT_NORMAL_QUANTILE = 0.90


def _calibrated_risks(
    model: IsolationForest,
    scaler: StandardScaler,
    calibration_scores: np.ndarray,
    features: np.ndarray,
) -> np.ndarray:
    raw_scores = -model.decision_function(scaler.transform(features))
    return np.searchsorted(calibration_scores, raw_scores, side="right") / len(calibration_scores)


def _select_thresholds(
    normal_risks: np.ndarray,
    anomaly_risks: np.ndarray,
) -> tuple[float, float]:
    labels = np.concatenate([np.zeros(len(normal_risks)), np.ones(len(anomaly_risks))])
    risks = np.concatenate([normal_risks, anomaly_risks])
    candidates = np.unique(risks)
    best: tuple[float, float, float] | None = None

    for candidate in candidates:
        predictions = risks >= candidate
        false_positive_rate = float(np.mean(predictions[labels == 0]))
        if false_positive_rate > MAX_VALIDATION_FALSE_POSITIVE_RATE:
            continue
        _, recall, f1, _ = precision_recall_fscore_support(
            labels, predictions, average="binary", zero_division=0
        )
        score = (float(f1), float(recall), -float(candidate))
        if best is None or score > best:
            best = score

    if best is None:
        raise ValueError("No critical threshold satisfies the validation FPR constraint")

    critical_threshold = -best[2]
    alert_threshold = float(np.quantile(normal_risks, ALERT_NORMAL_QUANTILE, method="higher"))
    if alert_threshold >= critical_threshold:
        alert_threshold = max(0.0, float(np.nextafter(critical_threshold, 0.0)))
    return alert_threshold, critical_threshold


def _evaluate(
    model: IsolationForest,
    scaler: StandardScaler,
    calibration_scores: np.ndarray,
    normal: np.ndarray,
    anomaly: np.ndarray,
    alert_threshold: float,
    critical_threshold: float,
) -> dict[str, float | int]:
    features = np.vstack([normal, anomaly])
    labels = np.concatenate([np.zeros(len(normal)), np.ones(len(anomaly))])
    risks = _calibrated_risks(model, scaler, calibration_scores, features)
    alert_predictions = risks >= alert_threshold
    predictions = risks >= critical_threshold
    precision, recall, f1, _ = precision_recall_fscore_support(
        labels, predictions, average="binary", zero_division=0
    )
    return {
        "samples": len(features),
        "normalSamples": len(normal),
        "anomalySamples": len(anomaly),
        "falsePositiveRateAtAlert": float(np.mean(alert_predictions[labels == 0])),
        "recallAtAlert": float(np.mean(alert_predictions[labels == 1])),
        "precisionAtCritical": float(precision),
        "recallAtCritical": float(recall),
        "f1AtCritical": float(f1),
        "falsePositiveRateAtCritical": float(np.mean(predictions[labels == 0])),
        "rocAuc": float(roc_auc_score(labels, risks)),
    }


def _split_summary(splits: BehaviorDataSplits) -> dict[str, dict[str, int]]:
    return {
        "train": {
            "samples": len(splits.train_normal),
            "sessions": len(splits.train_sessions),
        },
        "validation": {
            "samples": len(splits.validation_normal) + len(splits.validation_anomaly),
            "sessions": len(splits.validation_sessions),
        },
        "heldOutTest": {
            "samples": len(splits.test_normal) + len(splits.test_anomaly),
            "sessions": len(splits.test_sessions),
        },
    }


def train_bundle(random_seed: int = 42) -> tuple[BehaviorModelBundle, dict[str, Any]]:
    samples = generate_behavior_samples(random_seed=random_seed)
    splits = split_behavior_samples(samples, random_seed=random_seed)

    scaler = StandardScaler().fit(splits.train_normal)
    model = IsolationForest(
        n_estimators=200,
        max_samples="auto",
        contamination=0.05,
        random_state=random_seed,
        n_jobs=1,
    ).fit(scaler.transform(splits.train_normal))

    calibration_scores = np.sort(
        -model.decision_function(scaler.transform(splits.validation_normal))
    )
    validation_normal_risks = _calibrated_risks(
        model, scaler, calibration_scores, splits.validation_normal
    )
    validation_anomaly_risks = _calibrated_risks(
        model, scaler, calibration_scores, splits.validation_anomaly
    )
    alert_threshold, critical_threshold = _select_thresholds(
        validation_normal_risks, validation_anomaly_risks
    )
    bundle = BehaviorModelBundle(
        model=model,
        scaler=scaler,
        calibration_scores=calibration_scores,
        feature_names=FEATURE_NAMES,
        feature_version=FEATURE_VERSION,
        model_version=MODEL_VERSION,
        dataset_version=DATASET_VERSION,
        random_seed=random_seed,
        alert_threshold=alert_threshold,
        critical_threshold=critical_threshold,
    )
    metrics: dict[str, Any] = {
        "modelVersion": MODEL_VERSION,
        "featureVersion": FEATURE_VERSION,
        "datasetVersion": DATASET_VERSION,
        "randomSeed": random_seed,
        "alertThreshold": alert_threshold,
        "criticalThreshold": critical_threshold,
        "thresholdSelection": {
            "method": "validation-f1-with-fpr-constraint",
            "maxFalsePositiveRate": MAX_VALIDATION_FALSE_POSITIVE_RATE,
            "alertNormalQuantile": ALERT_NORMAL_QUANTILE,
        },
        "splitSummary": _split_summary(splits),
        "validation": _evaluate(
            model,
            scaler,
            calibration_scores,
            splits.validation_normal,
            splits.validation_anomaly,
            alert_threshold,
            critical_threshold,
        ),
        "heldOutTest": _evaluate(
            model,
            scaler,
            calibration_scores,
            splits.test_normal,
            splits.test_anomaly,
            alert_threshold,
            critical_threshold,
        ),
    }
    return bundle, metrics


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--model-output", type=Path, default=Path("models/behavior_iforest.joblib"))
    parser.add_argument(
        "--metadata-output", type=Path, default=Path("models/behavior_iforest.json")
    )
    parser.add_argument(
        "--evaluation-output", type=Path, default=Path("evaluate/behavior_metrics.json")
    )
    args = parser.parse_args()

    bundle, metrics = train_bundle(args.seed)
    args.model_output.parent.mkdir(parents=True, exist_ok=True)
    args.metadata_output.parent.mkdir(parents=True, exist_ok=True)
    args.evaluation_output.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(bundle, args.model_output)

    metadata = {
        "modelVersion": bundle.model_version,
        "featureVersion": bundle.feature_version,
        "datasetVersion": bundle.dataset_version,
        "randomSeed": bundle.random_seed,
        "alertThreshold": bundle.alert_threshold,
        "criticalThreshold": bundle.critical_threshold,
        "trainedAt": datetime.now(UTC).isoformat(),
        "featureNames": list(bundle.feature_names),
        "splitSummary": metrics["splitSummary"],
    }
    args.metadata_output.write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    args.evaluation_output.write_text(
        json.dumps(metrics, indent=2) + "\n", encoding="utf-8", newline="\n"
    )


if __name__ == "__main__":
    main()
