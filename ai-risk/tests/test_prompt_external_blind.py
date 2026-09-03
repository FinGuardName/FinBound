import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.schemas.prompt import PromptRiskLevel
from evaluate.prompt_external_blind import (
    build_report,
    read_external_records,
    verify_pinned_external_dataset,
)

AI_RISK_ROOT = Path(__file__).parents[1]


class FakeEvaluator:
    def evaluate(self, request):
        level = {
            "safe": PromptRiskLevel.LOW,
            "suspicious": PromptRiskLevel.ALERT,
            "attack": PromptRiskLevel.CRITICAL,
        }[request.input_text]
        return SimpleNamespace(risk_level=level)


def test_external_report_separates_critical_block_from_alert_signal() -> None:
    records = [
        {"text": "safe", "label": 0},
        {"text": "suspicious", "label": 1},
        {"text": "attack", "label": 1},
    ]

    report = build_report(records, FakeEvaluator(), "a" * 64)

    assert report["criticalBlock"]["recall"] == 0.5
    assert report["alertOrCriticalSignal"]["recall"] == 1.0
    assert report["authorizationDecisionProduced"] is False
    assert report["rawPromptIncluded"] is False
    assert report["criticalFalseNegativeSampleIds"] == ["DEEPSET-TEST-002"]


def test_external_reader_rejects_non_binary_labels(tmp_path: Path) -> None:
    dataset = tmp_path / "external.jsonl"
    dataset.write_text(json.dumps({"text": "example", "label": 2}) + "\n", encoding="utf-8")

    with pytest.raises(ValueError, match="binary label"):
        read_external_records(dataset)


def test_external_blind_set_rejects_an_unpinned_row_count() -> None:
    with pytest.raises(ValueError, match="row count mismatch"):
        verify_pinned_external_dataset(
            [{"text": "safe", "label": 0}],
            "de0996d15cabd838d50a4925a8493062ba70c29f845601cd6a17412236614486",
        )


def test_external_blind_set_rejects_modified_bytes() -> None:
    records = [{"text": f"sample-{index}", "label": index % 2} for index in range(116)]

    with pytest.raises(ValueError, match="SHA-256 mismatch"):
        verify_pinned_external_dataset(records, "0" * 64)


def test_committed_external_report_keeps_provenance_and_privacy_contract() -> None:
    report = json.loads(
        (AI_RISK_ROOT / "evaluate" / "prompt_external_blind_deepset.json").read_text(
            encoding="utf-8"
        )
    )
    sources = json.loads(
        (AI_RISK_ROOT / "datasets" / "prompt" / "sources.json").read_text(encoding="utf-8")
    )
    source = next(item for item in sources["sources"] if item["sourceId"] == report["sourceId"])

    assert report["sourceRevision"] == source["revision"]
    assert report["sourceArtifactSha256"] == source["artifacts"]["test"][0]["sha256"]
    assert report["usedForThresholdSelection"] is False
    assert report["rawPromptIncluded"] is False
    assert report["authorizationDecisionProduced"] is False
    assert sum(report["riskLevelCounts"].values()) == report["samples"] == 116
