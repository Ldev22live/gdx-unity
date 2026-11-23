import type { Instrument } from "./dsp";
import { noteToHz } from "./synth";

/**
 * Settings of the plucked-string instrument (TD-075), a Karplus-Strong string: the same fields, defaults and presets as the desktop's
 * `PluckPatch`. `decay` is the seconds a held string takes to fall 60 dB, `damping` how much high end each trip round the loop loses,
 * `brightness` how bright the first pluck is, and `release` the seconds a released string takes to fall 60 dB.
 */
export interface PluckPatch {
  decay: number;
  damping: number;
  brightness: number;
  release: number;
  volume: number;
}

export function defaultPluckPatch(): PluckPatch {
  return { decay: 2, damping: 0.5, brightness: 0.7, release: 0.15, volume: 0.6 };
}

export const PLUCK_PRESET_NAMES = ["Nylon Guitar", "Harp", "Pizzicato"] as const;

/** A named starting sound; the same values as the desktop's (TD-075 checks them against each other). */
export function pluckPreset(name: string): PluckPatch {
  const p = defaultPluckPatch();
  switch (name) {
    case "Nylon Guitar":
      Object.assign(p, { decay: 2.5, damping: 0.5, brightness: 0.6, release: 0.15 });
      break;
    case "Harp":
      Object.assign(p, { decay: 4, damping: 0.25, brightness: 0.8, release: 0.3 });
      break;
    case "Pizzicato":
      Object.assign(p, { decay: 0.6, damping: 0.7, brightness: 0.5, release: 0.08 });
      break;
    default:
      throw new Error(`Unknown plucked-string preset: ${name}`);
  }
  return p;
}

/** Strings that can sound at once; the oldest is taken over beyond that. */
export const MAX_PLUCK_VOICES = 16;
/** The longest delay line is for this pitch; lower notes are held at it. */
const LOWEST_HZ = 20;
/** Spare slots in the delay line so a read never meets the slot being written. */
const BUFFER_MARGIN = 4;
/** The shortest loop, in samples, so very high notes stay a loop and not a click. */
const MIN_DELAY_SAMPLES = 2;
/** The all-pass is used for a delay between this and one more sample, where it behaves best. */
const ALLPASS_MIN_DELAY = 0.5;
/** The loop's averaging filter delays the signal by half a sample at full damping; the delay line is made that much shorter. */
const DAMPING_DELAY_SAMPLES = 0.5;
/** A fall of 60 dB, as a gain. */
const SIXTY_DB = 0.001;
/** A note is cut off this many decay times after it starts (the string is about 90 dB down by then). */
const END_AFTER_DECAYS = 1.5;
/** The shortest decay and release, so a zero setting cannot divide by zero. */
const MIN_SECONDS = 0.01;
/** Lowest share of the noise a burst keeps passing through its low-pass, so a brightness of 0 still has some attack. */
const MIN_BRIGHTNESS_COEFFICIENT = 0.05;
/** Each voice is scaled down so a chord of several does not clip. */
const VOICE_HEADROOM = 0.5;
const NOISE_SEED = 0x2545f491;
const NOISE_PITCH_SPREAD = 0x9e3779b1;
const LCG_MULTIPLIER = 1664525;
const LCG_INCREMENT = 1013904223;
const TWO_TO_24 = 16777216;

class PluckVoice {
  readonly line: Float32Array;
  active = false;
  released = false;
  pitch = 0;
  velocity = 0;
  order = 0;
  write = 0;
  wholeDelay = 0;
  allpassCoefficient = 0;
  allpassIn = 0;
  allpassOut = 0;
  previous = 0;
  loopGain = 0;
  releaseGain = 1;
  age = 0;
  lastFrame = 0;
  damping = 0;

  constructor(
    private readonly sampleRate: number,
    private readonly patch: PluckPatch,
    bufferSize: number,
  ) {
    this.line = new Float32Array(bufferSize);
  }

  start(note: number, velocity: number, sequence: number): void {
    this.pitch = note;
    this.velocity = velocity;
    this.order = sequence;
    this.active = true;
    this.released = false;
    this.age = 0;
    this.releaseGain = 1;
    this.previous = 0;
    this.allpassIn = 0;
    this.allpassOut = 0;
    this.write = 0;
    this.damping = Math.min(Math.max(this.patch.damping, 0), 1);
    const hz = Math.max(noteToHz(note), LOWEST_HZ);
    const delay = Math.min(
      Math.max(this.sampleRate / hz - DAMPING_DELAY_SAMPLES * this.damping, MIN_DELAY_SAMPLES),
      this.line.length - BUFFER_MARGIN - 1,
    );
    this.wholeDelay = Math.floor(delay - ALLPASS_MIN_DELAY);
    const fraction = delay - this.wholeDelay;
    this.allpassCoefficient = (1 - fraction) / (1 + fraction);
    const decaySeconds = Math.max(this.patch.decay, MIN_SECONDS);
    this.loopGain = Math.pow(SIXTY_DB, 1 / (decaySeconds * hz));
    this.lastFrame = Math.floor(decaySeconds * END_AFTER_DECAYS * this.sampleRate);
    this.pluck();
  }

  /** Fills the delay line with a burst of noise, low-passed more the lower the brightness. */
  private pluck(): void {
    const coefficient = MIN_BRIGHTNESS_COEFFICIENT + (1 - MIN_BRIGHTNESS_COEFFICIENT) * Math.min(Math.max(this.patch.brightness, 0), 1);
    // 32-bit integer arithmetic, so it is the same sequence as the desktop's
    let state = (NOISE_SEED + Math.imul(this.pitch, NOISE_PITCH_SPREAD)) | 0;
    let smooth = 0;
    for (let i = 0; i < this.line.length; i++) {
      state = (Math.imul(state, LCG_MULTIPLIER) + LCG_INCREMENT) | 0;
      const noise = ((state >>> 8) / TWO_TO_24) * 2 - 1;
      smooth += coefficient * (noise - smooth);
      this.line[i] = smooth;
    }
  }

  render(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    const patch = this.patch;
    const gain = patch.volume * this.velocity * VOICE_HEADROOM;
    const releaseStep = Math.pow(SIXTY_DB, 1 / (Math.max(patch.release, MIN_SECONDS) * this.sampleRate));
    const size = this.line.length;
    for (let i = 0; i < frames; i++) {
      let readAt = this.write - this.wholeDelay;
      if (readAt < 0) readAt += size;
      const whole = this.line[readAt]!;
      const delayed = this.allpassCoefficient * whole + this.allpassIn - this.allpassCoefficient * this.allpassOut;
      this.allpassIn = whole;
      this.allpassOut = delayed;
      const filtered = (1 - this.damping) * delayed + this.damping * 0.5 * (delayed + this.previous);
      this.previous = delayed;
      this.line[this.write] = filtered * this.loopGain;
      this.write = this.write + 1 === size ? 0 : this.write + 1;

      const sample = delayed * this.releaseGain * gain;
      left[offset + i]! += sample;
      right[offset + i]! += sample;
      if (this.released) this.releaseGain *= releaseStep;
      this.age++;
      if (this.age >= this.lastFrame || this.releaseGain < SIXTY_DB) {
        this.active = false;
        break;
      }
    }
  }
}

/** A polyphonic plucked-string instrument (Karplus-Strong). The patch is read live. */
export class PluckedString implements Instrument {
  private readonly voices: PluckVoice[];
  private noteCounter = 0;

  constructor(
    sampleRate: number,
    readonly patch: PluckPatch,
  ) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
    const bufferSize = Math.ceil(sampleRate / LOWEST_HZ) + BUFFER_MARGIN;
    this.voices = Array.from({ length: MAX_PLUCK_VOICES }, () => new PluckVoice(sampleRate, patch, bufferSize));
  }

  get activeVoiceCount(): number {
    return this.voices.filter((voice) => voice.active).length;
  }

  noteOn(pitch: number, velocity: number): void {
    this.chooseVoice(pitch).start(pitch, Math.min(Math.max(velocity, 0), 1), ++this.noteCounter);
  }

  noteOff(pitch: number): void {
    for (const voice of this.voices) if (voice.active && voice.pitch === pitch && !voice.released) voice.released = true;
  }

  allNotesOff(): void {
    for (const voice of this.voices) if (voice.active) voice.released = true;
  }

  private chooseVoice(pitch: number): PluckVoice {
    let oldest: PluckVoice | null = null;
    for (const voice of this.voices) {
      if (!voice.active || voice.pitch === pitch) return voice;
      if (oldest === null || voice.order < oldest.order) oldest = voice;
    }
    return oldest!;
  }

  render(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    for (const voice of this.voices) if (voice.active) voice.render(left, right, offset, frames);
  }
}
