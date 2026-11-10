import { Quaternion, Scene, TransformNode, Vector3, NullEngine } from "@babylonjs/core";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { CameraRig, forward, pitchOf, yawOf } from "../src/runtime/CameraRig";
import { GameEntity, GameScene } from "../src/runtime/GameEntity";
import { buildScene } from "../src/runtime/SceneBuilder";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import { createHeadlessEngine, loadSample } from "./support";
import { createDefaultRegistry } from "../src/scripts/registry";
import type { ComponentData, EntityData } from "../src/scene/types";

function entityData(name: string, components: ComponentData[], at: [number, number, number]): EntityData {
  return {
    id: name,
    name,
    active: true,
    parentId: null,
    transform: { position: { x: at[0], y: at[1], z: at[2] }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
    components,
  };
}

/** A scene of bare entities with their nodes placed, enough for rigs and physics. */
function sceneOf(engine: NullEngine, entities: { name: string; at: [number, number, number]; components?: ComponentData[] }[]): GameScene {
  const babylon = new Scene(engine);
  babylon.useRightHandedSystem = true;
  return new GameScene(
    entities.map((entity) => {
      const node = new TransformNode(entity.name, babylon);
      node.position.set(...entity.at);
      return new GameEntity(entityData(entity.name, entity.components ?? [], entity.at), node);
    }),
  );
}

function rigData(overrides: Record<string, unknown>): Record<string, unknown> {
  return { mode: "FOLLOW", targetName: "Hero", pivotHeight: 0, ...overrides };
}

function lookedAt(entity: GameEntity): Vector3 {
  return new Vector3(0, 0, -1).rotateByQuaternionToRef(entity.getWorldRotation(), new Vector3());
}

describe("forward, yawOf and pitchOf", () => {
  it("shouldGiveTheDirectionACameraLooksAlongAndBeInvertedByYawOfAndPitchOf", () => {
    expect(forward(0, 0).toString()).toBe(new Vector3(0, 0, -1).toString());
    expect(forward(90, 0).x).toBeCloseTo(-1, 5);
    expect(forward(0, 30).y).toBeCloseTo(0.5, 5);
    for (const [yaw, pitch] of [[0, 0], [37, 12], [-120, -45], [170, 60]] as const) {
      const direction = forward(yaw, pitch);
      expect(yawOf(direction), `yaw of ${yaw}, ${pitch}`).toBeCloseTo(yaw, 3);
      expect(pitchOf(direction), `pitch of ${yaw}, ${pitch}`).toBeCloseTo(pitch, 3);
    }
  });
});

describe("a Camera Rig", () => {
  const engine = createHeadlessEngine();

  it("shouldSitAtTheFollowOffsetAtOnceAndLookAtTheTarget", () => {
    const scene = sceneOf(engine, [{ name: "Hero", at: [100, 0, -50] }, { name: "Camera", at: [0, 0, 0] }]);
    const camera = scene.findByName("Camera")!;
    const rig = new CameraRig(camera, rigData({ followOffsetX: 0, followOffsetY: 420, followOffsetZ: 560 }));
    rig.start((name) => scene.findByName(name));

    rig.update(1 / 60, null);

    expect(camera.getWorldPosition().toString()).toBe(new Vector3(100, 420, 510).toString());
    const toHero = new Vector3(100, 0, -50).subtract(camera.getWorldPosition()).normalize();
    expect(Vector3.Dot(lookedAt(camera), toHero), "looking straight at the target").toBeGreaterThan(0.9999);
  });

  it("shouldCloseTheSameShareOfTheGapInASecondAtThirtyAndSixtyFramesPerSecond", () => {
    const at = (fps: number): number => {
      const scene = sceneOf(engine, [{ name: "Hero", at: [0, 0, 0] }, { name: "Camera", at: [0, 0, 0] }]);
      const camera = scene.findByName("Camera")!;
      const rig = new CameraRig(camera, rigData({ followOffsetX: 0, followOffsetY: 0, followOffsetZ: 100, followRate: 6 }));
      rig.start((name) => scene.findByName(name));
      rig.update(1 / fps, null);
      scene.findByName("Hero")!.node.position.set(600, 0, 0);
      for (let i = 0; i < fps; i++) rig.update(1 / fps, null);
      return camera.getWorldPosition().x;
    };

    expect(at(60)).toBeCloseTo(at(30), 1);
    expect(at(60) / 600, "1 - exp(-6)").toBeCloseTo(1 - Math.exp(-6), 2);
  });

  it("shouldSitBehindAndAboveTheTargetInOrbitModeAndLookAlongItsYawAndPitch", () => {
    const scene = sceneOf(engine, [{ name: "Hero", at: [0, 0, 0] }, { name: "Camera", at: [0, 0, 0] }]);
    const camera = scene.findByName("Camera")!;
    const rig = new CameraRig(camera, rigData({ mode: "ORBIT", pivotHeight: 80, distance: 400, yaw: 0, pitch: -20 }));
    rig.start((name) => scene.findByName(name));

    rig.update(1 / 60, null);

    const position = camera.getWorldPosition();
    expect(position.z, "behind the character, which faces -Z").toBeCloseTo(400 * Math.cos((20 * Math.PI) / 180), 3);
    expect(position.y, "above it").toBeCloseTo(80 + 400 * Math.sin((20 * Math.PI) / 180), 3);
    expect(Vector3.Dot(lookedAt(camera), forward(0, -20))).toBeGreaterThan(0.99999);
  });

  it("shouldTurnWithRotateAndHoldPitchAndDistanceWithinTheirLimits", () => {
    const scene = sceneOf(engine, [{ name: "Hero", at: [0, 0, 0] }, { name: "Camera", at: [0, 0, 0] }]);
    const rig = new CameraRig(scene.findByName("Camera")!, rigData({ mode: "ORBIT", yaw: 170, pitch: 0 }));

    rig.rotate(30, 500);
    expect(rig.yaw, "170 + 30 wraps to -160").toBeCloseTo(-160, 5);
    expect(rig.pitch, "held at the default limit of 60").toBe(60);
    rig.setPitchLimits(-10, 10);
    expect(rig.pitch).toBe(10);
    expect(() => rig.setPitchLimits(-95, 10)).toThrow(/-89/);
    rig.setDistance(5000);
    expect(rig.distance, "held at 1200").toBe(1200);
    rig.setDistance(1);
    expect(rig.distance, "held at 100").toBe(100);
    expect(() => rig.setYaw(Number.NaN)).toThrow(/finite/);
  });

  it("shouldSitAtTheEyesInFirstPersonModeAndLookWhereTheTargetFacesTurnedByTheRigsYaw", () => {
    const scene = sceneOf(engine, [{ name: "Hero", at: [10, 0, 20] }, { name: "Camera", at: [0, 0, 0] }]);
    const hero = scene.findByName("Hero")!;
    hero.node.rotationQuaternion = Quaternion.RotationYawPitchRoll((90 * Math.PI) / 180, 0, 0);
    const camera = scene.findByName("Camera")!;
    const rig = new CameraRig(camera, rigData({ mode: "FIRST_PERSON", eyeHeight: 70, yaw: 15, pitch: 10 }));
    rig.start((name) => scene.findByName(name));

    rig.update(1 / 60, null);

    expect(camera.getWorldPosition().toString()).toBe(new Vector3(10, 70, 20).toString());
    expect(Vector3.Dot(lookedAt(camera), forward(105, 10)), "the target faces 90 degrees, the rig adds 15").toBeGreaterThan(0.99999);
  });

  it("shouldNameTheRigsEntityWhenTheTargetDoesNotExistAndStayIdleWithNoTargetName", () => {
    const scene = sceneOf(engine, [{ name: "Camera", at: [3, 4, 5] }]);
    const camera = scene.findByName("Camera")!;

    expect(() => new CameraRig(camera, rigData({ targetName: "Nobody" })).start((name) => scene.findByName(name))).toThrow(/Camera Rig on Camera follows 'Nobody'/);
    const idle = new CameraRig(camera, rigData({ targetName: "" }));
    idle.start((name) => scene.findByName(name));
    idle.update(1 / 60, null);

    expect(idle.getTarget()).toBeNull();
    expect(camera.getWorldPosition().toString()).toBe(new Vector3(3, 4, 5).toString());
  });
});

describe("a Camera Rig that keeps out of walls", () => {
  let jolt: JoltModule;
  const made: Physics3D[] = [];

  beforeAll(async () => {
    jolt = await loadJolt();
  });

  afterEach(() => {
    made.splice(0).forEach((physics) => physics.dispose());
  });

  function wall(at: [number, number, number]): { name: string; at: [number, number, number]; components: ComponentData[] } {
    return {
      name: "Wall",
      at,
      components: [{ type: "BoxCollider3DComponent", enabled: true, data: { sizeX: 2000, sizeY: 2000, sizeZ: 20, offsetX: 0, offsetY: 0, offsetZ: 0, trigger: false } }],
    };
  }

  function orbit(occlusion: boolean): number {
    const engine2 = createHeadlessEngine();
    const scene = sceneOf(engine2, [{ name: "Hero", at: [0, 0, 0] }, { name: "Camera", at: [0, 0, 0] }, wall([0, 0, 300])]);
    const physics = new Physics3D(scene, new JoltPhysicsBackend3D(jolt));
    made.push(physics);
    physics.update(1 / 60);
    const camera = scene.findByName("Camera")!;
    const rig = new CameraRig(camera, rigData({ mode: "ORBIT", pivotHeight: 0, distance: 500, yaw: 0, pitch: 0, occlusionEnabled: occlusion, occlusionMargin: 20 }));
    rig.start((name) => scene.findByName(name));
    rig.update(1 / 60, physics);
    return camera.getWorldPosition().z;
  }

  it("shouldStopInFrontOfAWallBehindTheTargetByTheMarginAndNotWhenOcclusionIsOff", () => {
    expect(orbit(false), "no occlusion: the full 500").toBeCloseTo(500, 3);
    expect(orbit(true), "the wall's front face is at z = 290, minus the 20 unit margin").toBeCloseTo(270, 0);
  });
});

describe("the character playground sample on the web", () => {
  it("shouldBuildWithEveryComponentSupportedAndAScriptForTheHero", () => {
    const built = buildScene(createHeadlessEngine(), loadSample("character-playground-3d.json"));
    const registry = createDefaultRegistry();

    expect([...built.unsupported]).toEqual([]);
    expect(registry.has("samples.walker.WalkerPlayer")).toBe(true);
    expect(built.game.findByName("Main Camera")!.getComponents("CameraRigComponent")).toHaveLength(1);
  });
});
