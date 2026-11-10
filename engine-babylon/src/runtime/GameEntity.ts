import { type Mesh, Quaternion, StandardMaterial, TransformNode, Vector3 } from "@babylonjs/core";
import { hear } from "../audio/spatial";
import type { CameraRig } from "./CameraRig";
import type { SpriteBinding } from "./SpriteBinding";
import { bool, num, str, type ComponentData, type EntityData } from "../scene/types";

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
  /** The entity's Sprite Animator, or null; scripts call {@code entity.sprite?.play("walk")} (TD-066). */
  sprite: SpriteBinding | null = null;
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

/** Where an Audio Source sends its sound when played; the scene player binds one while the scene plays (TD-043). */
export interface SoundSink {
  /**
   * Starts one sound effect: a factory sound name or a clip's path, linear volume 0..1, a speed multiplier and a pan from -1 (left)
   * to 1 (right; 0 or left out is the middle). Must not block.
   */
  play(sound: string, volume: number, pitch: number, pan?: number): void;
}

/** Every entity of a playing scene, in the order the scene file lists them (entities made by scripts come last). */
export class GameScene {
  /** The camera rigs of a playing scene by entity id; scripts turn them with {@link CameraRig.rotate}. Filled by the scene player. */
  readonly rigs = new Map<string, CameraRig>();
  readonly entities: GameEntity[];
  private readonly byId = new Map<string, GameEntity>();
  private factory: EntityFactory | null = null;
  private sink: SoundSink | null = null;

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

  /** Connects (or, with null, disconnects) the sound output; without one {@link playCue} makes no sound. */
  bindSound(sink: SoundSink | null): void {
    this.sink = sink;
  }

  /**
   * Plays every enabled Audio Source whose cue is {@code cue}, on active entities: the web counterpart of the desktop
   * `AudioSourceComponent.play(scene, cue)`.
   *
   * @returns how many sources played; 0 means no source has that cue, or the scene is not playing
   */
  playCue(cue: string): number {
    const sink = this.sink;
    if (!sink) return 0;
    let played = 0;
    for (const entity of this.entities) {
      if (!entity.isActiveInHierarchy) continue;
      for (const source of entity.getComponents("AudioSource")) {
        if (str(source.data, "cue", "sound") !== cue) continue;
        this.playSource(entity, source);
        played++;
      }
    }
    return played;
  }

  /**
   * Plays one Audio Source through the bound sink. A spatial source is heard from the scene's Audio Listener: quieter with distance
   * and moved between the speakers (nothing plays beyond its far distance); with no listener it plays in the middle at its own volume.
   */
  playSource(entity: GameEntity, source: ComponentData): void {
    const sink = this.sink;
    if (!sink) return;
    const sound = str(source.data, "sound", "Coin");
    const volume = num(source.data, "volume", 1);
    const pitch = num(source.data, "pitch", 1);
    const listener = bool(source.data, "spatial", false) ? this.findListener() : null;
    if (!listener) {
      sink.play(sound, volume, pitch);
      return;
    }
    const right = Vector3.TransformNormal(Vector3.Right(), listener.node.getWorldMatrix());
    const heard = hear(
      listener.getWorldPosition(),
      right,
      entity.getWorldPosition(),
      num(source.data, "minDistance", 100),
      num(source.data, "maxDistance", 2000),
    );
    if (heard.gain > 0) sink.play(sound, volume * heard.gain, pitch, heard.pan);
  }

  /** The entity the scene is heard from: the first active entity with an enabled Audio Listener, or null. */
  private findListener(): GameEntity | null {
    return this.entities.find((entity) => entity.isActiveInHierarchy && entity.getComponents("AudioListener").length > 0) ?? null;
  }

  getById(id: string): GameEntity | undefined {
    return this.byId.get(id);
  }

  /** Whether {@code entity} is still in the scene (it is not after {@link removeEntity}). */
  contains(entity: GameEntity): boolean {
    return this.byId.get(entity.id) === entity;
  }

  /** Removes {@code entity} and its children from the scene and disposes their nodes; the web counterpart of `Scene.removeEntity`. */
  removeEntity(entity: GameEntity): void {
    if (!this.contains(entity)) return;
    const doomed: GameEntity[] = [];
    const collect = (item: GameEntity): void => {
      doomed.push(item);
      item.children.forEach(collect);
    };
    collect(entity);
    for (const item of doomed) {
      const index = this.entities.indexOf(item);
      if (index >= 0) this.entities.splice(index, 1);
      this.byId.delete(item.id);
    }
    const siblings = entity.parent?.children;
    const at = siblings ? siblings.indexOf(entity) : -1;
    if (siblings && at >= 0) siblings.splice(at, 1);
    entity.node.dispose();
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
