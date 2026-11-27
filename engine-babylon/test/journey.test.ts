import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { EditorModel } from "../src/editor/EditorModel";
import { assignOwner, playersOf, rulesOf, setMessage } from "../src/editor/scenarioEditing";
import { evaluate, markerText, nextManifest, parseManifest, parseMarker, verifyAssets, manifestToJson, type ProjectManifest } from "../src/project/contract";
import { ScenarioRuntime } from "../src/runtime/ScenarioRuntime";
import { compareScenes, DESKTOP_NORMALIZATIONS } from "../src/scene/semanticDiff";
import { parseSceneFile, type EntityData } from "../src/scene/types";
import { parseTerrainFile } from "../src/terrain/terrain";
import { terrainToJson, TerrainStroke } from "../src/terrain/terrainStroke";
import { copyProject, ids, INTEROP_DIR, matchOrWrite, reader, writeFiles } from "./projectFiles";

/**
 * Roadmap A15, the web half of the cross-platform authoring journey (the desktop half is JourneyInteropTest): j2 sculpts the ground,
 * places an owned object and changes a rule on the desktop's first revision; j4 reopens the desktop's next revision, finds both
 * clients' edits, and plays the scenario to victory.
 */
const SCENE = "scenes/journey.json";
const TERRAIN = "terrains/Level.terrain";
const temp = () => mkdtempSync(join(tmpdir(), "journey-"));
const manifestAt = (root: string): ProjectManifest => parseManifest(readFileSync(join(root, "project-manifest.json"), "utf8"));

async function open(root: string) {
  const manifest = manifestAt(root);
  expect(await verifyAssets(manifest, reader(root)), "every hash matches").toEqual([]);
  const marker = parseMarker(readFileSync(join(root, "gdxunity.project"), "utf8"));
  expect([marker["projectId"], marker["startScene"]]).toEqual([manifest.projectId, manifest.startScene]);
  return { manifest, evaluation: evaluate(manifest) };
}

const named = (entities: EntityData[], name: string) => entities.find((e) => e.name === name)!;

describe("the cross-platform authoring journey (web half, roadmap A15)", () => {
  it("step j2: the web sculpts the ground, places an owned object, changes a rule and saves a revision", async () => {
    const root = copyProject(join(INTEROP_DIR, "j1-desktop"), join(temp(), "j2"));
    const { manifest, evaluation } = await open(root);
    expect(manifest.revision.client).toBe("desktop");
    expect(evaluation.access).not.toBe("readOnly");
    const model = new EditorModel(parseSceneFile(readFileSync(join(root, SCENE), "utf8")));
    const next = ids("j2-web");

    // Sculpt: a hill under the gate's approach
    const ground = parseTerrainFile(readFileSync(join(root, TERRAIN), "utf8"));
    const stroke = new TerrainStroke(ground);
    stroke.begin({ type: "RAISE", radius: 300, strength: 1, inverted: false, surface: 1 }, -200, 0);
    stroke.move(200, 0);
    expect(stroke.end().heights.length).toBeGreaterThan(0);

    // Place an object and give it to Player 2
    const crateId = next();
    model.edit("Place Crate", "scene", (file) => file.entities.push({
      id: crateId, name: "Crate", active: true, parentId: null,
      transform: { position: { x: -300, y: 50, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
      components: [{ type: "MeshPrimitiveComponent", enabled: true, data: { shape: "BOX", sizeX: 100, sizeY: 100, sizeZ: 100, colorR: 0.7,
        colorG: 0.5, colorB: 0.3, colorA: 1, materialPath: "" } }],
    }));
    expect(assignOwner(model, [crateId], playersOf(model.file)[1]!.id)).toEqual({ ok: true });

    // Change a rule: the welcome message
    expect(setMessage(model, String(rulesOf(model.file)[0]!["id"]), 0, "Find the gate, then rest", 4)).toEqual({ ok: true });

    const sceneText = model.serialize();
    const terrainText = terrainToJson(ground);
    const saved = await nextManifest(manifest, manifest.revision.id, { [SCENE]: sceneText, [TERRAIN]: terrainText },
      { newId: next, now: () => new Date("2026-11-27T09:10:00Z"), clientVersion: "web-interop" });
    writeFiles(root, { [SCENE]: sceneText, [TERRAIN]: terrainText, "project-manifest.json": manifestToJson(saved), "gdxunity.project": markerText(saved) });
    expect(await verifyAssets(saved, reader(root))).toEqual([]);

    matchOrWrite(root, join(INTEROP_DIR, "j2-web"));
  });

  it("step j4: the web reopens the desktop's revision with both clients' edits and plays the scenario to victory", async () => {
    const root = join(INTEROP_DIR, "j3-desktop");
    const { manifest } = await open(root);
    expect(manifest.revision.client).toBe("desktop");
    expect(manifest.revision.base).toBe(manifestAt(join(INTEROP_DIR, "j2-web")).revision.id);

    // The desktop's edit: the wall now uses its own carved model, and the original is kept
    const text = readFileSync(join(root, SCENE), "utf8");
    const file = parseSceneFile(text);
    const wallModel = String(named(file.entities, "House Wall").components.find((c) => c.type === "ModelComponent")!.data["model"]);
    expect(wallModel).toMatch(/^models\/edited\/Wall carved/);
    expect(manifest.assets.map((a) => a.path)).toEqual(expect.arrayContaining([wallModel, TERRAIN, SCENE]));

    expect(readFileSync(join(root, "models/wall.glb"))).toEqual(readFileSync(join(INTEROP_DIR, "j1-desktop", "models/wall.glb")));

    // The web's edits survived the desktop: the hill, the owned crate and the changed rule
    const ground = parseTerrainFile(readFileSync(join(root, TERRAIN), "utf8"));
    expect(Math.max(...ground.heights)).toBeGreaterThan(10);
    const webText = readFileSync(join(INTEROP_DIR, "j2-web", SCENE), "utf8");
    const differences = compareScenes(webText, text, DESKTOP_NORMALIZATIONS).map((d) => d.path.replace(/\[[0-9a-f-]{36} /, "["));
    expect(differences, "only the desktop's geometry edit differs from the web's save").toEqual(["entities[House Wall].components[ModelComponent#0].data.model"]);
    const players = (file as unknown as { scenario: { players: { id: string }[] } }).scenario.players;
    expect(named(file.entities, "Crate").components.find((c) => c.type === "OwnerComponent")!.data["player"]).toBe(players[1]!.id);

    // Play the scenario: the changed welcome, then the win when the Hero enters the gate
    const byId = new Map(file.entities.map((e) => [e.id, { id: e.id, components: e.components, parent: null }]));
    const log: string[] = [];
    const runtime = ScenarioRuntime.create((file as unknown as Record<string, unknown>)["scenario"], (id) => byId.get(id), (l) => log.push(l))!;
    expect(runtime.skipped).toEqual([]);
    for (let i = 0; i < 9; i++) runtime.update(0.25);
    expect(runtime.message).toBe("Find the gate, then rest");
    runtime.onContact(byId.get(named(file.entities, "Gate").id)!, byId.get(named(file.entities, "Hero").id)!, "TRIGGER_ENTER");
    runtime.update(0.25);
    expect(runtime.objectiveLines()).toEqual(["[x] Reach the gate"]);
    expect(runtime.result).toBe("VICTORY");
  });
});
