# Sample Games Report — Runnable 2D and 3D Starting Templates

**Date:** 2026-10-30 · **Branch:** `phase-12-3d-foundation` · **HEAD:** `1dc0a11` at start and end (no commits)
**Request:** "add at least 3 2D game runnable samples and at least 3 3D game runnable samples which users could
potentially use as starting templates" · **Decision:** TD-034 (proposed and applied; not reviewed separately)
**Status:** Implemented and integrated. Every sample was played headlessly by automated tests and run in the real
editor on Windows 11 with `--smoke-test --play`, which checks exit codes and Output Log errors. Nobody looked at the
screen, so how the samples look and feel is **Manually Not Verified (Headless Environment)**.

## What was delivered

Seven samples in the project's `samples/` folder. The repository's `assets/` folder is that project, and the
packaged editor bundles it as its sample project. Scripts are in `scripts/samples/`; a guide is in
[`assets/samples/README.md`](../../assets/samples/README.md).

| Sample | Kind | Gameplay |
|---|---|---|
| `platformer-2d` | 2D | Run and jump, platforms, 8 coins, goal flag, follow camera |
| `breakout-2d` | 2D | Paddle, ball, 40 bricks, score and lives |
| `shooter-2d` | 2D | Ship, pooled bullets and enemies, rising difficulty, scrolling stars |
| `physics-sandbox-2d` | 2D | Box2D only: ramps, crate stack, bouncy blocks, no scripts |
| `collector-3d` | 3D | Rolling ball, 10 spinning gems, timer, third-person camera |
| `runner-3d` | 3D | Three-lane endless runner, jumping, pooled obstacles, distance score |
| `hopper-3d` | 3D | Jump across 8 floating platforms to a goal, respawn on falling, follow camera |

## Engine and editor additions needed to make them games

Before this work, Play mode drew through the editor cameras with the grid and gizmos visible, and a game had no way
to show a score. Added:

- **`CameraComponent` and `GameCameraRig`.** While a scene with a camera plays, the view draws through that camera
  with no editor overlays. `GameViewPlayModeExtension` switches to 2D or 3D to match the camera and restores the
  previous view on Stop.
- **`TextComponent` and `HudRenderer`.** Screen-space text anchored to the view.
- **Editor integration.** Serializers, "Game" category in Add Component, and Details panels for both components, with
  a new text row in `InspectorFieldRows`.
- **Opening samples.** File → Open Sample... (`SceneFileController.openFrom`), plus the `--scene <file>` and `--play`
  launch options.
- **`NonInteractiveDialogService`.** Used in smoke-test mode. Found during testing: a failed scene open showed a modal
  error box, and that unattended run hung until it was killed. Now the run exits 1 and names the errors.
- **`EditorApplication.registerComponentSerializers`.** One shared list of the 20 component serializers, used by the
  editor and by `SampleGamesTest`.

## Verification

| Check | Command / test | Result |
|---|---|---|
| All tests | `./gradlew test --continue` | Exit 0. **539** tests, 0 failures (before: 514). editor-core 288 (+8), editor-desktop 31 (+8), engine-render 72 (+9); other modules unchanged |
| Samples play headlessly | `SampleGamesTest` (8 tests) | Each scene loads with the editor's serializers. All sample scripts compile and bind. Simulated keys then cover: walking, jumping and collecting a coin; launching and breaking bricks; firing and scoring; crates resting on the ground; collecting a gem; changing lane and jumping; falling and respawning. R restarts where offered |
| Samples in the real editor | `EditorLauncher --smoke-test=240 --scene samples/<name>.json --play` from `assets/`, for all 7 | All exit 0 with `0 error(s) in the Output Log`. This includes play-mode compilation, the game camera, the PBR 3D path and HUD text drawing |
| Packaged exe | `GdxUnityEditor.exe --project <copy of bundled sample> --scene samples/{breakout-2d,hopper-3d}.json --play --smoke-test=240` | Both exit 0. Scripts were compiled by the bundled Java 21 runtime into `build/scripts/samples/`. This closes the 18-A gap "script compilation in the packaged editor not exercised" |
| Failure is detected | Same command with `samples/missing.json` | Before the fix it hung (modal dialog). After the fix: exit 1, both errors listed |

Bugs found by the new tests and fixed before delivery:
- The collector's HUD entity "Gems Text" was itself counted as a gem, because its name starts with "Gem". It was
  renamed "Collected Text".
- In the platformer, the jump reached exactly the first ledge's height, so the ledge could not be landed on. The
  jump speed went from 640 to 700.
- A timing mistake in the hopper test. The game was correct.

## Diff review

**New:**
- engine-render: `CameraComponent`, `TextComponent`, their serializers, `GameCameraRig`, `HudRenderer`;
  tests `GameCameraRigTest`, `GameComponentsTest`
- editor-core: `GameViewPlayModeExtension`, `NonInteractiveDialogService`; their tests
- editor-desktop: `GameComponentEditorProviders`, `SampleGamesTest`
- assets: `samples/*.json` (7), `samples/README.md`, and 12 scripts under `scripts/samples/`
- this report

**Edited:**
- `SceneEditorWorkspace`: game view, HUD, view size
- `EditorApplication`: serializer list, extension, Open Sample, `--scene`/`--play`, smoke dialogs
- `EditorLaunchOptions`: `--scene`, `--play`
- `SceneFileController`: `openFrom`
- `DefaultEditorActions`: `FILE_OPEN_SAMPLE`
- `ComponentCatalog`
- `InspectorPanel`
- `InspectorFieldRows`: `textRow`
- tests: `EditorLaunchOptionsTest`, `SceneFileControllerTest`, `ComponentCatalogTest`,
  `ComponentCloningAndSerializationCoverageTest`
- docs: `TECHNICAL_DECISIONS.md` (TD-034), `FEATURE_TEST_MATRIX.md`

The scene files were produced by a generator script kept outside the repository, in the session scratchpad. The
JSON files are the source of truth from now on; edit them in the editor.

## Addendum: three 2.5D samples (same day)

The owner asked for "3 samples 2.5D games as well similar to the Trine franchise". These are original games in that
genre: side-scrolling physics platformers with 3D levels. They use no characters, names, levels or art from Trine.

| Sample | Gameplay |
|---|---|
| `ruins-runner-25d` | Side-scroller in a 3D ruin with backdrop, pillars and foreground rocks at different depths; horizontal and vertical moving platforms that carry the player; spikes; checkpoints; 8 orbs; goal |
| `three-heroes-25d` | Three heroes sharing one body, switched with 1/2/3 or Tab: Mason conjures up to two crates (E); Scout runs faster and double-jumps; Brute pushes the heavy block and smashes the cracked wall (E). The tower can only be climbed by Brute pushing the block against it and Scout double-jumping from it |
| `grapple-gorge-25d` | Grappling rope (hold J or Shift in the air) with pendulum swinging and pumping across two chasms; a pushable crate on a pressure plate raises a gate |

They share `scripts/samples/common/SideBody.java` and `SideCamera25D.java`. `SideBody` is a plain helper class
(gravity, side-view box collision, riding movers, pushing); `SideCamera25D` is a front-on follow camera.

Verification:
- `SampleGamesTest` grew to 12 tests. It now also checks that there are at least three 2.5D samples with perspective
  cameras. It covers: an orb collected by jumping; death on spikes and respawn; a moving platform moving; Mason's
  crate dropping onto the ground; switching to Scout and a double jump that goes past single-jump height; Mason
  unable to push the heavy block while Brute pushes it; only Brute smashing the wall; hooking an anchor, swinging out
  and releasing; and the crate on the plate opening the gate.
- In the real editor, `--smoke-test=240 --play` ran each of the three scenes: exit 0, no Output Log errors.
- `./gradlew test --continue`: exit 0, **543** tests, 0 failures (editor-desktop 35).

Not verified: that each level can be finished from start to end. Gap widths and jump heights were worked out from
the movement constants and are documented in the scripts, but nobody played through, and how the levels look and
feel was not seen.

## Known limitations

- Look and feel not observed: colours, text size, camera framing, difficulty.
- No audio.
- Collision is hand-written in scripts. Scripts cannot reach Box2D bodies or collision events, and there is no 3D
  physics simulation (TD-028).
- Scripts take no parameters from the scene, and only one script is bound per entity. Samples rely on entity-name
  conventions (documented in the README) and named constants.
- Only the built-in 15 px font, scaled.
- No mouse aiming: mouse coordinates are window coordinates.
- The `engine-desktop` runtime cannot run these scenes yet (Phase 18-B).
- Editor shortcuts stay active during Play; for example F (focus selection) still acts on the editor camera. Not
  checked for side effects in the game view.

## Next steps

1. User: File → Open Sample..., Play each one, and judge the feel. The tuning constants are at the top of each script.
2. Candidate engine follow-ups the samples exposed: script parameters in the scene file, Box2D access and
   collision callbacks for Java scripts, audio, and a game runtime that plays scenes (18-B).

No commit, push or other Git operation that changes history or the index was performed.
