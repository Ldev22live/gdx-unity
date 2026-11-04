/**
 * The audio thread: runs a SongPlayer and renders it into the browser's output, 128 frames at a time. The page owns the
 * song and sends it here (see AudioEngine); nothing here touches the DOM.
 */
import { SongPlayer, type PlayMode } from "./player";
import { defaultSong, type SongData } from "./song";

// The audio worklet scope has these globals; the DOM typings do not describe them.
declare const sampleRate: number;
declare class AudioWorkletProcessor {
  readonly port: MessagePort;
  constructor();
}
declare function registerProcessor(name: string, processor: new () => AudioWorkletProcessor): void;

/** Messages from the page. */
export type WorkletCommand =
  | { type: "song"; song: SongData }
  | { type: "play" }
  | { type: "pause" }
  | { type: "stop" }
  | { type: "seek"; ticks: number }
  | { type: "loop"; loop: boolean }
  | { type: "mode"; mode: PlayMode; patternId: number }
  | { type: "strip"; index: number | "master"; values: { volume?: number; pan?: number; muted?: boolean; solo?: boolean } }
  | { type: "effect"; strip: number | "master"; effectIndex: number; name: string; value: number }
  | { type: "noteOn"; channelId: number; pitch: number; velocity: number }
  | { type: "noteOff"; channelId: number; pitch: number };

/** What the worklet reports back about every `REPORT_FRAMES` frames. */
export interface WorkletReport {
  type: "report";
  position: number;
  playing: boolean;
  lengthTicks: number;
  peakLeft: number;
  peakRight: number;
  /** Peak of each insert strip, left and right. */
  strips: [number, number][];
  errors: string[];
}

/** About 23 ms at 44.1 kHz: often enough for a smooth playhead and meters. */
const REPORT_FRAMES = 1024;

class SongProcessor extends AudioWorkletProcessor {
  private player = new SongPlayer(defaultSong(), sampleRate);
  private framesSinceReport = 0;

  constructor() {
    super();
    this.port.onmessage = (event: MessageEvent<WorkletCommand>) => {
      try {
        this.handle(event.data);
      } catch (error) {
        this.port.postMessage({ type: "error", message: error instanceof Error ? error.message : String(error) });
      }
    };
  }

  private handle(command: WorkletCommand): void {
    const player = this.player;
    switch (command.type) {
      case "song":
        player.loadSong(command.song);
        break;
      case "play":
        player.play();
        break;
      case "pause":
        player.pause();
        break;
      case "stop":
        player.stop();
        break;
      case "seek":
        player.seek(command.ticks);
        break;
      case "loop":
        player.setLoop(command.loop);
        break;
      case "mode":
        player.setPlayback(command.mode, command.patternId);
        break;
      case "strip":
        player.setStrip(command.index, command.values);
        break;
      case "effect":
        player.setEffectParameter(command.strip, command.effectIndex, command.name, command.value);
        break;
      case "noteOn":
        player.previewNoteOn(command.channelId, command.pitch, command.velocity);
        break;
      case "noteOff":
        player.previewNoteOff(command.channelId, command.pitch);
        break;
    }
  }

  process(_inputs: Float32Array[][], outputs: Float32Array[][]): boolean {
    const output = outputs[0];
    const left = output?.[0];
    if (!output || !left) return true;
    const right = output[1] ?? left;
    this.player.render(left, right, left.length);
    this.framesSinceReport += left.length;
    if (this.framesSinceReport >= REPORT_FRAMES) {
      this.framesSinceReport = 0;
      const mixer = this.player.currentMixer;
      const report: WorkletReport = {
        type: "report",
        position: this.player.position,
        playing: this.player.isPlaying,
        lengthTicks: this.player.lengthTicks,
        peakLeft: mixer.master.peakLeft,
        peakRight: mixer.master.peakRight,
        strips: mixer.strips.map((strip) => [strip.peakLeft, strip.peakRight]),
        errors: this.player.errors,
      };
      this.port.postMessage(report);
    }
    return true;
  }
}

(registerProcessor as (name: string, processor: unknown) => void)("song-player", SongProcessor);
