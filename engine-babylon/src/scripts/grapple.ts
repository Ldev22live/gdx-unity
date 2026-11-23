import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { Solid, SideBody, solidsNamed } from "./side";
import { keyAxis, RADIANS_TO_DEGREES, requireEntity, requireText, type TextHandle } from "./util";

/** Port of `samples.grapple.GrappleGame`: a 2.5D grappling and physics-puzzle level with a pendulum swing. */
export class GrappleGame extends ScriptComponent {
  static readonly RUN_SPEED = 300;
  static readonly JUMP_SPEED = 780;
  static readonly GRAVITY = 2000;
  static readonly FALL_LIMIT = -500;
  /** Longest rope; anchors farther away cannot be hooked. */
  static readonly GRAPPLE_RANGE = 480;
  /** Sideways acceleration from A/D while swinging. */
  static readonly SWING_PUMP = 900;
  /** How far the gate rises when the plate is pressed, and its speed. */
  static readonly GATE_LIFT = 320;
  static readonly GATE_SPEED = 260;

  private solids: Solid[] = [];
  private anchors: Solid[] = [];
  private checkpoints: Solid[] = [];
  private body!: SideBody;
  private crate!: SideBody;
  private crateEntity!: GameEntity;
  private crateStartX = 0;
  private crateStartY = 0;
  private plate!: Solid;
  private gate!: Solid;
  private gateBaseY = 0;
  private goal!: Solid;
  private player!: GameEntity;
  private rope!: GameEntity;
  private statusText!: TextHandle;
  private messageText!: TextHandle;
  private hooked: Solid | null = null;
  private ropeLength = 0;
  private startX = 0;
  private startY = 0;
  private respawnX = 0;
  private respawnY = 0;
  private finished = false;

  override onStart(): void {
    this.solids.push(...solidsNamed(this.scene, "Platform", "Gate"));
    this.anchors.push(...solidsNamed(this.scene, "Anchor"));
    this.checkpoints.push(...solidsNamed(this.scene, "Checkpoint"));
    const gate = this.solids.find((solid) => solid.entity.name === "Gate");
    if (!gate) throw new Error("GrappleGame needs an entity named 'Gate'");
    this.gate = gate;
    this.gateBaseY = gate.y;
    this.crateEntity = requireEntity(this.scene, "Crate", "GrappleGame");
    const crateSolid = new Solid(this.crateEntity);
    this.crate = new SideBody(crateSolid.x, crateSolid.y, crateSolid.halfWidth, crateSolid.halfHeight);
    this.crate.self = crateSolid;
    this.crateStartX = this.crate.x;
    this.crateStartY = this.crate.y;
    this.solids.push(crateSolid);
    this.plate = new Solid(requireEntity(this.scene, "Plate", "GrappleGame"));
    this.goal = new Solid(requireEntity(this.scene, "Goal", "GrappleGame"));
    this.player = requireEntity(this.scene, "Player", "GrappleGame");
    const playerBox = new Solid(this.player);
    this.body = new SideBody(playerBox.x, playerBox.y, playerBox.halfWidth, playerBox.halfHeight);
    this.startX = playerBox.x;
    this.startY = playerBox.y;
    this.rope = requireEntity(this.scene, "Rope", "GrappleGame");
    this.statusText = requireText(this.scene, "Status Text", "GrappleGame");
    this.messageText = requireText(this.scene, "Message Text", "GrappleGame");
    this.restart();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    this.updateGate(dt);
    if (this.finished) return;

    const direction = keyAxis((...k) => this.input.isDown(...k), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    const grappleHeld = this.input.isDown("KeyJ", "ShiftLeft", "ShiftRight");
    if (grappleHeld && this.hooked === null && !this.body.grounded) {
      this.hooked = this.nearestAnchor();
      if (this.hooked !== null) {
        this.ropeLength = Math.hypot(this.body.x - this.hooked.x, this.body.y - this.hooked.y);
        this.scene.playCue("grapple");
      }
    }
    if (!grappleHeld) this.hooked = null;

    if (this.hooked !== null) {
      this.swingStep(this.hooked, direction, dt);
    } else {
      this.body.velocityX = direction * GrappleGame.RUN_SPEED;
      if (this.body.grounded && this.input.isJustPressed("Space", "KeyW", "ArrowUp")) {
        this.body.velocityY = GrappleGame.JUMP_SPEED;
        this.scene.playCue("jump");
      }
      this.body.step(dt, GrappleGame.GRAVITY, this.solids, (solid, amount) => this.pushCrate(solid, amount));
    }
    this.stepCrate(dt);
    this.body.moveEntity(this.player, 0);
    this.updateRope();

    for (const checkpoint of this.checkpoints) {
      if (this.body.touches(checkpoint.x, checkpoint.y, checkpoint.halfWidth, checkpoint.halfHeight)) {
        this.respawnX = checkpoint.x;
        this.respawnY = checkpoint.y + 20;
      }
    }
    if (this.body.y < GrappleGame.FALL_LIMIT) {
      this.hooked = null;
      this.scene.playCue("hurt");
      this.body.x = this.respawnX;
      this.body.y = this.respawnY;
      this.body.velocityX = 0;
      this.body.velocityY = 0;
    }
    if (this.body.touches(this.goal.x, this.goal.y, this.goal.halfWidth, this.goal.halfHeight)) {
      this.finished = true;
      this.scene.playCue("win");
      this.messageText.setText("You crossed the gorge! Press R to play again");
    }
  }

  /** Pendulum motion around the hooked anchor, then collision through {@link SideBody}. */
  private swingStep(hooked: Solid, direction: number, dt: number): void {
    const vx = this.body.velocityX + direction * GrappleGame.SWING_PUMP * dt;
    const vy = this.body.velocityY - GrappleGame.GRAVITY * dt;
    // Where the player would go, pulled back onto the rope's circle if the rope is taut
    let sx = this.body.x + vx * dt - hooked.x;
    let sy = this.body.y + vy * dt - hooked.y;
    const length = Math.hypot(sx, sy);
    if (length > this.ropeLength) {
      sx = (sx / length) * this.ropeLength;
      sy = (sy / length) * this.ropeLength;
    }
    const targetX = hooked.x + sx;
    const targetY = hooked.y + sy;
    this.body.velocityX = (targetX - this.body.x) / dt;
    this.body.velocityY = (targetY - this.body.y) / dt;
    this.body.moveX(targetX - this.body.x, this.solids, null);
    this.body.moveY(targetY - this.body.y, this.solids);
  }

  private nearestAnchor(): Solid | null {
    let best: Solid | null = null;
    let bestDistance = GrappleGame.GRAPPLE_RANGE;
    for (const anchor of this.anchors) {
      const distance = Math.hypot(this.body.x - anchor.x, this.body.y - anchor.y);
      if (anchor.y > this.body.y && distance < bestDistance) {
        best = anchor;
        bestDistance = distance;
      }
    }
    return best;
  }

  /** Shows the rope as a thin box stretched from the player to the anchor. */
  private updateRope(): void {
    this.rope.setActive(this.hooked !== null);
    if (this.hooked === null) return;
    const dx = this.hooked.x - this.body.x;
    const dy = this.hooked.y - this.body.y;
    const length = Math.hypot(dx, dy);
    this.rope.setLocalPosition(this.body.x + dx / 2, this.body.y + dy / 2, 0);
    // The rope box is 1 unit tall along its local Y axis; roll it towards the anchor and stretch it
    this.rope.setLocalEulerAngles(0, 0, Math.atan2(-dx, dy) * RADIANS_TO_DEGREES);
    this.rope.setLocalScale(1, length, 1);
  }

  /** The plate is pressed while the player or the crate rests on it; the gate rises while pressed. */
  private updateGate(dt: number): void {
    const pressed = restsOn(this.body, this.plate) || restsOn(this.crate, this.plate);
    const target = this.gateBaseY + (pressed ? GrappleGame.GATE_LIFT : 0);
    let y = this.gate.entity.getLocalPosition().y;
    const step = GrappleGame.GATE_SPEED * dt;
    y = Math.abs(target - y) <= step ? target : y + Math.sign(target - y) * step;
    this.gate.entity.setLocalPosition(this.gate.x, y, 0);
    this.gate.refresh();
    this.statusText.setText(pressed ? "Gate: open" : "Gate: closed");
  }

  private pushCrate(solid: Solid, amount: number): boolean {
    if (solid !== this.crate.self) return false;
    this.crate.moveX(amount, this.solids, null);
    solid.x = this.crate.x;
    this.crateEntity.setLocalPosition(this.crate.x, this.crate.y, 0);
    return true;
  }

  private stepCrate(dt: number): void {
    this.crate.velocityX = 0;
    this.crate.step(dt, GrappleGame.GRAVITY, this.solids, null);
    if (this.crate.y < GrappleGame.FALL_LIMIT) {
      this.crate.x = this.crateStartX;
      this.crate.y = this.crateStartY;
      this.crate.velocityY = 0;
    }
    this.crate.moveEntity(this.crateEntity, 0);
    this.crate.self!.refresh();
  }

  private restart(): void {
    this.body.x = this.startX;
    this.body.y = this.startY;
    this.body.velocityX = 0;
    this.body.velocityY = 0;
    this.respawnX = this.startX;
    this.respawnY = this.startY;
    this.crate.x = this.crateStartX;
    this.crate.y = this.crateStartY;
    this.crate.velocityY = 0;
    this.crate.moveEntity(this.crateEntity, 0);
    this.crate.self!.refresh();
    this.hooked = null;
    this.finished = false;
    this.body.moveEntity(this.player, 0);
    this.updateRope();
    this.messageText.setText("");
  }
}

function restsOn(body: SideBody, plate: Solid): boolean {
  return Math.abs(body.x - plate.x) < plate.halfWidth && Math.abs(body.y - body.halfHeight - (plate.y + plate.halfHeight)) < 30;
}
