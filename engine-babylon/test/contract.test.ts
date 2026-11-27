import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { EditorModel } from "../src/editor/EditorModel";
import { buildScene } from "../src/runtime/SceneBuilder";
import {
  checkPath, ContractError, describeUnsupported, evaluate, manifestToJson, nextManifest, parseManifest, RevisionConflict, verifyAssets,
} from "../src/project/contract";
import { compareScenes, DESKTOP_NORMALIZATIONS } from "../src/scene/semanticDiff";
import { parseSceneFile } from "../src/scene/types";
import { CONTRACT_DIR, ids, reader } from "./projectFiles";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

/** The shared project contract in the web editor (TD-106, roadmap A12.9): real files, the real editor model, no mocks. */
const PROJECT = join(CONTRACT_DIR, "valley-project");
const manifestText = () => readFileSync(join(PROJECT, "project-manifest.json"), "utf8");
const sceneText = () => readFileSync(join(PROJECT, "scenes", "valley.json"), "utf8");
const options = { newId: ids("contract-test"), now: () => new Date("2026-11-25T15:00:00Z"), clientVersion: "web-test" };

describe("project manifest on the web", () => {
  it("reads the fixture project, checks every hash and finds the optional profile it cannot run", async () => {
    const manifest = parseManifest(manifestText());
    expect(manifest.name).toBe("Contract Valley");
    expect(await verifyAssets(manifest, reader(PROJECT))).toEqual([]);
    const evaluation = evaluate(manifest);
    expect(evaluation.access).toBe("limited");
    expect(evaluation.explanations[0]).toContain("profile:action-rpg/3d");
    expect(manifestToJson(parseManifest(manifestToJson(manifest)))).toBe(manifestToJson(manifest));
  });

  it("refuses a newer version, an unknown member, a bad hash and unsafe paths", () => {
    const invalid = (name: string) => readFileSync(join(CONTRACT_DIR, "invalid", name), "utf8");
    expect(() => parseManifest(invalid("future-version.manifest.json"))).toThrow(/contract version 2 needs a newer editor/);
    expect(() => parseManifest(invalid("path-traversal.manifest.json"))).toThrow(/leaves the project folder/);
    expect(() => parseManifest(manifestText().replace('"name"', '"owner": 1, "name"'))).toThrow(/unknown member 'owner'/);
    expect(() => parseManifest(manifestText().replace(/"sha256": "[0-9a-f]/, '"sha256": "Z'))).toThrow(ContractError);
    for (const bad of ["../x", "/etc/passwd", "C:/Windows", "a\\b", "a//b", "a/%2e%2e/b", "a%2Fb"]) expect(() => checkPath(bad), bad).toThrow(ContractError);
    for (const good of ["materials/Meadow.material", "Models/GLB format/wall.glb", "a/b..c/d%20e.png"]) checkPath(good);
  });

  it("opens read-only when a required capability is missing, and reports missing or modified assets", async () => {
    const manifest = parseManifest(manifestText().replace('"ownership/1"', '"ownership/1", "teleporters/3"'));
    const evaluation = evaluate(manifest);
    expect(evaluation.access).toBe("read-only");
    expect(evaluation.explanations[0]).toMatch(/read-only: it needs teleporters\/3/);
    const files = reader(PROJECT);
    const findings = await verifyAssets(manifest, async (path) => (path === "scenes/valley.json" ? null
      : path === "materials/Meadow.material" ? new TextEncoder().encode("{}") : files(path)));
    expect(findings).toEqual([{ path: "materials/Meadow.material", issue: "modified" }, { path: "scenes/valley.json", issue: "missing" }]);
  });

  it("saves a new revision from the current base, and refuses a stale base as a conflict", async () => {
    const manifest = parseManifest(manifestText());
    const next = await nextManifest(manifest, manifest.revision.id, { "scenes/valley.json": "{}" }, { ...options, addedRequired: ["scene/1", "x-new/1"] });
    expect(next.revision.base).toBe(manifest.revision.id);
    expect(next.revision.client).toBe("web");
    expect(next.projectId).toBe(manifest.projectId);
    expect(next.required).toEqual([...manifest.required, "x-new/1"]);
    expect(next.optional).toEqual(manifest.optional);
    await expect(nextManifest(next, manifest.revision.id, {}, options)).rejects.toBeInstanceOf(RevisionConflict);
  });
});

describe("unknown data in the web editor", () => {
  it("keeps every unknown member, the scenario block and ownership while supported fields are edited", () => {
    const original = sceneText();
    const model = new EditorModel(parseSceneFile(original));
    const hero = model.file.entities.find((e) => e.name === "Hero")!;
    model.setTransform(hero.id, { ...hero.transform, position: { ...hero.transform.position, z: 650 } });
    const sun = model.file.entities.find((e) => e.name === "Sun")!;
    model.setComponentField(sun.id, 0, ["intensity"], 0.8);
    model.undo();
    model.redo();

    const differences = compareScenes(original, model.serialize());
    expect(differences.map((d) => d.path.replace(/\[[0-9a-f-]{36} /, "[")).sort()).toEqual([
      "entities[Hero].transform.position.z", "entities[Sun].components[DirectionalLightComponent#0].data.intensity"]);
    const saved = JSON.parse(model.serialize()) as Record<string, unknown>;
    expect(saved["scenario"]).toEqual((JSON.parse(original) as Record<string, unknown>)["scenario"]);
    expect(saved["exampleFutureSetting"]).toEqual({ kept: true });
  });

  it("builds the fixture scene treating ownership as data and naming only the component it cannot show", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, parseSceneFile(sceneText()));
    // SpawnPointComponent: the fixture's Start (TD-108 spawn); the web reports it unsupported (BACKEND_SUPPORT_MATRIX.md)
    expect([...built.unsupported.keys()].sort()).toEqual(["ExampleFutureComponent", "PlayerComponent", "SpawnPointComponent"]);
    expect(describeUnsupported(built.unsupported)).toMatch(/^Not shown on the web: .*ExampleFutureComponent.*kept unchanged/);
    built.scene.dispose();
    engine.dispose();
  });

  it("keeps the valley's atmosphere data through a web save and says it is not drawn (optional on the web, TD-106)", () => {
    const original = readFileSync(join(SAMPLES_DIR, "action-rpg-valley-3d.json"), "utf8");
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("action-rpg-valley-3d.json"));
    expect(built.unsupported.get("AtmosphereComponent")).toBe(1);
    expect(describeUnsupported(built.unsupported)).toContain("AtmosphereComponent");
    built.scene.dispose();
    engine.dispose();

    const model = new EditorModel(parseSceneFile(original));
    const valley = model.file.entities.find((e) => e.components.some((c) => c.type === "AtmosphereComponent"))!;
    const name = valley.name;
    model.rename(valley.id, `${name} (edited on the web)`, false);
    const differences = compareScenes(original, model.serialize());
    expect(differences.map((d) => d.path.replace(/\[[0-9a-f-]{36} /, "["))).toEqual([`entities[${name}].name`]);
    expect(compareScenes(original, model.serialize(), DESKTOP_NORMALIZATIONS).some((d) => d.path.includes("Atmosphere"))).toBe(false);
  });
});
