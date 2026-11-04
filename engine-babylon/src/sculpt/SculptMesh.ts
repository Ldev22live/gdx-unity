/**
 * An editable triangle mesh: shared vertices, so moving a vertex moves every triangle around it. No Babylon in here; the
 * viewport copies {@link positions} and {@link normals} into a Babylon mesh after each change.
 *
 * Web port of the desktop engine's SculptMesh (engine-sculpt), minus the BVH: brushes find vertices by scanning, which
 * is fast enough for the {@link MAX_VERTICES} this editor allows.
 */

/** Most vertices a sculpt may have; the brush scans every vertex per dab, so this keeps strokes interactive. */
export const MAX_VERTICES = 100_000;
/** Most cube-sphere cells per cube edge: 6 * 128^2 + 2 = 98,306 vertices. */
export const MAX_CUBE_SPHERE_RESOLUTION = 128;

export class SculptMesh {
  readonly positions: Float32Array;
  readonly triangles: Uint32Array;
  readonly normals: Float32Array;
  /** Neighbour vertices of vertex v are neighbors[neighborStart[v] .. neighborStart[v + 1]). */
  readonly neighborStart: Uint32Array;
  readonly neighbors: Uint32Array;
  /** Triangles using vertex v are vertexTriangles[triangleStart[v] .. triangleStart[v + 1]). */
  private readonly triangleStart: Uint32Array;
  private readonly vertexTriangles: Uint32Array;

  /**
   * @param positions x, y, z per vertex (kept, not copied)
   * @param triangles three vertex indices per triangle (kept, not copied)
   * @throws Error when the arrays are not multiples of 3, an index is out of range, or the mesh is too big
   */
  constructor(positions: Float32Array, triangles: Uint32Array) {
    if (positions.length % 3 !== 0 || triangles.length % 3 !== 0) {
      throw new Error(`positions and triangles must have lengths that are multiples of 3: ${positions.length}, ${triangles.length}`);
    }
    const vertexCount = positions.length / 3;
    if (vertexCount < 1 || triangles.length < 3) throw new Error("A sculpt needs at least one triangle");
    if (vertexCount > MAX_VERTICES) throw new Error(`A sculpt can have at most ${MAX_VERTICES} vertices, this one has ${vertexCount}`);
    for (let i = 0; i < triangles.length; i++) {
      if (triangles[i]! >= vertexCount) {
        throw new Error(`Triangle ${Math.floor(i / 3)} uses vertex ${triangles[i]} but there are ${vertexCount} vertices`);
      }
    }
    this.positions = positions;
    this.triangles = triangles;
    this.normals = new Float32Array(positions.length);

    const triangleCounts = new Uint32Array(vertexCount + 1);
    for (let i = 0; i < triangles.length; i++) triangleCounts[triangles[i]! + 1]!++;
    for (let v = 0; v < vertexCount; v++) triangleCounts[v + 1]! += triangleCounts[v]!;
    this.triangleStart = triangleCounts;
    this.vertexTriangles = new Uint32Array(triangles.length);
    const fill = triangleCounts.slice(0, vertexCount);
    for (let i = 0; i < triangles.length; i++) this.vertexTriangles[fill[triangles[i]!]!++] = Math.floor(i / 3);

    const lists: Set<number>[] = Array.from({ length: vertexCount }, () => new Set<number>());
    for (let t = 0; t < triangles.length; t += 3) {
      const a = triangles[t]!;
      const b = triangles[t + 1]!;
      const c = triangles[t + 2]!;
      lists[a]!.add(b).add(c);
      lists[b]!.add(a).add(c);
      lists[c]!.add(a).add(b);
    }
    this.neighborStart = new Uint32Array(vertexCount + 1);
    for (let v = 0; v < vertexCount; v++) this.neighborStart[v + 1] = this.neighborStart[v]! + lists[v]!.size;
    this.neighbors = new Uint32Array(this.neighborStart[vertexCount]!);
    for (let v = 0; v < vertexCount; v++) this.neighbors.set([...lists[v]!], this.neighborStart[v]!);
    this.recomputeNormals();
  }

  get vertexCount(): number {
    return this.positions.length / 3;
  }

  get triangleCount(): number {
    return this.triangles.length / 3;
  }

  /** Recomputes every vertex normal from the triangles around it. */
  recomputeNormals(): void {
    this.normals.fill(0);
    for (let t = 0; t < this.triangles.length; t += 3) this.addTriangleNormal(t);
    this.normalizeAll();
  }

  /** Recomputes the normals of the given vertices only (the ones a dab moved and their neighbours' triangles). */
  recomputeNormalsAround(vertices: ArrayLike<number>, count = vertices.length): void {
    const stamp = new Set<number>();
    for (let i = 0; i < count; i++) {
      const v = vertices[i]!;
      for (let k = this.triangleStart[v]!; k < this.triangleStart[v + 1]!; k++) stamp.add(this.vertexTriangles[k]!);
    }
    const affectedVertices = new Set<number>();
    for (const t of stamp) {
      affectedVertices.add(this.triangles[3 * t]!).add(this.triangles[3 * t + 1]!).add(this.triangles[3 * t + 2]!);
    }
    for (const v of affectedVertices) {
      this.normals[3 * v] = 0;
      this.normals[3 * v + 1] = 0;
      this.normals[3 * v + 2] = 0;
    }
    // Every triangle touching an affected vertex contributes to it, so those are summed (not only the changed ones)
    const touching = new Set<number>();
    for (const v of affectedVertices) {
      for (let k = this.triangleStart[v]!; k < this.triangleStart[v + 1]!; k++) touching.add(this.vertexTriangles[k]!);
    }
    for (const t of touching) this.addTriangleNormal(3 * t, affectedVertices);
    for (const v of affectedVertices) this.normalizeVertex(v);
  }

  private addTriangleNormal(offset: number, only?: Set<number>): void {
    const p = this.positions;
    const a = this.triangles[offset]!;
    const b = this.triangles[offset + 1]!;
    const c = this.triangles[offset + 2]!;
    const e1x = p[3 * b]! - p[3 * a]!;
    const e1y = p[3 * b + 1]! - p[3 * a + 1]!;
    const e1z = p[3 * b + 2]! - p[3 * a + 2]!;
    const e2x = p[3 * c]! - p[3 * a]!;
    const e2y = p[3 * c + 1]! - p[3 * a + 1]!;
    const e2z = p[3 * c + 2]! - p[3 * a + 2]!;
    // Area-weighted: the unnormalised cross product
    const nx = e1y * e2z - e1z * e2y;
    const ny = e1z * e2x - e1x * e2z;
    const nz = e1x * e2y - e1y * e2x;
    for (const v of [a, b, c]) {
      if (only && !only.has(v)) continue;
      this.normals[3 * v]! += nx;
      this.normals[3 * v + 1]! += ny;
      this.normals[3 * v + 2]! += nz;
    }
  }

  private normalizeAll(): void {
    for (let v = 0; v < this.vertexCount; v++) this.normalizeVertex(v);
  }

  private normalizeVertex(v: number): void {
    const n = this.normals;
    const length = Math.hypot(n[3 * v]!, n[3 * v + 1]!, n[3 * v + 2]!);
    if (length > 1e-12) {
      n[3 * v] = n[3 * v]! / length;
      n[3 * v + 1] = n[3 * v + 1]! / length;
      n[3 * v + 2] = n[3 * v + 2]! / length;
    } else {
      // A vertex whose triangles all collapsed has no direction; point it up rather than leave NaN
      n[3 * v] = 0;
      n[3 * v + 1] = 1;
      n[3 * v + 2] = 0;
    }
  }

  /** Puts earlier positions back (undo of a stroke) and refreshes the normals around them. */
  restore(indices: ArrayLike<number>, values: ArrayLike<number>): void {
    for (let i = 0; i < indices.length; i++) {
      const v = indices[i]!;
      this.positions[3 * v] = values[3 * i]!;
      this.positions[3 * v + 1] = values[3 * i + 1]!;
      this.positions[3 * v + 2] = values[3 * i + 2]!;
    }
    this.recomputeNormalsAround(indices);
  }
}

/**
 * A sphere made from a cube whose six faces are each a grid of resolution x resolution quads pushed out onto the sphere.
 * Its triangles are about the same size everywhere, so a brush acts the same on all sides.
 *
 * It has 6 * resolution^2 + 2 vertices and 12 * resolution^2 triangles, centred on the origin, facing outwards.
 */
export function cubeSphere(radius: number, resolution: number): SculptMesh {
  if (!(radius > 0) || !Number.isFinite(radius)) throw new Error(`radius must be a finite number greater than 0: ${radius}`);
  if (!Number.isInteger(resolution) || resolution < 1 || resolution > MAX_CUBE_SPHERE_RESOLUTION) {
    throw new Error(`resolution must be a whole number from 1 to ${MAX_CUBE_SPHERE_RESOLUTION}: ${resolution}`);
  }
  const side = resolution + 1;
  const vertexOfLattice = new Map<number, number>();
  const positions: number[] = [];
  const triangles: number[] = [];
  // For each face: the axis held fixed, its value (0 or resolution), and the two axes that vary
  const faces: [number, number, number, number][] = [
    [0, resolution, 1, 2], [0, 0, 1, 2],
    [1, resolution, 0, 2], [1, 0, 0, 2],
    [2, resolution, 0, 1], [2, 0, 0, 1],
  ];
  const lattice = [0, 0, 0];
  const spherePoint = () => {
    const [x, y, z] = lattice.map((n) => (2 * n) / resolution - 1) as [number, number, number];
    const x2 = x * x;
    const y2 = y * y;
    const z2 = z * z;
    positions.push(
      radius * x * Math.sqrt(1 - y2 / 2 - z2 / 2 + (y2 * z2) / 3),
      radius * y * Math.sqrt(1 - z2 / 2 - x2 / 2 + (z2 * x2) / 3),
      radius * z * Math.sqrt(1 - x2 / 2 - y2 / 2 + (x2 * y2) / 3),
    );
  };
  const outward = (a: number, b: number, c: number) => {
    const e1 = [0, 1, 2].map((i) => positions[3 * b + i]! - positions[3 * a + i]!) as [number, number, number];
    const e2 = [0, 1, 2].map((i) => positions[3 * c + i]! - positions[3 * a + i]!) as [number, number, number];
    const n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
    const centre = [0, 1, 2].map((i) => positions[3 * a + i]! + positions[3 * b + i]! + positions[3 * c + i]!);
    const facesOut = n[0]! * centre[0]! + n[1]! * centre[1]! + n[2]! * centre[2]! >= 0;
    triangles.push(a, facesOut ? b : c, facesOut ? c : b);
  };
  for (const face of faces) {
    for (let a = 0; a < resolution; a++) {
      for (let b = 0; b < resolution; b++) {
        const quad: number[] = [];
        for (let corner = 0; corner < 4; corner++) {
          lattice[face[0]] = face[1];
          lattice[face[2]] = a + (corner & 1);
          lattice[face[3]] = b + (corner >> 1);
          // Vertices are keyed by their place on the cube, so neighbouring faces share their edge vertices exactly
          const key = (lattice[0]! * side + lattice[1]!) * side + lattice[2]!;
          let index = vertexOfLattice.get(key);
          if (index === undefined) {
            index = positions.length / 3;
            vertexOfLattice.set(key, index);
            spherePoint();
          }
          quad.push(index);
        }
        outward(quad[0]!, quad[1]!, quad[3]!);
        outward(quad[0]!, quad[3]!, quad[2]!);
      }
    }
  }
  return new SculptMesh(new Float32Array(positions), new Uint32Array(triangles));
}

// ---- Storage in the scene file ---------------------------------------------------------------------------------------

/** Float32 or Uint32 data as base64, so a sculpt takes about a third of the space of a JSON number list. */
export function encodeArray(array: Float32Array | Uint32Array): string {
  const bytes = new Uint8Array(array.buffer, array.byteOffset, array.byteLength);
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  return btoa(binary);
}

function decodeBytes(text: string): Uint8Array {
  const binary = atob(text);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

export function decodeFloats(text: string): Float32Array {
  const bytes = decodeBytes(text);
  if (bytes.length % 4 !== 0) throw new Error("Sculpt positions are not a whole number of floats");
  return new Float32Array(bytes.buffer, 0, bytes.length / 4);
}

export function decodeInts(text: string): Uint32Array {
  const bytes = decodeBytes(text);
  if (bytes.length % 4 !== 0) throw new Error("Sculpt triangles are not a whole number of integers");
  return new Uint32Array(bytes.buffer, 0, bytes.length / 4);
}

/** The data of a SculptMeshComponent for a mesh (colour is added by the caller). */
export function encodeMesh(mesh: SculptMesh): { positions: string; triangles: string } {
  return { positions: encodeArray(mesh.positions), triangles: encodeArray(mesh.triangles) };
}

export function decodeMesh(data: { positions: string; triangles: string }): SculptMesh {
  return new SculptMesh(decodeFloats(data.positions), decodeInts(data.triangles));
}
