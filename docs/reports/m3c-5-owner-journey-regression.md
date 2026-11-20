# Roadmap M3c.5: Owner journey regression project and checklist (2026-11-16)

Milestone M3c.5 of [milestone-chart.md](../milestone-chart.md) (roadmap R3c). **Status:**
- **Implemented.** A regression test runs the owner acceptance journey (chart section 3) on a new project, through the editor's own operations. It leaves the project on disk.
- **Done-when partly met.** "The journey in §3 runs with recorded evidence": every step that exists today passes with evidence. Four parts stay **BLOCKED**, each naming the milestone that unblocks it.
- **Manually Not Verified (Headless Environment):** the human desktop checklist (section 6) has not been run by a person.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 to M3c.4 work (listed in their reports).
- No new module, dependency or technical decision.

**Design choices (mine, per the standing instruction):**
- **The "regression test project" is built by the test from a new Empty 3D project**, not shipped as a fixed sample. So it always exercises the real New Game path. The test leaves it in `editor-desktop/build/owner-journey/project` for the checklist and for running the real programs.
- **Two defects found by the journey were fixed here**, because the journey cannot pass without them (section 4).

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | A regression test project | **Met** | `OwnerJourneyRegressionTest` builds it from Project > New Game > Empty 3D and leaves it on disk |
| 2 | The journey in section 3 runs with recorded evidence | **Partly** | 9 checked steps PASS, 4 parts BLOCKED (table below). The test writes the table to `build/owner-journey/journey-steps.md` and asserts the BLOCKED list, so no step can quietly change |
| 3 | Save, reopen, export | **Met** (headless) | Save to the opened file. Close, then reopen in a fresh editor state: every entity, the genre, the mode, the terrain heights and paint survive, and the scene saves to the same text. Play again starts at the spawn. The export holds the scene, the `.terrain` and the house, and its files play 2 s in the game runtime's session with 0 errors |
| 4 | Human desktop checklist | **Written** | Section 6. **Not run by a person** |
| 5 | Web status stated | **Met** | Web: **not supported**. The web player ignores terrain (M3.11), has no terrain collision, and has no Action RPG profile; this project is not offered on the web |
| 6 | UX gate (Appendix E5) | **Pending** | Screenshots of the real editor and game runtime on the project (below); nothing was clicked by a person |

**Step results** (copied from the test's output):

| Step | What | Result | Evidence or reason |
|---|---|---|---|
| 1 | New project (Empty 3D) opens in World | PASS | `Journey`, 8 files, the starter palette included |
| 2 | Sculpt a hill, paint gravel, an editable .terrain | PASS | `terrains/Level.terrain`: hill 59 units, gravel at the centre, the file equals the edited terrain, the paint stroke undoes and redoes |
| 3 | Place trees and boulders from Objects | PASS | Oak Tree and Boulder from the project's own palette, one undo step each |
| 3 | Realistic verified CC0 trees and boulders; scatter foliage | BLOCKED | M2b.2, M2b.3 (CC0 import, provenance), M3.6 (scatter) |
| 4 | Place the player and a spawn point from Characters | PASS | Player and Spawn Point on the sculpted ground |
| 5 | Action RPG + 3D, Play | PASS | Starts at the spawn point; W walks the player 793 units over the sculpted terrain, grounded (real Jolt, editor Play) |
| 6 | Stop | PASS | Scene JSON identical, selection kept, 11 undo steps kept (**after the fix in section 4**) |
| 7 | Place a house; Edit 3D Model opens 3D Modeling in context | PASS | `models/cabin.gltf` imported (File > Import Asset's code) and placed. "Edit 3D Model of House (opens 3D Modeling; mesh editing is planned)" opens 3D Modeling with its notice; Back to World keeps the selection |
| 7 | A realistic house from the Buildings palette | BLOCKED | M2b.3 |
| 8 | Change the house's geometry and material; save a derived model | BLOCKED | R7a (M7a.1 to M7a.5); a glTF model has no material slot (the M2.2 gap) |
| 9 | Save, close, reopen, Play, export | PASS | Criterion 3 |
| 9 | Launch the exported game as a program | BLOCKED | Tests export with a stand-in runtime image (no packaged editor is built). The export's files were played by the game runtime's session instead; a packaged run is on the checklist |

## 2. What was built

- **`editor-desktop` test `OwnerJourneyRegressionTest`:** the journey (section 1) and its step table.
- **`editor-core` `project/StarterPalette`:** new 3D and 2.5D projects get project-owned copies of `prefabs/level` (Oak Tree, Pine Tree, Bush, Rock, Boulder) and `prefabs/characters` (Player, Spawn Point).
  - A file the project already has is never overwritten. 2D projects get none.
  - A scene that names no game mode gets none, rather than a guessed one.
  - Used by the gallery's `TemplateGallery.create` (empty starts and samples) and by File > Use Sample as Template.
  - The gallery's file count and size include it, and are checked against the files on disk.
  - `LevelSession`'s folder constants now point at it.
- **`editor-core` `EditorState.swapSceneForPlay`:** Play and Stop switch scenes through it, so the undo history is kept (section 4).

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*TemplateGalleryTest" --tests "*TemplateProjectTest" --tests "*SceneFileController*" :editor-terrain:test` | 1, then 0 | 3 failures first, all from the palette (section 4); fixed |
| `./gradlew :editor-desktop:test --tests "*OwnerJourneyRegressionTest"` | 1, 1, 0 | First a compile slip (`asset()` is a string). Then Stop lost the undo history (defect, section 4). Then a test-setup gap (`Gdx.input`), then passing |
| Mutation: Play switching scenes the old way (clearing history) | 1 | `PlayModeControllerTest` fails, and the journey stops at step 6; restored |
| `./gradlew :engine-desktop:run --args="--project <scratch copy> --scene scenes/Main.json --smoke-test=240"` with `GDXUNITY_SCREENSHOT` | 0 | "240 of 240 frames, 0 runtime error(s)" |
| `./gradlew :editor-desktop:run --args="--project <scratch copy> --scene scenes/Main.json --smoke-test"` with `GDXUNITY_EDITOR_SELECT=House` | 0 | 180 frames, 0 errors |
| `./gradlew cleanTest test --continue` | 0 | Every module passes (counts below) |

| Module | Before (M3c.4) | After |
|---|---|---|
| `editor-core` | 509 (1 skipped) | 511 (1 skipped): +1 palette test, +1 Play-history test |
| `editor-desktop` | 160 | 161: +1 journey test |
| All other modules | unchanged | unchanged, all pass (engine-scene 182, engine-render 167, engine-desktop 113, editor-terrain 24 and the rest) |
| **Total (JVM)** | | **2,090 tests, 0 failures, 1 skipped** (the POSIX-only test) |

**Screenshots** (looked at):
- [m3c-5/runtime-journey-frame-240.png](m3c-5/runtime-journey-frame-240.png): the game runtime on the journey project. The third-person camera is behind the player at the spawn point, with the boulder, the house, the oak tree and the gravel patch painted on the grass.
- [m3c-5/editor-journey-world.png](m3c-5/editor-journey-world.png): the editor with the House selected, in the side view, showing the sculpted hill, the player's capsule, boulder, tree and house. The footer reads "Game Genre Action RPG", 3D, "Playable".

## 4. Failures found and fixed

- **Play cleared the undo history (a real defect, Appendix E3).**
  - The cause: Play showed the runtime copy, and Stop the authored scene again, through `EditorState.setActiveScene`, which always clears the history. So every Play threw away the person's undo steps.
  - The fix: `swapSceneForPlay` switches without clearing. This is safe because undo, redo and commands are refused while the game plays, and Stop brings back the very scene object the commands were made on.
  - **The M3b.5 report and the chart claimed this was tested:** `ProfilePlayStepTest` compared the history count before and after Play, but its history was empty to begin with, so it passed either way. The chart's M3b.5 row now carries a correction.
  - New test `PlayModeControllerTest.shouldKeepTheUndoHistoryThroughPlayAndStop`, mutation-checked.
- **A new project had nothing to place** (finding H2 of M0.4): Objects and Characters read the project's own prefab folders, which a new project lacked. Fixed by `StarterPalette`. The three test failures this caused were expected changes:
  - the gallery's count for a sample (now includes the palette);
  - a test project whose scene names no mode (it now gets no palette, rather than failing);
  - a catalog check whose expected message changed with the order of checks (title is checked first again).
- **Test-only:** the game runtime's session needs a stand-in keyboard (`Gdx.input`), as in `TemplateGalleryProjectsTest`.

## 5. Diff review

- **New:**
  - `StarterPalette`;
  - `OwnerJourneyRegressionTest`;
  - this report and `docs/reports/m3c-5/`.
- **Changed:**
  - `EditorState`, `PlayModeController`, `TemplateGallery`, `SceneFileController`, `LevelSession`;
  - tests `TemplateGalleryTest`, `TemplateRatingsTest` (the new `emptyStarts(root)` signature), `PlayModeControllerTest`;
  - docs: chart (M3c.5, R3c line, M3b.5 correction, journey step 6), `INDEX.md`, `FEATURE_TEST_MATRIX.md` (three rows).
- No files outside this repository were touched; line endings kept.

## 6. Human desktop checklist (for a person at the keyboard)

Record for each step what you saw, PASS or FAIL, and a screenshot where useful. Use a packaged editor (`./gradlew :editor-desktop:zipEditorPackage`) for steps 15 and 16.

1. Start the editor. Project > New Game > **Empty 3D** > Create Project. The editor restarts on it in **World**, with a large view and the footer (Game Genre, Game Mode, Customize, Play).
2. World strip > **Terrain**: the ground tools appear. Start a level on grass (or use the Level button).
3. **Raise** a hill with a few strokes; Ctrl+Z takes back one stroke, Ctrl+Y redoes it.
4. **Ground**: paint a gravel patch with soft edges.
5. World strip > **Objects**: the palette lists Oak Tree, Pine Tree, Bush, Rock and Boulder. Place two trees and a boulder; each placement is one undo step.
6. World strip > **Characters**: place the **Player** and a **Spawn Point** away from it.
7. Footer: Game Genre **Action RPG**, Game Mode **3D**; the line says "Playable".
8. Press **Play**: the player appears at the spawn point, the camera follows from behind, and WASD walks over the hill without falling through. Space jumps.
9. Press **Stop**: everything is where it was, the same thing is selected, and **Ctrl+Z still undoes the last edit made before Play**.
10. File > Import Asset: choose `models/landscape/cabin.gltf` from the shipped assets, then drag it into the World.
11. Select the house > Customize > **Edit 3D Model**: 3D Modeling opens on the house, with the notice saying mesh editing is not available yet. Read it; is it clear?
12. **Back to World**: the house stays selected.
13. Save (Ctrl+S). Close the editor and start it again on the project. Everything is there, and the genre is still Action RPG.
14. Play again; Stop.
15. File > Export Game; **launch the exported game**. It plays like step 8.
16. Confirm the shipped samples folder is unchanged.
17. Listening: none in this journey.
18. Note anything confusing, slow, cluttered or hard to find (Appendix E5).

## 7. Known limitations and next steps

- **Blocked, by milestone:**
  - CC0 assets and scatter: M2b.2, M2b.3, M3.6;
  - the Buildings palette: M2b.3;
  - house mesh edit and replay: R7a and M3c.6;
  - a packaged export launched by a person: the checklist.
- **The starter palette** is primitive-built placeholders, not the realistic assets the journey asks for.
- **Next in R3c, in the chart's order:**
  - M3c.6 (house-edit replay) waits on R7a.
  - M3c.7 (Scenario B: 2D Platformer + 2D) needs a Platformer + 2D profile and MC.3.
  - M3c.8 (Scenario C: 2.5D side-scroller) can be attempted now on the Platformer + 2.5D profile. It would be recorded PASS or BLOCKED per step, as here.

## 8. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
