# MC.2 3D house slice

**Date:** 2026-11-24. **Milestone:** MC.2 (Appendix C3), milestone chart §8. **Done-when:** "A user changes a roof or wall entirely in ForgeRoom, sees it in the world and in gameplay, and undoes, saves, reloads and exports without loss."

The slice is the CC0 Kenney house from the Buildings palette, its roof changed in 3D Modeling. It ships with the original source, a manifest, a derived editable asset and a deterministic operation script that rebuilds that asset byte for byte. The three edit scopes are now distinct. **Edit Shared Prefab**, the one that was missing, is built: it warns before changing every instance.

## Acceptance criteria

| Criterion (chart row and Appendix C3) | Result | Evidence |
|---|---|---|
| One editable house: a verified CC0 Kenney modular structure | Met | `prefabs/buildings/CC0 House.prefab` (Kenney Building Kit, CC0-1.0, licence manifest TD-094), placed from the Buildings palette that every new 3D project gets. |
| A CC0 Blender demo object for conversion tests | Met, by M7a.6 | Cube Diorama wall: `fixtures/mesh-sources/models/thirdparty/blender/cube-diorama-wall.glb`, `MeshFixtureImportTest` (explicit conversion, procedural material refused). Not repeated here. |
| World, Edit 3D Model, change the roof, World, Action RPG + 3D Play, Stop, reopen, export | Met | `HouseSliceTest`: new Empty 3D project, level, player and spawn point, two houses, Edit 3D Model on each, the roof changed by the slice's script, Back to World, Play (both roofs in the runtime copy), Stop (scene JSON unchanged), save, reopen with prefab sync, Play, export. The export holds both derived models byte for byte, and its files play 2 s in `GameSession` with 0 errors. |
| Edit Instance, Edit Shared Prefab and Create Unique Mesh kept distinct | Met | **Create Unique Mesh** (existing `UNIQUE`): one house's roof, recorded as that instance's override. The prefab file is byte-identical afterwards. **Edit Shared Prefab** (new `PREFAB`): the roof is bound in the prefab itself. The other house changes, and a house placed afterwards has the new roof. **Edit Instance**: an instance's own override wins over the shared prefab. The unique house keeps its roof when the prefab changes. The 3D Modeling panel names the three scopes. |
| A warning before changing all instances | Met | `PREFAB` without confirmation throws, writes no file and binds nothing. Its message names the prefab, "All 2 instance(s) in this scene", and says other scenes change when opened (`MeshSaveSessionTest`, `HouseSliceTest`). In the panel, Save opens an **Edit shared prefab** dialog with that text. **Cancel** changes nothing. **Apply to all** saves (`HouseSliceGraphicsSmoke`, Windows GL). |
| Shipped with the original source, a manifest, a derived editable asset and a deterministic operation script and test | Met | `fixtures/mesh-sources/derived/kenney-house-roof/`: `operations.json` (raise the roof's 18 top vertices 0.4 units), `model.gltf` (the derived roof, texture embedded, source path and SHA-256 recorded), and `manifest.json` (both CC0 inputs with licence records and hashes, and hashes of both outputs). The original is the shipped `assets/thirdparty/kenney/building-kit/Models/GLB format/roof-flat-corner.glb`. `MeshOperationScriptTest` rebuilds `model.gltf` byte for byte from the original and the script. `HouseSliceTest` checks that the editor's own save of the same edit gives the same bytes. |
| Undo without loss | Met | Undo of the shared edit restores both houses and the prefab file byte for byte. Redo re-applies it. Binding and sharing are one undo step (`PrefabServiceTest`, `MeshSaveSessionTest`, `HouseSliceTest`, the GL smoke). |
| Originals never overwritten | Met | The repository's prefab and roof model, and the project's copy of the roof model, are byte-identical at the end of `HouseSliceTest`. Update on the provider original is refused (M3c.6). |

**Seen in the world and in gameplay.** In tests: the World and Play bindings, plus the drawn model instances in `HouseSliceGraphicsSmoke`. The visible raised roof in World, Play, after reopen and in the export is in the [M3c.6 frames](m3c-6-house-edit-replay.md) for the same edit. The [MC.2 frame](mc-2/shared-prefab.png) shows both houses after Apply to all. That smoke extrudes one small face through the panel, which is not visible at that distance, so the frame proves only that both houses are drawn.

## What was built

- `PrefabService.applyComponent(entity, component[, change])` (`editor-core`) writes one component of one instance part into its prefab and updates every instance in the scene. Other overrides stay overrides, and other instances keep their own overrides. It is one undo step that also restores the prefab file. The optional change runs first inside the same step, on a fresh copy of the component. Scene snapshots keep component objects rather than their values, so an in-place change would not undo. The first version had that bug, and `shouldMakeAChangeAndApplyItInOneUndoStep` caught it. `PrefabService.countInstances` counts the instances for the warning.
- `MeshSaveSession.Mode.PREFAB` "Edit shared prefab (every instance)" and `prefabWarning` (`editor-sculpt`). The mode needs confirmation and refuses a part that is not in a prefab instance.
- `MeshOperationScript` (`editor-sculpt`) reads, validates and replays a JSON operation list: on fresh drafts, on an open session's drafts, or to derived bytes. It also has a command line that never overwrites. `MeshSaveSession.encode` is now package-visible so the script writes exactly what a save writes.
- 3D Modeling panel (`ModelingNoticePanel`): the PREFAB save shows a confirmation dialog, and the lines name Edit Instance, Edit Shared Prefab and Create Unique Mesh.
- Test support: `HouseReplaySupport` holds the helpers M3c.6's test and smoke had duplicated. They now use it.

## Design choices (decided per the working agreement)

- **Scope names.** Create Unique Mesh is the M7a.4 unique instance mesh. Edit Shared Prefab binds a new derived model in the prefab. Edit Instance is a placed house's own overrides (World placement and Details), and it never writes a mesh. Edit Shared Prefab always derives a new model instead of rewriting the shared file, so the Kenney original stays untouched. Defaulting to a new derived asset is what Appendix C3 asks for.
- **A fixture, not a new gallery sample.** The house is already in every new 3D project's Buildings palette. A new gallery sample would also need entries in the sample inventory, migration plan, template audit and backend matrix. The slice ships beside M7a.6's fixtures. MC.7 records the 3D migration slice as blocked on R7a, and that record is not updated here.
- **Explicit vertex indices in the script.** The script lists indices rather than "the top" so that a reviewer sees exactly what changes and a changed source fails loudly.

## Files

| File | Change |
|---|---|
| `editor-core/.../prefab/PrefabService.java` | `applyComponent` (two forms), `countInstances`, `detachedCopy` |
| `editor-core/.../prefab/PrefabServiceTest.java` | +2 tests |
| `editor-sculpt/.../MeshSaveSession.java` | `Mode.PREFAB`, `prefabWarning`, package-visible `encode` |
| `editor-sculpt/.../MeshOperationScript.java` (new) | The operation script |
| `editor-sculpt/.../MeshSaveSessionTest.java` | +1 test |
| `editor-sculpt/.../MeshOperationScriptTest.java` (new) | 4 tests |
| `editor-desktop/.../ui/viewport/ModelingNoticePanel.java` | Shared-prefab confirmation; scope lines |
| `editor-desktop/src/test/.../HouseSliceTest.java` (new) | The slice journey, 1 test |
| `editor-desktop/src/test/.../HouseSliceGraphicsSmoke.java` (new) | Panel and GL check, run by hand |
| `editor-desktop/src/test/.../HouseReplaySupport.java` (new), `HouseEditReplayTest.java`, `HouseEditReplayGraphicsSmoke.java` | Shared helpers (M3c.6 files from earlier today) |
| `fixtures/mesh-sources/derived/kenney-house-roof/` (new) | `operations.json`, `model.gltf`, `manifest.json` |
| `docs/reports/mc-2-house-slice.md`, `docs/reports/mc-2/shared-prefab.png` (new); `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`, `docs/milestone-chart.md` | Report and status |

## Verification

Starting and ending HEAD: `b8bace5`. Before this milestone the working tree held the earlier uncommitted work (R7a, M3c.6, samples, documentation). Only the files above were touched.

1. `.\gradlew.bat :editor-core:test --tests "*PrefabServiceTest" :editor-sculpt:test --tests "*MeshSaveSessionTest"`: exit 0. 14 and 5 tests, 0 failures. Before the copy fix, `shouldMakeAChangeAndApplyItInOneUndoStep` failed ("undo takes back the change too", expected white, was green).
2. `.\gradlew.bat :editor-sculpt:test --tests "*MeshOperationScriptTest"`: exit 0, 4 tests, 0 failures.
3. `.\gradlew.bat :editor-desktop:test --tests "*HouseSliceTest" --tests "*HouseEditReplayTest"`: exit 0, 1 and 2 tests, 0 failures.
4. The derived model was produced by `MeshOperationScript` itself (`main`, asset root a scratch copy of `assets/thirdparty` and `assets/licenses`, the slice's `operations.json`), run through a scratch Gradle init script outside the repository.
5. `HouseSliceGraphicsSmoke` on Windows 11 used the same init-script runner as M3c.6 with `-DsmokeMain=HouseSliceGraphicsSmoke`. Exit 0. Output: `House slice: Edit shared prefab warned "Edit shared prefab prefabs/buildings/CC0 House.prefab? All 2 instance(s) in this scene change their Roof NE to the new model. Other scenes that place this prefab change when next opened."; Cancel changed nothing; Apply to all bound models/edited/Roof_NE-edited.gltf to both houses and the prefab, both drawn with it; one undo restored all`.
6. Module suites, `--continue --offline -x generateAssetList`, one at a time because a whole-suite run had been stopped earlier today for low system memory:

| Module | Before (M7a.5 report) | After | Failures or errors | Skips |
|---|---:|---:|---:|---:|
| editor-core | 613 | 615 | 0 | 1 |
| editor-sculpt | 63 | 68 | 0 | 0 |
| editor-desktop | 211 | **not completed** | n/a | n/a |

Both completed runs exited 0. The editor-sculpt count includes M7a.6's tests, if any were added there, so the "before" column is the last recorded run, not a fresh baseline. The editor-desktop module run was stopped by the host for low system memory before it wrote any results, and it was not restarted. Its new and changed tests (`HouseSliceTest` 1, `HouseEditReplayTest` 2) passed in the targeted run in step 3. The rest of that module, including the `ModelingNoticePanel` change, has no completed run.

The modules not listed were not run for this milestone. No code they depend on changed, apart from the `editor-core` and `editor-sculpt` additions listed above. A whole-suite run is still owed (AGENTS.md §10 item 2), for this milestone and for M3c.6.

## Known limitations

- **Instances in other scenes** take a shared-prefab change only when their scene is next opened. The warning says so, but it counts only the open scene's instances.
- **Collision:** the roof parts' authored Box Collider 3D keeps its size, as in M3c.6.
- **Edit Instance is not a mesh operation.** It is the existing World and Details override path. The panel says so, and it has no separate button.
- **The panel's own drafts were edited with a single face extrusion** in the GL smoke. The full roof raise used the operation script on a session's drafts, which are the objects the panel edits, not mouse clicks. **Manually Not Verified (Headless Environment):** a person choosing Edit shared prefab, reading the dialog and pressing Apply to all, deferred to the E2E run on the packaged editor.
- The graphics smokes are not in `gradlew test`. They need a display and were run on Windows only.
- Web: not supported (mesh editing is desktop only, Appendix G).

## Next steps

- MC.4 (2.5D hybrid sample) needs MC.3 (2D Studio slice, TD gate). M3c.7 needs MC.3 too. MC.3 is the next unblocked milestone in this track, but it needs a TD first (OpenRaster or a native layered format).
- Optional: make the slice a gallery sample once MC.7's 3D migration slice is planned, and update `OwnerJourneyRegressionTest` step 7 (Buildings palette), as noted in M3c.6.

No commit, push or destructive Git operation was performed.
