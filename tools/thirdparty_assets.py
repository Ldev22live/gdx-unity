"""Downloads, validates and records third-party assets (docs/asset-integration-report.md).

Run from the repository root:
  python tools/thirdparty_assets.py fetch-polyhaven <slug> <kind> <out_dir>
      kind is "model" (the 1k glTF with its .bin and textures) or "texture" (1k JPG: diffuse, GL normal, arm).
      Downloads into out_dir, which must be empty or absent, through Poly Haven's public API (free for any use, a User-Agent
      naming the tool required: https://github.com/Poly-Haven/Public-API/blob/master/ToS.md). Every file's MD5 is checked
      against the API's.
  python tools/thirdparty_assets.py install <fetch_json> <download_dir> <category> <dest_folder> <format> <date>
      Copies a fetched asset into assets/<dest_folder>/ and writes its entry in assets/licenses/manifest.json (replacing an
      entry with the same id): source, licence and its evidence, authors, retrieval date and tool, and every file's original
      name, size and SHA-256. The manifest is what ForgeRoom reads for a file's licence (ThirdPartyManifest).
  python tools/thirdparty_assets.py check-archive <zip>
      Lists a downloaded archive (for example a Kenney pack fetched by a person in a browser) and refuses it when an entry
      has an absolute path, "..", a link, or an executable or script extension.

Only Poly Haven is fetched automatically: Kenney's and OpenGameArt's robots.txt disallow AI agents, so their files are
downloaded by a person and only checked here.
"""
import hashlib
import json
import os
import stat
import sys
import urllib.request
import zipfile

USER_AGENT = "ForgeRoom-asset-import/1.0"
API = "https://api.polyhaven.com"
RESOLUTION = "1k"
TEXTURE_MAPS = ("Diffuse", "nor_gl", "arm")
# What ForgeRoom can read (AssetType): anything else in a download is reported, never imported
SUPPORTED = {".gltf", ".glb", ".bin", ".png", ".jpg", ".jpeg", ".wav", ".ogg", ".txt", ".md"}
REFUSED = {".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".ps1", ".sh", ".com", ".msi", ".jar", ".js", ".vbs", ".py",
           ".scr", ".app", ".apk", ".lnk"}


def safe_relative(path):
    """True for a relative path with no '..' part, no drive and no leading slash."""
    if not path or path.startswith(("/", "\\")) or ":" in path:
        return False
    parts = path.replace("\\", "/").split("/")
    return all(part not in ("", ".", "..") for part in parts)


def archive_problems(names_and_modes):
    """Problems in an archive's entries, given (name, unix mode or None) pairs; empty when it is safe to unpack."""
    problems = []
    for name, mode in names_and_modes:
        if name.endswith("/"):
            if not safe_relative(name.rstrip("/")):
                problems.append("unsafe folder path: " + name)
            continue
        if not safe_relative(name):
            problems.append("unsafe path: " + name)
        if mode is not None and stat.S_ISLNK(mode):
            problems.append("link: " + name)
        if os.path.splitext(name)[1].lower() in REFUSED:
            problems.append("executable or script: " + name)
    return problems


def check_archive(path):
    with zipfile.ZipFile(path) as archive:
        entries = [(info.filename, (info.external_attr >> 16) or None) for info in archive.infolist()]
    problems = archive_problems(entries)
    files = [name for name, _ in entries if not name.endswith("/")]
    unsupported = sorted({os.path.splitext(n)[1].lower() for n in files} - SUPPORTED)
    return {"files": len(files), "problems": problems, "unsupportedExtensions": unsupported}


def _get_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def _download(url, md5, target):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    actual = hashlib.md5(data).hexdigest()
    if actual != md5:
        raise ValueError("MD5 mismatch for %s: expected %s, got %s" % (url, md5, actual))
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "wb") as out:
        out.write(data)
    return {"file": os.path.basename(target), "url": url, "bytes": len(data), "md5": md5,
            "sha256": hashlib.sha256(data).hexdigest()}


def polyhaven_plan(files, kind):
    """The (relative path, url, md5) to download for one asset, from the API's files record. Refuses unsafe paths."""
    plan = []
    if kind == "model":
        entry = files["gltf"][RESOLUTION]["gltf"]
        plan.append((entry["url"].rsplit("/", 1)[1], entry["url"], entry["md5"]))
        for relative, include in sorted(entry.get("include", {}).items()):
            plan.append((relative, include["url"], include["md5"]))
    elif kind == "texture":
        for map_name in TEXTURE_MAPS:
            entry = files[map_name][RESOLUTION]["jpg"]
            plan.append((entry["url"].rsplit("/", 1)[1], entry["url"], entry["md5"]))
    else:
        raise ValueError("kind must be model or texture: " + kind)
    for relative, _, _ in plan:
        if not safe_relative(relative):
            raise ValueError("unsafe path in the API's file list: " + relative)
        if os.path.splitext(relative)[1].lower() not in SUPPORTED:
            raise ValueError("unsupported file type: " + relative)
    return plan


def fetch_polyhaven(slug, kind, out_dir):
    if os.path.exists(out_dir) and os.listdir(out_dir):
        raise ValueError("out_dir must be empty: " + out_dir)
    info = _get_json("%s/info/%s" % (API, slug))
    files = _get_json("%s/files/%s" % (API, slug))
    downloaded = [dict(_download(url, md5, os.path.join(out_dir, *relative.split("/"))), path=relative)
                  for relative, url, md5 in polyhaven_plan(files, kind)]
    return {"slug": slug, "name": info.get("name"), "authors": sorted(info.get("authors", {}).keys()),
            "source": "https://polyhaven.com/a/" + slug, "files": downloaded}


MANIFEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "licenses", "manifest.json")
POLY_HAVEN_LICENCE = "https://polyhaven.com/license"


def manifest_entry(fetched, category, dest_folder, file_format, retrieved):
    """The manifest entry of a fetched Poly Haven asset installed under dest_folder (relative to assets/)."""
    if not safe_relative(dest_folder):
        raise ValueError("unsafe destination: " + dest_folder)
    return {
        "id": "polyhaven-" + fetched["slug"].replace("_", "-"),
        "name": fetched["name"],
        "category": category,
        "source": fetched["source"],
        "licence": "CC0-1.0",
        "licenceEvidence": POLY_HAVEN_LICENCE,
        "authors": fetched["authors"],
        "retrieved": retrieved,
        "retrievedWith": "tools/thirdparty_assets.py fetch-polyhaven (Poly Haven public API, MD5 checked)",
        "format": file_format,
        "dependencies": [],
        "conversion": "none",
        "importStatus": "imported",
        "files": [{"path": dest_folder + "/" + f["path"], "original": f["file"], "bytes": f["bytes"], "sha256": f["sha256"]}
                  for f in fetched["files"]],
    }


def install(fetch_json, download_dir, category, dest_folder, file_format, retrieved, manifest_path=MANIFEST):
    with open(fetch_json, encoding="utf-8") as source:
        fetched = json.load(source)
    entry = manifest_entry(fetched, category, dest_folder, file_format, retrieved)
    assets_root = os.path.dirname(os.path.dirname(os.path.abspath(manifest_path)))
    for f in fetched["files"]:
        with open(os.path.join(download_dir, *f["path"].split("/")), "rb") as data_file:
            data = data_file.read()
        if hashlib.sha256(data).hexdigest() != f["sha256"]:
            raise ValueError("changed since it was fetched: " + f["path"])
        target = os.path.join(assets_root, *dest_folder.split("/"), *f["path"].split("/"))
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "wb") as out:
            out.write(data)
    manifest = {"schemaVersion": 1, "kind": "third-party-assets", "assets": []}
    if os.path.isfile(manifest_path):
        with open(manifest_path, encoding="utf-8") as source:
            manifest = json.load(source)
    manifest["assets"] = sorted([a for a in manifest["assets"] if a["id"] != entry["id"]] + [entry], key=lambda a: a["id"])
    os.makedirs(os.path.dirname(manifest_path), exist_ok=True)
    with open(manifest_path, "w", encoding="utf-8", newline="\n") as out:
        out.write(json.dumps(manifest, indent=2) + "\n")
    return entry


def main(argv):
    if len(argv) >= 4 and argv[0] == "fetch-polyhaven":
        print(json.dumps(fetch_polyhaven(argv[1], argv[2], argv[3]), indent=2))
    elif len(argv) == 7 and argv[0] == "install":
        entry = install(argv[1], argv[2], argv[3], argv[4], argv[5], argv[6])
        print("%s: %d files" % (entry["id"], len(entry["files"])))
    elif len(argv) == 2 and argv[0] == "check-archive":
        result = check_archive(argv[1])
        print(json.dumps(result, indent=2))
        return 1 if result["problems"] else 0
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
