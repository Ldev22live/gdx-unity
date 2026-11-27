import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { hasTriggerCollider, ScenarioRuntime, type RuleEntity } from "../src/runtime/ScenarioRuntime";
import { parseSceneFile, type EntityData } from "../src/scene/types";
import { CONTRACT_DIR } from "./projectFiles";

/** Plain rule entities from a scene file, with parents linked, as the playing scene's GameEntities provide them. */
function entitiesOf(entities: EntityData[]): Map<string, RuleEntity> {
  const byId = new Map<string, { id: string; components: EntityData["components"]; parent: RuleEntity | null }>();
  for (const entity of entities) byId.set(entity.id, { id: entity.id, components: entity.components, parent: null });
  for (const entity of entities) if (entity.parentId) byId.get(entity.id)!.parent = byId.get(entity.parentId) ?? null;
  return byId;
}

interface Trace {
  scene: string;
  steps: ({ frames: number; seconds: number } | { enter: { area: string; visitor: string } })[];
  expect: { log: string[]; message: string | null; objectiveLines: string[]; result: string | null; skipped: string[] };
}

describe("scenario rules on the web (roadmap A14, TD-109)", () => {
  it("replays the shared trace exactly as the desktop does", () => {
    const tracePath = join(CONTRACT_DIR, "scenario-trace", "valley.json");
    const trace = JSON.parse(readFileSync(tracePath, "utf8")) as Trace;
    const file = parseSceneFile(readFileSync(join(CONTRACT_DIR, "scenario-trace", trace.scene), "utf8"));
    const entities = entitiesOf(file.entities);
    const log: string[] = [];
    const runtime = ScenarioRuntime.create((file as unknown as Record<string, unknown>)["scenario"], (id) => entities.get(id), (l) => log.push(l))!;

    for (const step of trace.steps) {
      if ("enter" in step) runtime.onContact(entities.get(step.enter.area)!, entities.get(step.enter.visitor)!, "TRIGGER_ENTER");
      else for (let i = 0; i < step.frames; i++) runtime.update(step.seconds);
    }

    expect(log).toEqual(trace.expect.log);
    expect(runtime.message).toBe(trace.expect.message);
    expect(runtime.objectiveLines()).toEqual(trace.expect.objectiveLines);
    expect(runtime.result).toBe(trace.expect.result);
    expect(runtime.skipped).toEqual(trace.expect.skipped);
  });

  const area: RuleEntity = { id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", parent: null,
    components: [{ type: "BoxCollider3DComponent", enabled: true, data: { trigger: true } }] };
  const hero: RuleEntity = { id: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", parent: null,
    components: [{ type: "PlayerComponent", enabled: true, data: {} }] };
  const lookup = (id: string) => [area, hero].find((e) => e.id === id);
  const rule = (name: string, once: boolean, when: unknown, ...then: unknown[]) => ({
    id: `${name.length.toString(16).padStart(8, "0")}-0000-4000-8000-${String(Math.random()).slice(2, 14).padEnd(12, "0")}`,
    name, enabled: true, once, when, then,
  });
  const scenario = (...rules: unknown[]) => ({ version: 1, players: [], objectives: [], rules });

  it("repeats a timer at most once a frame and fires a repeating area rule on each entry", () => {
    const log: string[] = [];
    const runtime = ScenarioRuntime.create(scenario(
      rule("Tick", false, { type: "timer", seconds: 1 }, { type: "showMessage", text: "tick" }),
      rule("Door", false, { type: "enterArea", area: area.id, who: "player" }, { type: "showMessage", text: "in" })),
    lookup, (l) => log.push(l))!;

    runtime.update(10);
    runtime.onContact(area, hero, "TRIGGER_ENTER");
    runtime.onContact(hero, area, "TRIGGER_ENTER");
    runtime.update(0.5);
    runtime.onContact(area, hero, "TRIGGER_EXIT");
    runtime.update(0.5);

    expect(log.map((l) => l.split("'")[1])).toEqual(["Tick", "Door", "Tick"]);
  });

  it("skips invalid and unknown rules and runs nothing for a newer version", () => {
    const plain: RuleEntity = { id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc", parent: null, components: [] };
    const runtime = ScenarioRuntime.create(scenario(
      rule("Wall", true, { type: "enterArea", area: plain.id, who: "player" }, { type: "showMessage", text: "x" }),
      rule("Future", true, { type: "unitCount" }, { type: "showMessage", text: "y" }),
      rule("Ok", true, { type: "timer", seconds: 0 }, { type: "showMessage", text: "ok" })),
    (id) => (id === plain.id ? plain : lookup(id)), () => {})!;
    expect(runtime.ruleCount).toBe(1);
    expect(runtime.skipped).toHaveLength(2);
    expect(runtime.skipped[0]).toMatch(/no trigger collider/);
    expect(ScenarioRuntime.create({ version: 2, rules: [] }, lookup, () => {})!.skipped[0]).toMatch(/needs a newer editor/);
    expect(ScenarioRuntime.create(undefined, lookup, () => {})).toBeNull();
    expect(hasTriggerCollider(area)).toBe(true);
    expect(hasTriggerCollider({ ...area, components: [{ type: "BoxCollider2DComponent", enabled: true, data: { isSensor: true } }] })).toBe(true);
  });

  it("refuses more effects than the limit", () => {
    const effects = Array.from({ length: 9 }, () => ({ type: "showMessage", text: "x" }));
    expect(() => ScenarioRuntime.create(scenario(rule("Many", true, { type: "timer", seconds: 0 }, ...effects)), lookup, () => {}))
      .toThrow(/at most 8/);
  });
});
