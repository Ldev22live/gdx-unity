import type { CameraRig } from "./CameraRig";
import type { GameEntity, GameScene } from "./GameEntity";
import type { Input } from "./Input";
import type { Physics3D, RaycastHit } from "./physics3d/Physics3D";
import type { MotorMode } from "./physics3d/types";
import type { Vector3 } from "@babylonjs/core";

/**
 * Base class of game scripts, the web counterpart of the desktop engine's `ScriptComponent`. A script is bound to an
 * entity in the scene file by `scriptClassName` and found in the {@link ScriptRegistry} under that name.
 */
export abstract class ScriptComponent {
  entity!: GameEntity;
  scene!: GameScene;
  input!: Input;
  /** The scene's 3D physics while it plays, or null when the scene has no 3D collider (or physics is still loading). */
  physics3d: Physics3D | null = null;

  /** Called once, before the first update. */
  onStart(): void {}

  /** Called every frame while playing; {@code deltaTime} is in seconds. */
  onUpdate(_deltaTime: number): void {}

  /** 2D physics contacts of this entity: two solid colliders started or stopped touching. */
  onCollisionEnter2D(_other: GameEntity): void {}

  onCollisionExit2D(_other: GameEntity): void {}

  /** An entity entered or left one of this entity's sensor colliders (or this entity one of theirs). */
  onTriggerEnter2D(_other: GameEntity): void {}

  onTriggerExit2D(_other: GameEntity): void {}

  /** 3D physics contacts of this entity (TD-048): two solid bodies started or stopped touching. */
  onCollisionEnter3D(_other: GameEntity): void {}

  onCollisionExit3D(_other: GameEntity): void {}

  /** An entity entered or left one of this entity's trigger colliders (or this entity one of theirs). */
  onTriggerEnter3D(_other: GameEntity): void {}

  onTriggerExit3D(_other: GameEntity): void {}

  /** The scene's 3D physics, or null. */
  getPhysics3D(): Physics3D | null {
    return this.physics3d;
  }

  /** Like {@link getPhysics3D}, but fails with a message that says what to add when the scene has no 3D physics. */
  requirePhysics3D(): Physics3D {
    if (!this.physics3d) {
      throw new Error("This scene runs no 3D physics. Add a 3D collider (Add Component > Physics 3D) to an entity that is active when play starts.");
    }
    return this.physics3d;
  }

  /**
   * Changes the motor of this entity's hinge or slider joint (TD-051, TD-064): {@code target} is degrees per second or degrees for a
   * hinge, scene units per second or scene units for a slider.
   */
  setJointMotor3D(mode: MotorMode, target: number): void {
    this.requirePhysics3D().setJointMotor(this.entity, mode, target);
  }

  /** How far this entity's hinge has turned in degrees, or its slider has slid in scene units, since the joint was made. */
  getJointPosition3D(): number {
    return this.requirePhysics3D().getJointPosition(this.entity);
  }

  /** Sets the horizontal speed this entity's character walks at, in scene units per second (TD-049, TD-064); it keeps walking until changed. */
  moveCharacter3D(velocity: Vector3): void {
    this.requirePhysics3D().moveCharacter(this.entity, velocity);
  }

  /** Makes this entity's character jump at {@code speed} scene units per second, only while it stands on ground; returns whether it jumped. */
  jumpCharacter3D(speed: number): boolean {
    return this.requirePhysics3D().jumpCharacter(this.entity, speed);
  }

  /** Whether this entity's character stands on ground it can walk on. */
  isGrounded3D(): boolean {
    return this.requirePhysics3D().isCharacterGrounded(this.entity);
  }

  /** How fast this entity's character really moved in its last physics step, in scene units per second. */
  getCharacterVelocity3D(): Vector3 {
    return this.requirePhysics3D().getCharacterVelocity(this.entity);
  }

  /** Puts this entity's character somewhere at once, without sweeping (a respawn, a portal). */
  teleportCharacter3D(position: Vector3): void {
    this.requirePhysics3D().teleportCharacter(this.entity, position);
  }

  /** The camera rig on {@code entity} (this entity by default) while the game plays, or null when it has none (or its target was not found). */
  getCameraRig(entity: GameEntity = this.entity): CameraRig | null {
    return this.scene.rigs.get(entity.id) ?? null;
  }

  /** Casts a ray against every layer and returns the closest hit, or null. {@code direction} need not be normalized. */
  raycast3D(origin: Vector3, direction: Vector3, maxDistance: number): RaycastHit | null {
    return this.requirePhysics3D().raycast(origin, direction, maxDistance);
  }
}

export type ScriptFactory = () => ScriptComponent;

/** Scripts the web player can run, by the fully qualified class name the scene file uses. */
export class ScriptRegistry {
  private readonly factories = new Map<string, ScriptFactory>();

  register(className: string, factory: ScriptFactory): void {
    if (this.factories.has(className)) {
      throw new Error(`A script is already registered as ${className}`);
    }
    this.factories.set(className, factory);
  }

  has(className: string): boolean {
    return this.factories.has(className);
  }

  create(className: string): ScriptComponent {
    const factory = this.factories.get(className);
    if (!factory) throw new Error(`No web script registered as ${className}`);
    return factory();
  }
}
