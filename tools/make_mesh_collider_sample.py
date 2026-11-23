#!/usr/bin/env python3
"""Generates the mesh collider sample (TD-052): assets/models/physics-mesh-terrain.gltf and assets/samples/physics-mesh-3d.json.

The model is a heightfield in metres: flat ground, a ramp that climbs to a plateau, and a bowl. The scene gives it a Mesh
Collider 3D (the exact triangles) and drops balls, boxes and convex hull rocks onto it. Everything is deterministic and pure
Python; run it again to regenerate both files.

Usage (from the repository root):  python tools/make_mesh_collider_sample.py
"""
import base64
import json
import math
import os
import struct
import uuid

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
MODEL = os.path.join(ROOT, "assets", "models", "physics-mesh-terrain.gltf")
SAMPLE = os.path.join(ROOT, "assets", "samples", "physics-mesh-3d.json")

HALF_X = 8.0   # metres, the terrain spans -8..8 in x
HALF_Z = 5.0   # and -5..5 in z
CELL = 0.25
BOWL_CENTRE_X = 4.5
BOWL_RADIUS = 2.2
BOWL_DEPTH = 1.0
RAMP_FOOT_X = -6.0
RAMP_TOP_X = -2.0
PLATEAU_END_X = 0.0
DROP_END_X = 1.5
PLATEAU_HEIGHT = 1.0


def height(x, z):
    """Height of the ground in metres."""
    if x < RAMP_FOOT_X:
        h = 0.0
    elif x < RAMP_TOP_X:
        h = PLATEAU_HEIGHT * (x - RAMP_FOOT_X) / (RAMP_TOP_X - RAMP_FOOT_X)
    elif x < PLATEAU_END_X:
        h = PLATEAU_HEIGHT
    elif x < DROP_END_X:
        h = PLATEAU_HEIGHT * (1.0 - (x - PLATEAU_END_X) / (DROP_END_X - PLATEAU_END_X))
    else:
        h = 0.0
    r = math.hypot(x - BOWL_CENTRE_X, z)
    if r < BOWL_RADIUS:
        h -= BOWL_DEPTH * (1.0 - (r / BOWL_RADIUS) ** 2)
    return h


def build_model():
    nx = int(round(2 * HALF_X / CELL))
    nz = int(round(2 * HALF_Z / CELL))
    positions = []
    for j in range(nz + 1):
        for i in range(nx + 1):
            x = -HALF_X + i * CELL
            z = -HALF_Z + j * CELL
            positions.append((x, height(x, z), z))
    normals = []
    eps = 0.01
    for (x, _, z) in positions:
        dx = height(x + eps, z) - height(x - eps, z)
        dz = height(x, z + eps) - height(x, z - eps)
        n = (-dx / (2 * eps), 1.0, -dz / (2 * eps))
        length = math.sqrt(n[0] ** 2 + n[1] ** 2 + n[2] ** 2)
        normals.append((n[0] / length, n[1] / length, n[2] / length))
    indices = []
    for j in range(nz):
        for i in range(nx):
            a = j * (nx + 1) + i
            b = a + 1
            c = a + nx + 1
            d = c + 1
            # Counter-clockwise seen from above, so every triangle faces up
            indices += [a, c, b, b, c, d]
    position_bytes = b"".join(struct.pack("<3f", *p) for p in positions)
    normal_bytes = b"".join(struct.pack("<3f", *n) for n in normals)
    index_bytes = b"".join(struct.pack("<I", i) for i in indices)
    blob = position_bytes + normal_bytes + index_bytes
    lows = [min(p[k] for p in positions) for k in range(3)]
    highs = [max(p[k] for p in positions) for k in range(3)]
    return {
        "asset": {"version": "2.0", "generator": "Forgeroom mesh collider sample (tools/make_mesh_collider_sample.py)"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": "terrain", "mesh": 0}],
        "materials": [{"name": "ground", "doubleSided": True,
                       "pbrMetallicRoughness": {"baseColorFactor": [0.42, 0.55, 0.38, 1.0], "metallicFactor": 0.0, "roughnessFactor": 0.95}}],
        "meshes": [{"name": "terrain", "primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1}, "indices": 2, "material": 0}]}],
        "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": len(positions), "type": "VEC3", "min": lows, "max": highs},
            {"bufferView": 1, "componentType": 5126, "count": len(normals), "type": "VEC3"},
            {"bufferView": 2, "componentType": 5125, "count": len(indices), "type": "SCALAR"},
        ],
        "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": len(position_bytes), "target": 34962},
            {"buffer": 0, "byteOffset": len(position_bytes), "byteLength": len(normal_bytes), "target": 34962},
            {"buffer": 0, "byteOffset": len(position_bytes) + len(normal_bytes), "byteLength": len(index_bytes), "target": 34963},
        ],
        "buffers": [{"byteLength": len(blob), "uri": "data:application/octet-stream;base64," + base64.b64encode(blob).decode("ascii")}],
    }


NAMESPACE = uuid.UUID("a3b1c0de-52d4-4c1e-9d6b-0a7a3d2f4e52")
entities = []


def vec(x, y, z):
    return {"x": float(x), "y": float(y), "z": float(z)}


def entity(name, pos, comps, rot=(0.0, 0.0, 0.0, 1.0)):
    length = math.sqrt(sum(c * c for c in rot))
    rot = tuple(c / length for c in rot)
    entities.append({
        "id": str(uuid.uuid5(NAMESPACE, name)), "name": name, "active": True, "parentId": None,
        "transform": {"position": vec(*pos), "rotation": {"x": rot[0], "y": rot[1], "z": rot[2], "w": rot[3]}, "scale": vec(1, 1, 1)},
        "components": comps})


def comp(kind, data):
    return {"type": kind, "enabled": True, "data": data}


def mesh(shape, size, rgb):
    return comp("MeshPrimitiveComponent", {"shape": shape, "sizeX": float(size[0]), "sizeY": float(size[1]), "sizeZ": float(size[2]),
                                           "colorR": rgb[0], "colorG": rgb[1], "colorB": rgb[2], "colorA": 1.0})


def collider_offset():
    return {"offsetX": 0.0, "offsetY": 0.0, "offsetZ": 0.0, "trigger": False}


def body(mass, damping=0.2, friction=0.6, bounce=0.1):
    return comp("RigidBody3DComponent", {"motionType": "DYNAMIC", "mass": mass, "linearDamping": damping, "angularDamping": damping,
                                         "gravityFactor": 1.0, "friction": friction, "restitution": bounce, "layer": 0,
                                         "collisionMask": 65535})


def sphere_collider(radius):
    data = collider_offset()
    data["radius"] = float(radius)
    return comp("SphereCollider3DComponent", data)


def box_collider(size):
    data = collider_offset()
    data.update({"sizeX": float(size[0]), "sizeY": float(size[1]), "sizeZ": float(size[2])})
    return comp("BoxCollider3DComponent", data)


def build_scene():
    entity("Sun", (0, 800, 400), [comp("DirectionalLightComponent", {
        "directionX": -0.4, "directionY": -1.0, "directionZ": -0.6, "colorR": 1.0, "colorG": 0.96, "colorB": 0.88, "colorA": 1.0,
        "intensity": 1.0, "castShadows": True})])
    entity("Main Camera", (0, 520, 1100), [comp("CameraComponent", {
        "projection": "PERSPECTIVE", "orthographicHeight": 480.0, "fieldOfView": 60.0, "nearPlane": 1.0, "farPlane": 50000.0})],
        rot=(-0.17365, 0.0, 0.0, 0.98481))
    # The terrain is a static glTF model with a Mesh Collider 3D: the collider is the model's own triangles
    entity("Terrain", (0, 0, 0), [
        comp("ModelComponent", {"model": "models/physics-mesh-terrain.gltf", "importScale": 100.0, "loop": True, "playOnStart": True, "speed": 1.0}),
        comp("MeshCollider3DComponent", collider_offset()),
        comp("RigidBody3DComponent", {"motionType": "STATIC", "mass": 1.0, "linearDamping": 0.05, "angularDamping": 0.05,
                                      "gravityFactor": 1.0, "friction": 0.6, "restitution": 0.0, "layer": 0, "collisionMask": 65535}),
    ])
    # Balls start on the slope that leaves the plateau (surface height 100 * (1 - x / 150)) and roll down towards the bowl
    for i in range(4):
        x = 20 + i * 35
        surface = 100.0 * (1.0 - x / 150.0)
        entity("Ball %d" % (i + 1), (x, surface + 60 + i * 45, -80 + i * 55), [
            mesh("SPHERE", (40, 40, 40), (0.25 + 0.2 * i, 0.55, 1.0 - 0.2 * i)), sphere_collider(20), body(1.0, damping=0.5)])
    # Boxes dropped straight into the bowl
    for i in range(3):
        entity("Box %d" % (i + 1), (440 + i * 18, 330 + i * 120, -10 + i * 14), [
            mesh("BOX", (50, 50, 50), (0.95, 0.65 - 0.15 * i, 0.2)), box_collider((50, 50, 50)), body(2.0)])
    # Convex hull rocks: the hull is built from the mesh the entity shows
    for i in range(2):
        entity("Hull Rock %d" % (i + 1), (-330 + i * 120, 500 + i * 80, 60 - i * 120), [
            mesh("BOX", (70, 50, 60), (0.6, 0.6, 0.65)), comp("ConvexHullCollider3DComponent", collider_offset()), body(3.0)],
            rot=(0.2, 0.3, 0.1, 0.92))
    entity("Help Text", (0, 0, 0), [comp("TextComponent", {
        "text": "Mesh colliders: a glTF terrain with a ramp and a bowl as a Mesh Collider 3D; balls, boxes and convex hull rocks fall on it",
        "colorR": 0.8, "colorG": 0.85, "colorB": 0.95, "colorA": 1, "scale": 1.2, "anchor": "BOTTOM_LEFT", "offsetX": 16.0, "offsetY": 32.0})])
    return {"schemaVersion": 1, "name": "Physics Mesh Colliders 3D", "active": True, "entities": entities}


def write(path, data):
    with open(path, "w", encoding="utf8", newline="") as f:
        f.write(json.dumps(data, indent=2).replace("\n", "\r\n") + "\r\n")


if __name__ == "__main__":
    write(MODEL, build_model())
    write(SAMPLE, build_scene())
    print("wrote", MODEL, SAMPLE)
