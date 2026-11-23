import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { loadPendingModels } from "../src/runtime/ModelLoader";
import { buildScene } from "../src/runtime/SceneBuilder";
import type { SceneFile } from "../src/scene/types";
import { createHeadlessEngine } from "./support";

/**
 * Roadmap M2b.7 (TD-101): every model the web build is given (scripts/sync-samples.mjs) that names no outside file loads in the web
 * player. The desktop's asset support matrix (AssetSupportMatrix, docs/migration/asset-support.tsv) calls exactly these "Works" on the
 * web; a served model that names outside files (a .bin, a texture) is "Unchecked" there, because a model loaded from its bytes cannot
 * reach them.
 */
const ASSETS = join(import.meta.dirname, "..", "..", "assets");
/** How many identical files are loaded at once; the packs ship the same bytes under several names. */
const BATCH = 40;

/** The model folders of sync-samples.mjs: models/** , characters/ (top level) and thirdparty/**\/Models/**. */
function servedModels(): string[] {
  const found: string[] = [];
  const walk = (folder: string, recursive: boolean, keep: (path: string) => boolean) => {
    for (const entry of readdirSync(join(ASSETS, folder), { withFileTypes: true })) {
      const path = `${folder}/${entry.name}`;
      if (entry.isDirectory()) {
        if (recursive) walk(path, true, keep);
      } else if (/\.(gltf|glb)$/i.test(entry.name) && keep(path)) {
        found.push(path);
      }
    }
  };
  walk("models", true, () => true);
  walk("characters", false, () => true);
  walk("thirdparty", true, (path) => path.includes("/Models/"));
  return found.sort();
}

/** The glTF JSON of a .gltf or a .glb file. */
function gltfJson(bytes: Buffer, path: string): { buffers?: { uri?: string }[]; images?: { uri?: string }[] } {
  if (path.toLowerCase().endsWith(".gltf")) return JSON.parse(bytes.toString("utf8"));
  const length = bytes.readUInt32LE(12);
  return JSON.parse(bytes.subarray(20, 20 + length).toString("utf8"));
}

/** Whether every buffer and image of the model is inside the file (no uri, or a data: uri). */
function standsAlone(bytes: Buffer, path: string): boolean {
  const gltf = gltfJson(bytes, path);
  return [...(gltf.buffers ?? []), ...(gltf.images ?? [])].every((item) => item.uri === undefined || item.uri.startsWith("data:"));
}

function sceneWith(paths: string[]): SceneFile {
  return {
    schemaVersion: 1,
    name: "Models",
    entities: paths.map((path, i) => ({
      id: `model-${i}`,
      name: path,
      active: true,
      parentId: null,
      transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
      components: [{ type: "ModelComponent", enabled: true, data: { model: path, importScale: 100 } }],
    })),
  } as unknown as SceneFile;
}

describe("the models the web build serves", () => {
  const all = servedModels();
  const alone = all.filter((path) => standsAlone(readFileSync(join(ASSETS, path)), path));
  // One file per distinct content: the same bytes load the same way under any name
  const byContent = new Map<string, string>();
  for (const path of alone) {
    const hash = createHash("sha256").update(readFileSync(join(ASSETS, path))).digest("hex");
    if (!byContent.has(hash)) byContent.set(hash, path);
  }
  const distinct = [...byContent.values()];

  it("include the starter pack's Kenney models, which a refreshed sample places", () => {
    for (const model of ["tree_oak.glb", "tree_pineTallA.glb", "rock_tallA.glb", "plant_bushLarge.glb"]) {
      expect(alone).toContain(`thirdparty/kenney/nature-kit/Models/GLTF format/${model}`);
    }
    expect(all.length).toBeGreaterThan(alone.length);
  });

  it("each load into an entity with meshes, when they name no outside file", async () => {
    for (let start = 0; start < distinct.length; start += BATCH) {
      const batch = distinct.slice(start, start + BATCH);
      const engine = createHeadlessEngine();
      const built = buildScene(engine, sceneWith(batch));
      const failures = await loadPendingModels(built, (path) => {
        const mime = path.toLowerCase().endsWith(".glb") ? "model/gltf-binary" : "model/gltf+json";
        return `data:${mime};base64,${readFileSync(join(ASSETS, path)).toString("base64")}`;
      });
      expect(failures).toEqual([]);
      for (const pending of built.pendingModels) {
        expect(pending.entity.meshes.some((mesh) => mesh.getTotalVertices() > 0), pending.path).toBe(true);
      }
      built.scene.dispose();
      engine.dispose();
    }
  }, 600_000);
});
