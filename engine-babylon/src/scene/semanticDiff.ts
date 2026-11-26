/**
 * Compares two scene files by meaning (TD-106, roadmap A12.2): the same rules as the desktop's `SceneSemanticDiff`. Member order does
 * not matter; entities match by id and components by type and their order among components of that type; numbers are equal when they
 * are the same 32-bit float. Null, empty and missing are never the same, except where a documented normalization names the component
 * type and field.
 */

export type Kind = "MISSING" | "ADDED" | "CHANGED";
export interface Difference { kind: Kind; path: string; before?: string; after?: string }

export type Normalization =
  | { kind: "absentMeans"; componentType: string; field: string; value: string; reason: string }
  | { kind: "sixteenBits"; componentType: string; field: string; reason: string }
  | { kind: "direction"; componentType: string; x: string; y: string; z: string; reason: string };

/** The desktop serializers' verified equivalences (same list as `SceneSemanticDiff.DESKTOP`). */
export const DESKTOP_NORMALIZATIONS: readonly Normalization[] = [
  { kind: "direction", componentType: "DirectionalLightComponent", x: "directionX", y: "directionY", z: "directionZ",
    reason: "DirectionalLightComponent.setDirection normalises the vector" },
  { kind: "sixteenBits", componentType: "BoxCollider2DComponent", field: "maskBits", reason: "cast to short: 65535 is written as -1" },
  { kind: "sixteenBits", componentType: "BoxCollider2DComponent", field: "categoryBits", reason: "cast to short" },
  { kind: "sixteenBits", componentType: "CircleCollider2DComponent", field: "maskBits", reason: "cast to short" },
  { kind: "sixteenBits", componentType: "CircleCollider2DComponent", field: "categoryBits", reason: "cast to short" },
  { kind: "absentMeans", componentType: "SpriteAnimatorComponent", field: "slice", value: "null", reason: "a missing slice reads as null" },
];

const DIRECTION_TOLERANCE = 1e-5;
type Json = unknown;

const isObject = (v: Json): v is Record<string, Json> => typeof v === "object" && v !== null && !Array.isArray(v);
const text = (v: Json): string => JSON.stringify(v);

export function compareScenes(before: string | Json, after: string | Json, normalizations: readonly Normalization[] = []): Difference[] {
  const a = typeof before === "string" ? JSON.parse(before) : before;
  const b = typeof after === "string" ? JSON.parse(after) : after;
  const out: Difference[] = [];
  compareObjects(a, b, "", new Set(["entities"]), out);
  compareEntities((a as Record<string, Json>)["entities"], (b as Record<string, Json>)["entities"], out, normalizations);
  return out;
}

function compareEntities(before: Json, after: Json, out: Difference[], normalizations: readonly Normalization[]): void {
  const byId = (list: Json) => new Map(((list as Record<string, Json>[] | undefined) ?? []).map((e) => [String(e["id"]), e]));
  const a = byId(before);
  const b = byId(after);
  for (const [id, entity] of a) {
    const path = `entities[${id} ${String(entity["name"] ?? "")}]`;
    const other = b.get(id);
    if (!other) {
      out.push({ kind: "MISSING", path, before: "entity" });
      continue;
    }
    compareObjects(entity, other, path, new Set(["components"]), out);
    compareComponents(entity["components"], other["components"], `${path}.components`, out, normalizations);
  }
  for (const id of b.keys()) if (!a.has(id)) out.push({ kind: "ADDED", path: `entities[${id}]`, after: "entity" });
}

function compareComponents(before: Json, after: Json, path: string, out: Difference[], normalizations: readonly Normalization[]): void {
  const keyed = (list: Json) => {
    const map = new Map<string, Record<string, Json>>();
    const counts = new Map<string, number>();
    for (const c of (list as Record<string, Json>[] | undefined) ?? []) {
      const type = String(c["type"]);
      const n = counts.get(type) ?? 0;
      counts.set(type, n + 1);
      map.set(`${type}#${n}`, c);
    }
    return map;
  };
  const a = keyed(before);
  const b = keyed(after);
  for (const [key, component] of a) {
    const componentPath = `${path}[${key}]`;
    const other = b.get(key);
    if (!other) {
      out.push({ kind: "MISSING", path: componentPath, before: "component" });
      continue;
    }
    compareObjects(component, other, componentPath, new Set(["data"]), out);
    const dataPath = `${componentPath}.data`;
    const raw: Difference[] = [];
    if (isObject(component["data"]) && isObject(other["data"])) compareObjects(component["data"], other["data"], dataPath, new Set(), raw);
    else compareValues(component["data"], other["data"], dataPath, raw);
    const type = String(component["type"]);
    const same = sameDirections(type, component["data"], other["data"], normalizations);
    for (const d of raw) {
      const field = d.path.slice(dataPath.length + 1);
      if (!same.has(field) && !normalized(type, field, d, normalizations)) out.push(d);
    }
  }
  for (const key of b.keys()) if (!a.has(key)) out.push({ kind: "ADDED", path: `${path}[${key}]`, after: "component" });
}

function sameDirections(type: string, before: Json, after: Json, normalizations: readonly Normalization[]): Set<string> {
  const fields = new Set<string>();
  for (const n of normalizations) {
    if (n.kind !== "direction" || n.componentType !== type || !isObject(before) || !isObject(after)) continue;
    const unit = (d: Record<string, Json>) => {
      const [x, y, z] = [d[n.x], d[n.y], d[n.z]];
      if (typeof x !== "number" || typeof y !== "number" || typeof z !== "number") return null;
      const length = Math.hypot(x, y, z);
      return length === 0 ? null : [x / length, y / length, z / length];
    };
    const a = unit(before);
    const b = unit(after);
    if (a && b && a.every((v, i) => Math.abs(v - b[i]!) <= DIRECTION_TOLERANCE)) [n.x, n.y, n.z].forEach((f) => fields.add(f));
  }
  return fields;
}

function normalized(type: string, field: string, d: Difference, normalizations: readonly Normalization[]): boolean {
  for (const n of normalizations) {
    if (n.componentType !== type) continue;
    if (n.kind === "absentMeans" && n.field === field && d.kind !== "CHANGED") {
      if ((d.kind === "MISSING" ? d.before : d.after) === n.value) return true;
    }
    if (n.kind === "sixteenBits" && n.field === field && d.kind === "CHANGED") {
      const a = Number(d.before);
      const b = Number(d.after);
      if (Number.isInteger(a) && Number.isInteger(b) && ((a << 16) >> 16) === ((b << 16) >> 16)) return true;
    }
  }
  return false;
}

function compareObjects(before: Json, after: Json, path: string, skip: Set<string>, out: Difference[]): void {
  if (!isObject(before) || !isObject(after)) {
    compareValues(before, after, path, out);
    return;
  }
  for (const [key, value] of Object.entries(before)) {
    if (skip.has(key)) continue;
    const memberPath = path === "" ? key : `${path}.${key}`;
    if (!(key in after)) out.push({ kind: "MISSING", path: memberPath, before: text(value) });
    else compareValues(value, after[key], memberPath, out);
  }
  for (const [key, value] of Object.entries(after)) {
    if (!skip.has(key) && !(key in before)) out.push({ kind: "ADDED", path: path === "" ? key : `${path}.${key}`, after: text(value) });
  }
}

function compareValues(before: Json, after: Json, path: string, out: Difference[]): void {
  if (before === undefined || after === undefined) {
    if (before !== after) out.push(before === undefined ? { kind: "ADDED", path, after: text(after) } : { kind: "MISSING", path, before: text(before) });
    return;
  }
  if (isObject(before) && isObject(after)) {
    compareObjects(before, after, path, new Set(), out);
    return;
  }
  if (Array.isArray(before) && Array.isArray(after)) {
    if (before.length !== after.length) out.push({ kind: "CHANGED", path, before: `${before.length} items`, after: `${after.length} items` });
    for (let i = 0; i < Math.min(before.length, after.length); i++) compareValues(before[i], after[i], `${path}[${i}]`, out);
    return;
  }
  if (typeof before === "number" && typeof after === "number") {
    if (Math.fround(before) !== Math.fround(after)) out.push({ kind: "CHANGED", path, before: text(before), after: text(after) });
    return;
  }
  if (text(before) !== text(after)) out.push({ kind: "CHANGED", path, before: text(before), after: text(after) });
}
