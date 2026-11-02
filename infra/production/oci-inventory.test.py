import copy
import hashlib
import importlib.util
import io
import json
import pathlib
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('oci_inventory', pathlib.Path(__file__).with_name('oci-inventory.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class OciInventoryTest(unittest.TestCase):
    def fixture(self):
        files = {'oci-layout': b'{"imageLayoutVersion":"1.0.0"}'}

        def blob(value):
            content = json.dumps(value).encode()
            digest = hashlib.sha256(content).hexdigest()
            files['blobs/sha256/' + digest] = content
            return dict(digest='sha256:' + digest, size=len(content))

        config = blob(dict(os='linux', architecture='amd64'))
        layer = blob(dict(synthetic='layer bytes'))
        manifest = blob(dict(schemaVersion=2, mediaType='application/vnd.oci.image.manifest.v1+json', config=config, layers=[layer]))
        files['index.json'] = json.dumps(dict(schemaVersion=2, manifests=[manifest])).encode()
        return files

    def execute(self, files, duplicate=False, symlink=False):
        with tempfile.TemporaryDirectory(prefix='workforce-oci-') as directory:
            path = pathlib.Path(directory) / 'candidate.tar'
            with tarfile.open(path, 'w') as archive:
                for name, content in files.items():
                    entry = tarfile.TarInfo(name)
                    entry.size = len(content)
                    archive.addfile(entry, io.BytesIO(content))
                if duplicate:
                    entry = tarfile.TarInfo('index.json')
                    archive.addfile(entry, io.BytesIO(b''))
                if symlink:
                    entry = tarfile.TarInfo('link')
                    entry.type = tarfile.SYMTYPE
                    entry.linkname = '/etc/passwd'
                    archive.addfile(entry)
            return module.inventory(path)

    def test_valid_oci_without_extraction(self):
        self.assertEqual(set(self.execute(self.fixture())), {'manifest', 'config'})

    def test_modified_layer_rejected(self):
        files = self.fixture()
        key = next(k for k,v in files.items() if b'synthetic' in v)
        files[key] = b'X' * len(files[key])
        with self.assertRaises(ValueError): self.execute(files)

    def test_path_traversal_rejected(self):
        files = self.fixture()
        files['../../outside'] = b'untrusted'
        with self.assertRaises(ValueError): self.execute(files)

    def test_symlink_rejected(self):
        with self.assertRaises(ValueError): self.execute(self.fixture(), symlink=True)

    def test_duplicate_member_rejected(self):
        with self.assertRaises(ValueError): self.execute(self.fixture(), duplicate=True)

    def test_multiple_manifests_rejected(self):
        files = self.fixture()
        index = json.loads(files['index.json'])
        index['manifests'].append(copy.deepcopy(index['manifests'][0]))
        files['index.json'] = json.dumps(index).encode()
        with self.assertRaises(ValueError): self.execute(files)

    def test_validated_extraction_and_existing_destination_rejection(self):
        files = self.fixture()
        with tempfile.TemporaryDirectory(prefix='workforce-oci-') as directory:
            path = pathlib.Path(directory) / 'candidate.tar'
            with tarfile.open(path, 'w') as archive:
                for name, content in files.items():
                    entry = tarfile.TarInfo(name)
                    entry.size = len(content)
                    archive.addfile(entry, io.BytesIO(content))
            target = pathlib.Path(directory) / 'layout'
            module.inventory(path, target)
            self.assertEqual((target / 'index.json').read_bytes(), files['index.json'])
            with self.assertRaises(FileExistsError): module.inventory(path, target)


if __name__ == '__main__': unittest.main()
