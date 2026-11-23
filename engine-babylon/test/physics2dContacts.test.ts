import { describe, expect, it } from "vitest";
import { buildScene, type BuiltScene } from "../src/runtime/SceneBuilder";
import { Physics2D, type Contact2D } from "../src/runtime/Physics2D";
import { createHeadlessEngine } from "./support";

const STEP = 1 / 60;

function box(name: string, at: [number, number], size: [number, number], body: "STATIC" | "DYNAMIC", sensor = false): unknown {
  return {
    id: name, name, active: true, parentId: null,
    transform: { position: { x: at[0], y: at[1], z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
    components: [
      { type: "RigidBody2DComponent", enabled: true, data: { bodyType: body } },
      { type: "BoxCollider2DComponent", enabled: true, data: { width: size[0], height: size[1], isSensor: sensor } },
    ],
  };
}

function scene(entities: unknown[]): BuiltScene {
  return buildScene(createHeadlessEngine(), { schemaVersion: 1, name: "Contacts", active: true, entities: entities as never });
}

function run(physics: Physics2D, seconds: number): void {
  for (let i = 0; i < Math.round(seconds / STEP); i++) physics.update(STEP);
}

function describeContacts(contacts: Contact2D[]): string[] {
  return contacts.map((c) => `${c.kind} ${c.entityA.name}/${c.entityB.name}`);
}

describe("Physics2D contact events", () => {
  it("shouldReportASolidLandingOnceWithBothEntities", () => {
    const built = scene([box("Ground", [0, 0], [500, 20], "STATIC"), box("Faller", [0, 80], [20, 20], "DYNAMIC")]);
    const physics = new Physics2D(built.game);
    const heard: Contact2D[] = [];
    physics.addContactListener((contact) => heard.push(contact));

    run(physics, 1.5);

    const enters = heard.filter((c) => c.kind === "COLLISION_ENTER");
    expect(enters, describeContacts(heard).join(", ")).toHaveLength(1);
    expect(new Set([enters[0]!.entityA.name, enters[0]!.entityB.name])).toEqual(new Set(["Ground", "Faller"]));
  });

  it("shouldReportASensorOverlapAsASensorEventAndEndItWhenTheBodyLeaves", () => {
    const built = scene([box("Zone", [0, 0], [200, 200], "STATIC", true), box("Faller", [0, 300], [20, 20], "DYNAMIC")]);
    const physics = new Physics2D(built.game);
    const heard: Contact2D[] = [];
    physics.addContactListener((contact) => heard.push(contact));

    run(physics, 2.5);

    const kinds = heard.map((c) => c.kind);
    expect(kinds, describeContacts(heard).join(", ")).toContain("SENSOR_ENTER");
    expect(kinds, "it fell through the zone and out of the bottom").toContain("SENSOR_EXIT");
    expect(kinds, "a sensor is not solid").not.toContain("COLLISION_ENTER");
  });

  it("shouldDeliverContactsAfterTheStepSoAListenerMayChangeTheScene", () => {
    const built = scene([box("Ground", [0, 0], [500, 20], "STATIC"), box("Faller", [0, 80], [20, 20], "DYNAMIC")]);
    const physics = new Physics2D(built.game);
    physics.addContactListener((contact) => {
      // Deactivating an entity from inside a listener must not break the step that is running
      contact.entityB.setActive(false);
    });

    expect(() => run(physics, 1.5)).not.toThrow();
  });
});
