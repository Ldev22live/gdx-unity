import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { EditorModel, emptyScene } from "../src/editor/EditorModel";
import {
  addArea, addEffect, addNeutral, addObjective, addPlayer, addRule, assignOwner, createScenario, displayName, MAX_PLAYERS,
  moveRule, objectivesOf, ownerOf, playersOf, removeEffect, removeObjective, removePlayer, renamePlayer, renameRule, rulesOf,
  setBounds, setEffectType, setEndResult, setEnterArea, setMessage, setPlayerColor, setRuleOnce, setSpawn, setTimer, useIds,
  WHO_PLAYER,
} from "../src/editor/scenarioEditing";
import { ScenarioRuntime } from "../src/runtime/ScenarioRuntime";
import { parseSceneFile, type EntityData } from "../src/scene/types";
import { ids, INTEROP_DIR, matchOrWrite, writeFiles } from "./projectFiles";

afterEach(() => useIds());

function entity(name: string, ...components: EntityData["components"]): EntityData {
  return { id: `${name.toLowerCase()}-id`.padEnd(8, "x"), name, active: true, parentId: null,
    transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } }, components };
}

const player = () => ({ type: "PlayerComponent", enabled: true, data: { useProfileMovement: false } });
const spawn = () => ({ type: "SpawnPointComponent", enabled: true, data: {} });

describe("players and ownership on the web (roadmap A14, TD-108)", () => {
  it("creates two slots only on the explicit action, keeps ids through rename and undo, refuses bad names", () => {
    const model = new EditorModel(emptyScene());
    expect(playersOf(model.file)).toEqual([]);
    expect(createScenario(model)).toEqual({ ok: true });
    expect(playersOf(model.file).map((p) => p.name)).toEqual(["Player 1", "Player 2"]);
    const id = playersOf(model.file)[0]!.id;

    expect(renamePlayer(model, id, "  Red Banner ")).toEqual({ ok: true });
    expect(setPlayerColor(model, id, "#AA0000")).toEqual({ ok: true });
    expect(renamePlayer(model, id, "PLAYER 2")).toEqual({ ok: false, reason: "Another player is already called 'Player 2'" });
    expect(renamePlayer(model, id, " ").ok).toBe(false);
    expect(setPlayerColor(model, id, "red").ok).toBe(false);
    expect(playersOf(model.file)[0]).toMatchObject({ id, name: "Red Banner", color: "#aa0000" });
    model.undo();
    model.undo();
    expect(playersOf(model.file)[0]).toMatchObject({ id, name: "Player 1" });
    model.undo();
    expect((model.file as unknown as Record<string, unknown>)["scenario"]).toBeUndefined();
  });

  it("adds up to sixteen slots and one neutral, and names duplicates by id", () => {
    const model = new EditorModel(emptyScene());
    createScenario(model);
    expect(addNeutral(model)).toEqual({ ok: true });
    expect(addNeutral(model)).toEqual({ ok: false, reason: "This scenario already has a neutral player" });
    while (playersOf(model.file).length < MAX_PLAYERS) expect(addPlayer(model)).toEqual({ ok: true });
    expect(addPlayer(model).ok).toBe(false);
    const file = model.file as unknown as { scenario: { players: { name: string }[] } };
    file.scenario.players[1]!.name = "player 1";
    expect(displayName(model.file, playersOf(model.file)[1]!)).toMatch(/^player 1 \([0-9a-z-]{8}\)$/);
  });

  it("assigns, reassigns and clears owners in one step each and blocks removing an owner", () => {
    const model = new EditorModel({ ...emptyScene(), entities: [entity("Tower"), entity("Tree")] });
    createScenario(model);
    const [p1, p2] = playersOf(model.file).map((p) => p.id);
    expect(assignOwner(model, ["tower-id", "tree-idx"], p1!)).toEqual({ ok: true });
    expect(assignOwner(model, ["tree-idx"], p2!)).toEqual({ ok: true });
    expect(model.file.entities.map(ownerOf)).toEqual([p1, p2]);
    expect(model.file.entities[1]!.components.filter((c) => c.type === "OwnerComponent")).toHaveLength(1);
    expect(removePlayer(model, p1!)).toEqual({ ok: false, reason: "Cannot remove Player 1: it owns 'Tower'" });
    expect(assignOwner(model, ["tower-id"], null)).toEqual({ ok: true });
    expect(removePlayer(model, p1!)).toEqual({ ok: true });
    model.undo();
    expect(playersOf(model.file).map((p) => p.id)).toContain(p1);
  });

  it("takes only a Spawn Point as a spawn, protects it from deletion, and checks bounds", () => {
    const model = new EditorModel({ ...emptyScene(), entities: [entity("Rock"), entity("Start", spawn())] });
    createScenario(model);
    const p1 = playersOf(model.file)[0]!.id;
    expect(setSpawn(model, p1, "rock-idx").ok).toBe(false);
    expect(setSpawn(model, p1, "start-id")).toEqual({ ok: true });
    expect(() => model.deleteEntity("start-id")).toThrow("Cannot delete 'Start': it is the spawn of Player 1");
    expect(setBounds(model, { minX: 5, minZ: 0, maxX: 5, maxZ: 10 }).ok).toBe(false);
    expect(setBounds(model, { minX: -50, minZ: -40, maxX: 50, maxZ: 40 })).toEqual({ ok: true });
  });

  it("keeps unknown members and refuses a newer scenario", () => {
    const file = { ...emptyScene(), scenario: { version: 1, teams: { a: [1] }, players: [{ id: "p", name: "One", color: "#010203", faction: "north" }],
      objectives: [{ id: "o", text: "A", futureIcon: "star" }], rules: [] } } as never;
    const model = new EditorModel(file);
    expect(renamePlayer(model, "p", "First")).toEqual({ ok: true });
    const scenario = (model.file as unknown as { scenario: Record<string, unknown> }).scenario;
    expect(scenario["teams"]).toEqual({ a: [1] });
    expect((scenario["players"] as Record<string, unknown>[])[0]).toEqual({ id: "p", name: "First", color: "#010203", faction: "north" });
    expect((scenario["objectives"] as Record<string, unknown>[])[0]!["futureIcon"]).toBe("star");
    const newer = new EditorModel({ ...emptyScene(), scenario: { version: 2, players: [] } } as never);
    expect(addPlayer(newer)).toMatchObject({ ok: false, reason: expect.stringMatching(/needs a newer editor/) });
  });
});

describe("rules and objectives on the web (roadmap A14, TD-109)", () => {
  it("edits every part of a rule, one undo step each, and refuses bad values", () => {
    const model = new EditorModel(emptyScene());
    const rule = addRule(model) as string;
    expect(rulesOf(model.file)[0]).toMatchObject({ name: "Rule 1", enabled: true, once: true, when: { type: "timer", seconds: 5 } });
    const objective = addObjective(model, "Reach the house") as string;
    expect(renameRule(model, rule, "Arrive")).toEqual({ ok: true });
    expect(setRuleOnce(model, rule, false)).toEqual({ ok: true });
    expect(setTimer(model, rule, 2.5)).toEqual({ ok: true });
    expect(setMessage(model, rule, 0, "Go!", 3)).toEqual({ ok: true });
    expect(addEffect(model, rule, "completeObjective")).toEqual({ ok: true });
    expect(addEffect(model, rule, "endScenario")).toEqual({ ok: true });
    expect(setEndResult(model, rule, 2, "defeat")).toEqual({ ok: true });
    expect(rulesOf(model.file)[0]!["then"]).toEqual([{ type: "showMessage", text: "Go!", seconds: 3 },
      { type: "completeObjective", objective }, { type: "endScenario", result: "defeat" }]);
    expect(setTimer(model, rule, -1).ok).toBe(false);
    expect(setEndResult(model, rule, 0, "victory").ok).toBe(false);
    expect(removeObjective(model, objective)).toEqual({ ok: false, reason: "Cannot remove the objective: rule 'Arrive' completes it" });
    for (let i = 0; i < 7; i++) model.undo();
    expect(rulesOf(model.file)[0]!["name"]).toBe("Rule 1");
  });

  it("only takes an area with a trigger collider, protects it, and reorders and trims rules", () => {
    const model = new EditorModel({ ...emptyScene(), entities: [entity("Wall", { type: "BoxCollider3DComponent", enabled: true, data: { trigger: false } })] });
    const first = addRule(model) as string;
    const second = addRule(model) as string;
    expect(setEnterArea(model, first, "wall-idx", WHO_PLAYER).ok).toBe(false);
    const area = addArea(model, { x: 0, y: 0, z: -500 });
    expect(setEnterArea(model, first, area, WHO_PLAYER)).toEqual({ ok: true });
    expect(() => model.deleteEntity(area)).toThrow(/uses it as its area/);
    expect(moveRule(model, second, -1)).toEqual({ ok: true });
    expect(rulesOf(model.file).map((r) => r["id"])).toEqual([second, first]);
    expect(removeEffect(model, first, 0).ok).toBe(false);
    expect(setEffectType(model, first, 0, "completeObjective")).toEqual({ ok: false, reason: "Add an objective first" });
  });

  it("writes a web-authored scenario that the desktop checks (step w2) and that the web runtime plays", () => {
    useIds(ids("w2-web"));
    const hero: EntityData = { ...entity("Hero", player(),
      { type: "CharacterController3DComponent", enabled: true, data: { radius: 30, height: 80, slopeLimitDegrees: 45, stepHeight: 30, mass: 70, gravityScale: 1, layer: 0, collisionMask: 65535 } }),
    id: "11111111-1111-3111-8111-111111111111" };
    const tower: EntityData = { ...entity("Tower"), id: "22222222-2222-3222-8222-222222222222" };
    const start: EntityData = { ...entity("Start", spawn()), id: "33333333-3333-3333-8333-333333333333" };
    const model = new EditorModel({ ...emptyScene("Web Scenario"), entities: [hero, tower, start] });

    createScenario(model);
    addNeutral(model);
    const [p1, p2] = playersOf(model.file).map((p) => p.id);
    renamePlayer(model, p2!, "Raiders");
    setSpawn(model, p1!, start.id);
    assignOwner(model, [hero.id], p1!);
    assignOwner(model, [tower.id], p2!);
    setBounds(model, { minX: -2000, minZ: -2000, maxX: 2000, maxZ: 2000 });
    const objective = addObjective(model, "Reach the gate") as string;
    const welcome = addRule(model) as string;
    renameRule(model, welcome, "Welcome");
    setTimer(model, welcome, 1);
    setMessage(model, welcome, 0, "Find the gate", 3);
    const gate = addArea(model, { x: 0, y: 60, z: -600 });
    const arrive = addRule(model) as string;
    renameRule(model, arrive, "Arrive");
    setEnterArea(model, arrive, gate, WHO_PLAYER);
    setEffectType(model, arrive, 0, "completeObjective");
    addEffect(model, arrive, "endScenario");
    expect(objectivesOf(model.file)).toHaveLength(1);

    const text = model.serialize();
    const played = parseSceneFile(text);
    const byId = new Map(played.entities.map((e) => [e.id, { id: e.id, components: e.components, parent: null }]));
    const log: string[] = [];
    const runtime = ScenarioRuntime.create((played as unknown as Record<string, unknown>)["scenario"], (id) => byId.get(id), (l) => log.push(l))!;
    expect(runtime.skipped).toEqual([]);
    for (let i = 0; i < 4; i++) runtime.update(0.25);
    runtime.onContact(byId.get(gate)!, byId.get(hero.id)!, "TRIGGER_ENTER");
    runtime.update(0.25);
    expect(runtime.objectiveLines()).toEqual(["[x] Reach the gate"]);
    expect(runtime.result).toBe("VICTORY");
    expect(objective).toBeTruthy();

    const root = mkdtempSync(join(tmpdir(), "w2-"));
    writeFiles(root, { "scenes/web-scenario.json": text });
    matchOrWrite(root, join(INTEROP_DIR, "w2-web"));
    expect(readFileSync(join(root, "scenes", "web-scenario.json"), "utf8")).toBe(text);
  });
});
