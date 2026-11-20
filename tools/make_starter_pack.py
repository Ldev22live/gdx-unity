"""Writes the curated CC0 starter pack (roadmap M2b.2): a tree and a rock for the World strip's Objects
(assets/prefabs/level/) and a house for Buildings (assets/prefabs/buildings/), made from Kenney's Nature Kit and Building Kit.

Run from the repository root, after the two packs were imported through the editor's Assets > Import (they are in
assets/thirdparty/kenney/ and recorded in assets/licenses/manifest.json):  python tools/make_starter_pack.py

The models stay as Kenney made them; these prefabs only place them. Each prefab's origin is at the foot, so it stands where it is
placed. Kenney's Nature Kit is modelled at about one unit per tile, so the tree and the rock are scaled up to a believable size; the
Building Kit is in metres (walls 2.4 m high, 2 m long), so the house is at scale 1. Every model keeps the glTF import scale of 100
units per metre. Ids are derived from names, so running the script again writes the same files.

Collision (roadmap M2b.4, TD-096): each model carries simple static colliders measured once from Kenney's meshes (the numbers below,
in the model's metres), not a mesh collider. Boxes and capsules cost little when a level holds many copies, need no model loaded to
build, and suit any body. The tree blocks at its trunk only, so characters walk under the crown; the rock is one box; each house piece
gets the box of its own piece, and the doorway gets two jambs and a lintel so a character walks in through it.
"""
import json
import math
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
LEVEL = ASSETS / "prefabs" / "level"
BUILDINGS = ASSETS / "prefabs" / "buildings"
NATURE = "thirdparty/kenney/nature-kit/Models/GLTF format/"
BUILDING = "thirdparty/kenney/building-kit/Models/GLB format/"
UNITS_PER_METRE = 100.0
# Kenney's Nature Kit tree is 1.23 kit units tall; at 5 it stands about 6 m, like the starter Oak Tree
TREE_SCALE = 5.0
# The tall rock is 1 kit unit; at 1.5 it is a 1.5 m boulder
ROCK_SCALE = 1.5
# The Building Kit's grid: floor tiles and wall pieces are 2 m
CELL = 2.0
WALL_HEIGHT = 2.4

# Collider sizes, measured from the models' vertices (in the model's metres, before the prefab's scale). Kenney's nature models stand
# on a node lowered by 5 cm, so their meshes start at -0.05.
NATURE_FOOT = -0.05
# The oak's bark: 0.1 m round up the trunk (0.2 m where it flares at the ground), up to 0.5 m in the mesh where the crown begins
TRUNK_RADIUS = 0.11
TRUNK_TOP = 0.45
# The tall rock: 0.984 m by 0.684 m, 0.996 m high in the mesh
ROCK_SIZE = (0.984, 0.996, 0.684)
# Building Kit pieces: a floor tile is 0.1 m thick; walls are 0.1 m (plain) or 0.2 m (with an opening) thick; the doorway's opening is
# 1 m wide and 2.1 m high; the flat roof corner spans -1 to 1.2 m and is 0.4 m thick
FLOOR_THICKNESS = 0.1
WALL_THICKNESS = 0.1
OPENING_WALL_THICKNESS = 0.2
DOOR_WIDTH = 1.0
DOOR_HEIGHT = 2.1
ROOF_MIN = -1.0
ROOF_MAX = 1.2
ROOF_THICKNESS = 0.4


def entity_id(prefab, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"forgeroom/prefab/{prefab}/{name}"))


def box(size_m, centre_m):
    """A static Box Collider 3D in the piece's local units (model metres times the import scale)."""
    size = [round(v * UNITS_PER_METRE, 4) for v in size_m]
    centre = [round(v * UNITS_PER_METRE, 4) for v in centre_m]
    return {"type": "BoxCollider3DComponent", "enabled": True, "data": {
        "offsetX": centre[0], "offsetY": centre[1], "offsetZ": centre[2], "trigger": False,
        "sizeX": size[0], "sizeY": size[1], "sizeZ": size[2]}}


def capsule(radius_m, bottom_m, top_m):
    """A static upright Capsule Collider 3D from bottom to top (its caps included), in the piece's local units."""
    radius = radius_m * UNITS_PER_METRE
    total = (top_m - bottom_m) * UNITS_PER_METRE
    return {"type": "CapsuleCollider3DComponent", "enabled": True, "data": {
        "radius": round(radius, 4), "height": round(total - 2 * radius, 4),
        "offsetX": 0.0, "offsetY": round((bottom_m + top_m) / 2 * UNITS_PER_METRE, 4), "offsetZ": 0.0, "trigger": False}}


def model(path):
    if not (ASSETS / path).is_file():
        raise SystemExit(f"Missing {path}: import the Kenney pack through Assets > Import first")
    return {"type": "ModelComponent", "enabled": True, "data": {
        "model": path, "importScale": UNITS_PER_METRE, "loop": True, "playOnStart": True, "speed": 1.0}}


class Prefab:
    def __init__(self, name, folder):
        self.name = name
        self.folder = folder
        self.root_id = entity_id(name, "root")
        self.entities = [self._entity("root", name, None, (0, 0, 0), 0.0, 1.0, [])]

    def _entity(self, key, name, parent, position, yaw, scale, components):
        half = math.radians(yaw) / 2.0
        return {
            "id": entity_id(self.name, key), "parentId": parent, "name": name, "active": True,
            "transform": {"position": dict(zip("xyz", (round(float(v), 4) for v in position))),
                          "rotation": {"x": 0.0, "y": round(math.sin(half), 7), "z": 0.0, "w": round(math.cos(half), 7)},
                          "scale": {"x": float(scale), "y": float(scale), "z": float(scale)}},
            "components": components}

    def piece(self, key, name, path, position_m, yaw=0.0, scale=1.0, colliders=()):
        """A model, positioned in metres from the prefab's foot, with its colliders."""
        position = tuple(v * UNITS_PER_METRE for v in position_m)
        self.entities.append(self._entity(key, name, self.root_id, position, yaw, scale, [model(path)] + list(colliders)))

    def collision(self, piece_key, key, name, collider):
        """A child of a piece that only collides: a piece needing more than one box gets one child per box, each editable alone."""
        self.entities.append(self._entity(f"{piece_key} {key}", name, entity_id(self.name, piece_key), (0, 0, 0), 0.0, 1.0, [collider]))

    def write(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        data = {"schemaVersion": 1, "kind": "prefab", "name": self.name, "rootId": self.root_id, "entities": self.entities}
        (self.folder / f"{self.name}.prefab").write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def tree():
    p = Prefab("CC0 Oak Tree", LEVEL)
    p.piece("model", "Oak (Kenney Nature Kit)", NATURE + "tree_oak.glb", (0, 0, 0), scale=TREE_SCALE,
            colliders=[capsule(TRUNK_RADIUS, NATURE_FOOT, TRUNK_TOP)])
    p.write()


def rock():
    p = Prefab("CC0 Rock", LEVEL)
    p.piece("model", "Tall Rock (Kenney Nature Kit)", NATURE + "rock_tallA.glb", (0, 0, 0), scale=ROCK_SCALE,
            colliders=[box(ROCK_SIZE, (0, NATURE_FOOT + ROCK_SIZE[1] / 2, 0))])
    p.write()


def house():
    """A 4 m by 4 m house on the Building Kit's 2 m grid: four floor tiles, two wall pieces per side (a door and a window at the
    front, windows at the sides) and a flat roof of four corner pieces whose overhangs face out."""
    p = Prefab("CC0 House", BUILDINGS)
    half = CELL  # the house's half width: two cells of 2 m make 4 m
    cells = (-CELL / 2, CELL / 2)
    for i, x in enumerate(cells):
        for j, z in enumerate(cells):
            p.piece(f"floor {i}{j}", "Floor", BUILDING + "floor.glb", (x, 0, z),
                    colliders=[box((CELL, FLOOR_THICKNESS, CELL), (0, FLOOR_THICKNESS / 2, 0))])
    # Walls are thin along x and 2 m long along z, centred on the edge they stand on; turned 90 degrees they run along x
    walls = [
        ("east north", "wall-window-square.glb", (half, 0, cells[0]), 0.0),
        ("east south", "wall.glb", (half, 0, cells[1]), 0.0),
        ("west north", "wall.glb", (-half, 0, cells[0]), 180.0),
        ("west south", "wall-window-square.glb", (-half, 0, cells[1]), 180.0),
        ("north west", "wall.glb", (cells[0], 0, -half), 90.0),
        ("north east", "wall.glb", (cells[1], 0, -half), 90.0),
        ("front door", "wall-doorway-square.glb", (cells[0], 0, half), -90.0),
        ("front window", "wall-window-square.glb", (cells[1], 0, half), -90.0),
    ]
    for key, file, position, yaw in walls:
        piece_key = f"wall {key}"
        if file == "wall-doorway-square.glb":
            p.piece(piece_key, "Wall " + key.title(), BUILDING + file, position, yaw)
            # Two jambs either side of the opening and the lintel over it, so the doorway lets a character in
            jamb = (CELL - DOOR_WIDTH) / 2
            jamb_centre = DOOR_WIDTH / 2 + jamb / 2
            for side, z in (("left", -jamb_centre), ("right", jamb_centre)):
                p.collision(piece_key, f"jamb {side}", f"Collision Jamb {side.title()}",
                            box((OPENING_WALL_THICKNESS, WALL_HEIGHT, jamb), (0, WALL_HEIGHT / 2, z)))
            lintel = WALL_HEIGHT - DOOR_HEIGHT
            p.collision(piece_key, "lintel", "Collision Lintel",
                        box((OPENING_WALL_THICKNESS, lintel, DOOR_WIDTH), (0, DOOR_HEIGHT + lintel / 2, 0)))
            continue
        # A window is glass, so the whole piece blocks
        thickness = WALL_THICKNESS if file == "wall.glb" else OPENING_WALL_THICKNESS
        p.piece(piece_key, "Wall " + key.title(), BUILDING + file, position, yaw,
                colliders=[box((thickness, WALL_HEIGHT, CELL), (0, WALL_HEIGHT / 2, 0))])
    # The roof corner overhangs towards +x and +z; turning it puts the overhang on the outside of each cell
    for key, x, z, yaw in (("ne", CELL / 2, CELL / 2, 0.0), ("se", CELL / 2, -CELL / 2, 90.0),
                           ("sw", -CELL / 2, -CELL / 2, 180.0), ("nw", -CELL / 2, CELL / 2, -90.0)):
        span = ROOF_MAX - ROOF_MIN
        centre = (ROOF_MIN + ROOF_MAX) / 2
        p.piece(f"roof {key}", "Roof " + key.upper(), BUILDING + "roof-flat-corner.glb", (x, WALL_HEIGHT, z), yaw,
                colliders=[box((span, ROOF_THICKNESS, span), (centre, ROOF_THICKNESS / 2, centre))])
    p.write()


if __name__ == "__main__":
    tree()
    rock()
    house()
    print("Wrote CC0 Oak Tree and CC0 Rock (prefabs/level) and CC0 House (prefabs/buildings)")
