# Phase 13-B — Lighting Quality: Shadows, Image-Based Lighting, Tone Mapping

**Parent:** [phase-13-asset-pipeline.md](phase-13-asset-pipeline.md) step B · **Decisions:** TD-026, TD-030
**Status (2026-10-30):** Implemented and integrated. The visual check is deferred to end-to-end testing on the
Windows executable (Phase 18), then on Linux and macOS. TD-032 approved. Report:
[phase-13b-report.md](../reports/phase-13b-report.md).
**Baseline:** `1dc0a11` plus the uncommitted Phase 17 work on `phase-12-3d-foundation`.

## Goal

A directional light casts shadows, a sky lights the scene from all directions (image-based lighting, IBL), and
the final image is exposure-controlled and tone mapped. Primitives and imported models are lit the same way.
Settings save with the scene and work in the editor and the runtime renderer alike.

## Current state (from the source)

- `SceneRenderer3D` draws primitives with LibGDX's default `ModelBatch` shader and imported models with gdx-gltf's
  `PBRShaderProvider`. The two look different under the same lights.
- Lighting is ambient color + `DirectionalLightComponent` / `PointLightComponent`; no shadows, no sky, no IBL,
  no exposure or tone mapping.
- `updateEnvironmentLights()` allocates new `DirectionalLight`/`PointLight` objects every frame (AGENTS.md §6).
- `dispose()` swallows errors (TD-008 deviation, AGENTS.md §6). New GPU resources in this phase (shadow frame
  buffers, cubemaps, BRDF lookup texture) must not copy that pattern.
- The editor launcher does not request a GL 3 context; gdx-gltf's full IBL roughness needs `textureLod`
  (`gdx-gltf-evaluation.md` risk 5).

## Decision: TD-032 (approved 2026-10-30; final text in `TECHNICAL_DECISIONS.md`)

**Shadows, IBL and tone mapping through gdx-gltf's existing facilities, inside `engine-render`; no new dependency.**

- Draw primitives with the same PBR shader provider as imported models (base color from the component color,
  fixed default roughness/metallic as named constants). One shading model for everything.
- Shadows: gdx-gltf's directional shadow light (single map first; cascades as a quality setting) with its PBR
  depth shader for the shadow pass. One shadow-casting directional light per scene.
- IBL: generated at load time with gdx-gltf's `IBLBuilder` from a procedural sky (no HDRI file needed). HDR/HDRI
  sky files are a later step because they add an asset type.
- Tone mapping: the spike found none in gdx-gltf 2.3.0. Exposure scales the light colors; the operator is one line
  inserted into gdx-gltf's fragment shader (`ToneMappingShader`).
- No gdx-gltf types outside `engine-render` (same rule as TD-030).

Alternative: own shadow-map and IBL shaders. Rejected for now: several milestones of shader work for features the
already-approved library provides.

## Steps

Outcome per step: 0–7 done, except the optional sky background (step 4) and cascades (step 3), which were left
out; see the report.

| Step | Content | Tests (headless unless noted) |
|---|---|---|
| 0 — spike | Check in gdx-gltf 2.3.0 the shadow light, cascade, `IBLBuilder`, skybox and tone-mapping APIs; check `textureLod` with the current launcher context and decide whether `EditorLauncher` / runtime must request GL 3.2 (`setOpenGLEmulation(GL30, 3, 2)`). Record findings in TD-032 | — (spike notes) |
| 1 — unify shading | Primitives drawn by the PBR batch; remove the default-shader path; reuse light objects instead of allocating per frame | Light sync reuses instances (count unchanged across frames); `collectPrimitives` unchanged; needs graphics test: primitives and models match under one light |
| 2 — scene data | `DirectionalLightComponent.castShadows` (default true, older scenes load with it on); new `SkyLightComponent` (sky colors / intensity / show sky background) in `engine-scene/lighting`; scene-level `exposure` and `toneMapping` setting (component on a scene entity, so it saves, undoes and prefabs like everything else). Serializers registered, catalog entries, Create → Sky Light | Round-trip and legacy-load tests; `ComponentCloningAndSerializationCoverageTest` entries; `ComponentCatalogTest`; validation of negative intensity / exposure |
| 3 — shadows | Shadow pass for the chosen light; shadow box fitted to the camera view (pure math helper, e.g. `ShadowBounds`, in `engine-render`); quality settings: map size, distance, cascades, bias | `ShadowBounds` fit tests (contains the view frustum slice, stable under small camera moves); "which light casts" selection (first enabled, active, `castShadows`); needs graphics test: visible shadow |
| 4 — IBL and sky | Build diffuse/specular cubemaps + BRDF lookup when the sky light changes (not per frame); optional sky background; ambient color becomes the fallback when there is no sky light | Rebuild-only-on-change test with a fake builder; needs graphics test: reflections/sky visible |
| 5 — exposure and tone mapping | Apply scene settings to the PBR shader config; editor default keeps today's brightness | Settings → config mapping test; needs graphics test |
| 6 — quality settings and disposal | `RenderQuality3D` (Low/Medium/High presets: shadow map size, cascades, IBL resolution) documented in Javadoc and here; View menu preset selector; all new GPU resources disposed with TD-008 aggregation (`ResourceDisposalException`) | Preset values; disposal continues after one failure and reports it (fake disposables); `dispose()` idempotent |
| 7 — editor integration | Details panels for the new fields; default scene gets a sky light; editor fallback light stays a non-shadow fallback | Initial-scene test (`InitialScene3DContentTest`) |

## Acceptance criteria

1. A box on a plane, lit by a directional light with `castShadows`, casts a visible shadow in the editor 3D view
   and in play mode (manual).
2. Turning `castShadows` off, disabling the light, or undoing either removes the shadow (manual + undo test).
3. A sky light lights unlit sides with sky color and shows reflections on a metallic imported model (manual).
4. Exposure changes overall brightness; tone mapping keeps bright lights from clipping to white (manual).
5. Primitives and imported models look consistent under the same lights (manual).
6. Scenes saved before this phase load unchanged in content, with shadows on (automated legacy-load test).
7. No per-frame allocation in the light sync or shadow pass (code review + reuse test).
8. `./gradlew test --continue` exit 0, test counts per module recorded before and after; report in
   `docs/reports/phase-13b-report.md`; `FEATURE_TEST_MATRIX.md` and `INDEX.md` updated.

Manual items are recorded as **Manually Not Verified (Headless Environment)** if an agent cannot see the screen.

## Manual checklist

1. New scene → Create → Box, Create → Plane under it; the sun casts the box's shadow on the plane.
2. Rotate the sun with its direction field; the shadow follows. Uncheck Cast Shadows; the shadow goes; Ctrl+Z.
3. Fly far away and back: shadows stay sharp near the camera, no swimming when orbiting slowly.
4. Create → Sky Light; change its colors; unlit sides take the sky tint; toggle sky background.
5. Drag a metallic `.glb` in; it reflects the sky.
6. Change exposure from 1 to 2 and back; switch tone mapping off and on.
7. View → quality Low / High; shadow resolution visibly changes; no errors in the Output Log.
8. Save, reopen; all settings remain. Play mode shows the same lighting.

## Out of scope

HDRI sky files (new asset type), point/spot light shadows, baked lighting, screen-space effects (SSAO, bloom),
fog, depth-correct 2D/3D compositing (Phase 13-D), runtime launcher changes beyond what `SceneRenderer3D` already
shares with it.

## Risks

- `textureLod` / GL context: may require requesting GL 3.2 core in both launchers; check scene2d.ui still renders
  (step 0).
- Shadow acne and peter-panning: bias as a quality setting; covered by manual checklist item 3.
- IBL generation cost: done on change only, at a resolution set by the quality preset.
- Primitive appearance changes when switching to PBR; editor ambient default may need retuning (named constant).
