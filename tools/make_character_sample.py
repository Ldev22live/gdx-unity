#!/usr/bin/env python3
"""Generates assets/samples/character-creator-3d.json (TD-053): three characters made with the character creator, side by side, each
playing one of the humanoid animation templates baked into its model. Bake the characters first (see assets/samples/README.md):
    gradlew :editor-character:bakeCharacter --args="<assetRoot> Tall_Man gender=1 age=30 muscle=0.7 height=1.08 skin=0.78,0.58,0.45 slider:head/head-square=0.5"
    gradlew :editor-character:bakeCharacter --args="<assetRoot> Short_Woman gender=0 age=28 muscle=0.4 weight=0.4 height=0.93 skin=0.9,0.7,0.58"
    gradlew :editor-character:bakeCharacter --args="<assetRoot> Old_Man gender=0.9 age=78 muscle=0.2 weight=0.7 height=0.97 skin=0.8,0.64,0.55"
Usage (from the repository root):  python tools/make_character_sample.py
"""
import json
import os
import uuid

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUT = os.path.join(ROOT, "assets", "samples", "character-creator-3d.json")
NAMESPACE = uuid.UUID("c53d1e0e-53d4-4c1e-9d6b-0a7a3d2f4e53")
entities = []


def vec(x, y, z):
    return {"x": float(x), "y": float(y), "z": float(z)}


def entity(name, pos, comps, rot=(0.0, 0.0, 0.0, 1.0)):
    entities.append({"id": str(uuid.uuid5(NAMESPACE, name)), "name": name, "active": True, "parentId": None,
                     "transform": {"position": vec(*pos), "rotation": {"x": rot[0], "y": rot[1], "z": rot[2], "w": rot[3]}, "scale": vec(1, 1, 1)},
                     "components": comps})


def comp(kind, data):
    return {"type": kind, "enabled": True, "data": data}


entity("Sun", (0, 800, 400), [comp("DirectionalLightComponent", {
    "directionX": -0.3, "directionY": -1.0, "directionZ": -0.5, "colorR": 1.0, "colorG": 0.96, "colorB": 0.88, "colorA": 1.0,
    "intensity": 1.0, "castShadows": True})])
entity("Sky Light", (0, 0, 0), [comp("SkyLightComponent", {"skyR": 0.62, "skyG": 0.72, "skyB": 0.88, "groundR": 0.32, "groundG": 0.29, "groundB": 0.26,
    "intensity": 1.0}), comp("ToneMappingComponent", {"exposure": 1.0, "operator": "ACES_FILMIC"})])
# The characters face +Z, so the camera stands on +Z; an unrotated camera looks down -Z, towards them
entity("Main Camera", (0, 130, 520), [comp("CameraComponent", {
    "projection": "PERSPECTIVE", "orthographicHeight": 480.0, "fieldOfView": 55.0, "nearPlane": 1.0, "farPlane": 50000.0})])
entity("Floor", (0, -5, 0), [comp("MeshPrimitiveComponent", {
    "shape": "BOX", "sizeX": 900.0, "sizeY": 10.0, "sizeZ": 700.0, "colorR": 0.42, "colorG": 0.55, "colorB": 0.42, "colorA": 1.0})])
for name, x, clip in (("Tall_Man", -160, "Walk"), ("Short_Woman", 0, "Idle"), ("Old_Man", 160, "Walk")):
    entity(name.replace("_", " "), (x, 0, 0), [comp("ModelComponent", {
        "model": "characters/%s.gltf" % name, "importScale": 100.0, "animation": clip, "loop": True, "playOnStart": True, "speed": 1.0})])
entity("Help Text", (0, 0, 0), [comp("TextComponent", {
    "text": "Character creator: three characters built in the Animations tab > Character, each playing a humanoid animation template",
    "colorR": 0.8, "colorG": 0.85, "colorB": 0.95, "colorA": 1, "scale": 1.2, "anchor": "BOTTOM_LEFT", "offsetX": 16.0, "offsetY": 32.0})])

with open(OUT, "w", encoding="utf8", newline="") as f:
    f.write(json.dumps({"schemaVersion": 1, "name": "Character Creator 3D", "active": True, "entities": entities}, indent=2).replace("\n", "\r\n") + "\r\n")
print("wrote", OUT)
