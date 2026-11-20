"""Writes the terrain sample: assets/terrains/Valley.terrain and assets/samples/terrain-3d.json (roadmap R3, TD-082 and TD-087).

Run from the repository root:  python tools/make_terrain_samples.py

Both are ordinary project files: select the Terrain entity in the editor, turn Paint on in its Details and reshape the valley. The
valley has a Terrain Collider 3D (TD-087), so Hero, a Character Controller 3D, stands on it in Play: WASD or the arrows walk, Space
jumps, and slopes steeper than 45 degrees cannot be climbed. Hero is walked by graphs/samples/ValleyWalker.graph, the character
playground's walker graph (WalkerPlayer.graph) with this valley's hint, so the sample's logic is a graph, not Java.
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
GRAPHS = ROOT / "assets" / "graphs" / "samples"
WALKER_HINT = "WASD or arrows walk, Space jumps. Climb the steps and the gentle ramp, jump onto the low block, push the crates.  "
VALLEY_HINT = "WASD or arrows walk, Space jumps. Slopes steeper than 45 degrees cannot be climbed, and Hero slides down them.  "
RESOLUTION = 129
CELL_SIZE = 40.0
# Where Hero starts: on the valley floor, ahead of the camera
HERO_X = 0.0
HERO_Z = 1500.0
# Height of a default character's centre above the ground it stands on (40 + 30), plus a little to drop from
HERO_CLEARANCE = 80.0


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


def write_walker_graph():
    """ValleyWalker.graph: WalkerPlayer.graph with the hint it shows in Help Text changed for the valley; nothing else differs."""
    walker = json.loads((GRAPHS / "WalkerPlayer.graph").read_text(encoding="utf-8"))
    walker["name"] = "Valley Walker"
    replaced = 0
    for node in walker["nodes"]:
        for literal in node.get("literals", []):
            if isinstance(literal.get("value"), str) and literal["value"].startswith(WALKER_HINT):
                literal["value"] = VALLEY_HINT + literal["value"][len(WALKER_HINT):]
                replaced += 1
    if replaced != 2:
        raise SystemExit(f"WalkerPlayer.graph changed: expected its two hint texts, found {replaced}")
    (GRAPHS / "ValleyWalker.graph").write_text(json.dumps(walker, indent=2) + "\n", encoding="utf-8", newline="")


def main():
    write_terrain("Valley")
    write_walker_graph()
    scene = Scene("terrain", "Terrain Valley")
    lighting(scene, sky=(0.62, 0.76, 0.94))
    # Look down the valley from beyond its low end, high enough to see the whole shape
    # In Play the camera follows Hero (the rig of character-playground-3d); in the editor it shows the whole valley
    scene.add("Main Camera", (0, 1700, 4300), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=60.0, nearPlane=1.0, farPlane=50000.0),
        component("CameraRigComponent", mode="FOLLOW", targetName="Hero", followOffsetX=0.0, followOffsetY=420.0,
                  followOffsetZ=560.0, followRate=6.0, pivotHeight=0.0, eyeHeight=70.0, yaw=0.0, pitch=-20.0, minPitch=-80.0,
                  maxPitch=60.0, distance=400.0, minDistance=100.0, maxDistance=1200.0, occlusionEnabled=False,
                  occlusionMargin=20.0, occlusionMask=65535)],
        rotation=(-0.2164, 0, 0, 0.9763))
    scene.add("Valley", (0, 0, 0), [component("Terrain", terrainPath="terrains/Valley.terrain"),
                                    component("TerrainCollider3DComponent", offsetX=0.0, offsetY=0.0, offsetZ=0.0, trigger=False)])
    scene.add("Hero", (HERO_X, height(HERO_X, HERO_Z) + HERO_CLEARANCE, HERO_Z), [
        component("MeshPrimitiveComponent", shape="BOX", sizeX=60.0, sizeY=140.0, sizeZ=60.0, colorR=0.9, colorG=0.35, colorB=0.3,
                  colorA=1.0),
        component("CharacterController3DComponent", radius=30.0, height=80.0, slopeLimitDegrees=45.0, stepHeight=30.0, mass=70.0,
                  gravityScale=1.0, layer=0, collisionMask=65535),
        component("GraphScriptComponent", graph="graphs/samples/ValleyWalker.graph")])
    # In Play the walker graph replaces this with its hint and whether Hero stands on the ground
    scene.add("Help Text", (0, 0, 0), [text(
        "Select Valley and open Details: Paint turns on the terrain brush. Drag to reshape, Ctrl inverts, Shift smooths, Alt-drag "
        "orbits. Every stroke is undoable and saved. Press Play: WASD or the arrows walk Hero, Space jumps; slopes steeper than "
        "45 degrees cannot be climbed, and Hero slides down them.", "BOTTOM_LEFT", 16, 24, 1.0, (0.9, 0.95, 1.0))])
    scene.write(SAMPLES / "terrain-3d.json")
    print(f"Valley.terrain {RESOLUTION}x{RESOLUTION}, terrain-3d.json: {len(scene.entities)} entities")


if __name__ == "__main__":
    main()
