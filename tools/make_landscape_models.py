#!/usr/bin/env python3
"""Generates the landscape sample models and scene for Forgeroom.

Writes glTF 2.0 files (with their buffers embedded, so each model is one file) to assets/models/landscape/ and the sample
scene assets/samples/landscape-3d.json that places them. Everything is deterministic: running the script again gives the
same files. Pure Python, no dependencies.

Usage (from the repository root):  python tools/make_landscape_models.py

Units: the models are in metres, as glTF requires; the scene places them at 100 units per metre (ModelComponent's default
import scale), like every other 3D sample.
"""
import base64
import json
import math
import os
import random
import struct
import uuid

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
MODELS = os.path.join(ROOT, "assets", "models", "landscape")
SAMPLE = os.path.join(ROOT, "assets", "samples", "landscape-3d.json")
UNITS_PER_METRE = 100.0

# ---- Terrain shape: one function used both to build the mesh and to stand things on it ---------------------------------

TERRAIN_SIZE = 60.0
TERRAIN_CELLS = 96
LAKE_CENTRE = (-9.0, 7.0)
LAKE_RADIUS = 7.5
LAKE_LEVEL = -1.1
EDGE_FADE = 9.0


def _hash(ix, iz, seed):
    n = (ix * 374761393 + iz * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def _smooth(t):
    return t * t * (3 - 2 * t)


def value_noise(x, z, seed):
    ix, iz = math.floor(x), math.floor(z)
    fx, fz = _smooth(x - ix), _smooth(z - iz)
    a, b = _hash(ix, iz, seed), _hash(ix + 1, iz, seed)
    c, d = _hash(ix, iz + 1, seed), _hash(ix + 1, iz + 1, seed)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz


def fbm(x, z, seed, octaves=4):
    total, amplitude, frequency, norm = 0.0, 1.0, 1.0, 0.0
    for _ in range(octaves):
        total += value_noise(x * frequency, z * frequency, seed) * amplitude
        norm += amplitude
        amplitude *= 0.5
        frequency *= 2.0
    return total / norm


def terrain_height(x, z):
    """Height in metres at (x, z) metres from the terrain's centre."""
    base = (fbm(x * 0.045, z * 0.045, 7) - 0.5) * 9.0
    ridge = max(0.0, -z - 8.0) * 0.16  # the back of the valley rises into hills
    detail = (fbm(x * 0.2, z * 0.2, 19, 2) - 0.5) * 0.9
    height = base + ridge + detail + 1.0
    lake_distance = math.hypot(x - LAKE_CENTRE[0], z - LAKE_CENTRE[1])
    if lake_distance < LAKE_RADIUS * 1.9:
        bowl = 1.0 - _smooth(min(1.0, lake_distance / (LAKE_RADIUS * 1.9)))
        height = height * (1 - bowl) + (LAKE_LEVEL - 1.6) * bowl
    edge = min(TERRAIN_SIZE / 2 - abs(x), TERRAIN_SIZE / 2 - abs(z))
    if edge < EDGE_FADE:
        fade = _smooth(max(0.0, edge) / EDGE_FADE)
        height = height * fade + (-3.0) * (1 - fade)
    return height


def terrain_normal(x, z, step=0.2):
    dx = terrain_height(x + step, z) - terrain_height(x - step, z)
    dz = terrain_height(x, z + step) - terrain_height(x, z - step)
    return normalise((-dx, 2 * step, -dz))


# ---- Small vector helpers -------------------------------------------------------------------------------------------------

def normalise(v):
    length = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / length for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(color, amount):
    return tuple(min(1.0, max(0.0, c * amount)) for c in color)


# ---- Mesh builder -----------------------------------------------------------------------------------------------------------

class Mesh:
    """Vertices with position, normal and colour, and triangles. Counter-clockwise triangles face outwards."""

    def __init__(self):
        self.positions, self.normals, self.colors, self.indices = [], [], [], []

    def vertex(self, position, color, normal=None):
        self.positions.append(tuple(position))
        self.colors.append(tuple(color))
        self.normals.append(normal if normal is not None else (0.0, 1.0, 0.0))
        return len(self.positions) - 1

    def triangle(self, a, b, c):
        self.indices.extend((a, b, c))

    def smooth_normals(self, first=0):
        """Averages the face normals around each vertex from `first` on (area weighted)."""
        sums = {i: [0.0, 0.0, 0.0] for i in range(first, len(self.positions))}
        for t in range(0, len(self.indices), 3):
            a, b, c = self.indices[t:t + 3]
            if a < first:
                continue
            n = cross(sub(self.positions[b], self.positions[a]), sub(self.positions[c], self.positions[a]))
            for i in (a, b, c):
                for k in range(3):
                    sums[i][k] += n[k]
        for i, n in sums.items():
            self.normals[i] = normalise(n)

    # Shapes ---------------------------------------------------------------------------------------------------------------

    def box(self, centre, size, color):
        cx, cy, cz = centre
        hx, hy, hz = size[0] / 2, size[1] / 2, size[2] / 2
        faces = [((1, 0, 0), [(1, -1, -1), (1, 1, -1), (1, 1, 1), (1, -1, 1)]),
                 ((-1, 0, 0), [(-1, -1, 1), (-1, 1, 1), (-1, 1, -1), (-1, -1, -1)]),
                 ((0, 1, 0), [(-1, 1, -1), (-1, 1, 1), (1, 1, 1), (1, 1, -1)]),
                 ((0, -1, 0), [(-1, -1, 1), (-1, -1, -1), (1, -1, -1), (1, -1, 1)]),
                 ((0, 0, 1), [(-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]),
                 ((0, 0, -1), [(1, -1, -1), (-1, -1, -1), (-1, 1, -1), (1, 1, -1)])]
        for normal, corners in faces:
            ids = [self.vertex((cx + sx * hx, cy + sy * hy, cz + sz * hz), color, normal) for sx, sy, sz in corners]
            self.triangle(ids[0], ids[1], ids[2])
            self.triangle(ids[0], ids[2], ids[3])

    def cylinder(self, base, radius, height, color, segments=10, top_radius=None):
        """An upright (possibly tapering) cylinder, or a cone with top_radius 0, with a flat base."""
        top_radius = radius if top_radius is None else top_radius
        first = len(self.positions)
        bottom, top = [], []
        for s in range(segments):
            a = 2 * math.pi * s / segments
            bottom.append(self.vertex((base[0] + radius * math.cos(a), base[1], base[2] + radius * math.sin(a)), color))
            top.append(self.vertex((base[0] + top_radius * math.cos(a), base[1] + height, base[2] + top_radius * math.sin(a)), color))
        for s in range(segments):
            n = (s + 1) % segments
            self.triangle(bottom[s], top[s], top[n])
            self.triangle(bottom[s], top[n], bottom[n])
        self.smooth_normals(first)
        # Base cap with its own flat normals
        centre = self.vertex(base, color, (0.0, -1.0, 0.0))
        ring = [self.vertex(self.positions[b], color, (0.0, -1.0, 0.0)) for b in bottom]
        for s in range(segments):
            self.triangle(centre, ring[s], ring[(s + 1) % segments])

    def blob(self, centre, radius, color, seed, squash=1.0, roughness=0.18, detail=2, color_noise=0.12):
        """A lumpy sphere (a subdivided icosahedron with its points pushed in and out), for canopies and rocks."""
        rng = random.Random(seed)
        t = (1 + 5 ** 0.5) / 2
        points = [(-1, t, 0), (1, t, 0), (-1, -t, 0), (1, -t, 0), (0, -1, t), (0, 1, t), (0, -1, -t), (0, 1, -t),
                  (t, 0, -1), (t, 0, 1), (-t, 0, -1), (-t, 0, 1)]
        faces = [(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6),
                 (7, 1, 8), (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5), (2, 4, 11), (6, 2, 10),
                 (8, 6, 7), (9, 8, 1)]
        points = [normalise(p) for p in points]
        cache = {}

        def middle(a, b):
            key = (min(a, b), max(a, b))
            if key not in cache:
                points.append(normalise(tuple((points[a][i] + points[b][i]) / 2 for i in range(3))))
                cache[key] = len(points) - 1
            return cache[key]

        for _ in range(detail):
            faces = [f for a, b, c in faces for f in ((a, middle(a, b), middle(c, a)), (b, middle(b, c), middle(a, b)),
                                                      (c, middle(c, a), middle(b, c)), (middle(a, b), middle(b, c), middle(c, a)))]
        # One displacement per direction, so the surface stays closed
        radii = [1 + (rng.random() - 0.5) * 2 * roughness for _ in points]
        first = len(self.positions)
        ids = []
        for p, r in zip(points, radii):
            shade_amount = 1 + (rng.random() - 0.5) * 2 * color_noise
            ids.append(self.vertex((centre[0] + p[0] * r * radius, centre[1] + p[1] * r * radius * squash, centre[2] + p[2] * r * radius),
                                   shade(color, shade_amount)))
        for a, b, c in faces:
            self.triangle(ids[a], ids[b], ids[c])
        self.smooth_normals(first)


# ---- glTF writer ---------------------------------------------------------------------------------------------------------------

def write_gltf(path, name, mesh, alpha=None, roughness=0.95):
    buffer = bytearray()
    views, accessors = [], []

    def add(data_bytes, target, component, count, kind, minmax=None):
        while len(buffer) % 4:
            buffer.append(0)
        views.append({"buffer": 0, "byteOffset": len(buffer), "byteLength": len(data_bytes), "target": target})
        buffer.extend(data_bytes)
        accessor = {"bufferView": len(views) - 1, "componentType": component, "count": count, "type": kind}
        if minmax:
            accessor["min"], accessor["max"] = minmax
        accessors.append(accessor)
        return len(accessors) - 1

    count = len(mesh.positions)
    lows = [min(p[i] for p in mesh.positions) for i in range(3)]
    highs = [max(p[i] for p in mesh.positions) for i in range(3)]
    position = add(b"".join(struct.pack("<3f", *p) for p in mesh.positions), 34962, 5126, count, "VEC3", ([*lows], [*highs]))
    normal = add(b"".join(struct.pack("<3f", *n) for n in mesh.normals), 34962, 5126, count, "VEC3")
    color = add(b"".join(struct.pack("<4f", *c, 1.0) for c in mesh.colors), 34962, 5126, count, "VEC4")
    if count < 65536:
        indices = add(struct.pack("<%dH" % len(mesh.indices), *mesh.indices), 34963, 5123, len(mesh.indices), "SCALAR")
    else:
        indices = add(struct.pack("<%dI" % len(mesh.indices), *mesh.indices), 34963, 5125, len(mesh.indices), "SCALAR")
    material = {"name": name, "doubleSided": False,
                "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1 if alpha is None else alpha], "metallicFactor": 0.0,
                                         "roughnessFactor": roughness}}
    if alpha is not None:
        material["alphaMode"] = "BLEND"
        material["doubleSided"] = True
    document = {
        "asset": {"version": "2.0", "generator": "Forgeroom landscape generator (tools/make_landscape_models.py)"},
        "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"name": name, "mesh": 0}],
        "materials": [material],
        "meshes": [{"name": name, "primitives": [{"attributes": {"POSITION": position, "NORMAL": normal, "COLOR_0": color},
                                                   "indices": indices, "material": 0}]}],
        "accessors": accessors, "bufferViews": views,
        "buffers": [{"byteLength": len(buffer), "uri": "data:application/octet-stream;base64," + base64.b64encode(bytes(buffer)).decode("ascii")}],
    }
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(document, handle, indent=1)
        handle.write("\n")
    print("wrote %-22s %6d vertices %6d triangles %7d bytes" % (os.path.basename(path), count, len(mesh.indices) // 3, os.path.getsize(path)))


# ---- The models --------------------------------------------------------------------------------------------------------------------

GRASS = (0.33, 0.55, 0.22)
DARK_GRASS = (0.20, 0.40, 0.17)
SAND = (0.78, 0.70, 0.48)
ROCK = (0.46, 0.44, 0.42)
SNOW = (0.95, 0.96, 0.98)
BARK = (0.36, 0.24, 0.14)


def make_terrain():
    mesh = Mesh()
    side = TERRAIN_CELLS + 1
    step = TERRAIN_SIZE / TERRAIN_CELLS
    for j in range(side):
        for i in range(side):
            x, z = -TERRAIN_SIZE / 2 + i * step, -TERRAIN_SIZE / 2 + j * step
            h = terrain_height(x, z)
            n = terrain_normal(x, z)
            slope = 1 - n[1]
            variation = 1 + (value_noise(x * 0.9, z * 0.9, 3) - 0.5) * 0.22
            if h < LAKE_LEVEL + 0.5:
                color = mix(SAND, GRASS, max(0.0, (h - LAKE_LEVEL) / 0.5))
            elif h > 4.2:
                color = mix(GRASS, DARK_GRASS, min(1.0, (h - 4.2) / 2.5))
            else:
                color = mix(GRASS, DARK_GRASS, max(0.0, min(1.0, (h - 1.5) / 4.0)) * 0.7)
            if slope > 0.12:
                color = mix(color, ROCK, min(1.0, (slope - 0.12) / 0.2))
            mesh.vertex((x, h, z), shade(color, variation), n)
    for j in range(TERRAIN_CELLS):
        for i in range(TERRAIN_CELLS):
            a, b = j * side + i, j * side + i + 1
            c, d = (j + 1) * side + i, (j + 1) * side + i + 1
            mesh.triangle(a, c, b)
            mesh.triangle(b, c, d)
    return mesh


def make_mountain():
    mesh = Mesh()
    rings, segments, radius, peak = 40, 72, 22.0, 26.0
    mesh.vertex((0, peak, 0), SNOW)
    for r in range(1, rings + 1):
        t = r / rings
        for s in range(segments):
            a = 2 * math.pi * s / segments
            ridge = 1 + (fbm(math.cos(a) * 2.2 + t * 3, math.sin(a) * 2.2 + t * 3, 41, 3) - 0.5) * 0.9
            rr = radius * t * (1 + (fbm(math.cos(a) * 1.3, math.sin(a) * 1.3, 5, 2) - 0.5) * 0.5)
            height = peak * (1 - t) ** 1.35 * ridge
            if t > 0.9:
                height *= max(0.0, (1 - t) / 0.1)  # the foot melts into the ground
            y = height - 3.0
            snow_line = 0.62 * peak
            if height > snow_line:
                color = mix(ROCK, SNOW, min(1.0, (height - snow_line) / 3.0))
            elif height > 0.28 * peak:
                color = shade(ROCK, 0.9 + (fbm(rr, a * 5, 8) - 0.5) * 0.3)
            else:
                color = mix(DARK_GRASS, ROCK, height / (0.28 * peak))
            mesh.vertex((rr * math.cos(a), y, rr * math.sin(a)), color)
    for s in range(segments):
        mesh.triangle(0, 1 + (s + 1) % segments, 1 + s)
    for r in range(1, rings):
        for s in range(segments):
            n = (s + 1) % segments
            a, b = 1 + (r - 1) * segments + s, 1 + (r - 1) * segments + n
            c, d = 1 + r * segments + s, 1 + r * segments + n
            mesh.triangle(a, b, c)
            mesh.triangle(b, d, c)
    mesh.smooth_normals()
    return mesh


def make_pine():
    mesh = Mesh()
    mesh.cylinder((0, 0, 0), 0.22, 1.6, BARK, 8, 0.16)
    for level in range(5):
        y = 1.1 + level * 1.0
        radius = 1.55 - level * 0.27
        mesh.cylinder((0, y, 0), radius, 1.9 - level * 0.12, shade(DARK_GRASS, 0.9 + level * 0.07), 11, 0.0)
    return mesh


def make_oak():
    mesh = Mesh()
    mesh.cylinder((0, 0, 0), 0.3, 2.2, BARK, 9, 0.2)
    mesh.blob((0, 3.6, 0), 1.9, GRASS, 11, 0.85, 0.16)
    mesh.blob((1.2, 3.1, 0.4), 1.25, shade(GRASS, 1.1), 12, 0.9, 0.18)
    mesh.blob((-1.1, 3.3, -0.5), 1.35, shade(GRASS, 0.92), 13, 0.9, 0.18)
    mesh.blob((0.1, 4.4, -0.2), 1.1, shade(GRASS, 1.15), 14, 0.9, 0.18)
    return mesh


def make_rock():
    mesh = Mesh()
    mesh.blob((0, 0.55, 0), 0.9, ROCK, 21, 0.62, 0.28, 2, 0.14)
    mesh.blob((0.95, 0.3, 0.35), 0.5, shade(ROCK, 1.1), 22, 0.65, 0.3, 2, 0.14)
    return mesh


def make_bush():
    mesh = Mesh()
    mesh.blob((0, 0.4, 0), 0.62, shade(GRASS, 0.85), 31, 0.8, 0.2, 2, 0.18)
    mesh.blob((0.5, 0.3, 0.2), 0.42, shade(GRASS, 1.0), 32, 0.8, 0.2, 2, 0.18)
    mesh.blob((-0.4, 0.3, -0.25), 0.4, shade(GRASS, 1.12), 33, 0.8, 0.2, 2, 0.18)
    return mesh


def make_cabin():
    mesh = Mesh()
    wall, roof, trim = (0.55, 0.38, 0.22), (0.50, 0.17, 0.12), (0.30, 0.20, 0.12)
    mesh.box((0, 1.1, 0), (4.2, 2.2, 3.2), wall)
    # Gabled roof: a triangular prism with a little overhang
    hw, hd, base, top = 2.45, 1.95, 2.2, 3.6
    r = [(-hw, base, -hd), (hw, base, -hd), (hw, base, hd), (-hw, base, hd), (-hw, top, 0), (hw, top, 0)]
    slope_front = normalise(cross(sub(r[5], r[2]), sub(r[3], r[2])))
    slope_back = normalise(cross(sub(r[4], r[0]), sub(r[5], r[0])))

    def face(points, normal):
        ids = [mesh.vertex(p, roof, normal) for p in points]
        mesh.triangle(ids[0], ids[1], ids[2])
        if len(ids) == 4:
            mesh.triangle(ids[0], ids[2], ids[3])

    face([r[3], r[2], r[5], r[4]], slope_front)
    face([r[0], r[4], r[5], r[1]], slope_back)
    gable_left = [mesh.vertex(p, wall, (-1, 0, 0)) for p in (r[0], r[3], r[4])]
    mesh.triangle(gable_left[0], gable_left[1], gable_left[2])
    gable_right = [mesh.vertex(p, wall, (1, 0, 0)) for p in (r[1], r[5], r[2])]
    mesh.triangle(gable_right[0], gable_right[1], gable_right[2])
    mesh.box((0.4, 0.8, 1.62), (0.9, 1.6, 0.1), trim)            # door
    mesh.box((-1.3, 1.3, 1.62), (0.7, 0.6, 0.08), (0.62, 0.78, 0.9))  # windows
    mesh.box((1.4, 1.3, 1.62), (0.7, 0.6, 0.08), (0.62, 0.78, 0.9))
    mesh.box((1.3, 3.5, -0.6), (0.6, 1.3, 0.6), (0.45, 0.43, 0.42))  # chimney
    return mesh


def make_lake():
    mesh = Mesh()
    segments = 40
    mesh.vertex((0, 0, 0), (0.18, 0.45, 0.62), (0, 1, 0))
    for s in range(segments):
        a = 2 * math.pi * s / segments
        wobble = 1 + (value_noise(math.cos(a) * 2, math.sin(a) * 2, 77) - 0.5) * 0.12
        mesh.vertex((math.cos(a) * LAKE_RADIUS * 1.2 * wobble, 0, math.sin(a) * LAKE_RADIUS * 1.2 * wobble), (0.20, 0.50, 0.66), (0, 1, 0))
    for s in range(segments):
        mesh.triangle(0, 1 + (s + 1) % segments, 1 + s)
    return mesh


# ---- The sample scene -------------------------------------------------------------------------------------------------------------

def entity(name, position, components, parent=None, rotation=(0.0, 0.0, 0.0, 1.0), scale=1.0):
    ident = str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/landscape/" + name))
    return {"id": ident, "name": name, "active": True, "parentId": parent,
            "transform": {"position": {"x": position[0], "y": position[1], "z": position[2]},
                          "rotation": {"x": rotation[0], "y": rotation[1], "z": rotation[2], "w": rotation[3]},
                          "scale": {"x": scale, "y": scale, "z": scale}},
            "components": components}


def model(path, scale=UNITS_PER_METRE):
    return {"type": "ModelComponent", "enabled": True,
            "data": {"model": path, "importScale": scale, "loop": True, "playOnStart": True, "speed": 1.0}}


def yaw(angle):
    return (0.0, math.sin(angle / 2), 0.0, math.cos(angle / 2))


def place(name, model_name, x, z, rng, lift=-0.05, spin=True, scale=(1.0, 1.0)):
    s = scale[0] + (scale[1] - scale[0]) * rng.random()
    y = terrain_height(x, z) + lift * s
    return entity(name, (x * UNITS_PER_METRE, y * UNITS_PER_METRE, z * UNITS_PER_METRE),
                  [model("models/landscape/%s.gltf" % model_name, UNITS_PER_METRE * s)],
                  rotation=yaw(rng.random() * 2 * math.pi) if spin else (0.0, 0.0, 0.0, 1.0))


def make_scene():
    hopper = json.load(open(os.path.join(ROOT, "assets", "samples", "hopper-3d.json"), encoding="utf-8"))
    template = {e["name"]: e for e in hopper["entities"]}
    sun = json.loads(json.dumps(template["Sun"]))
    sun["id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/landscape/Sun"))
    sun["transform"]["position"] = {"x": 0.0, "y": 4000.0, "z": 2000.0}
    for component in sun["components"]:
        if component["type"] == "DirectionalLightComponent":
            component["data"].update(directionX=-0.45, directionY=-0.8, directionZ=-0.4, intensity=1.15, castShadows=False)
    sky = json.loads(json.dumps(template["Sky Light"]))
    sky["id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/landscape/Sky Light"))
    for component in sky["components"]:
        if component["type"] == "SkyLightComponent":
            component["data"].update(skyR=0.60, skyG=0.76, skyB=0.95, groundR=0.30, groundG=0.34, groundB=0.26, intensity=0.8)
    camera = json.loads(json.dumps(template["Main Camera"]))
    camera["id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/landscape/Main Camera"))
    # The hopper's camera follows its player with a script; the landscape has no player, so keep the camera alone
    camera["components"] = [c for c in camera["components"] if c["type"] == "CameraComponent"]
    pitch = math.radians(-24)
    camera["transform"]["position"] = {"x": 300.0, "y": 2300.0, "z": 4400.0}
    camera["transform"]["rotation"] = {"x": math.sin(pitch / 2), "y": 0.0, "z": 0.0, "w": math.cos(pitch / 2)}
    for component in camera["components"]:
        if component["type"] == "CameraComponent":
            component["data"].update(fieldOfView=62.0, farPlane=60000.0)

    rng = random.Random(2026)
    entities = [sun, sky, camera]
    entities.append(entity("Terrain", (0, 0, 0), [model("models/landscape/terrain-hills.gltf")]))
    entities.append(entity("Lake", (LAKE_CENTRE[0] * 100, LAKE_LEVEL * 100, LAKE_CENTRE[1] * 100), [model("models/landscape/lake.gltf")]))
    for k, (x, z, s) in enumerate([(-38, -37, 0.85), (-20, -42, 0.6), (-2, -39, 1.0), (17, -43, 0.65), (36, -36, 0.9)]):
        entities.append(entity("Mountain %d" % (k + 1), (x * 100, 0, z * 100), [model("models/landscape/mountain.gltf", UNITS_PER_METRE * s)],
                               rotation=yaw(k * 1.3)))

    def free(x, z, margin=1.5):
        if math.hypot(x - LAKE_CENTRE[0], z - LAKE_CENTRE[1]) < LAKE_RADIUS * 1.2 + margin:
            return False
        if abs(x) > TERRAIN_SIZE / 2 - 5 or abs(z) > TERRAIN_SIZE / 2 - 5:
            return False
        return terrain_normal(x, z)[1] > 0.88

    # The cabin on a flat spot near the lake
    cabin_at = None
    for _ in range(4000):
        x, z = rng.uniform(-6, 18), rng.uniform(4, 22)
        if free(x, z, 3.0) and all(terrain_normal(x + dx, z + dz)[1] > 0.97 for dx in (-2.5, 2.5) for dz in (-2, 2)):
            cabin_at = (x, z)
            break
    if cabin_at is None:
        raise SystemExit("no flat spot for the cabin; adjust the terrain")
    entities.append(place("Cabin", "cabin", cabin_at[0], cabin_at[1], rng, -0.1, spin=False))
    taken = [cabin_at]

    def clear_of(x, z, distance):
        return all(math.hypot(x - tx, z - tz) > distance for tx, tz in taken)

    def scatter(prefix, kind, count, region, distance, **options):
        placed = 0
        for _ in range(40000):
            if placed >= count:
                break
            x, z = rng.uniform(region[0], region[1]), rng.uniform(region[2], region[3])
            if free(x, z) and clear_of(x, z, distance):
                placed += 1
                taken.append((x, z))
                entities.append(place("%s %d" % (prefix, placed), kind, x, z, rng, **options))
        return placed

    scatter("Pine", "pine-tree", 26, (-28, 28, -27, 0), 3.2, scale=(0.85, 1.3))
    scatter("Oak", "oak-tree", 11, (-27, 27, 2, 27), 5.5, scale=(0.85, 1.25))
    scatter("Rock", "rock", 12, (-27, 27, -26, 26), 2.5, lift=0.0, scale=(0.6, 1.5))
    scatter("Bush", "bush", 16, (-27, 27, -10, 26), 1.8, scale=(0.8, 1.3))
    return {"schemaVersion": 1, "name": "Landscape", "active": True, "entities": entities}


def main():
    os.makedirs(MODELS, exist_ok=True)
    write_gltf(os.path.join(MODELS, "terrain-hills.gltf"), "terrain-hills", make_terrain(), roughness=1.0)
    write_gltf(os.path.join(MODELS, "mountain.gltf"), "mountain", make_mountain(), roughness=1.0)
    write_gltf(os.path.join(MODELS, "pine-tree.gltf"), "pine-tree", make_pine())
    write_gltf(os.path.join(MODELS, "oak-tree.gltf"), "oak-tree", make_oak())
    write_gltf(os.path.join(MODELS, "rock.gltf"), "rock", make_rock(), roughness=1.0)
    write_gltf(os.path.join(MODELS, "bush.gltf"), "bush", make_bush())
    write_gltf(os.path.join(MODELS, "cabin.gltf"), "cabin", make_cabin())
    write_gltf(os.path.join(MODELS, "lake.gltf"), "lake", make_lake(), alpha=0.72, roughness=0.1)
    scene = make_scene()
    with open(SAMPLE, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(scene, handle, indent=2)
        handle.write("\n")
    counts = {}
    for e in scene["entities"]:
        counts[e["name"].rstrip("0123456789 ")] = counts.get(e["name"].rstrip("0123456789 "), 0) + 1
    print("wrote %s: %d entities %s" % (os.path.basename(SAMPLE), len(scene["entities"]), counts))


if __name__ == "__main__":
    main()
