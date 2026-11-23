import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { loadPendingModels } from "../src/runtime/ModelLoader";
import { buildScene } from "../src/runtime/SceneBuilder";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

/** The sculpt exported by the desktop editor (`models/SculptedRock.glb`, TD-038 19-C) loads in the web player like any model. */
const MODEL = join(SAMPLES_DIR, "..", "models", "SculptedRock.glb");

describe("a sculpted model in the web player", () => {
  it("shouldLoadTheExportedGlbIntoItsEntityAtTheSculptedSize", async () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("sculpted-rock-3d.json"));
    expect(built.unsupported.size, "every component of the sample is supported").toBe(0);
    expect(built.pendingModels.map((model) => model.path)).toEqual(["models/SculptedRock.glb"]);
    const dataUrl = `data:model/gltf-binary;base64,${readFileSync(MODEL).toString("base64")}`;

    const failures = await loadPendingModels(built, () => dataUrl);

    expect(failures).toEqual([]);
    const rock = built.game.findByName("Sculpted Rock")!;
    const meshes = rock.meshes.filter((mesh) => mesh.getTotalVertices() > 0);
    expect(meshes.length, "one mesh per 60,000-vertex chunk").toBeGreaterThanOrEqual(1);
    const triangles = meshes.reduce((sum, mesh) => sum + mesh.getTotalIndices() / 3, 0);
    expect(triangles).toBeGreaterThan(20_000);

    // The holder applies the import scale (100): the model's metres become the sculpt's scene units
    rock.node.computeWorldMatrix(true);
    let high = 0;
    for (const mesh of meshes) {
      mesh.computeWorldMatrix(true);
      const box = mesh.getBoundingInfo().boundingBox;
      high = Math.max(high, box.maximumWorld.y - box.minimumWorld.y, box.maximumWorld.x - box.minimumWorld.x);
    }
    expect(high, "about the 120 units across the rock was sculpted at").toBeGreaterThan(80);
    expect(high).toBeLessThan(200);
    engine.dispose();
  });
});
