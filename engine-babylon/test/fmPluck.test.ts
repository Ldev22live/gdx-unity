import { describe, expect, it } from "vitest";
import { FM_PRESET_NAMES, FmSynthesizer, MAX_FM_VOICES, defaultFmPatch, fmPreset } from "../src/audio/fm";
import { MAX_PLUCK_VOICES, PLUCK_PRESET_NAMES, PluckedString, defaultPluckPatch, pluckPreset } from "../src/audio/pluck";
import { SongPlayer } from "../src/audio/player";
import { SAMPLE_RATE, defaultSong, parseSong, songToJson, synthSpec, type SongData } from "../src/audio/song";
import { noteToHz } from "../src/audio/synth";
import { MusicModel } from "../src/editor/music/MusicModel";

const RATE = SAMPLE_RATE;

function render(instrument: { render(l: Float32Array, r: Float32Array, offset: number, frames: number): void }, frames: number): Float32Array {
  const left = new Float32Array(frames);
  instrument.render(left, new Float32Array(frames), 0, frames);
  return left;
}

function peak(data: Float32Array, from = 0, to = data.length): number {
  let p = 0;
  for (let i = from; i < to; i++) p = Math.max(p, Math.abs(data[i]!));
  return p;
}

function rms(data: Float32Array, from: number, to: number): number {
  let sum = 0;
  for (let i = from; i < to; i++) sum += data[i]! * data[i]!;
  return Math.sqrt(sum / Math.max(to - from, 1));
}

function allFinite(data: Float32Array): boolean {
  return data.every((v) => Number.isFinite(v));
}

/** The lag at which the signal best matches itself shifted, searched within 20% of the lag expected for `hz`. */
function period(data: Float32Array, from: number, to: number, hz: number): number {
  const expected = RATE / hz;
  let bestLag = 0;
  let best = -2;
  for (let lag = Math.floor(expected * 0.8); lag <= Math.floor(expected * 1.2) + 1; lag++) {
    let sum = 0;
    let a = 0;
    let b = 0;
    for (let i = from; i < to; i++) {
      sum += data[i]! * data[i + lag]!;
      a += data[i]! * data[i]!;
      b += data[i + lag]! * data[i + lag]!;
    }
    const score = sum / Math.sqrt(a * b + 1e-12);
    if (score > best) {
      best = score;
      bestLag = lag;
    }
  }
  return bestLag;
}

describe("FM synthesizer on the web (TD-075)", () => {
  it("makes every preset audible, finite and not clipping across the keyboard", () => {
    for (const name of FM_PRESET_NAMES) {
      for (const note of [24, 60, 96]) {
        const synth = new FmSynthesizer(RATE, fmPreset(name));
        synth.noteOn(note, 1);
        const data = render(synth, 30000);
        expect(peak(data), `${name} at ${note}`).toBeGreaterThan(0.01);
        expect(allFinite(data), `${name} at ${note}`).toBe(true);
        expect(peak(data), `${name} at ${note}`).toBeLessThan(1.5);
      }
    }
  });

  it("makes a plain sine at the note's pitch with no modulation", () => {
    const patch = { ...defaultFmPatch(), index: 0, attack: 0.001, decay: 0.001, sustain: 1, release: 0.05, volume: 1 };
    const synth = new FmSynthesizer(RATE, patch);
    synth.noteOn(60, 1);
    const data = render(synth, 20000);
    expect(Math.abs(period(data, 4000, 8000, noteToHz(60)) - RATE / noteToHz(60))).toBeLessThanOrEqual(1);
    expect(Math.abs(peak(data, 4000, 8000) - peak(data, 12000, 16000))).toBeLessThan(0.02);
  });

  it("takes over the oldest voice beyond sixteen notes, falls silent after release, and reads its patch live", () => {
    const synth = new FmSynthesizer(RATE, fmPreset("Metal Pad"));
    for (let i = 0; i < MAX_FM_VOICES + 4; i++) synth.noteOn(40 + i, 0.8);
    expect(synth.activeVoiceCount).toBe(MAX_FM_VOICES);

    const piano = new FmSynthesizer(RATE, fmPreset("FM Piano"));
    piano.noteOn(60, 1);
    render(piano, 4000);
    piano.noteOff(60);
    const tail = render(piano, RATE);
    expect(peak(tail, tail.length - 1000)).toBe(0);
    expect(piano.activeVoiceCount).toBe(0);

    const patch = fmPreset("Bell");
    const live = new FmSynthesizer(RATE, patch);
    live.noteOn(60, 1);
    patch.volume = 0;
    expect(peak(render(live, 2000))).toBe(0);
  });

  it("refuses an unknown preset", () => {
    expect(() => fmPreset("Nope")).toThrow(/Unknown FM preset/);
  });
});

describe("plucked string on the web (TD-075)", () => {
  it("makes every preset audible, finite and fall silent across the keyboard", () => {
    for (const name of PLUCK_PRESET_NAMES) {
      const patch = pluckPreset(name);
      for (const note of [24, 60, 96]) {
        const string = new PluckedString(RATE, patch);
        string.noteOn(note, 1);
        const data = render(string, Math.floor((patch.decay * 1.5 + 0.2) * RATE));
        expect(peak(data, 0, 4000), `${name} at ${note}`).toBeGreaterThan(0.01);
        expect(allFinite(data), `${name} at ${note}`).toBe(true);
        expect(peak(data), `${name} at ${note}`).toBeLessThan(1.5);
        expect(peak(data, data.length - 1000), `${name} at ${note} never ended`).toBe(0);
      }
    }
  });

  it("rings at the note's pitch", () => {
    for (const note of [45, 57, 69, 81]) {
      const hz = noteToHz(note);
      const string = new PluckedString(RATE, pluckPreset("Harp"));
      string.noteOn(note, 1);
      const measured = period(render(string, 12000), 3000, 7000, hz);
      expect(Math.abs(measured - RATE / hz), `note ${note}`).toBeLessThanOrEqual((RATE / hz) * 0.02 + 1);
    }
  });

  it("sounds the same every time a note is played, and holds the extreme notes in its delay line", () => {
    const first = new PluckedString(RATE, pluckPreset("Nylon Guitar"));
    const second = new PluckedString(RATE, pluckPreset("Nylon Guitar"));
    first.noteOn(52, 1);
    second.noteOn(52, 1);
    expect(Array.from(render(first, 8000))).toEqual(Array.from(render(second, 8000)));
    for (const note of [0, 127]) {
      const string = new PluckedString(RATE, pluckPreset("Nylon Guitar"));
      string.noteOn(note, 1);
      const data = render(string, 20000);
      expect(allFinite(data), `note ${note}`).toBe(true);
      expect(peak(data), `note ${note}`).toBeLessThan(1.5);
    }
  });

  it("falls by about sixty decibels in its decay time, sooner after release, and takes over the oldest string", () => {
    const patch = { ...defaultPluckPatch(), decay: 2, damping: 0, brightness: 1 };
    const string = new PluckedString(RATE, patch);
    string.noteOn(69, 1);
    const data = render(string, Math.floor(2.2 * RATE));
    const start = rms(data, 0, 4000);
    expect(rms(data, RATE - 2000, RATE + 2000)).toBeGreaterThan(0.01 * start);
    expect(rms(data, 2 * RATE - 2000, 2 * RATE + 2000)).toBeLessThan(0.02 * start);

    const released = new PluckedString(RATE, { ...defaultPluckPatch(), decay: 8, release: 0.05 });
    released.noteOn(60, 1);
    render(released, 4000);
    released.noteOff(60);
    const tail = render(released, 22050);
    expect(peak(tail, tail.length - 1000)).toBe(0);

    const many = new PluckedString(RATE, pluckPreset("Harp"));
    for (let i = 0; i < MAX_PLUCK_VOICES + 4; i++) many.noteOn(40 + i, 0.8);
    expect(many.activeVoiceCount).toBe(MAX_PLUCK_VOICES);
  });

  it("refuses an unknown preset", () => {
    expect(() => pluckPreset("Nope")).toThrow(/Unknown plucked-string preset/);
  });
});

describe("songs with the new instruments (TD-075)", () => {
  function songWithBoth(): SongData {
    const song = defaultSong();
    const pattern = song.patterns[0]!;
    const fm = song.channels[3]!;
    const string = song.channels[4]!;
    fm.instrument = { ...synthSpec(), type: "FM", fm: { ...fmPreset("Glass"), ratio: 7.5 } };
    string.instrument = { ...synthSpec(), type: "PLUCK", pluck: { ...pluckPreset("Harp"), decay: 3.25 } };
    pattern.notes[String(fm.id)] = [[0, 96, 72, 1]];
    pattern.notes[String(string.id)] = [[0, 96, 52, 1], [96, 96, 55, 1]];
    return song;
  }

  it("round-trips FM and plucked-string settings through the file text", () => {
    const text = songToJson(songWithBoth());
    const read = parseSong(text);
    expect(read.channels[3]!.instrument.type).toBe("FM");
    expect(read.channels[3]!.instrument.fm.ratio).toBe(7.5);
    expect(read.channels[4]!.instrument.type).toBe("PLUCK");
    expect(read.channels[4]!.instrument.pluck.decay).toBe(3.25);
    expect(songToJson(read)).toBe(text);
  });

  it("opens a song written before the new blocks existed, with their defaults", () => {
    const text = songToJson(songWithBoth()).replace(/,"fm":\{[^}]*\},"pluck":\{[^}]*\}/g, "");
    expect(text).not.toContain('"fm"');
    const read = parseSong(text);
    for (const channel of read.channels) {
      expect(channel.instrument.fm).toEqual(fmPreset("Bell"));
      expect(channel.instrument.pluck).toEqual(pluckPreset("Nylon Guitar"));
    }
  });

  it("plays channels of the new kinds without complaint, and they are silent at zero volume", () => {
    const song = songWithBoth();
    const player = new SongPlayer(song);
    expect(player.errors).toEqual([]);
    player.play();
    const left = new Float32Array(RATE);
    player.render(left, new Float32Array(RATE), RATE);
    expect(peak(left)).toBeGreaterThan(0.05);
    expect(allFinite(left)).toBe(true);

    const quiet = structuredClone(song);
    quiet.channels[3]!.instrument.fm.volume = 0;
    quiet.channels[4]!.instrument.pluck.volume = 0;
    for (const channel of quiet.channels) if (channel.instrument.type === "SYNTH" || channel.instrument.type === "DRUMS") channel.muted = true;
    const silent = new SongPlayer(quiet);
    silent.play();
    const none = new Float32Array(RATE);
    silent.render(none, new Float32Array(RATE), RATE);
    expect(peak(none)).toBe(0);
  });
});

describe("the music model with the new instruments (TD-075)", () => {
  it("adds an FM or string channel, loads presets, edits a knob and undoes each step", () => {
    const m = new MusicModel(defaultSong());
    const fm = m.addChannel("FM");
    expect(m.song.channels.find((c) => c.id === fm)!.instrument.type).toBe("FM");
    m.loadFmPreset(fm, "FM Bass");
    expect(m.song.channels.find((c) => c.id === fm)!.instrument.fm).toEqual(fmPreset("FM Bass"));
    m.setFmField(fm, "index", 9);
    expect(m.song.channels.find((c) => c.id === fm)!.instrument.fm.index).toBe(9);
    m.undo();
    expect(m.song.channels.find((c) => c.id === fm)!.instrument.fm.index).toBe(fmPreset("FM Bass").index);

    const string = m.addChannel("PLUCK");
    expect(m.song.channels.find((c) => c.id === string)!.instrument.type).toBe("PLUCK");
    m.loadPluckPreset(string, "Pizzicato");
    m.setPluckField(string, "damping", 0.9);
    expect(m.song.channels.find((c) => c.id === string)!.instrument.pluck.damping).toBe(0.9);
    expect(() => m.loadFmPreset(string, "Nope")).toThrow(/Unknown FM preset/);
  });
});
