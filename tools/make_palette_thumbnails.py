"""Renders a thumbnail for every prefab of the World strip's Objects and Buildings (roadmap M2b.3): assets/prefabs/level/<Name>.png
and assets/prefabs/buildings/<Name>.png, beside each prefab, which is where the palette looks for them.

Run from the repository root:  python tools/make_palette_thumbnails.py [name ...]

Each prefab is staged alone in a scene (a sun, a sky light and a camera framed on its size, looking down from the front right), the
game runtime draws it once (--smoke-test with GDXUNITY_SCREENSHOT), and the centre of the picture is cut to a square and scaled to
THUMBNAIL pixels. The scenes are written to a temporary folder, never into assets/. Every prefab gets its thumbnail the same way, so
the shape-built originals and the CC0 starter pack look alike in the palette. Run it again after changing a prefab.

Then run  python tools/record_original_assets.py  so the pictures' licence record (licenses/originals.json, TD-099) has
their new checksums; ShippedSpritesProvenanceTest fails until it does.
"""
import json
import math
import os
import struct
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
FOLDERS = ("prefabs/level", "prefabs/buildings")
THUMBNAIL = 128
# The camera looks down at the prefab from this pitch, from the front right at this yaw (degrees)
PITCH = -24.0
YAW = 32.0
FIELD_OF_VIEW = 40.0
# How much of the view's height the prefab's bounding sphere fills (smaller leaves more margin)
FILL = 1.1
FRAMES = 40


def glb_bounds(path):
    data = path.read_bytes()
    length = struct.unpack_from("<I", data, 12)[0]
    doc = json.loads(data[20:20 + length].decode("utf-8"))
    lo = [math.inf] * 3
    hi = [-math.inf] * 3

    def visit(index, offset, scale):
        node = doc["nodes"][index]
        t = node.get("translation", [0, 0, 0])
        s = node.get("scale", [1, 1, 1])
        o = [offset[i] + t[i] * scale[i] for i in range(3)]
        sc = [scale[i] * s[i] for i in range(3)]
        if "mesh" in node:
            for primitive in doc["meshes"][node["mesh"]]["primitives"]:
                accessor = doc["accessors"][primitive["attributes"]["POSITION"]]
                for i in range(3):
                    lo[i] = min(lo[i], o[i] + accessor["min"][i] * sc[i])
                    hi[i] = max(hi[i], o[i] + accessor["max"][i] * sc[i])
        for child in node.get("children", []):
            visit(child, o, sc)

    for node in doc["scenes"][doc.get("scene", 0)]["nodes"]:
        visit(node, [0, 0, 0], [1, 1, 1])
    return lo, hi


def prefab_bounds(prefab):
    """World-unit bounds of a prefab whose parts are the root's children (as every palette prefab is), ignoring turns."""
    lo = [math.inf] * 3
    hi = [-math.inf] * 3
    for entity in prefab["entities"]:
        t = entity["transform"]
        p = [t["position"][k] for k in "xyz"]
        s = [t["scale"][k] for k in "xyz"]
        for component in entity["components"]:
            data = component.get("data", {})
            if component["type"] == "MeshPrimitiveComponent":
                half = [data["sizeX"] / 2 * s[0], data["sizeY"] / 2 * s[1], data["sizeZ"] / 2 * s[2]]
                box = ([p[i] - half[i] for i in range(3)], [p[i] + half[i] for i in range(3)])
            elif component["type"] == "ModelComponent":
                mlo, mhi = glb_bounds(ASSETS / data["model"])
                k = data.get("importScale", 100.0)
                # a model turned a quarter round swaps x and z, so allow for the larger of the two
                r = max(abs(mlo[0]), abs(mhi[0]), abs(mlo[2]), abs(mhi[2])) * k * s[0]
                box = ([p[0] - r, p[1] + mlo[1] * k * s[1], p[2] - r], [p[0] + r, p[1] + mhi[1] * k * s[1], p[2] + r])
            else:
                continue
            for i in range(3):
                lo[i] = min(lo[i], box[0][i])
                hi[i] = max(hi[i], box[1][i])
    if lo[0] == math.inf:
        raise SystemExit("Nothing to draw in " + prefab["name"])
    return lo, hi


def quaternion(yaw, pitch):
    """Yaw about y, then pitch about the camera's own x (the camera looks down -z)."""
    cy, sy = math.cos(math.radians(yaw) / 2), math.sin(math.radians(yaw) / 2)
    cp, sp = math.cos(math.radians(pitch) / 2), math.sin(math.radians(pitch) / 2)
    return {"x": cy * sp, "y": sy * cp, "z": -sy * sp, "w": cy * cp}


def entity(name, position, rotation, components):
    return {"id": str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/thumbnail/" + name)), "name": name, "active": True, "parentId": None,
            "transform": {"position": dict(zip("xyz", position)), "rotation": rotation, "scale": {"x": 1.0, "y": 1.0, "z": 1.0}},
            "components": components}


def stage(prefab):
    lo, hi = prefab_bounds(prefab)
    centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
    radius = math.dist(lo, hi) / 2
    distance = radius / FILL / math.sin(math.radians(FIELD_OF_VIEW) / 2)
    yaw, pitch = math.radians(YAW), math.radians(PITCH)
    back = [math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)]
    camera = [centre[i] + back[i] * distance for i in range(3)]
    entities = [
        entity("Sun", [0.0, 800.0, 400.0], {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0}, [{"type": "DirectionalLightComponent", "enabled": True,
            "data": {"directionX": -0.4, "directionY": -1.0, "directionZ": -0.6, "colorR": 1.0, "colorG": 0.96, "colorB": 0.88,
                     "colorA": 1.0, "intensity": 1.0, "castShadows": False}}]),
        entity("Sky Light", [0.0, 0.0, 0.0], {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0}, [
            {"type": "SkyLightComponent", "enabled": True, "data": {"skyR": 0.6, "skyG": 0.72, "skyB": 0.9, "groundR": 0.32,
                                                                      "groundG": 0.29, "groundB": 0.26, "intensity": 0.6}},
            {"type": "ToneMappingComponent", "enabled": True, "data": {"exposure": 1.0, "operator": "ACES_FILMIC"}}]),
        entity("Camera", camera, quaternion(YAW, PITCH), [{"type": "CameraComponent", "enabled": True, "data": {
            "projection": "PERSPECTIVE", "orthographicHeight": 480.0, "fieldOfView": FIELD_OF_VIEW, "nearPlane": 1.0,
            "farPlane": 50000.0}}]),
    ]
    entities += [json.loads(json.dumps(e)) for e in prefab["entities"]]
    return {"schemaVersion": 1, "name": "Thumbnail " + prefab["name"], "gameMode": "3d", "active": True, "entities": entities}


def render(scene_file, picture):
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    env = dict(os.environ, GDXUNITY_SCREENSHOT=str(picture))
    args = f'--project "{ASSETS}" --scene "{scene_file}" --smoke-test={FRAMES}'
    result = subprocess.run([str(gradlew), "-q", ":engine-desktop:run", f"--args={args}"], cwd=ROOT, env=env,
                            capture_output=True, text=True)
    if result.returncode != 0 or not picture.is_file():
        raise SystemExit(f"The game runtime could not draw {scene_file.name}:\n{result.stdout}\n{result.stderr}")


def thumbnail(picture, target):
    with Image.open(picture) as image:
        side = min(image.size)
        left = (image.width - side) // 2
        top = (image.height - side) // 2
        square = image.crop((left, top, left + side, top + side)).convert("RGB")
        square.resize((THUMBNAIL, THUMBNAIL), Image.LANCZOS).save(target, optimize=True)


def main(names):
    with tempfile.TemporaryDirectory(prefix="forgeroom-thumbnails-") as temp:
        for folder in FOLDERS:
            for path in sorted((ASSETS / folder).glob("*.prefab")):
                if names and path.stem not in names:
                    continue
                prefab = json.loads(path.read_text(encoding="utf-8"))
                scene_file = Path(temp) / (path.stem + ".json")
                scene_file.write_text(json.dumps(stage(prefab)), encoding="utf-8")
                picture = Path(temp) / (path.stem + ".png")
                render(scene_file, picture)
                thumbnail(picture, path.with_suffix(".png"))
                print("Wrote", path.with_suffix(".png").relative_to(ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
