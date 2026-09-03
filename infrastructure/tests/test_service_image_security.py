import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import zipfile

from service_image_security import (
    ImageVerificationError, verify_image_archive, verify_mock_response,
)


def tar_bytes(files):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w") as archive:
        for name, content in files.items():
            entry = tarfile.TarInfo(name)
            entry.size = len(content)
            archive.addfile(entry, io.BytesIO(content))
    return output.getvalue()


def jar_bytes(files):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in files.items():
            archive.writestr(name, content)
    return output.getvalue()


class ImageSecurityTest(unittest.TestCase):
    marker = b"synthetic-build-context-secret"

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "image.tar"

    def image(self, layers, config=b"{}"):
        names = [f"{index}/layer.tar" for index in range(len(layers))]
        files = {name: tar_bytes(layer) for name, layer in zip(names, layers)}
        files["config.json"] = config
        files["manifest.json"] = json.dumps([
            {"Config": "config.json", "Layers": names},
        ]).encode()
        self.path.write_bytes(tar_bytes(files))

    def test_clean_image_with_boot_jar_passes(self):
        self.image([{"app/app.jar": jar_bytes({"BOOT-INF/classes/application.yml": b"safe"})}])
        verify_image_archive(self.path, self.marker)

    def test_plaintext_canary_fails(self):
        self.image([{"app/.env": self.marker}])
        with self.assertRaises(ImageVerificationError):
            verify_image_archive(self.path, self.marker)

    def test_secret_deleted_in_later_layer_still_fails(self):
        self.image([{"app/.env": self.marker}, {"app/.wh..env": b""}])
        with self.assertRaises(ImageVerificationError):
            verify_image_archive(self.path, self.marker)

    def test_compressed_nested_jar_canary_fails(self):
        nested = jar_bytes({"credentials.properties": self.marker})
        self.image([{"app/app.jar": jar_bytes({"BOOT-INF/lib/fixture.jar": nested})}])
        with self.assertRaises(ImageVerificationError):
            verify_image_archive(self.path, self.marker)

    def test_image_config_canary_fails(self):
        self.image([{"app/readme": b"safe"}], config=self.marker)
        with self.assertRaises(ImageVerificationError):
            verify_image_archive(self.path, self.marker)

    def test_unreadable_jar_fails_closed(self):
        self.image([{"app/app.jar": b"not a zip archive"}])
        with self.assertRaises(zipfile.BadZipFile):
            verify_image_archive(self.path, self.marker)

    def test_missing_layer_fails_closed(self):
        self.path.write_bytes(tar_bytes({
            "manifest.json": b'[{"Config":"config.json","Layers":["missing.tar"]}]',
            "config.json": b"{}",
        }))
        with self.assertRaises(KeyError):
            verify_image_archive(self.path, self.marker)

    def test_empty_marker_fails_closed(self):
        with self.assertRaises(ImageVerificationError):
            verify_image_archive(self.path, b"")

    def test_authenticated_response_contract(self):
        response = {
            "requestId": "REQ-001", "tool": "CREDIT_SCORE_READ",
            "consumerId": "CUST-1001", "result": {"creditScore": 812},
        }
        self.path.write_text(json.dumps(response), encoding="utf-8")
        verify_mock_response(self.path)
        for invalid in ({"errorCode": "INTERNAL_ERROR"}, {**response, "result": {}},
                        {**response, "consumerId": "CUST-9999"}):
            with self.subTest(invalid=invalid):
                self.path.write_text(json.dumps(invalid), encoding="utf-8")
                with self.assertRaises(ImageVerificationError):
                    verify_mock_response(self.path)


if __name__ == "__main__":
    unittest.main()
