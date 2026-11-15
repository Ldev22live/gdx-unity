import { NullEngine } from "@babylonjs/core";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { graphEntity } from "../src/runtime/GraphEntity";
import type { Input } from "../src/runtime/Input";
import type { BuiltScene } from "../src/runtime/SceneBuilder";
import { parseSceneFile, str, type SceneFile } from "../src/scene/types";
import { parseGraph } from "../src/visualscript/graph";
import { GraphInstance } from "../src/visualscript/interpreter";
import { createDefaultRegistry as createNodeRegistry } from "../src/visualscript/nodes";

/** The sample scenes of the desktop editor, read straight from the repository so tests cover the real files. */
export const SAMPLES_DIR = join(import.meta.dirname, "..", "..", "assets", "samples");

export function loadSample(file: string): SceneFile {
  return parseSceneFile(readFileSync(join(SAMPLES_DIR, file), "utf8"));
}

/** A Babylon engine with no canvas or GPU, enough to build scenes and read their transforms. */
export function createHeadlessEngine(): NullEngine {
  return new NullEngine();
}

/** Starts the graphs a scene binds (the sample players of TD-068, TD-071, TD-072), the way ScenePlayer.play does; tick them each frame. */
export function startGraphs(built: BuiltScene, input: Input): GraphInstance[] {
  const nodes = createNodeRegistry();
  const instances: GraphInstance[] = [];
  for (const entity of built.game.entities) {
    for (const component of entity.getComponents("GraphScriptComponent")) {
      const path = str(component.data, "graph");
      const graph = parseGraph(readFileSync(join(SAMPLES_DIR, "..", path), "utf8"));
      instances.push(new GraphInstance(graph, nodes, graphEntity(entity, built.game), {
        log: () => {},
        error: (_i, error) => { throw error; },
        isKeyDown: (codes) => input.isDown(...codes),
        isKeyJustPressed: (codes) => input.isJustPressed(...codes),
      }, () => built.game.contains(entity)));
    }
  }
  instances.forEach((instance) => instance.beginPlay());
  return instances;
}
