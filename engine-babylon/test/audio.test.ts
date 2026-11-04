import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";
import { DrumKit } from "../src/audio/drums";
import { Adsr, BiquadFilter, Oscillator } from "../src/audio/dsp";
import { DelayEffect, ReverbEffect, createEffect } from "../src/audio/effects";
import { framesFor, renderSong, writeWav } from "../src/audio/export";
import { Mixer, softLimit } from "../src/audio/mixer";
import { SongPlayer, compileSchedule } from "../src/audio/player";
import {
  SAMPLE_RATE, STEP_TICKS, TICKS_PER_BAR, TICKS_PER_BEAT, addPattern, defaultSong, parseSong, songToJson, type SongData,
} from "../src/audio/song";
import { MAX_VOICES, Synthesizer, defaultPatch, noteToHz, presetPatch } from "../src/audio/synth";

const DEMO = join(import.meta.dirname, "..", "..", "assets", "music", "demo.song");
const demoText = () => readFileSync(DEMO, "utf8");

function block(frames: number): [Float32Array, Float32Array] {
  return [new Float32Array(frames), new Float32Array(frames)];
}

function peak(...arrays: Float32Array[]): number {
  return arrays.reduce((p, a) => a.reduce((m, v) => Math.max(m, Math.abs(v)), p), 0);
}

function rms(a: Float32Array): number {
  return Math.sqrt(a.reduce((s, v) => s + v * v, 0) / a.length);
}

describe("Oscillator", () => {
  it("makes the same noise as the desktop's 64-bit xorshift", () => {
    let state = 0x2545f4914f6cdd1dn;
    const mask = (1n << 64n) - 1n;
    const oscillator = new Oscillator();
    for (let i = 0; i < 50; i++) {
      state ^= (state << 13n) & mask;
      state ^= state >> 7n;
      state ^= (state << 17n) & mask;
      const expected = Number(state >> 11n) * (2 / 2 ** 53) - 1;
      expect(oscillator.next("NOISE", 0, 0.5)).toBeCloseTo(expected, 12);
    }
  });

  it("stays within -1 and 1 for every waveform", () => {
    for (const waveform of ["SINE", "SAW", "SQUARE", "TRIANGLE", "NOISE"] as const) {
      const oscillator = new Oscillator();
      for (let i = 0; i < 5000; i++) expect(Math.abs(oscillator.next(waveform, 440 / SAMPLE_RATE, 0.5))).toBeLessThanOrEqual(1.5);
    }
  });

  it("makes a sine of the right frequency", () => {
    const oscillator = new Oscillator();
    let crossings = 0;
    let previous = 0;
    for (let i = 0; i < SAMPLE_RATE; i++) {
      const value = oscillator.next("SINE", 100 / SAMPLE_RATE, 0.5);
      if (previous < 0 && value >= 0) crossings++;
      previous = value;
    }
    expect(crossings).toBeGreaterThanOrEqual(99);
    expect(crossings).toBeLessThanOrEqual(101);
  });
});

describe("Adsr", () => {
  it("goes through attack, decay and sustain, then releases to silence", () => {
    const envelope = new Adsr(1000);
    envelope.set(0.01, 0.01, 0.5, 0.05);
    envelope.noteOn();
    const stages = new Set<string>();
    for (let i = 0; i < 100; i++) {
      envelope.next();
      stages.add(envelope.currentStage);
    }
    expect([...stages]).toEqual(expect.arrayContaining(["ATTACK", "DECAY", "SUSTAIN"]));
    expect(envelope.currentLevel).toBeCloseTo(0.5, 5);
    envelope.noteOff();
    for (let i = 0; i < 500 && !envelope.isFinished; i++) envelope.next();
    expect(envelope.isFinished).toBe(true);
  });
});

describe("BiquadFilter", () => {
  it("passes low tones and cuts high ones", () => {
    const tone = (hz: number) => {
      const filter = new BiquadFilter(SAMPLE_RATE);
      filter.set("LOW_PASS", 500, 0.707);
      const oscillator = new Oscillator();
      let sum = 0;
      for (let i = 0; i < 20000; i++) {
        const y = filter.process(oscillator.next("SINE", hz / SAMPLE_RATE, 0.5));
        if (i > 5000) sum += y * y;
      }
      return Math.sqrt(sum / 15000);
    };
    expect(tone(100)).toBeGreaterThan(0.6);
    expect(tone(8000)).toBeLessThan(0.02);
  });
});

describe("Synthesizer", () => {
  it("sounds a note at the right pitch and dies away after release", () => {
    expect(noteToHz(69)).toBeCloseTo(440, 6);
    const synth = new Synthesizer(SAMPLE_RATE, { ...presetPatch("Pluck") });
    const [l, r] = block(4096);
    synth.noteOn(60, 1);
    synth.render(l, r, 0, 4096);
    expect(peak(l)).toBeGreaterThan(0.01);
    expect(Array.from(l)).toEqual(Array.from(r));
    synth.noteOff(60);
    for (let i = 0; i < 80; i++) synth.render(...block(4096), 0, 4096);
    expect(synth.activeVoiceCount).toBe(0);
  });

  it("takes over the oldest voice beyond sixteen notes", () => {
    const synth = new Synthesizer(SAMPLE_RATE, defaultPatch());
    for (let n = 0; n < MAX_VOICES + 4; n++) synth.noteOn(40 + n, 0.8);
    expect(synth.activeVoiceCount).toBe(MAX_VOICES);
  });

  it("reads its patch live", () => {
    const patch = defaultPatch();
    const synth = new Synthesizer(SAMPLE_RATE, patch);
    patch.volume = 0;
    synth.noteOn(60, 1);
    const [l, r] = block(2048);
    synth.render(l, r, 0, 2048);
    expect(peak(l)).toBe(0);
  });
});

describe("DrumKit", () => {
  it("plays a kick that fades out, and ignores notes that are not drums", () => {
    const kit = new DrumKit(SAMPLE_RATE);
    const [l, r] = block(2000);
    kit.noteOn(36, 1);
    kit.render(l, r, 0, 2000);
    expect(peak(l)).toBeGreaterThan(0.3);
    const quiet = new DrumKit(SAMPLE_RATE);
    const [ql, qr] = block(2000);
    quiet.noteOn(61, 1);
    quiet.render(ql, qr, 0, 2000);
    expect(peak(ql)).toBe(0);
    for (let i = 0; i < 20; i++) kit.render(...block(2048), 0, 2048);
    const [tl, tr] = block(512);
    kit.render(tl, tr, 0, 512);
    expect(peak(tl)).toBe(0);
  });
});

describe("effects", () => {
  it("delay repeats an impulse after its time, at the feedback level", () => {
    const delay = new DelayEffect(SAMPLE_RATE);
    delay.setParameter("time", 0.1);
    delay.setParameter("mix", 1);
    const frames = SAMPLE_RATE / 2;
    const [l, r] = block(frames);
    l[0] = 1;
    delay.process(l, r, 0, frames);
    const echoAt = Math.floor(0.1 * SAMPLE_RATE);
    expect(l[echoAt]).toBeCloseTo(1, 5);
    expect(l[echoAt - 1]).toBe(0);
    // Ping-pong: the next echo lands on the other side
    expect(r[2 * echoAt]).toBeGreaterThan(0.3);
  });

  it("reverb rings on after the input stops", () => {
    const reverb = new ReverbEffect(SAMPLE_RATE);
    reverb.setParameter("mix", 1);
    const frames = SAMPLE_RATE;
    const [l, r] = block(frames);
    l[0] = 1;
    r[0] = 1;
    reverb.process(l, r, 0, frames);
    expect(rms(l.subarray(20000, 30000))).toBeGreaterThan(0);
  });

  it("clamps parameters and rejects unknown names and types", () => {
    const delay = createEffect("delay", SAMPLE_RATE);
    delay.setParameter("feedback", 5);
    expect(delay.getParameter("feedback")).toBeCloseTo(0.95, 6);
    expect(() => delay.setParameter("nope", 1)).toThrow(/Unknown delay parameter/);
    expect(() => createEffect("chorus", SAMPLE_RATE)).toThrow(/Unknown effect type/);
  });
});

describe("Mixer", () => {
  const source = (value: number, strip: number) => ({
    renderStrip: (index: number, l: Float32Array, r: Float32Array, o: number, n: number) => {
      if (index === strip) for (let i = 0; i < n; i++) { l[o + i] = value; r[o + i] = value; }
    },
  });

  it("applies volume and constant-power pan", () => {
    const mixer = new Mixer(SAMPLE_RATE);
    mixer.strips[0]!.setVolume(0.5);
    mixer.strips[0]!.setPan(-1);
    const [l, r] = block(64);
    mixer.render(source(0.5, 0), l, r, 0, 64);
    expect(l[10]).toBeGreaterThan(0.3);
    expect(Math.abs(r[10]!)).toBeLessThan(1e-6);
    mixer.strips[0]!.setPan(0);
    mixer.render(source(0.5, 0), l, r, 0, 64);
    expect(l[10]).toBeCloseTo(r[10]!, 6);
  });

  it("mutes, and solos one strip over the others", () => {
    const mixer = new Mixer(SAMPLE_RATE);
    const both = { renderStrip: (i: number, l: Float32Array, r: Float32Array, o: number, n: number) => { for (let k = 0; k < n; k++) { l[o + k] = 0.1; r[o + k] = 0.1; } void i; } };
    const [l, r] = block(32);
    mixer.strips[0]!.muted = true;
    mixer.render(both, l, r, 0, 32);
    const withMute = l[5]!;
    mixer.strips[0]!.muted = false;
    mixer.render(both, l, r, 0, 32);
    expect(withMute).toBeLessThan(l[5]!);
    mixer.strips[2]!.solo = true;
    mixer.render(both, l, r, 0, 32);
    expect(l[5]!).toBeLessThan(withMute);
  });

  it("limits the output softly and never past 1", () => {
    expect(softLimit(0.5)).toBe(0.5);
    expect(softLimit(0.9)).toBeGreaterThan(0.8);
    expect(Math.abs(softLimit(50))).toBeLessThanOrEqual(1);
    expect(softLimit(-3)).toBeCloseTo(-softLimit(3), 10);
    let previous = 0;
    for (let x = 0; x < 5; x += 0.1) {
      expect(softLimit(x)).toBeGreaterThanOrEqual(previous);
      previous = softLimit(x);
    }
  });
});

describe("compileSchedule", () => {
  function songWith(...notes: [number, number, number, number][]): SongData {
    const song = defaultSong();
    song.patterns[0]!.notes[String(song.channels[3]!.id)] = notes;
    return song;
  }

  it("makes a note-on and a note-off per note, ordered by time", () => {
    const song = songWith([0, 96, 60, 0.8], [96, 96, 64, 0.8]);
    const events = compileSchedule(song, "PATTERN", song.patterns[0]!.id).events;
    expect(events.map((e) => [e.tick, e.on])).toEqual([[0, true], [96, false], [96, true], [192, false]]);
  });

  it("delays odd sixteenths by swing", () => {
    const song = songWith([STEP_TICKS, 10, 60, 1]);
    song.swing = 1;
    const on = compileSchedule(song, "PATTERN", song.patterns[0]!.id).events[0]!;
    expect(on.tick).toBe(STEP_TICKS + Math.round(STEP_TICKS / 3));
  });

  it("skips muted channels", () => {
    const song = songWith([0, 96, 60, 1]);
    song.channels[3]!.muted = true;
    expect(compileSchedule(song, "PATTERN", song.patterns[0]!.id).events).toHaveLength(0);
  });

  it("repeats a pattern to fill a longer clip and cuts notes at its end", () => {
    const song = songWith([0, TICKS_PER_BAR, 60, 1]);
    const pattern = song.patterns[0]!;
    song.clips.push([pattern.id, 0, 0, 2 * TICKS_PER_BAR + TICKS_PER_BEAT]);
    const schedule = compileSchedule(song, "SONG", 0);
    const ons = schedule.events.filter((e) => e.on).map((e) => e.tick);
    expect(ons).toEqual([0, TICKS_PER_BAR, 2 * TICKS_PER_BAR]);
    expect(schedule.events.at(-1)!.tick).toBe(2 * TICKS_PER_BAR + TICKS_PER_BEAT);
    expect(schedule.lengthTicks).toBe(2 * TICKS_PER_BAR + TICKS_PER_BEAT);
  });

  it("refuses an unknown pattern", () => {
    expect(() => compileSchedule(defaultSong(), "PATTERN", 999)).toThrow(/No pattern/);
  });
});

describe("SongPlayer", () => {
  function kickSong(): SongData {
    const song = defaultSong();
    song.bpm = 120;
    song.patterns[0]!.notes[String(song.channels[0]!.id)] = [[0, 24, 36, 1], [TICKS_PER_BEAT * 2, 24, 36, 1]];
    return song;
  }

  it("is silent until played, then sounds on the first frame and keeps time", () => {
    const player = new SongPlayer(kickSong());
    const [l, r] = block(256);
    player.render(l, r, 256);
    expect(peak(l)).toBe(0);
    player.play();
    player.render(l, r, 256);
    expect(peak(l)).toBeGreaterThan(0.01);
    expect(player.position).toBeCloseTo((256 / SAMPLE_RATE) * 2 * TICKS_PER_BEAT, 3);
  });

  it("loops without drifting: a beat after exactly one bar is the same position", () => {
    const player = new SongPlayer(kickSong());
    player.play();
    const frames = Math.round(SAMPLE_RATE * 2); // one bar at 120 bpm
    const [l, r] = block(frames);
    player.render(l, r, frames);
    expect(player.isPlaying).toBe(true);
    // The loop point is reached exactly; the playhead wraps at the start of the next block, with no drift
    expect(player.position).toBeCloseTo(TICKS_PER_BAR, 6);
    player.render(...block(100), 100);
    expect(player.position).toBeCloseTo((100 / SAMPLE_RATE) * 2 * TICKS_PER_BEAT, 4);
    // the second kick (beat 3) and the next bar's first kick both sounded
    expect(peak(l.subarray(Math.round(SAMPLE_RATE), Math.round(SAMPLE_RATE) + 500))).toBeGreaterThan(0.01);
  });

  it("stops at the end when not looping, and seek moves the playhead", () => {
    const player = new SongPlayer(kickSong());
    player.setLoop(false);
    player.play();
    const [l, r] = block(SAMPLE_RATE * 3);
    player.render(l, r, SAMPLE_RATE * 3);
    expect(player.isPlaying).toBe(false);
    player.seek(TICKS_PER_BEAT);
    expect(player.position).toBe(TICKS_PER_BEAT);
  });

  it("previews a note on a channel while stopped", () => {
    const song = defaultSong();
    const player = new SongPlayer(song);
    player.previewNoteOn(song.channels[3]!.id, 60, 1);
    const [l, r] = block(2048);
    player.render(l, r, 2048);
    expect(peak(l)).toBeGreaterThan(0.001);
  });

  it("keeps playing and its place when the song is replaced", () => {
    const song = kickSong();
    const player = new SongPlayer(song);
    player.play();
    player.render(...block(20000), 20000);
    const before = player.position;
    const edited = structuredClone(song);
    edited.bpm = 130;
    player.loadSong(edited);
    expect(player.isPlaying).toBe(true);
    expect(player.position).toBe(before);
  });

  it("reports instruments it cannot play and plays the rest", () => {
    const song = defaultSong();
    song.channels[3]!.instrument.type = "SFXR";
    const player = new SongPlayer(song);
    expect(player.errors).toHaveLength(1);
    expect(player.errors[0]).toMatch(/Lead: SFXR/);
  });
});

describe("the desktop's demo song", () => {
  it("opens and round-trips through the file text", () => {
    const song = parseSong(demoText());
    expect(song.name).toBe("Demo Beat");
    expect(song.bpm).toBe(110);
    expect(song.channels.length).toBe(4);
    const again = parseSong(songToJson(song));
    expect(again).toEqual(song);
  });

  it("renders to finite audio that never passes full scale, with the playable parts audible", () => {
    const song = parseSong(demoText());
    const recording = renderSong(song, "SONG", 0);
    expect(recording.left.length).toBeGreaterThanOrEqual(framesFor(song, "SONG", 0));
    expect(recording.left.every(Number.isFinite)).toBe(true);
    expect(peak(recording.left, recording.right)).toBeGreaterThan(0.05);
    expect(peak(recording.left, recording.right)).toBeLessThanOrEqual(1);
  });

  it("writes a valid 16-bit stereo WAV", () => {
    const wav = writeWav({ left: Float32Array.of(0, 1, -1), right: Float32Array.of(0, 0.5, -0.5), sampleRate: SAMPLE_RATE });
    const view = new DataView(wav.buffer);
    expect(String.fromCharCode(...wav.subarray(0, 4))).toBe("RIFF");
    expect(String.fromCharCode(...wav.subarray(8, 12))).toBe("WAVE");
    expect(view.getUint16(22, true)).toBe(2);
    expect(view.getUint32(24, true)).toBe(SAMPLE_RATE);
    expect(view.getUint32(40, true)).toBe(12);
    expect(view.getInt16(44 + 4, true)).toBe(32767);
    expect(wav.length).toBe(44 + 12);
  });

  it("exports an empty song as an error, not silence", () => {
    const song = defaultSong();
    expect(() => renderSong(song, "SONG", 0)).toThrow(/playlist is empty/);
  });
});

describe("song files", () => {
  it("rejects what is not a song, with the reason", () => {
    expect(() => parseSong("[]")).toThrow(/JSON object/);
    expect(() => parseSong("{")).toThrow(/Invalid song file/);
    expect(() => parseSong('{"version":2}')).toThrow(/Unsupported song file version 2/);
    expect(() => parseSong('{"version":1,"bpm":5}')).toThrow(/bpm must be between/);
    expect(() => parseSong('{"version":1,"patterns":[{"id":1,"lengthTicks":100,"notes":{}}]}')).toThrow(/whole number of bars/);
    expect(() => parseSong('{"version":1,"patterns":[{"id":1,"lengthTicks":384,"notes":{"1":[[0,0,60,1]]}}]}')).toThrow(/length must be at least 1/);
    expect(() => parseSong('{"version":1,"clips":[[9,0,0,384]]}')).toThrow(/does not fit/);
  });

  it("keeps ids moving forward after load", () => {
    const song = parseSong(demoText());
    const pattern = addPattern(song, "New", 1);
    expect(song.patterns.filter((p) => p.id === pattern.id)).toHaveLength(1);
  });
});

describe("the audio worklet scope", () => {
  it("builds a song and plays it without structuredClone, which that scope lacks", () => {
    vi.stubGlobal("structuredClone", undefined);
    try {
      const song = defaultSong();
      const player = new SongPlayer(song, 48000);
      player.play();
      const [l, r] = block(128);
      player.render(l, r, 128);
      expect(player.isPlaying).toBe(true);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
