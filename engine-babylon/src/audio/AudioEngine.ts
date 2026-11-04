import workletUrl from "./worklet.ts?worker&url";
import type { PlayMode } from "./player";
import type { SongData } from "./song";
import type { WorkletCommand, WorkletReport } from "./worklet";

export type { WorkletReport };

/**
 * The page side of music playback: owns the AudioContext and the worklet that renders the song, and turns calls into
 * messages. The browser only lets sound start after a click, so the context is created on the first call that needs it
 * ({@link ensureStarted}), which a Play or preview click makes.
 *
 * The song itself stays with the editor; every structural change is sent here whole ({@link sendSong}), and knob moves
 * that must not restart reverb tails go as small messages ({@link setStrip}, {@link setEffect}).
 */
export class AudioEngine {
  /** The latest report from the audio thread (position, meters). */
  report: WorkletReport | null = null;
  /** Called on every report and on a worklet error. */
  onReport: (report: WorkletReport) => void = () => undefined;
  onError: (message: string) => void = () => undefined;
  private context: AudioContext | null = null;
  private node: AudioWorkletNode | null = null;
  private starting: Promise<void> | null = null;
  private song: SongData | null = null;
  private mode: PlayMode = "PATTERN";
  private patternId = 0;
  private loop = true;

  get isStarted(): boolean {
    return this.node !== null;
  }

  /** The browser's AudioContext, once started; tests read the output level through it. */
  get audioContext(): AudioContext | null {
    return this.context;
  }

  /** The node the music comes out of, once started, so a test can tap it with an analyser. */
  get outputNode(): AudioNode | null {
    return this.node;
  }

  /** Starts the audio thread if it is not running, and brings it up to date with the song and transport settings. */
  ensureStarted(): Promise<void> {
    if (this.node) return this.resume();
    this.starting ??= this.start().catch((error) => {
      this.starting = null;
      throw error;
    });
    return this.starting;
  }

  private async start(): Promise<void> {
    const context = new AudioContext();
    await context.audioWorklet.addModule(workletUrl);
    const node = new AudioWorkletNode(context, "song-player", { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [2] });
    node.port.onmessage = (event: MessageEvent) => {
      const data = event.data as WorkletReport | { type: "error"; message: string };
      if (data.type === "report") {
        this.report = data;
        this.onReport(data);
      } else {
        this.onError(data.message);
      }
    };
    node.connect(context.destination);
    this.context = context;
    this.node = node;
    // Bring the new audio thread up to the page's state
    if (this.song) this.post({ type: "song", song: this.song });
    this.post({ type: "mode", mode: this.mode, patternId: this.patternId });
    this.post({ type: "loop", loop: this.loop });
    await this.resume();
  }

  private async resume(): Promise<void> {
    if (this.context && this.context.state !== "running") await this.context.resume();
  }

  private post(command: WorkletCommand): void {
    this.node?.port.postMessage(command);
  }

  // ---- Song and transport -------------------------------------------------------------------------------------

  sendSong(song: SongData): void {
    this.song = structuredClone(song);
    this.post({ type: "song", song: this.song });
  }

  setPlayback(mode: PlayMode, patternId: number): void {
    this.mode = mode;
    this.patternId = patternId;
    this.post({ type: "mode", mode, patternId });
  }

  setLoop(loop: boolean): void {
    this.loop = loop;
    this.post({ type: "loop", loop });
  }

  async play(): Promise<void> {
    await this.ensureStarted();
    this.post({ type: "play" });
  }

  pause(): void {
    this.post({ type: "pause" });
  }

  stop(): void {
    this.post({ type: "stop" });
  }

  seek(ticks: number): void {
    this.post({ type: "seek", ticks });
  }

  // ---- Live changes -------------------------------------------------------------------------------------------

  setStrip(index: number | "master", values: { volume?: number; pan?: number; muted?: boolean; solo?: boolean }): void {
    this.post({ type: "strip", index, values });
  }

  setEffect(strip: number | "master", effectIndex: number, name: string, value: number): void {
    this.post({ type: "effect", strip, effectIndex, name, value });
  }

  async noteOn(channelId: number, pitch: number, velocity: number): Promise<void> {
    await this.ensureStarted();
    this.post({ type: "noteOn", channelId, pitch, velocity });
  }

  noteOff(channelId: number, pitch: number): void {
    this.post({ type: "noteOff", channelId, pitch });
  }

  dispose(): void {
    this.node?.disconnect();
    void this.context?.close();
    this.node = null;
    this.context = null;
    this.starting = null;
  }
}
