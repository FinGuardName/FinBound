import json
from hashlib import sha256
from pathlib import Path

import pytest

from datasets.prompt import fetch_public
from datasets.prompt.prepare import build_report, read_records, validate_records


def test_korean_primary_seed_is_balanced_and_leakage_free() -> None:
    records = read_records()

    validate_records(records)
    report = build_report(records)

    assert report["datasetVersion"] == "finbound-prompt-eval-korean-primary-5"
    assert len(report["datasetSha256"]) == 64
    assert report["totalSamples"] == 216
    assert report["reviewStatus"] == {"DRAFT": 216}
    assert report["finalEvaluationReady"] is False
    assert {record["sourceId"] for record in records} == {"finbound-authored-korean-primary-v4"}
    for split in ("development", "validation"):
        split_report = report["splits"][split]
        assert split_report["samples"] == 48
        assert split_report["labels"] == {"0": 24, "1": 24}
        assert split_report["sampleTypes"] == {
            "normal": 12,
            "hard_negative": 12,
            "attack": 24,
        }
        assert split_report["inputLanguages"] == {"ko": 32, "mixed": 4, "en": 12}
        assert split_report["attackTypes"] == {
            "IGNORE_PREVIOUS_INSTRUCTION": 4,
            "POLICY_BYPASS": 4,
            "SYSTEM_PROMPT_EXTRACTION": 4,
            "CROSS_CUSTOMER_ACCESS": 4,
            "UNAUTHORIZED_TOOL_REQUEST": 4,
            "UNKNOWN_PROMPT_ATTACK": 4,
        }

    held_out = report["splits"]["held_out_test"]
    assert held_out["samples"] == 120
    assert held_out["labels"] == {"0": 60, "1": 60}
    assert held_out["sampleTypes"] == {
        "normal": 30,
        "hard_negative": 30,
        "attack": 60,
    }
    assert held_out["inputLanguages"] == {"ko": 80, "mixed": 20, "en": 20}
    assert held_out["attackTypes"] == {
        "IGNORE_PREVIOUS_INSTRUCTION": 10,
        "POLICY_BYPASS": 10,
        "SYSTEM_PROMPT_EXTRACTION": 10,
        "CROSS_CUSTOMER_ACCESS": 10,
        "UNAUTHORIZED_TOOL_REQUEST": 10,
        "UNKNOWN_PROMPT_ATTACK": 10,
    }


def test_group_leakage_is_rejected() -> None:
    records = read_records()
    held_out_index = next(
        index for index, record in enumerate(records) if record["split"] == "held_out_test"
    )
    records[held_out_index] = {
        **records[held_out_index],
        "groupId": records[1]["groupId"],
    }

    with pytest.raises(ValueError, match="Group leakage"):
        validate_records(records)


def test_sample_id_and_split_mismatch_is_rejected() -> None:
    records = read_records()
    records[0] = {**records[0], "split": "held_out_test"}

    with pytest.raises(ValueError, match="sampleId and split disagree"):
        validate_records(records)


def test_quoted_ignore_analysis_family_is_held_out_as_one_group() -> None:
    records = read_records()
    family_ids = {
        "KO-TEST-H-001",
        "KO-TEST-H-002",
        "KO-TEST-H-010",
        "KO-TEST-H-012",
        "KO-TEST-H-013",
        "KO-TEST-H-014",
        "KO-TEST-H-015",
        "KO-TEST-H-022",
        "KO-TEST-H-023",
        "EN-TEST-H-001",
        "EN-TEST-H-003",
    }
    family = [record for record in records if record["sampleId"] in family_ids]

    assert len(family) == len(family_ids)
    assert {record["groupId"] for record in family} == {"quoted-ignore-analysis-heldout"}
    assert {record["split"] for record in family} == {"held_out_test"}


def test_near_duplicate_across_splits_is_rejected() -> None:
    records = read_records()
    records[24] = {**records[24], "text": records[0]["text"] + "!"}

    with pytest.raises(ValueError, match="Near-duplicate text across splits"):
        validate_records(records)


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("label", True, "Invalid label"),
        ("reviewStatus", "REVIEWED", "Invalid reviewStatus"),
        ("inputLanguage", "kr", "Invalid inputLanguage"),
        ("sourceType", "translated", "Invalid sourceType"),
    ],
)
def test_invalid_schema_values_are_rejected(field: str, value: object, message: str) -> None:
    records = read_records()
    records[0] = {**records[0], field: value}

    with pytest.raises(ValueError, match=message):
        validate_records(records)


def test_external_sources_pin_revision_and_license() -> None:
    source_path = Path(__file__).parents[1] / "datasets" / "prompt" / "sources.json"
    manifest = json.loads(source_path.read_text(encoding="utf-8"))

    assert manifest["sources"]
    for source in manifest["sources"]:
        assert len(source["revision"]) == 40
        assert source["license"]
        assert source["decision"]
        if source["decision"].startswith("APPROVED_"):
            assert len(source["parquetRevision"]) == 40
            assert source["artifacts"].keys() == source["splits"].keys()
            for artifacts in source["artifacts"].values():
                for artifact in artifacts:
                    assert len(artifact["sha256"]) == 64
                    assert artifact["size"] > 0


def test_source_revision_mismatch_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    source = {"repository": "owner/dataset", "revision": "a" * 40}
    monkeypatch.setattr(fetch_public, "_get_json", lambda _: {"sha": "b" * 40})

    with pytest.raises(RuntimeError, match="expected commit"):
        fetch_public.verify_revision(source)


def test_fetch_uses_pinned_parquet_and_checksum(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    data = b"pinned parquet bytes"
    source = {
        "sourceId": "approved-source",
        "repository": "owner/dataset",
        "revision": "a" * 40,
        "parquetRevision": "b" * 40,
        "splits": {"train": 1},
        "artifacts": {
            "train": [
                {
                    "path": "default/train/0000.parquet",
                    "size": len(data),
                    "sha256": sha256(data).hexdigest(),
                }
            ]
        },
    }
    requested_urls: list[str] = []
    monkeypatch.setattr(fetch_public, "verify_revision", lambda _: None)
    monkeypatch.setattr(
        fetch_public,
        "_get_bytes",
        lambda url: requested_urls.append(url) or data,
    )
    monkeypatch.setattr(fetch_public, "_read_parquet_rows", lambda _: [{"text": "safe"}])

    output_path = fetch_public.fetch_split(source, "train", tmp_path)

    assert f"/resolve/{'b' * 40}/default/train/0000.parquet" in requested_urls[0]
    assert output_path.read_text(encoding="utf-8") == '{"text": "safe"}\n'


def test_fetch_rejects_artifact_checksum_mismatch(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    source = {
        "sourceId": "approved-source",
        "repository": "owner/dataset",
        "revision": "a" * 40,
        "parquetRevision": "b" * 40,
        "splits": {"train": 1},
        "artifacts": {
            "train": [
                {
                    "path": "default/train/0000.parquet",
                    "size": 7,
                    "sha256": "0" * 64,
                }
            ]
        },
    }
    monkeypatch.setattr(fetch_public, "verify_revision", lambda _: None)
    monkeypatch.setattr(fetch_public, "_get_bytes", lambda _: b"changed")

    with pytest.raises(RuntimeError, match="checksum mismatch"):
        fetch_public.fetch_split(source, "train", tmp_path)
