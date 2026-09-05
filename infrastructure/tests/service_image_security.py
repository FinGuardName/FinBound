"""Build-context canaries and Docker save layer checks; never print file contents."""

import io
import json
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tarfile
import zipfile


class ImageVerificationError(ValueError):
    pass


def prepare_context(repo: Path, destination: Path, marker_file: Path) -> None:
    """Copy tracked build inputs, including working-tree edits, without local secrets."""
    destination.mkdir(parents=True, exist_ok=False)
    root_files = {
        ".dockerignore", "gradlew", "settings.gradle.kts", "build.gradle.kts",
        "naver-checkstyle.xml", "naver-checkstyle-suppressions.xml",
    }
    tracked = subprocess.check_output(
        ["git", "-C", str(repo), "ls-files", "-z"],
    ).decode("utf-8").split("\0")
    for name in filter(None, tracked):
        if (name in root_files or name.startswith("gradle/")
                or name.startswith(("backend/agent/", "backend/mock-finance/"))):
            source = repo / name
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)

    # Generated BEFORE the build and planted in actual candidate build inputs.
    # Never use developer credentials or modify their .env / Gradle settings.
    marker = "finguard-build-canary-" + secrets.token_hex(32)
    marker_file.write_text(marker, encoding="utf-8")
    for relative_path in (
        ".env", "gradle.properties",
        "backend/agent/src/main/resources/.env",
        "backend/agent/src/main/resources/container-smoke.key",
        "backend/mock-finance/src/main/resources/.env",
        "backend/mock-finance/src/main/resources/container-smoke.key",
    ):
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("FINGUARD_INTERNAL_CREDENTIAL=" + marker + "\n", encoding="utf-8")


def check_contents(data: bytes, marker: bytes, archive: bool = False, depth: int = 0) -> None:
    if marker in data:
        raise ImageVerificationError("Build-context canary found in image content")
    if archive or data.startswith(b"PK\x03\x04"):
        if depth >= 8:
            raise ImageVerificationError("Nested archive limit exceeded")
        # Decompress JAR/ZIP entries, including Boot JAR dependency JARs.
        with zipfile.ZipFile(io.BytesIO(data)) as bundle:
            for entry in bundle.infolist():
                if not entry.is_dir():
                    check_contents(
                        bundle.read(entry), marker,
                        entry.filename.lower().endswith((".jar", ".zip")), depth + 1,
                    )


def verify_image_archive(image_path: Path, marker: bytes) -> None:
    if not marker:
        raise ImageVerificationError("A non-empty build-context canary is required")
    with tarfile.open(image_path, "r:*") as image:
        manifest_file = image.extractfile("manifest.json")
        manifests = json.load(manifest_file)
        if not manifests:
            raise ImageVerificationError("Image manifest is empty")
        for manifest in manifests:
            check_contents(image.extractfile(manifest["Config"]).read(), marker)
            if not manifest["Layers"]:
                raise ImageVerificationError("Image has no layers")
            # Inspect EVERY saved layer; docker export would hide deleted files.
            for layer_name in manifest["Layers"]:
                with tarfile.open(fileobj=image.extractfile(layer_name), mode="r|*") as layer:
                    for entry in layer:
                        if entry.isfile():
                            check_contents(
                                layer.extractfile(entry).read(), marker,
                                entry.name.lower().endswith((".jar", ".zip")),
                            )


def verify_mock_response(response_path: Path) -> None:
    response = json.loads(response_path.read_text(encoding="utf-8"))
    expected = {
        "requestId": "REQ-001", "tool": "CREDIT_SCORE_READ",
        "consumerId": "CUST-1001", "result": {"creditScore": 812},
    }
    if response != expected:
        raise ImageVerificationError("Authenticated Mock Finance response violates contract")


def main() -> int:
    try:
        command, *args = sys.argv[1:]
        if command == "prepare" and len(args) == 3:
            prepare_context(*(Path(arg) for arg in args))
        elif command == "verify" and len(args) == 2:
            verify_image_archive(Path(args[0]), Path(args[1]).read_bytes())
        elif command == "response" and len(args) == 1:
            verify_mock_response(Path(args[0]))
        else:
            raise ImageVerificationError("Invalid verification command")
    except Exception:
        # Invalid archives / I/O errors fail closed; no secret, payload or path reflection.
        print("Service image security verification failed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
