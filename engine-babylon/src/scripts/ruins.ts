import type { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { Solid, SideBody, solidsNamed } from "./side";
import { keyAxis, requireEntity, requireText, type TextHandle } from "./util";

/** Port of `samples.ruins.RuinsGame`: the rules of the 2.5D ruins platformer. */
export class RuinsGame extends ScriptComponent {
  static readonly RUN_SPEED = 300;
  static readonly JUMP_SPEED = 820;
  static readonly GRAVITY = 2000;
  /** Below this height the player has fallen into a pit. */
  static readonly FALL_LIMIT = -500;
  /** Mover travel from its start position, and seconds per back-and-forth cycle. */
  static readonly MOVER_RANGE = 120;
  static readonly MOVER_PERIOD = 4;
  /** Orb spin in degrees per second. */
  static readonly ORB_SPIN = 180;

  private movers: GameEntity[] = [];
  private moverHomes: Vector3[] = [];
  private orbs: GameEntity[] = [];
  private hazards: Solid[] = [];
  private checkpoints: Solid[] = [];
  private solids: Solid[] = [];
  private goal!: Solid;
  private player!: GameEntity;
  private body!: SideBody;
  private collectedText!: TextHandle;
  private deathsText!: TextHandle;
  private messageText!: TextHandle;
  private startX = 0;
  private startY = 0;
  private respawnX = 0;
  private respawnY = 0;
  private time = 0;
  private collected = 0;
  private deaths = 0;
  private finished = false;

  override onStart(): void {
    for (const entity of this.scene.entities) {
      const name = entity.name;
      if (name.startsWith("Mover")) {
        this.movers.push(entity);
        this.moverHomes.push(entity.getLocalPosition());
      } else if (name.startsWith("Orb")) this.orbs.push(entity);
      else if (name.startsWith("Spikes")) this.hazards.push(new Solid(entity));
      else if (name.startsWith("Checkpoint")) this.checkpoints.push(new Solid(entity));
    }
    this.solids = solidsNamed(this.scene, "Platform", "Mover");
    this.goal = new Solid(requireEntity(this.scene, "Goal", "RuinsGame"));
    this.player = requireEntity(this.scene, "Player", "RuinsGame");
    const playerBox = new Solid(this.player);
    this.body = new SideBody(playerBox.x, playerBox.y, playerBox.halfWidth, playerBox.halfHeight);
    this.startX = playerBox.x;
    this.startY = playerBox.y;
    this.collectedText = requireText(this.scene, "Collected Text", "RuinsGame");
    this.deathsText = requireText(this.scene, "Deaths Text", "RuinsGame");
    this.messageText = requireText(this.scene, "Message Text", "RuinsGame");
    this.restart();
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    this.time += dt;
    this.moveMovers();
    for (const orb of this.orbs) orb.rotate(RuinsGame.ORB_SPIN * dt, 0, 0);
    if (this.finished) return;

    const direction = keyAxis((...k) => this.input.isDown(...k), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    this.body.velocityX = direction * RuinsGame.RUN_SPEED;
    if (this.body.grounded && this.input.isJustPressed("Space", "KeyW", "ArrowUp")) {
      this.body.velocityY = RuinsGame.JUMP_SPEED;
      this.scene.playCue("jump");
    }
    this.body.step(dt, RuinsGame.GRAVITY, this.solids, null);
    this.body.moveEntity(this.player, 0);

    for (const orb of this.orbs) {
      const p = orb.getWorldPosition();
      if (orb.isActiveSelf && this.body.touches(p.x, p.y, 15, 15)) {
        orb.setActive(false);
        this.collected++;
        this.scene.playCue("coin");
        this.updateTexts();
      }
    }
    for (const checkpoint of this.checkpoints) {
      if (this.body.touches(checkpoint.x, checkpoint.y, checkpoint.halfWidth, checkpoint.halfHeight)) {
        this.respawnX = checkpoint.x;
        this.respawnY = checkpoint.y + 20;
      }
    }
    for (const hazard of this.hazards) {
      if (this.body.touches(hazard.x, hazard.y, hazard.halfWidth, hazard.halfHeight)) this.die();
    }
    if (this.body.y < RuinsGame.FALL_LIMIT) this.die();
    if (this.body.touches(this.goal.x, this.goal.y, this.goal.halfWidth, this.goal.halfHeight)) {
      this.finished = true;
      this.scene.playCue("win");
      this.messageText.setText(`You made it out! ${this.collected}/${this.orbs.length} orbs, ${this.deaths} deaths. Press R to play again`);
    }
  }

  /** Slides each mover along a sine wave; "Mover Y" ones move vertically. */
  private moveMovers(): void {
    this.movers.forEach((mover, i) => {
      const home = this.moverHomes[i]!;
      const offset = RuinsGame.MOVER_RANGE * Math.sin((this.time * 2 * Math.PI) / RuinsGame.MOVER_PERIOD);
      if (mover.name.startsWith("Mover Y")) mover.setLocalPosition(home.x, home.y + offset, home.z);
      else mover.setLocalPosition(home.x + offset, home.y, home.z);
    });
    for (const solid of this.solids) solid.refresh();
  }

  private die(): void {
    this.deaths++;
    this.scene.playCue("hurt");
    this.body.x = this.respawnX;
    this.body.y = this.respawnY;
    this.body.velocityX = 0;
    this.body.velocityY = 0;
    this.updateTexts();
  }

  private restart(): void {
    this.body.x = this.startX;
    this.body.y = this.startY;
    this.body.velocityX = 0;
    this.body.velocityY = 0;
    this.respawnX = this.startX;
    this.respawnY = this.startY;
    this.collected = 0;
    this.deaths = 0;
    this.finished = false;
    for (const orb of this.orbs) orb.setActive(true);
    this.body.moveEntity(this.player, 0);
    this.messageText.setText("");
    this.updateTexts();
  }

  private updateTexts(): void {
    this.collectedText.setText(`Orbs: ${this.collected} / ${this.orbs.length}`);
    this.deathsText.setText(`Deaths: ${this.deaths}`);
  }
}
