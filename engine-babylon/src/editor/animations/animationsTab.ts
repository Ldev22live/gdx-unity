import { Engine, Vector3 } from "@babylonjs/core";
import { applyClip, loadClip, resolveTarget, type Clip } from "../../runtime/Animation";
import type { GameEntity } from "../../runtime/GameEntity";
import { buildScene, type BuiltScene } from "../../runtime/SceneBuilder";
import { parseSceneFile, type SceneFile } from "../../scene/types";
import { h } from "../dom";
import type { EditorModel } from "../EditorModel";

/** One line of `animations/index.json`, written by `npm run sync-samples`. */
export interface ClipEntry {
  path: string;
  name: string;
  /** Folder under `animations/`: "humanoid" for the shipped Idle, Walk, Run, Jump and Fall. */
  group: string;
}

/** The scene whose "Idle Hero" is the preview figure: the humanoid skeleton every humanoid clip is keyed to. */
export const FIGURE_SCENE = "samples/humanoid-animations-3d.json";
const FIGURE_NAME = "Idle Hero";
/** Where the preview camera looks and how far back it sits, in scene units (the figure is about 200 tall). */
const FIGURE_FOCUS = new Vector3(0, 100, 0);
const CAMERA_DISTANCE = 480;
const SPEEDS = [0.25, 0.5, 1, 2];

/**
 * Reduces the humanoid sample to one figure: the lights, the floor and the camera, plus the Idle hero (moved to the
 * origin, without its Animator, so only the tab moves its bones).
 */
export function figureScene(sample: SceneFile): SceneFile {
  const hero = sample.entities.find((entity) => entity.name === FIGURE_NAME);
  if (!hero) throw new Error(`${FIGURE_SCENE} has no entity named '${FIGURE_NAME}'`);
  const keep = new Set<string>([hero.id]);
  for (let grew = true; grew; ) {
    grew = false;
    for (const entity of sample.entities) {
      if (entity.parentId !== null && keep.has(entity.parentId) && !keep.has(entity.id)) {
        keep.add(entity.id);
        grew = true;
      }
    }
  }
  const isHero = (name: string): boolean => name.endsWith(" Hero");
  const entities = structuredClone(sample.entities).filter((entity) => keep.has(entity.id) || (entity.parentId === null && !isHero(entity.name)));
  for (const entity of entities) {
    if (entity.id === hero.id) {
      entity.transform.position.z = 0;
      entity.components = entity.components.filter((component) => component.type !== "AnimatorComponent");
    }
  }
  return { ...sample, entities };
}

/**
 * The Animations tab (TD-044): the clip library (the humanoid Idle, Walk, Run, Jump and Fall, then every other clip), a
 * 3D preview of the clip on the humanoid figure with play, scrub, loop and speed, the clip's tracks, and a button that
 * makes the selected entity play the clip. It has its own small view, so it works whatever scene is open.
 */
export class AnimationsTab {
  readonly root: HTMLElement;
  private readonly canvas = h("canvas", { tabindex: "0" }) as HTMLCanvasElement;
  private readonly list = h("div", { class: "clips" });
  private readonly tracks = h("div", { class: "tracks" });
  private readonly message = h("div", { class: "message" });
  private readonly play = h("button", { class: "primary", title: "Play or pause (Space)" }, "Pause") as HTMLButtonElement;
  private readonly scrub = h("input", { type: "range", min: "0", max: "1", step: "0.001", value: "0" }) as HTMLInputElement;
  private readonly clock = h("span", { class: "clock" }, "0.00 / 0.00 s");
  private readonly loop = h("input", { type: "checkbox" }) as HTMLInputElement;
  private readonly speed = h("select") as HTMLSelectElement;
  private readonly title = h("div", { class: "clip-title" }, "Pick a clip");
  private readonly assign = h("button", { title: "Make the entity selected in the Viewport tab play this clip" }, "Use on selected entity") as HTMLButtonElement;

  private entries: ClipEntry[] = [];
  private readonly clips = new Map<string, Clip>();
  private current: ClipEntry | null = null;
  private clip: Clip | null = null;
  private time = 0;
  private playing = true;
  private visible = false;
  private engine: Engine | null = null;
  private built: BuiltScene | null = null;
  private figure: GameEntity | null = null;
  private starting: Promise<void> | null = null;

  constructor(
    host: HTMLElement,
    private readonly model: EditorModel,
    private readonly setStatus: (message: string, isError?: boolean) => void,
  ) {
    this.loop.checked = true;
    for (const value of SPEEDS) this.speed.append(h("option", { value: String(value) }, `${value}x`));
    this.speed.value = "1";
    this.root = h(
      "section",
      { id: "animations" },
      h("aside", { class: "library" }, h("h2", {}, "Clip Library"), this.list),
      h("div", { class: "preview" }, this.canvas, this.message),
      h("aside", { class: "inspect" }, h("h2", {}, "Clip"), this.title, this.assign, h("h2", {}, "Tracks"), this.tracks),
      h(
        "div",
        { class: "transport" },
        this.play,
        this.scrub,
        this.clock,
        h("label", { class: "inline" }, this.loop, "Loop"),
        h("label", { class: "inline" }, "Speed", this.speed),
      ),
    );
    this.root.hidden = true;
    host.append(this.root);

    this.play.addEventListener("click", () => this.setPlaying(!this.playing));
    this.scrub.addEventListener("input", () => {
      if (!this.clip) return;
      this.setPlaying(false);
      this.time = Number(this.scrub.value) * this.clip.duration;
      this.pose();
    });
    this.assign.addEventListener("click", () => this.assignToSelection());
    this.canvas.addEventListener("keydown", (event) => {
      if (event.code !== "Space") return;
      event.preventDefault();
      this.setPlaying(!this.playing);
    });
  }

  /** Shows or hides the tab; the 3D view is made the first time it is shown and only renders while shown. */
  setVisible(visible: boolean): void {
    this.visible = visible;
    this.root.hidden = !visible;
    if (!visible) {
      this.engine?.stopRenderLoop();
      return;
    }
    this.starting ??= this.start();
    void this.starting.then(() => {
      if (!this.visible || !this.engine) return;
      this.engine.resize();
      this.engine.stopRenderLoop();
      this.engine.runRenderLoop(() => this.frame());
    });
  }

  private async start(): Promise<void> {
    try {
      const [sampleText, indexText] = await Promise.all([fetchText(FIGURE_SCENE), fetchText("animations/index.json")]);
      this.entries = JSON.parse(indexText) as ClipEntry[];
      this.renderList();
      this.engine = new Engine(this.canvas, true, { preserveDrawingBuffer: false, stencil: true });
      this.built = buildScene(this.engine, figureScene(parseSceneFile(sampleText)));
      this.figure = this.built.game.findByName(FIGURE_NAME) ?? null;
      const camera = this.built.viewerCamera;
      camera.setTarget(FIGURE_FOCUS);
      camera.alpha = 0;
      camera.beta = Math.PI / 2;
      camera.radius = CAMERA_DISTANCE;
      camera.attachControl(this.canvas, true);
      this.built.scene.activeCamera = camera;
      window.addEventListener("resize", () => this.engine?.resize());
      const first = this.entries.find((entry) => entry.group === "humanoid");
      if (first) await this.select(first);
    } catch (error) {
      this.message.textContent = `Could not start the preview: ${error instanceof Error ? error.message : String(error)}. Run \`npm run sync-samples\`.`;
      this.message.classList.add("error");
    }
  }

  private renderList(): void {
    this.list.replaceChildren();
    const groups = new Map<string, ClipEntry[]>();
    for (const entry of this.entries) groups.set(entry.group, [...(groups.get(entry.group) ?? []), entry]);
    const ordered = [...groups.keys()].sort((a, b) => (a === "humanoid" ? -1 : b === "humanoid" ? 1 : a.localeCompare(b)));
    for (const group of ordered) {
      this.list.append(h("div", { class: "group" }, group === "" ? "Other clips" : group === "humanoid" ? "Humanoid" : group));
      for (const entry of groups.get(group)!) {
        const button = h("button", { class: "clip", "data-path": entry.path }, entry.name) as HTMLButtonElement;
        button.addEventListener("click", () => void this.select(entry));
        this.list.append(button);
      }
    }
  }

  /** Makes {@code entry} the previewed clip and starts it. */
  async select(entry: ClipEntry): Promise<void> {
    try {
      let clip = this.clips.get(entry.path);
      if (!clip) {
        clip = await loadClip(entry.path);
        this.clips.set(entry.path, clip);
      }
      this.current = entry;
      this.clip = clip;
      this.time = 0;
      this.setPlaying(true);
      this.showClip(entry, clip);
      this.pose();
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : String(error), true);
    }
  }

  private showClip(entry: ClipEntry, clip: Clip): void {
    for (const button of this.list.querySelectorAll("button.clip")) {
      button.classList.toggle("active", button.getAttribute("data-path") === entry.path);
    }
    this.title.textContent = `${entry.name} · ${clip.duration.toFixed(2)} s · ${clip.tracks.length} tracks`;
    let missing = 0;
    this.tracks.replaceChildren();
    for (const track of clip.tracks) {
      const found = this.figure !== null && resolveTarget(this.figure, track.target) !== null;
      if (!found) missing++;
      const bone = track.target === "" ? "(whole entity)" : track.target.split("/").pop()!;
      this.tracks.append(
        h("div", { class: found ? "track" : "track missing", title: `${track.target || "(entity)"} · ${track.property} · ${track.keys.length} keys` }, `${bone} · ${track.property.replace("transform.", "")}`),
      );
    }
    this.message.classList.remove("error");
    this.message.textContent =
      missing === 0 ? "" : `${missing} of ${clip.tracks.length} tracks animate bones the preview figure does not have; they play on entities that do.`;
  }

  private setPlaying(playing: boolean): void {
    this.playing = playing;
    this.play.textContent = playing ? "Pause" : "Play";
  }

  /** Applies the clip at the current time to the figure and updates the scrubber. */
  private pose(): void {
    if (!this.clip || !this.figure) return;
    applyClip(this.clip, this.time, this.figure);
    this.scrub.value = String(this.clip.duration > 0 ? this.time / this.clip.duration : 0);
    this.clock.textContent = `${this.time.toFixed(2)} / ${this.clip.duration.toFixed(2)} s`;
  }

  private frame(): void {
    if (this.playing && this.clip && this.engine) {
      const next = this.time + (this.engine.getDeltaTime() / 1000) * Number(this.speed.value);
      if (next >= this.clip.duration && !this.loop.checked) {
        this.time = this.clip.duration;
        this.setPlaying(false);
      } else {
        this.time = next % this.clip.duration;
      }
      this.pose();
    }
    this.built?.scene.render();
  }

  /** Makes the selected entity play the current clip, as one undo step: its Animator gets the clip, or it gets an Animator. */
  assignToSelection(): void {
    const id = this.model.selection;
    if (!this.current) {
      this.setStatus("Pick a clip first", true);
      return;
    }
    if (id === null) {
      this.setStatus("Select an entity in the Viewport tab first", true);
      return;
    }
    const clipPath = this.current.path;
    this.model.edit("Assign animation", "scene", (file) => {
      const entity = file.entities.find((item) => item.id === id);
      if (!entity) throw new Error(`Entity ${id} is not in the scene`);
      const animator = entity.components.find((component) => component.type === "AnimatorComponent");
      if (animator) animator.data["clip"] = clipPath;
      else entity.components.push({ type: "AnimatorComponent", enabled: true, data: { clip: clipPath, playOnStart: true, wrap: "LOOP", speed: 1 } });
    });
    this.setStatus(`${this.model.require(id).name} plays ${this.current.name} when the scene plays`);
  }
}

async function fetchText(path: string): Promise<string> {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.text();
}
