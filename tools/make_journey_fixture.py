"""Writes fixtures/contract-v1/journey-project: the starting project of the cross-platform journey (urGameForge Alpha A15).

Run from the repository root:  python tools/make_journey_fixture.py

A legacy project (format 1 marker, no manifest yet: the desktop's first save makes one, step j1) with what the journey edits:
- terrains/Level.terrain: a flat 33 x 33 grass ground (the web sculpts it, step j2);
- models/wall.glb: Kenney's CC0 building-kit wall (the desktop edits its geometry, step j3);
- scenes/journey.json: the ground, a player character, a camera, the wall, a gate area, two players and a reach-the-gate
  scenario (the web changes a rule and places an owned object, step j2; the web plays it, step j4).
The ids are fixed (uuid5), so the output is byte-for-byte reproducible.
"""
import base64
import json
import shutil
import struct
import uuid
from pathlib import Path

PROJECT = Path("fixtures/contract-v1/journey-project")
WALL = Path("assets/thirdparty/kenney/building-kit/Models/GLB format/wall.glb")
RESOLUTION = 33
CELL = 50.0


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"urgameforge/journey/{name}"))


def component(kind, **data):
    return {"type": kind, "enabled": True, "data": data}


def entity(name, position, components):
    return {"id": uid(name), "name": name, "active": True, "parentId": None,
            "transform": {"position": dict(zip("xyz", map(float, position))), "rotation": {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0},
                          "scale": {"x": 1.0, "y": 1.0, "z": 1.0}},
            "components": components}


def terrain_text():
    """The desktop's TerrainIO.toJson layout for a flat grass ground."""
    points = RESOLUTION * RESOLUTION
    heights = base64.b64encode(struct.pack("<" + "f" * points, *([0.0] * points))).decode()
    surfaces = base64.b64encode(bytes([1] * points)).decode()
    return ("{\n  \"version\": 1,\n  \"name\": \"Level\",\n"
            f"  \"resolutionX\": {RESOLUTION},\n  \"resolutionZ\": {RESOLUTION},\n  \"cellSize\": {CELL},\n"
            f"  \"heights\": \"{heights}\",\n  \"surfaces\": \"{surfaces}\"\n}}\n")


def main():
    if PROJECT.exists():
        shutil.rmtree(PROJECT)
    (PROJECT / "terrains").mkdir(parents=True)
    (PROJECT / "models").mkdir()
    (PROJECT / "scenes").mkdir()
    (PROJECT / "terrains" / "Level.terrain").write_text(terrain_text(), encoding="utf-8", newline="\n")
    shutil.copyfile(WALL, PROJECT / "models" / "wall.glb")
    texture = Path("Textures/colormap.png")
    (PROJECT / "models" / texture.parent).mkdir()
    shutil.copyfile(WALL.parent / texture, PROJECT / "models" / texture)
    p1, p2 = uid("player 1"), uid("player 2")
    gate = uid("Gate")
    scene = {
        "schemaVersion": 1, "name": "Journey Valley", "active": True,
        "entities": [
            entity("Sun", (0, 800, 400), [component("DirectionalLightComponent", directionX=-0.4, directionY=-1.0, directionZ=-0.6,
                                                    colorR=1.0, colorG=0.96, colorB=0.88, colorA=1.0, intensity=1.0, castShadows=True)]),
            entity("Main Camera", (0, 900, 1500), [component("CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0,
                                                            fieldOfView=55.0, nearPlane=1.0, farPlane=50000.0)]),
            entity("Level Ground", (0, 0, 0), [component("Terrain", terrainPath="terrains/Level.terrain"),
                                               component("TerrainCollider3DComponent", offsetX=0.0, offsetY=0.0, offsetZ=0.0, trigger=False)]),
            entity("Hero", (0, 71, 300), [
                component("MeshPrimitiveComponent", shape="BOX", sizeX=60.0, sizeY=140.0, sizeZ=60.0, colorR=0.3, colorG=0.42, colorB=0.62,
                          colorA=1.0, materialPath=""),
                component("CharacterController3DComponent", radius=30.0, height=80.0, slopeLimitDegrees=45.0, stepHeight=30.0,
                          mass=70.0, gravityScale=1.0, layer=0, collisionMask=65535),
                component("PlayerComponent", useProfileMovement=False),
                component("OwnerComponent", player=p1)]),
            entity("House Wall", (300, 0, -300), [component("ModelComponent", model="models/wall.glb", importScale=100.0, loop=True,
                                                             playOnStart=True, speed=1.0)]),
            entity("Gate", (0, 60, -300), [component("BoxCollider3DComponent", offsetX=0.0, offsetY=0.0, offsetZ=0.0, trigger=True,
                                                     sizeX=200.0, sizeY=120.0, sizeZ=120.0)]),
        ],
        "scenario": {
            "version": 1,
            "players": [{"id": p1, "name": "Player 1", "color": "#2f6fdf"}, {"id": p2, "name": "Player 2", "color": "#d9432f"}],
            "objectives": [{"id": uid("objective gate"), "text": "Reach the gate"}],
            "rules": [
                {"id": uid("rule welcome"), "name": "Welcome", "enabled": True, "once": True, "when": {"type": "timer", "seconds": 2},
                 "then": [{"type": "showMessage", "text": "Find the gate", "seconds": 4}]},
                {"id": uid("rule arrive"), "name": "Arrive", "enabled": True, "once": True,
                 "when": {"type": "enterArea", "area": gate, "who": "player"},
                 "then": [{"type": "completeObjective", "objective": uid("objective gate")}, {"type": "endScenario", "result": "victory"}]},
            ],
        },
    }
    (PROJECT / "scenes" / "journey.json").write_text(json.dumps(scene, indent=2) + "\n", encoding="utf-8", newline="\n")
    (PROJECT / "gdxunity.project").write_text("name=Journey Valley\nstartScene=scenes/journey.json\n", encoding="utf-8", newline="\n")
    print("wrote", PROJECT)


if __name__ == "__main__":
    main()
