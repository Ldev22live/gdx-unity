import { NullEngine, Scene, TransformNode, Vector3 } from "@babylonjs/core";
import { describe, expect, it } from "vitest";
import { decodeWav, isClipName } from "../src/audio/clips";
import { SfxPlayer, type SfxOutput } from "../src/audio/SfxPlayer";
import { hear } from "../src/audio/spatial";
import { GameEntity, GameScene, type SoundSink } from "../src/runtime/GameEntity";
import type { ComponentData, EntityData } from "../src/scene/types";

const EARS = { x: 0, y: 0, z: 0 };
const RIGHT = { x: 1, y: 0, z: 0 };

describe("hear", () => {
  it("shouldHearFullVolumeUpToTheNearDistanceAndNothingBeyondTheFarOne", () => {
    expect(hear(EARS, RIGHT, { x: 0, y: 0, z: -100 }, 100, 500).gain).toBeCloseTo(1, 5);
    expect(hear(EARS, RIGHT, { x: 0, y: 0, z: -200 }, 100, 500).gain).toBeCloseTo(0.75, 5);
    expect(hear(EARS, RIGHT, { x: 0, y: 0, z: -500 }, 100, 500).gain).toBe(0);
    expect(hear(EARS, RIGHT, { x: 0, y: 900, z: 0 }, 100, 500).gain).toBe(0);
  });

  it("shouldPanTowardsTheSideTheSoundIsOn", () => {
    expect(hear(EARS, RIGHT, { x: 300, y: 0, z: 0 }, 100, 1000).pan).toBeCloseTo(1, 5);
    expect(hear(EARS, RIGHT, { x: -300, y: 0, z: 0 }, 100, 1000).pan).toBeCloseTo(-1, 5);
    expect(hear(EARS, RIGHT, { x: 0, y: 0, z: -300 }, 100, 1000).pan).toBeCloseTo(0, 5);
    expect(hear(EARS, RIGHT, { x: 300, y: 0, z: -300 }, 100, 1000).pan).toBeCloseTo(Math.sqrt(0.5), 4);
  });

  it("shouldNotPanASoundAtTheListenersPlaceOrNeedAUnitRightVector", () => {
    expect(hear(EARS, { x: 5, y: 0, z: 0 }, EARS, 100, 500).pan).toBe(0);
    expect(hear(EARS, { x: 5, y: 0, z: 0 }, { x: 50, y: 0, z: 0 }, 100, 500).pan).toBeCloseTo(1, 5);
  });

  it("shouldRefuseDistancesThatDoNotGrowAndAZeroRightVector", () => {
    expect(() => hear(EARS, RIGHT, EARS, 500, 500)).toThrow(/min < max/);
    expect(() => hear(EARS, RIGHT, EARS, -1, 500)).toThrow(/min < max/);
    expect(() => hear(EARS, { x: 0, y: 0, z: 0 }, EARS, 100, 500)).toThrow(/zero vector/);
  });
});

/** Bytes of a WAV file: {@code channels} interleaved channels of {@code frames}, written by {@code write(view, at, value)}. */
function wav(options: { channels: number; bits: number; format?: number; sampleRate?: number; frames: number[][] }): Uint8Array {
  const { channels, bits, frames } = options;
  const format = options.format ?? 1;
  const bytesPerSample = bits / 8;
  const dataSize = frames.length * channels * bytesPerSample;
  const bytes = new Uint8Array(44 + dataSize);
  const view = new DataView(bytes.buffer);
  const text = (at: number, value: string): void => [...value].forEach((c, i) => view.setUint8(at + i, c.charCodeAt(0)));
  text(0, "RIFF");
  view.setUint32(4, 36 + dataSize, true);
  text(8, "WAVE");
  text(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, format, true);
  view.setUint16(22, channels, true);
  view.setUint32(24, options.sampleRate ?? 22050, true);
  view.setUint32(28, (options.sampleRate ?? 22050) * channels * bytesPerSample, true);
  view.setUint16(32, channels * bytesPerSample, true);
  view.setUint16(34, bits, true);
  text(36, "data");
  view.setUint32(40, dataSize, true);
  frames.forEach((frame, i) =>
    frame.forEach((value, channel) => {
      const at = 44 + (i * channels + channel) * bytesPerSample;
      if (format === 3) view.setFloat32(at, value, true);
      else if (bits === 8) view.setUint8(at, Math.round(value * 127) + 128);
      else if (bits === 16) view.setInt16(at, Math.round(value * 32767), true);
      else if (bits === 24) {
        const whole = Math.round(value * 8388607);
        view.setUint8(at, whole & 0xff);
        view.setUint8(at + 1, (whole >> 8) & 0xff);
        view.setUint8(at + 2, (whole >> 16) & 0xff);
      } else view.setInt32(at, Math.round(value * 2147483647), true);
    }),
  );
  return bytes;
}

describe("decodeWav", () => {
  it("shouldReadMono16BitWithItsRateAndLevel", () => {
    const clip = decodeWav(wav({ channels: 1, bits: 16, sampleRate: 44100, frames: [[0.5], [-0.25], [1]] }));

    expect(clip.sampleRate).toBe(44100);
    expect([...clip.samples].map((v) => Math.round(v * 100) / 100)).toEqual([0.5, -0.25, 1]);
  });

  it("shouldMixStereoDownToOneChannelByAveraging", () => {
    const clip = decodeWav(wav({ channels: 2, bits: 16, frames: [[1, 0], [0.5, -0.5]] }));

    expect(clip.samples[0]).toBeCloseTo(0.5, 3);
    expect(clip.samples[1]).toBeCloseTo(0, 3);
  });

  it("shouldReadEightTwentyFourAndThirtyTwoBitAndFloat", () => {
    const level = 0.5;
    for (const options of [
      { bits: 8, format: 1 },
      { bits: 24, format: 1 },
      { bits: 32, format: 1 },
      { bits: 32, format: 3 },
    ]) {
      const clip = decodeWav(wav({ channels: 1, frames: [[level], [-level]], ...options }));
      expect(clip.samples[0], `${options.bits} bit, format ${options.format}`).toBeCloseTo(level, 1);
      expect(clip.samples[1]).toBeCloseTo(-level, 1);
    }
  });

  it("shouldRefuseWhatIsNotAWavAndFilesWithoutSound", () => {
    expect(() => decodeWav(new TextEncoder().encode("this is not audio"))).toThrow(/Not a WAV/);
    expect(() => decodeWav(wav({ channels: 1, bits: 16, frames: [] }))).toThrow(/no sound/);
    expect(() => decodeWav(wav({ channels: 1, bits: 16, format: 2, frames: [[0]] }))).toThrow(/Unsupported WAV format/);
  });

  it("shouldTellClipNamesFromFactoryEffectsByExtension", () => {
    expect(isClipName("sounds/Hit.WAV")).toBe(true);
    expect(isClipName("sounds/hit.ogg")).toBe(true);
    expect(isClipName("Coin")).toBe(false);
  });
});

interface Played {
  samples: Float32Array;
  sampleRate: number;
  volume: number;
  pan: number;
}

class RecordingOutput implements SfxOutput {
  readonly played: Played[] = [];

  play(samples: Float32Array, sampleRate: number, volume: number, _pitch: number, pan: number): void {
    this.played.push({ samples, sampleRate, volume, pan });
  }
}

describe("SfxPlayer clips and pan", () => {
  it("shouldPlayAClipByItsNameAtItsOwnSampleRateAndPan", () => {
    const output = new RecordingOutput();
    const player = new SfxPlayer(output);
    const samples = new Float32Array([0.5, 0.5, 0.5]);
    player.addClip("sounds/hit.wav", { samples, sampleRate: 44100 });

    expect(player.isAvailable("sounds/hit.wav")).toBe(true);
    expect(player.isAvailable("sounds/other.wav")).toBe(false);
    player.play("sounds/hit.wav", 0.8, 1, -0.5);

    expect(output.played).toEqual([{ samples, sampleRate: 44100, volume: 0.8, pan: -0.5 }]);
  });

  it("shouldNotLetAClipTakeAFactoryNameAndShouldRefuseAPanOutsideMinusOneToOne", () => {
    const player = new SfxPlayer(new RecordingOutput());

    expect(() => player.addClip("Coin", { samples: new Float32Array(1), sampleRate: 1 })).toThrow(/factory/);
    expect(() => player.addClip(" ", { samples: new Float32Array(1), sampleRate: 1 })).toThrow(/needs a name/);
    expect(() => player.play("Coin", 1, 1, 1.5)).toThrow(/pan/);
    player.play("Coin", 1, 1);
  });
});

function sceneOf(engine: NullEngine, entities: { name: string; at?: [number, number, number]; components: ComponentData[] }[]): GameScene {
  const babylon = new Scene(engine);
  babylon.useRightHandedSystem = true;
  return new GameScene(
    entities.map((entity, index) => {
      const data: EntityData = {
        id: `entity-${index}`,
        name: entity.name,
        active: true,
        parentId: null,
        transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
        components: entity.components,
      };
      const node = new TransformNode(entity.name, babylon);
      if (entity.at) node.position.set(...entity.at);
      return new GameEntity(data, node);
    }),
  );
}

function spatialSource(cue: string, sound: string, spatial: boolean): ComponentData {
  return { type: "AudioSource", enabled: true, data: { cue, sound, volume: 1, pitch: 1, playOnStart: false, spatial, minDistance: 100, maxDistance: 500 } };
}

describe("GameScene spatial Audio Sources", () => {
  const listener: ComponentData = { type: "AudioListener", enabled: true, data: {} };

  function hearCue(entities: Parameters<typeof sceneOf>[1]): string[] {
    const engine = new NullEngine();
    const scene = sceneOf(engine, entities);
    const heard: string[] = [];
    const sink: SoundSink = { play: (sound, volume, pitch, pan) => heard.push(`${sound}@${volume.toFixed(2)}@${pitch}@${(pan ?? 0).toFixed(2)}`) };
    scene.bindSound(sink);
    scene.playCue("ping");
    engine.dispose();
    return heard;
  }

  it("shouldPlayAFarSourceOnTheRightQuieterAndInTheRightSpeaker", () => {
    expect(hearCue([
      { name: "Ears", components: [listener] },
      { name: "Radio", at: [300, 0, 0], components: [spatialSource("ping", "Coin", true)] },
    ])).toEqual(["Coin@0.50@1@1.00"]);
  });

  it("shouldTurnThePanWithTheListener", () => {
    const engine = new NullEngine();
    const scene = sceneOf(engine, [
      { name: "Ears", components: [listener] },
      { name: "Radio", at: [200, 0, 0], components: [spatialSource("ping", "Coin", true)] },
    ]);
    scene.entities[0]!.node.rotation.y = Math.PI;
    const heard: number[] = [];
    scene.bindSound({ play: (_s, _v, _p, pan) => heard.push(pan ?? 0) });

    scene.playCue("ping");

    expect(heard[0]).toBeCloseTo(-1, 4);
    engine.dispose();
  });

  it("shouldPlayNothingBeyondTheFarDistance", () => {
    expect(hearCue([
      { name: "Ears", components: [listener] },
      { name: "Radio", at: [0, 0, -900], components: [spatialSource("ping", "Coin", true)] },
    ])).toEqual([]);
  });

  it("shouldPlayInTheMiddleWithoutAListenerOrForASourceThatIsNotSpatial", () => {
    expect(hearCue([{ name: "Radio", at: [300, 0, 0], components: [spatialSource("ping", "Coin", true)] }])).toEqual(["Coin@1.00@1@0.00"]);
    expect(hearCue([
      { name: "Ears", components: [listener] },
      { name: "Radio", at: [300, 0, 0], components: [spatialSource("ping", "Coin", false)] },
    ])).toEqual(["Coin@1.00@1@0.00"]);
  });

  it("shouldKeepVector3UsedForTheRightAxisHonest", () => {
    expect(Vector3.TransformNormal(Vector3.Right(), new TransformNode("n", new Scene(new NullEngine())).getWorldMatrix()).x).toBe(1);
  });
});
