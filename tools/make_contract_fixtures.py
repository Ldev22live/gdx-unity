"""Writes fixtures/contract-v1/ for the proposed shared project contract (TD-106, urGameForge Alpha A11).

Run from the repository root:  python tools/make_contract_fixtures.py

The valley project is a normal scene (schemaVersion 1, readable by today's desktop and web) plus what TD-106 proposes:
- a project-manifest.json with asset hashes, capabilities and a revision;
- a format=2 gdxunity.project marker;
- the scenario block (two players and a neutral one, an objective, a timer rule and an area rule);
- OwnerComponent on owned entities.
It also holds deliberately foreign data that a conforming client must keep on save: a component type no client knows, an
unknown field in a known component, and an unknown top-level key. Two invalid manifests are negative cases.
The ids are fixed (uuid5), so the output is byte-for-byte reproducible.
"""
import hashlib
import json
import uuid
from pathlib import Path

ROOT = Path("fixtures/contract-v1")
PROJECT = ROOT / "valley-project"
NAMESPACE = uuid.NAMESPACE_URL


def uid(name):
    return str(uuid.uuid5(NAMESPACE, f"urgameforge/contract-v1/{name}"))


def component(kind, **data):
    return {"type": kind, "enabled": True, "data": data}


def entity(name, position, components, rotation=(0.0, 0.0, 0.0, 1.0)):
    return {
        "id": uid(name), "name": name, "active": True, "parentId": None,
        "transform": {
            "position": dict(zip("xyz", map(float, position))),
            "rotation": dict(zip("xyzw", map(float, rotation))),
            "scale": {"x": 1.0, "y": 1.0, "z": 1.0},
        },
        "components": components,
    }


def box(size, color, material=""):
    return component("MeshPrimitiveComponent", shape="BOX", sizeX=size[0], sizeY=size[1], sizeZ=size[2],
                     colorR=color[0], colorG=color[1], colorB=color[2], colorA=1.0, materialPath=material)


def collider(size, trigger=False):
    return component("BoxCollider3DComponent", offsetX=0.0, offsetY=0.0, offsetZ=0.0, trigger=trigger,
                     sizeX=size[0], sizeY=size[1], sizeZ=size[2])


def owner(player):
    return component("OwnerComponent", player=uid(player))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def valley_scene():
    ground = box((4000.0, 100.0, 4000.0), (0.40, 0.58, 0.34), "materials/Meadow.material")
    # Deliberately foreign data (TD-106 point 3): an unknown field in a known component
    ground["data"]["futureTerrainBlend"] = 0.25
    entities = [
        entity("Sun", (0, 800, 400), [component(
            "DirectionalLightComponent", directionX=-0.4, directionY=-1.0, directionZ=-0.6, colorR=1.0, colorG=0.96,
            colorB=0.88, colorA=1.0, intensity=1.0, castShadows=True)]),
        entity("Main Camera", (0, 900, 1800), [component(
            "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=55.0, nearPlane=1.0,
            farPlane=50000.0)], rotation=(-0.2588, 0.0, 0.0, 0.9659)),
        entity("Ground", (0, -50, 0), [ground, collider((4000.0, 100.0, 4000.0))]),
        entity("House", (600, 120, -600), [box((300.0, 240.0, 300.0), (0.70, 0.52, 0.36)),
                                          collider((300.0, 240.0, 300.0)), owner("Player 1")]),
        entity("Tower", (-700, 200, -500), [box((140.0, 400.0, 140.0), (0.55, 0.55, 0.60)),
                                           collider((140.0, 400.0, 140.0)), owner("Player 2")]),
        entity("Rock", (-200, 40, 200), [box((120.0, 80.0, 100.0), (0.50, 0.50, 0.48)),
                                        collider((120.0, 80.0, 100.0)), owner("Nature")]),
        entity("House Door Area", (600, 60, -380), [collider((200.0, 120.0, 120.0), trigger=True)]),
        entity("Hero", (0, 71, 600), [
            box((60.0, 140.0, 60.0), (0.30, 0.42, 0.62)),
            component("CharacterController3DComponent", radius=30.0, height=80.0, slopeLimitDegrees=45.0,
                      stepHeight=30.0, mass=70.0, gravityScale=1.0, layer=0, collisionMask=65535),
            component("PlayerComponent", useProfileMovement=False),
            owner("Player 1"),
            # Deliberately foreign data: a component type no client registers
            component("ExampleFutureComponent", note="kept verbatim by every conforming client", level=3),
        ]),
    ]
    scenario = {
        "version": 1,
        "players": [
            {"id": uid("Player 1"), "name": "Player 1", "color": "#2f6fdf", "spawn": uid("Hero")},
            {"id": uid("Player 2"), "name": "Player 2", "color": "#d94a3a"},
            {"id": uid("Nature"), "name": "Nature", "color": "#8a8a7a", "neutral": True},
        ],
        "bounds": {"minX": -2000.0, "minZ": -2000.0, "maxX": 2000.0, "maxZ": 2000.0},
        "objectives": [{"id": uid("Reach the house"), "text": "Reach the house", "player": uid("Player 1")}],
        "rules": [
            {"id": uid("Welcome"), "name": "Welcome", "enabled": True, "once": True,
             "when": {"type": "timer", "seconds": 2},
             "then": [{"type": "showMessage", "text": "Find the house in the valley.", "seconds": 4}]},
            {"id": uid("Arrived"), "name": "Arrived", "enabled": True, "once": True,
             "when": {"type": "enterArea", "area": uid("House Door Area"), "who": "player"},
             "then": [{"type": "completeObjective", "objective": uid("Reach the house")},
                      {"type": "showMessage", "text": "You reached the house!"},
                      {"type": "endScenario", "result": "victory", "player": uid("Player 1")}]},
        ],
    }
    return {
        "schemaVersion": 1, "name": "Contract Valley", "gameMode": "3d", "gameGenre": "action-rpg", "active": True,
        "scenario": scenario,
        # Deliberately foreign data: an unknown top-level key
        "exampleFutureSetting": {"kept": True},
        "entities": entities,
    }


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    write_json(PROJECT / "materials" / "Meadow.material", {
        "version": 1, "name": "Meadow", "baseColor": [0.42, 0.62, 0.34, 1.0], "metallic": 0.0, "roughness": 0.9,
        "emissive": [0.0, 0.0, 0.0], "emissiveStrength": 1.0, "normalScale": 1.0, "occlusionStrength": 1.0,
        "alphaMode": "OPAQUE", "alphaCutoff": 0.5, "doubleSided": False, "tiling": [1.0, 1.0], "offset": [0.0, 0.0],
        "textures": {}})
    write_json(PROJECT / "scenes" / "valley.json", valley_scene())
    (PROJECT / "gdxunity.project").write_text(
        "format=2\nname=Contract Valley\nstartScene=scenes/valley.json\nprojectId=" + uid("project")
        + "\nmanifest=project-manifest.json\n", encoding="utf-8", newline="\n")
    assets = [{"path": p, "kind": kind, "size": (PROJECT / p).stat().st_size, "sha256": sha256(PROJECT / p)}
              for p, kind in (("materials/Meadow.material", "material"), ("scenes/valley.json", "scene"))]
    manifest = {
        "contractVersion": 1, "projectId": uid("project"), "name": "Contract Valley",
        "startScene": "scenes/valley.json",
        "scenes": [{"id": uid("scene valley"), "path": "scenes/valley.json"}],
        "assets": assets,
        "capabilities": {
            "required": ["scene/1", "preserve-unknown/1", "material/1", "scenario/1", "ownership/1"],
            "optional": ["profile:action-rpg/3d", "physics3d/1"],
        },
        "revision": {"id": uid("revision 1"), "base": None, "savedBy": {"client": "desktop", "version": "0.1.0-dev"},
                     "savedAt": "2026-11-25T12:00:00Z"},
        "extensions": {"example.vendor": {"kept": True}},
    }
    write_json(PROJECT / "project-manifest.json", manifest)

    bad_path = json.loads(json.dumps(manifest))
    bad_path["assets"][0]["path"] = "../outside/secret.txt"
    write_json(ROOT / "invalid" / "path-traversal.manifest.json", bad_path)
    future = json.loads(json.dumps(manifest))
    future["contractVersion"] = 2
    write_json(ROOT / "invalid" / "future-version.manifest.json", future)
    print("wrote", ROOT)


if __name__ == "__main__":
    main()
