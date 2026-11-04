import { describe, expect, it } from "vitest";
import { Vector3 } from "@babylonjs/core";
import { Input } from "../src/runtime/Input";
import { Physics2D, FIXED_TIMESTEP } from "../src/runtime/Physics2D";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { createDefaultRegistry } from "../src/scripts/registry";
import { placeAtAnchor } from "../src/runtime/Hud";
import { str } from "../src/scene/types";
import { createHeadlessEngine, loadSample } from "./support";

/** Starts the scripts a scene binds, the way ScenePlayer.play does, without a canvas. */
function startScripts(built: BuiltScene, input: Input) {
  const registry = createDefaultRegistry();
  const scripts = [];
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
  return scripts;
}

describe("collector sample scripts", () => {
  it("shouldRollThePlayerWhenAKeyIsHeldAndStopAtTheArenaWall", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("collector-3d.json"));
    const input = new Input();
    const scripts = startScripts(built, input);
    const player = built.game.findByName("Player")!;
    const startX = player.getLocalPosition().x;

    input.press("KeyD");
    for (let i = 0; i < 60; i++) {
      scripts.forEach((script) => script.onUpdate(1 / 60));
      input.endFrame();
    }
    const afterOneSecond = player.getLocalPosition().x;
    expect(afterOneSecond).toBeGreaterThan(startX + 50);
    expect(afterOneSecond).toBeLessThanOrEqual(740);

    for (let i = 0; i < 600; i++) scripts.forEach((script) => script.onUpdate(1 / 60));
    expect(player.getLocalPosition().x).toBeLessThanOrEqual(740);
    engine.dispose();
  });

  it("shouldCollectAGemAndShowTheCountInTheText", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("collector-3d.json"));
    const scripts = startScripts(built, new Input());
    // Only the game rules run here: the player's own script would put the ball back at its start every frame
    const gameRules = scripts.filter((script) => script.constructor.name === "CollectorGame");
    expect(gameRules).toHaveLength(1);
    const gems = built.game.findByPrefix("Gem");
    const player = built.game.findByName("Player")!;
    const gemText = built.game.findByName("Collected Text")!.getComponent("TextComponent")!;
    expect(gems.length).toBeGreaterThan(0);
    expect(gemText.data["text"]).toBe(`Gems: 0 / ${gems.length}`);

    const home = gems[0]!.getLocalPosition();
    player.setLocalPosition(home.x, player.getLocalPosition().y, home.z);
    gameRules.forEach((script) => script.onUpdate(1 / 60));

    expect(gems[0]!.isActiveSelf).toBe(false);
    expect(gemText.data["text"]).toBe(`Gems: 1 / ${gems.length}`);
    engine.dispose();
  });

  it("shouldPutTheFollowCameraBehindThePlayerLookingAtIt", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("collector-3d.json"));
    startScripts(built, new Input());
    const camera = built.gameCamera!;
    camera.sync(16 / 9);
    camera.camera.getViewMatrix(true);

    const forward = camera.camera.getForwardRay(1).direction;
    const player = built.game.findByName("Player")!.getWorldPosition();
    const toTarget = new Vector3(player.x, player.y + 40, player.z).subtract(camera.camera.position).normalize();
    expect(Vector3.Dot(forward, toTarget)).toBeGreaterThan(0.999);
    engine.dispose();
  });
});

describe("Physics2D", () => {
  it("shouldLetADynamicBodyFallAndKeepStaticBodiesStill", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("physics-sandbox-2d.json"));
    const physics = new Physics2D(built.game);
    expect(physics.bodyCount).toBeGreaterThan(0);
    const ground = built.game.findByName("Ground")!;
    const groundStart = ground.getWorldPosition();
    // Starts high above the ground, so it has to fall
    const dynamic = built.game.findByName("Bouncy Block 1")!;
    const startY = dynamic.getWorldPosition().y;

    for (let i = 0; i < 30; i++) physics.update(FIXED_TIMESTEP);

    expect(dynamic.getWorldPosition().y).toBeLessThan(startY - 1);
    expect(ground.getWorldPosition().y).toBe(groundStart.y);
    engine.dispose();
  });

  it("shouldStopABodyOnTheGroundInsteadOfFallingThroughIt", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("physics-sandbox-2d.json"));
    const physics = new Physics2D(built.game);
    for (let i = 0; i < 60 * 8; i++) physics.update(FIXED_TIMESTEP);
    for (const entity of built.game.entities) {
      if (entity.getComponent("RigidBody2DComponent")?.data["bodyType"] === "DYNAMIC") {
        expect(entity.getWorldPosition().y, entity.name).toBeGreaterThan(0);
      }
    }
    engine.dispose();
  });
});

describe("placeAtAnchor", () => {
  function place(anchor: string, offsetX = 0, offsetY = 0): Record<string, string> {
    const fake = { style: {} as Record<string, string> };
    placeAtAnchor(fake as unknown as HTMLElement, anchor, offsetX, offsetY);
    return fake.style;
  }

  it("shouldAnchorToTheTopLeftCornerAndMoveUpWithAPositiveOffsetY", () => {
    expect(place("TOP_LEFT").left).toBe("12px");
    expect(place("TOP_LEFT").top).toBe("12px");
    expect(place("TOP_LEFT", 0, 5).top).toBe("7px");
  });

  it("shouldCentreTheTextHorizontallyForTheTopAnchor", () => {
    const style = place("TOP");
    expect(style.left).toBe("calc(50% + 0px)");
    expect(style.transform).toContain("translateX(-50%)");
  });

  it("shouldAnchorToTheBottomRightCorner", () => {
    const style = place("BOTTOM_RIGHT");
    expect(style.right).toBe("12px");
    expect(style.bottom).toBe("12px");
  });
});
