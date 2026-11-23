import {
  Color3,
  Matrix,
  Mesh,
  MeshBuilder,
  Quaternion,
  StandardMaterial,
  Vector3,
  type ArcRotateCamera,
  type PickingInfo,
} from "@babylonjs/core";
import { updateSculptMesh, type BuiltScene, type SculptBinding } from "../runtime/SceneBuilder";
import { encodeMesh } from "../sculpt/SculptMesh";
import { SCULPT_EXTENSION, readSculptFile, subdivide, writeSculptFile } from "../sculpt/sculptFile";
import {
  BRUSH_TYPES,
  FALLOFFS,
  MAX_SPACING,
  MIN_SPACING,
  SculptStroke,
  defaultBrush,
  type BrushSettings,
  type BrushType,
  type Falloff,
  type Symmetry,
} from "../sculpt/SculptStroke";
import { byId, h } from "./dom";
import type { EditorModel } from "./EditorModel";
import { button } from "./music/ui";

/** The brush starts this fraction of the sculpt's radius wide. */
const START_RADIUS_FRACTION = 0.25;
/** The radius slider goes from this fraction of the sculpt's radius ... */
const MIN_RADIUS_FRACTION = 0.02;
/** ... up to this many times it. */
const MAX_RADIUS_FRACTION = 1.5;
/** The strength slider's lowest value; a brush at zero would do nothing. */
const MIN_STRENGTH = 0.05;
const CURSOR_COLOR = new Color3(1, 0.75, 0.2);
const WIRE_COLOR = new Color3(0.08, 0.1, 0.14);
/** Polygon offset that draws the wire in front of the surface it lies on, so it does not flicker. */
const WIRE_Z_OFFSET = -2;
/** Mouse buttons that still orbit the camera while sculpting: middle and right (the left one paints). */
const ORBIT_BUTTONS = [1, 2];
const LABELS: Record<BrushType, string> = {
  DRAW: "Draw",
  INFLATE: "Inflate",
  SMOOTH: "Smooth",
  FLATTEN: "Flatten",
  PINCH: "Pinch",
  GRAB: "Grab",
};

/**
 * Sculpt mode of the web editor: while it is on, the left mouse button paints on the selected sculpt instead of selecting
 * and the brush panel is shown. One stroke becomes one undo step. Brushes come from the sculpt module (a port of the
 * desktop engine's), so a stroke here moves the surface the way it does in the desktop editor.
 */
export class SculptMode {
  private enabled = false;
  private built: BuiltScene | null = null;
  private binding: SculptBinding | null = null;
  private stroke: SculptStroke | null = null;
  private brushType: BrushType = "DRAW";
  private brush: BrushSettings = defaultBrush(1);
  private readonly symmetry: Symmetry = { x: false, y: false, z: false };
  private wireframe = false;
  private wire: Mesh | null = null;
  private cursor: Mesh | null = null;
  private grabPlane: { point: Vector3; normal: Vector3 } | null = null;
  private radiusRange = { min: 0.1, max: 10 };
  private readonly root = byId("sculpt-bar");
  private readonly radiusInput = h("input", { type: "range", step: "any" }) as HTMLInputElement;
  private readonly strengthInput = h("input", { type: "range", min: "0.05", max: "1", step: "0.05" }) as HTMLInputElement;

  /** Called when sculpt mode turns on or off, so the editor can update its toolbar and gizmos. */
  onChange: () => void = () => undefined;

  constructor(
    private readonly model: EditorModel,
    private readonly onMessage: (message: string, isError?: boolean) => void,
  ) {
    this.buildPanel();
  }

  get isEnabled(): boolean {
    return this.enabled;
  }

  /** The brush the next stroke uses; exposed for tests and tools. */
  get settings(): Readonly<BrushSettings> {
    return this.brush;
  }

  get currentBrush(): BrushType {
    return this.brushType;
  }

  get isWireframe(): boolean {
    return this.wireframe;
  }

  /** True while a stroke is being painted. */
  get isStroking(): boolean {
    return this.stroke?.isActive ?? false;
  }

  // ---- Mode ---------------------------------------------------------------------------------------------------

  toggle(): void {
    if (this.enabled) this.exit();
    else this.enter();
  }

  enter(): void {
    const id = this.model.selection;
    if (id === null || !this.built?.sculpts.has(id)) {
      this.onMessage("Select a sculpt first. Add one from the Add menu (Sculpt Sphere).", true);
      return;
    }
    this.enabled = true;
    this.bind();
    this.onMessage("Sculpting: left mouse paints, middle or right mouse orbits. Esc leaves sculpt mode.");
    this.sync();
  }

  exit(): void {
    if (!this.enabled) return;
    this.cancelStroke();
    this.enabled = false;
    this.unbind();
    this.sync();
  }

  /** The selection changed: follow it to another sculpt, or leave sculpt mode when it is not one. */
  selectionChanged(): void {
    if (!this.enabled) return;
    const id = this.model.selection;
    if (id === null || !this.built?.sculpts.has(id)) this.exit();
    else this.bind();
  }

  /** The viewport scene was rebuilt: attach to the new meshes and camera, and drop what pointed at the old ones. */
  attach(built: BuiltScene): void {
    this.cancelStrokeQuietly();
    this.built = built;
    this.binding = null;
    this.wire = null;
    this.cursor = null;
    this.stroke = null;
    this.grabPlane = null;
    built.scene.onPointerObservable.add((info) => this.onPointer(info.type, info.event as PointerEvent));
    if (!this.enabled) return;
    const id = this.model.selection;
    if (id === null || !built.sculpts.has(id)) {
      this.enabled = false;
      this.sync();
    } else {
      this.bind();
    }
  }

  /** The scene is going away (play, or a rebuild): finish nothing, keep the mode flag for the next attach. */
  detach(): void {
    this.cancelStrokeQuietly();
    this.built = null;
    this.binding = null;
  }

  private bind(): void {
    const built = this.built;
    const id = this.model.selection;
    if (!built || id === null) return;
    const binding = built.sculpts.get(id);
    if (!binding) return;
    this.cancelStrokeQuietly();
    this.disposeAids();
    this.binding = binding;
    this.stroke = new SculptStroke(binding.data);
    this.stroke.onMoved = () => updateSculptMesh(binding);
    const extent = binding.mesh.getBoundingInfo().boundingSphere.radius;
    this.radiusRange = { min: extent * MIN_RADIUS_FRACTION, max: extent * MAX_RADIUS_FRACTION };
    if (!(this.brush.radius >= this.radiusRange.min && this.brush.radius <= this.radiusRange.max)) {
      this.brush.radius = extent * START_RADIUS_FRACTION;
    }
    this.radiusInput.min = String(this.radiusRange.min);
    this.radiusInput.max = String(this.radiusRange.max);
    this.radiusInput.value = String(this.brush.radius);
    this.setOrbitButtons(true);
    this.applyWireframe();
  }

  private unbind(): void {
    this.setOrbitButtons(false);
    this.disposeAids();
    this.binding = null;
    this.stroke = null;
  }

  private disposeAids(): void {
    this.wire?.dispose();
    this.wire = null;
    this.cursor?.dispose();
    this.cursor = null;
  }

  /** While sculpting the left button paints, so it must not also orbit the camera. */
  private setOrbitButtons(sculpting: boolean): void {
    const camera = this.built?.viewerCamera as ArcRotateCamera | undefined;
    const pointers = camera?.inputs.attached["pointers"] as { buttons: number[] } | undefined;
    if (pointers) pointers.buttons = sculpting ? [...ORBIT_BUTTONS] : [0, 1, 2];
  }

  // ---- Painting -----------------------------------------------------------------------------------------------

  private onPointer(type: number, event: PointerEvent): void {
    if (!this.enabled || !this.binding || !this.stroke || !this.built) return;
    if (type === 1 /* POINTERDOWN */ && event.button === 0) this.startStroke(event);
    else if (type === 4 /* POINTERMOVE */) this.moveStroke(event);
    else if (type === 2 /* POINTERUP */ && event.button === 0) this.endStroke();
  }

  private pickSculpt(): PickingInfo | null {
    const built = this.built;
    const binding = this.binding;
    if (!built || !binding) return null;
    const pick = built.scene.pick(built.scene.pointerX, built.scene.pointerY, (mesh) => mesh === binding.mesh);
    return pick?.hit ? pick : null;
  }

  /** A picked point and normal in the sculpt's own space, which is what the brushes work in. */
  private toLocal(pick: PickingInfo): { point: number[]; normal: number[] } | null {
    const binding = this.binding;
    const point = pick.pickedPoint;
    const normal = pick.getNormal(true, true);
    if (!binding || !point || !normal) return null;
    const world = binding.mesh.getWorldMatrix();
    const local = Vector3.TransformCoordinates(point, world.clone().invert());
    // Normals move with the transpose of the world matrix, so non-uniform scale does not skew them
    const n = Vector3.TransformNormal(normal, Matrix.Transpose(world));
    return { point: [local.x, local.y, local.z], normal: [n.x, n.y, n.z] };
  }

  private startStroke(event: PointerEvent): void {
    if (!this.stroke || !this.built) return;
    const pick = this.pickSculpt();
    const hit = pick ? this.toLocal(pick) : null;
    if (!hit) return;
    const settings = { ...this.brush, subtract: this.brush.subtract !== event.ctrlKey };
    this.grabPlane = null;
    if (this.brushType === "GRAB") {
      const camera = this.built.scene.activeCamera;
      if (camera) this.grabPlane = { point: pick!.pickedPoint!.clone(), normal: camera.getForwardRay().direction.clone() };
    }
    this.stroke.begin(this.brushType, settings, this.symmetry, hit.point, hit.normal);
  }

  private moveStroke(event: PointerEvent): void {
    const stroke = this.stroke;
    if (!stroke || !this.built) return;
    if (!stroke.isActive) {
      this.moveCursor();
      return;
    }
    if (event.buttons === 0) {
      // The button went up somewhere the canvas did not see
      this.endStroke();
      return;
    }
    if (this.brushType === "GRAB") {
      const point = this.pointOnGrabPlane();
      if (point) stroke.move(this.localPoint(point), [0, 1, 0]);
      return;
    }
    const pick = this.pickSculpt();
    const hit = pick ? this.toLocal(pick) : null;
    if (hit) stroke.move(hit.point, hit.normal);
    this.moveCursor(pick);
  }

  private localPoint(world: Vector3): number[] {
    const local = Vector3.TransformCoordinates(world, this.binding!.mesh.getWorldMatrix().clone().invert());
    return [local.x, local.y, local.z];
  }

  /** Where the cursor ray meets the plane through the grabbed point that faces the camera. */
  private pointOnGrabPlane(): Vector3 | null {
    const built = this.built;
    const plane = this.grabPlane;
    const camera = built?.scene.activeCamera;
    if (!built || !plane || !camera) return null;
    const ray = built.scene.createPickingRay(built.scene.pointerX, built.scene.pointerY, Matrix.Identity(), camera);
    const denominator = Vector3.Dot(plane.normal, ray.direction);
    if (Math.abs(denominator) < 1e-9) return null;
    const t = Vector3.Dot(plane.normal, plane.point.subtract(ray.origin)) / denominator;
    return t > 0 ? ray.origin.add(ray.direction.scale(t)) : null;
  }

  private endStroke(): void {
    const stroke = this.stroke;
    const binding = this.binding;
    if (!stroke || !binding || !stroke.isActive) return;
    const delta = stroke.end();
    this.grabPlane = null;
    if (delta.indices.length === 0) return;
    const label = `Sculpt ${LABELS[this.brushType]}`;
    this.model.setSculptGeometry(binding.entity.id, encodeMesh(binding.data), label);
  }

  private cancelStroke(): void {
    if (this.stroke?.isActive) this.stroke.cancel();
  }

  /** Drops a stroke in progress without touching a mesh that may already be disposed. */
  private cancelStrokeQuietly(): void {
    if (!this.stroke?.isActive) return;
    this.stroke.onMoved = () => undefined;
    this.stroke.cancel();
  }

  // ---- Aids: brush cursor and wireframe ------------------------------------------------------------------------

  private moveCursor(known?: PickingInfo | null): void {
    const built = this.built;
    const binding = this.binding;
    if (!built || !binding) return;
    const pick = known === undefined ? this.pickSculpt() : known;
    if (!pick?.pickedPoint) {
      this.cursor?.setEnabled(false);
      return;
    }
    if (!this.cursor) {
      const disc = MeshBuilder.CreateTorus("brush cursor", { diameter: 2, thickness: 0.04, tessellation: 48 }, built.scene);
      const material = new StandardMaterial("brush cursor material", built.scene);
      material.disableLighting = true;
      material.emissiveColor = CURSOR_COLOR;
      material.alpha = 0.9;
      disc.material = material;
      disc.isPickable = false;
      disc.rotationQuaternion = Quaternion.Identity();
      this.cursor = disc;
    }
    const normal = pick.getNormal(true, true);
    this.cursor.setEnabled(true);
    this.cursor.position.copyFrom(pick.pickedPoint);
    // The brush radius is in the sculpt's space; the ring lives in world space, so scale it by the sculpt's world scale
    const scale = binding.entity.getWorldScale();
    const worldRadius = this.brush.radius * Math.max(scale.x, scale.y, scale.z);
    this.cursor.scaling.setAll(worldRadius);
    if (normal) this.cursor.rotationQuaternion = rotationFromUp(normal);
  }

  private applyWireframe(): void {
    const binding = this.binding;
    const built = this.built;
    if (!binding || !built) return;
    if (!this.wireframe) {
      this.wire?.dispose();
      this.wire = null;
      return;
    }
    if (this.wire) return;
    // A clone shares the geometry, so every brush dab updates the wire as well
    const wire = binding.mesh.clone(`${binding.mesh.name} wire`, binding.entity.node);
    if (!wire) return;
    const material = new StandardMaterial("sculpt wire material", built.scene);
    material.wireframe = true;
    material.disableLighting = true;
    material.emissiveColor = WIRE_COLOR;
    material.zOffset = WIRE_Z_OFFSET;
    wire.material = material;
    wire.isPickable = false;
    wire.metadata = null;
    this.wire = wire;
  }

  // ---- Panel --------------------------------------------------------------------------------------------------

  private buildPanel(): void {
    const brushes = h("div", { class: "row" });
    for (const type of BRUSH_TYPES) {
      const button = h("button", { "data-brush": type, title: `${LABELS[type]} brush` }, LABELS[type]);
      button.addEventListener("click", () => this.setBrush(type));
      brushes.append(button);
    }

    this.radiusInput.addEventListener("input", () => (this.brush.radius = Number(this.radiusInput.value)));
    this.strengthInput.value = String(this.brush.strength);
    this.strengthInput.addEventListener("input", () => (this.brush.strength = Number(this.strengthInput.value)));

    const falloff = h("select", { title: "How the effect fades towards the edge" }) as HTMLSelectElement;
    for (const name of FALLOFFS) falloff.append(h("option", { value: name }, name.charAt(0) + name.slice(1).toLowerCase()));
    falloff.addEventListener("change", () => (this.brush.falloff = falloff.value as Falloff));

    const spacing = h("input", { type: "range", min: String(MIN_SPACING), max: String(MAX_SPACING), step: "0.01", title: "Distance between dabs" }) as HTMLInputElement;
    spacing.value = String(this.brush.spacing);
    spacing.addEventListener("input", () => (this.brush.spacing = Number(spacing.value)));

    const subtract = checkbox("Subtract", "Dig instead of build (hold Ctrl while painting)", (on) => (this.brush.subtract = on));
    const wire = checkbox("Wireframe", "Show the mesh's edges over the surface", (on) => {
      this.wireframe = on;
      this.applyWireframe();
    });
    wire.input.id = "sculpt-wire";
    const mirror = h("div", { class: "row" }, h("span", {}, "Mirror"));
    for (const axis of ["x", "y", "z"] as const) {
      const box = checkbox(axis.toUpperCase(), `Mirror across the ${axis.toUpperCase()} plane`, (on) => (this.symmetry[axis] = on));
      mirror.append(box.label);
    }

    const geometry = h("div", { class: "row" },
      button("Subdivide", "Split every triangle in four: more detail to sculpt on", () => this.subdivideSelected(1)),
      button("Import…", "Replace the geometry with a .sculpt file", () => this.importSculpt()),
      button("Export", "Download the sculpt as a .sculpt file (opens in the desktop editor)", () => this.exportSculpt()),
    );
    const done = h("button", { id: "sculpt-done", class: "primary", title: "Leave sculpt mode (Esc)" }, "Done");
    done.addEventListener("click", () => this.exit());

    this.root.replaceChildren(
      h("div", { class: "row title" }, "Sculpt", done),
      brushes,
      h("label", {}, "Radius", this.radiusInput),
      h("label", {}, "Strength", this.strengthInput),
      h("label", {}, "Spacing", spacing),
      h("label", {}, "Falloff", falloff),
      h("div", { class: "row" }, subtract.label, wire.label),
      mirror,
      geometry,
    );
    this.refreshPanel();
  }

  /** Makes the brush bigger or smaller by a factor and stronger or weaker by an amount, within the sliders' limits. */
  adjustBrush(radiusFactor: number, strengthDelta: number): void {
    this.brush.radius = Math.min(Math.max(this.brush.radius * radiusFactor, this.radiusRange.min), this.radiusRange.max);
    this.brush.strength = Math.min(Math.max(this.brush.strength + strengthDelta, MIN_STRENGTH), 1);
    this.radiusInput.value = String(this.brush.radius);
    this.strengthInput.value = String(this.brush.strength);
  }

  /** Splits every triangle of the sculpt in four, `levels` times, so there is more geometry to sculpt on. */
  subdivideSelected(levels = 1): void {
    const binding = this.binding;
    if (!binding) return;
    try {
      const finer = subdivide(binding.data, levels);
      this.model.replaceSculptGeometry(binding.entity.id, encodeMesh(finer), "Subdivide sculpt");
      this.onMessage(`Subdivided: ${finer.vertexCount} vertices, ${finer.triangleCount} triangles`);
    } catch (error) {
      this.onMessage(error instanceof Error ? error.message : String(error), true);
    }
  }

  /** Downloads the sculpt as a desktop `.sculpt` file. */
  exportSculpt(): void {
    const binding = this.binding;
    if (!binding) return;
    const link = h("a", { download: `${binding.entity.name.replace(/[^\w-]+/g, "-").toLowerCase() || "sculpt"}.${SCULPT_EXTENSION}` }) as HTMLAnchorElement;
    link.href = URL.createObjectURL(new Blob([writeSculptFile(binding.data) as BlobPart], { type: "application/octet-stream" }));
    link.click();
    URL.revokeObjectURL(link.href);
    this.onMessage(`Exported ${binding.data.vertexCount} vertices as .${SCULPT_EXTENSION}`);
  }

  /** Replaces the selected sculpt's geometry with a `.sculpt` file's. */
  replaceFromBytes(bytes: Uint8Array): void {
    const binding = this.binding;
    if (!binding) return;
    try {
      const mesh = readSculptFile(bytes);
      this.model.replaceSculptGeometry(binding.entity.id, encodeMesh(mesh), "Import sculpt");
      this.onMessage(`Imported ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles`);
    } catch (error) {
      this.onMessage(`Cannot import: ${error instanceof Error ? error.message : String(error)}`, true);
    }
  }

  private importSculpt(): void {
    const input = h("input", { type: "file", accept: `.${SCULPT_EXTENSION}` }) as HTMLInputElement;
    input.addEventListener("change", async () => {
      const file = input.files?.[0];
      if (file) this.replaceFromBytes(new Uint8Array(await file.arrayBuffer()));
    });
    input.click();
  }

  setBrush(type: BrushType): void {
    this.brushType = type;
    this.refreshPanel();
  }

  private refreshPanel(): void {
    for (const button of this.root.querySelectorAll<HTMLElement>("button[data-brush]")) {
      button.classList.toggle("active", button.dataset["brush"] === this.brushType);
    }
  }

  /** Shows or hides the panel and tells the editor. */
  private sync(): void {
    document.body.classList.toggle("sculpting", this.enabled);
    this.root.hidden = !this.enabled;
    this.refreshPanel();
    this.onChange();
  }
}

function checkbox(text: string, title: string, onChange: (on: boolean) => void): { label: HTMLElement; input: HTMLInputElement } {
  const input = h("input", { type: "checkbox" }) as HTMLInputElement;
  input.addEventListener("change", () => onChange(input.checked));
  return { label: h("label", { class: "inline", title }, input, text), input };
}

/** The rotation that turns a torus lying in the XZ plane (its axis is +Y) so its axis follows {@code normal}. */
function rotationFromUp(normal: Vector3): Quaternion {
  const up = Vector3.Up();
  const n = normal.normalizeToNew();
  const dot = Vector3.Dot(up, n);
  if (dot > 0.9999) return Quaternion.Identity();
  if (dot < -0.9999) return Quaternion.RotationAxis(Vector3.Right(), Math.PI);
  const axis = Vector3.Cross(up, n).normalize();
  return Quaternion.RotationAxis(axis, Math.acos(dot));
}
