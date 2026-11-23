import { NullEngine, Scene, TransformNode, Vector3 } from "@babylonjs/core";
import { transformWithOxc } from "vite";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GameEntity, GameScene } from "../src/runtime/GameEntity";
import { graphEntity } from "../src/runtime/GraphEntity";
import { ScriptComponent } from "../src/runtime/ScriptComponent";
import { className, generateTypeScript } from "../src/visualscript/codegen";
import { Graph } from "../src/visualscript/graph";
import { GraphInstance, type GraphHost } from "../src/visualscript/interpreter";
import { createDefaultRegistry, EXEC_IN, NODE } from "../src/visualscript/nodes";
import { Builder, readSampleGraph } from "./graphSupport";

const REGISTRY = createDefaultRegistry();
const FRAME = 0.1;
const FRAMES = 20;
const MESSAGE = 'start "quoted" ${dollar} \\ back';

function makeEntity(babylon: Scene, name: string): GameEntity {
  const data = {
    id: name, name, active: true, parentId: null,
    transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
    components: [],
  };
  return new GameEntity(data, new TransformNode(name, babylon));
}

/** A scene of one entity named "Self" on a headless engine. */
function entityIn(engine: NullEngine): { entity: GameEntity; scene: GameScene; babylon: Scene } {
  const babylon = new Scene(engine);
  babylon.useRightHandedSystem = true;
  const entity = makeEntity(babylon, "Self");
  return { entity, scene: new GameScene([entity]), babylon };
}

/** The graph of the desktop test of generated code: variables, a branch, a vector, a delay and text. */
function pulseGraph(): Graph {
  const graph = new Graph("pulse test");
  graph.variables.push({ name: "count", type: "FLOAT", value: 0 });
  graph.variables.push({ name: "message", type: "STRING", value: MESSAGE });
  const b = new Builder(graph);

  const begin = b.node(NODE.beginPlay);
  const printStart = b.node(NODE.print);
  b.wire(b.node(NODE.getVariable, {}, { variable: "message" }), "value", printStart, "message");
  const setCount = b.node(NODE.setVariable, {}, { variable: "count" });
  const plusOne = b.node(NODE.add, { b: 1 });
  b.wire(b.node(NODE.getVariable, {}, { variable: "count" }), "value", plusOne, "a");
  b.wire(plusOne, "result", setCount, "value");
  const branch = b.node(NODE.branch);
  const greater = b.node(NODE.greater);
  b.wire(b.node(NODE.getVariable, {}, { variable: "count" }), "value", greater, "a");
  b.wire(greater, "result", branch, "condition");
  const setPosition = b.node(NODE.setPosition);
  const make = b.node(NODE.makeVector, { x: 1, y: 2, z: 3 });
  b.wire(make, "vector", setPosition, "position");
  const never = b.node(NODE.print, { message: "never" });
  b.exec(begin, printStart);
  b.exec(printStart, setCount);
  b.exec(setCount, branch);
  b.wire(branch, "true", setPosition, EXEC_IN);
  b.wire(branch, "false", never, EXEC_IN);

  // Every tick moves along x by the frame time, and after each 0.35 s delay turns 90 degrees and reports the count
  const tick = b.node(NODE.tick);
  const both = b.node(NODE.sequence);
  b.exec(tick, both);
  const moveBy = b.node(NODE.moveBy);
  const unit = b.node(NODE.makeVector, { x: 1 });
  const scale = b.node(NODE.scaleVector);
  b.wire(unit, "vector", scale, "vector");
  b.wire(tick, "deltaTime", scale, "factor");
  b.wire(scale, "result", moveBy, "offset");
  b.wire(both, "then0", moveBy, EXEC_IN);
  const delay = b.node(NODE.delay, { seconds: 0.35 });
  b.wire(both, "then1", delay, EXEC_IN);
  const rotate = b.node(NODE.rotateBy);
  b.wire(b.node(NODE.makeVector, { x: 90 }), "vector", rotate, "degrees");
  const setAgain = b.node(NODE.setVariable, {}, { variable: "count" });
  const plusOneAgain = b.node(NODE.add, { b: 1 });
  b.wire(b.node(NODE.getVariable, {}, { variable: "count" }), "value", plusOneAgain, "a");
  b.wire(plusOneAgain, "result", setAgain, "value");
  const report = b.node(NODE.print);
  const append = b.node(NODE.append, { a: "count=" });
  const asText = b.node(NODE.numberToText);
  b.wire(b.node(NODE.getVariable, {}, { variable: "count" }), "value", asText, "value");
  b.wire(asText, "text", append, "b");
  b.wire(append, "result", report, "message");
  b.exec(delay, rotate, "completed");
  b.exec(rotate, setAgain);
  b.exec(setAgain, report);
  return graph;
}

interface Outcome {
  lines: string[];
  position: Vector3;
  yaw: number;
}

function yawOf(entity: GameEntity): number {
  const q = entity.getWorldRotation();
  return (Math.atan2(2 * (q.y * q.w + q.x * q.z), 1 - 2 * (q.y * q.y + q.x * q.x)) * 180) / Math.PI;
}

function interpret(graph: Graph): Outcome {
  const engine = new NullEngine();
  const { entity, scene } = entityIn(engine);
  const lines: string[] = [];
  const host: GraphHost = { log: (_i, message) => lines.push(message), error: (_i, error) => { throw error; } };
  const instance = new GraphInstance(graph, REGISTRY, graphEntity(entity, scene), host);
  instance.beginPlay();
  for (let i = 0; i < FRAMES; i++) instance.tick(FRAME);
  const outcome = { lines, position: entity.getWorldPosition(), yaw: yawOf(entity) };
  engine.dispose();
  return outcome;
}

/**
 * Compiles the generated source (types stripped by the same Oxc transform Vite uses) and returns the script class it
 * exports. The two imports of a generated script are replaced by the modules the test hands in.
 */
async function compile(source: string, name: string): Promise<new () => ScriptComponent> {
  const { code } = await transformWithOxc(source, "Generated.ts", { lang: "ts" });
  const modules: Record<string, unknown> = {
    "@babylonjs/core": { Vector3 },
    "../runtime/ScriptComponent": { ScriptComponent },
  };
  const body = code
    .replace(/^import \{ (.+?) \} from "(.+?)";$/gm, (_all, names: string, module: string) => {
      if (!(module in modules)) throw new Error(`The generated script imports ${module}`);
      return `const { ${names} } = __modules[${JSON.stringify(module)}];`;
    })
    .replace(/^export class /m, "class ");
  expect(body, "no import left that the test cannot satisfy").not.toMatch(/^import /m);
  return new Function("__modules", `${body}
return ${name};`)(modules) as new () => ScriptComponent;
}

async function runGenerated(graph: Graph): Promise<Outcome> {
  const source = generateTypeScript(graph, REGISTRY);
  const Script = await compile(source, className(graph));
  const engine = new NullEngine();
  const { entity, scene } = entityIn(engine);
  const lines: string[] = [];
  vi.spyOn(console, "log").mockImplementation((message: unknown) => void lines.push(String(message)));
  const script = new Script();
  script.entity = entity;
  script.scene = scene;
  script.onStart();
  for (let i = 0; i < FRAMES; i++) script.onUpdate(FRAME);
  const outcome = { lines, position: entity.getWorldPosition(), yaw: yawOf(entity) };
  engine.dispose();
  return outcome;
}

afterEach(() => vi.restoreAllMocks());

describe("generateTypeScript", () => {
  it("shouldMakeAScriptThatDoesWhatTheInterpreterDoes", async () => {
    const graph = pulseGraph();
    const expected = interpret(graph);

    expect(expected.lines.length).toBeGreaterThan(3);
    expect(expected.lines[0]).toBe(MESSAGE);
    const actual = await runGenerated(graph);

    expect(actual.lines).toEqual(expected.lines);
    expect(actual.position.x).toBeCloseTo(expected.position.x, 6);
    expect(actual.position.y).toBeCloseTo(expected.position.y, 6);
    expect(actual.position.z).toBeCloseTo(expected.position.z, 6);
    expect(actual.yaw).toBeCloseTo(expected.yaw, 4);
  });

  it("shouldMakeTheSampleGraphsDoWhatTheyDo", async () => {
    for (const name of ["Spinner", "Hop", "Greeter"]) {
      const graph = readSampleGraph(name);
      const expected = interpret(graph);
      const actual = await runGenerated(graph);
      expect(actual.lines, name).toEqual(expected.lines);
      expect(actual.position.y, name).toBeCloseTo(expected.position.y, 6);
      expect(actual.yaw, name).toBeCloseTo(expected.yaw, 4);
    }
  });

  it("shouldNameTheClassAfterTheGraphAndSayWhereToSaveIt", () => {
    const source = generateTypeScript(pulseGraph(), REGISTRY);

    expect(className(pulseGraph())).toBe("PulseTest");
    expect(className(new Graph("2d"))).toBe("Graph2d");
    expect(source).toContain("export class PulseTest extends ScriptComponent");
    expect(source).toContain("src/scripts/PulseTest.ts");
  });

  it("shouldRefuseWhatTheInterpreterRefusesAndLoopsCodeCannotExpress", () => {
    const broken = new Graph("Broken");
    broken.addNode({ id: "n1", type: "unknown.type", x: 0, y: 0, literals: {}, properties: {} });
    expect(() => generateTypeScript(broken, REGISTRY)).toThrow(/problem/);

    const loop = new Graph("Loop");
    const b = new Builder(loop);
    const begin = b.node(NODE.beginPlay);
    const print = b.node(NODE.print);
    const again = b.node(NODE.print);
    b.exec(begin, print);
    b.exec(print, again);
    b.exec(again, print);
    expect(() => generateTypeScript(loop, REGISTRY)).toThrow(/loop/);
  });

  it("shouldMakeCollisionAndCustomEventsCallable", async () => {
    const graph = new Graph("Events");
    const b = new Builder(graph);
    const collision = b.node(NODE.collision);
    const print = b.node(NODE.print);
    const name = b.node(NODE.getName);
    b.wire(collision, "other", name, "target");
    b.wire(name, "name", print, "message");
    b.exec(collision, print);
    const custom = b.node(NODE.custom, {}, { name: "open" });
    b.exec(custom, b.node(NODE.print, { message: "opened" }));

    const Script = await compile(generateTypeScript(graph, REGISTRY), "Events");
    const engine = new NullEngine();
    const { entity, scene, babylon } = entityIn(engine);
    const other = makeEntity(babylon, "Player");
    const lines: string[] = [];
    vi.spyOn(console, "log").mockImplementation((message: unknown) => void lines.push(String(message)));
    const script = new Script() as ScriptComponent & { onCollision(other: GameEntity): void; fireCustomEvent(name: string): boolean };
    script.entity = entity;
    script.scene = scene;

    script.onCollision(other);
    expect(script.fireCustomEvent("open")).toBe(true);
    expect(script.fireCustomEvent("close")).toBe(false);

    expect(lines).toEqual(["Player", "opened"]);
    engine.dispose();
  });
});
