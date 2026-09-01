import argparse
import json
from pathlib import Path
from typing import Any

from app.prompt.model import HybridPromptClassifier, PromptClassifier, PromptDetectorConfig
from app.prompt.rules import detect_rule_matches, normalize_prompt_text
from datasets.prompt.prepare import dataset_digest, validate_records
from evaluate.prompt_metrics import evaluate_predictions, read_jsonl

DEFAULT_DATASET = (
    Path(__file__).resolve().parents[1] / "datasets" / "prompt" / "finbound_eval_approved.jsonl"
)
TARGET_VALIDATION_FALSE_POSITIVE_RATE = 0.05


def _predictions(
    records: list[dict[str, Any]],
    split: str,
    scores: dict[str, float],
    threshold: float,
    include_rules: bool,
) -> list[dict[str, Any]]:
    return [
        {
            "sampleId": record["sampleId"],
            "detected": scores[record["sampleId"]] >= threshold
            or (include_rules and bool(detect_rule_matches(normalize_prompt_text(record["text"])))),
        }
        for record in records
        if record["split"] == split
    ]


def select_model_score_threshold(
    records: list[dict[str, Any]],
    scores: dict[str, float],
    target_false_positive_rate: float = TARGET_VALIDATION_FALSE_POSITIVE_RATE,
) -> tuple[float, dict[str, Any]]:
    candidates = sorted(
        {scores[record["sampleId"]] for record in records if record["split"] == "validation"}
    )
    eligible: list[tuple[tuple[float, float, float, float], float, dict[str, Any]]] = []
    for threshold in candidates:
        selected = [record for record in records if record["split"] == "validation"]
        predictions = [scores[record["sampleId"]] >= threshold for record in selected]
        true_positives = sum(
            record["label"] == 1 and detected
            for record, detected in zip(selected, predictions, strict=True)
        )
        false_positives = sum(
            record["label"] == 0 and detected
            for record, detected in zip(selected, predictions, strict=True)
        )
        true_negatives = sum(
            record["label"] == 0 and not detected
            for record, detected in zip(selected, predictions, strict=True)
        )
        false_negatives = sum(
            record["label"] == 1 and not detected
            for record, detected in zip(selected, predictions, strict=True)
        )
        precision = (
            true_positives / (true_positives + false_positives)
            if true_positives + false_positives
            else 0.0
        )
        recall = true_positives / (true_positives + false_negatives)
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        false_positive_rate = false_positives / (false_positives + true_negatives)
        metrics = {
            "samples": len(selected),
            "truePositives": true_positives,
            "falsePositives": false_positives,
            "trueNegatives": true_negatives,
            "falseNegatives": false_negatives,
            "precision": round(precision, 6),
            "recall": round(recall, 6),
            "f1": round(f1, 6),
            "falsePositiveRate": round(false_positive_rate, 6),
        }
        report = {"split": "validation", "overall": metrics}
        if metrics["falsePositiveRate"] <= target_false_positive_rate:
            rank = (
                metrics["f1"],
                metrics["recall"],
                -metrics["falsePositiveRate"],
                -threshold,
            )
            eligible.append((rank, threshold, report))
    if not eligible:
        raise ValueError("No validation threshold satisfies the false-positive constraint")
    _, threshold, report = max(eligible, key=lambda item: item[0])
    return threshold, report


def _score_records(records: list[dict[str, Any]], classifier: PromptClassifier) -> dict[str, float]:
    return {
        record["sampleId"]: classifier.predict_attack_score(normalize_prompt_text(record["text"]))
        for record in records
    }


def build_report(
    records: list[dict[str, Any]],
    mode: str,
    config: PromptDetectorConfig,
    classifier: PromptClassifier,
) -> dict[str, Any]:
    validate_records(records)
    if dataset_digest(records) != config.approved_dataset_sha256:
        raise ValueError("Approved dataset digest does not match prompt detector configuration")
    common: dict[str, Any] = {
        "datasetVersion": config.dataset_version,
        "approvedDatasetSha256": config.approved_dataset_sha256,
        "modelVersion": config.model_version,
        "modelId": config.model_id,
        "modelRevision": config.model_revision,
        "modelArtifactSha256": config.model_artifact_sha256,
        "architecture": "korean-english-rules + pinned-pretrained-model + domain-adapter",
        "pretrainedScoreThreshold": config.pretrained_score_threshold,
        "domainAdapterArtifactSha256": config.domain_adapter_artifact_sha256,
        "domainAdapterScoreThreshold": config.domain_adapter_score_threshold,
        "hybridEvidenceThreshold": config.hybrid_evidence_threshold,
        "promptBlockThreshold": config.prompt_block_threshold,
        "ruleRisk": config.rule_risk,
        "targetValidationFalsePositiveRate": TARGET_VALIDATION_FALSE_POSITIVE_RATE,
        "rawPromptIncluded": False,
        "authorizationDecisionProduced": False,
        "heldOutUsedForSelection": False,
    }
    if mode == "select":
        validation_records = [record for record in records if record["split"] == "validation"]
        scores = _score_records(validation_records, classifier)
        selected_threshold, selection_metrics = select_model_score_threshold(records, scores)
        return {
            **common,
            "selectionSplit": "validation",
            "selectedModelScoreThreshold": selected_threshold,
            "modelOnlyValidation": selection_metrics,
            "selectionPolicy": (
                "maximize model-only F1, then recall, under validation FPR <= 0.05; "
                "break ties with lower FPR and lower threshold"
            ),
        }

    split = "validation" if mode == "validation" else "held_out_test"
    selected_records = [record for record in records if record["split"] == split]
    scores = _score_records(selected_records, classifier)
    if mode == "validation":
        selected_threshold, _ = select_model_score_threshold(records, scores)
        if abs(selected_threshold - config.hybrid_evidence_threshold) > 1e-12:
            raise ValueError("Configured model threshold does not match validation selection")
    return {
        **common,
        "split": split,
        "ruleOnly": evaluate_predictions(
            records,
            _predictions(records, split, scores, threshold=float("inf"), include_rules=True),
            split=split,
        ),
        "modelOnly": evaluate_predictions(
            records,
            _predictions(
                records,
                split,
                scores,
                threshold=config.hybrid_evidence_threshold,
                include_rules=False,
            ),
            split=split,
        ),
        "combined": evaluate_predictions(
            records,
            _predictions(
                records,
                split,
                scores,
                threshold=config.hybrid_evidence_threshold,
                include_rules=True,
            ),
            split=split,
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument(
        "--mode",
        choices=("select", "validation", "held-out"),
        required=True,
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    records = read_jsonl(args.dataset)
    config = PromptDetectorConfig.load()
    report = build_report(records, args.mode, config, HybridPromptClassifier(config))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(args.output)


if __name__ == "__main__":
    main()
