"""Verify that a build-context canary never entered any saved image layer."""

import argparse
import json
import tarfile
from pathlib import Path
from typing import BinaryIO


class ImageSecurityError(ValueError):
    pass


def _contains_marker(stream: BinaryIO, marker: bytes) -> bool:
    overlap = b""
    while chunk := stream.read(1024 * 1024):
        candidate = overlap + chunk
        if marker in candidate:
            return True
        overlap = candidate[-(len(marker) - 1) :] if len(marker) > 1 else b""
    return False


def verify_image_layers(image_path: Path, marker_path: Path) -> None:
    marker = marker_path.read_bytes()
    if not marker:
        raise ImageSecurityError("A non-empty canary is required")
    with tarfile.open(image_path, "r:*") as image:
        manifest_file = image.extractfile("manifest.json")
        if manifest_file is None:
            raise ImageSecurityError("Image manifest is unavailable")
        manifests = json.load(manifest_file)
        if not manifests:
            raise ImageSecurityError("Image manifest is empty")
        for manifest in manifests:
            config_file = image.extractfile(manifest["Config"])
            if config_file is None or _contains_marker(config_file, marker):
                raise ImageSecurityError("Canary found in image configuration")
            for layer_name in manifest["Layers"]:
                layer_file = image.extractfile(layer_name)
                if layer_file is None:
                    raise ImageSecurityError("Image layer is unavailable")
                with tarfile.open(fileobj=layer_file, mode="r|*") as layer:
                    for entry in layer:
                        if entry.isfile():
                            content = layer.extractfile(entry)
                            if content is not None and _contains_marker(content, marker):
                                raise ImageSecurityError("Canary found in image layer")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("marker", type=Path)
    args = parser.parse_args()
    try:
        verify_image_layers(args.image, args.marker)
    except (ImageSecurityError, OSError, tarfile.TarError, KeyError, ValueError):
        print("AI Risk image security verification failed")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
