/**
 * The retro sound-effect generator of the desktop engine's `SfxrGenerator` (TD-043), ported so the web player makes the
 * same sounds from the same preset names. Everything is computed from the settings, with a fixed noise seed, so a sound
 * is regenerated instead of stored.
 *
 * Only the factory presets are ported; the desktop's randomize and mutate are editor tools the web player does not
 * offer.
 */

/** Output sample rate, Hz. */
export const SFXR_SAMPLE_RATE = 44100;
const OVERSAMPLE = 8;
const NOISE_SIZE = 32;
const PHASER_SIZE = 1024;
const ENVELOPE_SCALE = 100000;
const NOISE_SEED = 0x5eed;
/** The sound stops after this many seconds even if the envelope has not ended. */
const MAX_SECONDS = 10;
const OUTPUT_GAIN = 2;

export type SfxrShape = "SQUARE" | "SAW" | "SINE" | "NOISE";

/** Settings of one sound, normalised like the desktop's `SfxrParams`: 0..1, or -1..1 for those that go both ways. */
export interface SfxrParams {
  shape: SfxrShape;
  baseFreq: number;
  minFreq: number;
  slide: number;
  deltaSlide: number;
  duty: number;
  dutySweep: number;
  vibratoDepth: number;
  vibratoSpeed: number;
  attack: number;
  sustain: number;
  decay: number;
  punch: number;
  lowPassCutoff: number;
  lowPassSweep: number;
  lowPassResonance: number;
  highPassCutoff: number;
  highPassSweep: number;
  phaserOffset: number;
  phaserSweep: number;
  repeatSpeed: number;
  arpeggioChange: number;
  arpeggioSpeed: number;
  volume: number;
}

/** Defaults of a new sound, the same as the desktop's `SfxrParams` field initialisers. */
function defaults(): SfxrParams {
  return {
    shape: "SQUARE",
    baseFreq: f32(0.3),
    minFreq: 0,
    slide: 0,
    deltaSlide: 0,
    duty: 0,
    dutySweep: 0,
    vibratoDepth: 0,
    vibratoSpeed: 0,
    attack: 0,
    sustain: f32(0.3),
    decay: f32(0.4),
    punch: 0,
    lowPassCutoff: 1,
    lowPassSweep: 0,
    lowPassResonance: 0,
    highPassCutoff: 0,
    highPassSweep: 0,
    phaserOffset: 0,
    phaserSweep: 0,
    repeatSpeed: 0,
    arpeggioChange: 0,
    arpeggioSpeed: 0,
    volume: f32(0.5),
  };
}

/** The desktop stores settings as 32-bit floats; rounding to that keeps the generated sound the same. */
function f32(value: number): number {
  return Math.fround(value);
}

/** Overrides applied to {@link defaults} for each factory sound; values are the desktop's float literals. */
const PRESET_CHANGES: Record<string, Partial<SfxrParams>> = {
  Coin: { baseFreq: 0.4, attack: 0, sustain: 0.02, decay: 0.3, punch: 0.4, arpeggioChange: 0.35, arpeggioSpeed: 0.6 },
  Laser: { shape: "SAW", baseFreq: 0.7, minFreq: 0.15, slide: -0.35, sustain: 0.1, decay: 0.35, duty: 0.3 },
  Explosion: {
    shape: "NOISE",
    baseFreq: 0.1,
    slide: -0.15,
    deltaSlide: 0.1,
    sustain: 0.35,
    decay: 0.6,
    punch: 0.6,
    lowPassCutoff: 0.5,
  },
  Powerup: { shape: "SQUARE", baseFreq: 0.25, slide: 0.3, sustain: 0.25, decay: 0.4, vibratoDepth: 0.3, vibratoSpeed: 0.5 },
  Hurt: { shape: "SAW", baseFreq: 0.35, slide: -0.25, sustain: 0.1, decay: 0.3, punch: 0.5, lowPassCutoff: 0.7 },
  Jump: { baseFreq: 0.3, slide: 0.25, sustain: 0.12, decay: 0.25, duty: 0.4 },
  Blip: { baseFreq: 0.45, sustain: 0.05, decay: 0.15, highPassCutoff: 0.1 },
};

/** Names of the factory sounds, the same as the desktop's, in menu order. */
export const SFXR_PRESET_NAMES: readonly string[] = ["Coin", "Laser", "Explosion", "Powerup", "Hurt", "Jump", "Blip"];

/** Whether {@code name} is a factory sound this player can make. */
export function isKnownSound(name: string): boolean {
  return Object.prototype.hasOwnProperty.call(PRESET_CHANGES, name);
}

/** The settings of a factory sound. @throws Error for an unknown name. */
export function sfxrPreset(name: string): SfxrParams {
  if (!isKnownSound(name)) throw new Error(`Unknown sound '${name}'; expected one of ${SFXR_PRESET_NAMES.join(", ")}`);
  const params = defaults();
  for (const [key, value] of Object.entries(PRESET_CHANGES[name]!)) {
    (params as unknown as Record<string, number | string>)[key] = typeof value === "number" ? f32(value) : value;
  }
  return params;
}

/**
 * `java.util.Random(seed).nextFloat()`, exactly: the desktop fills its noise table from one, and the explosion preset is
 * noise, so the same generator gives the same sound. The 48-bit state is kept as two 24-bit halves so every product
 * stays exact in a double.
 */
export class NoiseSource {
  private high: number;
  private low: number;
  private static readonly MULT_HIGH = 0x5de;
  private static readonly MULT_LOW = 0xece66d;
  private static readonly HALF = 0x1000000;

  constructor(seed: number) {
    const scrambled = (BigInt(seed) ^ 0x5deece66dn) & ((1n << 48n) - 1n);
    this.high = Number(scrambled >> 24n);
    this.low = Number(scrambled & 0xffffffn);
  }

  nextFloat(): number {
    const oldLow = this.low;
    const oldHigh = this.high;
    const lowProduct = oldLow * NoiseSource.MULT_LOW + 0xb;
    const carry = Math.floor(lowProduct / NoiseSource.HALF);
    this.low = lowProduct % NoiseSource.HALF;
    this.high = (oldHigh * NoiseSource.MULT_LOW + oldLow * NoiseSource.MULT_HIGH + carry) % NoiseSource.HALF;
    return this.high / NoiseSource.HALF;
  }
}

/** Renders a sound as mono audio at {@link SFXR_SAMPLE_RATE}, values in -1..1. */
export function generateSfxr(params: SfxrParams): Float32Array {
  const state = new Generator(params);
  const maxFrames = MAX_SECONDS * SFXR_SAMPLE_RATE;
  const buffer = new Float32Array(Math.min(maxFrames, state.totalEnvelopeFrames() + 1));
  let frames = 0;
  while (frames < buffer.length && state.playing) {
    buffer[frames++] = state.next();
  }
  return buffer.slice(0, frames);
}

/** Frames the three envelope stages add up to; the sound is never longer than this plus one. */
export function sfxrEnvelopeFrames(params: SfxrParams): number {
  return new Generator(params).totalEnvelopeFrames();
}

class Generator {
  playing = true;
  private readonly p: SfxrParams;
  private readonly noise = new NoiseSource(NOISE_SEED);
  private readonly noiseBuffer = new Float32Array(NOISE_SIZE);
  private readonly phaserBuffer = new Float32Array(PHASER_SIZE);
  private readonly envelopeLength: [number, number, number];
  private readonly maxPeriod: number;
  private period = 0;
  private slide = 0;
  private deltaSlide = 0;
  private duty = 0;
  private dutySlide = 0;
  private arpMultiplier = 0;
  private arpTime = 0;
  private arpLimit = 0;
  private repeatTime = 0;
  private readonly repeatLimit: number;
  private vibratoPhase = 0;
  private readonly vibratoSpeed: number;
  private readonly vibratoDepth: number;
  private phase = 0;
  private envelopeStage = 0;
  private envelopeTime = 0;
  private lowPass = 0;
  private lowPassDelta = 0;
  private lowPassWidth: number;
  private readonly lowPassWidthDelta: number;
  private readonly lowPassDamping: number;
  private readonly lowPassOn: boolean;
  private highPass = 0;
  private highPassCoefficient: number;
  private readonly highPassDelta: number;
  private phaserOffset: number;
  private readonly phaserDelta: number;
  private phaserIndex = 0;
  private phaserPosition = 0;

  constructor(params: SfxrParams) {
    const p = params;
    this.p = p;
    this.maxPeriod = f32(100 / (p.minFreq * p.minFreq + 0.001));
    // The desktop multiplies these as 32-bit floats, which can land one frame away from double arithmetic
    const stageFrames = (stage: number): number => Math.trunc(f32(f32(stage * stage) * ENVELOPE_SCALE));
    this.envelopeLength = [stageFrames(p.attack), stageFrames(p.sustain), stageFrames(p.decay)];
    for (let i = 0; i < NOISE_SIZE; i++) this.noiseBuffer[i] = this.noise.nextFloat() * 2 - 1;
    this.restart();
    this.lowPassOn = p.lowPassCutoff !== 1;
    this.lowPassWidth = Math.pow(p.lowPassCutoff, 3) * 0.1;
    this.lowPassWidthDelta = 1 + p.lowPassSweep * 0.0001;
    this.lowPassDamping = Math.min(0.8, (5 / (1 + Math.pow(p.lowPassResonance, 2) * 20)) * (0.01 + this.lowPassWidth));
    this.highPassCoefficient = Math.pow(p.highPassCutoff, 2) * 0.1;
    this.highPassDelta = 1 + p.highPassSweep * 0.0003;
    this.vibratoSpeed = Math.pow(p.vibratoSpeed, 2) * 0.01;
    this.vibratoDepth = p.vibratoDepth * 0.5;
    this.phaserOffset = Math.pow(p.phaserOffset, 2) * 1020 * Math.sign(p.phaserOffset);
    this.phaserDelta = Math.pow(p.phaserSweep, 2) * Math.sign(p.phaserSweep);
    this.repeatLimit = p.repeatSpeed === 0 ? 0 : Math.trunc(Math.pow(1 - p.repeatSpeed, 2) * 20000 + 32);
  }

  /** (Re)starts the pitch parts; a repeat restarts these but not the envelope. */
  private restart(): void {
    const p = this.p;
    this.period = 100 / (Math.pow(p.baseFreq, 2) + 0.001);
    this.slide = 1 - Math.pow(p.slide, 3) * 0.01;
    this.deltaSlide = -Math.pow(p.deltaSlide, 3) * 0.000001;
    this.duty = 0.5 - p.duty * 0.5;
    this.dutySlide = -p.dutySweep * 0.00005;
    this.arpMultiplier =
      p.arpeggioChange >= 0 ? 1 - Math.pow(p.arpeggioChange, 2) * 0.9 : 1 + Math.pow(p.arpeggioChange, 2) * 10;
    this.arpTime = 0;
    this.arpLimit = p.arpeggioSpeed >= 1 ? 0 : Math.trunc(Math.pow(1 - p.arpeggioSpeed, 2) * 20000 + 32);
  }

  totalEnvelopeFrames(): number {
    return this.envelopeLength[0] + this.envelopeLength[1] + this.envelopeLength[2];
  }

  next(): number {
    const p = this.p;
    this.repeatTime++;
    if (this.repeatLimit !== 0 && this.repeatTime >= this.repeatLimit) {
      this.repeatTime = 0;
      this.restart();
    }
    this.arpTime++;
    if (this.arpLimit !== 0 && this.arpTime >= this.arpLimit) {
      this.arpLimit = 0;
      this.period *= this.arpMultiplier;
    }
    this.slide += this.deltaSlide;
    this.period *= this.slide;
    if (this.period > this.maxPeriod) {
      this.period = this.maxPeriod;
      if (p.minFreq > 0) this.playing = false;
    }
    let effectivePeriod = this.period;
    if (this.vibratoDepth > 0) {
      this.vibratoPhase += this.vibratoSpeed;
      effectivePeriod = this.period * (1 + Math.sin(this.vibratoPhase) * this.vibratoDepth);
    }
    const periodFrames = Math.max(Math.trunc(effectivePeriod), 8);
    this.duty = Math.min(Math.max(this.duty + this.dutySlide, 0), 0.5);
    this.envelopeTime++;
    if (this.envelopeTime > this.envelopeLength[this.envelopeStage]!) {
      this.envelopeTime = 0;
      this.envelopeStage++;
      if (this.envelopeStage === 3) {
        this.playing = false;
        this.envelopeStage = 2;
      }
    }
    const envelope = this.envelopeVolume();
    this.phaserOffset += this.phaserDelta;
    this.phaserIndex = Math.min(Math.abs(Math.trunc(this.phaserOffset)), PHASER_SIZE - 1);
    if (this.highPassDelta !== 0) {
      this.highPassCoefficient = Math.min(Math.max(this.highPassCoefficient * this.highPassDelta, 0.00001), 0.1);
    }
    let mixed = 0;
    for (let over = 0; over < OVERSAMPLE; over++) {
      this.phase++;
      if (this.phase >= periodFrames) {
        this.phase %= periodFrames;
        if (p.shape === "NOISE") {
          for (let i = 0; i < NOISE_SIZE; i++) this.noiseBuffer[i] = this.noise.nextFloat() * 2 - 1;
        }
      }
      const fraction = this.phase / periodFrames;
      let raw: number;
      switch (p.shape) {
        case "SQUARE":
          raw = fraction < this.duty ? 0.5 : -0.5;
          break;
        case "SAW":
          raw = 1 - fraction * 2;
          break;
        case "SINE":
          raw = Math.sin(fraction * 2 * Math.PI);
          break;
        case "NOISE":
          raw = this.noiseBuffer[Math.trunc((this.phase * NOISE_SIZE) / periodFrames)]!;
          break;
      }
      const previousLowPass = this.lowPass;
      this.lowPassWidth = Math.min(Math.max(this.lowPassWidth * this.lowPassWidthDelta, 0), 0.1);
      if (this.lowPassOn) {
        this.lowPassDelta += (raw - this.lowPass) * this.lowPassWidth;
        this.lowPassDelta -= this.lowPassDelta * this.lowPassDamping;
      } else {
        this.lowPass = raw;
        this.lowPassDelta = 0;
      }
      this.lowPass += this.lowPassDelta;
      this.highPass += this.lowPass - previousLowPass;
      this.highPass -= this.highPass * this.highPassCoefficient;
      this.phaserBuffer[this.phaserPosition & (PHASER_SIZE - 1)] = this.highPass;
      const phased =
        this.highPass + this.phaserBuffer[(this.phaserPosition - this.phaserIndex + PHASER_SIZE) & (PHASER_SIZE - 1)]!;
      this.phaserPosition = (this.phaserPosition + 1) & (PHASER_SIZE - 1);
      mixed += phased * envelope;
    }
    const out = (mixed / OVERSAMPLE) * p.volume * OUTPUT_GAIN;
    return Math.min(Math.max(out, -1), 1);
  }

  private envelopeVolume(): number {
    const length = Math.max(this.envelopeLength[this.envelopeStage]!, 1);
    const t = this.envelopeTime / length;
    switch (this.envelopeStage) {
      case 0:
        return t;
      case 1:
        return 1 + (1 - t) * 2 * this.p.punch;
      default:
        return 1 - t;
    }
  }
}
