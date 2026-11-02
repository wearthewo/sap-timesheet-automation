"""Negative integrity regressions; actual upstream/distribution checks run at image build."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.dont_write_bytecode = True


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


prepare = load("prepare-source").prepare
verify = load("verify-distribution").verify


class BuildIntegrity(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def jar(self, name, entry="com/google/common/collect/ImmutableMap.class"):
        with zipfile.ZipFile(self.root / name, "w") as archive:
            archive.writestr(entry, b"untrusted class bytes")

    def test_changed_source_rejected_before_extraction(self):
        archive = self.root / "source.tar.gz"
        archive.write_bytes(b"untrusted input")
        with self.assertRaisesRegex(ValueError, "Unreviewed upstream"):
            prepare(archive, self.root / "output")
        self.assertFalse((self.root / "output").exists())

    def test_missing_guava_rejected(self):
        with self.assertRaisesRegex(ValueError, "exactly one Guava"):
            verify(self.root)

    def test_old_guava_rejected(self):
        self.jar("guava-33.4.0-jre.jar")
        with self.assertRaisesRegex(ValueError, "exactly one Guava"):
            verify(self.root)

    def test_correct_name_with_wrong_bytes_rejected(self):
        self.jar("guava-33.7.2-jre.jar")
        with self.assertRaisesRegex(ValueError, "bytes differ"):
            verify(self.root)

    def test_duplicate_class_owner_rejected(self):
        self.jar("guava-33.7.2-jre.jar")
        self.jar("provider.jar")
        with self.assertRaisesRegex(ValueError, "exactly one Guava"):
            verify(self.root)

    def test_shaded_class_owner_rejected(self):
        self.jar("guava-33.7.2-jre.jar")
        self.jar("provider.jar", "vendor/shaded/com/google/common/collect/ImmutableMap.class")
        with self.assertRaisesRegex(ValueError, "exactly one Guava"):
            verify(self.root)


if __name__ == "__main__":
    unittest.main()
