# M3c.6 House-edit replay

**Date:** 2026-11-24. **Milestone:** M3c.6 (R3c), milestone chart §8. **Done-when:** "The changed geometry shows in the scene and in Play."

A house from the Buildings palette is placed in a new project. One roof part is raised in 3D Modeling and saved as a derived model bound to that part. The edited roof then shows in the World, in editor Play, after save and reopen, and in the exported game's files. The provider original and the house's other three roof parts stay as shipped. M3c.6 adds no production code. R7a (M7a.1 to M7a.5) built the capability, and this milestone adds the replay as a Gradle regression plus a drawn check on a real GL context.

## Acceptance criteria

| Criterion | Result | Evidence |
|---|---|---|
| Starts after R7a, from a house a user can place | Met | Both checks start from Project > New Game > Empty 3D (`TemplateGallery.create`). The headless test places `prefabs/buildings/CC0 House.prefab` through the level session's Objects placement, one undo step, standing on a created level. The smoke instantiates the same prefab through `PrefabService`. |
| Modify a wall or roof | Met (roof) | Edit 3D Model (`WorkspaceNavigator.editModel`) opens 3D Modeling on the house. `MeshWorkspace` lists its parts, including **Roof NE**. Every top vertex of the roof's highest primitive is moved 0.4 model units up (40 world units at the prefab's import scale of 100), one undoable translate per vertex. |
| Save as a derived model | Met | `MeshSaveSession.save(UNIQUE, "Roof NE raised")` writes `models/edited/Roof NE raised.gltf` and rebinds only Roof NE, in one undo step. Undo returns the shipped model and redo returns the derived one. A second test confirms that **Update** on the shipped Kenney model is refused as read-only. That test also confirms that nothing is written and the roof stays bound. |
| Return to World | Met | `navigator.backToWorld()` returns to World with the house still selected. |
| The changed geometry shows in the scene | Met | Headless: Roof NE is bound to the derived model, and that file's highest point is exactly 0.4 model units above the shipped one. Its world-space top is 40 units higher. Roof SE, SW and NW stay on the shipped model, whose bytes are unchanged. GL: at the projected middle of the raise, the World frame's colour changed from rgb(90,96,115) to rgb(116,124,148). The control point on Roof SW is unchanged at rgb(108,117,149). [Before](m3c-6/1-world-before.png), [after](m3c-6/2-world-after.png). |
| The changed geometry shows in Play | Met | Headless: the Play runtime copy binds Roof NE to the derived model, and Stop returns the authoring scene JSON unchanged. GL: the editor Play frame samples rgb(116,124,148) at the raise and leaves the control unchanged. [Play frame](m3c-6/3-play.png). |
| Replay after save, close and reopen | Met | `SceneFileController.save`, then a fresh `EditorState` with its own `PrefabService` reopens the scene. The prefab sync keeps the Roof NE override, and the other roofs stay on the original. The reopened roof top stands where it was raised, and Play again binds the derived model. GL: a fresh model cache draws the same colour at the raise. [Reopened frame](m3c-6/4-reopened.png). |
| Survives export | Met | `GameExportCommand` with a stand-in runtime image. The game folder holds the derived model byte for byte, and the shipped roof model is still exported for the other three roof parts. The exported scene binds Roof NE to the derived model. Its files play for 2 s in the game runtime's `GameSession` with 0 errors. GL: the exported files, drawn with their own model cache, show the raise. [Game-files frame](m3c-6/5-game-files.png). |

I viewed all five frames. The NE roof corner, nearest the camera, stands visibly taller after the edit in every frame from the second on. The other three corners match the first frame.

## Design choices (milestone details decided, per the working agreement)

- **The roof rather than a wall.** M7a.5's smoke already extruded a wall face. Raising a roof exercises a different part and a translate instead of an extrusion.
- **Unique instance mesh.** The chart asks for "a derived model" that then shows in the scene. Of the three M7a.4 modes, **Create unique instance mesh** both derives a new project-owned model and binds it to the edited part. **Save as new model** only writes a reusable asset, and **Update** is refused for a provider original. The refusal is tested.
- **The edit goes through the modeling API, not panel clicks.** Both checks drive `MeshSaveSession` and `MeshPartEditor`, the same objects the 3D Modeling panel edits. M7a.4 and M7a.5 already pressed the panel's Apply and Save buttons in their Windows smoke. Selecting every top vertex by hand in the panel was not automated here.

## Files

| File | What |
|---|---|
| `editor-desktop/src/test/java/.../HouseEditReplayTest.java` (new) | Gradle regression with 2 tests: the replay from a new project to the exported game, and the refusal to overwrite the shipped roof model. |
| `editor-desktop/src/test/java/.../HouseEditReplayGraphicsSmoke.java` (new) | GL and Jolt run that draws the same camera view at five stages and compares sampled colours at the raise and at a control roof. It writes `build/m3c6/frames`. |
| `docs/reports/m3c-6/*.png` (new) | The five frames from the Windows run. |
| `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`, `docs/milestone-chart.md` | M3c.6 row, index entry and chart status. |

## Verification

Starting and ending HEAD: `b8bace5`. The starting working tree had 98 modified or untracked entries: R7a (M7a.1 to M7a.6), sample assets, OpenGameArt integration, editor and runtime work, and documentation. None of these were changed by this milestone. Only the files listed above were added or edited.

1. `.\gradlew.bat :editor-desktop:test --tests "*HouseEditReplayTest" --offline --console=plain -x generateAssetList`: exit 0. The XML report shows 2 tests, 0 failures, 0 errors and 0 skips (5.4 s and 0.9 s).
2. Graphics smoke on Windows 11, run from the repository root with the editor-desktop test runtime classpath. The run used a scratch Gradle init script outside the repository that registers a `JavaExec` task, `houseReplaySmoke`, with `classpath = sourceSets.test.runtimeClasspath`, `mainClass = ...HouseEditReplayGraphicsSmoke`, `workingDir = rootProject.projectDir` and `--enable-native-access=ALL-UNNAMED`. Command: `.\gradlew.bat -I <init script> :editor-desktop:houseReplaySmoke --offline -x generateAssetList`. Exit 0. Output:
   `House-edit replay: raised roof at pixel (640, 366) rgb(90,96,115) -> World rgb(116,124,148), Play rgb(116,124,148), reopened rgb(116,124,148), game files rgb(116,124,148); control roof rgb(108,117,149) -> rgb(108,117,149); derived model models/edited/Roof NE raised.gltf`.
   The first attempt failed honestly: Play refused with "Nothing under Player to stand on". The empty 3D start's ground box has no collider. The smoke now adds a Box Collider 3D fitted to the box, which is the fix Play's message names.
3. Full suite: `.\gradlew.bat test --continue --offline --console=plain -x generateAssetList`. Results below.

**Full suite not completed.** The run was stopped by the host for low system memory before any module wrote results. Its log is empty, and no XML report is newer than the smoke frames. It was not restarted. Per-module before and after counts are therefore **not recorded** for this milestone. The milestone adds only test sources in `editor-desktop` (2 new tests) and changes no production code. The last complete run (M7a.5 report, same HEAD) was 2,475 passed and 1 skipped. M7a.6's tests were added after that run, so a fresh full run is still required before this milestone meets AGENTS.md §10 item 2.

## Known limitations

- **Collision keeps the authored box.** Roof NE's Box Collider 3D (220 × 40 × 220) keeps its authored size. In Play the raised roof is drawn raised, but something landing on it meets the old box top. Exact collision follows the edited mesh only with a Mesh Collider 3D (M7a.5). Refitting authored boxes after a mesh edit is not built.
- **The game-files frame is not the game runtime's camera.** The exported files are drawn by `SceneRenderer3D` from the smoke's fixed camera. `GameRenderer`'s follow camera would look at the player, not the roof. The exported launcher was not run: the tests use a stand-in runtime image, as in M3c.5.
- **The graphics smoke is not part of `gradlew test`.** It needs a display and is run by hand as above. Its colour thresholds (changed above 45, same below 15, summed RGB) were checked on Windows only. Other GPUs and macOS's legacy GL 2.1 context are platform risks that were not checked.
- **`OwnerJourneyRegressionTest` still lists step 7 as BLOCKED** ("A realistic house from the Buildings palette", M2b.3). M2b.3 is built, and this replay now places that house. The journey's asserted BLOCKED list was left unchanged and needs a deliberate update.
- **Manually Not Verified (Headless Environment):** no person has selected the roof's vertices in the panel, dragged them, saved, or watched Play. Per the working agreement, this is deferred to the E2E run on the packaged Windows editor.
- Web: not supported. Mesh editing is desktop only (Appendix G).

## Found on the way (not changed)

- `docs/reports/m7a-6-source-preservation.md` is linked from `docs/INDEX.md`, `docs/FEATURE_TEST_MATRIX.md`, `docs/milestone-chart.md` and `docs/reports/authoring-sample-source-audit.md`, but the file does not exist. Only `docs/reports/m7a-6/cube-diorama-editor.png` is present.
- `docs/INDEX.md`'s M7a.3 line contains a mis-encoded apostrophe ("model?s").

## Next steps

- Update `OwnerJourneyRegressionTest` step 7 to place the house from the Buildings palette, which turns that step to PASS.
- Optionally offer "Refit collider to mesh" after a mesh save, for parts with authored boxes.
- The remaining R3c scenarios: M3c.7 (2D Platformer, needs MC.3) and M3c.9 (Audio, needs M8.6, MC.5 and MC.6). MC.2 (the 3D house slice) now has every dependency met.

No commit, push or destructive Git operation was performed.
