#!/usr/bin/env python3
"""Generates the animation sample clips and scene for the Animations tab.

Writes, deterministically:
  assets/animations/humanoid/{Wave,Crouch,Punch,Dance,LookAround}.anim
      more humanoid templates: they use the same part names as Idle, Walk, Run, Jump and Fall, so they play on the humanoid sample,
      appear first in the Clip Library and can be baked into a character made in the character creator (TD-053)
  assets/animations/samples/{SpinCube,DoorSwing,Pendulum,ColorPulse,PulsingLight,CameraSway}.anim
      general clips for any entity: a spin, a swing, a color change, a light pulse, a camera sway
  assets/samples/animation-samples-3d.json
      a scene that plays the general clips, one entity each

Angles are degrees, lengths scene units. Rotation conventions (right hand rule, characters face +Z, left is +X): a negative pitch swings
a hanging limb forward, a positive pitch leans an upright part forward; roll about Z swings a limb sideways (positive = towards +X).

Usage (from the repository root):  python tools/make_animation_samples.py
"""
import json
import os
import uuid

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
HUMANOID = os.path.join(ROOT, "assets", "animations", "humanoid")
SAMPLES = os.path.join(ROOT, "assets", "animations", "samples")
SCENE = os.path.join(ROOT, "assets", "samples", "animation-samples-3d.json")

YAW = "transform.rotation.yaw"
PITCH = "transform.rotation.pitch"
ROLL = "transform.rotation.roll"
POS_Y = "transform.position.y"
POS_X = "transform.position.x"

# Paths of the humanoid parts, as the existing clips name them
HIPS = "Hips"
SPINE = "Hips/Spine"
CHEST = "Hips/Spine/Chest"
NECK = "Hips/Spine/Chest/Neck"
HEAD = "Hips/Spine/Chest/Neck/Head"
ARM_L = "Hips/Spine/Chest/Arm L"
ARM_R = "Hips/Spine/Chest/Arm R"
FOREARM_L = ARM_L + "/Forearm L"
FOREARM_R = ARM_R + "/Forearm R"
THIGH_L = "Hips/Thigh L"
THIGH_R = "Hips/Thigh R"
SHIN_L = THIGH_L + "/Shin L"
SHIN_R = THIGH_R + "/Shin R"

HIPS_HEIGHT = 100.0  # the humanoid's hips at rest (HumanoidTemplates.TEMPLATE_HIPS_HEIGHT)


def clip(name, duration, tracks, frame_rate=30):
    """tracks: (target, property, [(time, value)], interpolation)"""
    return {
        "format": 1, "name": name, "duration": duration, "frameRate": frame_rate,
        "tracks": [{"target": t, "property": p, "keys": [{"t": round(k[0], 4), "v": round(k[1], 4), "i": i} for k in keys]}
                   for (t, p, keys, i) in tracks],
    }


def loop(values, duration):
    """Evenly spaced keys over the duration; the last value repeats the first so the loop is seamless."""
    n = len(values)
    keys = [(duration * i / n, v) for i, v in enumerate(values)]
    keys.append((duration, values[0]))
    return keys


def write_clip(folder, data):
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, data["name"] + ".anim"), "w", encoding="utf8", newline="") as f:
        f.write(json.dumps(data, indent=2).replace("\n", "\r\n") + "\r\n")


def humanoid_clips():
    clips = []
    # Wave: the right arm goes up and the forearm waves from side to side
    d = 2.0
    clips.append(clip("Wave", d, [
        (HIPS, POS_Y, [(0, HIPS_HEIGHT), (d, HIPS_HEIGHT)], "BEZIER"),
        (ARM_R, ROLL, [(0, 0), (0.35, -140), (1.65, -140), (d, 0)], "BEZIER"),
        (FOREARM_R, ROLL, [(0, 0), (0.35, -10), (0.6, -35), (0.85, 15), (1.1, -35), (1.35, 15), (1.65, -10), (d, 0)], "BEZIER"),
        (HEAD, YAW, [(0, 0), (0.6, -8), (1.4, -8), (d, 0)], "BEZIER"),
    ]))
    # Crouch: down on the heels and back up; the hips drop as far as the bent legs allow, so the feet stay on the floor
    d = 2.4
    clips.append(clip("Crouch", d, [
        # linear: a smoothed curve overshoots the held height and would sink the feet through the floor
        (HIPS, POS_Y, [(0, HIPS_HEIGHT), (0.8, 68), (1.6, 68), (d, HIPS_HEIGHT)], "LINEAR"),
        (SPINE, PITCH, [(0, 0), (0.8, 25), (1.6, 25), (d, 0)], "BEZIER"),
        (THIGH_L, PITCH, [(0, 0), (0.8, -70), (1.6, -70), (d, 0)], "BEZIER"),
        (THIGH_R, PITCH, [(0, 0), (0.8, -70), (1.6, -70), (d, 0)], "BEZIER"),
        (SHIN_L, PITCH, [(0, 0), (0.8, 80), (1.6, 80), (d, 0)], "BEZIER"),
        (SHIN_R, PITCH, [(0, 0), (0.8, 80), (1.6, 80), (d, 0)], "BEZIER"),
        (ARM_L, PITCH, [(0, 0), (0.8, -35), (1.6, -35), (d, 0)], "BEZIER"),
        (ARM_R, PITCH, [(0, 0), (0.8, -35), (1.6, -35), (d, 0)], "BEZIER"),
        (HEAD, PITCH, [(0, 0), (0.8, -20), (1.6, -20), (d, 0)], "BEZIER"),
    ]))
    # Punch: guard up, the right fist goes straight out with a twist of the body, then back to guard
    d = 0.9
    clips.append(clip("Punch", d, [
        (HIPS, POS_Y, [(0, 96), (0.2, 94), (0.35, 96), (d, 96)], "BEZIER"),
        (SPINE, YAW, [(0, 0), (0.2, 28), (0.5, 0), (d, 0)], "BEZIER"),
        (ARM_R, PITCH, [(0, -50), (0.2, -90), (0.5, -50), (d, -50)], "BEZIER"),
        (FOREARM_R, PITCH, [(0, -100), (0.2, 0), (0.5, -100), (d, -100)], "BEZIER"),
        (ARM_L, PITCH, [(0, -50), (d, -50)], "BEZIER"),
        (FOREARM_L, PITCH, [(0, -100), (d, -100)], "BEZIER"),
        (THIGH_L, PITCH, [(0, -12), (d, -12)], "BEZIER"),
        (THIGH_R, PITCH, [(0, 12), (d, 12)], "BEZIER"),
    ]))
    # Dance: bounce, sway and arms up in turn
    d = 2.0
    clips.append(clip("Dance", d, [
        (HIPS, POS_Y, loop([100, 94, 100, 94], d), "BEZIER"),
        (HIPS, YAW, loop([0, 14, 0, -14], d), "BEZIER"),
        (SPINE, YAW, loop([0, -10, 0, 10], d), "BEZIER"),
        (HEAD, ROLL, loop([0, 8, 0, -8], d), "BEZIER"),
        (ARM_L, ROLL, [(0, 10), (0.5, 120), (1.0, 10), (d, 10)], "BEZIER"),
        (ARM_R, ROLL, [(0, -10), (1.0, -10), (1.5, -120), (d, -10)], "BEZIER"),
        (THIGH_L, ROLL, loop([0, 6, 0, 0], d), "BEZIER"),
        (THIGH_R, ROLL, loop([0, 0, 0, -6], d), "BEZIER"),
    ]))
    # LookAround: head and neck sweep right, then left, the shoulders follow a little
    d = 4.0
    clips.append(clip("LookAround", d, [
        (HIPS, POS_Y, [(0, HIPS_HEIGHT), (d, HIPS_HEIGHT)], "BEZIER"),
        (NECK, YAW, [(0, 0), (1.0, -35), (2.0, 0), (3.0, 35), (d, 0)], "BEZIER"),
        (HEAD, YAW, [(0, 0), (1.0, -30), (2.0, 0), (3.0, 30), (d, 0)], "BEZIER"),
        (HEAD, PITCH, [(0, 0), (1.0, -5), (2.0, 0), (3.0, -5), (d, 0)], "BEZIER"),
        (SPINE, YAW, [(0, 0), (1.0, -8), (2.0, 0), (3.0, 8), (d, 0)], "BEZIER"),
    ]))
    return clips


def general_clips():
    clips = []
    clips.append(clip("SpinCube", 3.0, [("", YAW, [(0, 0), (3.0, 360)], "LINEAR"),
                                        ("", POS_Y, [(0, 40), (1.5, 80), (3.0, 40)], "BEZIER")]))
    clips.append(clip("DoorSwing", 2.4, [("", YAW, [(0, 0), (0.9, 100), (1.5, 100), (2.4, 0)], "BEZIER")]))
    clips.append(clip("Pendulum", 2.0, [("", ROLL, [(0, 30), (1.0, -30), (2.0, 30)], "BEZIER")]))
    clips.append(clip("ColorPulse", 3.0, [
        ("", "mesh.color.r", [(0, 0.9), (1.0, 0.2), (2.0, 0.2), (3.0, 0.9)], "BEZIER"),
        ("", "mesh.color.g", [(0, 0.2), (1.0, 0.8), (2.0, 0.2), (3.0, 0.2)], "BEZIER"),
        ("", "mesh.color.b", [(0, 0.2), (1.0, 0.3), (2.0, 0.9), (3.0, 0.2)], "BEZIER")]))
    clips.append(clip("PulsingLight", 1.6, [("", "light.point.intensity", [(0, 0.3), (0.8, 2.0), (1.6, 0.3)], "BEZIER")]))
    clips.append(clip("CameraSway", 6.0, [("", POS_X, [(0, -40), (3.0, 40), (6.0, -40)], "BEZIER"),
                                          ("", YAW, [(0, 4), (3.0, -4), (6.0, 4)], "BEZIER")]))
    return clips


NAMESPACE = uuid.UUID("d4a1e0e0-54d4-4c1e-9d6b-0a7a3d2f4e54")
entities = []


def entity(name, pos, comps):
    entities.append({"id": str(uuid.uuid5(NAMESPACE, name)), "name": name, "active": True, "parentId": None,
                     "transform": {"position": {"x": float(pos[0]), "y": float(pos[1]), "z": float(pos[2])},
                                   "rotation": {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0}, "scale": {"x": 1.0, "y": 1.0, "z": 1.0}},
                     "components": comps})


def comp(kind, data):
    return {"type": kind, "enabled": True, "data": data}


def mesh(shape, size, rgb):
    return comp("MeshPrimitiveComponent", {"shape": shape, "sizeX": float(size[0]), "sizeY": float(size[1]), "sizeZ": float(size[2]),
                                           "colorR": rgb[0], "colorG": rgb[1], "colorB": rgb[2], "colorA": 1.0})


def animator(path):
    return comp("AnimatorComponent", {"clip": "animations/samples/%s.anim" % path, "playOnStart": True, "wrap": "LOOP", "speed": 1})


def scene():
    entity("Sun", (0, 800, 400), [comp("DirectionalLightComponent", {
        "directionX": -0.3, "directionY": -1.0, "directionZ": -0.5, "colorR": 1.0, "colorG": 0.96, "colorB": 0.88, "colorA": 1.0,
        "intensity": 1.0, "castShadows": True})])
    entity("Main Camera", (0, 150, 650), [comp("CameraComponent", {
        "projection": "PERSPECTIVE", "orthographicHeight": 480.0, "fieldOfView": 55.0, "nearPlane": 1.0, "farPlane": 50000.0}),
        animator("CameraSway")])
    entity("Floor", (0, -5, 0), [mesh("BOX", (1100, 10, 600), (0.42, 0.55, 0.42))])
    entity("Spin Cube", (-400, 40, 0), [mesh("BOX", (80, 80, 80), (0.9, 0.6, 0.2)), animator("SpinCube")])
    entity("Swinging Panel", (-200, 100, 0), [mesh("BOX", (60, 200, 8), (0.55, 0.4, 0.3)), animator("DoorSwing")])
    entity("Rocking Bar", (0, 130, 0), [mesh("BOX", (16, 240, 16), (0.3, 0.55, 1.0)), animator("Pendulum")])
    entity("Color Pulse", (200, 45, 0), [mesh("SPHERE", (90, 90, 90), (0.9, 0.2, 0.2)), animator("ColorPulse")])
    entity("Pulsing Light", (400, 60, 60), [mesh("SPHERE", (30, 30, 30), (1.0, 0.95, 0.6)), comp("PointLightComponent", {
        "positionX": 0.0, "positionY": 0.0, "positionZ": 0.0, "colorR": 1.0, "colorG": 0.9, "colorB": 0.6, "colorA": 1.0,
        "intensity": 1.0, "range": 600.0}), animator("PulsingLight")])
    entity("Help Text", (0, 0, 0), [comp("TextComponent", {
        "text": "Animation samples: SpinCube, DoorSwing, Pendulum, ColorPulse, PulsingLight and CameraSway play here; open the Animations tab to preview, edit and reuse them",
        "colorR": 0.8, "colorG": 0.85, "colorB": 0.95, "colorA": 1, "scale": 1.2, "anchor": "BOTTOM_LEFT", "offsetX": 16.0, "offsetY": 32.0})])
    return {"schemaVersion": 1, "name": "Animation Samples 3D", "active": True, "entities": entities}


if __name__ == "__main__":
    for data in humanoid_clips():
        write_clip(HUMANOID, data)
    for data in general_clips():
        write_clip(SAMPLES, data)
    with open(SCENE, "w", encoding="utf8", newline="") as f:
        f.write(json.dumps(scene(), indent=2).replace("\n", "\r\n") + "\r\n")
    print("wrote 5 humanoid clips, 6 general clips and", SCENE)
