import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { parseTilemap, tilemapSolidAt, type TileMapData } from "../sprites/sprite";
import { clamp, keyAxis, requireEntity, requireText, type TextHandle } from "./util";

/**
 * Port of `samples.pixel.PixelHero`: a pixel-art platformer hero. A/D or the arrows run, Space/W/Up jump, R restarts. The level is the tile map of the entity
 * "Level", the hero plays its "idle" and "walk" sprite tags and turns with the direction, coins are collected on touch, the camera follows sideways.
 * Movement is written here, not done by the physics engine: the body is a box tested against the tile map's solid cells a few units at a time.
 */
export class PixelHero extends ScriptComponent {
  static readonly RUN_SPEED = 220;
  static readonly JUMP_SPEED = 640;
  static readonly GRAVITY = 1600;
  static readonly MAX_FALL_SPEED = 900;
  /** Below this height the hero has fallen out of the level and starts again. */
  static readonly FALL_LIMIT = -200;
  /** Half the width and height of the hero's body, in scene units. */
  static readonly HALF_WIDTH = 12;
  static readonly HALF_HEIGHT = 28;
  /** The longest distance the body moves before the level is tested again. */
  static readonly STEP = 1;
  /** A coin is picked up when the hero is this close to it. */
  static readonly COIN_REACH = 30;
  static readonly CAMERA_RATE = 6;
  /** Space between sample points when testing the body against the level; below the tile size, so no cell is missed. */
  static readonly PROBE_SPACING = 8;
  /** Pulls the body's far edges in so a body exactly as wide as the cells it fills does not touch the next ones. */
  static readonly EDGE_INSET = 0.01;

  private map!: TileMapData;
  private origin = { x: 0, y: 0 };
  private scale = { x: 1, y: 1 };
  private scoreText: TextHandle | null = null;
  private camera: GameEntity | null = null;
  private coins: GameEntity[] = [];
  private cameraMinX = 0;
  private cameraMaxX = 0;
  private startX = 0;
  private startY = 0;
  private x = 0;
  private y = 0;
  private velocityY = 0;
  private grounded = false;
  private collected = 0;
  private total = 0;

  override onStart(): void {
    const level = requireEntity(this.scene, "Level", "PixelHero");
    const tilemap = level.getComponent("TilemapComponent");
    if (!tilemap) throw new Error("PixelHero needs an entity named 'Level' with a Tilemap component");
    if (!this.entity.sprite) throw new Error("PixelHero needs a Sprite Animator on the hero");
    this.map = parseTilemap(tilemap.data);
    const origin = level.getWorldPosition();
    const scale = level.getWorldScale();
    this.origin = { x: origin.x, y: origin.y };
    this.scale = { x: scale.x, y: scale.y };
    this.coins = this.scene.entities.filter((e) => e.name.startsWith("Coin"));
    this.total = this.coins.length;
    this.scoreText = this.scene.entities.some((e) => e.name === "Score Text") ? requireText(this.scene, "Score Text", "PixelHero") : null;
    this.camera = this.scene.entities.find((e) => e.name === "Main Camera") ?? null;
    const width = this.map.cols * this.map.tileWidth * Math.abs(scale.x);
    const halfView = ((this.map.rows * this.map.tileHeight) * 16) / 9 / 2;
    this.cameraMinX = origin.x + halfView;
    this.cameraMaxX = Math.max(this.cameraMinX, origin.x + width - halfView);
    const position = this.entity.getWorldPosition();
    this.startX = position.x;
    this.startY = position.y;
    this.restart();
  }

  private restart(): void {
    this.x = this.startX;
    this.y = this.startY;
    this.velocityY = 0;
    this.collected = 0;
    for (const coin of this.coins) coin.setActive(true);
    this.entity.sprite!.play("idle", true);
    this.entity.setLocalPosition(this.x, this.y, this.entity.getLocalPosition().z);
    this.showScore();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    const direction = keyAxis((...k) => this.input.isDown(...k), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    const jump = this.input.isJustPressed("Space", "KeyW", "ArrowUp");
    if (jump && this.grounded) {
      this.velocityY = PixelHero.JUMP_SPEED;
      this.scene.playCue("jump");
    }
    this.velocityY = Math.max(this.velocityY - PixelHero.GRAVITY * dt, -PixelHero.MAX_FALL_SPEED);
    this.moveHorizontally(direction * PixelHero.RUN_SPEED * dt);
    this.moveVertically(this.velocityY * dt);
    this.entity.setLocalPosition(this.x, this.y, this.entity.getLocalPosition().z);

    const sprite = this.entity.sprite!;
    if (direction !== 0) sprite.setFlipX(direction < 0);
    sprite.play(direction !== 0 ? "walk" : "idle");
    this.collectCoins();
    this.followCamera(dt);
    if (this.y < PixelHero.FALL_LIMIT) {
      this.scene.playCue("hurt");
      this.x = this.startX;
      this.y = this.startY;
      this.velocityY = 0;
      this.entity.setLocalPosition(this.x, this.y, this.entity.getLocalPosition().z);
    }
  }

  /** Whether a body centred at (px, py) overlaps a solid cell: sample points along its edges and through it. */
  private blocked(px: number, py: number): boolean {
    const left = px - PixelHero.HALF_WIDTH;
    const right = px + PixelHero.HALF_WIDTH - PixelHero.EDGE_INSET;
    const bottom = py - PixelHero.HALF_HEIGHT;
    const top = py + PixelHero.HALF_HEIGHT - PixelHero.EDGE_INSET;
    for (let sx = left; ; sx += PixelHero.PROBE_SPACING) {
      const cx = Math.min(sx, right);
      for (let sy = bottom; ; sy += PixelHero.PROBE_SPACING) {
        const cy = Math.min(sy, top);
        if (tilemapSolidAt(this.map, this.origin, this.scale, cx, cy)) return true;
        if (cy >= top) break;
      }
      if (cx >= right) return false;
    }
  }

  private moveHorizontally(dx: number): void {
    let remaining = dx;
    while (Math.abs(remaining) > 0) {
      const step = Math.sign(remaining) * Math.min(Math.abs(remaining), PixelHero.STEP);
      if (this.blocked(this.x + step, this.y)) return;
      this.x += step;
      remaining -= step;
    }
  }

  private moveVertically(dy: number): void {
    this.grounded = false;
    let remaining = dy;
    while (Math.abs(remaining) > 0) {
      const step = Math.sign(remaining) * Math.min(Math.abs(remaining), PixelHero.STEP);
      if (this.blocked(this.x, this.y + step)) {
        this.grounded = step < 0;
        this.velocityY = 0;
        return;
      }
      this.y += step;
      remaining -= step;
    }
    // Standing still on the floor: the body is resting on it when a small step down would hit
    if (dy <= 0 && this.blocked(this.x, this.y - 1)) this.grounded = true;
  }

  private collectCoins(): void {
    for (const coin of this.coins) {
      if (!coin.isActiveSelf) continue;
      const p = coin.getWorldPosition();
      if (Math.abs(p.x - this.x) < PixelHero.COIN_REACH && Math.abs(p.y - this.y) < PixelHero.COIN_REACH) {
        coin.setActive(false);
        this.collected++;
        this.scene.playCue("coin");
        this.showScore();
      }
    }
  }

  private showScore(): void {
    this.scoreText?.setText(`Coins: ${this.collected} / ${this.total}${this.collected === this.total && this.total > 0 ? "   All found! R restarts" : ""}`);
  }

  private followCamera(dt: number): void {
    if (!this.camera) return;
    const p = this.camera.getLocalPosition();
    const target = clamp(this.x, this.cameraMinX, this.cameraMaxX);
    const blend = Math.min(1, PixelHero.CAMERA_RATE * dt);
    this.camera.setLocalPosition(p.x + (target - p.x) * blend, p.y, p.z);
  }
}
