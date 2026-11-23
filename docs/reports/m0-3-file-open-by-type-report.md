# M0.3 File > Open by type: report

**Milestone:** M0.3 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R0, "Trustworthy baseline").
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this milestone touched are listed below.

## Relationship to the earlier R0 pass

The earlier R0 pass ([r0-baseline-report.md](r0-baseline-report.md), §3.3) added the asset-type guard and the "not a scene" message. This milestone replaces its "double-click it in the Content Browser" wording and goes further by opening the four asset types that have an editor.

## Done-when and result

The chart's done-when for M0.3 is: *each type opens correctly or is refused with a clear message.* The types named are `.song`, `.graph`, `.anim`, `.sprite` and `.prefab`.

**Met headless; the desktop wiring and the native dialog were not exercised in the running editor** (see limits). The four types that have an editor now open in it from File > Open. Everything else is refused with a message that says what the file is and what File > Open does.

## What the audit found

| Finding | Detail |
|---|---|
| The 2026-11-12 bug is already half fixed | `SceneFileController.openFile` refused known asset types (a song gets "not a scene" instead of a schema error), with a test. |
| That refusal gave false advice | It always said "Double-click it in the Content Browser to open it in its editor". The Content Browser's double-click handles only graphs, animation clips, sprites and songs. For a `.prefab` (and models, textures, sounds, sculpts, materials, terrains) double-click does nothing, so the message sent people to a dead end. |
| Nothing opened a song, graph, clip or sprite from File > Open | The dialog was filtered to `*.json`, so those types could only arrive by typing a file name, and then only be refused. |
| Unsaved-changes prompt came first | File > Open asked "save changes?" before the file was chosen. Opening a song leaves the scene alone, so that prompt would have been wrong for assets. |

## What changed

| Type | Now |
|---|---|
| `.song`, `.graph`, `.anim`, `.sprite` | **Open in their own editor** (Music, Visual Scripting, Timeline, Sprites), the same handlers as a Content Browser double-click. The scene is not touched and no unsaved-changes prompt appears. A file outside the project's assets folder is refused with the reason ("is outside this project's assets folder ..., Copy it into the project first"). |
| `.prefab` | Refused: "crate.prefab is a prefab file, not a scene, and File > Open does not open it. File > Open opens scenes, songs, graphs, animation clips and sprites; other files are used from a scene, through the Content Browser or the Details of the component that uses them." A prefab has no editor of its own: it is placed in a scene and edited there. |
| Models, textures, sounds, sculpts, materials, terrains | The same refusal, naming the type. No promise of a double-click. |
| Scene (`.json`) | Unchanged, except the "save changes?" prompt now appears after the scene is chosen, not before. Cancel still keeps the scene. |
| The dialog | File > Open's file dialog now filters scenes plus the four asset types. File > Open Sample stays scenes-only. The title is "Open". |

Code: `SceneFileController` (asset routing, `AssetOpener`), `EditorDialogService` (a multi-extension `chooseOpenFile`, default falls back to one extension so existing fakes work), `TinyFileDialogService` (multi-pattern filter), `ProjectPaths` (new, the inside-or-outside-the-project rule), `AssetType.getExtensions()` (new getter), `EditorApplication` (one `openAssetInItsEditor` shared by the Content Browser and File > Open, and `openAssetFromFile`).

## Tests

| Test | What it pins |
|---|---|
| `SceneFileControllerTest.shouldOpenASongGraphClipAndSpriteInTheirOwnEditorWithoutTouchingTheScene` | Each of the four reaches the opener with the right type; no unsaved-changes prompt; the dirty scene is untouched. |
| `...shouldReportWhyAnAssetCouldNotBeOpenedAndLeaveTheSceneAlone` | An opener's reason is shown as an error. |
| `...shouldRefuseEveryAssetWithNoEditorOfItsOwnSayingWhatItIsAndWhatFileOpenDoes` | Seven types (prefab, model, texture, sound, sculpt, material, terrain) are each refused naming themselves, with the true list of what File > Open opens, and never mention a double-click (checked without regard to capital letters) or a schema error. |
| `...shouldOfferScenesAndTheOpenableAssetsInTheOpenDialogButOnlyScenesInTheSampleDialog` | The extensions the dialog is asked for. |
| `...shouldStillAskAboutUnsavedChangesAfterASceneIsChosenAndKeepTheSceneWhenCancelled` | A scene still prompts, after the choice, and Cancel keeps the scene. |
| `SceneFileControllerTest.shouldSayAnAssetFileIsNotASceneInsteadOfShowingASchemaError` (existing) | With no editor attached, a song is still refused plainly. Unchanged and passing. |
| `ProjectPathsTest` (5) | A file below the assets folder gives a forward-slash path; `..` inside the project is resolved; climbing out, a sibling like `assets-backup`, and the folder itself are outside; null arguments are refused. |

Did the tests fail without the change? The new tests call API that did not exist, so they could not have passed on the old code. The "no double-click advice" check is the one that pins the old defect, and I tightened it from case-sensitive to case-insensitive during this work because the old message said "Double-click" with a capital D.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*SceneFileControllerTest" --tests "*DefaultEditorActionsTest" compileJava compileTestJava` | 0 | Everything compiles; `SceneFileControllerTest` 18 of 18 |
| `./gradlew :editor-core:test --tests "*ProjectPathsTest" --tests "*SceneFileControllerTest"` | 0 | 5 of 5 and 18 of 18 |
| `./gradlew cleanTest test --continue --console=plain` (everything, from scratch) | 0 | **1,867 tests, 0 failures, 0 errors, 1 skipped**, 23 modules |

Counts: editor-core 402 to 407 in the final run (+5 `ProjectPathsTest`), after +5 earlier from `SceneFileControllerTest`; total 1,857 (after M0.2) to 1,867 (+10). Every other module is as in the [M0.2 report](m0-2-data-consistency-report.md). The one skip is the POSIX-only exporter test, unchanged.

## Limits of this evidence

- **Not seen in the running editor.** The wiring in `EditorApplication` (`openAssetInItsEditor`, `openAssetFromFile`) is not unit-tested; only the controller and the path rule are. Whether a song picked in File > Open really switches to the Music tab, whether the graph, clip and sprite editors behave the same as from the Content Browser, and what the dialog looks like are **Manually Not Verified (Headless Environment)**.
- **Native dialog.** The multi-extension filter goes to `tinyfd_openFileDialog` as several patterns; I did not see it on screen and did not check how Windows, Linux and macOS each present it.
- **Animation clips.** Opening a clip from File > Open uses the same lookup as a double-click (`animatorEntityFor`), which finds an entity whose Animator uses the clip; with none, the editor says so in the status bar. That is the existing behaviour, not changed.
- **A `.prefab` still has no way to be opened.** The refusal is accurate; whether a prefab should open for editing is a decision for milestone M1.5 (every asset type has an opening editor).
- **Behaviour change to note:** a scene now asks about unsaved changes after it is chosen, not before.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: done above.
2. Exact commands, exit codes and per-module counts: done above.
3. New failures investigated: none appeared in the full run.
4. Diff reviewed, no unrelated changes: only the files listed above.
5. Matrix and INDEX updated: INDEX and the chart updated. `docs/FEATURE_TEST_MATRIX.md` was not changed in this milestone (open: it should record the File > Open row).
6. Limits and next steps: above. Next is M0.4, the acceptance project.
7. **No commit, push or other destructive Git operation was performed.**
