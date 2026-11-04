import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { requireText, type TextHandle } from "./util";

/** Boxes that only touch do not count as overlapping (protects against rounding while standing on a platform). */
const SKIN = 0.01;

/** A solid box: centre and half sizes. */
class Box {
  constructor(
    readonly entity: GameEntity,
    readonly centre: Vector3,
    readonly half: Vector3,
  ) {}

  overlaps(p: Vector3, h: Vector3): boolean {
    return (
      Math.abs(p.x - this.centre.x) < h.x + this.half.x - SKIN &&
      Math.abs(p.y - this.centre.y) < h.y + this.half.y - SKIN &&
      Math.abs(p.z - this.centre.z) < h.z + this.half.z - SKIN
    );
  }
}

type Axis = "x" | "y" | "z";

/** Box of an entity's Mesh Primitive at its world position, including the entity's scale. */
function boxOf(entity: GameEntity): Box {
  const size = entity.getPrimitiveSize();
  if (!size) throw new Error(`'${entity.name}' needs a Mesh Primitive to have a size`);
  return new Box(entity, entity.getWorldPosition(), size.multiply(entity.getWorldScale()).scale(0.5));
}

/** Port of `samples.hopper.HopperPlayer`: 3D platforming with axis-aligned box collision. */
export class HopperPlayer extends ScriptComponent {
  static readonly MOVE_SPEED = 330;
  static readonly JUMP_SPEED = 820;
  static readonly GRAVITY = 2100;
  static readonly MAX_FALL_SPEED = 1400;
  /** Below this height the player has fallen off. */
  static readonly FALL_LIMIT = -600;

  private solids: Box[] = [];
  private readonly start = Vector3.Zero();
  private readonly position = Vector3.Zero();
  private readonly half = Vector3.Zero();
  private readonly velocity = Vector3.Zero();
  private goal: Box | null = null;
  private fallsText!: TextHandle;
  private timeText!: TextHandle;
  private messageText!: TextHandle;
  private grounded = false;
  private finished = false;
  private falls = 0;
  private elapsed = 0;

  override onStart(): void {
    for (const entity of this.scene.entities) {
      if (entity.name.startsWith("Platform") || entity.name === "Goal") {
        const box = boxOf(entity);
        this.solids.push(box);
        if (entity.name === "Goal") this.goal = box;
      }
    }
    if (!this.goal) throw new Error("HopperPlayer needs a box named 'Goal'");
    const self = boxOf(this.entity);
    this.half.copyFrom(self.half);
    this.start.copyFrom(self.centre);
    this.fallsText = requireText(this.scene, "Falls Text", "HopperPlayer");
    this.timeText = requireText(this.scene, "Time Text", "HopperPlayer");
    this.messageText = requireText(this.scene, "Message Text", "HopperPlayer");
    this.restart();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    if (this.finished) return;
    this.elapsed += dt;
    this.timeText.setText(`Time: ${this.elapsed.toFixed(1)}`);
    let inputX = (this.input.isDown("KeyD", "ArrowRight") ? 1 : 0) - (this.input.isDown("KeyA", "ArrowLeft") ? 1 : 0);
    let inputZ = (this.input.isDown("KeyS", "ArrowDown") ? 1 : 0) - (this.input.isDown("KeyW", "ArrowUp") ? 1 : 0);
    const length = Math.hypot(inputX, inputZ);
    if (length > 0) {
      inputX /= length;
      inputZ /= length;
    }
    if (this.grounded && this.input.isJustPressed("Space")) this.velocity.y = HopperPlayer.JUMP_SPEED;
    this.velocity.y = Math.max(this.velocity.y - HopperPlayer.GRAVITY * dt, -HopperPlayer.MAX_FALL_SPEED);
    // One axis at a time, so the player slides along walls and lands cleanly
    this.moveAxis("x", inputX * HopperPlayer.MOVE_SPEED * dt);
    this.moveAxis("z", inputZ * HopperPlayer.MOVE_SPEED * dt);
    this.grounded = false;
    const landedOn = this.moveAxis("y", this.velocity.y * dt);
    this.entity.setLocalPosition(this.position.x, this.position.y, this.position.z);
    if (landedOn !== null && landedOn === this.goal) {
      this.finished = true;
      this.messageText.setText(`Goal! ${this.elapsed.toFixed(1)} s with ${this.falls} falls. Press R to play again`);
    }
    if (this.position.y < HopperPlayer.FALL_LIMIT) {
      this.falls++;
      this.fallsText.setText(`Falls: ${this.falls}`);
      this.position.copyFrom(this.start);
      this.velocity.setAll(0);
    }
  }

  /** Moves along one axis and pushes the player out of any solid it entered; returns the box landed on, if any. */
  private moveAxis(axis: Axis, amount: number): Box | null {
    if (amount === 0) return null;
    this.position[axis] += amount;
    let landedOn: Box | null = null;
    for (const box of this.solids) {
      if (!box.overlaps(this.position, this.half)) continue;
      const reach = box.half[axis] + this.half[axis];
      this.position[axis] = amount > 0 ? box.centre[axis] - reach : box.centre[axis] + reach;
      if (axis === "y") {
        if (amount < 0) {
          this.grounded = true;
          landedOn = box;
        }
        this.velocity.y = 0;
      }
    }
    return landedOn;
  }

  private restart(): void {
    this.position.copyFrom(this.start);
    this.velocity.setAll(0);
    this.falls = 0;
    this.elapsed = 0;
    this.finished = false;
    this.entity.setLocalPosition(this.position.x, this.position.y, this.position.z);
    this.fallsText.setText("Falls: 0");
    this.messageText.setText("");
  }
}
