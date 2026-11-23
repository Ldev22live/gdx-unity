/** Small DOM helpers shared by the editor panels. */

export type Child = Node | string | null | undefined | false;

/** Creates an element with attributes and children: {@code h("button", { class: "x" }, "Save")}. */
export function h(tag: string, attributes: Record<string, string> = {}, ...children: Child[]): HTMLElement {
  const element = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, value);
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    element.append(child);
  }
  return element;
}

/** Looks up an element that the page must have; throws naming it otherwise. */
export function byId<T extends HTMLElement>(id: string): T {
  const found = document.getElementById(id);
  if (!found) throw new Error(`The page has no #${id}`);
  return found as T;
}

/** "MeshPrimitiveComponent" becomes "Mesh Primitive". */
export function componentTitle(type: string): string {
  return type.replace(/Component$/, "").replace(/([a-z0-9])([A-Z])/g, "$1 $2");
}

/** "sizeX" becomes "Size X"; "colorR" becomes "Color R". */
export function fieldTitle(key: string): string {
  const spaced = key.replace(/([a-z0-9])([A-Z])/g, "$1 $2");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}
