import type initJolt from "jolt-physics";
import {
  LAYER_COUNT,
  layersCollide,
  WORLD,
  type BodyDescriptor,
  type CharacterDescriptor,
  type ContactEvent,
  type JointDescriptor,
  type JointType,
  type MotorMode,
  type PhysicsBackend3D,
  type RayHit,
  type ShapeDescriptor,
} from "./types";

/** The Jolt module after loading: the factory function's type also describes everything it adds. */
export type JoltModule = typeof initJolt;
type Jolt = JoltModule;
type JoltBody = InstanceType<Jolt["Body"]>;
type JoltBodyId = InstanceType<Jolt["BodyID"]>;
type JoltShapeSettings = InstanceType<Jolt["ShapeSettings"]>;

let loaded: JoltModule | null = null;
let loading: Promise<JoltModule> | null = null;

/**
 * Loads Jolt Physics (the `jolt-physics` package, the same engine the desktop uses through jolt-jni) once; later calls get the same
 * module. The WebAssembly is inside the script file, so no extra file has to be served. A failed load can be tried again.
 */
export function loadJolt(): Promise<JoltModule> {
  // A dynamic import: the engine is a separate download that only scenes with 3D colliders fetch
  loading ??= import("jolt-physics")
    .then((module) => module.default())
    .then(
      (module) => {
        loaded = module as JoltModule;
        return loaded;
      },
      (error: unknown) => {
        loading = null;
        throw error;
      },
    );
  return loading;
}

/** The module if {@link loadJolt} has finished, else null. */
export function joltIfLoaded(): JoltModule | null {
  return loaded;
}

/** Object layers: two per engine layer, one for bodies that never move and one for bodies that can. */
const OBJECT_LAYERS = LAYER_COUNT * 2;
const BROAD_PHASE_STATIC = 0;
const BROAD_PHASE_MOVING = 1;
const BROAD_PHASE_LAYERS = 2;
/** Collision steps per {@link JoltPhysicsBackend3D.step}: the engine already steps at a fixed 1/60 s. */
const COLLISION_STEPS = 1;
/** Convex radius of a box collider, meters (Jolt's default, as on the desktop). */
const CONVEX_RADIUS = 0.05;
/** The first character handle; the next ones count down from it. */
const FIRST_CHARACTER_HANDLE = -2;
/** A character counts as touching a body that is no further than this (meters) from it, as on the desktop. */
const TOUCH_DISTANCE = 0.05;

/** Jolt's "no body" id, as the index-and-sequence number reads it (all bits set, signed or not). */
function isInvalidBodyHandle(handle: number): boolean {
  return handle === 0xffffffff || handle === -1;
}
/** Rounding of a convex hull's corners, meters: the desktop uses 0.5 cm for hulls too. */
const HULL_CONVEX_RADIUS = 0.005;

function objectLayer(layer: number, moving: boolean): number {
  return layer * 2 + (moving ? 1 : 0);
}

function isMovingLayer(layer: number): boolean {
  return (layer & 1) !== 0;
}

/** A character and the native helpers it needs for every update; all released with it. */
interface CharacterRecord {
  readonly handle: number;
  readonly virtual: InstanceType<JoltModule["CharacterVirtual"]>;
  readonly update: InstanceType<JoltModule["ExtendedUpdateSettings"]>;
  readonly broadPhaseFilter: InstanceType<JoltModule["BroadPhaseLayerFilter"]>;
  readonly objectFilter: InstanceType<JoltModule["ObjectLayerFilter"]>;
  readonly bodyFilter: InstanceType<JoltModule["BodyFilter"]>;
  readonly shapeFilter: InstanceType<JoltModule["ShapeFilter"]>;
  /** Handles of the bodies it touched in the last update, for contact events. */
  readonly touching: Set<number>;
}

interface JointRecord {
  readonly handle: number;
  readonly constraint: InstanceType<JoltModule["Constraint"]>;
  readonly type: JointType;
  readonly bodyA: number;
  readonly bodyB: number;
  /** {@link pairKey} of the two bodies when they must not collide with each other, else null. */
  readonly noCollideKey: string | null;
}

interface BodyRecord {
  readonly handle: number;
  readonly body: JoltBody;
  readonly id: JoltBodyId;
  readonly layer: number;
  readonly mask: number;
  readonly sensor: boolean;
}

/**
 * 3D physics on Jolt's JavaScript build, the web counterpart of the desktop's `JoltPhysicsBackend3D` (TD-059): the same object
 * layer scheme, shapes, sensors and contact events, so a scene behaves alike on both. Every Jolt object made with `new` is released
 * with {@code Jolt.destroy}: the module does not collect them.
 */
export class JoltPhysicsBackend3D implements PhysicsBackend3D {
  private readonly J: Jolt;
  private readonly jolt: InstanceType<Jolt["JoltInterface"]>;
  private readonly system: InstanceType<Jolt["PhysicsSystem"]>;
  private readonly bodies: InstanceType<Jolt["BodyInterface"]>;
  private readonly records = new Map<number, BodyRecord>();
  private readonly joints = new Map<number, JointRecord>();
  private readonly characters = new Map<number, CharacterRecord>();
  /** Characters count down from here, so a handle never equals a body's (those are positive) or {@link WORLD}. */
  private nextCharacterHandle = FIRST_CHARACTER_HANDLE;
  private gravityValues: [number, number, number] = [0, -9.81, 0];
  /** Body pairs that are joined and must not collide; refused in the contact validation callback. */
  private readonly noCollidePairs = new Set<string>();
  private nextJointHandle = 1;
  private readonly jointTemporaries: unknown[] = [];
  private readonly contactListener: InstanceType<Jolt["ContactListenerJS"]>;
  /** Sub-shape contacts alive per body pair: a pair is one contact however many sub-shapes touch. */
  private readonly pairContacts = new Map<string, number>();
  private events: ContactEvent[] = [];
  private readonly owned: unknown[] = [];
  private readonly broadPhaseFilter: InstanceType<Jolt["BroadPhaseLayerFilter"]>;
  private readonly objectFilter: InstanceType<Jolt["ObjectLayerFilter"]>;
  private readonly bodyFilter: InstanceType<Jolt["BodyFilter"]>;
  private readonly shapeFilter: InstanceType<Jolt["ShapeFilter"]>;
  private disposed = false;

  constructor(module: JoltModule) {
    const J = module;
    this.J = J;
    const settings = new J.JoltSettings();
    const pairs = new J.ObjectLayerPairFilterTable(OBJECT_LAYERS);
    const broadPhases = new J.BroadPhaseLayerInterfaceTable(OBJECT_LAYERS, BROAD_PHASE_LAYERS);
    const staticLayer = new J.BroadPhaseLayer(BROAD_PHASE_STATIC);
    const movingLayer = new J.BroadPhaseLayer(BROAD_PHASE_MOVING);
    for (let layer = 0; layer < LAYER_COUNT; layer++) {
      broadPhases.MapObjectToBroadPhaseLayer(objectLayer(layer, false), staticLayer);
      broadPhases.MapObjectToBroadPhaseLayer(objectLayer(layer, true), movingLayer);
    }
    // Everything may meet everything except two bodies that never move; the layer masks are checked when a contact starts
    for (let a = 0; a < OBJECT_LAYERS; a++) {
      for (let b = a; b < OBJECT_LAYERS; b++) {
        if (isMovingLayer(a) || isMovingLayer(b)) pairs.EnableCollision(a, b);
        else pairs.DisableCollision(a, b);
      }
    }
    const layersVsBroadPhase = new J.ObjectVsBroadPhaseLayerFilterTable(broadPhases, BROAD_PHASE_LAYERS, pairs, OBJECT_LAYERS);
    settings.mObjectLayerPairFilter = pairs;
    settings.mBroadPhaseLayerInterface = broadPhases;
    settings.mObjectVsBroadPhaseLayerFilter = layersVsBroadPhase;
    this.jolt = new J.JoltInterface(settings);
    // The interface takes over the three tables (it frees them itself: destroying them here would crash); only these are ours
    this.owned.push(settings, staticLayer, movingLayer);
    this.system = this.jolt.GetPhysicsSystem();
    this.bodies = this.system.GetBodyInterface();
    this.broadPhaseFilter = new J.BroadPhaseLayerFilter();
    this.objectFilter = new J.ObjectLayerFilter();
    this.bodyFilter = new J.BodyFilter();
    this.shapeFilter = new J.ShapeFilter();
    this.contactListener = this.createContactListener();
    this.system.SetContactListener(this.contactListener);
  }

  private createContactListener(): InstanceType<Jolt["ContactListenerJS"]> {
    const J = this.J;
    const listener = new J.ContactListenerJS();
    const handleOf = (pointer: number): number => J.wrapPointer(pointer, J.Body).GetID().GetIndexAndSequenceNumber();
    listener.OnContactValidate = (first: number, second: number): number => {
      const a = this.records.get(handleOf(first));
      const b = this.records.get(handleOf(second));
      const joined = a !== undefined && b !== undefined && this.noCollidePairs.has(pairKey(a.handle, b.handle));
      const collide = !joined && (!a || !b || layersCollide(a.layer, a.mask, b.layer, b.mask));
      return collide ? J.ValidateResult_AcceptAllContactsForThisBodyPair : J.ValidateResult_RejectAllContactsForThisBodyPair;
    };
    listener.OnContactAdded = (first: number, second: number): void => {
      this.contactAdded(handleOf(first), handleOf(second));
    };
    listener.OnContactPersisted = (): void => {};
    listener.OnContactRemoved = (pair: number): void => {
      const ids = J.wrapPointer(pair, J.SubShapeIDPair);
      this.contactRemoved(ids.GetBody1ID().GetIndexAndSequenceNumber(), ids.GetBody2ID().GetIndexAndSequenceNumber());
    };
    return listener;
  }

  setGravity(x: number, y: number, z: number): void {
    this.requireOpen();
    const gravity = new this.J.Vec3(x, y, z);
    this.system.SetGravity(gravity);
    this.J.destroy(gravity);
    // The characters' updates need the gravity as well; the backend keeps the numbers
    this.gravityValues = [x, y, z];
  }

  addBody(body: BodyDescriptor): number {
    this.requireOpen();
    if (!Number.isInteger(body.layer) || body.layer < 0 || body.layer >= LAYER_COUNT) {
      throw new Error(`layer must be between 0 and ${LAYER_COUNT - 1}: ${body.layer}`);
    }
    const J = this.J;
    const moving = body.motionType !== "STATIC";
    const shapeSettings = this.shapeSettingsOf(body.shapes);
    const result = shapeSettings.Create();
    if (result.HasError()) {
      const message = result.GetError().c_str();
      J.destroy(result);
      J.destroy(shapeSettings);
      throw new Error(`Jolt rejected the collision shape: ${message}`);
    }
    const position = new J.RVec3(body.position.x, body.position.y, body.position.z);
    const rotation = new J.Quat(body.rotation.x, body.rotation.y, body.rotation.z, body.rotation.w);
    const motion =
      body.motionType === "STATIC" ? J.EMotionType_Static : body.motionType === "KINEMATIC" ? J.EMotionType_Kinematic : J.EMotionType_Dynamic;
    const creation = new J.BodyCreationSettings(result.Get(), position, rotation, motion, objectLayer(body.layer, moving));
    creation.mIsSensor = body.sensor;
    creation.mAllowSleeping = false;
    creation.mFriction = body.friction;
    creation.mRestitution = body.restitution;
    creation.mLinearDamping = body.linearDamping;
    creation.mAngularDamping = body.angularDamping;
    creation.mGravityFactor = body.gravityFactor;
    if (body.motionType === "DYNAMIC") {
      // Keep the shape's inertia but give it the component's mass
      creation.mOverrideMassProperties = J.EOverrideMassProperties_CalculateInertia;
      creation.mMassPropertiesOverride.mMass = body.mass;
    }
    const created = this.bodies.CreateBody(creation);
    const id = created.GetID();
    this.bodies.AddBody(id, moving ? J.EActivation_Activate : J.EActivation_DontActivate);
    const handle = id.GetIndexAndSequenceNumber();
    this.records.set(handle, { handle, body: created, id, layer: body.layer, mask: body.collisionMask, sensor: body.sensor });
    J.destroy(creation);
    J.destroy(position);
    J.destroy(rotation);
    J.destroy(result);
    J.destroy(shapeSettings);
    return handle;
  }

  /** One shape, or a compound when there are several or one is moved off the body's origin. */
  private shapeSettingsOf(shapes: ShapeDescriptor[]): JoltShapeSettings {
    const J = this.J;
    if (shapes.length === 0) throw new Error("A body needs at least one collision shape");
    const first = shapes[0]!;
    if (shapes.length === 1 && first.offsetX === 0 && first.offsetY === 0 && first.offsetZ === 0) return this.singleShape(first);
    const compound = new J.StaticCompoundShapeSettings();
    const identity = new J.Quat(0, 0, 0, 1);
    for (const shape of shapes) {
      const offset = new J.Vec3(shape.offsetX, shape.offsetY, shape.offsetZ);
      // The compound keeps its parts' settings: they live as long as it does, so they are not released here
      compound.AddShape(offset, identity, this.singleShape(shape), 0);
      J.destroy(offset);
    }
    J.destroy(identity);
    return compound;
  }

  private singleShape(shape: ShapeDescriptor): JoltShapeSettings {
    const J = this.J;
    switch (shape.kind) {
      case "BOX": {
        const half = new J.Vec3(shape.halfX, shape.halfY, shape.halfZ);
        const settings = new J.BoxShapeSettings(half, CONVEX_RADIUS);
        J.destroy(half);
        return settings;
      }
      case "SPHERE":
        return new J.SphereShapeSettings(shape.radius);
      case "CAPSULE":
        // Jolt's capsule takes the half height of the straight part first, then the radius
        return new J.CapsuleShapeSettings(shape.halfHeight, shape.radius);
      case "HULL": {
        const settings = new J.ConvexHullShapeSettings();
        settings.mMaxConvexRadius = HULL_CONVEX_RADIUS;
        for (let i = 0; i + 2 < shape.points.length; i += 3) {
          const point = new J.Vec3(shape.points[i]!, shape.points[i + 1]!, shape.points[i + 2]!);
          settings.mPoints.push_back(point);
          J.destroy(point);
        }
        return settings;
      }
      case "MESH": {
        const vertices = new J.VertexList();
        for (let i = 0; i + 2 < shape.points.length; i += 3) {
          const vertex = new J.Float3(shape.points[i]!, shape.points[i + 1]!, shape.points[i + 2]!);
          vertices.push_back(vertex);
          J.destroy(vertex);
        }
        const triangles = new J.IndexedTriangleList();
        for (let i = 0; i + 2 < shape.indices.length; i += 3) {
          const triangle = new J.IndexedTriangle(shape.indices[i]!, shape.indices[i + 1]!, shape.indices[i + 2]!, 0);
          triangles.push_back(triangle);
          J.destroy(triangle);
        }
        const materials = new J.PhysicsMaterialList();
        const settings = new J.MeshShapeSettings(vertices, triangles, materials);
        J.destroy(vertices);
        J.destroy(triangles);
        J.destroy(materials);
        return settings;
      }
    }
  }

  removeBody(handle: number): void {
    const record = this.requireBody(handle);
    for (const joint of this.joints.values()) {
      if (joint.bodyA === handle || joint.bodyB === handle) throw new Error(`Body ${handle} still has a joint; remove the joint first`);
    }
    this.records.delete(handle);
    this.bodies.RemoveBody(record.id);
    this.bodies.DestroyBody(record.id);
    this.endContactsOf(handle);
  }

  /** Jolt does not report the contacts of a removed body ending: do it here so listeners can let go. */
  private endContactsOf(handle: number): void {
    for (const key of [...this.pairContacts.keys()]) {
      const [first, second] = key.split("_").map(Number) as [number, number];
      if (first === handle || second === handle) {
        this.events.push({ bodyA: first, bodyB: second, began: false });
        this.pairContacts.delete(key);
      }
    }
  }

  moveKinematic(
    handle: number,
    position: { x: number; y: number; z: number },
    rotation: { x: number; y: number; z: number; w: number },
    deltaSeconds: number,
  ): void {
    const record = this.requireBody(handle);
    const p = new this.J.RVec3(position.x, position.y, position.z);
    const q = new this.J.Quat(rotation.x, rotation.y, rotation.z, rotation.w);
    this.bodies.MoveKinematic(record.id, p, q, deltaSeconds);
    this.J.destroy(p);
    this.J.destroy(q);
  }

  getBodyTransform(
    handle: number,
    outPosition: { x: number; y: number; z: number },
    outRotation: { x: number; y: number; z: number; w: number },
  ): void {
    const record = this.requireBody(handle);
    // These return Jolt's own scratch objects: read them at once
    const position = this.bodies.GetPosition(record.id);
    outPosition.x = position.GetX();
    outPosition.y = position.GetY();
    outPosition.z = position.GetZ();
    const rotation = this.bodies.GetRotation(record.id);
    outRotation.x = rotation.GetX();
    outRotation.y = rotation.GetY();
    outRotation.z = rotation.GetZ();
    outRotation.w = rotation.GetW();
  }

  step(deltaSeconds: number): void {
    this.requireOpen();
    this.jolt.Step(deltaSeconds, COLLISION_STEPS);
  }

  drainContactEvents(): ContactEvent[] {
    const drained = this.events;
    this.events = [];
    return drained;
  }

  raycast(
    origin: { x: number; y: number; z: number },
    direction: { x: number; y: number; z: number },
    maxDistance: number,
    layerMask: number,
  ): RayHit | null {
    this.requireOpen();
    const J = this.J;
    const length = Math.hypot(direction.x, direction.y, direction.z);
    if (length === 0) throw new Error("A ray needs a direction");
    const dx = direction.x / length;
    const dy = direction.y / length;
    const dz = direction.z / length;
    const from = new J.RVec3(origin.x, origin.y, origin.z);
    const along = new J.Vec3(dx * maxDistance, dy * maxDistance, dz * maxDistance);
    const ray = new J.RRayCast(from, along);
    const settings = new J.RayCastSettings();
    const collector = new J.CastRayAllHitCollisionCollector();
    this.system
      .GetNarrowPhaseQuery()
      .CastRay(ray, settings, collector, this.broadPhaseFilter, this.objectFilter, this.bodyFilter, this.shapeFilter);
    let best: { record: BodyRecord; fraction: number; subShape: InstanceType<Jolt["SubShapeID"]> } | null = null;
    const hits = collector.mHits;
    for (let i = 0; i < hits.size(); i++) {
      const hit = hits.at(i);
      const record = this.records.get(hit.mBodyID.GetIndexAndSequenceNumber());
      const allowed = record !== undefined && !record.sensor && (layerMask & (1 << record.layer)) !== 0;
      if (allowed && (best === null || hit.mFraction < best.fraction)) {
        best = { record, fraction: hit.mFraction, subShape: hit.mSubShapeID2 };
      }
    }
    let result: RayHit | null = null;
    if (best) {
      const distance = best.fraction * maxDistance;
      const point = { x: origin.x + dx * distance, y: origin.y + dy * distance, z: origin.z + dz * distance };
      const at = new J.RVec3(point.x, point.y, point.z);
      const normal = best.record.body.GetWorldSpaceSurfaceNormal(best.subShape, at);
      result = { body: best.record.handle, point, normal: { x: normal.GetX(), y: normal.GetY(), z: normal.GetZ() }, distance };
      J.destroy(at);
    }
    J.destroy(collector);
    J.destroy(settings);
    J.destroy(ray);
    J.destroy(along);
    J.destroy(from);
    return result;
  }

  addForce(handle: number, x: number, y: number, z: number): void {
    this.withVec(x, y, z, (v) => this.bodies.AddForce(this.requireBody(handle).id, v, this.J.EActivation_Activate));
  }

  addTorque(handle: number, x: number, y: number, z: number): void {
    this.withVec(x, y, z, (v) => this.bodies.AddTorque(this.requireBody(handle).id, v, this.J.EActivation_Activate));
  }

  addImpulse(handle: number, x: number, y: number, z: number): void {
    this.withVec(x, y, z, (v) => this.bodies.AddImpulse(this.requireBody(handle).id, v));
  }

  setLinearVelocity(handle: number, x: number, y: number, z: number): void {
    this.withVec(x, y, z, (v) => this.bodies.SetLinearVelocity(this.requireBody(handle).id, v));
  }

  getLinearVelocity(handle: number, out: { x: number; y: number; z: number }): void {
    const velocity = this.bodies.GetLinearVelocity(this.requireBody(handle).id);
    out.x = velocity.GetX();
    out.y = velocity.GetY();
    out.z = velocity.GetZ();
  }

  setAngularVelocity(handle: number, x: number, y: number, z: number): void {
    this.withVec(x, y, z, (v) => this.bodies.SetAngularVelocity(this.requireBody(handle).id, v));
  }

  getAngularVelocity(handle: number, out: { x: number; y: number; z: number }): void {
    const velocity = this.bodies.GetAngularVelocity(this.requireBody(handle).id);
    out.x = velocity.GetX();
    out.y = velocity.GetY();
    out.z = velocity.GetZ();
  }

  private withVec(x: number, y: number, z: number, use: (vector: InstanceType<Jolt["Vec3"]>) => void): void {
    this.requireOpen();
    const vector = new this.J.Vec3(x, y, z);
    try {
      use(vector);
    } finally {
      this.J.destroy(vector);
    }
  }

  overlapSphere(center: { x: number; y: number; z: number }, radius: number, layerMask: number): number[] {
    this.requireOpen();
    const J = this.J;
    const sphere = new J.SphereShape(radius);
    const scale = new J.Vec3(1, 1, 1);
    const at = new J.RVec3(center.x, center.y, center.z);
    const transform = J.RMat44.prototype.sTranslation(at);
    const settings = new J.CollideShapeSettings();
    const baseOffset = new J.RVec3(0, 0, 0);
    const collector = new J.CollideShapeAllHitCollisionCollector();
    this.system
      .GetNarrowPhaseQuery()
      .CollideShape(sphere, scale, transform, settings, baseOffset, collector, this.broadPhaseFilter, this.objectFilter, this.bodyFilter, this.shapeFilter);
    const found = new Set<number>();
    const hits = collector.mHits;
    for (let i = 0; i < hits.size(); i++) {
      const record = this.records.get(hits.at(i).mBodyID2.GetIndexAndSequenceNumber());
      if (record && !record.sensor && (layerMask & (1 << record.layer)) !== 0) found.add(record.handle);
    }
    J.destroy(collector);
    J.destroy(baseOffset);
    J.destroy(settings);
    J.destroy(at);
    J.destroy(scale);
    J.destroy(sphere);
    return [...found];
  }

  dispose(): void {
    if (this.disposed) return;
    // Joints go first: they hold on to the bodies, and removing a joint needs the backend open
    for (const joint of [...this.joints.keys()]) this.removeJoint(joint);
    this.disposed = true;
    const J = this.J;
    for (const character of this.characters.values()) this.releaseCharacter(character);
    this.characters.clear();
    for (const record of this.records.values()) {
      this.bodies.RemoveBody(record.id);
      this.bodies.DestroyBody(record.id);
    }
    this.records.clear();
    this.pairContacts.clear();
    this.events = [];
    // The system calls the listener, so it goes first; the tables the interface points to go after it
    J.destroy(this.jolt);
    J.destroy(this.contactListener);
    for (const object of [this.broadPhaseFilter, this.objectFilter, this.bodyFilter, this.shapeFilter, ...this.owned]) J.destroy(object);
    this.owned.length = 0;
  }

  // --- Characters (TD-049, TD-064) ---

  addCharacter(description: CharacterDescriptor): number {
    this.requireOpen();
    const J = this.J;
    if (!Number.isInteger(description.layer) || description.layer < 0 || description.layer >= LAYER_COUNT) {
      throw new Error(`layer must be between 0 and ${LAYER_COUNT - 1}: ${description.layer}`);
    }
    if (!(description.radius > 0) || !(description.halfHeight > 0)) {
      throw new Error(`A character needs a positive radius and height: ${description.radius}, ${description.halfHeight}`);
    }
    const movingLayer = objectLayer(description.layer, true);
    const shapeSettings = new J.CapsuleShapeSettings(description.halfHeight, description.radius);
    const result = shapeSettings.Create();
    if (result.HasError()) {
      const message = result.GetError().c_str();
      J.destroy(result);
      J.destroy(shapeSettings);
      throw new Error(`Jolt rejected the character shape: ${message}`);
    }
    const settings = new J.CharacterVirtualSettings();
    const up = new J.Vec3(0, 1, 0);
    // Only the capsule's lower sphere can support the character; the plane is relative to the capsule's center
    const supporting = new J.Plane(up, description.halfHeight - description.radius);
    settings.mShape = result.Get();
    settings.mUp = up;
    settings.mMaxSlopeAngle = description.slopeRadians;
    settings.mMass = description.mass;
    settings.mSupportingVolume = supporting;
    const position = new J.RVec3(description.position.x, description.position.y, description.position.z);
    const rotation = new J.Quat(0, 0, 0, 1);
    const virtual = new J.CharacterVirtual(settings, position, rotation, this.system);
    const update = new J.ExtendedUpdateSettings();
    const stepDown = new J.Vec3(0, -description.stepHeight, 0);
    const stepUp = new J.Vec3(0, description.stepHeight, 0);
    update.mStickToFloorStepDown = stepDown;
    update.mWalkStairsStepUp = stepUp;
    const handle = this.nextCharacterHandle--;
    this.characters.set(handle, {
      handle,
      virtual,
      update,
      broadPhaseFilter: new J.DefaultBroadPhaseLayerFilter(this.jolt.GetObjectVsBroadPhaseLayerFilter(), movingLayer),
      objectFilter: new J.DefaultObjectLayerFilter(this.jolt.GetObjectLayerPairFilter(), movingLayer),
      bodyFilter: new J.BodyFilter(),
      shapeFilter: new J.ShapeFilter(),
      touching: new Set<number>(),
    });
    for (const object of [stepDown, stepUp, rotation, position, supporting, up, settings, result, shapeSettings]) J.destroy(object);
    return handle;
  }

  removeCharacter(handle: number): void {
    this.requireOpen();
    const character = this.requireCharacter(handle);
    this.characters.delete(handle);
    this.releaseCharacter(character);
    this.endContactsOf(handle);
  }

  private releaseCharacter(character: CharacterRecord): void {
    for (const object of [character.objectFilter, character.broadPhaseFilter, character.shapeFilter, character.bodyFilter, character.update, character.virtual]) {
      this.J.destroy(object);
    }
  }

  moveCharacter(handle: number, velocity: { x: number; y: number; z: number }, deltaSeconds: number): void {
    this.requireOpen();
    const J = this.J;
    const character = this.requireCharacter(handle);
    const speed = new J.Vec3(velocity.x, velocity.y, velocity.z);
    const gravity = new J.Vec3(...this.gravityValues);
    character.virtual.SetLinearVelocity(speed);
    character.virtual.ExtendedUpdate(
      deltaSeconds,
      gravity,
      character.update,
      character.broadPhaseFilter,
      character.objectFilter,
      character.bodyFilter,
      character.shapeFilter,
      this.jolt.GetTempAllocator(),
    );
    J.destroy(speed);
    J.destroy(gravity);
    this.reportCharacterContacts(character);
  }

  setCharacterPosition(handle: number, position: { x: number; y: number; z: number }): void {
    this.requireOpen();
    const at = new this.J.RVec3(position.x, position.y, position.z);
    this.requireCharacter(handle).virtual.SetPosition(at);
    this.J.destroy(at);
  }

  getCharacterPosition(handle: number, out: { x: number; y: number; z: number }): void {
    this.requireOpen();
    const position = this.requireCharacter(handle).virtual.GetPosition();
    out.x = position.GetX();
    out.y = position.GetY();
    out.z = position.GetZ();
  }

  isCharacterGrounded(handle: number): boolean {
    this.requireOpen();
    return this.requireCharacter(handle).virtual.GetGroundState() === this.J.EGroundState_OnGround;
  }

  private requireCharacter(handle: number): CharacterRecord {
    const character = this.characters.get(handle);
    if (!character) throw new Error(`No such character (removed already?): ${handle}`);
    return character;
  }

  /** Turns the bodies a character touches now, compared with the last update, into contact events under its handle. */
  private reportCharacterContacts(character: CharacterRecord): void {
    const contacts = character.virtual.GetActiveContacts();
    const now = new Set<number>();
    for (let i = 0; i < contacts.size(); i++) {
      const contact = contacts.at(i);
      const other = contact.mBodyB.GetIndexAndSequenceNumber();
      if (!isInvalidBodyHandle(other) && contact.mDistance <= TOUCH_DISTANCE) now.add(other);
    }
    for (const other of now) {
      if (!character.touching.has(other)) {
        character.touching.add(other);
        this.contactAdded(character.handle, other);
      }
    }
    for (const other of [...character.touching]) {
      if (!now.has(other)) {
        character.touching.delete(other);
        this.contactRemoved(character.handle, other);
      }
    }
  }

  // --- Joints (TD-051, TD-064) ---

  addJoint(joint: JointDescriptor): number {
    this.requireOpen();
    const J = this.J;
    const first = this.requireBody(joint.bodyA);
    const toWorld = joint.bodyB === WORLD;
    const second = toWorld ? null : this.requireBody(joint.bodyB);
    const settings = this.jointSettings(joint);
    const temporaries = this.jointTemporaries.splice(0);
    // Jolt measures a hinge angle or slider position of body 2 relative to body 1, so the connected body (or the world) is body 1:
    // a positive position then means this entity moved along the axis
    const connected = toWorld ? this.jolt.sGetFixedToWorldBody() : second!.body;
    const constraint = settings.Create(connected, first.body);
    // The settings copied the points and axes they were given; ours are released with them
    J.destroy(settings);
    for (const temporary of temporaries) J.destroy(temporary as object);
    if (!constraint) throw new Error(`Jolt could not make the ${joint.type} joint`);
    this.system.AddConstraint(constraint);
    let noCollideKey: string | null = null;
    if (!joint.collideConnected && !toWorld) {
      noCollideKey = pairKey(joint.bodyA, joint.bodyB);
      this.noCollidePairs.add(noCollideKey);
    }
    const handle = this.nextJointHandle++;
    const made: JointRecord = { handle, constraint, type: joint.type, bodyA: joint.bodyA, bodyB: joint.bodyB, noCollideKey };
    this.joints.set(handle, made);
    this.applyMotor(made, joint.motorMode, joint.motorTarget, joint.motorMaxForce);
    return handle;
  }

  removeJoint(handle: number): void {
    this.requireOpen();
    const joint = this.requireJoint(handle);
    this.joints.delete(handle);
    if (joint.noCollideKey !== null) this.noCollidePairs.delete(joint.noCollideKey);
    // The system holds the only reference: removing the constraint frees it, so it must not be released again (doing so corrupted memory,
    // which a real browser showed as "memory access out of bounds" when a scene with joints stopped, and broke every later scene)
    this.system.RemoveConstraint(joint.constraint);
  }

  setJointMotor(handle: number, mode: MotorMode, target: number, maxForce: number): void {
    this.requireOpen();
    this.applyMotor(this.requireJoint(handle), mode, target, maxForce);
  }

  getJointPosition(handle: number): number {
    this.requireOpen();
    const joint = this.requireJoint(handle);
    if (joint.type === "HINGE") return this.J.castObject(joint.constraint, this.J.HingeConstraint).GetCurrentAngle();
    if (joint.type === "SLIDER") return this.J.castObject(joint.constraint, this.J.SliderConstraint).GetCurrentPosition();
    throw new Error(`A ${joint.type} joint has no position; only a HINGE or SLIDER has`);
  }

  /** Remembers a Jolt object that only has to live until the constraint settings were used (see {@link addJoint}). */
  private temporary<T>(object: T): T {
    this.jointTemporaries.push(object);
    return object;
  }

  private requireJoint(handle: number): JointRecord {
    const joint = this.joints.get(handle);
    if (!joint) throw new Error(`No such joint (removed already?): ${handle}`);
    return joint;
  }

  private applyMotor(joint: JointRecord, mode: MotorMode, target: number, maxForce: number): void {
    const J = this.J;
    const state = mode === "OFF" ? J.EMotorState_Off : mode === "VELOCITY" ? J.EMotorState_Velocity : J.EMotorState_Position;
    if (joint.type === "HINGE") {
      const hinge = J.castObject(joint.constraint, J.HingeConstraint);
      hinge.GetMotorSettings().mMinTorqueLimit = -maxForce;
      hinge.GetMotorSettings().mMaxTorqueLimit = maxForce;
      if (mode === "VELOCITY") hinge.SetTargetAngularVelocity(target);
      if (mode === "POSITION") hinge.SetTargetAngle(target);
      hinge.SetMotorState(state);
    } else if (joint.type === "SLIDER") {
      const slider = J.castObject(joint.constraint, J.SliderConstraint);
      slider.GetMotorSettings().mMinForceLimit = -maxForce;
      slider.GetMotorSettings().mMaxForceLimit = maxForce;
      if (mode === "VELOCITY") slider.SetTargetVelocity(target);
      if (mode === "POSITION") slider.SetTargetPosition(target);
      slider.SetMotorState(state);
    } else if (mode !== "OFF") {
      throw new Error(`A ${joint.type} joint has no motor; only a HINGE or SLIDER has`);
    }
  }

  /** Settings in world space: Jolt turns the points and axes into each body's local frame, which makes the current pose the rest pose. */
  private jointSettings(joint: JointDescriptor): InstanceType<JoltModule["TwoBodyConstraintSettings"]> {
    const J = this.J;
    const anchor = joint.anchor;
    const axis = normalized(joint.axis);
    const point = (): InstanceType<JoltModule["RVec3"]> => this.temporary(new J.RVec3(anchor.x, anchor.y, anchor.z));
    const vector = (v: { x: number; y: number; z: number }): InstanceType<JoltModule["Vec3"]> => this.temporary(new J.Vec3(v.x, v.y, v.z));
    switch (joint.type) {
      case "FIXED": {
        const settings = new J.FixedConstraintSettings();
        settings.mSpace = J.EConstraintSpace_WorldSpace;
        settings.mAutoDetectPoint = false;
        settings.mPoint1 = point();
        settings.mPoint2 = point();
        settings.mAxisX1 = vector({ x: 1, y: 0, z: 0 });
        settings.mAxisX2 = vector({ x: 1, y: 0, z: 0 });
        settings.mAxisY1 = vector({ x: 0, y: 1, z: 0 });
        settings.mAxisY2 = vector({ x: 0, y: 1, z: 0 });
        return settings;
      }
      case "HINGE": {
        const normal = perpendicularTo(axis);
        const settings = new J.HingeConstraintSettings();
        settings.mSpace = J.EConstraintSpace_WorldSpace;
        settings.mPoint1 = point();
        settings.mPoint2 = point();
        settings.mHingeAxis1 = vector(axis);
        settings.mHingeAxis2 = vector(axis);
        settings.mNormalAxis1 = vector(normal);
        settings.mNormalAxis2 = vector(normal);
        settings.mLimitsMin = joint.limited ? joint.lower : -Math.PI;
        settings.mLimitsMax = joint.limited ? joint.upper : Math.PI;
        return settings;
      }
      case "SLIDER": {
        const normal = perpendicularTo(axis);
        const settings = new J.SliderConstraintSettings();
        settings.mSpace = J.EConstraintSpace_WorldSpace;
        settings.mAutoDetectPoint = false;
        settings.mPoint1 = point();
        settings.mPoint2 = point();
        settings.mSliderAxis1 = vector(axis);
        settings.mSliderAxis2 = vector(axis);
        settings.mNormalAxis1 = vector(normal);
        settings.mNormalAxis2 = vector(normal);
        if (joint.limited) {
          settings.mLimitsMin = joint.lower;
          settings.mLimitsMax = joint.upper;
        }
        return settings;
      }
      case "DISTANCE": {
        const other = joint.anchorB;
        const settings = new J.DistanceConstraintSettings();
        settings.mSpace = J.EConstraintSpace_WorldSpace;
        // Point 1 belongs to body 1, the connected body
        settings.mPoint1 = this.temporary(new J.RVec3(other.x, other.y, other.z));
        settings.mPoint2 = point();
        const now = Math.hypot(anchor.x - other.x, anchor.y - other.y, anchor.z - other.z);
        settings.mMinDistance = joint.limited ? joint.lower : now;
        settings.mMaxDistance = joint.limited ? joint.upper : now;
        return settings;
      }
      case "BALL": {
        const settings = new J.PointConstraintSettings();
        settings.mSpace = J.EConstraintSpace_WorldSpace;
        settings.mPoint1 = point();
        settings.mPoint2 = point();
        return settings;
      }
    }
  }

  // --- Contacts ---

  private contactAdded(a: number, b: number): void {
    const key = pairKey(a, b);
    const count = (this.pairContacts.get(key) ?? 0) + 1;
    this.pairContacts.set(key, count);
    if (count === 1) this.events.push({ bodyA: Math.min(a, b), bodyB: Math.max(a, b), began: true });
  }

  private contactRemoved(a: number, b: number): void {
    const key = pairKey(a, b);
    const count = this.pairContacts.get(key);
    // No count: the pair was reported ended already, when one of the bodies was removed
    if (count === undefined) return;
    if (count === 1) {
      this.pairContacts.delete(key);
      this.events.push({ bodyA: Math.min(a, b), bodyB: Math.max(a, b), began: false });
    } else {
      this.pairContacts.set(key, count - 1);
    }
  }

  private requireBody(handle: number): BodyRecord {
    this.requireOpen();
    const record = this.records.get(handle);
    if (!record) throw new Error(`No such body (removed already?): ${handle}`);
    return record;
  }

  private requireOpen(): void {
    if (this.disposed) throw new Error("The physics backend was disposed");
  }
}

function pairKey(a: number, b: number): string {
  return `${Math.min(a, b)}_${Math.max(a, b)}`;
}

function normalized(v: { x: number; y: number; z: number }): { x: number; y: number; z: number } {
  const length = Math.hypot(v.x, v.y, v.z);
  if (length === 0) throw new Error("A joint axis cannot be the zero vector");
  return { x: v.x / length, y: v.y / length, z: v.z / length };
}

/** Any unit vector at a right angle to {@code axis}; a hinge measures its angle from it. */
function perpendicularTo(axis: { x: number; y: number; z: number }): { x: number; y: number; z: number } {
  const helper = Math.abs(axis.x) < 0.9 ? { x: 1, y: 0, z: 0 } : { x: 0, y: 1, z: 0 };
  return normalized({
    x: axis.y * helper.z - axis.z * helper.y,
    y: axis.z * helper.x - axis.x * helper.z,
    z: axis.x * helper.y - axis.y * helper.x,
  });
}
