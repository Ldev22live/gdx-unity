import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import cases from "./fixtures/component-cases.json";
import { Graph, parseGraph, serializeGraph, defaultValue } from "../src/visualscript/graph";
import { GraphInstance } from "../src/visualscript/interpreter";
import { createDefaultRegistry } from "../src/visualscript/nodes";
import { COMPONENT_API } from "../src/visualscript/components";
import { connectionProblem } from "../src/visualscript/validator";
import { graphEntity } from "../src/runtime/GraphEntity";
import { buildScene } from "../src/runtime/SceneBuilder";
import { Builder } from "./graphSupport";
import { createHeadlessEngine, loadSample } from "./support";
import { readSampleGraph } from "./graphSupport";
import { BreakoutPaddle } from "../src/scripts/breakout";
import { Input } from "../src/runtime/Input";

const registry = createDefaultRegistry();

describe("curated component nodes", () => {
  it("shouldMatchTheReferencePaddleAtDifferentWidthsAndFrameTimes", () => {
    for (const width of [90, 140]) {
      const engine = createHeadlessEngine();
      const file = loadSample("breakout-2d.json");
      const data = file.entities.find((entity) => entity.name === "Paddle")!;
      data.components.find((component) => component.type === "ShapeRendererComponent")!.data["width"] = width;
      expect(data.components.some((component) => component.type === "GraphScriptComponent" && component.data["graph"] === "graphs/samples/BreakoutPaddle.graph")).toBe(true);
      file.entities = [data];
      const graphScene = buildScene(engine, structuredClone(file));
      const referenceScene = buildScene(engine, structuredClone(file));
      const input = new Input();
      const script = new BreakoutPaddle();
      script.entity = referenceScene.game.entities[0]!;
      script.scene = referenceScene.game;
      script.input = input;
      script.onStart();
      const instance = new GraphInstance(readSampleGraph("samples/BreakoutPaddle"), registry,
        graphEntity(graphScene.game.entities[0]!, graphScene.game),
        { isKeyDown: (codes) => input.isDown(...codes), log: () => {}, error: (_graph, error) => { throw error; } });
      instance.beginPlay();
      for (let frame = 0; frame < 480; frame++) {
        const keys = frame < 160 ? ["ArrowLeft"] : frame < 320 ? ["KeyD"] : ["KeyA", "ArrowRight"];
        for (const key of ["ArrowLeft", "KeyD", "KeyA", "ArrowRight"]) input.release(key);
        for (const key of keys) input.press(key);
        const dt = frame % 2 === 0 ? 1 / 60 : 1 / 120;
        instance.tick(dt);
        script.onUpdate(dt);
        expect(graphScene.game.entities[0]!.getLocalPosition().x).toBeCloseTo(script.entity.getLocalPosition().x, 2);
      }
      graphScene.scene.dispose();
      referenceScene.scene.dispose();
      engine.dispose();
    }
  });
  it("shouldExposeExactlyTheDesktopComponentApi", () => {
    const java = readFileSync(join(import.meta.dirname, "../../engine-visualscript/src/main/java/io/github/ldev22live/gdxunity/engine/visualscript/ComponentNodes.java"), "utf8");
    const signatures = [...java.matchAll(/(RENDER|AUDIO) \+ "([^"]+#.*?)"/g)].map((match) =>
      `${match[1] === "RENDER" ? "io.github.ldev22live.gdxunity.engine.render.components." : "io.github.ldev22live.gdxunity.engine.scene.audio."}${match[2]}`);
    expect(COMPONENT_API.map(([owner, method]) => `${owner}#${method}`)).toEqual(signatures);
    expect(new Set(cases.map((value) => `${value.component}#${value.method}`)).size).toBe(COMPONENT_API.length);
  });

  it("shouldMatchTheRecordedJavaBehaviorThroughRealRuntimeComponentAdapters", () => {
    const engine = createHeadlessEngine();
    const file = loadSample("breakout-2d.json");
    const data = file.entities.find((entity) => entity.name === "Paddle")!;
    data.components = [
      { type: "ShapeRendererComponent", enabled: true, data: { width: 90, height: 14, color: { r: 1, g: 1, b: 1, a: 1 } } },
      { type: "TextComponent", enabled: true, data: { text: "Initial" } },
      { type: "CameraComponent", enabled: true, data: { projection: "PERSPECTIVE", fieldOfView: 60 } },
      { type: "SpriteAnimatorComponent", enabled: true, data: { assetPath: "sprites/hero.sprite" } },
      { type: "TilemapComponent", enabled: true, data: { cols: 2, rows: 2, tileWidth: 32, tileHeight: 32,
        solid: [1], layers: [{ name: "Ground", visible: true, cells: [0, 0, 1, 0] }] } },
      { type: "AudioSourceComponent", enabled: true, data: { sound: "Coin", volume: 1 } },
    ];
    // Tile queries are in world coordinates; align with the Java fixture's origin.
    data.transform.position = { x: 0, y: 0, z: 0 };
    file.entities = [data];
    const built = buildScene(engine, file);
    const entity = built.game.entities[0]!;
    const adapter = graphEntity(entity, built.game);
    const played: number[] = [];
    built.game.bindSound({ play: (_sound, volume) => played.push(volume) });
    const sprite = entity.sprite!;
    const originalPlay = sprite.play.bind(sprite);
    let requestedTag: string | null = null;
    sprite.play = (tag) => { requestedTag = tag; originalPlay(tag); };
    for (const test of cases) {
      const [owner, method, resultType] = COMPONENT_API.find(([owner, method]) => owner.endsWith(`.${test.component}`) && method === test.method)!;
      const graph = new Graph("Component calls");
      const b = new Builder(graph);
      const begin = b.node("event.beginPlay");
      const get = b.node(`component.get:${test.component}`);
      const call = b.node(`java:${owner}#${method}`, Object.fromEntries(test.args.map((arg, i) => [`p${i}`, arg])));
      b.wire(get, "component", call, "target");
      if (resultType) {
        graph.variables.push({ name: "result", type: resultType, value: defaultValue(resultType) });
        const set = b.node("variable.set", {}, { variable: "result" });
        b.exec(begin, set);
        b.wire(call, "result", set, "value");
      } else {
        b.exec(begin, call);
        b.exec(call, b.node("action.print", { message: "done" }));
      }
      const errors: string[] = [];
      const lines: string[] = [];
      const instance = new GraphInstance(parseGraph(serializeGraph(graph)), registry, adapter,
        { error: (_graph, error) => errors.push(error.message), log: (_graph, line) => lines.push(line) });
      instance.beginPlay();
      if ("error" in test) {
        expect(errors.length, JSON.stringify(test)).toBe(1);
        expect(lines).toEqual([]);
      } else {
        expect(errors, JSON.stringify(test)).toEqual([]);
        if ("result" in test) expect(instance.getVariable("result"), JSON.stringify(test)).toBe(test.result);
        else expect(lines).toEqual(["done"]);
      }
    }
    expect(requestedTag).toBe("walk");
    expect(played).toEqual([0.25]);
    built.gameCamera!.sync(1);
    expect(built.gameCamera!.camera.fov).toBeCloseTo(75 * Math.PI / 180);
    built.scene.dispose();
    engine.dispose();
  });

  it("shouldRejectWiresBetweenDifferentComponentTypes", () => {
    const graph = new Graph("Types");
    const b = new Builder(graph);
    const shape = graph.requireNode(b.node("component.get:ShapeRendererComponent"));
    const text = graph.requireNode(b.node("java:io.github.ldev22live.gdxunity.engine.render.components.TextComponent#setText(java.lang.String)"));
    expect(connectionProblem(registry.pin(shape, graph, "component")!, registry.pin(text, graph, "target")!)).toContain("Cannot connect");
  });

  it("shouldReportAMissingComponentWithoutContinuingTheChain", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, loadSample("breakout-2d.json"));
    const paddle = built.game.entities.find((entity) => entity.name === "Paddle")!;
    const graph = new Graph("Missing text");
    const b = new Builder(graph);
    const lookup = b.node("component.get:TextComponent");
    const call = b.node("java:io.github.ldev22live.gdxunity.engine.render.components.TextComponent#setText(java.lang.String)", { p0: "hello" });
    b.wire(lookup, "component", call, "target");
    b.exec(b.node("event.beginPlay"), call);
    b.exec(call, b.node("action.print", { message: "wrong" }));
    const errors: string[] = [];
    const lines: string[] = [];
    new GraphInstance(graph, registry, graphEntity(paddle, built.game),
      { error: (_graph, error) => errors.push(error.message), log: (_graph, message) => lines.push(message) }).beginPlay();
    expect(errors[0]).toContain("has no TextComponent");
    expect(lines).toEqual([]);
    built.scene.dispose();
    engine.dispose();
  });
});
