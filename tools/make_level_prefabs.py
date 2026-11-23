"""Writes the level editor's objects: assets/prefabs/level/*.prefab (roadmap R3, TD-083), and its characters:
assets/prefabs/characters/*.prefab (roadmap M3c.2), and its buildings: assets/prefabs/buildings/*.prefab (UI M3).

Run from the repository root:  python tools/make_level_prefabs.py

Each object is an ordinary prefab made of the built-in shapes (boxes and spheres), with its origin at the foot so it stands on the
ground where it is placed.

Collision (roadmap M2b.4, TD-096): the parts a player should bump into carry a static 3D collider. A box part gets a Box Collider
3D of its own size; a rounded part (a rock's sphere) gets a Convex Hull Collider 3D built from its shape. Tree crowns and bushes
have none, so characters walk under branches and through bushes, and only trunks, rocks and walls block. Open one in the editor and change it like any prefab; the level editor lists every prefab in this folder.
Ids are derived from names, so running the script again writes the same files.

The characters are placed by the World strip's Characters section. Player is a character whose root is the centre of its Character
Controller 3D (the level editor lifts it to stand on the ground) and carries Player, so a genre profile controls it in Play. Spawn
Point has its origin at the foot like the objects; in Play the player starts there, facing the way it faces.
"""
import json
import math
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOLDER = ROOT / "assets" / "prefabs" / "level"
CHARACTERS = ROOT / "assets" / "prefabs" / "characters"
BUILDINGS = ROOT / "assets" / "prefabs" / "buildings"


def entity_id(prefab, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"forgeroom/prefab/{prefab}/{name}"))


def shape(kind, size, color):
    return {"type": "MeshPrimitiveComponent", "enabled": True, "data": {
        "shape": kind, "sizeX": float(size[0]), "sizeY": float(size[1]), "sizeZ": float(size[2]),
        "colorR": color[0], "colorG": color[1], "colorB": color[2], "colorA": 1.0}}


# What a part collides with (M2b.4): its own box, a hull around its shape, or nothing
SOLID = "box"
HULL = "hull"


def collider(kind, size):
    """A static 3D collider for a part: the box primitive's size, or a convex hull around the part's mesh."""
    if kind == SOLID:
        return {"type": "BoxCollider3DComponent", "enabled": True, "data": {
            "offsetX": 0.0, "offsetY": 0.0, "offsetZ": 0.0, "trigger": False,
            "sizeX": float(size[0]), "sizeY": float(size[1]), "sizeZ": float(size[2])}}
    if kind == HULL:
        return {"type": "ConvexHullCollider3DComponent", "enabled": True, "data": {
            "offsetX": 0.0, "offsetY": 0.0, "offsetZ": 0.0, "trigger": False}}
    raise ValueError(f"unknown collider kind {kind}")


class Prefab:
    def __init__(self, name, folder=FOLDER, root_components=()):
        self.name = name
        self.folder = folder
        self.entities = []
        self.root_id = entity_id(name, "root")
        self.entities.append(self._entity("root", name, None, (0, 0, 0), list(root_components)))

    def _entity(self, key, name, parent, position, components, roll=0.0):
        # roll: a turn about the z axis in degrees (a sloping roof)
        half = math.radians(roll) / 2.0
        return {
            "id": entity_id(self.name, key), "parentId": parent, "name": name, "active": True,
            "transform": {"position": dict(zip("xyz", map(float, position))),
                          "rotation": {"x": 0.0, "y": 0.0, "z": math.sin(half), "w": math.cos(half)},
                          "scale": {"x": 1.0, "y": 1.0, "z": 1.0}},
            "components": components}

    def part(self, name, kind, size, position, color, roll=0.0, collide=None):
        components = [shape(kind, size, color)]
        if collide is not None:
            components.append(collider(collide, size))
        self.entities.append(self._entity(name, name, self.root_id, position, components, roll))

    def write(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        data = {"schemaVersion": 1, "kind": "prefab", "name": self.name, "rootId": self.root_id, "entities": self.entities}
        (self.folder / f"{self.name}.prefab").write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


BARK = (0.38, 0.25, 0.14)
LEAF = (0.16, 0.42, 0.14)
LEAF_LIGHT = (0.24, 0.52, 0.18)
PINE = (0.10, 0.32, 0.18)
STONE = (0.50, 0.50, 0.52)
STONE_DARK = (0.38, 0.38, 0.41)


def oak():
    p = Prefab("Oak Tree")
    p.part("Trunk", "BOX", (36, 150, 36), (0, 75, 0), BARK, collide=SOLID)
    p.part("Crown", "SPHERE", (190, 150, 190), (0, 215, 0), LEAF)
    p.part("Crown Side", "SPHERE", (130, 110, 130), (60, 180, 25), LEAF_LIGHT)
    p.part("Crown Top", "SPHERE", (120, 100, 120), (-30, 285, -20), LEAF_LIGHT)
    p.write()


def pine():
    p = Prefab("Pine Tree")
    p.part("Trunk", "BOX", (30, 110, 30), (0, 55, 0), BARK, collide=SOLID)
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
    p.part("Main", "SPHERE", (110, 70, 90), (0, 28, 0), STONE, collide=HULL)
    p.part("Chip", "SPHERE", (50, 36, 44), (46, 14, 20), STONE_DARK, collide=HULL)
    p.write()


def boulder():
    p = Prefab("Boulder")
    p.part("Main", "SPHERE", (230, 170, 200), (0, 75, 0), STONE, collide=HULL)
    p.part("Shoulder", "SPHERE", (120, 90, 110), (-70, 40, 60), STONE_DARK, collide=HULL)
    p.part("Chip", "SPHERE", (70, 55, 65), (85, 25, -50), STONE, collide=HULL)
    p.write()


SHIRT = (0.85, 0.38, 0.30)
SKIN = (0.93, 0.76, 0.62)
BOOTS = (0.25, 0.22, 0.20)


def player():
    # The root is the capsule's centre: radius 30 and height 80 make it 140 tall, from 70 below the root to 70 above
    p = Prefab("Player", CHARACTERS, [
        {"type": "CharacterController3DComponent", "enabled": True, "data": {
            "radius": 30.0, "height": 80.0, "slopeLimitDegrees": 45.0, "stepHeight": 30.0, "mass": 70.0, "gravityScale": 1.0,
            "layer": 0, "collisionMask": 65535}},
        {"type": "PlayerComponent", "enabled": True, "data": {"useProfileMovement": False}}])
    p.part("Legs", "BOX", (44, 50, 34), (0, -45, 0), BOOTS)
    p.part("Body", "BOX", (56, 60, 40), (0, 10, 0), SHIRT)
    p.part("Head", "SPHERE", (40, 40, 40), (0, 52, 0), SKIN)
    # A nose shows which way the player faces: yaw 0 looks along -Z
    p.part("Nose", "BOX", (10, 10, 12), (0, 52, -22), SKIN)
    p.write()


def spawn_point():
    # No shape: the editor marks a spawn point in the 3D view, and the game shows nothing
    Prefab("Spawn Point", CHARACTERS, [{"type": "SpawnPointComponent", "enabled": True, "data": {}}]).write()


WALL = (0.86, 0.80, 0.68)
ROOF = (0.62, 0.24, 0.18)
TIMBER = (0.42, 0.28, 0.16)
GLASS = (0.55, 0.70, 0.85)
TOWER_STONE = (0.58, 0.56, 0.52)


def cottage():
    # The World strip's Buildings (UI M3): a small house of boxes, origin at the foot, the door on the -Z side (yaw 0 faces -Z)
    p = Prefab("Cottage", BUILDINGS)
    p.part("Walls", "BOX", (400, 220, 300), (0, 110, 0), WALL, collide=SOLID)
    # Two slopes meeting at the ridge, 35 degrees: each 250 long covers half the 400-wide house with some overhang
    p.part("Roof Left", "BOX", (260, 20, 330), (-100, 290, 0), ROOF, roll=35.0, collide=SOLID)
    p.part("Roof Right", "BOX", (260, 20, 330), (100, 290, 0), ROOF, roll=-35.0, collide=SOLID)
    p.part("Door", "BOX", (70, 130, 8), (0, 65, -152), TIMBER)
    p.part("Window Left", "BOX", (60, 50, 8), (-120, 140, -152), GLASS)
    p.part("Window Right", "BOX", (60, 50, 8), (120, 140, -152), GLASS)
    p.part("Chimney", "BOX", (40, 120, 40), (120, 330, 60), TOWER_STONE, collide=SOLID)
    p.write()


def watchtower():
    p = Prefab("Watchtower", BUILDINGS)
    p.part("Base", "BOX", (220, 400, 220), (0, 200, 0), TOWER_STONE, collide=SOLID)
    p.part("Top", "BOX", (280, 60, 280), (0, 430, 0), TOWER_STONE, collide=SOLID)
    for i, (x, z) in enumerate([(-110, -110), (110, -110), (-110, 110), (110, 110)]):
        p.part(f"Merlon {i + 1}", "BOX", (60, 50, 60), (x, 485, z), TOWER_STONE, collide=SOLID)
    p.part("Door", "BOX", (70, 120, 8), (0, 60, -112), TIMBER)
    p.write()


def main():
    for build in (oak, pine, bush, rock, boulder):
        build()
    print(f"wrote {len(list(FOLDER.glob('*.prefab')))} prefabs to {FOLDER}")
    for build in (player, spawn_point):
        build()
    print(f"wrote {len(list(CHARACTERS.glob('*.prefab')))} prefabs to {CHARACTERS}")
    for build in (cottage, watchtower):
        build()
    print(f"wrote {len(list(BUILDINGS.glob('*.prefab')))} prefabs to {BUILDINGS}")


if __name__ == "__main__":
    main()
