import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { DRUMS, DrumKit } from "../src/audio/drums";
import { FM_PRESET_NAMES, FmSynthesizer, fmPreset } from "../src/audio/fm";
import { PLUCK_PRESET_NAMES, PluckedString, pluckPreset } from "../src/audio/pluck";
import { SAMPLE_RATE } from "../src/audio/song";
import { PRESET_NAMES, Synthesizer, presetPatch } from "../src/audio/synth";

/** What the desktop's `InstrumentFingerprintTest` recorded: the peak and loudness of half a second of every preset and drum (TD-074). */
interface Fingerprints {
  frames: number;
  note: number;
  presets: Record<string, { peak: number; rms: number }>;
  fm: Record<string, { peak: number; rms: number }>;
  pluck: Record<string, { peak: number; rms: number }>;
  drums: Record<string, { peak: number; rms: number }>;
}

const FILE = join(import.meta.dirname, "..", "..", "engine-audio", "src", "test", "resources", "instrument-fingerprints.json");
const recorded: Fingerprints = JSON.parse(readFileSync(FILE, "utf8"));
/** The desktop renders in 32-bit floats and this in 64-bit, so the values agree to a few parts in a thousand, not exactly. */
const RELATIVE = 0.005;
const ABSOLUTE = 2e-4;

function measure(render: (left: Float32Array, right: Float32Array) => void): { peak: number; rms: number } {
  const left = new Float32Array(recorded.frames);
  const right = new Float32Array(recorded.frames);
  render(left, right);
  let peak = 0;
  let sum = 0;
  for (const v of left) {
    peak = Math.max(peak, Math.abs(v));
    sum += v * v;
  }
  return { peak, rms: Math.sqrt(sum / left.length) };
}

function expectClose(what: string, actual: { peak: number; rms: number }, expected: { peak: number; rms: number }): void {
  for (const key of ["peak", "rms"] as const) {
    const allowed = Math.max(ABSOLUTE, Math.abs(expected[key]) * RELATIVE);
    expect(Math.abs(actual[key] - expected[key]), `${what} ${key}: web ${actual[key]}, desktop ${expected[key]}`).toBeLessThanOrEqual(allowed);
  }
}

describe("instruments sound the same on the web as on the desktop (TD-074)", () => {
  it("offers the same preset names and the same drums", () => {
    expect([...PRESET_NAMES]).toEqual(Object.keys(recorded.presets));
    expect([...FM_PRESET_NAMES]).toEqual(Object.keys(recorded.fm));
    expect([...PLUCK_PRESET_NAMES]).toEqual(Object.keys(recorded.pluck));
    expect(DRUMS.map((entry) => entry.drum)).toEqual(Object.keys(recorded.drums));
    expect(new Set(DRUMS.map((entry) => entry.note)).size).toBe(DRUMS.length);
  });

  for (const name of PRESET_NAMES) {
    it(`renders the ${name} preset like the desktop`, () => {
      const synth = new Synthesizer(SAMPLE_RATE, { ...presetPatch(name) });
      synth.noteOn(recorded.note, 1);
      const actual = measure((l, r) => synth.render(l, r, 0, recorded.frames));
      expectClose(`preset ${name}`, actual, recorded.presets[name]!);
    });
  }

  for (const name of FM_PRESET_NAMES) {
    it(`renders the FM ${name} preset like the desktop`, () => {
      const synth = new FmSynthesizer(SAMPLE_RATE, fmPreset(name));
      synth.noteOn(recorded.note, 1);
      const actual = measure((l, r) => synth.render(l, r, 0, recorded.frames));
      expectClose(`FM preset ${name}`, actual, recorded.fm[name]!);
    });
  }

  for (const name of PLUCK_PRESET_NAMES) {
    it(`renders the plucked-string ${name} preset like the desktop`, () => {
      const string = new PluckedString(SAMPLE_RATE, pluckPreset(name));
      string.noteOn(recorded.note, 1);
      const actual = measure((l, r) => string.render(l, r, 0, recorded.frames));
      expectClose(`plucked-string preset ${name}`, actual, recorded.pluck[name]!);
    });
  }

  for (const entry of DRUMS) {
    it(`renders the ${entry.name} like the desktop`, () => {
      const kit = new DrumKit(SAMPLE_RATE);
      kit.noteOn(entry.note, 1);
      const actual = measure((l, r) => kit.render(l, r, 0, recorded.frames));
      expectClose(`drum ${entry.drum}`, actual, recorded.drums[entry.drum]!);
    });
  }
});
