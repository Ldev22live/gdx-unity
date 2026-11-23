import type { GameEntity, GameScene } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { requireEntity } from "./util";

/** Boxes that only touch do not count as overlapping (protects against rounding while standing). */
export const SIDE_SKIN = 0.01;
/** Fastest fall speed, so bodies never tunnel through thin platforms. */
export const MAX_FALL_SPEED = 1500;

/** A solid box in the side view, sized by its entity's Mesh Primitive. It may move; inactive entities are not solid. */
export class Solid {
  readonly halfWidth: number;
  readonly halfHeight: number;
  /** Centre and the movement since the previous {@link refresh}. */
  x: number;
  y: number;
  dx = 0;
  dy = 0;

  constructor(readonly entity: GameEntity) {
    const size = entity.getPrimitiveSize();
    if (!size) throw new Error(`'${entity.name}' needs a Mesh Primitive to be solid`);
    const scale = entity.getWorldScale();
    this.halfWidth = (size.x * scale.x) / 2;
    this.halfHeight = (size.y * scale.y) / 2;
    const position = entity.getWorldPosition();
    this.x = position.x;
    this.y = position.y;
  }

  /** Reads the entity's current position; call once per frame after moving the entity. */
  refresh(): void {
    const position = this.entity.getWorldPosition();
    this.dx = position.x - this.x;
    this.dy = position.y - this.y;
    this.x = position.x;
    this.y = position.y;
  }

  isSolid(): boolean {
    return this.entity.isActiveInHierarchy;
  }

  overlaps(px: number, py: number, phw: number, phh: number): boolean {
    return Math.abs(px - this.x) < phw + this.halfWidth - SIDE_SKIN && Math.abs(py - this.y) < phh + this.halfHeight - SIDE_SKIN;
  }
}

/** Decides what happens when a body walks into a solid; return true if the solid was moved out of the way. */
export type Pusher = (solid: Solid, amount: number) => boolean;

/** Solids of all entities whose name starts with one of the prefixes, in scene order. */
export function solidsNamed(scene: GameScene, ...prefixes: string[]): Solid[] {
  return scene.entities.filter((entity) => prefixes.some((prefix) => entity.name.startsWith(prefix))).map((entity) => new Solid(entity));
}

/**
 * Port of `samples.common.SideBody`: movement for 2.5D games. A box that moves in the XY plane (the side view), falls
 * under gravity and collides with solid boxes. The level is 3D, but gameplay happens at depth z = 0.
 */
export class SideBody {
  velocityX = 0;
  velocityY = 0;
  grounded = false;
  /** The solid stood on, or null; moving platforms carry the body along. */
  ground: Solid | null = null;
  /** This body's own solid when others can stand on it (crates); skipped in its own collisions. */
  self: Solid | null = null;

  constructor(
    public x: number,
    public y: number,
    readonly halfWidth: number,
    readonly halfHeight: number,
  ) {}

  /** One frame: ride the platform stood on, apply gravity, then move and collide horizontally and vertically. */
  step(dt: number, gravity: number, solids: Solid[], pusher: Pusher | null): void {
    if (this.grounded && this.ground !== null && this.ground.isSolid()) {
      this.x += this.ground.dx;
      this.y += this.ground.dy;
    }
    this.velocityY = Math.max(this.velocityY - gravity * dt, -MAX_FALL_SPEED);
    this.moveX(this.velocityX * dt, solids, pusher);
    this.moveY(this.velocityY * dt, solids);
  }

  /** Moves sideways; stops at solids unless {@code pusher} moves them away. */
  moveX(amount: number, solids: Solid[], pusher: Pusher | null): void {
    if (amount === 0) return;
    this.x += amount;
    for (const solid of solids) {
      if (solid === this.self || !solid.isSolid() || !solid.overlaps(this.x, this.y, this.halfWidth, this.halfHeight)) continue;
      if (pusher !== null && pusher(solid, amount) && !solid.overlaps(this.x, this.y, this.halfWidth, this.halfHeight)) continue;
      this.x = amount > 0 ? solid.x - solid.halfWidth - this.halfWidth : solid.x + solid.halfWidth + this.halfWidth;
    }
  }

  /** Moves up or down; landing sets {@link grounded} and {@link ground}, hitting a ceiling stops the jump. */
  moveY(amount: number, solids: Solid[]): void {
    this.y += amount;
    this.grounded = false;
    this.ground = null;
    for (const solid of solids) {
      if (solid === this.self || !solid.isSolid() || !solid.overlaps(this.x, this.y, this.halfWidth, this.halfHeight)) continue;
      if (amount <= 0) {
        this.y = solid.y + solid.halfHeight + this.halfHeight;
        this.grounded = true;
        this.ground = solid;
      } else {
        this.y = solid.y - solid.halfHeight - this.halfHeight;
      }
      this.velocityY = 0;
    }
  }

  /** True when this body's box overlaps the given box. */
  touches(boxX: number, boxY: number, boxHalfWidth: number, boxHalfHeight: number): boolean {
    return Math.abs(this.x - boxX) < this.halfWidth + boxHalfWidth && Math.abs(this.y - boxY) < this.halfHeight + boxHalfHeight;
  }

  /** Moves {@code entity} to this body's position at depth {@code z}. */
  moveEntity(entity: GameEntity, z: number): void {
    entity.setLocalPosition(this.x, this.y, z);
  }
}

/** Port of `samples.common.SideCamera25D`: looks at the level from the front, slightly from above, following the "Player". */
export class SideCamera25D extends ScriptComponent {
  /** Distance in front of the gameplay plane. */
  static readonly DISTANCE = 1000;
  /** Height above the player. */
  static readonly HEIGHT = 150;
  /** Downward tilt in degrees. */
  static readonly PITCH = -7;
  /** Lowest camera height, so the view never sinks far below the ground. */
  static readonly MIN_Y = 140;
  static readonly FOLLOW_RATE = 4;

  private target!: GameEntity;
  private x = 0;
  private y = 0;

  override onStart(): void {
    this.target = requireEntity(this.scene, "Player", "SideCamera25D");
    const position = this.target.getWorldPosition();
    this.x = position.x;
    this.y = Math.max(SideCamera25D.MIN_Y, position.y + SideCamera25D.HEIGHT);
    this.entity.setLocalEulerAngles(0, SideCamera25D.PITCH, 0);
    this.entity.setLocalPosition(this.x, this.y, SideCamera25D.DISTANCE);
  }

  override onUpdate(dt: number): void {
    const position = this.target.getWorldPosition();
    const blend = Math.min(1, SideCamera25D.FOLLOW_RATE * dt);
    this.x += (position.x - this.x) * blend;
    this.y += (Math.max(SideCamera25D.MIN_Y, position.y + SideCamera25D.HEIGHT) - this.y) * blend;
    this.entity.setLocalPosition(this.x, this.y, SideCamera25D.DISTANCE);
  }
}
