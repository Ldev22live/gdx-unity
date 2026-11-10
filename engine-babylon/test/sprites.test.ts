import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { Physics2D } from "../src/runtime/Physics2D";
import { buildScene } from "../src/runtime/SceneBuilder";
import { loadPendingSprites } from "../src/runtime/SpriteLoader";
import {
  buildAtlas,
  buildSprite,
  frameAt,
  frameSequence,
  frameUv,
  parseSpriteFile,
  parseTilemap,
  solidBoxes,
  SpriteAnimationState,
  tileLayerGeometry,
  type SpriteTag,
  type TileMapData,
} from "../src/sprites/sprite";
import { decodePngWithZlib } from "./pngSupport";
import { createHeadlessEngine, loadSample } from "./support";

const SPRITES_DIR = join(import.meta.dirname, "..", "..", "assets", "sprites");
const read = (path: string): Promise<string> => Promise.resolve(readFileSync(join(SPRITES_DIR, path.replace(/^sprites\//, "")), "utf8"));

async function loadSprite(name: string) {
  return buildSprite(parseSpriteFile(readFileSync(join(SPRITES_DIR, name), "utf8")), decodePngWithZlib);
}

const tag = (name: string, from: number, to: number, direction: SpriteTag["direction"] = "FORWARD"): SpriteTag => ({ name, from, to, direction });

function animation(): SpriteAnimationState {
  return new SpriteAnimationState({ width: 1, height: 1, durations: [100, 100, 100, 100, 100], tags: [tag("idle", 0, 1), tag("walk", 2, 4)], frames: [0, 1, 2, 3, 4].map(() => new Uint8Array(0)) });
}

describe("the bundled sprites on the web", () => {
  it("shouldReadTheHeroFileAndFlattenItsFramesLikeTheDesktop", async () => {
    const hero = await loadSprite("hero.sprite");

    expect(hero.width).toBe(16);
    expect(hero.frames).toHaveLength(6);
    expect(hero.tags.map((t) => t.name)).toEqual(["idle", "walk"]);
    expect(hero.durations).toEqual([400, 400, 120, 120, 120, 120]);
    const outline = [0x1d, 0x2b, 0x53, 0xff];
    // The head's outline starts at x = 5, y = 1 in frame 0 (see BundledSpritesTest on the desktop)
    expect([...hero.frames[0]!.subarray((1 * 16 + 5) * 4, (1 * 16 + 5) * 4 + 4)]).toEqual(outline);
    expect([...hero.frames[0]!.subarray(0, 4)]).toEqual([0, 0, 0, 0]);
    expect(hero.frames[2]).not.toEqual(hero.frames[3]);
  });

  it("shouldReadTheTileSetWithSeventeenTiles", async () => {
    const tiles = await loadSprite("tiles.sprite");

    expect(tiles.frames).toHaveLength(17);
    expect([...tiles.frames[15]!.subarray(0, 4)]).toEqual([0x7e, 0x5a, 0x3c, 0xff]);
    expect([...tiles.frames[0]!.subarray(8 * 4, 8 * 4 + 4)]).toEqual([0x3c, 0xb4, 0x4b, 0xff]);
  });

  it("shouldRefuseFilesThatAreNotSprites", () => {
    expect(() => parseSpriteFile('{"kind":"graph"}')).toThrow(/Not a sprite file/);
    expect(() => parseSpriteFile('{"kind":"sprite","schemaVersion":2}')).toThrow(/schemaVersion 2/);
    expect(() => parseSpriteFile('{"kind":"sprite","schemaVersion":1,"width":1,"height":1,"layers":[],"frames":[]}')).toThrow(/at least one layer/);
  });
});

describe("sprite playback", () => {
  it("shouldGiveTheSameSequencesAndFramesAsTheDesktop", () => {
    expect(frameSequence(4, tag("a", 1, 3))).toEqual([1, 2, 3]);
    expect(frameSequence(4, tag("a", 1, 3, "REVERSE"))).toEqual([3, 2, 1]);
    expect(frameSequence(4, tag("a", 0, 3, "PING_PONG"))).toEqual([0, 1, 2, 3, 2, 1]);
    expect(frameSequence(4, null)).toEqual([0, 1, 2, 3]);
    expect(frameAt([100, 300, 100], 3, null, 399, true)).toBe(1);
    expect(frameAt([100, 300, 100], 3, null, 400, true)).toBe(2);
    expect(frameAt([100, 300, 100], 3, null, 500, true)).toBe(0);
    expect(frameAt([100, 300, 100], 3, null, 9999, false)).toBe(2);
  });

  it("shouldPlayATagFromItsStartAndKeepPlayingItWhenAskedAgain", () => {
    const state = animation();
    state.play("walk");
    state.advance(0.15);
    expect(state.frame).toBe(3);
    state.play("walk");
    expect(state.frame).toBe(3);
    state.play("walk", true);
    expect(state.frame).toBe(2);
    state.play("idle");
    expect(state.frame).toBe(0);
  });

  it("shouldRefuseAnUnknownTagNamingTheKnownOnes", () => {
    expect(() => animation().play("run")).toThrow(/idle, walk/);
  });

  it("shouldApplySpeedLoopPauseAndStop", () => {
    const state = animation();
    state.speed = 2;
    state.advance(0.1);
    expect(state.frame).toBe(2);
    state.speed = 0;
    state.advance(10);
    expect(state.frame).toBe(2);
    expect(() => {
      state.speed = -1;
    }).toThrow();

    const once = animation();
    once.loop = false;
    once.play("idle", true);
    once.advance(5);
    expect(once.finished).toBe(true);
    expect(once.frame).toBe(1);

    const paused = animation();
    paused.advance(0.15);
    paused.pause();
    paused.advance(1);
    expect(paused.frame).toBe(1);
    paused.stop();
    expect(paused.frame).toBe(0);
  });
});

describe("the atlas", () => {
  it("shouldLayFramesInAGridAndGiveTextureCoordinatesWithTheTopRowHighest", async () => {
    const hero = await loadSprite("hero.sprite");
    const atlas = buildAtlas(hero);

    expect(atlas.columns).toBe(3);
    expect(atlas.width).toBe(48);
    expect(atlas.height).toBe(32);
    // Frame 4 is in the second row, second column
    const uv = frameUv(atlas, 4);
    expect(uv.u0).toBeCloseTo(16 / 48, 2);
    expect(uv.u1).toBeCloseTo(32 / 48, 2);
    expect(uv.v1).toBeCloseTo(1 - 16 / 32, 2);
    expect(uv.v0).toBeCloseTo(1 - 32 / 32, 2);
    expect(uv.u0, "pulled in from the frame's edge").toBeGreaterThan(16 / 48);
    expect(uv.u1).toBeLessThan(32 / 48);
    const at = ((16 + 1) * 48 + 16 + 5) * 4;
    expect([...atlas.data.subarray(at, at + 4)]).toEqual([...hero.frames[4]!.subarray((1 * 16 + 5) * 4, (1 * 16 + 5) * 4 + 4)]);
  });
});

function map(cols: number, rows: number, layers: number[][], solid: number[]): TileMapData {
  return parseTilemap({ cols, rows, solid, layers: layers.map((cells, i) => ({ name: `L${i}`, visible: true, cells })), tileSet: "sprites/tiles.sprite" });
}

describe("tile maps", () => {
  it("shouldMergeSolidCellsLikeTheDesktop", () => {
    const cells = new Array<number>(15).fill(0);
    for (let c = 0; c < 5; c++) cells[2 * 5 + c] = 1;
    for (const [c, r] of [[1, 0], [2, 0], [1, 1], [2, 1]] as const) cells[r * 5 + c] = 1;

    const boxes = solidBoxes(map(5, 3, [cells], [1]));

    expect(new Set(boxes.map((b) => `${b.col},${b.row},${b.cols},${b.rows}`))).toEqual(new Set(["1,0,2,3", "0,2,1,1", "3,2,2,1"]));
  });

  it("shouldIgnoreHiddenLayersAndNonSolidTiles", () => {
    const tiles = parseTilemap({
      cols: 3, rows: 1, solid: [1],
      layers: [{ name: "A", visible: true, cells: [1, 2, 0] }, { name: "B", visible: false, cells: [0, 0, 1] }],
    });

    expect(solidBoxes(tiles)).toEqual([{ col: 0, row: 0, cols: 1, rows: 1 }]);
  });

  it("shouldBuildQuadsOnlyForNonEmptyCellsWithTheMapsBottomLeftAtTheOrigin", async () => {
    const tiles = await loadSprite("tiles.sprite");
    const atlas = buildAtlas(tiles);
    const data = parseTilemap({ cols: 2, rows: 2, tileWidth: 10, tileHeight: 10, layers: [{ name: "A", visible: true, cells: [0, 3, 0, 0] }] });

    const geometry = tileLayerGeometry(data, data.layers[0]!, atlas, tiles.frames.length);

    expect(geometry.indices).toHaveLength(6);
    expect(geometry.positions.slice(0, 3)).toEqual([10, 10, 0]);
    expect(geometry.positions.slice(6, 9)).toEqual([20, 20, 0]);
  });

  it("shouldRefuseALayerThatDoesNotMatchTheMapSize", () => {
    expect(() => parseTilemap({ cols: 2, rows: 2, layers: [{ name: "A", cells: [1, 2, 3] }] })).toThrow(/3 cells/);
  });
});

describe("sprites in a played scene", () => {
  it("shouldLoadTheSpritesOfTheSampleSceneShowThemAndAnimateThem", async () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("sprite-animation-2d.json"));
    expect(built.pendingSprites).toHaveLength(3);
    expect(built.pendingSprites[0]!.binding.loaded).toBe(false);
    expect(built.pendingSprites[0]!.binding.mesh.isEnabled()).toBe(false);

    const failures = await loadPendingSprites(built, decodePngWithZlib, (p) => p, read);

    expect(failures).toEqual([]);
    const [idle, walking, flipped] = built.pendingSprites.map((p) => p.binding);
    expect(idle!.loaded && walking!.loaded && flipped!.loaded).toBe(true);
    expect(idle!.mesh.scaling.x).toBe(128);
    expect(flipped!.mesh.scaling.x).toBe(-128);
    expect(idle!.currentTag).toBe("idle");
    expect(walking!.frame).toBe(2);
    walking!.update(0.13);
    expect(walking!.frame).toBe(3);
    flipped!.update(0.07);
    expect(flipped!.frame, "speed 2 makes 0.07 s last 140 ms, past the first 120 ms frame").toBe(3);
    built.scene.dispose();
    engine.dispose();
  });

  it("shouldLetAScriptChangeTheTagThroughTheEntity", async () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("sprite-animation-2d.json"));
    const hero = built.game.entities.find((e) => e.name === "Idle Hero")!;
    hero.sprite!.play("walk");
    await loadPendingSprites(built, decodePngWithZlib, (p) => p, read);

    expect(hero.sprite!.currentTag).toBe("walk");
    expect(hero.sprite!.frame).toBe(2);
    expect(() => hero.sprite!.play("fly")).toThrow(/idle, walk/);
    built.scene.dispose();
    engine.dispose();
  });

  it("shouldReportASpriteFileThatIsMissingAndStillShowTheRest", async () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("sprite-animation-2d.json"));

    const failures = await loadPendingSprites(built, decodePngWithZlib, (p) => p, () => Promise.reject(new Error("404 Not Found")));

    expect(failures).toHaveLength(3);
    expect(failures[0]!.message).toContain("404");
    expect(built.pendingSprites[0]!.binding.loadError).toContain("sprites/hero.sprite");
    expect(() => built.pendingSprites[0]!.binding.frame).toThrow(/Cannot load/);
    built.scene.dispose();
    engine.dispose();
  });

  it("shouldBuildAMeshForEachVisibleLayerOfTheTileMapAndLetHeroesLandOnItsPlatforms", async () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("tilemap-platformer-2d.json"));
    expect(built.pendingTilemaps).toHaveLength(1);

    await loadPendingSprites(built, decodePngWithZlib, (p) => p, read);

    const level = built.game.entities.find((e) => e.name === "Level")!;
    expect(level.meshes).toHaveLength(2);
    const physics = new Physics2D(built.game);
    for (let i = 0; i < 300; i++) physics.update(1 / 60);
    const idle = built.game.entities.find((e) => e.name === "Idle Hero")!.getWorldPosition().y;
    const walking = built.game.entities.find((e) => e.name === "Walking Hero")!.getWorldPosition().y;
    expect(Math.abs(idle - 190), `idle hero y = ${idle}`).toBeLessThan(3);
    expect(Math.abs(walking - 254), `walking hero y = ${walking}`).toBeLessThan(3);
    built.scene.dispose();
    engine.dispose();
  });
});
