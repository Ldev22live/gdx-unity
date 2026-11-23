/**
 * Pixel-art sprites and tile maps for the web player (TD-066): reads the desktop's `.sprite` files, flattens their frames, plays tags, and does the
 * tile map arithmetic (merged solid boxes, auto-tile free: the cells are already fitted in the scene file). Pure code; the browser-only parts are the PNG
 * decoder passed in and the Babylon textures made from {@link buildAtlas}.
 */

export type TagDirection = "FORWARD" | "REVERSE" | "PING_PONG";

export interface SpriteTag {
  name: string;
  from: number;
  to: number;
  direction: TagDirection;
}

export interface SpriteLayer {
  name: string;
  visible: boolean;
  /** 0 to 255. */
  opacity: number;
}

export interface SpriteSlice {
  name: string;
  x: number;
  y: number;
  width: number;
  height: number;
  left: number;
  right: number;
  top: number;
  bottom: number;
  pivotX: number;
  pivotY: number;
}

/** A sprite as stored in the file: one PNG (base64) for each non-empty cel. */
export interface SpriteFileData {
  width: number;
  height: number;
  layers: SpriteLayer[];
  durations: number[];
  tags: SpriteTag[];
  slices: SpriteSlice[];
  cels: { layer: number; frame: number; png: string }[];
}

/** A sprite ready to draw: every frame flattened to RGBA bytes (row by row, row 0 at the top). */
export interface Sprite {
  width: number;
  height: number;
  durations: number[];
  tags: SpriteTag[];
  slices: SpriteSlice[];
  frames: Uint8Array[];
}

/** What a PNG decoder returns: RGBA bytes, row 0 at the top. */
export interface DecodedImage {
  width: number;
  height: number;
  rgba: Uint8Array;
}

export type PngDecoder = (bytes: Uint8Array) => Promise<DecodedImage>;

/** The file version this player reads. */
export const SPRITE_SCHEMA_VERSION = 1;

function record(value: unknown, what: string): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error(`${what} should be an object`);
  return value as Record<string, unknown>;
}

function list(value: unknown, what: string): unknown[] {
  if (!Array.isArray(value)) throw new Error(`${what} should be a list`);
  return value;
}

function int(value: unknown, what: string, fallback?: number): number {
  if (typeof value === "number" && Number.isInteger(value)) return value;
  if (value === undefined && fallback !== undefined) return fallback;
  throw new Error(`${what} should be a whole number, got ${JSON.stringify(value)}`);
}

function text(value: unknown, what: string): string {
  if (typeof value !== "string") throw new Error(`${what} should be text, got ${JSON.stringify(value)}`);
  return value;
}

/** Reads and checks `.sprite` text; throws with the reason when it is not a version 1 sprite. */
export function parseSpriteFile(json: string): SpriteFileData {
  const root = record(JSON.parse(json) as unknown, "A sprite file");
  if (root["kind"] !== "sprite") throw new Error('Not a sprite file (kind is not "sprite")');
  if (root["schemaVersion"] !== SPRITE_SCHEMA_VERSION) {
    throw new Error(`Unsupported sprite schemaVersion ${String(root["schemaVersion"])}; this player reads ${SPRITE_SCHEMA_VERSION}`);
  }
  const layers = list(root["layers"], "layers").map((l, i) => {
    const r = record(l, `layer ${i}`);
    return { name: text(r["name"], "layer name"), visible: r["visible"] !== false, opacity: int(r["opacity"], "layer opacity", 255) };
  });
  const durations = list(root["frames"], "frames").map((f, i) => int(record(f, `frame ${i}`)["duration"], "frame duration", 100));
  if (layers.length < 1 || durations.length < 1) throw new Error("A sprite needs at least one layer and one frame");
  const tags = list(root["tags"] ?? [], "tags").map((t, i) => {
    const r = record(t, `tag ${i}`);
    const direction = (r["direction"] ?? "FORWARD") as TagDirection;
    return { name: text(r["name"], "tag name"), from: int(r["from"], "tag from"), to: int(r["to"], "tag to"), direction };
  });
  for (const tag of tags) {
    if (tag.from < 0 || tag.to < tag.from || tag.to >= durations.length) {
      throw new Error(`Tag '${tag.name}' covers frames ${tag.from} to ${tag.to}, but the sprite has ${durations.length} frames`);
    }
  }
  const slices = list(root["slices"] ?? [], "slices").map((s, i) => {
    const r = record(s, `slice ${i}`);
    return {
      name: text(r["name"], "slice name"),
      x: int(r["x"], "slice x"),
      y: int(r["y"], "slice y"),
      width: int(r["width"], "slice width"),
      height: int(r["height"], "slice height"),
      left: int(r["left"], "slice left", 0),
      right: int(r["right"], "slice right", 0),
      top: int(r["top"], "slice top", 0),
      bottom: int(r["bottom"], "slice bottom", 0),
      pivotX: int(r["pivotX"], "slice pivotX", 0),
      pivotY: int(r["pivotY"], "slice pivotY", 0),
    };
  });
  const cels = list(root["cels"] ?? [], "cels").map((c, i) => {
    const r = record(c, `cel ${i}`);
    return { layer: int(r["layer"], "cel layer"), frame: int(r["frame"], "cel frame"), png: text(r["png"], "cel png") };
  });
  return {
    width: int(root["width"], "width"),
    height: int(root["height"], "height"),
    layers,
    durations,
    tags,
    slices,
    cels,
  };
}

function base64ToBytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** Source-over blend of one RGBA pixel (not premultiplied) with extra opacity 0 to 255, written into {@code out}; the same arithmetic as the desktop. */
function over(out: Uint8Array, at: number, src: Uint8Array, from: number, opacity: number): void {
  const sa = Math.trunc(((src[from + 3]!) * opacity + 127) / 255);
  if (sa === 0) return;
  const da = out[at + 3]!;
  if (sa === 255 || da === 0) {
    out[at] = src[from]!;
    out[at + 1] = src[from + 1]!;
    out[at + 2] = src[from + 2]!;
    out[at + 3] = sa;
    return;
  }
  const outA = sa + Math.trunc((da * (255 - sa)) / 255);
  for (let c = 0; c < 3; c++) {
    const s = src[from + c]!;
    const d = out[at + c]!;
    out[at + c] = Math.min(255, Math.trunc((s * sa * 255 + d * da * (255 - sa) + outA * 127) / (outA * 255)));
  }
  out[at + 3] = outA;
}

/** Decodes the cels and flattens every frame (visible layers bottom to top, with layer opacity). */
export async function buildSprite(file: SpriteFileData, decode: PngDecoder): Promise<Sprite> {
  const pixels = file.width * file.height * 4;
  const images = await Promise.all(
    file.cels.map(async (cel) => {
      const image = await decode(base64ToBytes(cel.png));
      if (image.width !== file.width || image.height !== file.height) {
        throw new Error(`A cel is ${image.width}x${image.height} but the sprite is ${file.width}x${file.height}`);
      }
      return { cel, image };
    }),
  );
  const frames = file.durations.map(() => new Uint8Array(pixels));
  for (let layerIndex = 0; layerIndex < file.layers.length; layerIndex++) {
    const layer = file.layers[layerIndex]!;
    if (!layer.visible || layer.opacity === 0) continue;
    for (const { cel, image } of images) {
      if (cel.layer !== layerIndex) continue;
      const out = frames[cel.frame];
      if (!out) throw new Error(`A cel names frame ${cel.frame}, but the sprite has ${frames.length}`);
      for (let i = 0; i < pixels; i += 4) over(out, i, image.rgba, i, layer.opacity);
    }
  }
  return { width: file.width, height: file.height, durations: file.durations, tags: file.tags, slices: file.slices, frames };
}

/** The browser's PNG decoder (an OffscreenCanvas); tests pass their own. */
export const decodePngInBrowser: PngDecoder = async (bytes) => {
  const bitmap = await createImageBitmap(new Blob([bytes as BlobPart], { type: "image/png" }));
  const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
  const context = canvas.getContext("2d");
  if (!context) throw new Error("No 2D canvas to decode a sprite with");
  context.drawImage(bitmap, 0, 0);
  const data = context.getImageData(0, 0, bitmap.width, bitmap.height).data;
  return { width: bitmap.width, height: bitmap.height, rgba: new Uint8Array(data.buffer, data.byteOffset, data.byteLength) };
};

// ---- playback ----

/** The frames a tag plays in order: forward, reverse, or ping-pong (forward then back without repeating the end frames); null plays all frames forward. */
export function frameSequence(frameCount: number, tag: SpriteTag | null): number[] {
  const from = tag ? tag.from : 0;
  const to = tag ? tag.to : frameCount - 1;
  const direction: TagDirection = tag ? tag.direction : "FORWARD";
  const out: number[] = [];
  if (direction === "REVERSE") {
    for (let f = to; f >= from; f--) out.push(f);
    return out;
  }
  for (let f = from; f <= to; f++) out.push(f);
  if (direction === "PING_PONG") for (let f = to - 1; f > from; f--) out.push(f);
  return out;
}

/** The frame showing {@code millis} after the start of the animation. */
export function frameAt(durations: readonly number[], frameCount: number, tag: SpriteTag | null, millis: number, loop: boolean): number {
  const sequence = frameSequence(frameCount, tag);
  let total = 0;
  for (const frame of sequence) total += durations[frame]!;
  let t = loop ? millis % total : Math.min(millis, total - 1);
  for (const frame of sequence) {
    const duration = durations[frame]!;
    if (t < duration) return frame;
    t -= duration;
  }
  return sequence[sequence.length - 1]!;
}

/** The playback state of one sprite animation, the same rules as the desktop's SpriteAnimationState. */
export class SpriteAnimationState {
  private tagName: string | null = null;
  private elapsed = 0;
  private speedFactor = 1;
  private looping = true;
  private playingNow = true;

  constructor(readonly sprite: Pick<Sprite, "width" | "height" | "durations" | "tags" | "frames">) {}

  private find(name: string | null): SpriteTag | null {
    if (name === null) return null;
    const tag = this.sprite.tags.find((t) => t.name === name);
    if (!tag) {
      const known = this.sprite.tags.map((t) => t.name).join(", ") || "none";
      throw new Error(`The sprite has no tag "${name}" (tags: ${known})`);
    }
    return tag;
  }

  /** Plays a tag (null: all frames); the tag that already plays continues unless {@code restart}. Throws for an unknown tag. */
  play(name: string | null, restart = false): void {
    this.find(name);
    const same = name === this.tagName;
    this.tagName = name;
    this.playingNow = true;
    if (restart || !same) this.elapsed = 0;
  }

  get tag(): string | null {
    return this.tagName;
  }

  pause(): void {
    this.playingNow = false;
  }

  resume(): void {
    this.playingNow = true;
  }

  stop(): void {
    this.playingNow = false;
    this.elapsed = 0;
  }

  get playing(): boolean {
    return this.playingNow;
  }

  get speed(): number {
    return this.speedFactor;
  }

  set speed(value: number) {
    if (!(value >= 0)) throw new Error(`speed cannot be negative or NaN: ${value}`);
    this.speedFactor = value;
  }

  get loop(): boolean {
    return this.looping;
  }

  set loop(value: boolean) {
    this.looping = value;
  }

  advance(seconds: number): void {
    if (this.playingNow) this.elapsed += seconds * this.speedFactor * 1000;
  }

  get frame(): number {
    return frameAt(this.sprite.durations, this.sprite.frames.length, this.find(this.tagName), Math.trunc(this.elapsed), this.looping);
  }

  get finished(): boolean {
    if (this.looping) return false;
    let total = 0;
    for (const f of frameSequence(this.sprite.frames.length, this.find(this.tagName))) total += this.sprite.durations[f]!;
    return this.elapsed >= total;
  }
}

// ---- atlas ----

/** All frames of a sprite in one grid image (the same layout as the desktop's FrameAtlas). */
export interface Atlas {
  width: number;
  height: number;
  columns: number;
  frameWidth: number;
  frameHeight: number;
  data: Uint8Array;
}

export function buildAtlas(sprite: Pick<Sprite, "width" | "height" | "frames">): Atlas {
  const count = sprite.frames.length;
  const columns = Math.ceil(Math.sqrt(count));
  const rows = Math.ceil(count / columns);
  const width = columns * sprite.width;
  const height = rows * sprite.height;
  const data = new Uint8Array(width * height * 4);
  for (let f = 0; f < count; f++) {
    const ox = (f % columns) * sprite.width;
    const oy = Math.trunc(f / columns) * sprite.height;
    const frame = sprite.frames[f]!;
    for (let y = 0; y < sprite.height; y++) {
      data.set(frame.subarray(y * sprite.width * 4, (y + 1) * sprite.width * 4), ((oy + y) * width + ox) * 4);
    }
  }
  return { width, height, columns, frameWidth: sprite.width, frameHeight: sprite.height, data };
}

/** How far, in texels, a frame's texture coordinates are pulled in from its edges, so nearest filtering never reads the neighbouring frame (a hairline seam). */
export const UV_INSET_TEXELS = 0.02;

/** Where a frame lies in the atlas texture, as texture coordinates (v grows upward, so the top row of the image has the larger v). */
export function frameUv(atlas: Atlas, frame: number): { u0: number; v0: number; u1: number; v1: number } {
  const x = (frame % atlas.columns) * atlas.frameWidth;
  const y = Math.trunc(frame / atlas.columns) * atlas.frameHeight;
  const du = UV_INSET_TEXELS / atlas.width;
  const dv = UV_INSET_TEXELS / atlas.height;
  return {
    u0: x / atlas.width + du,
    u1: (x + atlas.frameWidth) / atlas.width - du,
    v1: 1 - y / atlas.height - dv,
    v0: 1 - (y + atlas.frameHeight) / atlas.height + dv,
  };
}

// ---- tile maps ----

export interface TileLayerData {
  name: string;
  visible: boolean;
  /** Tile ids row by row, row 0 at the top; 0 is an empty cell. */
  cells: number[];
}

export interface TileMapData {
  cols: number;
  rows: number;
  tileWidth: number;
  tileHeight: number;
  collides: boolean;
  layers: TileLayerData[];
  solid: Set<number>;
  tileSet: string | null;
}

/** One box of solid cells: the column and row of its top left cell and how many cells across and down. */
export interface CellBox {
  col: number;
  row: number;
  cols: number;
  rows: number;
}

/** Reads the data of a Tilemap component of a scene file. */
export function parseTilemap(data: Record<string, unknown>): TileMapData {
  const cols = int(data["cols"], "cols", 20);
  const rows = int(data["rows"], "rows", 12);
  const layers = list(data["layers"] ?? [], "layers").map((l, i) => {
    const r = record(l, `tile layer ${i}`);
    const cells = list(r["cells"] ?? [], "cells").map((c) => int(c, "tile id"));
    if (cells.length !== cols * rows) throw new Error(`Tile layer '${String(r["name"])}' has ${cells.length} cells, but the map is ${cols}x${rows}`);
    return { name: text(r["name"] ?? "Layer", "layer name"), visible: r["visible"] !== false, cells };
  });
  if (layers.length === 0) layers.push({ name: "Ground", visible: true, cells: new Array<number>(cols * rows).fill(0) });
  const tileSet = data["tileSet"];
  return {
    cols,
    rows,
    tileWidth: typeof data["tileWidth"] === "number" ? data["tileWidth"] : 32,
    tileHeight: typeof data["tileHeight"] === "number" ? data["tileHeight"] : 32,
    collides: data["collides"] !== false,
    layers,
    solid: new Set(list(data["solid"] ?? [], "solid").map((t) => int(t, "solid tile id"))),
    tileSet: typeof tileSet === "string" && tileSet !== "" ? tileSet : null,
  };
}

/** The solid cells of all visible layers merged into boxes: runs along a row, joined with the runs of the same width below (the same as the desktop). */
export function solidBoxes(map: TileMapData): CellBox[] {
  const blocked = new Array<boolean>(map.cols * map.rows).fill(false);
  for (const layer of map.layers) {
    if (!layer.visible) continue;
    for (let i = 0; i < blocked.length; i++) if (map.solid.has(layer.cells[i]!)) blocked[i] = true;
  }
  const used = new Array<boolean>(blocked.length).fill(false);
  const free = (row: number, col: number): boolean => blocked[row * map.cols + col]! && !used[row * map.cols + col];
  const boxes: CellBox[] = [];
  for (let r = 0; r < map.rows; r++) {
    for (let c = 0; c < map.cols; c++) {
      if (!free(r, c)) continue;
      let width = 1;
      while (c + width < map.cols && free(r, c + width)) width++;
      let height = 1;
      const rowFree = (row: number): boolean => {
        for (let cc = c; cc < c + width; cc++) if (!free(row, cc)) return false;
        return true;
      };
      while (r + height < map.rows && rowFree(r + height)) height++;
      for (let rr = r; rr < r + height; rr++) for (let cc = c; cc < c + width; cc++) used[rr * map.cols + cc] = true;
      boxes.push({ col: c, row: r, cols: width, rows: height });
    }
  }
  return boxes;
}

/**
 * Whether the point (x, y) lies in a solid cell of a visible layer of a tile map whose bottom left corner is at {@code origin}, for scripted characters (the same
 * as the desktop's TilemapComponent.isSolidAt). False outside the map.
 */
export function tilemapSolidAt(map: TileMapData, origin: { x: number; y: number }, scale: { x: number; y: number }, x: number, y: number): boolean {
  const col = Math.floor((x - origin.x) / (map.tileWidth * Math.abs(scale.x)));
  const row = map.rows - 1 - Math.floor((y - origin.y) / (map.tileHeight * Math.abs(scale.y)));
  if (col < 0 || row < 0 || col >= map.cols || row >= map.rows) return false;
  return map.layers.some((layer) => layer.visible && map.solid.has(layer.cells[row * map.cols + col]!));
}

/** Vertex data of the visible, non-empty cells of one layer: quads in the XY plane with the map's bottom left corner at the origin. */
export function tileLayerGeometry(
  map: TileMapData,
  layer: TileLayerData,
  atlas: Atlas,
  frameCount: number,
): { positions: number[]; uvs: number[]; indices: number[] } {
  const positions: number[] = [];
  const uvs: number[] = [];
  const indices: number[] = [];
  const mapHeight = map.rows * map.tileHeight;
  for (let r = 0; r < map.rows; r++) {
    for (let c = 0; c < map.cols; c++) {
      const tile = layer.cells[r * map.cols + c]!;
      if (tile === 0 || tile > frameCount) continue;
      const x0 = c * map.tileWidth;
      const x1 = x0 + map.tileWidth;
      const y1 = mapHeight - r * map.tileHeight;
      const y0 = y1 - map.tileHeight;
      const uv = frameUv(atlas, tile - 1);
      const base = positions.length / 3;
      positions.push(x0, y0, 0, x1, y0, 0, x1, y1, 0, x0, y1, 0);
      uvs.push(uv.u0, uv.v0, uv.u1, uv.v0, uv.u1, uv.v1, uv.u0, uv.v1);
      indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }
  return { positions, uvs, indices };
}
