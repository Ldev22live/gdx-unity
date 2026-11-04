# Phase 17 Report — Skeletal Animation and Timeline

**Date:** 2026-10-29 · **Branch:** `phase-12-3d-foundation` · **Start HEAD:** `1dc0a11` · **End HEAD:** `1dc0a11`
(changes are uncommitted) · **Milestone:** [phase-17-animation.md](../milestones/phase-17-animation.md)

## Approvals

The project owner approved (2026-10-29): running the gdx-gltf spike and adopting the library if it passed, building
the sequencer, and two new modules (`engine-animation`, `editor-animation`). Recorded as TD-030 and TD-031.

## gdx-gltf spike

Scratch Gradle project outside the repository (session scratchpad), gdx-gltf 2.3.0 from JitPack, libGDX 1.14.2
headless. Result: **passed** — no linkage errors; triangle 1 node / 1 mesh / 3 vertices; `PBRShaderProvider` config
links; generated skinned glTF: 1 clip of 1 s, 2 bones, joint at 45 degrees at t = 0.5 s. Two harness notes: a stub GL is
needed headless, and poses land in `Node.localTransform`.

## Acceptance criteria

| Criterion | Met? | Evidence |
|---|---|---|
| Skeletons from imported models | Met | `ModelAssetsTest` (clips, bones, posing), `SkeletonTest.shouldBuildSkeletonFromSkinnedModelNodes` |
| FK and basic two-bone IK | Met (engine) | `SkeletonTest` (FK, reach, pole, out of reach); not exposed in the editor |
| Curves: linear, Bezier, stepped | Met | `AnimationCurveTest` (9) |
| Tracks for transform and custom properties | Met | `ClipSamplerTest` (children, rotation channels, mesh color, model clip time) |
| Timeline: dope sheet, scrubber, play / loop / reverse, auto-key | Met (headless logic); UI visual check pending | `TimelineEditingTest` (13), `TimelineLayoutTest` (5) |
| Curve editor for easing | Partly | Curve view with draggable keys and interpolation per key; no tangent handles |
| Animator component, edit-mode preview, play mode | Met | `AnimatorComponentTest` (8), `TimelineEditingTest` preview / play tests |
| Preview never saved or carried into play | Met | `PlayModeControllerTest.shouldLetExtensionsUndoTemporaryEditsBeforeSceneIsCopied`, `SceneFileControllerTest.shouldRunBeforeSaveHooksBeforeWriting`, `TimelineEditingTest.shouldEndPreviewBeforePlayAndAnimateInPlayMode` |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew test --continue` (before) | 0 | 388 tests, 0 failures |
| `./gradlew test --continue` (after) | 0 | **464 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew :editor-desktop:run` | — | Window "Untitled - GdxUnity Editor" opened; log has no exceptions (see below) |

Per module after: editor-animation 13 (new), editor-core 257 (+5), editor-desktop 22 (+8), editor-visualscript 4,
engine-animation 32 (new), engine-core 27, engine-render 45 (+18), engine-scene 55, engine-visualscript 9.

## Failures found and fixed during the work

- First editor launch crashed: `No SelectBoxStyle registered with name: default`. The skin never defined a select-box
  style, so existing drop-downs (Mesh Primitive shape, Rigid Body 2D type, graph variable type, enum rows) would also
  have crashed when built. Added a default style in `EditorSkinFactory`; also added the `›` glyph used in track labels.
- `PBRShaderProvider.createDefaultConfig()` reads shader files, which broke headless `SceneRenderer3D.initialize()`
  tests; the imported-model batch is now created on the first model draw.
- New coverage test showed `DuplicateEntityCommand` does not copy Model components. Pre-existing for all newer
  component types; left unchanged and listed as a limitation.

## Visual check

**Manually Not Verified (Headless Environment)** for the Timeline and models. The editor was launched and a
screenshot taken: the editor shell rendered and no exceptions were logged, but at the available screen size the
bottom panel (where the Timeline tab is) lay below the screen edge, and the editor then entered Play mode without
input from this session (the owner was presumably using it), so no further interaction was attempted. The manual
checklist is in the milestone.

## Diff summary

New: `engine-animation/`, `editor-animation/`, `ModelAssets`, `ModelPoser`, `ModelComponent`(+serializer),
Timeline UI, Model/Animator Details editors, `SkeletonOverlayRenderer`, `assets/models/BendingStrip.gltf` and `assets/animations/BallBounce.anim` (samples), 3D objects in the startup scene,
docs. Changed: `settings.gradle`, `gradle.properties` (`gltfVersion`), `engine-render/build.gradle`,
`editor-desktop/build.gradle`, `SceneRenderer3D`, `AssetType` (ANIMATION), `AssetPlacement`, `ComponentCatalog`,
`ViewportPicker`, `PlayModeController` (`beforePlay`), `SceneFileController` (before-save hooks),
`SceneEditorWorkspace`, `EditorSkinFactory`, `EditorApplication`. The untracked `docs/screen.png` predates this work
and was not touched.

## Next steps

Visual check with the checklist; tangent handles and box select; an IK component; Duplicate for all serializable
components; shadows/IBL (Phase 13-B); runtime launcher support; Phase 18 packaging.

No commit, push or other Git operation was performed.
