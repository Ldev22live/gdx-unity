# CC BY 3.0 sample art integration

Starting HEAD `b8bace5`. Ending HEAD `b8bace5`. The approved license decision is TD-103.

| Sample | Integrated assets | Gameplay and visual evidence |
|---|---|---|
| First-person Range 3D | Service bay and three wall panels from the sci-fi kit, plus an animated Thin Zombie observer | The observer uses its authored idle animation from outside the playable range. It adds no enemy behavior. A blue point light and the kit’s dark materials dress the range. The original target crates, camera, controller and wall colliders are intact. [Windows export frame](../assets/cc-by-frames/first-person-range-3d.png) · [Editor Play](../assets/cc-by-frames/editor-range-play.png) |
| Action RPG Courtyard 2.5D | Textured Warrior low poly training mannequin | The source lacks a rig and gameplay animation. It appears as a static display, with the original player and collision layout intact. The legacy export needed its UV layer activated before the normal map would load. [Windows export frame](../assets/cc-by-frames/action-rpg-iso-25d.png) |

Each sample has an editable on-screen credit. In the desktop game, **F1** opens a manifest-derived screen with title, creator, source, license and conversion changes. F1 or Escape returns to play. The web build puts an attribution file alongside its models and links to it from the player. [Credits capture](../assets/cc-by-frames/asset-credits.png) · [Asset provenance](../assets/THIRD_PARTY_ASSETS.md)

The four GLBs embed their textures and geometry. The asset manifest records each file’s SHA-256. All four matched. Original archives and extracted sources remain under `asset-sources/opengameart`. Running Blender in background mode with `--disable-autoexec --python-exit-code 1 --python tools/convert_attributed_opengameart.py` recreates the staged conversions.

## Verification

- Java 21 `DesktopLauncher --scene samples/<scene>.json --smoke-test=150` exited 0 for both scenes after the warrior UV fix.
- A graphics harness injected F1 into the normal desktop renderer, rendered 60 frames without errors and captured the credits screen.
- Separate template projects and standalone Windows exports were produced with `TemplateProject.create` and `GameExportCommand.run`. The installed app image was copied to a scratch template and updated with the compiled manifest and desktop renderer classes. Both executable launches used `--smoke-test=120` and exited 0. The range export contained 17 project files, including both Rosswet Mobile records. The courtyard contained 19 project files and the warrior record. The verified exports are under `build/cc-by/export-verification/games`.
- `EditorLauncher --project assets --scene samples/first-person-range-3d.json --play --smoke-test=120` exited 0 with no editor errors. Human input and drag-and-drop remain manually unverified.
- The isolated web sync generated the text credits with all three records. `npm test -- test/modelFiles.test.ts` exited 0 with two passing tests. This verifies the model loader, with desktop gameplay checks reported separately.
- A structured scene comparison confirmed that camera, player, spawn, rigid-body and wall collider configurations and transforms were preserved.

## Performance

The Java 21 desktop performance runner rendered 330 frames and recorded 300 after warm-up per scene on Intel Iris Xe. The run used `PerformanceBaseline record build/cc-by/performance-final.tsv --runs 1 samples/first-person-range-3d.json samples/action-rpg-iso-25d.json` and exited 0.

| Scene | P50 | P95 | Worst frame |
|---|---:|---:|---:|
| First-person Range | 5.61 ms | 8.36 ms | 25.86 ms |
| Action RPG Courtyard | 5.62 ms | 10.38 ms | 33.32 ms |

These single runs used the startup camera route. Sustained gameplay performance remains unmeasured.

The work preserves existing navigation, modeling and prior asset edits. No commit, push or destructive Git operation was performed.

## Gradle regression

The full `.\gradlew.bat test --continue` run with `GDXUNITY_UPDATE_AUDIT=1` exercised 2,470 tests. It exited 1 after one manifest round-trip assertion and one unrelated skip. The earlier boulder record listed an already embedded color texture as a runtime dependency. Its path now lives in the conversion record, where the manifest writer preserves it. All seven manifest tests passed when rerun against the corrected file. The [previous run](../assets/test-report.md) supplies the starting module counts.

| Module | Tests recorded | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 0 | 0 | 0 |
| editor-audio | 57 | 0 | 0 | 0 |
| editor-character | 10 | 0 | 0 | 0 |
| editor-core | 613 | 0 | 0 | 1 |
| editor-desktop | 211 | 0 | 0 | 0 |
| editor-sculpt | 57 | 0 | 0 | 0 |
| editor-sprite | 51 | 0 | 0 | 0 |
| editor-terrain | 63 | 0 | 0 | 0 |
| editor-visualscript | 94 | 0 | 0 | 0 |
| engine-animation | 32 | 0 | 0 | 0 |
| engine-audio | 233 | 0 | 0 | 0 |
| engine-character | 19 | 0 | 0 | 0 |
| engine-core | 61 | 1 | 0 | 0 |
| engine-desktop | 162 | 0 | 0 | 0 |
| engine-material | 15 | 0 | 0 | 0 |
| engine-physics | 60 | 0 | 0 | 0 |
| engine-render | 205 | 0 | 0 | 0 |
| engine-scene | 232 | 0 | 0 | 0 |
| engine-sculpt | 86 | 0 | 0 | 0 |
| engine-sprite | 44 | 0 | 0 | 0 |
| engine-terrain | 46 | 0 | 0 | 0 |
| engine-visualscript | 93 | 0 | 0 | 0 |
| engine-web | 9 | 0 | 0 | 0 |
| Total | 2470 | 1 | 0 | 1 |

Follow-up command `.\gradlew.bat :engine-core:test --offline --rerun -x :engine-core:generateAssetList` exited 0 with all 61 module tests passing. The other modules passed in the full run, with one existing skip.
