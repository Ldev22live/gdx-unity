import { readdirSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { buildScene } from "../src/runtime/SceneBuilder";
import { parseSceneFile } from "../src/scene/types";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

const sampleFiles = readdirSync(SAMPLES_DIR).filter((name) => name.endsWith(".json"));

describe("parseSceneFile", () => {
  it("shouldRejectAFileWithAnUnknownSchemaVersion", () => {
    expect(() => parseSceneFile(JSON.stringify({ schemaVersion: 2, entities: [] }))).toThrow(/schemaVersion 2/);
  });

  it("shouldRejectAFileWithoutEntities", () => {
    expect(() => parseSceneFile(JSON.stringify({ schemaVersion: 1 }))).toThrow(/entities/);
  });
});

describe("buildScene", () => {
  it("shouldBuildEverySampleSceneOfTheDesktopEditor", () => {
    expect(sampleFiles.length).toBeGreaterThan(0);
    for (const file of sampleFiles) {
      const engine = createHeadlessEngine();
      const sceneFile = loadSample(file);
      const built = buildScene(engine, sceneFile);
      expect(built.game.entities.length, file).toBe(sceneFile.entities.length);
      built.scene.dispose();
      engine.dispose();
    }
  });

  it("shouldSupportEveryComponentTypeUsedByTheSamples", () => {
    for (const file of sampleFiles) {
      const engine = createHeadlessEngine();
      const built = buildScene(engine, loadSample(file));
      expect([...built.unsupported.keys()], file).toEqual([]);
      built.scene.dispose();
      engine.dispose();
    }
  });

  it("shouldLookAlongMinusZLikeTheDesktopCameraWhenTheRotationIsIdentity", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("collector-3d.json"));
    const camera = built.gameCamera!;
    // The sample's camera is turned by its script; reset it to identity to test the base convention
    camera.entity.node.rotationQuaternion!.copyFromFloats(0, 0, 0, 1);
    camera.sync(16 / 9);
    camera.camera.getViewMatrix(true);
    const forward = camera.camera.getForwardRay(1).direction;
    expect(forward.x).toBeCloseTo(0, 5);
    expect(forward.y).toBeCloseTo(0, 5);
    expect(forward.z).toBeCloseTo(-1, 5);
    engine.dispose();
  });

  it("shouldPlaceAnOrthographicCameraInFrontOfTheZeroPlane", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("platformer-2d.json"));
    const camera = built.gameCamera!;
    expect(camera.orthographic).toBe(true);
    camera.sync(2);
    expect(camera.camera.position.z).toBeGreaterThan(100);
    expect(camera.camera.orthoTop).toBe(240);
    expect(camera.camera.orthoRight).toBe(480);
    engine.dispose();
  });

  it("shouldFailNamingTheEntityWhenAParentIsMissing", () => {
    const file = loadSample("physics-sandbox-2d.json");
    file.entities[0]!.parentId = "no-such-entity";
    const engine = createHeadlessEngine();
    expect(() => buildScene(engine, file)).toThrow(/Background.*no-such-entity/);
    engine.dispose();
  });

  it("shouldKeepChildrenInTheirParentsSpace", () => {
    const file = loadSample("arena-fps-3d.json");
    const childIndex = file.entities.findIndex((entity) => entity.parentId !== null);
    expect(childIndex).toBeGreaterThanOrEqual(0);
    const engine = createHeadlessEngine();
    const built = buildScene(engine, file);
    const child = built.game.entities[childIndex]!;
    expect(child.parent).not.toBeNull();
    expect(child.node.parent).toBe(child.parent!.node);
    engine.dispose();
  });
});
