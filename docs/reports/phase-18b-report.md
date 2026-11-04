# Phase 18-B Report — Game Runtime Parity

**Date:** 2026-10-30 · **Branch:** `phase-12-3d-foundation` · **HEAD:** `1dc0a11` at start and end (no commits)
**Plan:** [phase-18-packaging.md](../milestones/phase-18-packaging.md) §18-B · **Decision:** TD-035 (proposed and
applied; the owner has not reviewed it yet)
**Status:** Implemented and integrated on Windows 11 x64. The runtime was **started and checked by exit codes and
console output**. I did not see what it draws: **Manually Not Verified (Headless Environment)**. macOS and Linux were
not run.

## Starting state

HEAD `1dc0a11`, with 93 uncommitted entries from Phases 17, 13-B and 18-A and the sample games. They were left in
place. `engine-desktop` showed a hard-coded 2D demo scene and could not load scene files.

## What was built

| Area | Change |
|---|---|
| engine-desktop | `GameLaunchOptions` (`--scene`, `--project`, `--scripts`, `--smoke-test[=N]`; all paths absolute). `GameScripts` loads precompiled script classes from a folder or jar. `GameSession` starts scripts, 2D physics, graphs from `.graph` files and Animators from `.anim` files, in that order, and steps physics before the scene with a 0.1 s cap. It refuses a scene without a camera and records graph errors (up to 100). `GameRenderer` draws the 3D pass, then the 2D pass through the scene camera, then the screen text, and reports models that fail to load. `GameComponentSerializers` is the shared list of component types. `DesktopLauncher` replaces the demo and exits 0 or 1 |
| engine-desktop `StartupHelper` | Passes the program arguments to the relaunched JVM on macOS and on Linux with NVIDIA. Before this change, a relaunched runtime would have lost `--scene` |
| engine-visualscript | `GraphCollisionForwarder`, now shared by the editor's `GraphPlayModeExtension` and the runtime |
| editor-desktop | `registerComponentSerializers` delegates to `GameComponentSerializers` (which adds the prefab instance component). New dependency on `engine-desktop` |
| Build | `engine-desktop`: depends on `engine-visualscript` and `engine-animation`. A `gameScripts` source set compiles `assets/scripts`. `run` defaults to `--scene samples/platformer-2d.json` and passes the compiled scripts through `-Dgdxunity.scripts`. `assets/` is no longer bundled into the runtime jar (now 20 classes, about 62 KB) |
| Docs | TD-035, milestone §18-B, this report, FEATURE_TEST_MATRIX, INDEX, AGENTS.md §4/§5, samples README |

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Every sample runs in the runtime | Met | `:engine-desktop:run --args="--scene samples/<name>.json --smoke-test=240"`: exit 0 and "240 of 240 frames, 0 runtime error(s)" for all 10 samples |
| 2 | Loads what the editor saves: models, lights, sky, tone mapping, graphs, Animators, Camera, Text | Met by exit code, not looked at | A scratch project (outside the repo) with `BendingStrip.gltf`, sun, sky light, tone mapping, `BallBounce.anim` and a Print graph: exit 0, the log shows "Hello on Bouncer: graph running in the runtime", and no model error. The same scene with a missing model exits 1: "Model on Imported Model: Model file not found: models/Missing.gltf". Unit test `shouldLoadSceneFilesWithEveryEngineComponentType` |
| 3 | Scripts run without a compiler | Met | Classes come from the Gradle `gameScripts` output. The packaged editor compiled a scratch copy of the sample project, and the runtime ran `grapple-gorge-25d` from that project's `build/scripts` (exit 0) |
| 4 | Plays the same as editor Play | Met for the ten samples | `SampleGamesTest.shouldPlayEverySampleTheSameInTheGameRuntimeAsInEditorPlayMode`: 240 frames with the same keys, and every entity's position, active state and text must match within 0.001. To check that the test can fail, I ran the runtime path 1% slower; the test failed and named Player and Main Camera. The samples use no graphs or Animators, so parity for those is covered only by `GameSessionTest`, not compared with the editor |
| 5 | Errors exit 1 and name the file | Met | Missing scene: "Scene not found: …\assets\samples\nope.json", exit 1. Missing scripts: "No compiled scripts at …\assets\no\such\dir. Press Play in the editor once, or pass --scripts …", exit 1. Missing graph or clip: unit tests |
| 6 | Tests green, counts, docs | Met | Below |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew test --continue` (before) | 0 | 543 tests, 0 failures |
| `./gradlew test --continue` (after) | 0 | **563 tests, 0 failures**: editor-animation 13, editor-core 288, editor-desktop 36 (+1), editor-visualscript 4, engine-animation 32, engine-core 27, engine-desktop 19 (new), engine-render 72, engine-scene 63, engine-visualscript 9 |
| `:engine-desktop:run --args="--scene samples/<each>.json --smoke-test=240"` ×10 | 0 | 240 frames, 0 errors each |
| `:engine-desktop:run` with the scratch feature scene / the broken model scene | 0 / 1 | See criterion 2 |
| `:editor-desktop:run --args="--smoke-test=240 --scene samples/three-heroes-25d.json --play"` | 0 | The editor still works with the new dependency |
| `:editor-desktop:zipEditorPackage`, then `GdxUnityEditor.exe --project <scratch copy> --smoke-test=240 --scene samples/grapple-gorge-25d.json --play` | 0 / 0 | The packaged editor works and `Documents\GdxUnity` was not created |

## Problems found and fixed during the work

- **Relative working folder.** With the default `.`, the project path normalized to an empty path, so scenes
  resolved against the drive root (`\samples\platformer-2d.json`). The unit tests used absolute paths and missed
  this; the first real run found it. The fix makes paths absolute before normalizing, and there is now a test for
  the relative case.
- **Silent model failures.** The 3D renderer skips a model it cannot load. In the editor the reason shows in the
  Details panel, but the runtime has no panel, so a missing model would have gone unnoticed. Missing models are now
  runtime errors and fail a smoke test.
- **A wrong UTF-8 byte in the milestone document** came from a script writing in the Windows code page. It was
  corrected, and every file touched was checked to be valid UTF-8.

## Deviation from the proposal

I proposed moving the editor's Play setup into shared code. That was done for the component list and for graph
collision forwarding. It was not done for the start-up sequence, because sharing it needs a new module, and AGENTS.md
§4 requires approval before creating a module. For now there are two copies (`PlayModeController` with its
extensions, and `GameSession`), and the parity test keeps them equal. If you approve an `engine-runtime` module, both
could use one `GameSession` (TD-035, *Alternatives*).

## Diff review

The new files are in `engine-desktop/src/main` (5) and `engine-desktop/src/test` (4), plus
`engine-visualscript/.../GraphCollisionForwarder.java` and this report. Changed files: `DesktopLauncher` (rewritten;
the demo scene is gone and no code used it), `StartupHelper` (argument forwarding only),
`engine-desktop/build.gradle`, `editor-desktop/build.gradle` (one dependency), `EditorApplication` (serializer list
delegation and unused imports removed), `GraphPlayModeExtension` (uses the shared forwarder), `SampleGamesTest`
(parity test), plus the docs listed above. Existing uncommitted work was not touched except in those files. Every
edit there is listed here. The scratch projects used for testing are in the session scratchpad, not in the
repository.

## Known limitations

- What the runtime draws has not been looked at (**needs graphics test**: whether the runtime image matches the
  editor game view). One known difference is the ambient light. The editor's renderer uses a brighter ambient
  (0.35) than the runtime default (0.2), so scenes without a Sky Light may look darker in the runtime than in editor
  Play. All the 3D samples have a Sky Light.
- There is no runtime executable yet. You start the runtime through Gradle or a classpath.
- The start scene must be passed with `--scene`.
- Errors go to stderr only. There is no crash log or message box yet (18-C).
- The Gradle task compiles Java scripts only. Kotlin scripts work only if the editor compiled them.
- There is no 3D physics, no sound, and no way for scripts to switch scenes.
- The start-up sequence exists in two copies (see the deviation section).
- macOS and Linux were not run. The `StartupHelper` argument forwarding has not been tested on those systems.

## Next steps

- **18-C (game export):** package a project's scenes, assets and precompiled scripts with the runtime as a
  standalone exe through jpackage, with a start scene in `gdxunity.project`, a crash log and a message box. Not
  started; it needs your go-ahead.
- Optional: approve an `engine-runtime` module so editor Play and the runtime share one start-up sequence.
- E2E on Windows: run `gradlew :engine-desktop:run --args="--scene samples/<name>.json"` next to editor Play and
  compare how they look.

No commit, push or other Git operation was performed.
