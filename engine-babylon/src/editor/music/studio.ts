import { AudioEngine, type WorkletReport } from "../../audio/AudioEngine";
import { renderSong, writeWav } from "../../audio/export";
import type { PlayMode } from "../../audio/player";
import { STEPS_PER_BAR, STEP_TICKS, defaultSong, parseSong, playlistLengthTicks, songToJson } from "../../audio/song";
import { MAX_BPM, MIN_BPM } from "../../audio/song";
import { h } from "../dom";
import { InstrumentView } from "./instrumentView";
import { MixerView } from "./mixerView";
import { MusicModel, type MusicChange } from "./MusicModel";
import { PianoRoll } from "./pianoRoll";
import { Playlist } from "./playlist";
import { button, select, slider } from "./ui";

type BottomTab = "playlist" | "mixer" | "instrument";
const BOTTOM_TABS: readonly BottomTab[] = ["playlist", "mixer", "instrument"];
const TAB_TITLES: Record<BottomTab, string> = { playlist: "Playlist", mixer: "Mixer", instrument: "Instrument" };
/** A step click sounds the channel's note for this long. */
const STEP_PREVIEW_MS = 160;
const PREVIEW_VELOCITY = 0.8;
const STEP_VELOCITY = 0.8;

interface SongListEntry {
  file: string;
  name: string;
}

/**
 * The Music tab: transport, a channel rack with a step sequencer, the piano roll, and a bottom area with the playlist,
 * the mixer and the instrument settings. It owns a {@link MusicModel} (the song and its undo history) and drives an
 * {@link AudioEngine} (the audio thread). Songs are the desktop's `.song` files.
 */
export class MusicStudio {
  readonly model: MusicModel;
  readonly engine: AudioEngine;
  readonly pianoRoll: PianoRoll;
  readonly playlist: Playlist;
  readonly mixerView: MixerView;
  readonly instrumentView: InstrumentView;
  readonly root: HTMLElement;
  private readonly rack = h("div", { class: "rack" });
  private readonly transport = h("div", { class: "transport" });
  private readonly bottom = h("div", { class: "bottom-content" });
  private readonly position = h("span", { class: "position" }, "1.1");
  private readonly status = h("span", { class: "music-status" });
  private readonly masterMeter = h("div", { class: "master-meter" }, h("div", { class: "bar" }), h("div", { class: "bar" }));
  private playButton!: HTMLButtonElement;
  private mode: PlayMode = "PATTERN";
  private tab: BottomTab = "playlist";
  private fileHandle: FileSystemFileHandle | null = null;
  private visible = false;

  constructor(parent: HTMLElement, engine = new AudioEngine()) {
    this.engine = engine;
    this.model = new MusicModel(defaultSong());
    this.pianoRoll = new PianoRoll(this.model, engine);
    this.playlist = new Playlist(this.model);
    this.mixerView = new MixerView(this.model);
    this.instrumentView = new InstrumentView(this.model);
    this.root = h(
      "section",
      { id: "music", hidden: "" },
      this.transport,
      h("div", { class: "music-main" }, h("div", { class: "rack-column" }, this.rack), this.pianoRoll.root),
      h("div", { class: "music-bottom" }, this.tabBar(), this.bottom),
      this.status,
    );
    parent.append(this.root);

    this.model.addListener((change) => this.onChange(change));
    engine.onReport = (report) => this.onReport(report);
    engine.onError = (message) => this.setStatus(message, true);
    this.buildTransport();
    this.renderAll();
    engine.sendSong(this.model.song);
    window.addEventListener("keydown", (event) => this.onKey(event));
    window.addEventListener("beforeunload", (event) => {
      if (this.model.isDirty) event.preventDefault();
    });
  }

  get isVisible(): boolean {
    return this.visible;
  }

  /** Shows or hides the tab; the editor's tab buttons call this. */
  setVisible(visible: boolean): void {
    this.visible = visible;
    this.root.hidden = !visible;
    if (visible) this.renderAll();
  }

  // ---- Model -> views and audio -------------------------------------------------------------------------------

  private onChange(change: MusicChange): void {
    switch (change.kind) {
      case "strip":
        this.engine.setStrip(change.index, change.values);
        break;
      case "effect":
        this.engine.setEffect(change.strip, change.effectIndex, change.name, change.value);
        break;
      case "selection":
        this.pianoRoll.fitToNotes();
        this.renderRack();
        this.pianoRoll.render();
        this.renderBottom();
        break;
      case "song":
        this.engine.sendSong(this.model.song);
        this.engine.setPlayback(this.mode, this.model.patternId);
        if (!change.quiet) this.renderAll();
        break;
      case "replaced":
        this.engine.sendSong(this.model.song);
        this.engine.setPlayback(this.mode, this.model.patternId);
        this.renderAll();
        break;
    }
    this.updateTransport();
  }

  private renderAll(): void {
    this.renderRack();
    this.pianoRoll.render();
    this.playlist.render();
    this.mixerView.render();
    this.instrumentView.render();
    this.renderBottom();
    this.updateTransport();
  }

  // ---- Transport ----------------------------------------------------------------------------------------------

  private buildTransport(): void {
    this.playButton = button("▶ Play", "Play or pause (Space)", () => void this.togglePlay(), "primary") as HTMLButtonElement;
    const stop = button("■", "Stop and go to the start", () => this.stop());
    const loop = h("input", { type: "checkbox", checked: "" }) as HTMLInputElement;
    loop.checked = true;
    loop.addEventListener("change", () => this.engine.setLoop(loop.checked));
    const mode = select(["PATTERN", "SONG"] as const, this.mode, (value) => {
      this.mode = value;
      this.engine.setPlayback(this.mode, this.model.patternId);
      this.engine.seek(0);
      this.updateTransport();
    }, (m) => (m === "PATTERN" ? "Pattern" : "Song"));
    const bpm = h("input", { type: "number", min: String(MIN_BPM), max: String(MAX_BPM), step: "1", id: "music-bpm" }) as HTMLInputElement;
    bpm.value = String(this.model.song.bpm);
    bpm.addEventListener("input", () => {
      const value = Number(bpm.value);
      if (bpm.value.trim() !== "" && value >= MIN_BPM && value <= MAX_BPM) this.model.setBpm(value);
    });
    const swing = slider("Swing", 0, 1, 0.01, this.model.song.swing, (value) => this.model.setSwing(value));
    const songName = h("input", { type: "text", class: "song-name", id: "music-name" }) as HTMLInputElement;
    songName.value = this.model.song.name;
    songName.addEventListener("input", () => this.model.setName(songName.value));
    const songs = h("select", { title: "Open a bundled song" }, h("option", { value: "" }, "Songs…")) as HTMLSelectElement;
    songs.addEventListener("change", () => {
      const file = songs.value;
      songs.value = "";
      if (file) void this.openBundled(file).catch((e) => this.setStatus(String(e), true));
    });
    fetch("music/index.json")
      .then((response) => (response.ok ? (response.json() as Promise<SongListEntry[]>) : []))
      .then((entries) => entries.forEach((entry) => songs.append(h("option", { value: entry.file }, entry.name))))
      .catch(() => undefined);

    this.transport.append(
      this.playButton,
      stop,
      h("label", { class: "inline" }, loop, "Loop"),
      mode,
      h("label", { class: "inline" }, "BPM", bpm),
      swing.row,
      this.position,
      this.masterMeter,
      h("span", { class: "sep" }),
      songName,
      button("New", "A new song", () => this.newSong()),
      button("Open…", "Open a .song file", () => void this.openFromDisk().catch((e) => this.setStatus(String(e), true))),
      songs,
      button("Save", "Save the song (Ctrl+S)", () => void this.save()),
      button("Export WAV", "Render the song to a WAV file", () => this.exportWav()),
      button("↶", "Undo (Ctrl+Z)", () => this.model.undo()),
      button("↷", "Redo (Ctrl+Y)", () => this.model.redo()),
    );
    // Controls that mirror the model are refreshed from it
    this.transportRefresh = () => {
      if (document.activeElement !== bpm) bpm.value = String(this.model.song.bpm);
      if (document.activeElement !== songName) songName.value = this.model.song.name;
      swing.input.value = String(this.model.song.swing);
    };
  }

  private transportRefresh: () => void = () => undefined;

  private updateTransport(): void {
    this.transportRefresh();
    const playing = this.engine.report?.playing ?? false;
    this.playButton.textContent = playing ? "❚❚ Pause" : "▶ Play";
  }

  async togglePlay(): Promise<void> {
    try {
      await this.engine.ensureStarted();
      this.engine.sendSong(this.model.song);
      this.engine.setPlayback(this.mode, this.model.patternId);
      if (this.engine.report?.playing) this.engine.pause();
      else await this.engine.play();
    } catch (error) {
      this.setStatus(`Cannot start audio: ${error instanceof Error ? error.message : String(error)}`, true);
    }
  }

  stop(): void {
    this.engine.stop();
    this.pianoRoll.setPlayhead(null);
    this.playlist.setPlayhead(null);
  }

  private onReport(report: WorkletReport): void {
    this.playButton.textContent = report.playing ? "❚❚ Pause" : "▶ Play";
    const beats = report.position / 96;
    this.position.textContent = `${Math.floor(beats / 4) + 1}.${Math.floor(beats % 4) + 1}`;
    const shows = report.playing || report.position > 0;
    this.pianoRoll.setPlayhead(shows && this.mode === "PATTERN" ? report.position : null);
    this.playlist.setPlayhead(shows && this.mode === "SONG" ? report.position : null);
    if (this.visible) {
      this.mixerView.setMeters(report.strips, [report.peakLeft, report.peakRight]);
      const bars = this.masterMeter.querySelectorAll<HTMLElement>(".bar");
      bars[0]!.style.height = `${Math.min(report.peakLeft, 1.2) * 100}%`;
      bars[1]!.style.height = `${Math.min(report.peakRight, 1.2) * 100}%`;
    }
    if (report.errors.length > 0 && !this.status.classList.contains("error")) this.setStatus(report.errors.join(" "), false);
  }

  // ---- Rack and step sequencer --------------------------------------------------------------------------------

  private renderRack(): void {
    const model = this.model;
    const song = model.song;
    this.rack.replaceChildren();

    // Patterns
    const patterns = h("select", { class: "pattern-select", title: "The pattern being edited" }) as HTMLSelectElement;
    for (const pattern of song.patterns) {
      const option = h("option", { value: String(pattern.id) }, `${pattern.name} (${pattern.lengthTicks / 384} bar${pattern.lengthTicks > 384 ? "s" : ""})`) as HTMLOptionElement;
      option.selected = pattern.id === model.patternId;
      patterns.append(option);
    }
    patterns.addEventListener("change", () => model.selectPattern(Number(patterns.value)));
    const pattern = model.pattern;
    const bars = h("select", { title: "Pattern length in bars" }) as HTMLSelectElement;
    for (let n = 1; n <= 16; n++) {
      const option = h("option", { value: String(n) }, `${n} bar${n > 1 ? "s" : ""}`) as HTMLOptionElement;
      option.selected = pattern !== undefined && pattern.lengthTicks / 384 === n;
      bars.append(option);
    }
    bars.addEventListener("change", () => this.attempt(() => model.setPatternBars(model.patternId, Number(bars.value))));
    const patternName = h("input", { type: "text", value: pattern?.name ?? "", title: "Pattern name" }) as HTMLInputElement;
    patternName.addEventListener("change", () => this.attempt(() => model.renamePattern(model.patternId, patternName.value)));
    this.rack.append(
      h("div", { class: "rack-head" },
        patterns,
        button("+", "New pattern", () => this.attempt(() => model.addPattern(1))),
        button("⧉", "Duplicate the pattern", () => this.attempt(() => model.duplicatePattern(model.patternId))),
        button("−", "Delete the pattern", () => this.attempt(() => model.removePattern(model.patternId))),
      ),
      h("div", { class: "rack-head" }, patternName, bars),
    );

    // Channels
    const stepCount = pattern ? pattern.lengthTicks / STEP_TICKS : STEPS_PER_BAR;
    for (const channel of song.channels) {
      const selected = channel.id === model.channelId;
      const name = h("button", { class: selected ? "channel-name selected" : "channel-name", title: `Select ${channel.name}` }, channel.name);
      name.addEventListener("click", () => model.selectChannel(channel.id));
      const mute = h("input", { type: "checkbox", title: "Channel on" }) as HTMLInputElement;
      mute.checked = !channel.muted;
      mute.addEventListener("change", () => model.setChannelMuted(channel.id, !mute.checked));
      const steps = h("div", { class: "steps" });
      steps.style.gridTemplateColumns = `repeat(${stepCount}, 18px)`;
      for (let step = 0; step < stepCount; step++) {
        const on = pattern !== undefined && model.isStepOn(pattern.id, channel.id, step);
        const cell = h("button", { class: `step${on ? " on" : ""}${step % 4 === 0 ? " beat" : ""}`, "data-channel": String(channel.id), "data-step": String(step), title: `Step ${step + 1}` });
        cell.addEventListener("click", () => this.onStepClick(channel.id, step));
        steps.append(cell);
      }
      this.rack.append(h("div", { class: "channel-row" }, mute, name, steps));
    }
    this.rack.append(
      h("div", { class: "rack-head" },
        button("+ Synth", "Add a synthesizer channel", () => this.attempt(() => model.addChannel("SYNTH"))),
        button("+ Drums", "Add a drum channel", () => this.attempt(() => model.addChannel("DRUMS"))),
        button("Remove", "Remove the selected channel", () => this.attempt(() => model.removeChannel(model.channelId))),
      ),
    );
  }

  private onStepClick(channelId: number, step: number): void {
    const pattern = this.model.pattern;
    if (!pattern) return;
    const wasOn = this.model.isStepOn(pattern.id, channelId, step);
    this.attempt(() => this.model.toggleStep(pattern.id, channelId, step, STEP_VELOCITY));
    if (!wasOn) {
      const channel = this.model.song.channels.find((c) => c.id === channelId);
      if (channel) {
        void this.engine.noteOn(channelId, channel.stepPitch, PREVIEW_VELOCITY);
        setTimeout(() => this.engine.noteOff(channelId, channel.stepPitch), STEP_PREVIEW_MS);
      }
    }
  }

  // ---- Bottom tabs --------------------------------------------------------------------------------------------

  private tabBar(): HTMLElement {
    const bar = h("div", { class: "bottom-tabs" });
    for (const tab of BOTTOM_TABS) {
      const element = button(TAB_TITLES[tab], `Show the ${TAB_TITLES[tab].toLowerCase()}`, () => {
        this.tab = tab;
        this.renderBottom();
      });
      element.dataset["tab"] = tab;
      bar.append(element);
    }
    return bar;
  }

  private renderBottom(): void {
    for (const element of this.root.querySelectorAll<HTMLElement>(".bottom-tabs button")) {
      element.classList.toggle("active", element.dataset["tab"] === this.tab);
    }
    const view = this.tab === "playlist" ? this.playlist.root : this.tab === "mixer" ? this.mixerView.root : this.instrumentView.root;
    this.bottom.replaceChildren(view);
  }

  /** Shows a bottom tab; tests and tools use it. */
  showTab(tab: BottomTab): void {
    this.tab = tab;
    this.renderBottom();
  }

  // ---- Files --------------------------------------------------------------------------------------------------

  private confirmDiscard(): boolean {
    return !this.model.isDirty || window.confirm("This song has unsaved changes. Discard them?");
  }

  newSong(): void {
    if (!this.confirmDiscard()) return;
    this.fileHandle = null;
    this.stop();
    this.model.load(defaultSong());
  }

  /** Opens song file text. */
  openText(text: string, handle: FileSystemFileHandle | null = null): void {
    try {
      const song = parseSong(text);
      this.fileHandle = handle;
      this.stop();
      this.model.load(song);
      this.setStatus(`Opened ${song.name}: ${song.channels.length} channels, ${song.patterns.length} patterns`);
    } catch (error) {
      this.setStatus(`Cannot open: ${error instanceof Error ? error.message : String(error)}`, true);
    }
  }

  async openBundled(file: string): Promise<void> {
    if (!this.confirmDiscard()) return;
    const response = await fetch(`music/${file}`);
    if (!response.ok) throw new Error(`Could not load music/${file}: HTTP ${response.status}`);
    this.openText(await response.text());
  }

  async openFromDisk(): Promise<void> {
    if (!this.confirmDiscard()) return;
    const picker = (window as unknown as { showOpenFilePicker?: (o: object) => Promise<FileSystemFileHandle[]> }).showOpenFilePicker;
    if (picker) {
      const [handle] = await picker({ types: [{ description: "Forgeroom song", accept: { "application/json": [".song", ".json"] } }] });
      if (handle) this.openText(await (await handle.getFile()).text(), handle);
      return;
    }
    const input = h("input", { type: "file", accept: ".song,.json,application/json" }) as HTMLInputElement;
    input.addEventListener("change", async () => {
      const file = input.files?.[0];
      if (file) this.openText(await file.text());
    });
    input.click();
  }

  async save(): Promise<void> {
    const text = songToJson(this.model.song);
    const picker = (window as unknown as { showSaveFilePicker?: (o: object) => Promise<FileSystemFileHandle> }).showSaveFilePicker;
    try {
      if (picker) {
        this.fileHandle ??= await picker({
          suggestedName: `${fileSafe(this.model.song.name)}.song`,
          types: [{ description: "Forgeroom song", accept: { "application/json": [".song"] } }],
        });
        const writable = await this.fileHandle.createWritable();
        await writable.write(text);
        await writable.close();
      } else {
        download(`${fileSafe(this.model.song.name)}.song`, new Blob([text], { type: "application/json" }));
      }
      this.model.markSaved();
      this.setStatus("Saved");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      this.setStatus(`Cannot save: ${error instanceof Error ? error.message : String(error)}`, true);
    }
  }

  /** Renders the song (or the pattern, in pattern mode) to a WAV file and downloads it. */
  exportWav(): void {
    try {
      const song = this.model.song;
      const mode: PlayMode = this.mode === "SONG" && playlistLengthTicks(song) > 0 ? "SONG" : "PATTERN";
      const recording = renderSong(song, mode, this.model.patternId);
      download(`${fileSafe(song.name)}.wav`, new Blob([writeWav(recording) as BlobPart], { type: "audio/wav" }));
      this.setStatus(`Exported ${(recording.left.length / recording.sampleRate).toFixed(1)} s of ${mode === "SONG" ? "the song" : "the pattern"}`);
    } catch (error) {
      this.setStatus(`Cannot export: ${error instanceof Error ? error.message : String(error)}`, true);
    }
  }

  // ---- Keyboard and messages ----------------------------------------------------------------------------------

  private onKey(event: KeyboardEvent): void {
    if (!this.visible) return;
    const target = event.target as HTMLElement | null;
    const typing = target !== null && (target.tagName === "INPUT" || target.tagName === "SELECT" || target.tagName === "TEXTAREA");
    const ctrl = event.ctrlKey || event.metaKey;
    const key = event.key.toLowerCase();
    if (ctrl && key === "s") {
      event.preventDefault();
      void this.save();
    } else if (!typing && ctrl && key === "z") {
      event.preventDefault();
      if (event.shiftKey) this.model.redo();
      else this.model.undo();
    } else if (!typing && ctrl && key === "y") {
      event.preventDefault();
      this.model.redo();
    } else if (!typing && !ctrl && event.key === " ") {
      event.preventDefault();
      void this.togglePlay();
    }
  }

  /** Runs a model edit; a refused one (a full rack, the last pattern) is reported, not thrown into the page. */
  private attempt(run: () => void): void {
    try {
      run();
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : String(error), true);
    }
  }

  setStatus(message: string, isError = false): void {
    this.status.textContent = message;
    this.status.classList.toggle("error", isError);
  }
}

function fileSafe(name: string): string {
  return name.replace(/[^\w-]+/g, "-").replace(/^-+|-+$/g, "").toLowerCase() || "song";
}

function download(name: string, blob: Blob): void {
  const link = h("a", { download: name }) as HTMLAnchorElement;
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
}
