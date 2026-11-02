"""Reject missing, duplicate or unexpected Guava class owners, including relocated copies."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile


def verify(root):
    jars = sorted(Path(root).rglob("*.jar"))
    owners = []
    for jar in jars:
        with zipfile.ZipFile(jar) as content:
            if any(name.endswith("/collect/ImmutableMap.class") and "google" in name for name in content.namelist()):
                owners.append(jar)
    expected = "b530942257fb935f8b2cfaa5f8eb5bd59c555fd8e8d01b8ce98912e077ea606c"
    if len(owners) != 1 or "guava-33.7.2-jre.jar" not in owners[0].name:
        raise ValueError("Expected exactly one Guava 33.7.2-jre class owner")
    if hashlib.sha256(owners[0].read_bytes()).hexdigest() != expected:
        raise ValueError("Guava bytes differ from verified dependency")
    return {"jars": len(jars), "guava": str(owners[0].relative_to(root)), "sha256": expected}


if __name__ == "__main__":
    print(json.dumps(verify(sys.argv[1]), indent=2))
