# M1.5 Content Browser completeness: report

**Milestone:** M1.5 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1), together with finding H1 of the owner's partial hands-on session ([M0.4 report](m0-4-acceptance-project-report.md), addendum): there was no discoverable way to bring a model into a project.
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21, real display for one smoke run.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this milestone touched are listed below.

## Done-when and result

The chart's done-when for M1.5 is: *each asset type passes a test that opens it.* The deliverable is: *every asset type has an icon, an opening editor and a "used by" list.*

**Partly met, by design.** Every asset type now has a **tested, defined response** when opened, a type badge, a "used by" list and a way in (Import). But **only four types have an editor of their own** (graph, clip, sprite, song). The other seven (model, texture, sound, prefab, material, terrain, sculpt) get a tested explanation of how that kind of file is used, because their editors are other milestones' work: mesh editing R7a, a sound editor MC.5, the Material Editor M2.3, and so on. Building them here would be out of order.

## What the audit found

| Item | Before | Now |
|---|---|---|
| Icon | A text badge for every type (`[MODEL]`, `[SONG]` and so on), thumbnails for textures, models and sprites | Unchanged: every type has a badge. No graphical icons were made. |
| Opening | A double-click opened graphs, clips, sprites and songs; **for the other 7 types it silently did nothing** | Every type does something defined: the four open their editor; the seven say in the status bar how the file is used (`AssetOpening.guidance`) |
| "Used by" | Nothing | Selecting an asset shows which project files name it: "Selected asset: X (used by 2: scenes/a.json, prefabs/b.prefab)" (`AssetUsages`) |
| Getting a file in | **No way at all** (the owner tried File > Open on a model twice) | **File > Import Asset...** copies a file from anywhere into the folder for its type (`AssetImport`) |
| File > Open of a non-scene | Said what File > Open opens, not how the file is used | Also gives the type's guidance and points to File > Import Asset |

**A larger finding:** the editor's **File menu was not on screen**. The menu bar had been removed at the owner's request, and only the Create dropdown remained. So every File action with no button or shortcut was unreachable: **Open Sample..., Use Sample as Template... (M1.3), Export Game..., Exit, and the New 2D, 2.5D and 3D Scene entries**, as well as the new Import Asset. I asked the owner, who chose to add **one File dropdown beside Create**; the rest of the old menu bar stays removed. This also means **my M1.3 report was wrong** to call "Use Sample as Template" integrated: it was not reachable from the UI until this milestone (corrected there).

## What was built

- **`AssetOpening`** (`editor-core`, `asset/`): which types open in an editor, the guidance text for every other type, and `PLACEABLE`, the types the scene view places when dropped. The guidance texts name real places: the Material section of a Mesh Primitive's Details, Use Selected and Paint in a Terrain's Details, the Outliner's context menu for applying a prefab, Sculpt for a sculpt.
- **`AssetUsages`**: the "used by" list. A data file (scene, prefab, graph, material, clip, song, terrain, sprite) uses an asset when one of its JSON strings is **exactly** the asset's path; a Java or Kotlin script when a string literal is. The `build` folder is skipped. A file that mentions the path but is not readable JSON is still listed, so a use is not hidden.
- **`AssetImport`** and **File > Import Asset...**: the file goes to `models/`, `textures/`, `sounds/`, `music/`, `sprites/`, `graphs/`, `animations/`, `prefabs/`, `sculpts/`, `materials/` or `terrains/` under a name nothing there has (`Rock 2.glb`); nothing is ever overwritten, and the original is not touched. A `.gltf` with separate buffers or images goes into a folder of its own with them, so their relative names keep working; a missing one stops the import with its name and leaves nothing. A data file that names files this project lacks (a material's texture) is imported alone, and those names are **reported, not guessed at**. A file already in the project is not copied again. After an import the Content Browser shows the folder and selects the asset, and the status bar says how the file is used.
- **The File dropdown** in the top bar (`EditorMenuBar` with File and Create).

## Tests (12 new; `editor-core` 417 to 428, `editor-desktop` 134 to 135)

| Test | What it pins |
|---|---|
| `AssetOpeningTest` (2) | Exactly graphs, clips, sprites and songs have an editor; every type has a non-blank text that never promises a double-click; **the "drag into the scene view" texts are given exactly for the types `AssetPlacement` really places** (each type is dropped and the result compared), and never for materials, terrains, graphs, clips or songs |
| `AssetUsagesTest` (3) | Exact names only (`models/rock.glb2` and a sentence that mentions the path do not count); build output and other file kinds are skipped; a script literal counts; a broken JSON file that mentions the path is listed; in the real project, a song is found used by sample scenes |
| `AssetImportTest` (6) | Each kind into its folder; `Rock 2.glb` beside an existing `Rock.glb`, which is unchanged; a glTF with a buffer and a URL-encoded image path into `models/tree/` (and `models/tree 2/` the second time), a `data:` URI ignored; a missing glTF file names itself and leaves nothing; a URI that leaves its folder is refused; unknown and missing files refused; a file already inside is not copied; a material's missing texture is reported; the action imports, reports how the file is used, offers every asset extension in the dialog, and reports a failure in a dialog |
| `SceneFileControllerTest` (changed) | The File > Open refusal for each of seven types names File > Import Asset and that type's guidance |
| `EditabilityAuditTest` (+1) | The audit's list of opening editors and the Content Browser's are the same |

## Evidence from the real editor

`./gradlew :editor-desktop:run --args="--smoke-test"` rendered 180 frames with 0 errors in the Output Log. Its own screenshot, [m1-5-content-browser/top-bar-with-file-dropdown.png](m1-5-content-browser/top-bar-with-file-dropdown.png), shows **File** beside **Create** in the top bar. The dropdown was **not opened**: its items, the import dialog, the double-click messages and the "used by" text were not seen on screen.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew compileJava compileTestJava` | 1, then 0 | One missing import, fixed |
| `./gradlew :editor-core:test --tests ... :editor-desktop:test --tests "*EditabilityAuditTest"` | 0 | All new and changed tests pass |
| `./gradlew :editor-desktop:run --args="--smoke-test"` (with a screenshot) | 0 | 180 frames, 0 errors |
| `./gradlew cleanTest test --continue --console=plain` (everything, from scratch) | 0 | **1,902 tests, 0 failures, 0 errors, 1 skipped**, 23 modules |

Counts: 1,890 (after M1.3) to 1,902 (+12). The one skip is the POSIX-only exporter test.

## Limits

- **Seven types have no editor of their own.** They have a tested explanation instead. Their editors: models R7a, sounds MC.5, materials M2.3, and the others as listed in the chart.
- **Manually Not Verified (Headless Environment):** opening the File dropdown and choosing its items, the native import dialog, a double-click on each type in the Content Browser, and the "used by" text in the status bar.
- **The "used by" list** finds exact names only. A path a script builds in code is not found. It reads the project's files each time an asset is selected, which is fast for this project; it was not measured on a large one.
- **Import** does not follow the files a material, prefab or graph names (it reports the missing ones); importing a whole set at once is not offered. Only one file is imported per action.
- **No graphical icons**; the type badges are text.
- The File dropdown brings back **all** File actions, including New, Open and Save, which also have buttons. That duplication was accepted with the owner's choice of a single dropdown.

## Corrections made to earlier records

- [M1.3 report](m1-3-use-as-template-report.md): "integrated (File menu)" was wrong; the action had no route on screen until this milestone's File dropdown.
- The chart's M1.3 row and the matrix's M1.3 row, likewise.
- [M0.4 report](m0-4-acceptance-project-report.md) addendum: the checklist's "Export the game" step could not be done from the editor's UI before this milestone (Export Game had no route on screen).

## Files changed

- New: `editor-core/.../asset/AssetOpening.java`, `AssetUsages.java`, `AssetImport.java`, and their tests; this report and `m1-5-content-browser/top-bar-with-file-dropdown.png`.
- `editor-core/.../action/DefaultEditorActions.java` (Import Asset), `.../file/SceneFileController.java` (refusal text), `editor-desktop/.../EditorApplication.java` (File dropdown, import, double-click guidance), `.../ui/AssetBrowserPanel.java` ("used by"), and tests in `SceneFileControllerTest` and `EditabilityAuditTest`.
- `docs/FEATURE_TEST_MATRIX.md`, `docs/milestone-chart.md`, `docs/INDEX.md`, the M0.4 and M1.3 reports.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: done above, partly met by design, with the reason.
2. Exact commands, exit codes and per-module counts: done above.
3. New failures investigated: none.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above. Next in R1 is M1.4 (the template gallery), M1.6 (template source ownership) or M1.7 (labels).
7. **No commit, push or other destructive Git operation was performed.**
