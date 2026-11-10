import { Quaternion, Vector3 } from "@babylonjs/core";
import type { GameEntity } from "./GameEntity";

/** Easing between a key and the next, named as in the desktop editor's `.anim` files. */
export type Interpolation = "CONSTANT" | "LINEAR" | "BEZIER";
export type WrapMode = "ONCE" | "LOOP" | "PING_PONG";

export interface Keyframe {
  t: number;
  v: number;
  i: Interpolation;
}

export interface Track {
  /** Child names from the animated entity, separated by "/"; empty means the entity itself. */
  target: string;
  property: string;
  keys: Keyframe[];
}

/** A clip as stored in a `.anim` file (format 1) by the desktop editor's Timeline. */
export interface Clip {
  name: string;
  duration: number;
  tracks: Track[];
}

/** Animator component settings from a scene file. */
export interface AnimatorSettings {
  clip: string;
  playOnStart: boolean;
  wrap: WrapMode;
  speed: number;
}

const DEGREES_TO_RADIANS = Math.PI / 180;
const RADIANS_TO_DEGREES = 180 / Math.PI;
const POSITION = ["transform.position.x", "transform.position.y", "transform.position.z"] as const;
const ROTATION = ["transform.rotation.yaw", "transform.rotation.pitch", "transform.rotation.roll"] as const;
const SCALE = ["transform.scale.x", "transform.scale.y", "transform.scale.z"] as const;

/** Parses the text of a `.anim` file; throws an Error naming what is wrong. */
export function parseClip(text: string): Clip {
  const root: unknown = JSON.parse(text);
  if (typeof root !== "object" || root === null) throw new Error("Not a valid animation file: expected a JSON object");
  const data = root as { name?: string; duration?: number; tracks?: Track[] };
  if (typeof data.duration !== "number" || !(data.duration > 0)) throw new Error("Animation file has no positive duration");
  const tracks = (data.tracks ?? []).map((track) => ({
    target: track.target ?? "",
    property: track.property,
    keys: [...(track.keys ?? [])].sort((a, b) => a.t - b.t),
  }));
  return { name: data.name ?? "Clip", duration: data.duration, tracks };
}

/** Value of a curve at {@code time}: stepped, linear, or the desktop's automatic-tangent cubic Hermite. */
export function evaluateCurve(keys: Keyframe[], time: number): number {
  if (keys.length === 0) throw new Error("Cannot evaluate a curve without keys");
  const first = keys[0]!;
  const last = keys[keys.length - 1]!;
  if (time <= first.t) return first.v;
  if (time >= last.t) return last.v;
  let i = 0;
  while (keys[i + 1]!.t < time) i++;
  const from = keys[i]!;
  const to = keys[i + 1]!;
  const span = to.t - from.t;
  const s = (time - from.t) / span;
  switch (from.i) {
    case "CONSTANT":
      return from.v;
    case "LINEAR":
      return from.v + (to.v - from.v) * s;
    default: {
      const s2 = s * s;
      const s3 = s2 * s;
      const m0 = slopeAt(keys, i) * span;
      const m1 = slopeAt(keys, i + 1) * span;
      return (2 * s3 - 3 * s2 + 1) * from.v + (s3 - 2 * s2 + s) * m0 + (-2 * s3 + 3 * s2) * to.v + (s3 - s2) * m1;
    }
  }
}

/** Automatic tangent: flat at both ends, through the neighbours inside. */
function slopeAt(keys: Keyframe[], i: number): number {
  if (i <= 0 || i >= keys.length - 1) return 0;
  const before = keys[i - 1]!;
  const after = keys[i + 1]!;
  return (after.v - before.v) / (after.t - before.t);
}

/** The entity {@code path} names under {@code root}; the first child with each name is used. Null if there is none. */
export function resolveTarget(root: GameEntity, path: string): GameEntity | null {
  if (path === "") return root;
  let current = root;
  for (const name of path.split("/")) {
    const next = current.children.find((child) => child.name === name);
    if (!next) return null;
    current = next;
  }
  return current;
}

/** Yaw, pitch and roll in degrees of a rotation, the way the desktop's Quaternion reports them. */
export function toYawPitchRoll(q: Quaternion): Vector3 {
  const yaw = Math.atan2(2 * (q.y * q.w + q.x * q.z), 1 - 2 * (q.y * q.y + q.x * q.x));
  const pitch = Math.asin(Math.max(-1, Math.min(1, 2 * (q.w * q.x - q.z * q.y))));
  const roll = Math.atan2(2 * (q.w * q.z + q.x * q.y), 1 - 2 * (q.x * q.x + q.z * q.z));
  return new Vector3(yaw * RADIANS_TO_DEGREES, pitch * RADIANS_TO_DEGREES, roll * RADIANS_TO_DEGREES);
}

/** Sets every track's property on the tree under {@code root} to its value at {@code time}; returns the tracks applied. */
export function applyClip(clip: Clip, time: number, root: GameEntity): number {
  let applied = 0;
  // The three rotation channels of one entity are combined and written once
  const rotations = new Map<GameEntity, Vector3>();
  for (const track of clip.tracks) {
    if (track.keys.length === 0) continue;
    const target = resolveTarget(root, track.target);
    if (!target) continue;
    const value = evaluateCurve(track.keys, time);
    const position = POSITION.indexOf(track.property as (typeof POSITION)[number]);
    const rotation = ROTATION.indexOf(track.property as (typeof ROTATION)[number]);
    const scale = SCALE.indexOf(track.property as (typeof SCALE)[number]);
    if (position >= 0) {
      setComponent(target.node.position, position, value);
    } else if (scale >= 0) {
      setComponent(target.node.scaling, scale, value);
    } else if (rotation >= 0) {
      let angles = rotations.get(target);
      if (!angles) {
        angles = toYawPitchRoll(target.node.rotationQuaternion ?? Quaternion.Identity());
        rotations.set(target, angles);
      }
      setComponent(angles, rotation, value);
    } else {
      continue; // mesh colour, light and model-time channels are not played by the web player
    }
    applied++;
  }
  for (const [entity, angles] of rotations) {
    entity.node.rotationQuaternion = Quaternion.RotationYawPitchRoll(
      angles.x * DEGREES_TO_RADIANS,
      angles.y * DEGREES_TO_RADIANS,
      angles.z * DEGREES_TO_RADIANS,
    );
  }
  return applied;
}

function setComponent(vector: Vector3, index: number, value: number): void {
  if (index === 0) vector.x = value;
  else if (index === 1) vector.y = value;
  else vector.z = value;
}

/** Plays a clip on an entity: the web version of the desktop's Animator component. */
export class Animator {
  private time: number;
  private direction = 1;
  private playing: boolean;

  constructor(
    readonly entity: GameEntity,
    readonly clip: Clip,
    readonly settings: AnimatorSettings,
  ) {
    this.time = settings.speed < 0 ? clip.duration : 0;
    this.playing = settings.playOnStart;
    applyClip(clip, this.time, entity);
  }

  get isPlaying(): boolean {
    return this.playing;
  }

  get currentTime(): number {
    return this.time;
  }

  /** Moves the play position by {@code delta} seconds, handles the clip ends per the wrap mode and applies the pose. */
  update(delta: number): void {
    if (!this.playing) return;
    const { duration } = this.clip;
    const next = this.time + delta * this.settings.speed * this.direction;
    switch (this.settings.wrap) {
      case "ONCE":
        if (next >= duration || next <= 0) this.playing = false;
        this.time = Math.max(0, Math.min(duration, next));
        break;
      case "LOOP": {
        const wrapped = next % duration;
        this.time = wrapped < 0 ? wrapped + duration : wrapped;
        break;
      }
      default: {
        let reflected = next;
        // Reflect off each end as many times as the step crosses it
        while (reflected > duration || reflected < 0) {
          reflected = reflected > duration ? 2 * duration - reflected : -reflected;
          this.direction = -this.direction;
        }
        this.time = reflected;
      }
    }
    applyClip(this.clip, this.time, this.entity);
  }
}

/** Where a scene's clip path (for example `animations/skeleton/Wave.anim`) is found by the browser. */
export type ClipSource = (path: string) => string;
export const defaultClipSource: ClipSource = (path) => path;

/** Reads the clip of an Animator component; throws an Error that names the path when it cannot be loaded. */
export async function loadClip(path: string, source: ClipSource = defaultClipSource): Promise<Clip> {
  const response = await fetch(source(path));
  if (!response.ok) throw new Error(`Animation ${path}: HTTP ${response.status}`);
  try {
    return parseClip(await response.text());
  } catch (error) {
    throw new Error(`Animation ${path}: ${error instanceof Error ? error.message : String(error)}`);
  }
}
