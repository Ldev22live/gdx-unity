import { Adsr, type Instrument } from "./dsp";
import { noteToHz } from "./synth";

/**
 * Settings of the FM synthesizer (TD-075): the same fields, defaults and presets as the desktop's `FmPatch`. A sine carrier has its
 * phase bent by a sine modulator running at `ratio` times the note's frequency; `index` is how far it bends at the start of a note,
 * and the bend falls towards `indexSustain` of that over `indexDecay` seconds.
 */
export interface FmPatch {
  ratio: number;
  index: number;
  indexSustain: number;
  indexDecay: number;
  attack: number;
  decay: number;
  sustain: number;
  release: number;
  volume: number;
}

export function defaultFmPatch(): FmPatch {
  return { ratio: 2, index: 2, indexSustain: 0.2, indexDecay: 0.5, attack: 0.005, decay: 0.6, sustain: 0.3, release: 0.4, volume: 0.6 };
}

export const FM_PRESET_NAMES = ["Bell", "FM Piano", "Glass", "Metal Pad", "FM Bass"] as const;

/** A named starting sound; the same values as the desktop's (TD-075 checks them against each other). */
export function fmPreset(name: string): FmPatch {
  const p = defaultFmPatch();
  switch (name) {
    case "Bell":
      Object.assign(p, { ratio: 3.5, index: 4, indexSustain: 0.1, indexDecay: 1.2, attack: 0.001, decay: 2.5, sustain: 0, release: 1 });
      break;
    case "FM Piano":
      Object.assign(p, { ratio: 1, index: 2.2, indexSustain: 0.15, indexDecay: 0.4, attack: 0.002, decay: 1.4, sustain: 0.1, release: 0.3 });
      break;
    case "Glass":
      Object.assign(p, { ratio: 5, index: 2, indexSustain: 0.5, indexDecay: 0.8, attack: 0.02, decay: 1, sustain: 0.5, release: 0.8, volume: 0.5 });
      break;
    case "Metal Pad":
      Object.assign(p, { ratio: 1.41, index: 3, indexSustain: 0.6, indexDecay: 2, attack: 0.4, decay: 0.6, sustain: 0.8, release: 1.2, volume: 0.5 });
      break;
    case "FM Bass":
      Object.assign(p, { ratio: 1, index: 3, indexSustain: 0.3, indexDecay: 0.15, attack: 0.001, decay: 0.2, sustain: 0.8, release: 0.1, volume: 0.7 });
      break;
    default:
      throw new Error(`Unknown FM preset: ${name}`);
  }
  return p;
}

/** Notes that can sound at once; the oldest is taken over beyond that. */
export const MAX_FM_VOICES = 16;
/** Each voice is scaled down so a chord of several does not clip. */
const VOICE_HEADROOM = 0.5;
/** The modulator cannot run faster than half the sample rate; beyond that it would only fold back as noise. */
const MAX_PHASE_STEP = 0.5;
/** Shortest modulation fall, so a zero setting cannot divide by zero. */
const MIN_INDEX_DECAY = 0.001;
const TWO_PI = 2 * Math.PI;

class FmVoice {
  readonly envelope: Adsr;
  active = false;
  released = false;
  pitch = 0;
  velocity = 0;
  order = 0;
  carrierPhase = 0;
  modulatorPhase = 0;
  /** 1 at the start of the note, falling towards 0; the modulation depth follows it. */
  fall = 1;

  constructor(
    private readonly sampleRate: number,
    private readonly patch: FmPatch,
  ) {
    this.envelope = new Adsr(sampleRate);
  }

  start(note: number, velocity: number, sequence: number): void {
    this.pitch = note;
    this.velocity = velocity;
    this.order = sequence;
    this.active = true;
    this.released = false;
    this.carrierPhase = 0;
    this.modulatorPhase = 0;
    this.fall = 1;
    this.envelope.reset();
    this.envelope.set(this.patch.attack, this.patch.decay, this.patch.sustain, this.patch.release);
    this.envelope.noteOn();
  }

  release(): void {
    this.released = true;
    this.envelope.noteOff();
  }

  render(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    const patch = this.patch;
    const hz = noteToHz(this.pitch);
    const carrierStep = Math.min(hz / this.sampleRate, MAX_PHASE_STEP);
    const modulatorStep = Math.min((hz * patch.ratio) / this.sampleRate, MAX_PHASE_STEP);
    const fallStep = Math.exp(-1 / (Math.max(patch.indexDecay, MIN_INDEX_DECAY) * this.sampleRate));
    const share = Math.min(Math.max(patch.indexSustain, 0), 1);
    const gain = patch.volume * this.velocity * VOICE_HEADROOM;
    for (let i = 0; i < frames; i++) {
      const level = this.envelope.next();
      this.fall *= fallStep;
      const depth = patch.index * (share + (1 - share) * this.fall);
      const bend = depth * Math.sin(TWO_PI * this.modulatorPhase);
      const sample = Math.sin(TWO_PI * this.carrierPhase + bend) * level * gain;
      left[offset + i]! += sample;
      right[offset + i]! += sample;
      this.carrierPhase += carrierStep;
      if (this.carrierPhase >= 1) this.carrierPhase -= 1;
      this.modulatorPhase += modulatorStep;
      if (this.modulatorPhase >= 1) this.modulatorPhase -= 1;
      if (this.envelope.isFinished) {
        this.active = false;
        break;
      }
    }
  }
}

/** A polyphonic two-operator FM synthesizer. The patch is read live, so editing it changes notes that are already sounding. */
export class FmSynthesizer implements Instrument {
  private readonly voices: FmVoice[];
  private noteCounter = 0;

  constructor(
    sampleRate: number,
    readonly patch: FmPatch,
  ) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
    this.voices = Array.from({ length: MAX_FM_VOICES }, () => new FmVoice(sampleRate, patch));
  }

  get activeVoiceCount(): number {
    return this.voices.filter((voice) => voice.active).length;
  }

  noteOn(pitch: number, velocity: number): void {
    this.chooseVoice(pitch).start(pitch, Math.min(Math.max(velocity, 0), 1), ++this.noteCounter);
  }

  noteOff(pitch: number): void {
    for (const voice of this.voices) if (voice.active && voice.pitch === pitch && !voice.released) voice.release();
  }

  allNotesOff(): void {
    for (const voice of this.voices) if (voice.active && !voice.released) voice.release();
  }

  private chooseVoice(pitch: number): FmVoice {
    let oldest: FmVoice | null = null;
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
