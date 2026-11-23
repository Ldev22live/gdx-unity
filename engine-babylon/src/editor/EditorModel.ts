import type { ComponentData, EntityData, SceneFile, TransformData } from "../scene/types";
import { COMPONENT_TEMPLATES, ENTITY_PRESETS, newId } from "./presets";

/** Undo steps kept; older ones are dropped. */
export const MAX_UNDO_STEPS = 100;
/** Edits with the same coalescing key within this many milliseconds become one undo step (typing in a field). */
export const COALESCE_MS = 800;

export type ChangeKind =
  /** Only transforms changed: the view can move the nodes instead of rebuilding the scene. */
  | "transform"
  /** Anything else about the scene changed: the view rebuilds it. */
  | "scene"
  /** A sculpt's geometry changed and the view already shows it: nothing to rebuild. */
  | "sculpt"
  /** The whole file was replaced (open, undo, redo). */
  | "replaced"
  | "selection";

export interface ModelListener {
  (kind: ChangeKind): void;
}

interface Snapshot {
  label: string;
  file: SceneFile;
  selected: string | null;
}

/**
 * The scene being edited, kept as the scene file itself (so saving writes exactly what the desktop editor reads), plus
 * the selection and an undo history. It has no Babylon or DOM in it, so everything here is tested without a browser.
 *
 * Every change goes through {@link edit}: it takes a snapshot first, so undo restores the whole scene whatever was
 * changed. Scenes are small (the biggest sample is 190 KB), so snapshots are cheaper than writing an inverse for every
 * kind of edit and cannot get an inverse wrong.
 */
export class EditorModel {
  private fileData: SceneFile;
  private selectedId: string | null = null;
  private readonly undoStack: Snapshot[] = [];
  private readonly redoStack: Snapshot[] = [];
  private readonly listeners = new Set<ModelListener>();
  private savedRevision = 0;
  private revision = 0;
  private lastCoalesceKey: string | null = null;
  private lastCoalesceTime = 0;

  constructor(file: SceneFile) {
    this.fileData = structuredClone(file);
  }

  get file(): SceneFile {
    return this.fileData;
  }

  get selection(): string | null {
    return this.selectedId;
  }

  get isDirty(): boolean {
    return this.revision !== this.savedRevision;
  }

  get canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  get canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  get undoLabel(): string | null {
    return this.undoStack.at(-1)?.label ?? null;
  }

  get redoLabel(): string | null {
    return this.redoStack.at(-1)?.label ?? null;
  }

  addListener(listener: ModelListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(kind: ChangeKind): void {
    for (const listener of [...this.listeners]) listener(kind);
  }

  find(id: string): EntityData | undefined {
    return this.fileData.entities.find((entity) => entity.id === id);
  }

  require(id: string): EntityData {
    const entity = this.find(id);
    if (!entity) throw new Error(`No entity with id ${id}`);
    return entity;
  }

  childrenOf(id: string | null): EntityData[] {
    return this.fileData.entities.filter((entity) => entity.parentId === id);
  }

  /** The entity and everything under it, parents first. */
  subtree(id: string): EntityData[] {
    const result: EntityData[] = [];
    const visit = (entity: EntityData) => {
      result.push(entity);
      for (const child of this.childrenOf(entity.id)) visit(child);
    };
    visit(this.require(id));
    return result;
  }

  select(id: string | null): void {
    if (id !== null) this.require(id);
    if (id === this.selectedId) return;
    this.selectedId = id;
    this.notify("selection");
  }

  /**
   * Runs {@code change} on the scene file as one undo step.
   *
   * @param coalesceKey edits that repeat the same key within {@link COALESCE_MS} share one step (for typing in a field)
   */
  edit(label: string, kind: ChangeKind, change: (file: SceneFile) => void, coalesceKey?: string): void {
    const now = Date.now();
    const merges =
      coalesceKey !== undefined &&
      coalesceKey === this.lastCoalesceKey &&
      now - this.lastCoalesceTime < COALESCE_MS &&
      this.undoStack.length > 0;
    if (!merges) {
      this.undoStack.push({ label, file: structuredClone(this.fileData), selected: this.selectedId });
      if (this.undoStack.length > MAX_UNDO_STEPS) this.undoStack.shift();
    }
    this.redoStack.length = 0;
    this.lastCoalesceKey = coalesceKey ?? null;
    this.lastCoalesceTime = now;
    change(this.fileData);
    this.revision++;
    // A selection pointing at an entity the change removed would dangle
    if (this.selectedId !== null && !this.find(this.selectedId)) {
      this.selectedId = null;
      this.notify("selection");
    }
    this.notify(kind);
  }

  undo(): void {
    const step = this.undoStack.pop();
    if (!step) return;
    this.redoStack.push({ label: step.label, file: this.fileData, selected: this.selectedId });
    this.restore(step);
  }

  redo(): void {
    const step = this.redoStack.pop();
    if (!step) return;
    this.undoStack.push({ label: step.label, file: this.fileData, selected: this.selectedId });
    this.restore(step);
  }

  private restore(step: Snapshot): void {
    this.fileData = step.file;
    this.selectedId = step.selected !== null && this.find(step.selected) ? step.selected : null;
    this.revision++;
    this.lastCoalesceKey = null;
    this.notify("replaced");
    this.notify("selection");
  }

  /** Replaces the whole scene (open a file); clears the history. */
  load(file: SceneFile): void {
    this.fileData = structuredClone(file);
    this.selectedId = null;
    this.undoStack.length = 0;
    this.redoStack.length = 0;
    this.revision = 0;
    this.savedRevision = 0;
    this.lastCoalesceKey = null;
    this.notify("replaced");
    this.notify("selection");
  }

  /** The scene as the desktop editor's file text. */
  serialize(): string {
    return JSON.stringify(this.fileData, null, 2);
  }

  markSaved(): void {
    this.savedRevision = this.revision;
  }

  // ---- Edits --------------------------------------------------------------------------------------------------

  rename(id: string, name: string, coalesce = true): void {
    this.edit("Rename", "scene", () => (this.require(id).name = name), coalesce ? `rename:${id}` : undefined);
  }

  setActive(id: string, active: boolean): void {
    this.edit(active ? "Show" : "Hide", "scene", () => (this.require(id).active = active));
  }

  setTransform(id: string, transform: TransformData, label = "Move", coalesceKey?: string): void {
    this.edit(label, "transform", () => (this.require(id).transform = structuredClone(transform)), coalesceKey);
  }

  /** Sets one field of a component (a number, string or boolean, or a nested field with a path such as ["color", "r"]). */
  setComponentField(id: string, componentIndex: number, path: string[], value: unknown): void {
    this.edit(
      "Edit component",
      "scene",
      () => {
        const component = this.requireComponent(id, componentIndex);
        let target: Record<string, unknown> = component.data;
        for (const key of path.slice(0, -1)) {
          const next = target[key];
          if (typeof next !== "object" || next === null) throw new Error(`Component field '${key}' is not an object`);
          target = next as Record<string, unknown>;
        }
        const last = path.at(-1);
        if (last === undefined) throw new Error("A component field path cannot be empty");
        target[last] = value;
      },
      `component:${id}:${componentIndex}:${path.join(".")}`,
    );
  }

  /**
   * Stores a sculpt's geometry after a brush stroke as one undo step. The view already shows the stroke, so it does not
   * rebuild; undo and redo do (they replace the whole file).
   */
  setSculptGeometry(id: string, geometry: { positions: string; triangles: string }, label = "Sculpt"): void {
    this.edit(label, "sculpt", () => {
      const component = this.require(id).components.find((c) => c.type === "SculptMeshComponent");
      if (!component) throw new Error(`Entity ${id} has no sculpt`);
      component.data["positions"] = geometry.positions;
      component.data["triangles"] = geometry.triangles;
    });
  }

  /**
   * Replaces a sculpt's geometry with a different mesh (subdivide, import): unlike {@link setSculptGeometry} the vertex
   * count may change, so the view rebuilds.
   */
  replaceSculptGeometry(id: string, geometry: { positions: string; triangles: string }, label: string): void {
    this.edit(label, "scene", () => {
      const component = this.require(id).components.find((c) => c.type === "SculptMeshComponent");
      if (!component) throw new Error(`Entity ${id} has no sculpt`);
      component.data["positions"] = geometry.positions;
      component.data["triangles"] = geometry.triangles;
    });
  }

  setComponentEnabled(id: string, componentIndex: number, enabled: boolean): void {
    this.edit("Toggle component", "scene", () => (this.requireComponent(id, componentIndex).enabled = enabled));
  }

  addComponent(id: string, type: string): void {
    const template = COMPONENT_TEMPLATES[type];
    if (!template) throw new Error(`Unknown component type '${type}'`);
    this.edit("Add component", "scene", () => this.require(id).components.push(template()));
  }

  removeComponent(id: string, componentIndex: number): void {
    this.edit("Remove component", "scene", () => {
      this.requireComponent(id, componentIndex);
      this.require(id).components.splice(componentIndex, 1);
    });
  }

  /** Adds an entity made from a preset (see {@link ENTITY_PRESETS}) under {@code parentId}; returns its id and selects it. */
  addEntity(preset: string, parentId: string | null = null): string {
    const make = ENTITY_PRESETS[preset];
    if (!make) throw new Error(`Unknown entity preset '${preset}'`);
    if (parentId !== null) this.require(parentId);
    const entity = make(this.uniqueName(make.label));
    entity.parentId = parentId;
    this.edit(`Add ${make.label}`, "scene", (file) => file.entities.push(entity));
    this.select(entity.id);
    return entity.id;
  }

  /** Deletes the entity and everything under it. */
  deleteEntity(id: string): void {
    const doomed = new Set(this.subtree(id).map((entity) => entity.id));
    this.edit("Delete", "scene", (file) => {
      file.entities = file.entities.filter((entity) => !doomed.has(entity.id));
    });
  }

  /** Copies the entity and everything under it, next to the original; returns the id of the copy and selects it. */
  duplicateEntity(id: string): string {
    const original = this.subtree(id);
    const idMap = new Map(original.map((entity) => [entity.id, newId()]));
    const copies = original.map((entity, index) => {
      const copy = structuredClone(entity);
      copy.id = idMap.get(entity.id)!;
      if (index === 0) copy.name = this.uniqueName(`${entity.name} Copy`);
      else copy.parentId = idMap.get(entity.parentId!) ?? entity.parentId;
      return copy;
    });
    this.edit("Duplicate", "scene", (file) => file.entities.push(...copies));
    this.select(copies[0]!.id);
    return copies[0]!.id;
  }

  /**
   * Moves the entity under another one (or to the top level with null), keeping its local transform.
   *
   * @throws Error when the new parent is the entity itself or one of its descendants
   */
  reparent(id: string, parentId: string | null): void {
    if (parentId !== null && this.subtree(id).some((entity) => entity.id === parentId)) {
      throw new Error("An entity cannot become a child of itself or of its own descendants");
    }
    this.edit("Reparent", "scene", () => (this.require(id).parentId = parentId));
  }

  // ---- Helpers ------------------------------------------------------------------------------------------------

  private requireComponent(id: string, index: number): ComponentData {
    const component = this.require(id).components[index];
    if (!component) throw new Error(`Entity ${id} has no component at index ${index}`);
    return component;
  }

  /** {@code base}, or {@code base 2}, {@code base 3}... when that name is taken. */
  uniqueName(base: string): string {
    const names = new Set(this.fileData.entities.map((entity) => entity.name));
    if (!names.has(base)) return base;
    for (let n = 2; ; n++) {
      if (!names.has(`${base} ${n}`)) return `${base} ${n}`;
    }
  }
}

/** An empty scene to start from. */
export function emptyScene(name = "New Scene"): SceneFile {
  return { schemaVersion: 1, name, active: true, entities: [] };
}
