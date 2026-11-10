import { Vector3 } from "@babylonjs/core";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { MeshColliderGeometry } from "../src/runtime/physics3d/ColliderGeometry";
import type { GameEntity } from "../src/runtime/GameEntity";
import { Input } from "../src/runtime/Input";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import { WalkerPlayer } from "../src/scripts/walker";
import { createHeadlessEngine, loadSample } from "./support";

const STEP = 1 / 60;
const GRAVITY = 9.81;

let jolt: JoltModule;
const made: Physics3D[] = [];

beforeAll(async () => {
  jolt = await loadJolt();
});

afterEach(() => {
  made.splice(0).forEach((physics) => physics.dispose());
});

function playground(): { built: BuiltScene; physics: Physics3D; hero: ReturnType<BuiltScene["game"]["findByName"]> & object } {
  const built = buildScene(createHeadlessEngine(), loadSample("character-playground-3d.json"));
  const physics = new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
  made.push(physics);
  // The character is made at the start of the first update
  physics.update(STEP);
  return { built, physics, hero: built.game.findByName("Hero")! };
}

function run(physics: Physics3D, seconds: number, step = STEP): void {
  for (let i = 0; i < Math.round(seconds / step); i++) physics.update(step);
}

describe("a Character Controller 3D on the character-playground-3d sample", () => {
  it("shouldSettleOnTheFloorAndStayThereWithoutSinkingOrDrifting", () => {
    const { physics, hero } = playground();

    run(physics, 2);
    const settled = hero.getWorldPosition();
    run(physics, 10);
    const later = hero.getWorldPosition();

    expect(physics.isCharacterGrounded(hero)).toBe(true);
    expect(Vector3.Distance(settled, later), "no sinking or drifting in ten seconds").toBeLessThan(1);
    expect(physics.getWarnings()).toEqual([]);
  });

  it("shouldWalkAtTheSpeedItWasToldAndStopWhenToldToStop", () => {
    const { physics, hero } = playground();
    run(physics, 1);
    const start = hero.getWorldPosition();

    physics.moveCharacter(hero, new Vector3(300, 0, 0));
    run(physics, 1);
    const walked = hero.getWorldPosition().x - start.x;
    physics.moveCharacter(hero, Vector3.Zero());
    run(physics, 0.5);

    expect(walked, "300 units a second for a second").toBeGreaterThan(285);
    expect(walked).toBeLessThan(315);
    expect(Math.abs(hero.getWorldPosition().x - start.x - walked), "stopped").toBeLessThan(2);
    expect(physics.getCharacterVelocity(hero).length()).toBeLessThan(1);
  });

  it("shouldWalkTheSameDistanceAtThirtyAndSixtyFramesPerSecond", () => {
    const a = playground();
    const b = playground();
    run(a.physics, 1);
    run(b.physics, 1);
    const startA = a.hero.getWorldPosition().x;
    const startB = b.hero.getWorldPosition().x;

    a.physics.moveCharacter(a.hero, new Vector3(300, 0, 0));
    b.physics.moveCharacter(b.hero, new Vector3(300, 0, 0));
    run(a.physics, 1, 1 / 60);
    run(b.physics, 1, 1 / 30);

    expect(Math.abs(a.hero.getWorldPosition().x - startA - (b.hero.getWorldPosition().x - startB))).toBeLessThan(6);
  });

  it("shouldJumpToTheHeightThePhysicsSaysAndOnlyWhileOnTheGround", () => {
    const { physics, hero } = playground();
    run(physics, 1);
    const ground = hero.getWorldPosition().y;
    let apex = ground;

    expect(physics.jumpCharacter(hero, 450)).toBe(true);
    expect(physics.jumpCharacter(hero, 450), "not again in the air").toBe(false);
    for (let i = 0; i < 90; i++) {
      physics.update(STEP);
      apex = Math.max(apex, hero.getWorldPosition().y);
    }

    const expected = ((450 / 100) ** 2 / (2 * GRAVITY)) * 100;
    expect((apex - ground) / expected, `apex ${apex - ground} against v squared over 2g ${expected}`).toBeGreaterThan(0.95);
    expect((apex - ground) / expected).toBeLessThan(1.05);
    run(physics, 2);
    expect(physics.isCharacterGrounded(hero), "landed").toBe(true);
    expect(() => physics.jumpCharacter(hero, -1)).toThrow(/positive/);
  });

  /** Walks the character with {@code velocity} for {@code seconds} and returns the highest its center got and where it ended. */
  function walk(physics: Physics3D, hero: GameEntity, velocity: Vector3, seconds: number): { peak: number; end: Vector3 } {
    physics.moveCharacter(hero, velocity);
    let peak = hero.getWorldPosition().y;
    for (let i = 0; i < Math.round(seconds / STEP); i++) {
      physics.update(STEP);
      peak = Math.max(peak, hero.getWorldPosition().y);
    }
    return { peak, end: hero.getWorldPosition() };
  }

  it("shouldClimbTheFourStepsOfTwentyUnitsEachBecauseTheStepHeightIsThirty", () => {
    const { physics, hero } = playground();
    physics.teleportCharacter(hero, new Vector3(-420, 75, 600));
    run(physics, 1);
    const floorLevel = hero.getWorldPosition().y;

    const { peak } = walk(physics, hero, new Vector3(300, 0, 0), 3);

    // The steps are 20, 40, 60 and 80 units high, so the character's center ends up 80 above where it stood on the floor
    expect(peak - floorLevel, "up to the top of the fourth step").toBeGreaterThan(75);
  });

  it("shouldBeStoppedByTheBlockThatIsHigherThanAStepAndNotClimbIt", () => {
    const { physics, hero } = playground();
    physics.teleportCharacter(hero, new Vector3(-700, 75, -100));
    run(physics, 1);
    const floorLevel = hero.getWorldPosition().y;

    const { peak, end } = walk(physics, hero, new Vector3(0, 0, -300), 3);

    expect(peak - floorLevel, "still on the floor").toBeLessThan(10);
    expect(end.z, "stopped before the block that fills z = -400 to -200").toBeGreaterThan(-200);
  });

  it("shouldWalkUpTheGentleRampAndNotUpTheSteepOne", () => {
    const { physics, hero } = playground();
    physics.teleportCharacter(hero, new Vector3(150, 75, 100));
    run(physics, 1);
    const flat = hero.getWorldPosition().y;
    const gentle = walk(physics, hero, new Vector3(300, 0, 0), 3).peak;

    physics.teleportCharacter(hero, new Vector3(150, 75, -300));
    run(physics, 1);
    const steepFlat = hero.getWorldPosition().y;
    const steep = walk(physics, hero, new Vector3(300, 0, 0), 3).peak;

    expect(gentle - flat, "up the 25 degree ramp").toBeGreaterThan(40);
    expect(steep - steepFlat, "the 60 degree ramp is over the 45 degree limit").toBeLessThan(25);
  });

  it("shouldPushACrateAcrossTheFloor", () => {
    const { built, physics, hero } = playground();
    const crate = built.game.findByName("Crate 1")!;
    physics.teleportCharacter(hero, new Vector3(300, 75, 700));
    run(physics, 1);
    const before = crate.getWorldPosition().z;

    physics.moveCharacter(hero, new Vector3(0, 0, 250));
    run(physics, 4);

    expect(crate.getWorldPosition().z - before, "the crate moved away from the character").toBeGreaterThan(60);
  });

  it("shouldReportContactsWithTheBodiesItTouches", () => {
    const { built, physics, hero } = playground();
    const crate = built.game.findByName("Crate 1")!;
    const touched: string[] = [];
    physics.addContactListener((contact) => {
      if (contact.kind === "COLLISION_ENTER" && (contact.entityA === hero || contact.entityB === hero)) {
        touched.push((contact.entityA === hero ? contact.entityB : contact.entityA).name);
      }
    });
    physics.teleportCharacter(hero, new Vector3(300, 75, 760));
    run(physics, 0.5);

    physics.moveCharacter(hero, new Vector3(0, 0, 250));
    run(physics, 2);

    expect(touched).toContain(crate.name);
  });

  it("shouldRefuseACharacterBesideARigidBodyAndNameTheEntity", () => {
    const built = buildScene(createHeadlessEngine(), {
      schemaVersion: 1,
      name: "Refusal",
      active: true,
      entities: [
        {
          id: "Mixed", name: "Mixed", active: true, parentId: null,
          transform: { position: { x: 0, y: 100, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
          components: [
            { type: "CharacterController3DComponent", enabled: true, data: {} },
            { type: "RigidBody3DComponent", enabled: true, data: { motionType: "DYNAMIC", mass: 1 } },
            { type: "BoxCollider3DComponent", enabled: true, data: { sizeX: 50, sizeY: 50, sizeZ: 50 } },
          ],
        },
      ] as never,
    });
    const physics = new Physics3D(built.game, new JoltPhysicsBackend3D(jolt));
    made.push(physics);

    run(physics, 0.3);

    expect(physics.getWarnings()).toHaveLength(1);
    expect(physics.getWarnings()[0]).toContain("Mixed");
    expect(physics.getWarnings()[0]).toContain("Rigid Body 3D");
    expect(() => physics.isCharacterGrounded(built.game.findByName("Mixed")!)).toThrow(/is not a character/);
  });

  it("shouldRemoveTheCharacterWhenItsEntityIsSwitchedOffAndMakeItAgainWhenSwitchedOn", () => {
    const { physics, hero } = playground();
    run(physics, 1);
    expect(physics.isCharacterGrounded(hero)).toBe(true);

    hero.setActive(false);
    physics.update(STEP);
    expect(() => physics.isCharacterGrounded(hero)).toThrow(/is not a character/);

    hero.setActive(true);
    run(physics, 1);
    expect(physics.isCharacterGrounded(hero)).toBe(true);
  });

  it("shouldTeleportWithoutSweepingAndClearItsMotion", () => {
    const { physics, hero } = playground();
    run(physics, 1);
    physics.moveCharacter(hero, new Vector3(300, 0, 0));

    physics.teleportCharacter(hero, new Vector3(0, 75, -500));

    expect(hero.getWorldPosition().z).toBeCloseTo(-500, 3);
    run(physics, 1);
    expect(Math.abs(hero.getWorldPosition().x), "the walking velocity was cleared").toBeLessThan(2);
  });

  it("shouldRefuseToUseACharacterAsARigidBody", () => {
    const { physics, hero } = playground();
    physics.update(STEP);

    expect(() => physics.addImpulse(hero, new Vector3(1, 0, 0))).toThrow(/is a character/);
  });
});

describe("the WalkerPlayer script on the web", () => {
  it("shouldWalkWithTheKeysJumpWithSpaceAndShowWhetherItIsOnTheGround", () => {
    const { built, physics, hero } = playground();
    const script = new WalkerPlayer();
    script.entity = hero;
    script.scene = built.game;
    script.input = new Input();
    script.physics3d = physics;
    const label = built.game.findByName("Help Text")!.getComponent("TextComponent")!.data;
    script.onStart();
    run(physics, 1);
    const start = hero.getWorldPosition();

    script.input.press("KeyD");
    for (let i = 0; i < 60; i++) {
      script.onUpdate(STEP);
      physics.update(STEP);
      script.input.endFrame();
    }
    expect(hero.getWorldPosition().x - start.x, "D walks +x at 300 a second").toBeGreaterThan(280);
    expect(String(label["text"])).toContain("On the ground");

    script.input.release("KeyD");
    script.input.press("Space");
    script.onUpdate(STEP);
    physics.update(STEP);
    script.input.endFrame();
    for (let i = 0; i < 10; i++) {
      script.onUpdate(STEP);
      physics.update(STEP);
    }
    expect(hero.getWorldPosition().y, "jumped").toBeGreaterThan(start.y + 20);
    expect(String(label["text"])).toContain("In the air");
  });
});
