# M1.3 Play Sample or Use as Template: report

**Milestone:** M1.3 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1; Appendix F1).
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21, real display.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this milestone touched are listed below.

## Correction (2026-11-14, found in M1.5)

This report first described the action as reachable from the File menu. **It was not:** the editor's menu bar had been removed, and only the Create dropdown remained, so "Use Sample as Template..." had no route on screen. Milestone M1.5 found this and, with the owner's choice, added a File dropdown beside Create; the action is reachable from that point on. See [m1-5-content-browser-report.md](m1-5-content-browser-report.md).

## Done-when and result

The chart's done-when for M1.3 is: *a template edits freely and the original is untouched.*

**Met headless for what can be checked without a person; one part is by construction, not by test (see limits).** A sample can now be turned into a separate project that holds everything it needs and does not depend on the project that holds the samples, and making it changes nothing in the shipped samples (checked over all 40). That the new project can be edited freely follows from it being separate copies of the files; **no edit was made to a template by a person in the editor**.

## What the audit found

- **Play Sample already existed** as File > Open Sample: it opens a sample as an untitled copy, so Play and any edit work and the shipped file is never written (the project's `samples/` folder is read-only to Save). That is what Appendix F calls *Play Sample*; it was left as it is.
- **Nothing made a separate project.** A copy of just the scene would still need the sample project's models, graphs, songs, sounds and the shared Java code (for example `samples.common.SideBody`, used by the 2.5D samples).
- **The editor opens one project per start**, so it cannot switch to a new project from inside; the roadmap's gallery (M1.4) is where a proper hand-over will live.

## What was built

**`TemplateProject.create(sourceRoot, scenePath, projectsDir, name)`** (`editor-core`, `project/`) makes the new project:

- It copies the scene to `scenes/<name>.json` and writes the project file (`name`, `startScene`).
- It finds every other file the game needs by **following references**, to a fixed point: the file names in the scene, then the file names inside those files (a graph that names a sound, a material that names a texture, a glTF that names its buffers and images, relative to the glTF), and for Java scripts the other scripts in the same package, the sample code they import (`import samples.common.X`) and the files they open by name.
- It **never writes into the source** and **never overwrites a folder**: the new project gets a name no folder has (`Name`, then `Name 2`), made safe as an exported game's name is. A half-made folder is removed on any failure, and a cleanup problem is attached to the original error.
- A referenced file that is not readable JSON stops the copy with the file's name, rather than being skipped: guessing would hide a missing file.

**File > Use Sample as Template...** (`DefaultEditorActions.registerTemplateAction`, `SceneFileController.useAsTemplate`): choose a sample in the file dialog (scenes only); the project is made in the user's projects folder. The open scene is neither replaced nor asked about. The status bar says where it is and how to open it: *"Created the project 'X' from X.json (N files) in <folder>. Open it by starting the editor with --project "<path>". The sample is unchanged."*

## Tests (9 new; `editor-core` 408 to 417)

| Test | What it shows |
|---|---|
| `TemplateProjectTest.shouldMakeASelfContainedProjectFromEverySampleWithoutTouchingTheOriginal` | **All 40 samples** become projects. For each: the new folder is a project; the scene is byte-identical; following the references again from inside the template finds nothing it does not hold; every file exists; **the Java scripts compile inside the template without the sample project** (19 samples have scripts); and a snapshot of the whole `assets` tree (every file's size and time) is unchanged afterwards. About 37 seconds. |
| `...shouldCopyOnlyWhatTheSceneNeedsAndNotOtherScenesOrUnrelatedFiles` | A glTF brings its `.bin` and image (and ignores a `data:` URI); a graph's sound is followed; a script brings its package, the imported shared code and a file named in a string; other scenes, other samples' code and unused files are left out. |
| `...shouldNeverOverwriteAnExistingFolderAndGiveTheNextNameInstead` | `Game`, `Game 2`; a blank name uses the scene's; `a/b:c` becomes `abc`; the first project's own file is untouched. |
| `...shouldRefuseAnythingThatIsNotASceneInsideTheSourceAndLeaveNothingBehind` | A text file, a missing scene and `../outside.json` are refused; no folder is left. |
| `...shouldRemoveTheHalfMadeFolderAndSayWhichFileCouldNotBeReadWhenAReferencedFileIsBroken` | A broken graph file stops the copy, names the file, leaves nothing. |
| `...shouldLeaveSomeTemplatesOnDiskForRunningTheGameOnThem` | Leaves five templates (and compiles their scripts) in `editor-core/build/template-projects/` for the runs below. |
| `SceneFileControllerTest` (+2) | Making a template does not ask about or touch the open scene, and says where the project is; cancelling, a file outside the project, and a broken sample make nothing and say why. |
| `DefaultEditorActionsTest` (+1) | The action is in the File menu and is enabled only when the samples folder exists. |

**A real defect found by running over all 40 samples:** a sample's text "Wave 1 / 3" is not a valid Windows path, and resolving it as a possible file name threw. It is now treated as "cannot name a file". A hand-written test would not have caught this.

## Evidence from the real programs

The game runtime (`:engine-desktop:run --project <template> --scene scenes/<name>.json --smoke-test=240`, with the template's own compiled scripts where it has them):

| Template | Files (the project file included) | Result |
|---|---:|---|
| `ruins-runner-25d` (2.5D, shared scripts, a song) | 7 | 240 of 240 frames, 0 runtime errors |
| `collector-3d` (3D, a graph and Java) | 9 | 240 of 240, 0 errors |
| `terrain-3d` (a `.terrain`) | 3 | 240 of 240, 0 errors |
| `landscape-3d` (many models) | 10 | 240 of 240, 0 errors |
| `racing-3d` (engine sounds, Java) | 8 | 240 of 240, 0 errors |

The real editor on one template: `:editor-desktop:run --args="--project <ruins template> --scene ... --play --smoke-test=240"` rendered 240 frames with 0 errors in the Output Log. Its own screenshot, [m1-3-template/ruins-runner-template-in-editor.png](m1-3-template/ruins-runner-template-in-editor.png), shows the editor opened on a project whose Content Browser holds only a `music` folder, in Play mode, with the HUD ("Orbs: 0 / 8", "Deaths: 0"), the player, the platforms and the help text.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*TemplateProjectTest"` (first run) | 1 | 2 of 5 failed: the "Wave 1 / 3" path defect (real) and a case-insensitive file-system expectation of mine (the code was right) |
| Same, after both fixes and the sanitizer expectation | 0 | 6 of 6 |
| `./gradlew :engine-desktop:run ... --smoke-test=240` on five templates | 0 each | 240 of 240 frames, 0 errors each |
| `./gradlew :editor-desktop:run ... --play --smoke-test=240` on the 2.5D template | 0 | 240 frames, 0 errors |
| `./gradlew cleanTest test --continue --console=plain` (everything, from scratch) | 0 | **1,890 tests, 0 failures, 0 errors, 1 skipped**, 23 modules |

Counts: 1,881 (after M1.1, computed) to 1,890 (+9). editor-core 408 to 417; editor-desktop 134 unchanged. The one skip is the POSIX-only exporter test.

## Limits

- **No person used the new File menu item or the dialog.** The controller method and the action's registration are tested; the menu, the native file dialog and the status message on screen are **Manually Not Verified (Headless Environment)**.
- **The new project is not opened from inside the editor.** It is created, and the message says to start the editor with `--project <path>`. The packaged editor and the project gallery (M1.4) are the places to make that smooth. Because the editor cannot switch projects, "Use as Template" followed by "keep working" is two steps.
- **Finding references by scanning text can be wrong both ways.** It over-copies when a string merely equals an existing path, and under-copies a file whose name a script builds in code (for example `"sounds/" + name`). All 40 templates were built and every Java script compiled, but **only 5 of the 40 templates were run in the game**; the other 35 were not.
- **Kotlin scripts:** the import-following expression expects Java's trailing semicolon, so a Kotlin script's sample imports would not be followed. No sample uses Kotlin scripts that need it, but this is untested.
- **No license manifest is copied**, because none exists for the samples (M1.1 flagged this; the sample-asset license work is M2b.x).
- **"Edits freely"** rests on the files being separate copies. Nobody edited a template and checked the sample afterwards in the editor; the test checks the sample is unchanged after the template is *made*.
- The template is named after its scene; a name chosen by the person (a dialog) is not offered yet.

## Files changed

- New: `editor-core/.../project/TemplateProject.java`, `TemplateProjectTest.java`, this report, and `m1-3-template/ruins-runner-template-in-editor.png`.
- `editor-core/.../file/SceneFileController.java` (`useAsTemplate`), `.../action/DefaultEditorActions.java` (the action), `editor-desktop/.../EditorApplication.java` (registers it), and the tests in `SceneFileControllerTest` and `DefaultEditorActionsTest`.
- `docs/FEATURE_TEST_MATRIX.md`, `docs/milestone-chart.md`, `docs/INDEX.md`.
- Not tracked: `editor-core/build/template-projects/` (five templates, build output).

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: done above, with the limits.
2. Exact commands, exit codes and per-module counts: done above.
3. New failures investigated: the first run's two failures were a real defect (fixed) and a wrong expectation of mine.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above. Next is M1.4 (the template gallery) or M1.5 (the model import path found in the owner's session).
7. **No commit, push or other destructive Git operation was performed.**
