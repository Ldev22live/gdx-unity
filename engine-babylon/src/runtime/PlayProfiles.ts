import { Quaternion, Vector3 } from "@babylonjs/core";
import type { SceneFile } from "../scene/types";
import { CameraRig, forward, yawOf } from "./CameraRig";
import type { GameEntity, GameScene } from "./GameEntity";
import type { Input } from "./Input";
import type { Physics3D } from "./physics3d/Physics3D";

/**
 * The web's genre profiles (TD-110, roadmap A14): **Action RPG + 3D** and **Platformer + 2.5D**, ports of the desktop's
 * `PlayProfiles`, `GroundWalkMover`, `PlaneRunMover` and `ProfileCameraRig` with the same requirements, messages, speeds, camera
 * set-up and default keys. The other pairs the desktop plays stay desktop-only here: such a scene plays without a profile, and
 * says so. Pairs the desktop cannot play either are refused with the desktop's words.
 */

/** The desktop's `GameGenres` constants the two web profiles use (scene units, seconds, degrees). */
export const SIDE_CAMERA_DISTANCE = 900;
export const SIDE_CAMERA_HEIGHT = 150;
export const SIDE_CAMERA_FOLLOW_RATE = 6;
export const PLATFORMER_RUN_SPEED = 450;
export const PLATFORMER_JUMP_SPEED = 650;
export const THIRD_PERSON_DISTANCE = 520;
export const THIRD_PERSON_PIVOT_HEIGHT = 90;
export const THIRD_PERSON_PITCH = 18;
export const ACTION_RPG_WALK_SPEED = 400;
export const ACTION_RPG_JUMP_SPEED = 600;
/** How fast a walking player turns to face where it goes (the desktop's private `TURN_DEGREES_PER_SECOND`). */
export const PLAYER_TURN_DEGREES_PER_SECOND = 720;
/** How fast the turn keys turn the third-person camera (the desktop's `ProfileCameraRig.TURN_DEGREES_PER_SECOND`). */
export const CAMERA_TURN_DEGREES_PER_SECOND = 120;
/** How far above the spawn point the player's capsule starts (the desktop's `SPAWN_CLEARANCE`). */
const SPAWN_CLEARANCE = 1;
/** The desktop's default camera rig distance limits (`CameraRigComponent.DEFAULT_MIN_DISTANCE` / `DEFAULT_MAX_DISTANCE`). */
const DEFAULT_MIN_DISTANCE = 100;
const DEFAULT_MAX_DISTANCE = 1200;
/** Below this squared length a horizontal direction counts as none. */
const NO_DIRECTION = 1e-6;

/** The desktop's `InputMapping.defaults()`, as KeyboardEvent codes. */
export const DEFAULT_KEYS = {
  MOVE_LEFT: ["KeyA", "ArrowLeft"],
  MOVE_RIGHT: ["KeyD", "ArrowRight"],
  MOVE_FORWARD: ["KeyW", "ArrowUp"],
  MOVE_BACK: ["KeyS", "ArrowDown"],
  JUMP: ["Space"],
  TURN_LEFT: ["KeyQ"],
  TURN_RIGHT: ["KeyE"],
} as const;

const GENRE_LABELS: Record<string, string> = {
  "platformer": "Platformer / Side-scroller", "action-rpg": "Action RPG", "adventure": "Adventure",
  "first-person-shooter": "First-person Shooter", "third-person-shooter": "Third-person Shooter",
  "real-time-strategy": "Real-time Strategy", "turn-based-strategy": "Turn-based Strategy", "racing": "Racing",
  "puzzle": "Puzzle", "simulation": "Simulation", "sandbox": "Sandbox",
};
const MODE_LABELS: Record<string, string> = { "2d": "2D", "2.5d": "2.5D", "3d": "3D" };
/** The seven pairs the desktop plays (TD-088); only two of them play on the web (TD-110). */
const DESKTOP_PLAYABLE = new Set(["platformer|2d", "platformer|2.5d", "platformer|3d", "action-rpg|2.5d", "action-rpg|3d",
  "first-person-shooter|3d", "third-person-shooter|3d"]);

export interface WebProfile {
  genreLabel: string;
  modeLabel: string;
  camera: { kind: "THIRD_PERSON" | "SIDE_FOLLOW"; distance: number; height: number; pitchDegrees: number; followRate: number };
  movement: { kind: "GROUND_WALK" | "PLANE_RUN"; speed: number; jumpSpeed: number; turnDegreesPerSecond: number };
}

/** The web's profiles by `genre|mode` (TD-110). */
export const WEB_PROFILES: Record<string, WebProfile> = {
  "action-rpg|3d": {
    genreLabel: "Action RPG", modeLabel: "3D",
    camera: { kind: "THIRD_PERSON", distance: THIRD_PERSON_DISTANCE, height: THIRD_PERSON_PIVOT_HEIGHT, pitchDegrees: THIRD_PERSON_PITCH, followRate: 0 },
    movement: { kind: "GROUND_WALK", speed: ACTION_RPG_WALK_SPEED, jumpSpeed: ACTION_RPG_JUMP_SPEED, turnDegreesPerSecond: PLAYER_TURN_DEGREES_PER_SECOND },
  },
  "platformer|2.5d": {
    genreLabel: "Platformer / Side-scroller", modeLabel: "2.5D",
    camera: { kind: "SIDE_FOLLOW", distance: SIDE_CAMERA_DISTANCE, height: SIDE_CAMERA_HEIGHT, pitchDegrees: 0, followRate: SIDE_CAMERA_FOLLOW_RATE },
    movement: { kind: "PLANE_RUN", speed: PLATFORMER_RUN_SPEED, jumpSpeed: PLATFORMER_JUMP_SPEED, turnDegreesPerSecond: PLAYER_TURN_DEGREES_PER_SECOND },
  },
};

export type ProfileResolution =
  | { outcome: "NO_GENRE" }
  /** A pair the desktop plays but the web does not yet: Play runs without a profile and says so. */
  | { outcome: "DESKTOP_ONLY"; message: string }
  | { outcome: "REFUSED"; message: string }
  | { outcome: "PROFILE"; message: string; profile: WebProfile };

/** What Play does with the scene's saved genre and mode (the desktop's `PlayProfiles.resolve`, plus the web's desktop-only pairs). */
export function resolveProfile(file: SceneFile): ProfileResolution {
  const record = file as unknown as Record<string, unknown>;
  const genre = typeof record["gameGenre"] === "string" && record["gameGenre"] !== "" ? String(record["gameGenre"]) : null;
  if (genre === null) return { outcome: "NO_GENRE" };
  const mode = typeof record["gameMode"] === "string" && record["gameMode"] !== "" ? String(record["gameMode"]) : null;
  if (mode === null) {
    return { outcome: "REFUSED", message: "This scene has a genre but no saved Game Mode: choose 2D, 2.5D or 3D and save it." };
  }
  const genreLabel = GENRE_LABELS[genre];
  if (genreLabel === undefined) {
    return { outcome: "REFUSED", message: `This scene's genre '${genre}' is not known to this version of ForgeRoom. Choose another Game Genre, or none.` };
  }
  const key = `${genre}|${mode}`;
  const modeLabel = MODE_LABELS[mode] ?? mode;
  const profile = WEB_PROFILES[key];
  if (profile) return { outcome: "PROFILE", message: `Playing as ${genreLabel} + ${modeLabel}.`, profile };
  if (DESKTOP_PLAYABLE.has(key)) {
    return { outcome: "DESKTOP_ONLY", message: `${genreLabel} + ${modeLabel} plays on the desktop; the web plays Action RPG + 3D and `
      + "Platformer + 2.5D for now (TD-110), so this scene plays without its genre's movement and camera." };
  }
  return { outcome: "REFUSED", message: `${genreLabel} + ${modeLabel} cannot be played yet.` };
}

const enabled = (entity: GameEntity, type: string) => entity.components.some((c) => c.type === type && c.enabled);

/** The active entities with an enabled Player component, in scene order. */
export function players(game: GameScene): GameEntity[] {
  return game.entities.filter((e) => e.isActiveInHierarchy && enabled(e, "PlayerComponent"));
}

/** The active entities with an enabled Spawn Point, in scene order. */
export function spawnPoints(game: GameScene): GameEntity[] {
  return game.entities.filter((e) => e.isActiveInHierarchy && enabled(e, "SpawnPointComponent"));
}

/** A safe one-step fix the editor offers for a problem (the desktop's `ProfileFix`). */
export interface ProfileFix {
  kind: "MAKE_PLAYER" | "ADD_CHARACTER_CONTROLLER_3D" | "ADD_CAMERA";
  /** The entity it changes; absent for Add a camera. */
  entityId?: string;
  label: string;
}

/** Why Play cannot start, in the desktop's words, with the entity concerned and a fix when one is safe. */
export interface ProfileProblem {
  message: string;
  entityName?: string;
  fix?: ProfileFix;
}

/** The name that marks the likely player when no entity has a Player component yet (the desktop's `PLAYER_NAME`). */
export const PLAYER_NAME = "Player";

/** The only active entity named "Player", else the only Character Controller 3D, else none (the desktop's `likelyPlayer`). */
function likelyPlayer(game: GameScene): GameEntity | undefined {
  const active = game.entities.filter((e) => e.isActiveInHierarchy);
  const named = active.filter((e) => e.name.toLowerCase() === PLAYER_NAME.toLowerCase());
  if (named.length === 1) return named[0];
  const characters = active.filter((e) => e.getComponent("CharacterController3DComponent"));
  return named.length === 0 && characters.length === 1 ? characters[0] : undefined;
}

/** What the scene lacks for a profile (the desktop's `validate`, same words and fixes); empty when Play may start. */
export function validateProfile(game: GameScene, camera: GameEntity | null): ProfileProblem[] {
  const problems: ProfileProblem[] = [];
  const found = players(game);
  if (found.length === 0) {
    const message = "No player: select the entity to control and add Player (Add Component > Game).";
    const likely = likelyPlayer(game);
    problems.push(likely
      ? { message, entityName: likely.name, fix: { kind: "MAKE_PLAYER", entityId: likely.id, label: `Make ${likely.name} the player` } }
      : { message });
  } else if (found.length > 1) {
    problems.push({ message: `More than one player: ${found.map((e) => e.name).join(", ")}. Remove Player from all but one.`, entityName: found[1]!.name });
  }
  if (found.length === 1) {
    const player = found[0]!;
    if (!player.getComponent("CharacterController3DComponent")) {
      const solid = player.getComponent("RigidBody3DComponent")
        || player.components.some((c) => c.enabled && /Collider3DComponent$/.test(c.type));
      problems.push(solid
        ? { message: `${player.name} has a Rigid Body 3D or a 3D collider; a player walks with a Character Controller 3D instead. Remove them and add a Character Controller 3D.`, entityName: player.name }
        : { message: `${player.name} cannot walk: it needs a Character Controller 3D.`, entityName: player.name,
          fix: { kind: "ADD_CHARACTER_CONTROLLER_3D", entityId: player.id, label: `Add a Character Controller 3D to ${player.name}` } });
    }
  }
  const spawns = spawnPoints(game);
  if (spawns.length > 1) {
    problems.push({ message: `More than one spawn point: ${spawns.map((e) => e.name).join(", ")}. Remove Spawn Point from all but one.`, entityName: spawns[1]!.name });
  }
  if (camera === null) problems.push({ message: "No camera: add a Camera to see the game.", fix: { kind: "ADD_CAMERA", label: "Add a camera" } });
  return problems;
}

/** How far below the player's centre the ground check looks for something to stand on (the desktop's 100 m). */
export const GROUND_CHECK_DISTANCE = 10000;
const BELOW_CAPSULE = 1;
const SKIP_STEP = 10;
const MAX_PLAYER_SKIPS = 16;

/**
 * The desktop's ground check (TD-088, roadmap M3b.5): a ray straight down from just below the player's capsule must reach something
 * solid within {@link GROUND_CHECK_DISTANCE}, or the player would fall for ever. Run it once the physics bodies exist (after the
 * terrains loaded). Null when there is ground.
 */
export function groundProblem(game: GameScene, physics: Physics3D): ProfileProblem | null {
  const player = players(game)[0];
  const controller = player?.getComponent("CharacterController3DComponent");
  if (!player || !controller) return null;
  physics.prepareBodies();
  const height = Number(controller.data["height"] ?? 80) + 2 * Number(controller.data["radius"] ?? 30);
  const below = height / 2 + BELOW_CAPSULE;
  const origin = player.getWorldPosition();
  origin.y -= below;
  let left = GROUND_CHECK_DISTANCE - below;
  const down = new Vector3(0, -1, 0);
  for (let skips = 0; skips <= MAX_PLAYER_SKIPS && left > 0; skips++) {
    const hit = physics.raycast(origin, down, left);
    if (!hit) break;
    if (hit.entity !== player) return null;
    // The ray started inside the player's own shape: carry on from just below where it left it
    left -= hit.distance + SKIP_STEP;
    origin.copyFrom(hit.point);
    origin.y -= SKIP_STEP;
  }
  return { message: `Nothing under ${player.name} to stand on: add a Terrain Collider 3D or a Box Collider 3D below it, or move it over the ground.`, entityName: player.name };
}

/** A profile's per-frame part: the player's mover, or the third-person camera's turn keys. */
export interface ProfilePart {
  update(deltaSeconds: number): void;
}

/** What starting a profile gave: the message for the status line, and the parts Play updates each frame after physics. */
export interface ProfileStart {
  message: string;
  parts: ProfilePart[];
  /** The profile's camera rig, to run with the scene's rigs; null when the camera keeps its own Camera Rig. */
  rig: CameraRig | null;
}

/**
 * The profile step of web Play, after {@link validateProfile} passed and 3D physics exists but before its first update: the player
 * is put on the spawn point, the mover goes on the player unless its own script or graph moves it, and a rig goes on the game camera
 * unless it has its own (the desktop's `plan` and `apply`).
 */
export function startProfile(resolution: Extract<ProfileResolution, { outcome: "PROFILE" }>, game: GameScene, camera: GameEntity,
                             physics: Physics3D, input: Input): ProfileStart {
  const profile = resolution.profile;
  const player = players(game)[0]!;
  const notes = [resolution.message];
  const spawnNote = placeAtSpawn(game, player);
  const ownLogic = enabled(player, "EntityScriptBindingComponent") || enabled(player, "GraphScriptComponent");
  const playerData = player.getComponent("PlayerComponent")!.data;
  const addMovement = !ownLogic || playerData["useProfileMovement"] === true;
  if (!addMovement) {
    notes.push(`${player.name} is moved by its own script or graph; the profile supplies the camera only. Turn on Use Profile Movement on its Player to use the profile's movement.`);
  }
  const parts: ProfilePart[] = [];
  if (addMovement) {
    parts.push(profile.movement.kind === "GROUND_WALK"
      ? new GroundWalkMover(player, camera, physics, input, profile.movement)
      : new PlaneRunMover(player, physics, input, profile.movement));
  }
  let rig: CameraRig | null = null;
  if (enabled(camera, "CameraRigComponent")) {
    notes.push(`${camera.name} keeps its own Camera Rig.`);
  } else {
    rig = profileCameraRig(camera, player, profile);
    if (profile.camera.kind === "THIRD_PERSON") parts.push(new CameraTurnKeys(rig, input));
  }
  if (spawnNote !== null) notes.push(spawnNote);
  return { message: notes.join(" "), parts, rig };
}

/** The desktop's `ProfileCameraRig` set-up, as Camera Rig data the web rig reads; started on the player. */
export function profileCameraRig(camera: GameEntity, player: GameEntity, profile: WebProfile): CameraRig {
  const setup = profile.camera;
  const data: Record<string, unknown> = setup.kind === "SIDE_FOLLOW"
    // Beside the play plane on +Z, looking back along -Z at the player: the side view of a 2.5D game
    ? { mode: "FOLLOW", targetName: player.name, followOffsetX: 0, followOffsetY: setup.height, followOffsetZ: setup.distance,
      followRate: setup.followRate, pivotHeight: 0 }
    : { mode: "ORBIT", targetName: player.name, minDistance: Math.min(DEFAULT_MIN_DISTANCE, setup.distance),
      maxDistance: Math.max(DEFAULT_MAX_DISTANCE, setup.distance), distance: setup.distance, pivotHeight: setup.height,
      pitch: -setup.pitchDegrees, occlusionEnabled: true };
  const rig = new CameraRig(camera, data);
  rig.start((name) => (name === player.name ? player : undefined));
  if (setup.kind === "THIRD_PERSON") {
    // Starts behind the player, looking the way it faces
    const facing = forward(0, 0).rotateByQuaternionToRef(player.getWorldRotation(), new Vector3());
    if (facing.x * facing.x + facing.z * facing.z > NO_DIRECTION) rig.setYaw(yawOf(facing));
  }
  return rig;
}

/** Puts the player on the scene's spawn point, standing on it and facing its way (the desktop's `placeAtSpawn`); null without one. */
function placeAtSpawn(game: GameScene, player: GameEntity): string | null {
  const spawns = spawnPoints(game);
  if (spawns.length !== 1) return null;
  const spawn = spawns[0]!;
  const controller = player.getComponent("CharacterController3DComponent");
  const height = controller ? Number(controller.data["height"] ?? 180) + 2 * Number(controller.data["radius"] ?? 40) : 0;
  const lift = controller ? height / 2 + SPAWN_CLEARANCE : 0;
  const at = spawn.getWorldPosition();
  const facing = forward(0, 0).rotateByQuaternionToRef(spawn.getWorldRotation(), new Vector3());
  const yaw = facing.x * facing.x + facing.z * facing.z > NO_DIRECTION ? yawOf(facing) : 0;
  // The player is expected at the scene's root, as on the desktop's movers
  player.node.position.set(at.x, at.y + lift, at.z);
  player.node.rotationQuaternion = Quaternion.RotationYawPitchRoll((yaw * Math.PI) / 180, 0, 0);
  player.node.computeWorldMatrix(true);
  return `${player.name} starts at ${spawn.name}.`;
}

/** {@code from} turned toward {@code to} by at most {@code maxStep} degrees, the short way round, wrapped to -180..180. */
export function turnToward(from: number, to: number, maxStep: number): number {
  const difference = wrap(to - from);
  if (Math.abs(difference) <= maxStep) return wrap(to);
  return wrap(from + Math.sign(difference) * maxStep);
}

function wrap(degrees: number): number {
  let wrapped = degrees % 360;
  if (wrapped > 180) wrapped -= 360;
  else if (wrapped < -180) wrapped += 360;
  return wrapped;
}

/**
 * The horizontal unit direction to walk for move keys {@code right} and {@code ahead} (each -1, 0 or 1) seen from a camera looking
 * along {@code viewYawDegrees}; zero when no key is held or they cancel out (the desktop's `GroundWalkMover.walkDirection`).
 */
export function walkDirection(viewYawDegrees: number, right: number, ahead: number): Vector3 {
  const yaw = (viewYawDegrees * Math.PI) / 180;
  const sin = Math.sin(yaw);
  const cos = Math.cos(yaw);
  const out = new Vector3(-sin * ahead + cos * right, 0, -cos * ahead - sin * right);
  return out.lengthSquared() > NO_DIRECTION ? out.normalize() : Vector3.Zero();
}

/** Action RPG + 3D: walk relative to the camera, face where it walks, one jump per press (the desktop's `GroundWalkMover`). */
export class GroundWalkMover implements ProfilePart {
  private facingYaw: number | null = null;
  private jumpHeld = false;
  jumps = 0;

  constructor(private readonly player: GameEntity, private readonly camera: GameEntity, private readonly physics: Physics3D,
              private readonly input: Input, private readonly movement: WebProfile["movement"]) {}

  update(deltaSeconds: number): void {
    if (this.facingYaw === null) this.facingYaw = yawFacing(this.player.getWorldRotation(), 0);
    const right = (this.input.isDown(...DEFAULT_KEYS.MOVE_RIGHT) ? 1 : 0) - (this.input.isDown(...DEFAULT_KEYS.MOVE_LEFT) ? 1 : 0);
    const ahead = (this.input.isDown(...DEFAULT_KEYS.MOVE_FORWARD) ? 1 : 0) - (this.input.isDown(...DEFAULT_KEYS.MOVE_BACK) ? 1 : 0);
    const direction = walkDirection(yawFacing(this.camera.getWorldRotation(), this.facingYaw), right, ahead);
    this.physics.moveCharacter(this.player, direction.scale(this.movement.speed));
    if (direction.lengthSquared() > NO_DIRECTION) {
      this.facingYaw = turnToward(this.facingYaw, yawOf(direction), this.movement.turnDegreesPerSecond * deltaSeconds);
      this.player.node.rotationQuaternion = Quaternion.RotationYawPitchRoll((this.facingYaw * Math.PI) / 180, 0, 0);
    }
    const jumpPressed = this.input.isDown(...DEFAULT_KEYS.JUMP);
    // One jump per press: holding the key does not jump again on landing
    if (jumpPressed && !this.jumpHeld && this.physics.jumpCharacter(this.player, this.movement.jumpSpeed)) this.jumps++;
    this.jumpHeld = jumpPressed;
  }

  /** The yaw the player faces, in degrees (yaw 0 faces -Z, positive turns left). */
  get facing(): number {
    return this.facingYaw ?? 0;
  }
}

/** Platformer + 2.5D: run along X on the starting depth, one jump per press (the desktop's `PlaneRunMover`). */
export class PlaneRunMover implements ProfilePart {
  private onPlane = false;
  private jumpHeld = false;
  jumps = 0;

  constructor(private readonly player: GameEntity, private readonly physics: Physics3D, private readonly input: Input,
              private readonly movement: WebProfile["movement"]) {}

  update(): void {
    if (!this.onPlane) {
      this.physics.lockCharacterDepth(this.player, this.player.getWorldPosition().z);
      this.onPlane = true;
    }
    const direction = (this.input.isDown(...DEFAULT_KEYS.MOVE_RIGHT) ? 1 : 0) - (this.input.isDown(...DEFAULT_KEYS.MOVE_LEFT) ? 1 : 0);
    this.physics.moveCharacter(this.player, new Vector3(direction * this.movement.speed, 0, 0));
    const jumpPressed = this.input.isDown(...DEFAULT_KEYS.JUMP);
    if (jumpPressed && !this.jumpHeld && this.physics.jumpCharacter(this.player, this.movement.jumpSpeed)) this.jumps++;
    this.jumpHeld = jumpPressed;
  }
}

/** The third-person camera's turn keys, Q and E by default (the desktop's `ProfileCameraRig.onUpdate`). */
class CameraTurnKeys implements ProfilePart {
  constructor(private readonly rig: CameraRig, private readonly input: Input) {}

  update(deltaSeconds: number): void {
    const turn = (this.input.isDown(...DEFAULT_KEYS.TURN_LEFT) ? 1 : 0) - (this.input.isDown(...DEFAULT_KEYS.TURN_RIGHT) ? 1 : 0);
    // Positive yaw turns left
    if (turn !== 0) this.rig.rotate(turn * CAMERA_TURN_DEGREES_PER_SECOND * deltaSeconds, 0);
  }
}

/** The yaw a rotation faces, or {@code whenVertical} when it looks straight up or down. */
function yawFacing(rotation: Quaternion, whenVertical: number): number {
  const facing = forward(0, 0).rotateByQuaternionToRef(rotation, new Vector3());
  return facing.x * facing.x + facing.z * facing.z > NO_DIRECTION ? yawOf(facing) : whenVertical;
}
