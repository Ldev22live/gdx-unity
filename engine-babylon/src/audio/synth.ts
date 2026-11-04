import { Adsr, BiquadFilter, Oscillator, type FilterType, type Instrument, type Waveform } from "./dsp";

export type LfoTarget = "NONE" | "PITCH" | "CUTOFF" | "VOLUME";
export const LFO_TARGETS: readonly LfoTarget[] = ["NONE", "PITCH", "CUTOFF", "VOLUME"];

/** Settings of the built-in synthesizer: the same fields the desktop writes into a `.song` file. */
export interface SynthPatch {
  osc1: Waveform;
  osc2: Waveform;
  /** Cents. */
  osc2Detune: number;
  osc2Octave: number;
  osc2Mix: number;
  pulseWidth: number;
  attack: number;
  decay: number;
  sustain: number;
  release: number;
  filterType: FilterType;
  cutoff: number;
  resonance: number;
  filterEnvOctaves: number;
  lfoTarget: LfoTarget;
  lfoRate: number;
  lfoDepth: number;
  volume: number;
}

export function defaultPatch(): SynthPatch {
  return {
    osc1: "SAW", osc2: "SAW", osc2Detune: 7, osc2Octave: 0, osc2Mix: 0.5, pulseWidth: 0.5,
    attack: 0.01, decay: 0.2, sustain: 0.7, release: 0.25,
    filterType: "LOW_PASS", cutoff: 8000, resonance: 0.7, filterEnvOctaves: 0,
    lfoTarget: "NONE", lfoRate: 5, lfoDepth: 0, volume: 0.6,
  };
}

export const PRESET_NAMES = ["Saw Lead", "Square Chip", "Sub Bass", "Soft Pad", "Pluck"] as const;

/** A named starting sound; the same five presets as the desktop studio. */
export function presetPatch(name: string): SynthPatch {
  const p = defaultPatch();
  switch (name) {
    case "Saw Lead":
      Object.assign(p, { attack: 0.005, release: 0.2, cutoff: 6000, lfoTarget: "PITCH", lfoRate: 5.5, lfoDepth: 0.12 });
      break;
    case "Square Chip":
      Object.assign(p, { osc1: "SQUARE", osc2: "SQUARE", pulseWidth: 0.25, osc2Mix: 0, attack: 0.002, decay: 0.08, sustain: 0.6, release: 0.08, cutoff: 12000 });
      break;
    case "Sub Bass":
      Object.assign(p, { osc1: "SINE", osc2: "SQUARE", osc2Octave: -1, osc2Mix: 0.35, osc2Detune: 0, attack: 0.004, sustain: 0.9, release: 0.12, cutoff: 900, volume: 0.8 });
      break;
    case "Soft Pad":
      Object.assign(p, { osc1: "SAW", osc2: "TRIANGLE", osc2Detune: 12, attack: 0.6, decay: 0.5, sustain: 0.8, release: 1.2, cutoff: 2500, lfoTarget: "CUTOFF", lfoRate: 0.3, lfoDepth: 0.6, volume: 0.5 });
      break;
    case "Pluck":
      Object.assign(p, { osc1: "TRIANGLE", osc2: "SAW", osc2Mix: 0.3, attack: 0.001, decay: 0.25, sustain: 0, release: 0.15, cutoff: 1200, filterEnvOctaves: 3, resonance: 2 });
      break;
    default:
      throw new Error(`Unknown synth preset: ${name}`);
  }
  return p;
}

/** Most notes one synthesizer sounds at once; the oldest is taken over beyond that. */
export const MAX_VOICES = 16;
/** The filter's coefficients are recomputed every this many samples, not every one. */
const CONTROL_INTERVAL = 16;
const A4_HZ = 440;
const A4_NOTE = 69;
const SEMITONES_PER_OCTAVE = 12;
const CENTS_PER_SEMITONE = 100;
/** Each voice is scaled down so a chord of several does not clip. */
const VOICE_HEADROOM = 0.5;

/** Frequency of a MIDI note (fractional notes allowed). */
export function noteToHz(note: number): number {
  return A4_HZ * Math.pow(2, (note - A4_NOTE) / SEMITONES_PER_OCTAVE);
}

class Voice {
  readonly osc1 = new Oscillator();
  readonly osc2 = new Oscillator();
  readonly lfo = new Oscillator();
  readonly envelope: Adsr;
  readonly filter: BiquadFilter;
  active = false;
  released = false;
  pitch = 0;
  velocity = 0;
  order = 0;

  constructor(
    private readonly sampleRate: number,
    private readonly patch: SynthPatch,
  ) {
    this.envelope = new Adsr(sampleRate);
    this.filter = new BiquadFilter(sampleRate);
  }

  start(note: number, velocity: number, sequence: number): void {
    this.pitch = note;
    this.velocity = velocity;
    this.order = sequence;
    this.active = true;
    this.released = false;
    this.osc1.setPhase(0);
    this.osc2.setPhase(0);
    this.lfo.setPhase(0);
    this.filter.reset();
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
    const hz1 = noteToHz(this.pitch);
    const hz2 = noteToHz(this.pitch + patch.osc2Octave * SEMITONES_PER_OCTAVE + patch.osc2Detune / CENTS_PER_SEMITONE);
    const mix2 = Math.min(Math.max(patch.osc2Mix, 0), 1);
    const gain = patch.volume * this.velocity * VOICE_HEADROOM;
    const lfoStep = patch.lfoRate / this.sampleRate;
    for (let i = 0; i < frames; i++) {
      const lfoValue = patch.lfoTarget === "NONE" ? 0 : this.lfo.next("SINE", lfoStep, 0.5) * patch.lfoDepth;
      const pitchBend = patch.lfoTarget === "PITCH" ? Math.pow(2, lfoValue / SEMITONES_PER_OCTAVE) : 1;
      const level = this.envelope.next();
      if (i % CONTROL_INTERVAL === 0) {
        let octaves = patch.filterEnvOctaves * level;
        if (patch.lfoTarget === "CUTOFF") octaves += lfoValue;
        this.filter.set(patch.filterType, patch.cutoff * Math.pow(2, octaves), patch.resonance);
      }
      const a = this.osc1.next(patch.osc1, (hz1 * pitchBend) / this.sampleRate, patch.pulseWidth);
      const b = this.osc2.next(patch.osc2, (hz2 * pitchBend) / this.sampleRate, patch.pulseWidth);
      let sample = this.filter.process(a * (1 - mix2) + b * mix2) * level * gain;
      if (patch.lfoTarget === "VOLUME") sample *= 1 - Math.abs(lfoValue);
      left[offset + i]! += sample;
      right[offset + i]! += sample;
      if (this.envelope.isFinished) {
        this.active = false;
        break;
      }
    }
  }
}

/**
 * The built-in subtractive synthesizer: 16 voices, two oscillators, a filter with its own envelope amount, and an LFO.
 * The patch is read live, so editing it changes notes that are already sounding.
 */
export class Synthesizer implements Instrument {
  private readonly voices: Voice[];
  private noteCounter = 0;

  constructor(
    sampleRate: number,
    readonly patch: SynthPatch,
  ) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
    this.voices = Array.from({ length: MAX_VOICES }, () => new Voice(sampleRate, patch));
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

  private chooseVoice(pitch: number): Voice {
    let oldest: Voice | null = null;
    for (const voice of this.voices) {
      if (!voice.active) return voice;
      // Retriggering a note takes over its own voice
      if (voice.pitch === pitch) return voice;
      if (oldest === null || voice.order < oldest.order) oldest = voice;
    }
    return oldest!;
  }

  render(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    for (const voice of this.voices) if (voice.active) voice.render(left, right, offset, frames);
  }
}
