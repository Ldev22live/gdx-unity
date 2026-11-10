#!/usr/bin/env python3
"""Converts a subset of MakeHuman's CC0 data into the compact files of Forgeroom's character creator (TD-053).

Reads (read-only) from a MakeHuman 1.x installation: data/3dobjs/base.npz, data/targets.npz, data/modifiers/modeling_modifiers.json,
data/rigs/game_engine.mhskel and data/rigs/default_weights.mhw. Writes assets/characters/makehuman/ (mesh.bin, targets.bin,
sliders.json, rig.json, weights.bin, LICENSE-ASSETS.txt, CREDITS.md). Nothing of MakeHuman's program code is read or used: only its
data, which its license.txt releases under CC0 1.0.

Usage (from the repository root):
    python tools/convert_makehuman_assets.py "<folder that contains makehuman.py>"
Needs Python 3 with numpy. Deterministic: running it again gives the same files.

File formats (all little endian), also described by CharacterData in engine-character:
  mesh.bin    "FGCM" u32 version=1, u32 baseVertexCount, f32[3*base] positions in MakeHuman units (0.1 m),
              u32 renderVertexCount, then per render vertex i32 sourceVertex + f32 u + f32 v, u32 triangleCount, i32[3*tri]
  targets.bin "FGCT" u32 version=1, u32 count, then per target: u16 nameLength, utf8 name, u32 entries, u16[entries] vertex,
              f16[3*entries] offsets (IEEE half floats, MakeHuman units)
  weights.bin "FGCW" u32 version=1, u32 baseVertexCount, then per base vertex: u8 count (0..4), then count x (u8 bone, f32 weight)
"""
import io
import json
import os
import struct
import sys
import zipfile

import numpy as np

# Groups of targets that are left out: not needed for a body/face creator, or not wanted (TD-053 open question 1)
EXCLUDED_TARGET_GROUPS = {"genitals", "expression", "asym", "measure", "eyebrows", "eyes"}
# Macro anchors. The body of each gender and age comes from three ethnic variants, blended equally (the "universal" average-build
# targets of that gender and age are empty: the base mesh is that body). Muscle and weight come from the young adult's strong/weak and
# heavy/lean variants, which the code adds in proportion (TD-053). The other 56 muscle and weight combinations are not needed.
MACRO_GENDERS = ("male", "female")
MACRO_AGES = ("baby", "child", "young", "old")
MACRO_ETHNICITIES = ("african", "asian", "caucasian")
KEPT_GROUP = "body"
# targets.npz stores offsets as int16 in thousandths of a unit (checked: a baby target moves the head about 10 units)
FIXED_POINT = 1000.0


def read_npy(blob):
    return np.load(io.BytesIO(blob), allow_pickle=False)


def main(source):
    data = os.path.join(source, "data")
    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "characters", "makehuman")
    os.makedirs(out_dir, exist_ok=True)
    read_files = []

    def note(path):
        read_files.append(os.path.relpath(path, source).replace("\\", "/"))
        return path

    # ---- base mesh --------------------------------------------------------------------------------------------------
    base = np.load(note(os.path.join(data, "3dobjs", "base.npz")))
    coord = base["coord"].astype("<f4")
    fvert, fuvs, group = base["fvert"], base["fuvs"], base["group"]
    texco = base["texco"].astype("<f4")
    names_blob = bytes(base["fgstr"].tobytes())
    offsets = list(base["fgidx"]) + [len(names_blob)]
    group_names = [names_blob[offsets[i]:offsets[i + 1]].decode("ascii") for i in range(len(offsets) - 1)]
    body_group = group_names.index(KEPT_GROUP)
    render_vertices = {}
    triangles = []
    for face in np.nonzero(group == body_group)[0]:
        corners = [(int(fvert[face][k]), int(fuvs[face][k])) for k in range(4)]
        ids = []
        for corner in corners:
            if corner not in render_vertices:
                render_vertices[corner] = len(render_vertices)
            ids.append(render_vertices[corner])
        # Quads are split along one diagonal; the mesh's own corner order keeps them counter-clockwise
        triangles += [ids[0], ids[1], ids[2], ids[0], ids[2], ids[3]]
    with open(os.path.join(out_dir, "mesh.bin"), "wb") as f:
        f.write(b"FGCM" + struct.pack("<II", 1, len(coord)))
        f.write(coord.tobytes())
        ordered = sorted(render_vertices.items(), key=lambda kv: kv[1])
        f.write(struct.pack("<I", len(ordered)))
        for (vertex, uv), _ in ordered:
            f.write(struct.pack("<iff", vertex, float(texco[uv][0]), float(texco[uv][1])))
        f.write(struct.pack("<I", len(triangles) // 3))
        f.write(np.array(triangles, dtype="<i4").tobytes())
    print("mesh: %d base vertices, %d render vertices, %d triangles" % (len(coord), len(render_vertices), len(triangles) // 3))

    # ---- targets ----------------------------------------------------------------------------------------------------
    archive = zipfile.ZipFile(note(os.path.join(data, "targets.npz")))
    available = {n[len("targets/"):-len(".index.npy")] for n in archive.namelist() if n.endswith(".index.npy")}
    modifiers = json.load(open(note(os.path.join(data, "modifiers", "modeling_modifiers.json")), encoding="utf8"))
    wanted = set()
    sliders = []
    for entry in modifiers:
        group_name = entry["group"]
        if group_name in EXCLUDED_TARGET_GROUPS or group_name == "macrodetails":
            continue
        for modifier in entry["modifiers"]:
            if "target" not in modifier:
                continue  # macro variables (gender, age, ...) are handled by the code, not as plain targets
            target = modifier["target"]
            if "min" in modifier:
                low = "%s/%s-%s" % (group_name, target, modifier["min"])
                high = "%s/%s-%s" % (group_name, target, modifier["max"])
                if low in available and high in available:
                    wanted.update([low, high])
                    sliders.append({"id": "%s/%s" % (group_name, target), "group": group_name, "label": target, "min": low, "max": high})
            else:
                single = "%s/%s" % (group_name, target)
                if single in available:
                    wanted.add(single)
                    sliders.append({"id": single, "group": group_name, "label": target, "max": single})
    macros = []
    for gender in MACRO_GENDERS:
        for age in MACRO_AGES:
            for ethnicity in MACRO_ETHNICITIES:
                macros.append("macrodetails/%s-%s-%s" % (ethnicity, gender, age))
        for build in ("minmuscle-averageweight", "maxmuscle-averageweight", "averagemuscle-minweight", "averagemuscle-maxweight"):
            macros.append("macrodetails/universal-%s-young-%s" % (gender, build))
    missing = [m for m in macros if m not in available]
    if missing:
        sys.exit("macro targets missing: %s" % missing)
    wanted.update(macros)
    # unique and ordered, so the file is deterministic
    ordered_targets = sorted(wanted)
    with open(os.path.join(out_dir, "targets.bin"), "wb") as f:
        f.write(b"FGCT" + struct.pack("<II", 1, len(ordered_targets)))  # version 1: u16 vertex numbers, f16 offsets
        for name in ordered_targets:
            index = read_npy(archive.read("targets/%s.index.npy" % name)).astype("<u2")
            # The archive keeps offsets as int16 thousandths of a MakeHuman unit; half floats are precise enough for the sub-millimeter result
            vector = (read_npy(archive.read("targets/%s.vector.npy" % name)).astype("<f4") / FIXED_POINT).astype("<f2")
            encoded = name.encode("utf8")
            f.write(struct.pack("<H", len(encoded)) + encoded + struct.pack("<I", len(index)))
            f.write(index.tobytes())
            f.write(vector.reshape(-1).tobytes())
    unique = {}
    for slider in sliders:
        unique.setdefault(slider["id"], slider)
    sliders = sorted(unique.values(), key=lambda s: (s["group"], s["id"]))
    with open(os.path.join(out_dir, "sliders.json"), "w", encoding="utf8", newline="\n") as f:
        json.dump({"version": 1, "sliders": sliders}, f, indent=1)
    print("targets: %d (%d sliders, %d macro anchors)" % (len(ordered_targets), len(sliders), len(macros)))

    # ---- rig --------------------------------------------------------------------------------------------------------
    rig = json.load(open(note(os.path.join(data, "rigs", "game_engine.mhskel")), encoding="utf8"))
    bone_names = sorted(rig["bones"], key=lambda n: (depth(rig["bones"], n), n))
    bones = []
    for name in bone_names:
        bone = rig["bones"][name]
        bones.append({"name": name, "parent": bone["parent"], "head": rig["joints"][bone["head"]], "tail": rig["joints"][bone["tail"]]})
    with open(os.path.join(out_dir, "rig.json"), "w", encoding="utf8", newline="\n") as f:
        json.dump({"version": 1, "name": rig["name"], "license": rig["license"], "copyright": rig["copyright"], "bones": bones}, f, indent=1)

    # ---- skin weights, merged from the default rig's bones onto the game rig's --------------------------------------
    default_weights = json.load(open(note(os.path.join(data, "rigs", "default_weights.mhw")), encoding="utf8"))["weights"]
    per_vertex = [dict() for _ in range(len(coord))]
    for index, name in enumerate(bone_names):
        bone = rig["bones"][name]
        sources = list(bone.get("reference") or []) + list(bone.get("weights_reference") or [])
        for source_name in sources:
            for vertex, weight in default_weights.get(source_name, []):
                per_vertex[vertex][index] = per_vertex[vertex].get(index, 0.0) + weight
    # Weight groups of the default rig that no game bone refers to (jaw, lips, eyelids, tongue ... and the breasts) move with the nearest
    # bone that exists: the head, or the chest for the breasts
    referenced = set()
    for name in bone_names:
        bone = rig["bones"][name]
        referenced.update(bone.get("reference") or [])
        referenced.update(bone.get("weights_reference") or [])
    for group_name, entries in default_weights.items():
        if group_name in referenced:
            continue
        target_bone = bone_names.index("spine_03" if group_name.startswith("breast") else "head")
        for vertex, weight in entries:
            per_vertex[vertex][target_bone] = per_vertex[vertex].get(target_bone, 0.0) + weight
    with open(os.path.join(out_dir, "weights.bin"), "wb") as f:
        f.write(b"FGCW" + struct.pack("<II", 1, len(coord)))
        for influences in per_vertex:
            best = sorted(influences.items(), key=lambda kv: (-kv[1], kv[0]))[:4]
            total = sum(w for _, w in best)
            f.write(struct.pack("<B", len(best)))
            for bone_index, weight in sorted(best):
                f.write(struct.pack("<Bf", bone_index, weight / total))
    covered = sum(1 for v in per_vertex if v)
    print("rig: %d bones, weights for %d of %d vertices" % (len(bones), covered, len(coord)))

    # ---- licence and credits ----------------------------------------------------------------------------------------
    with open(os.path.join(out_dir, "LICENSE-ASSETS.txt"), "w", encoding="utf8", newline="\n") as f:
        f.write("The files in this folder are converted from the MakeHuman community project's bundled assets (base mesh, morph\n"
                "targets, game_engine rig and skin weights), which MakeHuman releases under CC0 1.0 Universal\n"
                "(https://creativecommons.org/publicdomain/zero/1.0/). Authors named in the sources: MakeHuman Team,\n"
                "Data Collection AB, Joel Palmius, Jonas Hauquier. Only data was used; none of MakeHuman's AGPL program code.\n"
                "Source: https://www.makehumancommunity.org/\n")
    with open(os.path.join(out_dir, "CREDITS.md"), "w", encoding="utf8", newline="\n") as f:
        f.write("# Character creator data (TD-053)\n\nConverted by `tools/convert_makehuman_assets.py` from a MakeHuman 1.x installation. "
                "CC0 1.0 (see `LICENSE-ASSETS.txt`).\n\n## Files read\n\n" + "\n".join("- `%s`" % n for n in sorted(set(read_files))) +
                "\n\n## Targets converted\n\n" + "\n".join("- `%s`" % n for n in ordered_targets) + "\n")
    print("wrote", os.path.normpath(out_dir))


def depth(bones, name):
    d = 0
    while bones[name]["parent"] is not None:
        name = bones[name]["parent"]
        d += 1
    return d


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
