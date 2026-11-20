/**
 * The desktop's `.terrain` file (TD-082, TD-083, TD-089) read by the web player (roadmap M3.11), and the geometry it draws: the same grid,
 * normals, ground colours, paint layers, holes and water as the desktop's `TerrainMeshBuilder`. Pure data, no Babylon types, so it is
 * tested without an engine.
 */

/** The file version this player reads, the same as the desktop's `TerrainIO.VERSION`. */
export const TERRAIN_VERSION = 1;
/** Paint layers a point can carry: every surface but Auto (`Surface.LAYER_COUNT`). */
export const LAYER_COUNT = 6;
/** A full layer weight (`Terrain.FULL_WEIGHT`). */
export const FULL_WEIGHT = 255;
/** Grid points per side the desktop allows (`Terrain.MIN_RESOLUTION` and `MAX_RESOLUTION`). */
export const MIN_RESOLUTION = 2;
export const MAX_RESOLUTION = 1025;

/**
 * The surfaces by file code, with their sRGB colours (`Surface`): Auto, Grass, Sand, Snow, Gravel, Rock, Dirt. Layer {@code n} paints
 * surface {@code n + 1}.
 */
export const SURFACE_COLORS: readonly (readonly [number, number, number])[] = [
  [0.28, 0.48, 0.17],
  [0.30, 0.52, 0.18],
  [0.80, 0.71, 0.46],
  [0.92, 0.94, 0.97],
  [0.50, 0.48, 0.45],
  [0.46, 0.43, 0.40],
  [0.42, 0.31, 0.20],
];
const AUTO = 0;
const SAND = 2;
const GRAVEL = 4;

/** Steepness (normal y) where rock starts to show and where it is full (`TerrainMeshBuilder.ROCK_START`, `ROCK_FULL`). */
const ROCK_START = 0.85;
const ROCK_FULL = 0.65;
/** Share of the height range where Auto ground starts to turn to snow and where it is full (`SNOW_START`, `SNOW_FULL`). */
const SNOW_START = 0.84;
const SNOW_FULL = 0.96;
const GRASS: readonly number[] = [0.28, 0.48, 0.17];
const ROCK: readonly number[] = [0.46, 0.43, 0.40];
const SNOW: readonly number[] = [0.92, 0.94, 0.97];
/** Gamma of the desktop's sRGB-to-linear conversion; colours are blended in linear light, as there. */
const GAMMA = 2.2;
/** Water colour and opacity (`TerrainGpu.WATER_COLOR`, linear on the desktop), as sRGB for the web's materials. */
export const WATER_COLOR = { r: Math.pow(0.10, 1 / GAMMA), g: Math.pow(0.28, 1 / GAMMA), b: Math.pow(0.40, 1 / GAMMA), a: 0.72 };

/** One terrain as the file holds it. */
export interface TerrainData {
  name: string;
  resolutionX: number;
  resolutionZ: number;
  cellSize: number;
  /** Height of every grid point, row by row along x. */
  heights: Float32Array;
  /** Base surface code per point, or null for all Auto. */
  surfaces: Uint8Array | null;
  /** {@link LAYER_COUNT} weights per point, or null for none. */
  layers: Uint8Array | null;
  /** 1 for a hole per point, or null for none. */
  holes: Uint8Array | null;
  /** Height of the water, or null for no water. */
  waterLevel: number | null;
}

/** The triangles of a terrain, ready for a mesh: colours are sRGB with alpha. */
export interface TerrainGeometry {
  positions: Float32Array;
  normals: Float32Array;
  colors: Float32Array;
  indices: Uint32Array;
}

function decodeBase64(text: string): Uint8Array {
  const binary = atob(text);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function integer(root: Record<string, unknown>, key: string): number {
  const value = root[key];
  if (typeof value !== "number" || !Number.isInteger(value)) throw new Error(`${key} must be a whole number`);
  return value;
}

function bytesOf(root: Record<string, unknown>, key: string, expected: number): Uint8Array | null {
  const value = root[key];
  if (value === undefined) return null;
  if (typeof value !== "string") throw new Error(`${key} must be a Base64 string`);
  const bytes = decodeBase64(value);
  if (bytes.length !== expected) throw new Error(`${key} hold ${bytes.length} bytes but the grid needs ${expected}`);
  return bytes;
}

/**
 * Reads a `.terrain` file as strictly as the desktop's `TerrainIO.fromJson`: a newer version, a grid out of range, data of the wrong length,
 * an unknown surface, weights past full or a hole byte other than 0 or 1 is an error that names the problem.
 */
export function parseTerrainFile(text: string): TerrainData {
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch (error) {
    throw new Error(`Not a terrain file: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (typeof root !== "object" || root === null || Array.isArray(root)) throw new Error("Not a terrain file: expected a JSON object");
  const r = root as Record<string, unknown>;
  if (r.version !== TERRAIN_VERSION) {
    throw new Error(r.version === undefined ? "Not a terrain file: it has no version"
      : `Terrain file version ${String(r.version)} is not supported (this player reads version ${TERRAIN_VERSION})`);
  }
  const resolutionX = integer(r, "resolutionX");
  const resolutionZ = integer(r, "resolutionZ");
  for (const [axis, value] of [["resolutionX", resolutionX], ["resolutionZ", resolutionZ]] as const) {
    if (value < MIN_RESOLUTION || value > MAX_RESOLUTION) throw new Error(`${axis} must be ${MIN_RESOLUTION} to ${MAX_RESOLUTION}: ${value}`);
  }
  const cellSize = r.cellSize;
  if (typeof cellSize !== "number" || !(cellSize > 0) || !Number.isFinite(cellSize)) throw new Error(`cellSize must be a number above 0`);
  const points = resolutionX * resolutionZ;
  const heightBytes = bytesOf(r, "heights", points * 4);
  if (!heightBytes) throw new Error("heights must be a Base64 string");
  const view = new DataView(heightBytes.buffer, heightBytes.byteOffset, heightBytes.byteLength);
  const heights = new Float32Array(points);
  for (let i = 0; i < points; i++) {
    heights[i] = view.getFloat32(i * 4, true);
    if (!Number.isFinite(heights[i]!)) throw new Error(`height ${i} is not a finite number`);
  }
  const surfaces = bytesOf(r, "surfaces", points);
  if (surfaces) {
    for (const code of surfaces) if (code >= SURFACE_COLORS.length) throw new Error(`Unknown surface code ${code} (0 to ${SURFACE_COLORS.length - 1})`);
  }
  let layers: Uint8Array | null = null;
  if (r.layers !== undefined) {
    const count = integer(r, "layerCount");
    if (count < 1 || count > LAYER_COUNT) throw new Error(`layerCount must be 1 to ${LAYER_COUNT}: ${count}`);
    const stored = bytesOf(r, "layers", points * count)!;
    // An older file with fewer layers reads, the missing ones empty
    layers = new Uint8Array(points * LAYER_COUNT);
    for (let p = 0; p < points; p++) {
      let sum = 0;
      for (let layer = 0; layer < count; layer++) {
        const weight = stored[p * count + layer]!;
        layers[p * LAYER_COUNT + layer] = weight;
        sum += weight;
      }
      if (sum > FULL_WEIGHT) throw new Error(`the weights at point ${p} sum to ${sum}, past ${FULL_WEIGHT}`);
    }
  } else if (r.layerCount !== undefined) {
    throw new Error("layerCount is given but layers is missing");
  }
  const holes = bytesOf(r, "holes", points);
  if (holes) for (const hole of holes) if (hole > 1) throw new Error(`a hole byte must be 0 or 1: ${hole}`);
  let waterLevel: number | null = null;
  if (r.waterLevel !== undefined) {
    if (typeof r.waterLevel !== "number" || !Number.isFinite(r.waterLevel)) throw new Error("waterLevel must be a number");
    waterLevel = r.waterLevel;
  }
  const name = typeof r.name === "string" ? r.name : "";
  return { name, resolutionX, resolutionZ, cellSize, heights, surfaces, layers, holes, waterLevel };
}

function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
}

/** How much of the steep-ground rock shows through a surface: sand and gravel keep more of their own colour. */
function rockShare(surface: number): number {
  return surface === SAND ? 0.5 : surface === GRAVEL ? 0.3 : 1;
}

/** The linear colour of {@code surface} on ground this steep at this share of the height range (`TerrainMeshBuilder.color`). */
export function surfaceColor(surface: number, normalY: number, heightShare: number, out: number[] = [0, 0, 0]): number[] {
  const rock = 1 - smoothstep(ROCK_FULL, ROCK_START, normalY);
  if (surface !== AUTO) {
    const own = SURFACE_COLORS[surface]!;
    const amount = rock * rockShare(surface);
    for (let c = 0; c < 3; c++) out[c] = Math.pow(own[c]! + (ROCK[c]! - own[c]!) * amount, GAMMA);
    return out;
  }
  const snow = smoothstep(SNOW_START, SNOW_FULL, heightShare) * (1 - rock * 0.6);
  for (let c = 0; c < 3; c++) {
    let srgb = GRASS[c]! + (ROCK[c]! - GRASS[c]!) * rock;
    srgb += (SNOW[c]! - srgb) * snow;
    out[c] = Math.pow(srgb, GAMMA);
  }
  return out;
}

/** Width along x and depth along z in world units; the grid is centred on its entity, as on the desktop. */
export function terrainSize(data: TerrainData): { x: number; z: number } {
  return { x: (data.resolutionX - 1) * data.cellSize, z: (data.resolutionZ - 1) * data.cellSize };
}

function isHole(data: TerrainData, ix: number, iz: number): boolean {
  return data.holes !== null && data.holes[iz * data.resolutionX + ix] === 1;
}

/**
 * The terrain's triangles: one vertex per grid point with the desktop's normal (central differences) and colour (base ground blended with
 * each paint layer by weight, in linear light, then back to sRGB for the web's materials), two triangles per cell wound to face up, and no
 * triangle in a cell touching a hole. The snow line follows the whole terrain's height range, as on the desktop.
 */
export function terrainGeometry(data: TerrainData): TerrainGeometry {
  const { resolutionX: rx, resolutionZ: rz, cellSize, heights } = data;
  const points = rx * rz;
  const positions = new Float32Array(points * 3);
  const normals = new Float32Array(points * 3);
  const colors = new Float32Array(points * 4);
  let low = Infinity;
  let high = -Infinity;
  for (const h of heights) {
    low = Math.min(low, h);
    high = Math.max(high, h);
  }
  const span = Math.max(high - low, 1e-3);
  const size = terrainSize(data);
  const base = [0, 0, 0];
  const layer = [0, 0, 0];
  for (let iz = 0; iz < rz; iz++) {
    for (let ix = 0; ix < rx; ix++) {
      const p = iz * rx + ix;
      const h = heights[p]!;
      positions[p * 3] = -size.x / 2 + ix * cellSize;
      positions[p * 3 + 1] = h;
      positions[p * 3 + 2] = -size.z / 2 + iz * cellSize;
      const x0 = Math.max(ix - 1, 0);
      const x1 = Math.min(ix + 1, rx - 1);
      const z0 = Math.max(iz - 1, 0);
      const z1 = Math.min(iz + 1, rz - 1);
      const nx = -(heights[iz * rx + x1]! - heights[iz * rx + x0]!) / ((x1 - x0) * cellSize);
      const nz = -(heights[z1 * rx + ix]! - heights[z0 * rx + ix]!) / ((z1 - z0) * cellSize);
      const length = Math.hypot(nx, 1, nz);
      normals[p * 3] = nx / length;
      normals[p * 3 + 1] = 1 / length;
      normals[p * 3 + 2] = nz / length;
      const share = (h - low) / span;
      surfaceColor(data.surfaces ? data.surfaces[p]! : AUTO, 1 / length, share, base);
      let baseShare = 1;
      if (data.layers) {
        let sum = 0;
        for (let l = 0; l < LAYER_COUNT; l++) sum += data.layers[p * LAYER_COUNT + l]!;
        baseShare = Math.max(0, FULL_WEIGHT - sum) / FULL_WEIGHT;
      }
      const linear = [base[0]! * baseShare, base[1]! * baseShare, base[2]! * baseShare];
      if (baseShare < 1 && data.layers) {
        for (let l = 0; l < LAYER_COUNT; l++) {
          const weight = data.layers[p * LAYER_COUNT + l]!;
          if (weight === 0) continue;
          surfaceColor(l + 1, 1 / length, share, layer);
          for (let c = 0; c < 3; c++) linear[c]! += layer[c]! * (weight / FULL_WEIGHT);
        }
      }
      for (let c = 0; c < 3; c++) colors[p * 4 + c] = Math.pow(linear[c]!, 1 / GAMMA);
      colors[p * 4 + 3] = 1;
    }
  }
  const indices: number[] = [];
  for (let iz = 0; iz < rz - 1; iz++) {
    for (let ix = 0; ix < rx - 1; ix++) {
      if (isHole(data, ix, iz) || isHole(data, ix + 1, iz) || isHole(data, ix, iz + 1) || isHole(data, ix + 1, iz + 1)) continue;
      const a = iz * rx + ix;
      const b = a + 1;
      const c = a + rx;
      const d = c + 1;
      indices.push(a, c, b, b, c, d);
    }
  }
  return { positions, normals, colors, indices: Uint32Array.from(indices) };
}
