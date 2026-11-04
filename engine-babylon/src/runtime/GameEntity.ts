import { type Mesh, Quaternion, StandardMaterial, TransformNode, Vector3 } from "@babylonjs/core";
import type { ComponentData, EntityData } from "../scene/types";

/**
 * One entity of a playing scene: the file's data plus the Babylon node that carries its transform. Meshes and lights
 * of its components hang under {@link node}, so disabling the node hides the whole entity.
 */
export class GameEntity {
  readonly id: string;
  readonly name: string;
  /** Components of the entity. Scripts add to it through the scene's factory methods, never directly. */
  readonly components: ComponentData[];
  /** Meshes drawn for the entity's Mesh Primitive and Shape Renderer components. */
  readonly meshes: Mesh[] = [];
  /** Position, rotation and scale (in the parent's space); the single source of truth for where the entity is. */
  readonly node: TransformNode;
  parent: GameEntity | null = null;
  readonly children: GameEntity[] = [];
  private activeSelf: boolean;

  constructor(data: EntityData, node: TransformNode) {
    this.id = data.id;
    this.name = data.name;
    this.components = data.components;
    this.node = node;
    this.activeSelf = data.active;
  }

  get isActiveSelf(): boolean {
    return this.activeSelf;
  }

  /** True when the entity and all its parents are active. */
  get isActiveInHierarchy(): boolean {
    return this.node.isEnabled(true);
  }

  /** Shows or hides the entity and everything under it. */
  setActive(active: boolean): void {
    this.activeSelf = active;
    this.node.setEnabled(active);
  }

  /** The first enabled component of a type, or undefined. */
  getComponent(type: string): ComponentData | undefined {
    return this.components.find((component) => component.type === type && component.enabled);
  }

  getComponents(type: string): ComponentData[] {
    return this.components.filter((component) => component.type === type && component.enabled);
  }

  /** World position of the entity (a copy). */
  getWorldPosition(): Vector3 {
    this.node.computeWorldMatrix(true);
    return this.node.getAbsolutePosition().clone();
  }

  /** World rotation as a quaternion (a copy). */
  getWorldRotation(): Quaternion {
    this.node.computeWorldMatrix(true);
    const rotation = new Quaternion();
    this.node.getWorldMatrix().decompose(new Vector3(), rotation, new Vector3());
    return rotation;
  }

  /** World yaw (rotation around Y) in degrees, the way the desktop engine's `Quaternion.getYaw()` reports it. */
  getWorldYawDegrees(): number {
    const q = this.getWorldRotation();
    return (Math.atan2(2 * (q.y * q.w + q.x * q.z), 1 - 2 * (q.y * q.y + q.x * q.x)) * 180) / Math.PI;
  }

  /** World scale (a copy). */
  getWorldScale(): Vector3 {
    this.node.computeWorldMatrix(true);
    const scale = new Vector3();
    this.node.getWorldMatrix().decompose(scale, new Quaternion(), new Vector3());
    return scale;
  }

  setLocalScale(x: number, y: number, z: number): void {
    this.node.scaling.set(x, y, z);
  }

  /** Turns the entity by yaw, pitch and roll degrees on top of its current rotation, like `Transform.rotate`. */
  rotate(yawDeg: number, pitchDeg: number, rollDeg: number): void {
    const toRadians = Math.PI / 180;
    const delta = Quaternion.RotationYawPitchRoll(yawDeg * toRadians, pitchDeg * toRadians, rollDeg * toRadians);
    const current = this.node.rotationQuaternion ?? Quaternion.Identity();
    this.node.rotationQuaternion = current.multiply(delta);
  }

  /** Size of the entity's Mesh Primitive (before the entity's scale), or undefined without one. */
  getPrimitiveSize(): Vector3 | undefined {
    const primitive = this.getComponent("MeshPrimitiveComponent");
    if (!primitive) return undefined;
    const d = primitive.data;
    return new Vector3(d["sizeX"] as number, d["sizeY"] as number, d["sizeZ"] as number);
  }

  /** Changes the colour of the entity's Mesh Primitive, both in its data and on screen. */
  setPrimitiveColor(r: number, g: number, b: number, a = 1): void {
    const primitive = this.getComponent("MeshPrimitiveComponent");
    if (!primitive) throw new Error(`Entity '${this.name}' has no Mesh Primitive to colour`);
    Object.assign(primitive.data, { colorR: r, colorG: g, colorB: b, colorA: a });
    for (const mesh of this.meshes) {
      const material = mesh.material;
      if (material instanceof StandardMaterial) {
        material.diffuseColor.set(r, g, b);
        material.alpha = a;
      }
    }
  }

  getLocalPosition(): Vector3 {
    return this.node.position.clone();
  }

  setLocalPosition(x: number, y: number, z: number): void {
    this.node.position.set(x, y, z);
  }

  /**
   * Sets the rotation from yaw (around Y), pitch (around X) and roll (around Z) in degrees, applied roll first and
   * yaw last, the way the desktop engine's `setLocalEulerAngles` does.
   */
  setLocalEulerAngles(yawDeg: number, pitchDeg: number, rollDeg: number): void {
    const toRadians = Math.PI / 180;
    this.node.rotationQuaternion = Quaternion.RotationYawPitchRoll(yawDeg * toRadians, pitchDeg * toRadians, rollDeg * toRadians);
  }
}

/** A colour with channels from 0 to 1; alpha defaults to opaque. */
export interface ColorLike {
  r: number;
  g: number;
  b: number;
  a?: number;
}

/** Builds the pieces of entities that scripts create while the game runs; implemented by the scene builder. */
export interface EntityFactory {
  createEntity(name: string): GameEntity;
  addMeshPrimitive(entity: GameEntity, shape: string, size: Vector3, color: ColorLike): void;
  addShapeRenderer(entity: GameEntity, width: number, height: number, color: ColorLike): void;
}

/** Every entity of a playing scene, in the order the scene file lists them (entities made by scripts come last). */
export class GameScene {
  readonly entities: GameEntity[];
  private readonly byId = new Map<string, GameEntity>();
  private factory: EntityFactory | null = null;

  constructor(entities: GameEntity[]) {
    this.entities = entities;
    for (const entity of entities) this.byId.set(entity.id, entity);
  }

  /** Called by the scene builder once the Babylon scene exists; enables {@link createEntity} and friends. */
  attachFactory(factory: EntityFactory): void {
    this.factory = factory;
  }

  private requireFactory(): EntityFactory {
    if (!this.factory) throw new Error("This scene cannot create entities at run time");
    return this.factory;
  }

  /** Creates an empty, active entity at the origin and adds it to the scene. */
  createEntity(name: string): GameEntity {
    const entity = this.requireFactory().createEntity(name);
    this.entities.push(entity);
    this.byId.set(entity.id, entity);
    return entity;
  }

  /** Gives an entity a Mesh Primitive; {@code shape} is BOX, SPHERE or PLANE. */
  addMeshPrimitive(entity: GameEntity, shape: string, size: Vector3, color: ColorLike): void {
    this.requireFactory().addMeshPrimitive(entity, shape, size, color);
  }

  /** Gives an entity a filled rectangle (Shape Renderer), centred on its position. */
  addShapeRenderer(entity: GameEntity, width: number, height: number, color: ColorLike): void {
    this.requireFactory().addShapeRenderer(entity, width, height, color);
  }

  getById(id: string): GameEntity | undefined {
    return this.byId.get(id);
  }

  /** The first entity with this exact name, or undefined. */
  findByName(name: string): GameEntity | undefined {
    return this.entities.find((entity) => entity.name === name);
  }

  /** Every entity whose name starts with {@code prefix}, in scene order. */
  findByPrefix(prefix: string): GameEntity[] {
    return this.entities.filter((entity) => entity.name.startsWith(prefix));
  }
}
