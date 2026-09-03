import argparse
import hashlib
from pathlib import Path

from app.prompt.model import DEFAULT_MODEL_DIR, PromptDetectorConfig


def download(target: Path = DEFAULT_MODEL_DIR) -> Path:
    try:
        from huggingface_hub import snapshot_download
    except ImportError as error:
        raise RuntimeError("Install the prompt optional dependencies first") from error

    config = PromptDetectorConfig.load()
    snapshot_download(
        repo_id=config.model_id,
        revision=config.model_revision,
        allow_patterns=[f"{config.tokenizer_path}/*"],
        local_dir=target,
    )
    artifact = target / config.model_artifact_path
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    if digest != config.model_artifact_sha256:
        raise RuntimeError("Downloaded prompt model artifact digest does not match")
    return artifact


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", type=Path, default=DEFAULT_MODEL_DIR)
    args = parser.parse_args()
    print(download(args.target))
