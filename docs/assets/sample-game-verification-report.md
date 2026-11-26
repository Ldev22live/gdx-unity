# Sample game verification report

Starting HEAD: `b8bace5`. The initial working tree had edits to the decision log, feature matrix, index, milestone chart, editor navigation, modeling UI, editable mesh and sculpt code, plus their tests and reports. Existing changes were preserved.

## Baseline Gradle test run

`.\gradlew.bat test --continue` first exited 1 because the sandbox denied the wrapper cache lock. The approved rerun exited 1 after executing tests. `BackendSupportMatrixTest` reported that its Babylon matrix lists PrefabInstance as unsupported while SceneBuilder lists it as supported. This occurred before the sample material changes and had no relation to genre queries.

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 0 | 0 | 0 |
| editor-audio | 57 | 0 | 0 | 0 |
| editor-character | 10 | 0 | 0 | 0 |
| editor-core | 611 | 0 | 0 | 1 |
| editor-desktop | 211 | 0 | 0 | 0 |
| editor-sculpt | 57 | 0 | 0 | 0 |
| editor-sprite | 51 | 0 | 0 | 0 |
| editor-terrain | 63 | 0 | 0 | 0 |
| editor-visualscript | 94 | 0 | 0 | 0 |
| engine-animation | 32 | 0 | 0 | 0 |
| engine-audio | 233 | 0 | 0 | 0 |
| engine-character | 19 | 0 | 0 | 0 |
| engine-core | 60 | 0 | 0 | 0 |
| engine-desktop | 153 | 1 | 0 | 0 |
| engine-material | 15 | 0 | 0 | 0 |
| engine-physics | 60 | 0 | 0 | 0 |
| engine-render | 205 | 0 | 0 | 0 |
| engine-scene | 232 | 0 | 0 | 0 |
| engine-sculpt | 86 | 0 | 0 | 0 |
| engine-sprite | 44 | 0 | 0 | 0 |
| engine-terrain | 46 | 0 | 0 | 0 |
| engine-visualscript | 93 | 0 | 0 | 0 |
| engine-web | 9 | 0 | 0 | 0 |
| All | 2,458 | 1 | 0 | 1 |

For baseline graphics timings, see the [performance report](sample-game-performance-report.md).

## Inspected runtime frames

Windows desktop, Java 21, 1280 ? 720. Each scene rendered 120 startup frames through DesktopLauncher and exited 0 with zero runtime errors, both before and after. Before scenes were copied from `b8bace5` into a temporary project alongside the two original terrain files. The first temporary project capture exited 1 because the shell added a UTF-8 BOM. After rewriting the copies as UTF-8 without a BOM, all eight captures ran. The working scenes were used directly for the after frames.

| Sample | Before | After | What is visible |
|---|---|---|---|
| Action RPG Valley | [Frame](screenshots/action-rpg-valley-3d-before.png) | [Frame](screenshots/action-rpg-valley-3d-after.png) | Clear blue sky and textured stone and wood obstacles. The player is a box and the heightfield uses painted colors. |
| Action RPG Isometric | [Frame](screenshots/action-rpg-iso-25d-before.png) | [Frame](screenshots/action-rpg-iso-25d-after.png) | Rock and grass pattern on the courtyard. The long wall boxes stretch the brick UVs, which should be addressed with properly authored wall meshes. |
| Terrain Platformer 2.5D | [Frame](screenshots/platformer-profile-terrain-25d-before.png) | [Frame](screenshots/platformer-profile-terrain-25d-after.png) | Brick wall, wood platforms and a green terrain strip. The wall uses the primitive's original face UVs. |
| Platformer 3D | [Frame](screenshots/platformer-profile-3d-before.png) | [Frame](screenshots/platformer-profile-3d-after.png) | Stone detail on the route and the existing bright finish flag. Platform sides still have their box silhouette. |

Manual keyboard, audio and editor workflow verification remains open. Runtime captures cover visible startup presentation. Automated input tests cover movement, collision and camera behaviors separately.

## Final automated checks

`GDXUNITY_UPDATE_AUDIT=1` was set for `.\gradlew.bat test --continue :engine-desktop:perfBaseline '-PperfScenes=samples/platformer-2d.json,samples/platformer-profile-terrain-25d.json,samples/action-rpg-valley-3d.json' -PperfRuns=1 '-PperfOut=build/perf/sample-asset-after.tsv'`. Exit 0. XML reports recorded 2,462 tests, 0 failures, 0 errors and 1 skip. The editor-core count was 613 and the engine-desktop count was 155. All other module counts match the baseline table.

- `AssetBrowserFilterModelTest` passed all 11 cases, including combined genre facets, a missing file and malformed JSON.
- `SampleEnvironmentMaterialsTest` passed both cases covering PBR material references, texture dependencies and the Valley atmosphere. Existing Jolt movement, collision, camera and template tests ran in the suite.
- The Babylon PrefabInstance documentation now describes the metadata that the existing player accepts. All four `BackendSupportMatrixTest` cases passed. No web runtime code was changed.
- The generated asset support, sample inventory and editability tables reflect the authored changes. Nine reused texture files matched their original manifest checksums.
- A JSON comparison against `b8bace5` found exactly 21 material assignments and one atmosphere component in the four sample scenes. Controller configuration, input, collision, entity transforms and gameplay scripts had no other scene edits.

Ending HEAD: `b8bace5`. No commit, push or destructive Git operation was performed.
