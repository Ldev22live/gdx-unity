import type { GameEntity, GameScene } from "../runtime/GameEntity";
import { num } from "../scene/types";

export const DEGREES_TO_RADIANS = Math.PI / 180;
export const RADIANS_TO_DEGREES = 180 / Math.PI;

export function clamp(value: number, min: number, max: number): number {
  return value < min ? min : value > max ? max : value;
}

/**
 * `java.util.Random` ported exactly (same 48-bit generator), so a script that seeds it plays the same random pattern
 * on the web as on the desktop.
 */
export class JavaRandom {
  private seed: bigint;
  private static readonly MULTIPLIER = 0x5deece66dn;
  private static readonly MASK = (1n << 48n) - 1n;

  constructor(seed: number) {
    this.seed = (BigInt(seed) ^ JavaRandom.MULTIPLIER) & JavaRandom.MASK;
  }

  private next(bits: number): number {
    this.seed = (this.seed * JavaRandom.MULTIPLIER + 0xbn) & JavaRandom.MASK;
    // Take the top `bits` of the 48-bit state as a signed 32-bit value, like the Java original
    return Number(BigInt.asIntN(32, this.seed >> BigInt(48 - bits)));
  }

  nextFloat(): number {
    return this.next(24) / (1 << 24);
  }

  nextInt(bound: number): number {
    if (bound <= 0) throw new Error(`bound must be positive, got ${bound}`);
    if ((bound & -bound) === bound) return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    let bits: number;
    let value: number;
    do {
      bits = this.next(31);
      value = bits % bound;
    } while (bits - value + (bound - 1) < 0 || bits - value + (bound - 1) > 0x7fffffff);
    return value;
  }

  nextBoolean(): boolean {
    return this.next(1) !== 0;
  }
}

/** A Text component's text, set through one object so scripts read like the desktop ones. */
export class TextHandle {
  constructor(private readonly data: Record<string, unknown>) {}

  setText(text: string): void {
    this.data["text"] = text;
  }
}

/** Size of an entity's Shape Renderer (before scale), or fails naming the entity. */
export function shapeSize(entity: GameEntity): { width: number; height: number } {
  const shape = entity.getComponent("ShapeRendererComponent");
  if (!shape) throw new Error(`'${entity.name}' needs a Shape Renderer to have a size`);
  return { width: num(shape.data, "width"), height: num(shape.data, "height") };
}

export function requireEntity(scene: GameScene, name: string, who: string): GameEntity {
  const entity = scene.findByName(name);
  if (!entity) throw new Error(`${who} needs an entity named '${name}'`);
  return entity;
}

export function requireText(scene: GameScene, name: string, who: string): TextHandle {
  const text = requireEntity(scene, name, who).getComponent("TextComponent");
  if (!text) throw new Error(`The scene needs an entity '${name}' with a Text component`);
  return new TextHandle(text.data);
}

/** The four arrow/WASD style direction keys as -1, 0 or 1 on each axis. */
export function keyAxis(isDown: (...codes: string[]) => boolean, negative: string[], positive: string[]): number {
  return (isDown(...positive) ? 1 : 0) - (isDown(...negative) ? 1 : 0);
}
