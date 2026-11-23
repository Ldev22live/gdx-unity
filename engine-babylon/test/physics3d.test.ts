import { beforeAll, describe, expect, it } from "vitest";
import { Vector3 } from "@babylonjs/core";
import { Input } from "../src/runtime/Input";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { Physics3D, type Contact } from "../src/runtime/physics3d/Physics3D";
import { ALL_LAYERS, type BodyDescriptor, type ShapeDescriptor } from "../src/runtime/physics3d/types";
import { SandboxLauncher } from "../src/scripts/sandbox";
import { createHeadlessEngine, loadSample } from "./support";

const STEP = 1 / 60;
/** Jolt lets a resting body sink a little into what it rests on (the desktop's Jolt does the same). */
const REST_TOLERANCE = 0.04;

let jolt: JoltModule;

beforeAll(async () => {
  jolt = await loadJolt();
});

function shape(kind: "BOX" | "SPHERE", size: number, offsetY = 0): ShapeDescriptor {
  return kind === "BOX"
    ? { kind, halfX: size, halfY: size, halfZ: size, offsetX: 0, offsetY, offsetZ: 0 }
    : { kind, radius: size, offsetX: 0, offsetY, offsetZ: 0 };
}

function body(overrides: Partial<BodyDescriptor> & { shapes: ShapeDescriptor[] }): BodyDescriptor {
  return {
    motionType: "DYNAMIC",
    position: { x: 0, y: 0, z: 0 },
    rotation: { x: 0, y: 0, z: 0, w: 1 },
    sensor: false,
    mass: 1,
    linearDamping: 0,
    angularDamping: 0,
    gravityFactor: 1,
    friction: 0.5,
    restitution: 0,
    layer: 0,
    collisionMask: ALL_LAYERS,
    ...overrides,
  };
}

/** A static floor whose top is at y = 0. */
function floor(layer = 0): BodyDescriptor {
  return body({ motionType: "STATIC", position: { x: 0, y: -0.5, z: 0 }, shapes: [{ kind: "BOX", halfX: 50, halfY: 0.5, halfZ: 50, offsetX: 0, offsetY: 0, offsetZ: 0 }], layer });
}

function run(backend: JoltPhysicsBackend3D, seconds: number): void {
  for (let i = 0; i < Math.round(seconds / STEP); i++) backend.step(STEP);
}

function heightOf(backend: JoltPhysicsBackend3D, handle: number): number {
  const position = { x: 0, y: 0, z: 0 };
  backend.getBodyTransform(handle, position, { x: 0, y: 0, z: 0, w: 1 });
  return position.y;
}

describe("JoltPhysicsBackend3D", () => {
  it("shouldSettleASphereOnAFloorAtItsRadius", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    backend.setGravity(0, -9.81, 0);
    backend.addBody(floor());
    const ball = backend.addBody(body({ position: { x: 0, y: 5, z: 0 }, shapes: [shape("SPHERE", 0.5)] }));
    run(backend, 4);
    expect(heightOf(backend, ball)).toBeCloseTo(0.5, 1);
    expect(heightOf(backend, ball)).toBeGreaterThan(0.5 - REST_TOLERANCE);
    backend.dispose();
  });

  it("shouldPlaceAShapeWithAnOffsetFromTheBodyOrigin", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    backend.setGravity(0, -9.81, 0);
    backend.addBody(floor());
    // The sphere sits 1 m above the origin, so the origin comes to rest 1 m lower than the sphere's radius says
    const ball = backend.addBody(body({ position: { x: 0, y: 5, z: 0 }, shapes: [shape("SPHERE", 0.5, 1)] }));
    run(backend, 4);
    expect(heightOf(backend, ball)).toBeCloseTo(-0.5, 1);
    backend.dispose();
  });

  it("shouldLetOnlyBodiesWhoseMasksIncludeEachOthersLayersCollide", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    backend.setGravity(0, -9.81, 0);
    // Layers 9 and 12: past the first few, where a wrong layer table would show up
    backend.addBody(floor(9));
    const meets = backend.addBody(body({ layer: 12, collisionMask: 1 << 9, position: { x: -2, y: 3, z: 0 }, shapes: [shape("SPHERE", 0.5)] }));
    const ignores = backend.addBody(body({ layer: 12, collisionMask: ALL_LAYERS & ~(1 << 9), position: { x: 2, y: 3, z: 0 }, shapes: [shape("SPHERE", 0.5)] }));
    run(backend, 4);
    expect(heightOf(backend, meets)).toBeCloseTo(0.5, 1);
    expect(heightOf(backend, ignores)).toBeLessThan(-5);
    backend.dispose();
  });

  it("shouldReturnTheClosestSolidHitWithItsNormalAndSkipSensorsAndMaskedLayers", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    const solid = backend.addBody(body({ motionType: "STATIC", position: { x: 0, y: 1, z: -10 }, shapes: [shape("BOX", 1)] }));
    const sensor = backend.addBody(body({ motionType: "STATIC", sensor: true, position: { x: 0, y: 1, z: -5 }, shapes: [shape("BOX", 1)] }));
    const hidden = backend.addBody(body({ motionType: "STATIC", layer: 3, position: { x: 0, y: 1, z: -7 }, shapes: [shape("BOX", 1)] }));
    backend.step(STEP);

    const hit = backend.raycast({ x: 0, y: 1, z: 0 }, { x: 0, y: 0, z: -1 }, 50, ALL_LAYERS & ~(1 << 3))!;
    expect(hit.body).toBe(solid);
    expect(hit.distance).toBeCloseTo(9, 2);
    expect(hit.point.z).toBeCloseTo(-9, 2);
    expect(hit.normal.z).toBeCloseTo(1, 2);

    expect(backend.raycast({ x: 0, y: 1, z: 0 }, { x: 0, y: 0, z: -1 }, 50, ALL_LAYERS)!.body).toBe(hidden);
    expect(backend.raycast({ x: 0, y: 1, z: 0 }, { x: 0, y: 0, z: -1 }, 3, ALL_LAYERS)).toBeNull();
    expect(sensor).not.toBe(hit.body);
    backend.dispose();
  });

  it("shouldFindBodiesInASphereEachOnceAndSkipSensors", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    const near = backend.addBody(body({ motionType: "STATIC", position: { x: 1, y: 0, z: 0 }, shapes: [shape("BOX", 0.5), shape("SPHERE", 0.5, 2)] }));
    backend.addBody(body({ motionType: "STATIC", position: { x: 0, y: 0, z: 1 }, sensor: true, shapes: [shape("BOX", 0.5)] }));
    backend.addBody(body({ motionType: "STATIC", position: { x: 30, y: 0, z: 0 }, shapes: [shape("BOX", 0.5)] }));
    backend.step(STEP);

    expect(backend.overlapSphere({ x: 0, y: 0, z: 0 }, 3, ALL_LAYERS)).toEqual([near]);
    expect(backend.overlapSphere({ x: 0, y: 0, z: 0 }, 3, 1 << 7)).toEqual([]);
    backend.dispose();
  });

  it("shouldReportOneContactPerBodyPairWhenTheyTouchAndWhenTheyPartAndWhenOneIsRemoved", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    backend.setGravity(0, -9.81, 0);
    const ground = backend.addBody(floor());
    // A box has several contact points with the floor: still one contact
    const crate = backend.addBody(body({ position: { x: 0, y: 1, z: 0 }, shapes: [shape("BOX", 0.5)] }));
    run(backend, 2);
    const began = backend.drainContactEvents().filter((event) => event.began);
    expect(began).toHaveLength(1);
    expect(new Set([began[0]!.bodyA, began[0]!.bodyB])).toEqual(new Set([ground, crate]));

    backend.removeBody(crate);
    const ended = backend.drainContactEvents();
    expect(ended).toHaveLength(1);
    expect(ended[0]!.began).toBe(false);
    expect(() => backend.removeBody(crate)).toThrow(/No such body/);
    backend.dispose();
  });

  it("shouldChangeAVelocityByTheImpulseOverTheMass", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    backend.setGravity(0, 0, 0);
    const heavy = backend.addBody(body({ mass: 4, shapes: [shape("SPHERE", 0.5)] }));
    backend.addImpulse(heavy, 0, 0, -8);
    const velocity = { x: 0, y: 0, z: 0 };
    backend.getLinearVelocity(heavy, velocity);
    expect(velocity.z).toBeCloseTo(-2, 3);

    // A force over one step: velocity change = force / mass * dt
    backend.setLinearVelocity(heavy, 0, 0, 0);
    backend.addForce(heavy, 8, 0, 0);
    backend.step(STEP);
    backend.getLinearVelocity(heavy, velocity);
    expect(velocity.x).toBeCloseTo((8 / 4) * STEP, 3);
    backend.dispose();
  });

  it("shouldRefuseToWorkAfterItWasDisposedAndAllowDisposingTwice", () => {
    const backend = new JoltPhysicsBackend3D(jolt);
    backend.dispose();
    backend.dispose();
    expect(() => backend.step(STEP)).toThrow(/disposed/);
  });
});

function sandbox(): { built: BuiltScene; physics: Physics3D } {
  const built = buildScene(createHeadlessEngine(), loadSample("physics-sandbox-3d.json"));
  return { built, physics: new Physics3D(built.game, new JoltPhysicsBackend3D(jolt)) };
}

describe("Physics3D on the physics-sandbox-3d sample", () => {
  it("shouldOnlyBeUsedByScenesWithA3DCollider", () => {
    const withColliders = buildScene(createHeadlessEngine(), loadSample("physics-sandbox-3d.json"));
    const without = buildScene(createHeadlessEngine(), loadSample("collector-3d.json"));
    expect(Physics3D.isUsedBy(withColliders.game)).toBe(true);
    expect(Physics3D.isUsedBy(without.game)).toBe(false);
  });

  it("shouldLetEveryBallAndCrateFallOntoTheFloorAndStayOnIt", () => {
    const { built, physics } = sandbox();
    const floorEntity = built.game.findByName("Floor")!;
    const floorTop = floorEntity.getWorldPosition().y + 50;
    const dynamic = built.game.entities.filter((entity) => /^(Ball|Crate) /.test(entity.name));
    expect(dynamic.length).toBe(18);
    const started = dynamic.map((entity) => entity.getWorldPosition().y);

    for (let i = 0; i < 300; i++) physics.update(STEP);

    dynamic.forEach((entity, index) => {
      const y = entity.getWorldPosition().y;
      expect(y, entity.name).toBeLessThan(started[index]! + 1);
      expect(y, entity.name).toBeGreaterThan(floorTop);
      // The last ball lands on top of the crate pyramid, so there is no tight upper bound
      expect(y, entity.name).toBeLessThan(floorTop + 400);
    });
    // A ball of radius 40 units rests with its center 40 units above the floor
    const ball = built.game.findByName("Ball 1")!;
    expect(ball.getWorldPosition().y).toBeCloseTo(floorTop + 40, -1);
    expect(physics.getStepCount()).toBe(300);
    physics.dispose();
  });

  it("shouldTellListenersWhichEntitiesCollidedAndKeepStaticEntitiesWhereTheyAre", () => {
    const { built, physics } = sandbox();
    const contacts: Contact[] = [];
    physics.addContactListener((contact) => contacts.push(contact));
    const wall = built.game.findByName("Wall North")!;
    const wallBefore = wall.getWorldPosition();
    for (let i = 0; i < 200; i++) physics.update(STEP);

    const ballContacts = contacts.filter((c) => c.kind === "COLLISION_ENTER" && [c.entityA.name, c.entityB.name].includes("Ball 1"));
    expect(ballContacts.length).toBeGreaterThan(0);
    expect(wall.getWorldPosition().equals(wallBefore)).toBe(true);
    physics.dispose();
  });

  it("shouldCastRaysAndSphereQueriesInSceneUnits", () => {
    const { built, physics } = sandbox();
    for (let i = 0; i < 200; i++) physics.update(STEP);
    const ball = built.game.findByName("Ball 1")!;
    const at = ball.getWorldPosition();

    const hit = physics.raycast(new Vector3(at.x, at.y + 300, at.z), new Vector3(0, -1, 0), 1000)!;
    expect(hit.entity).toBe(ball);
    expect(hit.distance).toBeCloseTo(300 - 40, -1);
    expect(hit.normal.y).toBeGreaterThan(0.9);

    const near = physics.overlapSphere(at, 30);
    expect(near).toContain(ball);
    expect(near.every((entity, index) => near.indexOf(entity) === index)).toBe(true);
    physics.dispose();
  });

  it("shouldApplyImpulsesAndVelocitiesInSceneUnitsAndRefuseStaticBodies", () => {
    const { built, physics } = sandbox();
    physics.update(STEP);
    const ball = built.game.findByName("Ball 1")!;

    physics.setLinearVelocity(ball, new Vector3(0, 0, 0));
    physics.addImpulse(ball, new Vector3(900, 0, 0));
    expect(physics.getLinearVelocity(ball).x).toBeCloseTo(900, 0);

    const floorEntity = built.game.findByName("Floor")!;
    expect(() => physics.addImpulse(floorEntity, new Vector3(1, 0, 0))).toThrow(/not dynamic/);
    expect(() => physics.setLinearVelocity(floorEntity, new Vector3(1, 0, 0))).toThrow(/static/);
    expect(() => physics.addForce(ball, new Vector3(Number.NaN, 0, 0))).toThrow(/finite/);
    expect(() => physics.raycast(new Vector3(), new Vector3(), 10)).toThrow(/direction/);
    physics.dispose();
  });

  it("shouldStopTakingCallsOnceDisposed", () => {
    const { built, physics } = sandbox();
    physics.dispose();
    expect(() => physics.getLinearVelocity(built.game.findByName("Ball 1")!)).toThrow(/stopped/);
    physics.update(STEP);
  });
});

describe("SandboxLauncher", () => {
  it("shouldAimAtWhatTheCameraLooksAtAndKickItAlongTheViewDirection", () => {
    const { built, physics } = sandbox();
    for (let i = 0; i < 240; i++) physics.update(STEP);
    const camera = built.game.findByName("Main Camera")!;
    const script = new SandboxLauncher();
    const input = new Input();
    script.entity = camera;
    script.scene = built.game;
    script.input = input;
    script.physics3d = physics;
    script.onStart();

    script.onUpdate(STEP);
    const label = built.game.findByName("Help Text")!.getComponent("TextComponent")!.data["text"] as string;
    expect(label).toMatch(/Aiming at: /);
    const aimedName = label.split("Aiming at: ")[1]!;
    expect(aimedName).not.toBe("nothing");

    const aimed = built.game.findByName(aimedName)!;
    if (aimed.getComponent("RigidBody3DComponent")) {
      input.press("Space");
      script.onUpdate(STEP);
      // A kick is a speed change of 900 units per second, whatever the mass
      expect(physics.getLinearVelocity(aimed).length()).toBeGreaterThan(800);
    }
    physics.dispose();
  });

  it("shouldDoNothingWithoutPhysics", () => {
    const built = buildScene(createHeadlessEngine(), loadSample("physics-sandbox-3d.json"));
    const script = new SandboxLauncher();
    script.entity = built.game.findByName("Main Camera")!;
    script.scene = built.game;
    script.input = new Input();
    script.onStart();
    expect(() => script.onUpdate(STEP)).not.toThrow();
  });
});
