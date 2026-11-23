import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { generateTypeScript } from "../src/visualscript/codegen";
import { generateJvm } from "../src/visualscript/codegenJvm";
import { Graph } from "../src/visualscript/graph";
import { GraphInstance, type GraphHost, type GraphRuntimeError } from "../src/visualscript/interpreter";
import { parseKeyNames } from "../src/visualscript/keys";
import { createDefaultRegistry, NODE, type GraphEntity } from "../src/visualscript/nodes";
import { Builder } from "./graphSupport";

const REGISTRY = createDefaultRegistry();
const FRAME = 1 / 60;
const KEY_NAMES_JAVA = join(
  import.meta.dirname, "..", "..", "engine-visualscript", "src", "main", "java", "io", "github", "ldev22live", "gdxunity", "engine", "visualscript", "KeyNames.java",
);

class FakeEntity implements GraphEntity {
  position = { x: 0, y: 0, z: 0 };
  readonly name = "Ship";
  getWorldPosition() {
    return { ...this.position };
  }
  setWorldPosition(position: { x: number; y: number; z: number }): void {
    this.position = { ...position };
  }
  rotate(): void {}
  destroy(): void {}
}

class KeyboardHost implements GraphHost {
  down = new Set<string>();
  readonly errors: GraphRuntimeError[] = [];
  log(): void {}
  error(_instance: GraphInstance, error: GraphRuntimeError): void {
    this.errors.push(error);
  }
  isKeyDown(codes: string[]): boolean {
    return codes.some((code) => this.down.has(code));
  }
}

/** Tick, then Move By (axis * 100 * deltaTime, 0, 0): a ship that flies sideways along x. */
function shipGraph(negative = "A,LEFT", positive = "D,RIGHT"): Graph {
  const graph = new Graph("Ship");
  const b = new Builder(graph);
  const tick = b.node(NODE.tick);
  const axis = b.node(NODE.axis, {}, { negative, positive });
  const step = b.node(NODE.multiply, { b: 100 });
  const distance = b.node(NODE.multiply);
  const offset = b.node(NODE.makeVector);
  const move = b.node(NODE.moveBy);
  b.wire(tick, "deltaTime", step, "a");
  b.wire(axis, "value", distance, "a");
  b.wire(step, "result", distance, "b");
  b.wire(distance, "result", offset, "x");
  b.wire(offset, "vector", move, "offset");
  b.exec(tick, move);
  return graph;
}

describe("parseKeyNames", () => {
  it("shouldMapLibgdxNamesToBrowserKeyCodesInAnyCase", () => {
    expect(parseKeyNames("w, up,SPACE,shift_left,num_5,f12").map((key) => key.code)).toEqual(["KeyW", "ArrowUp", "Space", "ShiftLeft", "Digit5", "F12"]);
    expect(parseKeyNames("a,Left").map((key) => key.name)).toEqual(["A", "LEFT"]);
  });

  it("shouldNameTheKeyItDoesNotKnow", () => {
    expect(() => parseKeyNames("A,FIRE")).toThrow(/Unknown key 'FIRE'/);
    expect(() => parseKeyNames("DPAD_CENTER")).toThrow(/Unknown key/);
  });

  it("shouldAgreeWithTheDesktopsTableOfBrowserKeyCodes", () => {
    const java = readFileSync(KEY_NAMES_JAVA, "utf8");
    const entries = [...java.matchAll(/Map\.entry\("([A-Z_]+)", "(\w+)"\)/g)].map((m) => [m[1]!, m[2]!] as const);
    expect(entries.length).toBeGreaterThan(10);
    for (const [name, code] of entries) expect(parseKeyNames(name)[0]!.code, name).toBe(code);
  });
});

describe("Key Down and Input Axis nodes", () => {
  it("shouldFlyTheShipWithTheKeysThatAreHeld", () => {
    const host = new KeyboardHost();
    const ship = new FakeEntity();
    const instance = new GraphInstance(shipGraph(), REGISTRY, ship, host);
    instance.beginPlay();

    host.down = new Set(["KeyD"]);
    for (let i = 0; i < 60; i++) instance.tick(FRAME);
    expect(ship.position.x).toBeCloseTo(100, 3);

    host.down = new Set(["ArrowLeft"]);
    for (let i = 0; i < 120; i++) instance.tick(FRAME);
    expect(ship.position.x).toBeCloseTo(-100, 3);

    host.down = new Set(["KeyA", "KeyD"]);
    for (let i = 0; i < 60; i++) instance.tick(FRAME);
    expect(ship.position.x).toBeCloseTo(-100, 3);
    expect(host.errors).toEqual([]);
  });

  it("shouldReadNoKeyWhenTheHostHasNoKeyboard", () => {
    const ship = new FakeEntity();
    const plain: GraphHost = { log() {}, error() {} };
    const instance = new GraphInstance(shipGraph(), REGISTRY, ship, plain);
    instance.beginPlay();

    for (let i = 0; i < 60; i++) instance.tick(FRAME);

    expect(ship.position.x).toBe(0);
  });

  it("shouldReportAnUnknownKeyAsAnErrorThatNamesTheNode", () => {
    const host = new KeyboardHost();
    const instance = new GraphInstance(shipGraph("A", "NOT_A_KEY"), REGISTRY, new FakeEntity(), host);
    instance.beginPlay();

    instance.tick(FRAME);

    expect(host.errors).toHaveLength(1);
    expect(host.errors[0]!.message).toContain("NOT_A_KEY");
  });

  it("shouldGenerateTypeScriptThatAsksTheScriptsInput", () => {
    const code = generateTypeScript(shipGraph(), REGISTRY);

    expect(code).toContain('this.input.isDown("KeyD", "ArrowRight")');
    expect(code).toContain('this.input.isDown("KeyA", "ArrowLeft")');
  });

  it("shouldGenerateJavaAndKotlinThatAskLibgdxsInput", () => {
    const java = generateJvm(shipGraph(), REGISTRY, "java");
    const kotlin = generateJvm(shipGraph(), REGISTRY, "kotlin");

    expect(java).toContain("keyDown(Input.Keys.D, Input.Keys.RIGHT)");
    expect(java).toContain("private static boolean keyDown(int... keys)");
    expect(java).toContain("import com.badlogic.gdx.Input;");
    expect(kotlin).toContain("keyDown(Input.Keys.D, Input.Keys.RIGHT)");
    expect(kotlin).toContain("private fun keyDown(vararg keys: Int): Boolean");
  });
});
