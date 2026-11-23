import type { SculptMesh } from "./SculptMesh";

export type BrushType = "DRAW" | "INFLATE" | "SMOOTH" | "FLATTEN" | "PINCH" | "GRAB";
export type Falloff = "SMOOTH" | "LINEAR" | "SHARP" | "CONSTANT";

export const BRUSH_TYPES: readonly BrushType[] = ["DRAW", "INFLATE", "SMOOTH", "FLATTEN", "PINCH", "GRAB"];
export const FALLOFFS: readonly Falloff[] = ["SMOOTH", "LINEAR", "SHARP", "CONSTANT"];

/** A dab at strength 1 moves a vertex at the brush centre by this fraction of the radius. */
export const DISPLACEMENT_PER_STRENGTH = 0.1;
/** Fraction of the way to the neighbour average that a smooth dab at strength 1 moves a vertex. */
export const SMOOTH_RATE = 0.5;
/** Fraction of the distance to the average plane that a flatten dab at strength 1 removes. */
export const FLATTEN_RATE = 0.5;
/** Fraction of the distance to the brush centre that a pinch dab at strength 1 covers. */
export const PINCH_RATE = 0.3;
/** A pinch dab also lifts the surface along the average normal by this fraction of a draw dab, so a crease shows. */
export const PINCH_LIFT = 0.5;
/** Most dabs one move places, so a huge jump of the cursor cannot freeze the editor. */
export const MAX_DABS_PER_MOVE = 64;
/** Smallest and largest dab spacing, as a fraction of the radius. */
export const MIN_SPACING = 0.02;
export const MAX_SPACING = 2;
/** A mirrored dab is skipped when the centre is within this fraction of the radius of the mirror plane. */
const MIRROR_PLANE_TOLERANCE = 1e-3;
const EPSILON = 1e-9;

export interface BrushSettings {
  radius: number;
  /** 0 to 1: how much one dab moves the surface. */
  strength: number;
  /** Distance between dabs as a fraction of the radius. */
  spacing: number;
  falloff: Falloff;
  /** Works in the opposite direction (digs instead of builds). Smooth, flatten and grab ignore it. */
  subtract: boolean;
}

export function defaultBrush(radius: number): BrushSettings {
  return { radius, strength: 0.5, spacing: 0.15, falloff: "SMOOTH", subtract: false };
}

/** Mirror planes through the mesh origin (its local axes). */
export interface Symmetry {
  x: boolean;
  y: boolean;
  z: boolean;
}

/** What a stroke changed: the vertices it touched with their positions before and after, for undo. */
export interface StrokeDelta {
  indices: number[];
  before: number[];
  after: number[];
}

/** The brush weight at a distance from the centre: 1 at the centre, 0 at or beyond the radius, never increasing. */
export function falloffWeight(falloff: Falloff, distanceFraction: number): number {
  if (!(distanceFraction < 1)) return 0;
  const remaining = 1 - Math.max(distanceFraction, 0);
  switch (falloff) {
    case "SMOOTH":
      return remaining * remaining * (3 - 2 * remaining);
    case "LINEAR":
      return remaining;
    case "SHARP":
      return remaining * remaining;
    case "CONSTANT":
      return 1;
  }
}

/**
 * One brush stroke: begin when the button goes down, move as the cursor travels over the surface, end to get the
 * {@link StrokeDelta} for the undo stack. Dab brushes apply their effect at evenly spaced points along the path, so the
 * result does not depend on how often the mouse reported; grab picks up the vertices under the brush and carries them.
 * With symmetry every dab is repeated on the mirrored sides.
 *
 * Positions are in the mesh's local space. Port of the desktop engine's SculptStroke; one instance can be reused.
 */
export class SculptStroke {
  private active = false;
  private type: BrushType = "DRAW";
  private settings: BrushSettings = defaultBrush(1);
  private mirrorMask = 0;
  private last = [0, 0, 0];
  private lastNormal = [0, 1, 0];
  private start = [0, 0, 0];
  private carry = 0;
  private dabs = 0;

  private readonly touchedStamp: Int32Array;
  private readonly grabStamp: Int32Array;
  private strokeId = 0;
  private touched: number[] = [];
  private originals: number[] = [];

  private grabVertices: number[] = [];
  private grabWeights: number[] = [];
  private grabMasks: number[] = [];
  private grabOrigins: number[] = [];

  /** Called with the vertices a dab or grab moved, to refresh normals and the drawn mesh. */
  onMoved: (vertices: number[]) => void = () => undefined;

  constructor(private readonly mesh: SculptMesh) {
    this.touchedStamp = new Int32Array(mesh.vertexCount);
    this.grabStamp = new Int32Array(mesh.vertexCount);
  }

  get isActive(): boolean {
    return this.active;
  }

  /** Dab positions placed along the current or last stroke (a mirrored copy is not counted again), or grab moves. */
  get dabCount(): number {
    return this.dabs;
  }

  begin(type: BrushType, settings: BrushSettings, symmetry: Symmetry, point: readonly number[], normal: readonly number[]): void {
    if (this.active) throw new Error("A stroke is already in progress");
    requireFinite(point, "point");
    const unit = unitNormal(normal);
    this.type = type;
    this.settings = { ...settings };
    this.mirrorMask = (symmetry.x ? 1 : 0) | (symmetry.y ? 2 : 0) | (symmetry.z ? 4 : 0);
    this.strokeId++;
    this.touched = [];
    this.originals = [];
    this.grabVertices = [];
    this.grabWeights = [];
    this.grabMasks = [];
    this.grabOrigins = [];
    this.carry = 0;
    this.dabs = 0;
    this.last = [...point];
    this.start = [...point];
    this.lastNormal = unit;
    this.active = true;
    if (type === "GRAB") this.captureGrab(point);
    else this.applyDabSymmetric(point, unit);
  }

  move(point: readonly number[], normal: readonly number[]): void {
    this.requireActive();
    requireFinite(point, "point");
    const unit = unitNormal(normal);
    if (this.type === "GRAB") {
      this.moveGrab([point[0]! - this.start[0]!, point[1]! - this.start[1]!, point[2]! - this.start[2]!]);
      return;
    }
    const dx = point[0]! - this.last[0]!;
    const dy = point[1]! - this.last[1]!;
    const dz = point[2]! - this.last[2]!;
    const distance = Math.hypot(dx, dy, dz);
    if (distance <= EPSILON) return;
    const step = this.settings.spacing * this.settings.radius;
    let next = step - this.carry;
    let placed = 0;
    while (next <= distance && placed < MAX_DABS_PER_MOVE) {
      const t = next / distance;
      const q = [0, 1, 2].map((i) => this.lastNormal[i]! + (unit[i]! - this.lastNormal[i]!) * t);
      const length = Math.hypot(q[0]!, q[1]!, q[2]!);
      const n = length > EPSILON ? q.map((value) => value / length) : unit;
      this.applyDabSymmetric([this.last[0]! + dx * t, this.last[1]! + dy * t, this.last[2]! + dz * t], n);
      next += step;
      placed++;
    }
    // When the cap was hit the remaining travel is dropped, so the stroke resumes from the new point
    this.carry = placed === MAX_DABS_PER_MOVE ? 0 : distance - (next - step);
    this.last = [...point];
    this.lastNormal = unit;
  }

  /** Ends the stroke and returns what it changed (empty when it touched nothing). */
  end(): StrokeDelta {
    this.requireActive();
    this.active = false;
    const after: number[] = [];
    const p = this.mesh.positions;
    for (const v of this.touched) after.push(p[3 * v]!, p[3 * v + 1]!, p[3 * v + 2]!);
    return { indices: this.touched, before: this.originals, after };
  }

  /** Abandons the stroke and puts every touched vertex back. Nothing is recorded for undo. */
  cancel(): void {
    this.requireActive();
    this.active = false;
    this.mesh.restore(this.touched, this.originals);
    this.onMoved(this.touched);
  }

  private requireActive(): void {
    if (!this.active) throw new Error("No stroke is in progress; call begin first");
  }

  private isDuplicateCopy(mask: number, c: readonly number[]): boolean {
    const tolerance = MIRROR_PLANE_TOLERANCE * this.settings.radius;
    return ((mask & 1) !== 0 && Math.abs(c[0]!) < tolerance) || ((mask & 2) !== 0 && Math.abs(c[1]!) < tolerance) || ((mask & 4) !== 0 && Math.abs(c[2]!) < tolerance);
  }

  private applyDabSymmetric(c: readonly number[], n: readonly number[]): void {
    this.dabs++;
    for (let mask = 0; mask < 8; mask++) {
      if ((mask & ~this.mirrorMask) !== 0 || this.isDuplicateCopy(mask, c)) continue;
      const sx = mask & 1 ? -1 : 1;
      const sy = mask & 2 ? -1 : 1;
      const sz = mask & 4 ? -1 : 1;
      this.applyDab([c[0]! * sx, c[1]! * sy, c[2]! * sz], [n[0]! * sx, n[1]! * sy, n[2]! * sz]);
    }
  }

  /** Vertices within the radius of a point; a scan, which the vertex cap keeps fast. */
  private collect(c: readonly number[]): number[] {
    const p = this.mesh.positions;
    const radius = this.settings.radius;
    const r2 = radius * radius;
    const found: number[] = [];
    for (let v = 0, n = this.mesh.vertexCount; v < n; v++) {
      const dx = p[3 * v]! - c[0]!;
      if (dx > radius || dx < -radius) continue;
      const dy = p[3 * v + 1]! - c[1]!;
      if (dy > radius || dy < -radius) continue;
      const dz = p[3 * v + 2]! - c[2]!;
      if (dx * dx + dy * dy + dz * dz < r2) found.push(v);
    }
    return found;
  }

  private applyDab(c: readonly number[], faceNormal: readonly number[]): void {
    const affected = this.collect(c);
    const count = affected.length;
    if (count === 0) return;
    const { radius, strength, falloff, subtract } = this.settings;
    const p = this.mesh.positions;
    const normals = this.mesh.normals;
    const weights = new Array<number>(count);
    let weightSum = 0;
    const centre = [0, 0, 0];
    const normal = [0, 0, 0];
    for (let i = 0; i < count; i++) {
      const v = affected[i]!;
      const w = falloffWeight(falloff, Math.hypot(p[3 * v]! - c[0]!, p[3 * v + 1]! - c[1]!, p[3 * v + 2]! - c[2]!) / radius);
      weights[i] = w;
      weightSum += w;
      for (let k = 0; k < 3; k++) {
        centre[k]! += w * p[3 * v + k]!;
        normal[k]! += w * normals[3 * v + k]!;
      }
    }
    if (weightSum <= EPSILON) return;
    for (let k = 0; k < 3; k++) centre[k]! /= weightSum;
    const normalLength = Math.hypot(normal[0]!, normal[1]!, normal[2]!);
    const avg = normalLength > EPSILON ? normal.map((value) => value / normalLength) : [...faceNormal];

    const sign = subtract ? -1 : 1;
    const push = radius * DISPLACEMENT_PER_STRENGTH * strength * sign;
    const results = new Float32Array(3 * count);
    for (let i = 0; i < count; i++) {
      const v = affected[i]!;
      const w = weights[i]!;
      const px = p[3 * v]!;
      const py = p[3 * v + 1]!;
      const pz = p[3 * v + 2]!;
      let rx = px;
      let ry = py;
      let rz = pz;
      if (w > 0) {
        switch (this.type) {
          case "DRAW":
            rx += avg[0]! * w * push;
            ry += avg[1]! * w * push;
            rz += avg[2]! * w * push;
            break;
          case "INFLATE":
            rx += normals[3 * v]! * w * push;
            ry += normals[3 * v + 1]! * w * push;
            rz += normals[3 * v + 2]! * w * push;
            break;
          case "SMOOTH": {
            const from = this.mesh.neighborStart[v]!;
            const to = this.mesh.neighborStart[v + 1]!;
            if (to > from) {
              let ax = 0;
              let ay = 0;
              let az = 0;
              for (let k = from; k < to; k++) {
                const u = this.mesh.neighbors[k]!;
                ax += p[3 * u]!;
                ay += p[3 * u + 1]!;
                az += p[3 * u + 2]!;
              }
              const amount = w * strength * SMOOTH_RATE;
              const inverse = 1 / (to - from);
              rx += (ax * inverse - px) * amount;
              ry += (ay * inverse - py) * amount;
              rz += (az * inverse - pz) * amount;
            }
            break;
          }
          case "FLATTEN": {
            const offset = (px - centre[0]!) * avg[0]! + (py - centre[1]!) * avg[1]! + (pz - centre[2]!) * avg[2]!;
            const amount = offset * w * strength * FLATTEN_RATE;
            rx -= avg[0]! * amount;
            ry -= avg[1]! * amount;
            rz -= avg[2]! * amount;
            break;
          }
          case "PINCH": {
            const toX = c[0]! - px;
            const toY = c[1]! - py;
            const toZ = c[2]! - pz;
            const along = toX * avg[0]! + toY * avg[1]! + toZ * avg[2]!;
            const amount = w * strength * PINCH_RATE * sign;
            const lift = w * push * PINCH_LIFT;
            rx += (toX - avg[0]! * along) * amount + avg[0]! * lift;
            ry += (toY - avg[1]! * along) * amount + avg[1]! * lift;
            rz += (toZ - avg[2]! * along) * amount + avg[2]! * lift;
            break;
          }
          case "GRAB":
            throw new Error("Grab is not a dab brush");
        }
      }
      results[3 * i] = rx;
      results[3 * i + 1] = ry;
      results[3 * i + 2] = rz;
    }

    const moved: number[] = [];
    for (let i = 0; i < count; i++) {
      if (weights[i]! <= 0) continue;
      const v = affected[i]!;
      this.recordOriginal(v);
      p[3 * v] = results[3 * i]!;
      p[3 * v + 1] = results[3 * i + 1]!;
      p[3 * v + 2] = results[3 * i + 2]!;
      moved.push(v);
    }
    this.mesh.recomputeNormalsAround(moved);
    this.onMoved(moved);
  }

  /** Remembers where a vertex was the first time this stroke touches it. */
  private recordOriginal(v: number): void {
    if (this.touchedStamp[v] === this.strokeId) return;
    this.touchedStamp[v] = this.strokeId;
    this.touched.push(v);
    const p = this.mesh.positions;
    this.originals.push(p[3 * v]!, p[3 * v + 1]!, p[3 * v + 2]!);
  }

  private captureGrab(c: readonly number[]): void {
    const p = this.mesh.positions;
    for (let mask = 0; mask < 8; mask++) {
      if ((mask & ~this.mirrorMask) !== 0 || this.isDuplicateCopy(mask, c)) continue;
      const m = [mask & 1 ? -c[0]! : c[0]!, mask & 2 ? -c[1]! : c[1]!, mask & 4 ? -c[2]! : c[2]!];
      for (const v of this.collect(m)) {
        if (this.grabStamp[v] === this.strokeId) continue;
        const w = falloffWeight(this.settings.falloff, Math.hypot(p[3 * v]! - m[0]!, p[3 * v + 1]! - m[1]!, p[3 * v + 2]! - m[2]!) / this.settings.radius);
        if (w <= 0) continue;
        this.grabStamp[v] = this.strokeId;
        this.grabVertices.push(v);
        this.grabWeights.push(w);
        this.grabMasks.push(mask);
        this.grabOrigins.push(p[3 * v]!, p[3 * v + 1]!, p[3 * v + 2]!);
        this.recordOriginal(v);
      }
    }
  }

  private moveGrab(delta: readonly number[]): void {
    if (this.grabVertices.length === 0) return;
    const p = this.mesh.positions;
    for (let i = 0; i < this.grabVertices.length; i++) {
      const v = this.grabVertices[i]!;
      const mask = this.grabMasks[i]!;
      const w = this.grabWeights[i]!;
      p[3 * v] = this.grabOrigins[3 * i]! + (mask & 1 ? -delta[0]! : delta[0]!) * w;
      p[3 * v + 1] = this.grabOrigins[3 * i + 1]! + (mask & 2 ? -delta[1]! : delta[1]!) * w;
      p[3 * v + 2] = this.grabOrigins[3 * i + 2]! + (mask & 4 ? -delta[2]! : delta[2]!) * w;
    }
    this.mesh.recomputeNormalsAround(this.grabVertices);
    this.onMoved(this.grabVertices);
    this.dabs++;
  }
}

function requireFinite(values: readonly number[], what: string): void {
  if (!values.every(Number.isFinite)) throw new Error(`The ${what} must be finite: ${values.join(", ")}`);
}

function unitNormal(normal: readonly number[]): number[] {
  requireFinite(normal, "normal");
  const length = Math.hypot(normal[0]!, normal[1]!, normal[2]!);
  if (!(length > EPSILON)) throw new Error("The normal has no length");
  return [normal[0]! / length, normal[1]! / length, normal[2]! / length];
}
