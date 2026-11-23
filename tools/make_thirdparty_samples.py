"""Writes the materials and the sample that use the downloaded third-party assets (docs/asset-integration-report.md):
assets/materials/RedBrick03.material, WoodFloorDeck.material and RockyTerrain02.material (Poly Haven CC0 textures), and
assets/samples/polyhaven-showcase-3d.json.

Run from the repository root:  python tools/make_thirdparty_samples.py

The showcase is a new, opt-in sample (MC.7's rule: no existing sample is replaced). A wood deck floor, a brick wall with the Poly
Haven rollershutter door set into it, a rocky block, and the stone fire pit, under a sun that casts shadows. The materials use the
1k colour, OpenGL normal and "arm" maps: arm packs ambient occlusion (red), roughness (green) and metalness (blue), so the same
image fills the metallic-roughness and occlusion slots; metallic and roughness are 1 so the map decides (glTF's rule). The models
keep their own embedded PBR materials and are scaled from metres to scene units (100 per metre).
"""
import json
from pathlib import Path

from make_block_samples import Scene, component, lighting, text, SAMPLES

ASSETS = Path(__file__).resolve().parent.parent / "assets"
TEXTURES = "textures/thirdparty/polyhaven"
MODELS = "models/thirdparty/polyhaven"
METRES = 100.0

MATERIALS = [
    # file, name (the file name, as every material is named), slug, tiling, normal strength
    ("RedBrick03.material", "RedBrick03", "red_brick_03", (3.0, 1.5), 1.0),
    ("WoodFloorDeck.material", "WoodFloorDeck", "wood_floor_deck", (6.0, 6.0), 1.0),
    ("RockyTerrain02.material", "RockyTerrain02", "rocky_terrain_02", (1.0, 1.0), 1.0),
]


def write_materials():
    for file_name, name, slug, tiling, normal in MATERIALS:
        folder = "%s/%s/%s_" % (TEXTURES, slug, slug)
        material = {
            "version": 1, "name": name, "baseColor": [1.0, 1.0, 1.0, 1.0], "metallic": 1.0, "roughness": 1.0,
            "emissive": [0.0, 0.0, 0.0], "emissiveStrength": 1.0, "normalScale": normal, "occlusionStrength": 1.0,
            "alphaMode": "OPAQUE", "alphaCutoff": 0.5, "doubleSided": False, "tiling": list(tiling), "offset": [0.0, 0.0],
            "textures": {"BASE_COLOR": folder + "diff_1k.jpg", "NORMAL": folder + "nor_gl_1k.jpg",
                         "METALLIC_ROUGHNESS": folder + "arm_1k.jpg", "OCCLUSION": folder + "arm_1k.jpg"},
        }
        (ASSETS / "materials" / file_name).write_text(json.dumps(material, indent=2) + "\n", encoding="utf-8", newline="")
        print("materials/" + file_name)


def textured_box(size, material):
    return component("MeshPrimitiveComponent", shape="BOX", sizeX=float(size[0]), sizeY=float(size[1]), sizeZ=float(size[2]),
                     colorR=1.0, colorG=1.0, colorB=1.0, colorA=1.0, materialPath="materials/" + material)


def textured_plane(size_x, size_z, material):
    return component("MeshPrimitiveComponent", shape="PLANE", sizeX=float(size_x), sizeY=1.0, sizeZ=float(size_z),
                     colorR=1.0, colorG=1.0, colorB=1.0, colorA=1.0, materialPath="materials/" + material)


def model(path):
    return component("ModelComponent", model=path, importScale=METRES, loop=True, playOnStart=True, speed=1.0)


def showcase():
    scene = Scene("polyhaven-showcase", "Poly Haven Showcase 3D", mode="3d")
    lighting(scene, sky=(0.60, 0.72, 0.90))
    scene.add("Main Camera", (0.0, 260.0, 700.0), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=55.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=(-0.1305, 0.0, 0.0, 0.9914))
    scene.add("Wood Deck", (0.0, 0.0, 0.0), [textured_plane(1600.0, 1600.0, "WoodFloorDeck.material")])
    # The wall stands behind the door: the door model's frame is 108 wide and 240 tall, with its back at z = 0
    scene.add("Brick Wall", (0.0, 150.0, -320.0), [textured_box((900.0, 300.0, 40.0), "RedBrick03.material")])
    scene.add("Rollershutter Door", (0.0, 0.0, -300.0), [model(MODELS + "/rollershutter_door/rollershutter_door_1k.gltf")])
    scene.add("Rock Block", (-300.0, 60.0, -80.0), [textured_box((120.0, 120.0, 120.0), "RockyTerrain02.material")])
    scene.add("Stone Fire Pit", (220.0, 19.0, 60.0), [model(MODELS + "/stone_fire_pit/stone_fire_pit_1k.gltf")])
    scene.add("Help Text", (0, 0, 0), [text(
        "CC0 assets from Poly Haven: wood deck, brick, rock (materials) and the door and fire pit (glTF models).",
        "BOTTOM_LEFT", 16, 24, 1.0, (0.95, 0.95, 0.95))])
    scene.write(SAMPLES / "polyhaven-showcase-3d.json")
    print("polyhaven-showcase-3d.json: %d entities" % len(scene.entities))


if __name__ == "__main__":
    write_materials()
    showcase()
