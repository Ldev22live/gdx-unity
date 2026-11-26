"""Export the approved CC BY 3.0 samples from the repository root with Blender."""

from pathlib import Path
import sys

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
from convert_opengameart import assign_material, skin_material

SOURCE = Path("asset-sources/opengameart").resolve()
OUTPUT = Path("build/cc-by").resolve()
ZOMBIE_CLIPS = {"idle", "walk", "run", "attack1_r", "hurt", "dead1"}


def export_model(name):
    bpy.ops.export_scene.gltf(
        filepath=str(OUTPUT / (name + ".glb")),
        export_format="GLB",
        export_animation_mode="ACTIONS",
        export_def_bones=True,
        export_tangents=True,
    )


def export_warrior():
    source = SOURCE / "warrior-low-poly" / "guerrero_low_poly"
    bpy.ops.wm.open_mainfile(filepath=str(source / "MANIQUI_lowpoly.blend"), use_scripts=False, load_ui=False)
    warrior = bpy.data.objects["player"]
    for obj in list(bpy.context.scene.objects):
        if obj != warrior:
            bpy.data.objects.remove(obj, do_unlink=True)
    # The legacy project stores the UVs but has no layer marked for rendering.
    warrior.data.uv_layers.active_index = 0
    warrior.data.uv_layers[0].active_render = True
    color = bpy.data.images.load(str(source / "maniqui_txt.jpg"))
    normal = bpy.data.images.load(str(source / "maniqui_normal512.jpg"))
    assign_material(warrior.name, skin_material("Warrior", color, normal))
    export_model("warrior")


def export_zombie():
    source = SOURCE / "thin-zombie"
    bpy.ops.wm.open_mainfile(filepath=str(source / "new_thin_zombie.blend"), use_scripts=False, load_ui=False)
    for obj in list(bpy.context.scene.objects):
        if obj.type not in {"MESH", "ARMATURE"}:
            bpy.data.objects.remove(obj, do_unlink=True)
    for action in list(bpy.data.actions):
        if action.name not in ZOMBIE_CLIPS:
            bpy.data.actions.remove(action, do_unlink=True)
    color = bpy.data.images.load(str(source / "new_thin_zombie.png"))
    assign_material("new_thin_zombie", skin_material("Thin Zombie", color))
    export_model("thin-zombie")


def export_sci_fi_prop(name, mesh, texture):
    source = SOURCE / "horror-sci-fi-map-kit" / "sci_map_obj"
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.wm.obj_import(filepath=str(source / mesh))
    color = bpy.data.images.load(str(source / "textures" / texture))
    material = skin_material(name, color)
    for obj in bpy.context.scene.objects:
        if obj.type == "MESH":
            assign_material(obj.name, material)
    export_model(name)


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    export_warrior()
    export_zombie()
    export_sci_fi_prop("sci-fi-cover", "others/cover.obj", "cover.png")
    export_sci_fi_prop("sci-fi-room", "room/room.obj", "room.png")
