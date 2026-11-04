import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";

/** Port of `samples.collector.CollectorPlayer`: a rolling ball moved with WASD or the arrow keys. */
export class CollectorPlayer extends ScriptComponent {
  /** Acceleration from the keys, in units per second squared. */
  static readonly ACCELERATION = 1500;
  /** Top speed in units per second. */
  static readonly MAX_SPEED = 460;
  /** Fraction of speed kept per second without input (rolling friction). */
  static readonly DRAG = 0.15;
  /** Half the arena's inner size minus the ball radius; the walls stand just outside. */
  static readonly ARENA_HALF_SIZE = 740;
  /** Share of speed kept when bouncing off a wall. */
  static readonly WALL_BOUNCE = 0.6;

  private start = Vector3.Zero();
  private readonly velocity = Vector3.Zero();
  private readonly position = Vector3.Zero();

  override onStart(): void {
    this.start = this.entity.getLocalPosition();
    this.position.copyFrom(this.start);
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.position.copyFrom(this.start);
      this.velocity.setAll(0);
    }
    let inputX = 0;
    let inputZ = 0;
    if (this.input.isDown("KeyA", "ArrowLeft")) inputX -= 1;
    if (this.input.isDown("KeyD", "ArrowRight")) inputX += 1;
    if (this.input.isDown("KeyW", "ArrowUp")) inputZ -= 1;
    if (this.input.isDown("KeyS", "ArrowDown")) inputZ += 1;

    const v = this.velocity;
    v.x += inputX * CollectorPlayer.ACCELERATION * dt;
    v.z += inputZ * CollectorPlayer.ACCELERATION * dt;
    v.scaleInPlace(Math.pow(CollectorPlayer.DRAG, dt));
    const speed = v.length();
    if (speed > CollectorPlayer.MAX_SPEED) v.scaleInPlace(CollectorPlayer.MAX_SPEED / speed);
    this.position.addInPlace(v.scale(dt));

    const half = CollectorPlayer.ARENA_HALF_SIZE;
    if (Math.abs(this.position.x) > half) {
      this.position.x = Math.sign(this.position.x) * half;
      v.x = -v.x * CollectorPlayer.WALL_BOUNCE;
    }
    if (Math.abs(this.position.z) > half) {
      this.position.z = Math.sign(this.position.z) * half;
      v.z = -v.z * CollectorPlayer.WALL_BOUNCE;
    }
    this.entity.setLocalPosition(this.position.x, this.position.y, this.position.z);
  }
}

/** Port of `samples.collector.CollectorGame`: gems spin and bob; touching one with the "Player" collects it. */
export class CollectorGame extends ScriptComponent {
  /** Distance between player and gem centres that counts as touching. */
  static readonly PICKUP_DISTANCE = 60;
  /** Gem spin in degrees per second. */
  static readonly SPIN_SPEED = 120;
  /** Gem bob height and speed (radians per second). */
  static readonly BOB_HEIGHT = 10;
  static readonly BOB_RATE = 3;

  private gems: GameEntity[] = [];
  private gemHomes: Vector3[] = [];
  private player!: GameEntity;
  private gemsText!: Record<string, unknown>;
  private timeText!: Record<string, unknown>;
  private messageText!: Record<string, unknown>;
  private elapsed = 0;
  private spin = 0;
  private collected = 0;
  private finished = false;

  override onStart(): void {
    this.player = this.requireEntity("Player");
    this.gems = this.scene.findByPrefix("Gem");
    this.gemHomes = this.gems.map((gem) => gem.getLocalPosition());
    this.gemsText = this.requireText("Collected Text");
    this.timeText = this.requireText("Time Text");
    this.messageText = this.requireText("Message Text");
    this.restart();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    this.spin += dt;
    const playerPosition = this.player.getWorldPosition();
    this.gems.forEach((gem, i) => {
      if (!gem.isActiveSelf) return;
      const home = this.gemHomes[i]!;
      gem.setLocalPosition(home.x, home.y + Math.sin(this.spin * CollectorGame.BOB_RATE + i) * CollectorGame.BOB_HEIGHT, home.z);
      gem.setLocalEulerAngles(this.spin * CollectorGame.SPIN_SPEED, 0, 0);
      const distance = Vector3.Distance(home, new Vector3(playerPosition.x, home.y, playerPosition.z));
      if (distance < CollectorGame.PICKUP_DISTANCE) {
        gem.setActive(false);
        this.collected++;
        this.gemsText["text"] = `Gems: ${this.collected} / ${this.gems.length}`;
        if (this.collected === this.gems.length) {
          this.finished = true;
          this.messageText["text"] = `All gems in ${this.elapsed.toFixed(1)} s! Press R to play again`;
        }
      }
    });
    if (!this.finished) {
      this.elapsed += dt;
      this.timeText["text"] = `Time: ${this.elapsed.toFixed(1)}`;
    }
  }

  private restart(): void {
    for (const gem of this.gems) gem.setActive(true);
    this.collected = 0;
    this.elapsed = 0;
    this.finished = false;
    this.gemsText["text"] = `Gems: 0 / ${this.gems.length}`;
    this.messageText["text"] = "";
  }

  private requireEntity(name: string): GameEntity {
    const entity = this.scene.findByName(name);
    if (!entity) throw new Error(`CollectorGame needs an entity named '${name}'`);
    return entity;
  }

  private requireText(entityName: string): Record<string, unknown> {
    const text = this.requireEntity(entityName).getComponent("TextComponent");
    if (!text) throw new Error(`Entity '${entityName}' has no Text component`);
    return text.data;
  }
}

/** Port of `samples.common.FollowCamera3D`: stays behind and above the "Player" and looks at it. */
export class FollowCamera3D extends ScriptComponent {
  /** Camera position relative to the player: above and towards +Z (the player runs towards -Z). */
  static readonly OFFSET = new Vector3(0, 340, 520);
  /** Point above the player's centre the camera looks at. */
  static readonly LOOK_HEIGHT = 40;
  /** How quickly the camera catches up; higher is snappier. */
  static readonly FOLLOW_RATE = 5;

  private readonly position = Vector3.Zero();
  private target!: GameEntity;

  override onStart(): void {
    const target = this.scene.findByName("Player");
    if (!target) throw new Error("FollowCamera3D needs an entity named 'Player'");
    this.target = target;
    this.position.copyFrom(target.getWorldPosition().add(FollowCamera3D.OFFSET));
    this.aim();
  }

  override onUpdate(dt: number): void {
    const desired = this.target.getWorldPosition().add(FollowCamera3D.OFFSET);
    const t = Math.min(1, FollowCamera3D.FOLLOW_RATE * dt);
    this.position.addInPlace(desired.subtract(this.position).scale(t));
    this.aim();
  }

  private aim(): void {
    const look = this.target.getWorldPosition();
    const direction = new Vector3(look.x, look.y + FollowCamera3D.LOOK_HEIGHT, look.z).subtract(this.position).normalize();
    // Forward (0, 0, -1) turned by pitch, then yaw, points along (-cos p sin y, sin p, -cos p cos y)
    const toDegrees = 180 / Math.PI;
    const yaw = Math.atan2(-direction.x, -direction.z) * toDegrees;
    const pitch = Math.asin(Math.max(-1, Math.min(1, direction.y))) * toDegrees;
    this.entity.setLocalPosition(this.position.x, this.position.y, this.position.z);
    this.entity.setLocalEulerAngles(yaw, pitch, 0);
  }
}
