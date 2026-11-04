import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { clamp, keyAxis, requireEntity, requireText, shapeSize, type TextHandle } from "./util";

/** Boxes that only touch do not count as overlapping (protects against rounding while standing on a platform). */
const SKIN = 0.01;

/** One solid or collectable box: centre and half size. */
class Box {
  constructor(
    readonly entity: GameEntity,
    readonly x: number,
    readonly y: number,
    readonly halfWidth: number,
    readonly halfHeight: number,
  ) {}

  overlaps(px: number, py: number, phw: number, phh: number): boolean {
    return Math.abs(px - this.x) < phw + this.halfWidth - SKIN && Math.abs(py - this.y) < phh + this.halfHeight - SKIN;
  }
}

/** Box of an entity's Shape Renderer at its world position, including the entity's scale. */
function boxOf(entity: GameEntity): Box {
  const size = shapeSize(entity);
  const position = entity.getWorldPosition();
  const scale = entity.getWorldScale();
  return new Box(entity, position.x, position.y, (size.width * scale.x) / 2, (size.height * scale.y) / 2);
}

/** Port of `samples.platformer.FollowCamera2D`: keeps the camera on the "Player", horizontally only. */
export class FollowCamera2D extends ScriptComponent {
  static readonly FOLLOW_RATE = 6;
  /** Leftmost camera centre: half of the 640-unit design width, so the view starts at x = 0. */
  static readonly MIN_X = 320;
  /** Rightmost camera centre, so the view ends at the level's right edge. */
  static readonly MAX_X = 1900;
  private target!: GameEntity;

  override onStart(): void {
    this.target = requireEntity(this.scene, "Player", "FollowCamera2D");
  }

  override onUpdate(dt: number): void {
    const p = this.entity.getLocalPosition();
    const targetX = clamp(this.target.getWorldPosition().x, FollowCamera2D.MIN_X, FollowCamera2D.MAX_X);
    const blend = Math.min(1, FollowCamera2D.FOLLOW_RATE * dt);
    this.entity.setLocalPosition(p.x + (targetX - p.x) * blend, p.y, p.z);
  }
}

/** Port of `samples.platformer.PlatformerPlayer`: run, jump, land on platforms, collect coins, reach the goal. */
export class PlatformerPlayer extends ScriptComponent {
  static readonly RUN_SPEED = 260;
  static readonly JUMP_SPEED = 700;
  static readonly GRAVITY = 1700;
  static readonly MAX_FALL_SPEED = 900;
  /** Below this height the player has fallen off the level and starts again. */
  static readonly FALL_LIMIT = -300;

  private platforms: Box[] = [];
  private coins: Box[] = [];
  private goal: Box | null = null;
  private scoreText!: TextHandle;
  private messageText!: TextHandle;
  private startX = 0;
  private startY = 0;
  private halfWidth = 0;
  private halfHeight = 0;
  private x = 0;
  private y = 0;
  private velocityY = 0;
  private grounded = false;
  private coinsCollected = 0;
  private finished = false;

  override onStart(): void {
    for (const entity of this.scene.entities) {
      if (entity.name.startsWith("Platform")) this.platforms.push(boxOf(entity));
      else if (entity.name.startsWith("Coin")) this.coins.push(boxOf(entity));
      else if (entity.name === "Goal") this.goal = boxOf(entity);
    }
    this.scoreText = requireText(this.scene, "Score Text", "PlatformerPlayer");
    this.messageText = requireText(this.scene, "Message Text", "PlatformerPlayer");
    const self = boxOf(this.entity);
    this.halfWidth = self.halfWidth;
    this.halfHeight = self.halfHeight;
    this.startX = self.x;
    this.startY = self.y;
    this.restart();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    if (this.finished) return;
    const direction = keyAxis((...k) => this.input.isDown(...k), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    const jump = this.input.isJustPressed("Space", "KeyW", "ArrowUp");
    if (jump && this.grounded) this.velocityY = PlatformerPlayer.JUMP_SPEED;
    this.velocityY = Math.max(this.velocityY - PlatformerPlayer.GRAVITY * dt, -PlatformerPlayer.MAX_FALL_SPEED);
    this.moveHorizontally(direction * PlatformerPlayer.RUN_SPEED * dt);
    this.moveVertically(this.velocityY * dt);
    this.entity.setLocalPosition(this.x, this.y, 0);
    this.collectCoins();
    if (this.goal && this.goal.overlaps(this.x, this.y, this.halfWidth, this.halfHeight)) {
      this.finished = true;
      this.messageText.setText(`Level complete! ${this.coinsCollected}/${this.coins.length} coins. Press R to play again`);
    }
    if (this.y < PlatformerPlayer.FALL_LIMIT) {
      this.x = this.startX;
      this.y = this.startY;
      this.velocityY = 0;
    }
  }

  /** Moves sideways and stops at the side of any platform hit. */
  private moveHorizontally(dx: number): void {
    this.x += dx;
    for (const platform of this.platforms) {
      if (platform.overlaps(this.x, this.y, this.halfWidth, this.halfHeight)) {
        this.x = dx > 0 ? platform.x - platform.halfWidth - this.halfWidth : platform.x + platform.halfWidth + this.halfWidth;
      }
    }
  }

  /** Moves up or down; landing makes the player grounded, hitting a platform from below stops the jump. */
  private moveVertically(dy: number): void {
    this.y += dy;
    this.grounded = false;
    for (const platform of this.platforms) {
      if (platform.overlaps(this.x, this.y, this.halfWidth, this.halfHeight)) {
        if (dy <= 0) {
          this.y = platform.y + platform.halfHeight + this.halfHeight;
          this.grounded = true;
        } else {
          this.y = platform.y - platform.halfHeight - this.halfHeight;
        }
        this.velocityY = 0;
      }
    }
  }

  private collectCoins(): void {
    for (const coin of this.coins) {
      if (coin.entity.isActiveSelf && coin.overlaps(this.x, this.y, this.halfWidth, this.halfHeight)) {
        coin.entity.setActive(false);
        this.coinsCollected++;
        this.updateScore();
      }
    }
  }

  private restart(): void {
    this.x = this.startX;
    this.y = this.startY;
    this.velocityY = 0;
    this.coinsCollected = 0;
    this.finished = false;
    for (const coin of this.coins) coin.entity.setActive(true);
    this.entity.setLocalPosition(this.x, this.y, 0);
    this.messageText.setText("");
    this.updateScore();
  }

  private updateScore(): void {
    this.scoreText.setText(`Coins: ${this.coinsCollected} / ${this.coins.length}`);
  }
}
