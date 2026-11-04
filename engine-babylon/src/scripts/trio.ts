import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { Solid, SideBody, solidsNamed } from "./side";
import { keyAxis, requireEntity, requireText, type TextHandle } from "./util";

/** One hero's movement and look. */
interface Hero {
  readonly name: string;
  readonly runSpeed: number;
  readonly jumpSpeed: number;
  readonly airJumps: number;
  readonly help: string;
}

const HEROES: readonly Hero[] = [
  { name: "Mason", runSpeed: 280, jumpSpeed: 820, airJumps: 0, help: "E: conjure a crate" },
  { name: "Scout", runSpeed: 360, jumpSpeed: 820, airJumps: 1, help: "Space in mid-air: jump again" },
  { name: "Brute", runSpeed: 240, jumpSpeed: 720, airJumps: 0, help: "Push heavy blocks, E: smash cracked walls" },
];

/** Port of `samples.trio.TrioGame`: three heroes who share one body (Mason conjures crates, Scout double-jumps, Brute pushes). */
export class TrioGame extends ScriptComponent {
  static readonly GRAVITY = 2000;
  static readonly FALL_LIMIT = -500;
  /** Conjured crate size and how many can exist at once. */
  static readonly CRATE_SIZE = 80;
  static readonly MAX_CRATES = 2;
  /** How far in front of the hero a crate appears (centre distance). */
  static readonly CRATE_DISTANCE = 75;
  /** How close the Brute must be to a cracked wall to smash it. */
  static readonly SMASH_REACH = 40;
  /** Collision half size shared by all heroes. */
  static readonly HALF_WIDTH = 20;
  static readonly HALF_HEIGHT = 35;

  private heroEntities: GameEntity[] = [];
  private solids: Solid[] = [];
  private crates: SideBody[] = [];
  private crateEntities: GameEntity[] = [];
  private readonly pushables = new Map<Solid, SideBody>();
  private heavyBlocks: SideBody[] = [];
  private heavyEntities: GameEntity[] = [];
  private heavyHomes: [number, number][] = [];
  private crackedWalls: GameEntity[] = [];
  private goal!: Solid;
  private player!: GameEntity;
  private body!: SideBody;
  private partyText!: TextHandle;
  private messageText!: TextHandle;
  private startX = 0;
  private startY = 0;
  private hero = 0;
  private airJumpsLeft = 0;
  private facing = 1;
  private nextCrate = 0;
  private finished = false;

  override onStart(): void {
    this.heroEntities = HEROES.map((hero) => requireEntity(this.scene, `Hero ${hero.name}`, "TrioGame"));
    this.player = requireEntity(this.scene, "Player", "TrioGame");
    const start = this.player.getLocalPosition();
    this.startX = start.x;
    this.startY = start.y;
    this.body = new SideBody(this.startX, this.startY, TrioGame.HALF_WIDTH, TrioGame.HALF_HEIGHT);
    this.solids.push(...solidsNamed(this.scene, "Platform", "Cracked"));
    for (const entity of this.scene.entities) {
      if (entity.name.startsWith("Cracked")) {
        this.crackedWalls.push(entity);
      } else if (entity.name.startsWith("Heavy")) {
        const block = this.addPushable(entity, this.heavyBlocks, this.heavyEntities);
        this.heavyHomes.push([block.x, block.y]);
      }
    }
    const size = TrioGame.CRATE_SIZE;
    for (let i = 0; i < TrioGame.MAX_CRATES; i++) {
      const crate = this.scene.createEntity(`Conjured Crate ${i}`);
      this.scene.addMeshPrimitive(crate, "BOX", new Vector3(size, size, size), { r: 0.55, g: 0.75, b: 1 });
      crate.setActive(false);
      this.addPushable(crate, this.crates, this.crateEntities);
    }
    this.goal = new Solid(requireEntity(this.scene, "Goal", "TrioGame"));
    this.partyText = requireText(this.scene, "Party Text", "TrioGame");
    this.messageText = requireText(this.scene, "Message Text", "TrioGame");
    this.restart();
  }

  /** Makes {@code entity} a falling, pushable box that others can stand on. */
  private addPushable(entity: GameEntity, bodies: SideBody[], entities: GameEntity[]): SideBody {
    const solid = new Solid(entity);
    const pushable = new SideBody(solid.x, solid.y, solid.halfWidth, solid.halfHeight);
    pushable.self = solid;
    this.solids.push(solid);
    this.pushables.set(solid, pushable);
    bodies.push(pushable);
    entities.push(entity);
    return pushable;
  }

  override onUpdate(dt: number): void {
    if (this.input.isJustPressed("KeyR")) {
      this.restart();
      return;
    }
    if (this.finished) return;
    this.handleSwitching();
    const current = HEROES[this.hero]!;
    const direction = keyAxis((...k) => this.input.isDown(...k), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    if (direction !== 0) this.facing = direction;
    this.body.velocityX = direction * current.runSpeed;
    if (this.body.grounded) this.airJumpsLeft = current.airJumps;
    if (this.input.isJustPressed("Space", "KeyW", "ArrowUp")) {
      if (this.body.grounded) {
        this.body.velocityY = current.jumpSpeed;
      } else if (this.airJumpsLeft > 0) {
        this.airJumpsLeft--;
        this.body.velocityY = current.jumpSpeed;
      }
    }
    if (this.input.isJustPressed("KeyE")) this.useAbility();
    this.stepPushables(this.crates, this.crateEntities, dt);
    this.stepPushables(this.heavyBlocks, this.heavyEntities, dt);
    this.body.step(dt, TrioGame.GRAVITY, this.solids, (solid, amount) => this.push(solid, amount));
    this.body.moveEntity(this.player, 0);
    this.body.moveEntity(this.heroEntities[this.hero]!, 0);
    if (this.body.y < TrioGame.FALL_LIMIT) {
      this.body.x = this.startX;
      this.body.y = this.startY;
      this.body.velocityY = 0;
    }
    if (this.body.touches(this.goal.x, this.goal.y, this.goal.halfWidth, this.goal.halfHeight)) {
      this.finished = true;
      this.messageText.setText("The party reached the top! Press R to play again");
    }
  }

  private handleSwitching(): void {
    let chosen = this.hero;
    if (this.input.isJustPressed("Digit1")) chosen = 0;
    if (this.input.isJustPressed("Digit2")) chosen = 1;
    if (this.input.isJustPressed("Digit3")) chosen = 2;
    if (this.input.isJustPressed("Tab")) chosen = (this.hero + 1) % HEROES.length;
    if (chosen !== this.hero) this.selectHero(chosen);
  }

  private selectHero(index: number): void {
    this.hero = index;
    this.heroEntities.forEach((entity, i) => entity.setActive(i === index));
    this.body.moveEntity(this.heroEntities[index]!, 0);
    const hero = HEROES[index]!;
    this.partyText.setText(`Hero: ${hero.name}  (${hero.help})`);
  }

  private useAbility(): void {
    const name = HEROES[this.hero]!.name;
    if (name === "Mason") this.conjureCrate();
    else if (name === "Brute") this.smashWall();
  }

  /** Places a crate in front of the Mason if the space is free; the oldest crate is reused when all are out. */
  private conjureCrate(): void {
    const x = this.body.x + this.facing * TrioGame.CRATE_DISTANCE;
    const y = this.body.y - TrioGame.HALF_HEIGHT + TrioGame.CRATE_SIZE / 2 + 1;
    const half = TrioGame.CRATE_SIZE / 2;
    for (const solid of this.solids) {
      if (solid.isSolid() && solid.entity !== this.crateEntities[this.nextCrate] && solid.overlaps(x, y, half, half)) {
        this.messageText.setText("No room for a crate there");
        return;
      }
    }
    const crate = this.crates[this.nextCrate]!;
    crate.x = x;
    crate.y = y;
    crate.velocityY = 0;
    const entity = this.crateEntities[this.nextCrate]!;
    entity.setActive(true);
    crate.moveEntity(entity, 0);
    crate.self!.refresh();
    this.nextCrate = (this.nextCrate + 1) % TrioGame.MAX_CRATES;
    this.messageText.setText("");
  }

  private smashWall(): void {
    for (const wall of this.crackedWalls) {
      const solid = this.solidOf(wall);
      if (wall.isActiveSelf && this.body.touches(solid.x, solid.y, solid.halfWidth + TrioGame.SMASH_REACH, solid.halfHeight)) {
        wall.setActive(false);
        return;
      }
    }
  }

  /** Crates can be pushed by every hero; heavy blocks only by the Brute. */
  private push(solid: Solid, amount: number): boolean {
    const pushed = this.pushables.get(solid);
    if (pushed === undefined || (this.heavyBlocks.includes(pushed) && HEROES[this.hero]!.name !== "Brute")) return false;
    pushed.moveX(amount, this.solids, null);
    solid.x = pushed.x;
    solid.entity.setLocalPosition(pushed.x, pushed.y, 0);
    return true;
  }

  private stepPushables(bodies: SideBody[], entities: GameEntity[], dt: number): void {
    bodies.forEach((pushable, i) => {
      const entity = entities[i]!;
      if (!entity.isActiveSelf) return;
      pushable.velocityX = 0;
      pushable.step(dt, TrioGame.GRAVITY, this.solids, null);
      pushable.moveEntity(entity, 0);
      pushable.self!.refresh();
      if (pushable.y < TrioGame.FALL_LIMIT) entity.setActive(false);
    });
  }

  private solidOf(entity: GameEntity): Solid {
    const solid = this.solids.find((candidate) => candidate.entity === entity);
    if (!solid) throw new Error(`'${entity.name}' is not a solid`);
    return solid;
  }

  private restart(): void {
    this.body.x = this.startX;
    this.body.y = this.startY;
    this.body.velocityX = 0;
    this.body.velocityY = 0;
    this.finished = false;
    this.nextCrate = 0;
    for (const crate of this.crateEntities) crate.setActive(false);
    for (const wall of this.crackedWalls) wall.setActive(true);
    this.heavyBlocks.forEach((block, i) => {
      const home = this.heavyHomes[i]!;
      block.x = home[0];
      block.y = home[1];
      block.velocityY = 0;
      block.moveEntity(this.heavyEntities[i]!, 0);
      block.self!.refresh();
    });
    this.body.moveEntity(this.player, 0);
    this.selectHero(0);
    this.messageText.setText("");
  }
}
