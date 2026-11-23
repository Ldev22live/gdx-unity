"""Writes the samples played by their genre profile (TD-088): assets/samples/platformer-profile-25d.json (roadmap M3b.3),
assets/samples/action-rpg-valley-3d.json (roadmap M3c.1), assets/samples/action-rpg-iso-25d.json (roadmap M3c.3) and
assets/samples/platformer-profile-3d.json (roadmap M3b.7, slice 2) and assets/samples/first-person-range-3d.json (roadmap M3b.7,
slice 3).

Run from the repository root:  python tools/make_profile_samples.py

Neither scene has scripts or graphs. Their Game Genre and Game Mode make Play add the camera and the movement to the entity with
a Player and a Character Controller 3D:
- Platformer + 2.5D: a side camera, running and jumping along the play plane. The ground, platforms, wall and angled crate are
  boxes with Box Collider 3D.
- Action RPG + 3D: a camera behind Hero that Q and E turn, walking relative to the camera and jumping, on the terrain sample's
  valley (terrains/Valley.terrain, written by make_terrain_samples.py), with a boulder and a crate in the way.
- Action RPG + 2.5D: an isometric camera high and at an angle, walking across the ground relative to the screen and jumping, in a
  walled courtyard of boxes. Hero starts at the Spawn Point (roadmap M3c.2), not where it stands.
- Platformer + 3D: a chase camera behind and above the Jumper that Q and E turn, running relative to the camera and jumping, across
  floating stones over nothing: a fall puts the Jumper back on the start platform. The stones rise and step to the side, and the
  course is crossed by the acceptance test with the profile's own speeds.
- First-person Shooter + 3D: a camera at the Shooter's eyes, Q and E turn, W and S walk, A and D step sideways, Space jumps, and F
  or Ctrl shoots along the view: in a walled range, crates on a shelf (dynamic Rigid Body 3D) are knocked off when hit. The crosshair
  is a centred text.
It reuses the scene helpers of make_block_samples.py and the valley's height function of make_terrain_samples.py.
"""
import json
import math

from make_block_samples import Scene, box, component, lighting, text, SAMPLES
from make_terrain_samples import height, HERO_X, HERO_Z, HERO_CLEARANCE

# Ground top is y = 0; a default character's centre stands 70 above what it stands on (40 + its 30 radius)
STANDING = 70.0
DEPTH = 400.0
# Each solid: name, centre x, top y, width, height, colour, rotation about y in degrees
SOLIDS = [
    ("Ground", 1150.0, 0.0, 3500.0, 100.0, (0.36, 0.55, 0.32), 0.0),
    ("Start Wall", -560.0, 600.0, 60.0, 600.0, (0.45, 0.42, 0.40), 0.0),
    ("Low Platform", 500.0, 120.0, 300.0, 40.0, (0.72, 0.56, 0.36), 0.0),
    ("High Platform", 900.0, 240.0, 300.0, 40.0, (0.72, 0.56, 0.36), 0.0),
    ("Step Platform", 1300.0, 120.0, 260.0, 40.0, (0.72, 0.56, 0.36), 0.0),
    ("Jump Wall", 1800.0, 160.0, 60.0, 160.0, (0.62, 0.36, 0.32), 0.0),
    ("Angled Crate", 2300.0, 80.0, 200.0, 80.0, (0.80, 0.62, 0.30), 35.0),
    ("End Wall", 2860.0, 600.0, 60.0, 600.0, (0.45, 0.42, 0.40), 0.0),
]
# Valley obstacles: name, centre x, centre z, size, colour, rotation about y in degrees. Each is sunk a little into the slope
VALLEY_OBSTACLES = [
    ("Boulder", 260.0, 1000.0, 160.0, (0.52, 0.50, 0.47), 0.0),
    ("Crate", -240.0, 700.0, 120.0, (0.72, 0.56, 0.36), 20.0),
]
SINK = 20.0


def yaw_rotation(yaw):
    half = math.radians(yaw) / 2.0
    return (0.0, math.sin(half), 0.0, math.cos(half))


def collider_box(size, color):
    return [box(size, color),
            component("BoxCollider3DComponent", offsetX=0.0, offsetY=0.0, offsetZ=0.0, trigger=False,
                      sizeX=float(size[0]), sizeY=float(size[1]), sizeZ=float(size[2]))]


def character():
    return component("CharacterController3DComponent", radius=30.0, height=80.0, slopeLimitDegrees=45.0, stepHeight=30.0,
                     mass=70.0, gravityScale=1.0, layer=0, collisionMask=65535)


def write_with_genre(scene, file_name, genre):
    """Writes the scene; the Scene helper writes the game mode, the genre is the scene file's optional gameGenre field (TD-088)."""
    path = SAMPLES / file_name
    scene.write(path)
    data = json.loads(path.read_text(encoding="utf-8"))
    ordered = {}
    for key, value in data.items():
        ordered[key] = value
        if key == "gameMode":
            ordered["gameGenre"] = genre
    path.write_text(json.dumps(ordered, indent=2, ensure_ascii=False), encoding="utf-8", newline="")
    print(f"{file_name}: {len(scene.entities)} entities")


def platformer():
    scene = Scene("platformer-profile", "Platformer Profile 2.5D", mode="2.5d")
    lighting(scene, sky=(0.60, 0.76, 0.95))
    # No rig and no script: the Platformer + 2.5D profile adds a side camera that follows the Runner in Play
    scene.add("Main Camera", (0.0, STANDING + 150.0, 900.0), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=50.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=(-0.0825, 0.0, 0.0, 0.9966))
    for name, x, top, width, height_, color, yaw in SOLIDS:
        size = (width, height_, width if yaw else DEPTH)
        scene.add(name, (x, top - height_ / 2.0, 0.0), collider_box(size, color), rotation=yaw_rotation(yaw))
    scene.add("Runner", (0.0, STANDING + 10.0, 0.0), [
        box((60.0, 140.0, 60.0), (0.25, 0.45, 0.90)), character(), component("PlayerComponent", useProfileMovement=False)])
    # Light text: the game runtime clears to a dark background behind the level
    scene.add("Help Text", (0, 0, 0), [text(
        "A / D or the arrows run, Space jumps. No scripts: the Platformer genre and 2.5D mode supply it all.",
        "BOTTOM_LEFT", 16, 24, 1.0, (0.9, 0.95, 1.0))])
    write_with_genre(scene, "platformer-profile-25d.json", "platformer")


def action_rpg_valley():
    scene = Scene("action-rpg-valley", "Action RPG Valley 3D", mode="3d")
    lighting(scene, sky=(0.62, 0.76, 0.94))
    # No rig and no script: the Action RPG + 3D profile puts a third-person camera behind Hero in Play. In the editor this
    # camera shows the valley from beyond its low end, as in the terrain sample
    scene.add("Main Camera", (0, 1700, 4300), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=60.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=(-0.2164, 0, 0, 0.9763))
    scene.add("Valley", (0, 0, 0), [component("Terrain", terrainPath="terrains/Valley.terrain"),
                                    component("TerrainCollider3DComponent", offsetX=0.0, offsetY=0.0, offsetZ=0.0, trigger=False)])
    for name, x, z, size, color, yaw in VALLEY_OBSTACLES:
        scene.add(name, (x, height(x, z) + size / 2.0 - SINK, z), collider_box((size, size, size), color),
                  rotation=yaw_rotation(yaw))
    scene.add("Hero", (HERO_X, height(HERO_X, HERO_Z) + HERO_CLEARANCE, HERO_Z), [
        component("MeshPrimitiveComponent", shape="BOX", sizeX=60.0, sizeY=140.0, sizeZ=60.0, colorR=0.9, colorG=0.35, colorB=0.3,
                  colorA=1.0),
        character(), component("PlayerComponent", useProfileMovement=False)])
    scene.add("Help Text", (0, 0, 0), [text(
        "WASD or the arrows walk, Space jumps, Q and E turn the camera. No scripts: the Action RPG genre and 3D mode supply it all.",
        "BOTTOM_LEFT", 16, 24, 1.0, (0.9, 0.95, 1.0))])
    write_with_genre(scene, "action-rpg-valley-3d.json", "action-rpg")


# The courtyard: name, centre x, centre z, size x, height, size z, colour
COURTYARD = [
    ("East Wall", 1000.0, 0.0, 100.0, 200.0, 3000.0, (0.55, 0.50, 0.44)),
    ("West Wall", -1500.0, 0.0, 100.0, 200.0, 3000.0, (0.55, 0.50, 0.44)),
    ("North Wall", -250.0, -1500.0, 2600.0, 200.0, 100.0, (0.55, 0.50, 0.44)),
    ("South Wall", -250.0, 1500.0, 2600.0, 200.0, 100.0, (0.55, 0.50, 0.44)),
    ("Crate", -200.0, 100.0, 120.0, 120.0, 120.0, (0.72, 0.56, 0.36)),
    ("Pillar", 400.0, -600.0, 140.0, 300.0, 140.0, (0.62, 0.60, 0.58)),
    ("Low Step", -900.0, -700.0, 400.0, 30.0, 300.0, (0.50, 0.62, 0.40)),
]
SPAWN = (-600.0, 600.0)
ISO_YAW = 45.0
ISO_PITCH = -35.0
ISO_DISTANCE = 1300.0
ISO_PIVOT = 40.0


def quaternion(yaw, pitch):
    """LibGDX's setEulerAngles(yaw, pitch, 0): yaw about y, then pitch about x."""
    hy, hp = math.radians(yaw) / 2.0, math.radians(pitch) / 2.0
    qy = (0.0, math.sin(hy), 0.0, math.cos(hy))
    qx = (math.sin(hp), 0.0, 0.0, math.cos(hp))
    x1, y1, z1, w1 = qy
    x2, y2, z2, w2 = qx
    return (w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2, w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2, w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2)


def action_rpg_iso():
    scene = Scene("action-rpg-iso", "Action RPG Courtyard 2.5D", mode="2.5d")
    lighting(scene, sky=(0.62, 0.74, 0.90))
    # In the editor the camera already shows the view Play gives: from high up and diagonally, looking at the spawn point
    pivot = (SPAWN[0], STANDING + ISO_PIVOT, SPAWN[1])
    yaw, pitch = math.radians(ISO_YAW), math.radians(ISO_PITCH)
    forward = (-math.sin(yaw) * math.cos(pitch), math.sin(pitch), -math.cos(yaw) * math.cos(pitch))
    eye = tuple(pivot[i] - forward[i] * ISO_DISTANCE for i in range(3))
    scene.add("Main Camera", eye, [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=45.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=quaternion(ISO_YAW, ISO_PITCH))
    scene.add("Ground", (-250.0, -50.0, 0.0), collider_box((2600.0, 100.0, 3000.0), (0.40, 0.58, 0.34)))
    for name, x, z, sx, h, sz, color in COURTYARD:
        scene.add(name, (x, h / 2.0, z), collider_box((sx, h, sz), color))
    scene.add("Spawn Point", (SPAWN[0], 0.0, SPAWN[1]), [component("SpawnPointComponent")])
    # Hero stands elsewhere: in Play it starts at the spawn point
    scene.add("Hero", (0.0, STANDING + 1.0, 0.0), [
        box((60.0, 140.0, 60.0), (0.85, 0.38, 0.30)), character(), component("PlayerComponent", useProfileMovement=False)])
    scene.add("Help Text", (0, 0, 0), [text(
        "WASD or the arrows walk across the courtyard, Space jumps. No scripts: the Action RPG genre and 2.5D mode supply it all.",
        "BOTTOM_LEFT", 16, 24, 1.0, (0.9, 0.95, 1.0))])
    write_with_genre(scene, "action-rpg-iso-25d.json", "action-rpg")


# The sky course, along -Z from the start: name, centre x, centre z, top y, size x, size z, colour. No ground below
COURSE = [
    ("Start Platform", 0.0, 0.0, 0.0, 600.0, 600.0, (0.45, 0.62, 0.40)),
    ("Stone 1", 0.0, -700.0, 0.0, 300.0, 300.0, (0.72, 0.56, 0.36)),
    ("Stone 2", 0.0, -1150.0, 120.0, 300.0, 300.0, (0.72, 0.56, 0.36)),
    ("Stone 3", 350.0, -1550.0, 240.0, 300.0, 300.0, (0.72, 0.56, 0.36)),
    ("Goal Platform", 350.0, -2150.0, 240.0, 600.0, 600.0, (0.92, 0.78, 0.30)),
]
COURSE_THICKNESS = 80.0
JUMPER_Z = 150.0


def platformer_3d():
    scene = Scene("platformer-profile-3d", "Platformer Profile 3D", mode="3d")
    lighting(scene, sky=(0.58, 0.74, 0.96))
    # No rig and no script: the Platformer + 3D profile puts a chase camera behind the Jumper in Play. In the editor this camera
    # shows the course from behind and above the start
    scene.add("Main Camera", (0.0, 700.0, 1300.0), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=60.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=quaternion(0.0, -22.0))
    for name, x, z, top, sx, sz, color in COURSE:
        scene.add(name, (x, top - COURSE_THICKNESS / 2.0, z), collider_box((sx, COURSE_THICKNESS, sz), color))
    # The flag is scenery: no collider, so it never blocks the landing
    goal = COURSE[-1]
    scene.add("Flag Pole", (goal[1], goal[3] + 150.0, goal[2] - 200.0), [box((16.0, 300.0, 16.0), (0.85, 0.85, 0.88))])
    scene.add("Flag", (goal[1] + 60.0, goal[3] + 260.0, goal[2] - 200.0), [box((110.0, 70.0, 6.0), (0.90, 0.25, 0.22))])
    scene.add("Jumper", (0.0, STANDING + 1.0, JUMPER_Z), [
        box((60.0, 140.0, 60.0), (0.30, 0.55, 0.95)), character(), component("PlayerComponent", useProfileMovement=False)])
    scene.add("Help Text", (0, 0, 0), [text(
        "WASD or the arrows run, Space jumps, Q and E turn the camera; fall and you start again. No scripts: the Platformer genre "
        "and 3D mode supply it all.", "BOTTOM_LEFT", 16, 24, 1.0, (0.9, 0.95, 1.0))])
    write_with_genre(scene, "platformer-profile-3d.json", "platformer")


# The range: name, centre x, centre z, size x, height, size z, colour; all static, standing on the floor (top y = 0)
RANGE = [
    ("North Wall", 0.0, -1300.0, 2400.0, 300.0, 100.0, (0.55, 0.52, 0.48)),
    ("South Wall", 0.0, 1300.0, 2400.0, 300.0, 100.0, (0.55, 0.52, 0.48)),
    ("East Wall", 1200.0, 0.0, 100.0, 300.0, 2500.0, (0.55, 0.52, 0.48)),
    ("West Wall", -1200.0, 0.0, 100.0, 300.0, 2500.0, (0.55, 0.52, 0.48)),
    ("Shelf", 0.0, -900.0, 1000.0, 100.0, 120.0, (0.45, 0.36, 0.28)),
]
# Crates on the shelf, 1500 in front of the Shooter: the side ones are about 11 degrees off the middle
TARGETS = [("Crate Left", -300.0), ("Crate Middle", 0.0), ("Crate Right", 300.0)]
TARGET_SIZE = 80.0
SHOOTER_Z = 600.0


def dynamic_box(size, color, mass):
    return collider_box(size, color) + [component(
        "RigidBody3DComponent", motionType="DYNAMIC", mass=mass, linearDamping=0.05, angularDamping=0.05, gravityFactor=1.0,
        friction=0.5, restitution=0.2, layer=0, collisionMask=65535)]


def first_person_range():
    scene = Scene("first-person-range", "First-person Range 3D", mode="3d")
    lighting(scene, sky=(0.60, 0.72, 0.90))
    # No rig and no script: the First-person Shooter + 3D profile puts the camera at the Shooter's eyes in Play. In the editor this
    # camera shows the range from above the Shooter's shoulder
    scene.add("Main Camera", (0.0, 500.0, SHOOTER_Z + 700.0), [component(
        "CameraComponent", projection="PERSPECTIVE", orthographicHeight=480.0, fieldOfView=60.0, nearPlane=1.0, farPlane=50000.0)],
        rotation=quaternion(0.0, -15.0))
    scene.add("Floor", (0.0, -50.0, 0.0), collider_box((2400.0, 100.0, 2600.0), (0.42, 0.50, 0.40)))
    for name, x, z, sx, h, sz, color in RANGE:
        scene.add(name, (x, h / 2.0, z), collider_box((sx, h, sz), color))
    shelf_top = RANGE[-1][4]
    for name, x in TARGETS:
        scene.add(name, (x, shelf_top + TARGET_SIZE / 2.0 + 1.0, RANGE[-1][2]),
                  dynamic_box((TARGET_SIZE, TARGET_SIZE, TARGET_SIZE), (0.80, 0.58, 0.30), 5.0))
    scene.add("Shooter", (0.0, STANDING + 1.0, SHOOTER_Z), [
        box((60.0, 140.0, 60.0), (0.35, 0.40, 0.48)), character(), component("PlayerComponent", useProfileMovement=False)])
    scene.add("Crosshair", (0, 0, 0), [text("+", "CENTER", 0, 0, 1.6, (1.0, 1.0, 1.0))])
    scene.add("Help Text", (0, 0, 0), [text(
        "W and S walk, A and D step sideways, Q and E turn, Space jumps, F or Ctrl shoots. No scripts: the First-person Shooter "
        "genre and 3D mode supply it all.", "BOTTOM_LEFT", 16, 24, 1.0, (0.9, 0.95, 1.0))])
    write_with_genre(scene, "first-person-range-3d.json", "first-person-shooter")


def main():
    platformer()
    action_rpg_valley()
    action_rpg_iso()
    platformer_3d()
    first_person_range()


if __name__ == "__main__":
    main()
