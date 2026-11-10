import type { Clip } from "./clips";
import { generateSfxr, isKnownSound, sfxrPreset, SFXR_PRESET_NAMES, SFXR_SAMPLE_RATE } from "./sfxr";

/** Plays one generated sound. The browser implementation is {@link WebAudioSfxOutput}; tests use a recording fake. */
export interface SfxOutput {
  /**
   * Starts {@code samples} (mono, -1..1) once, at linear {@code volume} 0..1 and {@code pitch} times the speed.
   * {@code pan} places it between the speakers: -1 all left, 0 middle (both at full volume), 1 all right; only the far speaker gets
   * quieter. Must not block; called from the game loop.
   */
  play(samples: Float32Array, sampleRate: number, volume: number, pitch: number, pan: number): void;
}

/** Sound effects of a played scene (TD-043): makes each sound once, then replays it from memory on every cue. */
export class SfxPlayer {
  private readonly sounds = new Map<string, Float32Array>();
  /** Recorded clips by the name they were added under, with their own sample rate (generated sounds all use {@link SFXR_SAMPLE_RATE}). */
  private readonly clips = new Map<string, Clip>();

  constructor(private readonly output: SfxOutput) {}

  /** Whether {@code sound} is a factory sound name. */
  static isKnown(sound: string): boolean {
    return isKnownSound(sound);
  }

  /**
   * Adds a recorded clip, played by {@code name} from now on.
   *
   * @throws Error when {@code name} is blank or the name of a factory sound
   */
  addClip(name: string, clip: Clip): void {
    if (name.trim() === "") throw new Error("A clip needs a name");
    if (isKnownSound(name)) throw new Error(`'${name}' is the name of a factory effect`);
    this.clips.set(name, clip);
  }

  /** Whether {@code sound} is a factory sound or a clip added with {@link addClip}. */
  isAvailable(sound: string): boolean {
    return isKnownSound(sound) || this.clips.has(sound);
  }

  /** The factory sound names, for messages. */
  static get names(): readonly string[] {
    return SFXR_PRESET_NAMES;
  }

  /** Makes {@code sound} now so the first {@link play} does not pay for it. @throws Error for an unknown name. */
  prepare(sound: string): Float32Array {
    const clip = this.clips.get(sound);
    if (clip) return clip.samples;
    let samples = this.sounds.get(sound);
    if (!samples) {
      samples = generateSfxr(sfxrPreset(sound));
      this.sounds.set(sound, samples);
    }
    return samples;
  }

  /**
   * Plays a sound once.
   *
   * @throws Error for an unknown sound, a volume outside 0..1 or a pitch that is not above 0
   */
  play(sound: string, volume: number, pitch: number, pan = 0): void {
    if (!(pan >= -1 && pan <= 1)) throw new Error(`pan must be within -1 to 1: ${pan}`);
    if (!(volume >= 0 && volume <= 1)) throw new Error(`volume must be within 0 to 1: ${volume}`);
    if (!(pitch > 0) || !Number.isFinite(pitch)) throw new Error(`pitch must be above 0: ${pitch}`);
    this.output.play(this.prepare(sound), this.clips.get(sound)?.sampleRate ?? SFXR_SAMPLE_RATE, volume, pitch, pan);
  }
}

/**
 * Sound out of the browser's AudioContext. The context is created on the first sound, which happens after the click on
 * Play, because browsers keep audio silent until the user has interacted with the page. Each sound is an AudioBuffer
 * made once and replayed through a one-shot source with a gain.
 */
export class WebAudioSfxOutput implements SfxOutput {
  private context: AudioContext | null = null;
  private readonly buffers = new WeakMap<Float32Array, AudioBuffer>();

  play(samples: Float32Array, sampleRate: number, volume: number, pitch: number, pan: number): void {
    const context = this.ensureContext();
    if (context.state === "suspended") void context.resume();
    let buffer = this.buffers.get(samples);
    if (!buffer) {
      buffer = context.createBuffer(1, samples.length, sampleRate);
      buffer.copyToChannel(samples as Float32Array<ArrayBuffer>, 0);
      this.buffers.set(samples, buffer);
    }
    const source = context.createBufferSource();
    source.buffer = buffer;
    source.playbackRate.value = pitch;
    // One gain per speaker, so panning only quietens the far one and a sound in the middle is as loud as it always was
    const left = context.createGain();
    left.gain.value = volume * (pan > 0 ? 1 - pan : 1);
    const right = context.createGain();
    right.gain.value = volume * (pan < 0 ? 1 + pan : 1);
    const merger = context.createChannelMerger(2);
    source.connect(left).connect(merger, 0, 0);
    source.connect(right).connect(merger, 0, 1);
    merger.connect(context.destination);
    source.start();
  }

  /** Closes the AudioContext; a later sound opens a new one. */
  dispose(): void {
    void this.context?.close();
    this.context = null;
  }

  private ensureContext(): AudioContext {
    this.context ??= new AudioContext();
    return this.context;
  }
}
