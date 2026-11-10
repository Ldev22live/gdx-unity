# Web 3D physics, step 1 report (TD-059)

**Date:** 2026-11-08 · **Decision:** TD-059 (approved: "approve TD-059", as typed; step 1 only) · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)

## Acceptance (step 1)

| Criterion | Result | Evidence |
|---|---|---|
| The web player simulates rigid bodies with box, sphere and capsule colliders on Jolt | Met | `test/physics3d.test.ts`: a sphere rests at its radius (2 cm of Jolt's usual settling), offsets, layer masks (layers 9 and 12), triggers, impulses and forces in the right units |
| Queries and events | Met | closest solid ray hit with normal, sensors and masked layers skipped, sphere overlap each once, one contact per body pair, an ended contact when a body is removed |
| `physics-sandbox-3d` runs with its script on the web | Met (headless browser, software rendering) | `docs/reports/img/td59-web-sandbox-playing.png` (balls landed in a row, crate pyramid standing, the script's "Aiming at: Floor" line) and `td59-web-sandbox-loaded.png` (before Play) |
| Real browser: loads, Play after the engine loads, Stop, Play again | Met | `tools/web-browser-check/check.mjs`: buttons follow the real state (after Stop: play enabled; after the second Play: playing), no console errors |
| Tests, type check and build | Met | below |
| Steps 2 and 3 | Not started | hull and mesh colliders, joints; character controller, camera rig, `WalkerPlayer` |

## What was built

New in `engine-babylon/src/runtime/physics3d/`: `types.ts`, `JoltBackend.ts` (the desktop's `JoltPhysicsBackend3D` for the web), `Physics3D.ts` (the desktop's `PhysicsSystem3D` for bodies, colliders, queries, events). Changed: `ScenePlayer` (makes the system on Play, waits for Jolt the first time, `onPlayingChanged`, contact delivery to scripts), `ScriptComponent` (`physics3d`, `raycast3D`, contact callbacks), `SceneBuilder` (four more supported component types), `main.ts` (follows the play state), `scripts/sandbox.ts` and `registry.ts` (the `SandboxLauncher` port), `package.json` (`jolt-physics`), `README.md`. `tools/web-browser-check/` holds the browser script.

Found and fixed on the way:
- The three Jolt layer tables belong to Jolt's interface: destroying them in `dispose` crashed ("table index is out of bounds"). Only the settings and the two broad phase layer objects are released by us.
- In the page, Play can start a moment after the click (the engine loads first), so the Stop button stayed disabled and a Stop pressed during the wait would have been followed by a late Play. Fixed with `onPlayingChanged` and a pending Play that Stop and a new scene cancel.
- First bundle put Jolt (3.2 MB, 953 KB gzipped) in the main chunk. It is a dynamic import now, so 2D scenes do not download it (main chunk 1.36 MB gzipped).
- Two wrong assumptions in my own tests: the last ball rests on top of the crates (no tight height bound), and `console.log` is hidden in this vitest setup (I wrote debug output to a file, then deleted that test).

## Commands and results (2026-11-08, Windows, Node 26.12.1)

- `npm install jolt-physics@^1.1.1` (npm registry), `npx tsc --noEmit` (exit 0), `npx vitest run` (17 files, 282 tests passed), `npm run build` (exit 0; it writes only git-ignored folders: `dist/`, `public/*`; one warning that Jolt's script mentions `node:module`, which only its Node branch uses).
- Browser: `vite preview` plus a headless Edge (software rendering) started by me with its own scratch profile, driven by `check.mjs`; only my processes were stopped afterwards.
- No Java code or sample file changed in this milestone, so the Java suites were not re-run; the last full run is in the TD-056 report.

| Suite | Before | After |
|---|---|---|
| Web tests (vitest) | 16 files, 265 | 17 files, 282 |
| Web type check, `npm run build` | passes / not run | passes / passes |

## Manually Not Verified (Headless Environment)

Everything the browser run could not show: a real graphics card and frame rate (the run rendered a few frames per second, so the simulation advanced in 0.1 s slices and the balls took about 25 s to land); the keys (Space kick, F push, E pop, T stop) in the browser (the script is tested with simulated input); browsers other than Edge; the desktop and web simulations side by side (both are Jolt, but the versions were not compared: jolt-jni 6.2.0 against the `jolt-physics` 1.1.1 build); the editor page's Play with 3D scenes.

## Known limitations and next steps

- Scenes with hull or mesh colliders, joints, characters or camera rigs load, but those parts do nothing and the rest of their bodies still simulate (a joint sample falls apart, a mesh terrain is not solid). The player lists the unsupported component types.
- Step 2: convex hull and mesh colliders, joints. Step 3: character controller, camera rig, `WalkerPlayer`. Each needs its own approval.
- About 953 KB more to download, for 3D scenes only.

## Git

No commit, push or other Git write was done. The two PNGs in `docs/reports/img/` are new untracked files.
