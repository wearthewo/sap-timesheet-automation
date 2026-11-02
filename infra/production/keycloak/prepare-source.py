"""Apply the reviewed Guava and Kiota corrections to checksum-pinned upstream source."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import sys
import tarfile


def prepare(archive, target):
    archive, target = Path(archive), Path(target).resolve()
    if hashlib.sha256(archive.read_bytes()).hexdigest() != "f7763d73c33a2d345227522aa51a5ead3b7f0fde2a807704933174b8a3628b5c":
        raise ValueError("Unreviewed upstream source archive")
    if target.exists():
        raise ValueError("Source destination must be new")
    # Authenticate the complete archive and validate all paths before extraction.
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            path = PurePosixPath(member.name)
            if path.is_absolute() or ".." in path.parts or path.parts[0] != "keycloak-26.8.1":
                raise ValueError("Unsafe archive path")
            if not (member.isfile() or member.isdir() or member.issym()):
                raise ValueError("Unsupported archive entry")
            if member.issym():
                (target / path.parent / member.linkname).resolve().relative_to(target / "keycloak-26.8.1")
        source.extractall(target)
    root = target / "keycloak-26.8.1"
    for child in root.iterdir():
        child.rename(target / child.name)
    root.rmdir()
    pom = target / "pom.xml"
    text = pom.read_text()
    needle = "    <dependencyManagement>\n\n        <dependencies>\n"
    if text.count(needle) != 1:
        raise ValueError("Upstream dependency management changed")
    patch = ""
    for artifact, version in [("guava", "33.7.2-jre"), ("failureaccess", "1.0.3")]:
        patch += ("            <dependency>\n                <groupId>com.google.guava</groupId>\n"
                  f"                <artifactId>{artifact}</artifactId>\n"
                  f"                <version>{version}</version>\n            </dependency>\n")
    pom.write_bytes(text.replace(needle, needle + patch).encode())
    package = target / "js/libs/keycloak-admin-client/package.json"
    config = json.loads(package.read_text())
    task = config["wireit"]["generate:openapi"]
    expected = "kiota generate -l typescript -d openapi.json -c AdminClient -n ApiSdk -o ./src/generated --clean-output"
    if task["command"] != expected or task["env"]["KIOTA_VERSION"] != "v1.32.4":
        raise ValueError("Upstream generator changed")
    task["command"] += " && test -s src/generated/adminClient.ts"
    task["env"]["LD_LIBRARY_PATH"] = {"external": True}
    package.write_bytes((json.dumps(config, indent=2) + "\n").encode())
    (target / "mvnw").chmod(0o755)


if __name__ == "__main__":
    prepare(*sys.argv[1:])
