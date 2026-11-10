import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { Graph, parseGraph, serializeGraph } from "../src/visualscript/graph";
import { GraphInstance, MAX_NODES_PER_EVENT, type GraphHost, type GraphRuntimeError } from "../src/visualscript/interpreter";
import { createDefaultRegistry, EXEC_IN, NODE, type GraphEntity } from "../src/visualscript/nodes";
import { GraphStore } from "../src/visualscript/store";
import { Builder, readSampleGraph } from "./graphSupport";
import { connectionProblem, DESKTOP_ONLY_NODE_TYPES, validateGraph } from "../src/visualscript/validator";

const JAVA_NODES = join(
  import.meta.dirname, "..", "..", "engine-visualscript", "src", "main", "java", "io", "github", "ldev22live", "gdxunity", "engine", "visualscript", "BuiltInNodes.java",
);
const REGISTRY = createDefaultRegistry();
const FRAME = 1 / 60;

class FakeEntity implements GraphEntity {
  position = { x: 0, y: 0, z: 0 };
  yaw = 0;
  destroyed = false;

  constructor(readonly name: string) {}

  getWorldPosition() {
    return { ...this.position };
  }

  setWorldPosition(position: { x: number; y: number; z: number }): void {
    this.position = { ...position };
  }

  rotate(yaw: number): void {
    this.yaw += yaw;
  }

  destroy(): void {
    this.destroyed = true;
  }
}

class RecordingHost implements GraphHost {
  readonly lines: string[] = [];
  readonly errors: GraphRuntimeError[] = [];

  log(_instance: GraphInstance, message: string): void {
    this.lines.push(message);
  }

  error(_instance: GraphInstance, error: GraphRuntimeError): void {
    this.errors.push(error);
  }
}

function run(graph: Graph, frames: number): { entity: FakeEntity; host: RecordingHost; instance: GraphInstance } {
  const entity = new FakeEntity("Thing");
  const host = new RecordingHost();
  const instance = new GraphInstance(graph, REGISTRY, entity, host);
  instance.beginPlay();
  for (let i = 0; i < frames; i++) instance.tick(FRAME);
  return { entity, host, instance };
}

describe("graph files", () => {
  it("shouldReadAndWriteTheSampleGraphsOfTheDesktopEditor", () => {
    for (const name of ["Spinner", "Hop", "Greeter"]) {
      const graph = readSampleGraph(name);
      expect(graph.name).toBe(name);
      expect(parseGraph(serializeGraph(graph))).toEqual(graph);
      expect(validateGraph(graph, REGISTRY)).toEqual([]);
    }
  });

  it("shouldRejectFilesItCannotRead", () => {
    expect(() => parseGraph("")).toThrow(/empty/);
    expect(() => parseGraph("{")).toThrow(/Malformed/);
    expect(() => parseGraph(JSON.stringify({ schemaVersion: 2, kind: "graph" }))).toThrow(/schemaVersion/);
    expect(() => parseGraph(JSON.stringify({ schemaVersion: 1, kind: "scene" }))).toThrow(/Not a graph/);
    expect(() => parseGraph(JSON.stringify({ schemaVersion: 1, kind: "graph", variables: [{ name: "e", type: "ENTITY", value: null }] }))).toThrow(/entities/);
  });

  it("shouldOfferTheSameNodeTypesAsTheDesktopEditor", () => {
    const java = readFileSync(JAVA_NODES, "utf8");
    const desktopIds = [...java.matchAll(/public static final String (?:EVENT|FLOW|ACTION|VARIABLE|DATA|MATH|LOGIC|VECTOR|TEXT|INPUT)_\w+ = "([\w.]+)";/g)].map((m) => m[1]!);
    expect(desktopIds.length).toBeGreaterThan(30);
    // The 3D physics and character nodes run only on the desktop: the web editor lists them as such, not as nodes it offers
    const shared = desktopIds.filter((id) => !DESKTOP_ONLY_NODE_TYPES.includes(id));
    const componentJava = readFileSync(JAVA_NODES.replace("BuiltInNodes.java", "ComponentNodes.java"), "utf8");
    const components = [...new Set([...componentJava.matchAll(/(?:RENDER|AUDIO) \+ "(\w+)#/g)].map((match) => `component.get:${match[1]}`))];
    const loopsJava = readFileSync(JAVA_NODES.replace("BuiltInNodes.java", "LoopNodes.java"), "utf8");
    const loops = [...loopsJava.matchAll(/public static final String \w+ = "(flow\.\w+)"/g)].map((match) => match[1]!);
    // The Java method nodes (TD-065) are checked against the desktop by javaMethodNodes.test.ts
    expect(REGISTRY.all().map((type) => type.id).filter((id) => !id.startsWith("java:")).sort()).toEqual([...shared, ...components, ...loops].sort());
    for (const id of DESKTOP_ONLY_NODE_TYPES) {
      expect(desktopIds).toContain(id);
    }
  });
});

describe("validateGraph", () => {
  it("shouldRefuseWiresBetweenKindsAndTypes", () => {
    const flow = REGISTRY.require(NODE.branch).pins({ id: "x", type: NODE.branch, x: 0, y: 0, literals: {}, properties: {} }, new Graph("t"));
    const execOut = flow.find((pin) => pin.id === "true")!;
    const condition = flow.find((pin) => pin.id === "condition")!;
    const execIn = flow.find((pin) => pin.id === EXEC_IN)!;

    expect(connectionProblem(execOut, execIn)).toBeNull();
    expect(connectionProblem(execOut, condition)).toMatch(/Execution pins connect only/);
    expect(connectionProblem(condition, execIn)).toMatch(/output to an input/);
  });

  it("shouldReportMissingSettingsUnknownTypesDoubleWiresAndDataLoops", () => {
    const graph = new Graph("Broken");
    const b = new Builder(graph);
    const get = b.node(NODE.getVariable);
    graph.addNode({ id: "u", type: "nope", x: 0, y: 0, literals: {}, properties: {} });
    const tick = b.node(NODE.tick);
    const printA = b.node(NODE.print);
    const printB = b.node(NODE.print);
    b.exec(tick, printA);
    b.exec(tick, printB);
    const addA = b.node(NODE.add);
    const addB = b.node(NODE.add);
    b.wire(addA, "result", addB, "a");
    b.wire(addB, "result", addA, "a");

    const messages = validateGraph(graph, REGISTRY).map((problem) => problem.message);

    expect(messages).toContain("Get Variable needs a variable");
    expect(messages).toContain("Unknown node type nope");
    expect(messages.some((m) => m.includes("more than one wire"))).toBe(true);
    expect(messages.some((m) => m.includes("loop"))).toBe(true);
    expect(get).toBeDefined();
  });

  it("shouldExplainThatDesktopOnlyPhysicsNodesCannotRunOnTheWeb", () => {
    const graph = new Graph("Physics");
    graph.addNode({ id: "f", type: "action.addForce", x: 0, y: 0, literals: {}, properties: {} });
    graph.addNode({ id: "r", type: "action.raycast3d", x: 0, y: 0, literals: {}, properties: {} });
    graph.addNode({ id: "m", type: "action.moveCharacter", x: 0, y: 0, literals: {}, properties: {} });
    graph.addNode({ id: "j", type: "action.jumpCharacter", x: 0, y: 0, literals: {}, properties: {} });
    graph.addNode({ id: "g", type: "data.isGrounded", x: 0, y: 0, literals: {}, properties: {} });

    const problems = validateGraph(graph, REGISTRY);

    expect(problems).toHaveLength(5);
    for (const problem of problems) {
      expect(problem.message).toContain("needs 3D physics");
      expect(problem.message).toContain("desktop");
      expect(problem.message).not.toContain("Unknown node type");
    }
  });

  it("shouldRefuseToRunAGraphWithProblems", () => {
    const graph = new Graph("Broken");
    new Builder(graph).node(NODE.getVariable);

    expect(() => new GraphInstance(graph, REGISTRY, new FakeEntity("x"), new RecordingHost())).toThrow(/1 problem/);
  });
});

describe("GraphInstance", () => {
  it("shouldTurnTheSpinnerNinetyDegreesEachSecond", () => {
    const { entity } = run(readSampleGraph("Spinner"), 60);

    expect(entity.yaw).toBeCloseTo(90, 5);
  });

  it("shouldHopUpThenDownAfterTheDelays", () => {
    const graph = readSampleGraph("Hop");
    const entity = new FakeEntity("Hopper");
    const instance = new GraphInstance(graph, REGISTRY, entity, new RecordingHost());
    instance.beginPlay();
    const frames = (count: number): number => {
      for (let i = 0; i < count; i++) instance.tick(FRAME);
      return entity.position.y;
    };

    expect(frames(30)).toBe(0);
    expect(frames(30)).toBe(120);
    expect(frames(48)).toBe(0);
  });

  it("shouldPrintTheSineAndCosineOfDegrees", () => {
    const graph = new Graph("Trig");
    const b = new Builder(graph);
    const begin = b.node(NODE.beginPlay);
    const sine = b.node(NODE.sine, { degrees: 30 });
    const cosine = b.node(NODE.cosine, { degrees: 60 });
    const printSine = b.node(NODE.print);
    const printCosine = b.node(NODE.print);
    const sineText = b.node(NODE.numberToText);
    const cosineText = b.node(NODE.numberToText);
    b.wire(sine, "result", sineText, "value");
    b.wire(cosine, "result", cosineText, "value");
    b.wire(sineText, "text", printSine, "message");
    b.wire(cosineText, "text", printCosine, "message");
    b.exec(begin, printSine);
    b.exec(printSine, printCosine);
    const host = new RecordingHost();

    new GraphInstance(graph, REGISTRY, new FakeEntity("Trig"), host).beginPlay();

    expect(host.lines).toEqual(["0.5", "0.5"]);
  });

  it("shouldGreetByEntityName", () => {
    const entity = new FakeEntity("Greeter Sphere");
    const host = new RecordingHost();

    new GraphInstance(readSampleGraph("Greeter"), REGISTRY, entity, host).beginPlay();

    expect(host.lines).toEqual(["Hello from a visual script!", "I am Greeter Sphere"]);
  });

  it("shouldKeepRunningLaterEventsAfterANodeFails", () => {
    const graph = new Graph("Fails");
    const b = new Builder(graph);
    const tick = b.node(NODE.tick);
    const divide = b.node(NODE.divide, { a: 1, b: 0 });
    const text = b.node(NODE.numberToText);
    const print = b.node(NODE.print);
    b.wire(divide, "result", text, "value");
    b.wire(text, "text", print, "message");
    b.exec(tick, print);
    const begin = b.node(NODE.beginPlay);
    const hello = b.node(NODE.print, { message: "still here" });
    b.exec(begin, hello);

    const { host } = run(graph, 2);

    expect(host.errors).toHaveLength(2);
    expect(host.errors[0]!.message).toBe("Division by zero");
    expect(host.lines).toEqual(["still here"]);
  });

  it("shouldStopAnEndlessLoopOfExecutionWires", () => {
    const graph = new Graph("Loop");
    const b = new Builder(graph);
    const begin = b.node(NODE.beginPlay);
    const a = b.node(NODE.print, { message: "a" });
    const c = b.node(NODE.print, { message: "c" });
    b.exec(begin, a);
    b.exec(a, c);
    b.exec(c, a);

    const { host } = run(graph, 0);

    expect(host.errors[0]!.message).toContain(String(MAX_NODES_PER_EVENT));
    expect(host.lines.length).toBeLessThanOrEqual(MAX_NODES_PER_EVENT);
  });

  it("shouldRunTheCustomEventsOfThatNameAndSayWhenThereAreNone", () => {
    const graph = new Graph("Events");
    const b = new Builder(graph);
    const custom = b.node(NODE.custom, {}, { name: "open" });
    b.exec(custom, b.node(NODE.print, { message: "opened" }));
    const host = new RecordingHost();
    const instance = new GraphInstance(graph, REGISTRY, new FakeEntity("Door"), host);

    expect(instance.fireCustomEvent("open")).toBe(true);
    expect(instance.fireCustomEvent("close")).toBe(false);
    expect(host.lines).toEqual(["opened"]);
  });

  it("shouldDeliverTheOtherEntityOfACollision", () => {
    const graph = new Graph("Touch");
    const b = new Builder(graph);
    const collision = b.node(NODE.collision);
    const name = b.node(NODE.getName);
    const print = b.node(NODE.print);
    b.wire(collision, "other", name, "target");
    b.wire(name, "name", print, "message");
    b.exec(collision, print);
    const host = new RecordingHost();

    new GraphInstance(graph, REGISTRY, new FakeEntity("Door"), host).collision(new FakeEntity("Player"));

    expect(host.lines).toEqual(["Player"]);
  });

  it("shouldStopWhenItsEntityLeavesTheScene", () => {
    const graph = readSampleGraph("Spinner");
    const entity = new FakeEntity("Gone");
    let alive = true;
    const instance = new GraphInstance(graph, REGISTRY, entity, new RecordingHost(), () => alive);

    instance.tick(FRAME);
    alive = false;
    instance.tick(FRAME);

    expect(entity.yaw).toBeCloseTo(90 * FRAME, 5);
    expect(instance.isRunning).toBe(false);
  });

  it("shouldFormatNumbersAsTheDesktopDoes", () => {
    const graph = new Graph("Numbers");
    const b = new Builder(graph);
    const begin = b.node(NODE.beginPlay);
    let previous = begin;
    for (const value of [3, 2.5, 1 / 3, -4, 0.0004]) {
      const text = b.node(NODE.numberToText, { value });
      const print = b.node(NODE.print);
      b.wire(text, "text", print, "message");
      b.exec(previous, print);
      previous = print;
    }

    expect(run(graph, 0).host.lines).toEqual(["3", "2.5", "0.333", "-4", "0"]);
  });
});

describe("GraphStore", () => {
  it("shouldPreferAnEditedGraphToTheFileAndReadFilesOnce", async () => {
    let reads = 0;
    const store = new GraphStore(async () => {
      reads++;
      return serializeGraph(new Graph("From file"));
    });
    const edited = new Graph("Edited");
    store.set("graphs/Edited.graph", edited);

    expect(await store.load("graphs/Edited.graph")).toBe(edited);
    const [a, b] = await Promise.all([store.load("graphs/File.graph"), store.load("graphs/File.graph")]);
    expect(a).toBe(b);
    expect(reads).toBe(1);
    expect(a.name).toBe("From file");
  });

  it("shouldNameThePathWhenAGraphCannotBeLoaded", async () => {
    const store = new GraphStore(async () => {
      throw new Error("HTTP 404");
    });

    await expect(store.load("graphs/Missing.graph")).rejects.toThrow("Graph graphs/Missing.graph: HTTP 404");
  });
});
