# M0.4 Acceptance project: report

**Milestone:** M0.4 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R0, "Trustworthy baseline").
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21 (packaged runtime bundled), real display.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this milestone touched are listed below.

## Done-when and result

The roadmap's M0.4 is: *one person builds a small game end to end (create scene, import a model, physics, camera, graph logic, animation, music, save, reopen, play, export, launch the export) and records what they saw at each step in a report.* The chart's done-when is: *the report exists with observed results.*

**Partly met, and this report says exactly which part.** The whole chain was built and run, and what was seen at each step is recorded below. But **it was not built by a person clicking in the editor.** I, the agent, built it through the editor's own operations in code (the same commands, components, asset classes and session objects the editor's panels call), then ran the result in the real game runtime, in the real editor's Play mode, through the real export command of the packaged editor, and launched the exported game. I looked at the screenshots those programs saved. What a person would feel doing it by hand (the Content Browser's import, the Details panel, the graph canvas, the Timeline, the Music Studio, the file dialogs) was **not observed**. A checklist for that follows.

## The project

Built by `AcceptanceProjectTest` (`editor-desktop`, `audit/`), which leaves a real project folder in `editor-desktop/build/acceptance-project` (scene, model, graph, clip, song, audio) and its step log in [m0-4-acceptance/acceptance-steps.txt](m0-4-acceptance/acceptance-steps.txt):
a floor, a red ball that falls onto it, an imported rock model that bobs up and down, a camera and a light, a graph that prints "touched <name>" when the ball touches something, and a drum pattern rendered to audio.

## Step by step: what was seen

| # | Step | How it was done | What was observed | Result |
|---|---|---|---|---|
| 1 | Create a scene | `SceneFileController.newScene(3D)` | A new untitled scene in game mode 3D. | Seen |
| 2 | Import a model | The glTF file copied into the project's `models/`, then Add Component > Model through the catalog | A 498,668 byte binary glTF with its header; a Model component on `Rock Mesh`. In the real runtime the model **is drawn** (screenshot 2: the grey rock). The Content Browser's own import flow was not used. | Seen, by copy |
| 3 | Physics | A box-collider floor, a dynamic rigid body with a sphere collider | After 3 simulated seconds the ball rests at y = 48 (it began at 400; the floor's top is 0). | Seen |
| 4 | Camera | Add Component > Camera in a 3D scene | **Finding F1, fixed (below).** The first run came out orthographic. | Seen, defect fixed |
| 5 | Graph logic | A graph built node by node (On Collision, Get Name, Append Text, Print; 4 links), attached as a Graph Script | The graph printed "touched Floor" once in the simulation, and "[Game] Toucher on Ball: touched Floor" in the real runtime and in the exported game. | Seen |
| 6 | Animation | A clip with a position-Y track (0 at 0 s, 100 at 2 s) made through the Timeline's document, saved, bound by an Animator | At 1 s the rock's mesh had moved to y = 50. | Seen |
| 7 | Music | A song created in a Music Studio session with two drum steps and saved; a **Music Source** component (TD-061) on its own entity plays it; the song also rendered to a WAV with the Music Studio's export | The song file and the Music Source with `music/theme.song`; the WAV is 352,844 bytes and not silent (peak sample 25,503 of 32,767). In the real game runtime the scene with the Music Source ran 240 frames with 0 runtime errors. **Not listened to.** | Seen as data; **not heard** |
| 8 | Save | `saveSceneToFile` | `scenes/acceptance.json`, 4,949 characters, 7 entities. | Seen |
| 9 | Reopen | A fresh editor state opened the file | Its saved form is identical to the original, game mode kept. | Seen |
| 10 | Play | `PlayModeController` with the graph, animation and view extensions; then the **real editor** with `--play --smoke-test=240` | The simulation numbers above, and the authored scene identical to before Play. In the real editor (screenshot 3) the toolbar shows "Playing", the status bar "Play mode started", the outline lists the 7 entities, and the game shows in the viewport; the Output Log had 0 errors in 240 frames. | Seen |
| 11 | Export | The **packaged editor** (`ForgeroomEditor.exe --export-game`, image rebuilt today with `packageEditor`) | "exported the game ... (6 project files); start Acceptance Game.exe": the executable, the runtime jars (including `engine-material`), a bundled Java runtime, and the project's six files. | Seen |
| 12 | Launch the export | `Acceptance Game.exe --smoke-test=240` on its own bundled Java | 240 of 240 frames, 0 runtime errors, the graph fired, and the frame (screenshot 4) is the same as the runtime's: green floor, red ball, rock. Exit code 0. | Seen |

Screenshots (the programs' own saved frames, in [m0-4-acceptance/](m0-4-acceptance/)):
1. `1-runtime-default-orthographic-camera.png`: the game runtime before the camera fix; only the top of the ball shows, no floor.
2. `2-runtime-perspective-camera.png`: the same project with a perspective camera; floor, ball and rock.
3. `3-editor-play-mode.png`: the real editor in Play mode.
4. `4-exported-game.png`: the exported standalone game.

(The ball and the rock overlap because I put them at the same place; that is my layout, not a defect.)

## Findings

| # | Finding | State |
|---|---|---|
| **F1** | **A camera added to a 3D scene was orthographic.** `Add Component > Camera` created `new CameraComponent()`, orthographic, whatever the scene's game mode. In the real runtime this showed a flat slice: only the top of the ball and no floor (screenshot 1). Play also switches the editor to the 2D view for an orthographic camera. A first-time user following the obvious steps would get a confusing result. | **Fixed.** A new camera is perspective in a 3D or 2.5D scene and orthographic in a 2D scene. `ComponentCatalogTest.shouldAddAPerspectiveCameraTo3DAndTo2_5DScenesAndAnOrthographicOneTo2DScenes` pins it; the acceptance test asserts it. |
| **F2 (withdrawn)** | **This finding was wrong and is withdrawn.** My first version of this report said a scene cannot play a song and must use a rendered WAV. In fact a scene plays a `.song` through the **Music Source** component (TD-061): it is in Add Component under Audio, the game runtime's audio binds it, and five samples use it. I did not check the Add Component list before writing the finding, and my first acceptance project used an Audio Source with a rendered WAV instead. | **Corrected 2026-11-14:** the acceptance test now adds a Music Source with the song, and the runtime smoke run was repeated (240 frames, 0 errors). The export and the exported-game launch (steps 11 and 12) and screenshots 2 to 4 were made with the **earlier version** of the scene, which had an Audio Source with the WAV, not the Music Source; they were not repeated. |
| **F3** | **The export copies the runtime from the packaged image.** The image under `build/package` was from 2026-11-06, before terrain, materials and today's fixes; an export from it would carry the old engine. It had to be rebuilt (`packageEditor`, about a minute) for this run to test current code. I did not examine whether the export warns about a stale image. | Observed, not fixed. Worth a check in milestone M9.4 (release checks). |
| **F4** | In the editor's Content Browser (screenshot 3) the folders `animations`, `graphs`, `models`, `music` and `sounds` are listed but `scenes` is not, although `scenes/acceptance.json` exists. Scenes are presumably not an asset type the browser lists. | Observed, not judged: I did not check whether that is intended. |

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-desktop:test --tests "*AcceptanceProjectTest"` | 0 | Steps 1 to 10 built and checked; step log written (run twice: before and after the camera fix) |
| `./gradlew :engine-desktop:run --args="--project <acceptance-project> --scene scenes/acceptance.json --smoke-test=240"` | 0 | 240 of 240 frames, 0 runtime errors, graph fired (run twice: default camera, perspective camera) |
| `./gradlew :editor-desktop:run --args="--project <acceptance-project> --scene scenes/acceptance.json --play --smoke-test=240"` | 0 | "Smoke test rendered 240 frames, 0 error(s) in the Output Log" |
| `./gradlew :editor-desktop:packageEditor` | 0 | The app image rebuilt (the old one was 8 days old) |
| `ForgeroomEditor.exe --project <acceptance-project> --export-game <folder>` (the packaged editor) | 0 | Exported, 6 project files |
| `"Acceptance Game.exe" --smoke-test=240` | 0 | 240 of 240 frames, 0 runtime errors |
| `./gradlew cleanTest test --continue --console=plain` (everything, from scratch, after the fix) | 0 | **1,869 tests, 0 failures, 0 errors, 1 skipped**, 23 modules |

Counts: 1,867 after M0.3 to 1,869 (+2: `ComponentCatalogTest` +1, `AcceptanceProjectTest` +1). editor-core 407 to 408, editor-desktop 121 to 122. The one skip is the POSIX-only exporter test, unchanged.

## Files changed

- `editor-core/.../component/ComponentCatalog.java` (`createCamera`, F1)
- Tests: `ComponentCatalogTest` (+1), `AcceptanceProjectTest` (new, `editor-desktop/.../audit/`)
- Evidence: `docs/reports/m0-4-acceptance/` (four screenshots and the step log)
- Docs: this report, `docs/milestone-chart.md`, `docs/INDEX.md`
- Not tracked: the rebuilt `editor-desktop/build/package` image and `editor-desktop/build/acceptance-project` (build outputs)

## What was not observed (the person's part of M0.4)

Everything that needs a person at the keyboard, **Manually Not Verified (Headless Environment)** in the sense of AGENTS.md §8:
- Importing the model through the **Content Browser** (drag and drop or the import action), and placing it in the viewport with the gizmos.
- The **Details** panel for each component (physics, camera, audio source, animator), and the Add Component menu.
- The **graph canvas**: placing and wiring the four nodes with the mouse.
- The **Timeline** panel: adding the track and the keys, scrubbing, previewing.
- The **Music Studio** panel: drawing notes, instruments, the mixer, the export dialog.
- **Save** and **Open** dialogs, the window title and the dirty marker.
- **Listening** to the music and the sound; the editing viewport's look (the screenshot is Play mode); performance; Linux and macOS.
- Whether the **export dialog** (as opposed to the command line) behaves the same.

### Checklist for the person's run

Start the editor with `GDXUNITY_EDITOR_TRACE=<file>` (the testing aid added in this session) so a log of statuses, undo steps and exceptions is kept. Then, in a new 3D scene, record what you see for each: 1 File, New 3D scene. 2 Import `SculptedRock.glb` into the Content Browser and drag it into the viewport. 3 Add a floor and a ball with Rigid Body 3D and colliders. 4 Add a Camera (it should now be perspective) and a Sun. 5 Add a Graph Script and wire On Collision to Print. 6 Add an Animator and key a position in the Timeline. 7 Make a song in the Music Studio and export it to WAV; add an Audio Source with it. 8 Save. 9 Close and Open the file again. 10 Press Play, watch, press Stop. 11 Export the game. 12 Start the exported game. Write down anything that surprised you, and whether the music was audible.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: done above, with the part that is not met stated.
2. Exact commands, exit codes and per-module counts: done above.
3. New failures investigated: the first run found F1 (fixed); no other failures.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: INDEX and the chart updated. `docs/FEATURE_TEST_MATRIX.md` was not changed (open).
6. Limits and next steps: above. The next milestone is M0.5 (reconcile the documents), and the person's run of the checklist closes M0.4 fully.
7. **No commit, push or other destructive Git operation was performed.**

## Addendum, 2026-11-14: the owner's hands-on session, started and deferred

A person (the owner) started the hands-on run on a fresh, empty project (`editor-desktop/build/hands-on-project`, containing only `gdxunity.project` and `models/SculptedRock.glb`) with `GDXUNITY_EDITOR_TRACE` on. The session ran about six minutes, ended with exit code 0 and no exception, and **did not reach the checklist's steps 2 to 12.** The owner then decided to **finish the milestones before running end-to-end tests**, so the hands-on run is **deferred**, and this milestone stays "partly met". What the session showed, from the trace and the owner's remarks:

| # | Finding | Evidence | Where it belongs |
|---|---|---|---|
| H1 | **There is no discoverable way to bring a model into a project.** The owner tried File > Open on `SculptedRock.glb` twice (15:17:06 and 15:18:38). The refusal added in M0.3 is accurate but does not say how to use a model, and it arrives as a modal error dialog. The editor has an Import action only for graphs. | Trace; owner's remark "open failed" | M1.5 (Content Browser completeness): add a File > Import and a hint in the refusal |
| H2 | **The Place tool fails without saying why in a new project.** The level objects live in the main project's `assets/prefabs/level/`, so a fresh project has none, and the message "Choose an object to place first" (15:20:21) does not say there are none to choose from. | Trace; the project folder listing | M2b.3 (Objects palette): say "no objects in this project" and offer starter objects |
| H3 | **The undo history is capped at 100 steps** (`CommandHistory.DEFAULT_MAX_HISTORY`) and each terrain stroke is a step; 28 Raise clicks in a few seconds used 28. Heavy painting pushes the oldest steps off, including the New Level. | Trace (undo reached 28); source | M3.1 / M3.2 (terrain): consider merging quick strokes or a larger cap |
| H4 | **Not determined:** the game mode a New scene starts in (the owner switched modes within seconds), and whether the "save changes?" prompt appeared when a second New was made over a modified scene (the trace does not log dialogs). | Trace | To check in the hands-on run |
| H6 | **Export Game had no route on screen** (no File menu, no button, no shortcut), so the checklist's step 11 could not have been done from the editor; found in M1.5 and fixed there with a File dropdown. | Source | M1.5 (done) |
| H5 | Reached and worked: New scene, New Level on grass, 28 Raise strokes, Sculpt, Level and view toggles. | Trace | n/a |

The checklist above is unchanged and still the way to close this milestone.
