import type { ComponentData, EntityData } from "../scene/types";
import type { EditorModel } from "./EditorModel";
import { newId } from "./presets";

/**
 * The placement palettes of the web World shell (roadmap A13): the desktop's own palette prefabs (Objects `prefabs/level`,
 * Buildings `prefabs/buildings`, Characters `prefabs/characters`), shipped with the web build by `scripts/sync-samples.mjs`.
 * A placed prefab becomes a linked instance in the same form the desktop writes (`PrefabInstanceComponent` on every entity), so the
 * desktop treats it exactly like one it placed itself and keeps it up to date with the prefab.
 */

/** One palette entry, from `prefabs/index.json`. */
export interface PaletteEntry {
  /** Project-relative prefab path, e.g. `prefabs/level/CC0 Oak Tree.prefab`. */
  path: string;
  name: string;
  /** Project-relative thumbnail (`.png` beside the prefab), or null. */
  thumbnail: string | null;
}

/** A prefab file: the desktop's `PrefabDocument` form. */
export interface PrefabFile {
  schemaVersion: number;
  kind: "prefab";
  name: string;
  rootId: string;
  entities: EntityData[];
}

/** The component type of the prefab link (the desktop's `PrefabInstanceComponentSerializer.TYPE_ID`). */
export const PREFAB_LINK_TYPE = "PrefabInstance";

/** The palette entries under `folder` (`prefabs/level`), in name order. */
export function entriesIn(index: readonly PaletteEntry[], folder: string): PaletteEntry[] {
  const prefix = folder.endsWith("/") ? folder : `${folder}/`;
  return index.filter((entry) => entry.path.startsWith(prefix)).sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * Parses a prefab file.
 *
 * @throws Error naming what is wrong when it is not a prefab this editor can place
 */
export function parsePrefab(text: string, path: string): PrefabFile {
  const parsed = JSON.parse(text) as Partial<PrefabFile>;
  if (parsed.kind !== "prefab" || parsed.schemaVersion !== 1) {
    throw new Error(`${path} is not a version 1 prefab`);
  }
  if (!Array.isArray(parsed.entities) || typeof parsed.rootId !== "string" || !parsed.entities.some((e) => e.id === parsed.rootId)) {
    throw new Error(`${path} has no root entity`);
  }
  return parsed as PrefabFile;
}

/**
 * Places a linked instance of `prefab` with its root at `position` (scene units), as one undo step, and selects the root. Every
 * entity gets a new id and a link to its prefab entity; the root's link also lists the prefab's entity ids (`knownSourceIds`), as
 * the desktop's do. The root keeps the prefab's rotation and scale.
 *
 * @param makeId  where new entity ids come from (UUIDs; tests pass a fixed sequence)
 * @param ownerId the scenario player that owns the placed root (TD-108: in the same undo step), or null for Unowned
 * @returns the new root's id
 */
export function placePrefab(model: EditorModel, prefab: PrefabFile, prefabPath: string,
                            position: { x: number; y: number; z: number }, makeId: () => string = newId,
                            ownerId: string | null = null): string {
  const ids = new Map<string, string>();
  for (const entity of prefab.entities) ids.set(entity.id, makeId());
  const knownSourceIds = prefab.entities.map((entity) => entity.id);
  const copies: EntityData[] = prefab.entities.map((entity) => {
    const copy = structuredClone(entity);
    const root = entity.id === prefab.rootId;
    copy.id = ids.get(entity.id)!;
    copy.parentId = root ? null : entity.parentId === null ? null : ids.get(entity.parentId) ?? null;
    copy.components = copy.components.filter((component) => component.type !== PREFAB_LINK_TYPE);
    const link: ComponentData = {
      type: PREFAB_LINK_TYPE,
      enabled: true,
      data: root
        ? { prefab: prefabPath, sourceId: entity.id, root: true, overrides: [], knownSourceIds }
        : { prefab: prefabPath, sourceId: entity.id, root: false, overrides: [] },
    };
    copy.components.push(link);
    if (root) copy.transform.position = { x: position.x, y: position.y, z: position.z };
    if (root && ownerId !== null) {
      copy.components = copy.components.filter((component) => component.type !== "OwnerComponent");
      copy.components.push({ type: "OwnerComponent", enabled: true, data: { player: ownerId } });
    }
    return copy;
  });
  const rootId = ids.get(prefab.rootId)!;
  model.edit(`Place ${prefab.name}`, "scene", (file) => file.entities.push(...copies));
  model.select(rootId);
  return rootId;
}
