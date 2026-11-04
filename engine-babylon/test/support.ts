import { NullEngine } from "@babylonjs/core";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSceneFile, type SceneFile } from "../src/scene/types";

/** The sample scenes of the desktop editor, read straight from the repository so tests cover the real files. */
export const SAMPLES_DIR = join(import.meta.dirname, "..", "..", "assets", "samples");

export function loadSample(file: string): SceneFile {
  return parseSceneFile(readFileSync(join(SAMPLES_DIR, file), "utf8"));
}

/** A Babylon engine with no canvas or GPU, enough to build scenes and read their transforms. */
export function createHeadlessEngine(): NullEngine {
  return new NullEngine();
}
