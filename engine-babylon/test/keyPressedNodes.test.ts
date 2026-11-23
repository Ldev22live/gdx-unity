import { describe, expect, it } from "vitest";
import { generateTypeScript } from "../src/visualscript/codegen";
import { generateJvm } from "../src/visualscript/codegenJvm";
import { Graph } from "../src/visualscript/graph";
import { GraphInstance } from "../src/visualscript/interpreter";
import { createDefaultRegistry, NODE, type GraphEntity } from "../src/visualscript/nodes";
import { Builder } from "./graphSupport";

const REGISTRY = createDefaultRegistry();
const FRAMES = 4;

class FakeEntity implements GraphEntity {
  readonly name = "Self";
  readonly cues: string[] = [];
  getWorldPosition() {
    return { x: 0, y: 0, z: 0 };
  }
  setWorldPosition(): void {}
  rotate(): void {}
  destroy(): void {}
  playCue(cue: string): void {
    this.cues.push(cue);
  }
}

/** Tick -> Branch(Key Pressed SPACE): True -> Print "jump pressed" -> Play Sound "jump". */
function example(): Graph {
  const graph = new Graph("pressed");
  const b = new Builder(graph);
  const tick = b.node(NODE.tick);
  const key = b.node(NODE.keyPressed, {}, { key: "SPACE" });
  const branch = b.node(NODE.branch);
  const print = b.node(NODE.print, { message: "jump pressed" });
  const sound = b.node(NODE.playCue, {}, { cue: "jump" });
  b.wire(key, "pressed", branch, "condition");
  b.exec(tick, branch);
  b.exec(branch, print, "true");
  b.exec(print, sound);
  return graph;
}

describe("Key Pressed and Play Sound on the web (TD-072)", () => {
  it("reacts only on the frame the key goes down and plays the cue then", () => {
    const entity = new FakeEntity();
    const lines: string[] = [];
    const errors: string[] = [];
    let frame = 0;
    const instance = new GraphInstance(example(), REGISTRY, entity, {
      log: (_i, line) => lines.push(line),
      error: (_i, error) => errors.push(error.message),
      isKeyDown: (codes) => codes.includes("Space") && (frame === 1 || frame === 2),
      isKeyJustPressed: (codes) => codes.includes("Space") && frame === 1,
    });

    for (frame = 0; frame < FRAMES; frame++) instance.tick(1 / 60);

    expect(errors).toEqual([]);
    expect(lines).toEqual(["jump pressed"]);
    expect(entity.cues).toEqual(["jump"]);
  });

  it("reads no key without a host that reports presses, and names an unknown key or a missing cue", () => {
    const quiet = new FakeEntity();
    const errors: string[] = [];
    new GraphInstance(example(), REGISTRY, quiet, { log: () => {}, error: (_i, error) => errors.push(error.message) }).tick(1 / 60);
    expect(errors).toEqual([]);
    expect(quiet.cues).toEqual([]);

    const bad = example();
    bad.requireNode("n2").properties["key"] = "NOT_A_KEY";
    const badErrors: string[] = [];
    new GraphInstance(bad, REGISTRY, quiet, { log: () => {}, error: (_i, error) => badErrors.push(error.message) }).tick(1 / 60);
    expect(badErrors).toHaveLength(1);
    expect(badErrors[0]).toContain("NOT_A_KEY");

    const silent = example();
    silent.requireNode("n5").properties["cue"] = "";
    expect(() => new GraphInstance(silent, REGISTRY, quiet, { log: () => {}, error: () => {} })).toThrow("Play Sound needs a cue");
  });

  it("generates the press check and the cue in TypeScript, Java and Kotlin", () => {
    const graph = example();

    expect(generateTypeScript(graph, REGISTRY)).toContain('this.input.isJustPressed("Space")');
    expect(generateTypeScript(graph, REGISTRY)).toContain('this.scene.playCue("jump")');
    const java = generateJvm(graph, REGISTRY, "java");
    expect(java).toContain("keyPressed(Input.Keys.SPACE)");
    expect(java).toContain("Gdx.input.isKeyJustPressed(key)");
    expect(java).toContain('AudioSourceComponent.play(getScene(), "jump")');
    const kotlin = generateJvm(graph, REGISTRY, "kotlin");
    expect(kotlin).toContain("keys.any { Gdx.input.isKeyJustPressed(it) }");
    expect(kotlin).toContain("import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent");
  });
});
