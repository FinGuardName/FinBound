import argparse
from pathlib import Path

from app.prompt.model import (
    DEFAULT_MODEL_DIR,
    PromptDetectorConfig,
    PromptModelError,
    read_verified_artifact,
)


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
    try:
        read_verified_artifact(
            artifact,
            config.model_artifact_sha256,
            "Downloaded prompt model artifact",
        )
        for artifact_path, artifact_sha256 in config.tokenizer_artifacts:
            read_verified_artifact(
                target / artifact_path,
                artifact_sha256,
                "Downloaded prompt tokenizer artifact",
            )
    except PromptModelError as error:
        raise RuntimeError("Downloaded prompt model bundle integrity check failed") from error
    return artifact


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", type=Path, default=DEFAULT_MODEL_DIR)
    args = parser.parse_args()
    print(download(args.target))
