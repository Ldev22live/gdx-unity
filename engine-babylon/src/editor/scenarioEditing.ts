import type { ComponentData, EntityData, SceneFile } from "../scene/types";
import { hasTriggerCollider, MAX_EFFECTS, MAX_RULES, SUPPORTED_VERSION } from "../runtime/ScenarioRuntime";
import type { EditorModel } from "./EditorModel";
import { newId } from "./presets";

/**
 * Editing a scene's scenario on the web (roadmap A14): players, bounds and ownership (TD-108) and rules and objectives (TD-109),
 * ports of the desktop's `ScenarioEditor`, `RulesEditor` and `ScenarioReferences` with the same limits, refusals and wording. Every
 * change is one {@link EditorModel.edit} undo step on the scene file, which keeps every member this version does not edit
 * (objectives and rules of newer types, other clients' fields). A refused change leaves the file untouched and returns why.
 */

/** Where new player, rule, objective and area ids come from: UUIDs, or a fixed sequence in tests ({@link useIds}). */
let makeId: () => string = newId;

/** Replaces the id source (tests that write fixtures); call with no argument to go back to UUIDs. */
export function useIds(source: () => string = newId): void {
  makeId = source;
}

export type EditResult = { ok: true } | { ok: false; reason: string };
const OK: EditResult = { ok: true };
const refuse = (reason: string): EditResult => ({ ok: false, reason });

/** Most player slots and longest name (schema `players.maxItems`, `name.maxLength`). */
export const MAX_PLAYERS = 16;
export const MAX_NAME_LENGTH = 60;
export const MAX_RULE_NAME = 80;
export const MAX_TEXT = 200;
export const MAX_OBJECTIVES = 64;
export const MAX_TIMER_SECONDS = 86400;
export const NEW_TIMER_SECONDS = 5;
export const DEFAULT_MESSAGE_SECONDS = 4;
export const MAX_MESSAGE_SECONDS = 60;
/** The colours new slots take, in order, and the neutral slot's (the desktop's `ScenarioSetup` palette). */
export const SLOT_COLORS = ["#2f6fdf", "#d9432f", "#3fa34d", "#e0b62d", "#8e4fc9", "#2fb5c9", "#e07a2d", "#d94f9a"];
export const NEUTRAL_COLOR = "#8a8a7a";
export const NEUTRAL_NAME = "Neutral";
/** The size of an area made by {@link addArea}, scene units (a 3 m cube). */
export const AREA_SIZE = 300;
const COLOR = /^#[0-9a-fA-F]{6}$/;
export const WHO_PLAYER = "player";

type Json = Record<string, unknown>;
export interface PlayerSlot { id: string; name: string; color: string; neutral?: boolean; spawn?: string; [other: string]: unknown }
export interface Bounds { minX: number; minZ: number; maxX: number; maxZ: number }

const scenarioOf = (file: SceneFile): Json | undefined => {
  const block = (file as unknown as Json)["scenario"];
  return typeof block === "object" && block !== null && !Array.isArray(block) ? (block as Json) : undefined;
};
const arrayOf = (block: Json, name: string): Json[] => {
  if (!Array.isArray(block[name])) block[name] = [];
  return block[name] as Json[];
};

/** The scene's scenario block, read-only reason when this version must not edit it (a newer version), or none. */
export function scenarioState(file: SceneFile): { block: Json | undefined; readOnlyReason: string | null } {
  const block = scenarioOf(file);
  if (!block) return { block, readOnlyReason: null };
  const version = typeof block["version"] === "number" ? block["version"] : -1;
  if (version > SUPPORTED_VERSION) {
    return { block, readOnlyReason: `This scenario is version ${version} and needs a newer editor (this one edits version ${SUPPORTED_VERSION}); it is kept as it is` };
  }
  if (version !== SUPPORTED_VERSION) return { block, readOnlyReason: `The scenario has no valid version (${String(block["version"])}); it is kept as it is` };
  return { block, readOnlyReason: null };
}

/** The player slots, in order (empty without a scenario). */
export function playersOf(file: SceneFile): PlayerSlot[] {
  const block = scenarioOf(file);
  return block && Array.isArray(block["players"]) ? (block["players"] as PlayerSlot[]) : [];
}

/** A slot's name, followed by the start of its id when another slot has the same name ignoring case (the desktop's `displayName`). */
export function displayName(file: SceneFile, slot: PlayerSlot): string {
  const key = slot.name.toLowerCase();
  const same = playersOf(file).filter((other) => other.name.toLowerCase() === key).length;
  return same > 1 ? `${slot.name} (${slot.id.slice(0, 8)})` : slot.name;
}

/** Why {@code name} cannot be the name of player {@code playerId} (null for a new slot), or null when it can. */
export function nameProblem(file: SceneFile, playerId: string | null, name: string): string | null {
  if (name.trim() === "") return "A player needs a name";
  const trimmed = name.trim();
  if (trimmed.length > MAX_NAME_LENGTH) return `A player name has at most ${MAX_NAME_LENGTH} characters`;
  const clash = playersOf(file).find((slot) => slot.id !== playerId && slot.name.toLowerCase() === trimmed.toLowerCase());
  return clash ? `Another player is already called '${clash.name}'` : null;
}

/**
 * Runs {@code change} on the scenario block as one undo step; with {@code create} a scene without one gets an empty block first.
 * A change that throws {@link Refusal} is not recorded.
 */
function editScenario(model: EditorModel, label: string, create: boolean, change: (block: Json, file: SceneFile) => void): EditResult {
  const state = scenarioState(model.file);
  if (state.readOnlyReason) return refuse(state.readOnlyReason);
  if (!state.block && !create) return refuse("This scene has no players or rules yet");
  // Try on a copy first, so a refusal never leaves an undo step or a half-made change
  const trial = structuredClone(model.file);
  const trialRecord = trial as unknown as Json;
  trialRecord["scenario"] ??= { version: SUPPORTED_VERSION, players: [], objectives: [], rules: [] };
  try {
    change(trialRecord["scenario"] as Json, trial);
  } catch (error) {
    if (error instanceof Refusal) return refuse(error.message);
    throw error;
  }
  if (JSON.stringify(trial) === JSON.stringify(model.file)) return refuse("Nothing to change");
  model.edit(label, "scene", (file) => {
    const record = file as unknown as Json;
    record["scenario"] = (trial as unknown as Json)["scenario"];
    file.entities = trial.entities;
  });
  return OK;
}

class Refusal extends Error {}

// ---- players (TD-108) ------------------------------------------------------------------------------------------------------

/** Gives the scene a scenario with two named, coloured player slots (only on this explicit action). */
export function createScenario(model: EditorModel): EditResult {
  if (scenarioOf(model.file)) return refuse("This scene already has players");
  return editScenario(model, "Add Players", true, (block) => {
    arrayOf(block, "players").push({ id: makeId(), name: "Player 1", color: SLOT_COLORS[0]! }, { id: makeId(), name: "Player 2", color: SLOT_COLORS[1]! });
  });
}

export function addPlayer(model: EditorModel): EditResult {
  return editScenario(model, "Add Player", false, (block, file) => {
    const players = arrayOf(block, "players") as PlayerSlot[];
    if (players.length >= MAX_PLAYERS) throw new Refusal(`A scenario has at most ${MAX_PLAYERS} players`);
    let number = 1;
    while (nameProblem(file, null, `Player ${number}`) !== null) number++;
    const colored = players.filter((slot) => !slot.neutral).length;
    players.push({ id: makeId(), name: `Player ${number}`, color: SLOT_COLORS[colored % SLOT_COLORS.length]! });
  });
}

export function addNeutral(model: EditorModel): EditResult {
  return editScenario(model, "Add Neutral Player", false, (block, file) => {
    const players = arrayOf(block, "players") as PlayerSlot[];
    if (players.some((slot) => slot.neutral)) throw new Refusal("This scenario already has a neutral player");
    if (players.length >= MAX_PLAYERS) throw new Refusal(`A scenario has at most ${MAX_PLAYERS} players`);
    let name = NEUTRAL_NAME;
    for (let number = 2; nameProblem(file, null, name) !== null; number++) name = `${NEUTRAL_NAME} ${number}`;
    players.push({ id: makeId(), name, color: NEUTRAL_COLOR, neutral: true });
  });
}

function slotIn(block: Json, playerId: string): PlayerSlot {
  const slot = (arrayOf(block, "players") as PlayerSlot[]).find((p) => p.id === playerId);
  if (!slot) throw new Refusal(`There is no player ${playerId} in this scenario`);
  return slot;
}

export function renamePlayer(model: EditorModel, playerId: string, name: string): EditResult {
  const problem = nameProblem(model.file, playerId, name);
  if (problem) return refuse(problem);
  return editScenario(model, "Rename Player", false, (block) => (slotIn(block, playerId).name = name.trim()));
}

export function setPlayerColor(model: EditorModel, playerId: string, color: string): EditResult {
  if (!COLOR.test(color)) return refuse(`A player colour is #rrggbb, not ${color}`);
  return editScenario(model, "Recolour Player", false, (block) => (slotIn(block, playerId).color = color.toLowerCase()));
}

/** Sets where a player starts: an entity with a Spawn Point, or null for none. */
export function setSpawn(model: EditorModel, playerId: string, entityId: string | null): EditResult {
  if (entityId !== null) {
    const entity = model.file.entities.find((e) => e.id === entityId);
    if (!entity) return refuse(`There is no entity ${entityId} in this scene`);
    if (!entity.components.some((c) => c.type === "SpawnPointComponent" && c.enabled)) {
      return refuse(`'${entity.name}' is not a Spawn Point; place one from Characters first`);
    }
  }
  return editScenario(model, "Set Spawn", false, (block) => {
    const slot = slotIn(block, playerId);
    if (entityId === null) delete slot.spawn;
    else slot.spawn = entityId;
  });
}

/** The entities a spawn can be: those with an enabled Spawn Point. */
export function spawnCandidates(file: SceneFile): EntityData[] {
  return file.entities.filter((e) => e.components.some((c) => c.type === "SpawnPointComponent" && c.enabled));
}

/** Removes a slot unless something refers to it (owned entities, objectives, rules, or a rule this version cannot check). */
export function removePlayer(model: EditorModel, playerId: string): EditResult {
  const references = referencesToPlayer(model.file, playerId);
  const slot = playersOf(model.file).find((p) => p.id === playerId);
  if (slot && references.length > 0) return refuse(`Cannot remove ${displayName(model.file, slot)}: ${references.join("; ")}`);
  return editScenario(model, "Remove Player", false, (block) => {
    slotIn(block, playerId);
    block["players"] = arrayOf(block, "players").filter((p) => p["id"] !== playerId);
  });
}

/** Sets the playable area (finite, minima below maxima), or clears it with null. */
export function setBounds(model: EditorModel, bounds: Bounds | null): EditResult {
  if (bounds) {
    const values = [bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ];
    if (!values.every(Number.isFinite)) return refuse(`Bounds must be finite numbers: ${values.join(", ")}`);
    if (!(bounds.minX < bounds.maxX) || !(bounds.minZ < bounds.maxZ)) return refuse("Each minimum must be below its maximum");
  }
  return editScenario(model, bounds ? "Set Map Bounds" : "Clear Map Bounds", false, (block) => {
    if (bounds) block["bounds"] = { ...bounds };
    else delete block["bounds"];
  });
}

/** Makes {@code playerId} own the entities, or makes them unowned with null, in one undo step. */
export function assignOwner(model: EditorModel, entityIds: string[], playerId: string | null): EditResult {
  if (playerId !== null) {
    const state = scenarioState(model.file);
    if (state.readOnlyReason) return refuse(state.readOnlyReason);
    if (!playersOf(model.file).some((p) => p.id === playerId)) return refuse(`There is no player ${playerId} in this scenario`);
  }
  for (const id of entityIds) if (!model.file.entities.some((e) => e.id === id)) return refuse(`There is no entity ${id} in this scene`);
  const before = JSON.stringify(model.file.entities);
  const trial = structuredClone(model.file.entities);
  for (const entity of trial.filter((e) => entityIds.includes(e.id))) {
    entity.components = entity.components.filter((c) => c.type !== "OwnerComponent");
    if (playerId !== null) entity.components.push(ownerComponent(playerId));
  }
  if (JSON.stringify(trial) === before) return refuse("Nothing to change");
  model.edit(playerId === null ? "Make Unowned" : "Set Owner", "scene", (file) => (file.entities = trial));
  return OK;
}

/** The saved owner component, as the desktop's `OwnerComponentSerializer` writes it. */
export function ownerComponent(playerId: string): ComponentData {
  return { type: "OwnerComponent", enabled: true, data: { player: playerId } };
}

/** The owner of an entity (player id), or null for unowned. */
export function ownerOf(entity: EntityData): string | null {
  const owner = entity.components.find((c) => c.type === "OwnerComponent");
  return owner ? String(owner.data["player"]) : null;
}

// ---- references and deletion safeguards ------------------------------------------------------------------------------------

function isOpaque(rule: Json): boolean {
  const when = rule["when"] as Json | undefined;
  const type = when && typeof when["type"] === "string" ? when["type"] : null;
  if (type !== "timer" && type !== "enterArea") return true;
  const then = Array.isArray(rule["then"]) ? (rule["then"] as Json[]) : [];
  return then.some((e) => !["showMessage", "completeObjective", "endScenario"].includes(String(e["type"])));
}

/** What refers to a player: owned entities, objectives, rules, or a rule this version cannot check. */
export function referencesToPlayer(file: SceneFile, playerId: string): string[] {
  const found: string[] = [];
  const owned = file.entities.filter((e) => ownerOf(e) === playerId);
  if (owned.length === 1) found.push(`it owns '${owned[0]!.name}'`);
  else if (owned.length > 1) found.push(`it owns ${owned.length} objects, among them '${owned[0]!.name}'`);
  const block = scenarioOf(file);
  if (!block) return found;
  for (const objective of arrayOf(structuredClone(block), "objectives")) {
    if (objective["player"] === playerId) found.push(`objective '${String(objective["text"])}' belongs to it`);
  }
  for (const rule of arrayOf(structuredClone(block), "rules")) {
    const name = String(rule["name"] ?? "?");
    if (isOpaque(rule)) {
      found.push(`rule '${name}' has a type this version cannot check`);
      continue;
    }
    if ((rule["when"] as Json)["who"] === playerId) found.push(`rule '${name}' waits for it`);
    for (const effect of rule["then"] as Json[]) if (effect["player"] === playerId) found.push(`rule '${name}' ends the scenario for it`);
  }
  return found;
}

/** What in the scenario names entity {@code entityId} (a spawn, a rule's area, an objective); a newer block is searched as text. */
export function referencesToEntity(file: SceneFile, entityId: string): string[] {
  const state = scenarioState(file);
  if (!state.block) return [];
  if (state.readOnlyReason) return JSON.stringify(state.block).includes(entityId) ? ["the scenario (which this version cannot edit) names it"] : [];
  const found: string[] = [];
  for (const slot of playersOf(file)) if (slot.spawn === entityId) found.push(`it is the spawn of ${displayName(file, slot)}`);
  for (const rule of arrayOf(structuredClone(state.block), "rules")) {
    const when = rule["when"] as Json | undefined;
    if (when && when["area"] === entityId) found.push(`rule '${String(rule["name"])}' uses it as its area`);
    else if (isOpaque(rule) && JSON.stringify(rule).includes(entityId)) found.push(`rule '${String(rule["name"])}' (a type this version cannot check) names it`);
  }
  if (JSON.stringify(state.block["objectives"] ?? []).includes(entityId)) found.push("an objective names it");
  return found;
}

// ---- rules and objectives (TD-109) -----------------------------------------------------------------------------------------

export function rulesOf(file: SceneFile): Json[] {
  const block = scenarioOf(file);
  return block && Array.isArray(block["rules"]) ? (block["rules"] as Json[]) : [];
}

export function objectivesOf(file: SceneFile): Json[] {
  const block = scenarioOf(file);
  return block && Array.isArray(block["objectives"]) ? (block["objectives"] as Json[]) : [];
}

function ruleIn(block: Json, ruleId: string): Json {
  const rule = arrayOf(block, "rules").find((r) => r["id"] === ruleId);
  if (!rule) throw new Refusal(`There is no item ${ruleId}`);
  return rule;
}

function newEffect(type: string, block: Json): Json {
  if (type === "showMessage") return { type, text: "Hello", seconds: DEFAULT_MESSAGE_SECONDS };
  if (type === "completeObjective") {
    const first = arrayOf(block, "objectives")[0];
    if (!first) throw new Refusal("Add an objective first");
    return { type, objective: first["id"] };
  }
  if (type === "endScenario") return { type, result: "victory" };
  throw new Refusal(`Unknown effect type ${type}`);
}

/** Adds an enabled one-shot rule (after 5 s, show a message); a scene without a scenario gets one with no players. */
export function addRule(model: EditorModel): string | EditResult {
  const id = makeId();
  const result = editScenario(model, "Add Rule", true, (block) => {
    const rules = arrayOf(block, "rules");
    if (rules.length >= MAX_RULES) throw new Refusal(`A scenario has at most ${MAX_RULES} rules`);
    let number = rules.length + 1;
    while (rules.some((r) => String(r["name"]).toLowerCase() === `rule ${number}`)) number++;
    rules.push({ id, name: `Rule ${number}`, enabled: true, once: true, when: { type: "timer", seconds: NEW_TIMER_SECONDS },
      then: [newEffect("showMessage", block)] });
  });
  return result.ok ? id : result;
}

export function renameRule(model: EditorModel, ruleId: string, name: string): EditResult {
  const trimmed = name.trim();
  if (trimmed === "" || trimmed.length > MAX_RULE_NAME) return refuse(`A rule name has 1 to ${MAX_RULE_NAME} characters`);
  return editScenario(model, "Rename Rule", false, (block) => (ruleIn(block, ruleId)["name"] = trimmed));
}

export function setRuleEnabled(model: EditorModel, ruleId: string, enabled: boolean): EditResult {
  return editScenario(model, enabled ? "Enable Rule" : "Disable Rule", false, (block) => (ruleIn(block, ruleId)["enabled"] = enabled));
}

export function setRuleOnce(model: EditorModel, ruleId: string, once: boolean): EditResult {
  return editScenario(model, once ? "Rule Fires Once" : "Rule Repeats", false, (block) => (ruleIn(block, ruleId)["once"] = once));
}

export function setTimer(model: EditorModel, ruleId: string, seconds: number): EditResult {
  if (!(seconds >= 0) || seconds > MAX_TIMER_SECONDS) return refuse(`A timer is 0 to ${MAX_TIMER_SECONDS} seconds, not ${seconds}`);
  return editScenario(model, "Set Rule Timer", false, (block) => (ruleIn(block, ruleId)["when"] = { type: "timer", seconds }));
}

/** Makes the rule wait for {@code who} ({@link WHO_PLAYER} or a player id) to enter an area with a trigger or sensor collider. */
export function setEnterArea(model: EditorModel, ruleId: string, areaId: string, who: string): EditResult {
  const area = model.file.entities.find((e) => e.id === areaId);
  if (!area) return refuse("Choose an area: an entity with a trigger collider (Add Area makes one)");
  if (!hasTriggerCollider({ id: area.id, components: area.components, parent: null })) {
    return refuse(`'${area.name}' has no trigger collider, so nothing can enter it`);
  }
  if (who !== WHO_PLAYER && !playersOf(model.file).some((p) => p.id === who)) return refuse(`'${who}' is neither the player nor a player of this scenario`);
  return editScenario(model, "Set Rule Area", false, (block) => (ruleIn(block, ruleId)["when"] = { type: "enterArea", area: areaId, who }));
}

export function moveRule(model: EditorModel, ruleId: string, direction: -1 | 1): EditResult {
  return editScenario(model, "Move Rule", false, (block) => {
    const rules = arrayOf(block, "rules");
    const at = rules.findIndex((r) => r["id"] === ruleId);
    const to = at + direction;
    if (at < 0 || to < 0 || to >= rules.length) throw new Refusal("The rule cannot move further");
    rules.splice(to, 0, ...rules.splice(at, 1));
  });
}

export function removeRule(model: EditorModel, ruleId: string): EditResult {
  return editScenario(model, "Remove Rule", false, (block) => {
    ruleIn(block, ruleId);
    block["rules"] = arrayOf(block, "rules").filter((r) => r["id"] !== ruleId);
  });
}

export function addEffect(model: EditorModel, ruleId: string, type: string): EditResult {
  return editScenario(model, "Add Effect", false, (block) => {
    const then = arrayOf(ruleIn(block, ruleId), "then");
    if (then.length >= MAX_EFFECTS) throw new Refusal(`A rule has at most ${MAX_EFFECTS} effects`);
    then.push(newEffect(type, block));
  });
}

function effectIn(block: Json, ruleId: string, index: number, type?: string): Json {
  const effect = arrayOf(ruleIn(block, ruleId), "then")[index];
  if (!effect) throw new Refusal(`The rule has no effect ${index + 1}`);
  if (type !== undefined && effect["type"] !== type) throw new Refusal(`Effect ${index + 1} is not ${type}`);
  return effect;
}

export function setEffectType(model: EditorModel, ruleId: string, index: number, type: string): EditResult {
  return editScenario(model, "Set Effect", false, (block) => {
    const then = arrayOf(ruleIn(block, ruleId), "then");
    if (!then[index]) throw new Refusal(`The rule has no effect ${index + 1}`);
    if (then[index]!["type"] === type) throw new Refusal("Nothing to change");
    then[index] = newEffect(type, block);
  });
}

export function setMessage(model: EditorModel, ruleId: string, index: number, text: string, seconds: number): EditResult {
  const trimmed = text.trim();
  if (trimmed === "" || trimmed.length > MAX_TEXT) return refuse(`A message has 1 to ${MAX_TEXT} characters`);
  if (!(seconds >= 0) || seconds > MAX_MESSAGE_SECONDS) return refuse(`A message shows 0 to ${MAX_MESSAGE_SECONDS} seconds`);
  return editScenario(model, "Set Message", false, (block) => {
    const effect = effectIn(block, ruleId, index, "showMessage");
    effect["text"] = trimmed;
    effect["seconds"] = seconds;
  });
}

export function setCompletedObjective(model: EditorModel, ruleId: string, index: number, objectiveId: string): EditResult {
  if (!objectivesOf(model.file).some((o) => o["id"] === objectiveId)) return refuse(`There is no objective ${objectiveId}`);
  return editScenario(model, "Set Completed Objective", false, (block) => (effectIn(block, ruleId, index, "completeObjective")["objective"] = objectiveId));
}

export function setEndResult(model: EditorModel, ruleId: string, index: number, result: string): EditResult {
  if (!["victory", "defeat", "draw"].includes(result)) return refuse(`A result is victory, defeat or draw, not ${result}`);
  return editScenario(model, "Set Scenario Result", false, (block) => (effectIn(block, ruleId, index, "endScenario")["result"] = result));
}

export function removeEffect(model: EditorModel, ruleId: string, index: number): EditResult {
  return editScenario(model, "Remove Effect", false, (block) => {
    const then = arrayOf(ruleIn(block, ruleId), "then");
    if (!then[index]) throw new Refusal(`The rule has no effect ${index + 1}`);
    if (then.length === 1) throw new Refusal("A rule needs at least one effect; remove the rule instead");
    then.splice(index, 1);
  });
}

export function addObjective(model: EditorModel, text: string): string | EditResult {
  const trimmed = text.trim();
  if (trimmed === "" || trimmed.length > MAX_TEXT) return refuse(`An objective has 1 to ${MAX_TEXT} characters`);
  const id = makeId();
  const result = editScenario(model, "Add Objective", true, (block) => {
    const objectives = arrayOf(block, "objectives");
    if (objectives.length >= MAX_OBJECTIVES) throw new Refusal(`A scenario has at most ${MAX_OBJECTIVES} objectives`);
    objectives.push({ id, text: trimmed });
  });
  return result.ok ? id : result;
}

function objectiveIn(block: Json, objectiveId: string): Json {
  const objective = arrayOf(block, "objectives").find((o) => o["id"] === objectiveId);
  if (!objective) throw new Refusal(`There is no item ${objectiveId}`);
  return objective;
}

export function setObjectiveText(model: EditorModel, objectiveId: string, text: string): EditResult {
  const trimmed = text.trim();
  if (trimmed === "" || trimmed.length > MAX_TEXT) return refuse(`An objective has 1 to ${MAX_TEXT} characters`);
  return editScenario(model, "Set Objective Text", false, (block) => (objectiveIn(block, objectiveId)["text"] = trimmed));
}

export function setObjectiveHidden(model: EditorModel, objectiveId: string, hidden: boolean): EditResult {
  return editScenario(model, hidden ? "Hide Objective" : "Show Objective", false, (block) => {
    const objective = objectiveIn(block, objectiveId);
    if (hidden) objective["initial"] = "hidden";
    else delete objective["initial"];
  });
}

export function removeObjective(model: EditorModel, objectiveId: string): EditResult {
  for (const rule of rulesOf(model.file)) {
    const then = Array.isArray(rule["then"]) ? (rule["then"] as Json[]) : [];
    if (then.some((e) => e["objective"] === objectiveId)) return refuse(`Cannot remove the objective: rule '${String(rule["name"])}' completes it`);
  }
  return editScenario(model, "Remove Objective", false, (block) => {
    objectiveIn(block, objectiveId);
    block["objectives"] = arrayOf(block, "objectives").filter((o) => o["id"] !== objectiveId);
  });
}

/** The entities an area rule can use: those with a trigger (3D) or sensor (2D) collider. */
export function areaCandidates(file: SceneFile): EntityData[] {
  return file.entities.filter((e) => hasTriggerCollider({ id: e.id, components: e.components, parent: null }));
}

/** Creates an area at {@code position}: an entity with a trigger Box Collider 3D of {@link AREA_SIZE}, selected, in one undo step. */
export function addArea(model: EditorModel, position: { x: number; y: number; z: number }): string {
  let number = 1;
  while (model.file.entities.some((e) => e.name === `Area ${number}`)) number++;
  const entity: EntityData = {
    id: makeId(), name: `Area ${number}`, active: true, parentId: null,
    transform: { position: { ...position }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
    components: [{ type: "BoxCollider3DComponent", enabled: true,
      data: { offsetX: 0, offsetY: 0, offsetZ: 0, trigger: true, sizeX: AREA_SIZE, sizeY: AREA_SIZE, sizeZ: AREA_SIZE } }],
  };
  model.edit(`Add ${entity.name}`, "scene", (file) => file.entities.push(entity));
  model.select(entity.id);
  return entity.id;
}
