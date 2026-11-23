import { STEP_TICKS, TICKS_PER_BAR, TICKS_PER_BEAT, isBlackKey, notesOf, pitchName, type NoteTuple } from "../../audio/song";
import { h } from "../dom";
import type { AudioEngine } from "../../audio/AudioEngine";
import type { MusicModel } from "./MusicModel";
import { localPoint, select } from "./ui";

/** Pixels per tick: a sixteenth is 36 px wide. */
const PX_PER_TICK = 1.5;
const ROW_HEIGHT = 14;
const KEYBOARD_WIDTH = 52;
/** Pitches shown at once: three octaves. */
const VISIBLE_ROWS = 36;
const HIGHEST_PITCH = 127;
/** The right edge of a note this many pixels wide resizes it instead of moving it. */
const RESIZE_HANDLE_PX = 7;
/** Rows the wheel scrolls per notch. */
const WHEEL_ROWS = 3;
const DEFAULT_VELOCITY = 0.8;
const NOTE_COLOR = "#5aa0ff";
const SELECTED_COLOR = "#ffb347";

const SNAPS = [
  { label: "1/4", ticks: TICKS_PER_BEAT },
  { label: "1/8", ticks: TICKS_PER_BEAT / 2 },
  { label: "1/16", ticks: STEP_TICKS },
  { label: "1/32", ticks: STEP_TICKS / 2 },
] as const;

type Drag =
  | { kind: "move"; index: number; grabTick: number; grabPitch: number; original: NoteTuple; preview: NoteTuple }
  | { kind: "resize"; index: number; original: NoteTuple; preview: NoteTuple };

/**
 * The piano roll: the notes of the selected channel in the selected pattern. Click empty space to add a note (drag
 * sideways to set its length), drag a note to move it, drag its right edge to resize, right-click to delete. Each
 * gesture is one undo step. The wheel scrolls the pitch range.
 */
export class PianoRoll {
  readonly root: HTMLElement;
  private readonly canvas = h("canvas", { class: "roll-canvas" }) as HTMLCanvasElement;
  private readonly scroller = h("div", { class: "roll-scroll" });
  private readonly playhead = h("div", { class: "playhead" });
  private readonly snapSelect: HTMLSelectElement;
  private lowPitch = 48;
  private snapTicks: number = STEP_TICKS;
  private drag: Drag | null = null;
  private heldPitch: number | null = null;

  constructor(
    private readonly model: MusicModel,
    private readonly engine: AudioEngine,
  ) {
    this.snapSelect = select(SNAPS.map((s) => s.label), "1/16", (label) => {
      this.snapTicks = SNAPS.find((s) => s.label === label)!.ticks;
    });
    this.scroller.append(this.canvas, this.playhead);
    this.root = h("div", { class: "roll" }, h("div", { class: "roll-bar" }, h("span", {}, "Piano roll"), h("label", { class: "inline" }, "Snap", this.snapSelect)), this.scroller);
    this.canvas.addEventListener("pointerdown", (e) => this.onDown(e));
    this.canvas.addEventListener("pointermove", (e) => this.onMove(e));
    this.canvas.addEventListener("pointerup", (e) => this.onUp(e));
    this.canvas.addEventListener("pointercancel", () => this.cancel());
    this.canvas.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      this.deleteAt(e);
    });
    this.canvas.addEventListener(
      "wheel",
      (e) => {
        e.preventDefault();
        this.scrollPitch(e.deltaY > 0 ? -WHEEL_ROWS : WHEEL_ROWS);
      },
      { passive: false },
    );
    this.fitToNotes();
    this.render();
  }

  /** The pitch at the bottom of the view; tests and tools read it. */
  get bottomPitch(): number {
    return this.lowPitch;
  }

  private scrollPitch(rows: number): void {
    this.lowPitch = Math.min(Math.max(this.lowPitch + rows, 0), HIGHEST_PITCH - VISIBLE_ROWS + 1);
    this.render();
  }

  /** Scrolls the view so the channel's notes are in sight (or around middle C for an empty channel). */
  fitToNotes(): void {
    const pattern = this.model.pattern;
    const channel = this.model.channel;
    if (!pattern || !channel) return;
    const notes = notesOf(pattern, channel.id);
    const centre = notes.length > 0 ? Math.round(notes.reduce((s, n) => s + n[2], 0) / notes.length) : channel.stepPitch;
    this.lowPitch = Math.min(Math.max(centre - Math.floor(VISIBLE_ROWS / 2), 0), HIGHEST_PITCH - VISIBLE_ROWS + 1);
  }

  private yToPitch(y: number): number {
    return this.lowPitch + VISIBLE_ROWS - 1 - Math.floor(y / ROW_HEIGHT);
  }

  private xToTick(x: number): number {
    return (x - KEYBOARD_WIDTH) / PX_PER_TICK;
  }

  private tickToX(tick: number): number {
    return KEYBOARD_WIDTH + tick * PX_PER_TICK;
  }

  private pitchTop(pitch: number): number {
    return (VISIBLE_ROWS - 1 - (pitch - this.lowPitch)) * ROW_HEIGHT;
  }

  /** Index of the note under a canvas point, or -1. */
  private noteAt(x: number, y: number): number {
    const pattern = this.model.pattern;
    const channel = this.model.channel;
    if (!pattern || !channel) return -1;
    const tick = this.xToTick(x);
    const pitch = this.yToPitch(y);
    return notesOf(pattern, channel.id).findIndex((n) => n[2] === pitch && tick >= n[0] && tick < n[0] + n[1]);
  }

  private onDown(event: PointerEvent): void {
    const pattern = this.model.pattern;
    const channel = this.model.channel;
    if (!pattern || !channel || event.button !== 0) return;
    const { x, y } = localPoint(this.canvas, event);
    const pitch = this.yToPitch(y);
    if (pitch < 0 || pitch > HIGHEST_PITCH) return;
    this.canvas.setPointerCapture(event.pointerId);
    if (x < KEYBOARD_WIDTH) {
      this.startPreview(channel.id, pitch);
      return;
    }
    const index = this.noteAt(x, y);
    if (index >= 0) {
      const note = notesOf(pattern, channel.id)[index]!;
      const onEdge = x >= this.tickToX(note[0] + note[1]) - RESIZE_HANDLE_PX;
      this.drag = onEdge
        ? { kind: "resize", index, original: note, preview: note }
        : { kind: "move", index, grabTick: this.xToTick(x) - note[0], grabPitch: pitch - note[2], original: note, preview: note };
      this.startPreview(channel.id, note[2]);
    } else {
      const tick = Math.floor(Math.max(this.xToTick(x), 0) / this.snapTicks) * this.snapTicks;
      if (tick >= pattern.lengthTicks) return;
      const note: NoteTuple = [tick, this.snapTicks, pitch, DEFAULT_VELOCITY];
      const added = this.model.addNote(pattern.id, channel.id, note);
      this.drag = { kind: "resize", index: added, original: note, preview: note };
      this.startPreview(channel.id, pitch);
    }
    this.render();
  }

  private onMove(event: PointerEvent): void {
    const drag = this.drag;
    const pattern = this.model.pattern;
    if (!drag || !pattern) return;
    const { x, y } = localPoint(this.canvas, event);
    if (drag.kind === "move") {
      const tick = Math.round((this.xToTick(x) - drag.grabTick) / this.snapTicks) * this.snapTicks;
      const pitch = Math.min(Math.max(this.yToPitch(y) - drag.grabPitch, 0), HIGHEST_PITCH);
      const start = Math.min(Math.max(tick, 0), pattern.lengthTicks - 1);
      const length = Math.min(drag.original[1], pattern.lengthTicks - start);
      drag.preview = [start, length, pitch, drag.original[3]];
    } else {
      const end = Math.round(this.xToTick(x) / this.snapTicks) * this.snapTicks;
      const length = Math.min(Math.max(end - drag.original[0], this.snapTicks), pattern.lengthTicks - drag.original[0]);
      drag.preview = [drag.original[0], length, drag.original[2], drag.original[3]];
    }
    this.render();
  }

  private onUp(event: PointerEvent): void {
    this.stopPreview();
    const drag = this.drag;
    this.drag = null;
    if (this.canvas.hasPointerCapture(event.pointerId)) this.canvas.releasePointerCapture(event.pointerId);
    const pattern = this.model.pattern;
    const channel = this.model.channel;
    if (!drag || !pattern || !channel) return;
    // A note that was just added is already in the song; only a gesture that changed it needs a second edit
    if (drag.preview.some((v, i) => v !== drag.original[i])) {
      // Adding then resizing is two undo steps on purpose: undo first removes the length change, then the note
      this.model.replaceNote(pattern.id, channel.id, drag.index, drag.preview, drag.kind === "move" ? "Move note" : "Resize note");
    }
    this.render();
  }

  private cancel(): void {
    this.stopPreview();
    this.drag = null;
    this.render();
  }

  private deleteAt(event: MouseEvent): void {
    const pattern = this.model.pattern;
    const channel = this.model.channel;
    if (!pattern || !channel) return;
    const { x, y } = localPoint(this.canvas, event);
    const index = this.noteAt(x, y);
    if (index >= 0) this.model.removeNote(pattern.id, channel.id, index);
  }

  private startPreview(channelId: number, pitch: number): void {
    this.stopPreview();
    this.heldPitch = pitch;
    void this.engine.noteOn(channelId, pitch, DEFAULT_VELOCITY);
  }

  private stopPreview(): void {
    if (this.heldPitch !== null) this.engine.noteOff(this.model.channelId, this.heldPitch);
    this.heldPitch = null;
  }

  /** Moves the playhead line; `ticks` is null when nothing plays in this pattern. */
  setPlayhead(ticks: number | null): void {
    this.playhead.style.display = ticks === null ? "none" : "block";
    if (ticks !== null) this.playhead.style.left = `${this.tickToX(ticks)}px`;
  }

  /** Redraws the grid and the notes. */
  render(): void {
    const pattern = this.model.pattern;
    const channel = this.model.channel;
    const width = KEYBOARD_WIDTH + (pattern?.lengthTicks ?? TICKS_PER_BAR) * PX_PER_TICK;
    const height = VISIBLE_ROWS * ROW_HEIGHT;
    this.canvas.width = Math.ceil(width);
    this.canvas.height = height;
    this.canvas.style.width = `${Math.ceil(width)}px`;
    this.canvas.style.height = `${height}px`;
    const g = this.canvas.getContext("2d");
    if (!g) return;
    g.fillStyle = "#12141a";
    g.fillRect(0, 0, width, height);
    for (let row = 0; row < VISIBLE_ROWS; row++) {
      const pitch = this.lowPitch + VISIBLE_ROWS - 1 - row;
      g.fillStyle = isBlackKey(pitch) ? "#171a22" : "#1b1f29";
      g.fillRect(KEYBOARD_WIDTH, row * ROW_HEIGHT, width, ROW_HEIGHT - 1);
      g.fillStyle = isBlackKey(pitch) ? "#22252e" : "#d9dce5";
      g.fillRect(0, row * ROW_HEIGHT, KEYBOARD_WIDTH - 2, ROW_HEIGHT - 1);
      if (pitch % 12 === 0) {
        g.fillStyle = "#222";
        g.font = "10px sans-serif";
        g.fillText(pitchName(pitch), 4, row * ROW_HEIGHT + 10);
      }
    }
    if (!pattern || !channel) return;
    for (let tick = 0; tick <= pattern.lengthTicks; tick += STEP_TICKS) {
      const x = this.tickToX(tick);
      g.fillStyle = tick % TICKS_PER_BAR === 0 ? "#556" : tick % TICKS_PER_BEAT === 0 ? "#3a3f4d" : "#262a36";
      g.fillRect(x, 0, 1, height);
    }
    const notes = notesOf(pattern, channel.id);
    notes.forEach((note, index) => {
      const shown = this.drag && this.drag.index === index ? this.drag.preview : note;
      if (shown[2] < this.lowPitch || shown[2] >= this.lowPitch + VISIBLE_ROWS) return;
      const x = this.tickToX(shown[0]);
      const y = this.pitchTop(shown[2]);
      g.globalAlpha = 0.35 + 0.65 * shown[3];
      g.fillStyle = this.drag && this.drag.index === index ? SELECTED_COLOR : NOTE_COLOR;
      g.fillRect(x, y, Math.max(shown[1] * PX_PER_TICK - 1, 2), ROW_HEIGHT - 1);
      g.globalAlpha = 1;
    });
  }
}
