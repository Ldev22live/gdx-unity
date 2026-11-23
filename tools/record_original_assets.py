"""Writes assets/licenses/originals.json: the record of the 2D art made in this repository (roadmap M2b.5, TD-099).

Run from the repository root:  python tools/record_original_assets.py [--check]

Every shipped 2D file (.png, .jpg, .jpeg, .gif, .sprite under assets/, build/ left out) must have a record. A downloaded file has
one in assets/licenses/manifest.json (ThirdPartyManifest); every other one gets one here, with its size, SHA-256, licence (the
repository's own, Apache-2.0) and how it was made, from the ORIGINS rules below. A file no rule covers stops the tool: add a rule
that says truthfully where it came from, never a catch-all.

A file byte for byte the same as a recorded download, at a path the manifest does not list, is not recorded here (it did not come
from this repository): it is listed as a copy, so a person can delete it or record it.

derivedFrom names the downloads a picture shows: a thumbnail or a preview that draws a CC0 model. It is found by following the
references of the prefab or sample scene the picture was made from.

--check writes nothing and exits 1 when the file on disk differs from what would be written.
"""
import fnmatch
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
MANIFEST = ASSETS / "licenses" / "manifest.json"
RECORD = ASSETS / "licenses" / "originals.json"
EXTENSIONS = (".png", ".jpg", ".jpeg", ".gif", ".sprite")
LICENCE = "Apache-2.0"
# Files whose references are followed to find the downloads a picture shows
REFERENCING = (".json", ".prefab", ".material")
STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')

# (glob relative to assets/, how it was made, the file whose references give derivedFrom, or None). First match wins.
ORIGINS = [
    ("templates/previews/*.png", "tools/make_template_previews.py (the game runtime's drawing of the sample)",
     lambda path: "samples/" + Path(path).stem + ".json"),
    ("prefabs/level/*.png", "tools/make_palette_thumbnails.py (the game runtime's drawing of the prefab)",
     lambda path: path[:-len(".png")] + ".prefab"),
    ("prefabs/buildings/*.png", "tools/make_palette_thumbnails.py (the game runtime's drawing of the prefab)",
     lambda path: path[:-len(".png")] + ".prefab"),
    ("textures/brick.png", "tools/make_material_samples.py (drawn with Pillow)", None),
    ("textures/brick_normal.png", "tools/make_material_samples.py (drawn with Pillow)", None),
    ("textures/checker.png", "tools/make_material_samples.py (drawn with Pillow)", None),
    ("textures/neon.png", "tools/make_material_samples.py (drawn with Pillow)", None),
    ("sprites/*.sprite", "by hand in this repository (the pixels are in the file; no generator)", None),
    ("libgdx.png", "the gdx-liftoff project template (the libGDX logo); used only by the unbuilt core/ module", None),
]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def shipped_2d_files():
    found = []
    for path in ASSETS.rglob("*"):
        relative = path.relative_to(ASSETS).as_posix()
        if relative == "build" or relative.startswith("build/"):
            continue
        if path.is_file() and path.suffix.lower() in EXTENSIONS:
            found.append(relative)
    return sorted(found)


def downloads_shown(start, by_path):
    """The manifest ids of the downloaded files that `start` refers to, following references through scenes, prefabs and materials."""
    ids, seen, queue = set(), set(), [start]
    while queue:
        current = queue.pop()
        if current in seen:
            continue
        seen.add(current)
        if current in by_path:
            ids.add(by_path[current])
        file = ASSETS / current
        if not file.is_file() or file.suffix.lower() not in REFERENCING:
            continue
        for text in STRING.findall(file.read_text(encoding="utf-8")):
            text = text.replace("\\/", "/")
            if text in by_path or ((ASSETS / text).is_file() if "/" in text and ".." not in text else False):
                queue.append(text)
    return sorted(ids)


def build():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    by_path = {f["path"]: asset["id"] for asset in manifest["assets"] for f in asset["files"]}
    recorded_sha = {f["sha256"] for asset in manifest["assets"] for f in asset["files"]}
    files, copies, unknown = [], [], []
    for relative in shipped_2d_files():
        if relative in by_path:
            continue
        digest = sha256(ASSETS / relative)
        if digest in recorded_sha:
            copies.append(relative)
            continue
        rule = next((r for r in ORIGINS if fnmatch.fnmatchcase(relative, r[0])), None)
        if rule is None:
            unknown.append(relative)
            continue
        source = rule[2](relative) if rule[2] else None
        record = {"path": relative, "bytes": (ASSETS / relative).stat().st_size, "sha256": digest, "licence": LICENCE,
                  "madeWith": rule[1]}
        shown = downloads_shown(source, by_path) if source else []
        if shown:
            record["derivedFrom"] = shown
        files.append(record)
    text = json.dumps({"schemaVersion": 1, "kind": "project-originals",
                       "note": "Written by tools/record_original_assets.py (TD-099); licence of the repository: LICENSE",
                       "files": files}, indent=1) + "\n"
    return text, copies, unknown


def main():
    check = "--check" in sys.argv[1:]
    text, copies, unknown = build()
    if unknown:
        print("No rule says where these came from; add one to ORIGINS:", file=sys.stderr)
        for path in unknown:
            print("  " + path, file=sys.stderr)
        return 1
    if copies:
        print(f"{len(copies)} file(s) are byte-for-byte copies of recorded downloads at unrecorded paths (not recorded here):",
              file=sys.stderr)
        for path in copies[:10]:
            print("  " + path, file=sys.stderr)
        if len(copies) > 10:
            print(f"  ... and {len(copies) - 10} more", file=sys.stderr)
    current = RECORD.read_text(encoding="utf-8") if RECORD.is_file() else ""
    if check:
        if current != text:
            print(f"{RECORD.relative_to(ROOT).as_posix()} is out of date: run python tools/record_original_assets.py", file=sys.stderr)
            return 1
        return 0
    if current != text:
        RECORD.write_text(text, encoding="utf-8", newline="\n")
    print(f"{len(json.loads(text)['files'])} original file(s) recorded in {RECORD.relative_to(ROOT).as_posix()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
