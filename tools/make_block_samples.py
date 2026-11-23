"""Writes the two block-style sample scenes: assets/samples/blockworld-3d.json and assets/samples/obby-3d.json.

Run from the repository root:  python tools/make_block_samples.py

The scenes are ordinary scene files (edit them in the editor afterwards). This script only exists so the obstacle course's
layout is computed, and checked for jumps the character can actually make, instead of placed by hand. Ids are derived from
names, so running it again writes the same files.
"""
import json
import math
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SAMPLES = ROOT / "assets" / "samples"

# The character's movement, mirrored from ObbyGame.java (RUN_SPEED, JUMP_SPEED, GRAVITY, HALF_WIDTH)
RUN_SPEED = 400.0
JUMP_SPEED = 900.0
GRAVITY = 2400.0
HALF_WIDTH = 28.0
# A jump must be this much shorter than the physical limit, so the course is fair and not frame-perfect
SAFETY = 0.8


def entity_id(scene, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"forgeroom/{scene}/{name}"))


class Scene:
    def __init__(self, key, title, mode="3d"):
        self.key = key
        self.title = title
        self.mode = mode
        self.entities = []

    def add(self, name, position=(0, 0, 0), components=(), parent=None, rotation=(0, 0, 0, 1)):
        entity = {
            "id": entity_id(self.key, name),
            "name": name,
            "active": True,
            "parentId": entity_id(self.key, parent) if parent else None,
            "transform": {
                "position": dict(zip("xyz", map(float, position))),
                "rotation": dict(zip("xyzw", map(float, rotation))),
                "scale": {"x": 1.0, "y": 1.0, "z": 1.0},
            },
            "components": list(components),
        }
        self.entities.append(entity)
        return entity

    def write(self, path):
        names = [e["name"] for e in self.entities]
        assert len(names) == len(set(names)), "entity names must be unique"
        data = {"schemaVersion": 1, "name": self.title, "gameMode": self.mode, "active": True, "entities": self.entities}
        Path(path).write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8", newline="")


def component(kind, **data):
    return {"type": kind, "enabled": True, "data": data}


def box(size, color):
    return component("MeshPrimitiveComponent", shape="BOX", sizeX=float(size[0]), sizeY=float(size[1]), sizeZ=float(size[2]),
                     colorR=color[0], colorG=color[1], colorB=color[2], colorA=color[3] if len(color) > 3 else 1.0)


def script(path, class_name):
    return component("EntityScriptBindingComponent", scriptPath=f"scripts/samples/{path}.java",
                     scriptClassName=f"samples.{class_name}", language="JAVA")


def text(value, anchor, offset_x, offset_y, scale=1.0, color=(1, 1, 1)):
    return component("TextComponent", text=value, colorR=color[0], colorG=color[1], colorB=color[2], colorA=1, scale=scale,
                     anchor=anchor, offsetX=float(offset_x), offsetY=float(offset_y))


def cue(name, sound, volume, pitch=1.0):
    return component("AudioSource", cue=name, sound=sound, volume=volume, pitch=pitch, playOnStart=False)


def lighting(scene, sky=(0.62, 0.72, 0.88)):
    scene.add("Sun", (0, 800, 400), [component(
        "DirectionalLightComponent", directionX=-0.4, directionY=-1.0, directionZ=-0.6, colorR=1.0, colorG=0.96, colorB=0.88,
        colorA=1.0, intensity=1.0, castShadows=True)])
    scene.add("Sky Light", (0, 0, 0), [
        component("SkyLightComponent", skyR=sky[0], skyG=sky[1], skyB=sky[2], groundR=0.32, groundG=0.29, groundB=0.26,
                  intensity=0.6),
        component("ToneMappingComponent", exposure=1.0, operator="ACES_FILMIC")])


def camera(scene, position, fov, extra=()):
    scene.add("Main Camera", position, [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=float(fov), nearPlane=1.0,
        farPlane=50000.0), *extra])


# --- Block World ---

def make_blockworld():
    scene = Scene("blockworld", "Block World")
    lighting(scene, sky=(0.55, 0.74, 0.96))
    camera(scene, (0, 170, 0), 70)
    scene.add("World", (0, 0, 0), [script("blockworld/BlockWorld", "blockworld.BlockWorld")])
    scene.add("Selection", (0, -1000, 0), [box((104, 104, 104), (1.0, 1.0, 1.0, 0.28))])
    scene.add("Hotbar Text", (0, 0, 0), [text("", "BOTTOM", 0, 56, 1.4, (1, 1, 1))])
    scene.add("Stats Text", (0, 0, 0), [text("", "TOP_LEFT", 16, -16, 1.3)])
    scene.add("Crosshair Text", (0, 0, 0), [text("+", "CENTER", 0, 0, 2.0)])
    scene.add("Help Text", (0, 0, 0), [text(
        "WASD: walk   Arrows: look   Space: jump   F: break block   G: place block   1-6: choose block   R: new world",
        "BOTTOM_LEFT", 16, 24, 1.0, (0.85, 0.9, 1.0))])
    scene.add("Sound Effects", (0, 0, 0), [
        cue("jump", "Jump", 0.4), cue("break", "Explosion", 0.3, 2.4), cue("place", "Blip", 0.5, 0.7)])
    scene.write(SAMPLES / "blockworld-3d.json")
    return scene


# --- Obby ---

PALETTE = [(0.89, 0.15, 0.21), (0.0, 0.45, 0.85), (0.98, 0.8, 0.1), (0.2, 0.7, 0.25), (0.6, 0.25, 0.75),
           (0.98, 0.5, 0.1), (0.1, 0.75, 0.8)]
CHECKPOINT_COLOR = (0.62, 0.64, 0.7)
LAVA = (0.95, 0.35, 0.05)
GOLD = (1.0, 0.82, 0.1)
# Half the character's height (ObbyGame.HALF_HEIGHT): the centre of the collision box when standing on the start pad at height 0
PLAYER_CENTRE_HEIGHT = 78.0
THICK = 40.0


def reach_for(dy):
    """The furthest the character's centre can travel horizontally during a jump that ends dy above where it started."""
    disc = JUMP_SPEED ** 2 - 2 * GRAVITY * dy
    assert disc > 0, f"a rise of {dy} cannot be jumped"
    return RUN_SPEED * (JUMP_SPEED + math.sqrt(disc)) / GRAVITY


def rect_gap(a, b):
    """Edge-to-edge distance in the ground plane between two (x, z, sizeX, sizeZ) rectangles."""
    dx = max(abs(a[0] - b[0]) - (a[2] + b[2]) / 2, 0.0)
    dz = max(abs(a[1] - b[1]) - (a[3] + b[3]) / 2, 0.0)
    return math.hypot(dx, dz)


class Course:
    def __init__(self, scene):
        self.scene = scene
        self.colour = 0
        self.counts = {}
        self.jumps = []  # (name_from, name_to, gap, rise) of every jump the player makes by running and jumping
        self.last = None  # (name, x, z, sx, sz, top)
        self.coins = 0

    def next_colour(self):
        self.colour += 1
        return PALETTE[self.colour % len(PALETTE)]

    def name(self, prefix):
        self.counts[prefix] = self.counts.get(prefix, 0) + 1
        return f"{prefix} {self.counts[prefix]}"

    def part(self, name, x, top, z, sx, sz, color, thick=THICK, jump=True):
        self.scene.add(name, (x, top - thick / 2, z), [box((sx, thick, sz), color)])
        if jump and self.last:
            prev = self.last
            gap = rect_gap((prev[1], prev[2], prev[3], prev[4]), (x, z, sx, sz))
            self.jumps.append((prev[0], name, gap, top - prev[5]))
        self.last = (name, x, z, sx, sz, top)
        return self.last

    def coin(self, x, y, z):
        self.coins += 1
        self.scene.add(f"Coin {self.coins}", (x, y, z), [box((40, 40, 8), GOLD)])

    def check(self):
        for source, target, gap, rise in self.jumps:
            # The character can take off up to a half width past the edge and land a half width short, which is slack kept in reserve
            limit = reach_for(max(rise, 0.0)) * SAFETY
            assert gap <= limit, f"{source} -> {target}: gap {gap:.0f} is more than the {limit:.0f} a jump allows (rise {rise:.0f})"


def make_obby():
    scene = Scene("obby", "Obby")
    lighting(scene, sky=(0.5, 0.75, 1.0))
    camera(scene, (0, 300, 620), 65)

    course = Course(scene)
    # Stage 0: the start pad
    course.part("Checkpoint 0", 0, 0, 0, 400, 400, CHECKPOINT_COLOR)
    edge = -200.0
    top = 0.0

    def step(prefix, size, gap, rise=0.0, x=0.0, color=None, thick=THICK, jump=True):
        nonlocal edge, top
        top += rise
        z = edge - gap - size / 2
        name = prefix if prefix == "Finish" or prefix.startswith("Checkpoint") else course.name(prefix)
        course.part(name, x, top, z, size, size, color or course.next_colour(), thick, jump)
        edge = z - size / 2
        return name, x, z

    # Stage 1: warm-up hops that stretch out
    gaps = [100, 120, 140, 150, 160, 170]
    rises = [0, 20, 0, 20, 0, 20]
    xs = [0, 70, -70, 0, 90, -90]
    for i, gap in enumerate(gaps):
        _, x, z = step("Platform", 200, gap, rises[i], xs[i])
        if i in (1, 3, 5):
            course.coin(x, top + 120, z)
    step("Checkpoint 1", 360, 120, 0, 0, CHECKPOINT_COLOR)
    # Stage 2: stepping stones over lava
    lava_start = edge
    for i, gap in enumerate([110, 130, 150, 150, 160, 160, 170, 170]):
        _, x, z = step("Platform", 150, gap, 0, [0, -100, 100, -100, 100, 0, -110, 110][i])
        if i in (2, 5):
            course.coin(x, top + 120, z)
    lava_end = edge - 260
    scene.add("Kill Lava 1", (0, top - 150 - 15, (lava_start + lava_end) / 2), [box((1500, 30, lava_start - lava_end + 400), LAVA)])
    step("Checkpoint 2", 360, 130, 0, 0, CHECKPOINT_COLOR)
    # Stage 3: pads that slide and lift. Each mover has a pad either side; the jump on and off is checked, the ride is not
    reach = 260.0
    movers = 0

    def mover_z(pad_size=220):
        nonlocal edge, movers
        movers += 1
        gap = 90.0
        origin_z = edge - gap - 90 - reach
        scene.add(f"Mover Z {movers}", (0, top - THICK / 2, origin_z), [box((180, THICK, 180), (0.98, 0.5, 0.1))])
        far_edge = origin_z - reach - 90
        # Jumps on and off at the ends of its travel
        course.jumps.append((course.last[0], f"Mover Z {movers}", gap, 0.0))
        z = far_edge - gap - pad_size / 2
        name = course.name("Platform")
        course.part(name, 0, top, z, pad_size, pad_size, course.next_colour(), jump=False)
        course.jumps.append((f"Mover Z {movers}", name, gap, 0.0))
        edge = z - pad_size / 2

    step("Platform", 220, 120)
    mover_z()
    # An elevator between a low pad and a high one
    movers += 1
    elevator_origin = top + 200
    scene.add(f"Mover Y {movers}", (0, elevator_origin - THICK / 2, edge - 90 - 90), [box((180, THICK, 180), (0.2, 0.7, 0.25))])
    course.jumps.append((course.last[0], f"Mover Y {movers}", 90.0, -60.0))
    top += 400
    high_z = edge - 90 - 180 - 90 - 110
    course.part(course.name("Platform"), 0, top, high_z, 220, 220, course.next_colour(), jump=False)
    course.jumps.append((f"Mover Y {movers}", course.last[0], 90.0, -60.0))
    edge = high_z - 110
    course.coin(0, top + 120, high_z)
    mover_z()
    step("Checkpoint 3", 360, 120, 0, 0, CHECKPOINT_COLOR)
    # Stage 4: tiles that drop away; stop and you fall
    for i in range(9):
        _, x, z = step("Fade", 170, 90, 0, [0, 110, -110][i % 3], (0.98, 0.65, 0.85))
        if i in (3, 7):
            course.coin(x, top + 120, z)
    step("Checkpoint 4", 360, 100, 0, 0, CHECKPOINT_COLOR)
    # Stage 5: a long walkway swept by two bars
    walk_len = 1000.0
    z = edge - 100 - walk_len / 2
    course.part("Platform Walkway", 0, top, z, 360, walk_len, (0.1, 0.75, 0.8))
    for index, offset in enumerate([-walk_len / 5, walk_len / 5], start=1):
        scene.add(f"Spinner {index}", (0, top + 20, z + offset), [box((560, 40, 36), (0.89, 0.15, 0.21))])
    course.coin(0, top + 120, z)
    edge = z - walk_len / 2
    step("Checkpoint 5", 360, 100, 0, 0, CHECKPOINT_COLOR)
    # Stage 6: bounce pads climb to the finish
    for round_index in range(3):
        step("Pad", 200, 100 if round_index == 0 else 60, 0, 0, (0.55, 0.9, 0.2))
        name, x, z = step("Platform", 260, 220, 350, 0, None, jump=False)
        course.coin(x, top + 120, z)
    # Finish: a pad on the last landing leads to it
    step("Pad", 200, 60, 0, 0, (0.55, 0.9, 0.2))
    step("Finish", 420, 220, 350, 0, (1.0, 0.85, 0.15), jump=False)
    course.check()
    bounce = 1500.0
    assert (bounce ** 2) / (2 * GRAVITY) > 350 + 40, "pads must launch high enough for the next landing"
    reach_bounce = RUN_SPEED * (bounce + math.sqrt(bounce ** 2 - 2 * GRAVITY * 350)) / GRAVITY
    assert 220 <= reach_bounce * SAFETY, "a bounce must carry across the gap to its landing"

    # The character: Player is the collision box's centre (156 tall); its parts hang from it
    scene.add("Player", (0, PLAYER_CENTRE_HEIGHT, 0), [script("obby/ObbyGame", "obby.ObbyGame")])
    skin = (0.98, 0.85, 0.2)
    shirt = (0.0, 0.45, 0.85)
    pants = (0.2, 0.7, 0.25)
    scene.add("Torso", (0, 6, 0), [box((56, 56, 30), shirt)], parent="Player")
    scene.add("Head", (0, 56, 0), [box((44, 44, 44), skin)], parent="Player")
    scene.add("Eye L", (-10, 6, -23), [box((7, 9, 3), (0.05, 0.05, 0.05))], parent="Head")
    scene.add("Eye R", (10, 6, -23), [box((7, 9, 3), (0.05, 0.05, 0.05))], parent="Head")
    for side, sign in (("L", -1), ("R", 1)):
        scene.add(f"Arm {side} Pivot", (sign * 40, 34, 0), [], parent="Player")
        scene.add(f"Arm {side}", (0, -26, 0), [box((22, 56, 24), skin)], parent=f"Arm {side} Pivot")
        scene.add(f"Leg {side} Pivot", (sign * 14, -22, 0), [], parent="Player")
        scene.add(f"Leg {side}", (0, -28, 0), [box((26, 56, 26), pants)], parent=f"Leg {side} Pivot")

    scene.add("Stage Text", (0, 0, 0), [text("", "TOP_LEFT", 16, -16, 1.5)])
    scene.add("Coins Text", (0, 0, 0), [text("", "TOP_LEFT", 16, -48, 1.3, GOLD)])
    scene.add("Time Text", (0, 0, 0), [text("", "TOP_RIGHT", -16, -16, 1.5)])
    scene.add("Deaths Text", (0, 0, 0), [text("", "TOP_RIGHT", -16, -48, 1.3, (1.0, 0.6, 0.6))])
    scene.add("Message Text", (0, 0, 0), [text("", "CENTER", 0, 120, 1.8)])
    scene.add("Help Text", (0, 0, 0), [text(
        "WASD: run   Space: jump   Left/Right: turn camera   Up/Down: tilt   R: restart   Reach the gold Finish",
        "BOTTOM_LEFT", 16, 32, 1.0, (0.85, 0.9, 1.0))])
    scene.add("Sound Effects", (0, 0, 0), [
        cue("jump", "Jump", 0.45), cue("coin", "Coin", 0.5), cue("checkpoint", "Powerup", 0.5, 1.3),
        cue("hurt", "Hurt", 0.6), cue("bounce", "Blip", 0.6, 1.8), cue("win", "Powerup", 0.8)])
    scene.write(SAMPLES / "obby-3d.json")
    return scene, course


if __name__ == "__main__":
    world = make_blockworld()
    obby, course = make_obby()
    print(f"blockworld-3d.json: {len(world.entities)} entities")
    print(f"obby-3d.json: {len(obby.entities)} entities, {len(course.jumps)} checked jumps, {course.coins} coins")
