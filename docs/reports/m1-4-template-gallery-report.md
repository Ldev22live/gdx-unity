# M1.4 Template gallery: report

**Milestone:** M1.4 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1). **Decision record:** TD-086 (proposed).
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21 (Gradle toolchain), real display.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier milestones; the files this milestone touched are listed below.

## Why M1.4

The previous report named M1b.5 as next, but that is a usability test with an unfamiliar person, which an agent cannot run. M1.4's dependencies (M1.3, M1b.3) are met, it has no approval gate, and it is step 1 of the owner journey (open or create a project and land in World).

## Done-when and result

*A new project from each offered template opens, plays and exports.*

**Met headless, with the limits below.** `TemplateGalleryProjectsTest` makes a project from each of the **43** choices the gallery offers (3 empty starts and 40 samples). For each project it opens the start scene in the editor's state (in its own game mode, with a camera), compiles the scripts, plays 120 frames in the game runtime's session with 0 errors, and runs Export Game. It also checks that the shipped samples are unchanged. One template, **Mesh Colliders**, cannot play headless, because its glTF collider needs a GL context. The test names it as the only exception, and that project was played in the real game runtime instead.

## What was built

| Part | Detail |
|---|---|
| **Project > New Game...** | New action `file.new-game`, in the header's Project dropdown (`ActionInventoryTest`: 73 actions, all with a route) |
| **The gallery** (`TemplateGalleryScreen`) | A list of the empty 2D, 2.5D and 3D starts and the templates, with an All / 2D / 2.5D / 3D filter. The selected entry shows its preview, genre, dimension, size, summary, a desktop line, a web line and "Not yet rated Fully Customizable" (M1.7 is the gate). It has a project-name field and **Play Sample** / **Use as Template** buttons (**Create Project** for an empty start). After the project is made it offers **Open '<name>' (restarts the editor)** or **Not now** |
| **Catalog** (`assets/templates/gallery.json`, `TemplateGallery`) | 40 entries (title, genre, summary, web flag). The dimension comes from each scene's `gameMode`; the size comes from `TemplateProject.plan` (what Use as Template would copy). Reading is strict: a missing scene, a scene with no game mode or an entry with no title is an error that names it |
| **Empty starts** (`StarterProject`) | One scene in `scenes/Main.json`. 2D: an orthographic camera. 2.5D: a side-view camera, sun, sky light and ground strip. 3D: a camera tilted 30 degrees down, sun, sky light and flat ground. It never overwrites a folder and leaves nothing behind on failure |
| **Hand-over** (`EditorRelaunch`, TD-086) | The editor opens one project per start, so it asks the usual unsaved-changes questions, starts a second editor with `--project` and `--scene` (the packaged launcher, or the same Java, options and class path without a debugger) and exits. If the start fails, this editor stays open and says why |
| **Previews** (`tools/make_template_previews.py`) | 40 PNGs at 400 by 225 (924 KB), each the game runtime's own frame after 120 frames. All 40 runs exited 0 |
| **Testing aid** | `GDXUNITY_EDITOR_GALLERY=1` (or a template's title) opens the gallery on the first frame (listed in AGENTS.md) |

## Tests

| Test | What it shows |
|---|---|
| `TemplateGalleryTest` (12, editor-core) | Starts first, then every sample scene exactly once (the catalog fails when a sample is missing); dimension from the scene; size equals the plan; the plan equals what is copied; platform lines; preview only when the file exists; strict catalog errors; no catalog gives only the starts; size formatting; empty starts in each mode with the right camera, lights and ground; no overwrite; no leftover folder on a write failure; both kinds made through the gallery |
| `TemplateGalleryProjectsTest` (2, editor-desktop) | The done-when over all 43 choices (above). Also leaves the empty starts and Mesh Colliders on disk in `editor-desktop/build/gallery-projects/` for the runs below |
| `EditorRelaunchTest` (3, editor-desktop) | The command for a packaged launcher, for a development run (debugger option dropped) and for `java.exe` on Windows |
| `ActionInventoryTest` (changed) | 73 actions; New Game's planned home; the inventory in [world-first-ux-audit.md](world-first-ux-audit.md) regenerated |

## Evidence from the real programs

| Run | Result |
|---|---|
| Game runtime, `--smoke-test=240`, on the gallery-made My 2D Game, My 25D Game, My 3D Game and physics-mesh-3d | Exit 0 each; 240 of 240 frames, 0 runtime errors. [Screenshots](m1-4-gallery/gallery-projects-in-game-runtime.png): empty 2D (nothing but the background, as intended), the 2.5D ground strip, the 3D ground, and balls and boxes settled in the mesh bowl |
| Game runtime on all 40 samples (preview tool) | 40 of 40 exited 0 |
| Editor, `GDXUNITY_EDITOR_GALLERY=1 --smoke-test=180` | Exit 0, 0 errors. [gallery.png](m1-4-gallery/gallery.png) (1920x991), [gallery-1024x640.png](m1-4-gallery/gallery-1024x640.png): the whole gallery fits at the minimum window |
| Editor, `GDXUNITY_EDITOR_GALLERY="Circuit Racer"` | Exit 0, 0 errors. [gallery-racer.png](m1-4-gallery/gallery-racer.png): preview, "Genre: Racing, Dimension: 3D, Size: 8 files, 235 KB", the platform lines, Play Sample and Use as Template |
| Editor started as the hand-over would start it: `--project "<My 3D Game>" --scene scenes/Main.json --smoke-test=180` | Exit 0, 0 errors. [new-3d-project-in-editor.png](m1-4-gallery/new-3d-project-in-editor.png): `World / Main.json`, the World strip, Customize and Play in the footer |

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*TemplateGalleryTest" --tests "*TemplateProjectTest" :editor-desktop:test ...` (first) | 1 | 1 failure from a wrong expectation of mine (folder names drop the dot: "My 25D Game"); the desktop tests did not run |
| `./gradlew :editor-desktop:test --tests "*TemplateGalleryProjectsTest"` (first runs) | 1 | Mesh Colliders: no collider geometry source, then "models cannot be loaded here" (needs GL). Handled as a named exception, see above |
| `./gradlew :editor-core:test --tests "*TemplateGalleryTest"` and `:editor-desktop:test --tests "*EditorRelaunchTest" --tests "*TemplateGalleryProjectsTest"` | 0 | TemplateGalleryTest 12/12, EditorRelaunchTest 3/3, TemplateGalleryProjectsTest 1/1 (67 s); then `shouldLeave...` 1/1 |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*ActionInventoryTest"`, then without the variable | 0, 0 | 6/6 |
| Game runtime and editor smoke runs above | 0 each | 0 errors each |
| `./gradlew cleanTest test --continue` (whole repository, first attempt) | not finished | Stopped by Claude Code because the machine ran low on memory |
| `./gradlew cleanTest test --continue` (whole repository, rerun on request) | 0 | **1,940 tests, 0 failures, 0 errors, 1 skipped** (the POSIX-only exporter test), 23 modules |
| `GDXUNITY_EDITOR_GALLERY="Circuit Racer" ./gradlew :editor-desktop:run --args="--smoke-test=180"` after the deferred-click edit | 0 | 180 frames, 0 errors |

**Counts (from the full run's XML reports):** editor-core 440 to **452** (+12, 1 skipped), editor-desktop 143 to **148** (+5); every other module unchanged. Repository 1,923 (computed after M1b.2) to **1,940**, 0 failures.

**Last edit:** list, filter and Create clicks now rebuild the gallery after the click finishes (`Gdx.app.postRunnable`), the pattern the theme toggle uses. It is compiled and tested by the full run, and the gallery smoke run passed on it; the clicks themselves were not made.

## Limits

- **Manually Not Verified (Headless Environment):** no gallery button was clicked (filter, rows, Play Sample, Use as Template, Create Project, Open, Not now), and the restart was never triggered from the button. Only its target command was run.
- **Export** in the test copies a stand-in runtime image. No exported game from a gallery template was launched in this milestone.
- **Mesh Colliders** plays only with graphics, so its play check is the game-runtime run, not the test.
- **The MT reference templates do not exist**, so the gallery offers the 40 samples. None is labelled Fully Customizable; matrix-driven labels are M1.7.
- **The editor's starting view is not framed on the new scene** (seen in the screenshot); existing behaviour, not changed here.
- The gallery is reached from the Project menu; it is not shown at startup. A restart takes as long as a cold start.
- The web line marks the nine samples the web player's headless tests list; none was run in a browser.

## Next

- TD-086 needs approval (or changes).
- A person runs: Project > New Game > Racer > Use as Template > Open, then Play in the new editor.

## Files changed

- New: `editor-core/.../project/TemplateGallery.java`, `StarterProject.java`, `TemplateGalleryTest.java`; `editor-desktop/.../EditorRelaunch.java`, `.../ui/TemplateGalleryScreen.java`, `EditorRelaunchTest.java`, `TemplateGalleryProjectsTest.java`; `assets/templates/gallery.json`, `assets/templates/previews/*.png` (40); `tools/make_template_previews.py`; this report and `m1-4-gallery/*.png`.
- Changed: `editor-core/.../project/TemplateProject.java` (`plan`, two helpers made package-visible), `editor-desktop/.../EditorApplication.java` (action, gallery, hand-over, testing aid), `ActionInventoryTest.java`, `docs/reports/world-first-ux-audit.md` (regenerated), `assets/samples/README.md`, `AGENTS.md` (testing aid), `docs/TECHNICAL_DECISIONS.md` (TD-086), `docs/FEATURE_TEST_MATRIX.md`, `docs/milestone-chart.md`, `docs/INDEX.md`.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: above. The done-when is met headless; not met: clicks by a person and a launched export.
2. Commands, exit codes and counts: above (full run: 1,940 tests, 0 failures).
3. New failures investigated: a wrong expectation of mine, and the GL-only collider (named exception).
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
