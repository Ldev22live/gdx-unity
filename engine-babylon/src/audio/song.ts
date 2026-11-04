import { createEffect } from "./effects";
import { DEFAULT_STRIPS, Mixer } from "./mixer";
import { defaultPatch, presetPatch, type SynthPatch } from "./synth";

/**
 * The song model: plain data that is exactly the desktop's `.song` JSON file (version 1), so a song made on either side
 * opens on the other. Everything here is pure functions over that data; the playing and editing code builds on it.
 */

/** Ticks in one beat; time in a song is counted in ticks. */
export const TICKS_PER_BEAT = 96;
export const BEATS_PER_BAR = 4;
export const TICKS_PER_BAR = TICKS_PER_BEAT * BEATS_PER_BAR;
/** Ticks in one step of the step sequencer (a sixteenth note). */
export const STEP_TICKS = TICKS_PER_BEAT / 4;
export const STEPS_PER_BAR = TICKS_PER_BAR / STEP_TICKS;
/** The sample rate every song renders at, as on the desktop. */
export const SAMPLE_RATE = 44100;
export const MIN_BPM = 20;
export const MAX_BPM = 300;
export const MAX_CHANNELS = 16;
export const MAX_PATTERN_BARS = 16;
export const MAX_TRACKS = 16;
export const MIN_PITCH = 0;
export const MAX_PITCH = 127;
/** The file format version this code reads and writes. */
export const SONG_VERSION = 1;

export type InstrumentType = "SYNTH" | "DRUMS" | "SAMPLE" | "SFXR" | "SOUNDFONT";
/** Instrument types this web version can play; the others are read, kept and saved, but sound silent. */
export const PLAYABLE_INSTRUMENTS: readonly InstrumentType[] = ["SYNTH", "DRUMS"];

export interface InstrumentSpec {
  type: InstrumentType;
  path: string;
  rootNote: number;
  oneShot: boolean;
  bank: number;
  program: number;
  patch: SynthPatch;
  /** sfxr settings: kept untouched so a desktop song round-trips; this version does not play them. */
  sfxr: unknown;
}

export interface ChannelData {
  id: number;
  name: string;
  mixerIndex: number;
  muted: boolean;
  /** Pitch a step in the step sequencer plays (drum channels use the drum's own note). */
  stepPitch: number;
  instrument: InstrumentSpec;
}

/** [tick, length, pitch, velocity]: a note, as the file stores it. */
export type NoteTuple = [number, number, number, number];

export interface PatternData {
  id: number;
  name: string;
  lengthTicks: number;
  /** Notes by channel id (a JSON object key, so a string). */
  notes: Record<string, NoteTuple[]>;
}

/** [patternId, track, startTick, lengthTicks]: a pattern placed on the playlist. */
export type ClipTuple = [number, number, number, number];

export interface EffectData {
  type: string;
  params: Record<string, number>;
}

export interface StripData {
  name: string;
  volume: number;
  pan: number;
  muted: boolean;
  solo: boolean;
  effects: EffectData[];
}

export interface SongData {
  version: number;
  name: string;
  bpm: number;
  swing: number;
  nextChannelId: number;
  nextPatternId: number;
  channels: ChannelData[];
  patterns: PatternData[];
  clips: ClipTuple[];
  mixer: { strips: StripData[]; master: StripData };
}

// ---- Creating ------------------------------------------------------------------------------------------------------

/** Default sfxr settings (the desktop's "Coin" preset shape), only so a new SFXR channel round-trips. */
function defaultSfxr(): unknown {
  // A fresh object each time. (structuredClone is not available in the audio worklet scope, where this module runs.)
  return {
    shape: "SQUARE", volume: 0.5, arpeggioSpeed: 0.6,
    values: [0.4, 0, 0, 0, 0, 0, 0, 0, 0, 0.02, 0.3, 0.4, 1, 0, 0, 0, 0, 0, 0, 0, 0.35],
  };
}

export function synthSpec(patch: SynthPatch = defaultPatch()): InstrumentSpec {
  return { type: "SYNTH", path: "", rootNote: 60, oneShot: false, bank: 0, program: 0, patch, sfxr: defaultSfxr() };
}

export function drumSpec(): InstrumentSpec {
  return { ...synthSpec(), type: "DRUMS" };
}

function newStrip(name: string, volume = 0.8): StripData {
  return { name, volume, pan: 0, muted: false, solo: false, effects: [] };
}

/** An empty song with the default mixer (eight inserts and a master). */
export function emptySong(name = "Untitled"): SongData {
  return {
    version: SONG_VERSION, name, bpm: 120, swing: 0, nextChannelId: 1, nextPatternId: 1,
    channels: [], patterns: [], clips: [],
    mixer: { strips: Array.from({ length: DEFAULT_STRIPS }, (_, i) => newStrip(`Insert ${i + 1}`)), master: newStrip("Master", 1) },
  };
}

/** A starting point like the usual first screen of a sequencer: kick, snare and hi-hat rows, a lead and a bass. */
export function defaultSong(name = "New Song"): SongData {
  const song = emptySong(name);
  addChannel(song, "Kick", drumSpec(), 0).stepPitch = 36;
  addChannel(song, "Snare", drumSpec(), 1).stepPitch = 38;
  addChannel(song, "Hat", drumSpec(), 2).stepPitch = 42;
  addChannel(song, "Lead", synthSpec(presetPatch("Saw Lead")), 3);
  addChannel(song, "Bass", synthSpec(presetPatch("Sub Bass")), 4);
  addPattern(song, "Pattern 1", 1);
  for (const [i, channel] of song.channels.entries()) song.mixer.strips[i]!.name = channel.name;
  return song;
}

export function addChannel(song: SongData, name: string, instrument: InstrumentSpec, mixerIndex: number): ChannelData {
  if (song.channels.length >= MAX_CHANNELS) throw new Error(`A song has at most ${MAX_CHANNELS} channels`);
  if (mixerIndex < 0 || mixerIndex >= song.mixer.strips.length) {
    throw new Error(`Mixer strip ${mixerIndex} does not exist (0..${song.mixer.strips.length - 1})`);
  }
  const channel: ChannelData = { id: song.nextChannelId++, name, mixerIndex, muted: false, stepPitch: 60, instrument };
  song.channels.push(channel);
  return channel;
}

export function addPattern(song: SongData, name: string, bars: number): PatternData {
  if (!Number.isInteger(bars) || bars < 1 || bars > MAX_PATTERN_BARS) throw new Error(`A pattern is 1..${MAX_PATTERN_BARS} bars long: ${bars}`);
  const pattern: PatternData = { id: song.nextPatternId++, name, lengthTicks: bars * TICKS_PER_BAR, notes: {} };
  song.patterns.push(pattern);
  return pattern;
}

// ---- Lookups -------------------------------------------------------------------------------------------------------

export function findChannel(song: SongData, id: number): ChannelData | undefined {
  return song.channels.find((channel) => channel.id === id);
}

export function findPattern(song: SongData, id: number): PatternData | undefined {
  return song.patterns.find((pattern) => pattern.id === id);
}

export function notesOf(pattern: PatternData, channelId: number): NoteTuple[] {
  return pattern.notes[String(channelId)] ?? [];
}

/** Tick where the last clip ends, 0 for an empty playlist. */
export function playlistLengthTicks(song: SongData): number {
  return song.clips.reduce((end, clip) => Math.max(end, clip[2] + clip[3]), 0);
}

/** True when a clip fits on its track without overlapping another (other than the one at `ignoreIndex`). */
export function canPlaceClip(song: SongData, clip: ClipTuple, ignoreIndex = -1): boolean {
  if (!findPattern(song, clip[0]) || clip[1] < 0 || clip[1] >= MAX_TRACKS || clip[2] < 0 || clip[3] < TICKS_PER_BAR) return false;
  return song.clips.every((other, i) => i === ignoreIndex || other[1] !== clip[1] || !(clip[2] < other[2] + other[3] && other[2] < clip[2] + clip[3]));
}

/** Name like "C4" or "F#3" for a MIDI pitch (60 is C4). */
export function pitchName(pitch: number): string {
  const names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  return `${names[((pitch % 12) + 12) % 12]}${Math.floor(pitch / 12) - 1}`;
}

export function isBlackKey(pitch: number): boolean {
  return [1, 3, 6, 8, 10].includes(((pitch % 12) + 12) % 12);
}

// ---- File ----------------------------------------------------------------------------------------------------------

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function finite(value: unknown, what: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) throw new Error(`${what} must be a number`);
  return value;
}

/**
 * Reads a song file.
 *
 * @throws Error when the text is not a valid song; the message says what is wrong
 */
export function parseSong(text: string): SongData {
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch (error) {
    throw new Error(`Invalid song file: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (!isRecord(root)) throw new Error("A song file is a JSON object");
  if (root["version"] !== SONG_VERSION) throw new Error(`Unsupported song file version ${String(root["version"])} (expected ${SONG_VERSION})`);
  const song = emptySong(typeof root["name"] === "string" ? root["name"] : "Untitled");
  song.bpm = finite(root["bpm"] ?? 120, "bpm");
  song.swing = finite(root["swing"] ?? 0, "swing");
  if (!(song.bpm >= MIN_BPM && song.bpm <= MAX_BPM)) throw new Error(`bpm must be between ${MIN_BPM} and ${MAX_BPM}: ${song.bpm}`);
  if (!(song.swing >= 0 && song.swing <= 1)) throw new Error(`swing must be 0..1: ${song.swing}`);

  const mixer = root["mixer"];
  if (isRecord(mixer)) {
    const strips = Array.isArray(mixer["strips"]) ? mixer["strips"] : [];
    strips.slice(0, song.mixer.strips.length).forEach((value, i) => (song.mixer.strips[i] = readStrip(value, song.mixer.strips[i]!)));
    if (isRecord(mixer["master"])) song.mixer.master = readStrip(mixer["master"], song.mixer.master);
  }

  for (const value of Array.isArray(root["channels"]) ? root["channels"] : []) {
    if (!isRecord(value)) throw new Error("A channel must be an object");
    const mixerIndex = finite(value["mixerIndex"] ?? 0, "channel mixerIndex");
    if (mixerIndex < 0 || mixerIndex >= song.mixer.strips.length) throw new Error(`Mixer strip ${mixerIndex} does not exist`);
    song.channels.push({
      id: finite(value["id"], "channel id"),
      name: typeof value["name"] === "string" ? value["name"] : "Channel",
      mixerIndex,
      muted: value["muted"] === true,
      stepPitch: finite(value["stepPitch"] ?? 60, "channel stepPitch"),
      instrument: readInstrument(value["instrument"]),
    });
  }
  for (const value of Array.isArray(root["patterns"]) ? root["patterns"] : []) {
    if (!isRecord(value)) throw new Error("A pattern must be an object");
    const lengthTicks = finite(value["lengthTicks"], "pattern lengthTicks");
    if (lengthTicks < TICKS_PER_BAR || lengthTicks % TICKS_PER_BAR !== 0) throw new Error(`Pattern length must be a whole number of bars: ${lengthTicks}`);
    const pattern: PatternData = { id: finite(value["id"], "pattern id"), name: typeof value["name"] === "string" ? value["name"] : "Pattern", lengthTicks, notes: {} };
    const notes = value["notes"];
    for (const [channelId, list] of isRecord(notes) ? Object.entries(notes) : []) {
      if (!Array.isArray(list)) throw new Error(`Notes of channel ${channelId} must be a list`);
      pattern.notes[channelId] = list.map((n): NoteTuple => {
        if (!Array.isArray(n) || n.length < 4) throw new Error("A note is [tick, length, pitch, velocity]");
        return checkNote([finite(n[0], "note tick"), finite(n[1], "note length"), finite(n[2], "note pitch"), finite(n[3], "note velocity")]);
      });
    }
    song.patterns.push(pattern);
  }
  const clips = (Array.isArray(root["clips"]) ? root["clips"] : []).map((c): ClipTuple => {
    if (!Array.isArray(c) || c.length < 4) throw new Error("A clip is [patternId, track, startTick, lengthTicks]");
    return [finite(c[0], "clip pattern"), finite(c[1], "clip track"), finite(c[2], "clip start"), finite(c[3], "clip length")];
  });
  for (const clip of clips) {
    if (!canPlaceClip(song, clip)) throw new Error(`Clip does not fit the playlist: ${JSON.stringify(clip)}`);
    song.clips.push(clip);
  }
  const maxChannel = song.channels.reduce((m, c) => Math.max(m, c.id), 0);
  const maxPattern = song.patterns.reduce((m, p) => Math.max(m, p.id), 0);
  song.nextChannelId = Math.max(finite(root["nextChannelId"] ?? 1, "nextChannelId"), maxChannel + 1);
  song.nextPatternId = Math.max(finite(root["nextPatternId"] ?? 1, "nextPatternId"), maxPattern + 1);
  return song;
}

/** Throws for a note the engine cannot represent (the same limits as the desktop's Note record). */
export function checkNote(note: NoteTuple): NoteTuple {
  const [tick, length, pitch, velocity] = note;
  if (!(tick >= 0)) throw new Error(`tick must not be negative: ${tick}`);
  if (!(length >= 1)) throw new Error(`length must be at least 1 tick: ${length}`);
  if (!(pitch >= MIN_PITCH && pitch <= MAX_PITCH)) throw new Error(`pitch must be 0..127: ${pitch}`);
  if (!(velocity >= 0 && velocity <= 1)) throw new Error(`velocity must be 0..1: ${velocity}`);
  return note;
}

function readInstrument(value: unknown): InstrumentSpec {
  if (!isRecord(value)) throw new Error("A channel needs an instrument");
  const type = String(value["type"] ?? "SYNTH") as InstrumentType;
  if (!["SYNTH", "DRUMS", "SAMPLE", "SFXR", "SOUNDFONT"].includes(type)) throw new Error(`Unknown instrument type ${type}`);
  const spec = synthSpec();
  spec.type = type;
  spec.path = typeof value["path"] === "string" ? value["path"] : "";
  spec.rootNote = typeof value["rootNote"] === "number" ? value["rootNote"] : 60;
  spec.oneShot = value["oneShot"] === true;
  spec.bank = typeof value["bank"] === "number" ? value["bank"] : 0;
  spec.program = typeof value["program"] === "number" ? value["program"] : 0;
  if (isRecord(value["patch"])) spec.patch = { ...defaultPatch(), ...(value["patch"] as Partial<SynthPatch>) };
  if (value["sfxr"] !== undefined) spec.sfxr = value["sfxr"];
  return spec;
}

function readStrip(value: unknown, fallback: StripData): StripData {
  if (!isRecord(value)) return fallback;
  const effects: EffectData[] = [];
  for (const e of Array.isArray(value["effects"]) ? value["effects"] : []) {
    if (!isRecord(e) || typeof e["type"] !== "string") throw new Error("An effect needs a type");
    createEffect(e["type"], SAMPLE_RATE); // throws for an unknown type
    const params: Record<string, number> = {};
    for (const [name, v] of isRecord(e["params"]) ? Object.entries(e["params"]) : []) params[name] = finite(v, `effect parameter ${name}`);
    effects.push({ type: e["type"], params });
  }
  return {
    name: typeof value["name"] === "string" ? value["name"] : fallback.name,
    volume: typeof value["volume"] === "number" ? value["volume"] : fallback.volume,
    pan: typeof value["pan"] === "number" ? value["pan"] : 0,
    muted: value["muted"] === true,
    solo: value["solo"] === true,
    effects,
  };
}

/** The song as file text. */
export function songToJson(song: SongData): string {
  const notes = (pattern: PatternData) =>
    `{${Object.entries(pattern.notes).map(([id, list]) => `${JSON.stringify(id)}:${JSON.stringify(list)}`).join(",")}}`;
  return [
    `{"version":${SONG_VERSION},"name":${JSON.stringify(song.name)},"bpm":${song.bpm},"swing":${song.swing},"nextChannelId":${song.nextChannelId},"nextPatternId":${song.nextPatternId},`,
    `"channels":[\n${song.channels.map((c) => JSON.stringify(c)).join(",\n")}],`,
    `"patterns":[\n${song.patterns.map((p) => `{"id":${p.id},"name":${JSON.stringify(p.name)},"lengthTicks":${p.lengthTicks},"notes":${notes(p)}}`).join(",\n")}],`,
    `"clips":${JSON.stringify(song.clips)},`,
    `"mixer":{"strips":[\n${song.mixer.strips.map((s) => JSON.stringify(s)).join(",\n")}],"master":${JSON.stringify(song.mixer.master)}}}\n`,
  ].join("\n");
}

/** Builds the runtime mixer (strips, effect instances) a song describes. */
export function buildMixer(song: SongData, sampleRate = SAMPLE_RATE): Mixer {
  const mixer = new Mixer(sampleRate, song.mixer.strips.length);
  const apply = (target: Mixer["master"], data: StripData) => {
    target.name = data.name;
    target.setVolume(data.volume);
    target.setPan(data.pan);
    target.muted = data.muted;
    target.solo = data.solo;
    for (const effect of data.effects) {
      const instance = createEffect(effect.type, sampleRate);
      for (const [name, value] of Object.entries(effect.params)) instance.setParameter(name, value);
      target.addEffect(instance);
    }
  };
  song.mixer.strips.forEach((data, i) => apply(mixer.strips[i]!, data));
  apply(mixer.master, song.mixer.master);
  return mixer;
}
