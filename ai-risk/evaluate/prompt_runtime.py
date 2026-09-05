import argparse
import json
from pathlib import Path
from typing import Any

from app.prompt.decision import decide_prompt_risk
from app.prompt.model import HybridPromptClassifier, PromptClassifier, PromptDetectorConfig
from app.prompt.rules import detect_rule_matches, normalize_prompt_text
from datasets.prompt.prepare import dataset_digest, validate_records
from evaluate.prompt_metrics import evaluate_predictions, read_jsonl

DEFAULT_DATASET = (
    Path(__file__).resolve().parents[1] / "datasets" / "prompt" / "finbound_eval_approved.jsonl"
)
TARGET_VALIDATION_FALSE_POSITIVE_RATE = 0.05
MIN_MODEL_SUPPORT_THRESHOLD = 0.5


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


def _ai_primary_predictions(
    records: list[dict[str, Any]],
    split: str,
    scores: dict[str, float],
    config: PromptDetectorConfig,
) -> list[dict[str, Any]]:
    predictions: list[dict[str, Any]] = []
    for record in records:
        if record["split"] != split:
            continue
        matches = detect_rule_matches(normalize_prompt_text(record["text"]))
        decision = decide_prompt_risk(scores[record["sampleId"]], matches, config)
        predictions.append(
            {
                "sampleId": record["sampleId"],
                "detected": decision.detected,
                "riskLevel": decision.risk_level,
            }
        )
    return predictions


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


def select_model_support_threshold(
    records: list[dict[str, Any]],
    scores: dict[str, float],
    high_threshold: float,
    target_false_positive_rate: float = TARGET_VALIDATION_FALSE_POSITIVE_RATE,
) -> tuple[float, dict[str, Any]]:
    selected = [record for record in records if record["split"] == "validation"]
    candidates = sorted(
        {
            scores[record["sampleId"]]
            for record in selected
            if MIN_MODEL_SUPPORT_THRESHOLD <= scores[record["sampleId"]] < high_threshold
        }
    )
    eligible: list[tuple[tuple[float, float, float, float], float, dict[str, Any]]] = []
    for threshold in candidates:
        predictions = [
            scores[record["sampleId"]] >= high_threshold
            or (
                scores[record["sampleId"]] >= threshold
                and bool(detect_rule_matches(normalize_prompt_text(record["text"])))
            )
            for record in selected
        ]
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
        report = {
            "split": "validation",
            "overall": {
                "samples": len(selected),
                "truePositives": true_positives,
                "falsePositives": false_positives,
                "trueNegatives": true_negatives,
                "falseNegatives": false_negatives,
                "precision": round(precision, 6),
                "recall": round(recall, 6),
                "f1": round(f1, 6),
                "falsePositiveRate": round(false_positive_rate, 6),
            },
        }
        metrics = report["overall"]
        if metrics["falsePositiveRate"] <= target_false_positive_rate:
            rank = (
                metrics["f1"],
                metrics["recall"],
                -metrics["falsePositiveRate"],
                threshold,
            )
            eligible.append((rank, threshold, report))
    if not eligible:
        raise ValueError("No model support threshold satisfies the validation constraints")
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
        "modelSupportThreshold": config.model_support_threshold,
        "modelHighThreshold": config.model_high_threshold,
        "promptAlertThreshold": config.prompt_alert_threshold,
        "promptBlockThreshold": config.prompt_block_threshold,
        "ruleAlertRisk": config.rule_alert_risk,
        "targetValidationFalsePositiveRate": TARGET_VALIDATION_FALSE_POSITIVE_RATE,
        "rawPromptIncluded": False,
        "authorizationDecisionProduced": False,
        "heldOutUsedForSelection": False,
        "heldOutRepeatedForDiagnostic": True,
    }
    if mode == "select":
        validation_records = [record for record in records if record["split"] == "validation"]
        scores = _score_records(validation_records, classifier)
        high_threshold, model_only_metrics = select_model_score_threshold(records, scores)
        support_threshold, gated_metrics = select_model_support_threshold(
            records,
            scores,
            high_threshold,
        )
        return {
            **common,
            "selectionSplit": "validation",
            "selectedModelHighThreshold": high_threshold,
            "selectedModelSupportThreshold": support_threshold,
            "modelOnlyValidation": model_only_metrics,
            "aiPrimaryGatedValidation": gated_metrics,
            "selectionPolicy": (
                "select the model-only high threshold under validation FPR <= 0.05; then "
                "select support evidence >= 0.5 for model+rule corroboration by F1 and recall, "
                "breaking ties toward the higher support threshold; rules alone remain ALERT"
            ),
        }

    split = "validation" if mode == "validation" else "held_out_test"
    selected_records = [record for record in records if record["split"] == split]
    scores = _score_records(selected_records, classifier)
    if mode == "validation":
        high_threshold, _ = select_model_score_threshold(records, scores)
        support_threshold, _ = select_model_support_threshold(records, scores, high_threshold)
        if abs(high_threshold - config.model_high_threshold) > 1e-12:
            raise ValueError("Configured model high threshold does not match validation selection")
        if abs(support_threshold - config.model_support_threshold) > 1e-12:
            raise ValueError(
                "Configured model support threshold does not match validation selection"
            )
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
                threshold=config.model_high_threshold,
                include_rules=False,
            ),
            split=split,
        ),
        "legacyRuleOrModel": evaluate_predictions(
            records,
            _predictions(
                records,
                split,
                scores,
                threshold=config.model_high_threshold,
                include_rules=True,
            ),
            split=split,
        ),
        "aiPrimaryGated": evaluate_predictions(
            records,
            _ai_primary_predictions(records, split, scores, config),
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
