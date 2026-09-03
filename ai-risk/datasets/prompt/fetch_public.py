import argparse
import hashlib
import json
from io import BytesIO
from pathlib import Path
from typing import Any
from urllib.parse import quote
from urllib.request import urlopen

HUB_API = "https://huggingface.co/api/datasets"
HUB_BASE = "https://huggingface.co/datasets"
MANIFEST_PATH = Path(__file__).with_name("sources.json")


def _get_json(url: str) -> dict[str, Any]:
    with urlopen(url, timeout=30) as response:
        return json.load(response)


def _get_bytes(url: str) -> bytes:
    with urlopen(url, timeout=60) as response:
        return response.read()


def load_source(source_id: str) -> dict[str, Any]:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    for source in manifest["sources"]:
        if source["sourceId"] == source_id:
            return source
    raise ValueError(f"Unknown sourceId: {source_id}")


def verify_revision(source: dict[str, Any]) -> None:
    revision = quote(source["revision"], safe="")
    metadata = _get_json(f"{HUB_API}/{source['repository']}/revision/{revision}")
    if metadata["sha"] != source["revision"]:
        raise RuntimeError("Pinned source revision did not resolve to the expected commit.")


def _artifact_url(source: dict[str, Any], artifact: dict[str, Any]) -> str:
    revision = quote(source["parquetRevision"], safe="")
    path = quote(artifact["path"], safe="/")
    return f"{HUB_BASE}/{source['repository']}/resolve/{revision}/{path}"


def _read_parquet_rows(data: bytes) -> list[dict[str, Any]]:
    try:
        from pyarrow import parquet
    except ImportError as error:
        raise RuntimeError(
            "Parquet support is required. Install the data dependency with "
            "`pip install -e '.[data]'`."
        ) from error
    return parquet.read_table(BytesIO(data)).to_pylist()


def fetch_split(source: dict[str, Any], split: str, output_dir: Path) -> Path:
    verify_revision(source)
    expected_rows = source["splits"][split]
    artifacts = source.get("artifacts", {}).get(split)
    if not artifacts:
        raise RuntimeError(f"No pinned Parquet artifact for split: {split}")
    output_path = output_dir / source["sourceId"] / f"{split}.jsonl"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    rows: list[dict[str, Any]] = []
    for artifact in artifacts:
        data = _get_bytes(_artifact_url(source, artifact))
        digest = hashlib.sha256(data).hexdigest()
        if digest != artifact["sha256"]:
            raise RuntimeError(
                f"Artifact checksum mismatch for {source['sourceId']}/{split}: "
                f"expected {artifact['sha256']}, received {digest}"
            )
        if len(data) != artifact["size"]:
            raise RuntimeError(
                f"Artifact size mismatch for {source['sourceId']}/{split}: "
                f"expected {artifact['size']}, received {len(data)}"
            )
        rows.extend(_read_parquet_rows(data))

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
