import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from datasets.prompt.prepare import DATASET_VERSION, dataset_digest, read_records, validate_records

REQUIRED_REVIEW_FIELDS = {
    "reviewItemId",
    "proposedLabel",
    "proposedSampleType",
    "proposedAttackType",
    "naturalnessApproved",
    "groupingApproved",
    "decision",
    "notes",
}


def _review_item_id(record: dict[str, Any], digest: str) -> str:
    value = f"{digest}:{record['sampleId']}".encode()
    return hashlib.sha256(value).hexdigest()[:16]


def build_blind_packet(records: list[dict[str, Any]], reviewer: str) -> dict[str, Any]:
    if not reviewer.strip():
        raise ValueError("reviewer is required")
    validate_records(records)
    digest = dataset_digest(records)
    shuffled = sorted(
        records,
        key=lambda record: hashlib.sha256(
            f"{reviewer.casefold()}:{digest}:{record['sampleId']}".encode()
        ).hexdigest(),
    )
    return {
        "schemaVersion": 1,
        "datasetVersion": DATASET_VERSION,
        "datasetSha256": digest,
        "reviewer": reviewer,
        "reviewedAt": None,
        "items": [
            {
                "reviewItemId": _review_item_id(record, digest),
                "text": record["text"],
                "proposedLabel": None,
                "proposedSampleType": None,
                "proposedAttackType": None,
                "naturalnessApproved": None,
                "groupingApproved": None,
                "decision": None,
                "notes": "",
            }
            for record in shuffled
        ],
    }


def _review_items(
    packet: dict[str, Any], records: list[dict[str, Any]]
) -> dict[str, dict[str, Any]]:
    if packet.get("datasetVersion") != DATASET_VERSION:
        raise ValueError("Review packet datasetVersion mismatch")
    if packet.get("datasetSha256") != dataset_digest(records):
        raise ValueError("Review packet datasetSha256 mismatch")
    if not isinstance(packet.get("reviewer"), str) or not packet["reviewer"].strip():
        raise ValueError("Review packet reviewer is required")
    if not isinstance(packet.get("reviewedAt"), str) or not packet["reviewedAt"].strip():
        raise ValueError("Review packet reviewedAt is required")

    items: dict[str, dict[str, Any]] = {}
    for item in packet.get("items", []):
        if set(item) - {"text"} != REQUIRED_REVIEW_FIELDS:
            raise ValueError(f"Invalid review fields: {item.get('reviewItemId', '<unknown>')}")
        review_item_id = item["reviewItemId"]
        if review_item_id in items:
            raise ValueError(f"Duplicate reviewItemId: {review_item_id}")
        items[review_item_id] = item

    digest = dataset_digest(records)
    expected_ids = {_review_item_id(record, digest) for record in records}
    if set(items) != expected_ids:
        raise ValueError("Review packet sample set mismatch")
    return items


def finalize_reviews(
    records: list[dict[str, Any]],
    first_packet: dict[str, Any],
    second_packet: dict[str, Any],
) -> list[dict[str, Any]]:
    validate_records(records)
    first = _review_items(first_packet, records)
    second = _review_items(second_packet, records)
    if first_packet["reviewer"].casefold() == second_packet["reviewer"].casefold():
        raise ValueError("Two distinct reviewers are required")

    approved: list[dict[str, Any]] = []
    errors: list[str] = []
    digest = dataset_digest(records)
    for record in records:
        sample_id = record["sampleId"]
        review_item_id = _review_item_id(record, digest)
        expected = (
            record["label"],
            record["sampleType"],
            record["attackType"],
        )
        decisions = (first[review_item_id], second[review_item_id])
        for decision in decisions:
            proposed = (
                decision["proposedLabel"],
                decision["proposedSampleType"],
                decision["proposedAttackType"],
            )
            if decision["decision"] != "APPROVED":
                errors.append(f"{sample_id}: reviewer did not approve")
            elif proposed != expected:
                errors.append(f"{sample_id}: label decision differs from authored value")
            elif decision["naturalnessApproved"] is not True:
                errors.append(f"{sample_id}: naturalness was not approved")
            elif decision["groupingApproved"] is not True:
                errors.append(f"{sample_id}: grouping was not approved")
        if decisions[0]["decision"] != decisions[1]["decision"]:
            errors.append(f"{sample_id}: reviewer decisions disagree")
        approved.append({**record, "reviewStatus": "APPROVED"})

    if errors:
        preview = "; ".join(errors[:10])
        suffix = f"; and {len(errors) - 10} more" if len(errors) > 10 else ""
        raise ValueError(f"Review finalization failed: {preview}{suffix}")
    return approved


def build_approval_manifest(
    records: list[dict[str, Any]],
    first_packet: dict[str, Any],
    second_packet: dict[str, Any],
) -> dict[str, Any]:
    approved = finalize_reviews(records, first_packet, second_packet)

    def packet_digest(packet: dict[str, Any]) -> str:
        canonical = json.dumps(
            packet, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode()
        return hashlib.sha256(canonical).hexdigest()

    return {
        "schemaVersion": 1,
        "datasetVersion": DATASET_VERSION,
        "sourceDatasetSha256": dataset_digest(records),
        "approvedDatasetSha256": dataset_digest(approved),
        "approvedSamples": len(approved),
        "reviewers": [
            {
                "reviewer": packet["reviewer"],
                "reviewedAt": packet["reviewedAt"],
                "packetSha256": packet_digest(packet),
            }
            for packet in (first_packet, second_packet)
        ],
        "status": "APPROVED",
    }


def _read_packet(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _write_jsonl(path: Path, records: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for record in records:
            output.write(json.dumps(record, ensure_ascii=False) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    create = subparsers.add_parser("create")
    create.add_argument("--reviewer", required=True)
    create.add_argument("--output", type=Path, required=True)

    finalize = subparsers.add_parser("finalize")
    finalize.add_argument("--review-a", type=Path, required=True)
    finalize.add_argument("--review-b", type=Path, required=True)
    finalize.add_argument("--output", type=Path, required=True)
    finalize.add_argument("--manifest", type=Path, required=True)

    args = parser.parse_args()
    records = read_records()
    if args.command == "create":
        _write_json(args.output, build_blind_packet(records, args.reviewer))
    else:
        first_packet = _read_packet(args.review_a)
        second_packet = _read_packet(args.review_b)
        approved = finalize_reviews(
            records,
            first_packet,
            second_packet,
        )
        _write_jsonl(args.output, approved)
        _write_json(
            args.manifest,
            build_approval_manifest(records, first_packet, second_packet),
        )
    print(args.output)


if __name__ == "__main__":
    main()
