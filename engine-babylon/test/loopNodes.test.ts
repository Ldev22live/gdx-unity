import { describe, expect, it } from "vitest";
import { Graph, parseGraph, serializeGraph } from "../src/visualscript/graph";
import { GraphInstance } from "../src/visualscript/interpreter";
import { createDefaultRegistry, type GraphEntity } from "../src/visualscript/nodes";
import { generateTypeScript } from "../src/visualscript/codegen";
import { generateJvm } from "../src/visualscript/codegenJvm";
import { Builder } from "./graphSupport";

const registry = createDefaultRegistry();
const self: GraphEntity = { name: "Self", getWorldPosition: () => ({ x: 0, y: 0, z: 0 }), setWorldPosition: () => {}, rotate: () => {}, destroy: () => {} };

function example(count: number, nested = 0) {
  const graph = new Graph("Repeat");
  const b = new Builder(graph);
  const loop = b.node("flow.repeat", { count });
  graph.requireNode(loop).integerLiterals = new Set(["count"]);
  b.exec(b.node("event.beginPlay"), loop);
  if (nested) {
    const inner = b.node("flow.repeat", { count: nested });
    graph.requireNode(inner).integerLiterals = new Set(["count"]);
    b.exec(loop, inner, "body");
    b.exec(inner, b.node("action.print", { message: "inner" }), "body");
    b.exec(inner, b.node("action.print", { message: "outer" }), "completed");
  } else {
    const text = b.node("text.fromFloat");
    const print = b.node("action.print");
    b.wire(loop, "index", text, "value");
    b.wire(text, "text", print, "message");
    b.exec(loop, print, "body");
  }
  b.exec(loop, b.node("action.print", { message: "done" }), "completed");
  const lines: string[] = [];
  const errors: string[] = [];
  const instance = new GraphInstance(parseGraph(serializeGraph(graph)), registry, self,
    { log: (_graph, line) => lines.push(line), error: (_graph, error) => errors.push(error.message) });
  instance.beginPlay();
  return { graph, lines, errors };
}

describe("bounded Repeat", () => {
  it("shouldRunTheBodyInIndexOrderBeforeCompleted", () => {
    expect(example(3).lines).toEqual(["0", "1", "2", "done"]);
  });
  it("shouldSkipTheBodyForZeroIterations", () => {
    expect(example(0).lines).toEqual(["done"]);
  });
  it("shouldFinishNestedBodiesBeforeTheNextOuterIteration", () => {
    expect(example(2, 2).lines).toEqual(["inner", "inner", "outer", "inner", "inner", "outer", "done"]);
  });
  it("shouldRejectCountsOutsideTheLimit", () => {
    for (const count of [-1, 1001]) {
      const result = example(count);
      expect(result.errors[0]).toContain("Repeat count");
      expect(result.lines).toEqual([]);
    }
  });
  it("shouldShareTheEventBudgetAcrossNestedLoops", () => {
    const result = example(1000, 1000);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain("10000");
    expect(result.lines).not.toContain("done");
    expect(result.lines.length).toBeLessThan(10000);
  });
  it("shouldRefuseUnsupportedSourceGenerationExplicitly", () => {
    const { graph } = example(2);
    expect(() => generateTypeScript(graph, registry)).toThrow("flow.repeat");
    for (const language of ["java", "kotlin"] as const) expect(() => generateJvm(graph, registry, language)).toThrow("flow.repeat");
  });
});

describe("For Each Entity (roadmap M1.2)", () => {
  function entity(name: string): GraphEntity {
    return { name, getWorldPosition: () => ({ x: 0, y: 0, z: 0 }), setWorldPosition: () => {}, rotate: () => {}, destroy: () => {} };
  }

  /** A registry whose test node outputs `list` on its List pin, standing in for a desktop Java method node. */
  function run(list: unknown) {
    const local = createDefaultRegistry();
    local.register({ id: "test.list", title: "List", category: "Test", kind: "pure", properties: [],
      pins: () => [{ id: "result", label: "Result", type: "OBJECT", objectClass: "java.util.List", input: false, exec: false }],
      run: (c) => c.output("result", list) });
    const graph = new Graph("For each");
    const b = new Builder(graph);
    const loop = b.node("flow.forEachEntity");
    b.wire(b.node("test.list"), "result", loop, "list");
    b.exec(b.node("event.beginPlay"), loop);
    const name = b.node("data.getName");
    const index = b.node("text.fromFloat");
    const line = b.node("text.append");
    const print = b.node("action.print");
    b.wire(loop, "entity", name, "target");
    b.wire(loop, "index", index, "value");
    b.wire(index, "text", line, "a");
    b.wire(name, "name", line, "b");
    b.wire(line, "result", print, "message");
    b.exec(loop, print, "body");
    b.exec(loop, b.node("action.print", { message: "done" }), "completed");
    const lines: string[] = [];
    const errors: string[] = [];
    new GraphInstance(parseGraph(serializeGraph(graph)), local, self,
      { log: (_graph, text) => lines.push(text), error: (_graph, error) => errors.push(error.message) }).beginPlay();
    return { lines, errors };
  }

  it("shouldRunTheBodyForEachEntityInOrderThenCompleted", () => {
    expect(run([entity("Crate"), entity("Ball")])).toEqual({ lines: ["0Crate", "1Ball", "done"], errors: [] });
  });

  it("shouldStopWithAnErrorOnAMissingListOrSomethingThatIsNotAnEntity", () => {
    expect(run(undefined).errors[0]).toContain("needs a list");
    expect(run([entity("Crate"), 3]).errors[0]).toContain("not an entity");
    expect(run([entity("Crate"), 3]).lines).toEqual([]);
  });
});
