# M9.4: Backend Support Matrix and Release Checks

**Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart.

**Why this milestone:** after M9.3 it was the next startable row. It has no dependency and no gate.

## 1. Acceptance criteria

| # | Criterion (chart row M9.4) | Status | Evidence |
|---|---|---|---|
| 1 | Desktop, TeaVM and Babylon behaviour feature by feature | **Met** | [`docs/BACKEND_SUPPORT_MATRIX.md`](../BACKEND_SUPPORT_MATRIX.md): 44 component types (the 43 the game runtime registers, plus the web editor's Sculpt Mesh) and 21 features, each with a status per backend from a seven-word legend, and evidence or the reason in the cell |
| 2 | Checks on real Windows, Linux and macOS packages | **Windows met; Linux and macOS not checked** | Windows: the packaged editor zip was built, unzipped and run (section 3). Linux and macOS: there is no machine here, and jpackage cannot cross-build. Recorded as Unchecked |
| 3 | Done-when: "Every feature has a recorded status per backend" | **Met, and enforced** | `BackendSupportMatrixTest` fails when a registered component type has no row, a cell starts with no legend word, or a Babylon cell contradicts the player's `SUPPORTED_COMPONENTS`. It was run against a deliberately wrong copy and failed as it should (section 4) |

## 2. What was built and changed

- **`docs/BACKEND_SUPPORT_MATRIX.md`:**
  - a legend: Works, Partial, Read only, No, Blocked, Unchecked, Not applicable;
  - section 1, one row per component type;
  - section 2, features (scripts, graphs, physics, profiles, navigation, materials, terrain, atmosphere, post-processing, models, clips, sprites, audio, music, input, editor);
  - section 3, release checks per package and OS.
- **`BackendSupportMatrixTest` (`engine-desktop`, 4 tests).** Its checks:
  - one row per registered type, plus the web-only type;
  - legend words in every status cell of all three tables;
  - Babylon cells agree with `SUPPORTED_COMPONENTS`, read from `engine-babylon/src/runtime/SceneBuilder.ts`;
  - the 3D physics types are No on TeaVM, and no registered type is No on the desktop.
- **`engine-desktop/build.gradle`:** the matrix and `SceneBuilder.ts` are declared as inputs of `test`. Without that, Gradle treated the test as up to date after an edit to the matrix alone and skipped it (section 4).
- **Fixes the checks needed:**
  - **`assets/samples/README.md`:** table rows for eight samples that had none (`action-rpg-iso-25d`, `action-rpg-valley-3d`, `materials-3d`, `platformer-profile-25d`, `humanoid-animations-3d`, `visual-scripting-3d`, `sculpted-rock-3d`, `depth-compositing-3d`). The TeaVM build's catalog step refuses a sample without one, which hid the compile failure below. Existing rows were not changed.
  - **Babylon `validator.ts`:** `action.navMoveTo`, `action.navStop` and `data.navHasArrived` are listed as desktop only, with the reason "needs navigation" (the physics nodes keep theirs). **This was a regression from M6.4:** Babylon's node-parity test failed once the desktop gained those nodes, and the M6.4 run did not include the Babylon suite. There is a new test for the message.
- **READMEs:**
  - `engine-web/README.md` now warns that the release build does not compile and that its status table is the November 2 state;
  - `engine-babylon/README.md` points to the matrix. Its own "Known limits" still says there are no 3D physics or models, which contradicts its earlier sections; the matrix records what the code does.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `npx vitest run` (engine-babylon), before the fix | 1 | 502 of 503: `shouldOfferTheSameNodeTypesAsTheDesktopEditor` failed on `action.navMoveTo`, `action.navStop` and `data.navHasArrived` (the M6.4 regression) |
| `npx vitest run` (engine-babylon), after | 0 | **504 of 504** |
| `npm run typecheck`, `npm run build` (engine-babylon) | 0, 0 | Built; one chunk of 6.5 MB (1.4 MB gzipped), as before |
| Headless Chrome on the Babylon build (`python -m http.server` and `chrome --headless=new --screenshot`): `hopper-3d`, `platformer-2d`, `action-rpg-valley-3d`, `landscape-3d`, `visual-scripting-3d`, `tilemap-platformer-2d` | 0 each | Looked at: [hopper](m9-4/babylon-hopper-3d.png) drawn with its HUD; [valley](m9-4/babylon-action-rpg-valley-3d.png) terrain drawn, with the status bar reading "unsupported: PlayerComponent x1"; [tile map](m9-4/babylon-tilemap-platformer-2d.png) drawn with its sprites; [landscape](m9-4/babylon-landscape-3d.png) glTF models drawn. These show the scene as loaded; Play was not pressed. The console logs hold only Chrome's own messages (extension registry, a web-app install) |
| `./gradlew :engine-web:gdx_teavm_web_js_release_build`, first run | 1 | `generateGameCatalog`: "assets/samples/README.md has no table row for action-rpg-iso-25d.json" |
| The same, after 4 rows | 1 | The same for `depth-compositing-3d.json`: my first check had matched names anywhere in the README, not table rows |
| The same, after 8 rows | 1 | `gdx_teavm_web_js_release_compile`: "Errors occurred during TeaVM build". Classes TeaVM cannot translate, with their call sites: `ConcurrentLinkedQueue` (`SfxPlayer` from `GameAudio`), `SplittableRandom` (`EngineSynth`), `OggInputStream` (`OggClips`), `Executable.getParameters` and `Parameter` (`JavaMethodNodes` from `NodeRegistry.createDefault`), `Field.getInt` (`KeyNames`), `ImageIO` and `BufferedImage` (`PixelPng` from `TilemapComponent.load`), `System.load` and `HexFormat` (`JoltNatives` from `GameSession.startPhysics3D`) |
| `./gradlew :engine-web:test` | 0 | **9** (JVM tests; they do not run TeaVM) |
| `./gradlew :editor-desktop:zipEditorPackage` | 0 | `ForgeroomEditor-1.0.0-windows-x64.zip`, 159 MB |
| Unzipped into the session's scratch folder; `ForgeroomGame.exe --scene samples/hopper-3d.json --smoke-test=120` | 1 | "Scene not found: ...\app\game\samples\hopper-3d.json": the game launcher's default project is an exported game's folder. It wrote a crash log to `%LOCALAPPDATA%\Forgeroom Game\logs` (see section 6) |
| `ForgeroomGame.exe --project app\sample-project --scene samples/<scene>.json --smoke-test=120`, with `APPDATA` and `LOCALAPPDATA` in the scratch folder | 1, 1, 0 | `hopper-3d` and `platformer-2d`: "No compiled scripts at ...\sample-project\build\scripts. Press Play in the editor once, or pass --scripts", as designed. `action-rpg-valley-3d` (no scripts): "120 of 120 frames, 0 runtime error(s)"; [screenshot](m9-4/windows-package-game.png), looked at: terrain, the player standing on it through Jolt, shadows, HUD |
| `ForgeroomEditor.exe --scene samples/hopper-3d.json --smoke-test` (scratch `APPDATA`) | 0 | "Smoke test rendered 180 frames, 0 error(s)"; [screenshot](m9-4/windows-package-editor.png), looked at: four views with the hopper level |
| `./gradlew :engine-desktop:test` | 0 | **125** (was 121): `BackendSupportMatrixTest` 4 of 4 |

## 4. Failures found and fixed

- **The Babylon node-parity regression from M6.4** (section 2). Fixed, with a test.
- **The TeaVM catalog step** needed eight sample rows. Added.
- **The guard test was skipped after a matrix edit.** I changed one Babylon cell to a wrong value and deleted a row, then ran the test. It "passed": Gradle saw no changed input and did not run it. With the matrix and `SceneBuilder.ts` declared as test inputs, the same wrong copy failed 3 of 4 tests:
  - the row sets differ;
  - "DynamicMeshComponent: matrix says Works, SUPPORTED_COMPONENTS does not list it";
  - a missing-row lookup, which now reports "has no row" instead of a NullPointerException.

  The file was then restored, checked identical to its backup, and the test passed again.
- **Not fixed: the TeaVM compile failure.** It needs changes in audio, visual scripting, sprites and the runtime's physics start-up, or a decision to retire the backend. It is recorded as Blocked (section 6).

## 5. Diff review

- **New:**
  - `docs/BACKEND_SUPPORT_MATRIX.md`;
  - `BackendSupportMatrixTest`;
  - this report and `docs/reports/m9-4/` (six screenshots).
- **Changed:**
  - `engine-desktop/build.gradle` (test inputs);
  - `assets/samples/README.md` (8 rows);
  - `engine-babylon/src/visualscript/validator.ts`, `engine-babylon/test/visualscript.test.ts`;
  - `engine-web/README.md`, `engine-babylon/README.md` (a note each);
  - chart (M9.4 row and R9 overview), FEATURE_TEST_MATRIX, INDEX.
- **Pre-existing uncommitted changes.** `engine-babylon/README.md` and the chart already had uncommitted edits; they were kept.
- **Build outputs, not source:**
  - `engine-babylon/dist/` was rebuilt;
  - `editor-desktop/build/package/` got a new zip and image. An older `GdxUnityEditor-1.0.0-windows-x64.zip` from 2026-11-03 is still there, untouched.

## 6. Known limitations and next steps

- **Decision for the owner: repair TeaVM or retire it.** Both web backends play the same scene files. Babylon is the one being extended (materials, terrain, 3D physics, an editor), while TeaVM has not built since new runtime code arrived.
  - **Repairing it** means:
    - web-safe replacements for the audio pieces (`ConcurrentLinkedQueue`, `SplittableRandom`, the Ogg reader);
    - graph node tables without reflection;
    - PNG decoding without `ImageIO`;
    - a 3D physics start-up that does not name Jolt's class in the web build.

    Then the TeaVM column is checked again.
  - **Retiring it** removes a module and its deploy files, and needs your approval (AGENTS.md §4).
  - **My recommendation:** retire TeaVM and keep Babylon as the web backend. Babylon already runs more of the engine on the web (3D physics, materials, terrain), and TeaVM cannot run Jolt at all.
- **Linux and macOS packages:** unchecked. jpackage must run on each OS; the editor build has the macOS `-XstartOnFirstThread` option, never exercised here.
- **Packaged game and scripts:** a scripted sample cannot play from `ForgeroomGame.exe` until the editor has compiled the project's scripts. The message says so. Pressing Play in the packaged editor (which compiles them) was not tried.
- **Audio:** no sound was listened to on any backend.
- **Babylon screenshots:** they show loaded scenes, not Play; play behaviour rests on the Babylon test suite.
- **Outside the repository:**
  - one crash log, `%LOCALAPPDATA%\Forgeroom Game\logs\crash-20261118-073724.log`, from the first packaged game run, before the scratch folders were set (it records the "Scene not found" error);
  - everything else went to the session's scratch folder.
- **Next by phase order:**
  - unblocked: M9.5 (performance baselines) and the audits MC.1, MP.1 and MT.8;
  - M6.1 and M6.3 need decision records, and M2.3 needs approval.

## 7. Git

No commit, push, stash, reset or other Git operation was performed.
