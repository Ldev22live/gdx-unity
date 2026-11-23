/** Points closer than this (meters) count as one; the same value as the desktop's `ColliderPoints3D`. */
const MERGE_DISTANCE = 1e-4;

/** Most points a convex hull is made from; Jolt's limit, and the desktop's `PhysicsSystem3D.MAX_HULL_POINTS`. */
export const MAX_HULL_POINTS = 64;

/**
 * Removes duplicate points and, when more than {@code maxPoints} remain, keeps the {@code maxPoints} that spread out the most
 * (farthest-point sampling, starting from the point farthest from the center), which keeps the extreme corners a hull is made of.
 * The same algorithm as the desktop's `ColliderPoints3D.reduce`.
 *
 * @param points x, y, z per point
 * @returns x, y, z of the kept points
 */
export function reduceHullPoints(points: ArrayLike<number>, maxPoints: number = MAX_HULL_POINTS): Float32Array {
  const unique: [number, number, number][] = [];
  const seen = new Set<string>();
  for (let i = 0; i + 2 < points.length; i += 3) {
    const key = `${Math.round(points[i]! / MERGE_DISTANCE)},${Math.round(points[i + 1]! / MERGE_DISTANCE)},${Math.round(points[i + 2]! / MERGE_DISTANCE)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push([points[i]!, points[i + 1]!, points[i + 2]!]);
  }
  const kept = unique.length <= maxPoints ? unique : sample(unique, maxPoints);
  const result = new Float32Array(kept.length * 3);
  kept.forEach((point, i) => result.set(point, i * 3));
  return result;
}

function sample(points: [number, number, number][], count: number): [number, number, number][] {
  let cx = 0;
  let cy = 0;
  let cz = 0;
  for (const [x, y, z] of points) {
    cx += x;
    cy += y;
    cz += z;
  }
  cx /= points.length;
  cy /= points.length;
  cz /= points.length;
  let next = 0;
  let best = -1;
  points.forEach((point, i) => {
    const distance = squared(point, cx, cy, cz);
    if (distance > best) {
      best = distance;
      next = i;
    }
  });
  const distance = new Float64Array(points.length).fill(Number.MAX_VALUE);
  const kept: [number, number, number][] = [];
  while (kept.length < count) {
    const chosen = points[next]!;
    kept.push(chosen);
    best = -1;
    for (let i = 0; i < points.length; i++) {
      distance[i] = Math.min(distance[i]!, squared(points[i]!, chosen[0], chosen[1], chosen[2]));
      if (distance[i]! > best) {
        best = distance[i]!;
        next = i;
      }
    }
  }
  return kept;
}

function squared(p: [number, number, number], x: number, y: number, z: number): number {
  const dx = p[0] - x;
  const dy = p[1] - y;
  const dz = p[2] - z;
  return dx * dx + dy * dy + dz * dz;
}
