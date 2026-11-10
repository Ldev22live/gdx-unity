import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { clamp, DEGREES_TO_RADIANS, keyAxis, requireEntity, requireText, shapeSize, type TextHandle } from "./util";

/** Port of `samples.breakout.BreakoutPaddle`: moves the paddle left and right between the walls. */
export class BreakoutPaddle extends ScriptComponent {
  static readonly SPEED = 520;
  static readonly LEFT_WALL = 10;
  static readonly RIGHT_WALL = 630;
  private halfWidth = 0;

  override onStart(): void {
    this.halfWidth = shapeSize(this.entity).width / 2;
  }

  override onUpdate(dt: number): void {
    const direction = keyAxis((...k) => this.input.isDown(...k), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    const p = this.entity.getLocalPosition();
    const x = clamp(p.x + direction * BreakoutPaddle.SPEED * dt, BreakoutPaddle.LEFT_WALL + this.halfWidth, BreakoutPaddle.RIGHT_WALL - this.halfWidth);
    this.entity.setLocalPosition(x, p.y, p.z);
  }
}

/** Port of `samples.breakout.BreakoutBall`: the ball and the rules of Breakout. */
export class BreakoutBall extends ScriptComponent {
  static readonly START_SPEED = 360;
  static readonly SPEED_GAIN = 6;
  static readonly MAX_SPEED = 720;
  /** Steepest bounce off the paddle edge, in degrees from vertical. */
  static readonly MAX_BOUNCE_ANGLE = 60;
  static readonly LEFT = 10;
  static readonly RIGHT = 630;
  static readonly TOP = 470;
  static readonly BRICK_POINTS = 10;
  static readonly START_LIVES = 3;

  private bricks: GameEntity[] = [];
  private paddle!: GameEntity;
  private paddleHalfWidth = 0;
  private paddleHalfHeight = 0;
  private radius = 0;
  private scoreText!: TextHandle;
  private livesText!: TextHandle;
  private messageText!: TextHandle;
  private x = 0;
  private y = 0;
  private velocityX = 0;
  private velocityY = 0;
  private speed = 0;
  private launched = false;
  private gameOver = false;
  private score = 0;
  private lives = 0;

  override onStart(): void {
    this.bricks = this.scene.findByPrefix("Brick");
    this.paddle = requireEntity(this.scene, "Paddle", "BreakoutBall");
    const paddleSize = shapeSize(this.paddle);
    this.paddleHalfWidth = paddleSize.width / 2;
    this.paddleHalfHeight = paddleSize.height / 2;
    this.radius = shapeSize(this.entity).width / 2;
    this.scoreText = requireText(this.scene, "Score Text", "BreakoutBall");
    this.livesText = requireText(this.scene, "Lives Text", "BreakoutBall");
    this.messageText = requireText(this.scene, "Message Text", "BreakoutBall");
    this.restart();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    if (this.gameOver) return;
    if (!this.launched) {
      const paddle = this.paddle.getWorldPosition();
      this.x = paddle.x;
      this.y = paddle.y + this.paddleHalfHeight + this.radius + 1;
      if (this.input.isJustPressed("Space")) {
        this.launched = true;
        this.scene.playCue("launch");
        this.messageText.setText("");
        this.velocityX = this.speed * 0.4;
        this.velocityY = this.speed;
        this.normalizeVelocity();
      }
    } else {
      this.step(dt);
    }
    this.entity.setLocalPosition(this.x, this.y, 0);
  }

  private step(dt: number): void {
    this.x += this.velocityX * dt;
    this.y += this.velocityY * dt;
    if (this.x - this.radius < BreakoutBall.LEFT) {
      this.x = BreakoutBall.LEFT + this.radius;
      this.velocityX = Math.abs(this.velocityX);
    }
    if (this.x + this.radius > BreakoutBall.RIGHT) {
      this.x = BreakoutBall.RIGHT - this.radius;
      this.velocityX = -Math.abs(this.velocityX);
    }
    if (this.y + this.radius > BreakoutBall.TOP) {
      this.y = BreakoutBall.TOP - this.radius;
      this.velocityY = -Math.abs(this.velocityY);
    }
    const paddle = this.paddle.getWorldPosition();
    if (this.velocityY < 0 && this.overlaps(paddle.x, paddle.y, this.paddleHalfWidth, this.paddleHalfHeight)) {
      // Where the ball hits the paddle sets the bounce angle: centre straight up, edges steep
      const hit = clamp((this.x - paddle.x) / this.paddleHalfWidth, -1, 1);
      const angle = hit * BreakoutBall.MAX_BOUNCE_ANGLE * DEGREES_TO_RADIANS;
      this.velocityX = this.speed * Math.sin(angle);
      this.velocityY = this.speed * Math.cos(angle);
      this.scene.playCue("paddle");
      this.y = paddle.y + this.paddleHalfHeight + this.radius;
    }
    this.hitBricks();
    if (this.y < -this.radius) this.loseLife();
  }

  /** Bounces off the first brick touched, on the side with the smaller overlap. */
  private hitBricks(): void {
    for (const brick of this.bricks) {
      if (!brick.isActiveSelf) continue;
      const size = shapeSize(brick);
      const p = brick.getWorldPosition();
      const halfWidth = size.width / 2;
      const halfHeight = size.height / 2;
      if (!this.overlaps(p.x, p.y, halfWidth, halfHeight)) continue;
      const overlapX = halfWidth + this.radius - Math.abs(this.x - p.x);
      const overlapY = halfHeight + this.radius - Math.abs(this.y - p.y);
      if (overlapX < overlapY) {
        this.velocityX = this.x < p.x ? -Math.abs(this.velocityX) : Math.abs(this.velocityX);
      } else {
        this.velocityY = this.y < p.y ? -Math.abs(this.velocityY) : Math.abs(this.velocityY);
      }
      brick.setActive(false);
      this.score += BreakoutBall.BRICK_POINTS;
      this.scene.playCue("brick");
      this.speed = Math.min(BreakoutBall.MAX_SPEED, this.speed + BreakoutBall.SPEED_GAIN);
      this.normalizeVelocity();
      this.updateTexts();
      if (this.bricksLeft() === 0) {
        this.gameOver = true;
        this.scene.playCue("win");
        this.messageText.setText(`You cleared the wall! Score ${this.score}. Press R to play again`);
      }
      return;
    }
  }

  private loseLife(): void {
    this.lives--;
    this.scene.playCue("lose");
    this.launched = false;
    this.updateTexts();
    if (this.lives <= 0) {
      this.gameOver = true;
      this.messageText.setText(`Game over. Score ${this.score}. Press R to play again`);
    } else {
      this.messageText.setText("Press Space to launch");
    }
  }

  private restart(): void {
    for (const brick of this.bricks) brick.setActive(true);
    this.score = 0;
    this.lives = BreakoutBall.START_LIVES;
    this.speed = BreakoutBall.START_SPEED;
    this.launched = false;
    this.gameOver = false;
    this.messageText.setText("Press Space to launch");
    this.updateTexts();
  }

  private overlaps(boxX: number, boxY: number, halfWidth: number, halfHeight: number): boolean {
    return Math.abs(this.x - boxX) < halfWidth + this.radius && Math.abs(this.y - boxY) < halfHeight + this.radius;
  }

  private normalizeVelocity(): void {
    const length = Math.hypot(this.velocityX, this.velocityY);
    this.velocityX = (this.velocityX / length) * this.speed;
    this.velocityY = (this.velocityY / length) * this.speed;
  }

  private bricksLeft(): number {
    return this.bricks.filter((brick) => brick.isActiveSelf).length;
  }

  private updateTexts(): void {
    this.scoreText.setText(`Score: ${this.score}`);
    this.livesText.setText(`Lives: ${this.lives}`);
  }
}
