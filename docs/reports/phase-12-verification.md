# Phase 12 Verification and Design-Doc Alignment Report

**Date:** 2026-10-29
**Scope:** Verify the uncommitted Phase 12 (3D rendering foundation & camera systems) work against the
source, fix defects found, and add the process artifacts required by `docs/design-doc.md.txt`
(§10.3 coverage matrix, §11 `AGENTS.md` and agent skills).
**Out of scope:** Phase 13, wiring the 3D viewport into the editor, any Git operation.

## 1. Repository state

| | Value |
|---|---|
| Branch | `main` (up to date with `origin/main`) |
| Starting HEAD | `7772df4` |
| Final HEAD | `7772df4` (unchanged; nothing staged or committed) |

Pre-existing uncommitted user work (Phase 12) at start: modified `GizmoHandle`, `TransformGizmoMath`,
`ViewportPicker`, `TransformGizmoTest`, `TransformGizmoRenderer`, `Transform`, `TransformTest`,
`docs/Oct28-current-state.md`; untracked `PerspectiveCamera3D`, `SceneRenderer3D`, lighting components,
`ViewportCameraController3D` and their tests, `docs/INDEX.md`, `docs/design-doc.md.txt`.
Some of these files were edited by this work (listed in §4). `docs/Oct28-current-state.md` was not edited.

## 2. Phase 12 deliverables: claimed vs. actual

`docs/Oct28-current-state.md` reports Phase 12 as "COMPLETED & VALIDATED". The source supports
**implemented with headless tests**, not validated:

| Deliverable (roadmap) | Actual state |
|---|---|
| `PerspectiveCamera3D` | Implemented and tested. Not used by the editor or runtime. `setZoom` is a silent no-op. |
| `ModelBatch` wrapper and rendering pass | `SceneRenderer3D` begins/ends a `ModelBatch` pass but **submits no geometry** (no model component exists). Not used by the editor or runtime. |
| Directional/point lights + ambient | Components exist and sync into the LibGDX `Environment`. **No serializers** (lights are lost on scene save), no inspector, no dedicated tests. |
| 3D transform enhancements | Implemented and tested (`TransformTest`). |
| 3D viewport navigation | `ViewportCameraController3D` implemented and tested. **Not connected to editor input.** |
| 3D gizmos (move arrows + planes, rotate rings, scale cubes) | Hit-test and delta math implemented and tested. `GizmoInteractionController` only uses the 2D path; no ray-to-plane drag; renderer's 3D branch has no perspective viewport to draw into yet. |
| Headless tests for 3D camera, raycasting, gizmo math | Present; extended in this work. |

The roadmap checklist is accurate in leaving these unchecked. Treat the earlier "validated" wording as
superseded by this report.

## 3. Defects found and fixed

| # | Defect | Fix | Test (fails without fix) |
|---|---|---|---|
| 1 | `TransformGizmoMath.hitTest3D` returned `MOVE_CENTER` when clicking the center in **ROTATE** mode, so a rotate click would start a move drag. | Center handle only checked for MOVE / SCALE. | `shouldHitTest3DRotationRingsWithoutCenterHandle` |
| 2 | 3D axis hit boxes stopped at `axisLen`, but the renderer draws tip boxes out to `axisLen + handleRadius`, so clicking a visible tip missed. (The 2D hit-test already included the tip.) | Axis boxes extend to `axisLen + handleRadius`. | `shouldHitTest3DAxisTipBeyondAxisLength` |
| 3 | Planar handle size differed between hit-test (`0.4`) and renderer (`0.35`); XZ/YZ planes were hit-testable but never drawn. | Shared `PLANE_HANDLE_SIZE_RATIO`; renderer draws all three planes. | `shouldHitTest3DPlanarMoveHandles` |
| 4 | `TransformGizmoRenderer` drew 3D-only handles (Z axis, X/Y rings, plane quad) in the **live 2D editor**, where the 2D hit-test can never return them: visible but unclickable handles. | 3D-only handles drawn only when the camera is a `PerspectiveCamera3D`. | Visual; needs an in-app check (see §6). |
| 5 | `ViewportCameraController3D.orbit` could rotate past the poles, flipping the camera upside down. Dead `dist` variable. | Polar angle clamped to `[1°, 179°]`; dead code removed; magic numbers named. | `shouldClampVerticalOrbitAtPolesWithoutFlipping` |
| 6 | `ViewportPicker.getPickRay` fabricated a ray from screen coordinates when the camera was missing: a silent fallback the design doc forbids (§1). | Throws `NullPointerException` / `IllegalArgumentException`. | `shouldRejectPickRayWithoutCamera`, `shouldRejectPickRayWhenCameraHasNoViewport` |
| 7 | `SceneRenderer3D.render` had two identical empty `ModelBatch` branches; Javadoc implied model rendering existed. | One begin/end pass; Javadoc states no geometry is submitted. | Covered by existing `SceneRenderer3DTest`. |

`hitTest3D` was also restructured around `rayHitsBox`/`rayHitsRing` helpers and regular imports instead of
inline fully qualified names.

## 4. Files changed by this work

Code: `TransformGizmoMath.java`, `TransformGizmoRenderer.java`, `ViewportPicker.java`,
`ViewportCameraController3D.java`, `SceneRenderer3D.java`.
Tests: `TransformGizmoTest.java` (+4), `ViewportPickerTest.java` (+2), `ViewportCameraController3DTest.java` (+1).
Docs/process (new): `AGENTS.md` (cross-referenced to the approved decisions in `TECHNICAL_DECISIONS.md`), `.windsurf/workflows/{milestone-audit,implement-milestone,verify-build,review-diff}.md`,
`docs/FEATURE_TEST_MATRIX.md`, this report.
Docs (edited): `README.md` (replaced gdx-liftoff template text that described the unbuilt `core`/`lwjgl3`
modules), `docs/INDEX.md` (absolute `file:///C:/...` links → relative; Phase 12 status corrected).

## 5. Verification

| Command | Exit | Result |
|---|---|---|
| `./gradlew test --continue --console=plain` (baseline, before changes) | 0 | 255 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew test :editor-desktop:compileJava :engine-desktop:compileJava --continue --console=plain` (final) | 0 | 262 tests, 0 failures, 0 errors, 0 skipped |

Per module (final): editor-core 168 (+7), editor-desktop 6, engine-core 27, engine-render 20, engine-scene 41.
`engine-core` and `engine-scene` were up-to-date in the final run (no input changes since the baseline run).
Counts were taken from `*/build/test-results/test/*.xml`.

## 6. Not verified / known limitations

- Fix #4 (2D gizmo appearance) is **Manually Not Verified (Headless Environment)**: the editor was not
  launched. Run the existing checklist in `docs/milestones/milestone-6-transform-gizmos.md` §7. The fix
  restores exactly what that checklist expects: Move shows red X, green Y and the center handle; Rotate shows
  one blue ring; Scale shows red/green boxes.
- The success path of `ViewportPicker.getPickRay` is untested headlessly because `Camera.unproject` reads
  `Gdx.graphics`.
- No macOS or Linux runs.
- Renderer `dispose()` methods (2D and 3D) swallow disposal exceptions. This deviates from approved
  decision **TD-008** (aggregate failures into `ResourceDisposalException`) and design doc §12. Left
  unchanged here because `SceneRenderer2D` is outside Phase 12 scope.
- `PerspectiveCamera3D` constructor silently falls back to 800×600 when `Gdx.graphics` is absent.

## 7. Recommended next steps (each needs authorization)

1. Review and commit the Phase 12 working tree.
2. Finish Phase 12 integration: a perspective viewport in the editor using `SceneRenderer3D`,
   `ViewportCameraController3D` and `getPickRay`; a 3D drag path in `GizmoInteractionController`
   (ray-to-plane projection); serializers and inspector editors for light components.
3. Add a model/mesh component so `SceneRenderer3D` renders geometry (overlaps Phase 13; decide ordering).
4. Bring renderer `dispose()` in line with TD-008.
5. Remove the unbuilt `core/` and `lwjgl3/` template directories (already a planned task in
   `ALL_IN_ONE_STUDIO_ROADMAP.md` §6). (`docs/porject-suggestions.md` is an intentional mirror; no action.)
6. When integrating the 3D gizmo: TD-016 scales handles by camera zoom, which is constant for
   `PerspectiveCamera3D`. At the default handle size (60 world units) and camera distance (10), the gizmo
   would fill the view. Add a distance-to-camera scale factor, and record it as a new decision extending
   TD-016.
7. Record Phase 12 decisions (perspective-camera gizmo gating, `ScreenViewport` for the 3D camera vs
   TD-011's `FitViewport`) in `docs/TECHNICAL_DECISIONS.md`.

No commit, push, staging, reset, or other Git operation was performed.
