import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { parseTerrainFile, type TerrainData } from "../src/terrain/terrain";
import { applyDelta, heightAt, terrainToJson, TerrainStroke, type Brush, type BrushType } from "../src/terrain/terrainStroke";
import { CONTRACT_DIR } from "./projectFiles";

const DIR = join(CONTRACT_DIR, "terrain-trace");

interface Strokes {
  resolution: number;
  cellSize: number;
  toleranceHeight: number;
  strokes: { type: BrushType; radius: number; strength: number; inverted?: boolean; surface?: number; path: [number, number][] }[];
}

function flat(resolution: number, cellSize: number): TerrainData {
  return { name: "Terrain Trace", resolutionX: resolution, resolutionZ: resolution, cellSize, heights: new Float32Array(resolution * resolution),
    surfaces: null, layers: null, holes: null, waterLevel: null };
}

function play(trace: Strokes, terrain: TerrainData) {
  const deltas = [];
  for (const stroke of trace.strokes) {
    const brush: Brush = { type: stroke.type, radius: stroke.radius, strength: stroke.strength, inverted: stroke.inverted ?? false,
      surface: stroke.surface ?? 1 };
    const onTerrain = new TerrainStroke(terrain);
    const [first, ...rest] = stroke.path;
    onTerrain.begin(brush, first![0], first![1]);
    for (const [x, z] of rest) onTerrain.move(x, z);
    deltas.push(onTerrain.end());
  }
  return deltas;
}

describe("terrain strokes on the web (roadmap A14)", () => {
  const trace = JSON.parse(readFileSync(join(DIR, "strokes.json"), "utf8")) as Strokes;

  it("gives the terrain the desktop's TerrainStroke gives for the shared strokes", () => {
    const expected = parseTerrainFile(readFileSync(join(DIR, "expected.terrain"), "utf8"));
    const terrain = flat(trace.resolution, trace.cellSize);
    play(trace, terrain);

    let worst = 0;
    terrain.heights.forEach((h, i) => (worst = Math.max(worst, Math.abs(h - expected.heights[i]!))));
    expect(worst, `largest height difference ${worst}`).toBeLessThanOrEqual(trace.toleranceHeight);
    expect(Math.max(...expected.heights), "the strokes really moved the ground").toBeGreaterThan(10);
    let layerDifference = 0;
    terrain.layers!.forEach((w, i) => (layerDifference = Math.max(layerDifference, Math.abs(w - expected.layers![i]!))));
    expect(layerDifference).toBeLessThanOrEqual(1);
    expect([...terrain.holes!]).toEqual([...expected.holes!]);
    expect([...expected.holes!].filter((h) => h === 1).length).toBeGreaterThan(0);
  });

  it("undoes and redoes every stroke exactly, and writes the desktop's file layout", () => {
    const terrain = flat(trace.resolution, trace.cellSize);
    const deltas = play(trace, terrain);
    const after = terrainToJson(terrain);
    for (const delta of [...deltas].reverse()) applyDelta(terrain, delta, false);
    expect(terrain.heights.every((h) => h === 0)).toBe(true);
    expect(terrain.holes!.every((h) => h === 0)).toBe(true);
    expect(terrainToJson(terrain)).not.toContain("\"holes\"");
    for (const delta of deltas) applyDelta(terrain, delta, true);
    expect(terrainToJson(terrain)).toBe(after);

    // The same layout and number spelling as the desktop's TerrainIO.toJson: the file reads back as it was written
    const expectedText = readFileSync(join(DIR, "expected.terrain"), "utf8").replace(/\r\n/g, "\n");
    expect(after.split("\n").map((line) => line.split(":")[0])).toEqual(expectedText.split("\n").map((line) => line.split(":")[0]));
    expect(after).toContain('"cellSize": 50.0,');
    const reread = parseTerrainFile(after);
    expect([...reread.heights]).toEqual([...terrain.heights]);
  });

  it("refuses a stroke that starts off the terrain", () => {
    const terrain = flat(5, 10);
    expect(Number.isNaN(heightAt(terrain, 100, 0))).toBe(true);
    expect(() => new TerrainStroke(terrain).begin({ type: "RAISE", radius: 10, strength: 0.5, inverted: false, surface: 1 }, 100, 0))
      .toThrow(/must begin on the terrain/);
  });
});
