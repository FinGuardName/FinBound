import json
from copy import deepcopy
from pathlib import Path

import pytest

from datasets.prompt.prepare import build_report, dataset_digest, read_records, validate_records
from datasets.prompt.review import (
    build_ai_assisted_approval_manifest,
    build_approval_manifest,
    build_blind_packet,
    finalize_ai_assisted_review,
    finalize_reviews,
)
from evaluate.prompt_metrics import evaluate_predictions

AI_RISK_ROOT = Path(__file__).parents[1]


def _completed_packet(records: list[dict], reviewer: str) -> dict:
    packet = build_blind_packet(records, reviewer)
    packet["reviewedAt"] = "2026-09-01T12:00:00+09:00"
    record_by_text = {record["text"]: record for record in records}
    for item in packet["items"]:
        record = record_by_text[item["text"]]
        item.update(
            {
                "proposedLabel": record["label"],
                "proposedSampleType": record["sampleType"],
                "proposedAttackType": record["attackType"],
                "naturalnessApproved": True,
                "groupingApproved": True,
                "decision": "APPROVED",
            }
        )
    return packet


def test_blind_review_packet_does_not_expose_authored_labels() -> None:
    records = read_records()

    packet = build_blind_packet(records, "reviewer-a")

    assert packet["schemaVersion"] == 2
    assert packet["datasetSha256"]
    assert len(packet["items"]) == 216
    assert "sampleId" not in packet["items"][0]
    assert "label" not in packet["items"][0]
    assert "sampleType" not in packet["items"][0]
    assert "attackType" not in packet["items"][0]
    assert "groupId" not in packet["items"][0]
    assert "split" not in packet["items"][0]
    assert packet["items"][0]["proposedLabel"] is None
    assert packet["items"][0]["reviewGroupId"]


def test_blind_review_packet_places_same_opaque_group_together() -> None:
    records = read_records()

    packet = build_blind_packet(records, "reviewer-a")
    positions_by_group: dict[str, list[int]] = {}
    for position, item in enumerate(packet["items"]):
        positions_by_group.setdefault(item["reviewGroupId"], []).append(position)

    assert all(
        positions == list(range(positions[0], positions[-1] + 1))
        for positions in positions_by_group.values()
    )


def test_two_independent_matching_reviews_create_approved_set() -> None:
    records = read_records()
    first = _completed_packet(records, "reviewer-a")
    second = _completed_packet(records, "reviewer-b")

    approved = finalize_reviews(records, first, second)
    manifest = build_approval_manifest(records, first, second)

    assert len(approved) == 216
    assert {record["reviewStatus"] for record in approved} == {"APPROVED"}
    assert manifest["status"] == "APPROVED"
    assert manifest["approvedSamples"] == 216
    assert len({reviewer["reviewer"] for reviewer in manifest["reviewers"]}) == 2
    assert "text" not in str(manifest)


def test_ai_assisted_review_records_owner_approval_and_limitation() -> None:
    records = read_records()
    packet = _completed_packet(records, "codex-ai-review")

    approved = finalize_ai_assisted_review(
        records,
        packet,
        approver="YEOUL0520",
        approved_at="2026-09-01T12:00:00+09:00",
    )
    manifest = build_ai_assisted_approval_manifest(
        records,
        packet,
        approver="YEOUL0520",
        approved_at="2026-09-01T12:00:00+09:00",
    )

    assert len(approved) == 216
    assert {record["reviewStatus"] for record in approved} == {"APPROVED"}
    assert manifest["reviewMethod"] == "AI_ASSISTED_OWNER_APPROVAL"
    assert manifest["independentHumanReview"] is False
    assert manifest["reviewer"]["reviewerType"] == "AI"
    assert manifest["approver"]["approver"] == "YEOUL0520"
    assert manifest["limitation"]
    assert "text" not in str(manifest)


def test_ai_assisted_review_requires_distinct_owner() -> None:
    records = read_records()
    packet = _completed_packet(records, "YEOUL0520")

    with pytest.raises(ValueError, match="must be distinct"):
        finalize_ai_assisted_review(
            records,
            packet,
            approver="YEOUL0520",
            approved_at="2026-09-01T12:00:00+09:00",
        )


def test_committed_approved_dataset_matches_manifest_and_report() -> None:
    seed = read_records()
    approved = read_records(AI_RISK_ROOT / "datasets" / "prompt" / "finbound_eval_approved.jsonl")
    manifest = json.loads(
        (AI_RISK_ROOT / "datasets" / "prompt" / "approval_manifest.json").read_text(
            encoding="utf-8"
        )
    )
    committed_report = json.loads(
        (AI_RISK_ROOT / "evaluate" / "prompt_dataset_report.json").read_text(encoding="utf-8")
    )

    validate_records(approved)
    generated_report = build_report(approved)

    assert len(approved) == 216
    assert {record["reviewStatus"] for record in approved} == {"APPROVED"}
    assert manifest["sourceDatasetSha256"] == dataset_digest(seed)
    assert manifest["approvedDatasetSha256"] == dataset_digest(approved)
    assert manifest["reviewMethod"] == "AI_ASSISTED_OWNER_APPROVAL"
    assert manifest["independentHumanReview"] is False
    assert manifest["approver"]["approver"] == "YEOUL0520"
    assert generated_report["finalEvaluationReady"] is True
    assert committed_report == generated_report


def test_review_finalization_rejects_same_reviewer() -> None:
    records = read_records()
    first = _completed_packet(records, "reviewer-a")
    second = _completed_packet(records, "REVIEWER-A")

    with pytest.raises(ValueError, match="distinct reviewers"):
        finalize_reviews(records, first, second)


def test_review_finalization_rejects_label_disagreement() -> None:
    records = read_records()
    first = _completed_packet(records, "reviewer-a")
    second = _completed_packet(records, "reviewer-b")
    reviewed_item = next(item for item in second["items"] if item["text"] == records[0]["text"])
    reviewed_item["proposedLabel"] = 1 - records[0]["label"]

    with pytest.raises(ValueError, match="label decision differs"):
        finalize_reviews(records, first, second)


def test_review_finalization_rejects_packet_text_tampering() -> None:
    records = read_records()
    first = _completed_packet(records, "reviewer-a")
    second = _completed_packet(records, "reviewer-b")
    second["items"][0]["text"] = "tampered review text"

    with pytest.raises(ValueError, match="text mismatch"):
        finalize_reviews(records, first, second)


def test_prompt_metrics_cover_required_dimensions_without_raw_text() -> None:
    records = [{**record, "reviewStatus": "APPROVED"} for record in read_records()]
    held_out = [record for record in records if record["split"] == "held_out_test"]
    predictions = [
        {"sampleId": record["sampleId"], "detected": bool(record["label"])} for record in held_out
    ]
    positive = next(record for record in held_out if record["label"] == 1)
    negative = next(record for record in held_out if record["label"] == 0)
    prediction_by_id = {prediction["sampleId"]: prediction for prediction in predictions}
    prediction_by_id[positive["sampleId"]]["detected"] = False
    prediction_by_id[negative["sampleId"]]["detected"] = True

    report = evaluate_predictions(records, predictions)

    assert report["overall"]["falseNegatives"] == 1
    assert report["overall"]["falsePositives"] == 1
    assert report["split"] == "held_out_test"
    assert report["overall"]["samples"] == 120
    assert set(report["byLanguage"]) == {"en", "ko", "mixed"}
    assert len(report["byAttackType"]) == 6
    assert report["koreanFinanceBenign"]["overall"]["samples"] == 40
    assert report["koreanFinanceBenign"]["normal"]["samples"] == 20
    assert report["koreanFinanceBenign"]["hardNegative"]["samples"] == 20
    assert report["overall"]["confidenceIntervals95"]["recall"]
    assert all(
        metrics["samples"] == 10 and metrics["recallConfidenceInterval95"]
        for metrics in report["byAttackType"].values()
    )
    assert report["falseNegativeSampleIds"] == [positive["sampleId"]]
    assert report["falsePositiveSampleIds"] == [negative["sampleId"]]
    assert "text" not in str(report)


def test_prompt_metrics_reject_draft_data() -> None:
    records = read_records()
    held_out = [record for record in records if record["split"] == "held_out_test"]
    predictions = [
        {"sampleId": record["sampleId"], "detected": bool(record["label"])} for record in held_out
    ]

    with pytest.raises(ValueError, match="only APPROVED"):
        evaluate_predictions(deepcopy(records), predictions)
