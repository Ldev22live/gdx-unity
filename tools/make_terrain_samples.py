"""Writes the terrain sample: assets/terrains/Valley.terrain and assets/samples/terrain-3d.json (roadmap R3, TD-082).

Run from the repository root:  python tools/make_terrain_samples.py

Both are ordinary project files: select the Terrain entity in the editor, turn Paint on in its Details and reshape the valley.
This script only exists so the sample can be regenerated. Heights are written as the .terrain format reads them: 32-bit floats,
little endian, row by row along x, in one Base64 string. It reuses the scene helpers of make_block_samples.py.
"""
import base64
import json
import math
import struct
from pathlib import Path

from make_block_samples import Scene, camera, component, lighting, text, SAMPLES, ROOT

TERRAINS = ROOT / "assets" / "terrains"
RESOLUTION = 129
CELL_SIZE = 40.0


def height(x, z):
    """A valley running along z with hills either side and a few bumps: smooth and fully deterministic."""
    valley = 260.0 * (1.0 - math.exp(-((x / 900.0) ** 2)))
    ridges = 70.0 * math.sin(x / 420.0 + 1.3) * math.cos(z / 510.0) + 40.0 * math.sin(z / 260.0 + x / 700.0)
    bowl = 120.0 * (math.hypot(x, z) / 3600.0) ** 2
    return valley + ridges * (0.35 + 0.65 * min(1.0, abs(x) / 1200.0)) + bowl


def write_terrain(name):
    TERRAINS.mkdir(parents=True, exist_ok=True)
    half = (RESOLUTION - 1) * CELL_SIZE / 2.0
    heights = bytearray()
    for iz in range(RESOLUTION):
        for ix in range(RESOLUTION):
            heights += struct.pack("<f", height(-half + ix * CELL_SIZE, -half + iz * CELL_SIZE))
    data = {
        "version": 1,
        "name": name,
        "resolutionX": RESOLUTION,
        "resolutionZ": RESOLUTION,
        "cellSize": CELL_SIZE,
        "heights": base64.b64encode(bytes(heights)).decode("ascii"),
    }
    (TERRAINS / f"{name}.terrain").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="")


def main():
    write_terrain("Valley")
    scene = Scene("terrain", "Terrain Valley")
    lighting(scene, sky=(0.62, 0.76, 0.94))
    # Look down the valley from beyond its low end, high enough to see the whole shape
    scene.add("Main Camera", (0, 1700, 4300), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=60.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=(-0.2164, 0, 0, 0.9763))
    scene.add("Valley", (0, 0, 0), [component("Terrain", terrainPath="terrains/Valley.terrain")])
    scene.add("Help Text", (0, 0, 0), [text(
        "Select Valley and open Details: Paint turns on the terrain brush. Drag to reshape, Ctrl inverts, Shift smooths, Alt-drag "
        "orbits. Every stroke is undoable and saved.", "BOTTOM_LEFT", 16, 24, 1.0, (0.9, 0.95, 1.0))])
    scene.write(SAMPLES / "terrain-3d.json")
    print(f"Valley.terrain {RESOLUTION}x{RESOLUTION}, terrain-3d.json: {len(scene.entities)} entities")


if __name__ == "__main__":
    main()
