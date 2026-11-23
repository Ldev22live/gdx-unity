import "@babylonjs/loaders/glTF/2.0";
import { ImportMeshAsync, type AbstractMesh } from "@babylonjs/core";
import type { BuiltScene } from "./SceneBuilder";

/** Where a scene file's model path (for example `models/landscape/pine-tree.gltf`) is found by the browser. */
export type ModelSource = (path: string) => string;

/** The page serves the desktop's model files next to the scene files (`npm run sync-samples` copies them). */
export const defaultModelSource: ModelSource = (path) => path;

/** What went wrong loading one model; the scene still shows everything else. */
export interface ModelFailure {
  entityName: string;
  path: string;
  message: string;
}

/**
 * Loads the glTF models the scene's Model components name and puts them under their entities: the model's own meshes
 * become the entity's meshes, so they are picked, outlined and moved with it. glTF files are in metres; the component's
 * import scale (100 units per metre by default) is applied to a holder node under the entity.
 *
 * Models load together and one that fails (a missing file, a damaged one) is reported, not thrown: the rest still load.
 * Nothing is added when the scene was disposed while the models were loading (an editor rebuild).
 */
export async function loadPendingModels(built: BuiltScene, source: ModelSource = defaultModelSource): Promise<ModelFailure[]> {
  const failures: ModelFailure[] = [];
  await Promise.all(
    built.pendingModels.map(async (pending) => {
      try {
        const url = source(pending.path);
        const extension = pending.path.slice(pending.path.lastIndexOf(".")).toLowerCase();
        const result = await ImportMeshAsync(url, built.scene, { pluginExtension: extension });
        if (built.scene.isDisposed) return;
        const root = result.meshes[0];
        if (!root) throw new Error("the file has no meshes");
        root.parent = pending.holder;
        for (const mesh of result.meshes as AbstractMesh[]) {
          mesh.metadata = { ...(mesh.metadata ?? {}), entityId: pending.entity.id };
          (pending.entity.meshes as AbstractMesh[]).push(mesh);
          mesh.receiveShadows = true;
        }
      } catch (error) {
        failures.push({
          entityName: pending.entity.name,
          path: pending.path,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }),
  );
  built.modelFailures.push(...failures);
  return failures;
}
