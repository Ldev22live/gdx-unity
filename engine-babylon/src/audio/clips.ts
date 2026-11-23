/** A recorded sound clip, mixed down to one channel (the web player's effects are mono). */
export interface Clip {
  samples: Float32Array;
  sampleRate: number;
}

/** Sounds with one of these extensions are clips read from the project, not factory effects. */
export const CLIP_EXTENSIONS: readonly string[] = [".wav", ".ogg"];

/** Whether {@code sound} names a recorded clip, by its extension. */
export function isClipName(sound: string): boolean {
  const lower = sound.toLowerCase();
  return CLIP_EXTENSIONS.some((extension) => lower.endsWith(extension));
}

const FORMAT_PCM = 1;
const FORMAT_FLOAT = 3;
const FORMAT_EXTENSIBLE = 0xfffe;
const MIN_FMT_SIZE = 16;

/**
 * Reads a RIFF WAVE file: PCM at 8, 16, 24 or 32 bits or 32 bit float, any number of channels (mixed down to one by averaging),
 * any sample rate. The same files the desktop's `WavIO` reads.
 *
 * @throws Error when the bytes are not such a file, or hold no sound
 */
export function decodeWav(bytes: Uint8Array): Clip {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const tag = (at: number): string => String.fromCharCode(...bytes.subarray(at, at + 4));
  if (bytes.length < 12 || tag(0) !== "RIFF" || tag(8) !== "WAVE") throw new Error("Not a WAV file");
  let format = 0;
  let channels = 0;
  let sampleRate = 0;
  let bits = 0;
  let data: { at: number; size: number } | null = null;
  for (let at = 12; at + 8 <= bytes.length; ) {
    const size = view.getUint32(at + 4, true);
    const body = at + 8;
    if (tag(at) === "fmt ") {
      if (size < MIN_FMT_SIZE) throw new Error("The WAV format chunk is too short");
      format = view.getUint16(body, true);
      channels = view.getUint16(body + 2, true);
      sampleRate = view.getUint32(body + 4, true);
      bits = view.getUint16(body + 14, true);
      if (format === FORMAT_EXTENSIBLE && size >= 26) format = view.getUint16(body + 24, true);
    } else if (tag(at) === "data") {
      data = { at: body, size: Math.min(size, bytes.length - body) };
    }
    at = body + size + (size % 2);
  }
  if (!data) throw new Error("The WAV file has no sound data");
  if (channels < 1 || sampleRate < 1) throw new Error("The WAV file has no format chunk");
  const isFloat = format === FORMAT_FLOAT;
  if (format !== FORMAT_PCM && !isFloat) throw new Error(`Unsupported WAV format ${format}`);
  if (![8, 16, 24, 32].includes(bits) || (isFloat && bits !== 32)) throw new Error(`Unsupported WAV sample size: ${bits} bits`);
  const bytesPerSample = bits / 8;
  const frames = Math.floor(data.size / (bytesPerSample * channels));
  if (frames === 0) throw new Error("The WAV file holds no sound");
  const samples = new Float32Array(frames);
  for (let frame = 0; frame < frames; frame++) {
    let sum = 0;
    for (let channel = 0; channel < channels; channel++) {
      sum += sampleAt(view, data.at + (frame * channels + channel) * bytesPerSample, bits, isFloat);
    }
    samples[frame] = sum / channels;
  }
  return { samples, sampleRate };
}

function sampleAt(view: DataView, at: number, bits: number, isFloat: boolean): number {
  if (isFloat) return view.getFloat32(at, true);
  switch (bits) {
    case 8:
      return (view.getUint8(at) - 128) / 128;
    case 16:
      return view.getInt16(at, true) / 32768;
    case 24:
      return ((view.getUint8(at + 2) << 24) | (view.getUint8(at + 1) << 16) | (view.getUint8(at) << 8)) / 2147483648;
    default:
      return view.getInt32(at, true) / 2147483648;
  }
}

/** Turns the bytes of a clip file into a {@link Clip}. {@code .ogg} needs the browser's decoder, so it is passed in. */
export type ClipDecoder = (path: string, bytes: Uint8Array) => Promise<Clip>;

/** WAV by {@link decodeWav}, anything else (Ogg Vorbis) by the browser's `AudioContext.decodeAudioData`. */
export const webClipDecoder: ClipDecoder = async (path, bytes) => {
  if (path.toLowerCase().endsWith(".wav")) return decodeWav(bytes);
  const context = new AudioContext();
  try {
    const decoded = await context.decodeAudioData(bytes.slice().buffer);
    const samples = new Float32Array(decoded.length);
    for (let channel = 0; channel < decoded.numberOfChannels; channel++) {
      const data = decoded.getChannelData(channel);
      for (let i = 0; i < samples.length; i++) samples[i]! += data[i]! / decoded.numberOfChannels;
    }
    return { samples, sampleRate: decoded.sampleRate };
  } catch (error) {
    throw new Error(`Cannot decode ${path}: ${error instanceof Error ? error.message : String(error)}`);
  } finally {
    void context.close();
  }
};

/** Reads and decodes the clip at {@code path}; throws an Error that names the path when it cannot be loaded. */
export async function loadClipFile(path: string, decode: ClipDecoder = webClipDecoder, source: (path: string) => string = (p) => p): Promise<Clip> {
  const response = await fetch(source(path));
  if (!response.ok) throw new Error(`Sound ${path}: HTTP ${response.status}`);
  try {
    return await decode(path, new Uint8Array(await response.arrayBuffer()));
  } catch (error) {
    throw new Error(`Sound ${path}: ${error instanceof Error ? error.message : String(error)}`);
  }
}
