"""Writes the level editor's objects: assets/prefabs/level/*.prefab (roadmap R3, TD-083).

Run from the repository root:  python tools/make_level_prefabs.py

Each object is an ordinary prefab made of the built-in shapes (boxes and spheres), with its origin at the foot so it stands on the
ground where it is placed. Open one in the editor and change it like any prefab; the level editor lists every prefab in this folder.
Ids are derived from names, so running the script again writes the same files.
"""
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOLDER = ROOT / "assets" / "prefabs" / "level"


def entity_id(prefab, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"forgeroom/prefab/{prefab}/{name}"))


def shape(kind, size, color):
    return {"type": "MeshPrimitiveComponent", "enabled": True, "data": {
        "shape": kind, "sizeX": float(size[0]), "sizeY": float(size[1]), "sizeZ": float(size[2]),
        "colorR": color[0], "colorG": color[1], "colorB": color[2], "colorA": 1.0}}


class Prefab:
    def __init__(self, name):
        self.name = name
        self.entities = []
        self.root_id = entity_id(name, "root")
        self.entities.append(self._entity("root", name, None, (0, 0, 0), []))

    def _entity(self, key, name, parent, position, components):
        return {
            "id": entity_id(self.name, key), "parentId": parent, "name": name, "active": True,
            "transform": {"position": dict(zip("xyz", map(float, position))),
                          "rotation": {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0},
                          "scale": {"x": 1.0, "y": 1.0, "z": 1.0}},
            "components": components}

    def part(self, name, kind, size, position, color):
        self.entities.append(self._entity(name, name, self.root_id, position, [shape(kind, size, color)]))

    def write(self):
        FOLDER.mkdir(parents=True, exist_ok=True)
        data = {"schemaVersion": 1, "kind": "prefab", "name": self.name, "rootId": self.root_id, "entities": self.entities}
        (FOLDER / f"{self.name}.prefab").write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


BARK = (0.38, 0.25, 0.14)
LEAF = (0.16, 0.42, 0.14)
LEAF_LIGHT = (0.24, 0.52, 0.18)
PINE = (0.10, 0.32, 0.18)
STONE = (0.50, 0.50, 0.52)
STONE_DARK = (0.38, 0.38, 0.41)


def oak():
    p = Prefab("Oak Tree")
    p.part("Trunk", "BOX", (36, 150, 36), (0, 75, 0), BARK)
    p.part("Crown", "SPHERE", (190, 150, 190), (0, 215, 0), LEAF)
    p.part("Crown Side", "SPHERE", (130, 110, 130), (60, 180, 25), LEAF_LIGHT)
    p.part("Crown Top", "SPHERE", (120, 100, 120), (-30, 285, -20), LEAF_LIGHT)
    p.write()


def pine():
    p = Prefab("Pine Tree")
    p.part("Trunk", "BOX", (30, 110, 30), (0, 55, 0), BARK)
    p.part("Lower Tier", "SPHERE", (170, 110, 170), (0, 130, 0), PINE)
    p.part("Middle Tier", "SPHERE", (125, 100, 125), (0, 205, 0), PINE)
    p.part("Top Tier", "SPHERE", (80, 90, 80), (0, 275, 0), PINE)
    p.write()


def bush():
    p = Prefab("Bush")
    p.part("Main", "SPHERE", (90, 60, 90), (0, 28, 0), LEAF)
    p.part("Side", "SPHERE", (60, 45, 60), (40, 20, 18), LEAF_LIGHT)
    p.write()


def rock():
    p = Prefab("Rock")
    p.part("Main", "SPHERE", (110, 70, 90), (0, 28, 0), STONE)
    p.part("Chip", "SPHERE", (50, 36, 44), (46, 14, 20), STONE_DARK)
    p.write()


def boulder():
    p = Prefab("Boulder")
    p.part("Main", "SPHERE", (230, 170, 200), (0, 75, 0), STONE)
    p.part("Shoulder", "SPHERE", (120, 90, 110), (-70, 40, 60), STONE_DARK)
    p.part("Chip", "SPHERE", (70, 55, 65), (85, 25, -50), STONE)
    p.write()


def main():
    for build in (oak, pine, bush, rock, boulder):
        build()
    print(f"wrote {len(list(FOLDER.glob('*.prefab')))} prefabs to {FOLDER}")


if __name__ == "__main__":
    main()
