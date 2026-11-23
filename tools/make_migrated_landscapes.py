"""Roadmap M3.10: the five landscape samples with their ground as an editable terrain, beside their originals, which are only read.

For each of samples/landscape-3d.json, landscape-desert-3d.json, landscape-alpine-3d.json, landscape-island-3d.json and
landscape-autumn-3d.json this writes a migrated copy (the same name with -terrain before -3d) and its ground,
terrains/<title>.terrain:

- The ground's heights are the same function the baked glTF ground was built from (tools/make_landscape_models.py and
  tools/make_landscape_biomes.py), sampled on a 129 by 129 grid over the same 60 m square. Every tree, rock and building of the original
  was stood on that function, so they stand on the terrain too.
- Each grid point gets the ground (Grass, Sand, Snow, Gravel, Rock or Dirt) nearest in colour to the original mesh's colour there.
- The baked ground entity "Terrain" is replaced by one with a Terrain and a Terrain Collider 3D; every other entity keeps its id and data.
- A Hero (a Player with a Character Controller 3D) stands on gentle open ground in front of the camera, and the scene is an Action RPG,
  so Play walks it over the ground.

Deterministic: run it again and the files are the same. Usage, from the repository root: python tools/make_migrated_landscapes.py
"""
import base64
import json
import math
import os
import struct
import sys
import uuid
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_landscape_biomes as biomes  # noqa: E402
import make_landscape_models as forest  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
SAMPLES = ASSETS / "samples"
TERRAINS = ASSETS / "terrains"
UNITS_PER_METRE = 100.0
# The ground's square (60 m, as the baked meshes) and grid: 128 cells of 46.875 units
SIZE_METRES = 60.0
RESOLUTION = 129
CELL = SIZE_METRES * UNITS_PER_METRE / (RESOLUTION - 1)
# The grounds a point can get, with the colour each is drawn in (engine-terrain Surface), and its code in the .terrain file
SURFACES = {
    "Grass": (1, (0.30, 0.52, 0.18)),
    "Sand": (2, (0.80, 0.71, 0.46)),
    "Snow": (3, (0.92, 0.94, 0.97)),
    "Gravel": (4, (0.50, 0.48, 0.45)),
    "Rock": (5, (0.46, 0.43, 0.40)),
    "Dirt": (6, (0.42, 0.31, 0.20)),
}
# The Hero: where to look for its start (metres, in front of the camera), how gentle and how open the ground must be there
HERO_FROM = (0.0, 14.0)
HERO_MIN_NORMAL_Y = 0.97
HERO_CLEAR_METRES = 2.5
HERO_ABOVE_WATER_METRES = 0.6
# A default character's centre stands this far above the ground (capsule 30 radius + 40 half height), plus a little to drop from
HERO_CLEARANCE = 80.0
GENRE = "action-rpg"


def forest_color(x, z, h, n):
    """The forest valley's ground colour (as make_landscape_models.make_terrain colours its vertices, without the shading noise)."""
    slope = 1 - n[1]
    if h < forest.LAKE_LEVEL + 0.5:
        color = forest.mix(forest.SAND, forest.GRASS, max(0.0, (h - forest.LAKE_LEVEL) / 0.5))
    elif h > 4.2:
        color = forest.mix(forest.GRASS, forest.DARK_GRASS, min(1.0, (h - 4.2) / 2.5))
    else:
        color = forest.mix(forest.GRASS, forest.DARK_GRASS, max(0.0, min(1.0, (h - 1.5) / 4.0)) * 0.7)
    if slope > 0.12:
        color = forest.mix(color, forest.ROCK, min(1.0, (slope - 0.12) / 0.2))
    return color


class Ground:
    """One landscape's ground: its height and colour functions (metres), and the level under which it is under water, or None."""

    def __init__(self, height, normal, color, water_level):
        self.height, self.normal, self.color, self.water_level = height, normal, color, water_level


def grounds():
    desert = biomes.Biome("desert", "Desert Canyon", biomes.desert_height, biomes.desert_color, water_level=-0.3)
    alpine = biomes.Biome("alpine", "Alpine Valley", biomes.alpine_height, biomes.alpine_color, water_level=-0.1)
    island = biomes.Biome("island", "Tropical Island", biomes.island_height, biomes.island_color, water_level=0.05)
    autumn = biomes.Biome("autumn", "Autumn Forest", biomes.autumn_height, biomes.autumn_color, water_level=-0.2)

    def of(biome):
        return Ground(biome.height, biome.normal, biome.color_fn, biome.water_level)

    return [
        ("landscape-3d.json", "Landscape Valley", Ground(forest.terrain_height, forest.terrain_normal, forest_color, forest.LAKE_LEVEL)),
        ("landscape-desert-3d.json", "Desert Canyon", of(desert)),
        ("landscape-alpine-3d.json", "Alpine Valley", of(alpine)),
        ("landscape-island-3d.json", "Tropical Island", of(island)),
        ("landscape-autumn-3d.json", "Autumn Forest", of(autumn)),
    ]


def nearest_surface(color):
    best, best_code = None, 0
    for name, (code, surface) in SURFACES.items():
        d = sum((a - b) ** 2 for a, b in zip(color, surface))
        if best is None or d < best:
            best, best_code = d, code
    return best_code


def write_terrain(title, ground):
    heights = bytearray()
    surfaces = bytearray()
    half = SIZE_METRES / 2.0
    step = SIZE_METRES / (RESOLUTION - 1)
    for iz in range(RESOLUTION):
        for ix in range(RESOLUTION):
            x, z = -half + ix * step, -half + iz * step
            h = ground.height(x, z)
            heights += struct.pack("<f", h * UNITS_PER_METRE)
            surfaces.append(nearest_surface(ground.color(x, z, h, ground.normal(x, z))))
    data = {
        "version": 1,
        "name": title,
        "resolutionX": RESOLUTION,
        "resolutionZ": RESOLUTION,
        "cellSize": CELL,
        "heights": base64.b64encode(bytes(heights)).decode("ascii"),
        "surfaces": base64.b64encode(bytes(surfaces)).decode("ascii"),
    }
    TERRAINS.mkdir(parents=True, exist_ok=True)
    path = f"terrains/{title}.terrain"
    (ASSETS / path).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="")
    return path


def entity_id(sample, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/samples/" + sample + "/" + name))


def transform(x, y, z):
    return {"position": {"x": x, "y": y, "z": z}, "rotation": {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0},
            "scale": {"x": 1.0, "y": 1.0, "z": 1.0}}


def hero_start(ground, standing):
    """The first point on a widening spiral from HERO_FROM that is gentle, dry and clear of everything standing there (metres)."""
    for ring in range(0, 400):
        radius = ring * 0.5
        steps = max(1, ring * 6)
        for k in range(steps):
            angle = 2 * math.pi * k / steps
            x = HERO_FROM[0] + radius * math.cos(angle)
            z = HERO_FROM[1] + radius * math.sin(angle)
            if abs(x) > SIZE_METRES / 2 - 8 or abs(z) > SIZE_METRES / 2 - 8:
                continue
            h = ground.height(x, z)
            if ground.water_level is not None and h < ground.water_level + HERO_ABOVE_WATER_METRES:
                continue
            if ground.normal(x, z)[1] < HERO_MIN_NORMAL_Y:
                continue
            if any(math.hypot(x - sx, z - sz) < HERO_CLEAR_METRES for sx, sz in standing):
                continue
            return x, z, h
    raise SystemExit("No open, gentle, dry ground for the Hero")


def migrate(original_name, title, ground):
    original = json.loads((SAMPLES / original_name).read_text(encoding="utf-8"))
    migrated_name = original_name.replace("-3d.json", "-terrain-3d.json")
    terrain_path = write_terrain(title, ground)
    entities = []
    standing = []
    for entity in original["entities"]:
        if entity["name"] == "Terrain":
            entities.append({
                "id": entity_id(migrated_name, "Terrain"), "name": "Terrain", "active": True, "parentId": None,
                "transform": transform(0.0, 0.0, 0.0),
                "components": [
                    {"type": "Terrain", "enabled": True, "data": {"terrainPath": terrain_path}},
                    {"type": "TerrainCollider3DComponent", "enabled": True,
                     "data": {"offsetX": 0.0, "offsetY": 0.0, "offsetZ": 0.0, "trigger": False}}]})
            continue
        entities.append(entity)
        if any(c["type"] == "ModelComponent" for c in entity["components"]):
            p = entity["transform"]["position"]
            standing.append((p["x"] / UNITS_PER_METRE, p["z"] / UNITS_PER_METRE))
    x, z, h = hero_start(ground, standing)
    entities.append({
        "id": entity_id(migrated_name, "Hero"), "name": "Hero", "active": True, "parentId": None,
        "transform": transform(round(x * UNITS_PER_METRE, 3), round(h * UNITS_PER_METRE + HERO_CLEARANCE, 3), round(z * UNITS_PER_METRE, 3)),
        "components": [
            {"type": "MeshPrimitiveComponent", "enabled": True, "data": {
                "shape": "BOX", "sizeX": 60.0, "sizeY": 140.0, "sizeZ": 60.0, "colorR": 0.9, "colorG": 0.35, "colorB": 0.3, "colorA": 1.0}},
            {"type": "CharacterController3DComponent", "enabled": True, "data": {
                "radius": 30.0, "height": 80.0, "slopeLimitDegrees": 45.0, "stepHeight": 30.0, "mass": 70.0, "gravityScale": 1.0,
                "layer": 0, "collisionMask": 65535}},
            {"type": "PlayerComponent", "enabled": True, "data": {"useProfileMovement": False}}]})
    scene = {
        "schemaVersion": original["schemaVersion"],
        "name": original["name"] + " (Terrain)",
        "gameMode": original["gameMode"],
        "gameGenre": GENRE,
        "active": original.get("active", True),
        "entities": entities,
    }
    (SAMPLES / migrated_name).write_text(json.dumps(scene, indent=2) + "\n", encoding="utf-8", newline="")
    return migrated_name, terrain_path


def main():
    for original_name, title, ground in grounds():
        migrated, terrain = migrate(original_name, title, ground)
        print(f"{original_name} -> {migrated} ({terrain})")


if __name__ == "__main__":
    main()
