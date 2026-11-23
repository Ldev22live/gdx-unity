import { BiquadFilter, FILTER_TYPES, MAX_Q, MIN_CUTOFF, MIN_Q } from "./dsp";

/** An insert effect: processes stereo audio in place. Parameters are plain numbers by name, as in the song file. */
export interface Effect {
  readonly type: EffectType;
  readonly parameterNames: readonly string[];
  getParameter(name: string): number;
  setParameter(name: string, value: number): void;
  process(left: Float32Array, right: Float32Array, offset: number, frames: number): void;
  reset(): void;
}

export type EffectType = "delay" | "reverb" | "filter";
export const EFFECT_TYPES: readonly EffectType[] = ["delay", "reverb", "filter"];

/** Value range and step of each parameter, so the UI can offer a slider without knowing the effect. */
export interface ParameterRange {
  min: number;
  max: number;
  step: number;
}

export const PARAMETER_RANGES: Record<EffectType, Record<string, ParameterRange>> = {
  delay: { time: { min: 0.02, max: 1, step: 0.01 }, feedback: { min: 0, max: 0.95, step: 0.01 }, mix: { min: 0, max: 1, step: 0.01 } },
  reverb: { room: { min: 0, max: 1, step: 0.01 }, damping: { min: 0, max: 1, step: 0.01 }, mix: { min: 0, max: 1, step: 0.01 } },
  filter: { mode: { min: 0, max: 2, step: 1 }, cutoff: { min: MIN_CUTOFF, max: 20000, step: 10 }, resonance: { min: MIN_Q, max: MAX_Q, step: 0.1 } },
};

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/** Longest and shortest echo time, seconds; the most feedback, so echoes always die out. */
const DELAY_MAX_TIME = 1;
const DELAY_MIN_TIME = 0.02;
const DELAY_MAX_FEEDBACK = 0.95;

/** A stereo ping-pong delay: each side feeds the other, so the echoes move across the field. */
export class DelayEffect implements Effect {
  readonly type = "delay";
  readonly parameterNames = ["time", "feedback", "mix"];
  private readonly bufferLeft: Float32Array;
  private readonly bufferRight: Float32Array;
  private writeIndex = 0;
  private time = 0.3;
  private feedback = 0.4;
  private mix = 0.3;

  constructor(private readonly sampleRate: number) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
    const size = Math.floor(DELAY_MAX_TIME * sampleRate) + 1;
    this.bufferLeft = new Float32Array(size);
    this.bufferRight = new Float32Array(size);
  }

  getParameter(name: string): number {
    if (name === "time") return this.time;
    if (name === "feedback") return this.feedback;
    if (name === "mix") return this.mix;
    throw new Error(`Unknown delay parameter: ${name}`);
  }

  setParameter(name: string, value: number): void {
    if (name === "time") this.time = clamp(value, DELAY_MIN_TIME, DELAY_MAX_TIME);
    else if (name === "feedback") this.feedback = clamp(value, 0, DELAY_MAX_FEEDBACK);
    else if (name === "mix") this.mix = clamp(value, 0, 1);
    else throw new Error(`Unknown delay parameter: ${name}`);
  }

  process(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    const size = this.bufferLeft.length;
    const delayFrames = Math.min(Math.floor(this.time * this.sampleRate), size - 1);
    for (let i = 0; i < frames; i++) {
      const read = (this.writeIndex - delayFrames + size) % size;
      const echoLeft = this.bufferLeft[read]!;
      const echoRight = this.bufferRight[read]!;
      const inLeft = left[offset + i]!;
      const inRight = right[offset + i]!;
      this.bufferLeft[this.writeIndex] = inLeft + echoRight * this.feedback;
      this.bufferRight[this.writeIndex] = inRight + echoLeft * this.feedback;
      left[offset + i] = inLeft + echoLeft * this.mix;
      right[offset + i] = inRight + echoRight * this.mix;
      this.writeIndex = (this.writeIndex + 1) % size;
    }
  }

  reset(): void {
    this.bufferLeft.fill(0);
    this.bufferRight.fill(0);
    this.writeIndex = 0;
  }
}

/** A filter insert: mode 0 low pass, 1 high pass, 2 band pass. */
export class FilterEffect implements Effect {
  readonly type = "filter";
  readonly parameterNames = ["mode", "cutoff", "resonance"];
  private readonly left: BiquadFilter;
  private readonly right: BiquadFilter;
  private mode = 0;
  private cutoff = 4000;
  private resonance = 0.7;

  constructor(sampleRate: number) {
    this.left = new BiquadFilter(sampleRate);
    this.right = new BiquadFilter(sampleRate);
    this.apply();
  }

  getParameter(name: string): number {
    if (name === "mode") return this.mode;
    if (name === "cutoff") return this.cutoff;
    if (name === "resonance") return this.resonance;
    throw new Error(`Unknown filter parameter: ${name}`);
  }

  setParameter(name: string, value: number): void {
    if (name === "mode") this.mode = clamp(Math.round(value), 0, FILTER_TYPES.length - 1);
    else if (name === "cutoff") this.cutoff = clamp(value, MIN_CUTOFF, 20000);
    else if (name === "resonance") this.resonance = clamp(value, MIN_Q, MAX_Q);
    else throw new Error(`Unknown filter parameter: ${name}`);
    this.apply();
  }

  private apply(): void {
    this.left.set(FILTER_TYPES[this.mode]!, this.cutoff, this.resonance);
    this.right.set(FILTER_TYPES[this.mode]!, this.cutoff, this.resonance);
  }

  process(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    for (let i = 0; i < frames; i++) {
      left[offset + i] = this.left.process(left[offset + i]!);
      right[offset + i] = this.right.process(right[offset + i]!);
    }
  }

  reset(): void {
    this.left.reset();
    this.right.reset();
  }
}

// Freeverb-style reverb: eight damped feedback combs in parallel, four allpasses in series, per channel.
const COMB_TUNING = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617];
const ALLPASS_TUNING = [556, 441, 341, 225];
/** The right channel's delays are this many samples longer than the left's, which widens the image. */
const STEREO_SPREAD = 23;
/** The tunings above are for this sample rate and are scaled to the actual one. */
const REFERENCE_RATE = 44100;
const ALLPASS_FEEDBACK = 0.5;
const REVERB_INPUT_GAIN = 0.015;
const ROOM_SCALE = 0.28;
const ROOM_OFFSET = 0.7;
const DAMP_SCALE = 0.4;
const WET_GAIN = 3;

class Comb {
  private readonly buffer: Float32Array;
  private index = 0;
  private store = 0;
  feedback = 0;
  damp = 0;

  constructor(size: number) {
    this.buffer = new Float32Array(Math.max(size, 1));
  }

  process(input: number): number {
    const output = this.buffer[this.index]!;
    this.store = output * (1 - this.damp) + this.store * this.damp;
    this.buffer[this.index] = input + this.store * this.feedback;
    this.index = (this.index + 1) % this.buffer.length;
    return output;
  }

  clear(): void {
    this.buffer.fill(0);
    this.store = 0;
    this.index = 0;
  }
}

class Allpass {
  private readonly buffer: Float32Array;
  private index = 0;

  constructor(size: number) {
    this.buffer = new Float32Array(Math.max(size, 1));
  }

  process(input: number): number {
    const buffered = this.buffer[this.index]!;
    const output = -input + buffered;
    this.buffer[this.index] = input + buffered * ALLPASS_FEEDBACK;
    this.index = (this.index + 1) % this.buffer.length;
    return output;
  }

  clear(): void {
    this.buffer.fill(0);
    this.index = 0;
  }
}

/** A reverb for a sense of room: room size, damping (how dull the tail is) and wet mix. */
export class ReverbEffect implements Effect {
  readonly type = "reverb";
  readonly parameterNames = ["room", "damping", "mix"];
  private readonly combsLeft: Comb[];
  private readonly combsRight: Comb[];
  private readonly allpassLeft: Allpass[];
  private readonly allpassRight: Allpass[];
  private room = 0.5;
  private damping = 0.5;
  private mix = 0.25;

  constructor(sampleRate: number) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
    const scale = sampleRate / REFERENCE_RATE;
    this.combsLeft = COMB_TUNING.map((n) => new Comb(Math.round(n * scale)));
    this.combsRight = COMB_TUNING.map((n) => new Comb(Math.round((n + STEREO_SPREAD) * scale)));
    this.allpassLeft = ALLPASS_TUNING.map((n) => new Allpass(Math.round(n * scale)));
    this.allpassRight = ALLPASS_TUNING.map((n) => new Allpass(Math.round((n + STEREO_SPREAD) * scale)));
    this.applyParameters();
  }

  getParameter(name: string): number {
    if (name === "room") return this.room;
    if (name === "damping") return this.damping;
    if (name === "mix") return this.mix;
    throw new Error(`Unknown reverb parameter: ${name}`);
  }

  setParameter(name: string, value: number): void {
    const clamped = clamp(value, 0, 1);
    if (name === "room") this.room = clamped;
    else if (name === "damping") this.damping = clamped;
    else if (name === "mix") this.mix = clamped;
    else throw new Error(`Unknown reverb parameter: ${name}`);
    this.applyParameters();
  }

  private applyParameters(): void {
    const feedback = this.room * ROOM_SCALE + ROOM_OFFSET;
    const damp = this.damping * DAMP_SCALE;
    for (const comb of [...this.combsLeft, ...this.combsRight]) {
      comb.feedback = feedback;
      comb.damp = damp;
    }
  }

  process(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    for (let i = 0; i < frames; i++) {
      const input = (left[offset + i]! + right[offset + i]!) * REVERB_INPUT_GAIN;
      let outLeft = 0;
      let outRight = 0;
      for (let c = 0; c < this.combsLeft.length; c++) {
        outLeft += this.combsLeft[c]!.process(input);
        outRight += this.combsRight[c]!.process(input);
      }
      for (let a = 0; a < this.allpassLeft.length; a++) {
        outLeft = this.allpassLeft[a]!.process(outLeft);
        outRight = this.allpassRight[a]!.process(outRight);
      }
      left[offset + i]! += outLeft * WET_GAIN * this.mix;
      right[offset + i]! += outRight * WET_GAIN * this.mix;
    }
  }

  reset(): void {
    for (const comb of [...this.combsLeft, ...this.combsRight]) comb.clear();
    for (const allpass of [...this.allpassLeft, ...this.allpassRight]) allpass.clear();
  }
}

/** Creates an effect by its song-file type name. */
export function createEffect(type: string, sampleRate: number): Effect {
  switch (type) {
    case "delay":
      return new DelayEffect(sampleRate);
    case "reverb":
      return new ReverbEffect(sampleRate);
    case "filter":
      return new FilterEffect(sampleRate);
    default:
      throw new Error(`Unknown effect type: ${type} (known: ${EFFECT_TYPES.join(", ")})`);
  }
}
