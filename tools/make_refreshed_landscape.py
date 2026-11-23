"""Roadmap M2b.6: the first opt-in sample refresh. Landscape Valley (Terrain) with its generated trees, rocks and bushes replaced by
the curated CC0 starter pack, beside its original, which is only read.

Reads samples/landscape-terrain-3d.json (M3.10: the valley on an editable .terrain) and writes samples/landscape-cc0-terrain-3d.json:

- Every Oak, Pine, Rock and Bush of the original (a ModelComponent on a model of tools/make_landscape_models.py) becomes an instance of
  the matching CC0 prefab (tools/make_starter_pack.py), exactly as the editor saves a placed prefab: the instance root keeps the
  original entity's id, name, position and turn; the prefab's members follow it as children, each linked by a PrefabInstance
  component. The original's import scale (its size against the generated model's) becomes the root's even scale, recorded as the
  instance's only override, so each object keeps its relative size and the prefab stays the source of everything else.
- Everything else (the terrain, the lake, the mountains, the cabin, the lights, the camera and the Hero) is kept as it is, with its id.
- The replacements carry the prefabs' simple static colliders (M2b.4, TD-096): trunks and rocks block, bushes are walked through.
  M3.10 placed the Hero at least 2.5 m from every object, so it still starts in the open.

Deterministic: run it again and the file is the same. Usage, from the repository root: python tools/make_refreshed_landscape.py
"""
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
SAMPLES = ASSETS / "samples"
ORIGINAL = "landscape-terrain-3d.json"
REFRESHED = "landscape-cc0-terrain-3d.json"
# The generated model each replaced entity shows, and the CC0 prefab that replaces it
REPLACEMENTS = {
    "models/landscape/oak-tree.gltf": "prefabs/level/CC0 Oak Tree.prefab",
    "models/landscape/pine-tree.gltf": "prefabs/level/CC0 Pine Tree.prefab",
    "models/landscape/rock.gltf": "prefabs/level/CC0 Rock.prefab",
    "models/landscape/bush.gltf": "prefabs/level/CC0 Bush.prefab",
}
# The generated models were imported at 100 units per metre; an entity's import scale over this is its size against the model
UNITS_PER_METRE = 100.0
# The root's even scale, the instance's override (PrefabState paths); a placed prefab's name and placement are never overrides
SCALE_OVERRIDES = ["transform/scale/x", "transform/scale/y", "transform/scale/z"]


def member_id(original_id, source_id):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"forgeroom/samples/{REFRESHED}/{original_id}/{source_id}"))


def link(prefab_path, source_id, root, known=None):
    data = {"prefab": prefab_path, "sourceId": source_id, "root": root, "overrides": SCALE_OVERRIDES if root else []}
    if root:
        data["knownSourceIds"] = known
    return {"type": "PrefabInstance", "enabled": True, "data": data}


def model_of(entity):
    models = [c for c in entity["components"] if c["type"] == "ModelComponent"]
    return models[0]["data"] if len(models) == 1 and len(entity["components"]) == 1 else None


def instance(entity, prefab_path, prefab):
    """The original entity as a prefab instance: its root, then the prefab's members under it."""
    model = model_of(entity)
    scale = round(model["importScale"] / UNITS_PER_METRE, 6)
    known = [e["id"] for e in prefab["entities"]]
    ids = {prefab["rootId"]: entity["id"]}
    for member in prefab["entities"]:
        if member["id"] != prefab["rootId"]:
            ids[member["id"]] = member_id(entity["id"], member["id"])
    transform = json.loads(json.dumps(entity["transform"]))
    transform["scale"] = {"x": scale, "y": scale, "z": scale}
    out = [{"id": entity["id"], "name": entity["name"], "active": entity["active"], "parentId": None, "transform": transform,
            "components": [link(prefab_path, prefab["rootId"], True, known)]}]
    for member in prefab["entities"]:
        if member["id"] == prefab["rootId"]:
            continue
        out.append({"id": ids[member["id"]], "name": member["name"], "active": member["active"],
                    "parentId": ids[member["parentId"]], "transform": member["transform"],
                    "components": member["components"] + [link(prefab_path, member["id"], False)]})
    return out


def main():
    original = json.loads((SAMPLES / ORIGINAL).read_text(encoding="utf-8"))
    prefabs = {path: json.loads((ASSETS / path).read_text(encoding="utf-8")) for path in REPLACEMENTS.values()}
    entities = []
    counts = {}
    for entity in original["entities"]:
        model = model_of(entity)
        prefab_path = REPLACEMENTS.get(model["model"]) if model else None
        if prefab_path is None:
            entities.append(entity)
            continue
        entities.extend(instance(entity, prefab_path, prefabs[prefab_path]))
        counts[prefab_path] = counts.get(prefab_path, 0) + 1
    scene = dict(original)
    scene["name"] = original["name"].replace(" (Terrain)", "") + " (CC0)"
    scene["entities"] = entities
    (SAMPLES / REFRESHED).write_text(json.dumps(scene, indent=2) + "\n", encoding="utf-8", newline="")
    for path, n in sorted(counts.items()):
        print(f"{n:3d} x {path}")
    print(f"{ORIGINAL} -> {REFRESHED}")


if __name__ == "__main__":
    main()
