import { SongPlayer, type PlayMode } from "./player";
import { SAMPLE_RATE, TICKS_PER_BEAT, findPattern, playlistLengthTicks, type SongData } from "./song";

/** A stereo recording. */
export interface Recording {
  left: Float32Array;
  right: Float32Array;
  sampleRate: number;
}

const BLOCK = 1024;
/** The render stops when this much silence follows the music, so reverb and release tails are kept but not padded. */
const SILENCE_LEVEL = 0.0005;
const SILENCE_SECONDS = 0.25;
/** Longest tail kept after the music ends, and longest export overall. */
export const MAX_TAIL_SECONDS = 8;
export const MAX_SECONDS = 20 * 60;

/** Frames of music in the song or pattern (without any tail). */
export function framesFor(song: SongData, mode: PlayMode, patternId: number): number {
  const ticks = mode === "SONG" ? playlistLengthTicks(song) : (findPattern(song, patternId)?.lengthTicks ?? 0);
  return Math.ceil((ticks * SAMPLE_RATE * 60) / (song.bpm * TICKS_PER_BEAT));
}

/**
 * Renders the song or one pattern to a recording, faster than real time: the music, then a tail until silence (at most
 * {@link MAX_TAIL_SECONDS}). Uses the same player as live playback, so what you hear is what you export.
 *
 * @throws Error when there is nothing to export
 */
export function renderSong(song: SongData, mode: PlayMode, patternId: number): Recording {
  const player = new SongPlayer(song);
  player.setPlayback(mode, patternId);
  player.setLoop(false);
  player.seek(0);
  player.play();
  const musicFrames = framesFor(song, mode, patternId);
  if (musicFrames <= 0) throw new Error(`Nothing to export: the ${mode === "SONG" ? "playlist is empty" : "pattern has no length"}`);
  const total = Math.min(Math.floor(MAX_SECONDS * SAMPLE_RATE), musicFrames + Math.floor(MAX_TAIL_SECONDS * SAMPLE_RATE) + BLOCK);
  const left = new Float32Array(total);
  const right = new Float32Array(total);
  const blockLeft = new Float32Array(BLOCK);
  const blockRight = new Float32Array(BLOCK);
  const silentNeeded = Math.floor(SILENCE_SECONDS * SAMPLE_RATE);
  let written = 0;
  let silent = 0;
  while (written < total) {
    const frames = Math.min(BLOCK, total - written);
    player.render(blockLeft, blockRight, frames);
    let peak = 0;
    for (let i = 0; i < frames; i++) {
      left[written + i] = blockLeft[i]!;
      right[written + i] = blockRight[i]!;
      peak = Math.max(peak, Math.abs(blockLeft[i]!), Math.abs(blockRight[i]!));
    }
    written += frames;
    silent = peak < SILENCE_LEVEL ? silent + frames : 0;
    if (!player.isPlaying && silent >= silentNeeded) break;
  }
  // Cut the trailing silence, but never the music itself
  const end = Math.max(written - Math.min(silent, written), Math.min(musicFrames, written));
  return { left: left.slice(0, end), right: right.slice(0, end), sampleRate: SAMPLE_RATE };
}

const WAV_HEADER_BYTES = 44;
const INT16_SCALE = 32768;

/** A 16-bit stereo PCM WAV file of the recording. */
export function writeWav(recording: Recording): Uint8Array {
  const frames = recording.left.length;
  const dataLength = frames * 2 * 2;
  const bytes = new Uint8Array(WAV_HEADER_BYTES + dataLength);
  const view = new DataView(bytes.buffer);
  const text = (at: number, value: string) => [...value].forEach((c, i) => view.setUint8(at + i, c.charCodeAt(0)));
  text(0, "RIFF");
  view.setUint32(4, 36 + dataLength, true);
  text(8, "WAVE");
  text(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, 2, true);
  view.setUint32(24, recording.sampleRate, true);
  view.setUint32(28, recording.sampleRate * 4, true);
  view.setUint16(32, 4, true);
  view.setUint16(34, 16, true);
  text(36, "data");
  view.setUint32(40, dataLength, true);
  const sample = (value: number) => Math.round(Math.min(Math.max(value, -1), 1) * (INT16_SCALE - 1));
  for (let i = 0; i < frames; i++) {
    view.setInt16(WAV_HEADER_BYTES + i * 4, sample(recording.left[i]!), true);
    view.setInt16(WAV_HEADER_BYTES + i * 4 + 2, sample(recording.right[i]!), true);
  }
  return bytes;
}
