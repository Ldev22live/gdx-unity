"""Roadmap MP.3: opt-in migrated copies of one 2D and one 2.5D sample, beside their originals, which are only read.

- samples/platformer-tiles-2d.json: platformer-2d with its seven flat-colour platform boxes replaced by one tile map painted with the
  shared tile set (sprites/tiles.sprite) in 20-unit cells. Every platform edge in the original lies on that grid, so the solid area is
  exactly the same and the player's collision (PlatformerPlayer, which also reads a colliding tile map's boxes) gives the same run. The
  platforms are now painted in Customize > 2D Studio: Tiles instead of typed in as box sizes.
- samples/platformer-profile-terrain-25d.json: platformer-profile-25d with its box ground replaced by a flat terrain
  (terrains/Platformer Ground.terrain) whose top is where the box's top was, so the level plays the same and its ground can be
  sculpted into slopes in World: Terrain.

Every other entity keeps its id and data; the migrated entity's id is recorded below. Deterministic: run it again and the files are the
same. Usage, from the repository root: python tools/make_migrated_samples.py
"""
import base64
import json
import struct
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
SAMPLES = ASSETS / "samples"

# ---- 2D: platforms to a tile map ----
TILE = 20.0
TILE_SET = "sprites/tiles.sprite"
# The shared tile set's auto-tile rule (as in tilemap-platformer-2d): tile id = neighbour mask + 1 (north 1, east 2, south 4, west 8)
GROUND_RULE = list(range(1, 17))
PLATFORM_PREFIX = "Platform"

# ---- 2.5D: box ground to a terrain ----
TERRAIN_NAME = "Platformer Ground"
TERRAIN_CELL = 25.0


def entity_id(sample, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/samples/" + sample + "/" + name))


def write_scene(name, scene):
    text = json.dumps(scene, indent=2) + "\n"
    (SAMPLES / name).write_text(text, encoding="utf-8", newline="")


def platformer_tiles():
    original = json.loads((SAMPLES / "platformer-2d.json").read_text(encoding="utf-8"))
    platforms = []
    kept = []
    insert_at = None
    for entity in original["entities"]:
        if entity["name"].startswith(PLATFORM_PREFIX):
            shape = entity["components"][0]["data"]
            position = entity["transform"]["position"]
            platforms.append((position["x"] - shape["width"] / 2, position["y"] - shape["height"] / 2,
                              position["x"] + shape["width"] / 2, position["y"] + shape["height"] / 2))
            if insert_at is None:
                insert_at = len(kept)
        else:
            kept.append(entity)

    right = max(p[2] for p in platforms)
    top = max(p[3] for p in platforms)
    cols = int(round(right / TILE))
    rows = int(round(top / TILE))
    solid = [[False] * cols for _ in range(rows)]
    for left, bottom, right_edge, top_edge in platforms:
        for value in (left, bottom, right_edge, top_edge):
            if value % TILE != 0 or value < 0:
                raise ValueError(f"platform edge {value} is not on the {TILE}-unit grid: the tiles would not match the box")
        for col in range(int(left / TILE), int(right_edge / TILE)):
            for y in range(int(bottom / TILE), int(top_edge / TILE)):
                solid[rows - 1 - y][col] = True  # row 0 is the top row

    cells = []
    for row in range(rows):
        for col in range(cols):
            if not solid[row][col]:
                cells.append(0)
                continue
            mask = 0
            if row > 0 and solid[row - 1][col]:
                mask |= 1
            if col + 1 < cols and solid[row][col + 1]:
                mask |= 2
            if row + 1 < rows and solid[row + 1][col]:
                mask |= 4
            if col > 0 and solid[row][col - 1]:
                mask |= 8
            cells.append(GROUND_RULE[mask])

    level = {
        "id": entity_id("platformer-tiles-2d", "Level"),
        "name": "Level",
        "active": True,
        "parentId": None,
        "transform": {"position": {"x": 0.0, "y": 0.0, "z": 0.0}, "rotation": {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0},
                      "scale": {"x": 1.0, "y": 1.0, "z": 1.0}},
        "components": [{"type": "TilemapComponent", "enabled": True, "data": {
            "tileSet": TILE_SET, "tileWidth": TILE, "tileHeight": TILE, "collides": True, "cols": cols, "rows": rows,
            "solid": GROUND_RULE, "layers": [{"name": "Ground", "visible": True, "cells": cells}],
            "rules": [{"name": "ground", "tiles": GROUND_RULE}], "tint": {"r": 1, "g": 1, "b": 1, "a": 1}}}],
    }
    kept.insert(insert_at, level)
    migrated = dict(original)
    migrated["name"] = original["name"] + " (Tiles)"
    migrated["entities"] = kept
    write_scene("platformer-tiles-2d.json", migrated)
    print(f"platformer-tiles-2d.json: {len(platforms)} platform boxes became a {cols} x {rows} tile map")


def platformer_terrain():
    original = json.loads((SAMPLES / "platformer-profile-25d.json").read_text(encoding="utf-8"))
    migrated = json.loads(json.dumps(original))
    ground = next(e for e in migrated["entities"] if e["name"] == "Ground")
    box = next(c for c in ground["components"] if c["type"] == "MeshPrimitiveComponent")["data"]
    position = ground["transform"]["position"]
    top = position["y"] + box["sizeY"] / 2
    res_x = int(round(box["sizeX"] / TERRAIN_CELL)) + 1
    res_z = int(round(box["sizeZ"] / TERRAIN_CELL)) + 1
    if (res_x - 1) * TERRAIN_CELL != box["sizeX"] or (res_z - 1) * TERRAIN_CELL != box["sizeZ"]:
        raise ValueError("the ground box is not a whole number of terrain cells")
    heights = bytearray()
    for _ in range(res_x * res_z):
        heights += struct.pack("<f", 0.0)
    terrain = {
        "version": 1,
        "name": TERRAIN_NAME,
        "resolutionX": res_x,
        "resolutionZ": res_z,
        "cellSize": TERRAIN_CELL,
        "heights": base64.b64encode(bytes(heights)).decode("ascii"),
    }
    (ASSETS / "terrains" / f"{TERRAIN_NAME}.terrain").write_text(json.dumps(terrain, indent=2) + "\n", encoding="utf-8", newline="")

    # The terrain is centred on its entity and flat at height 0 in its own space: the entity stands where the box's top was
    ground["transform"]["position"] = {"x": position["x"], "y": top, "z": position["z"]}
    ground["components"] = [
        {"type": "Terrain", "enabled": True, "data": {"terrainPath": f"terrains/{TERRAIN_NAME}.terrain"}},
        {"type": "TerrainCollider3DComponent", "enabled": True,
         "data": {"offsetX": 0.0, "offsetY": 0.0, "offsetZ": 0.0, "trigger": False}},
    ]
    migrated["name"] = original["name"] + " (Terrain)"
    write_scene("platformer-profile-terrain-25d.json", migrated)
    print(f"platformer-profile-terrain-25d.json: the ground box became a {res_x} x {res_z} terrain of {TERRAIN_CELL}-unit cells")


def main():
    platformer_tiles()
    platformer_terrain()


if __name__ == "__main__":
    main()
