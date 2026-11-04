/**
 * Signal-processing building blocks of the music engine: oscillator, envelope and filter. No DOM and no Web Audio in
 * here, so the same code runs in an AudioWorklet, on the main thread (offline export) and in tests.
 *
 * Port of the desktop engine's `engine-audio` (Oscillator, Adsr, BiquadFilter).
 */

export type Waveform = "SINE" | "SAW" | "SQUARE" | "TRIANGLE" | "NOISE";
export const WAVEFORMS: readonly Waveform[] = ["SINE", "SAW", "SQUARE", "TRIANGLE", "NOISE"];

/** Seed of the noise generator, as high and low 32-bit halves of 0x2545F4914F6CDD1D. */
const NOISE_SEED_HIGH = 0x2545f491;
const NOISE_SEED_LOW = 0x4f6cdd1d;
const TWO_PI = 2 * Math.PI;
/** 2^21 and 2^53: scale the 53 high bits of the noise state to -1..1. */
const TWO_POW_21 = 2097152;
const TWO_POW_53 = 9007199254740992;

/**
 * One band-limited oscillator. Saw and square use PolyBLEP to cut aliasing; noise is a deterministic xorshift64
 * generator (done in two 32-bit halves), so renders repeat exactly, as on the desktop.
 */
export class Oscillator {
  private phase = 0;
  private noiseHigh = NOISE_SEED_HIGH;
  private noiseLow = NOISE_SEED_LOW;

  /** Starts the cycle at `cycles` (0..1), so voices can start in phase or spread. */
  setPhase(cycles: number): void {
    this.phase = cycles - Math.floor(cycles);
  }

  /**
   * Next sample in -1..1.
   *
   * @param phaseStep cycles per sample (frequency / sample rate), 0..0.5
   * @param pulseWidth share of the cycle a square wave is high, clamped to 0.05..0.95
   */
  next(waveform: Waveform, phaseStep: number, pulseWidth: number): number {
    const dt = Math.min(Math.max(phaseStep, 0), 0.5);
    const t = this.phase;
    let value: number;
    switch (waveform) {
      case "SINE":
        value = Math.sin(TWO_PI * t);
        break;
      case "SAW":
        value = 2 * t - 1 - polyBlep(t, dt);
        break;
      case "SQUARE": {
        const width = Math.min(Math.max(pulseWidth, 0.05), 0.95);
        value = t < width ? 1 : -1;
        value += polyBlep(t, dt);
        value -= polyBlep((t - width + 1) % 1, dt);
        break;
      }
      case "TRIANGLE":
        value = 4 * Math.abs(t - 0.5) - 1;
        break;
      case "NOISE":
        value = this.nextNoise();
        break;
      default:
        throw new Error(`Unknown waveform ${String(waveform)}`);
    }
    this.phase += dt;
    if (this.phase >= 1) this.phase -= 1;
    return value;
  }

  private nextNoise(): number {
    let high = this.noiseHigh;
    let low = this.noiseLow;
    // x ^= x << 13
    high = (high ^ ((high << 13) | (low >>> 19))) >>> 0;
    low = (low ^ (low << 13)) >>> 0;
    // x ^= x >>> 7
    low = (low ^ ((low >>> 7) | (high << 25))) >>> 0;
    high = (high ^ (high >>> 7)) >>> 0;
    // x ^= x << 17
    high = (high ^ ((high << 17) | (low >>> 15))) >>> 0;
    low = (low ^ (low << 17)) >>> 0;
    this.noiseHigh = high;
    this.noiseLow = low;
    // (state >>> 11) as a 53-bit number, mapped to -1..1
    const top53 = high * TWO_POW_21 + (low >>> 11);
    return top53 * (2 / TWO_POW_53) - 1;
  }
}

function polyBlep(t: number, dt: number): number {
  if (dt <= 0) return 0;
  if (t < dt) {
    const x = t / dt;
    return x + x - x * x - 1;
  }
  if (t > 1 - dt) {
    const x = (t - 1) / dt;
    return x * x + x + x + 1;
  }
  return 0;
}

export type AdsrStage = "IDLE" | "ATTACK" | "DECAY" | "SUSTAIN" | "RELEASE";

/** The release is over when the level falls under this. */
const ENVELOPE_SILENT = 0.0001;
/** Smallest stage time in seconds, so no stage divides by zero. */
const MIN_STAGE_SECONDS = 0.001;
/** The release curve falls by this factor (-80 dB) over the release time. */
const RELEASE_FALL = 10000;

/** Attack-decay-sustain-release envelope: linear attack and decay, exponential release. */
export class Adsr {
  private attack = 0.01;
  private decay = 0.1;
  private sustain = 0.7;
  private release = 0.2;
  private stage: AdsrStage = "IDLE";
  private level = 0;
  private releaseCoefficient = 0;

  constructor(private readonly sampleRate: number) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
  }

  /** Sets the times in seconds and the sustain level (0..1). */
  set(attackSeconds: number, decaySeconds: number, sustainLevel: number, releaseSeconds: number): void {
    this.attack = Math.max(attackSeconds, MIN_STAGE_SECONDS);
    this.decay = Math.max(decaySeconds, MIN_STAGE_SECONDS);
    this.sustain = Math.min(Math.max(sustainLevel, 0), 1);
    this.release = Math.max(releaseSeconds, MIN_STAGE_SECONDS);
  }

  /** Starts the attack from the current level, so a retrigger does not click. */
  noteOn(): void {
    this.stage = "ATTACK";
  }

  /** Starts the release from the current level. */
  noteOff(): void {
    if (this.stage === "IDLE" || this.stage === "RELEASE") return;
    this.stage = "RELEASE";
    this.releaseCoefficient = Math.exp(-Math.log(RELEASE_FALL) / (this.release * this.sampleRate));
  }

  get isFinished(): boolean {
    return this.stage === "IDLE";
  }

  get currentStage(): AdsrStage {
    return this.stage;
  }

  get currentLevel(): number {
    return this.level;
  }

  /** Level for the next sample, 0..1. */
  next(): number {
    switch (this.stage) {
      case "IDLE":
        break;
      case "ATTACK":
        this.level += 1 / (this.attack * this.sampleRate);
        if (this.level >= 1) {
          this.level = 1;
          this.stage = "DECAY";
        }
        break;
      case "DECAY":
        this.level -= (1 - this.sustain) / (this.decay * this.sampleRate);
        if (this.level <= this.sustain) {
          this.level = this.sustain;
          this.stage = "SUSTAIN";
        }
        break;
      case "SUSTAIN":
        this.level = this.sustain;
        break;
      case "RELEASE":
        this.level *= this.releaseCoefficient;
        if (this.level < ENVELOPE_SILENT) {
          this.level = 0;
          this.stage = "IDLE";
        }
        break;
    }
    return this.level;
  }

  /** Back to silence with no release. */
  reset(): void {
    this.stage = "IDLE";
    this.level = 0;
  }
}

export type FilterType = "LOW_PASS" | "HIGH_PASS" | "BAND_PASS";
export const FILTER_TYPES: readonly FilterType[] = ["LOW_PASS", "HIGH_PASS", "BAND_PASS"];

/** Lowest cutoff, Hz. */
export const MIN_CUTOFF = 20;
/** Highest cutoff as a share of the sample rate (stays below Nyquist). */
const MAX_CUTOFF_SHARE = 0.45;
/** Lowest resonance; 0.707 is flat. */
export const MIN_Q = 0.5;
/** Highest resonance. */
export const MAX_Q = 20;

/** A resonant two-pole filter (RBJ biquad). One instance filters one channel of audio. */
export class BiquadFilter {
  private b0 = 1;
  private b1 = 0;
  private b2 = 0;
  private a1 = 0;
  private a2 = 0;
  private z1 = 0;
  private z2 = 0;

  constructor(private readonly sampleRate: number) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
  }

  /** Recomputes the coefficients; cutoff and Q are clamped to usable ranges. */
  set(type: FilterType, cutoffHz: number, q: number): void {
    const cutoff = Math.min(Math.max(cutoffHz, MIN_CUTOFF), this.sampleRate * MAX_CUTOFF_SHARE);
    const quality = Math.min(Math.max(q, MIN_Q), MAX_Q);
    const w0 = (TWO_PI * cutoff) / this.sampleRate;
    const cos = Math.cos(w0);
    const alpha = Math.sin(w0) / (2 * quality);
    const a0 = 1 + alpha;
    let nb0: number;
    let nb1: number;
    let nb2: number;
    switch (type) {
      case "LOW_PASS":
        nb0 = (1 - cos) / 2;
        nb1 = 1 - cos;
        nb2 = nb0;
        break;
      case "HIGH_PASS":
        nb0 = (1 + cos) / 2;
        nb1 = -(1 + cos);
        nb2 = nb0;
        break;
      case "BAND_PASS":
        nb0 = alpha;
        nb1 = 0;
        nb2 = -alpha;
        break;
      default:
        throw new Error(`Unknown filter type ${String(type)}`);
    }
    this.b0 = nb0 / a0;
    this.b1 = nb1 / a0;
    this.b2 = nb2 / a0;
    this.a1 = (-2 * cos) / a0;
    this.a2 = (1 - alpha) / a0;
  }

  process(x: number): number {
    const y = this.b0 * x + this.z1;
    this.z1 = this.b1 * x - this.a1 * y + this.z2;
    this.z2 = this.b2 * x - this.a2 * y;
    return y;
  }

  /** Clears the memory, so a new note does not start with the last one's tail. */
  reset(): void {
    this.z1 = 0;
    this.z2 = 0;
  }
}

/** Something that turns note events into audio: a synth or a drum kit. */
export interface Instrument {
  /** Starts a note. `pitch` is a MIDI note number (60 = middle C), `velocity` 0..1. */
  noteOn(pitch: number, velocity: number): void;
  /** Releases a note started with noteOn; the sound may ring on through its release. */
  noteOff(pitch: number): void;
  /** Releases every sounding note. */
  allNotesOff(): void;
  /** Adds `frames` frames of sound into `left` and `right` starting at `offset`. */
  render(left: Float32Array, right: Float32Array, offset: number, frames: number): void;
}
