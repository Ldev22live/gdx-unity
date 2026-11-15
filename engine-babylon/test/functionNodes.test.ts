import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { Graph, parseGraph, serializeGraph, makeParam, cloneGraph } from "../src/visualscript/graph";
import { GraphInstance } from "../src/visualscript/interpreter";
import { createDefaultRegistry, NODE, PROPERTY_FUNCTION, PROPERTY_NAME, type GraphEntity } from "../src/visualscript/nodes";
import { generateJvm } from "../src/visualscript/codegenJvm";
import { generateTypeScript } from "../src/visualscript/codegen";
import { validateGraph } from "../src/visualscript/validator";
import { Builder } from "./graphSupport";

const registry = createDefaultRegistry();
const self: GraphEntity = { name: "Self", getWorldPosition: () => ({ x: 0, y: 0, z: 0 }), setWorldPosition: () => {}, rotate: () => {}, destroy: () => {} };

/** The file the desktop test `GraphFunctionsTest` is checked against too: one function and one declared event. */
const PARITY_FILE = join(import.meta.dirname, "../../engine-visualscript/src/test/resources/functions-parity.graph");

function run(graph: Graph): { lines: string[]; errors: string[]; instance: GraphInstance } {
  const lines: string[] = [];
  const errors: string[] = [];
  const instance = new GraphInstance(graph, registry, self, { log: (_i, line) => lines.push(line), error: (_i, error) => errors.push(error.message) });
  instance.beginPlay();
  return { lines, errors, instance };
}

function problems(graph: Graph): string[] {
  return validateGraph(graph, registry).map((p) => p.message);
}

/** A function name(x: number) -> result: number whose Return hands back x times `factor`. */
function scaling(graph: Graph, name: string, factor: number) {
  const fn = graph.addFunction(name);
  fn.inputs.push(makeParam("x", "FLOAT"));
  fn.outputs.push(makeParam("result", "FLOAT"));
  const b = new Builder(fn.body);
  const entry = b.node(NODE.functionEntry);
  const multiply = b.node(NODE.multiply, { b: factor });
  const ret = b.node(NODE.functionReturn);
  b.wire(entry, "x", multiply, "a");
  b.wire(multiply, "result", ret, "result");
  b.exec(entry, ret);
  return fn;
}

describe("graph functions on the web (TD-070)", () => {
  it("runs the file the desktop runs and records the same lines", () => {
    const graph = parseGraph(readFileSync(PARITY_FILE, "utf8"));

    const result = run(graph);

    expect(result.errors).toEqual([]);
    expect(result.lines).toEqual(["42", "dmg 4", "fired"]);
  });

  it("calls a function with inputs and gets its outputs, and hands them to another call", () => {
    const graph = new Graph("Calls");
    scaling(graph, "double", 2);
    scaling(graph, "triple", 3);
    const b = new Builder(graph);
    const first = b.node(NODE.functionCall, { x: 5 }, { [PROPERTY_FUNCTION]: "double" });
    const second = b.node(NODE.functionCall, {}, { [PROPERTY_FUNCTION]: "triple" });
    b.wire(first, "result", second, "x");
    b.exec(b.node(NODE.beginPlay), first);
    b.exec(first, second);
    const text = b.node(NODE.numberToText);
    b.wire(second, "result", text, "value");
    const print = b.node(NODE.print);
    b.wire(text, "text", print, "message");
    b.exec(second, print);

    expect(run(graph).lines).toEqual(["30"]);
  });

  it("shares the variables of the graph with its functions", () => {
    const graph = new Graph("Shared");
    graph.variables.push({ name: "score", type: "FLOAT", value: 1 });
    const bump = graph.addFunction("bump");
    const body = new Builder(bump.body);
    const entry = body.node(NODE.functionEntry);
    const get = body.node(NODE.getVariable, {}, { variable: "score" });
    const add = body.node(NODE.add, { b: 10 });
    const set = body.node(NODE.setVariable, {}, { variable: "score" });
    body.wire(get, "value", add, "a");
    body.wire(add, "result", set, "value");
    body.exec(entry, set);
    const b = new Builder(graph);
    b.exec(b.node(NODE.beginPlay), b.node(NODE.functionCall, {}, { [PROPERTY_FUNCTION]: "bump" }));

    const result = run(graph);

    expect(result.errors).toEqual([]);
    expect(result.instance.getVariable("score")).toBe(11);
  });

  it("ends the function at the first Return and stops the caller's chain when the body fails", () => {
    const graph = new Graph("Early");
    const early = graph.addFunction("early");
    early.outputs.push(makeParam("result", "FLOAT"));
    const body = new Builder(early.body);
    const entry = body.node(NODE.functionEntry);
    const sequence = body.node(NODE.sequence);
    body.exec(entry, sequence);
    const ret = body.node(NODE.functionReturn, { result: 7 });
    body.exec(sequence, ret, "then0");
    body.exec(sequence, body.node(NODE.print, { message: "never" }), "then1");
    const broken = graph.addFunction("broken");
    const brokenBody = new Builder(broken.body);
    const brokenEntry = brokenBody.node(NODE.functionEntry);
    const repeat = brokenBody.node("flow.repeat", { count: 5000 });
    broken.body.requireNode(repeat).integerLiterals = new Set(["count"]);
    brokenBody.exec(brokenEntry, repeat);
    const b = new Builder(graph);
    const call = b.node(NODE.functionCall, {}, { [PROPERTY_FUNCTION]: "early" });
    const text = b.node(NODE.numberToText);
    b.wire(call, "result", text, "value");
    const print = b.node(NODE.print);
    b.wire(text, "text", print, "message");
    b.exec(b.node(NODE.beginPlay), call);
    b.exec(call, print);
    const fail = b.node(NODE.functionCall, {}, { [PROPERTY_FUNCTION]: "broken" });
    b.exec(print, fail);
    b.exec(fail, b.node(NODE.print, { message: "after the failure" }));

    const result = run(graph);

    expect(result.lines).toEqual(["7"]);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain("Repeat count");
  });

  it("fires a custom event with parameters before the Fire node continues, and stops an event that fires itself", () => {
    const graph = new Graph("Events");
    const hit = graph.addEvent("hit");
    hit.params.push(makeParam("label", "STRING"));
    const b = new Builder(graph);
    const receiver = b.node(NODE.custom, {}, { [PROPERTY_NAME]: "hit" });
    const print = b.node(NODE.print);
    b.wire(receiver, "label", print, "message");
    b.exec(receiver, print);
    const fire = b.node(NODE.fireEvent, { label: "boom" }, { [PROPERTY_NAME]: "hit" });
    b.exec(b.node(NODE.beginPlay), fire);
    b.exec(fire, b.node(NODE.print, { message: "fired" }));
    expect(run(graph).lines).toEqual(["boom", "fired"]);

    const loop = new Graph("Loop");
    loop.addEvent("loop");
    const l = new Builder(loop);
    const again = l.node(NODE.fireEvent, {}, { [PROPERTY_NAME]: "loop" });
    l.exec(l.node(NODE.custom, {}, { [PROPERTY_NAME]: "loop" }), again);
    l.exec(l.node(NODE.beginPlay), l.node(NODE.fireEvent, {}, { [PROPERTY_NAME]: "loop" }));
    const result = run(loop);
    expect(result.errors.length).toBeGreaterThan(0);
    expect(result.errors[0]).toContain("deep");
  });

  it("validates like the desktop: missing function, circle, entry count, events in a body, entry outside a function", () => {
    const missing = new Graph("Missing");
    new Builder(missing).node(NODE.functionCall, {}, { [PROPERTY_FUNCTION]: "gone" });
    expect(problems(missing).some((m) => m.includes("Function gone does not exist"))).toBe(true);

    const circle = new Graph("Circle");
    for (const [from, to] of [["a", "b"], ["b", "a"]] as const) {
      const body = new Builder(circle.addFunction(from).body);
      body.exec(body.node(NODE.functionEntry), body.node(NODE.functionCall, {}, { [PROPERTY_FUNCTION]: to }));
    }
    expect(problems(circle).some((m) => m.includes("circle") && m.includes("a -> b -> a"))).toBe(true);

    const odd = new Graph("Odd");
    const oddBody = new Builder(odd.addFunction("odd").body);
    oddBody.node(NODE.tick);
    oddBody.node(NODE.delay);
    const found = problems(odd);
    expect(found.some((m) => m.includes("exactly one Function Entry"))).toBe(true);
    expect(found.some((m) => m.includes("cannot hold Event Tick"))).toBe(true);
    expect(found.some((m) => m.includes("cannot hold a Delay"))).toBe(true);

    const outside = new Graph("Outside");
    const o = new Builder(outside);
    o.node(NODE.functionEntry);
    o.node(NODE.functionReturn);
    expect(problems(outside).filter((m) => m.includes("only be used inside a function"))).toHaveLength(2);

    const undeclared = new Graph("Undeclared");
    new Builder(undeclared).node(NODE.fireEvent, {}, { [PROPERTY_NAME]: "nope" });
    expect(problems(undeclared).some((m) => m.includes("not declared"))).toBe(true);
  });

  it("writes version 1 without functions and version 2 with them, and round trips", () => {
    const plain = new Graph("Plain");
    new Builder(plain).node(NODE.beginPlay);
    expect(JSON.parse(serializeGraph(plain)).schemaVersion).toBe(1);

    const graph = parseGraph(readFileSync(PARITY_FILE, "utf8"));
    const text = serializeGraph(graph);
    expect(JSON.parse(text).schemaVersion).toBe(2);
    expect(serializeGraph(cloneGraph(graph))).toBe(text);
    expect(() => parseGraph('{"schemaVersion":3,"kind":"graph","name":"x"}')).toThrow("Unsupported graph schemaVersion");
  });

  it("refuses to generate TypeScript, Java and Kotlin for graphs with functions", () => {
    const graph = parseGraph(readFileSync(PARITY_FILE, "utf8"));

    expect(() => generateTypeScript(graph, registry)).toThrow("TypeScript source generation does not support");
    expect(() => generateJvm(graph, registry, "java")).toThrow("Java source generation does not support");
    expect(() => generateJvm(graph, registry, "kotlin")).toThrow("Kotlin source generation does not support");
  });
});
