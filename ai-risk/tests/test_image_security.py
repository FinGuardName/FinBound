import io
import json
import tarfile
from pathlib import Path

import pytest

from tests.image_security import ImageSecurityError, verify_image_layers


def _tar_entry(bundle: tarfile.TarFile, name: str, content: bytes) -> None:
    entry = tarfile.TarInfo(name)
    entry.size = len(content)
    bundle.addfile(entry, io.BytesIO(content))


def _write_image(path: Path, layer_content: bytes) -> None:
    layer_buffer = io.BytesIO()
    with tarfile.open(fileobj=layer_buffer, mode="w") as layer:
        _tar_entry(layer, "app/file.txt", layer_content)
    with tarfile.open(path, mode="w") as image:
        _tar_entry(
            image,
            "manifest.json",
            json.dumps([{"Config": "config.json", "Layers": ["layer.tar"]}]).encode(),
        )
        _tar_entry(image, "config.json", b"{}")
        _tar_entry(image, "layer.tar", layer_buffer.getvalue())


def test_image_layer_verification_rejects_build_context_canary(tmp_path: Path) -> None:
    marker = tmp_path / "marker"
    marker.write_bytes(b"private-build-canary")
    image = tmp_path / "image.tar"
    _write_image(image, b"prefix-private-build-canary-suffix")

    with pytest.raises(ImageSecurityError, match="image layer"):
        verify_image_layers(image, marker)


def test_image_layer_verification_accepts_clean_image(tmp_path: Path) -> None:
    marker = tmp_path / "marker"
    marker.write_bytes(b"private-build-canary")
    image = tmp_path / "image.tar"
    _write_image(image, b"public-runtime-content")

    verify_image_layers(image, marker)
