import type { SceneFile } from "../scene/types";
import { h } from "./dom";
import type { EditorApp } from "./EditorApp";
import * as edit from "./scenarioEditing";

/**
 * The Players and Rules palette rows of the web World shell (roadmap A14): the web counterparts of the desktop's `PlayersPanel`
 * and `RulesPanel`. Every control calls {@link edit}, so each change is one undo step and a refused one says why in the status line.
 * The row is rebuilt after each change (the caller re-renders when the scenario changes).
 */

type Result = edit.EditResult | string;

/** Reports a refused change in the status line; returns true when the change went through. */
function report(app: EditorApp, result: Result): boolean {
  if (typeof result === "string" || result.ok) return true;
  app.showStatus(result.reason, true);
  return false;
}

function button(label: string, run: () => void, title = ""): HTMLButtonElement {
  const element = h("button", title ? { title } : {}, label) as HTMLButtonElement;
  element.addEventListener("click", run);
  return element;
}

function textInput(value: string, commit: (text: string) => void, width = 140): HTMLInputElement {
  const input = h("input", { type: "text", value }) as HTMLInputElement;
  input.style.width = `${width}px`;
  input.addEventListener("change", () => {
    if (input.value.trim() !== value) commit(input.value);
  });
  return input;
}

function numberInput(value: number | null, commit: (n: number) => void, width = 56): HTMLInputElement {
  const input = h("input", { type: "number", step: "any", value: value === null ? "" : String(value) }) as HTMLInputElement;
  input.style.width = `${width}px`;
  input.addEventListener("change", () => commit(Number(input.value)));
  return input;
}

function select(choices: [string, string][], current: string, choose: (value: string) => void, width = 120): HTMLSelectElement {
  const element = h("select") as HTMLSelectElement;
  element.style.width = `${width}px`;
  for (const [value, label] of choices) element.append(h("option", { value }, label));
  if (!choices.some(([value]) => value === current)) element.append(h("option", { value: current }, "Missing"));
  element.value = current;
  element.addEventListener("change", () => {
    if (element.value !== current) choose(element.value);
  });
  return element;
}

const card = (...children: (Node | string)[]) => h("div", { class: "scenario-card" }, ...children);
const row = (...children: (Node | string)[]) => h("div", { class: "scenario-row" }, ...children);
const muted = (text: string) => h("span", { class: "muted" }, text);

// ---- Players (TD-108) ------------------------------------------------------------------------------------------------------

/** The owner choices: Unowned, then each player by its display name. */
export function ownerChoices(file: SceneFile): [string, string][] {
  return [["", "Unowned"], ...edit.playersOf(file).map((p): [string, string] => [p.id, edit.displayName(file, p) + (p.neutral ? " (neutral)" : "")])];
}

/**
 * The Players row: the owner new placements get, Add Player and Add Neutral, a card per slot (colour, name, spawn, remove) and the
 * map bounds. {@code placementOwner} is read and set through the two callbacks (the World shell keeps it).
 */
export function renderPlayers(app: EditorApp, getOwner: () => string | null, setOwner: (id: string | null) => void): HTMLElement[] {
  const model = app.model;
  const file = model.file;
  const state = edit.scenarioState(file);
  if (state.readOnlyReason) return [h("div", { class: "note" }, `Players cannot be edited: ${state.readOnlyReason}`)];
  if (!state.block) {
    return [h("div", { class: "note" }, "This scene has no players. ", button("Add Players", () => report(app, edit.createScenario(model))),
      h("div", { class: "summary" }, "Ownership is saved with the scene. Play does not use it yet: no teams, diplomacy, economy or unit AI."))];
  }
  const owner = getOwner();
  const owners = ownerChoices(file);
  const side = card(muted("New objects owned by"),
    select(owners, owner ?? "", (value) => {
      setOwner(value === "" ? null : value);
      app.showStatus(`New objects are placed as ${owners.find(([id]) => id === value)?.[1] ?? "Unowned"}`);
    }, 150),
    row(button("Add Player", () => report(app, edit.addPlayer(model))), button("Add Neutral", () => report(app, edit.addNeutral(model)))),
    muted("Play does not use ownership yet."));
  if (owner !== null && !edit.playersOf(file).some((p) => p.id === owner)) {
    side.append(h("div", { class: "warning" }, "The chosen owner was removed: choose again"));
  }
  const cards: HTMLElement[] = [side];
  const spawns: [string, string][] = [["", "None"], ...edit.spawnCandidates(file).map((e): [string, string] => [e.id, e.name])];
  for (const slot of edit.playersOf(file)) {
    const swatch = button("●", () => {
      const at = edit.SLOT_COLORS.findIndex((c) => c.toLowerCase() === slot.color.toLowerCase());
      report(app, edit.setPlayerColor(model, slot.id, edit.SLOT_COLORS[(at + 1) % edit.SLOT_COLORS.length]!));
    }, "Next colour");
    swatch.style.color = slot.color;
    cards.push(card(
      row(swatch, textInput(slot.name, (name) => report(app, edit.renamePlayer(model, slot.id, name)), 120)),
      row(muted("Spawn"), select(spawns, slot.spawn ?? "", (value) => report(app, edit.setSpawn(model, slot.id, value === "" ? null : value)), 110)),
      row(muted(slot.neutral ? "Neutral" : "Player"), button("Remove", () => report(app, edit.removePlayer(model, slot.id))))));
  }
  const bounds = (state.block["bounds"] ?? null) as edit.Bounds | null;
  const values: (number | null)[] = bounds ? [bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ] : [null, null, null, null];
  const fields = values.map((value) => numberInput(value, () => undefined, 60));
  cards.push(card(muted(bounds ? "Map bounds" : "Map bounds (none)"),
    row(muted("X"), fields[0]!, muted("to"), fields[1]!), row(muted("Z"), fields[2]!, muted("to"), fields[3]!),
    row(button("Set", () => {
      const [minX, maxX, minZ, maxZ] = fields.map((f) => (f.value.trim() === "" ? Number.NaN : Number(f.value)));
      report(app, edit.setBounds(model, { minX: minX!, minZ: minZ!, maxX: maxX!, maxZ: maxZ! }));
    }), button("Clear", () => report(app, edit.setBounds(model, null))))));
  return cards;
}

// ---- Rules (TD-109) --------------------------------------------------------------------------------------------------------

const EFFECTS: [string, string][] = [["showMessage", "Show message"], ["completeObjective", "Complete objective"], ["endScenario", "End scenario"]];

/** The Rules row: Add Rule, Add Area and Add Objective, the objectives, and a card per rule. */
export function renderRules(app: EditorApp, areaPosition: () => { x: number; y: number; z: number }): HTMLElement[] {
  const model = app.model;
  const file = model.file;
  const state = edit.scenarioState(file);
  if (state.readOnlyReason) return [h("div", { class: "note" }, `Rules cannot be edited: ${state.readOnlyReason}`)];
  const cards: HTMLElement[] = [card(
    row(button("Add Rule", () => report(app, edit.addRule(model))),
      button("Add Area", () => edit.addArea(model, areaPosition()), "A trigger box at the view centre; move and scale it, then choose it in a rule")),
    row(button("Add Objective", () => report(app, edit.addObjective(model, "New objective")))),
    muted("Rules run in Play, top to bottom."))];
  const objectives = card(muted("Objectives"));
  if (edit.objectivesOf(file).length === 0) objectives.append(muted(" none yet"));
  for (const objective of edit.objectivesOf(file)) {
    const id = String(objective["id"]);
    const hidden = objective["initial"] === "hidden";
    objectives.append(row(textInput(String(objective["text"] ?? ""), (text) => report(app, edit.setObjectiveText(model, id, text))),
      button(hidden ? "Hidden" : "Shown", () => report(app, edit.setObjectiveHidden(model, id, !hidden))),
      button("×", () => report(app, edit.removeObjective(model, id)), "Remove the objective")));
  }
  cards.push(objectives);
  const rules = edit.rulesOf(file);
  const areas: [string, string][] = edit.areaCandidates(file).map((e) => [e.id, e.name]);
  const whos: [string, string][] = [[edit.WHO_PLAYER, "the player"], ...edit.playersOf(file).map((p): [string, string] => [p.id, edit.displayName(file, p)])];
  const objectiveChoices: [string, string][] = edit.objectivesOf(file).map((o) => [String(o["id"]), String(o["text"])]);
  rules.forEach((rule, index) => {
    const id = String(rule["id"]);
    const enabled = rule["enabled"] === true;
    const once = rule["once"] !== false;
    const when = (rule["when"] ?? {}) as Record<string, unknown>;
    const ruleCard = card(row(muted(`${index + 1}.`),
      button(enabled ? "On" : "Off", () => report(app, edit.setRuleEnabled(model, id, !enabled))),
      textInput(String(rule["name"] ?? ""), (name) => report(app, edit.renameRule(model, id, name)), 120),
      button(once ? "Once" : "Repeat", () => report(app, edit.setRuleOnce(model, id, !once))),
      button("‹", () => report(app, edit.moveRule(model, id, -1)), "Earlier"), button("›", () => report(app, edit.moveRule(model, id, 1)), "Later"),
      button("×", () => report(app, edit.removeRule(model, id)), "Remove the rule")));
    const type = String(when["type"] ?? "");
    if (type !== "timer" && type !== "enterArea") {
      ruleCard.append(muted(`When: '${type}' (a newer type, kept as it is)`));
    } else {
      const condition = row(muted("When"), select([["timer", "Timer"], ["enterArea", "Enter area"]], type, (value) => {
        if (value === "timer") report(app, edit.setTimer(model, id, edit.NEW_TIMER_SECONDS));
        else if (areas.length === 0) app.showStatus("There is no area yet: press Add Area", true);
        else report(app, edit.setEnterArea(model, id, areas[0]![0], edit.WHO_PLAYER));
      }, 100));
      if (type === "timer") {
        condition.append(numberInput(Number(when["seconds"] ?? 0), (seconds) => report(app, edit.setTimer(model, id, seconds))), muted("s"));
      } else {
        const who = String(when["who"] ?? edit.WHO_PLAYER);
        condition.append(select(areas, String(when["area"] ?? ""), (area) => report(app, edit.setEnterArea(model, id, area, who)), 100), muted("by"),
          select(whos, who, (value) => report(app, edit.setEnterArea(model, id, String(when["area"]), value)), 100));
      }
      ruleCard.append(condition);
    }
    const then = Array.isArray(rule["then"]) ? (rule["then"] as Record<string, unknown>[]) : [];
    then.forEach((effect, effectIndex) => {
      const effectType = String(effect["type"] ?? "");
      if (!EFFECTS.some(([value]) => value === effectType)) {
        ruleCard.append(muted(`Do: '${effectType}' (a newer type, kept as it is)`));
        return;
      }
      const line = row(muted("Do"), select(EFFECTS, effectType, (value) => report(app, edit.setEffectType(model, id, effectIndex, value)), 130));
      if (effectType === "showMessage") {
        const seconds = Number(effect["seconds"] ?? edit.DEFAULT_MESSAGE_SECONDS);
        const text = String(effect["text"] ?? "");
        line.append(textInput(text, (value) => report(app, edit.setMessage(model, id, effectIndex, value, seconds)), 120),
          numberInput(seconds, (value) => report(app, edit.setMessage(model, id, effectIndex, text, value)), 44));
      } else if (effectType === "completeObjective") {
        line.append(select(objectiveChoices, String(effect["objective"] ?? ""), (value) => report(app, edit.setCompletedObjective(model, id, effectIndex, value)), 140));
      } else {
        line.append(select([["victory", "Victory"], ["defeat", "Defeat"], ["draw", "Draw"]], String(effect["result"] ?? "victory"),
          (value) => report(app, edit.setEndResult(model, id, effectIndex, value)), 100));
      }
      line.append(button("×", () => report(app, edit.removeEffect(model, id, effectIndex)), "Remove the effect"));
      ruleCard.append(line);
    });
    if (then.length < 8) ruleCard.append(row(button("+ Effect", () => report(app, edit.addEffect(model, id, "showMessage")))));
    cards.push(ruleCard);
  });
  return cards;
}
