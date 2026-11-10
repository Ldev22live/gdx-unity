import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { figureScene } from "../src/editor/animations/animationsTab";
import { applyClip, parseClip, resolveTarget, type Clip } from "../src/runtime/Animation";
import type { GameEntity } from "../src/runtime/GameEntity";
import { buildScene } from "../src/runtime/SceneBuilder";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

const ANIMATIONS_DIR = join(SAMPLES_DIR, "..", "animations");
const CLIPS = ["Idle", "Walk", "Run", "Jump", "Fall"];
const FOOT_R = "Hips/Thigh R/Shin R/Foot R";
const FOOT_L = "Hips/Thigh L/Shin L/Foot L";

function clip(name: string): Clip {
  return parseClip(readFileSync(join(ANIMATIONS_DIR, "humanoid", `${name}.anim`), "utf8"));
}

/** The showcase scene as the web player builds it, with the hero of each clip. */
function heroes(): Map<string, GameEntity> {
  const engine = createHeadlessEngine();
  const built = buildScene(engine, loadSample("humanoid-animations-3d.json"));
  const result = new Map<string, GameEntity>();
  for (const name of CLIPS) result.set(name, built.game.findByName(`${name} Hero`)!);
  return result;
}

function worldY(hero: GameEntity, path: string): number {
  return resolveTarget(hero, path)!.getWorldPosition().y;
}

function worldZ(hero: GameEntity, path: string): number {
  return resolveTarget(hero, path)!.getWorldPosition().z;
}

describe("humanoid clips in the web player", () => {
  const figures = heroes();

  it("shouldFindEveryBoneOfEveryClipOnTheSkeleton", () => {
    for (const name of CLIPS) {
      const data = clip(name);
      expect(data.tracks.length, name).toBeGreaterThanOrEqual(8);
      for (const track of data.tracks) expect(resolveTarget(figures.get(name)!, track.target), `${name}: ${track.target}`).not.toBeNull();
      expect(applyClip(data, 0, figures.get(name)!), name).toBe(data.tracks.length);
    }
  });

  it("shouldLiftTheHipsOffTheGroundWhenJumpingAndLandStanding", () => {
    const hero = figures.get("Jump")!;
    const jump = clip("Jump");
    const at = (time: number): number => {
      applyClip(jump, time, hero);
      return worldY(hero, "Hips");
    };

    const standing = at(0);
    expect(at(0.2)).toBeLessThan(standing - 20);
    expect(at(0.55)).toBeGreaterThan(standing + 60);
    expect(at(1)).toBeCloseTo(standing, 3);
    applyClip(jump, 0.55, hero);
    expect(Math.min(worldY(hero, FOOT_R), worldY(hero, FOOT_L))).toBeGreaterThan(40);
  });

  it("shouldSwingTheLegsWiderAndFasterWhenRunningThanWalking", () => {
    const stride = (name: string): number => {
      const hero = figures.get(name)!;
      const data = clip(name);
      let widest = 0;
      for (let i = 0; i <= 20; i++) {
        applyClip(data, (data.duration * i) / 20, hero);
        widest = Math.max(widest, Math.abs(worldZ(hero, FOOT_R) - worldZ(hero, FOOT_L)));
      }
      return widest;
    };

    expect(clip("Run").duration).toBeLessThan(clip("Walk").duration);
    expect(stride("Run")).toBeGreaterThan(stride("Walk"));
  });

  it("shouldThrowTheArmsUpWhenFalling", () => {
    const hero = figures.get("Fall")!;
    applyClip(clip("Fall"), 0, hero);

    expect(worldY(hero, "Hips/Spine/Chest/Arm R/Forearm R")).toBeGreaterThan(worldY(hero, "Hips/Spine/Chest"));
  });

  it("shouldLoopEveryClipSeamlessly", () => {
    for (const name of CLIPS) {
      const hero = figures.get(name)!;
      const data = clip(name);
      applyClip(data, 0, hero);
      const start = resolveTarget(hero, FOOT_R)!.getWorldPosition().clone();
      applyClip(data, data.duration, hero);
      const end = resolveTarget(hero, FOOT_R)!.getWorldPosition();
      expect(end.subtract(start).length(), name).toBeLessThan(0.01);
    }
  });
});

describe("figureScene", () => {
  it("shouldKeepOneHeroAtTheOriginWithoutItsAnimatorAndTheLightsAndFloor", () => {
    const figure = figureScene(loadSample("humanoid-animations-3d.json"));
    const names = figure.entities.map((entity) => entity.name);
    const hero = figure.entities.find((entity) => entity.name === "Idle Hero")!;

    expect(names.filter((name) => name.endsWith(" Hero"))).toEqual(["Idle Hero"]);
    expect(names).toEqual(expect.arrayContaining(["Sun", "Sky Light", "Floor", "Main Camera", "Hips", "Head"]));
    expect(hero.transform.position.z).toBe(0);
    expect(hero.components).toEqual([]);
    // Every kept entity's parent is kept too, so the scene builds
    const ids = new Set(figure.entities.map((entity) => entity.id));
    expect(figure.entities.every((entity) => entity.parentId === null || ids.has(entity.parentId))).toBe(true);
    expect(() => buildScene(createHeadlessEngine(), figure)).not.toThrow();
  });

  it("shouldRefuseASceneWithoutTheFigure", () => {
    expect(() => figureScene({ ...loadSample("platformer-2d.json") })).toThrow(/Idle Hero/);
  });
});
