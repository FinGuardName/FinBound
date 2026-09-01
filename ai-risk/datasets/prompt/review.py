import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from datasets.prompt.prepare import DATASET_VERSION, dataset_digest, read_records, validate_records

REVIEW_PACKET_SCHEMA_VERSION = 2
REVIEW_INPUT_FIELDS = {
    "reviewItemId",
    "reviewGroupId",
    "proposedLabel",
    "proposedSampleType",
    "proposedAttackType",
    "naturalnessApproved",
    "groupingApproved",
    "decision",
    "notes",
}
PACKET_ITEM_FIELDS = REVIEW_INPUT_FIELDS | {"text"}


def _review_item_id(record: dict[str, Any], digest: str) -> str:
    value = f"{digest}:{record['sampleId']}".encode()
    return hashlib.sha256(value).hexdigest()[:16]


def _review_group_id(record: dict[str, Any], digest: str) -> str:
    value = f"{digest}:{record['groupId']}".encode()
    return hashlib.sha256(value).hexdigest()[:12]


def build_blind_packet(records: list[dict[str, Any]], reviewer: str) -> dict[str, Any]:
    if not reviewer.strip():
        raise ValueError("reviewer is required")
    validate_records(records)
    digest = dataset_digest(records)
    grouped_and_shuffled = sorted(
        records,
        key=lambda record: (
            hashlib.sha256(
                f"{reviewer.casefold()}:{digest}:{record['groupId']}".encode()
            ).hexdigest(),
            hashlib.sha256(
                f"{reviewer.casefold()}:{digest}:{record['sampleId']}".encode()
            ).hexdigest(),
        ),
    )
    return {
        "schemaVersion": REVIEW_PACKET_SCHEMA_VERSION,
        "datasetVersion": DATASET_VERSION,
        "datasetSha256": digest,
        "reviewer": reviewer,
        "reviewedAt": None,
        "items": [
            {
                "reviewItemId": _review_item_id(record, digest),
                "reviewGroupId": _review_group_id(record, digest),
                "text": record["text"],
                "proposedLabel": None,
                "proposedSampleType": None,
                "proposedAttackType": None,
                "naturalnessApproved": None,
                "groupingApproved": None,
                "decision": None,
                "notes": "",
            }
            for record in grouped_and_shuffled
        ],
    }


def _review_items(
    packet: dict[str, Any], records: list[dict[str, Any]]
) -> dict[str, dict[str, Any]]:
    if packet.get("schemaVersion") != REVIEW_PACKET_SCHEMA_VERSION:
        raise ValueError("Review packet schemaVersion mismatch")
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
        if set(item) != PACKET_ITEM_FIELDS:
            raise ValueError(f"Invalid review fields: {item.get('reviewItemId', '<unknown>')}")
        review_item_id = item["reviewItemId"]
        if review_item_id in items:
            raise ValueError(f"Duplicate reviewItemId: {review_item_id}")
        items[review_item_id] = item

    digest = dataset_digest(records)
    expected_ids = {_review_item_id(record, digest) for record in records}
    if set(items) != expected_ids:
        raise ValueError("Review packet sample set mismatch")
    for record in records:
        item = items[_review_item_id(record, digest)]
        if item["text"] != record["text"]:
            raise ValueError("Review packet text mismatch")
        if item["reviewGroupId"] != _review_group_id(record, digest):
            raise ValueError("Review packet group mapping mismatch")
    return items


def _decision_errors(records: list[dict[str, Any]], items: dict[str, dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    digest = dataset_digest(records)
    for record in records:
        sample_id = record["sampleId"]
        decision = items[_review_item_id(record, digest)]
        proposed = (
            decision["proposedLabel"],
            decision["proposedSampleType"],
            decision["proposedAttackType"],
        )
        expected = (record["label"], record["sampleType"], record["attackType"])
        if decision["decision"] != "APPROVED":
            errors.append(f"{sample_id}: reviewer did not approve")
        elif proposed != expected:
            errors.append(f"{sample_id}: label decision differs from authored value")
        elif decision["naturalnessApproved"] is not True:
            errors.append(f"{sample_id}: naturalness was not approved")
        elif decision["groupingApproved"] is not True:
            errors.append(f"{sample_id}: grouping was not approved")
    return errors


def _raise_review_errors(errors: list[str]) -> None:
    if not errors:
        return
    preview = "; ".join(errors[:10])
    suffix = f"; and {len(errors) - 10} more" if len(errors) > 10 else ""
    raise ValueError(f"Review finalization failed: {preview}{suffix}")


def _approved_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{**record, "reviewStatus": "APPROVED"} for record in records]


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

    errors = _decision_errors(records, first) + _decision_errors(records, second)
    digest = dataset_digest(records)
    for record in records:
        sample_id = record["sampleId"]
        review_item_id = _review_item_id(record, digest)
        decisions = (first[review_item_id], second[review_item_id])
        if decisions[0]["decision"] != decisions[1]["decision"]:
            errors.append(f"{sample_id}: reviewer decisions disagree")

    _raise_review_errors(errors)
    return _approved_records(records)


def finalize_ai_assisted_review(
    records: list[dict[str, Any]],
    review_packet: dict[str, Any],
    approver: str,
    approved_at: str,
) -> list[dict[str, Any]]:
    validate_records(records)
    if not approver.strip():
        raise ValueError("AI-assisted review approver is required")
    if not approved_at.strip():
        raise ValueError("AI-assisted review approvedAt is required")
    if review_packet.get("reviewer", "").casefold() == approver.casefold():
        raise ValueError("AI reviewer and dataset approver must be distinct")
    items = _review_items(review_packet, records)
    _raise_review_errors(_decision_errors(records, items))
    return _approved_records(records)


def _packet_digest(packet: dict[str, Any]) -> str:
    canonical = json.dumps(
        packet, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode()
    return hashlib.sha256(canonical).hexdigest()


def build_approval_manifest(
    records: list[dict[str, Any]],
    first_packet: dict[str, Any],
    second_packet: dict[str, Any],
) -> dict[str, Any]:
    approved = finalize_reviews(records, first_packet, second_packet)

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
                "packetSha256": _packet_digest(packet),
            }
            for packet in (first_packet, second_packet)
        ],
        "status": "APPROVED",
    }


def build_ai_assisted_approval_manifest(
    records: list[dict[str, Any]],
    review_packet: dict[str, Any],
    approver: str,
    approved_at: str,
) -> dict[str, Any]:
    approved = finalize_ai_assisted_review(records, review_packet, approver, approved_at)
    return {
        "schemaVersion": 2,
        "datasetVersion": DATASET_VERSION,
        "sourceDatasetSha256": dataset_digest(records),
        "approvedDatasetSha256": dataset_digest(approved),
        "approvedSamples": len(approved),
        "reviewMethod": "AI_ASSISTED_OWNER_APPROVAL",
        "independentHumanReview": False,
        "reviewer": {
            "reviewer": review_packet["reviewer"],
            "reviewerType": "AI",
            "reviewedAt": review_packet["reviewedAt"],
            "packetSha256": _packet_digest(review_packet),
        },
        "approver": {
            "approver": approver,
            "approvedAt": approved_at,
            "approvalScope": "all-reviewed-samples",
        },
        "limitation": (
            "Time-boxed P0 review approved by the dataset owner; "
            "no independent human item-level review was performed."
        ),
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

    ai_finalize = subparsers.add_parser("finalize-ai-assisted")
    ai_finalize.add_argument("--review", type=Path, required=True)
    ai_finalize.add_argument("--approver", required=True)
    ai_finalize.add_argument("--approved-at", required=True)
    ai_finalize.add_argument("--output", type=Path, required=True)
    ai_finalize.add_argument("--manifest", type=Path, required=True)

    args = parser.parse_args()
    records = read_records()
    if args.command == "create":
        _write_json(args.output, build_blind_packet(records, args.reviewer))
    elif args.command == "finalize":
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
    else:
        review_packet = _read_packet(args.review)
        approved = finalize_ai_assisted_review(
            records,
            review_packet,
            args.approver,
            args.approved_at,
        )
        _write_jsonl(args.output, approved)
        _write_json(
            args.manifest,
            build_ai_assisted_approval_manifest(
                records,
                review_packet,
                args.approver,
                args.approved_at,
            ),
        )
    print(args.output)


if __name__ == "__main__":
    main()
