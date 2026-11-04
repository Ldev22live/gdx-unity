import { TICKS_PER_BAR, canPlaceClip, findPattern, playlistLengthTicks, type ClipTuple } from "../../audio/song";
import { h } from "../dom";
import type { MusicModel } from "./MusicModel";
import { localPoint } from "./ui";

const BAR_WIDTH = 48;
const TRACK_HEIGHT = 26;
const HEADER_WIDTH = 56;
/** Tracks shown; the song allows up to MAX_TRACKS but eight is what fits comfortably. */
const VISIBLE_TRACKS = 8;
/** Bars of empty room kept after the last clip, and the least bars shown. */
const SPARE_BARS = 4;
const MIN_BARS = 16;
/** The right edge of a clip this many pixels wide resizes it. */
const RESIZE_HANDLE_PX = 8;
const CLIP_COLORS = ["#3b5b8f", "#6b4b8f", "#3f8f6b", "#8f6b3b", "#8f3b5b", "#3b8f8f"];

type Drag =
  | { kind: "move"; index: number; grabBar: number; original: ClipTuple; preview: ClipTuple }
  | { kind: "resize"; index: number; original: ClipTuple; preview: ClipTuple };

/**
 * The playlist: patterns placed on tracks along the song's bars. Click an empty bar to place the selected pattern,
 * drag a clip to move it (to another track too), drag its right edge to resize, right-click to delete.
 */
export class Playlist {
  readonly root: HTMLElement;
  private readonly canvas = h("canvas", { class: "playlist-canvas" }) as HTMLCanvasElement;
  private readonly scroller = h("div", { class: "roll-scroll" });
  private readonly playhead = h("div", { class: "playhead" });
  private drag: Drag | null = null;

  constructor(private readonly model: MusicModel) {
    this.scroller.append(this.canvas, this.playhead);
    this.root = h("div", { class: "playlist" }, this.scroller);
    this.canvas.addEventListener("pointerdown", (e) => this.onDown(e));
    this.canvas.addEventListener("pointermove", (e) => this.onMove(e));
    this.canvas.addEventListener("pointerup", (e) => this.onUp(e));
    this.canvas.addEventListener("pointercancel", () => {
      this.drag = null;
      this.render();
    });
    this.canvas.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      const { x, y } = localPoint(this.canvas, e);
      const index = this.clipAt(x, y);
      if (index >= 0) this.model.removeClip(index);
    });
    this.render();
  }

  private barCount(): number {
    return Math.max(MIN_BARS, Math.ceil(playlistLengthTicks(this.model.song) / TICKS_PER_BAR) + SPARE_BARS);
  }

  private xToBar(x: number): number {
    return (x - HEADER_WIDTH) / BAR_WIDTH;
  }

  private clipAt(x: number, y: number): number {
    const bar = this.xToBar(x);
    const track = Math.floor(y / TRACK_HEIGHT);
    return this.model.song.clips.findIndex((c) => c[1] === track && bar * TICKS_PER_BAR >= c[2] && bar * TICKS_PER_BAR < c[2] + c[3]);
  }

  private onDown(event: PointerEvent): void {
    if (event.button !== 0) return;
    const { x, y } = localPoint(this.canvas, event);
    const track = Math.floor(y / TRACK_HEIGHT);
    if (x < HEADER_WIDTH || track < 0 || track >= VISIBLE_TRACKS) return;
    this.canvas.setPointerCapture(event.pointerId);
    const index = this.clipAt(x, y);
    if (index >= 0) {
      const clip = this.model.song.clips[index]!;
      const endX = HEADER_WIDTH + ((clip[2] + clip[3]) / TICKS_PER_BAR) * BAR_WIDTH;
      this.drag =
        x >= endX - RESIZE_HANDLE_PX
          ? { kind: "resize", index, original: clip, preview: clip }
          : { kind: "move", index, grabBar: Math.floor(this.xToBar(x)) - clip[2] / TICKS_PER_BAR, original: clip, preview: clip };
      return;
    }
    // Place the selected pattern at the bar, if it fits
    const pattern = this.model.pattern;
    if (!pattern) return;
    const clip: ClipTuple = [pattern.id, track, Math.max(Math.floor(this.xToBar(x)), 0) * TICKS_PER_BAR, pattern.lengthTicks];
    if (canPlaceClip(this.model.song, clip)) this.model.addClip(clip);
  }

  private onMove(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag) return;
    const { x, y } = localPoint(this.canvas, event);
    const original = drag.original;
    if (drag.kind === "move") {
      const bar = Math.max(Math.round(this.xToBar(x) - drag.grabBar - 0.5), 0);
      const track = Math.min(Math.max(Math.floor(y / TRACK_HEIGHT), 0), VISIBLE_TRACKS - 1);
      drag.preview = [original[0], track, bar * TICKS_PER_BAR, original[3]];
    } else {
      const endBar = Math.max(Math.round(this.xToBar(x)), original[2] / TICKS_PER_BAR + 1);
      drag.preview = [original[0], original[1], original[2], endBar * TICKS_PER_BAR - original[2]];
    }
    this.render();
  }

  private onUp(event: PointerEvent): void {
    const drag = this.drag;
    this.drag = null;
    if (this.canvas.hasPointerCapture(event.pointerId)) this.canvas.releasePointerCapture(event.pointerId);
    if (!drag) return;
    const changed = drag.preview.some((v, i) => v !== drag.original[i]);
    if (changed && canPlaceClip(this.model.song, drag.preview, drag.index)) this.model.replaceClip(drag.index, drag.preview);
    else this.render();
  }

  /** Moves the playhead line; `ticks` is null when the song is not what plays. */
  setPlayhead(ticks: number | null): void {
    this.playhead.style.display = ticks === null ? "none" : "block";
    if (ticks !== null) this.playhead.style.left = `${HEADER_WIDTH + (ticks / TICKS_PER_BAR) * BAR_WIDTH}px`;
  }

  render(): void {
    const bars = this.barCount();
    const width = HEADER_WIDTH + bars * BAR_WIDTH;
    const height = VISIBLE_TRACKS * TRACK_HEIGHT;
    this.canvas.width = width;
    this.canvas.height = height;
    this.canvas.style.width = `${width}px`;
    this.canvas.style.height = `${height}px`;
    const g = this.canvas.getContext("2d");
    if (!g) return;
    g.fillStyle = "#12141a";
    g.fillRect(0, 0, width, height);
    for (let track = 0; track < VISIBLE_TRACKS; track++) {
      g.fillStyle = track % 2 ? "#171a22" : "#1b1f29";
      g.fillRect(HEADER_WIDTH, track * TRACK_HEIGHT, width, TRACK_HEIGHT - 1);
      g.fillStyle = "#9aa";
      g.font = "11px sans-serif";
      g.fillText(`Track ${track + 1}`, 4, track * TRACK_HEIGHT + 17);
    }
    for (let bar = 0; bar <= bars; bar++) {
      g.fillStyle = bar % 4 === 0 ? "#556" : "#2a2e3a";
      g.fillRect(HEADER_WIDTH + bar * BAR_WIDTH, 0, 1, height);
    }
    this.model.song.clips.forEach((clip, index) => {
      const shown = this.drag && this.drag.index === index ? this.drag.preview : clip;
      const pattern = findPattern(this.model.song, shown[0]);
      const x = HEADER_WIDTH + (shown[2] / TICKS_PER_BAR) * BAR_WIDTH;
      const w = (shown[3] / TICKS_PER_BAR) * BAR_WIDTH - 1;
      g.fillStyle = CLIP_COLORS[shown[0] % CLIP_COLORS.length]!;
      g.fillRect(x, shown[1] * TRACK_HEIGHT + 1, w, TRACK_HEIGHT - 3);
      g.fillStyle = "#fff";
      g.fillText(pattern?.name ?? "?", x + 4, shown[1] * TRACK_HEIGHT + 17, Math.max(w - 8, 0));
    });
  }
}
