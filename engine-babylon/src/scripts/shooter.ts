import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { clamp, JavaRandom, keyAxis, requireEntity, requireText, shapeSize, type TextHandle } from "./util";

/** Port of `samples.shooter.ShooterShip`: flies the player's ship inside the lower part of the screen. */
export class ShooterShip extends ScriptComponent {
  static readonly SPEED = 340;
  static readonly MIN_X = 30;
  static readonly MAX_X = 610;
  static readonly MIN_Y = 40;
  static readonly MAX_Y = 260;

  override onUpdate(dt: number): void {
    const dx = keyAxis((...k) => this.input.isDown(...k), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    const dy = keyAxis((...k) => this.input.isDown(...k), ["KeyS", "ArrowDown"], ["KeyW", "ArrowUp"]);
    const p = this.entity.getLocalPosition();
    this.entity.setLocalPosition(
      clamp(p.x + dx * ShooterShip.SPEED * dt, ShooterShip.MIN_X, ShooterShip.MAX_X),
      clamp(p.y + dy * ShooterShip.SPEED * dt, ShooterShip.MIN_Y, ShooterShip.MAX_Y),
      p.z,
    );
  }
}

/** Port of `samples.shooter.ShooterGame`: Space fires, enemies arrive faster and faster, lives and score. */
export class ShooterGame extends ScriptComponent {
  static readonly BULLET_POOL = 24;
  static readonly ENEMY_POOL = 16;
  static readonly FIRE_INTERVAL = 0.18;
  static readonly BULLET_SPEED = 620;
  static readonly BULLET_WIDTH = 4;
  static readonly BULLET_HEIGHT = 14;
  static readonly ENEMY_WIDTH = 30;
  static readonly ENEMY_HEIGHT = 22;
  static readonly ENEMY_SPEED = 90;
  static readonly ENEMY_SPEED_GAIN = 2.5;
  static readonly SWAY_AMPLITUDE = 60;
  static readonly SWAY_RATE = 2;
  static readonly SPAWN_INTERVAL = 1.2;
  static readonly MIN_SPAWN_INTERVAL = 0.35;
  static readonly SPAWN_SPEED_UP = 0.03;
  static readonly STAR_SPEED = 120;
  static readonly SPAWN_Y = 510;
  static readonly OFF_SCREEN_Y = -30;
  static readonly ENEMY_POINTS = 100;
  static readonly START_LIVES = 3;
  /** Fixed seed: the same enemy pattern every game. */
  static readonly RANDOM_SEED = 42;

  private bullets: GameEntity[] = [];
  private enemies: GameEntity[] = [];
  private stars: GameEntity[] = [];
  private readonly enemyBaseX = new Array<number>(ShooterGame.ENEMY_POOL).fill(0);
  private readonly enemyAge = new Array<number>(ShooterGame.ENEMY_POOL).fill(0);
  private random = new JavaRandom(ShooterGame.RANDOM_SEED);
  private ship!: GameEntity;
  private shipHalfWidth = 0;
  private shipHalfHeight = 0;
  private scoreText!: TextHandle;
  private livesText!: TextHandle;
  private messageText!: TextHandle;
  private fireCooldown = 0;
  private spawnTimer = 0;
  private spawnInterval = 0;
  private playTime = 0;
  private score = 0;
  private lives = 0;
  private gameOver = false;

  override onStart(): void {
    this.ship = requireEntity(this.scene, "Ship", "ShooterGame");
    const size = shapeSize(this.ship);
    this.shipHalfWidth = size.width / 2;
    this.shipHalfHeight = size.height / 2;
    this.scoreText = requireText(this.scene, "Score Text", "ShooterGame");
    this.livesText = requireText(this.scene, "Lives Text", "ShooterGame");
    this.messageText = requireText(this.scene, "Message Text", "ShooterGame");
    this.stars = this.scene.findByPrefix("Star");
    for (let i = 0; i < ShooterGame.BULLET_POOL; i++) {
      this.bullets.push(this.createPooled(`Bullet ${i}`, ShooterGame.BULLET_WIDTH, ShooterGame.BULLET_HEIGHT, 1, 0.92, 0.3));
    }
    for (let i = 0; i < ShooterGame.ENEMY_POOL; i++) {
      this.enemies.push(this.createPooled(`Enemy ${i}`, ShooterGame.ENEMY_WIDTH, ShooterGame.ENEMY_HEIGHT, 0.95, 0.3, 0.35));
    }
    this.restart();
  }

  /** Creates an inactive entity with a rectangle; the pool switches it on when needed. */
  private createPooled(name: string, width: number, height: number, r: number, g: number, b: number): GameEntity {
    const entity = this.scene.createEntity(name);
    this.scene.addShapeRenderer(entity, width, height, { r, g, b });
    entity.setActive(false);
    return entity;
  }

  override onUpdate(dt: number): void {
    this.scrollStars(dt);
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    if (this.gameOver) return;
    this.playTime += dt;
    this.fireCooldown -= dt;
    if (this.input.isDown("Space") && this.fireCooldown <= 0) {
      const ship = this.ship.getWorldPosition();
      activate(this.bullets, ship.x, ship.y + this.shipHalfHeight);
      this.fireCooldown = ShooterGame.FIRE_INTERVAL;
    }
    this.spawnTimer -= dt;
    if (this.spawnTimer <= 0) {
      const index = activate(this.enemies, 40 + this.random.nextFloat() * 560, ShooterGame.SPAWN_Y);
      if (index >= 0) {
        this.enemyBaseX[index] = this.enemies[index]!.getLocalPosition().x;
        this.enemyAge[index] = 0;
      }
      this.spawnInterval = Math.max(ShooterGame.MIN_SPAWN_INTERVAL, this.spawnInterval - ShooterGame.SPAWN_SPEED_UP);
      this.spawnTimer = this.spawnInterval;
    }
    this.moveBullets(dt);
    this.moveEnemies(dt);
    this.resolveHits();
  }

  private moveBullets(dt: number): void {
    for (const bullet of this.bullets) {
      if (!bullet.isActiveSelf) continue;
      const p = bullet.getLocalPosition();
      bullet.setLocalPosition(p.x, p.y + ShooterGame.BULLET_SPEED * dt, 0);
      if (p.y > ShooterGame.SPAWN_Y) bullet.setActive(false);
    }
  }

  private moveEnemies(dt: number): void {
    const speed = ShooterGame.ENEMY_SPEED + ShooterGame.ENEMY_SPEED_GAIN * this.playTime;
    this.enemies.forEach((enemy, i) => {
      if (!enemy.isActiveSelf) return;
      this.enemyAge[i]! += dt;
      const p = enemy.getLocalPosition();
      const x = clamp(this.enemyBaseX[i]! + Math.sin(this.enemyAge[i]! * ShooterGame.SWAY_RATE) * ShooterGame.SWAY_AMPLITUDE, 20, 620);
      enemy.setLocalPosition(x, p.y - speed * dt, 0);
      if (p.y < ShooterGame.OFF_SCREEN_Y) enemy.setActive(false);
    });
  }

  private resolveHits(): void {
    const ship = this.ship.getWorldPosition();
    for (const enemy of this.enemies) {
      if (!enemy.isActiveSelf) continue;
      const enemyPosition = enemy.getLocalPosition();
      for (const bullet of this.bullets) {
        if (
          bullet.isActiveSelf &&
          overlaps(bullet.getLocalPosition(), ShooterGame.BULLET_WIDTH / 2, ShooterGame.BULLET_HEIGHT / 2, enemyPosition, ShooterGame.ENEMY_WIDTH / 2, ShooterGame.ENEMY_HEIGHT / 2)
        ) {
          bullet.setActive(false);
          enemy.setActive(false);
          this.score += ShooterGame.ENEMY_POINTS;
          this.updateTexts();
          break;
        }
      }
      if (
        enemy.isActiveSelf &&
        overlaps(ship, this.shipHalfWidth, this.shipHalfHeight, enemyPosition, ShooterGame.ENEMY_WIDTH / 2, ShooterGame.ENEMY_HEIGHT / 2)
      ) {
        enemy.setActive(false);
        this.lives--;
        this.updateTexts();
        if (this.lives <= 0) {
          this.gameOver = true;
          this.messageText.setText(`Game over. Score ${this.score}. Press R to play again`);
        }
      }
    }
  }

  private scrollStars(dt: number): void {
    for (const star of this.stars) {
      const p = star.getLocalPosition();
      const y = p.y - ShooterGame.STAR_SPEED * dt;
      star.setLocalPosition(p.x, y < 0 ? y + 480 : y, p.z);
    }
  }

  private restart(): void {
    for (const entity of this.bullets) entity.setActive(false);
    for (const entity of this.enemies) entity.setActive(false);
    this.random = new JavaRandom(ShooterGame.RANDOM_SEED);
    this.score = 0;
    this.lives = ShooterGame.START_LIVES;
    this.playTime = 0;
    this.fireCooldown = 0;
    this.spawnInterval = ShooterGame.SPAWN_INTERVAL;
    this.spawnTimer = ShooterGame.SPAWN_INTERVAL;
    this.gameOver = false;
    this.messageText.setText("");
    this.updateTexts();
  }

  private updateTexts(): void {
    this.scoreText.setText(`Score: ${this.score}`);
    this.livesText.setText(`Lives: ${this.lives}`);
  }
}

/** Switches on the first inactive entity of {@code pool} at (x, y); returns its index, or -1 if all are in use. */
function activate(pool: GameEntity[], x: number, y: number): number {
  for (let i = 0; i < pool.length; i++) {
    const entity = pool[i]!;
    if (!entity.isActiveSelf) {
      entity.setLocalPosition(x, y, 0);
      entity.setActive(true);
      return i;
    }
  }
  return -1;
}

function overlaps(a: Vector3, aHalfWidth: number, aHalfHeight: number, b: Vector3, bHalfWidth: number, bHalfHeight: number): boolean {
  return Math.abs(a.x - b.x) < aHalfWidth + bHalfWidth && Math.abs(a.y - b.y) < aHalfHeight + bHalfHeight;
}
