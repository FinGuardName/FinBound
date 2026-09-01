import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

from datasets.prompt.prepare import DATASET_VERSION, dataset_digest, validate_records


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def _binary_metrics(labels: list[int], predictions: list[bool]) -> dict[str, int | float]:
    counts = Counter(
        ("tp" if label == 1 and prediction else "fn")
        if label == 1
        else ("fp" if prediction else "tn")
        for label, prediction in zip(labels, predictions, strict=True)
    )
    tp, fp, tn, fn = (counts[name] for name in ("tp", "fp", "tn", "fn"))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    false_positive_rate = fp / (fp + tn) if fp + tn else 0.0
    return {
        "samples": len(labels),
        "truePositives": tp,
        "falsePositives": fp,
        "trueNegatives": tn,
        "falseNegatives": fn,
        "precision": round(precision, 6),
        "recall": round(recall, 6),
        "f1": round(f1, 6),
        "falsePositiveRate": round(false_positive_rate, 6),
    }


def evaluate_predictions(
    records: list[dict[str, Any]],
    predictions: list[dict[str, Any]],
    split: str = "held_out_test",
) -> dict[str, Any]:
    validate_records(records)
    if not records or any(record["reviewStatus"] != "APPROVED" for record in records):
        raise ValueError("Final evaluation requires only APPROVED records")
    selected_records = [record for record in records if record["split"] == split]
    if not selected_records:
        raise ValueError(f"No approved records for split: {split}")

    prediction_by_id: dict[str, dict[str, Any]] = {}
    for prediction in predictions:
        sample_id = prediction.get("sampleId")
        if not isinstance(sample_id, str) or not sample_id:
            raise ValueError("Prediction sampleId is required")
        if sample_id in prediction_by_id:
            raise ValueError(f"Duplicate prediction sampleId: {sample_id}")
        if type(prediction.get("detected")) is not bool:
            raise ValueError(f"Prediction detected must be boolean: {sample_id}")
        prediction_by_id[sample_id] = prediction

    expected_ids = {record["sampleId"] for record in selected_records}
    if set(prediction_by_id) != expected_ids:
        raise ValueError("Prediction sample set does not match the approved evaluation set")

    detected = [prediction_by_id[record["sampleId"]]["detected"] for record in selected_records]
    labels = [record["label"] for record in selected_records]
    report: dict[str, Any] = {
        "datasetVersion": DATASET_VERSION,
        "sourceDatasetSha256": dataset_digest(records),
        "evaluationSetSha256": dataset_digest(selected_records),
        "split": split,
        "overall": _binary_metrics(labels, detected),
        "byLanguage": {},
        "byAttackType": {},
        "falseNegativeSampleIds": [],
        "falsePositiveSampleIds": [],
    }

    for language in sorted({record["inputLanguage"] for record in selected_records}):
        selected = [
            index
            for index, record in enumerate(selected_records)
            if record["inputLanguage"] == language
        ]
        report["byLanguage"][language] = _binary_metrics(
            [labels[index] for index in selected],
            [detected[index] for index in selected],
        )

    for attack_type in sorted(
        {record["attackType"] for record in selected_records if record["attackType"]}
    ):
        selected = [
            index
            for index, record in enumerate(selected_records)
            if record["attackType"] == attack_type
        ]
        detected_count = sum(detected[index] for index in selected)
        report["byAttackType"][attack_type] = {
            "samples": len(selected),
            "detected": detected_count,
            "recall": round(detected_count / len(selected), 6),
        }

    report["falseNegativeSampleIds"] = [
        record["sampleId"]
        for record, prediction in zip(selected_records, detected, strict=True)
        if record["label"] == 1 and not prediction
    ]
    report["falsePositiveSampleIds"] = [
        record["sampleId"]
        for record, prediction in zip(selected_records, detected, strict=True)
        if record["label"] == 0 and prediction
    ]
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gold", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--split",
        choices=("development", "validation", "held_out_test"),
        default="held_out_test",
    )
    args = parser.parse_args()

    report = evaluate_predictions(
        read_jsonl(args.gold), read_jsonl(args.predictions), split=args.split
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(args.output)


if __name__ == "__main__":
    main()
