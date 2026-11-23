import { Quaternion, Vector3 } from "@babylonjs/core";
import type { GameEntity } from "./GameEntity";
import type { Physics3D } from "./physics3d/Physics3D";
import { ALL_LAYERS } from "./physics3d/types";
import { bool, num, str, type ComponentData } from "../scene/types";

export type CameraRigMode = "FOLLOW" | "ORBIT" | "FIRST_PERSON";

/** How far in front of a wall the occluded camera stops. */
const DEFAULT_OCCLUSION_MARGIN = 20;
/**
 * The occlusion ray starts at the pivot, usually inside the target's own shape, which it then reports at distance 0. It steps forward
 * and tries again, at most this often (as on the desktop): a target wider than about 2 x 16 x 10 units across the ray cannot be left, and
 * then the camera is not pulled in.
 */
const MAX_TARGET_SKIPS = 16;
/** How far the occlusion ray moves on after hitting the target itself. */
const SKIP_STEP = 10;
/** The pitch limits a rig may be given, in degrees either way. */
const PITCH_LIMIT = 89;

/**
 * Moves and turns the entity it is on, normally one with a camera, to follow another entity: the web counterpart of the desktop's
 * `CameraRigComponent` (TD-050, TD-064). It runs while the game plays, after the physics step, so it sees the pose a character or body
 * just reached. Keep the entity at the scene's root: the rig sets its position as a world position.
 *
 * - FOLLOW: stays at the follow offset from the target and looks at the target plus the pivot height; the follow rate smooths the
 *   movement, the same at any frame rate.
 * - ORBIT: third person. Sits {@link distance} from the pivot (target plus pivot height) opposite to where yaw and pitch look. A script turns
 *   it with {@link rotate}. With occlusion on, the camera is pulled in front of walls using the 3D physics.
 * - FIRST_PERSON: sits at the target plus the eye height, looks where the target faces turned by yaw, tilted by pitch.
 *
 * The rig reads no input. Angles are degrees: yaw 0 looks along -Z, positive yaw turns left; positive pitch looks up. Lengths are scene units.
 */
export class CameraRig {
  readonly mode: CameraRigMode;
  private target: GameEntity | null = null;
  private placed = false;
  private yawDegrees: number;
  private pitchDegrees: number;
  private distanceUnits: number;
  private minPitch: number;
  private maxPitch: number;
  private readonly minDistance: number;
  private readonly maxDistance: number;
  private readonly followOffset: Vector3;
  private readonly followRate: number;
  private readonly pivotHeight: number;
  private readonly eyeHeight: number;
  private readonly occlusion: boolean;
  private readonly occlusionMargin: number;
  private readonly occlusionMask: number;
  private readonly targetName: string;

  constructor(
    readonly entity: GameEntity,
    data: Record<string, unknown>,
  ) {
    this.mode = str(data, "mode", "FOLLOW") as CameraRigMode;
    this.targetName = str(data, "targetName", "").trim();
    this.followOffset = new Vector3(num(data, "followOffsetX", 0), num(data, "followOffsetY", 420), num(data, "followOffsetZ", 560));
    this.followRate = num(data, "followRate", 6);
    this.pivotHeight = num(data, "pivotHeight", 80);
    this.eyeHeight = num(data, "eyeHeight", 70);
    this.minPitch = num(data, "minPitch", -80);
    this.maxPitch = num(data, "maxPitch", 60);
    this.minDistance = num(data, "minDistance", 100);
    this.maxDistance = num(data, "maxDistance", 1200);
    this.yawDegrees = wrapDegrees(num(data, "yaw", 0));
    this.pitchDegrees = clamp(num(data, "pitch", -20), this.minPitch, this.maxPitch);
    this.distanceUnits = clamp(num(data, "distance", 400), this.minDistance, this.maxDistance);
    this.occlusion = bool(data, "occlusionEnabled", false);
    this.occlusionMargin = num(data, "occlusionMargin", DEFAULT_OCCLUSION_MARGIN);
    this.occlusionMask = num(data, "occlusionMask", ALL_LAYERS) & ALL_LAYERS;
  }

  /** Whether {@code component} is an enabled Camera Rig, for the player to make a rig of. */
  static isRig(component: ComponentData): boolean {
    return component.type === "CameraRigComponent" && component.enabled;
  }

  get yaw(): number {
    return this.yawDegrees;
  }

  get pitch(): number {
    return this.pitchDegrees;
  }

  get distance(): number {
    return this.distanceUnits;
  }

  /** The entity being followed, once the game has started and a target name is set; otherwise null. */
  getTarget(): GameEntity | null {
    return this.target;
  }

  /** Degrees; wrapped to -180..180. */
  setYaw(yaw: number): void {
    requireFinite(yaw, "yaw");
    this.yawDegrees = wrapDegrees(yaw);
  }

  /** Degrees; held between the pitch limits. */
  setPitch(pitch: number): void {
    requireFinite(pitch, "pitch");
    this.pitchDegrees = clamp(pitch, this.minPitch, this.maxPitch);
  }

  /** Turns by the given amounts; the way a script applies mouse or stick movement. */
  rotate(yawDegrees: number, pitchDegrees: number): void {
    this.setYaw(this.yawDegrees + yawDegrees);
    this.setPitch(this.pitchDegrees + pitchDegrees);
  }

  /** Both between -89 and 89, minimum not above maximum; the current pitch is brought inside. */
  setPitchLimits(minPitch: number, maxPitch: number): void {
    requireFinite(minPitch, "minPitch");
    requireFinite(maxPitch, "maxPitch");
    if (minPitch < -PITCH_LIMIT || maxPitch > PITCH_LIMIT || minPitch > maxPitch) {
      throw new Error(`Pitch limits must satisfy -89 <= min <= max <= 89: ${minPitch}, ${maxPitch}`);
    }
    this.minPitch = minPitch;
    this.maxPitch = maxPitch;
    this.pitchDegrees = clamp(this.pitchDegrees, minPitch, maxPitch);
  }

  /** Held between the distance limits. */
  setDistance(distance: number): void {
    requireFinite(distance, "distance");
    this.distanceUnits = clamp(distance, this.minDistance, this.maxDistance);
  }

  /**
   * Finds the target; call once when the game starts.
   *
   * @throws Error naming the rig's entity when the target name matches no entity
   */
  start(findByName: (name: string) => GameEntity | undefined): void {
    this.target = null;
    this.placed = false;
    if (this.targetName === "") return;
    this.target = findByName(this.targetName) ?? null;
    if (!this.target) {
      throw new Error(`Camera Rig on ${this.entity.name} follows '${this.targetName}' but no entity has that name. Fix the target name in the Details panel.`);
    }
  }

  /** Moves and turns the entity for this frame. */
  update(deltaSeconds: number, physics: Physics3D | null): void {
    const target = this.target;
    if (!target) return;
    const at = target.getWorldPosition();
    switch (this.mode) {
      case "FOLLOW":
        this.updateFollow(at, deltaSeconds);
        break;
      case "ORBIT":
        this.updateOrbit(at, physics);
        break;
      case "FIRST_PERSON":
        this.updateFirstPerson(at, target);
        break;
    }
    this.placed = true;
  }

  private updateFollow(target: Vector3, deltaSeconds: number): void {
    const wanted = target.add(this.followOffset);
    const current = this.entity.getWorldPosition();
    if (this.placed && this.followRate > 0) {
      Vector3.LerpToRef(current, wanted, 1 - Math.exp(-this.followRate * deltaSeconds), current);
    } else {
      current.copyFrom(wanted);
    }
    this.place(current);
    const toPivot = new Vector3(target.x, target.y + this.pivotHeight, target.z).subtract(current);
    if (toPivot.lengthSquared() > 1e-6) this.turn(yawOf(toPivot), pitchOf(toPivot));
  }

  private updateOrbit(target: Vector3, physics: Physics3D | null): void {
    const pivot = new Vector3(target.x, target.y + this.pivotHeight, target.z);
    const direction = forward(this.yawDegrees, this.pitchDegrees);
    const length = this.occlusion ? this.occludedDistance(pivot, direction, this.distanceUnits, physics) : this.distanceUnits;
    this.place(pivot.subtract(direction.scale(length)));
    this.turn(this.yawDegrees, this.pitchDegrees);
  }

  private updateFirstPerson(target: Vector3, targetEntity: GameEntity): void {
    this.place(new Vector3(target.x, target.y + this.eyeHeight, target.z));
    const facing = yawOf(forward(0, 0).rotateByQuaternionToRef(targetEntity.getWorldRotation(), new Vector3()));
    this.turn(wrapDegrees(facing + this.yawDegrees), this.pitchDegrees);
  }

  /**
   * How far back from the pivot the camera may sit: the wanted distance, or the distance to the first thing in the way (that is not the
   * target) minus the margin, never below 0.
   */
  private occludedDistance(pivot: Vector3, direction: Vector3, wanted: number, physics: Physics3D | null): number {
    if (!physics) return wanted;
    const back = direction.scale(-1);
    const origin = pivot.clone();
    let travelled = 0;
    for (let skips = 0; skips <= MAX_TARGET_SKIPS; skips++) {
      const hit = physics.raycast(origin, back, wanted - travelled + this.occlusionMargin, this.occlusionMask);
      if (!hit) return wanted;
      if (hit.entity === this.target || hit.entity === this.entity) {
        // The ray started inside the target (or hit it on the way out): carry on from just beyond that point
        travelled += hit.distance + SKIP_STEP;
        origin.copyFrom(hit.point).addInPlace(back.scale(SKIP_STEP));
        if (travelled >= wanted) return wanted;
        continue;
      }
      return Math.max(0, Math.min(wanted, travelled + hit.distance - this.occlusionMargin));
    }
    return wanted;
  }

  private place(world: Vector3): void {
    this.entity.node.position.copyFrom(world);
  }

  private turn(yaw: number, pitch: number): void {
    this.entity.node.rotationQuaternion = Quaternion.RotationYawPitchRoll((yaw * Math.PI) / 180, (pitch * Math.PI) / 180, 0);
  }
}

/** Unit vector a camera with this yaw and pitch looks along (yaw 0 looks along -Z, positive yaw turns left, positive pitch looks up). */
export function forward(yawDegrees: number, pitchDegrees: number): Vector3 {
  const yaw = (yawDegrees * Math.PI) / 180;
  const pitch = (pitchDegrees * Math.PI) / 180;
  const horizontal = Math.cos(pitch);
  return new Vector3(-Math.sin(yaw) * horizontal, Math.sin(pitch), -Math.cos(yaw) * horizontal);
}

/** Yaw in degrees of a direction, the inverse of {@link forward} for the horizontal part. */
export function yawOf(direction: Vector3): number {
  return (Math.atan2(-direction.x, -direction.z) * 180) / Math.PI;
}

/** Pitch in degrees of a direction; positive looks up. */
export function pitchOf(direction: Vector3): number {
  return (Math.atan2(direction.y, Math.hypot(direction.x, direction.z)) * 180) / Math.PI;
}

function wrapDegrees(degrees: number): number {
  let wrapped = degrees % 360;
  if (wrapped > 180) wrapped -= 360;
  else if (wrapped < -180) wrapped += 360;
  return wrapped;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function requireFinite(value: number, name: string): void {
  if (!Number.isFinite(value)) throw new Error(`${name} must be a finite number: ${value}`);
}
