# M6.2: Post-Processing Volumes

**Date:** 2026-11-17. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart. [TD-091](../TECHNICAL_DECISIONS.md) records the design for the owner's review, because the milestone adds a component type to the scene file and changes how the 3D renderer finishes a frame.

**Why this milestone:** with M2.3 waiting on a decision record and approval, and M1b.5 and M8.1 needing a person, M6.2 was the first startable row in the chart's phase order. Its dependency, M2.1, is built, and it has no gate.

## 1. Acceptance criteria

| # | Criterion (chart row M6.2) | Status | Evidence |
|---|---|---|---|
| 1 | Bloom | **Met** | Dreamy: the lava ball and the red ball glow ([game-Dreamy.png](m6-2/game-Dreamy.png), looked at). Threshold arithmetic: `PostProcessingTest` |
| 2 | Colour grading | **Met** | Noir is black and white with hard contrast; Vivid is saturated; Cinematic is warmer, with more contrast ([Noir](m6-2/game-Noir.png), [Vivid](m6-2/game-Vivid.png), [Cinematic](m6-2/game-Cinematic.png), looked at). White balance keeps a grey's luminance: `PostProcessingTest` |
| 3 | Ambient occlusion | **Met, subtle** | Noir with occlusion only, measured against Plain after the fix in section 4. 22,283 pixels change, all inside the band of objects (x 197 to 1141, y 248 to 484), by at most 10 of 255. The base of the stone block darkens from (102, 95, 83) to (95, 89, 77). Open ground and the pond are unchanged. At the presets' settings the effect is faint (section 6) |
| 4 | Depth of field | **Met** | Dreamy focuses at 8 m: the red ball, about 8 m away, stays sharp, while the farther blocks and the near pond blur. Circle of confusion: `PostProcessingTest` |
| 5 | "As a volume component" | **Met** | Post Process Volume: global, or a box that fades in over a blend distance, with weight and priority. Reach and blending: `PostProcessVolumesTest` (9). In the editor: Add Component, Details, presets as one undo step, agent tool (`PostProcessPresetsTest`, 6) |
| 6 | Done-when: "A volume changes the frame" | **Met** | The game runtime played six scenes, 120 frames each with 0 errors. The four global looks differ from Plain across the whole frame. "Outside" has a Noir box volume 50 m away from the camera, and its frame is **pixel-identical** to Plain (PIL difference: none) |

## 2. What was built

- **`engine-scene` (`environment`):**
  - `PostProcessVolumeComponent`: placement (global, size, blend distance, weight, priority) and four effects, each with its own switch. Bloom has intensity, threshold and radius. Grading has exposure in stops, contrast, saturation, warmth, tint and a colour filter. Ambient occlusion has intensity and radius. Depth of field has focus distance, sharp range and blur. Every setter checks its range.
  - `PostProcessSettings`: the blended result.
  - `PostProcessVolumes`: finds every volume that reaches the camera and applies them lowest priority first. It does not allocate per frame once warm.
  - `PostProcessPreset`: None, Cinematic, Dreamy, Noir, Warm, Cold, Vivid.
  - `PostProcessVolumeComponentSerializer`.
- **`engine-render`:**
  - `PostProcessor` (needs GL):
    - an off-screen frame and a packed-depth frame, both with 24-bit depth;
    - bloom, ambient occlusion and the depth-of-field blur at half resolution;
    - a composite that writes the host's colour **and depth**.
  - `PostProcessMath` mirrors the shader arithmetic for tests.
  - `SceneRenderer3D`:
    - sky, scene and weather move into `renderSkySceneAndWeather`; when the camera's settings have an effect on, they are drawn into the off-screen frame;
    - a depth pass then uses `createPostDepthBatch` (gdx-gltf's depth shader with `gl_FragCoord.z`);
    - new methods: `resolvePostProcess`, `getPostProcessSettings`, `set/isPostProcessingEnabled`;
    - the post-processor and depth batch are disposed with the renderer.
- **`engine-desktop`:** the serializer is registered in `GameComponentSerializers` (41 types).
- **`editor-core`:**
  - `EnvironmentPresets`:
    - `applyScenePostProcess` changes the global volume, making a "Post Process Volume" entity when there is none;
    - `applyPostProcess` changes a named volume;
    - `describePostProcess` names the preset the global volume holds.
  - The catalog entry "Post Process Volume" (Rendering 3D, one per entity).
  - `environment_preset` gains `target: post`.
- **`editor-desktop`:**
  - `EnvironmentComponentEditorProviders.PostProcess`:
    - a Preset menu, then Global, Weight and Priority, and the four effect switches;
    - Size and Blend show only for a box, and each effect's fields only while its switch is on;
    - every change is one undo step.
  - Registered in `InspectorPanel`; `TemplateMatrix` counts the component as editable.
- **Tests:**
  - new: `PostProcessVolumesTest` (9), `PostProcessingTest` (7), `PostProcessPresetsTest` (6), `PostProcessScenesTest` (1, writes `engine-desktop/build/m6-2-post`);
  - updated: `GameComponentSerializersTest` (41 types) and `environment.EnvironmentPresetsTest` (the target error message now lists `post`).

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test --tests "*PostProcessVolumesTest"` | 1, then 0 | 1 of 9 failed first: the test's expected value was wrong. The volume blended from 0 by half way to 2, which gives 1, not 1.5. I fixed the test, then 9 of 9 |
| `./gradlew :engine-render:test --tests "*PostProcessingTest" ...` | 1, then 0 | The white-balance test failed: LibGDX's `Color.set` clamps to 1, which cut off gains above 1. The gains are now a `float[]`; then 6, later 7 of 7. `AtmosphereRenderingTest` 14, `SceneRenderer3DTest` 8 |
| `./gradlew :editor-core:test --tests "*PostProcessPresetsTest" --tests "*EnvironmentPresetsTest" --tests "*ComponentCatalogTest"` | 0 | 6 + 8 + 15 |
| `./gradlew :engine-desktop:test --tests "*PostProcessScenesTest" --tests "*GameComponentSerializersTest"` | 0 | 1 + 1 |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--project <engine-desktop/build/m6-2-post> --scene scenes/<Plain, Cinematic, Dreamy, Noir, Vivid, Outside>.json --smoke-test=120"` | 0 each | "120 of 120 frames, 0 runtime error(s)" each |
| `GDXUNITY_EDITOR_ASSISTANT=0 GDXUNITY_EDITOR_SELECT="Post Process Volume" GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--project <same> --scene scenes/Dreamy.json --smoke-test"` | 0 | 180 frames, 0 errors ([editor-Dreamy.png](m6-2/editor-Dreamy.png)) |
| `./gradlew :engine-scene:test` | 0 | **200** tests, 0 failures |
| `./gradlew :engine-render:test` | 0 | **199**, 0 failures |
| `./gradlew :engine-desktop:test` | 0 | **116**, 0 failures |
| `./gradlew :editor-core:test` | 0 | **527**, 1 skipped, 0 failures |
| `./gradlew :editor-desktop:test` | 0 | **163**, 0 failures (`ComponentConsistencyTest`, `TemplateRatingsTest`, `WebUxContractTest`, `ActionInventoryTest` included) |

Per-module before counts were not recorded at the start of this milestone. The full `./gradlew test --continue` was not run: the host stopped it twice before for low memory. Instead, the five modules this milestone touches were run one at a time. The other modules have no code changes in this milestone.

## 4. Failures found and fixed

- **Banded ambient occlusion.** The first Noir frame showed horizontal bands across the flat ground and the pond. Turning occlusion off removed them; occlusion alone showed them. Two causes:
  1. **gdx-gltf's depth shader** packs a depth computed per vertex. Across a large triangle seen in perspective, the interpolated value drifts from the true depth. The post-processing depth pass now uses the same shader with that one line replaced by `gl_FragCoord.z`. The replacement fails loudly if gdx-gltf's text changes, and the shadow pass keeps the original shader.
  2. **The occlusion shader** read depth without filtering but rebuilt each position from the exact sample coordinate. That turned a plane seen at an angle into steps, and the steps into false creases. Positions now come from the texel's centre.
  
  After both fixes the ground and pond pixels match Plain exactly.

## 5. Diff review

- **New:**
  - `engine-scene/.../environment/`: `PostProcessVolumeComponent`, `PostProcessSettings`, `PostProcessVolumes`, `PostProcessPreset`, `serialization/PostProcessVolumeComponentSerializer`;
  - `engine-render/.../`: `PostProcessor`, `PostProcessMath`;
  - the four test classes above;
  - this report and `docs/reports/m6-2/`.
- **Changed:**
  - `SceneRenderer3D`, `GameComponentSerializers` and its test;
  - `ComponentCatalog`, `EnvironmentPresets`, `EnvironmentPresetTool` and the editor-core `EnvironmentPresetsTest`;
  - `EnvironmentComponentEditorProviders`, `InspectorPanel`, `TemplateMatrix`;
  - docs: TD-091, the chart, FEATURE_TEST_MATRIX, INDEX, AI_AGENT_ARCHITECTURE_AUDIT.
- **Pre-existing uncommitted work.** Several of these files carry earlier milestones' uncommitted changes: `EnvironmentPresets`, `EnvironmentPresetTool`, the providers and the M3.9 tests are themselves uncommitted from M3.9. Those changes were kept, and only the lines listed here were added.
- **Generated, not tracked:** the scenes in `engine-desktop/build/m6-2-post` are build output. No files outside this repository were touched.

## 6. Known limitations and next steps

- **No HDR.** The effects work on the displayed 8-bit colours: bloom finds what is bright on screen, and clipped highlights stay clipped. A floating-point frame would need a GL 3 context or a float texture extension check (TD-032 kept GL 2).
- **Occlusion is screen-space and subtle.** It misses what is off screen and darkens the whole lit colour, not only ambient light. At the presets' radius (40 to 60 units) the contact darkening is faint.
- **Depth of field is a sharp and blurred mix.** Sharp edges in front of blurred ones get a soft halo (visible round the red ball in Dreamy), and there is no bokeh.
- **Translucent surfaces.** Those at least half opaque (Water, 0.55) take the focus and the occlusion. Fainter ones (Glass, 0.25) are skipped.
- **Editor view.** It is post-processed too, the grid included (seen blurred in the Dreamy editor shot). There is no View menu toggle yet; `setPostProcessingEnabled` exists for one.
- **Not covered:** 2D scenes; the web player (it does not read the component; Appendix G does not require parity); a level-palette row for post-processing.
- **Manually Not Verified (graphics):**
  - walking a camera into a box volume and watching it blend in (the blend is unit-tested; the frame was seen only inside and outside);
  - editing the fields with the mouse;
  - 2D sprites drawn into a post-processed 3D scene (the composite writes depth for them, but no such scene was looked at);
  - other GPUs and Linux or macOS: the runs were on this Windows machine only.
- **Next by phase order:**
  - M2.3 (Material Editor) needs a decision record and approval.
  - M6.1 (particles) and M6.3 (in-game UI) are gated by TD.
  - Unblocked: M6.4 navigation, M9.3 to M9.5 pipeline, and the audits MC.1, MP.1 and MT.8.

## 7. Git

No commit, push, stash, reset or other Git operation was performed.
