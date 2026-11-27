import { readFileSync } from "node:fs";
import { join } from "node:path";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { EditorModel, emptyScene } from "../src/editor/EditorModel";
import { applyProfileFix, CAMERA_NAME } from "../src/editor/profileFixes";
import { GROUND_ENTITY_NAME, TerrainEditing } from "../src/editor/terrainEditing";
import { buildScene } from "../src/runtime/SceneBuilder";
import { groundProblem, validateProfile } from "../src/runtime/PlayProfiles";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { MeshColliderGeometry } from "../src/runtime/physics3d/ColliderGeometry";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import type { ScenePlayer } from "../src/runtime/ScenePlayer";
import type { TerrainData } from "../src/terrain/terrain";
import { parseSceneFile, type SceneFile } from "../src/scene/types";
import { CONTRACT_DIR } from "./projectFiles";
import { createHeadlessEngine } from "./support";

const SCENE = join(CONTRACT_DIR, "profile-trace", "action-rpg-3d.json");
let jolt: JoltModule;
const made: Physics3D[] = [];

beforeAll(async () => {
  jolt = await loadJolt();
});

afterEach(() => {
  made.splice(0).forEach((physics) => physics.dispose());
});

const check = (file: SceneFile) => {
  const built = buildScene(createHeadlessEngine(), file);
  return { built, problems: validateProfile(built.game, built.gameCamera?.entity ?? null) };
};

describe("web Play's checks and fixes (roadmap A14, TD-110)", () => {
  it("offers the desktop's fixes one at a time, each one undo step, until Play may start", () => {
    const file = parseSceneFile(readFileSync(SCENE, "utf8"));
    const hero = file.entities.find((e) => e.name === "Hero")!;
    hero.name = "Player";
    hero.components = hero.components.filter((c) => c.type !== "PlayerComponent" && c.type !== "CharacterController3DComponent");
    file.entities = file.entities.filter((e) => e.name !== "Main Camera");
    const model = new EditorModel(file);

    let problems = check(model.file).problems;
    expect(problems.map((p) => p.fix?.label)).toEqual(["Make Player the player", "Add a camera"]);
    expect(applyProfileFix(model, problems[0]!.fix!)).toBeNull();
    problems = check(model.file).problems;
    expect(problems.map((p) => p.message)).toEqual(["Player cannot walk: it needs a Character Controller 3D.", "No camera: add a Camera to see the game."]);
    expect(applyProfileFix(model, problems[0]!.fix!)).toBeNull();
    expect(applyProfileFix(model, problems[1]!.fix!)).toBeNull();
    expect(model.file.entities.some((e) => e.name === CAMERA_NAME)).toBe(true);
    expect(check(model.file).problems).toEqual([]);
    model.undo();
    expect(check(model.file).problems.map((p) => p.fix?.kind)).toEqual(["ADD_CAMERA"]);
  });

  it("checks the ground under the player as the desktop does", () => {
    const file = parseSceneFile(readFileSync(SCENE, "utf8"));
    const withGround = check(file).built;
    const physics = new Physics3D(withGround.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
    made.push(physics);
    expect(groundProblem(withGround.game, physics)).toBeNull();

    file.entities = file.entities.filter((e) => e.name !== "Ground");
    const floating = check(file).built;
    const nothing = new Physics3D(floating.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
    made.push(nothing);
    expect(groundProblem(floating.game, nothing)?.message)
      .toBe("Nothing under Hero to stand on: add a Terrain Collider 3D or a Box Collider 3D below it, or move it over the ground.");
  });
});

describe("New Level on the web (roadmap A14)", () => {
  it("makes a flat ground of the chosen size and surface with its entity, in one undo step, and refuses a second", () => {
    const model = new EditorModel(emptyScene());
    const overrides = new Map<string, TerrainData>();
    const player = { terrainOverrides: overrides, redrawTerrain: () => undefined } as unknown as ScenePlayer;
    const terrain = new TerrainEditing(model, player);

    const made = terrain.createGround("SMALL", 2);

    expect(made).toEqual({ path: "terrains/Level.terrain" });
    const ground = model.file.entities.find((e) => e.name === GROUND_ENTITY_NAME)!;
    expect(ground.components.map((c) => c.type)).toEqual(["Terrain", "TerrainCollider3DComponent"]);
    expect(ground.components[0]!.data["terrainPath"]).toBe("terrains/Level.terrain");
    const data = overrides.get("terrains/Level.terrain")!;
    expect([data.resolutionX, data.resolutionZ, data.cellSize, data.name]).toEqual([65, 65, 40, "Level"]);
    expect(data.surfaces!.every((s) => s === 2)).toBe(true);
    expect(terrain.unsavedPaths).toEqual(["terrains/Level.terrain"]);
    expect(terrain.createGround("STANDARD", 1)).toEqual({ reason: "This scene already has a level ground; change its Ground instead" });
    expect(terrain.saveTerrain("terrains/Level.terrain")).toContain('"resolutionX": 65');

    model.undo();
    expect(model.file.entities).toEqual([]);
    expect(terrain.createGround("STANDARD", 0)).toEqual({ path: "terrains/Level 2.terrain" });
    expect(overrides.get("terrains/Level 2.terrain")!.surfaces).toBeNull();
  });
});
