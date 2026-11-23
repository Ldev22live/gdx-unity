# Phase 13-B Report — Shadows, Sky Light, Tone Mapping

**Date:** 2026-10-30 · **Branch:** `phase-12-3d-foundation` · **HEAD:** `1dc0a11` at start and end (no commits)
**Plan:** [phase-13b-lighting.md](../milestones/phase-13b-lighting.md) · **Decision:** TD-032 (approved by "go")
**Status:** Implemented and integrated. **Not validated:** every visual item below is
**Manually Not Verified (Headless Environment)**. The agent could not see the screen and did not run the editor.

## Starting state

The working tree already had 46 uncommitted entries from Phase 17 and earlier, left by the user. They were left in
place. Files from that set that this phase also edited are listed under "Diff review".

## Spike findings (step 0)

These come from reading the gdx-gltf 2.3.0 jar (`javap` and its GLSL sources):

- The jar has `DirectionalShadowLight`, `PBRDepthShaderProvider`, `IBLBuilder`, `brdfLUT.png` and `CascadeShadowMap`.
- gdx-gltf has **no tone mapping or exposure**. Its fragment shader adds emissive and then applies gamma.
- The PBR shader shadows directional light 0 only.
- Point-light falloff `1/(1+d²)` matches LibGDX's default shader. The PBR diffuse term is divided by π.
- `textureLod` (needed for rough reflections) is used only on GL 3 or with `EXT_shader_texture_lod`, and only when the
  specular cubemap is mip-mapped. The editor does not request GL 3.
- LibGDX 1.14.2 frame buffers do not nest, and the editor draws the scene into its own frame buffer.
- `Color.set(r, g, b, a)` clamps to 1. A unit test caught this: light colors above white were being clamped.

## What was built

| Area | Change |
|---|---|
| engine-scene | `DirectionalLightComponent.castShadows` defaults to true, and older scenes load with it on. New `SkyLightComponent` and `ToneMappingComponent`, each with a serializer. |
| engine-render | `SceneRenderer3D` now draws primitives and models with one PBR batch, uses sRGB→linear colors and π-scaled direct light, and reuses light objects and attributes every frame. The rest of the work is in new classes: `ShadowBounds` (texel-snapped shadow volume), `RenderQuality3D`, `ToneMappingShader`, `SkyLightMaps` / `BakedSkyLightMaps`. The shadow pass, shadow-light creation and sky baking save and restore the host's frame buffer and viewport. `dispose()` now collects failures per TD-008 instead of swallowing them. |
| editor-core | Create → Sky Light and Create → Tone Mapping. Window → Shadows and Lighting Quality: Low/Medium/High (`registerLightingActions`). Catalog entries for both new components. |
| editor-desktop | Details panels: Cast Shadows, a Sky Light panel, a Tone Mapping panel. Serializers registered. The startup scene now has a "Sky Light" entity: sky intensity 0.5 plus ACES tone mapping. |

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Box on plane casts a visible shadow in the editor and play mode | Manually Not Verified (Headless Environment) | Shadow-caster choice and volume fitting: `SceneRenderer3DTest`, `ShadowBoundsTest` |
| 2 | Turning `castShadows` off, disabling the light, or undo removes the shadow | Partly met | Caster selection follows the flag and enabled state (`shouldChooseFirstShadowCastingLightAndPutItFirst`). The edit goes through `ModifyComponentPropertyCommand`. On-screen result not verified. |
| 3 | Sky light tints unlit sides and is reflected by a metallic model | Manually Not Verified (Headless Environment) | Bakes only when the sky changes; intensity acts as a multiplier (`shouldBakeSkyLightOnlyWhenColorsOrQualityChange`) |
| 4 | Exposure changes brightness; tone mapping prevents clipping | Partly met | Exposure scaling (`shouldScaleDirectLightByExposureAndPbrFactorInLinearSpace`). Shader insertion position (`ToneMappingShaderTest`). Shader compile and look not verified. |
| 5 | Primitives and models look consistent | Manually Not Verified (Headless Environment) | Same batch and shader by construction |
| 6 | Old scenes load unchanged, with shadows on | Met | `shouldCastShadowsWhenSceneWasSavedBeforeTheFlagExisted` |
| 7 | No per-frame allocation in light sync / shadow pass | Partly met | Light objects, attributes, shadow bounds and the GL query buffer are reused (`shouldReuseLightObjectsAcrossUpdates`). Component getters (`getColor()`, `getDirection()`, `getWorldPosition()`) and the existing `collectPrimitives` still allocate. |
| 8 | Tests green, counts recorded, docs updated | Met | See below |

## Commands and results

| When | Command | Exit | Tests |
|---|---|---|---|
| Before | `./gradlew test --continue` | 0 | 464, 0 failures: editor-animation 13, editor-core 257, editor-desktop 22, editor-visualscript 4, engine-animation 32, engine-core 27, engine-render 45, engine-scene 55, engine-visualscript 9 |
| After | `./gradlew test --continue` | 0 | 495, 0 failures, 0 skipped: editor-animation 13, editor-core **261**, editor-desktop **23**, editor-visualscript 4, engine-animation 32, engine-core 27, engine-render **63**, engine-scene **63**, engine-visualscript 9 |

Counts come from `<module>/build/test-results/test/*.xml`.

During development one new test failed. The light color was clamped to 1 by `Color.set`. This was a real bug, and it
is fixed. No pre-existing test failed.

The compiler printed an "unchecked operations" note for `InspectorPanel.java` when that file recompiled. This phase
added two `register` lines there, in the same form as the existing ones. The note is most likely pre-existing and
was not investigated.

## Diff review

**New files:**
- engine-scene: `SkyLightComponent`, `ToneMappingComponent`, their serializers, `SkyLightComponentTest`, `ToneMappingComponentTest`
- engine-render: `RenderQuality3D`, `ShadowBounds`, `ToneMappingShader`, `SkyLightMaps`, `BakedSkyLightMaps`, `ShadowBoundsTest`, `ToneMappingShaderTest`, `RenderQuality3DTest`
- docs: `docs/milestones/phase-13b-lighting.md`, this report

**Edited files that were not already modified:** `DirectionalLightComponent`, `DirectionalLightComponentSerializer`,
`LightSerializationTest`, `SceneRenderer3DTest`, `DefaultEditorActions`, `Create3DActionsTest`,
`LightComponentEditorProviders`, `InspectorPanel`.

**Edited files that already had the user's uncommitted changes.** Only the hunks described here were added:
- `SceneRenderer3D.java`: rewritten around the Phase 17 model code. `collectModels`, `posedInstanceFor` and `getModelInstance` were kept unchanged.
- `ComponentCatalog.java`: two entries.
- `ComponentCatalogTest.java`, `ComponentCloningAndSerializationCoverageTest.java`: one test each.
- `EditorApplication.java`: serializer registration, lighting actions, the startup sky light.
- `InitialScene3DContentTest.java` (untracked): one test.
- `docs/TECHNICAL_DECISIONS.md` (TD-032), `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`, `docs/milestones/phase-13-asset-pipeline.md`: status rows.

## Known limitations

- **Nothing was seen running.** Shader compilation (the injected tone-mapping line, PBR primitives), the shadow pass,
  frame buffer restore inside the editor view and IBL baking are only exercised with a GL context. They are listed
  as "needs graphics test" in the feature matrix.
- The startup scene's look (sky intensity 0.5 + ACES) is untuned. Primitive and model brightness changed because
  lighting is now linear with gamma.
- One shadow map, no cascades. Only one casting light. Point lights cast no shadows. Shadow bias is a fixed constant.
- No sky background is drawn.
- Without GL 3 / `EXT_shader_texture_lod`, reflections use the blurred irradiance map.
- Exposure does not scale emissive surfaces. Unlit materials are not tone mapped.
- Tone mapping depends on gdx-gltf 2.3.0's shader text. `ToneMappingShaderTest` fails if that text changes.
- `engine-desktop` (runtime launcher) still does not render 3D.
- Duplicate does not copy light components. This is pre-existing: Duplicate copies only six component types.

## Cross-platform notes

The engine is meant to be cross-platform. The code added here is written for that:

- It uses only GL 2.0 / GLES 2.0 calls (`glGetIntegerv` with `GL_FRAMEBUFFER_BINDING` and `GL_VIEWPORT`). It does
  not assume the default framebuffer is 0, and it saves the viewport in back-buffer pixels, so HiDPI/Retina is
  covered.
- The injected tone-mapping line is GLSL ES 1.00-compatible: float literals only, `clamp`, no new uniforms.
- Reflection quality depends on the platform's GL context (`BakedSkyLightMaps.supportsReflectionMap()`). On macOS,
  LWJGL3's default context is legacy GL 2.1, which usually lacks `EXT_shader_texture_lod`. There, reflections fall
  back to the blurred sky until a launcher requests a GL 3.2 core context. Whether gdx-gltf's shaders compile under
  that context has not been checked.
- Not checked on any platform: shadow-map precision and shader compilation on real drivers (Windows, Linux, macOS).

**Update (2026-10-30, Phase 18-A):** the packaged editor's `--smoke-test` rendered 180 frames of the startup scene on
Windows 11 (Java 21.0.11) with exit code 0 and no Output Log errors. This shows that the PBR shaders, including the
injected tone-mapping line, the shadow pass and sky baking, compile and run without exceptions on that machine. It
says nothing about how the result looks; the visual items above stay unverified.

## Next steps

1. Visual checks are deferred to end-to-end testing on the Windows executable (Phase 18 packaging), at the user's
   request (2026-10-30). Use the manual checklist in [phase-13b-lighting.md](../milestones/phase-13b-lighting.md).
   Pay particular attention to shader compile errors in the Output Log, to whether the 3D view stays inside its panel
   on the first frame, and to shadow acne. The same checklist should later run on Linux and macOS builds.
2. Tune `SHADOW_BIAS`, `PRIMITIVE_ROUGHNESS` and `INITIAL_SKY_INTENSITY` from what is seen.
3. Optional: sky background (`SceneSkybox`), cascaded shadows, primitive roughness/metallic fields.

No commit, push or other Git operation that changes history or the index was performed.
