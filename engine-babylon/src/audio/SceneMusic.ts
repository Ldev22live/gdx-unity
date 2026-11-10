import type { PlayMode } from "./player";
import { parseSong, type SongData } from "./song";

/** What {@link SceneMusic} needs from the audio engine; `AudioEngine` implements it, tests use a recording fake. */
export interface MusicEngine {
  sendSong(song: SongData): void;
  setPlayback(mode: PlayMode, patternId: number): void;
  setLoop(loop: boolean): void;
  setStrip(index: number | "master", values: { volume?: number }): void;
  play(): Promise<void>;
  stop(): void;
}

/** A Music Source component's settings. */
export interface MusicSourceSettings {
  song: string;
  volume: number;
  loop: boolean;
}

/** Reads a song file; replaced in tests. */
export type SongFetcher = (path: string) => Promise<string>;

/**
 * Songs in scenes on the web (TD-061): plays the `.song` of a Music Source through the music engine. The browser has one
 * song engine, so one song sounds at a time; starting another replaces it. The desktop plays several at once.
 */
export class SceneMusic {
  private engine: MusicEngine | null = null;
  /** Counts starts, so a song that was still loading when the scene stopped is not started late. */
  private generation = 0;

  constructor(
    private readonly createEngine: () => MusicEngine,
    private readonly fetcher: SongFetcher,
  ) {}

  /** The song at {@code source.song}, read and ready, or an Error naming the path. */
  async load(path: string): Promise<SongData> {
    if (path.trim() === "") throw new Error("no song chosen");
    try {
      return parseSong(await this.fetcher(path));
    } catch (error) {
      throw new Error(`song '${path}' cannot be loaded: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /** Starts the song from the beginning at the source's volume; resolves when the engine was told to play. */
  async start(source: MusicSourceSettings): Promise<void> {
    const generation = ++this.generation;
    const song = await this.load(source.song);
    if (generation !== this.generation) return;
    const engine = (this.engine ??= this.createEngine());
    engine.sendSong(song);
    engine.setPlayback(song.clips.length === 0 ? "PATTERN" : "SONG", song.patterns[0]?.id ?? 0);
    engine.setLoop(source.loop);
    engine.setStrip("master", { volume: source.volume * song.mixer.master.volume });
    await engine.play();
  }

  /** Stops the song, and cancels one that is still loading. */
  stop(): void {
    this.generation++;
    this.engine?.stop();
  }
}
