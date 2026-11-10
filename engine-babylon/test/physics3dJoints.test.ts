import { Vector3 } from "@babylonjs/core";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { MeshColliderGeometry } from "../src/runtime/physics3d/ColliderGeometry";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import type { SceneFile } from "../src/scene/types";
import { createHeadlessEngine, loadSample } from "./support";

const STEP = 1 / 60;

let jolt: JoltModule;
/** Systems made by a test: each owns a Jolt world, so each is disposed when the test ends. */
const made: Physics3D[] = [];

beforeAll(async () => {
  jolt = await loadJolt();
});

afterEach(() => {
  made.splice(0).forEach((physics) => physics.dispose());
});

function physicsFor(built: BuiltScene): Physics3D {
  const physics = new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
  made.push(physics);
  return physics;
}

function run(physics: Physics3D, seconds: number): void {
  for (let i = 0; i < Math.round(seconds / STEP); i++) physics.update(STEP);
}

function jointSample(): { built: BuiltScene; physics: Physics3D } {
  const built = buildScene(createHeadlessEngine(), loadSample("physics-joints-3d.json"));
  return { built, physics: physicsFor(built) };
}

describe("joints on the physics-joints-3d sample", () => {
  it("shouldMakeEveryJointOfTheSampleWithoutAWarning", () => {
    const { built, physics } = jointSample();

    physics.update(STEP);

    const jointed = built.game.entities.filter((entity) => entity.getComponents("Joint3DComponent").length > 0);
    expect(jointed.length).toBe(8);
    for (const entity of jointed) expect(physics.hasJoint(entity), entity.name).toBe(true);
    expect(physics.getWarnings()).toEqual([]);
  });

  it("shouldSwingTheDoorOpenWithItsMotorAndStopItAtTheUpperLimit", () => {
    const { built, physics } = jointSample();
    const door = built.game.findByName("Door")!;

    run(physics, 1);
    const early = physics.getJointPosition(door);
    run(physics, 6);
    const late = physics.getJointPosition(door);

    expect(early, "30 degrees a second for a second").toBeGreaterThan(15);
    expect(early).toBeLessThan(45);
    expect(late, "stopped at the 100 degree limit").toBeGreaterThan(95);
    expect(late).toBeLessThan(103);
  });

  it("shouldSlideThePlatformAlongItsRailToTheEndOfTravelWithoutLeavingTheAxis", () => {
    const { built, physics } = jointSample();
    const platform = built.game.findByName("Slider Platform")!;
    const start = platform.getWorldPosition();

    run(physics, 3);
    const midway = physics.getJointPosition(platform);
    run(physics, 6);
    const end = physics.getJointPosition(platform);

    expect(midway, "120 units a second for 3 seconds").toBeGreaterThan(300);
    expect(midway).toBeLessThan(380);
    expect(end, "the 700 unit limit").toBeGreaterThan(690);
    expect(end).toBeLessThan(710);
    const at = platform.getWorldPosition();
    expect(Math.abs(at.x - (start.x + end))).toBeLessThan(10);
    expect(Math.abs(at.z - start.z), "off the rail sideways").toBeLessThan(3);
    expect(Math.abs(at.y - start.y), "off the rail up or down").toBeLessThan(6);
  });

  it("shouldKeepThePendulumBobOnItsCircleAndLetItSwingThroughTheBottom", () => {
    const { built, physics } = jointSample();
    const bob = built.game.findByName("Pendulum Bob")!;
    const hinge = bob.getComponents("Joint3DComponent")[0]!.data;
    const pivot = Vector3.TransformCoordinates(new Vector3(Number(hinge.anchorX), Number(hinge.anchorY), Number(hinge.anchorZ)), (bob.node.computeWorldMatrix(true), bob.node.getWorldMatrix()));
    const radius = Vector3.Distance(bob.getWorldPosition(), pivot);
    expect(radius).toBeGreaterThan(300);
    let crossedBottom = false;
    const side = Math.sign(bob.getWorldPosition().x - pivot.x);

    for (let i = 0; i < 60 * 5; i++) {
      physics.update(STEP);
      const at = bob.getWorldPosition();
      expect(Math.abs(Vector3.Distance(at, pivot) - radius) / radius, `radius at step ${i}`).toBeLessThan(0.02);
      if (Math.sign(at.x - pivot.x) === -side) crossedBottom = true;
    }
    expect(crossedBottom, "the bob swung to the other side").toBe(true);
  });

  it("shouldKeepTheChainTogetherAsItFalls", () => {
    const { built, physics } = jointSample();
    const links = [1, 2, 3, 4, 5].map((n) => built.game.findByName(`Chain Link ${n}`)!);
    const gaps = (): number[] => links.slice(1).map((link, i) => Vector3.Distance(link.getWorldPosition(), links[i]!.getWorldPosition()));
    const before = gaps();

    run(physics, 5);

    gaps().forEach((gap, i) => {
      expect(Math.abs(gap - before[i]!) / before[i]!, `link ${i + 2} to link ${i + 1}`).toBeLessThan(0.12);
    });
    expect(links[4]!.getWorldPosition().y, "the chain hangs down").toBeLessThan(links[0]!.getWorldPosition().y);
  });

  it("shouldChangeTheMotorOfARunningJointFromAScript", () => {
    const { built, physics } = jointSample();
    const door = built.game.findByName("Door")!;
    run(physics, 1);

    physics.setJointMotor(door, "POSITION", 40);
    run(physics, 4);

    expect(physics.getJointPosition(door), "driven to 40 degrees").toBeCloseTo(40, -1);
    expect(() => physics.setJointMotor(built.game.findByName("Chain Link 1")!, "VELOCITY", 10)).toThrow(/only a HINGE or SLIDER/);
    expect(() => physics.getJointPosition(built.game.findByName("Chain Link 1")!)).toThrow(/only a HINGE or SLIDER/);
  });

  it("shouldRemoveAJointBeforeItsBodyWhenTheEntityIsSwitchedOff", () => {
    const { built, physics } = jointSample();
    const door = built.game.findByName("Door")!;
    physics.update(STEP);
    expect(physics.hasJoint(door)).toBe(true);

    door.setActive(false);
    physics.update(STEP);

    expect(physics.hasJoint(door)).toBe(false);
    expect(physics.hasBody(door)).toBe(false);
    expect(physics.getWarnings()).toEqual([]);
    expect(() => physics.getJointPosition(door)).toThrow(/has no joint/);
    door.setActive(true);
    physics.update(STEP);
    expect(physics.hasJoint(door), "made again when the entity is active again").toBe(true);
  });
});

function transform(x: number, y: number, z: number): unknown {
  return { position: { x, y, z }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } };
}

function box(name: string, motion: string | null, joint: Record<string, unknown> | null, at: [number, number, number] = [0, 100, 0]): unknown {
  const components: unknown[] = [
    { type: "BoxCollider3DComponent", enabled: true, data: { sizeX: 100, sizeY: 100, sizeZ: 100, offsetX: 0, offsetY: 0, offsetZ: 0, trigger: false } },
  ];
  if (motion) components.push({ type: "RigidBody3DComponent", enabled: true, data: { motionType: motion, mass: 1 } });
  if (joint) components.push({ type: "Joint3DComponent", enabled: true, data: { type: "HINGE", connectedName: "", axisX: 0, axisY: 1, axisZ: 0, ...joint } });
  return { id: name, name, active: true, parentId: null, transform: transform(...at), components };
}

function warningsOf(entities: unknown[]): string[] {
  const scene: SceneFile = { schemaVersion: 1, name: "Joints", active: true, entities: entities as never };
  const built = buildScene(createHeadlessEngine(), scene);
  const physics = physicsFor(built);
  run(physics, 0.2);
  return [...physics.getWarnings()];
}

describe("joints that cannot be made", () => {
  it("shouldNameTheEntityAndTheFixWhenNothingCouldMove", () => {
    const warnings = warningsOf([box("Stuck", "STATIC", {})]);

    expect(warnings).toHaveLength(1);
    expect(warnings[0]).toContain("Stuck");
    expect(warnings[0]).toContain("dynamic");
  });

  it("shouldRefuseAJointToAnEntityThatDoesNotExistAndToItself", () => {
    expect(warningsOf([box("A", "DYNAMIC", { connectedName: "Nowhere" })])[0]).toContain('no entity with that name');
    expect(warningsOf([box("A", "DYNAMIC", { connectedName: "A" })])[0]).toContain("connected to itself");
  });

  it("shouldRefuseAMotorOnABallJointAndLimitsThatDoNotIncludeZero", () => {
    expect(warningsOf([box("Ball", "DYNAMIC", { type: "BALL", motorMode: "VELOCITY", motorTarget: 5, motorMaxForce: 10 })])[0]).toContain("no motor");
    expect(warningsOf([box("Door", "DYNAMIC", { type: "HINGE", limitsEnabled: true, lowerLimit: 10, upperLimit: 90 })])[0]).toContain("include 0");
    expect(warningsOf([box("Door", "DYNAMIC", { type: "HINGE", limitsEnabled: true, lowerLimit: -200, upperLimit: 90 })])[0]).toContain("between -180 and 180");
    expect(warningsOf([box("Rail", "DYNAMIC", { type: "SLIDER", limitsEnabled: true, lowerLimit: 5, upperLimit: 90 })])[0]).toContain("include 0");
  });

  it("shouldSayOnlyOnceAndKeepSimulatingTheOtherBodies", () => {
    const built = buildScene(createHeadlessEngine(), {
      schemaVersion: 1,
      name: "Joints",
      active: true,
      entities: [box("Stuck", "STATIC", {}, [0, 100, 0]), box("Free", "DYNAMIC", null, [300, 500, 0])] as never,
    });
    const physics = physicsFor(built);
    const free = built.game.findByName("Free")!;
    const startedAt = free.getWorldPosition().y;

    run(physics, 1);

    expect(physics.getWarnings()).toHaveLength(1);
    expect(free.getWorldPosition().y, "the free box fell").toBeLessThan(startedAt - 100);
  });

  it("shouldJoinTwoBodiesWithAFixedJointSoTheyFallTogetherAtTheSameDistance", () => {
    const built = buildScene(createHeadlessEngine(), {
      schemaVersion: 1,
      name: "Joints",
      active: true,
      entities: [
        box("Pillar", "STATIC", null, [0, 100, 0]),
        box("Hanging", "DYNAMIC", { type: "FIXED", connectedName: "Pillar", anchorX: 0, anchorY: -50, anchorZ: 0 }, [0, 250, 0]),
      ] as never,
    });
    const physics = physicsFor(built);
    const hanging = built.game.findByName("Hanging")!;

    run(physics, 3);

    expect(physics.hasJoint(hanging)).toBe(true);
    // Held up by the joint, not resting on the pillar: it stays where it was, give or take the solver's sag
    expect(hanging.getWorldPosition().y).toBeGreaterThan(230);
    expect(physics.getWarnings()).toEqual([]);
  });
});
