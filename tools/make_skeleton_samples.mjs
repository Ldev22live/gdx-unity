// Generates the skeleton animation samples: three scenes in assets/samples and their clips in assets/animations.
//   node tools/make_skeleton_samples.mjs
//
// A skeleton here is a hierarchy of plain entities: each *bone* is an empty entity placed on its joint, and the
// visible limb is a box entity child of the bone, offset so the bone rotates it about the joint. A clip then
// animates the bones' rotations by path ("Hips/Spine/Arm R/Forearm R"), and every child follows its parent (forward
// kinematics). Rotation channels are yaw (around Y), pitch (around X) and roll (around Z), in degrees.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "assets");

// ---- scene building ---------------------------------------------------------------------------------------------

/** Stable ids so regenerating does not churn the files. */
function idFor(scene, name) {
  const h = createHash("sha1").update(scene + "/" + name).digest("hex");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-5${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

function quatFromYaw(deg) {
  const r = (deg * Math.PI) / 360;
  return { x: 0, y: Math.sin(r), z: 0, w: Math.cos(r) };
}

class SceneBuilder {
  constructor(name) {
    this.name = name;
    this.entities = [];
    this.ids = new Map();
  }
  add(name, { parent = null, pos = [0, 0, 0], yaw = 0, scale = [1, 1, 1], components = [] } = {}) {
    const key = (parent ? this.ids.get(parent) + "/" : "") + name;
    const id = idFor(this.name, key);
    this.ids.set(name + "@" + (parent ?? ""), id);
    this.ids.set(name, id);
    this.entities.push({
      id, name, active: true, parentId: parent ? this.ids.get(parent) : null,
      transform: {
        position: { x: pos[0], y: pos[1], z: pos[2] }, rotation: quatFromYaw(yaw),
        scale: { x: scale[0], y: scale[1], z: scale[2] },
      },
      components,
    });
    return name;
  }
  json() {
    return JSON.stringify({ schemaVersion: 1, name: this.name, active: true, entities: this.entities }, null, 2) + "\n";
  }
}

const box = (sx, sy, sz, [r, g, b]) => ({
  type: "MeshPrimitiveComponent", enabled: true,
  data: { shape: "BOX", sizeX: sx, sizeY: sy, sizeZ: sz, colorR: r, colorG: g, colorB: b, colorA: 1 },
});
const sphere = (d, [r, g, b]) => ({
  type: "MeshPrimitiveComponent", enabled: true,
  data: { shape: "SPHERE", sizeX: d, sizeY: d, sizeZ: d, colorR: r, colorG: g, colorB: b, colorA: 1 },
});
const animator = (clip, wrap = "LOOP") => ({
  type: "AnimatorComponent", enabled: true, data: { clip, playOnStart: true, wrap, speed: 1 },
});

function environment(s, floorSize = 1600) {
  s.add("Sun", {
    pos: [0, 800, 400],
    components: [{ type: "DirectionalLightComponent", enabled: true, data: {
      directionX: -0.4, directionY: -1, directionZ: -0.6, colorR: 1, colorG: 0.96, colorB: 0.88, colorA: 1,
      intensity: 1, castShadows: true } }],
  });
  s.add("Sky Light", {
    components: [
      { type: "SkyLightComponent", enabled: true, data: {
        skyR: 0.62, skyG: 0.72, skyB: 0.88, groundR: 0.32, groundG: 0.29, groundB: 0.26, intensity: 0.7 } },
      { type: "ToneMappingComponent", enabled: true, data: { exposure: 1, operator: "ACES_FILMIC" } },
    ],
  });
  s.add("Floor", { pos: [0, -5, 0], components: [box(floorSize, 10, floorSize, [0.36, 0.42, 0.38])] });
}

function camera(s, pos, yaw) {
  s.add("Main Camera", {
    pos, yaw,
    components: [{ type: "CameraComponent", enabled: true, data: {
      projection: "PERSPECTIVE", orthographicHeight: 480, fieldOfView: 50, nearPlane: 1, farPlane: 50000 } }],
  });
}

/** A bone: an empty joint entity. */
function bone(s, name, parent, pos) {
  return s.add(name, { parent, pos });
}
/** A visible limb under a bone. Named "<bone> Mesh" so it never matches a bone path. */
function limb(s, boneName, size, offset, color, shape = "BOX") {
  return s.add(boneName + " Mesh", {
    parent: boneName, pos: offset,
    components: [shape === "SPHERE" ? sphere(size[0], color) : box(size[0], size[1], size[2], color)],
  });
}

// ---- clips ------------------------------------------------------------------------------------------------------

/** keys: [[time, value], ...] -> a track; every key eases (BEZIER) unless interpolation says otherwise. */
function track(target, property, keys, interpolation = "BEZIER") {
  return { target, property, keys: keys.map(([t, v]) => ({ t, v, i: interpolation })) };
}
const PITCH = "transform.rotation.pitch";
const ROLL = "transform.rotation.roll";
const YAW = "transform.rotation.yaw";
const POS_Y = "transform.position.y";

function clip(name, duration, tracks) {
  return JSON.stringify({ format: 1, name, duration, frameRate: 30, tracks }, null, 2) + "\n";
}

// ---- humanoid ---------------------------------------------------------------------------------------------------

const SKIN = [0.93, 0.76, 0.62];
const SHIRT = [0.25, 0.45, 0.85];
const PANTS = [0.2, 0.22, 0.3];

/**
 * Builds Hips > Spine > Chest > (Neck > Head, Arm L/R > Forearm L/R, ) and Hips > Thigh L/R > Shin L/R > Foot L/R
 * under an entity called "Hero". Bone origins are at the joints; the root "Hero" stands on the floor.
 * Facing +Z. Left/right are the hero's own (his right is world -X).
 */
function buildHumanoid(s, rootPos) {
  s.add("Hero", { pos: rootPos });
  bone(s, "Hips", "Hero", [0, 100, 0]);
  limb(s, "Hips", [44, 26, 26], [0, 0, 0], PANTS);
  bone(s, "Spine", "Hips", [0, 10, 0]);
  limb(s, "Spine", [40, 40, 24], [0, 20, 0], SHIRT);
  bone(s, "Chest", "Spine", [0, 40, 0]);
  limb(s, "Chest", [48, 36, 26], [0, 18, 0], SHIRT);
  bone(s, "Neck", "Chest", [0, 36, 0]);
  bone(s, "Head", "Neck", [0, 8, 0]);
  limb(s, "Head", [36, 36, 36], [0, 18, 0], SKIN, "SPHERE");
  for (const [side, sx] of [["R", -1], ["L", 1]]) {
    bone(s, `Arm ${side}`, "Chest", [sx * 30, 30, 0]);
    limb(s, `Arm ${side}`, [14, 44, 14], [0, -20, 0], SHIRT);
    bone(s, `Forearm ${side}`, `Arm ${side}`, [0, -42, 0]);
    limb(s, `Forearm ${side}`, [12, 42, 12], [0, -20, 0], SKIN);
    bone(s, `Thigh ${side}`, "Hips", [sx * 12, -8, 0]);
    limb(s, `Thigh ${side}`, [18, 48, 18], [0, -24, 0], PANTS);
    bone(s, `Shin ${side}`, `Thigh ${side}`, [0, -48, 0]);
    limb(s, `Shin ${side}`, [16, 46, 16], [0, -23, 0], PANTS);
    bone(s, `Foot ${side}`, `Shin ${side}`, [0, -46, 0]);
    limb(s, `Foot ${side}`, [18, 8, 30], [0, -4, 8], [0.15, 0.15, 0.15]);
  }
}

// ---- 1. wave ---------------------------------------------------------------------------------------------------

function waveScene() {
  const s = new SceneBuilder("Skeleton 1 - Wave");
  environment(s, 1200);
  camera(s, [0, 150, 420], 0);
  buildHumanoid(s, [0, 0, 0]);
  // The Animator sits on the root; track targets are paths of bone names below it.
  s.entities.find((e) => e.name === "Hero").components.push(animator("animations/skeleton/Wave.anim"));
  return s;
}

function waveClip() {
  const D = 2;
  const R = "Hips/Spine/Chest/Arm R";
  const F = R + "/Forearm R";
  return clip("Wave", D, [
    // Breathing: the whole spine rocks slightly and the hips bob. First and last key are equal so the loop is seamless.
    track("Hips", POS_Y, [[0, 100], [1, 97], [2, 100]]),
    track("Hips/Spine", PITCH, [[0, 0], [1, 3], [2, 0]]),
    track("Hips/Spine/Chest/Neck/Head", YAW, [[0, 0], [0.5, -12], [1.5, 12], [2, 0]]),
    // The waving arm: raise the upper arm once (roll about Z) ...
    track(R, ROLL, [[0, -10], [0.4, -140], [1.6, -140], [2, -10]]),
    // ... and swing only the forearm back and forth, which is what reads as a wave
    track(F, ROLL, [[0, 0], [0.4, 0], [0.7, -40], [1.0, 30], [1.3, -40], [1.6, 0], [2, 0]]),
    // The other arm hangs and sways a little
    track("Hips/Spine/Chest/Arm L", ROLL, [[0, 6], [1, 10], [2, 6]]),
  ]);
}

// ---- 2. walk ----------------------------------------------------------------------------------------------------

function walkScene() {
  const s = new SceneBuilder("Skeleton 2 - Walk Cycle");
  environment(s, 1600);
  // Side view: the camera is on +X and looks along -X, so the hero (facing +Z) walks across the screen
  camera(s, [520, 130, 0], 90);
  buildHumanoid(s, [0, 0, 0]);
  s.entities.find((e) => e.name === "Hero").components.push(animator("animations/skeleton/WalkCycle.anim"));
  return s;
}

function walkClip() {
  const D = 1;
  // Legs swing about X (pitch). A negative pitch swings a limb towards +Z, the way the hero faces.
  const thighR = [[0, -28], [0.25, 0], [0.5, 28], [0.75, 0], [1, -28]];
  const thighL = [[0, 28], [0.25, 0], [0.5, -28], [0.75, 0], [1, 28]];
  // The knee bends only while the leg swings forward (positive pitch folds the shin back), lifting the foot clear
  const shinR = [[0, 10], [0.25, 5], [0.5, 5], [0.75, 55], [1, 10]];
  const shinL = [[0, 5], [0.25, 55], [0.5, 10], [0.75, 5], [1, 5]];
  const armR = [[0, 24], [0.5, -24], [1, 24]]; // arms swing opposite to the leg on the same side
  const armL = [[0, -24], [0.5, 24], [1, -24]];
  return clip("WalkCycle", D, [
    // Two steps per cycle: the hips are lowest when both feet are down (0.25, 0.75) and highest mid-stance
    track("Hips", POS_Y, [[0, 98], [0.25, 103], [0.5, 98], [0.75, 103], [1, 98]]),
    track("Hips", ROLL, [[0, 3], [0.5, -3], [1, 3]]),
    track("Hips/Thigh R", PITCH, thighR),
    track("Hips/Thigh L", PITCH, thighL),
    track("Hips/Thigh R/Shin R", PITCH, shinR),
    track("Hips/Thigh L/Shin L", PITCH, shinL),
    track("Hips/Spine/Chest/Arm R", PITCH, armR),
    track("Hips/Spine/Chest/Arm L", PITCH, armL),
    track("Hips/Spine/Chest/Arm R/Forearm R", PITCH, [[0, -20], [0.5, -50], [1, -20]]),
    track("Hips/Spine/Chest/Arm L/Forearm L", PITCH, [[0, -50], [0.5, -20], [1, -50]]),
    // The torso counter-rotates against the hips
    track("Hips/Spine", YAW, [[0, -6], [0.5, 6], [1, -6]]),
  ]);
}

// ---- 3. tentacle (chain, follow-through) ------------------------------------------------------------------------

function tentacleScene() {
  const s = new SceneBuilder("Skeleton 3 - Tentacle");
  environment(s, 1200);
  camera(s, [0, 220, 560], 0);
  s.add("Tentacle", { pos: [0, 0, 0], components: [] });
  s.add("Base", { parent: "Tentacle", pos: [0, 0, 0], components: [box(110, 24, 110, [0.3, 0.3, 0.35])] });
  const segments = 6;
  let parent = "Tentacle";
  for (let i = 1; i <= segments; i++) {
    const name = `Segment ${i}`;
    const length = 60;
    const width = 40 - i * 4;
    bone(s, name, parent, i === 1 ? [0, 12, 0] : [0, length, 0]);
    limb(s, name, [width, length, width], [0, length / 2, 0], [0.75 - i * 0.05, 0.25 + i * 0.08, 0.55]);
    parent = name;
  }
  s.entities.find((e) => e.name === "Tentacle").components.push(animator("animations/skeleton/Tentacle.anim", "LOOP"));
  return s;
}

function tentacleClip() {
  const D = 3;
  const tracks = [];
  let path = "";
  for (let i = 1; i <= 6; i++) {
    path += (i === 1 ? "" : "/") + `Segment ${i}`;
    // Every segment runs the same wave but starts a little later than its parent: follow-through. The extra keys
    // at the loop ends repeat the first value so the loop is seamless.
    const lag = (i - 1) * 0.18;
    const wave = (t) => 22 * Math.sin(((t - lag) / D) * Math.PI * 2);
    const times = [0, 0.375, 0.75, 1.125, 1.5, 1.875, 2.25, 2.625, 3];
    tracks.push(track(path, ROLL, times.map((t) => [t, Math.round(wave(t) * 10) / 10])));
    tracks.push(track(path, PITCH, times.map((t) => [t, Math.round(10 * 9 * Math.cos(((t - lag) / D) * Math.PI * 2)) / 10])));
  }
  return clip("Tentacle", D, tracks);
}

// ---- write ----------------------------------------------------------------------------------------------------

function write(rel, text) {
  const path = join(root, rel);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, text);
  console.log("wrote", rel);
}

write("samples/skeleton-wave-3d.json", waveScene().json());
write("samples/skeleton-walk-3d.json", walkScene().json());
write("samples/skeleton-tentacle-3d.json", tentacleScene().json());
write("animations/skeleton/Wave.anim", waveClip());
write("animations/skeleton/WalkCycle.anim", walkClip());
write("animations/skeleton/Tentacle.anim", tentacleClip());
