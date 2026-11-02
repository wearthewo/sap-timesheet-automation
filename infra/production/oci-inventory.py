#!/usr/bin/env python3
"""Read an OCI archive without extracting paths; verify every referenced blob."""
import base64
import hashlib
import json
import re
import sys
import tarfile
import pathlib


def require(condition):
    # Validation must remain enabled even if Python is invoked with -O.
    if not condition:
        raise ValueError('Invalid OCI descriptor, platform or digest')


def inventory(path, output=None):
    with tarfile.open(path, 'r:*') as archive:
        members = {}
        for member in archive.getmembers():
            name = member.name[2:] if member.name.startswith('./') else member.name
            if member.isdir() and name.rstrip('/') in ('', 'blobs', 'blobs/sha256'):
                continue
            if not member.isfile() or not re.fullmatch(r'(index.json|oci-layout|blobs/sha256/[a-f0-9]{64})', name):
                raise ValueError('Unexpected OCI archive path/type')
            if name in members:
                raise ValueError('Duplicate archive member')
            members[name] = member

        def read(name):
            if members[name].size > 16 * 1024 * 1024:
                raise ValueError('Oversized manifest/config')
            return archive.extractfile(members[name]).read()

        layout = json.loads(read('oci-layout'))
        require(layout['imageLayoutVersion'] == '1.0.0')
        index = json.loads(read('index.json'))
        require(index['schemaVersion'] == 2 and len(index['manifests']) == 1)

        def blob(descriptor, small=False):
            require(re.fullmatch(r'sha256:[a-f0-9]{64}', descriptor['digest']))
            name = 'blobs/sha256/' + descriptor['digest'][7:]
            require(members[name].size == descriptor['size'])
            stream = archive.extractfile(members[name])
            h = hashlib.sha256()
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                h.update(chunk)
            require('sha256:' + h.hexdigest() == descriptor['digest'])
            return read(name) if small else None

        manifest_bytes = blob(index['manifests'][0], True)
        manifest = json.loads(manifest_bytes)
        require(manifest['schemaVersion'] == 2 and manifest['mediaType'] == 'application/vnd.oci.image.manifest.v1+json')
        config_bytes = blob(manifest['config'], True)
        config = json.loads(config_bytes)
        require(config['architecture'] == 'amd64' and config['os'] == 'linux')
        for layer in manifest['layers']:
            blob(layer)
        if output is not None:
            # All paths/types and referenced digests have been checked first.
            # Never extract over an existing directory or follow archive links.
            destination = pathlib.Path(output)
            destination.mkdir()
            (destination / 'blobs' / 'sha256').mkdir(parents=True)
            for name, member in members.items():
                with (destination / name).open('xb') as target:
                    source = archive.extractfile(member)
                    for chunk in iter(lambda: source.read(1024 * 1024), b''):
                        target.write(chunk)
        return dict(manifest=base64.b64encode(manifest_bytes).decode(), config=base64.b64encode(config_bytes).decode())


if __name__ == '__main__':
    print(json.dumps(inventory(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)))
