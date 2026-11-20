import { Vector3 } from "@babylonjs/core";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { loadPendingTerrains } from "../src/runtime/TerrainLoader";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { MeshColliderGeometry } from "../src/runtime/physics3d/ColliderGeometry";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import { LAYER_COUNT, WATER_COLOR, parseTerrainFile, surfaceColor, terrainGeometry, terrainSize } from "../src/terrain/terrain";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

/** Roadmap M3.11: the web player reads the desktop's `.terrain` files, draws them as the desktop does, and stands characters on them. */

const TERRAINS_DIR = join(SAMPLES_DIR, "..", "terrains");
const valleyText = readFileSync(join(TERRAINS_DIR, "Valley.terrain"), "utf8");
/** Reads a project file the way the browser would fetch it from public/. */
const fromAssets = async (path: string): Promise<string> => readFileSync(join(SAMPLES_DIR, "..", path), "utf8");

function base64(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString("base64");
}

/** A 3 by 3 terrain, flat at height 10 unless {@code heights} says otherwise, with optional extra file fields. */
function smallTerrain(extra: Record<string, unknown> = {}, heights: number[] = new Array(9).fill(10)): string {
  const data = new Float32Array(heights);
  return JSON.stringify({
    version: 1,
    name: "Small",
    resolutionX: 3,
    resolutionZ: 3,
    cellSize: 50,
    heights: base64(new Uint8Array(data.buffer)),
    ...extra,
  });
}

describe("parseTerrainFile", () => {
  it("shouldReadTheShippedValleyLikeTheDesktop", () => {
    const valley = parseTerrainFile(valleyText);
    expect(valley.name).toBe("Valley");
    expect(valley.resolutionX).toBe(129);
    expect(valley.resolutionZ).toBe(129);
    expect(valley.cellSize).toBe(40);
    expect(valley.heights.length).toBe(129 * 129);
    const low = Math.min(...valley.heights);
    const high = Math.max(...valley.heights);
    expect(low).toBeGreaterThan(-25);
    expect(high).toBeLessThan(450);
    expect(valley.surfaces).toBeNull();
    expect(valley.waterLevel).toBeNull();
    expect(terrainSize(valley)).toEqual({ x: 5120, z: 5120 });
  });

  it("shouldReadLayersHolesAndWaterAndFillMissingLayers", () => {
    const weights = new Uint8Array(9 * 2);
    weights[4 * 2 + 1] = 200; // the middle point, layer 1 (Sand), in a file with only two layers
    const holes = new Uint8Array(9);
    holes[0] = 1;
    const terrain = parseTerrainFile(smallTerrain({ layerCount: 2, layers: base64(weights), holes: base64(holes), waterLevel: 12.5 }));
    expect(terrain.layers!.length).toBe(9 * LAYER_COUNT);
    expect(terrain.layers![4 * LAYER_COUNT + 1]).toBe(200);
    expect(terrain.layers![4 * LAYER_COUNT + 5]).toBe(0);
    expect(terrain.holes![0]).toBe(1);
    expect(terrain.waterLevel).toBe(12.5);
  });

  it("shouldRefuseFilesTheDesktopRefuses", () => {
    expect(() => parseTerrainFile("not json")).toThrow(/Not a terrain file/);
    expect(() => parseTerrainFile(smallTerrain({ version: 2 }))).toThrow(/version 2 is not supported/);
    expect(() => parseTerrainFile(smallTerrain({ resolutionX: 1 }))).toThrow(/resolutionX must be 2 to 1025/);
    expect(() => parseTerrainFile(smallTerrain({ heights: base64(new Uint8Array(8)) }))).toThrow(/heights hold 8 bytes/);
    expect(() => parseTerrainFile(smallTerrain({ surfaces: base64(new Uint8Array(9).fill(9)) }))).toThrow(/Unknown surface code 9/);
    const overFull = new Uint8Array(9 * 2).fill(200);
    expect(() => parseTerrainFile(smallTerrain({ layerCount: 2, layers: base64(overFull) }))).toThrow(/sum to 400/);
    expect(() => parseTerrainFile(smallTerrain({ layerCount: 2 }))).toThrow(/layers is missing/);
    expect(() => parseTerrainFile(smallTerrain({ holes: base64(new Uint8Array(9).fill(2)) }))).toThrow(/0 or 1/);
  });
});

describe("terrainGeometry", () => {
  it("shouldPlaceOneVertexPerPointCentredOnTheEntityWithTwoUpFacingTrianglesPerCell", () => {
    const terrain = parseTerrainFile(smallTerrain());
    const geometry = terrainGeometry(terrain);
    expect(geometry.positions.length).toBe(9 * 3);
    expect(Array.from(geometry.positions.slice(0, 3))).toEqual([-50, 10, -50]);
    expect(Array.from(geometry.positions.slice(24, 27))).toEqual([50, 10, 50]);
    expect(geometry.indices.length).toBe(4 * 6);
    // The first triangle (a, c, b) faces +y: (c - a) x (b - a)
    const [a, c, b] = [0, 3, 1].map((i) => new Vector3(geometry.positions[i * 3], geometry.positions[i * 3 + 1], geometry.positions[i * 3 + 2]));
    expect(Vector3.Cross(c!.subtract(a!), b!.subtract(a!)).y).toBeGreaterThan(0);
    expect(Array.from(geometry.indices.slice(0, 3))).toEqual([0, 3, 1]);
    for (let p = 0; p < 9; p++) expect(geometry.normals[p * 3 + 1]).toBeCloseTo(1, 6);
  });

  it("shouldLeaveOutEveryCellTouchingAHole", () => {
    const holes = new Uint8Array(9);
    holes[4] = 1; // the middle point touches all four cells
    expect(terrainGeometry(parseTerrainFile(smallTerrain({ holes: base64(holes) }))).indices.length).toBe(0);
    holes.fill(0);
    holes[0] = 1; // a corner touches one cell
    expect(terrainGeometry(parseTerrainFile(smallTerrain({ holes: base64(holes) }))).indices.length).toBe(3 * 6);
  });

  it("shouldColourGroundWithTheDesktopsRulesInLinearLightThenSrgb", () => {
    // Flat Auto ground at the bottom of the range: grass
    const flat = terrainGeometry(parseTerrainFile(smallTerrain({}, [0, 0, 0, 0, 0, 0, 0, 0, 100])));
    expect(flat.colors[0]).toBeCloseTo(0.28, 4);
    expect(flat.colors[1]).toBeCloseTo(0.48, 4);
    expect(flat.colors[2]).toBeCloseTo(0.17, 4);
    // The top of the range on gentle ground is snow; the same formula as the desktop's TerrainMeshBuilder.color
    expect(surfaceColor(0, 1, 1)[0]).toBeCloseTo(Math.pow(0.92, 2.2), 6);
    // Steep ground turns to rock
    expect(surfaceColor(0, 0.5, 0)[0]).toBeCloseTo(Math.pow(0.46, 2.2), 6);
    // Painted sand keeps half its own colour on steep ground
    expect(surfaceColor(2, 0.5, 0)[0]).toBeCloseTo(Math.pow(0.80 + (0.46 - 0.80) * 0.5, 2.2), 6);
  });

  it("shouldBlendAPaintedLayerOverTheBaseByWeight", () => {
    const weights = new Uint8Array(9 * LAYER_COUNT);
    weights[0 * LAYER_COUNT + 1] = 255; // full sand at point 0
    weights[1 * LAYER_COUNT + 1] = 51; // a fifth sand at point 1
    const geometry = terrainGeometry(parseTerrainFile(smallTerrain({ layerCount: LAYER_COUNT, layers: base64(weights) })));
    expect(geometry.colors[0]).toBeCloseTo(0.80, 4);
    const grass = Math.pow(0.28, 2.2);
    const sand = Math.pow(0.80, 2.2);
    expect(geometry.colors[4]).toBeCloseTo(Math.pow(grass * 0.8 + sand * 0.2, 1 / 2.2), 4);
  });
});

describe("the web player's Terrain component", () => {
  it("shouldDrawTheTerrainSamplesGroundOnceItsFileHasLoaded", async () => {
    const built = buildScene(createHeadlessEngine(), loadSample("terrain-3d.json"));
    expect(built.unsupported.has("Terrain")).toBe(false);
    expect(built.pendingTerrains.map((pending) => pending.path)).toEqual(["terrains/Valley.terrain"]);

    const failures = await loadPendingTerrains(built, (path) => path, fromAssets);

    expect(failures).toEqual([]);
    const valley = built.game.findByName("Valley")!;
    const ground = valley.meshes.find((mesh) => (mesh.metadata as { terrain?: boolean }).terrain);
    expect(ground).toBeDefined();
    expect(ground!.getTotalVertices()).toBe(129 * 129);
    expect(ground!.getTotalIndices()).toBe(128 * 128 * 6);
    expect(ground!.parent).toBe(valley.node);
  });

  it("shouldDrawWaterAtTheFilesLevelAndReportAFileThatCannotBeRead", async () => {
    const built = buildScene(createHeadlessEngine(), loadSample("terrain-3d.json"));
    const withWater = async (): Promise<string> => smallTerrain({ waterLevel: 40 });
    await loadPendingTerrains(built, (path) => path, withWater);
    const water = built.game.findByName("Valley")!.meshes.find((mesh) => (mesh.metadata as { terrainWater?: boolean }).terrainWater);
    expect(water!.position.y).toBe(40);
    expect((water!.material as unknown as { alpha: number }).alpha).toBeCloseTo(WATER_COLOR.a, 6);

    const broken = buildScene(createHeadlessEngine(), loadSample("terrain-3d.json"));
    const failures = await loadPendingTerrains(broken, (path) => path, async () => "{}");
    expect(failures).toEqual([{ entityName: "Valley", path: "terrains/Valley.terrain", message: "Not a terrain file: it has no version" }]);
    expect(broken.terrainFailures).toEqual(failures);
  });
});

describe("a Terrain Collider 3D on the web (real Jolt)", () => {
  let jolt: JoltModule;
  const made: Physics3D[] = [];
  beforeAll(async () => {
    jolt = await loadJolt();
  });
  afterEach(() => {
    made.splice(0).forEach((physics) => physics.dispose());
  });

  async function valley(): Promise<{ built: BuiltScene; physics: Physics3D }> {
    const built = buildScene(createHeadlessEngine(), loadSample("terrain-3d.json"));
    await loadPendingTerrains(built, (path) => path, fromAssets);
    const physics = new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
    made.push(physics);
    return { built, physics };
  }

  it("shouldStandTheSamplesHeroOnTheValleyAndLetItWalkUphill", async () => {
    const { built, physics } = await valley();
    const hero = built.game.findByName("Hero")!;
    for (let i = 0; i < 120; i++) physics.update(1 / 60);

    expect(physics.getWarnings()).toEqual([]);
    expect(physics.isCharacterGrounded(hero)).toBe(true);
    const settled = hero.getWorldPosition();
    expect(settled.y, "standing on the valley floor, not fallen through").toBeGreaterThan(-60);

    // Walk sideways out of the valley: the ground rises, and the hero rises with it
    physics.moveCharacter(hero, new Vector3(250, 0, 0));
    for (let i = 0; i < 180; i++) physics.update(1 / 60);
    const walked = hero.getWorldPosition();
    expect(walked.x - settled.x).toBeGreaterThan(400);
    expect(walked.y - settled.y, "climbed the valley side").toBeGreaterThan(20);
  });

  it("shouldWaitForTheTerrainFileBeforeMakingTheGround", () => {
    const built = buildScene(createHeadlessEngine(), loadSample("terrain-3d.json"));
    const physics = new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
    made.push(physics);
    physics.update(1 / 60);
    expect(physics.getWarnings(), "pending, not refused").toEqual([]);
  });
});
