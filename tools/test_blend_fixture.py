"""Exercise the Cube Diorama conversion in an installed Blender, using the checked-in source archive."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile


def verify(blender):
    repo = Path(__file__).resolve().parents[1]
    fixtures = repo / "fixtures/mesh-sources"
    entry = json.loads((fixtures / "licenses/manifest.json").read_text())["assets"][0]
    record = entry["originalArchive"]
    archive = repo / record["repositoryPath"]
    if hashlib.sha256(archive.read_bytes()).hexdigest() != record["sha256"]:
        raise ValueError("Cube Diorama archive checksum differs from its source record")
    with tempfile.TemporaryDirectory() as temp:
        work = Path(temp)
        source = work / Path(record["member"]).name
        with ZipFile(archive) as bundle:
            source.write_bytes(bundle.read(record["member"]))
        output = work / "wall.glb"
        command = [blender, "--background", "--factory-startup", "--disable-autoexec", str(source),
                   "--python-exit-code", "1", "--python", str(repo / "tools/blend_to_static_glb.py"),
                   "--", "--object", "Wall1", "--output", str(output)]
        refused = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if refused.returncode == 0 or "Unsupported material graph: Plaster - Teal" not in refused.stdout or output.exists():
            raise RuntimeError("Expected the procedural plaster refusal with no GLB output:\n" + refused.stdout)
        subprocess.run(command + ["--geometry-only"], check=True)
        if hashlib.sha256(output.read_bytes()).hexdigest() != entry["files"][0]["sha256"]:
            raise ValueError("Converted GLB differs from the pinned Blender fixture")
        if hashlib.sha256(source.read_bytes()).hexdigest() != record["memberSha256"]:
            raise ValueError("Conversion changed the preserved Blender source")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--blender", default="blender")
    verify(parser.parse_args().blender)

