import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { SceneMusic, type MusicEngine } from "../src/audio/SceneMusic";
import type { SongData } from "../src/audio/song";
import { SAMPLES_DIR } from "./support";

const MUSIC_DIR = join(SAMPLES_DIR, "..", "music");
const read = async (path: string): Promise<string> => readFileSync(join(MUSIC_DIR, path.replace(/^music\//, "")), "utf8");

class RecordingEngine implements MusicEngine {
  readonly calls: string[] = [];
  song: SongData | null = null;

  sendSong(song: SongData): void {
    this.song = song;
    this.calls.push("song");
  }

  setPlayback(mode: string, patternId: number): void {
    this.calls.push(`mode:${mode}:${patternId}`);
  }

  setLoop(loop: boolean): void {
    this.calls.push(`loop:${loop}`);
  }

  setStrip(index: number | "master", values: { volume?: number }): void {
    this.calls.push(`strip:${index}:${values.volume!.toFixed(3)}`);
  }

  async play(): Promise<void> {
    this.calls.push("play");
  }

  stop(): void {
    this.calls.push("stop");
  }
}

describe("SceneMusic", () => {
  it("shouldSendTheSongItsSettingsAndPlayIt", async () => {
    const engine = new RecordingEngine();
    const music = new SceneMusic(() => engine, read);

    await music.start({ song: "music/neon-drive.song", volume: 0.5, loop: false });

    expect(engine.song?.name).toBeTruthy();
    expect(engine.calls[0]).toBe("song");
    expect(engine.calls.at(-1)).toBe("play");
    expect(engine.calls).toContain("loop:false");
    expect(engine.calls.find((call) => call.startsWith("mode:"))).toMatch(/^mode:(PATTERN|SONG):\d+$/);
    const master = engine.song!.mixer.master.volume * 0.5;
    expect(engine.calls).toContain(`strip:master:${master.toFixed(3)}`);
  });

  it("shouldNameTheSongThatCannotBeLoaded", async () => {
    const music = new SceneMusic(() => new RecordingEngine(), read);

    await expect(music.start({ song: "music/nope.song", volume: 1, loop: true })).rejects.toThrow(/song 'music\/nope.song' cannot be loaded/);
    await expect(music.start({ song: " ", volume: 1, loop: true })).rejects.toThrow(/no song chosen/);
  });

  it("shouldNotStartASongThatWasStillLoadingWhenTheSceneStopped", async () => {
    const engine = new RecordingEngine();
    let release: (text: string) => void = () => undefined;
    const slow = new SceneMusic(() => engine, () => new Promise<string>((resolve) => (release = resolve)));

    const starting = slow.start({ song: "music/neon-drive.song", volume: 1, loop: true });
    slow.stop();
    release(await read("music/neon-drive.song"));
    await starting;

    expect(engine.calls).not.toContain("play");
  });

  it("shouldReadEveryBundledSongThePlayerCanPlay", async () => {
    for (const name of ["ambient-drift", "funk-machine", "lofi-evening", "neon-drive", "pixel-quest"]) {
      const engine = new RecordingEngine();
      await new SceneMusic(() => engine, read).start({ song: `music/${name}.song`, volume: 1, loop: true });
      expect(engine.calls.at(-1), name).toBe("play");
    }
  });
});
