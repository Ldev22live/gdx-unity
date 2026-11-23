import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { Graph, parseGraph, serializeGraph, type Vec3 } from "../src/visualscript/graph";
import { GraphInstance, type GraphHost, type GraphRuntimeError } from "../src/visualscript/interpreter";
import { ACTION_SUFFIX, implementationOf, JAVA_METHOD_TABLE } from "../src/visualscript/javaMethods";
import { connectionProblem, DESKTOP_ONLY_NODE_TYPES, validateGraph } from "../src/visualscript/validator";
import { createDefaultRegistry, type GraphEntity } from "../src/visualscript/nodes";
import { Builder, GRAPHS_DIR } from "./graphSupport";

const REGISTRY = createDefaultRegistry();
const FRAME = 1 / 60;
const JAVA_IDS = join(
  import.meta.dirname, "..", "..", "engine-visualscript", "src", "main", "java", "io", "github", "ldev22live", "gdxunity", "engine", "visualscript", "WebMethodNodes.java",
);

function close(actual: unknown, expected: unknown, what: string): void {
  if (typeof expected === "number") {
    expect(actual, what).toBeCloseTo(expected, Math.abs(expected) > 100 ? 2 : 3);
  } else if (typeof expected === "object" && expected !== null) {
    const a = actual as Vec3;
    const e = expected as Vec3;
    expect(a.x, `${what} x`).toBeCloseTo(e.x, 3);
    expect(a.y, `${what} y`).toBeCloseTo(e.y, 3);
    expect(a.z, `${what} z`).toBeCloseTo(e.z, 3);
  } else {
    expect(actual, what).toBe(expected);
  }
}

describe("the Java method nodes of the web table", () => {
  it("shouldListTheSameNodesAsTheDesktopsWebMethodNodesClass", () => {
    const java = readFileSync(JAVA_IDS, "utf8");
    const mathIds = [...java.matchAll(/MATH \+ "([^"]+)"/g)].map((m) => `java:com.badlogic.gdx.math.MathUtils#${m[1]}`);
    const vectorIds = [...java.matchAll(/VECTOR \+ "([^"]+)"/g)].map((m) => `java:com.badlogic.gdx.math.Vector3#${m[1]}`);
    expect([...mathIds, ...vectorIds].sort()).toEqual(JAVA_METHOD_TABLE.map((node) => node.id).sort());
  });

  it("shouldHaveAnImplementationForEveryNodeAndNoneWithoutANode", () => {
    for (const node of JAVA_METHOD_TABLE) expect(implementationOf(node.id), node.id).toBeDefined();
    expect(JAVA_METHOD_TABLE.length).toBeGreaterThan(40);
  });

  it("shouldRegisterEveryNodeWithTheDesktopsPinsAndAnActionTwinForThePureOnes", () => {
    for (const node of JAVA_METHOD_TABLE) {
      const type = REGISTRY.require(node.id);
      expect(type.kind, node.id).toBe(node.kind);
      const data = type.pins({ id: "n", type: node.id, x: 0, y: 0, literals: {}, properties: {} }, new Graph("g")).filter((pin) => !pin.exec);
      expect(data.map((pin) => `${pin.id}:${pin.input ? "in" : "out"}:${pin.type}`), node.id).toEqual(
        node.pins.filter((pin) => !pin.exec).map((pin) => `${pin.id}:${pin.input ? "in" : "out"}:${pin.type}`),
      );
      if (node.kind === "pure") {
        const twin = REGISTRY.require(node.id + ACTION_SUFFIX);
        expect(twin.kind).toBe("action");
      } else {
        expect(REGISTRY.get(node.id + ACTION_SUFFIX), `${node.id} is an Action already`).toBeUndefined();
      }
    }
  });

  it("shouldGiveWhatRealJavaCallsGaveForEveryRecordedCase", () => {
    let checked = 0;
    for (const node of JAVA_METHOD_TABLE) {
      const implementation = implementationOf(node.id)!;
      for (const [index, testCase] of node.cases.entries()) {
        close(implementation(testCase.in), testCase.out, `${node.id} case ${index}`);
        checked++;
      }
    }
    expect(checked).toBeGreaterThan(100);
  });

  it("shouldKeepTheRandomNodesInTheirRanges", () => {
    const random = (id: string, inputs: Record<string, unknown>): number | boolean =>
      implementationOf(`java:com.badlogic.gdx.math.MathUtils#${id}`)!(inputs) as number | boolean;
    for (let i = 0; i < 200; i++) {
      const unit = random("random()", {}) as number;
      expect(unit >= 0 && unit < 1).toBe(true);
      const between = random("random(float,float)", { p0: 5, p1: 6 }) as number;
      expect(between >= 5 && between <= 6).toBe(true);
      const whole = random("random(int,int)", { p0: 2, p1: 4 }) as number;
      expect([2, 3, 4]).toContain(whole);
    }
    const flips = new Set(Array.from({ length: 100 }, () => random("randomBoolean()", {})));
    expect(flips.size).toBe(2);
  });

  it("shouldRunAPureNodeAndItsActionTwinInAGraphLikeTheDesktopDoes", () => {
    const run = (type: string, wireExec: boolean): number[] => {
      const graph = new Graph("Clamp");
      const b = new Builder(graph);
      const begin = b.node("event.beginPlay");
      const clamp = b.node(type, { p0: 42, p1: 0, p2: 10 });
      const make = b.node("vector.make");
      const move = b.node("action.setPosition");
      b.wire(clamp, "result", make, "x");
      b.wire(make, "vector", move, "position");
      if (wireExec) {
        b.exec(begin, clamp);
        b.exec(clamp, move);
      } else {
        b.exec(begin, move);
      }
      expect(validateGraph(graph, REGISTRY)).toEqual([]);
      const entity = new MovableEntity();
      new GraphInstance(graph, REGISTRY, entity, new QuietHost()).beginPlay();
      return [entity.position.x];
    };

    expect(run("java:com.badlogic.gdx.math.MathUtils#clamp(float,float,float)", false)).toEqual([10]);
    expect(run("java:com.badlogic.gdx.math.MathUtils#clamp(float,float,float)" + ACTION_SUFFIX, true)).toEqual([10]);
  });
});

class MovableEntity implements GraphEntity {
  position = { x: 0, y: 0, z: 0 };
  readonly name = "Mover";
  getWorldPosition() {
    return { ...this.position };
  }
  setWorldPosition(position: { x: number; y: number; z: number }): void {
    this.position = { ...position };
  }
  rotate(): void {}
  destroy(): void {}
}

class QuietHost implements GraphHost {
  readonly errors: GraphRuntimeError[] = [];
  log(): void {}
  error(_instance: GraphInstance, error: GraphRuntimeError): void {
    this.errors.push(error);
    throw error;
  }
}

describe("whole numbers on the web", () => {
  it("shouldWireAnIntegerIntoAFloatPinButNotAFloatIntoAnIntegerPin", () => {
    const floor = REGISTRY.require("java:com.badlogic.gdx.math.MathUtils#floor(float)");
    const clampInt = REGISTRY.require("java:com.badlogic.gdx.math.MathUtils#clamp(int,int,int)");
    const result = floor.pins({ id: "a", type: floor.id, x: 0, y: 0, literals: {}, properties: {} }, new Graph("g")).find((pin) => pin.id === "result")!;
    const intIn = clampInt.pins({ id: "b", type: clampInt.id, x: 0, y: 0, literals: {}, properties: {} }, new Graph("g")).find((pin) => pin.id === "p0")!;
    const floatIn = REGISTRY.require("java:com.badlogic.gdx.math.MathUtils#lerp(float,float,float)")
      .pins({ id: "c", type: "x", x: 0, y: 0, literals: {}, properties: {} }, new Graph("g")).find((pin) => pin.id === "p0")!;
    const floatOut = REGISTRY.require("java:com.badlogic.gdx.math.MathUtils#sin(float)")
      .pins({ id: "d", type: "x", x: 0, y: 0, literals: {}, properties: {} }, new Graph("g")).find((pin) => pin.id === "result")!;

    expect(connectionProblem(result, floatIn)).toBeNull();
    expect(connectionProblem(result, intIn)).toBeNull();
    expect(connectionProblem(floatOut, intIn)).toBe("Cannot connect FLOAT to INTEGER");
  });

  it("shouldReadAndWriteIntegerLiteralsAsTheyAreInAFile", () => {
    const text = JSON.stringify({
      schemaVersion: 1, kind: "graph", name: "Ints", variables: [{ name: "count", type: "INTEGER", value: 3 }],
      nodes: [{ id: "n1", type: "java:com.badlogic.gdx.math.MathUtils#clamp(int,int,int)", x: 0, y: 0, literals: [{ pin: "p0", type: "INTEGER", value: 7 }, { pin: "p1", type: "INTEGER", value: 0 }], properties: [] }],
      links: [],
    });

    const graph = parseGraph(text);
    const again = JSON.parse(serializeGraph(graph));

    expect(graph.variables[0]).toEqual({ name: "count", type: "INTEGER", value: 3 });
    expect(again.nodes[0].literals).toEqual([{ pin: "p0", type: "INTEGER", value: 7 }, { pin: "p1", type: "INTEGER", value: 0 }]);
    expect(() => parseGraph(text.replace('"value":7', '"value":7.5'))).toThrow(/is not a INTEGER/);
  });
});

describe("the example graphs on the web", () => {
  const examples = JSON.parse(readFileSync(join(GRAPHS_DIR, "samples", "examples.json"), "utf8")).examples as { graph: string }[];

  it("shouldValidateEveryExampleThatDoesNotNeedThe3DPhysicsTheWebCannotRunYet", () => {
    let valid = 0;
    for (const example of examples) {
      const graph = parseGraph(readFileSync(join(GRAPHS_DIR, example.graph), "utf8"));
      const problems = validateGraph(graph, REGISTRY).map((problem) => problem.message);
      const needsPhysics = graph.nodes.some((node) => DESKTOP_ONLY_NODE_TYPES.includes(node.type));
      if (needsPhysics) {
        expect(problems.length, example.graph).toBeGreaterThan(0);
      } else {
        expect(problems, example.graph).toEqual([]);
        valid++;
      }
    }
    expect(valid, "the examples that run on the web").toBeGreaterThanOrEqual(12);
  });

  it("shouldFlyTheShooterShipOnTheWebWithTheKeysAndMathUtilsClamp", () => {
    const graph = parseGraph(readFileSync(join(GRAPHS_DIR, "samples", "ShooterShip.graph"), "utf8"));
    const ship = new MovableEntity();
    ship.position = { x: 300, y: 100, z: 0 };
    const keys = new Set<string>(["KeyD"]);
    const host: GraphHost = { log() {}, error(_i, e) { throw e; }, isKeyDown: (codes) => codes.some((code) => keys.has(code)) };
    const instance = new GraphInstance(graph, REGISTRY, ship, host);
    instance.beginPlay();

    for (let i = 0; i < 30; i++) instance.tick(FRAME);
    expect(ship.position.x, "half a second at 340 a second").toBeCloseTo(300 + 170, 1);
    for (let i = 0; i < 120; i++) instance.tick(FRAME);

    expect(ship.position.x, "held by MathUtils.clamp at the right edge").toBeCloseTo(610, 3);
  });
});
