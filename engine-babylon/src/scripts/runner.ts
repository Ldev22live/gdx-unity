import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { JavaRandom, requireEntity, requireText, type TextHandle } from "./util";

/** Port of `samples.runner.RunnerPlayer`: A/D switch between three lanes, Space jumps. */
export class RunnerPlayer extends ScriptComponent {
  /** X position of the left, middle and right lane. */
  static readonly LANES = [-130, 0, 130];
  static readonly LANE_CHANGE_SPEED = 1100;
  static readonly JUMP_SPEED = 760;
  static readonly GRAVITY = 2400;

  private groundY = 0;
  private z = 0;
  private lane = 1;
  private x = 0;
  private y = 0;
  private velocityY = 0;

  override onStart(): void {
    const start = this.entity.getLocalPosition();
    this.groundY = start.y;
    this.z = start.z;
    this.x = RunnerPlayer.LANES[this.lane]!;
    this.y = this.groundY;
  }

  override onUpdate(dt: number): void {
    const lanes = RunnerPlayer.LANES;
    if (this.input.isJustPressed("KeyR")) {
      this.lane = 1;
      this.x = lanes[this.lane]!;
      this.y = this.groundY;
      this.velocityY = 0;
    }
    if (this.input.isJustPressed("KeyA", "ArrowLeft") && this.lane > 0) this.lane--;
    if (this.input.isJustPressed("KeyD", "ArrowRight") && this.lane < lanes.length - 1) this.lane++;
    const onGround = this.y <= this.groundY;
    if (onGround && this.input.isJustPressed("Space", "KeyW", "ArrowUp")) {
      this.velocityY = RunnerPlayer.JUMP_SPEED;
      this.scene.playCue("jump");
    }
    const targetX = lanes[this.lane]!;
    const step = RunnerPlayer.LANE_CHANGE_SPEED * dt;
    this.x = Math.abs(targetX - this.x) <= step ? targetX : this.x + Math.sign(targetX - this.x) * step;
    this.velocityY -= RunnerPlayer.GRAVITY * dt;
    this.y += this.velocityY * dt;
    if (this.y < this.groundY) {
      this.y = this.groundY;
      this.velocityY = 0;
    }
    this.entity.setLocalPosition(this.x, this.y, this.z);
  }
}

/** Port of `samples.runner.RunnerGame`: blocks come down the lanes, faster and faster; touching one ends the run. */
export class RunnerGame extends ScriptComponent {
  static readonly BLOCK_POOL = 12;
  static readonly BLOCK_SIZE = 90;
  /** Where blocks appear (far ahead) and where they are recycled (behind the camera). */
  static readonly SPAWN_Z = -3600;
  static readonly RECYCLE_Z = 500;
  static readonly START_SPEED = 900;
  static readonly SPEED_GAIN = 30;
  static readonly MAX_SPEED = 2400;
  static readonly BLOCK_SPACING = 520;
  static readonly MARKER_SPACING = 400;
  /** Half the player's size: the player is a 50-unit cube. */
  static readonly PLAYER_HALF_SIZE = 25;
  static readonly UNITS_PER_METRE = 100;
  /** Fixed seed: the same course every run. */
  static readonly RANDOM_SEED = 7;

  private blocks: GameEntity[] = [];
  private markers: GameEntity[] = [];
  private random = new JavaRandom(RunnerGame.RANDOM_SEED);
  private player!: GameEntity;
  private distanceText!: TextHandle;
  private messageText!: TextHandle;
  private speed = 0;
  private distance = 0;
  private nextBlockIn = 0;
  private gameOver = false;

  override onStart(): void {
    this.player = requireEntity(this.scene, "Player", "RunnerGame");
    this.distanceText = requireText(this.scene, "Distance Text", "RunnerGame");
    this.messageText = requireText(this.scene, "Message Text", "RunnerGame");
    this.markers = this.scene.findByPrefix("Marker");
    const size = RunnerGame.BLOCK_SIZE;
    for (let i = 0; i < RunnerGame.BLOCK_POOL; i++) {
      const block = this.scene.createEntity(`Block ${i}`);
      this.scene.addMeshPrimitive(block, "BOX", new Vector3(size, size, size), { r: 0.9, g: 0.25, b: 0.25 });
      block.setActive(false);
      this.blocks.push(block);
    }
    this.restart();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    if (this.gameOver) return;
    this.speed = Math.min(RunnerGame.MAX_SPEED, this.speed + RunnerGame.SPEED_GAIN * dt);
    const travel = this.speed * dt;
    this.distance += travel;
    this.distanceText.setText(`Distance: ${this.metres()} m`);
    this.nextBlockIn -= travel;
    if (this.nextBlockIn <= 0) {
      this.spawnBlock();
      this.nextBlockIn = RunnerGame.BLOCK_SPACING * (0.6 + this.random.nextFloat());
    }
    const playerPosition = this.player.getWorldPosition();
    for (const block of this.blocks) {
      if (!block.isActiveSelf) continue;
      const p = block.getLocalPosition();
      p.z += travel;
      block.setLocalPosition(p.x, p.y, p.z);
      if (p.z > RunnerGame.RECYCLE_Z) {
        block.setActive(false);
      } else if (this.touches(playerPosition, p)) {
        this.gameOver = true;
        this.scene.playCue("crash");
        this.messageText.setText(`Crashed after ${this.metres()} m. Press R to run again`);
      }
    }
    const wrap = RunnerGame.MARKER_SPACING * this.markers.length;
    for (const marker of this.markers) {
      const p = marker.getLocalPosition();
      p.z += travel;
      if (p.z > RunnerGame.RECYCLE_Z) p.z -= wrap;
      marker.setLocalPosition(p.x, p.y, p.z);
    }
  }

  private metres(): number {
    return Math.trunc(this.distance / RunnerGame.UNITS_PER_METRE);
  }

  private touches(player: Vector3, block: Vector3): boolean {
    const reach = RunnerGame.PLAYER_HALF_SIZE + RunnerGame.BLOCK_SIZE / 2;
    return (
      Math.abs(player.x - block.x) < reach &&
      Math.abs(player.z - block.z) < reach &&
      player.y - RunnerGame.PLAYER_HALF_SIZE < block.y + RunnerGame.BLOCK_SIZE / 2
    );
  }

  private spawnBlock(): void {
    for (const block of this.blocks) {
      if (!block.isActiveSelf) {
        const laneX = RunnerPlayer.LANES[this.random.nextInt(RunnerPlayer.LANES.length)]!;
        block.setLocalPosition(laneX, RunnerGame.BLOCK_SIZE / 2, RunnerGame.SPAWN_Z);
        block.setActive(true);
        return;
      }
    }
  }

  private restart(): void {
    for (const block of this.blocks) block.setActive(false);
    this.random = new JavaRandom(RunnerGame.RANDOM_SEED);
    this.speed = RunnerGame.START_SPEED;
    this.distance = 0;
    this.nextBlockIn = RunnerGame.BLOCK_SPACING;
    this.gameOver = false;
    this.messageText.setText("");
    this.distanceText.setText("Distance: 0 m");
  }
}
