import { Quaternion } from "@babylonjs/core";
import type { ComponentData, TransformData } from "../scene/types";
import { componentTitle, fieldTitle, h } from "./dom";
import type { EditorModel } from "./EditorModel";
import { COMPONENT_TEMPLATES } from "./presets";

const DEGREES = 180 / Math.PI;
const RADIANS = Math.PI / 180;

/** Fields whose value is one of a fixed list, shown as a drop-down. */
const CHOICES: Record<string, string[]> = {
  shape: ["BOX", "SPHERE", "PLANE"],
  projection: ["PERSPECTIVE", "ORTHOGRAPHIC"],
  operator: ["ACES_FILMIC"],
  bodyType: ["STATIC", "DYNAMIC", "KINEMATIC"],
  anchor: ["TOP_LEFT", "TOP", "TOP_RIGHT", "CENTER", "BOTTOM_LEFT", "BOTTOM_RIGHT"],
  shapeType: ["FILLED"],
};

/** Component fields that hold bulk data and are not shown as text boxes. */
const BULK_FIELDS: ReadonlySet<string> = new Set(["positions", "triangles"]);

/** Euler angles in degrees (x = pitch, y = yaw, z = roll) of a rotation quaternion. */
export function toEulerDegrees(q: { x: number; y: number; z: number; w: number }): [number, number, number] {
  const euler = new Quaternion(q.x, q.y, q.z, q.w).toEulerAngles();
  return [euler.x * DEGREES, euler.y * DEGREES, euler.z * DEGREES];
}

/** The rotation quaternion for Euler angles in degrees, the inverse of {@link toEulerDegrees}. */
export function fromEulerDegrees(x: number, y: number, z: number): { x: number; y: number; z: number; w: number } {
  const q = Quaternion.RotationYawPitchRoll(y * RADIANS, x * RADIANS, z * RADIANS);
  return { x: q.x, y: q.y, z: q.z, w: q.w };
}

/**
 * Edits the selected entity: name, active, transform and every field of every component. Number fields commit on each
 * keystroke (one undo step per field, see the model's coalescing); the panel does not redraw itself for its own edits,
 * so typing never loses focus.
 */
export class InspectorPanel {
  private editing = false;
  private rotationFields: HTMLInputElement[] = [];
  private positionFields: HTMLInputElement[] = [];
  private scaleFields: HTMLInputElement[] = [];

  constructor(
    private readonly root: HTMLElement,
    private readonly model: EditorModel,
    private readonly onError: (message: string) => void,
  ) {
    this.render();
  }

  /** Redraws unless the change came from this panel. */
  render(): void {
    if (this.editing) return;
    this.root.replaceChildren();
    const id = this.model.selection;
    if (id === null) {
      this.root.append(h("div", { class: "empty" }, "Nothing selected. Click an entity in the outline or the view."));
      return;
    }
    const entity = this.model.require(id);

    const name = h("input", { type: "text", value: entity.name }) as HTMLInputElement;
    name.addEventListener("input", () => this.edit(() => this.model.rename(id, name.value)));
    const active = h("input", { type: "checkbox" }) as HTMLInputElement;
    active.checked = entity.active;
    active.addEventListener("change", () => this.edit(() => this.model.setActive(id, active.checked)));
    this.root.append(h("div", { class: "section" }, h("label", {}, "Name", name), h("label", { class: "inline" }, active, "Active")));

    this.root.append(this.transformSection(id, entity.transform));
    entity.components.forEach((component, index) => this.root.append(this.componentSection(id, component, index)));

    const add = h("select", {}, h("option", { value: "" }, "Add component…")) as HTMLSelectElement;
    for (const type of Object.keys(COMPONENT_TEMPLATES)) add.append(h("option", { value: type }, componentTitle(type)));
    add.addEventListener("change", () => {
      if (add.value === "") return;
      this.model.addComponent(id, add.value);
    });
    this.root.append(h("div", { class: "section" }, add));
  }

  /** Updates the transform fields in place after a change made elsewhere (the gizmo). */
  refreshTransform(): void {
    const id = this.model.selection;
    if (id === null || this.positionFields.length === 0) return;
    const t = this.model.require(id).transform;
    const euler = toEulerDegrees(t.rotation);
    fill(this.positionFields, [t.position.x, t.position.y, t.position.z]);
    fill(this.rotationFields, euler);
    fill(this.scaleFields, [t.scale.x, t.scale.y, t.scale.z]);
  }

  private edit(change: () => void): void {
    this.editing = true;
    try {
      change();
    } catch (error) {
      this.onError(error instanceof Error ? error.message : String(error));
    } finally {
      this.editing = false;
    }
  }

  private transformSection(id: string, transform: TransformData): HTMLElement {
    const euler = toEulerDegrees(transform.rotation);
    const read = (): TransformData => {
      const [px, py, pz] = this.positionFields.map((field) => Number(field.value));
      const [rx, ry, rz] = this.rotationFields.map((field) => Number(field.value));
      const [sx, sy, sz] = this.scaleFields.map((field) => Number(field.value));
      return {
        position: { x: px!, y: py!, z: pz! },
        rotation: fromEulerDegrees(rx!, ry!, rz!),
        scale: { x: sx!, y: sy!, z: sz! },
      };
    };
    const commit = (touchedRotation: boolean) => {
      const next = read();
      // Rebuilding the quaternion from rounded Euler fields would wobble an untouched rotation; keep the original
      if (!touchedRotation) next.rotation = structuredClone(this.model.require(id).transform.rotation);
      if (Object.values(next.position).concat(Object.values(next.scale)).some((n) => !Number.isFinite(n))) return;
      this.edit(() => this.model.setTransform(id, next, "Edit transform", `transform:${id}`));
    };
    const row = (title: string, values: number[], touchesRotation: boolean): [HTMLElement, HTMLInputElement[]] => {
      const fields = values.map((value) => numberField(value, () => commit(touchesRotation)));
      return [h("div", { class: "vec" }, h("span", { class: "label" }, title), ...fields), fields];
    };
    const [positionRow, positionFields] = row("Position", [transform.position.x, transform.position.y, transform.position.z], false);
    const [rotationRow, rotationFields] = row("Rotation", euler, true);
    const [scaleRow, scaleFields] = row("Scale", [transform.scale.x, transform.scale.y, transform.scale.z], false);
    this.positionFields = positionFields;
    this.rotationFields = rotationFields;
    this.scaleFields = scaleFields;
    return h("div", { class: "section" }, h("h3", {}, "Transform"), positionRow, rotationRow, scaleRow);
  }

  private componentSection(id: string, component: ComponentData, index: number): HTMLElement {
    const enabled = h("input", { type: "checkbox" }) as HTMLInputElement;
    enabled.checked = component.enabled;
    enabled.addEventListener("change", () => this.model.setComponentEnabled(id, index, enabled.checked));
    const remove = h("button", { class: "small", title: "Remove component" }, "✕");
    remove.addEventListener("click", () => this.model.removeComponent(id, index));
    const section = h("div", { class: "section" }, h("h3", {}, enabled, componentTitle(component.type), remove));
    for (const [key, value] of Object.entries(component.data)) {
      // Sculpt geometry is megabytes of base64: edited with the brushes, never as text
      if (BULK_FIELDS.has(key)) continue;
      section.append(this.fieldRow(id, index, [key], value));
    }
    return section;
  }

  private fieldRow(id: string, componentIndex: number, path: string[], value: unknown): HTMLElement {
    const key = path.at(-1)!;
    const set = (next: unknown) => this.edit(() => this.model.setComponentField(id, componentIndex, path, next));
    if (typeof value === "number") {
      return h("label", {}, fieldTitle(key), numberField(value, (n) => set(n)));
    }
    if (typeof value === "boolean") {
      const box = h("input", { type: "checkbox" }) as HTMLInputElement;
      box.checked = value;
      box.addEventListener("change", () => set(box.checked));
      return h("label", { class: "inline" }, box, fieldTitle(key));
    }
    if (typeof value === "string") {
      const choices = CHOICES[key];
      if (choices) {
        const select = h("select", {}) as HTMLSelectElement;
        for (const choice of choices.includes(value) ? choices : [value, ...choices]) {
          const option = h("option", { value: choice }, choice) as HTMLOptionElement;
          option.selected = choice === value;
          select.append(option);
        }
        select.addEventListener("change", () => set(select.value));
        return h("label", {}, fieldTitle(key), select);
      }
      const input = h("input", { type: "text", value }) as HTMLInputElement;
      input.addEventListener("input", () => set(input.value));
      return h("label", {}, fieldTitle(key), input);
    }
    if (typeof value === "object" && value !== null) {
      const group = h("div", { class: "group" }, h("span", { class: "label" }, fieldTitle(key)));
      for (const [childKey, childValue] of Object.entries(value)) {
        group.append(this.fieldRow(id, componentIndex, [...path, childKey], childValue));
      }
      return group;
    }
    return h("div", { class: "unsupported" }, `${fieldTitle(key)}: ${JSON.stringify(value)}`);
  }
}

/** A number input that calls {@code onChange} with the new value on every valid edit. */
function numberField(value: number, onChange: (value: number) => void): HTMLInputElement {
  const input = h("input", { type: "number", step: "any" }) as HTMLInputElement;
  input.value = String(round(value));
  input.addEventListener("input", () => {
    const parsed = Number(input.value);
    if (input.value.trim() !== "" && Number.isFinite(parsed)) onChange(parsed);
  });
  return input;
}

function fill(fields: HTMLInputElement[], values: number[]): void {
  fields.forEach((field, i) => {
    if (document.activeElement !== field) field.value = String(round(values[i]!));
  });
}

/** Rounds for display only (the data keeps full precision). */
function round(value: number): number {
  return Math.round(value * 1000) / 1000;
}
