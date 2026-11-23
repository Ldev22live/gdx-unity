import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { parseGraph } from "../src/visualscript/graph";
import { GraphInstance } from "../src/visualscript/interpreter";
import { parseKeyNames } from "../src/visualscript/keys";
import { createDefaultRegistry, type GraphEntity } from "../src/visualscript/nodes";
import { validateGraph } from "../src/visualscript/validator";
import { GRAPHS_DIR } from "./graphSupport";

const REGISTRY = createDefaultRegistry();
const FRAME = 1 / 60;
const RESOURCES = join(import.meta.dirname, "..", "..", "editor-visualscript", "src", "test", "resources");
/** How far the web graph may be from the Java script, in scene units: the web does its sums in doubles, the script in floats. */
const TOLERANCE = 0.1;
const RECORD_EVERY = 10;

interface Scenario {
  start: { x: number; y: number; z: number };
  steps: { frames: number; keys: string[] }[];
  expected: number[][];
  /** The number of sound cues the scenario must play, when it plays any. */
  cues?: number;
}

class FakeEntity implements GraphEntity {
  position: { x: number; y: number; z: number };
  readonly name = "Player";
  readonly cues: string[] = [];
  constructor(start: { x: number; y: number; z: number }) {
    this.position = { ...start };
  }
  getWorldPosition() {
    return { ...this.position };
  }
  setWorldPosition(position: { x: number; y: number; z: number }): void {
    this.position = { ...position };
  }
  rotate(): void {}
  destroy(): void {}
  playCue(cue: string): void {
    this.cues.push(cue);
  }
}

/** Plays a sample graph with the keys of a scenario the desktop recorded from the Java script, and checks every sampled position. */
function replay(graphFile: string, scenarioFile: string): FakeEntity {
  const scenario = JSON.parse(readFileSync(join(RESOURCES, scenarioFile), "utf8")) as Scenario;
  const graph = parseGraph(readFileSync(join(GRAPHS_DIR, "samples", graphFile), "utf8"));
  expect(validateGraph(graph, REGISTRY)).toEqual([]);
  let down = new Set<string>();
  let previous = new Set<string>();
  const errors: string[] = [];
  const entity = new FakeEntity(scenario.start);
  const instance = new GraphInstance(graph, REGISTRY, entity, {
    log: () => {},
    error: (_i, error) => errors.push(error.message),
    isKeyDown: (codes) => codes.some((code) => down.has(code)),
    isKeyJustPressed: (codes) => codes.some((code) => down.has(code) && !previous.has(code)),
  });
  instance.beginPlay();

  const positions: { x: number; y: number; z: number }[] = [];
  let frame = 0;
  for (const step of scenario.steps) {
    const keys = new Set(step.keys.flatMap((name) => parseKeyNames(name).map((key) => key.code)));
    for (let i = 0; i < step.frames; i++) {
      previous = down;
      down = keys;
      instance.tick(FRAME);
      if (++frame % RECORD_EVERY === 0) positions.push(entity.getWorldPosition());
    }
  }

  expect(errors).toEqual([]);
  expect(positions).toHaveLength(scenario.expected.length);
  positions.forEach((position, index) => {
    const [x, y, z] = scenario.expected[index]!;
    const at = `frame ${(index + 1) * RECORD_EVERY}`;
    expect(Math.abs(position.x - x!), `x at ${at}: ${position.x} against ${x}`).toBeLessThan(TOLERANCE);
    expect(Math.abs(position.y - y!), `y at ${at}: ${position.y} against ${y}`).toBeLessThan(TOLERANCE);
    expect(Math.abs(position.z - z!), `z at ${at}: ${position.z} against ${z}`).toBeLessThan(TOLERANCE);
  });
  if (scenario.cues !== undefined) expect(entity.cues).toEqual(Array(scenario.cues).fill("jump"));
  return entity;
}

describe("sample graphs on the web (TD-071, TD-072)", () => {
  it("moves the Collector ball like the Java script, at the positions the desktop recorded", () => {
    replay("CollectorPlayer.graph", "collector-trajectory.json");
  });

  it("moves the Runner player like the Java script, and plays the jump sound once per take-off", () => {
    const entity = replay("RunnerPlayer.graph", "runner-trajectory.json");

    expect(entity.cues).toHaveLength(3);
  });
});
