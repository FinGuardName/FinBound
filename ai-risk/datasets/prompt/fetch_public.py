import argparse
import json
from pathlib import Path
from typing import Any
from urllib.parse import urlencode
from urllib.request import urlopen

HUB_API = "https://huggingface.co/api/datasets"
VIEWER_API = "https://datasets-server.huggingface.co"
MANIFEST_PATH = Path(__file__).with_name("sources.json")


def _get_json(url: str) -> dict[str, Any]:
    with urlopen(url, timeout=30) as response:
        return json.load(response)


def load_source(source_id: str) -> dict[str, Any]:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    for source in manifest["sources"]:
        if source["sourceId"] == source_id:
            return source
    raise ValueError(f"Unknown sourceId: {source_id}")


def verify_revision(source: dict[str, Any]) -> None:
    metadata = _get_json(f"{HUB_API}/{source['repository']}")
    if metadata["sha"] != source["revision"]:
        raise RuntimeError(
            "Upstream revision changed. Review the dataset before updating sources.json."
        )


def fetch_split(source: dict[str, Any], split: str, output_dir: Path) -> Path:
    verify_revision(source)
    expected_rows = source["splits"][split]
    output_path = output_dir / source["sourceId"] / f"{split}.jsonl"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    rows: list[dict[str, Any]] = []
    for offset in range(0, expected_rows, 100):
        query = urlencode(
            {
                "dataset": source["repository"],
                "config": source["config"],
                "split": split,
                "offset": offset,
                "length": min(100, expected_rows - offset),
            }
        )
        page = _get_json(f"{VIEWER_API}/rows?{query}")
        rows.extend(item["row"] for item in page["rows"])

    if len(rows) != expected_rows:
        raise RuntimeError(f"Expected {expected_rows} rows but received {len(rows)}")
    with output_path.open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False) + "\n")
    return output_path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_id")
    parser.add_argument("split")
    parser.add_argument("--output-dir", type=Path, default=Path("datasets/cache/prompt"))
    args = parser.parse_args()

    source = load_source(args.source_id)
    if source["decision"] not in {"APPROVED_BASELINE", "APPROVED_ATTACK_SUPPLEMENT"}:
        raise RuntimeError(f"Source is not approved for download: {source['decision']}")
    if args.split not in source["splits"]:
        raise ValueError(f"Unknown split for source: {args.split}")
    print(fetch_split(source, args.split, args.output_dir))


if __name__ == "__main__":
    main()
