# OpenGameArt test report

Starting HEAD `b8bace5`. The working tree already contained the prior sample material refresh, browser classification and modeling workspace work, plus the person-supplied sources. The selected source files were moved intact into the separate source folder. Their hashes are in the [license manifest](asset-license-manifest.json).

## Focused and runtime verification

- Java 21 JUnit launcher ran `OpenGameArtModelsTest`, `SampleEnvironmentMaterialsTest`, `PlatformerChaseProfileOnJoltTest` and `ActionRpgValleyProfileOnJoltTest` against the repository. All 15 tests passed, including real Jolt course traversal, camera, bunny jump and landing, and the original boulder collision. The GLB tests cover skin joints, clip names, embedded textures, the mirrored fox geometry and the wildlife normal maps.
- Java 21 DesktopLauncher with `--scene samples/<scene>.json --smoke-test=180` exited 0 for each of the three updated scenes. [Platformer](opengameart-frames/platformer-profile-3d.png) ? [RPG Valley](opengameart-frames/action-rpg-valley-3d.png) ? [RPG courtyard](opengameart-frames/action-rpg-iso-25d.png)
- EditorLauncher loaded the existing platformer sample for 120 frames and entered Play for a separate 180-frame smoke run. Both exited 0. The model Details view reported 30 bones and six clips. [Editor Details](opengameart-frames/platformer-editor.png) ? [Editor Play](opengameart-frames/platformer-editor-play.png)
- `TemplateProject.create` and `GameExportCommand.run` created separate projects and standalone Windows builds for the three upgraded samples using the installed app image. Each exported executable passed `--smoke-test=120`, with 18, 10 and 18 project files respectively. The working sample assets were the inputs.
- The inspected frames show the bunny, textured pavers, boulders and textured wildlife. The paver stretches on vertical box faces. The current RPG player remains a box and the terrain uses the existing painted heightfield.
- **Manually Not Verified (Headless Environment)** applies to human input, mouse-driven drag-and-drop, save and edit interaction, and listening. Runtime and editor frame captures and automated Jolt input supply separate evidence.

## Full regression

`.\gradlew.bat test --continue` was launched before edits. Its long run overlapped subsequent working-tree changes, so it did not provide a clean before snapshot. It exited 1, as editor audit tests read the new scenes before generated tables and fixture expectations were updated, while the engine-desktop test compiler used the earlier Gradle configuration. Loose unrelated source downloads added in `assets/characters` independently triggered packaging and provenance tests.

The local Assets drawer rendered for 180 frames with zero editor errors and showed the imported model path and thumbnail. [Content Browser](opengameart-frames/platformer-assets.png)

Babylon model-loader check used `npm test -- test/modelFiles.test.ts` from `engine-babylon`. The sandbox blocked Vite from spawning a worker on the first attempt. The approved rerun exited 0 with both tests passing. This checks model loading in the browser backend, separate from Java sample gameplay.

The isolated JUnit asset and template audit passed all 50 tests with exit 0. The verification copy excluded the unrelated loose character downloads and retained the repository test sources. The generated asset support, inventory, and template tables came from that successful audit. The normal worktree still contains those loose downloads.

### Post-integration Gradle run

`GDXUNITY_UPDATE_AUDIT=1` with `.\gradlew.bat test --continue` exited 1. The engine-desktop suite, including the new tests, passed all 159 cases. The only failures were two editor packaging audits for unrelated loose downloads under `assets/characters`. `AssetSupportMatrixTest` stopped at a source `.blend` in `guerrero_low_poly`. `ShippedSpritesProvenanceTest` listed 16 unrecorded images from `guerrero_low_poly`, `new_thin_zombie` and `sci_map_obj`. Those files were left untouched. The five model support cases that require the matrix initialization ran in the separately verified audit copy.

| Module | Tests recorded | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 0 | 0 | 0 |
| editor-audio | 57 | 0 | 0 | 0 |
| editor-character | 10 | 0 | 0 | 0 |
| editor-core | 613 | 0 | 0 | 1 |
| editor-desktop | 206 | 2 | 0 | 0 |
| editor-sculpt | 57 | 0 | 0 | 0 |
| editor-sprite | 51 | 0 | 0 | 0 |
| editor-terrain | 63 | 0 | 0 | 0 |
| editor-visualscript | 94 | 0 | 0 | 0 |
| engine-animation | 32 | 0 | 0 | 0 |
| engine-audio | 233 | 0 | 0 | 0 |
| engine-character | 19 | 0 | 0 | 0 |
| engine-core | 60 | 0 | 0 | 0 |
| engine-desktop | 159 | 0 | 0 | 0 |
| engine-material | 15 | 0 | 0 | 0 |
| engine-physics | 60 | 0 | 0 | 0 |
| engine-render | 205 | 0 | 0 | 0 |
| engine-scene | 232 | 0 | 0 | 0 |
| engine-sculpt | 86 | 0 | 0 | 0 |
| engine-sprite | 44 | 0 | 0 | 0 |
| engine-terrain | 46 | 0 | 0 | 0 |
| engine-visualscript | 93 | 0 | 0 | 0 |
| engine-web | 9 | 0 | 0 | 0 |
| Total | 2461 | 2 | 0 | 1 |

A structured comparison against the starting revision confirmed that the scene genre, camera, terrain, spawn, character controller and blocking collider configuration were preserved in all three scenes. The [prior pass report](sample-game-verification-report.md) provides the last clean before counts. Ending HEAD is `b8bace5`. No commit, push or destructive Git operation was performed.

A Java 21 LWJGL capture harness injected Space into the unchanged Platformer input mapping and observed the real GLB Jump clip advancing. After initializing LibGDX natives in the harness, the 78-frame run exited 0 with zero runtime errors. [Airborne frame](opengameart-frames/platformer-jump.png)
