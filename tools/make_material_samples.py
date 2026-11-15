"""Writes the material showroom: assets/materials/*.material and assets/samples/materials-3d.json (roadmap R2, TD-081).

Run from the repository root:  python tools/make_material_samples.py

The files are ordinary project files: edit the materials in the editor (Details of a Mesh Primitive that uses one) and the scene
as any other. This script only exists so the showroom can be regenerated. It reuses the scene helpers of make_block_samples.py.
"""
import json
from pathlib import Path

from make_block_samples import SAMPLES, Scene, box, component, lighting, text

ROOT = Path(__file__).resolve().parent.parent
MATERIALS = ROOT / "assets" / "materials"

# name -> material fields, as MaterialIO writes them (version 1); colours are sRGB
LIBRARY = {
    "Rock": {"baseColor": [0.45, 0.43, 0.40, 1.0], "metallic": 0.0, "roughness": 0.95},
    "Wood": {"baseColor": [0.55, 0.35, 0.18, 1.0], "metallic": 0.0, "roughness": 0.75},
    "Water": {"baseColor": [0.10, 0.35, 0.60, 0.55], "metallic": 0.0, "roughness": 0.05, "alphaMode": "BLEND", "doubleSided": True},
    "Gold": {"baseColor": [1.0, 0.77, 0.25, 1.0], "metallic": 1.0, "roughness": 0.25},
    "Lava": {"baseColor": [0.20, 0.05, 0.02, 1.0], "metallic": 0.0, "roughness": 0.8, "emissive": [1.0, 0.3, 0.05], "emissiveStrength": 4.0},
    "Glass": {"baseColor": [0.80, 0.90, 1.0, 0.25], "metallic": 0.0, "roughness": 0.02, "alphaMode": "BLEND"},
    # Textured (the textures below); tiling repeats them across each face
    "Brick": {"roughness": 0.85, "tiling": [3.0, 3.0],
              "textures": {"BASE_COLOR": "textures/brick.png", "NORMAL": "textures/brick_normal.png"}, "normalScale": 1.5},
    "Checker": {"roughness": 0.4, "tiling": [4.0, 4.0], "textures": {"BASE_COLOR": "textures/checker.png"}},
    "Neon": {"baseColor": [0.05, 0.05, 0.08, 1.0], "roughness": 0.6, "emissiveStrength": 3.0, "tiling": [2.0, 2.0],
             "textures": {"EMISSIVE": "textures/neon.png"}},
}
TEXTURES = ROOT / "assets" / "textures"
TEXTURE_SIZE = 256


def write_textures():
    """Draws the showroom's textures with Pillow: bricks, a normal map of the same bricks, a checkerboard and glowing stripes."""
    from PIL import Image, ImageDraw

    TEXTURES.mkdir(parents=True, exist_ok=True)
    size = TEXTURE_SIZE
    brick_h, brick_w, mortar = size // 8, size // 4, 6

    def bricks(draw, brick_colour, mortar_colour):
        draw.rectangle([0, 0, size, size], fill=mortar_colour)
        for row in range(8):
            shift = (brick_w // 2) if row % 2 else 0
            for col in range(-1, 5):
                x0 = col * brick_w + shift
                tone = 150 + ((row * 7 + col * 13) % 5) * 12
                draw.rectangle([x0 + mortar // 2, row * brick_h + mortar // 2, x0 + brick_w - mortar // 2 - 1,
                                (row + 1) * brick_h - mortar // 2 - 1], fill=brick_colour(tone))

    colour = Image.new("RGB", (size, size))
    bricks(ImageDraw.Draw(colour), lambda tone: (tone, tone // 3, tone // 4), (190, 185, 175))
    colour.save(TEXTURES / "brick.png")

    # Normal map: flat (128,128,255) on a brick, tilted at the mortar lines so the joints read as grooves
    normal = Image.new("RGB", (size, size))
    bricks(ImageDraw.Draw(normal), lambda tone: (128, 128, 255), (128, 90, 200))
    normal.save(TEXTURES / "brick_normal.png")

    checker = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(checker)
    cell = size // 8
    for row in range(8):
        for col in range(8):
            draw.rectangle([col * cell, row * cell, (col + 1) * cell - 1, (row + 1) * cell - 1],
                           fill=(235, 235, 235) if (row + col) % 2 == 0 else (40, 60, 160))
    checker.save(TEXTURES / "checker.png")

    neon = Image.new("RGB", (size, size), (0, 0, 0))
    draw = ImageDraw.Draw(neon)
    for stripe in range(4):
        y = stripe * size // 4 + size // 16
        draw.rectangle([0, y, size, y + size // 12], fill=(0, 255, 200) if stripe % 2 == 0 else (255, 60, 200))
    neon.save(TEXTURES / "neon.png")


def material_json(name, fields):
    data = {
        "version": 1,
        "name": name,
        "baseColor": [1.0, 1.0, 1.0, 1.0],
        "metallic": 0.0,
        "roughness": 0.7,
        "emissive": [0.0, 0.0, 0.0],
        "emissiveStrength": 1.0,
        "normalScale": 1.0,
        "occlusionStrength": 1.0,
        "alphaMode": "OPAQUE",
        "alphaCutoff": 0.5,
        "doubleSided": False,
        "tiling": [1.0, 1.0],
        "offset": [0.0, 0.0],
        "textures": {},
    }
    data.update(fields)
    return json.dumps(data, indent=2) + "\n"


def mesh(shape, size, material, color=(0.8, 0.8, 0.8, 1.0)):
    return component("MeshPrimitiveComponent", shape=shape, sizeX=float(size[0]), sizeY=float(size[1]), sizeZ=float(size[2]),
                     colorR=color[0], colorG=color[1], colorB=color[2], colorA=color[3], materialPath=f"materials/{material}.material")


def main():
    MATERIALS.mkdir(parents=True, exist_ok=True)
    write_textures()
    for name, fields in LIBRARY.items():
        (MATERIALS / f"{name}.material").write_text(material_json(name, fields), encoding="utf-8", newline="")

    scene = Scene("materials", "Material Showroom")
    lighting(scene, sky=(0.62, 0.74, 0.92))
    scene.add("Main Camera", (0, 520, 1500), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=55.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=(-0.1305, 0, 0, 0.9914))
    scene.add("Ground", (0, 0, 0), [mesh("PLANE", (4000, 1, 2600), "Rock")])
    scene.add("Pond", (0, 4, 750), [mesh("PLANE", (2600, 1, 700), "Water")])
    scene.add("Gold Ball", (-1000, 150, -200), [mesh("SPHERE", (300, 300, 300), "Gold")])
    scene.add("Lava Ball", (-500, 150, -200), [mesh("SPHERE", (300, 300, 300), "Lava")])
    scene.add("Glass Ball", (0, 150, -200), [mesh("SPHERE", (300, 300, 300), "Glass")])
    scene.add("Rock Block", (500, 150, -200), [mesh("BOX", (300, 300, 300), "Rock")])
    scene.add("Wood Block", (1000, 150, -200), [mesh("BOX", (300, 300, 300), "Wood")])
    scene.add("Brick Block", (-500, 150, -700), [mesh("BOX", (300, 300, 300), "Brick")])
    scene.add("Checker Ball", (0, 150, -700), [mesh("SPHERE", (300, 300, 300), "Checker")])
    scene.add("Neon Block", (500, 150, -700), [mesh("BOX", (300, 300, 300), "Neon")])
    scene.add("Plain Ball", (0, 100, 200), [mesh("SPHERE", (200, 200, 200), "Rock", (0.9, 0.2, 0.2, 1.0))])
    # The plain ball names the Rock material too; clear it so it shows the older, material-free look next to the others
    scene.entities[-1]["components"][0]["data"].pop("materialPath")
    scene.add("Help Text", (0, 0, 0), [text(
        "Select a ball or block and open Details: Material shows its .material file, whose values and textures you can change. "
        "The red ball has no material.", "BOTTOM_LEFT", 16, 24, 1.0, (0.85, 0.9, 1.0))])
    scene.write(SAMPLES / "materials-3d.json")
    print(f"{len(LIBRARY)} materials, materials-3d.json: {len(scene.entities)} entities")


if __name__ == "__main__":
    main()
