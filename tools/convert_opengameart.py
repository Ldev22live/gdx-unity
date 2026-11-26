"""Export selected OpenGameArt Blender sources to staged, self-contained GLBs."""

import argparse
from pathlib import Path
import sys

import bpy
from mathutils import Vector


def skin_material(name, color, normal=None, transparent=False):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    shader = nodes.get("Principled BSDF")
    shader.inputs["Roughness"].default_value = 0.85
    image = nodes.new("ShaderNodeTexImage")
    image.image = color
    links.new(image.outputs["Color"], shader.inputs["Base Color"])
    if transparent:
        links.new(image.outputs["Alpha"], shader.inputs["Alpha"])
    if normal is not None:
        normal.colorspace_settings.name = "Non-Color"
        image_normal = nodes.new("ShaderNodeTexImage")
        image_normal.image = normal
        normal_map = nodes.new("ShaderNodeNormalMap")
        links.new(image_normal.outputs["Color"], normal_map.inputs["Color"])
        links.new(normal_map.outputs["Normal"], shader.inputs["Normal"])
    return material


def assign_material(name, material):
    mesh = bpy.data.objects[name].data
    mesh.materials.clear()
    mesh.materials.append(material)


def convert(source, output, kind, stone_color=None):
    bpy.ops.wm.open_mainfile(filepath=str(source), load_ui=False, use_scripts=False)
    if bpy.data.libraries:
        raise ValueError("Source contains linked Blender libraries")
    if kind == "bunny":
        keep = {"Idle", "Walk", "Jump", "Climb", "Investigate", "Talk"}
        for action in list(bpy.data.actions):
            if action.name not in keep:
                bpy.data.actions.remove(action, do_unlink=True)
    elif kind == "fox":
        fox = bpy.data.objects["Fox"]
        mirror = next(modifier for modifier in fox.modifiers if modifier.type == "MIRROR")
        with bpy.context.temp_override(object=fox, active_object=fox):
            bpy.ops.object.modifier_apply(modifier=mirror.name)
        color = bpy.data.images.load(str(source.with_name("fox_diffuse.png")), check_existing=True)
        normal = bpy.data.images.load(str(source.with_name("fox_normal.png")), check_existing=True)
        assign_material("Fox", skin_material("Fox fur", color, normal))
    elif kind == "rabbit":
        bpy.data.objects.remove(bpy.data.objects["Plane"], do_unlink=True)
        color = bpy.data.images["rabbot-skinn"]
        normal = bpy.data.images["rabbit-NORM"]
        assign_material("Rabbit", skin_material("Rabbit skin", color, normal))
        assign_material("Fur", skin_material("Rabbit fur", bpy.data.images["Fur-skin"], transparent=True))
        for action in list(bpy.data.actions):
            if action.name not in {"Running", "Jump", "Sitting.000"}:
                bpy.data.actions.remove(action, do_unlink=True)
    else:
        rock = bpy.data.objects["Rock"]
        for obj in list(bpy.context.scene.objects):
            if obj != rock:
                bpy.data.objects.remove(obj, do_unlink=True)
        depsgraph = bpy.context.evaluated_depsgraph_get()
        mesh = bpy.data.meshes.new_from_object(rock.evaluated_get(depsgraph))
        transform = rock.matrix_world.copy()
        mesh.transform(transform)
        low = Vector(tuple(min(v.co[i] for v in mesh.vertices) for i in range(3)))
        high = Vector(tuple(max(v.co[i] for v in mesh.vertices) for i in range(3)))
        center = (low + high) * 0.5
        for vertex in mesh.vertices:
            vertex.co -= center
        output_rock = bpy.data.objects.new("Boulder", mesh)
        bpy.context.scene.collection.objects.link(output_rock)
        bpy.data.objects.remove(rock, do_unlink=True)
        if stone_color is None:
            raise ValueError("The boulder needs an approved stone base-color image")
        bpy.ops.object.select_all(action="DESELECT")
        bpy.context.view_layer.objects.active = output_rock
        output_rock.select_set(True)
        bpy.ops.object.mode_set(mode="EDIT")
        bpy.ops.mesh.select_all(action="SELECT")
        bpy.ops.uv.smart_project(island_margin=0.02)
        bpy.ops.object.mode_set(mode="OBJECT")
        color = bpy.data.images.load(str(stone_color.resolve(strict=True)), check_existing=True)
        stone = skin_material("Weathered stone", color)
        assign_material(output_rock.name, stone)
    bpy.ops.export_scene.gltf(
        filepath=str(output), export_format="GLB", export_animation_mode="ACTIONS",
        export_force_sampling=True, export_def_bones=True, export_tangents=True,
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=["bunny", "fox", "rabbit", "boulder"])
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--stone-color", type=Path)
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    convert(args.source.resolve(strict=True), args.output.resolve(), args.kind, args.stone_color)
