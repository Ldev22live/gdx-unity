"""Run inside Blender in background mode. Export one named static mesh without saving the .blend."""
import argparse
import hashlib
import os
from pathlib import Path
import sys
import uuid

import bpy


def material_issues(obj):
    issues = []
    for slot in obj.material_slots:
        material = slot.material
        if not material or not material.node_tree:
            continue
        nodes = material.node_tree.nodes
        outputs = [n for n in nodes if n.type == "OUTPUT_MATERIAL" and n.is_active_output]
        shaders = [n for n in nodes if n.type == "BSDF_PRINCIPLED"]
        simple = (len(outputs) == len(shaders) == 1
                  and len(outputs[0].inputs["Surface"].links) == 1
                  and outputs[0].inputs["Surface"].links[0].from_node == shaders[0]
                  and not any(s.is_linked for s in shaders[0].inputs)
                  and not any(s.is_linked for s in outputs[0].inputs if s.name != "Surface"))
        if not simple:
            issues.append(f"{material.name}: {', '.join(sorted({n.type for n in nodes}))}")
    return issues


def convert(object_name, destination, geometry_only):
    source = Path(bpy.data.filepath)
    if source.suffix.lower() != ".blend" or not source.is_file():
        raise ValueError("Open a saved .blend source before running the converter")
    destination = destination.absolute()
    if destination.suffix.lower() != ".glb" or destination.exists():
        raise ValueError(f"Choose a new .glb file: {destination}")
    obj = bpy.context.scene.objects.get(object_name)
    if obj is None or obj.type != "MESH":
        raise ValueError(f"Expected a mesh object in the active scene: {object_name}")
    if obj.find_armature() or obj.data.shape_keys or obj.animation_data or obj.data.animation_data:
        raise ValueError(f"Rigged, animated and shape-key meshes are unsupported: {obj.name}")
    if obj.library or obj.data.library or obj.constraints or obj.parent or any(m.type == "NODES" for m in obj.modifiers):
        raise ValueError(f"Linked, parented, constrained and geometry-node objects are unsupported: {obj.name}")
    issues = material_issues(obj)
    if issues and not geometry_only:
        raise ValueError("Unsupported material graph: " + "; ".join(issues)
                         + ". Bake the material separately or explicitly pass --geometry-only.")
    if geometry_only:
        print("Geometry only; omitting material slots:", [s.name for s in obj.material_slots])

    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    graph = bpy.context.evaluated_depsgraph_get()
    mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(graph), depsgraph=graph)
    if not mesh.polygons:
        raise ValueError(f"No faces to export: {obj.name}")
    derived = bpy.data.objects.new(obj.name + "-static", mesh)
    bpy.context.scene.collection.objects.link(derived)
    derived.matrix_world = obj.matrix_world.copy()
    derived["sourceBlend"] = source.name
    derived["sourceObject"] = obj.name
    derived["sourceSha256"] = digest
    derived["geometryOnly"] = geometry_only
    bpy.ops.object.select_all(action="DESELECT")
    derived.select_set(True)
    bpy.context.view_layer.objects.active = derived
    destination.parent.mkdir(parents=True, exist_ok=True)
    staged = destination.with_name(f".{uuid.uuid4().hex}.glb")
    with staged.open("xb"):
        pass
    try:
        result = bpy.ops.export_scene.gltf(filepath=str(staged), export_format="GLB", use_selection=True,
                                           export_animations=False, export_extras=True,
                                           export_materials="NONE" if geometry_only else "EXPORT")
        if "FINISHED" not in result or not staged.is_file():
            raise RuntimeError(f"glTF exporter did not finish: {result}")
        if hashlib.sha256(source.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Source changed during conversion")
        os.link(staged, destination)  # Publish only the finished file, failing if the name was taken.
    finally:
        staged.unlink(missing_ok=True)
    print(f"Exported {obj.name} to {destination}; source SHA-256 {digest}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--object", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--geometry-only", action="store_true")
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    convert(args.object, args.output, args.geometry_only)

