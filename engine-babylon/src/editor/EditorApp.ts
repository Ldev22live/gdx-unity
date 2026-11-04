import {
  Color3,
  GizmoManager,
  HighlightLayer,
  Matrix,
  Quaternion,
  Vector3,
  type AbstractMesh,
  type ArcRotateCamera,
} from "@babylonjs/core";
import { ScenePlayer } from "../runtime/ScenePlayer";
import type { BuiltScene } from "../runtime/SceneBuilder";
import { parseSceneFile, type SceneFile, type TransformData } from "../scene/types";
import { byId, h } from "./dom";
import { EditorModel, emptyScene } from "./EditorModel";
import { HierarchyPanel } from "./hierarchy";
import { InspectorPanel } from "./inspector";
import { ENTITY_PRESETS } from "./presets";
import { SculptMode } from "./sculptMode";

export type Tool = "select" | "move" | "rotate" | "scale";

/** Pointer movement in pixels beyond which a press is a camera drag, not a click that selects. */
const CLICK_SLOP_PX = 4;
const HIGHLIGHT_COLOR = new Color3(1, 0.75, 0.2);
/** Each press of the brush-size key scales the radius by this factor, and each strength key adds or removes this much. */
const BRUSH_SIZE_STEP = 1.2;
const BRUSH_STRENGTH_STEP = 0.05;
/** How far past the scene's size the orbit camera sits when framing everything. */
const FRAME_MARGIN = 1.3;

interface CameraState {
  alpha: number;
  beta: number;
  radius: number;
  target: Vector3;
}

interface SampleEntry {
  file: string;
  name: string;
}

/**
 * The web editor: wires the model (scene data and undo), the panels and the Babylon viewport together. The viewport shows
 * a scene built from the model's data; edits either move a node (transforms) or rebuild the scene (everything else).
 * Play runs a copy of the scene, so playing never changes what is being edited.
 */
export class EditorApp {
  readonly model: EditorModel;
  /** The player that draws the viewport; public so tests and tools can reach the Babylon scene. */
  readonly player: ScenePlayer;
  private readonly hierarchy: HierarchyPanel;
  private readonly inspector: InspectorPanel;
  private readonly status = byId("status");
  /** Brush painting on the selected sculpt; public so tests and tools can drive it. */
  readonly sculpt: SculptMode;
  private tool: Tool = "move";
  private gizmos: GizmoManager | null = null;
  private highlight: HighlightLayer | null = null;
  private readonly instrumented = new WeakSet<object>();
  private dragBefore: TransformData | null = null;
  private cameraState: CameraState | null = null;
  private fileHandle: FileSystemFileHandle | null = null;
  private playing = false;

  constructor(player: ScenePlayer, initial: SceneFile) {
    this.player = player;
    this.model = new EditorModel(initial);
    const onError = (message: string) => this.setStatus(message, true);
    this.hierarchy = new HierarchyPanel(byId("hierarchy"), this.model, onError);
    this.inspector = new InspectorPanel(byId("inspector"), this.model, onError);
    this.sculpt = new SculptMode(this.model, (message, isError = false) => this.setStatus(message, isError));
    this.sculpt.onChange = () => {
      this.attachSelection();
      this.updateToolbar();
    };

    player.useGameCamera = false;
    player.onSceneBuilt = (built) => this.onSceneBuilt(built);
    this.model.addListener((kind) => this.onModelChanged(kind));

    this.buildToolbar();
    this.bindKeyboard();
    window.addEventListener("beforeunload", (event) => {
      if (this.model.isDirty) event.preventDefault();
    });
    this.rebuild(true);
    this.updateToolbar();
  }

  // ---- Model -> view ------------------------------------------------------------------------------------------

  private onModelChanged(kind: string): void {
    switch (kind) {
      case "transform": {
        const id = this.model.selection;
        if (id !== null) this.applyTransformToNode(id);
        this.inspector.refreshTransform();
        break;
      }
      case "scene":
        this.rebuild(false);
        this.hierarchy.render();
        this.inspector.render();
        break;
      case "replaced":
        this.rebuild(false);
        this.hierarchy.render();
        this.inspector.render();
        break;
      case "selection":
        this.hierarchy.render();
        this.inspector.render();
        this.sculpt.selectionChanged();
        this.attachSelection();
        break;
    }
    this.updateToolbar();
  }

  /** Rebuilds the viewport scene from the model, keeping the camera where it was. */
  private rebuild(frame: boolean): void {
    this.rememberCamera();
    this.gizmos?.dispose();
    this.gizmos = null;
    this.highlight = null;
    this.sculpt.detach();
    this.player.useGameCamera = this.playing;
    this.player.load(structuredClone(this.model.file));
    if (frame) this.frameAll();
    this.setStatus(`${this.model.file.name}: ${this.model.file.entities.length} entities`);
  }

  private rememberCamera(): void {
    const built = this.player.getBuilt();
    if (!built || this.playing) return;
    const camera = built.viewerCamera;
    this.cameraState = { alpha: camera.alpha, beta: camera.beta, radius: camera.radius, target: camera.target.clone() };
  }

  private onSceneBuilt(built: BuiltScene): void {
    if (this.playing) return;
    if (this.cameraState) this.restoreCamera(built.viewerCamera, this.cameraState);
    this.gizmos = new GizmoManager(built.scene);
    this.gizmos.usePointerToAttachGizmos = false;
    this.highlight = new HighlightLayer("selection", built.scene);
    this.sculpt.attach(built);
    this.applyTool();
    this.attachSelection();
    this.installPicking(built);
  }

  private restoreCamera(camera: ArcRotateCamera, state: CameraState): void {
    // Setting the target makes the camera recompute its angles from where it is, so it goes first
    camera.target = state.target.clone();
    camera.alpha = state.alpha;
    camera.beta = state.beta;
    camera.radius = state.radius;
  }

  /** Moves the orbit camera so everything in the scene is in view; flat 2D scenes are seen head-on. */
  frameAll(): void {
    const built = this.player.getBuilt();
    if (!built) return;
    const min = new Vector3(Infinity, Infinity, Infinity);
    const max = new Vector3(-Infinity, -Infinity, -Infinity);
    let flat = true;
    for (const entity of built.game.entities) {
      for (const mesh of entity.meshes) {
        mesh.computeWorldMatrix(true);
        const box = mesh.getBoundingInfo().boundingBox;
        min.minimizeInPlace(box.minimumWorld);
        max.maximizeInPlace(box.maximumWorld);
        if (mesh.metadata && entity.getComponent("MeshPrimitiveComponent")) flat = false;
      }
    }
    if (!Number.isFinite(min.x)) {
      for (const entity of built.game.entities) {
        const p = entity.getWorldPosition();
        min.minimizeInPlace(p);
        max.maximizeInPlace(p);
      }
    }
    const camera = built.viewerCamera;
    if (!Number.isFinite(min.x)) {
      camera.target = Vector3.Zero();
      camera.radius = 500;
      return;
    }
    const size = Math.max(Vector3.Distance(min, max), 100);
    camera.target = min.add(max).scale(0.5);
    camera.radius = size * FRAME_MARGIN;
    // Seen from +Z, like the game cameras; flat scenes straight on, 3D scenes from above and in front
    camera.alpha = Math.PI / 2;
    camera.beta = flat ? Math.PI / 2 : 1.1;
    camera.upperBetaLimit = Math.PI;
    camera.maxZ = Math.max(camera.maxZ, size * 40);
    this.cameraState = { alpha: camera.alpha, beta: camera.beta, radius: camera.radius, target: camera.target.clone() };
  }

  // ---- Selection, picking, gizmos -----------------------------------------------------------------------------

  private installPicking(built: BuiltScene): void {
    let down: { x: number; y: number } | null = null;
    built.scene.onPointerObservable.add((info) => {
      const event = info.event as PointerEvent;
      if (info.type === 1 /* POINTERDOWN */) {
        down = event.button === 0 ? { x: event.clientX, y: event.clientY } : null;
      } else if (info.type === 2 /* POINTERUP */ && down !== null) {
        const moved = Math.hypot(event.clientX - down.x, event.clientY - down.y);
        down = null;
        if (moved > CLICK_SLOP_PX || this.dragBefore !== null || this.sculpt.isEnabled) return;
        const pick = built.scene.pick(built.scene.pointerX, built.scene.pointerY, (mesh) => mesh.metadata?.entityId !== undefined);
        const id = pick?.pickedMesh?.metadata?.entityId as string | undefined;
        this.model.select(id ?? null);
      }
    });
  }

  private attachSelection(): void {
    const built = this.player.getBuilt();
    if (!built || !this.gizmos || !this.highlight) return;
    this.highlight.removeAllMeshes();
    const id = this.model.selection;
    const entity = id === null ? undefined : built.game.getById(id);
    if (!entity) {
      this.gizmos.attachToNode(null);
      return;
    }
    if (this.sculpt.isEnabled) {
      // The glow and the gizmo would hide the surface being painted
      this.gizmos.attachToNode(null);
      return;
    }
    for (const mesh of entity.meshes as AbstractMesh[]) this.highlight.addMesh(mesh as never, HIGHLIGHT_COLOR);
    this.gizmos.attachToNode(this.tool === "select" ? null : entity.node);
  }

  setTool(tool: Tool): void {
    this.sculpt.exit();
    this.tool = tool;
    this.applyTool();
    this.attachSelection();
    this.updateToolbar();
  }

  private applyTool(): void {
    const gizmos = this.gizmos;
    if (!gizmos) return;
    gizmos.positionGizmoEnabled = this.tool === "move";
    gizmos.rotationGizmoEnabled = this.tool === "rotate";
    gizmos.scaleGizmoEnabled = this.tool === "scale";
    // Each gizmo reports the start and end of a drag; one drag becomes one undo step
    const active = [gizmos.gizmos.positionGizmo, gizmos.gizmos.rotationGizmo, gizmos.gizmos.scaleGizmo];
    for (const gizmo of active) {
      if (!gizmo || this.instrumented.has(gizmo)) continue;
      this.instrumented.add(gizmo);
      gizmo.onDragStartObservable.add(() => (this.dragBefore = this.currentTransform()));
      gizmo.onDragEndObservable.add(() => this.commitDrag());
    }
  }

  private currentTransform(): TransformData | null {
    const id = this.model.selection;
    const entity = id === null ? undefined : this.player.getBuilt()?.game.getById(id);
    if (!entity) return null;
    const node = entity.node;
    const q = node.rotationQuaternion ?? Quaternion.Identity();
    return {
      position: { x: node.position.x, y: node.position.y, z: node.position.z },
      rotation: { x: q.x, y: q.y, z: q.z, w: q.w },
      scale: { x: node.scaling.x, y: node.scaling.y, z: node.scaling.z },
    };
  }

  private commitDrag(): void {
    const after = this.currentTransform();
    const before = this.dragBefore;
    const id = this.model.selection;
    // The press that ends a gizmo drag also reaches the picking handler; clear the flag only after it has run
    setTimeout(() => (this.dragBefore = null), 0);
    if (!after || !before || id === null || JSON.stringify(after) === JSON.stringify(before)) return;
    const label = this.tool === "rotate" ? "Rotate" : this.tool === "scale" ? "Scale" : "Move";
    this.model.setTransform(id, after, label);
  }

  private applyTransformToNode(id: string): void {
    const entity = this.player.getBuilt()?.game.getById(id);
    if (!entity) return;
    const t = this.model.require(id).transform;
    entity.node.position.set(t.position.x, t.position.y, t.position.z);
    entity.node.rotationQuaternion = new Quaternion(t.rotation.x, t.rotation.y, t.rotation.z, t.rotation.w);
    entity.node.scaling.set(t.scale.x, t.scale.y, t.scale.z);
  }

  // ---- Commands -----------------------------------------------------------------------------------------------

  addEntity(preset: string): void {
    this.model.addEntity(preset, null);
  }

  deleteSelected(): void {
    const id = this.model.selection;
    if (id !== null) this.model.deleteEntity(id);
  }

  duplicateSelected(): void {
    const id = this.model.selection;
    if (id !== null) this.model.duplicateEntity(id);
  }

  /** Where an entity's origin appears in the view, in canvas pixels, or null when it is not in the scene. Used by tests. */
  screenPositionOf(id: string): { x: number; y: number } | null {
    const built = this.player.getBuilt();
    const entity = built?.game.getById(id);
    const camera = built?.scene.activeCamera;
    if (!built || !entity || !camera) return null;
    const engine = built.scene.getEngine();
    const viewport = camera.viewport.toGlobal(engine.getRenderWidth(), engine.getRenderHeight());
    const point = Vector3.Project(entity.getWorldPosition(), Matrix.Identity(), built.scene.getTransformMatrix(), viewport);
    // Babylon's pixels count the hardware scaling; the page sees CSS pixels
    const scale = engine.getHardwareScalingLevel();
    return { x: point.x * scale, y: point.y * scale };
  }

  focusSelected(): void {
    const id = this.model.selection;
    const built = this.player.getBuilt();
    const entity = id === null ? undefined : built?.game.getById(id);
    if (!built || !entity) return;
    built.viewerCamera.target = entity.getWorldPosition();
  }

  togglePlay(): void {
    if (this.playing) this.stop();
    else this.play();
  }

  private play(): void {
    this.sculpt.exit();
    this.rememberCamera();
    this.gizmos?.dispose();
    this.gizmos = null;
    this.playing = true;
    document.body.classList.add("playing");
    this.player.useGameCamera = true;
    // Play works on a copy of the scene: the model is never touched
    this.player.load(structuredClone(this.model.file));
    this.player.play();
    this.setStatus("Playing. Click the view and use the game's keys.");
    this.updateToolbar();
  }

  private stop(): void {
    this.player.stop();
    this.playing = false;
    document.body.classList.remove("playing");
    this.rebuild(false);
    this.updateToolbar();
  }

  // ---- Files --------------------------------------------------------------------------------------------------

  private confirmDiscard(): boolean {
    return !this.model.isDirty || window.confirm("This scene has unsaved changes. Discard them?");
  }

  newScene(): void {
    if (!this.confirmDiscard()) return;
    this.fileHandle = null;
    this.model.load(emptyScene());
    this.rebuild(true);
  }

  openText(text: string, handle: FileSystemFileHandle | null = null): void {
    let file: SceneFile;
    try {
      file = parseSceneFile(text);
    } catch (error) {
      this.setStatus(`Cannot open: ${error instanceof Error ? error.message : String(error)}`, true);
      return;
    }
    this.fileHandle = handle;
    this.model.load(file);
    this.rebuild(true);
  }

  async openSample(file: string): Promise<void> {
    if (!this.confirmDiscard()) return;
    const response = await fetch(`samples/${file}`);
    if (!response.ok) throw new Error(`Could not load samples/${file}: HTTP ${response.status}`);
    this.openText(await response.text());
  }

  async openFromDisk(): Promise<void> {
    if (!this.confirmDiscard()) return;
    const picker = (window as unknown as { showOpenFilePicker?: (o: object) => Promise<FileSystemFileHandle[]> }).showOpenFilePicker;
    if (picker) {
      const [handle] = await picker({ types: [{ description: "Forgeroom scene", accept: { "application/json": [".json"] } }] });
      if (!handle) return;
      this.openText(await (await handle.getFile()).text(), handle);
      return;
    }
    const input = h("input", { type: "file", accept: ".json,application/json" }) as HTMLInputElement;
    input.addEventListener("change", async () => {
      const file = input.files?.[0];
      if (file) this.openText(await file.text());
    });
    input.click();
  }

  /** Saves to the file the scene came from when the browser allows it, otherwise downloads it. */
  async save(): Promise<void> {
    const text = this.model.serialize();
    const picker = (window as unknown as { showSaveFilePicker?: (o: object) => Promise<FileSystemFileHandle> }).showSaveFilePicker;
    try {
      if (picker) {
        this.fileHandle ??= await picker({
          suggestedName: `${this.model.file.name.replace(/[^\w-]+/g, "-").toLowerCase() || "scene"}.json`,
          types: [{ description: "Forgeroom scene", accept: { "application/json": [".json"] } }],
        });
        const writable = await this.fileHandle.createWritable();
        await writable.write(text);
        await writable.close();
      } else {
        const link = h("a", { download: `${this.model.file.name || "scene"}.json` }) as HTMLAnchorElement;
        link.href = URL.createObjectURL(new Blob([text], { type: "application/json" }));
        link.click();
        URL.revokeObjectURL(link.href);
      }
      this.model.markSaved();
      this.setStatus("Saved");
      this.updateToolbar();
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      this.setStatus(`Cannot save: ${error instanceof Error ? error.message : String(error)}`, true);
    }
  }

  // ---- Toolbar and keyboard -----------------------------------------------------------------------------------

  private buildToolbar(): void {
    const click = (id: string, run: () => void) => byId(id).addEventListener("click", run);
    click("new", () => this.newScene());
    click("open", () => void this.openFromDisk().catch((e) => this.setStatus(String(e), true)));
    click("save", () => void this.save());
    click("undo", () => this.model.undo());
    click("redo", () => this.model.redo());
    click("play", () => this.togglePlay());
    click("delete", () => this.deleteSelected());
    click("duplicate", () => this.duplicateSelected());
    click("frame", () => this.frameAll());
    click("tool-sculpt", () => this.sculpt.toggle());
    for (const tool of ["select", "move", "rotate", "scale"] as const) click(`tool-${tool}`, () => this.setTool(tool));

    const add = byId<HTMLSelectElement>("add");
    for (const [key, preset] of Object.entries(ENTITY_PRESETS)) add.append(h("option", { value: key }, preset.label));
    add.addEventListener("change", () => {
      if (add.value) this.addEntity(add.value);
      add.value = "";
    });

    const samples = byId<HTMLSelectElement>("samples");
    samples.addEventListener("change", () => {
      const file = samples.value;
      samples.value = "";
      if (file) this.openSample(file).catch((e) => this.setStatus(String(e), true));
    });
    fetch("samples/index.json")
      .then((response) => (response.ok ? (response.json() as Promise<SampleEntry[]>) : []))
      .then((entries) => {
        for (const entry of entries) samples.append(h("option", { value: entry.file }, entry.name));
      })
      .catch(() => undefined);
  }

  private updateToolbar(): void {
    byId<HTMLButtonElement>("undo").disabled = !this.model.canUndo || this.playing;
    byId<HTMLButtonElement>("redo").disabled = !this.model.canRedo || this.playing;
    byId<HTMLButtonElement>("undo").title = this.model.undoLabel ? `Undo ${this.model.undoLabel} (Ctrl+Z)` : "Undo (Ctrl+Z)";
    byId<HTMLButtonElement>("redo").title = this.model.redoLabel ? `Redo ${this.model.redoLabel} (Ctrl+Y)` : "Redo (Ctrl+Y)";
    byId("play").textContent = this.playing ? "■ Stop" : "▶ Play";
    byId("dirty").textContent = this.model.isDirty ? "●" : "";
    byId("title").textContent = this.model.file.name;
    const hasSelection = this.model.selection !== null;
    byId<HTMLButtonElement>("delete").disabled = !hasSelection;
    byId<HTMLButtonElement>("duplicate").disabled = !hasSelection;
    for (const tool of ["select", "move", "rotate", "scale"] as const) {
      byId(`tool-${tool}`).classList.toggle("active", this.tool === tool);
    }
  }

  private bindKeyboard(): void {
    window.addEventListener("keydown", (event) => {
      const target = event.target as HTMLElement | null;
      const typing = target !== null && (target.tagName === "INPUT" || target.tagName === "SELECT" || target.tagName === "TEXTAREA");
      const ctrl = event.ctrlKey || event.metaKey;
      // The Music tab has its own keys (and its own Save)
      if (document.body.classList.contains("music")) return;
      if (ctrl && event.key.toLowerCase() === "s") {
        event.preventDefault();
        void this.save();
        return;
      }
      if (this.playing || typing) return;
      if (event.key === "Escape" && this.sculpt.isEnabled) {
        this.sculpt.exit();
        return;
      }
      if (this.sculpt.isEnabled && !ctrl) {
        // The desktop's brush keys: ] and [ for size, = and - for strength
        const keys: Record<string, [number, number]> = { "]": [BRUSH_SIZE_STEP, 0], "[": [1 / BRUSH_SIZE_STEP, 0], "=": [1, BRUSH_STRENGTH_STEP], "-": [1, -BRUSH_STRENGTH_STEP] };
        const adjust = keys[event.key];
        if (adjust) {
          this.sculpt.adjustBrush(adjust[0], adjust[1]);
          return;
        }
      }
      if (!ctrl && event.key.toLowerCase() === "b") {
        this.sculpt.toggle();
        return;
      }
      if (ctrl && event.key.toLowerCase() === "z") {
        event.preventDefault();
        if (event.shiftKey) this.model.redo();
        else this.model.undo();
      } else if (ctrl && event.key.toLowerCase() === "y") {
        event.preventDefault();
        this.model.redo();
      } else if (ctrl && event.key.toLowerCase() === "d") {
        event.preventDefault();
        this.duplicateSelected();
      } else if (!ctrl && event.key === "Delete") {
        this.deleteSelected();
      } else if (!ctrl && event.key.toLowerCase() === "q") this.setTool("select");
      else if (!ctrl && event.key.toLowerCase() === "w") this.setTool("move");
      else if (!ctrl && event.key.toLowerCase() === "e") this.setTool("rotate");
      else if (!ctrl && event.key.toLowerCase() === "r") this.setTool("scale");
      else if (!ctrl && event.key.toLowerCase() === "f") this.focusSelected();
    });
  }

  private setStatus(message: string, isError = false): void {
    this.status.textContent = message;
    this.status.classList.toggle("error", isError);
  }
}
