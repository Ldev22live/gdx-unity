import { NullEngine, Scene, TransformNode } from "@babylonjs/core";
import { describe, expect, it } from "vitest";
import { type SfxOutput, SfxPlayer } from "../src/audio/SfxPlayer";
import { SFXR_SAMPLE_RATE } from "../src/audio/sfxr";
import { GameEntity, GameScene, type SoundSink } from "../src/runtime/GameEntity";
import type { ComponentData, EntityData } from "../src/scene/types";

interface Played {
  samples: Float32Array;
  sampleRate: number;
  volume: number;
  pitch: number;
}

class RecordingOutput implements SfxOutput {
  readonly played: Played[] = [];

  play(samples: Float32Array, sampleRate: number, volume: number, pitch: number): void {
    this.played.push({ samples, sampleRate, volume, pitch });
  }
}

describe("SfxPlayer", () => {
  it("shouldSendTheGeneratedSoundWithItsVolumeAndPitchToTheOutput", () => {
    const output = new RecordingOutput();
    const player = new SfxPlayer(output);

    player.play("Jump", 0.5, 2);

    expect(output.played).toHaveLength(1);
    expect(output.played[0]).toMatchObject({ sampleRate: SFXR_SAMPLE_RATE, volume: 0.5, pitch: 2 });
    expect(output.played[0]!.samples.length).toBeGreaterThan(1000);
  });

  it("shouldMakeEachSoundOnceAndReplayItFromMemory", () => {
    const output = new RecordingOutput();
    const player = new SfxPlayer(output);

    player.play("Coin", 1, 1);
    player.play("Coin", 0.5, 1.5);

    expect(output.played[1]!.samples).toBe(output.played[0]!.samples);
    expect(player.prepare("Coin")).toBe(output.played[0]!.samples);
  });

  it("shouldRejectAnUnknownSoundAndBadVolumeOrPitch", () => {
    const player = new SfxPlayer(new RecordingOutput());

    expect(SfxPlayer.isKnown("Kazoo")).toBe(false);
    expect(() => player.play("Kazoo", 1, 1)).toThrow(/Unknown sound/);
    expect(() => player.play("Coin", 1.5, 1)).toThrow(/volume/);
    expect(() => player.play("Coin", Number.NaN, 1)).toThrow(/volume/);
    expect(() => player.play("Coin", 1, 0)).toThrow(/pitch/);
  });
});

/** A scene of bare entities; only the components and the node's enabled state matter to cues. */
function sceneOf(engine: NullEngine, entities: { name: string; active?: boolean; components: ComponentData[] }[]): GameScene {
  const babylon = new Scene(engine);
  return new GameScene(
    entities.map((entity, index) => {
      const data: EntityData = {
        id: `entity-${index}`,
        name: entity.name,
        active: entity.active ?? true,
        parentId: null,
        transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
        components: entity.components,
      };
      const gameEntity = new GameEntity(data, new TransformNode(entity.name, babylon));
      gameEntity.setActive(data.active);
      return gameEntity;
    }),
  );
}

function source(cue: string, sound: string, volume = 1, pitch = 1, enabled = true): ComponentData {
  return { type: "AudioSource", enabled, data: { cue, sound, volume, pitch, playOnStart: false } };
}

describe("GameScene.playCue", () => {
  const heard: string[] = [];
  const sink: SoundSink = { play: (sound, volume, pitch) => heard.push(`${sound}@${volume}@${pitch}`) };

  it("shouldPlayEveryEnabledSourceWithTheCueOnActiveEntities", () => {
    heard.length = 0;
    const engine = new NullEngine();
    const scene = sceneOf(engine, [
      { name: "First", components: [source("coin", "Coin", 0.5)] },
      { name: "Second", components: [source("coin", "Blip", 1, 2), source("jump", "Jump")] },
      { name: "Hidden", active: false, components: [source("coin", "Laser")] },
      { name: "Disabled", components: [source("coin", "Hurt", 1, 1, false)] },
    ]);
    scene.bindSound(sink);

    expect(scene.playCue("coin")).toBe(2);
    expect(heard).toEqual(["Coin@0.5@1", "Blip@1@2"]);
    expect(scene.playCue("missing")).toBe(0);
    engine.dispose();
  });

  it("shouldMakeNoSoundWhileNoSinkIsBound", () => {
    heard.length = 0;
    const engine = new NullEngine();
    const scene = sceneOf(engine, [{ name: "First", components: [source("coin", "Coin")] }]);

    expect(scene.playCue("coin")).toBe(0);
    scene.bindSound(sink);
    expect(scene.playCue("coin")).toBe(1);
    scene.bindSound(null);
    expect(scene.playCue("coin")).toBe(0);
    expect(heard).toEqual(["Coin@1@1"]);
    engine.dispose();
  });
});
