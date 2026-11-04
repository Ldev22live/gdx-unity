import { BiquadFilter, MIN_Q, Oscillator, type Instrument } from "./dsp";

export type Drum = "KICK" | "SNARE" | "CLAP" | "CLOSED_HAT" | "OPEN_HAT" | "TOM" | "CRASH";

/** Each drum answers to its General MIDI note, as on the desktop. */
export const DRUMS: readonly { drum: Drum; note: number; name: string }[] = [
  { drum: "KICK", note: 36, name: "Kick" },
  { drum: "SNARE", note: 38, name: "Snare" },
  { drum: "CLAP", note: 39, name: "Clap" },
  { drum: "CLOSED_HAT", note: 42, name: "Closed Hat" },
  { drum: "OPEN_HAT", note: 46, name: "Open Hat" },
  { drum: "TOM", note: 45, name: "Tom" },
  { drum: "CRASH", note: 49, name: "Crash" },
];

export function drumForNote(note: number): Drum | null {
  return DRUMS.find((entry) => entry.note === note)?.drum ?? null;
}

/** Hits sounding at once; beyond that the oldest slot is taken over. */
export const MAX_HITS = 24;
const KICK_START_HZ = 160;
const KICK_END_HZ = 45;
const KICK_PITCH_DROP_SECONDS = 0.06;
const SNARE_TONE_HZ = 190;
const CLAP_BURST_SECONDS = 0.012;
const CLAP_BURST_GAP_SECONDS = 0.011;
const TOM_START_HZ = 220;
const TOM_END_HZ = 110;
const HAT_HIGH_PASS_HZ = 7000;
/** The amplitude envelope falls by e^-8 over a drum's length. */
const DECAY_FALL = 8;

const SECONDS: Record<Drum, number> = {
  KICK: 0.45, SNARE: 0.22, CLAP: 0.25, CLOSED_HAT: 0.06, OPEN_HAT: 0.35, TOM: 0.35, CRASH: 1.2,
};

class Hit {
  readonly noise = new Oscillator();
  readonly highPass: BiquadFilter;
  readonly bandPass: BiquadFilter;
  active = false;
  drum: Drum = "KICK";
  velocity = 0;
  frame = 0;
  length = 0;
  phase = 0;

  constructor(private readonly sampleRate: number) {
    this.highPass = new BiquadFilter(sampleRate);
    this.bandPass = new BiquadFilter(sampleRate);
  }

  start(drum: Drum, velocity: number): void {
    this.drum = drum;
    this.velocity = velocity;
    this.frame = 0;
    this.phase = 0;
    this.active = true;
    this.noise.setPhase(0);
    this.highPass.reset();
    this.bandPass.reset();
    this.highPass.set("HIGH_PASS", HAT_HIGH_PASS_HZ, MIN_Q);
    this.bandPass.set("BAND_PASS", 1800, 0.8);
    this.length = Math.floor(SECONDS[drum] * this.sampleRate);
  }

  render(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    for (let i = 0; i < frames; i++) {
      if (this.frame >= this.length) {
        this.active = false;
        return;
      }
      const t = this.frame / this.sampleRate;
      const progress = this.frame / this.length;
      const decay = Math.exp(-DECAY_FALL * progress);
      let sample: number;
      switch (this.drum) {
        case "KICK":
          sample = this.kick(t, decay);
          break;
        case "SNARE":
          sample = (this.bandPass.process(this.noiseSample()) * 1.4 + this.tone(SNARE_TONE_HZ) * 0.5) * decay;
          break;
        case "CLAP":
          sample = this.clap(t);
          break;
        case "TOM":
          sample = this.tone(TOM_END_HZ + (TOM_START_HZ - TOM_END_HZ) * Math.exp(-t / 0.08)) * decay;
          break;
        default:
          sample = this.highPass.process(this.noiseSample()) * decay * 0.6;
      }
      sample *= this.velocity;
      left[offset + i]! += sample;
      right[offset + i]! += sample;
      this.frame++;
    }
  }

  private noiseSample(): number {
    return this.noise.next("NOISE", 0, 0.5);
  }

  private tone(hz: number): number {
    this.phase += hz / this.sampleRate;
    if (this.phase >= 1) this.phase -= 1;
    return Math.sin(2 * Math.PI * this.phase);
  }

  private kick(t: number, decay: number): number {
    const drop = Math.exp(-t / KICK_PITCH_DROP_SECONDS);
    return this.tone(KICK_END_HZ + (KICK_START_HZ - KICK_END_HZ) * drop) * decay;
  }

  /** Three short noise bursts, then a tail. */
  private clap(t: number): number {
    const period = CLAP_BURST_SECONDS + CLAP_BURST_GAP_SECONDS;
    let envelope: number;
    if (t < period * 3) {
      const inBurst = t % period;
      envelope = inBurst < CLAP_BURST_SECONDS ? 1 - inBurst / CLAP_BURST_SECONDS : 0;
    } else {
      envelope = Math.exp(-(t - period * 3) * 18);
    }
    return this.bandPass.process(this.noiseSample()) * envelope * 1.5;
  }
}

/** A drum kit made from noise and sine tones; each drum answers to its General MIDI note. */
export class DrumKit implements Instrument {
  private readonly hits: Hit[];
  private nextHit = 0;

  constructor(sampleRate: number) {
    if (!(sampleRate > 0)) throw new Error(`sampleRate must be positive: ${sampleRate}`);
    this.hits = Array.from({ length: MAX_HITS }, () => new Hit(sampleRate));
  }

  noteOn(pitch: number, velocity: number): void {
    const drum = drumForNote(pitch);
    if (drum === null) return;
    let hit = this.hits.find((candidate) => !candidate.active);
    if (!hit) {
      // All busy: a slot is taken over, round-robin
      hit = this.hits[this.nextHit]!;
      this.nextHit = (this.nextHit + 1) % MAX_HITS;
    }
    hit.start(drum, Math.min(Math.max(velocity, 0), 1));
  }

  noteOff(): void {
    // Drums ring out on their own
  }

  allNotesOff(): void {
    for (const hit of this.hits) hit.active = false;
  }

  render(left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    for (const hit of this.hits) if (hit.active) hit.render(left, right, offset, frames);
  }
}
