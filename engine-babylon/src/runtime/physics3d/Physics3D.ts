import { Quaternion, Vector3 } from "@babylonjs/core";
import type { GameEntity, GameScene } from "../GameEntity";
import { bool, num, str, type ComponentData } from "../../scene/types";
import { reduceHullPoints } from "./colliderPoints";
import type { ColliderGeometrySource } from "./ColliderGeometry";
import {
  ALL_LAYERS,
  DEFAULT_LAYER,
  METERS_PER_UNIT,
  UNITS_PER_METER,
  WORLD,
  type BodyDescriptor,
  type JointDescriptor,
  type JointType,
  type MotionType,
  type MotorMode,
  type PhysicsBackend3D,
  type ShapeDescriptor,
} from "./types";

export const FIXED_TIMESTEP = 1 / 60;
/** Longest frame time simulated; longer frames (a background tab, a breakpoint) are cut to this. */
export const MAX_FRAME_DELTA = 0.1;
export const MAX_SUB_STEPS = 5;
/** Earth gravity in meters per second squared. */
export const DEFAULT_GRAVITY = -9.81;

const BOX_COLLIDER = "BoxCollider3DComponent";
const SPHERE_COLLIDER = "SphereCollider3DComponent";
const CAPSULE_COLLIDER = "CapsuleCollider3DComponent";
const HULL_COLLIDER = "ConvexHullCollider3DComponent";
const MESH_COLLIDER = "MeshCollider3DComponent";
const RIGID_BODY = "RigidBody3DComponent";
const JOINT = "Joint3DComponent";
const CHARACTER = "CharacterController3DComponent";
/** A rising character that moved less than this fraction of what it asked for hit its head: it stops rising (as on the desktop). */
const CEILING_HIT_FRACTION = 0.25;
/** Hinge limits must lie within this many degrees either way, as on the desktop (`Joint3DComponent.MAX_HINGE_DEGREES`). */
const MAX_HINGE_DEGREES = 180;
/** The collider component types this player simulates (TD-059, TD-064). Joints and characters are not built yet. */
export const COLLIDER_TYPES: readonly string[] = [BOX_COLLIDER, SPHERE_COLLIDER, CAPSULE_COLLIDER, HULL_COLLIDER, MESH_COLLIDER];

const NO_BODY = -1;
/** A mesh collider with more triangles than this adds a warning, as on the desktop. */
const MAX_MESH_TRIANGLES_WARNING = 100_000;

export type ContactKind = "COLLISION_ENTER" | "COLLISION_EXIT" | "TRIGGER_ENTER" | "TRIGGER_EXIT";

export interface Contact {
  entityA: GameEntity;
  entityB: GameEntity;
  kind: ContactKind;
}

export type ContactListener = (contact: Contact) => void;

/** Closest ray hit in scene units. */
export interface RaycastHit {
  entity: GameEntity;
  point: Vector3;
  normal: Vector3;
  distance: number;
}

/** A character's state between fixed steps; speeds are meters per second. */
interface CharacterState {
  readonly entity: GameEntity;
  readonly handle: number;
  readonly controller: ComponentData;
  desiredX: number;
  desiredZ: number;
  verticalSpeed: number;
  grounded: boolean;
  /** How far the character really moved in its last step, per second. */
  readonly velocity: { x: number; y: number; z: number };
}

/** A live joint: the handles of the two bodies it was made for, so it can be replaced when one of them is. */
interface JointState {
  readonly entity: GameEntity;
  readonly connected: GameEntity | null;
  readonly handle: number;
  readonly bodyA: number;
  readonly bodyB: number;
  readonly type: JointType;
  readonly motorMaxForce: number;
}

interface EntityBodies {
  readonly entity: GameEntity;
  readonly motionType: MotionType;
  solid: number;
  sensor: number;
}

function enabledColliders(entity: GameEntity): ComponentData[] {
  return COLLIDER_TYPES.flatMap((type) => entity.getComponents(type));
}

function requireFinite(vector: Vector3, what: string): void {
  if (!Number.isFinite(vector.x) || !Number.isFinite(vector.y) || !Number.isFinite(vector.z)) {
    throw new Error(`${what} must be finite: ${vector.x}, ${vector.y}, ${vector.z}`);
  }
}

/**
 * Runs 3D physics for a playing scene on a {@link PhysicsBackend3D}: the web counterpart of the desktop's `PhysicsSystem3D`
 * (TD-028, TD-048, TD-059). Entities with enabled 3D colliders get bodies when the system is made (entities that start later are
 * picked up on the next update); entities without a Rigid Body 3D are static; trigger colliders become a sensor body that follows
 * the entity. The simulation advances in fixed 1/60 s steps, kinematic bodies follow their entities and dynamic bodies write their
 * pose back. Every vector in the public methods is in scene units (100 per meter): positions and distances in units, velocities
 * in units per second, forces in kilogram units per second squared, impulses in kilogram units per second. Angular velocity is
 * in radians per second.
 *
 * Convex hull and triangle mesh colliders (TD-064) are built from the triangles of the entity's meshes ({@link ColliderGeometrySource});
 * a mesh collider is for static bodies only. One that cannot be built is refused with a message that names the entity
 * ({@link getWarnings}) and its entity has no body; the other colliders of the scene go on. Not built yet: character controllers. Joints (TD-051, TD-064): a Joint 3D component on an entity joins its body to the entity it names (or to the world),
 * made when both bodies exist and removed when either goes; a joint that cannot be made adds a warning naming the entity.
 */
export class Physics3D {
  private readonly bodiesByEntity = new Map<string, EntityBodies>();
  private readonly entityByHandle = new Map<number, GameEntity>();
  private readonly sensorHandles = new Set<number>();
  private readonly listeners: ContactListener[] = [];
  /** Forces and torques in SI units waiting for the next steps, see {@link addForce}. */
  private readonly pendingForces = new Map<string, Vector3>();
  private readonly pendingTorques = new Map<string, Vector3>();
  /** Why entities were left without a body, see {@link getWarnings}. */
  private readonly warnings: string[] = [];
  /** Entities whose colliders were refused: they are not tried again every update. */
  private readonly refused = new Set<string>();
  private readonly jointsByEntity = new Map<string, JointState>();
  private readonly charactersByEntity = new Map<string, CharacterState>();
  /** Entities whose character was refused: it is not tried again every update. */
  private readonly refusedCharacters = new Set<string>();
  /** Entities whose joint was refused: it is not tried again every update. */
  private readonly refusedJoints = new Set<string>();
  private accumulator = 0;
  private stepCount = 0;
  private disposed = false;
  private readonly tmpPosition = { x: 0, y: 0, z: 0 };
  private readonly tmpRotation = { x: 0, y: 0, z: 0, w: 1 };
  private readonly tmpVector = { x: 0, y: 0, z: 0 };

  /** Whether {@code scene} has a 3D collider on an active entity: the player makes a physics system (and loads Jolt) only then. */
  static isUsedBy(scene: GameScene): boolean {
    return scene.entities.some((entity) => entity.isActiveInHierarchy && (enabledColliders(entity).length > 0 || entity.getComponents(CHARACTER).length > 0));
  }

  /**
   * @param geometry where hull and mesh colliders read their triangles; without it they are refused
   */
  constructor(
    private readonly scene: GameScene,
    private readonly backend: PhysicsBackend3D,
    private readonly geometry: ColliderGeometrySource | null = null,
  ) {
    backend.setGravity(0, DEFAULT_GRAVITY, 0);
  }

  addContactListener(listener: ContactListener): void {
    this.listeners.push(listener);
  }

  removeContactListener(listener: ContactListener): void {
    const at = this.listeners.indexOf(listener);
    if (at >= 0) this.listeners.splice(at, 1);
  }

  /** What was refused or skipped so far, one line each, naming the entity (a mesh collider on a moving body, a model that did not load). */
  getWarnings(): readonly string[] {
    return this.warnings;
  }

  /** Fixed steps taken so far. */
  getStepCount(): number {
    return this.stepCount;
  }

  /** Whether {@code entity} currently has a body. */
  hasBody(entity: GameEntity): boolean {
    return this.bodiesByEntity.has(entity.id);
  }

  /** Advances the simulation by {@code deltaSeconds} in fixed steps (at most {@link MAX_SUB_STEPS} per call), then syncs poses and reports contacts. */
  update(deltaSeconds: number): void {
    if (this.disposed) return;
    this.syncBodies();
    this.accumulator += Math.min(Math.max(0, deltaSeconds), MAX_FRAME_DELTA);
    let steps = 0;
    while (this.accumulator >= FIXED_TIMESTEP && steps < MAX_SUB_STEPS) {
      this.pushKinematicPoses();
      this.applyPendingForces();
      this.stepCharacters();
      this.backend.step(FIXED_TIMESTEP);
      this.accumulator -= FIXED_TIMESTEP;
      steps++;
      this.stepCount++;
    }
    if (steps > 0) {
      // A force acts for the simulated time since the script applied it; with no step yet it waits for the next update
      this.pendingForces.clear();
      this.pendingTorques.clear();
    }
    // Too far behind: drop the backlog rather than spiral
    if (this.accumulator > FIXED_TIMESTEP) this.accumulator = 0;
    this.pullDynamicPoses();
    this.pullCharacterPoses();
    this.dispatchContacts();
  }

  // --- Queries ---

  /**
   * Casts a ray in scene units and returns the closest solid hit, or null.
   *
   * @param layerMask layers that can be hit; all of them by default
   */
  raycast(origin: Vector3, direction: Vector3, maxDistance: number, layerMask: number = ALL_LAYERS): RaycastHit | null {
    requireFinite(origin, "origin");
    if (direction.lengthSquared() === 0 || !(maxDistance > 0)) {
      throw new Error(`A ray needs a direction and a positive distance: ${direction.toString()}, ${maxDistance}`);
    }
    const along = direction.normalizeToNew();
    const hit = this.backend.raycast(
      { x: origin.x * METERS_PER_UNIT, y: origin.y * METERS_PER_UNIT, z: origin.z * METERS_PER_UNIT },
      along,
      maxDistance * METERS_PER_UNIT,
      layerMask,
    );
    if (!hit) return null;
    const entity = this.entityByHandle.get(hit.body);
    if (!entity) return null;
    return {
      entity,
      point: new Vector3(hit.point.x * UNITS_PER_METER, hit.point.y * UNITS_PER_METER, hit.point.z * UNITS_PER_METER),
      normal: new Vector3(hit.normal.x, hit.normal.y, hit.normal.z),
      distance: hit.distance * UNITS_PER_METER,
    };
  }

  /** Entities whose shape overlaps a sphere, each once, in no particular order. Sensors (trigger colliders) are not reported. */
  overlapSphere(center: Vector3, radius: number, layerMask: number = ALL_LAYERS): GameEntity[] {
    requireFinite(center, "center");
    if (!(radius > 0) || !Number.isFinite(radius)) throw new Error(`Sphere radius must be positive and finite: ${radius}`);
    const handles = this.backend.overlapSphere(
      { x: center.x * METERS_PER_UNIT, y: center.y * METERS_PER_UNIT, z: center.z * METERS_PER_UNIT },
      radius * METERS_PER_UNIT,
      layerMask,
    );
    const found = new Set<GameEntity>();
    for (const handle of handles) {
      const entity = this.entityByHandle.get(handle);
      if (entity) found.add(entity);
    }
    return [...found];
  }

  // --- Forces and velocities ---

  /**
   * Pushes a dynamic entity with a force that keeps acting until the next update has stepped: call it every frame the push should
   * last. The force acts on every fixed step of that update, so a push that lasts a second moves the body the same at 30 and at
   * 60 frames per second.
   */
  addForce(entity: GameEntity, force: Vector3): void {
    this.dynamicBodyOf(entity, "addForce");
    requireFinite(force, "force");
    this.accumulate(this.pendingForces, entity.id, force.scale(METERS_PER_UNIT));
  }

  /** Like {@link addForce} for a torque. */
  addTorque(entity: GameEntity, torque: Vector3): void {
    this.dynamicBodyOf(entity, "addTorque");
    requireFinite(torque, "torque");
    this.accumulate(this.pendingTorques, entity.id, torque.scale(METERS_PER_UNIT * METERS_PER_UNIT));
  }

  private accumulate(map: Map<string, Vector3>, id: string, amount: Vector3): void {
    const sum = map.get(id);
    if (sum) sum.addInPlace(amount);
    else map.set(id, amount);
  }

  /** Changes a dynamic entity's velocity at once by {@code impulse / mass}: a kick, a jump, a shot's recoil. */
  addImpulse(entity: GameEntity, impulse: Vector3): void {
    const handle = this.dynamicBodyOf(entity, "addImpulse");
    requireFinite(impulse, "impulse");
    this.backend.addImpulse(handle, impulse.x * METERS_PER_UNIT, impulse.y * METERS_PER_UNIT, impulse.z * METERS_PER_UNIT);
  }

  /** Sets the velocity of a dynamic or kinematic entity, in scene units per second. */
  setLinearVelocity(entity: GameEntity, velocity: Vector3): void {
    const handle = this.movingBodyOf(entity, "setLinearVelocity");
    requireFinite(velocity, "velocity");
    this.backend.setLinearVelocity(handle, velocity.x * METERS_PER_UNIT, velocity.y * METERS_PER_UNIT, velocity.z * METERS_PER_UNIT);
  }

  /** The entity's linear velocity in scene units per second; zero for a static body. */
  getLinearVelocity(entity: GameEntity): Vector3 {
    this.backend.getLinearVelocity(this.solidBodyOf(entity, "getLinearVelocity"), this.tmpVector);
    return new Vector3(this.tmpVector.x * UNITS_PER_METER, this.tmpVector.y * UNITS_PER_METER, this.tmpVector.z * UNITS_PER_METER);
  }

  /** Sets the angular velocity of a dynamic or kinematic entity, in radians per second around the world axes. */
  setAngularVelocity(entity: GameEntity, velocity: Vector3): void {
    const handle = this.movingBodyOf(entity, "setAngularVelocity");
    requireFinite(velocity, "velocity");
    this.backend.setAngularVelocity(handle, velocity.x, velocity.y, velocity.z);
  }

  /** The entity's angular velocity in radians per second around the world axes; zero for a static body. */
  getAngularVelocity(entity: GameEntity): Vector3 {
    this.backend.getAngularVelocity(this.solidBodyOf(entity, "getAngularVelocity"), this.tmpVector);
    return new Vector3(this.tmpVector.x, this.tmpVector.y, this.tmpVector.z);
  }

  private solidBodyOf(entity: GameEntity, operation: string): number {
    if (this.disposed) throw new Error(`${operation}: the 3D physics of this scene has stopped`);
    if (this.charactersByEntity.has(entity.id)) {
      throw new Error(`${operation}: ${entity.name} is a character (it has a Character Controller 3D), not a rigid body. Move it with moveCharacter3D and jumpCharacter3D.`);
    }
    const bodies = this.bodiesByEntity.get(entity.id);
    if (!bodies) throw new Error(`${operation}: ${entity.name} has no 3D body (it needs a 3D collider, and the scene must be playing)`);
    if (bodies.solid === NO_BODY) {
      throw new Error(`${operation}: ${entity.name} has only trigger colliders, so it has no solid body`);
    }
    return bodies.solid;
  }

  private movingBodyOf(entity: GameEntity, operation: string): number {
    const handle = this.solidBodyOf(entity, operation);
    if (this.bodiesByEntity.get(entity.id)!.motionType === "STATIC") {
      throw new Error(`${operation}: ${entity.name} is static; give it a Rigid Body 3D that is dynamic or kinematic`);
    }
    return handle;
  }

  private dynamicBodyOf(entity: GameEntity, operation: string): number {
    const handle = this.solidBodyOf(entity, operation);
    if (this.bodiesByEntity.get(entity.id)!.motionType !== "DYNAMIC") {
      throw new Error(`${operation}: ${entity.name} is not dynamic; only a dynamic Rigid Body 3D takes forces and impulses`);
    }
    return handle;
  }

  private applyPendingForces(): void {
    for (const [id, force] of this.pendingForces) {
      const bodies = this.bodiesByEntity.get(id);
      if (bodies && bodies.solid !== NO_BODY) this.backend.addForce(bodies.solid, force.x, force.y, force.z);
    }
    for (const [id, torque] of this.pendingTorques) {
      const bodies = this.bodiesByEntity.get(id);
      if (bodies && bodies.solid !== NO_BODY) this.backend.addTorque(bodies.solid, torque.x, torque.y, torque.z);
    }
  }

  // --- Bodies ---

  /** Creates bodies for entities that have colliders and none yet, and removes the bodies of entities that are gone or inactive. */
  private syncBodies(): void {
    const wanted = new Set<string>();
    for (const entity of this.scene.entities) {
      if (!entity.isActiveInHierarchy) continue;
      const controller = entity.getComponent(CHARACTER);
      if (controller && controller.enabled) {
        wanted.add(entity.id);
        if (!this.charactersByEntity.has(entity.id) && !this.refusedCharacters.has(entity.id)) this.createCharacter(entity, controller);
        continue;
      }
      if (enabledColliders(entity).length === 0) continue;
      wanted.add(entity.id);
      if (!this.bodiesByEntity.has(entity.id) && !this.refused.has(entity.id)) this.createBodies(entity);
    }
    // Joints go before the bodies they join
    for (const state of [...this.jointsByEntity.values()]) {
      if (this.isStale(state, wanted)) this.removeJoint(state);
    }
    for (const bodies of [...this.bodiesByEntity.values()]) {
      if (!wanted.has(bodies.entity.id) || !this.scene.contains(bodies.entity)) this.removeBodies(bodies);
    }
    for (const state of [...this.charactersByEntity.values()]) {
      if (!wanted.has(state.entity.id) || !this.scene.contains(state.entity)) this.removeCharacter(state);
    }
    for (const entity of this.scene.entities) {
      if (!entity.isActiveInHierarchy || this.jointsByEntity.has(entity.id) || this.refusedJoints.has(entity.id)) continue;
      const joint = entity.getComponent(JOINT);
      if (joint && joint.enabled) this.createJoint(entity, joint);
    }
  }

  // --- Characters (TD-049, TD-064) ---

  /**
   * Sets the speed a character walks at, in scene units per second along the world X and Z axes; it keeps walking until this is called
   * again. The Y component is ignored: gravity and {@link jumpCharacter} decide the vertical movement.
   */
  moveCharacter(entity: GameEntity, velocity: Vector3): void {
    const state = this.characterOf(entity, "moveCharacter");
    requireFinite(velocity, "velocity");
    state.desiredX = velocity.x * METERS_PER_UNIT;
    state.desiredZ = velocity.z * METERS_PER_UNIT;
  }

  /**
   * Makes a character leave the ground at {@code speed} scene units per second, but only while it stands on ground.
   *
   * @returns whether it jumped
   */
  jumpCharacter(entity: GameEntity, speed: number): boolean {
    const state = this.characterOf(entity, "jumpCharacter");
    if (!Number.isFinite(speed) || speed <= 0) throw new Error(`Jump speed must be positive and finite: ${speed}`);
    if (!state.grounded) return false;
    state.verticalSpeed = speed * METERS_PER_UNIT;
    state.grounded = false;
    return true;
  }

  /**
   * Puts a character at {@code position} (scene units, the capsule's center) at once, for a respawn or a portal: nothing between the old
   * and the new place is touched, and its fall speed and walking velocity are cleared.
   */
  teleportCharacter(entity: GameEntity, position: Vector3): void {
    const state = this.characterOf(entity, "teleportCharacter");
    requireFinite(position, "position");
    this.backend.setCharacterPosition(state.handle, { x: position.x * METERS_PER_UNIT, y: position.y * METERS_PER_UNIT, z: position.z * METERS_PER_UNIT });
    state.desiredX = 0;
    state.desiredZ = 0;
    state.verticalSpeed = 0;
    state.grounded = false;
    state.velocity.x = 0;
    state.velocity.y = 0;
    state.velocity.z = 0;
    setWorldPose(entity, position.x, position.y, position.z, entity.getWorldRotation());
  }

  /** Whether a character stands on ground it can walk on. False in the air and on slopes above its limit. */
  isCharacterGrounded(entity: GameEntity): boolean {
    return this.characterOf(entity, "isCharacterGrounded").grounded;
  }

  /** How fast a character actually moved in its last fixed step, in scene units per second (walls and floors included). */
  getCharacterVelocity(entity: GameEntity): Vector3 {
    const v = this.characterOf(entity, "getCharacterVelocity").velocity;
    return new Vector3(v.x * UNITS_PER_METER, v.y * UNITS_PER_METER, v.z * UNITS_PER_METER);
  }

  private characterOf(entity: GameEntity, operation: string): CharacterState {
    if (this.disposed) throw new Error(`${operation}: the 3D physics of this scene has stopped`);
    const state = this.charactersByEntity.get(entity.id);
    if (!state) {
      throw new Error(`${operation}: ${entity.name} is not a character. Add a Character Controller 3D to it and keep it active; the character is created at the start of the next physics update, so an entity created this frame has none yet.`);
    }
    return state;
  }

  private createCharacter(entity: GameEntity, controller: ComponentData): void {
    const rigidBody = entity.getComponent(RIGID_BODY);
    if ((rigidBody && rigidBody.enabled) || enabledColliders(entity).length > 0) {
      this.refusedCharacters.add(entity.id);
      this.warnings.push(`${entity.name}: CharacterController3D refused: it has a ${rigidBody && rigidBody.enabled ? "Rigid Body 3D" : "3D collider"} too; the character is its own body, so remove the other physics components or disable the controller`);
      return;
    }
    const d = controller.data;
    const position = entity.getWorldPosition().scale(METERS_PER_UNIT);
    const handle = this.backend.addCharacter({
      position: { x: position.x, y: position.y, z: position.z },
      radius: num(d, "radius", 40) * METERS_PER_UNIT,
      halfHeight: num(d, "height", 180) * METERS_PER_UNIT * 0.5,
      slopeRadians: (num(d, "slopeLimitDegrees", 45) * Math.PI) / 180,
      stepHeight: num(d, "stepHeight", 30) * METERS_PER_UNIT,
      mass: num(d, "mass", 70),
      layer: num(d, "layer", DEFAULT_LAYER),
      collisionMask: num(d, "collisionMask", ALL_LAYERS),
    });
    this.charactersByEntity.set(entity.id, { entity, handle, controller, desiredX: 0, desiredZ: 0, verticalSpeed: 0, grounded: false, velocity: { x: 0, y: 0, z: 0 } });
    this.entityByHandle.set(handle, entity);
  }

  private removeCharacter(state: CharacterState): void {
    this.backend.removeCharacter(state.handle);
    this.entityByHandle.delete(state.handle);
    this.charactersByEntity.delete(state.entity.id);
  }

  private stepCharacters(): void {
    const position = { x: 0, y: 0, z: 0 };
    for (const state of this.charactersByEntity.values()) {
      // Gravity as the average speed over the step, so a jump follows the exact parabola whatever the step length
      let moveY: number;
      let speedChange: number;
      if (state.grounded && state.verticalSpeed <= 0) {
        state.verticalSpeed = 0;
        moveY = 0;
        speedChange = 0;
      } else {
        speedChange = DEFAULT_GRAVITY * num(state.controller.data, "gravityScale", 1) * FIXED_TIMESTEP;
        moveY = state.verticalSpeed + 0.5 * speedChange;
      }
      this.backend.getCharacterPosition(state.handle, position);
      const { x, y, z } = position;
      this.backend.moveCharacter(state.handle, { x: state.desiredX, y: moveY, z: state.desiredZ }, FIXED_TIMESTEP);
      this.backend.getCharacterPosition(state.handle, position);
      state.grounded = this.backend.isCharacterGrounded(state.handle);
      state.velocity.x = (position.x - x) / FIXED_TIMESTEP;
      state.velocity.y = (position.y - y) / FIXED_TIMESTEP;
      state.velocity.z = (position.z - z) / FIXED_TIMESTEP;
      if (moveY > 0 && position.y - y < CEILING_HIT_FRACTION * moveY * FIXED_TIMESTEP) {
        state.verticalSpeed = 0; // the head hit something: stop rising
      } else {
        state.verticalSpeed += speedChange;
      }
    }
  }

  private pullCharacterPoses(): void {
    for (const state of this.charactersByEntity.values()) {
      this.backend.getCharacterPosition(state.handle, this.tmpPosition);
      // Only the position belongs to the simulation: the capsule is always upright and the entity keeps its rotation
      setWorldPose(state.entity, this.tmpPosition.x * UNITS_PER_METER, this.tmpPosition.y * UNITS_PER_METER, this.tmpPosition.z * UNITS_PER_METER, state.entity.getWorldRotation());
    }
  }

  // --- Joints (TD-051, TD-064) ---

  /**
   * Changes the motor of the joint on {@code entity} while the game runs; hinge and slider only. {@code target} is degrees per second
   * (VELOCITY) or degrees (POSITION) for a hinge, scene units per second or scene units for a slider.
   */
  setJointMotor(entity: GameEntity, mode: MotorMode, target: number): void {
    const state = this.jointOf(entity, "setJointMotor");
    this.requireMotorType(entity, state.type);
    if (!Number.isFinite(target)) throw new Error(`setJointMotor: target must be finite: ${target}`);
    this.backend.setJointMotor(state.handle, mode, motorTargetInSi(state.type, target), state.motorMaxForce);
  }

  /** How far a hinge has turned in degrees, or a slider has slid in scene units, from where it stood when the joint was made. */
  getJointPosition(entity: GameEntity): number {
    const state = this.jointOf(entity, "getJointPosition");
    this.requireMotorType(entity, state.type);
    const value = this.backend.getJointPosition(state.handle);
    return state.type === "HINGE" ? (value * 180) / Math.PI : value * UNITS_PER_METER;
  }

  /** Whether {@code entity} currently has a joint in the simulation. */
  hasJoint(entity: GameEntity): boolean {
    return this.jointsByEntity.has(entity.id);
  }

  private requireMotorType(entity: GameEntity, type: JointType): void {
    if (type !== "HINGE" && type !== "SLIDER") {
      throw new Error(`${entity.name} has a ${type} joint, and only a HINGE or SLIDER has a motor or a position.`);
    }
  }

  private jointOf(entity: GameEntity, operation: string): JointState {
    if (this.disposed) throw new Error(`${operation}: the 3D physics of this scene has stopped`);
    const state = this.jointsByEntity.get(entity.id);
    if (!state) {
      throw new Error(`${operation}: ${entity.name} has no joint. Add a Joint 3D to it and keep it, its body and the connected entity active; the joint is created at the start of the next physics update.`);
    }
    return state;
  }

  /** Whether a joint must go: its entity, its component or one of its bodies is gone, inactive or replaced. */
  private isStale(state: JointState, wanted: Set<string>): boolean {
    const component = state.entity.getComponent(JOINT);
    if (!component || !component.enabled || !wanted.has(state.entity.id) || !this.scene.contains(state.entity)) return true;
    if (this.bodiesByEntity.get(state.entity.id)?.solid !== state.bodyA) return true;
    const connected = state.connected;
    return connected !== null && (!wanted.has(connected.id) || !this.scene.contains(connected) || this.bodiesByEntity.get(connected.id)?.solid !== state.bodyB);
  }

  private removeJoint(state: JointState): void {
    this.backend.removeJoint(state.handle);
    this.jointsByEntity.delete(state.entity.id);
  }

  private refuseJoint(entity: GameEntity, reason: string): void {
    this.refusedJoints.add(entity.id);
    this.warnings.push(`${entity.name}: Joint3D refused: ${reason}`);
  }

  /**
   * Makes the joint of {@code entity} once its body and the connected entity's body exist. Waits while the connected entity is
   * inactive or has no body yet; every other problem is a warning naming the entity, so a joint that does not exist is never silent.
   */
  private createJoint(entity: GameEntity, joint: ComponentData): void {
    const d = joint.data;
    if (this.charactersByEntity.has(entity.id) || this.refusedCharacters.has(entity.id)) {
      return this.refuseJoint(entity, "it is a character (it has a Character Controller 3D); characters cannot be joined, use a rigid body");
    }
    const bodiesA = this.bodiesByEntity.get(entity.id);
    if (!bodiesA) return; // the entity's collider is still loading its geometry
    if (bodiesA.solid === NO_BODY) {
      return this.refuseJoint(entity, "it has no solid 3D collider (not a trigger) to join; add a Box, Sphere or Capsule Collider 3D");
    }
    let connected: GameEntity | null = null;
    let bodyB = WORLD;
    const connectedName = str(d, "connectedName", "");
    if (connectedName !== "") {
      connected = this.scene.entities.find((candidate) => candidate.name === connectedName) ?? null;
      if (!connected) return this.refuseJoint(entity, `it is connected to "${connectedName}", but the scene has no entity with that name`);
      if (connected === entity) return this.refuseJoint(entity, "it is connected to itself");
      if (this.charactersByEntity.has(connected.id)) {
        return this.refuseJoint(entity, `${connected.name} is a character (it has a Character Controller 3D); characters cannot be joined, use a rigid body`);
      }
      if (!connected.isActiveInHierarchy) return; // the joint appears when the connected entity does
      const bodiesB = this.bodiesByEntity.get(connected.id);
      if (!bodiesB) return; // not made yet: a hull or mesh collider may still be waiting for its model
      if (bodiesB.solid === NO_BODY) {
        return this.refuseJoint(entity, `${connected.name} is joined by it but has no solid 3D collider (not a trigger)`);
      }
      bodyB = bodiesB.solid;
    }
    const motionB = connected ? this.bodiesByEntity.get(connected.id)!.motionType : null;
    if (bodiesA.motionType !== "DYNAMIC" && motionB !== "DYNAMIC") {
      return this.refuseJoint(entity, `${connected ? `neither it nor ${connected.name} is` : "it is not"} a dynamic body, so nothing could move; give a Rigid Body 3D with motion type DYNAMIC to one of them`);
    }
    const type = str(d, "type", "FIXED") as JointType;
    const motorMode = str(d, "motorMode", "OFF") as MotorMode;
    const hasMotor = type === "HINGE" || type === "SLIDER";
    if (!hasMotor && motorMode !== "OFF") {
      return this.refuseJoint(entity, `a ${type} joint has no motor, which only a HINGE or SLIDER has; set the motor mode to OFF`);
    }
    const limited = bool(d, "limitsEnabled", false);
    const limitProblem = limited ? checkLimits(type, num(d, "lowerLimit", 0), num(d, "upperLimit", 0)) : null;
    if (limitProblem) return this.refuseJoint(entity, limitProblem);

    entity.node.computeWorldMatrix(true);
    const matrix = entity.node.getWorldMatrix();
    const anchor = Vector3.TransformCoordinates(new Vector3(num(d, "anchorX", 0), num(d, "anchorY", 0), num(d, "anchorZ", 0)), matrix).scale(METERS_PER_UNIT);
    let anchorB = anchor.clone();
    if (type === "DISTANCE") {
      const local = new Vector3(num(d, "connectedAnchorX", 0), num(d, "connectedAnchorY", 0), num(d, "connectedAnchorZ", 0));
      if (connected) {
        connected.node.computeWorldMatrix(true);
        anchorB = Vector3.TransformCoordinates(local, connected.node.getWorldMatrix()).scale(METERS_PER_UNIT);
      } else {
        anchorB = local.scale(METERS_PER_UNIT);
      }
    }
    const axisLocal = new Vector3(num(d, "axisX", 0), num(d, "axisY", 1), num(d, "axisZ", 0));
    if (axisLocal.lengthSquared() === 0) return this.refuseJoint(entity, "its axis is the zero vector");
    const axis = axisLocal.normalizeToNew().applyRotationQuaternion(entity.getWorldRotation());
    const hinge = type === "HINGE";
    const lower = num(d, "lowerLimit", 0);
    const upper = num(d, "upperLimit", 0);
    const toMotor = (value: number): number => motorTargetInSi(type, value);
    const descriptor: JointDescriptor = {
      type,
      bodyA: bodiesA.solid,
      bodyB,
      anchor: { x: anchor.x, y: anchor.y, z: anchor.z },
      anchorB: { x: anchorB.x, y: anchorB.y, z: anchorB.z },
      axis: { x: axis.x, y: axis.y, z: axis.z },
      collideConnected: bool(d, "collideConnected", false),
      limited,
      lower: hinge ? (lower * Math.PI) / 180 : lower * METERS_PER_UNIT,
      upper: hinge ? (upper * Math.PI) / 180 : upper * METERS_PER_UNIT,
      motorMode,
      motorTarget: hasMotor ? toMotor(num(d, "motorTarget", 0)) : 0,
      motorMaxForce: num(d, "motorMaxForce", 0),
    };
    const handle = this.backend.addJoint(descriptor);
    this.jointsByEntity.set(entity.id, { entity, connected, handle, bodyA: bodiesA.solid, bodyB, type, motorMaxForce: descriptor.motorMaxForce });
  }

  private createBodies(entity: GameEntity): void {
    const rigidBody = entity.getComponent(RIGID_BODY);
    const motion: MotionType = rigidBody ? (str(rigidBody.data, "motionType") as MotionType) : "STATIC";
    const scale = entity.getWorldScale();
    const solid: ShapeDescriptor[] = [];
    const triggers: ShapeDescriptor[] = [];
    for (const collider of enabledColliders(entity)) {
      let shape: ShapeDescriptor | null;
      if (collider.type === HULL_COLLIDER || collider.type === MESH_COLLIDER) {
        shape = this.meshShape(entity, collider, motion, scale);
        // Still loading: no body yet, and nothing is said; the next update tries again
        if (shape === null) return;
      } else {
        shape = describe(collider, scale);
      }
      (bool(collider.data, "trigger", false) ? triggers : solid).push(shape);
    }
    if (this.refused.has(entity.id)) return;
    const position = entity.getWorldPosition().scale(METERS_PER_UNIT);
    const rotation = entity.getWorldRotation();
    const bodies: EntityBodies = { entity, motionType: motion, solid: NO_BODY, sensor: NO_BODY };
    if (solid.length > 0) {
      bodies.solid = this.backend.addBody(descriptor(motion, position, rotation, solid, false, rigidBody));
      this.entityByHandle.set(bodies.solid, entity);
    }
    if (triggers.length > 0) {
      // The sensor follows the entity: it never simulates on its own
      const sensorMotion: MotionType = motion === "STATIC" ? "STATIC" : "KINEMATIC";
      bodies.sensor = this.backend.addBody(descriptor(sensorMotion, position, rotation, triggers, true, rigidBody));
      this.entityByHandle.set(bodies.sensor, entity);
      this.sensorHandles.add(bodies.sensor);
    }
    this.bodiesByEntity.set(entity.id, bodies);
  }

  /**
   * The shape of a hull or mesh collider, or null while its model loads. A collider that can never be built adds a warning and marks
   * the entity as refused (the method then returns a placeholder the caller ignores).
   */
  private meshShape(entity: GameEntity, collider: ComponentData, motion: MotionType, scale: Vector3): ShapeDescriptor | null {
    const refuse = (reason: string): ShapeDescriptor => {
      this.refused.add(entity.id);
      this.warnings.push(`${entity.name}: ${collider.type.replace("Component", "")} refused: ${reason}`);
      return { kind: "SPHERE", radius: 0, offsetX: 0, offsetY: 0, offsetZ: 0 };
    };
    if (collider.type === MESH_COLLIDER && motion !== "STATIC") {
      return refuse("a triangle mesh can only be on a static body; give the entity no Rigid Body 3D, or use a convex hull");
    }
    if (!this.geometry) return refuse("this scene cannot read meshes");
    const result = this.geometry.geometryOf(entity);
    if (result.state === "pending") return null;
    if (result.state === "failed") return refuse(result.reason);
    const { points, indices } = result.geometry;
    if (collider.type === MESH_COLLIDER && indices.length < 3) return refuse("the mesh has no triangles");
    const d = collider.data;
    // The points are in meters with the entity's scale applied already (see MeshColliderGeometry); the offset is scaled like the others
    const offsetX = num(d, "offsetX", 0) * scale.x * METERS_PER_UNIT;
    const offsetY = num(d, "offsetY", 0) * scale.y * METERS_PER_UNIT;
    const offsetZ = num(d, "offsetZ", 0) * scale.z * METERS_PER_UNIT;
    if (collider.type === MESH_COLLIDER) {
      if (indices.length / 3 > MAX_MESH_TRIANGLES_WARNING) {
        this.warnings.push(`${entity.name}: the mesh collider has ${indices.length / 3} triangles; above ${MAX_MESH_TRIANGLES_WARNING} it is slow to build`);
      }
      return { kind: "MESH", points, indices, offsetX, offsetY, offsetZ };
    }
    return { kind: "HULL", points: reduceHullPoints(points), offsetX, offsetY, offsetZ };
  }

  private removeBodies(bodies: EntityBodies): void {
    for (const handle of [bodies.solid, bodies.sensor]) {
      if (handle === NO_BODY) continue;
      this.backend.removeBody(handle);
      this.entityByHandle.delete(handle);
      this.sensorHandles.delete(handle);
    }
    this.bodiesByEntity.delete(bodies.entity.id);
    this.pendingForces.delete(bodies.entity.id);
    this.pendingTorques.delete(bodies.entity.id);
  }

  // --- Transform sync ---

  private pushKinematicPoses(): void {
    for (const bodies of this.bodiesByEntity.values()) {
      const kinematicSolid = bodies.motionType === "KINEMATIC" && bodies.solid !== NO_BODY;
      const movingSensor = bodies.motionType !== "STATIC" && bodies.sensor !== NO_BODY;
      if (!kinematicSolid && !movingSensor) continue;
      const position = bodies.entity.getWorldPosition().scale(METERS_PER_UNIT);
      const rotation = bodies.entity.getWorldRotation();
      if (kinematicSolid) this.backend.moveKinematic(bodies.solid, position, rotation, FIXED_TIMESTEP);
      if (movingSensor) this.backend.moveKinematic(bodies.sensor, position, rotation, FIXED_TIMESTEP);
    }
  }

  private pullDynamicPoses(): void {
    for (const bodies of this.bodiesByEntity.values()) {
      if (bodies.motionType !== "DYNAMIC" || bodies.solid === NO_BODY) continue;
      this.backend.getBodyTransform(bodies.solid, this.tmpPosition, this.tmpRotation);
      setWorldPose(
        bodies.entity,
        this.tmpPosition.x * UNITS_PER_METER,
        this.tmpPosition.y * UNITS_PER_METER,
        this.tmpPosition.z * UNITS_PER_METER,
        this.tmpRotation,
      );
    }
  }

  // --- Events ---

  private dispatchContacts(): void {
    for (const event of this.backend.drainContactEvents()) {
      const a = this.entityByHandle.get(event.bodyA);
      const b = this.entityByHandle.get(event.bodyB);
      if (!a || !b || a === b) continue;
      const trigger = this.sensorHandles.has(event.bodyA) || this.sensorHandles.has(event.bodyB);
      const kind: ContactKind = trigger
        ? event.began ? "TRIGGER_ENTER" : "TRIGGER_EXIT"
        : event.began ? "COLLISION_ENTER" : "COLLISION_EXIT";
      for (const listener of [...this.listeners]) listener({ entityA: a, entityB: b, kind });
    }
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.jointsByEntity.clear();
    this.charactersByEntity.clear();
    this.bodiesByEntity.clear();
    this.entityByHandle.clear();
    this.sensorHandles.clear();
    this.listeners.length = 0;
    this.backend.dispose();
  }
}

/** A hinge turns in radians, a slider moves in meters. */
function motorTargetInSi(type: JointType, target: number): number {
  return type === "HINGE" ? (target * Math.PI) / 180 : target * METERS_PER_UNIT;
}

/** What is wrong with the limits of a joint, or null; the same rules as the desktop's `PhysicsSystem3D.checkLimits`. */
function checkLimits(type: JointType, lower: number, upper: number): string | null {
  if (type === "HINGE" && (lower < -MAX_HINGE_DEGREES || upper > MAX_HINGE_DEGREES || lower > 0 || upper < 0)) {
    return `a hinge with limits ${lower} to ${upper} degrees; they must lie between -180 and 180 and include 0, the angle the hinge starts at`;
  }
  if (type === "SLIDER" && (lower > 0 || upper < 0)) {
    return `a slider with limits ${lower} to ${upper}; they must include 0, the position the slider starts at`;
  }
  if (type === "DISTANCE" && lower < 0) return `a distance joint with a smallest distance of ${lower}; a distance cannot be negative`;
  return null;
}

/** Shape in meters with the entity's world scale applied, as the desktop's `PhysicsSystem3D.describe`. */
export function describe(collider: ComponentData, worldScale: Vector3): ShapeDescriptor {
  const d = collider.data;
  const sx = Math.abs(worldScale.x);
  const sy = Math.abs(worldScale.y);
  const sz = Math.abs(worldScale.z);
  const m = METERS_PER_UNIT;
  const offsetX = num(d, "offsetX", 0) * worldScale.x * m;
  const offsetY = num(d, "offsetY", 0) * worldScale.y * m;
  const offsetZ = num(d, "offsetZ", 0) * worldScale.z * m;
  switch (collider.type) {
    case BOX_COLLIDER:
      return {
        kind: "BOX",
        halfX: num(d, "sizeX") * sx * 0.5 * m,
        halfY: num(d, "sizeY") * sy * 0.5 * m,
        halfZ: num(d, "sizeZ") * sz * 0.5 * m,
        offsetX,
        offsetY,
        offsetZ,
      };
    case SPHERE_COLLIDER:
      return { kind: "SPHERE", radius: num(d, "radius") * Math.max(sx, sy, sz) * m, offsetX, offsetY, offsetZ };
    case CAPSULE_COLLIDER:
      return {
        kind: "CAPSULE",
        radius: num(d, "radius") * Math.max(sx, sz) * m,
        halfHeight: num(d, "height") * sy * 0.5 * m,
        offsetX,
        offsetY,
        offsetZ,
      };
    default:
      throw new Error(`Unsupported 3D collider: ${collider.type} (hull and mesh colliders are built from meshes, not described)`);
  }
}

function descriptor(
  motion: MotionType,
  position: Vector3,
  rotation: Quaternion,
  shapes: ShapeDescriptor[],
  sensor: boolean,
  rigidBody: ComponentData | undefined,
): BodyDescriptor {
  const d = rigidBody?.data;
  return {
    motionType: motion,
    position: { x: position.x, y: position.y, z: position.z },
    rotation: { x: rotation.x, y: rotation.y, z: rotation.z, w: rotation.w },
    shapes,
    sensor,
    mass: d ? num(d, "mass", 1) : 1,
    linearDamping: d ? num(d, "linearDamping", 0.05) : 0.05,
    angularDamping: d ? num(d, "angularDamping", 0.05) : 0.05,
    gravityFactor: d ? num(d, "gravityFactor", 1) : 1,
    friction: d ? num(d, "friction", 0.5) : 0.5,
    restitution: d ? num(d, "restitution", 0) : 0,
    layer: d ? num(d, "layer", DEFAULT_LAYER) : DEFAULT_LAYER,
    collisionMask: d ? num(d, "collisionMask", ALL_LAYERS) : ALL_LAYERS,
  };
}

/** Sets an entity's local pose so its world position (scene units) and rotation become the given ones. */
function setWorldPose(entity: GameEntity, x: number, y: number, z: number, rotation: { x: number; y: number; z: number; w: number }): void {
  const world = new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w);
  if (!entity.parent) {
    entity.node.position.set(x, y, z);
    entity.node.rotationQuaternion = world;
    return;
  }
  entity.node.setAbsolutePosition(new Vector3(x, y, z));
  const parentRotation = entity.parent.getWorldRotation();
  entity.node.rotationQuaternion = Quaternion.Inverse(parentRotation).multiply(world);
}
