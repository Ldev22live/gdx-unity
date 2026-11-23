# Validation pass, Phases 12-17

**Date:** 2026-11-07 · **HEAD at start and end:** `f9849c2` (nothing committed; the working tree holds the owner's and earlier phases' uncommitted changes, untouched)
**Asked:** "do the validation passes 12-17 then follow this with teleport/respawn call, camera rigs, joints, mesh colliders".

## What was checked

1. **Clean full test run** (`gradlew test --continue --rerun-tasks`, 4m 34s, exit 1). Only `:editor-audio:test` failed: the 2 known `BundledDemoSongTest` failures (Phase 20, not in scope here). Every other module passed.
2. **Matrix against source.** Every test class named in `docs/FEATURE_TEST_MATRIX.md` exists (script `audit.py` in the session scratchpad: 166 test classes found, none missing). Named main classes resolve, except a few names that are inner classes or external libraries (`CharacterVirtual`, `Dockerfile`, `Exit3D` fragments).
3. **Smoke runs on the real runtime** (Windows, window opened): 17 scene files with `engine-desktop:run --smoke-test=120` and the editor with `--smoke-test`. All exit 0, 0 runtime errors.

| Module | Tests | Skipped | Failures |
|---|---|---|---|
| engine-core | 32 | 0 | 0 |
| engine-scene | 109 | 0 | 0 |
| engine-render | 90 | 0 | 0 |
| engine-visualscript | 22 | 0 | 0 |
| engine-animation | 32 | 0 | 0 |
| engine-audio | 185 | 0 | 0 |
| engine-physics | 51 | 0 | 0 |
| engine-sculpt | 77 | 0 | 0 |
| engine-web | 9 | 0 | 0 |
| engine-desktop | 44 | 0 | 0 |
| editor-core | 354 | 1 | 0 |
| editor-visualscript | 28 | 0 | 0 |
| editor-animation | 17 | 0 | 0 |
| editor-audio | 55 | 0 | 2 (known, pre-existing) |
| editor-sculpt | 45 | 0 | 0 |
| editor-desktop | 68 | 0 | 0 |

Scenes smoke-run (120 frames each): hopper-3d, collector-3d, puzzle-3d, runner-3d, racing-3d, arena-fps-3d, physics-sandbox-3d, humanoid-animations-3d, skeleton-walk-3d, skeleton-wave-3d, skeleton-tentacle-3d, visual-scripting-3d, landscape-3d, physics-sandbox-2d (plus character-playground-3d earlier). Editor: 180 frames, 0 errors in the Output Log.

## Result per phase

| Phase | Implemented (tests) | Integrated (reachable) | Validated (seen in running app) |
|---|---|---|---|
| 12 3D foundation, shell, content | Yes | Yes (editor smoke test opens and renders) | **No**: needs a person |
| 13 asset pipeline, lighting | Yes | Yes | **No** |
| 14 prefabs | Yes | Yes | **No** |
| 15 3D physics (A-G) | Yes | Yes (game runtime and Play) | **No** |
| 16 visual scripting | Yes (Java/Kotlin generation matches the interpreter) | Yes | **No**; web side tests **not run** (below) |
| 17 animation, Timeline | Yes | Yes | **No** |

"Validated" is not claimed for any phase: a smoke test proves a scene loads, renders frames and runs scripts without errors, not how it looks or feels.

## Not run, and why

- **Web tests** (`engine-babylon`: `npm test`, `npm run typecheck`, covering the 15-F and 15-G changes to `visualscript.test.ts`): `engine-babylon/node_modules` is empty and the C: drive has about 3 GB free. Free space, run `npm ci`, then both commands.
- **Linux and macOS** behaviour of Jolt and everything else.

## Manually Not Verified (Headless Environment)

Looking at and playing every scene above; shadows, sky light and tone mapping (13-B); gizmos, Content Browser drag-and-drop, prefab overrides on screen; the orange character capsule, Details editors and graph palette; the Timeline and bone display (17).

## Findings

No regression found. No code was changed in this pass.

## Git

No commit, push or other Git write was done.
