import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { Input } from "../src/runtime/Input";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { loadPendingSprites } from "../src/runtime/SpriteLoader";
import { createDefaultRegistry } from "../src/scripts/registry";
import { str } from "../src/scene/types";
import { decodePngWithZlib } from "./pngSupport";
import { createHeadlessEngine, loadSample } from "./support";

const SPRITES_DIR = join(import.meta.dirname, "..", "..", "assets", "sprites");
const read = (path: string): Promise<string> => Promise.resolve(readFileSync(join(SPRITES_DIR, path.replace(/^sprites\//, "")), "utf8"));
const FRAME = 1 / 60;

async function start(file: string) {
  const engine = createHeadlessEngine();
  const built: BuiltScene = buildScene(engine, loadSample(file));
  await loadPendingSprites(built, decodePngWithZlib, (p) => p, read);
  const input = new Input();
  const registry = createDefaultRegistry();
  const scripts: ReturnType<typeof registry.create>[] = [];
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
  const hero = built.game.findByName("Player")!;
  const step = (frames: number): void => {
    for (let i = 0; i < frames; i++) {
      scripts.forEach((script) => script.onUpdate(FRAME));
      for (const sprite of built.sprites) sprite.update(FRAME);
      input.endFrame();
    }
  };
  return { engine, built, input, hero, step };
}

describe("the PixelHero script on the web", () => {
  it("shouldFallOntoTheFloorAndStandThereIdle", async () => {
    const { engine, hero, step } = await start("pixel-adventure-2d.json");

    step(120);

    const p = hero.getWorldPosition();
    // The floor's top is at y = 64 and the body is 56 tall
    expect(p.y).toBeGreaterThan(64 + 28 - 1);
    expect(p.y).toBeLessThan(64 + 28 + 2);
    expect(hero.sprite!.currentTag).toBe("idle");
    engine.dispose();
  });

  it("shouldRunRightPlayWalkTurnLeftAndFlipTheSprite", async () => {
    const { engine, hero, input, step } = await start("pixel-adventure-2d.json");
    step(90);
    const x0 = hero.getWorldPosition().x;

    input.press("KeyD");
    step(60);
    expect(hero.getWorldPosition().x).toBeGreaterThan(x0 + 150);
    expect(hero.sprite!.currentTag).toBe("walk");
    expect(hero.sprite!.mesh.scaling.x).toBeGreaterThan(0);

    input.release("KeyD");
    input.press("KeyA");
    step(30);
    expect(hero.sprite!.mesh.scaling.x, "facing left").toBeLessThan(0);
    input.release("KeyA");
    step(5);
    expect(hero.sprite!.currentTag).toBe("idle");
    engine.dispose();
  });

  it("shouldJumpOnlyFromTheGroundAndLandAgain", async () => {
    const { engine, hero, input, step } = await start("pixel-adventure-2d.json");
    step(90);
    const ground = hero.getWorldPosition().y;

    input.press("Space");
    step(1);
    input.release("Space");
    step(20);
    expect(hero.getWorldPosition().y).toBeGreaterThan(ground + 60);

    input.press("Space");
    step(1);
    input.release("Space");
    const airborne = hero.getWorldPosition().y;
    step(60);
    expect(hero.getWorldPosition().y, "back on the floor, no double jump").toBeCloseTo(ground, 0);
    expect(airborne).toBeGreaterThan(ground);
    engine.dispose();
  });

  it("shouldCollectACoinWhenTheHeroTouchesItAndCountItInTheText", async () => {
    const { engine, built, hero, step } = await start("pixel-adventure-2d.json");
    const coin = built.game.findByName("Coin 6")!;
    const text = built.game.findByName("Score Text")!.getComponent("TextComponent")!;
    expect(text.data["text"]).toBe("Coins: 0 / 6");
    step(90);

    // The script owns the hero's position, so the coin is brought to the hero
    const at = hero.getWorldPosition();
    coin.setLocalPosition(at.x, at.y, 0);
    step(2);

    expect(coin.isActiveSelf).toBe(false);
    expect(text.data["text"]).toContain("Coins: 1 / 6");
    engine.dispose();
  });

  it("shouldFallInAPitAndStartOverAndStartOverAgainOnR", async () => {
    const { engine, hero, input, step } = await start("pixel-adventure-2d.json");
    step(90);
    const home = hero.getWorldPosition();

    // Run right: the first pit is at columns 14 to 16 (x 448 to 544); the hero falls in, passes the fall limit and starts again
    input.press("KeyD");
    let lowest = Infinity;
    let respawned = false;
    for (let i = 0; i < 600 && !respawned; i++) {
      step(1);
      const p = hero.getWorldPosition();
      lowest = Math.min(lowest, p.y);
      if (lowest < 0 && p.x < home.x + 1) respawned = true;
    }
    input.release("KeyD");
    expect(lowest, "the hero dropped below the floor").toBeLessThan(0);
    expect(respawned, "and came back to the start").toBe(true);

    input.press("KeyD");
    step(30);
    input.release("KeyD");
    expect(hero.getWorldPosition().x).toBeGreaterThan(home.x + 50);
    input.press("KeyR");
    step(1);
    input.release("KeyR");
    expect(hero.getWorldPosition().x).toBeCloseTo(home.x, 0);
    engine.dispose();
  });

  it("shouldRunTheSameScriptInTheSideScrollerWithAPerspectiveCamera", async () => {
    const { engine, built, hero, input, step } = await start("sprite-25d.json");

    input.press("KeyD");
    step(120);

    expect(hero.getWorldPosition().x).toBeGreaterThan(300);
    expect(built.gameCamera!.orthographic).toBe(false);
    expect(hero.sprite!.currentTag).toBe("walk");
    engine.dispose();
  });
});
