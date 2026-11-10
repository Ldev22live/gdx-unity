#!/usr/bin/env python3
"""Generates four more landscape samples for Forgeroom: a desert canyon, a snowy alpine valley, a tropical island and an
autumn forest.

Builds on tools/make_landscape_models.py (the forest valley): it reuses that script's mesh builder, glTF writer and
helpers, and writes into the same folder, assets/models/landscape/, plus one scene per biome in assets/samples/.
Deterministic and dependency free. Run from the repository root:  python tools/make_landscape_biomes.py
"""
import json
import math
import os
import random
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_landscape_models as base  # noqa: E402
from make_landscape_models import (  # noqa: E402
    Mesh, UNITS_PER_METRE, fbm, mix, model, normalise, shade, value_noise, write_gltf, yaw,
)

MODELS = base.MODELS
SAMPLES = os.path.join(base.ROOT, "assets", "samples")
SIZE = 60.0
CELLS = 96
EDGE_FADE = 9.0


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


# ---- A biome: how the ground is shaped and coloured, and what grows on it ------------------------------------------------------

class Biome:
    def __init__(self, key, title, height, color, water=None, water_level=None, min_normal=0.88, edge_floor=-3.0):
        self.key, self.title, self.height_fn, self.color_fn = key, title, height, color
        self.water = water                  # (centre_x, centre_z, radius) of a pond to keep clear, or None
        self.water_level = water_level      # the height under which a point is under water, or None
        self.min_normal = min_normal
        self.edge_floor = edge_floor

    def height(self, x, z):
        h = self.height_fn(x, z)
        edge = min(SIZE / 2 - abs(x), SIZE / 2 - abs(z))
        if edge < EDGE_FADE:
            fade = smooth(max(0.0, edge) / EDGE_FADE)
            h = h * fade + self.edge_floor * (1 - fade)
        return h

    def normal(self, x, z, step=0.2):
        dx = self.height(x + step, z) - self.height(x - step, z)
        dz = self.height(x, z + step) - self.height(x, z - step)
        return normalise((-dx, 2 * step, -dz))

    def terrain_mesh(self):
        mesh = Mesh()
        side = CELLS + 1
        step = SIZE / CELLS
        for j in range(side):
            for i in range(side):
                x, z = -SIZE / 2 + i * step, -SIZE / 2 + j * step
                h = self.height(x, z)
                n = self.normal(x, z)
                mesh.vertex((x, h, z), self.color_fn(x, z, h, n), n)
        for j in range(CELLS):
            for i in range(CELLS):
                a, b = j * side + i, j * side + i + 1
                c, d = (j + 1) * side + i, (j + 1) * side + i + 1
                mesh.triangle(a, c, b)
                mesh.triangle(b, c, d)
        return mesh


def bowl(h, x, z, centre, radius, floor):
    """Pushes the ground into a basin around `centre`, for a lake or oasis."""
    d = math.hypot(x - centre[0], z - centre[1])
    if d < radius * 1.9:
        k = 1.0 - smooth(d / (radius * 1.9))
        return h * (1 - k) + floor * k
    return h


# Desert -----------------------------------------------------------------------------------------------------------------------------

OASIS = (-10.0, 9.0, 4.5)


def desert_height(x, z):
    dunes = (fbm(x * 0.06 + z * 0.02, z * 0.06, 21, 3) - 0.5) * 2.4
    ripples = abs(math.sin(x * 0.2 + fbm(x * 0.05, z * 0.05, 5) * 6)) * 0.3
    mask = fbm(x * 0.035 + 10, z * 0.035 - 4, 33, 3)
    mesa = smooth((mask - 0.56) / 0.07) * 8.0
    h = dunes + ripples + mesa + 0.8
    return bowl(h, x, z, (OASIS[0], OASIS[1]), OASIS[2], -0.9)


def desert_color(x, z, h, n):
    sand = mix((0.86, 0.72, 0.48), (0.80, 0.64, 0.40), value_noise(x * 0.7, z * 0.7, 3))
    if h > 2.5 or n[1] < 0.8:
        band = 0.5 + 0.5 * math.sin(h * 4.2 + value_noise(x * 0.3, z * 0.3, 8) * 2)
        rock = mix((0.74, 0.40, 0.24), (0.58, 0.27, 0.18), band)
        return mix(sand, rock, smooth((max(h - 2.5, (0.85 - n[1]) * 12)) / 1.5))
    return sand


# Alpine -----------------------------------------------------------------------------------------------------------------------------

ICE = (8.0, -4.0, 6.5)


def alpine_height(x, z):
    h = (fbm(x * 0.04, z * 0.04, 52, 4) - 0.5) * 17.0 + max(0.0, -z - 6) * 0.22 + 3.0
    return bowl(h, x, z, (ICE[0], ICE[1]), ICE[2], -1.2)


def alpine_color(x, z, h, n):
    snow = shade((0.94, 0.96, 0.99), 0.93 + value_noise(x * 0.8, z * 0.8, 12) * 0.1)
    color = snow
    if h < 0.6:
        color = mix((0.55, 0.62, 0.55), snow, smooth(h / 0.6))  # bare tundra near the ice
    if n[1] < 0.86:
        color = mix(color, (0.42, 0.40, 0.40), smooth((0.86 - n[1]) / 0.2))
    return color


# Island -----------------------------------------------------------------------------------------------------------------------------

def island_height(x, z):
    r = math.hypot(x * 1.0, z * 1.15) / 25.0
    noise = (fbm(x * 0.07, z * 0.07, 61, 3) - 0.5) * 1.4
    land = (1 - smooth(r)) * 6.0 + noise * (1 - smooth(r) * 0.6)
    return land - 0.7 if r < 1.05 else -3.0 + (1 - smooth((r - 1.05) / 0.6)) * 2.2 - 0.0


def island_color(x, z, h, n):
    if h < 0.25:
        return mix((0.80, 0.72, 0.52), (0.55, 0.50, 0.40), smooth(-h / 2.5))  # wet sand, then sea floor
    if h < 1.0:
        return mix((0.88, 0.80, 0.58), (0.30, 0.60, 0.25), smooth((h - 0.25) / 0.75))
    return shade(mix((0.28, 0.58, 0.22), (0.16, 0.42, 0.18), smooth((h - 1.0) / 3.5)), 0.92 + value_noise(x, z, 4) * 0.16)


# Autumn -----------------------------------------------------------------------------------------------------------------------------

POND = (9.0, -3.0, 7.0)


def autumn_height(x, z):
    h = (fbm(x * 0.05, z * 0.05, 71, 4) - 0.5) * 8.5 + max(0.0, -z - 10) * 0.15 + 1.0
    return bowl(h, x, z, (POND[0], POND[1]), POND[2], -2.2)


def autumn_color(x, z, h, n):
    leaves = mix((0.60, 0.42, 0.16), (0.46, 0.30, 0.12), value_noise(x * 0.6, z * 0.6, 14))
    grass = mix((0.50, 0.52, 0.18), leaves, smooth((h - 0.2) / 2.0))
    if h < -0.2:
        grass = mix((0.72, 0.62, 0.40), grass, smooth((h + 1.3) / 1.1))
    if n[1] < 0.84:
        grass = mix(grass, (0.45, 0.40, 0.36), smooth((0.84 - n[1]) / 0.2))
    return grass


# ---- Extra models --------------------------------------------------------------------------------------------------------------------

def disc(radius, color, segments=40, seed=77):
    mesh = Mesh()
    mesh.vertex((0, 0, 0), color, (0, 1, 0))
    for s in range(segments):
        a = 2 * math.pi * s / segments
        wobble = 1 + (value_noise(math.cos(a) * 2, math.sin(a) * 2, seed) - 0.5) * 0.12
        mesh.vertex((math.cos(a) * radius * wobble, 0, math.sin(a) * radius * wobble), color, (0, 1, 0))
    for s in range(segments):
        mesh.triangle(0, 1 + (s + 1) % segments, 1 + s)
    return mesh


def add_leaf(mesh, base_point, direction, length, width, droop, color):
    """A drooping palm frond: a flat, pointed blade drawn on both sides."""
    dx, dz = direction
    px, pz = -dz, dx
    b = base_point
    mid = (b[0] + dx * length * 0.5, b[1] + length * 0.18, b[2] + dz * length * 0.5)
    tip = (b[0] + dx * length, b[1] - droop, b[2] + dz * length)
    left = (mid[0] + px * width, mid[1], mid[2] + pz * width)
    right = (mid[0] - px * width, mid[1], mid[2] - pz * width)
    for tri in ((b, left, tip), (b, tip, right)):
        n = normalise(base.cross(base.sub(tri[1], tri[0]), base.sub(tri[2], tri[0])))
        up = [mesh.vertex(p, color, n) for p in tri]
        mesh.triangle(*up)
        flipped = (n[0] * -1, n[1] * -1, n[2] * -1)
        down = [mesh.vertex(p, shade(color, 0.8), flipped) for p in tri]
        mesh.triangle(down[0], down[2], down[1])


def make_palm():
    mesh = Mesh()
    trunk = (0.52, 0.38, 0.24)
    x = 0.0
    for k in range(6):
        mesh.cylinder((x, k * 0.95, 0), 0.26 - k * 0.025, 1.0, shade(trunk, 0.9 + (k % 2) * 0.15), 8, 0.24 - k * 0.025)
        x += 0.12
    top = (x, 5.7, 0.0)
    for k in range(8):
        a = 2 * math.pi * k / 8
        add_leaf(mesh, top, (math.cos(a), math.sin(a)), 2.9, 0.42, 1.0, shade((0.22, 0.55, 0.2), 0.9 + (k % 3) * 0.1))
    mesh.blob((top[0], top[1] - 0.15, top[2]), 0.32, (0.40, 0.28, 0.14), 5, 1.0, 0.1, 1)
    return mesh


def make_cactus():
    mesh = Mesh()
    green = (0.24, 0.50, 0.26)
    mesh.cylinder((0, 0, 0), 0.30, 3.3, green, 10, 0.26)
    mesh.blob((0, 3.3, 0), 0.27, shade(green, 1.05), 3, 1.0, 0.04, 1, 0.05)
    mesh.box((0.55, 1.5, 0), (0.8, 0.26, 0.26), shade(green, 0.95))
    mesh.cylinder((0.88, 1.38, 0), 0.14, 1.2, green, 8, 0.13)
    mesh.box((-0.5, 2.1, 0), (0.7, 0.26, 0.26), shade(green, 1.05))
    mesh.cylinder((-0.80, 1.98, 0), 0.14, 0.95, green, 8, 0.13)
    return mesh


def make_mesa():
    """A butte: one continuous rock column that flares at the foot, with colour bands (strata) and a flat top."""
    mesh = Mesh()
    rings, seg, height, radius = 24, 20, 9.0, 5.2
    bands = [(0.78, 0.46, 0.28), (0.66, 0.34, 0.21), (0.86, 0.60, 0.38), (0.60, 0.29, 0.19), (0.74, 0.42, 0.25)]
    grid = []
    for r in range(rings + 1):
        t = r / rings
        flare = 1 + 0.55 * (1 - t) ** 4
        row = []
        for s in range(seg):
            a = 2 * math.pi * s / seg
            wobble = 1 + (fbm(math.cos(a) * 1.6 + t * 2.0, math.sin(a) * 1.6 + t * 2.0, 9, 2) - 0.5) * 0.35
            rr = radius * flare * wobble * (1 - 0.1 * t)
            band = bands[int(t * 9 + value_noise(a * 2, t * 4, 6) * 0.6) % len(bands)]
            row.append(mesh.vertex((rr * math.cos(a), t * height, rr * math.sin(a)), shade(band, 0.94 + value_noise(s, r, 2) * 0.12)))
        grid.append(row)
    for r in range(rings):
        for s in range(seg):
            n = (s + 1) % seg
            mesh.triangle(grid[r][s], grid[r + 1][s], grid[r + 1][n])
            mesh.triangle(grid[r][s], grid[r + 1][n], grid[r][n])
    mesh.smooth_normals()
    # A flat sandy top, with its own upward normals so it lights evenly
    top = height
    centre = mesh.vertex((0, top, 0), (0.84, 0.70, 0.48), (0, 1, 0))
    ring_ids = [mesh.vertex(mesh.positions[v], (0.84, 0.70, 0.48), (0, 1, 0)) for v in grid[rings]]
    for s in range(seg):
        mesh.triangle(centre, ring_ids[(s + 1) % seg], ring_ids[s])
    return mesh


def square(half, color):
    """A flat square sheet facing up, for a sea."""
    mesh = Mesh()
    ids = [mesh.vertex((sx * half, 0, sz * half), color, (0, 1, 0)) for sx, sz in ((-1, -1), (-1, 1), (1, 1), (1, -1))]
    mesh.triangle(ids[0], ids[1], ids[2])
    mesh.triangle(ids[0], ids[2], ids[3])
    return mesh


def make_tuft(color, seed):
    mesh = Mesh()
    mesh.blob((0, 0.35, 0), 0.5, color, seed, 0.7, 0.3, 2, 0.15)
    mesh.blob((0.45, 0.25, 0.2), 0.34, shade(color, 1.1), seed + 1, 0.7, 0.3, 2, 0.15)
    return mesh


def make_snow_pine():
    mesh = Mesh()
    mesh.cylinder((0, 0, 0), 0.22, 1.6, base.BARK, 8, 0.16)
    for level in range(5):
        y = 1.1 + level * 1.0
        radius = 1.5 - level * 0.26
        green = shade((0.18, 0.36, 0.22), 0.9 + level * 0.05)
        mesh.cylinder((0, y, 0), radius, 1.9 - level * 0.12, mix(green, (0.95, 0.97, 1.0), 0.38), 11, 0.0)
    return mesh


def make_snow_rock():
    mesh = Mesh()
    mesh.blob((0, 0.55, 0), 0.9, (0.78, 0.80, 0.84), 41, 0.62, 0.28, 2, 0.12)
    mesh.blob((0.95, 0.3, 0.35), 0.5, (0.86, 0.88, 0.92), 42, 0.65, 0.3, 2, 0.1)
    return mesh


def make_colored_oak(color, seed):
    mesh = Mesh()
    mesh.cylinder((0, 0, 0), 0.3, 2.2, base.BARK, 9, 0.2)
    mesh.blob((0, 3.6, 0), 1.9, color, seed, 0.85, 0.16)
    mesh.blob((1.2, 3.1, 0.4), 1.25, shade(color, 1.1), seed + 1, 0.9, 0.18)
    mesh.blob((-1.1, 3.3, -0.5), 1.35, shade(color, 0.9), seed + 2, 0.9, 0.18)
    mesh.blob((0.1, 4.4, -0.2), 1.1, shade(color, 1.15), seed + 3, 0.9, 0.18)
    return mesh


def make_autumn_pine():
    mesh = Mesh()
    mesh.cylinder((0, 0, 0), 0.22, 1.6, base.BARK, 8, 0.16)
    for level in range(5):
        mesh.cylinder((0, 1.1 + level, 0), 1.55 - level * 0.27, 1.9 - level * 0.12, shade((0.30, 0.38, 0.14), 0.9 + level * 0.07), 11, 0.0)
    return mesh


def make_hut():
    mesh = Mesh()
    wall, thatch = (0.72, 0.56, 0.34), (0.78, 0.66, 0.30)
    mesh.box((0, 0.9, 0), (3.0, 1.8, 3.0), wall)
    apex, h = (0, 3.4, 0), 1.95
    corners = [(-h * 0.95, 1.7, -h * 0.95), (h * 0.95, 1.7, -h * 0.95), (h * 0.95, 1.7, h * 0.95), (-h * 0.95, 1.7, h * 0.95)]
    for k in range(4):
        a, b = corners[k], corners[(k + 1) % 4]
        n = normalise(base.cross(base.sub(apex, a), base.sub(b, a)))
        ids = [mesh.vertex(p, thatch, n) for p in (a, apex, b)]
        mesh.triangle(*ids)
    mesh.box((0, 0.7, 1.52), (0.8, 1.4, 0.08), base.BARK)
    return mesh


def write_extra_models():
    # Desert
    write_gltf(os.path.join(MODELS, "mesa.gltf"), "mesa", make_mesa(), roughness=1.0)
    write_gltf(os.path.join(MODELS, "cactus.gltf"), "cactus", make_cactus())
    write_gltf(os.path.join(MODELS, "palm-tree.gltf"), "palm-tree", make_palm())
    write_gltf(os.path.join(MODELS, "dry-bush.gltf"), "dry-bush", make_tuft((0.58, 0.48, 0.26), 51))
    desert_rock = Mesh()
    desert_rock.blob((0, 0.55, 0), 0.9, (0.64, 0.38, 0.28), 61, 0.62, 0.28, 2, 0.14)
    desert_rock.blob((0.95, 0.3, 0.35), 0.5, (0.70, 0.43, 0.31), 62, 0.65, 0.3, 2, 0.14)
    write_gltf(os.path.join(MODELS, "desert-rock.gltf"), "desert-rock", desert_rock, roughness=1.0)
    write_gltf(os.path.join(MODELS, "oasis.gltf"), "oasis", disc(OASIS[2] * 1.15, (0.16, 0.55, 0.62)), alpha=0.8, roughness=0.1)
    # Alpine
    write_gltf(os.path.join(MODELS, "snow-pine.gltf"), "snow-pine", make_snow_pine())
    write_gltf(os.path.join(MODELS, "snow-rock.gltf"), "snow-rock", make_snow_rock(), roughness=1.0)
    write_gltf(os.path.join(MODELS, "ice.gltf"), "ice", disc(ICE[2] * 1.15, (0.50, 0.78, 0.92), seed=88), alpha=0.94, roughness=0.05)
    # Island
    write_gltf(os.path.join(MODELS, "ocean.gltf"), "ocean", square(34.0, (0.10, 0.50, 0.72)), alpha=0.8, roughness=0.1)
    write_gltf(os.path.join(MODELS, "hut.gltf"), "hut", make_hut())
    write_gltf(os.path.join(MODELS, "tropical-bush.gltf"), "tropical-bush", make_tuft((0.16, 0.50, 0.20), 71))
    # Autumn
    write_gltf(os.path.join(MODELS, "autumn-oak-red.gltf"), "autumn-oak-red", make_colored_oak((0.74, 0.20, 0.10), 81))
    write_gltf(os.path.join(MODELS, "autumn-oak-orange.gltf"), "autumn-oak-orange", make_colored_oak((0.88, 0.46, 0.10), 91))
    write_gltf(os.path.join(MODELS, "autumn-oak-yellow.gltf"), "autumn-oak-yellow", make_colored_oak((0.90, 0.74, 0.18), 101))
    write_gltf(os.path.join(MODELS, "autumn-pine.gltf"), "autumn-pine", make_autumn_pine())
    write_gltf(os.path.join(MODELS, "autumn-bush.gltf"), "autumn-bush", make_tuft((0.70, 0.36, 0.12), 111))


# ---- Scenes --------------------------------------------------------------------------------------------------------------------------

def scene_for(biome, terrain_file, water_models, spec, sky, sun_color, sun_direction, sun_intensity, ground, seed):
    """`spec` lists what to scatter: (prefix, model, count, (x0, x1, z0, z1), spacing, (scale_low, scale_high), lift, needs_land)."""
    hopper = json.load(open(os.path.join(base.ROOT, "assets", "samples", "hopper-3d.json"), encoding="utf-8"))
    template = {e["name"]: e for e in hopper["entities"]}

    def ident(name):
        return str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/%s/%s" % (biome.key, name)))

    sun = json.loads(json.dumps(template["Sun"]))
    sun["id"] = ident("Sun")
    sun["transform"]["position"] = {"x": 0.0, "y": 4000.0, "z": 2000.0}
    for c in sun["components"]:
        if c["type"] == "DirectionalLightComponent":
            c["data"].update(directionX=sun_direction[0], directionY=sun_direction[1], directionZ=sun_direction[2],
                             colorR=sun_color[0], colorG=sun_color[1], colorB=sun_color[2], intensity=sun_intensity, castShadows=False)
    skylight = json.loads(json.dumps(template["Sky Light"]))
    skylight["id"] = ident("Sky Light")
    for c in skylight["components"]:
        if c["type"] == "SkyLightComponent":
            c["data"].update(skyR=sky[0], skyG=sky[1], skyB=sky[2], groundR=ground[0], groundG=ground[1], groundB=ground[2], intensity=0.8)
    camera = json.loads(json.dumps(template["Main Camera"]))
    camera["id"] = ident("Main Camera")
    camera["components"] = [c for c in camera["components"] if c["type"] == "CameraComponent"]
    pitch = math.radians(-24)
    camera["transform"]["position"] = {"x": 300.0, "y": 2300.0, "z": 4400.0}
    camera["transform"]["rotation"] = {"x": math.sin(pitch / 2), "y": 0.0, "z": 0.0, "w": math.cos(pitch / 2)}
    for c in camera["components"]:
        if c["type"] == "CameraComponent":
            c["data"].update(fieldOfView=62.0, farPlane=60000.0)

    def make_entity(name, position, path, scale=1.0, rotation=(0.0, 0.0, 0.0, 1.0)):
        e = base.entity(name, position, [model("models/landscape/" + path, UNITS_PER_METRE * scale)], rotation=rotation)
        e["id"] = ident(name)
        return e

    entities = [sun, skylight, camera, make_entity("Terrain", (0, 0, 0), terrain_file)]
    for name, path, (x, y, z) in water_models:
        entities.append(make_entity(name, (x * 100, y * 100, z * 100), path))

    rng = random.Random(seed)
    taken = []
    ponds = [w for w in (biome.water,) if w]

    def free(x, z, min_normal, needs_land):
        if abs(x) > SIZE / 2 - 5 or abs(z) > SIZE / 2 - 5:
            return False
        if any(math.hypot(x - px, z - pz) < pr * 1.2 + 1.5 for px, pz, pr in ponds):
            return False
        if needs_land and biome.water_level is not None and biome.height(x, z) < biome.water_level + 0.35:
            return False
        return biome.normal(x, z)[1] > min_normal

    for prefix, path, count, region, spacing, scale, lift, needs_land in spec:
        placed = 0
        for _ in range(60000):
            if placed >= count:
                break
            x, z = rng.uniform(region[0], region[1]), rng.uniform(region[2], region[3])
            if not free(x, z, biome.min_normal if prefix not in ("Mesa",) else 0.0, needs_land):
                continue
            if any(math.hypot(x - tx, z - tz) < spacing + ts for tx, tz, ts in taken):
                continue
            s = scale[0] + (scale[1] - scale[0]) * rng.random()
            placed += 1
            taken.append((x, z, 0.0))
            y = biome.height(x, z) + lift * s
            entities.append(make_entity("%s %d" % (prefix, placed), (x * 100, y * 100, z * 100), path, s, yaw(rng.random() * 2 * math.pi)))
        if placed < count:
            print("  note: only placed %d of %d %s" % (placed, count, prefix))
    return {"schemaVersion": 1, "name": biome.title, "active": True, "entities": entities}


def write_scene(filename, scene):
    with open(os.path.join(SAMPLES, filename), "w", encoding="utf-8", newline="\n") as handle:
        json.dump(scene, handle, indent=2)
        handle.write("\n")
    counts = {}
    for e in scene["entities"]:
        key = e["name"].rstrip("0123456789 ")
        counts[key] = counts.get(key, 0) + 1
    print("wrote %s: %d entities %s" % (filename, len(scene["entities"]), counts))


def main():
    os.makedirs(MODELS, exist_ok=True)
    write_extra_models()

    desert = Biome("desert", "Desert Canyon", desert_height, desert_color, water=(OASIS[0], OASIS[1], OASIS[2]), water_level=-0.3, min_normal=0.9)
    write_gltf(os.path.join(MODELS, "desert-terrain.gltf"), "desert-terrain", desert.terrain_mesh(), roughness=1.0)
    write_scene("landscape-desert-3d.json", scene_for(
        desert, "desert-terrain.gltf", [("Oasis", "oasis.gltf", (OASIS[0], -0.45, OASIS[1]))],
        [("Mesa", "mesa.gltf", 4, (-30, 30, -30, -6), 14.0, (1.0, 1.6), -0.3, False),
         ("Palm", "palm-tree.gltf", 7, (OASIS[0] - 9, OASIS[0] + 9, OASIS[1] - 9, OASIS[1] + 9), 2.6, (0.8, 1.15), -0.05, True),
         ("Cactus", "cactus.gltf", 18, (-27, 27, -27, 27), 4.0, (0.7, 1.3), -0.05, True),
         ("Rock", "desert-rock.gltf", 14, (-27, 27, -27, 27), 3.0, (0.6, 1.5), 0.0, True),
         ("Dry Bush", "dry-bush.gltf", 22, (-27, 27, -27, 27), 2.0, (0.8, 1.3), 0.0, True)],
        sky=(0.78, 0.86, 0.95), sun_color=(1.0, 0.88, 0.68), sun_direction=(-0.45, -0.8, -0.4), sun_intensity=1.3, ground=(0.55, 0.42, 0.28), seed=11))

    alpine = Biome("alpine", "Alpine Valley", alpine_height, alpine_color, water=ICE, water_level=-0.1, min_normal=0.85)
    write_gltf(os.path.join(MODELS, "alpine-terrain.gltf"), "alpine-terrain", alpine.terrain_mesh(), roughness=1.0)
    scene = scene_for(
        alpine, "alpine-terrain.gltf", [("Ice", "ice.gltf", (ICE[0], -1.15, ICE[1]))],
        [("Snow Pine", "snow-pine.gltf", 30, (-28, 28, -28, 28), 3.0, (0.8, 1.35), -0.05, True),
         ("Rock", "snow-rock.gltf", 14, (-27, 27, -27, 27), 3.0, (0.6, 1.5), 0.0, True)],
        sky=(0.68, 0.79, 0.92), sun_color=(0.96, 0.97, 1.0), sun_direction=(-0.35, -0.75, -0.5), sun_intensity=1.1, ground=(0.62, 0.66, 0.72), seed=21)
    # Mountains of the forest valley, reused as the backdrop, and its cabin
    for k, (x, z, s) in enumerate([(-36, -38, 0.9), (-14, -43, 0.65), (6, -40, 1.0), (26, -43, 0.7), (40, -36, 0.85)]):
        e = base.entity("Mountain %d" % (k + 1), (x * 100, 0, z * 100), [model("models/landscape/mountain.gltf", UNITS_PER_METRE * s)], rotation=yaw(k * 1.7))
        e["id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/alpine/Mountain %d" % (k + 1)))
        scene["entities"].append(e)
    cabin_x, cabin_z = -12.0, 14.0
    for _ in range(2000):
        if alpine.normal(cabin_x, cabin_z)[1] > 0.97 and all(alpine.normal(cabin_x + dx, cabin_z + dz)[1] > 0.95 for dx in (-2.5, 2.5) for dz in (-2, 2)):
            break
        cabin_x, cabin_z = cabin_x + 0.7, cabin_z - 0.5
        if cabin_z < -20 or cabin_x > 25:
            cabin_x, cabin_z = -22.0 + (cabin_x % 7), 20.0
    cabin = base.entity("Cabin", (cabin_x * 100, (alpine.height(cabin_x, cabin_z) - 0.1) * 100, cabin_z * 100), [model("models/landscape/cabin.gltf")])
    cabin["id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/alpine/Cabin"))
    scene["entities"].append(cabin)
    write_scene("landscape-alpine-3d.json", scene)

    island = Biome("island", "Tropical Island", island_height, island_color, water_level=0.05, min_normal=0.86)
    write_gltf(os.path.join(MODELS, "island-terrain.gltf"), "island-terrain", island.terrain_mesh(), roughness=1.0)
    write_scene("landscape-island-3d.json", scene_for(
        island, "island-terrain.gltf", [("Ocean", "ocean.gltf", (0.0, 0.0, 0.0))],
        [("Hut", "hut.gltf", 2, (-8, 8, -8, 10), 6.0, (1.0, 1.2), -0.05, True),
         ("Palm", "palm-tree.gltf", 26, (-22, 22, -22, 22), 3.0, (0.8, 1.3), -0.05, True),
         ("Bush", "tropical-bush.gltf", 20, (-22, 22, -22, 22), 2.0, (0.8, 1.4), 0.0, True),
         ("Rock", "rock.gltf", 12, (-24, 24, -24, 24), 3.0, (0.5, 1.3), 0.0, True)],
        sky=(0.46, 0.76, 0.98), sun_color=(1.0, 0.96, 0.85), sun_direction=(-0.4, -0.85, -0.3), sun_intensity=1.25, ground=(0.78, 0.72, 0.5), seed=31))

    autumn = Biome("autumn", "Autumn Forest", autumn_height, autumn_color, water=POND, water_level=-0.2, min_normal=0.88)
    write_gltf(os.path.join(MODELS, "autumn-terrain.gltf"), "autumn-terrain", autumn.terrain_mesh(), roughness=1.0)
    scene = scene_for(
        autumn, "autumn-terrain.gltf", [("Pond", "lake.gltf", (POND[0], -1.15, POND[1]))],
        [("Red Oak", "autumn-oak-red.gltf", 9, (-28, 28, -28, 28), 5.0, (0.85, 1.3), -0.05, True),
         ("Orange Oak", "autumn-oak-orange.gltf", 11, (-28, 28, -28, 28), 5.0, (0.85, 1.3), -0.05, True),
         ("Yellow Oak", "autumn-oak-yellow.gltf", 9, (-28, 28, -28, 28), 5.0, (0.85, 1.3), -0.05, True),
         ("Pine", "autumn-pine.gltf", 14, (-28, 28, -28, 4), 3.4, (0.85, 1.3), -0.05, True),
         ("Bush", "autumn-bush.gltf", 22, (-27, 27, -27, 27), 1.8, (0.8, 1.4), 0.0, True),
         ("Rock", "rock.gltf", 10, (-27, 27, -27, 27), 3.0, (0.6, 1.4), 0.0, True)],
        sky=(0.80, 0.83, 0.88), sun_color=(1.0, 0.84, 0.58), sun_direction=(-0.5, -0.7, -0.4), sun_intensity=1.15, ground=(0.45, 0.34, 0.2), seed=41)
    cx, cz = -14.0, 12.0
    for _ in range(3000):
        if autumn.normal(cx, cz)[1] > 0.97 and all(autumn.normal(cx + dx, cz + dz)[1] > 0.95 for dx in (-2.5, 2.5) for dz in (-2, 2)) \
                and math.hypot(cx - POND[0], cz - POND[1]) > POND[2] * 1.2 + 3:
            break
        cx, cz = cx + 0.9, cz - 0.4
        if cz < -20 or cx > 25:
            cx, cz = -24.0 + (cx % 5), 22.0
    cabin = base.entity("Cabin", (cx * 100, (autumn.height(cx, cz) - 0.1) * 100, cz * 100), [model("models/landscape/cabin.gltf")])
    cabin["id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "forgeroom/autumn/Cabin"))
    scene["entities"].append(cabin)
    write_scene("landscape-autumn-3d.json", scene)


if __name__ == "__main__":
    main()
