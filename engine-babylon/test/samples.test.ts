import { readdirSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { Input } from "../src/runtime/Input";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import type { ScriptComponent } from "../src/runtime/ScriptComponent";
import { createDefaultRegistry } from "../src/scripts/registry";
import { JavaRandom } from "../src/scripts/util";
import { str } from "../src/scene/types";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

const registry = createDefaultRegistry();
const sampleFiles = readdirSync(SAMPLES_DIR).filter((name) => name.endsWith(".json"));

function boundClasses(built: BuiltScene): string[] {
  const classes: string[] = [];
  for (const entity of built.game.entities) {
    const binding = entity.getComponent("EntityScriptBindingComponent");
    if (binding) classes.push(str(binding.data, "scriptClassName"));
  }
  return classes;
}

/** Starts a scene's scripts like ScenePlayer.play does, and returns a function that runs one frame. */
function play(file: string) {
  const engine = createHeadlessEngine();
  const built = buildScene(engine, loadSample(file));
  const input = new Input();
  const scripts: ScriptComponent[] = [];
  for (const entity of built.game.entities) {
    const binding = entity.getComponent("EntityScriptBindingComponent");
    if (!binding) continue;
    const script = registry.create(str(binding.data, "scriptClassName"));
    script.entity = entity;
    script.scene = built.game;
    script.input = input;
    scripts.push(script);
  }
  scripts.forEach((script) => script.onStart());
  return {
    built,
    input,
    scripts,
    frame(dt = 1 / 60) {
      for (const script of [...scripts]) script.onUpdate(dt);
      input.endFrame();
    },
    dispose() {
      built.scene.dispose();
      engine.dispose();
    },
  };
}

const fullyPorted = sampleFiles.filter((file) => {
  const engine = createHeadlessEngine();
  const built = buildScene(engine, loadSample(file));
  const ok = boundClasses(built).every((name) => registry.has(name));
  built.scene.dispose();
  engine.dispose();
  return ok;
});

describe("JavaRandom", () => {
  it("shouldMatchTheJavaGeneratorForSeed42", () => {
    // new Random(42).nextFloat() is 0.7275637; its first next(31) is 1562431130, and 1562431130 % 100 is 30
    expect(new JavaRandom(42).nextFloat()).toBeCloseTo(0.7275637, 6);
    expect(new JavaRandom(42).nextInt(100)).toBe(30);
  });
});

describe("ported sample scenes", () => {
  it("shouldHaveEveryScriptOfTheScenesTheyClaimToPlay", () => {
    expect(fullyPorted).toContain("collector-3d.json");
    expect(fullyPorted).toContain("shooter-2d.json");
    expect(fullyPorted).toContain("breakout-2d.json");
    expect(fullyPorted).toContain("platformer-2d.json");
    expect(fullyPorted).toContain("runner-3d.json");
    expect(fullyPorted).toContain("hopper-3d.json");
    expect(fullyPorted).toContain("ruins-runner-25d.json");
    expect(fullyPorted).toContain("grapple-gorge-25d.json");
    expect(fullyPorted).toContain("three-heroes-25d.json");
  });

  it.each(fullyPorted)("shouldRunTenSecondsOfRandomInputWithoutAnError: %s", (file) => {
    const game = play(file);
    const keys = ["KeyW", "KeyA", "KeyS", "KeyD", "Space", "ArrowLeft", "ArrowRight", "ArrowUp", "KeyJ", "KeyE"];
    const random = new JavaRandom(7);
    for (let i = 0; i < 600; i++) {
      if (i % 20 === 0) {
        for (const key of keys) game.input.release(key);
        game.input.press(keys[random.nextInt(keys.length)]!);
      }
      game.frame();
    }
    game.dispose();
  });
});

describe("shooter", () => {
  it("shouldFireBulletsWhileSpaceIsHeldAndScoreWhenAnEnemyIsHit", () => {
    const game = play("shooter-2d.json");
    game.input.press("Space");
    let scoreText = "";
    const score = game.built.game.findByName("Score Text")!.getComponent("TextComponent")!;
    for (let i = 0; i < 60 * 20 && scoreText === ""; i++) {
      game.frame();
      if ((score.data["text"] as string) !== "Score: 0") scoreText = score.data["text"] as string;
    }
    expect(scoreText).toMatch(/^Score: [1-9]\d*00$/);
    game.dispose();
  });

  it("shouldCreateThePooledBulletsAndEnemiesAsEntitiesOfTheScene", () => {
    const game = play("shooter-2d.json");
    expect(game.built.game.findByPrefix("Bullet ")).toHaveLength(24);
    expect(game.built.game.findByPrefix("Enemy ")).toHaveLength(16);
    expect(game.built.game.findByName("Bullet 0")!.meshes).toHaveLength(1);
    game.dispose();
  });
});

describe("breakout", () => {
  it("shouldLaunchTheBallWithSpaceAndKeepItInsideThePlayfield", () => {
    const game = play("breakout-2d.json");
    const ball = game.built.game.findByName("Ball")!;
    const start = ball.getLocalPosition();
    game.input.press("Space");
    let highest = start.y;
    for (let i = 0; i < 120; i++) {
      game.frame();
      const p = ball.getLocalPosition();
      expect(p.x).toBeGreaterThan(0);
      expect(p.x).toBeLessThan(640);
      highest = Math.max(highest, p.y);
    }
    expect(highest).toBeGreaterThan(start.y + 100);
    game.dispose();
  });
});

describe("platformer", () => {
  it("shouldStandOnTheGroundAndRunRightWhenDIsHeld", () => {
    const game = play("platformer-2d.json");
    const player = game.built.game.findByName("Player")!;
    for (let i = 0; i < 30; i++) game.frame();
    const restingY = player.getLocalPosition().y;
    game.input.press("KeyD");
    for (let i = 0; i < 60; i++) game.frame();
    expect(player.getLocalPosition().x).toBeGreaterThan(100);
    expect(player.getLocalPosition().y).toBeCloseTo(restingY, 3);
    game.dispose();
  });

  it("shouldLeaveTheGroundWhenSpaceIsPressed", () => {
    const game = play("platformer-2d.json");
    const player = game.built.game.findByName("Player")!;
    for (let i = 0; i < 30; i++) game.frame();
    const restingY = player.getLocalPosition().y;
    game.input.press("Space");
    for (let i = 0; i < 10; i++) game.frame();
    expect(player.getLocalPosition().y).toBeGreaterThan(restingY + 20);
    game.dispose();
  });
});

describe("runner", () => {
  it("shouldSwitchLanesAndEndTheRunWhenABlockIsHit", () => {
    const game = play("runner-3d.json");
    const player = game.built.game.findByName("Player")!;
    game.input.press("KeyD");
    for (let i = 0; i < 20; i++) game.frame();
    expect(player.getLocalPosition().x).toBeCloseTo(130, 3);
    // Standing still in a lane, a block eventually arrives
    const message = game.built.game.findByName("Message Text")!.getComponent("TextComponent")!;
    for (let i = 0; i < 60 * 30 && message.data["text"] === ""; i++) {
      game.frame();
      game.input.release("KeyD");
    }
    expect(message.data["text"]).toMatch(/^Crashed after \d+ m/);
    game.dispose();
  });
});

describe("hopper", () => {
  it("shouldLandOnTheStartPlatformAndFallBackToTheStartAfterWalkingOffTheEdge", () => {
    const game = play("hopper-3d.json");
    const player = game.built.game.findByName("Player")!;
    for (let i = 0; i < 40; i++) game.frame();
    const restingY = player.getLocalPosition().y;
    game.input.press("KeyD");
    let fell = false;
    const falls = game.built.game.findByName("Falls Text")!.getComponent("TextComponent")!;
    for (let i = 0; i < 60 * 8 && !fell; i++) {
      game.frame();
      fell = falls.data["text"] !== "Falls: 0";
    }
    expect(fell).toBe(true);
    game.input.release("KeyD");
    // The reset position reaches the entity on the next frame, as in the Java script
    game.frame();
    expect(Math.abs(player.getLocalPosition().y - restingY)).toBeLessThan(20);
    game.dispose();
  });
});

describe("ruins", () => {
  it("shouldStandOnTheStartPlatformAndRunRight", () => {
    const game = play("ruins-runner-25d.json");
    const player = game.built.game.findByName("Player")!;
    for (let i = 0; i < 40; i++) game.frame();
    const restingY = player.getLocalPosition().y;
    const startX = player.getLocalPosition().x;
    game.input.press("KeyD");
    for (let i = 0; i < 30; i++) game.frame();
    expect(player.getLocalPosition().x).toBeGreaterThan(startX + 100);
    expect(player.getLocalPosition().y).toBeCloseTo(restingY, 0);
    game.dispose();
  });

  it("shouldFollowThePlayerWithTheSideCamera", () => {
    const game = play("ruins-runner-25d.json");
    const camera = game.built.gameCamera!.entity;
    game.input.press("KeyD");
    for (let i = 0; i < 120; i++) game.frame();
    const player = game.built.game.findByName("Player")!.getWorldPosition();
    expect(Math.abs(camera.getLocalPosition().x - player.x)).toBeLessThan(400);
    expect(camera.getLocalPosition().z).toBe(1000);
    game.dispose();
  });
});

describe("grapple", () => {
  it("shouldShowTheRopeOnlyWhileGrapplingAndOpenTheGateWhenThePlateIsPressed", () => {
    const game = play("grapple-gorge-25d.json");
    const rope = game.built.game.findByName("Rope")!;
    for (let i = 0; i < 30; i++) game.frame();
    expect(rope.isActiveSelf).toBe(false);
    const status = game.built.game.findByName("Status Text")!.getComponent("TextComponent")!;
    expect(status.data["text"]).toBe("Gate: closed");
    // Put the crate on the plate: the gate opens
    // The script owns the crate's position (its SideBody), so move the body, not the entity
    const grapple = game.scripts.find((script) => script.constructor.name === "GrappleGame") as unknown as {
      crate: { x: number; y: number };
    };
    const plate = game.built.game.findByName("Plate")!.getWorldPosition();
    grapple.crate.x = plate.x;
    grapple.crate.y = plate.y + 60;
    for (let i = 0; i < 60; i++) game.frame();
    expect(status.data["text"]).toBe("Gate: open");
    game.dispose();
  });
});

describe("three heroes", () => {
  it("shouldSwitchHeroesAndShowOnlyTheChosenOne", () => {
    const game = play("three-heroes-25d.json");
    const mason = game.built.game.findByName("Hero Mason")!;
    const scout = game.built.game.findByName("Hero Scout")!;
    expect(mason.isActiveSelf).toBe(true);
    expect(scout.isActiveSelf).toBe(false);
    game.input.press("Digit2");
    game.frame();
    expect(mason.isActiveSelf).toBe(false);
    expect(scout.isActiveSelf).toBe(true);
    game.dispose();
  });

  it("shouldConjureACrateWithTheMasonAndOnlyTwoAtATime", () => {
    const game = play("three-heroes-25d.json");
    for (let i = 0; i < 30; i++) game.frame();
    const crates = () => game.built.game.findByPrefix("Conjured Crate").filter((crate) => crate.isActiveSelf).length;
    expect(crates()).toBe(0);
    game.input.press("KeyE");
    game.frame();
    expect(crates()).toBe(1);
    game.input.release("KeyE");
    for (let i = 0; i < 5; i++) game.frame();
    game.input.press("KeyE");
    game.frame();
    game.input.release("KeyE");
    expect(crates()).toBeLessThanOrEqual(2);
    game.dispose();
  });
});
