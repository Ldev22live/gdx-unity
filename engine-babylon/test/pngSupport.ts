import { inflateSync } from "node:zlib";
import type { DecodedImage, PngDecoder } from "../src/sprites/sprite";

/**
 * A small PNG decoder for tests (8-bit, not interlaced, grey, RGB, RGBA or palette): the browser decodes with a canvas, which Node does not have.
 * It reads the files the desktop's ImageIO writes for `.sprite` cels.
 */
export const decodePngWithZlib: PngDecoder = async (bytes): Promise<DecodedImage> => {
  const signature = [137, 80, 78, 71, 13, 10, 26, 10];
  signature.forEach((b, i) => {
    if (bytes[i] !== b) throw new Error("not a PNG");
  });
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let pos = 8;
  let width = 0;
  let height = 0;
  let colorType = 0;
  let palette: Uint8Array | null = null;
  let transparency: Uint8Array | null = null;
  const parts: Uint8Array[] = [];
  while (pos < bytes.length) {
    const length = view.getUint32(pos);
    const type = String.fromCharCode(bytes[pos + 4]!, bytes[pos + 5]!, bytes[pos + 6]!, bytes[pos + 7]!);
    const body = bytes.subarray(pos + 8, pos + 8 + length);
    if (type === "IHDR") {
      width = new DataView(body.buffer, body.byteOffset).getUint32(0);
      height = new DataView(body.buffer, body.byteOffset).getUint32(4);
      if (body[8] !== 8 || body[12] !== 0) throw new Error("only 8-bit, not interlaced PNGs");
      colorType = body[9]!;
    } else if (type === "PLTE") palette = body;
    else if (type === "tRNS") transparency = body;
    else if (type === "IDAT") parts.push(body);
    pos += 12 + length;
  }
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
  if (!channels) throw new Error(`unsupported PNG color type ${colorType}`);
  const raw = inflateSync(Buffer.concat(parts));
  const stride = width * channels;
  const lines = new Uint8Array(height * stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)]!;
    for (let x = 0; x < stride; x++) {
      const value = raw[y * (stride + 1) + 1 + x]!;
      const left = x >= channels ? lines[y * stride + x - channels]! : 0;
      const up = y > 0 ? lines[(y - 1) * stride + x]! : 0;
      const upLeft = y > 0 && x >= channels ? lines[(y - 1) * stride + x - channels]! : 0;
      let predicted = 0;
      if (filter === 1) predicted = left;
      else if (filter === 2) predicted = up;
      else if (filter === 3) predicted = (left + up) >> 1;
      else if (filter === 4) {
        const p = left + up - upLeft;
        const pa = Math.abs(p - left);
        const pb = Math.abs(p - up);
        const pc = Math.abs(p - upLeft);
        predicted = pa <= pb && pa <= pc ? left : pb <= pc ? up : upLeft;
      }
      lines[y * stride + x] = (value + predicted) & 255;
    }
  }
  const rgba = new Uint8Array(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    const s = i * channels;
    let r: number;
    let g: number;
    let b: number;
    let a = 255;
    if (colorType === 6) [r, g, b, a] = [lines[s]!, lines[s + 1]!, lines[s + 2]!, lines[s + 3]!];
    else if (colorType === 2) [r, g, b] = [lines[s]!, lines[s + 1]!, lines[s + 2]!];
    else if (colorType === 0) r = g = b = lines[s]!;
    else if (colorType === 4) {
      r = g = b = lines[s]!;
      a = lines[s + 1]!;
    } else {
      const index = lines[s]!;
      if (!palette) throw new Error("PNG has no palette");
      [r, g, b] = [palette[index * 3]!, palette[index * 3 + 1]!, palette[index * 3 + 2]!];
      a = transparency && index < transparency.length ? transparency[index]! : 255;
    }
    rgba.set([r, g, b, a], i * 4);
  }
  return { width, height, rgba };
};
