/** A 3D point or direction. */
export interface Vec3Like {
  x: number;
  y: number;
  z: number;
}

/** What a listener hears of a sound at a place: a volume multiplier 0..1 and a pan -1 (left) to 1 (right). */
export interface Heard {
  gain: number;
  pan: number;
}

/** Closer than this to the listener counts as the same place, so the pan does not jump around at zero. */
const SAME_PLACE = 1e-4;

/**
 * How a sound at a place is heard from another (Phase 20-C): full volume up to {@code minDistance}, a straight line down to
 * silence at {@code maxDistance}, and a pan that follows how far to the listener's right the sound is. The same rules as the
 * desktop's `SpatialAudio`; height and depth do not pan.
 *
 * @throws Error unless 0 <= minDistance < maxDistance, or when {@code right} is the zero vector
 */
export function hear(listener: Vec3Like, right: Vec3Like, source: Vec3Like, minDistance: number, maxDistance: number): Heard {
  if (!(minDistance >= 0 && maxDistance > minDistance)) {
    throw new Error(`Distances must satisfy 0 <= min < max: ${minDistance}, ${maxDistance}`);
  }
  const rightLength = Math.hypot(right.x, right.y, right.z);
  if (rightLength === 0) throw new Error("right cannot be the zero vector");
  const dx = source.x - listener.x;
  const dy = source.y - listener.y;
  const dz = source.z - listener.z;
  const distance = Math.hypot(dx, dy, dz);
  const gain = distance <= minDistance ? 1 : distance >= maxDistance ? 0 : 1 - (distance - minDistance) / (maxDistance - minDistance);
  const pan = distance < SAME_PLACE ? 0 : (dx * right.x + dy * right.y + dz * right.z) / (rightLength * distance);
  return { gain, pan: Math.max(-1, Math.min(1, pan)) };
}
