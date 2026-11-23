# M3.9: Sky, Fog, Weather and Water Bodies

**Date:** 2026-11-17. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed).
**Decision:** [TD-090](../TECHNICAL_DECISIONS.md#td-090-sky-and-weather-water-bodies-and-a-24-bit-depth-buffer-roadmap-m39), recorded for the owner's review (the chart lists no approval gate).

## 1. Acceptance criteria

| # | Criterion (chart row M3.9) | Status | Evidence |
|---|---|---|---|
| 1 | Water body component | **Met** | `WaterBodyComponent` (size, colour and opacity, roughness, waves), drawn translucent and wave-animated; `AtmosphereRenderingTest` (surface, normals, waves); game-runtime screenshots |
| 2 | Presets | **Met** | `AtmospherePreset` (Clear Day, Overcast, Foggy, Sunset, Night, Rain, Snow) and `WaterPreset` (Lake, Ocean, Swamp, Tropical, Icy); Details Preset menus, level palette Sky row, agent tool `environment_preset` |
| 3 | Fog | **Met** | Linear distance fog through gdx-gltf's PBR fog; `shouldSetTheFogColourAndRangeFromTheSkyAndWeather`; Foggy screenshot |
| 4 | Sky and weather (milestone title) | **Met** | Sky gradient, rain and snow particles, sunlight multiplier; Rain and Snow screenshots |
| 5 | Done-when: "a preset applies and persists" | **Met in tests** | editor-core `EnvironmentPresetsTest` (one undo step, save and reopen gives the same file); `WeatherScenesTest` (written with the game's serializer list, read back by the game runtime's loader with the same presets, 30 frames each). **Not done by a person with the mouse** |

## 2. What was built

- **`engine-scene`**, new package `environment`:
  - `AtmosphereComponent` ("Sky and Weather") and `WaterBodyComponent`;
  - `AtmospherePreset` and `WaterPreset`;
  - their serializers, registered in `GameComponentSerializers` (used by the game and the editor).
- **`engine-render`:**
  - `SkyGradient`, `AtmosphereRenderer` (sky and weather drawing), `WeatherParticles`, `WaterSurfaceBuilder`, `WaterBodyGpu`.
  - `SceneRenderer3D` uses them: sky after the shadow pass, PBR fog, sunlight on directional lights, water with the scene but out of the shadow pass, weather last.
  - New `SceneRenderer3D` methods: `findAtmosphere`, `collectWaterBodies`, `getAtmosphere`, `getWeatherParticles`, `setSkyOverlay`.
- **`editor-core`:**
  - `environment/EnvironmentPresets`: every preset change goes through it as one undo step.
  - Catalog entries "Sky and Weather" (Lighting) and "Water Body" (Rendering 3D).
  - Agent tool `EnvironmentPresetTool` (`environment_preset`), registered in `DefaultAgentTools`.
- **`editor-desktop`:**
  - `EnvironmentComponentEditorProviders`: a Preset menu plus every field, each field its own undo step. `InspectorFieldRows.choiceRow` is new for the menu.
  - The level palette's Sky row: a preset menu showing None, Custom or the preset. It is refreshed after undo steps and scene changes, not every frame.
  - The 3D grid is drawn over the sky (`SceneEditorWorkspace`).
- **`editor-terrain`:** `LevelSession.getEditorState()`.
- **Audit:** `TemplateMatrix` counts both components as editable lighting and environment.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test --tests "*EnvironmentPresetsTest"` | 0 | 9 of 9 |
| `./gradlew :engine-render:test --tests "*AtmosphereRenderingTest" --tests "*SceneRenderer3DTest"` | 0 | 14 + 8 |
| `./gradlew :editor-core:test --tests "*environment.EnvironmentPresetsTest" --tests "*ComponentCatalogTest"` | 1 | 8 of 8 new tests pass. 1 failure: `ComponentCatalogTest` pinned the Lighting list, which now has "Sky and Weather". I updated the expected list; **the fixed test has not been run since** (see below) |
| `./gradlew :engine-desktop:test --tests "*WeatherScenesTest" --tests "*GameComponentSerializersTest"` | 0 | 1 + 1 (40 serializer types) |
| `./gradlew :editor-desktop:compileJava :editor-desktop:compileTestJava :engine-desktop:compileTestJava` | 0 | compiles |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--project <engine-desktop/build/m3-9-weather> --scene scenes/<Sunset, Rain, Snow, Foggy>.json --smoke-test=150"` | 0 each | "150 of 150 frames, 0 runtime error(s)" each |
| `GDXUNITY_EDITOR_ASSISTANT=0 GDXUNITY_EDITOR_SELECT="Sky and Weather" ... :editor-desktop:run --args="--project <same> --scene scenes/Rain.json --smoke-test"`, and `GDXUNITY_EDITOR_SELECT=Lake GDXUNITY_EDITOR_WINDOW=1024x640` on Sunset | 0 each | 180 frames, 0 errors each |
| `./gradlew :editor-core:test --tests "*ComponentCatalogTest"` (after the fix, 2026-11-17) | 0 | 15 of 15 |
| `./gradlew :editor-desktop:test --tests "*ComponentConsistencyTest"` | 0 | 5 of 5: save and load, duplicate, undo and Play isolation over every addable component, now including both new ones |
| `./gradlew test --continue` | none | **Stopped by the host because the machine ran low on memory.** Before the stop, editor-animation (17), editor-audio (57) and editor-character (10) passed. No full-suite counts were recorded for this milestone |

**Not run, so not claimed:**
- the whole suite after the changes;
- the editor-desktop audit tests (`TemplateRatingsTest`, `WebUxContractTest`, `ActionInventoryTest`).

The previous full counts are in the M3.8 report (engine-render 178, engine-desktop 114, editor-desktop 163). Run `./gradlew test --continue` when memory allows.

**Screenshots** (each one looked at):
- [m3-9/game-sunset.png](m3-9/game-sunset.png): violet-to-orange sky; a Lake water body whose shore follows the valley floor.
- [m3-9/game-rain.png](m3-9/game-rain.png): grey sky, rain streaks, dimmed sun, Ocean water.
- [m3-9/game-snow.png](m3-9/game-snow.png): white sky, snow flakes, Icy water.
- [m3-9/game-foggy.png](m3-9/game-foggy.png): the far valley lost in fog.
- [m3-9/editor-sky-details.png](m3-9/editor-sky-details.png): editor at 1920x991, Sky and Weather in Details (Preset: Rain), rain and fog in the view.
- [m3-9/editor-water-details-1024.png](m3-9/editor-water-details-1024.png): editor at 1024x640, Water Body in Details (Preset: Lake). These were taken before I shortened the wave labels, which were clipped at that width; the shortened labels are not yet seen in a screenshot.

## 4. Failures found and fixed

- **Water drew in bands against the ground.** Screenshots showed saw-tooth edges. I tested in turn: level of detail off, a raised small lake, a top view, the sky off, and the material's depth attribute off. Under 60 % of the lake the ground stands above the water, yet the top view showed the whole rectangle.
  - Cause: both desktop apps used LibGDX's default **16-bit depth buffer**. With 1 to 50,000 near and far planes, that resolves only about 250 units at 4 km.
  - Fix: `DesktopLauncher` asks for 24 depth bits, and `SceneViewportWidget` builds its frame buffer with `GL_DEPTH_COMPONENT24`. The shoreline is now correct.
  - This bug predates M3.9 and is very likely the "shimmer" TD-089 recorded for terrain water. That has not been re-checked.
- The sky covered the editor's ground grid. The grid is now drawn over the sky through `setSkyOverlay`.
- Details labels were clipped at 1024x640. I shortened them (Zenith, Horizon, Fog colour, Colour, Waves, Spacing, Speed).
- The test fixes listed in section 3.

## 5. Diff review

- **New files:**
  - `engine-scene/.../environment/`: 4 classes, 2 serializers, 1 test;
  - `engine-render`: 5 classes, 1 test;
  - `editor-core`: `environment/EnvironmentPresets`, `agent/tools/EnvironmentPresetTool`, 1 test;
  - `editor-desktop`: `EnvironmentComponentEditorProviders`;
  - `engine-desktop`: `WeatherScenesTest`;
  - this report and `docs/reports/m3-9/`.
- **Changed by this milestone:**
  - `SceneRenderer3D`, `GameComponentSerializers` and its test, `DesktopLauncher`;
  - `ComponentCatalog` and its test, `DefaultAgentTools`;
  - `InspectorPanel`, `InspectorFieldRows`, `LevelPalette`, `SceneViewportWidget`, `SceneEditorWorkspace`, `TemplateMatrix`;
  - `LevelSession`;
  - the docs: TECHNICAL_DECISIONS, milestone-chart, FEATURE_TEST_MATRIX, INDEX, AI_AGENT_ARCHITECTURE_AUDIT.
- **Pre-existing uncommitted changes:** many of the changed files already held uncommitted work from earlier milestones (M3.3 to M3.8). That work was left in place, and these edits were added on top.
- The scenes for the screenshots are written by `WeatherScenesTest` to `engine-desktop/build/m3-9-weather`, a build folder. No sample or shipped asset was changed. No files outside this repository were touched.

## 6. Known limitations and next steps

- No clouds, sun disc, stars or time of day. Sky Light is separate and presets do not change it.
- Weather is visual only: nothing gets wet, there is no sound, and rain falls through roofs.
- Water:
  - does not collide, reflect, refract or blend into the shore;
  - through thick fog, the far end of a translucent lake shows faintly lighter than the fog around it.
- Fog is linear distance fog only.
- The editor's view animates the weather and waves while you edit.
- The web player draws neither component.
- **Platform risk:** the 24-bit depth requests (window and `GL_DEPTH_COMPONENT24` render buffer) were checked on Windows only. Desktop GL drivers on Linux and macOS (including macOS's legacy GL 2.1 context) normally provide them, but this is not verified. Check it on the packaged builds.
- No shipped sample uses them yet. M3.10, which converts the landscape samples, is the natural place to add them.
- **Manual check for a person:** in the editor, Add Component > Sky and Weather, then pick each preset from Details and from the level palette's Sky row, undo each one, save, reopen, then press Play.
- **Next in R3:** M3.6 (scatter) waits on M2b.4, M3.10 needs M3.6, and M3.11 is the web player.

## 7. Git

No commit, push, stash, reset or other Git operation was performed.
