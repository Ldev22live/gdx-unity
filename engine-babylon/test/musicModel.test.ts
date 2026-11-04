import { describe, expect, it } from "vitest";
import { MusicModel, MAX_MUSIC_UNDO, type MusicChange } from "../src/editor/music/MusicModel";
import { STEP_TICKS, TICKS_PER_BAR, defaultSong, notesOf } from "../src/audio/song";

function model(): MusicModel {
  return new MusicModel(defaultSong());
}

function record(m: MusicModel): MusicChange[] {
  const seen: MusicChange[] = [];
  m.addListener((c) => seen.push(c));
  return seen;
}

describe("MusicModel steps and notes", () => {
  it("toggles a step on and off on the channel's step pitch", () => {
    const m = model();
    const kick = m.song.channels[0]!;
    m.toggleStep(m.patternId, kick.id, 4);
    expect(m.isStepOn(m.patternId, kick.id, 4)).toBe(true);
    expect(notesOf(m.pattern!, kick.id)).toEqual([[4 * STEP_TICKS, STEP_TICKS, 36, 0.8]]);
    m.toggleStep(m.patternId, kick.id, 4);
    expect(m.isStepOn(m.patternId, kick.id, 4)).toBe(false);
    expect(m.pattern!.notes[String(kick.id)]).toBeUndefined();
  });

  it("refuses a step outside the pattern", () => {
    const m = model();
    expect(() => m.toggleStep(m.patternId, m.channelId, 16)).toThrow(/outside the pattern/);
  });

  it("keeps notes sorted and cuts them to the pattern", () => {
    const m = model();
    const lead = m.song.channels[3]!.id;
    m.setNotes(m.patternId, lead, [[96, 24, 64, 1], [0, 24, 60, 1], [TICKS_PER_BAR - 10, 100, 62, 1], [TICKS_PER_BAR + 5, 24, 70, 1]]);
    const notes = notesOf(m.pattern!, lead);
    expect(notes.map((n) => n[0])).toEqual([0, 96, TICKS_PER_BAR - 10]);
    expect(notes[2]![1]).toBe(10);
  });

  it("adds, replaces and removes a note", () => {
    const m = model();
    const lead = m.song.channels[3]!.id;
    const index = m.addNote(m.patternId, lead, [48, 24, 67, 0.8]);
    expect(index).toBe(0);
    const moved = m.replaceNote(m.patternId, lead, index, [96, 48, 69, 0.8], "Move note");
    expect(notesOf(m.pattern!, lead)[moved]).toEqual([96, 48, 69, 0.8]);
    m.removeNote(m.patternId, lead, moved);
    expect(notesOf(m.pattern!, lead)).toEqual([]);
  });

  it("rejects a note the engine cannot play, leaving the song untouched", () => {
    const m = model();
    const lead = m.song.channels[3]!.id;
    expect(() => m.setNotes(m.patternId, lead, [[0, 0, 60, 1]])).toThrow(/length/);
    expect(() => m.setNotes(m.patternId, lead, [[0, 10, 200, 1]])).toThrow(/pitch/);
    expect(m.canUndo).toBe(false);
    expect(m.isDirty).toBe(false);
  });

  it("shortens a pattern and drops the notes past the new end", () => {
    const m = model();
    m.setPatternBars(m.patternId, 2);
    const lead = m.song.channels[3]!.id;
    m.setNotes(m.patternId, lead, [[0, 24, 60, 1], [TICKS_PER_BAR + 24, 24, 62, 1]]);
    m.setPatternBars(m.patternId, 1);
    expect(notesOf(m.pattern!, lead)).toHaveLength(1);
    expect(() => m.setPatternBars(m.patternId, 99)).toThrow(/1\.\.16 bars/);
  });
});

describe("MusicModel undo", () => {
  it("undoes and redoes an edit, with a label, and marks the song dirty", () => {
    const m = model();
    m.toggleStep(m.patternId, m.channelId, 0);
    expect(m.undoLabel).toBe("Toggle step");
    expect(m.isDirty).toBe(true);
    m.undo();
    expect(m.isStepOn(m.patternId, m.channelId, 0)).toBe(false);
    expect(m.canRedo).toBe(true);
    m.redo();
    expect(m.isStepOn(m.patternId, m.channelId, 0)).toBe(true);
    m.markSaved();
    expect(m.isDirty).toBe(false);
  });

  it("merges the moves of one knob into one step, but not two different knobs", () => {
    const m = model();
    m.setStrip(0, { volume: 0.5 });
    m.setStrip(0, { volume: 0.6 });
    m.setStrip(0, { volume: 0.7 });
    expect(m.song.mixer.strips[0]!.volume).toBe(0.7);
    m.undo();
    expect(m.song.mixer.strips[0]!.volume).toBe(0.8);
    expect(m.canUndo).toBe(false);
    m.setStrip(0, { volume: 0.5 });
    m.setStrip(1, { volume: 0.5 });
    m.undo();
    expect(m.song.mixer.strips[1]!.volume).toBe(0.8);
    expect(m.song.mixer.strips[0]!.volume).toBe(0.5);
  });

  it("keeps at most 100 steps", () => {
    const m = model();
    for (let i = 0; i < MAX_MUSIC_UNDO + 20; i++) m.toggleStep(m.patternId, m.channelId, i % 16);
    let steps = 0;
    while (m.canUndo) {
      m.undo();
      steps++;
    }
    expect(steps).toBe(MAX_MUSIC_UNDO);
  });

  it("drops the redo history after a new edit", () => {
    const m = model();
    m.toggleStep(m.patternId, m.channelId, 0);
    m.undo();
    m.toggleStep(m.patternId, m.channelId, 1);
    expect(m.canRedo).toBe(false);
  });
});

describe("MusicModel structure", () => {
  it("adds a channel on an unused insert, selects it, and undoes cleanly", () => {
    const m = model();
    const id = m.addChannel("SYNTH");
    expect(m.channelId).toBe(id);
    expect(m.channel!.mixerIndex).toBe(5);
    m.undo();
    expect(m.song.channels.find((c) => c.id === id)).toBeUndefined();
  });

  it("stops at sixteen channels", () => {
    const m = model();
    while (m.song.channels.length < 16) m.addChannel("DRUMS");
    expect(() => m.addChannel("SYNTH")).toThrow(/at most 16/);
  });

  it("removing a channel removes its notes everywhere", () => {
    const m = model();
    const lead = m.song.channels[3]!.id;
    m.setNotes(m.patternId, lead, [[0, 24, 60, 1]]);
    m.removeChannel(lead);
    expect(m.pattern!.notes[String(lead)]).toBeUndefined();
    expect(m.song.channels.some((c) => c.id === lead)).toBe(false);
    expect(m.channelId).not.toBe(lead);
  });

  it("will not remove the last pattern, and removing one removes its clips", () => {
    const m = model();
    expect(() => m.removePattern(m.patternId)).toThrow(/at least one pattern/);
    const second = m.addPattern(2);
    m.addClip([second, 0, 0, 2 * TICKS_PER_BAR]);
    expect(m.song.clips).toHaveLength(1);
    m.removePattern(second);
    expect(m.song.clips).toHaveLength(0);
    expect(m.patternId).toBe(m.song.patterns[0]!.id);
  });

  it("duplicates a pattern with its notes", () => {
    const m = model();
    m.toggleStep(m.patternId, m.channelId, 2);
    const copy = m.duplicatePattern(m.patternId);
    expect(copy).not.toBe(m.song.patterns[0]!.id);
    expect(m.isStepOn(copy, m.channelId, 2)).toBe(true);
  });

  it("refuses overlapping clips and out-of-range tempo", () => {
    const m = model();
    const p = m.patternId;
    m.addClip([p, 0, 0, TICKS_PER_BAR]);
    expect(() => m.addClip([p, 0, 100, TICKS_PER_BAR])).toThrow(/does not fit/);
    m.addClip([p, 1, 0, TICKS_PER_BAR]);
    expect(() => m.replaceClip(1, [p, 0, 0, TICKS_PER_BAR])).toThrow(/does not fit/);
    expect(() => m.setBpm(5)).toThrow(/bpm must be/);
    expect(() => m.setSwing(2)).toThrow(/swing must be/);
  });
});

describe("MusicModel mixer and instrument", () => {
  it("reports knob moves as live changes and structure as song changes", () => {
    const m = model();
    const seen = record(m);
    m.setStrip(2, { pan: 0.5 });
    m.addEffect(2, "delay");
    m.setEffectParameter(2, 0, "time", 0.2);
    m.setPatchField(m.song.channels[3]!.id, "cutoff", 1000);
    expect(seen.map((c) => c.kind)).toEqual(["strip", "song", "effect", "song"]);
    expect(seen[3]).toEqual({ kind: "song", quiet: true });
    expect(m.song.mixer.strips[2]!.effects[0]).toEqual({ type: "delay", params: { time: 0.2, feedback: 0.4, mix: 0.3 } });
  });

  it("limits a strip to four effects and rejects unknown ones", () => {
    const m = model();
    for (let i = 0; i < 4; i++) m.addEffect(0, "reverb");
    expect(() => m.addEffect(0, "reverb")).toThrow(/at most 4/);
    expect(() => m.addEffect(1, "chorus")).toThrow(/Unknown effect type/);
    expect(() => m.setEffectParameter(0, 0, "nope", 1)).toThrow(/Unknown reverb parameter/);
  });

  it("clamps an effect parameter into the song", () => {
    const m = model();
    m.addEffect(0, "delay");
    m.setEffectParameter(0, 0, "feedback", 9);
    expect(m.song.mixer.strips[0]!.effects[0]!.params["feedback"]).toBeCloseTo(0.95, 6);
  });

  it("loads a preset into a channel and edits one setting", () => {
    const m = model();
    const lead = m.song.channels[3]!.id;
    m.loadPreset(lead, "Pluck");
    expect(m.song.channels[3]!.instrument.patch.filterEnvOctaves).toBe(3);
    m.setPatchField(lead, "osc1", "SQUARE");
    expect(m.song.channels[3]!.instrument.patch.osc1).toBe("SQUARE");
    expect(() => m.loadPreset(lead, "Nope")).toThrow(/Unknown synth preset/);
  });

  it("opening a song clears history and selects its first pattern and channel", () => {
    const m = model();
    m.toggleStep(m.patternId, m.channelId, 0);
    const other = defaultSong("Other");
    m.load(other);
    expect(m.canUndo).toBe(false);
    expect(m.isDirty).toBe(false);
    expect(m.song.name).toBe("Other");
  });
});
