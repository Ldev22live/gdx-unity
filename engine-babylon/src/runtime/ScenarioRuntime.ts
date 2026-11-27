import type { ComponentData } from "../scene/types";

/**
 * Runs a scene's scenario rules while it plays (TD-109, roadmap A14): the web port of the desktop's
 * `engine-scene/.../scenario/ScenarioRuntime`, with the same semantics, checked by the shared trace fixture
 * `fixtures/contract-v1/scenario-trace/valley.json` on both engines. It never runs code, loads modules or calls graphs.
 *
 * Each {@link update} (one Play frame, game time) checks the rules in list order. A rule whose condition holds runs its effects in
 * order. Effects only change what is shown (message, objectives, result), which no condition reads, so rules cannot trigger each
 * other. `endScenario` stops all further evaluation. Invalid rules are skipped and listed once in {@link skipped}.
 *
 * - `timer` fires once Play has run `seconds`; with `once: false`, again every `seconds`, at most once a frame.
 * - `enterArea` fires in a frame in which a matching entity entered the area's trigger (3D) or sensor (2D) collider since the
 *   last frame, once per frame however many entered. Leaving an area does nothing in Alpha.
 */

/** How long a message shows when its rule gives no `seconds`. */
export const DEFAULT_MESSAGE_SECONDS = 4;
/** The longest a message shows (schema `showMessage.seconds` maximum). */
export const MAX_MESSAGE_SECONDS = 60;
/** Most rules (schema `rules.maxItems`) and effects per rule (schema `then.maxItems`). */
export const MAX_RULES = 256;
export const MAX_EFFECTS = 8;
/** The scenario block version this runtime reads. */
export const SUPPORTED_VERSION = 1;
const CONDITIONS = new Set(["timer", "enterArea"]);
const EFFECTS = new Set(["showMessage", "completeObjective", "endScenario"]);
const WHO_PLAYER = "player";
/** The desktop's `Collider3DComponent` and `Collider2DComponent` types, whose `trigger` / `isSensor` makes an area. */
const COLLIDERS_3D = new Set(["BoxCollider3DComponent", "SphereCollider3DComponent", "CapsuleCollider3DComponent",
  "ConvexHullCollider3DComponent", "MeshCollider3DComponent", "TerrainCollider3DComponent"]);
const COLLIDERS_2D = new Set(["BoxCollider2DComponent", "CircleCollider2DComponent"]);

/** What the runtime needs of an entity: the web `GameEntity` has it, and tests pass plain objects. */
export interface RuleEntity {
  readonly id: string;
  readonly components: readonly ComponentData[];
  readonly parent: RuleEntity | null;
}

export type ScenarioResult = "VICTORY" | "DEFEAT" | "DRAW";

/** The label shown on screen: "Victory", "Defeat" or "Draw" (the desktop's `Result.label()`). */
export function resultLabel(result: ScenarioResult): string {
  return result.charAt(0) + result.slice(1).toLowerCase();
}

export interface Objective {
  id: string;
  text: string;
  playerId: string | null;
  visible: boolean;
  complete: boolean;
}

interface Effect {
  type: string;
  text: string | null;
  seconds: number;
  objective: string | null;
  result: ScenarioResult | null;
}

interface Rule {
  name: string;
  once: boolean;
  condition: string;
  seconds: number;
  areaId: string | null;
  who: string | null;
  effects: Effect[];
  fired: boolean;
  timerFirings: number;
  entered: boolean;
}

type Json = Record<string, unknown>;
const isObject = (value: unknown): value is Json => typeof value === "object" && value !== null && !Array.isArray(value);
const list = (value: unknown): Json[] => (Array.isArray(value) ? value.filter(isObject) : []);
const text = (value: unknown): string | null => (typeof value === "string" ? value : null);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function enabled(component: ComponentData): boolean {
  return component.enabled !== false;
}

/** True when the entity has an enabled 3D trigger collider or 2D sensor collider (the desktop's `hasTriggerCollider`). */
export function hasTriggerCollider(entity: RuleEntity): boolean {
  return entity.components.some((component) => enabled(component) && (
    (COLLIDERS_3D.has(component.type) && component.data["trigger"] === true)
    || (COLLIDERS_2D.has(component.type) && component.data["isSensor"] === true)));
}

export class ScenarioRuntime {
  private readonly rules: Rule[] = [];
  private readonly skippedReasons: string[] = [];
  private readonly objectiveList: Objective[] = [];
  private elapsedSeconds = 0;
  private messageText: string | null = null;
  private messageLeft = 0;
  private resultValue: ScenarioResult | null = null;
  private revisionCount = 0;

  private constructor(private readonly log: (line: string) => void) {}

  /**
   * The runtime for a scene file's `scenario` block, or null when it has none. Rules the checks find fault with are skipped (listed
   * in {@link skipped}); a block of a newer version runs nothing.
   *
   * @param entityById the playing scene's entities, for area and owner lookups
   * @throws Error when the block has more than {@link MAX_RULES} rules or a rule more than {@link MAX_EFFECTS} effects
   */
  static create(scenario: unknown, entityById: (id: string) => RuleEntity | undefined, log: (line: string) => void): ScenarioRuntime | null {
    if (scenario === undefined || scenario === null) return null;
    const runtime = new ScenarioRuntime(log);
    if (!isObject(scenario)) {
      runtime.skippedReasons.push("The scenario block must be an object");
      return runtime;
    }
    const version = typeof scenario["version"] === "number" ? scenario["version"] : -1;
    if (version > SUPPORTED_VERSION) {
      runtime.skippedReasons.push(`Scenario version ${version} needs a newer editor (this one reads ${SUPPORTED_VERSION}); it is kept and not run`);
      return runtime;
    }
    if (version !== SUPPORTED_VERSION) {
      runtime.skippedReasons.push(`Missing or invalid scenario version: ${version}`);
      return runtime;
    }
    const players = new Set(list(scenario["players"]).map((player) => text(player["id"])).filter((id): id is string => id !== null));
    const objectiveIds = new Set<string>();
    for (const objective of list(scenario["objectives"])) {
      const id = text(objective["id"]) ?? "";
      objectiveIds.add(id);
      runtime.objectiveList.push({
        id, text: text(objective["text"]) ?? "", playerId: text(objective["player"]),
        visible: objective["initial"] !== "hidden", complete: false,
      });
    }
    const rules = Array.isArray(scenario["rules"]) ? scenario["rules"] : [];
    if (rules.length > MAX_RULES) throw new Error(`A scenario has at most ${MAX_RULES} rules; this one has ${rules.length}`);
    rules.forEach((rule, index) => {
      if (!isObject(rule)) {
        runtime.skippedReasons.push(`Rule ${index + 1} is not run: it is not an object`);
        return;
      }
      const then = Array.isArray(rule["then"]) ? rule["then"] : [];
      if (then.length > MAX_EFFECTS) {
        throw new Error(`Rule '${text(rule["name"]) ?? "?"}' has ${then.length} effects; at most ${MAX_EFFECTS}`);
      }
      const problem = ScenarioRuntime.problemOf(rule, then, entityById, players, objectiveIds);
      if (problem !== null) {
        runtime.skippedReasons.push(`Rule ${index + 1} is not run: ${problem}`);
        return;
      }
      if (rule["enabled"] !== true) return;
      const when = rule["when"] as Json;
      runtime.rules.push({
        name: text(rule["name"]) ?? `Rule ${index + 1}`,
        once: rule["once"] !== false,
        condition: String(when["type"]),
        seconds: typeof when["seconds"] === "number" ? when["seconds"] : 0,
        areaId: text(when["area"]),
        who: text(when["who"]),
        effects: (then as Json[]).map((effect) => ({
          type: String(effect["type"]),
          text: text(effect["text"]),
          seconds: Math.min(MAX_MESSAGE_SECONDS, Math.max(0, typeof effect["seconds"] === "number" ? effect["seconds"] : DEFAULT_MESSAGE_SECONDS)),
          objective: text(effect["objective"]),
          result: typeof effect["result"] === "string" ? (effect["result"].toUpperCase() as ScenarioResult) : null,
        })),
        fired: false, timerFirings: 0, entered: false,
      });
    });
    return runtime;
  }

  /** Why a rule cannot run (the desktop validator's ERROR and NOT_RUN findings for rules), or null when it can. */
  private static problemOf(rule: Json, then: unknown[], entityById: (id: string) => RuleEntity | undefined, players: Set<string>,
                           objectives: Set<string>): string | null {
    if (!UUID.test(String(rule["id"]))) return `Not a UUID: ${String(rule["id"])}`;
    const when = rule["when"];
    const condition = isObject(when) ? text(when["type"]) : null;
    if (condition === null) return "A rule needs a condition";
    if (!CONDITIONS.has(condition)) return `Condition '${condition}' is not in this version's rule set; the rule is kept and not run`;
    if (condition === "enterArea" && isObject(when)) {
      const areaId = text(when["area"]);
      const area = areaId === null ? undefined : entityById(areaId);
      if (!area) return `Area entity ${areaId} is not in the scene`;
      if (!hasTriggerCollider(area)) return `Area '${areaId}' has no trigger collider, so nothing can enter it (TD-109)`;
      const who = text(when["who"]);
      if (who !== WHO_PLAYER && (who === null || !players.has(who))) return `'${who}' is neither 'player' nor a defined player`;
    }
    if (then.length === 0) return "A rule needs at least one effect";
    for (const effect of then) {
      const type = isObject(effect) ? text(effect["type"]) : null;
      if (type === null || !EFFECTS.has(type)) return `Effect '${type}' is not in this version's rule set; the rule is kept and not run`;
      const e = effect as Json;
      if (type === "completeObjective" && !objectives.has(text(e["objective"]) ?? "")) return `Objective ${String(e["objective"])} is not defined`;
      if (type === "endScenario" && e["player"] !== undefined && !players.has(String(e["player"]))) return `Player ${String(e["player"])} is not defined`;
    }
    return null;
  }

  // ---- events --------------------------------------------------------------------------------------------------------------

  /** Records that `visitor` entered `area` (a 3D trigger or 2D sensor began overlapping); matching rules fire on the next update. */
  onEnter(area: RuleEntity, visitor: RuleEntity): void {
    if (this.resultValue !== null) return;
    for (const rule of this.rules) {
      if (rule.condition === "enterArea" && rule.areaId === area.id && ScenarioRuntime.matches(rule.who, visitor)) rule.entered = true;
    }
  }

  /** Feeds a contact of either engine: only trigger and sensor enters count, in both directions. */
  onContact(entityA: RuleEntity, entityB: RuleEntity, kind: string): void {
    if (kind !== "TRIGGER_ENTER" && kind !== "SENSOR_ENTER") return;
    this.onEnter(entityA, entityB);
    this.onEnter(entityB, entityA);
  }

  /** True when `visitor` (or an entity it belongs to) is the controlled player, or is owned by player `who`. */
  private static matches(who: string | null, visitor: RuleEntity): boolean {
    for (let at: RuleEntity | null = visitor; at !== null; at = at.parent) {
      if (who === WHO_PLAYER) {
        if (at.components.some((c) => c.type === "PlayerComponent" && enabled(c))) return true;
      } else {
        const owner = at.components.find((c) => c.type === "OwnerComponent" && enabled(c));
        if (owner) return owner.data["player"] === who;
      }
    }
    return false;
  }

  // ---- the frame -----------------------------------------------------------------------------------------------------------

  /** Advances game time by `deltaTime` seconds and checks the rules in list order. Not called while Paused. */
  update(deltaTime: number): void {
    const delta = Number.isFinite(deltaTime) && deltaTime >= 0 ? deltaTime : 0;
    this.elapsedSeconds += delta;
    if (this.messageText !== null) {
      this.messageLeft -= delta;
      if (this.messageLeft <= 0) {
        this.messageText = null;
        this.revisionCount++;
      }
    }
    for (const rule of this.rules) {
      if (this.resultValue !== null) break;
      const entered = rule.entered;
      rule.entered = false;
      if (rule.once && rule.fired) continue;
      if (this.holds(rule, entered)) this.fire(rule);
    }
  }

  private holds(rule: Rule, entered: boolean): boolean {
    if (rule.condition === "enterArea") return entered;
    if (rule.once || rule.seconds <= 0) return this.elapsedSeconds >= rule.seconds;
    // A repeating timer fires each time another whole period has passed, at most once a frame
    const periods = Math.floor(this.elapsedSeconds / rule.seconds);
    if (periods > rule.timerFirings) {
      rule.timerFirings = periods;
      return true;
    }
    return false;
  }

  private fire(rule: Rule): void {
    rule.fired = true;
    const done: string[] = [];
    for (const effect of rule.effects) {
      if (this.resultValue !== null) break;
      if (effect.type === "showMessage") {
        this.messageText = effect.text;
        this.messageLeft = effect.seconds;
        done.push(`message "${effect.text}"`);
      } else if (effect.type === "completeObjective") {
        for (const objective of this.objectiveList) {
          if (objective.id === effect.objective) {
            objective.complete = true;
            objective.visible = true;
          }
        }
        done.push("objective complete");
      } else if (effect.type === "endScenario") {
        this.resultValue = effect.result;
        done.push(`end: ${resultLabel(effect.result!)}`);
      }
    }
    this.revisionCount++;
    this.log(`Rule '${rule.name}' at ${this.elapsedSeconds.toFixed(1)} s: ${done.join(", ")}`);
  }

  // ---- what Play shows -----------------------------------------------------------------------------------------------------

  get elapsed(): number {
    return this.elapsedSeconds;
  }

  get message(): string | null {
    return this.messageText;
  }

  get objectives(): readonly Objective[] {
    return this.objectiveList;
  }

  get result(): ScenarioResult | null {
    return this.resultValue;
  }

  get skipped(): readonly string[] {
    return this.skippedReasons;
  }

  get ruleCount(): number {
    return this.rules.length;
  }

  /** Changes whenever the message, an objective or the result changes, so a view redraws only then. */
  get revision(): number {
    return this.revisionCount;
  }

  /** The visible objectives as HUD lines (`[x] Reach the house`), as the desktop's `ScenarioRuntimeComponent.objectiveLines`. */
  objectiveLines(): string[] {
    return this.objectiveList.filter((o) => o.visible).map((o) => `${o.complete ? "[x]" : "[ ]"} ${o.text}`);
  }
}
