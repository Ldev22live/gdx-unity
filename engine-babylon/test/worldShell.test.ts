import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { EditorModel, emptyScene } from "../src/editor/EditorModel";
import { entriesIn, parsePrefab, placePrefab, PREFAB_LINK_TYPE, type PaletteEntry } from "../src/editor/prefabPalette";
import {
  CATEGORIES, genreAndMode, layoutStyle, MIN_VIEWPORT_SHARE, scenarioSummary, VIEW_ANGLES, viewportShare,
} from "../src/editor/worldShell";
import { parseSceneFile } from "../src/scene/types";
import { ids, INTEROP_DIR, matchOrWrite, writeFiles } from "./projectFiles";

/** The desktop's palette prefabs, read from the desktop project (the web build ships copies of the same files). */
const ASSETS = join(import.meta.dirname, "..", "..", "assets");
const prefab = (path: string) => parsePrefab(readFileSync(join(ASSETS, path), "utf8"), path);

describe("web World shell (roadmap A13)", () => {
  it("uses the World shell unless ?layout=classic asks for the earlier page", () => {
    expect(layoutStyle("")).toBe("world");
    expect(layoutStyle("?scene=x.json")).toBe("world");
    expect(layoutStyle("?layout=Classic")).toBe("classic");
    expect(layoutStyle("?layout=other")).toBe("world");
  });

  it("offers the desktop's categories in its order and says plainly what is edited on the desktop for now", () => {
    expect(CATEGORIES.map((c) => c.label)).toEqual(["Map", "Terrain", "Objects", "Buildings", "Characters", "Players", "Rules"]);
    const desktopOnly = CATEGORIES.filter((c) => c.state.kind === "desktop").map((c) => c.id);
    expect(desktopOnly).toEqual([]); // Terrain, Players and Rules are edited on the web since A14
    for (const category of CATEGORIES) {
      if (category.state.kind === "desktop") expect(category.state.note).toMatch(/A14/);
    }
    expect(CATEGORIES.filter((c) => c.prefabFolder).map((c) => c.prefabFolder))
      .toEqual(["prefabs/level", "prefabs/buildings", "prefabs/characters"]);
  });

  it("leaves the map at least 70 % at both target sizes, even with the palette row open", () => {
    for (const [width, height] of [[1280, 720], [1920, 1080]] as const) {
      expect(viewportShare(width, height, false)).toBeGreaterThanOrEqual(MIN_VIEWPORT_SHARE);
      expect(viewportShare(width, height, true)).toBeGreaterThanOrEqual(MIN_VIEWPORT_SHARE);
    }
    // A narrow laptop window keeps most of the map with the palette closed
    expect(viewportShare(1024, 640, false)).toBeGreaterThanOrEqual(MIN_VIEWPORT_SHARE);
    expect(() => viewportShare(0, 720, false)).toThrow(/positive/);
  });

  it("has camera presets that look down for Top, at an angle for Iso and from the front for Perspective", () => {
    expect(VIEW_ANGLES.top.beta).toBeLessThan(0.01);
    expect(VIEW_ANGLES.iso.beta).toBeCloseTo(0.9553, 3); // the isometric elevation, atan(sqrt 2)
    expect(VIEW_ANGLES.perspective.beta).toBeGreaterThan(VIEW_ANGLES.iso.beta);
  });

  it("summarises the scenario read-only and shows the desktop's genre and mode names", () => {
    const valley = readFileSync(join(INTEROP_DIR, "..", "valley-project", "scenes", "valley.json"), "utf8");
    const file = parseSceneFile(valley);
    expect(scenarioSummary(file)).toBe("Scenario version 1: 3 players (Player 1, Player 2, Nature), 1 objective, 2 rules.");
    expect(scenarioSummary(emptyScene())).toBe("This scene has no players or rules yet.");
    const sample = parseSceneFile(readFileSync(join(ASSETS, "samples", "action-rpg-valley-3d.json"), "utf8"));
    expect(genreAndMode(sample)).toEqual({ genre: "Action RPG", mode: "3D" });
    expect(genreAndMode(emptyScene())).toEqual({ genre: "None", mode: "None" });
  });
});

describe("palette prefabs on the web (roadmap A13)", () => {
  it("lists a folder's prefabs by name", () => {
    const index: PaletteEntry[] = [
      { path: "prefabs/level/Rock.prefab", name: "Rock", thumbnail: null },
      { path: "prefabs/buildings/Cottage.prefab", name: "Cottage", thumbnail: "prefabs/buildings/Cottage.png" },
      { path: "prefabs/level/Bush.prefab", name: "Bush", thumbnail: null },
    ];
    expect(entriesIn(index, "prefabs/level").map((e) => e.name)).toEqual(["Bush", "Rock"]);
  });

  it("places the CC0 house as a linked instance of every part, in one undo step, selected", () => {
    const house = prefab("prefabs/buildings/CC0 House.prefab");
    const model = new EditorModel(emptyScene());
    const before = model.file.entities.length;

    const rootId = placePrefab(model, house, "prefabs/buildings/CC0 House.prefab", { x: 300, y: 0, z: -200 });

    const placed = model.file.entities.slice(before);
    expect(placed).toHaveLength(house.entities.length);
    expect(model.selection).toBe(rootId);
    const root = placed.find((e) => e.id === rootId)!;
    expect(root.parentId).toBeNull();
    expect(root.transform.position).toEqual({ x: 300, y: 0, z: -200 });
    const placedIds = new Set(placed.map((e) => e.id));
    for (const entity of placed) {
      expect(house.entities.some((source) => source.id === entity.id), "every entity has a new id").toBe(false);
      if (entity.id !== rootId) expect(placedIds.has(entity.parentId!), "parents point inside the copy").toBe(true);
      const links = entity.components.filter((c) => c.type === PREFAB_LINK_TYPE);
      expect(links).toHaveLength(1);
      expect(links[0]!.data["prefab"]).toBe("prefabs/buildings/CC0 House.prefab");
    }
    expect((root.components.find((c) => c.type === PREFAB_LINK_TYPE)!.data["knownSourceIds"] as string[]).sort())
      .toEqual(house.entities.map((e) => e.id).sort());
    model.undo();
    expect(model.file.entities).toHaveLength(before);
  });

  it("gives a placed prefab's root its owner in the same undo step (TD-108)", () => {
    const tree = prefab("prefabs/level/CC0 Oak Tree.prefab");
    const model = new EditorModel(emptyScene());
    const owner = "11111111-1111-4111-8111-111111111111";

    const rootId = placePrefab(model, tree, "prefabs/level/CC0 Oak Tree.prefab", { x: 0, y: 0, z: 0 }, undefined, owner);

    const owners = model.file.entities.map((e) => e.components.find((c) => c.type === "OwnerComponent")?.data["player"] ?? null);
    expect(owners).toEqual(model.file.entities.map((e) => (e.id === rootId ? owner : null)));
    model.undo();
    expect(model.file.entities).toEqual([]);
  });

  it("refuses a file that is not a prefab", () => {
    expect(() => parsePrefab("{\"kind\": \"scene\"}", "x.prefab")).toThrow(/not a version 1 prefab/);
    expect(() => parsePrefab("{\"kind\": \"prefab\", \"schemaVersion\": 1, \"rootId\": \"a\", \"entities\": []}", "y.prefab"))
      .toThrow(/no root entity/);
  });

  it("writes a scene with web-placed prefabs for the desktop to check (step w1)", () => {
    const model = new EditorModel(emptyScene("Web Placed"));
    const next = ids("w1-web");
    placePrefab(model, prefab("prefabs/level/CC0 Oak Tree.prefab"), "prefabs/level/CC0 Oak Tree.prefab", { x: -200, y: 0, z: 0 }, next);
    placePrefab(model, prefab("prefabs/buildings/CC0 House.prefab"), "prefabs/buildings/CC0 House.prefab", { x: 400, y: 0, z: -300 }, next);
    const root = mkdtempSync(join(tmpdir(), "w1-"));
    writeFiles(root, { "scenes/web-placed.json": model.serialize() });

    matchOrWrite(root, join(INTEROP_DIR, "w1-web"));
  });
});
