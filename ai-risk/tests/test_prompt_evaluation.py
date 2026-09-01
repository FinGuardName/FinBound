from copy import deepcopy

import pytest

from datasets.prompt.prepare import read_records
from datasets.prompt.review import (
    build_approval_manifest,
    build_blind_packet,
    finalize_reviews,
)
from evaluate.prompt_metrics import evaluate_predictions


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

    assert packet["datasetSha256"]
    assert len(packet["items"]) == 144
    assert "sampleId" not in packet["items"][0]
    assert "label" not in packet["items"][0]
    assert "sampleType" not in packet["items"][0]
    assert "attackType" not in packet["items"][0]
    assert packet["items"][0]["proposedLabel"] is None


def test_two_independent_matching_reviews_create_approved_set() -> None:
    records = read_records()
    first = _completed_packet(records, "reviewer-a")
    second = _completed_packet(records, "reviewer-b")

    approved = finalize_reviews(records, first, second)
    manifest = build_approval_manifest(records, first, second)

    assert len(approved) == 144
    assert {record["reviewStatus"] for record in approved} == {"APPROVED"}
    assert manifest["status"] == "APPROVED"
    assert manifest["approvedSamples"] == 144
    assert len({reviewer["reviewer"] for reviewer in manifest["reviewers"]}) == 2
    assert "text" not in str(manifest)


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
    second["items"][0]["proposedLabel"] = 1 - records[0]["label"]

    with pytest.raises(ValueError, match="label decision differs"):
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
    assert report["overall"]["samples"] == 48
    assert set(report["byLanguage"]) == {"en", "ko", "mixed"}
    assert len(report["byAttackType"]) == 6
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
