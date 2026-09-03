import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any, Protocol

from app.prompt.model import PromptDetectorConfig
from app.prompt.service import PromptRiskService, input_hash
from app.schemas.prompt import PromptRiskLevel, PromptRiskRequest, PromptRiskResponse
from datasets.prompt.fetch_public import load_source
from evaluate.prompt_metrics import _binary_metrics

SOURCE_ID = "hf-deepset-prompt-injections"
SOURCE_SPLIT = "test"
DEFAULT_DATASET = (
    Path(__file__).resolve().parents[1]
    / "datasets"
    / "cache"
    / "prompt"
    / SOURCE_ID
    / f"{SOURCE_SPLIT}.jsonl"
)


class PromptEvaluator(Protocol):
    def evaluate(self, request: PromptRiskRequest) -> PromptRiskResponse: ...


def read_external_records(path: Path) -> list[dict[str, Any]]:
    records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]
    if not records:
        raise ValueError("External blind set must not be empty")
    for index, record in enumerate(records, start=1):
        if set(record) != {"text", "label"}:
            raise ValueError(f"External blind row {index} has unexpected fields")
        if not isinstance(record["text"], str) or not record["text"].strip():
            raise ValueError(f"External blind row {index} requires non-empty text")
        if type(record["label"]) is not int or record["label"] not in {0, 1}:
            raise ValueError(f"External blind row {index} requires a binary label")
    return records


def verify_pinned_external_dataset(
    records: list[dict[str, Any]], evaluation_set_sha256: str
) -> None:
    source = load_source(SOURCE_ID)
    expected = source["normalizedArtifacts"][SOURCE_SPLIT]
    if len(records) != expected["records"]:
        raise ValueError(
            "External blind set row count mismatch: "
            f"expected {expected['records']}, received {len(records)}"
        )
    if evaluation_set_sha256 != expected["sha256"]:
        raise ValueError(
            "External blind set SHA-256 mismatch: "
            f"expected {expected['sha256']}, received {evaluation_set_sha256}"
        )


def build_report(
    records: list[dict[str, Any]],
    evaluator: PromptEvaluator,
    evaluation_set_sha256: str,
) -> dict[str, Any]:
    levels: list[PromptRiskLevel] = []
    for index, record in enumerate(records, start=1):
        text = record["text"]
        response = evaluator.evaluate(
            PromptRiskRequest(
                agentRunId="EXTERNAL-BLIND-RUN",
                inputRef=f"DEEPSET-TEST-{index:03d}",
                inputText=text,
                inputHash=input_hash(text),
                contentLanguage="en",
            )
        )
        levels.append(response.risk_level)

    labels = [record["label"] for record in records]
    critical = [level is PromptRiskLevel.CRITICAL for level in levels]
    flagged = [level is not PromptRiskLevel.LOW for level in levels]
    source = load_source(SOURCE_ID)
    artifact = source["artifacts"][SOURCE_SPLIT][0]
    return {
        "evaluationName": "deepset-prompt-injections-pinned-test-post-freeze",
        "sourceId": SOURCE_ID,
        "sourceRevision": source["revision"],
        "sourceParquetRevision": source["parquetRevision"],
        "sourceArtifactSha256": artifact["sha256"],
        "evaluationSetSha256": evaluation_set_sha256,
        "license": source["license"],
        "split": SOURCE_SPLIT,
        "samples": len(records),
        "modelVersion": PromptDetectorConfig.load().model_version,
        "usedForTraining": False,
        "usedForThresholdSelection": False,
        "evaluatedAfterModelAndThresholdFreeze": True,
        "rawPromptIncluded": False,
        "authorizationDecisionProduced": False,
        "scope": "external English baseline; not FinBound Korean-domain performance",
        "limitations": [
            "Public labels use a broader generic prompt-injection definition than FinBound policy.",
            "Possible overlap with unknown pretrained-model training data cannot be excluded.",
            "This external set does not replace Korean financial-domain evaluation.",
        ],
        "riskLevelCounts": dict(sorted(Counter(level.value for level in levels).items())),
        "criticalBlock": _binary_metrics(labels, critical),
        "alertOrCriticalSignal": _binary_metrics(labels, flagged),
        "criticalFalseNegativeSampleIds": [
            f"DEEPSET-TEST-{index:03d}"
            for index, (label, prediction) in enumerate(zip(labels, critical, strict=True), start=1)
            if label == 1 and not prediction
        ],
        "criticalFalsePositiveSampleIds": [
            f"DEEPSET-TEST-{index:03d}"
            for index, (label, prediction) in enumerate(zip(labels, critical, strict=True), start=1)
            if label == 0 and prediction
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).with_name("prompt_external_blind_deepset.json"),
    )
    args = parser.parse_args()
    if not args.dataset.is_file():
        raise FileNotFoundError(
            "Fetch the pinned external split with "
            "`python -m datasets.prompt.fetch_public hf-deepset-prompt-injections test`"
        )
    records = read_external_records(args.dataset)
    digest = hashlib.sha256(args.dataset.read_bytes()).hexdigest()
    verify_pinned_external_dataset(records, digest)
    report = build_report(records, PromptRiskService(), digest)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(args.output)


if __name__ == "__main__":
    main()
