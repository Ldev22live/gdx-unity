import { describe, expect, it } from "vitest";
import {
  generateSfxr,
  isKnownSound,
  NoiseSource,
  sfxrEnvelopeFrames,
  sfxrPreset,
  SFXR_PRESET_NAMES,
  SFXR_SAMPLE_RATE,
} from "../src/audio/sfxr";

/**
 * What the desktop engine's `SfxrGenerator` makes for each factory sound: frames, the sum of absolute sample values and
 * the sample in the middle. Printed by running the Java generator on the same presets, so these tests fail if the port
 * drifts from the desktop sounds.
 */
const DESKTOP_REFERENCE: Record<string, { frames: number; absoluteSum: number; middle: number }> = {
  Coin: { frames: 9041, absoluteSum: 4479.452018553432, middle: 0.5009966 },
  Laser: { frames: 7091, absoluteSum: 4667.953826882993, middle: -1.0 },
  Explosion: { frames: 48250, absoluteSum: 26560.31220062417, middle: -0.447177 },
  Powerup: { frames: 22251, absoluteSum: 12520.347337199133, middle: -0.1774859 },
  Hurt: { frames: 10001, absoluteSum: 4869.831275094963, middle: -0.89481807 },
  Jump: { frames: 7691, absoluteSum: 4379.508269057726, middle: -0.55182576 },
  Blip: { frames: 2501, absoluteSum: 1335.9459051373706, middle: 0.51732045 },
};

describe("generateSfxr", () => {
  it("shouldMakeTheSameSoundsAsTheDesktopGenerator", () => {
    for (const name of SFXR_PRESET_NAMES) {
      const reference = DESKTOP_REFERENCE[name]!;
      const samples = generateSfxr(sfxrPreset(name));
      const absoluteSum = samples.reduce((sum, value) => sum + Math.abs(value), 0);
      expect(samples.length, `${name} frames`).toBe(reference.frames);
      expect(absoluteSum / reference.absoluteSum, `${name} loudness`).toBeCloseTo(1, 3);
      expect(samples[Math.trunc(samples.length / 2)]!, `${name} middle sample`).toBeCloseTo(reference.middle, 3);
    }
  });

  it("shouldKnowEveryDesktopPresetAndNothingElse", () => {
    expect([...SFXR_PRESET_NAMES].sort()).toEqual(Object.keys(DESKTOP_REFERENCE).sort());
    expect(isKnownSound("Coin")).toBe(true);
    expect(isKnownSound("Kazoo")).toBe(false);
    expect(isKnownSound("toString"), "inherited object keys are not sounds").toBe(false);
  });

  it("shouldRenderTheSameSoundEveryTime", () => {
    const first = generateSfxr(sfxrPreset("Explosion"));
    const second = generateSfxr(sfxrPreset("Explosion"));
    expect(second).toEqual(first);
  });

  it("shouldStayWithinFullScaleAndNeverExceedTheEnvelope", () => {
    for (const name of SFXR_PRESET_NAMES) {
      const params = sfxrPreset(name);
      const samples = generateSfxr(params);
      expect(samples.length, name).toBeLessThanOrEqual(sfxrEnvelopeFrames(params) + 1);
      expect(Math.max(...samples.map(Math.abs)), name).toBeLessThanOrEqual(1);
      expect(samples.length / SFXR_SAMPLE_RATE, name).toBeGreaterThan(0.01);
    }
  });

  it("shouldRejectAnUnknownSound", () => {
    expect(() => sfxrPreset("Kazoo")).toThrow(/Unknown sound 'Kazoo'/);
  });
});

describe("NoiseSource", () => {
  it("shouldMatchJavaUtilRandomNextFloat", () => {
    // Printed by `new java.util.Random(42).nextFloat()`
    expect(new NoiseSource(42).nextFloat()).toBeCloseTo(0.7275637, 7);
  });

  it("shouldStayWithinZeroToOne", () => {
    const noise = new NoiseSource(0x5eed);
    for (let i = 0; i < 1000; i++) {
      const value = noise.nextFloat();
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    }
  });
});
