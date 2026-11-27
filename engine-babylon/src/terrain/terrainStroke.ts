import { FULL_WEIGHT, LAYER_COUNT, TERRAIN_VERSION, type TerrainData } from "./terrain";

/**
 * Terrain brush strokes on the web (roadmap A14): a port of the desktop's `TerrainStroke` (TD-082, TD-089, roadmap M3.5) with its
 * tools, constants and falloff. RAISE, LOWER, SMOOTH, FLATTEN, NOISE, PAINT, HOLE, ERODE and RAMP each change the {@link TerrainData}
 * in place. {@link TerrainStroke.end} returns what changed, for one undo step. Heights are held as 32-bit floats as on the desktop;
 * the shared trace `fixtures/contract-v1/terrain-trace` checks that both engines give the same terrain. Also
 * {@link terrainToJson}, the desktop's `TerrainIO.toJson`.
 */

export type BrushType = "RAISE" | "LOWER" | "SMOOTH" | "FLATTEN" | "NOISE" | "PAINT" | "HOLE" | "ERODE" | "RAMP";

/** The brush (the desktop's `TerrainBrush`): radius in world units, strength 0 to 1, Ctrl inverts, surface code for PAINT. */
export interface Brush {
  type: BrushType;
  radius: number;
  strength: number;
  inverted: boolean;
  /** Surface code (0 Auto, 1 Grass, 2 Sand, 3 Snow, 4 Gravel, 5 Rock, 6 Dirt); Auto wipes paint. */
  surface: number;
}

export const DEFAULT_RADIUS = 300;
export const DEFAULT_STRENGTH = 0.5;
export const MAX_RADIUS = 100_000;
/** The desktop's `TerrainStroke` and `Terrain` constants. */
const DAB_SPACING = 0.25;
const HEIGHT_PER_DAB = 0.05;
const NOISE_CELLS = 4;
const HOLE_THRESHOLD = 0.25;
const PAINT_RATE = 2;
const TALUS_SLOPE = 0.7;
const EROSION_RATE = 0.5;
const EROSION_PASSES = 4;
const RAMP_EDGE = 0.4;
export const MAX_ABS_HEIGHT = 100_000;

/** What a stroke changed: for undo, the indices and the values before and after. */
export interface TerrainDelta {
  heights: { index: number; before: number; after: number }[];
  weights: { index: number; before: number; after: number }[];
  holes: { index: number; before: number; after: number }[];
}

const f = Math.fround;
const sizeX = (t: TerrainData) => f((t.resolutionX - 1) * t.cellSize);
const sizeZ = (t: TerrainData) => f((t.resolutionZ - 1) * t.cellSize);
const xAt = (t: TerrainData, ix: number) => f(-sizeX(t) / 2 + ix * t.cellSize);
const zAt = (t: TerrainData, iz: number) => f(-sizeZ(t) / 2 + iz * t.cellSize);

/** The height under (x, z) in the terrain's own space, bilinear between grid points; NaN off the terrain (the desktop's `heightAt`). */
export function heightAt(t: TerrainData, x: number, z: number): number {
  const fx = f(f(x + sizeX(t) / 2) / t.cellSize);
  const fz = f(f(z + sizeZ(t) / 2) / t.cellSize);
  if (!(fx >= 0 && fx <= t.resolutionX - 1 && fz >= 0 && fz <= t.resolutionZ - 1)) return Number.NaN;
  const ix = Math.min(Math.trunc(fx), t.resolutionX - 2);
  const iz = Math.min(Math.trunc(fz), t.resolutionZ - 2);
  const tx = f(fx - ix);
  const tz = f(fz - iz);
  const h = t.heights;
  const w = t.resolutionX;
  return f(f(h[iz * w + ix]! * (1 - tx) + h[iz * w + ix + 1]! * tx) * (1 - tz) + f(h[(iz + 1) * w + ix]! * (1 - tx) + h[(iz + 1) * w + ix + 1]! * tx) * tz);
}

/** 1 at the centre falling smoothly to 0 at the radius. */
function weight(dx: number, dz: number, radius: number): number {
  const distance = f(Math.sqrt(dx * dx + dz * dz));
  if (distance >= radius) return 0;
  const s = f(1 - distance / radius);
  return f(s * s * (3 - 2 * s));
}

const smooth = (t: number) => f(t * t * (3 - 2 * t));

/** A fixed pseudo-random value in -1 to 1 per lattice point: the desktop's 32-bit integer hash. */
function lattice(x: number, z: number): number {
  let h = (Math.imul(x, 374761393) + Math.imul(z, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return f((h & 0xffff) / 32767.5 - 1);
}

export class TerrainStroke {
  private brush: Brush | null = null;
  private lastX = 0;
  private lastZ = 0;
  private flattenHeight = 0;
  private rampStart = { x: 0, z: 0, height: 0 };
  private rampEnd = { x: 0, z: 0 };
  private readonly originals = new Map<number, number>();
  private readonly originalWeights = new Map<number, number>();
  private readonly originalHoles = new Map<number, number>();

  constructor(private readonly terrain: TerrainData) {}

  get isActive(): boolean {
    return this.brush !== null;
  }

  /** Starts a stroke with a dab at (x, z) in the terrain's own space; the brush is copied. */
  begin(brush: Brush, x: number, z: number): void {
    if (this.brush) throw new Error("A stroke is already in progress");
    const height = heightAt(this.terrain, x, z);
    if (Number.isNaN(height)) throw new Error(`The stroke must begin on the terrain: ${x}, ${z}`);
    if (!(brush.radius > 0) || brush.radius > MAX_RADIUS) throw new Error(`Brush radius must be above 0 and at most ${MAX_RADIUS}: ${brush.radius}`);
    if (!(brush.strength >= 0 && brush.strength <= 1)) throw new Error(`Brush strength must be 0 to 1: ${brush.strength}`);
    this.brush = { ...brush, radius: f(brush.radius), strength: f(brush.strength) };
    this.flattenHeight = height;
    this.originals.clear();
    this.originalWeights.clear();
    this.originalHoles.clear();
    this.lastX = f(x);
    this.lastZ = f(z);
    if (brush.type === "RAMP") {
      this.rampStart = { x: f(x), z: f(z), height };
      this.rampEnd = { x: f(x), z: f(z) };
      return;
    }
    this.dab(f(x), f(z));
  }

  /** Carries the stroke to (x, z) with dabs spaced a quarter of the radius apart. */
  move(x: number, z: number): void {
    const brush = this.require();
    if (brush.type === "RAMP") {
      if (!Number.isNaN(heightAt(this.terrain, x, z))) this.rampEnd = { x: f(x), z: f(z) };
      return;
    }
    const dx = f(f(x) - this.lastX);
    const dz = f(f(z) - this.lastZ);
    const distance = f(Math.sqrt(dx * dx + dz * dz));
    const spacing = f(brush.radius * DAB_SPACING);
    const dabs = Math.trunc(distance / spacing);
    for (let i = 1; i <= dabs; i++) {
      const t = f(f(i * spacing) / distance);
      this.dab(f(this.lastX + dx * t), f(this.lastZ + dz * t));
    }
    if (dabs > 0) {
      const covered = f(f(dabs * spacing) / distance);
      this.lastX = f(this.lastX + dx * covered);
      this.lastZ = f(this.lastZ + dz * covered);
    }
  }

  /** Finishes the stroke; returns what changed (empty arrays when nothing moved). */
  end(): TerrainDelta {
    const brush = this.require();
    if (brush.type === "RAMP") this.ramp();
    this.brush = null;
    const t = this.terrain;
    const heights = [...this.originals].filter(([i, before]) => t.heights[i] !== before)
      .map(([index, before]) => ({ index, before, after: t.heights[index]! }));
    const weights = [...this.originalWeights].filter(([i, before]) => t.layers![i] !== before)
      .map(([index, before]) => ({ index, before, after: t.layers![index]! }));
    const holes = [...this.originalHoles].filter(([i, before]) => t.holes![i] !== before)
      .map(([index, before]) => ({ index, before, after: t.holes![index]! }));
    return { heights, weights, holes };
  }

  private require(): Brush {
    if (!this.brush) throw new Error("No stroke is in progress");
    return this.brush;
  }

  private dab(x: number, z: number): void {
    const t = this.terrain;
    const brush = this.brush!;
    const radius = brush.radius;
    const cell = t.cellSize;
    const halfX = f(sizeX(t) / 2);
    const halfZ = f(sizeZ(t) / 2);
    const ixMin = Math.max(0, Math.ceil(f(f(x - radius + halfX) / cell)));
    const ixMax = Math.min(t.resolutionX - 1, Math.floor(f(f(x + radius + halfX) / cell)));
    const izMin = Math.max(0, Math.ceil(f(f(z - radius + halfZ) / cell)));
    const izMax = Math.min(t.resolutionZ - 1, Math.floor(f(f(z + radius + halfZ) / cell)));
    if (ixMin > ixMax || izMin > izMax) return;
    if (brush.type === "PAINT") return this.paintDab(x, z, ixMin, ixMax, izMin, izMax);
    if (brush.type === "HOLE") return this.holeDab(x, z, ixMin, ixMax, izMin, izMax);
    if (brush.type === "ERODE") {
      for (let pass = 0; pass < EROSION_PASSES; pass++) this.erodeDab(x, z, ixMin, ixMax, izMin, izMax);
      return;
    }
    if (brush.type === "SMOOTH") return this.smoothDab(x, z, ixMin, ixMax, izMin, izMax);
    const strength = brush.strength;
    const step = f(f(strength * radius) * HEIGHT_PER_DAB);
    for (let iz = izMin; iz <= izMax; iz++) {
      for (let ix = ixMin; ix <= ixMax; ix++) {
        const w = weight(f(xAt(t, ix) - x), f(zAt(t, iz) - z), radius);
        if (w <= 0) continue;
        const height = t.heights[iz * t.resolutionX + ix]!;
        let target: number;
        switch (brush.type) {
          case "RAISE": target = height + (brush.inverted ? -step : step) * w; break;
          case "LOWER": target = height + (brush.inverted ? step : -step) * w; break;
          case "FLATTEN": target = height + (this.flattenHeight - height) * Math.min(1, f(strength * w)); break;
          case "NOISE": target = height + f(f((brush.inverted ? -step : step) * w) * this.noise(xAt(t, ix), zAt(t, iz))); break;
          default: throw new Error(`Unhandled brush ${brush.type}`);
        }
        this.write(ix, iz, f(target));
      }
    }
  }

  private paintDab(x: number, z: number, ixMin: number, ixMax: number, izMin: number, izMax: number): void {
    const t = this.terrain;
    const brush = this.brush!;
    const wipe = brush.inverted || brush.surface === 0;
    const target = wipe ? -1 : brush.surface - 1;
    t.layers ??= new Uint8Array(t.resolutionX * t.resolutionZ * LAYER_COUNT);
    const layers = t.layers;
    const pointWeights = new Array<number>(LAYER_COUNT).fill(0);
    for (let iz = izMin; iz <= izMax; iz++) {
      for (let ix = ixMin; ix <= ixMax; ix++) {
        const falloff = weight(f(xAt(t, ix) - x), f(zAt(t, iz) - z), brush.radius);
        if (falloff <= 0) continue;
        const amount = Math.min(1, f(f(brush.strength * falloff) * PAINT_RATE));
        const point = iz * t.resolutionX + ix;
        for (let layer = 0; layer < LAYER_COUNT; layer++) pointWeights[layer] = layers[point * LAYER_COUNT + layer]!;
        if (wipe) {
          for (let layer = 0; layer < LAYER_COUNT; layer++) pointWeights[layer] = Math.round(f(pointWeights[layer]! * f(1 - amount)));
        } else {
          const painted = Math.round(f(pointWeights[target]! + f((FULL_WEIGHT - pointWeights[target]!) * amount)));
          pointWeights[target] = painted;
          let others = 0;
          for (let layer = 0; layer < LAYER_COUNT; layer++) if (layer !== target) others += pointWeights[layer]!;
          const room = FULL_WEIGHT - painted;
          if (others > room) {
            for (let layer = 0; layer < LAYER_COUNT; layer++) {
              // Integer division rounds down, as on the desktop, so the sum stays within full
              if (layer !== target) pointWeights[layer] = Math.trunc((pointWeights[layer]! * room) / others);
            }
          }
        }
        for (let layer = 0; layer < LAYER_COUNT; layer++) {
          const index = point * LAYER_COUNT + layer;
          const old = layers[index]!;
          if (old !== pointWeights[layer]) {
            if (!this.originalWeights.has(index)) this.originalWeights.set(index, old);
            layers[index] = pointWeights[layer]!;
          }
        }
      }
    }
  }

  private holeDab(x: number, z: number, ixMin: number, ixMax: number, izMin: number, izMax: number): void {
    const t = this.terrain;
    const cut = !this.brush!.inverted;
    t.holes ??= new Uint8Array(t.resolutionX * t.resolutionZ);
    for (let iz = izMin; iz <= izMax; iz++) {
      for (let ix = ixMin; ix <= ixMax; ix++) {
        const index = iz * t.resolutionX + ix;
        if (weight(f(xAt(t, ix) - x), f(zAt(t, iz) - z), this.brush!.radius) >= HOLE_THRESHOLD && (t.holes[index] === 1) !== cut) {
          if (!this.originalHoles.has(index)) this.originalHoles.set(index, t.holes[index]!);
          t.holes[index] = cut ? 1 : 0;
        }
      }
    }
  }

  private erodeDab(x: number, z: number, ixMin: number, ixMax: number, izMin: number, izMax: number): void {
    const t = this.terrain;
    const x0 = Math.max(0, ixMin - 1);
    const x1 = Math.min(t.resolutionX - 1, ixMax + 1);
    const z0 = Math.max(0, izMin - 1);
    const z1 = Math.min(t.resolutionZ - 1, izMax + 1);
    const width = x1 - x0 + 1;
    const scratch = new Float32Array(width * (z1 - z0 + 1));
    const radius = this.brush!.radius;
    const cell = t.cellSize;
    const rate = f(EROSION_RATE * this.brush!.strength);
    const at = (ix: number, iz: number) => t.heights[iz * t.resolutionX + ix]!;
    for (let iz = izMin; iz <= izMax; iz++) {
      for (let ix = ixMin; ix <= ixMax; ix++) {
        const w = weight(f(xAt(t, ix) - x), f(zAt(t, iz) - z), radius);
        if (w <= 0) continue;
        const height = at(ix, iz);
        let lowX = -1;
        let lowZ = -1;
        let steepest = 0;
        let drop = 0;
        for (let dz = -1; dz <= 1; dz++) {
          for (let dx = -1; dx <= 1; dx++) {
            const nx = ix + dx;
            const nz = iz + dz;
            if ((dx === 0 && dz === 0) || nx < x0 || nx > x1 || nz < z0 || nz > z1) continue;
            const distance = f(cell * f(Math.sqrt(dx * dx + dz * dz)));
            const slope = f(f(height - at(nx, nz)) / distance);
            if (slope > steepest) {
              steepest = slope;
              lowX = nx;
              lowZ = nz;
              drop = distance;
            }
          }
        }
        if (lowX < 0 || steepest <= TALUS_SLOPE) continue;
        const excess = f(f(steepest - TALUS_SLOPE) * drop);
        const moved = f(f(excess * 0.5) * Math.min(1, f(rate * w)));
        scratch[(iz - z0) * width + (ix - x0)] = f(scratch[(iz - z0) * width + (ix - x0)]! - moved);
        scratch[(lowZ - z0) * width + (lowX - x0)] = f(scratch[(lowZ - z0) * width + (lowX - x0)]! + moved);
      }
    }
    for (let iz = z0; iz <= z1; iz++) {
      for (let ix = x0; ix <= x1; ix++) {
        const change = scratch[(iz - z0) * width + (ix - x0)]!;
        if (change !== 0) this.write(ix, iz, f(at(ix, iz) + change));
      }
    }
  }

  private ramp(): void {
    const t = this.terrain;
    const start = this.rampStart;
    const end = this.rampEnd;
    const dx = f(end.x - start.x);
    const dz = f(end.z - start.z);
    const lengthSquared = f(dx * dx + dz * dz);
    const cell = t.cellSize;
    if (lengthSquared < cell * cell) return; // a click, not a drag
    const endHeight = heightAt(t, end.x, end.z);
    const half = this.brush!.radius;
    const halfX = f(sizeX(t) / 2);
    const halfZ = f(sizeZ(t) / 2);
    const ixMin = Math.max(0, Math.ceil(f(f(Math.min(start.x, end.x) - half + halfX) / cell)));
    const ixMax = Math.min(t.resolutionX - 1, Math.floor(f(f(Math.max(start.x, end.x) + half + halfX) / cell)));
    const izMin = Math.max(0, Math.ceil(f(f(Math.min(start.z, end.z) - half + halfZ) / cell)));
    const izMax = Math.min(t.resolutionZ - 1, Math.floor(f(f(Math.max(start.z, end.z) + half + halfZ) / cell)));
    const inner = f(half * (1 - RAMP_EDGE));
    for (let iz = izMin; iz <= izMax; iz++) {
      for (let ix = ixMin; ix <= ixMax; ix++) {
        const px = f(xAt(t, ix) - start.x);
        const pz = f(zAt(t, iz) - start.z);
        const along = f(f(px * dx + pz * dz) / lengthSquared);
        if (along < 0 || along > 1) continue;
        const side = f(Math.sqrt(Math.max(0, f(px * px + pz * pz - along * along * lengthSquared))));
        if (side >= half) continue;
        const blend = side <= inner ? 1 : smooth(f(1 - f(side - inner) / f(half - inner)));
        const target = f(start.height + f(endHeight - start.height) * along);
        const height = t.heights[iz * t.resolutionX + ix]!;
        this.write(ix, iz, f(height + f(target - height) * blend));
      }
    }
  }

  private smoothDab(x: number, z: number, ixMin: number, ixMax: number, izMin: number, izMax: number): void {
    const t = this.terrain;
    const width = ixMax - ixMin + 1;
    const scratch = new Float32Array(width * (izMax - izMin + 1));
    const radius = this.brush!.radius;
    const at = (ix: number, iz: number) => t.heights[iz * t.resolutionX + ix]!;
    for (let iz = izMin; iz <= izMax; iz++) {
      for (let ix = ixMin; ix <= ixMax; ix++) {
        const w = weight(f(xAt(t, ix) - x), f(zAt(t, iz) - z), radius);
        const height = at(ix, iz);
        let result = height;
        if (w > 0) {
          let sum = 0;
          let neighbours = 0;
          for (let dz = -1; dz <= 1; dz++) {
            for (let dx = -1; dx <= 1; dx++) {
              const nx = ix + dx;
              const nz = iz + dz;
              if ((dx !== 0 || dz !== 0) && nx >= 0 && nx < t.resolutionX && nz >= 0 && nz < t.resolutionZ) {
                sum = f(sum + at(nx, nz));
                neighbours++;
              }
            }
          }
          result = f(height + f(f(sum / neighbours) - height) * Math.min(1, f(this.brush!.strength * w)));
        }
        scratch[(iz - izMin) * width + (ix - ixMin)] = result;
      }
    }
    for (let iz = izMin; iz <= izMax; iz++) {
      for (let ix = ixMin; ix <= ixMax; ix++) this.write(ix, iz, scratch[(iz - izMin) * width + (ix - ixMin)]!);
    }
  }

  private write(ix: number, iz: number, height: number): void {
    const index = iz * this.terrain.resolutionX + ix;
    if (!this.originals.has(index)) this.originals.set(index, this.terrain.heights[index]!);
    this.terrain.heights[index] = Math.max(-MAX_ABS_HEIGHT, Math.min(MAX_ABS_HEIGHT, height));
  }

  private noise(x: number, z: number): number {
    const size = f(this.terrain.cellSize * NOISE_CELLS);
    const fx = f(x / size);
    const fz = f(z / size);
    const x0 = Math.floor(fx);
    const z0 = Math.floor(fz);
    const tx = smooth(f(fx - x0));
    const tz = smooth(f(fz - z0));
    const a = lattice(x0, z0);
    const b = lattice(x0 + 1, z0);
    const c = lattice(x0, z0 + 1);
    const d = lattice(x0 + 1, z0 + 1);
    return f(f(a * (1 - tx) + b * tx) * (1 - tz) + f(c * (1 - tx) + d * tx) * tz);
  }
}

/** Puts a stroke's changes back (undo) or forward again (redo). */
export function applyDelta(t: TerrainData, delta: TerrainDelta, forward: boolean): void {
  for (const change of delta.heights) t.heights[change.index] = forward ? change.after : change.before;
  if (delta.weights.length > 0) {
    t.layers ??= new Uint8Array(t.resolutionX * t.resolutionZ * LAYER_COUNT);
    for (const change of delta.weights) t.layers[change.index] = forward ? change.after : change.before;
  }
  if (delta.holes.length > 0) {
    t.holes ??= new Uint8Array(t.resolutionX * t.resolutionZ);
    for (const change of delta.holes) t.holes[change.index] = forward ? change.after : change.before;
  }
}

/** Java's `Float.toString` for the numbers a terrain file holds (shortest digits that read back as the same 32-bit float). */
function javaFloat(value: number): string {
  const v = f(value);
  if (Number.isInteger(v) && Math.abs(v) < 1e7) return `${v}.0`;
  for (let digits = 1; digits <= 9; digits++) {
    const text = v.toPrecision(digits);
    if (f(Number(text)) === v) return String(Number(text));
  }
  return String(v);
}

function base64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

/** The terrain as the desktop's `TerrainIO.toJson` writes it: the same terrain always gives the same text, on both engines. */
export function terrainToJson(t: TerrainData): string {
  const heights = new Uint8Array(t.heights.length * 4);
  const view = new DataView(heights.buffer);
  t.heights.forEach((h, i) => view.setFloat32(i * 4, h, true));
  const any = (bytes: Uint8Array | null) => bytes !== null && bytes.some((b) => b !== 0);
  const surfaces = any(t.surfaces) ? `,\n  "surfaces": "${base64(t.surfaces!)}"` : "";
  const layers = any(t.layers) ? `,\n  "layerCount": ${LAYER_COUNT},\n  "layers": "${base64(t.layers!)}"` : "";
  const holes = any(t.holes) ? `,\n  "holes": "${base64(t.holes!)}"` : "";
  const water = t.waterLevel !== null ? `,\n  "waterLevel": ${javaFloat(t.waterLevel)}` : "";
  return "{\n"
    + `  "version": ${TERRAIN_VERSION},\n`
    + `  "name": ${JSON.stringify(t.name)},\n`
    + `  "resolutionX": ${t.resolutionX},\n`
    + `  "resolutionZ": ${t.resolutionZ},\n`
    + `  "cellSize": ${javaFloat(t.cellSize)},\n`
    + `  "heights": "${base64(heights)}"${surfaces}${layers}${holes}${water}\n`
    + "}\n";
}
