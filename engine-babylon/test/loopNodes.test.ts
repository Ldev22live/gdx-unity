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
