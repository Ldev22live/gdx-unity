/**
 * Engine-neutral 3D physics types of the web player: the TypeScript counterpart of the desktop engine's `PhysicsBackend3D` and
 * its descriptors (TD-028, TD-059). Distances here are meters; {@link Physics3D} converts from and to scene units.
 */

/** Scene units per meter; the desktop engine uses the same value. */
export const UNITS_PER_METER = 100;
export const METERS_PER_UNIT = 1 / UNITS_PER_METER;

/** Collision layers an entity can be on, as on the desktop (`PhysicsLayers3D`). */
export const LAYER_COUNT = 16;
export const DEFAULT_LAYER = 0;
/** A mask that includes every layer. */
export const ALL_LAYERS = (1 << LAYER_COUNT) - 1;

/** Two bodies collide when each one's mask contains the other's layer. */
export function layersCollide(layerA: number, maskA: number, layerB: number, maskB: number): boolean {
  return (maskA & (1 << layerB)) !== 0 && (maskB & (1 << layerA)) !== 0;
}

export type MotionType = "STATIC" | "KINEMATIC" | "DYNAMIC";

/** One collision shape of a body, in meters, with its offset from the body's origin. */
export type ShapeDescriptor =
  | { kind: "BOX"; halfX: number; halfY: number; halfZ: number; offsetX: number; offsetY: number; offsetZ: number }
  | { kind: "SPHERE"; radius: number; offsetX: number; offsetY: number; offsetZ: number }
  /** {@code halfHeight} is half the length of the straight part between the two caps. */
  | { kind: "CAPSULE"; radius: number; halfHeight: number; offsetX: number; offsetY: number; offsetZ: number }
  /** A convex hull around {@code points} (x, y, z per point, at most 64, meters, the entity's scale applied). */
  | { kind: "HULL"; points: Float32Array; offsetX: number; offsetY: number; offsetZ: number }
  /** A triangle mesh: {@code points} (x, y, z per vertex, meters) and three {@code indices} per triangle. Static bodies only. */
  | { kind: "MESH"; points: Float32Array; indices: Uint32Array; offsetX: number; offsetY: number; offsetZ: number };

export interface BodyDescriptor {
  motionType: MotionType;
  /** Meters. */
  position: { x: number; y: number; z: number };
  rotation: { x: number; y: number; z: number; w: number };
  shapes: ShapeDescriptor[];
  /** A sensor reports overlaps but never pushes anything. */
  sensor: boolean;
  mass: number;
  linearDamping: number;
  angularDamping: number;
  gravityFactor: number;
  friction: number;
  restitution: number;
  layer: number;
  collisionMask: number;
}

/** Two bodies started or stopped touching; {@code began} is false when they stopped. */
export interface ContactEvent {
  bodyA: number;
  bodyB: number;
  began: boolean;
}

/** Closest ray hit, in meters. */
export interface RayHit {
  body: number;
  point: { x: number; y: number; z: number };
  normal: { x: number; y: number; z: number };
  distance: number;
}

export type JointType = "FIXED" | "HINGE" | "SLIDER" | "DISTANCE" | "BALL";
export type MotorMode = "OFF" | "VELOCITY" | "POSITION";

/** {@code bodyB} of a joint that holds {@code bodyA} against the world. */
export const WORLD = -1;

/**
 * A joint handed to the backend (TD-051, TD-064): meters and radians, everything in world space at the moment of creation. The pose the
 * two bodies have then is the joint's rest pose. The same fields as the desktop's `JointDescriptor3D`.
 */
export interface JointDescriptor {
  type: JointType;
  /** The entity's own body. */
  bodyA: number;
  /** The connected body, or {@link WORLD}. */
  bodyB: number;
  /** The anchor on body A, world space (the joint's one point for BALL, FIXED, HINGE and SLIDER). */
  anchor: { x: number; y: number; z: number };
  /** DISTANCE only: the anchor on body B, world space. */
  anchorB: { x: number; y: number; z: number };
  /** Hinge or slider axis, world space, unit length. */
  axis: { x: number; y: number; z: number };
  collideConnected: boolean;
  /** Whether {@code lower} and {@code upper} apply; for DISTANCE off means "keep the starting distance". */
  limited: boolean;
  /** Radians (hinge) or meters (slider, distance). */
  lower: number;
  upper: number;
  motorMode: MotorMode;
  /** Radians per second or radians (hinge), meters per second or meters (slider). */
  motorTarget: number;
  /** Newton meters (hinge) or newtons (slider). */
  motorMaxForce: number;
}

/**
 * One walking character (TD-049, TD-064), in meters and radians: an upright capsule whose center is at {@code position}. The same
 * fields as the desktop's `CharacterDescriptor3D`.
 */
export interface CharacterDescriptor {
  position: { x: number; y: number; z: number };
  radius: number;
  /** Half of the capsule's cylinder part; the whole capsule is {@code 2 * (halfHeight + radius)} tall. */
  halfHeight: number;
  /** Steepest walkable slope in radians from flat. */
  slopeRadians: number;
  /** Highest step climbed, in meters (0 turns it off). */
  stepHeight: number;
  mass: number;
  layer: number;
  collisionMask: number;
}

/** The simulation behind {@link Physics3D}; bodies are known by number handles. */
export interface PhysicsBackend3D {
  setGravity(x: number, y: number, z: number): void;
  addBody(body: BodyDescriptor): number;
  removeBody(handle: number): void;
  moveKinematic(handle: number, position: { x: number; y: number; z: number }, rotation: { x: number; y: number; z: number; w: number }, deltaSeconds: number): void;
  getBodyTransform(handle: number, outPosition: { x: number; y: number; z: number }, outRotation: { x: number; y: number; z: number; w: number }): void;
  step(deltaSeconds: number): void;
  /** Contact events since the last call, oldest first; the list is emptied. */
  drainContactEvents(): ContactEvent[];
  raycast(origin: { x: number; y: number; z: number }, direction: { x: number; y: number; z: number }, maxDistance: number, layerMask: number): RayHit | null;
  addForce(handle: number, x: number, y: number, z: number): void;
  addTorque(handle: number, x: number, y: number, z: number): void;
  addImpulse(handle: number, x: number, y: number, z: number): void;
  setLinearVelocity(handle: number, x: number, y: number, z: number): void;
  getLinearVelocity(handle: number, out: { x: number; y: number; z: number }): void;
  setAngularVelocity(handle: number, x: number, y: number, z: number): void;
  getAngularVelocity(handle: number, out: { x: number; y: number; z: number }): void;
  /** Handles of the bodies overlapping a sphere, each once; sensors are not reported. */
  overlapSphere(center: { x: number; y: number; z: number }, radius: number, layerMask: number): number[];
  /** Makes a character; its handle is negative, so it never equals a body's. */
  addCharacter(character: CharacterDescriptor): number;
  removeCharacter(handle: number): void;
  /**
   * Moves a character for one step: {@code velocity} (meters per second, gravity already included by the caller) is swept through
   * the world, climbing steps and sticking to slopes it can walk.
   */
  moveCharacter(handle: number, velocity: { x: number; y: number; z: number }, deltaSeconds: number): void;
  /** Puts a character somewhere at once, without sweeping. */
  setCharacterPosition(handle: number, position: { x: number; y: number; z: number }): void;
  getCharacterPosition(handle: number, out: { x: number; y: number; z: number }): void;
  /** Whether a character stands on ground it can walk on. */
  isCharacterGrounded(handle: number): boolean;
  /** Makes a joint between two bodies (or a body and the world); returns its handle. A body with a joint cannot be removed before it. */
  addJoint(joint: JointDescriptor): number;
  removeJoint(handle: number): void;
  /** Changes the motor of a hinge or slider. @throws Error for another type. */
  setJointMotor(handle: number, mode: MotorMode, target: number, maxForce: number): void;
  /** The angle in radians of a hinge, or the position in meters of a slider, from where it stood when the joint was made. */
  getJointPosition(handle: number): number;
  dispose(): void;
}
