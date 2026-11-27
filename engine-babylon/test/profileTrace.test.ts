import { readFileSync } from "node:fs";
import { join } from "node:path";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { Input } from "../src/runtime/Input";
import { buildScene } from "../src/runtime/SceneBuilder";
import { JoltPhysicsBackend3D, loadJolt, type JoltModule } from "../src/runtime/physics3d/JoltBackend";
import { MeshColliderGeometry } from "../src/runtime/physics3d/ColliderGeometry";
import { Physics3D } from "../src/runtime/physics3d/Physics3D";
import { players, resolveProfile, startProfile, validateProfile } from "../src/runtime/PlayProfiles";
import { parseSceneFile } from "../src/scene/types";
import { CONTRACT_DIR } from "./projectFiles";
import { createHeadlessEngine } from "./support";

const DIR = join(CONTRACT_DIR, "profile-trace");
/** The trace's key names as KeyboardEvent codes (the desktop's InputMapping.defaults()). */
const CODES: Record<string, string> = { W: "KeyW", A: "KeyA", S: "KeyS", D: "KeyD", SPACE: "Space", Q: "KeyQ", E: "KeyE" };

interface Trace {
  frameSeconds: number;
  toleranceUnits: number;
  runs: { scene: string; phases: { keys: string[]; frames: number; record?: string }[] }[];
}

let jolt: JoltModule;
const made: Physics3D[] = [];

beforeAll(async () => {
  jolt = await loadJolt();
});

afterEach(() => {
  made.splice(0).forEach((physics) => physics.dispose());
});

describe("the web's genre profiles (roadmap A14, TD-110)", () => {
  const trace = JSON.parse(readFileSync(join(DIR, "trace.json"), "utf8")) as Trace;
  const expected = JSON.parse(readFileSync(join(DIR, "expected.json"), "utf8")) as Record<string, Record<string, number[]>>;

  for (const run of trace.runs) {
    it(`moves the player of ${run.scene} where the desktop does, within ${trace.toleranceUnits} units`, () => {
      const file = parseSceneFile(readFileSync(join(DIR, run.scene), "utf8"));
      const resolution = resolveProfile(file);
      expect(resolution.outcome).toBe("PROFILE");
      if (resolution.outcome !== "PROFILE") return;
      const built = buildScene(createHeadlessEngine(), file);
      const camera = built.gameCamera!.entity;
      expect(validateProfile(built.game, camera)).toEqual([]);
      const physics = new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), new MeshColliderGeometry());
      made.push(physics);
      const input = new Input();
      const started = startProfile(resolution, built.game, camera, physics, input);
      expect(started.message).toMatch(/^Playing as/);
      const rig = started.rig!;
      const player = players(built.game)[0]!;
      const frame = () => {
        physics.update(trace.frameSeconds);
        for (const part of started.parts) part.update(trace.frameSeconds);
        rig.update(trace.frameSeconds, physics);
      };

      for (const phase of run.phases) {
        for (const code of Object.values(CODES)) input.release(code);
        for (const key of phase.keys) input.press(CODES[key]!);
        for (let i = 0; i < phase.frames; i++) frame();
        if (phase.record) {
          const at = player.getWorldPosition();
          const want = expected[run.scene]![phase.record]!;
          const label = `${run.scene} ${phase.record}: web (${at.x.toFixed(1)}, ${at.y.toFixed(1)}, ${at.z.toFixed(1)}) vs desktop (${want.join(", ")})`;
          expect(Math.abs(at.x - want[0]!), label).toBeLessThanOrEqual(trace.toleranceUnits);
          expect(Math.abs(at.y - want[1]!), label).toBeLessThanOrEqual(trace.toleranceUnits);
          expect(Math.abs(at.z - want[2]!), label).toBeLessThanOrEqual(trace.toleranceUnits);
        }
      }
      expect(physics.getWarnings()).toEqual([]);
    });
  }

  it("plays the desktop's other pairs without a profile and refuses pairs nobody plays, in the desktop's words", () => {
    const scene = (genre: string, mode: string | null) =>
      ({ schemaVersion: 1, name: "S", active: true, entities: [], gameGenre: genre, ...(mode === null ? {} : { gameMode: mode }) }) as never;
    expect(resolveProfile(scene("action-rpg", "2.5d"))).toMatchObject({ outcome: "DESKTOP_ONLY" });
    expect(resolveProfile(scene("first-person-shooter", "3d"))).toMatchObject({ outcome: "DESKTOP_ONLY" });
    expect(resolveProfile(scene("racing", "3d"))).toEqual({ outcome: "REFUSED", message: "Racing + 3D cannot be played yet." });
    expect(resolveProfile(scene("action-rpg", null))).toMatchObject({ outcome: "REFUSED", message: expect.stringMatching(/no saved Game Mode/) });
    expect(resolveProfile(scene("zombies", "3d"))).toMatchObject({ outcome: "REFUSED", message: expect.stringMatching(/'zombies' is not known/) });
    expect(resolveProfile({ schemaVersion: 1, name: "S", active: true, entities: [] })).toEqual({ outcome: "NO_GENRE" });
  });
});
