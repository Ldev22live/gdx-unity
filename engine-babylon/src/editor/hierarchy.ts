import type { EntityData } from "../scene/types";
import { h } from "./dom";
import type { EditorModel } from "./EditorModel";

const DRAG_TYPE = "application/x-gdxunity-entity";

/**
 * The scene outline: entities as a tree by parent, click to select, drag one onto another to make it a child (or onto
 * the empty area to move it to the top level). It only reads the model and calls its edit methods.
 */
export class HierarchyPanel {
  private readonly collapsed = new Set<string>();

  constructor(
    private readonly root: HTMLElement,
    private readonly model: EditorModel,
    /** Reports a failed edit (such as a forbidden reparent) to the user. */
    private readonly onError: (message: string) => void,
  ) {
    root.addEventListener("dragover", (event) => event.preventDefault());
    root.addEventListener("drop", (event) => {
      if (event.target !== root) return;
      this.dropOn(event, null);
    });
    this.render();
  }

  render(): void {
    this.root.replaceChildren();
    const roots = this.model.childrenOf(null);
    if (roots.length === 0) {
      this.root.append(h("div", { class: "empty" }, "The scene is empty. Use Add to create an entity."));
      return;
    }
    for (const entity of roots) this.renderEntity(entity, 0);
  }

  private renderEntity(entity: EntityData, depth: number): void {
    const children = this.model.childrenOf(entity.id);
    const isCollapsed = this.collapsed.has(entity.id);
    const toggle = h("span", { class: "toggle" }, children.length === 0 ? "" : isCollapsed ? "▸" : "▾");
    toggle.addEventListener("click", (event) => {
      event.stopPropagation();
      if (isCollapsed) this.collapsed.delete(entity.id);
      else this.collapsed.add(entity.id);
      this.render();
    });
    const row = h(
      "div",
      {
        class: `row${entity.id === this.model.selection ? " selected" : ""}${entity.active ? "" : " inactive"}`,
        style: `padding-left: ${6 + depth * 14}px`,
        draggable: "true",
        "data-entity": entity.id,
      },
      toggle,
      h("span", { class: "kind" }, kindIcon(entity)),
      h("span", { class: "name" }, entity.name),
    );
    row.addEventListener("click", () => this.model.select(entity.id));
    row.addEventListener("dragstart", (event) => event.dataTransfer?.setData(DRAG_TYPE, entity.id));
    row.addEventListener("dragover", (event) => {
      event.preventDefault();
      row.classList.add("drop");
    });
    row.addEventListener("dragleave", () => row.classList.remove("drop"));
    row.addEventListener("drop", (event) => {
      event.stopPropagation();
      row.classList.remove("drop");
      this.dropOn(event, entity.id);
    });
    this.root.append(row);
    if (!isCollapsed) for (const child of children) this.renderEntity(child, depth + 1);
  }

  private dropOn(event: DragEvent, parentId: string | null): void {
    event.preventDefault();
    const id = event.dataTransfer?.getData(DRAG_TYPE);
    if (!id || this.model.find(id) === undefined) return;
    if (this.model.require(id).parentId === parentId) return;
    try {
      this.model.reparent(id, parentId);
    } catch (error) {
      this.onError(error instanceof Error ? error.message : String(error));
    }
  }
}

/** A one-character hint of what the entity is, from its components. */
function kindIcon(entity: EntityData): string {
  const types = new Set(entity.components.map((component) => component.type));
  if (types.has("CameraComponent")) return "◎";
  if (types.has("DirectionalLightComponent") || types.has("SkyLightComponent")) return "☀";
  if (types.has("TextComponent")) return "T";
  if (types.has("MeshPrimitiveComponent")) return "◆";
  if (types.has("ShapeRendererComponent")) return "▭";
  if (types.has("EntityScriptBindingComponent")) return "{}";
  return "·";
}
