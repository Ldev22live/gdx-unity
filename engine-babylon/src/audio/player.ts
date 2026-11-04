import { DrumKit } from "./drums";
import type { Instrument } from "./dsp";
import { Mixer, type StripSource } from "./mixer";
import {
  PLAYABLE_INSTRUMENTS,
  SAMPLE_RATE,
  STEP_TICKS,
  TICKS_PER_BEAT,
  TICKS_PER_BAR,
  buildMixer,
  findPattern,
  playlistLengthTicks,
  type InstrumentSpec,
  type PatternData,
  type SongData,
} from "./song";
import { Synthesizer } from "./synth";

export type PlayMode = "PATTERN" | "SONG";

/** One timed note event of a flattened song. */
export interface ScheduleEvent {
  tick: number;
  channelId: number;
  pitch: number;
  velocity: number;
  on: boolean;
}

/** How far swing delays every second sixteenth at full swing: a third of a step (triplet feel). */
const SWING_STEP_SHARE = 1 / 3;

/** A song flattened to note-on and note-off events in time order, with swing applied. */
export interface Schedule {
  events: ScheduleEvent[];
  lengthTicks: number;
}

/** Orders events by time, note-offs before note-ons at the same tick, then by channel and pitch. */
function byTime(a: ScheduleEvent, b: ScheduleEvent): number {
  return a.tick - b.tick || Number(a.on) - Number(b.on) || a.channelId - b.channelId || a.pitch - b.pitch;
}

function addPattern(events: ScheduleEvent[], song: SongData, pattern: PatternData, origin: number, windowEnd: number): void {
  const swingShift = Math.round(song.swing * STEP_TICKS * SWING_STEP_SHARE);
  for (const channel of song.channels) {
    if (channel.muted) continue;
    for (const [tick, length, pitch, velocity] of pattern.notes[String(channel.id)] ?? []) {
      let start = tick;
      if (Math.floor(start / STEP_TICKS) % 2 === 1) start += swingShift;
      if (start >= windowEnd) continue;
      const end = Math.min(start + length, windowEnd);
      events.push({ tick: origin + start, channelId: channel.id, pitch, velocity, on: true });
      events.push({ tick: origin + end, channelId: channel.id, pitch, velocity: 0, on: false });
    }
  }
}

/**
 * Flattens a song (or one pattern) into timed events. In song mode each playlist clip repeats its pattern to fill the
 * clip; notes are cut at the clip end. Muted channels give no events.
 *
 * @throws Error when `mode` is PATTERN and `patternId` is not a pattern of the song
 */
export function compileSchedule(song: SongData, mode: PlayMode, patternId: number): Schedule {
  const events: ScheduleEvent[] = [];
  let lengthTicks: number;
  if (mode === "PATTERN") {
    const pattern = findPattern(song, patternId);
    if (!pattern) throw new Error(`No pattern with id ${patternId}`);
    addPattern(events, song, pattern, 0, pattern.lengthTicks);
    lengthTicks = pattern.lengthTicks;
  } else {
    for (const [pid, , start, length] of song.clips) {
      const pattern = findPattern(song, pid);
      if (!pattern) continue;
      for (let offset = 0; offset < length; offset += pattern.lengthTicks) {
        addPattern(events, song, pattern, start + offset, Math.min(length - offset, pattern.lengthTicks));
      }
    }
    lengthTicks = playlistLengthTicks(song);
  }
  events.sort(byTime);
  return { events, lengthTicks };
}

class SilentInstrument implements Instrument {
  noteOn(): void {}
  noteOff(): void {}
  allNotesOff(): void {}
  render(): void {}
}

interface Slot {
  channelId: number;
  mixerIndex: number;
  signature: string;
  instrument: Instrument;
}

function signature(spec: InstrumentSpec): string {
  return `${spec.type}|${spec.path}|${spec.rootNote}|${spec.oneShot}|${spec.bank}|${spec.program}`;
}

const SECONDS_PER_MINUTE = 60;
/** An event within half a frame of the playhead is due now. */
const HALF_FRAME = 0.5;

/**
 * Plays a song: renders its instruments through the mixer and fires the schedule's events at the right sample. Frame
 * accurate and deterministic. The song can be replaced while playing ({@link loadSong}); position and transport state
 * are kept, so editing during playback continues from the same place.
 *
 * Instruments the web version cannot make (samples, sfxr, SoundFont) sound silent and are listed in {@link errors}.
 */
export class SongPlayer implements StripSource {
  errors: string[] = [];
  private song: SongData;
  private mixer: Mixer;
  private slots: Slot[] = [];
  private schedule: Schedule;
  private mode: PlayMode = "PATTERN";
  private patternId = 0;
  private playing = false;
  private loop = true;
  private positionTicks = 0;
  private eventIndex = 0;

  constructor(song: SongData, private readonly sampleRate = SAMPLE_RATE) {
    this.song = song;
    this.mixer = buildMixer(song, sampleRate);
    this.schedule = { events: [], lengthTicks: TICKS_PER_BAR };
    this.patternId = song.patterns[0]?.id ?? 0;
    this.rebuildInstruments();
    this.recompile();
  }

  get isPlaying(): boolean {
    return this.playing;
  }

  get isLoop(): boolean {
    return this.loop;
  }

  get position(): number {
    return Math.max(this.positionTicks, 0);
  }

  get playMode(): PlayMode {
    return this.mode;
  }

  get currentPatternId(): number {
    return this.patternId;
  }

  get lengthTicks(): number {
    return this.schedule.lengthTicks;
  }

  get currentMixer(): Mixer {
    return this.mixer;
  }

  setPlayback(mode: PlayMode, patternId: number): void {
    this.mode = mode;
    this.patternId = patternId;
    this.recompile();
  }

  setLoop(loop: boolean): void {
    this.loop = loop;
  }

  play(): void {
    this.playing = true;
  }

  pause(): void {
    this.playing = false;
    this.releaseAll();
  }

  stop(): void {
    this.playing = false;
    this.releaseAll();
    this.positionTicks = 0;
    this.eventIndex = 0;
  }

  seek(ticks: number): void {
    this.positionTicks = Math.max(ticks, 0);
    this.releaseAll();
    this.eventIndex = this.firstEventAtOrAfter(this.positionTicks);
  }

  /**
   * Replaces the song. Instruments that did not change are kept, so notes keep sounding; the mixer is rebuilt from the
   * new song (effect tails restart), and the schedule is recompiled around the current position.
   */
  loadSong(song: SongData): void {
    this.song = song;
    this.mixer = buildMixer(song, this.sampleRate);
    this.rebuildInstruments();
    if (this.mode === "PATTERN" && !findPattern(song, this.patternId)) this.patternId = song.patterns[0]?.id ?? 0;
    this.recompile();
    this.releaseAll();
  }

  /** Applies a live mixer knob without rebuilding anything, so dragging a fader never restarts effect tails. */
  setStrip(index: number | "master", values: { volume?: number; pan?: number; muted?: boolean; solo?: boolean }): void {
    const strip = index === "master" ? this.mixer.master : this.mixer.strips[index];
    if (!strip) return;
    if (values.volume !== undefined) strip.setVolume(values.volume);
    if (values.pan !== undefined) strip.setPan(values.pan);
    if (values.muted !== undefined) strip.muted = values.muted;
    if (values.solo !== undefined) strip.solo = values.solo;
  }

  /** Sets one effect parameter live. */
  setEffectParameter(strip: number | "master", effectIndex: number, name: string, value: number): void {
    const target = strip === "master" ? this.mixer.master : this.mixer.strips[strip];
    target?.effects[effectIndex]?.setParameter(name, value);
  }

  previewNoteOn(channelId: number, pitch: number, velocity: number): void {
    this.slots.find((slot) => slot.channelId === channelId)?.instrument.noteOn(pitch, velocity);
  }

  previewNoteOff(channelId: number, pitch: number): void {
    this.slots.find((slot) => slot.channelId === channelId)?.instrument.noteOff(pitch);
  }

  private recompile(): void {
    if (this.mode === "PATTERN" && !findPattern(this.song, this.patternId)) {
      this.schedule = { events: [], lengthTicks: TICKS_PER_BAR };
    } else {
      this.schedule = compileSchedule(this.song, this.mode, this.patternId);
    }
    this.eventIndex = this.firstEventAtOrAfter(this.positionTicks);
  }

  private rebuildInstruments(): void {
    const old = new Map(this.slots.map((slot) => [slot.channelId, slot]));
    const rebuilt: Slot[] = [];
    const problems: string[] = [];
    for (const channel of this.song.channels) {
      const spec = channel.instrument;
      const sig = signature(spec);
      const previous = old.get(channel.id);
      // A synth's patch is read live, so only a change of kind needs a new instrument
      if (previous && previous.signature === sig && previous.mixerIndex === channel.mixerIndex) {
        if (previous.instrument instanceof Synthesizer) Object.assign(previous.instrument.patch, spec.patch);
        rebuilt.push(previous);
        continue;
      }
      let instrument: Instrument;
      if (spec.type === "SYNTH") instrument = new Synthesizer(this.sampleRate, { ...spec.patch });
      else if (spec.type === "DRUMS") instrument = new DrumKit(this.sampleRate);
      else {
        problems.push(`${channel.name}: ${spec.type} instruments are not supported in the web version (playable: ${PLAYABLE_INSTRUMENTS.join(", ")})`);
        instrument = new SilentInstrument();
      }
      rebuilt.push({ channelId: channel.id, mixerIndex: channel.mixerIndex, signature: sig, instrument });
    }
    this.slots = rebuilt;
    this.errors = problems;
  }

  private releaseAll(): void {
    for (const slot of this.slots) slot.instrument.allNotesOff();
  }

  private firstEventAtOrAfter(tick: number): number {
    const events = this.schedule.events;
    let low = 0;
    let high = events.length;
    while (low < high) {
      const mid = (low + high) >>> 1;
      if (events[mid]!.tick < tick) low = mid + 1;
      else high = mid;
    }
    return low;
  }

  renderStrip(stripIndex: number, left: Float32Array, right: Float32Array, offset: number, frames: number): void {
    for (const slot of this.slots) if (slot.mixerIndex === stripIndex) slot.instrument.render(left, right, offset, frames);
  }

  /** Renders `frames` frames into `left` and `right`, advancing the song when it is playing. */
  render(left: Float32Array, right: Float32Array, frames: number): void {
    let done = 0;
    while (done < frames) {
      let chunk = frames - done;
      const framesPerTick = (this.sampleRate * SECONDS_PER_MINUTE) / (this.song.bpm * TICKS_PER_BEAT);
      if (this.playing) {
        chunk = Math.min(chunk, this.playChunk(framesPerTick));
        if (chunk <= 0) continue;
      }
      this.mixer.render(this, left, right, done, chunk);
      if (this.playing) this.positionTicks += chunk / framesPerTick;
      done += chunk;
    }
  }

  /** Fires the events that are due and returns how many frames can be rendered before the next one or the loop end. */
  private playChunk(framesPerTick: number): number {
    const events = this.schedule.events;
    let chunk = Number.MAX_SAFE_INTEGER;
    while (this.eventIndex < events.length) {
      const event = events[this.eventIndex]!;
      const framesUntil = (event.tick - this.positionTicks) * framesPerTick;
      if (framesUntil <= HALF_FRAME) {
        this.dispatch(event);
        this.eventIndex++;
        continue;
      }
      chunk = Math.ceil(framesUntil - HALF_FRAME);
      break;
    }
    const length = this.schedule.lengthTicks;
    const framesToEnd = (length - this.positionTicks) * framesPerTick;
    if (length <= 0 || framesToEnd <= HALF_FRAME) {
      if (this.loop && length > 0) {
        // Keep the fraction of a frame the loop point fell between, so loops do not drift
        this.positionTicks -= length;
        this.eventIndex = 0;
      } else {
        this.playing = false;
        this.releaseAll();
        this.positionTicks = 0;
        this.eventIndex = 0;
      }
      return 0;
    }
    return Math.min(chunk, Math.ceil(framesToEnd - HALF_FRAME));
  }

  private dispatch(event: ScheduleEvent): void {
    const slot = this.slots.find((candidate) => candidate.channelId === event.channelId);
    if (!slot) return;
    if (event.on) slot.instrument.noteOn(event.pitch, event.velocity);
    else slot.instrument.noteOff(event.pitch);
  }
}
