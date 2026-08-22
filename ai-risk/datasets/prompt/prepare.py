import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

SOURCE_PATH = Path(__file__).with_name("native_ko_seed.jsonl")
DEFAULT_REPORT_PATH = Path(__file__).resolve().parents[2] / "evaluate" / "prompt_dataset_report.json"
ALLOWED_SPLITS = {"development", "validation", "held_out_test"}
ALLOWED_SAMPLE_TYPES = {"normal", "hard_negative", "attack"}
ALLOWED_ATTACK_TYPES = {
    "IGNORE_PREVIOUS_INSTRUCTION",
    "POLICY_BYPASS",
    "SYSTEM_PROMPT_EXTRACTION",
    "CROSS_CUSTOMER_ACCESS",
    "UNAUTHORIZED_TOOL_REQUEST",
    "UNKNOWN_PROMPT_ATTACK",
}
SENSITIVE_PATTERNS = (
    re.compile(r"\b\d{6}-[1-4]\d{6}\b"),
    re.compile(r"\b01[016789]-?\d{3,4}-?\d{4}\b"),
    re.compile(r"\b\d{10,14}\b"),
)


def read_records(path: Path = SOURCE_PATH) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def validate_records(records: list[dict[str, Any]]) -> None:
    required = {
        "sampleId", "groupId", "split", "text", "label", "sampleType", "sourceId",
        "sourceType", "attackType", "inputLanguage", "reviewStatus",
    }
    sample_ids: set[str] = set()
    normalized_texts: set[str] = set()
    group_splits: dict[str, set[str]] = defaultdict(set)

    for record in records:
        missing = required - record.keys()
        if missing:
            raise ValueError(f"{record.get('sampleId', '<unknown>')} missing fields: {missing}")
        if record["sampleId"] in sample_ids:
            raise ValueError(f"Duplicate sampleId: {record['sampleId']}")
        sample_ids.add(record["sampleId"])

        normalized = " ".join(record["text"].split()).casefold()
        if normalized in normalized_texts:
            raise ValueError(f"Duplicate text: {record['sampleId']}")
        normalized_texts.add(normalized)

        if record["split"] not in ALLOWED_SPLITS:
            raise ValueError(f"Invalid split: {record['split']}")
        if record["sampleType"] not in ALLOWED_SAMPLE_TYPES:
            raise ValueError(f"Invalid sampleType: {record['sampleType']}")
        if record["label"] not in {0, 1}:
            raise ValueError(f"Invalid label: {record['label']}")
        if record["label"] == 1 and record["attackType"] not in ALLOWED_ATTACK_TYPES:
            raise ValueError(f"Attack sample requires a valid attackType: {record['sampleId']}")
        if record["label"] == 0 and record["attackType"] is not None:
            raise ValueError(f"Benign sample cannot have attackType: {record['sampleId']}")
        if any(pattern.search(record["text"]) for pattern in SENSITIVE_PATTERNS):
            raise ValueError(f"Potential sensitive value: {record['sampleId']}")
        group_splits[record["groupId"]].add(record["split"])

    leaking_groups = {group: splits for group, splits in group_splits.items() if len(splits) > 1}
    if leaking_groups:
        raise ValueError(f"Group leakage across splits: {leaking_groups}")


def build_report(records: list[dict[str, Any]]) -> dict[str, Any]:
    report: dict[str, Any] = {
        "datasetVersion": "finguard-prompt-eval-ko-1",
        "totalSamples": len(records),
        "reviewStatus": dict(Counter(record["reviewStatus"] for record in records)),
        "splits": {},
    }
    for split in sorted(ALLOWED_SPLITS):
        selected = [record for record in records if record["split"] == split]
        report["splits"][split] = {
            "samples": len(selected),
            "groups": len({record["groupId"] for record in selected}),
            "labels": dict(Counter(str(record["label"]) for record in selected)),
            "sampleTypes": dict(Counter(record["sampleType"] for record in selected)),
            "attackTypes": dict(
                Counter(record["attackType"] for record in selected if record["attackType"])
            ),
        }
    return report


def main(report_path: Path = DEFAULT_REPORT_PATH) -> None:
    records = read_records()
    validate_records(records)
    report = build_report(records)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
