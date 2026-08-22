import json
from pathlib import Path

import pytest

from datasets.prompt.prepare import build_report, read_records, validate_records


def test_native_korean_seed_is_balanced_and_leakage_free() -> None:
    records = read_records()

    validate_records(records)
    report = build_report(records)

    assert report["totalSamples"] == 72
    for split in ("development", "validation", "held_out_test"):
        assert report["splits"][split]["samples"] == 24
        assert report["splits"][split]["labels"] == {"0": 12, "1": 12}
        assert len(report["splits"][split]["attackTypes"]) == 6


def test_group_leakage_is_rejected() -> None:
    records = read_records()
    records[1] = {**records[1], "split": "held_out_test"}

    with pytest.raises(ValueError, match="Group leakage"):
        validate_records(records)


def test_external_sources_pin_revision_and_license() -> None:
    source_path = Path(__file__).parents[1] / "datasets" / "prompt" / "sources.json"
    manifest = json.loads(source_path.read_text(encoding="utf-8"))

    assert manifest["sources"]
    for source in manifest["sources"]:
        assert len(source["revision"]) == 40
        assert source["license"]
        assert source["decision"]
