import { readFileSync } from "node:fs";
import { join } from "node:path";
import { Vector3 } from "@babylonjs/core";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { buildScene } from "../src/runtime/SceneBuilder";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { MeshColliderGeometry } from "../src/runtime/physics3d/ColliderGeometry";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import { ScenarioRuntime } from "../src/runtime/ScenarioRuntime";
import { parseSceneFile } from "../src/scene/types";
import { CONTRACT_DIR } from "./projectFiles";
import { createHeadlessEngine } from "./support";

const STEP = 1 / 60;
/** How fast the test walks the Hero, in scene units per second (a brisk walk at 100 units per metre). */
const WALK_SPEED = 400;

let jolt: JoltModule;
const made: Physics3D[] = [];

beforeAll(async () => {
  jolt = await loadJolt();
});

afterEach(() => {
  made.splice(0).forEach((physics) => physics.dispose());
});

describe("scenario rules in web Play with real Jolt (roadmap A14, TD-109)", () => {
  it("shows the timed message and wins when the Hero walks into the house's door area", () => {
    const text = readFileSync(join(CONTRACT_DIR, "valley-project", "scenes", "valley.json"), "utf8");
    const file = parseSceneFile(text);
    const built = buildScene(createHeadlessEngine(), file);
    const physics = new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
    made.push(physics);
    const log: string[] = [];
    const rules = ScenarioRuntime.create((file as unknown as Record<string, unknown>)["scenario"], (id) => built.game.getById(id),
      (line) => log.push(line))!;
    physics.addContactListener((contact) => rules.onContact(contact.entityA, contact.entityB, contact.kind));
    const step = () => {
      physics.update(STEP);
      rules.update(STEP);
    };

    for (let i = 0; i < Math.round(2.5 / STEP); i++) step();
    expect(rules.message).toBe("Find the house in the valley.");
    expect(rules.result).toBeNull();

    const hero = built.game.findByName("Hero")!;
    const door = built.game.findByName("House Door Area")!.getWorldPosition();
    for (let i = 0; i < Math.round(10 / STEP) && rules.result === null; i++) {
      const toDoor = door.subtract(hero.getWorldPosition());
      toDoor.y = 0;
      physics.moveCharacter(hero, toDoor.length() < 1 ? Vector3.Zero() : toDoor.normalize().scale(WALK_SPEED));
      step();
    }

    expect(rules.objectiveLines()).toEqual(["[x] Reach the house"]);
    expect(rules.result).toBe("VICTORY");
    expect(log.some((line) => line.startsWith("Rule 'Arrived' at"))).toBe(true);
    expect(rules.skipped).toEqual([]);
  });
});
