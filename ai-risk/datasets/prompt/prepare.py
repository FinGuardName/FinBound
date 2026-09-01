import json
import re
import unicodedata
from collections import Counter, defaultdict
from itertools import combinations
from pathlib import Path
from typing import Any

SOURCE_PATH = Path(__file__).with_name("native_ko_seed.jsonl")
DEFAULT_REPORT_PATH = (
    Path(__file__).resolve().parents[2] / "evaluate" / "prompt_dataset_report.json"
)
ALLOWED_SPLITS = {"development", "validation", "held_out_test"}
ALLOWED_SAMPLE_TYPES = {"normal", "hard_negative", "attack"}
ALLOWED_INPUT_LANGUAGES = {"ko", "en", "mixed"}
ALLOWED_REVIEW_STATUSES = {"DRAFT", "APPROVED", "REJECTED"}
ALLOWED_SOURCE_TYPES = {"native_authored"}
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
SAMPLE_ID_PATTERN = re.compile(r"^KO-(DEV|VAL|TEST)-(N|H|A)-\d{3}$")
MAX_TEXT_LENGTH = 4096
CROSS_SPLIT_SIMILARITY_LIMIT = 0.82
REQUIRED_FIELDS = {
    "sampleId",
    "groupId",
    "split",
    "text",
    "label",
    "sampleType",
    "sourceId",
    "sourceType",
    "attackType",
    "inputLanguage",
    "reviewStatus",
}


def read_records(path: Path = SOURCE_PATH) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def _normalize_text(text: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", text).split()).casefold()


def _character_ngrams(text: str, size: int = 3) -> set[str]:
    compact = _normalize_text(text).replace(" ", "")
    if len(compact) <= size:
        return {compact}
    return {compact[index : index + size] for index in range(len(compact) - size + 1)}


def _similarity(left_ngrams: set[str], right_ngrams: set[str]) -> float:
    return len(left_ngrams & right_ngrams) / len(left_ngrams | right_ngrams)


def validate_records(records: list[dict[str, Any]]) -> None:
    sample_ids: set[str] = set()
    normalized_texts: set[str] = set()
    group_splits: dict[str, set[str]] = defaultdict(set)
    text_fingerprints: list[tuple[dict[str, Any], set[str]]] = []

    for record in records:
        missing = REQUIRED_FIELDS - record.keys()
        if missing:
            raise ValueError(f"{record.get('sampleId', '<unknown>')} missing fields: {missing}")
        extra = record.keys() - REQUIRED_FIELDS
        if extra:
            raise ValueError(f"{record.get('sampleId', '<unknown>')} unknown fields: {extra}")
        for field in REQUIRED_FIELDS - {"label", "attackType"}:
            if not isinstance(record[field], str) or not record[field].strip():
                raise ValueError(f"{record.get('sampleId', '<unknown>')} invalid {field}")
        if not SAMPLE_ID_PATTERN.fullmatch(record["sampleId"]):
            raise ValueError(f"Invalid sampleId: {record['sampleId']}")
        if record["sampleId"] in sample_ids:
            raise ValueError(f"Duplicate sampleId: {record['sampleId']}")
        sample_ids.add(record["sampleId"])

        normalized = _normalize_text(record["text"])
        if normalized in normalized_texts:
            raise ValueError(f"Duplicate text: {record['sampleId']}")
        normalized_texts.add(normalized)
        if len(normalized) > MAX_TEXT_LENGTH:
            raise ValueError(f"Text exceeds {MAX_TEXT_LENGTH} characters: {record['sampleId']}")
        if any(unicodedata.category(character).startswith("C") for character in record["text"]):
            raise ValueError(f"Control character in text: {record['sampleId']}")

        if record["split"] not in ALLOWED_SPLITS:
            raise ValueError(f"Invalid split: {record['split']}")
        if record["sampleType"] not in ALLOWED_SAMPLE_TYPES:
            raise ValueError(f"Invalid sampleType: {record['sampleType']}")
        if type(record["label"]) is not int or record["label"] not in {0, 1}:
            raise ValueError(f"Invalid label: {record['label']}")
        if record["sourceType"] not in ALLOWED_SOURCE_TYPES:
            raise ValueError(f"Invalid sourceType: {record['sourceType']}")
        if record["inputLanguage"] not in ALLOWED_INPUT_LANGUAGES:
            raise ValueError(f"Invalid inputLanguage: {record['inputLanguage']}")
        if record["reviewStatus"] not in ALLOWED_REVIEW_STATUSES:
            raise ValueError(f"Invalid reviewStatus: {record['reviewStatus']}")
        if (record["label"] == 1) != (record["sampleType"] == "attack"):
            raise ValueError(f"Label and sampleType disagree: {record['sampleId']}")
        if record["label"] == 1 and record["attackType"] not in ALLOWED_ATTACK_TYPES:
            raise ValueError(f"Attack sample requires a valid attackType: {record['sampleId']}")
        if record["label"] == 0 and record["attackType"] is not None:
            raise ValueError(f"Benign sample cannot have attackType: {record['sampleId']}")
        if any(pattern.search(record["text"]) for pattern in SENSITIVE_PATTERNS):
            raise ValueError(f"Potential sensitive value: {record['sampleId']}")
        group_splits[record["groupId"]].add(record["split"])
        text_fingerprints.append((record, _character_ngrams(record["text"])))

    leaking_groups = {group: splits for group, splits in group_splits.items() if len(splits) > 1}
    if leaking_groups:
        raise ValueError(f"Group leakage across splits: {leaking_groups}")

    for (left, left_ngrams), (right, right_ngrams) in combinations(text_fingerprints, 2):
        if left["split"] == right["split"]:
            continue
        similarity = _similarity(left_ngrams, right_ngrams)
        if similarity >= CROSS_SPLIT_SIMILARITY_LIMIT:
            raise ValueError(
                "Near-duplicate text across splits: "
                f"{left['sampleId']} and {right['sampleId']} ({similarity:.3f})"
            )


def build_report(records: list[dict[str, Any]]) -> dict[str, Any]:
    report: dict[str, Any] = {
        "datasetVersion": "finbound-prompt-eval-ko-2",
        "totalSamples": len(records),
        "reviewStatus": dict(Counter(record["reviewStatus"] for record in records)),
        "finalEvaluationReady": bool(records)
        and all(record["reviewStatus"] == "APPROVED" for record in records),
        "splits": {},
    }
    for split in sorted(ALLOWED_SPLITS):
        selected = [record for record in records if record["split"] == split]
        report["splits"][split] = {
            "samples": len(selected),
            "groups": len({record["groupId"] for record in selected}),
            "labels": dict(Counter(str(record["label"]) for record in selected)),
            "sampleTypes": dict(Counter(record["sampleType"] for record in selected)),
            "inputLanguages": dict(Counter(record["inputLanguage"] for record in selected)),
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
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


if __name__ == "__main__":
    main()
