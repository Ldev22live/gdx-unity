import { readFileSync } from "node:fs";
import { join } from "node:path";
import { Vector3 } from "@babylonjs/core";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { loadPendingModels } from "../src/runtime/ModelLoader";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { MeshColliderGeometry } from "../src/runtime/physics3d/ColliderGeometry";
import { MAX_HULL_POINTS, reduceHullPoints } from "../src/runtime/physics3d/colliderPoints";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

const STEP = 1 / 60;
const MODELS_DIR = join(SAMPLES_DIR, "..", "models");

let jolt: JoltModule;
/** Systems made by a test: each owns a Jolt world, so each is disposed when the test ends. */
const made: Physics3D[] = [];

beforeAll(async () => {
  jolt = await loadJolt();
});

afterEach(() => {
  made.splice(0).forEach((physics) => physics.dispose());
});

function track(physics: Physics3D): Physics3D {
  made.push(physics);
  return physics;
}

/** Serves a bundled model as a data URL, so Babylon loads it in Node without a web server. */
function modelSource(path: string): string {
  const file = join(MODELS_DIR, path.replace(/^models\//, ""));
  return `data:model/gltf+json;base64,${readFileSync(file).toString("base64")}`;
}

describe("reduceHullPoints", () => {
  it("shouldMergeDuplicatePointsAndKeepTheRest", () => {
    const reduced = reduceHullPoints([0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0.00001, 0, 0, 1, 0]);

    expect([...reduced]).toEqual([0, 0, 0, 1, 0, 0, 0, 1, 0]);
  });

  it("shouldKeepTheCornersOfACubeWhenManyInnerPointsAreAdded", () => {
    const points: number[] = [];
    for (const x of [-1, 1]) for (const y of [-1, 1]) for (const z of [-1, 1]) points.push(x, y, z);
    for (let i = 0; i < 500; i++) points.push(Math.sin(i) * 0.5, Math.cos(i * 1.3) * 0.5, Math.sin(i * 0.7) * 0.5);

    const reduced = reduceHullPoints(points);

    expect(reduced.length / 3).toBe(MAX_HULL_POINTS);
    const kept = new Set<string>();
    for (let i = 0; i < reduced.length; i += 3) kept.add(`${reduced[i]},${reduced[i + 1]},${reduced[i + 2]}`);
    for (const x of [-1, 1]) for (const y of [-1, 1]) for (const z of [-1, 1]) expect(kept.has(`${x},${y},${z}`), `corner ${x} ${y} ${z}`).toBe(true);
  });
});

function meshScene(): { built: BuiltScene; physics: Physics3D } {
  const built = buildScene(createHeadlessEngine(), loadSample("physics-mesh-3d.json"));
  const physics = track(new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry((entity) => built.modelFailures.some((f) => f.entityName === entity.name))));
  return { built, physics };
}

describe("hull and mesh colliders on the physics-mesh-3d sample", () => {
  it("shouldWaitForTheTerrainModelThenLetTheBallsRollIntoTheBowlAndStayThere", async () => {
    const { built, physics } = meshScene();
    const terrain = built.game.findByName("Terrain")!;

    // The model has not loaded: the terrain has no body yet, and nothing was refused
    physics.update(STEP);
    expect(physics.hasBody(terrain)).toBe(false);
    expect(physics.getWarnings()).toEqual([]);

    expect(await loadPendingModels(built, modelSource)).toEqual([]);
    physics.update(STEP);
    expect(physics.hasBody(terrain)).toBe(true);

    for (let i = 0; i < 60 * 8; i++) physics.update(STEP);

    const balls = built.game.entities.filter((entity) => /^Ball /.test(entity.name));
    expect(balls).toHaveLength(4);
    // The terrain's height under each ball, from a second world that has no balls in it
    const probe = meshScene();
    probe.built.game.entities.filter((entity) => /^(Ball|Box|Hull Rock) /.test(entity.name)).forEach((entity) => entity.setActive(false));
    await loadPendingModels(probe.built, modelSource);
    probe.physics.update(STEP);
    for (const ball of balls) {
      const at = ball.getWorldPosition();
      const ground = probe.physics.raycast(new Vector3(at.x, 5000, at.z), new Vector3(0, -1, 0), 10000);
      expect(ground?.entity.name, `${ball.name} is over the terrain`).toBe("Terrain");
      // A ball of radius 20 resting on the surface: its center is about a radius above it (a slope adds a little)
      expect(at.y - ground!.point.y, `${ball.name} rests on the terrain`).toBeGreaterThan(15);
      expect(at.y - ground!.point.y, `${ball.name} rests on the terrain`).toBeLessThan(40);
      expect(at.y, `${ball.name} came to rest lower than it started`).toBeLessThan(150);
    }
  });

  it("shouldStopTheHullRocksOnTheTerrainWhereAConvexHullOfTheirBoxSaysTheyRest", async () => {
    const { built, physics } = meshScene();
    await loadPendingModels(built, modelSource);
    const rocks = built.game.entities.filter((entity) => /^Hull Rock /.test(entity.name));
    expect(rocks).toHaveLength(2);

    for (let i = 0; i < 60 * 8; i++) physics.update(STEP);

    for (const rock of rocks) {
      expect(physics.hasBody(rock), rock.name).toBe(true);
      expect(rock.getWorldPosition().y, `${rock.name} fell through`).toBeGreaterThan(-40);
      expect(rock.getWorldPosition().y, `${rock.name} fell towards the ground`).toBeLessThan(500);
    }
    expect(physics.getWarnings()).toEqual([]);
  });

  it("shouldHitTheTerrainMeshWithARayAndGetANormalPointingUp", async () => {
    const { built, physics } = meshScene();
    await loadPendingModels(built, modelSource);
    physics.update(STEP);

    const hit = physics.raycast(new Vector3(0, 2000, 300), new Vector3(0, -1, 0), 4000);

    expect(hit?.entity.name).toBe("Terrain");
    expect(hit!.normal.y).toBeGreaterThan(0.5);
  });
});

describe("refused and missing geometry", () => {
  function sceneWith(entities: unknown[]): BuiltScene {
    return buildScene(createHeadlessEngine(), {
      schemaVersion: 1,
      name: "Refusals",
      active: true,
      entities: entities as never,
    });
  }

  function entity(name: string, shape: string, collider: string, motion: string | null, trigger = false): unknown {
    const components: unknown[] = [
      { type: "MeshPrimitiveComponent", enabled: true, data: { shape, sizeX: 100, sizeY: 100, sizeZ: 100, colorR: 1, colorG: 1, colorB: 1, colorA: 1 } },
      { type: collider, enabled: true, data: { offsetX: 0, offsetY: 0, offsetZ: 0, trigger } },
    ];
    if (motion) components.push({ type: "RigidBody3DComponent", enabled: true, data: { motionType: motion, mass: 1 } });
    return {
      id: name, name, active: true, parentId: null,
      transform: { position: { x: 0, y: 200, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
      components,
    };
  }

  it("shouldRefuseAMeshColliderOnADynamicBodyAndNameTheEntityWhileOthersGoOn", () => {
    const built = sceneWith([
      entity("Bad Terrain", "BOX", "MeshCollider3DComponent", "DYNAMIC"),
      entity("Fine Crate", "BOX", "ConvexHullCollider3DComponent", "DYNAMIC"),
    ]);
    const physics = track(new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry()));

    for (let i = 0; i < 10; i++) physics.update(STEP);

    expect(physics.hasBody(built.game.findByName("Bad Terrain")!)).toBe(false);
    expect(physics.hasBody(built.game.findByName("Fine Crate")!)).toBe(true);
    expect(physics.getWarnings()).toHaveLength(1);
    expect(physics.getWarnings()[0]).toContain("Bad Terrain");
    expect(physics.getWarnings()[0]).toContain("static body");
  });

  it("shouldRefuseHullAndMeshCollidersWhenTheSceneCannotReadMeshesAndSayOnlyOnce", () => {
    const built = sceneWith([entity("Rock", "BOX", "ConvexHullCollider3DComponent", "DYNAMIC")]);
    const physics = track(new Physics3D(built.game, new JoltPhysicsBackend3D(jolt)));

    for (let i = 0; i < 10; i++) physics.update(STEP);

    expect(physics.getWarnings()).toEqual(["Rock: ConvexHullCollider3D refused: this scene cannot read meshes"]);
    expect(physics.hasBody(built.game.findByName("Rock")!)).toBe(false);
  });

  it("shouldRefuseAHullOnAnEntityWithNoModelOrPrimitiveAndNameWhatToAdd", () => {
    const built = sceneWith([
      {
        id: "Empty", name: "Empty", active: true, parentId: null,
        transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
        components: [{ type: "ConvexHullCollider3DComponent", enabled: true, data: {} }],
      },
    ]);
    const physics = track(new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry()));

    physics.update(STEP);

    expect(physics.getWarnings()[0]).toContain("no Model or Mesh Primitive");
  });

  it("shouldMakeAStaticMeshFromAPrimitiveThatADynamicBallRestsOn", () => {
    const built = sceneWith([
      { ...(entity("Slab", "BOX", "MeshCollider3DComponent", "STATIC") as object), transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } } },
      {
        id: "Ball", name: "Ball", active: true, parentId: null,
        transform: { position: { x: 0, y: 300, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
        components: [
          { type: "SphereCollider3DComponent", enabled: true, data: { radius: 20, offsetX: 0, offsetY: 0, offsetZ: 0, trigger: false } },
          { type: "RigidBody3DComponent", enabled: true, data: { motionType: "DYNAMIC", mass: 1 } },
        ],
      },
    ]);
    const physics = track(new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry()));

    for (let i = 0; i < 60 * 3; i++) physics.update(STEP);

    // A 100 unit box has its top at y = 50 and the ball is 20 units in radius
    // (Jolt lets a resting body sink a little into what it rests on: up to 4 units here, as in the other tests)
    const rest = built.game.findByName("Ball")!.getWorldPosition().y;
    expect(rest).toBeGreaterThan(66);
    expect(rest).toBeLessThan(71);
    expect(physics.getWarnings()).toEqual([]);
  });
});
