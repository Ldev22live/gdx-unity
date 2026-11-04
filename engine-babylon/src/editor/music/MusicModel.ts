import { LFO_TARGETS, presetPatch, type SynthPatch } from "../../audio/synth";
import { createEffect } from "../../audio/effects";
import { MAX_EFFECTS } from "../../audio/mixer";
import {
  MAX_BPM,
  MAX_CHANNELS,
  MAX_PATTERN_BARS,
  MIN_BPM,
  STEP_TICKS,
  TICKS_PER_BAR,
  addChannel,
  addPattern,
  canPlaceClip,
  checkNote,
  defaultSong,
  drumSpec,
  findChannel,
  findPattern,
  notesOf,
  synthSpec,
  type ChannelData,
  type ClipTuple,
  type InstrumentType,
  type NoteTuple,
  type PatternData,
  type SongData,
} from "../../audio/song";

/** Undo steps kept; older ones are dropped. */
export const MAX_MUSIC_UNDO = 100;
/** Edits with the same coalescing key within this many milliseconds become one undo step (dragging a knob). */
export const MUSIC_COALESCE_MS = 800;

/**
 * What a listener is told. `strip` and `effect` are knob moves the audio thread applies live; `song` is everything else.
 * A `quiet` song change is one the control being dragged or typed in already shows, so panels must not redraw.
 */
export type MusicChange =
  | { kind: "song"; quiet?: boolean }
  | { kind: "replaced" }
  | { kind: "selection" }
  | { kind: "strip"; index: number | "master"; values: { volume?: number; pan?: number; muted?: boolean; solo?: boolean } }
  | { kind: "effect"; strip: number | "master"; effectIndex: number; name: string; value: number };

interface Snapshot {
  label: string;
  song: SongData;
  pattern: number;
  channel: number;
}

/**
 * A song being edited, with its own undo history (like the desktop's MusicDocument): the song is the `.song` file's own
 * data, and every edit goes through {@link edit}, which snapshots first. No DOM and no audio in here.
 */
export class MusicModel {
  private songData: SongData;
  private selectedPattern: number;
  private selectedChannel: number;
  private readonly undoStack: Snapshot[] = [];
  private readonly redoStack: Snapshot[] = [];
  private readonly listeners = new Set<(change: MusicChange) => void>();
  private revision = 0;
  private savedRevision = 0;
  private lastKey: string | null = null;
  private lastTime = 0;

  constructor(song: SongData = defaultSong()) {
    this.songData = structuredClone(song);
    this.selectedPattern = this.songData.patterns[0]?.id ?? 0;
    this.selectedChannel = this.songData.channels[0]?.id ?? 0;
  }

  get song(): SongData {
    return this.songData;
  }

  get patternId(): number {
    return this.selectedPattern;
  }

  get channelId(): number {
    return this.selectedChannel;
  }

  get pattern(): PatternData | undefined {
    return findPattern(this.songData, this.selectedPattern);
  }

  get channel(): ChannelData | undefined {
    return findChannel(this.songData, this.selectedChannel);
  }

  get isDirty(): boolean {
    return this.revision !== this.savedRevision;
  }

  get canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  get canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  get undoLabel(): string | null {
    return this.undoStack.at(-1)?.label ?? null;
  }

  addListener(listener: (change: MusicChange) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(change: MusicChange): void {
    for (const listener of [...this.listeners]) listener(change);
  }

  markSaved(): void {
    this.savedRevision = this.revision;
  }

  /** Replaces the whole song (open, new); clears the history. */
  load(song: SongData): void {
    this.songData = structuredClone(song);
    this.selectedPattern = this.songData.patterns[0]?.id ?? 0;
    this.selectedChannel = this.songData.channels[0]?.id ?? 0;
    this.undoStack.length = 0;
    this.redoStack.length = 0;
    this.revision = 0;
    this.savedRevision = 0;
    this.lastKey = null;
    this.notify({ kind: "replaced" });
  }

  selectPattern(id: number): void {
    if (!findPattern(this.songData, id)) throw new Error(`No pattern with id ${id}`);
    if (id === this.selectedPattern) return;
    this.selectedPattern = id;
    this.notify({ kind: "selection" });
    this.notify({ kind: "song" });
  }

  selectChannel(id: number): void {
    if (!findChannel(this.songData, id)) throw new Error(`No channel with id ${id}`);
    if (id === this.selectedChannel) return;
    this.selectedChannel = id;
    this.notify({ kind: "selection" });
  }

  /**
   * Runs `change` on the song as one undo step. A change that throws leaves the song as it was (the snapshot is put
   * back) and rethrows.
   */
  edit(label: string, change: (song: SongData) => void, change2: MusicChange = { kind: "song" }, coalesceKey?: string): void {
    const now = Date.now();
    const merges = coalesceKey !== undefined && coalesceKey === this.lastKey && now - this.lastTime < MUSIC_COALESCE_MS && this.undoStack.length > 0;
    const before: Snapshot = { label, song: structuredClone(this.songData), pattern: this.selectedPattern, channel: this.selectedChannel };
    try {
      change(this.songData);
    } catch (error) {
      this.songData = before.song;
      throw error;
    }
    if (!merges) {
      this.undoStack.push(before);
      if (this.undoStack.length > MAX_MUSIC_UNDO) this.undoStack.shift();
    }
    this.redoStack.length = 0;
    this.lastKey = coalesceKey ?? null;
    this.lastTime = now;
    this.revision++;
    // A selection pointing at something the change removed would dangle
    if (!findPattern(this.songData, this.selectedPattern)) this.selectedPattern = this.songData.patterns[0]?.id ?? 0;
    if (!findChannel(this.songData, this.selectedChannel)) this.selectedChannel = this.songData.channels[0]?.id ?? 0;
    this.notify(change2);
  }

  undo(): void {
    const step = this.undoStack.pop();
    if (!step) return;
    this.redoStack.push({ label: step.label, song: this.songData, pattern: this.selectedPattern, channel: this.selectedChannel });
    this.restore(step);
  }

  redo(): void {
    const step = this.redoStack.pop();
    if (!step) return;
    this.undoStack.push({ label: step.label, song: this.songData, pattern: this.selectedPattern, channel: this.selectedChannel });
    this.restore(step);
  }

  private restore(step: Snapshot): void {
    this.songData = step.song;
    this.selectedPattern = findPattern(this.songData, step.pattern) ? step.pattern : (this.songData.patterns[0]?.id ?? 0);
    this.selectedChannel = findChannel(this.songData, step.channel) ? step.channel : (this.songData.channels[0]?.id ?? 0);
    this.revision++;
    this.lastKey = null;
    this.notify({ kind: "replaced" });
  }

  // ---- Song ---------------------------------------------------------------------------------------------------

  setName(name: string): void {
    this.edit("Rename song", (s) => (s.name = name), { kind: "song", quiet: true }, "song-name");
  }

  setBpm(bpm: number): void {
    if (!(bpm >= MIN_BPM && bpm <= MAX_BPM)) throw new Error(`bpm must be between ${MIN_BPM} and ${MAX_BPM}: ${bpm}`);
    this.edit("Tempo", (s) => (s.bpm = bpm), { kind: "song", quiet: true }, "bpm");
  }

  setSwing(swing: number): void {
    if (!(swing >= 0 && swing <= 1)) throw new Error(`swing must be 0..1: ${swing}`);
    this.edit("Swing", (s) => (s.swing = swing), { kind: "song", quiet: true }, "swing");
  }

  // ---- Channels -----------------------------------------------------------------------------------------------

  /** Adds a channel on the first mixer insert not used by another channel (or insert 1 when all are), and selects it. */
  addChannel(kind: "SYNTH" | "DRUMS", name?: string): number {
    if (this.songData.channels.length >= MAX_CHANNELS) throw new Error(`A song has at most ${MAX_CHANNELS} channels`);
    let id = 0;
    this.edit("Add channel", (s) => {
      const used = new Set(s.channels.map((c) => c.mixerIndex));
      const free = s.mixer.strips.findIndex((_, i) => !used.has(i));
      const label = name ?? (kind === "DRUMS" ? `Drums ${s.nextChannelId}` : `Synth ${s.nextChannelId}`);
      const channel = addChannel(s, label, kind === "DRUMS" ? drumSpec() : synthSpec(presetPatch("Saw Lead")), free < 0 ? 0 : free);
      if (kind === "DRUMS") channel.stepPitch = 36;
      id = channel.id;
    });
    this.selectedChannel = id;
    this.notify({ kind: "selection" });
    return id;
  }

  removeChannel(id: number): void {
    this.requireChannel(id);
    this.edit("Remove channel", (s) => {
      s.channels = s.channels.filter((c) => c.id !== id);
      for (const pattern of s.patterns) delete pattern.notes[String(id)];
    });
  }

  renameChannel(id: number, name: string): void {
    this.requireChannel(id);
    this.edit("Rename channel", (s) => (findChannel(s, id)!.name = name), { kind: "song" }, `channel-name:${id}`);
  }

  setChannelMuted(id: number, muted: boolean): void {
    this.requireChannel(id);
    this.edit(muted ? "Mute channel" : "Unmute channel", (s) => (findChannel(s, id)!.muted = muted));
  }

  setChannelMixer(id: number, mixerIndex: number): void {
    this.requireChannel(id);
    this.edit("Route channel", (s) => {
      if (mixerIndex < 0 || mixerIndex >= s.mixer.strips.length) throw new Error(`Mixer strip ${mixerIndex} does not exist`);
      findChannel(s, id)!.mixerIndex = mixerIndex;
    });
  }

  setStepPitch(id: number, pitch: number): void {
    this.requireChannel(id);
    this.edit("Step pitch", (s) => {
      if (!(pitch >= 0 && pitch <= 127)) throw new Error(`stepPitch must be 0..127: ${pitch}`);
      findChannel(s, id)!.stepPitch = pitch;
    });
  }

  /** Switches a channel between the synthesizer and the drum kit (the only kinds this version plays). */
  setInstrumentType(id: number, type: InstrumentType): void {
    this.requireChannel(id);
    this.edit("Change instrument", (s) => {
      const channel = findChannel(s, id)!;
      channel.instrument.type = type;
    });
  }

  loadPreset(id: number, preset: string): void {
    this.requireChannel(id);
    const patch = presetPatch(preset);
    this.edit("Load preset", (s) => {
      const channel = findChannel(s, id)!;
      channel.instrument.type = "SYNTH";
      channel.instrument.patch = patch;
    });
  }

  /** Sets one synth setting; numbers outside the setting's use are the caller's to range-check (the UI does). */
  setPatchField<K extends keyof SynthPatch>(id: number, field: K, value: SynthPatch[K]): void {
    this.requireChannel(id);
    if (field === "lfoTarget" && !LFO_TARGETS.includes(value as never)) throw new Error(`Unknown LFO target ${String(value)}`);
    this.edit("Edit instrument", (s) => (findChannel(s, id)!.instrument.patch[field] = value), { kind: "song", quiet: true }, `patch:${id}:${field}`);
  }

  private requireChannel(id: number): void {
    if (!findChannel(this.songData, id)) throw new Error(`No channel with id ${id}`);
  }

  // ---- Patterns -----------------------------------------------------------------------------------------------

  addPattern(bars = 1): number {
    let id = 0;
    this.edit("Add pattern", (s) => (id = addPattern(s, `Pattern ${s.nextPatternId}`, bars).id));
    this.selectedPattern = id;
    this.notify({ kind: "selection" });
    this.notify({ kind: "song" });
    return id;
  }

  /** Copies a pattern with all its notes and selects the copy. */
  duplicatePattern(id: number): number {
    const source = this.requirePattern(id);
    let newId = 0;
    this.edit("Duplicate pattern", (s) => {
      const copy = addPattern(s, `${source.name} copy`, source.lengthTicks / TICKS_PER_BAR);
      copy.notes = structuredClone(source.notes);
      newId = copy.id;
    });
    this.selectedPattern = newId;
    this.notify({ kind: "selection" });
    return newId;
  }

  /** Removes a pattern and the clips that played it. The last pattern cannot be removed. */
  removePattern(id: number): void {
    this.requirePattern(id);
    if (this.songData.patterns.length <= 1) throw new Error("A song needs at least one pattern");
    this.edit("Remove pattern", (s) => {
      s.patterns = s.patterns.filter((p) => p.id !== id);
      s.clips = s.clips.filter((c) => c[0] !== id);
    });
  }

  renamePattern(id: number, name: string): void {
    this.requirePattern(id);
    this.edit("Rename pattern", (s) => (findPattern(s, id)!.name = name), { kind: "song" }, `pattern-name:${id}`);
  }

  /** Changes the length; notes that start at or after the new end are removed, others are cut to fit. */
  setPatternBars(id: number, bars: number): void {
    this.requirePattern(id);
    if (!Number.isInteger(bars) || bars < 1 || bars > MAX_PATTERN_BARS) throw new Error(`A pattern is 1..${MAX_PATTERN_BARS} bars long: ${bars}`);
    this.edit("Pattern length", (s) => {
      const pattern = findPattern(s, id)!;
      pattern.lengthTicks = bars * TICKS_PER_BAR;
      for (const [channel, notes] of Object.entries(pattern.notes)) {
        const kept = notes
          .filter((n) => n[0] < pattern.lengthTicks)
          .map((n): NoteTuple => (n[0] + n[1] > pattern.lengthTicks ? [n[0], pattern.lengthTicks - n[0], n[2], n[3]] : n));
        if (kept.length > 0) pattern.notes[channel] = kept;
        else delete pattern.notes[channel];
      }
    });
  }

  private requirePattern(id: number): PatternData {
    const pattern = findPattern(this.songData, id);
    if (!pattern) throw new Error(`No pattern with id ${id}`);
    return pattern;
  }

  // ---- Notes --------------------------------------------------------------------------------------------------

  /** Replaces all notes of a channel in a pattern, cut to the pattern's length and sorted by time then pitch. */
  setNotes(patternId: number, channelId: number, notes: NoteTuple[], label = "Edit notes"): void {
    this.requirePattern(patternId);
    this.requireChannel(channelId);
    notes.forEach(checkNote);
    this.edit(label, (s) => {
      const pattern = findPattern(s, patternId)!;
      const fitted = notes
        .filter((n) => n[0] < pattern.lengthTicks)
        .map((n): NoteTuple => (n[0] + n[1] > pattern.lengthTicks ? [n[0], pattern.lengthTicks - n[0], n[2], n[3]] : [...n]))
        .sort((a, b) => a[0] - b[0] || a[2] - b[2]);
      if (fitted.length > 0) pattern.notes[String(channelId)] = fitted;
      else delete pattern.notes[String(channelId)];
    });
  }

  /** Adds one note; returns its index in the sorted list afterwards. */
  addNote(patternId: number, channelId: number, note: NoteTuple): number {
    const pattern = this.requirePattern(patternId);
    this.setNotes(patternId, channelId, [...notesOf(pattern, channelId), note], "Add note");
    return notesOf(this.requirePattern(patternId), channelId).findIndex((n) => n[0] === note[0] && n[2] === note[2]);
  }

  removeNote(patternId: number, channelId: number, index: number): void {
    const notes = [...notesOf(this.requirePattern(patternId), channelId)];
    if (index < 0 || index >= notes.length) throw new Error(`No note ${index}`);
    notes.splice(index, 1);
    this.setNotes(patternId, channelId, notes, "Delete note");
  }

  /** Replaces one note (move, resize, velocity); returns its index in the sorted list afterwards. */
  replaceNote(patternId: number, channelId: number, index: number, note: NoteTuple, label = "Edit note"): number {
    const notes = [...notesOf(this.requirePattern(patternId), channelId)];
    if (index < 0 || index >= notes.length) throw new Error(`No note ${index}`);
    notes[index] = note;
    this.setNotes(patternId, channelId, notes, label);
    return notesOf(this.requirePattern(patternId), channelId).findIndex((n) => n[0] === note[0] && n[2] === note[2]);
  }

  /** Switches a step of the step sequencer: removed if it was on, added with `velocity` if off. */
  toggleStep(patternId: number, channelId: number, step: number, velocity = 0.8): void {
    const pattern = this.requirePattern(patternId);
    const channel = findChannel(this.songData, channelId);
    if (!channel) throw new Error(`No channel with id ${channelId}`);
    if (step < 0 || step * STEP_TICKS >= pattern.lengthTicks) throw new Error(`Step ${step} is outside the pattern`);
    const tick = step * STEP_TICKS;
    const notes = [...notesOf(pattern, channelId)];
    const at = notes.findIndex((n) => n[0] === tick && n[2] === channel.stepPitch);
    if (at >= 0) notes.splice(at, 1);
    else notes.push([tick, STEP_TICKS, channel.stepPitch, velocity]);
    this.setNotes(patternId, channelId, notes, "Toggle step");
  }

  isStepOn(patternId: number, channelId: number, step: number): boolean {
    const channel = findChannel(this.songData, channelId);
    const pattern = findPattern(this.songData, patternId);
    if (!channel || !pattern) return false;
    return notesOf(pattern, channelId).some((n) => n[0] === step * STEP_TICKS && n[2] === channel.stepPitch);
  }

  // ---- Playlist -----------------------------------------------------------------------------------------------

  /** Places a pattern on the playlist; the clip must fit its track. */
  addClip(clip: ClipTuple): void {
    if (!canPlaceClip(this.songData, clip)) throw new Error(`Clip does not fit the playlist: ${JSON.stringify(clip)}`);
    this.edit("Add clip", (s) => s.clips.push([...clip]));
  }

  removeClip(index: number): void {
    if (index < 0 || index >= this.songData.clips.length) throw new Error(`No clip ${index}`);
    this.edit("Delete clip", (s) => s.clips.splice(index, 1));
  }

  /** Moves or resizes a clip; it must still fit its track. */
  replaceClip(index: number, clip: ClipTuple): void {
    if (index < 0 || index >= this.songData.clips.length) throw new Error(`No clip ${index}`);
    if (!canPlaceClip(this.songData, clip, index)) throw new Error(`Clip does not fit the playlist: ${JSON.stringify(clip)}`);
    this.edit("Edit clip", (s) => (s.clips[index] = [...clip]));
  }

  // ---- Mixer --------------------------------------------------------------------------------------------------

  setStrip(index: number | "master", values: { volume?: number; pan?: number; muted?: boolean; solo?: boolean }): void {
    this.edit(
      "Mixer",
      (s) => {
        const strip = index === "master" ? s.mixer.master : s.mixer.strips[index];
        if (!strip) throw new Error(`No mixer strip ${index}`);
        Object.assign(strip, values);
      },
      { kind: "strip", index, values },
      Object.keys(values).some((k) => k === "volume" || k === "pan") ? `strip:${index}:${Object.keys(values).join()}` : undefined,
    );
  }

  addEffect(index: number | "master", type: string): void {
    createEffect(type, 44100); // throws for an unknown type
    this.edit("Add effect", (s) => {
      const strip = index === "master" ? s.mixer.master : s.mixer.strips[index];
      if (!strip) throw new Error(`No mixer strip ${index}`);
      if (strip.effects.length >= MAX_EFFECTS) throw new Error(`A strip holds at most ${MAX_EFFECTS} effects`);
      const effect = createEffect(type, 44100);
      const params: Record<string, number> = {};
      for (const name of effect.parameterNames) params[name] = effect.getParameter(name);
      strip.effects.push({ type, params });
    });
  }

  removeEffect(index: number | "master", effectIndex: number): void {
    this.edit("Remove effect", (s) => {
      const strip = index === "master" ? s.mixer.master : s.mixer.strips[index];
      if (!strip?.effects[effectIndex]) throw new Error(`No effect ${effectIndex} on strip ${index}`);
      strip.effects.splice(effectIndex, 1);
    });
  }

  setEffectParameter(index: number | "master", effectIndex: number, name: string, value: number): void {
    this.edit(
      "Effect",
      (s) => {
        const strip = index === "master" ? s.mixer.master : s.mixer.strips[index];
        const effect = strip?.effects[effectIndex];
        if (!effect) throw new Error(`No effect ${effectIndex} on strip ${index}`);
        const probe = createEffect(effect.type, 44100);
        probe.setParameter(name, value); // throws for an unknown name; clamps the value
        effect.params[name] = probe.getParameter(name);
      },
      { kind: "effect", strip: index, effectIndex, name, value },
      `effect:${index}:${effectIndex}:${name}`,
    );
  }
}
