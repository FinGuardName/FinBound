import argparse
import json
from datetime import UTC, datetime
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.metrics import precision_recall_fscore_support, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

from app.behavior.model import BehaviorModelBundle
from app.feature_builder import FEATURE_NAMES, FEATURE_VERSION
from datasets.synthetic_behavior import DATASET_VERSION, generate_behavior_samples

MODEL_VERSION = "iforest-1"


def train_bundle(random_seed: int = 42) -> tuple[BehaviorModelBundle, dict[str, float | int | str]]:
    normal, anomaly = generate_behavior_samples(random_seed=random_seed)
    train_normal, validation_normal = train_test_split(
        normal, test_size=0.25, random_state=random_seed
    )
    validation = np.vstack([validation_normal, anomaly])
    labels = np.concatenate([np.zeros(len(validation_normal)), np.ones(len(anomaly))])

    scaler = StandardScaler().fit(train_normal)
    model = IsolationForest(
        n_estimators=200,
        max_samples="auto",
        contamination=0.05,
        random_state=random_seed,
        n_jobs=1,
    ).fit(scaler.transform(train_normal))

    validation_raw = -model.decision_function(scaler.transform(validation))
    calibration_scores = np.sort(-model.decision_function(scaler.transform(validation_normal)))
    risks = np.searchsorted(calibration_scores, validation_raw, side="right") / len(
        calibration_scores
    )
    predictions = risks >= 0.90
    precision, recall, f1, _ = precision_recall_fscore_support(
        labels, predictions, average="binary", zero_division=0
    )
    false_positive_rate = float(np.mean(predictions[labels == 0]))

    bundle = BehaviorModelBundle(
        model=model,
        scaler=scaler,
        calibration_scores=calibration_scores,
        feature_names=FEATURE_NAMES,
        feature_version=FEATURE_VERSION,
        model_version=MODEL_VERSION,
        dataset_version=DATASET_VERSION,
        random_seed=random_seed,
    )
    metrics: dict[str, float | int | str] = {
        "modelVersion": MODEL_VERSION,
        "featureVersion": FEATURE_VERSION,
        "datasetVersion": DATASET_VERSION,
        "randomSeed": random_seed,
        "precisionAtCritical": float(precision),
        "recallAtCritical": float(recall),
        "f1AtCritical": float(f1),
        "falsePositiveRateAtCritical": false_positive_rate,
        "rocAuc": float(roc_auc_score(labels, risks)),
    }
    return bundle, metrics


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--model-output", type=Path, default=Path("models/behavior_iforest.joblib"))
    parser.add_argument("--metadata-output", type=Path, default=Path("models/behavior_iforest.json"))
    parser.add_argument("--evaluation-output", type=Path, default=Path("evaluate/behavior_metrics.json"))
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
        "trainedAt": datetime.now(UTC).isoformat(),
        "featureNames": list(bundle.feature_names),
    }
    args.metadata_output.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    args.evaluation_output.write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
