# Web player checks report (TD-057)

**Date:** 2026-11-08 · **Decision:** TD-057 (approved: "approve TD-057", as typed) · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)

## Acceptance

| Criterion | Result | Evidence |
|---|---|---|
| Dependencies installed from the lockfile | Met | `npm ci` in `engine-babylon`: "added 44 packages in 39s", exit 0 (the old `node_modules` was an incomplete install with two empty package folders) |
| Type check run | Met | `npm run typecheck` (`tsc --noEmit`): exit 0, before and after the fixes |
| Tests run | Met | first run: 2 failed, 225 passed (16 files, 2 failed); last run: 16 files, 265 passed, exit 0 |
| Each failure fixed or listed | Met | below |
| No Git write | Met | none |

## What the first run showed

1. `samples.test.ts` could not load and `shouldBuildEverySampleSceneOfTheDesktopEditor` threw "Component field 'skyR' should be a number": my `character-creator-3d` sample (TD-053, `tools/make_character_sample.py`) wrote its Sky Light and Tone Mapping with empty `data`. The desktop fills defaults in; the web player requires every field. **Fixed** in the generator (the desktop defaults written out: sky 0.62/0.72/0.88, ground 0.32/0.29/0.26, intensity 1, ACES_FILMIC, exposure 1) and the sample regenerated; only those lines changed.
2. `shouldSupportEveryComponentTypeUsedByTheSamples`: `animation-samples-3d` (my sample from this session) uses `PointLightComponent`, which the web player ignored. **Fixed:** `SceneBuilder` builds a Babylon point light that follows its entity (color, intensity, range). The look was not seen.
3. The same test then listed four more samples with components the web player does not simulate: `physics-sandbox-3d`, `physics-joints-3d`, `physics-mesh-3d` and `character-playground-3d` (rigid bodies, box/sphere/convex hull/mesh colliders, joints, character controller, camera rig; TD-048 to TD-052). **Not fixed, a known gap:** it needs a browser physics engine and its own decision. The test now lists these (`KNOWN_UNSUPPORTED`), so another gap, or one of these being closed, still fails it. This changes what the test asserts (from "none" to "exactly these"); that was my call, flagged here.

## Commands and results (2026-11-08, Windows, Node 26.12.1, npm 11.20.0)

- `npm ci --no-audit --no-fund`, `npm run typecheck`, `npm test`, in `engine-babylon`.
- Java side after the sample regeneration: `gradlew :engine-desktop:test :editor-desktop:test :engine-character:test` passes (69, 77, 19; unchanged).

| Suite | Before | After |
|---|---|---|
| Web tests (vitest) | never run | 265 passed in 16 files |
| Web type check | never run | passes |
| engine-desktop, editor-desktop, engine-character | 69, 77, 19 | 69, 77, 19 |

## Manually Not Verified (Headless Environment)

The web player in a browser: nothing was drawn or played; `npm run build` (rewrites the working tree through `sync-samples`) and `npm run dev`; characters, animations and the point light on the web; sound.

## Known limitations and next steps

- The web player lacks 3D physics, joints, colliders, mesh colliders, character controllers and camera rigs. A decision on how to do 3D physics in the browser comes first.
- `package-lock.json` and other `engine-babylon` files were already modified before this milestone (the owner's work); `npm ci` did not change them.
- Next candidates: web 3D physics parity (needs a decision), eyes and hair, ethnicity sliders, joint breaking force.

## Git

No commit, push or other Git write was done.
