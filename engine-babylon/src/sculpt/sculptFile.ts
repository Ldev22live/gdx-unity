import { MAX_VERTICES, SculptMesh } from "./SculptMesh";

/** File extension of a sculpt, without the dot: the desktop editor's own editing format. */
export const SCULPT_EXTENSION = "sculpt";
/** "GSCM" read as little-endian bytes: identifies the file type. */
const MAGIC = 0x4d435347;
const VERSION = 1;
const HEADER_BYTES = 16;
/** The desktop accepts up to this many triangles; a bigger count in a file is corrupt or too big for this editor. */
const MAX_FILE_TRIANGLES = 8_000_000;

/**
 * Writes a mesh in the desktop's `.sculpt` layout (little-endian): the magic `GSCM`, version 1, vertex count, triangle
 * count, then 3 floats per vertex and 3 ints per triangle. The desktop editor opens the file as is.
 */
export function writeSculptFile(mesh: SculptMesh): Uint8Array {
  const bytes = new Uint8Array(HEADER_BYTES + 4 * mesh.positions.length + 4 * mesh.triangles.length);
  const view = new DataView(bytes.buffer);
  view.setUint32(0, MAGIC, true);
  view.setInt32(4, VERSION, true);
  view.setInt32(8, mesh.vertexCount, true);
  view.setInt32(12, mesh.triangleCount, true);
  let at = HEADER_BYTES;
  for (const value of mesh.positions) {
    view.setFloat32(at, value, true);
    at += 4;
  }
  for (const index of mesh.triangles) {
    view.setInt32(at, index, true);
    at += 4;
  }
  return bytes;
}

/**
 * Reads a `.sculpt` file.
 *
 * @throws Error when the data is not a sculpt file, has another version, is truncated, is bigger than this editor
 *         handles ({@link MAX_VERTICES} vertices), or describes an invalid mesh
 */
export function readSculptFile(bytes: Uint8Array): SculptMesh {
  if (bytes.length < HEADER_BYTES) throw new Error("The sculpt file is truncated");
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (view.getUint32(0, true) !== MAGIC) throw new Error("Not a sculpt file (wrong file signature)");
  const version = view.getInt32(4, true);
  if (version !== VERSION) throw new Error(`Unsupported sculpt file version ${version}; this editor reads version ${VERSION}`);
  const vertexCount = view.getInt32(8, true);
  const triangleCount = view.getInt32(12, true);
  if (vertexCount < 1 || triangleCount < 1 || triangleCount > MAX_FILE_TRIANGLES) {
    throw new Error(`The sculpt file has an invalid size: ${vertexCount} vertices, ${triangleCount} triangles`);
  }
  if (vertexCount > MAX_VERTICES) {
    throw new Error(`This sculpt has ${vertexCount} vertices; the web editor handles at most ${MAX_VERTICES}`);
  }
  if (bytes.length < HEADER_BYTES + 12 * vertexCount + 12 * triangleCount) throw new Error("The sculpt file is truncated");
  const positions = new Float32Array(3 * vertexCount);
  const triangles = new Uint32Array(3 * triangleCount);
  let at = HEADER_BYTES;
  for (let i = 0; i < positions.length; i++, at += 4) positions[i] = view.getFloat32(at, true);
  for (let i = 0; i < triangles.length; i++, at += 4) triangles[i] = view.getInt32(at, true);
  if (!positions.every(Number.isFinite)) throw new Error("The sculpt file is corrupt: a position is not a number");
  return new SculptMesh(positions, triangles);
}

/** Most midpoint-subdivision levels in one step (each quadruples the triangles). */
export const MAX_SUBDIVISION_LEVELS = 2;

/**
 * A new mesh with every triangle split into four, `levels` times, by joining the midpoints of its edges. New vertices lie
 * on the old edges, so the shape does not change (a coarse mesh stays faceted until sculpted or smoothed). Original
 * vertices keep their indices and positions.
 *
 * @throws Error when `levels` is out of range or the result would have more than {@link MAX_VERTICES} vertices
 */
export function subdivide(mesh: SculptMesh, levels: number): SculptMesh {
  if (!Number.isInteger(levels) || levels < 1 || levels > MAX_SUBDIVISION_LEVELS) {
    throw new Error(`levels must be a whole number from 1 to ${MAX_SUBDIVISION_LEVELS}: ${levels}`);
  }
  let current = mesh;
  for (let level = 0; level < levels; level++) {
    const next = subdivideOnce(current);
    current = next;
  }
  return current;
}

function subdivideOnce(mesh: SculptMesh): SculptMesh {
  const vertexCount = mesh.vertexCount;
  // Each shared edge becomes exactly one vertex, found by its two end vertices
  const midpoints = new Map<number, number>();
  const extra: number[] = [];
  const projectedEdges = (mesh.triangleCount * 3) / 2; // closed meshes: every edge is shared by two triangles
  if (vertexCount + projectedEdges > MAX_VERTICES) {
    throw new Error(`Subdividing ${mesh.triangleCount} triangles would make about ${Math.round(vertexCount + projectedEdges)} vertices; the web editor handles at most ${MAX_VERTICES}`);
  }
  const midpoint = (a: number, b: number): number => {
    const key = a < b ? a * vertexCount + b : b * vertexCount + a;
    let index = midpoints.get(key);
    if (index === undefined) {
      index = vertexCount + extra.length / 3;
      midpoints.set(key, index);
      for (let k = 0; k < 3; k++) extra.push((mesh.positions[3 * a + k]! + mesh.positions[3 * b + k]!) / 2);
    }
    return index;
  };
  const triangles: number[] = [];
  for (let t = 0; t < mesh.triangles.length; t += 3) {
    const a = mesh.triangles[t]!;
    const b = mesh.triangles[t + 1]!;
    const c = mesh.triangles[t + 2]!;
    const ab = midpoint(a, b);
    const bc = midpoint(b, c);
    const ca = midpoint(c, a);
    triangles.push(a, ab, ca, ab, b, bc, ca, bc, c, ab, bc, ca);
  }
  if (vertexCount + extra.length / 3 > MAX_VERTICES) {
    throw new Error(`Subdividing would make ${vertexCount + extra.length / 3} vertices; the web editor handles at most ${MAX_VERTICES}`);
  }
  const positions = new Float32Array(mesh.positions.length + extra.length);
  positions.set(mesh.positions);
  positions.set(extra, mesh.positions.length);
  return new SculptMesh(positions, new Uint32Array(triangles));
}
