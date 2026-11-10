import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { isKnownSound } from "../src/audio/sfxr";
import { loadSample, SAMPLES_DIR } from "./support";

/**
 * A script triggers a sound by cue name, and a cue nobody answers is silent, so a typo would never show. These tests
 * read the scripts' sources and the scene files and check that they agree, in the web scripts and against the desktop
 * (Java) scripts they are ports of.
 */
const WEB_SCRIPTS_DIR = join(import.meta.dirname, "..", "src", "scripts");
const JAVA_SCRIPTS_DIR = join(import.meta.dirname, "..", "..", "assets", "scripts", "samples");

/** Cues each class plays, from `this.scene.playCue("x")` in a web script file. */
function webCuesByClass(): Map<string, Set<string>> {
  const result = new Map<string, Set<string>>();
  for (const file of readdirSync(WEB_SCRIPTS_DIR).filter((name) => name.endsWith(".ts"))) {
    const text = readFileSync(join(WEB_SCRIPTS_DIR, file), "utf8");
    // Everything between one `export class` and the next belongs to the first
    const parts = text.split(/^export class (\w+)/m);
    for (let i = 1; i < parts.length; i += 2) {
      const cues = new Set([...parts[i + 1]!.matchAll(/playCue\("(\w+)"\)/g)].map((match) => match[1]!));
      result.set(parts[i]!, cues);
    }
  }
  return result;
}

/** Cues each class plays, from `AudioSourceComponent.play(getScene(), "x")` in a Java script file. */
function javaCuesByClass(): Map<string, Set<string>> {
  const result = new Map<string, Set<string>>();
  const walk = (dir: string): void => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        walk(join(dir, entry.name));
      } else if (entry.name.endsWith(".java")) {
        const calls = readFileSync(join(dir, entry.name), "utf8").match(/AudioSourceComponent\.play\([^;]*\);/g) ?? [];
        result.set(entry.name.replace(".java", ""), new Set(calls.flatMap((call) => [...call.matchAll(/"(\w+)"/g)].map((m) => m[1]!))));
      }
    }
  };
  walk(JAVA_SCRIPTS_DIR);
  return result;
}

/** Simple class names of the scripts a scene file binds. */
function boundClasses(file: string): string[] {
  const names: string[] = [];
  for (const entity of loadSample(file).entities) {
    for (const component of entity.components) {
      if (component.type === "EntityScriptBindingComponent") names.push(String(component.data["scriptClassName"]).split(".").pop()!);
    }
  }
  return names;
}

function sceneCues(file: string): string[] {
  const cues: string[] = [];
  for (const entity of loadSample(file).entities) {
    for (const component of entity.components) {
      if (component.type === "AudioSource") cues.push(String(component.data["cue"]));
    }
  }
  return cues;
}

const sampleFiles = readdirSync(SAMPLES_DIR).filter((name) => name.endsWith(".json"));

describe("sample sound effects", () => {
  const web = webCuesByClass();
  const java = javaCuesByClass();

  it("shouldPlayTheSameCuesInTheWebScriptsAsInTheDesktopScripts", () => {
    let compared = 0;
    for (const [name, cues] of web) {
      if (cues.size === 0 && !java.has(name)) continue;
      expect([...cues].sort(), `${name}: web vs desktop`).toEqual([...(java.get(name) ?? [])].sort());
      compared++;
    }
    expect(compared).toBeGreaterThan(5);
  });

  it("shouldAnswerExactlyTheCuesOfTheWebScriptsInEveryPlayableSample", () => {
    let checked = 0;
    for (const file of sampleFiles) {
      const classes = boundClasses(file);
      // Only samples whose every script has a web version play there; the others (fps, fighter, puzzle, racing) are desktop only
      if (classes.length === 0 || !classes.every((name) => web.has(name))) continue;
      const played = new Set(classes.flatMap((name) => [...web.get(name)!]));
      expect([...played].sort(), file).toEqual([...sceneCues(file)].sort());
      checked++;
    }
    expect(checked).toBeGreaterThan(5);
  });

  it("shouldNameARealSoundAndAnInRangeVolumeAndPitchInEverySample", () => {
    let sources = 0;
    for (const file of sampleFiles) {
      for (const entity of loadSample(file).entities) {
        for (const component of entity.components.filter((c) => c.type === "AudioSource")) {
          const { sound, volume, pitch } = component.data as { sound: string; volume: number; pitch: number };
          expect(isKnownSound(sound), `${file}: ${sound}`).toBe(true);
          expect(volume).toBeGreaterThanOrEqual(0);
          expect(volume).toBeLessThanOrEqual(1);
          expect(pitch).toBeGreaterThanOrEqual(0.25);
          expect(pitch).toBeLessThanOrEqual(4);
          sources++;
        }
      }
    }
    expect(sources).toBeGreaterThan(30);
  });
});
