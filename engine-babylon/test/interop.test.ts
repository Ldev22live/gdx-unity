import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { EditorModel, emptyScene } from "../src/editor/EditorModel";
import {
  evaluate, manifestToJson, markerText, nextManifest, parseManifest, parseMarker, sha256Hex, verifyAssets, type ProjectManifest,
} from "../src/project/contract";
import { compareScenes, DESKTOP_NORMALIZATIONS } from "../src/scene/semanticDiff";
import { parseSceneFile, type EntityData } from "../src/scene/types";
import { copyProject, ids, INTEROP_DIR, matchOrWrite, reader, writeFiles } from "./projectFiles";

/**
 * Web half of the cross-platform round trips (TD-106, roadmap A12.10); the desktop half is CrossPlatformInteropTest. Each step reads
 * the folder the other client wrote under fixtures/contract-v1/interop/ and writes its own, which must equal the committed one.
 */
const SCENE = "scenes/valley.json";
const temp = () => mkdtempSync(join(tmpdir(), "interop-"));
const fixedNow = (iso: string) => () => new Date(iso);
const manifestAt = (root: string): ProjectManifest => parseManifest(readFileSync(join(root, "project-manifest.json"), "utf8"));
const sceneAt = (root: string, path = SCENE): string => readFileSync(join(root, path), "utf8");
const entity = (model: EditorModel, name: string): EntityData => model.file.entities.find((e) => e.name === name)!;

/** Opens a project as the web editor does: manifest read, every hash checked, marker agreeing, capabilities evaluated. */
async function open(root: string) {
  const manifest = manifestAt(root);
  expect(await verifyAssets(manifest, reader(root)), "every hash matches").toEqual([]);
  const marker = parseMarker(readFileSync(join(root, "gdxunity.project"), "utf8"));
  expect([marker["projectId"], marker["name"], marker["startScene"]]).toEqual([manifest.projectId, manifest.name, manifest.startScene]);
  return { manifest, evaluation: evaluate(manifest) };
}

describe("cross-platform round trips (web half)", () => {
  it("step 2: the web opens the desktop's save, edits supported fields and saves a new revision", async () => {
    const root = copyProject(join(INTEROP_DIR, "1-desktop"), join(temp(), "p2"));
    const { manifest, evaluation } = await open(root);
    expect(manifest.revision.client).toBe("desktop");
    expect(evaluation.access).toBe("limited");
    expect(evaluation.missingOptional).toEqual(["profile:action-rpg/3d"]);

    const original = sceneAt(root);
    const model = new EditorModel(parseSceneFile(original));
    const house = entity(model, "House");
    model.setTransform(house.id, { ...house.transform, position: { ...house.transform.position, x: 720 } });
    model.rename(entity(model, "Rock").id, "Boulder", false);
    const saved = model.serialize();

    expect(compareScenes(original, saved).map((d) => d.path)).toEqual([
      `entities[${house.id} House].transform.position.x`, `entities[${entity(model, "Boulder").id} Rock].name`]);
    const next = await nextManifest(manifest, manifest.revision.id, { [SCENE]: saved },
      { newId: ids("2-web"), now: fixedNow("2026-11-25T14:10:00Z"), clientVersion: "web-interop" });
    writeFiles(root, { [SCENE]: saved, "project-manifest.json": manifestToJson(next), "gdxunity.project": markerText(next) });

    matchOrWrite(root, join(INTEROP_DIR, "2-web"));
  });

  it("step 4: the web reopens the desktop's advanced save with both clients' edits and nothing lost", async () => {
    const root = join(INTEROP_DIR, "3-desktop");
    const { manifest } = await open(root);
    expect(manifest.revision.client).toBe("desktop");
    expect(manifest.revision.base).toBe(manifestAt(join(INTEROP_DIR, "2-web")).revision.id);
    const text = sceneAt(root);
    const model = new EditorModel(parseSceneFile(text));

    expect(entity(model, "House").transform.position.x).toBe(720);
    expect(entity(model, "Boulder")).toBeDefined();
    const owner = (name: string) => entity(model, name).components.find((c) => c.type === "OwnerComponent")!.data["player"];
    expect(owner("Tower"), "the desktop's ownership edit").toBe(owner("House"));
    expect(entity(model, "Sun").components[0]!.data["intensity"]).toBe(1.5);
    expect(entity(model, "Hero").components.map((c) => c.type)).toContain("ExampleFutureComponent");
    const file = JSON.parse(text) as Record<string, unknown>;
    expect(file["scenario"]).toBeDefined();
    // The desktop's scenario edits through its Players and Rules editors (A04, A05; roadmap A15) arrive intact
    const scenario = file["scenario"] as { players: { name: string }[]; rules: { name: string; when: { seconds?: number } }[] };
    expect(scenario.players[1]!.name).toBe("Raiders");
    expect(scenario.rules[0]!.name).toBe("Welcome");
    expect(scenario.rules[0]!.when.seconds).toBe(3);
    expect(file["exampleFutureSetting"]).toEqual({ kept: true });
    expect(compareScenes(text, model.serialize())).toEqual([]);
    // Against the original fixture, only the two clients' six edits differ (and the desktop's documented normalizations)
    const fixture = readFileSync(join(INTEROP_DIR, "..", "valley-project", SCENE), "utf8");
    expect(compareScenes(fixture, text, DESKTOP_NORMALIZATIONS).map((d) => d.path.replace(/\[[0-9a-f-]{36} /, "[")).sort()).toEqual([
      "entities[House].transform.position.x", "entities[Rock].name",
      "entities[Sun].components[DirectionalLightComponent#0].data.intensity",
      "entities[Tower].components[OwnerComponent#0].data.player", "scenario.players[1].name", "scenario.rules[0].when.seconds"]);
  });

  it("step r1: the web creates a project of its own", async () => {
    const root = temp();
    const next = ids("r1-web");
    const projectId = next();
    const player = next();
    const model = new EditorModel(emptyScene("Web Valley"));
    const crate = next();
    model.edit("Create", "scene", (file) => {
      file.entities.push({
        id: crate, name: "Crate", active: true, parentId: null,
        transform: { position: { x: 0, y: 50, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
        components: [
          { type: "MeshPrimitiveComponent", enabled: true, data: { shape: "BOX", sizeX: 100, sizeY: 100, sizeZ: 100, colorR: 0.7, colorG: 0.5,
            colorB: 0.3, colorA: 1 } },
          { type: "OwnerComponent", enabled: true, data: { player } },
        ],
      });
      (file as unknown as Record<string, unknown>)["scenario"] = {
        version: 1, players: [{ id: player, name: "Player 1", color: "#2f6fdf" }], objectives: [], rules: [],
      };
    });
    const text = model.serialize();
    const scenePath = "scenes/main.json";
    const bytes = new TextEncoder().encode(text);
    const manifest: ProjectManifest = {
      projectId, name: "Web Valley", startScene: scenePath, scenes: [{ id: next(), path: scenePath }],
      assets: [{ path: scenePath, kind: "scene", size: bytes.length, sha256: await sha256Hex(bytes) }],
      required: ["scene/1", "preserve-unknown/1", "scenario/1", "ownership/1"], optional: [],
      revision: { id: next(), base: null, client: "web", clientVersion: "web-interop", savedAt: "2026-11-25T14:30:00.000Z" },
      extensions: {},
    };
    writeFiles(root, { [scenePath]: text, "project-manifest.json": manifestToJson(manifest), "gdxunity.project": markerText(manifest) });
    expect((await open(root)).evaluation.access).toBe("full");

    matchOrWrite(root, join(INTEROP_DIR, "r1-web"));
  });

  it("step r3: the web reopens its project after a desktop save", async () => {
    const root = join(INTEROP_DIR, "r2-desktop");
    const { manifest } = await open(root);
    expect(manifest.revision.client).toBe("desktop");
    expect(manifest.revision.base).toBe(manifestAt(join(INTEROP_DIR, "r1-web")).revision.id);
    expect(manifest.projectId).toBe(manifestAt(join(INTEROP_DIR, "r1-web")).projectId);
    const text = sceneAt(root, manifest.startScene);
    const model = new EditorModel(parseSceneFile(text));
    expect(entity(model, "Crate").transform.scale).toEqual({ x: 2, y: 2, z: 2 });
    expect(entity(model, "Crate").components.map((c) => c.type)).toEqual(["MeshPrimitiveComponent", "OwnerComponent"]);
    expect(compareScenes(sceneAt(join(INTEROP_DIR, "r1-web"), manifest.startScene), text, DESKTOP_NORMALIZATIONS)
      .map((d) => d.path.replace(/\[[0-9a-f-]{36} /, "[")))
      .toEqual(["entities[Crate].transform.scale.x", "entities[Crate].transform.scale.y", "entities[Crate].transform.scale.z"]);
    expect(compareScenes(text, model.serialize())).toEqual([]);
  });
});
