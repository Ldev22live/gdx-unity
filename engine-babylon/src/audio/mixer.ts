import type { Effect } from "./effects";

/** Number of insert strips a song has (plus the master). */
export const DEFAULT_STRIPS = 8;
/** Most effects on one strip. */
export const MAX_EFFECTS = 4;
/** Highest strip volume; 1 is unity gain. */
export const MAX_VOLUME = 1.5;
/** Output below this is untouched; above it is bent smoothly towards 1 by the soft limiter. */
export const LIMITER_THRESHOLD = 0.8;
/** Meters fall by 3 per second (a full-scale peak is gone in a third of a second). */
const METER_FALL_PER_SECOND = 3;

/** Something that fills one mixer strip with sound. */
export interface StripSource {
  renderStrip(stripIndex: number, left: Float32Array, right: Float32Array, offset: number, frames: number): void;
}

/** One insert on the mixer: volume, pan, mute, solo, an effect chain and a peak meter. */
export class Strip {
  readonly effects: Effect[] = [];
  volume = 0.8;
  pan = 0;
  muted = false;
  solo = false;
  peakLeft = 0;
  peakRight = 0;

  constructor(
    public name: string,
    private readonly sampleRate: number,
  ) {}

  setVolume(volume: number): void {
    this.volume = Math.min(Math.max(volume, 0), MAX_VOLUME);
  }

  setPan(pan: number): void {
    this.pan = Math.min(Math.max(pan, -1), 1);
  }

  addEffect(effect: Effect): void {
    if (this.effects.length >= MAX_EFFECTS) throw new Error(`A strip holds at most ${MAX_EFFECTS} effects`);
    this.effects.push(effect);
  }

  removeEffect(effect: Effect): boolean {
    const index = this.effects.indexOf(effect);
    if (index < 0) return false;
    this.effects.splice(index, 1);
    return true;
  }

  processEffects(left: Float32Array, right: Float32Array, frames: number): void {
    for (const effect of this.effects) effect.process(left, right, 0, frames);
  }

  meter(left: number, right: number, frames: number): void {
    const fall = Math.max(0, 1 - (METER_FALL_PER_SECOND * frames) / this.sampleRate);
    this.peakLeft = Math.max(left, this.peakLeft * fall);
    this.peakRight = Math.max(right, this.peakRight * fall);
  }
}

/** A gentle limiter: linear up to the threshold, then bent towards 1, so summed channels cannot clip. */
export function softLimit(x: number): number {
  const magnitude = Math.abs(x);
  if (magnitude <= LIMITER_THRESHOLD) return x;
  const room = 1 - LIMITER_THRESHOLD;
  const limited = LIMITER_THRESHOLD + room * Math.tanh((magnitude - LIMITER_THRESHOLD) / room);
  return x < 0 ? -limited : limited;
}

/**
 * Sums the instruments of a song through insert strips (volume, constant-power pan, effects, mute and solo), then a
 * master strip and the soft limiter.
 */
export class Mixer {
  readonly strips: Strip[];
  readonly master: Strip;
  private stripLeft = new Float32Array(0);
  private stripRight = new Float32Array(0);
  private sumLeft = new Float32Array(0);
  private sumRight = new Float32Array(0);

  constructor(
    readonly sampleRate: number,
    stripCount = DEFAULT_STRIPS,
  ) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
    if (stripCount < 1) throw new Error(`stripCount must be at least 1: ${stripCount}`);
    this.strips = Array.from({ length: stripCount }, (_, i) => new Strip(`Insert ${i + 1}`, sampleRate));
    this.master = new Strip("Master", sampleRate);
    this.master.setVolume(1);
  }

  /** Fills `left` and `right` (from `offset`) with the mixed sound of `frames` frames. */
  render(source: StripSource, left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    if (this.stripLeft.length < frames) {
      this.stripLeft = new Float32Array(frames);
      this.stripRight = new Float32Array(frames);
      this.sumLeft = new Float32Array(frames);
      this.sumRight = new Float32Array(frames);
    }
    this.sumLeft.fill(0, 0, frames);
    this.sumRight.fill(0, 0, frames);
    const anySolo = this.strips.some((strip) => strip.solo);
    for (let s = 0; s < this.strips.length; s++) {
      const strip = this.strips[s]!;
      this.stripLeft.fill(0, 0, frames);
      this.stripRight.fill(0, 0, frames);
      // Muted and unsoloed strips still run, so their instruments and effects keep their state
      source.renderStrip(s, this.stripLeft, this.stripRight, 0, frames);
      strip.processEffects(this.stripLeft, this.stripRight, frames);
      this.addToSum(strip, !strip.muted && (!anySolo || strip.solo), frames);
    }
    this.master.processEffects(this.sumLeft, this.sumRight, frames);
    let peakLeft = 0;
    let peakRight = 0;
    const gain = this.master.muted ? 0 : this.master.volume;
    for (let i = 0; i < frames; i++) {
      const l = this.sumLeft[i]! * gain;
      const r = this.sumRight[i]! * gain;
      left[offset + i] = softLimit(l);
      right[offset + i] = softLimit(r);
      peakLeft = Math.max(peakLeft, Math.abs(l));
      peakRight = Math.max(peakRight, Math.abs(r));
    }
    // The meter shows the true level, before the limiter
    this.master.meter(peakLeft, peakRight, frames);
  }

  private addToSum(strip: Strip, audible: boolean, frames: number): void {
    // Constant-power pan: equal loudness anywhere across the field
    const angle = ((strip.pan + 1) * Math.PI) / 4;
    const gainLeft = Math.cos(angle) * Math.SQRT2 * strip.volume;
    const gainRight = Math.sin(angle) * Math.SQRT2 * strip.volume;
    let peakLeft = 0;
    let peakRight = 0;
    for (let i = 0; i < frames; i++) {
      const l = this.stripLeft[i]! * gainLeft;
      const r = this.stripRight[i]! * gainRight;
      peakLeft = Math.max(peakLeft, Math.abs(l));
      peakRight = Math.max(peakRight, Math.abs(r));
      if (audible) {
        this.sumLeft[i]! += l;
        this.sumRight[i]! += r;
      }
    }
    strip.meter(peakLeft, peakRight, frames);
  }
}
