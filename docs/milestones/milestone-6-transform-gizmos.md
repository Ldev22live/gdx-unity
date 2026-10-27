# Milestone 6 Implementation Report: Visual Transform Gizmos and Viewport Interaction

**Project:** GdxUnity  
**Milestone:** 6 — Visual Transform Gizmos and Viewport Interaction  
**Date:** 2026-10-22  
**Status:** Complete  

---

## 1. Executive Summary

Milestone 6 extends **GdxUnity**'s desktop editor with interactive, viewport-based **Move**, **Rotate**, and **Scale** visual transform gizmos. Gizmo interactions operate on the selected entity's authoritative `Transform` using pure mathematical models, keeping the viewport responsive, accurate across camera zoom levels, and fully synchronized with the Inspector and Play Mode lifecycle.

Key accomplishments:
1. **Architectural Separation:**
   - `editor-core`: Houses `GizmoHandle`, immutable `TransformSnapshot`, `TransformGizmoMath`, and `GizmoInteractionController`.
   - `editor-desktop`: Houses `TransformGizmoRenderer` (rendering handles via `ShapeRenderer`) and integrated input handling in `ViewportInputProcessor`.
2. **Move Gizmo:** Provides X-axis (Red), Y-axis (Green), and Center-plane (Cyan) handles. World delta movement is cleanly projected into local coordinates for parented entities without world displacement error or cumulative numerical drift.
3. **Rotate Gizmo:** Provides a 2D rotation ring (Blue) around the Z axis. Dragging accurately calculates angular deltas in degrees and mutates local rotation through `Transform.setLocalRotation(Quaternion)`.
4. **Scale Gizmo:** Provides X-axis, Y-axis, and Center uniform scale handles with positive `MIN_SCALE = 0.001f` enforcement to prevent numerical collapse, zero division, or degenerate matrix scaling.
5. **Handle Picking & Input Priority:** Handle hit-testing occurs before entity picking. Clicking a handle begins manipulation without altering or clearing entity selection.
6. **Interruption & Cancellation Safety:** Switching tool modes, changing selection, replacing scenes, or releasing the mouse cleanly terminates manipulation without leaving the editor in a stuck drag state.
7. **Inspector Synchronisation:** Whenever a gizmo drag mutates an entity's transform, `EditorState.notifyEntityTransformModified()` fires, updating the `InspectorPanel` in real time.
8. **Automated Testing Suite:** Added 7 comprehensive headless unit tests in `editor-core` verifying hit-testing, parented transformations, rotation wrap, and scale clamping. Workspace total stands at **81 unit tests, 0 failures, 100% passing**.

---

## 2. Architecture & Module Boundaries

### Dependency Graph

```
engine-desktop           editor-desktop
    │                         │
    │                    ┌────┴────────────┐
    │                    ▼                 ▼
    │               editor-core      engine-render
    │                    │                 │
    └───────────┬────────┘                 │
                ▼                          ▼
           engine-render ───────────► engine-scene
                                           │
                                           ▼
                                      engine-core
```

- Runtime modules (`engine-core`, `engine-scene`, `engine-render`, `engine-desktop`) remain **completely independent** of editor modules.
- `editor-core` contains all gizmo manipulation mathematics, hit-testing algorithms, and interaction state management, executing 100% headlessly.
- `editor-desktop` handles OpenGL handle drawing and user mouse events on the desktop window.

---

## 3. Gizmo Modes and Supported Operations

| Tool Mode | Handles Available | Visual Appearance | Supported Manipulation |
| :--- | :--- | :--- | :--- |
| **Move** | `MOVE_X`<br>`MOVE_Y`<br>`MOVE_CENTER` | Red arrow pointing right<br>Green arrow pointing up<br>Cyan square at origin | Axis-aligned translation along X or Y<br>Free planar translation in the viewport plane |
| **Rotate** | `ROTATE_Z` | Blue circular ring (radius 50px) | 2D angular rotation around Z axis |
| **Scale** | `SCALE_X`<br>`SCALE_Y`<br>`SCALE_UNIFORM` | Red terminal box on X axis<br>Green terminal box on Y axis<br>Cyan square at origin | Single-axis scaling along X or Y<br>Uniform scaling across X and Y simultaneously |

---

## 4. Coordinate Conversion and Parent Transform Handling

- **Screen-to-World:** Unprojected through `Camera2D.getViewport().unproject(Vector3)` to obtain world coordinates.
- **Scale Factor Adaptation:** Handle dimensions and hit-test radii scale proportionally with camera zoom (`effectiveScale = camera.getZoom()`). This ensures handles maintain a consistent pixel-space footprint on screen regardless of whether the camera is zoomed in to `0.1x` or out to `10.0x`.
- **Parent Hierarchy Projection:** When manipulating an entity with a parent:
  $$\Delta_{\text{local}} = R_{\text{parent}}^{-1} \times \left( \frac{\mathbf{P}_{\text{world\_target}} - \mathbf{P}_{\text{parent\_world}}}{\mathbf{S}_{\text{parent\_world}}} \right)$$
  Gizmo movement computes the target world position and converts it to local position using the parent's world rotation and scale. This ensures parented entities move accurately without drift or skew.

---

## 5. Input Priority & Drag Cancellation

In `EditorApplication.ViewportInputProcessor`:
1. **Right Mouse:** Pans the camera (independent of gizmos).
2. **Left Mouse Down:**
   - First, `gizmoController.hitTest()` checks if any active gizmo handle was clicked.
   - If hit: `startDrag()` captures an immutable `TransformSnapshot` and enters dragging mode. Entity selection is retained.
   - If miss: Falls through to `ViewportPicker.pickEntityAt()` for entity selection.
3. **Left Mouse Drag:**
   - If `gizmoController.isDragging()`: updates entity transform relative to the initial snapshot and notifies `EditorState`.
4. **Left Mouse Up:**
   - Calls `endDrag()`, concluding manipulation.
5. **Safety Cancellations:**
   - If the user changes tool mode (`ToolMode.MOVE` -> `ToolMode.ROTATE`), clears selection, or reloads the scene during a drag, `cancelDrag()` restores the initial snapshot and resets dragging state.

---

## 6. Files Created or Modified

| File | Change Type | Description |
| :--- | :---: | :--- |
| `editor-core/.../gizmo/GizmoHandle.java` | Created | Enum identifying gizmo handles. |
| `editor-core/.../gizmo/TransformSnapshot.java` | Created | Record capturing immutable pre-drag local and world transform states. |
| `editor-core/.../gizmo/TransformGizmoMath.java` | Created | Pure mathematical hit-testing, world-to-local conversion, and scale clamping algorithms. |
| `editor-core/.../gizmo/GizmoInteractionController.java` | Created | Controller managing drag states and transform mutations with automatic cancellation. |
| `editor-desktop/.../ui/TransformGizmoRenderer.java` | Created | Viewport renderer drawing gizmo lines, arrows, rings, and boxes via `ShapeRenderer`. |
| `editor-desktop/.../ui/EditorToolbar.java` | Modified | Added Rotate and Scale tool buttons to Tool Modes section. |
| `editor-desktop/.../EditorApplication.java` | Modified | Integrated gizmo controller, gizmo renderer, and input handling into render and input loop. |
| `editor-core/.../EditorState.java` | Modified | Added `onEntityTransformModified` listener callback and notification hook. |
| `editor-desktop/.../ui/InspectorPanel.java` | Modified | Added `onEntityTransformModified` listener to refresh property fields during dragging. |
| `engine-render/.../SceneRenderer2D.java` | Modified | Added `getShapeRenderer()` getter to permit gizmo overlay rendering. |
| `editor-core/.../gizmo/TransformGizmoTest.java` | Created | 7 headless unit tests for gizmo math, hit-testing, parenting, and cancellations. |
| `docs/ROADMAP.md` | Modified | Marked Milestone 6 complete with all acceptance criteria checked. |
| `docs/TECHNICAL_DECISIONS.md` | Modified | Documented TD-016 (Visual Transform Gizmos and Viewport Interaction). |
| `docs/TEST_STRATEGY.md` | Modified | Added Transform Gizmo headless testing guidelines. |

---

## 7. Verification and Testing

### Automated Headless Tests
Run from repository root:
```powershell
./gradlew.bat test
```
**Results:**
- `engine-core`: 19 tests passed
- `engine-scene`: 23 tests passed
- `engine-render`: 11 tests passed
- `editor-core`: 28 tests passed (12 M4 tests + 9 M5 tests + 7 M6 tests)
- **Total: 81 tests passed, 0 failures, 100% success rate.**

### Full Gradle Build
```powershell
./gradlew.bat build
```
**Result:** `BUILD SUCCESSFUL in 8s` (all 6 modules compile, test, and package cleanly).

### Manual Desktop Smoke Test Procedure
To visually test gizmos in an environment with display support:
```powershell
./gradlew.bat :editor-desktop:run
```

**Verification Checklist:**
1. Editor opens in Edit Mode.
2. Select an entity in the hierarchy (e.g. `ParentCore` or `ChildPlanet`):
   - Click **Move**: Red (X), Green (Y), and Cyan (Center) handles appear at entity origin.
   - Drag X handle: Entity moves horizontally in real time, and Inspector Position X updates.
   - Drag Y handle: Entity moves vertically, and Inspector Position Y updates.
   - Drag Center handle: Entity moves freely in 2D plane.
3. Click **Rotate**:
   - Blue circular ring appears around entity origin.
   - Drag ring: Entity rotates smoothly around Z axis. Inspector Rotation updates.
4. Click **Scale**:
   - Red and Green terminal boxes appear on axes.
   - Drag X box: Entity scales horizontally.
   - Drag Center box: Entity scales uniformly. Scale is clamped above `0.001f`.
5. Pan the camera (right-drag) and zoom (mouse wheel):
   - Gizmos remain positioned accurately at entity origin and retain screen-readable proportions.
6. Test parented entity (`ChildPlanet` or `Moon`):
   - Move gizmo alters child local position while correctly matching world cursor movement.
7. Switch tools during a drag:
   - Drag cancels cleanly without sticking.
8. Enter **Play Mode** (▶ Play):
   - Gizmos continue operating on runtime entities without altering the authored scene.
   - Stop (⏹ Stop) restores authored positions and removes runtime mutations.

---

## 8. Remaining Limitations & Recommended Milestone 7 Scope

1. **Asset Pipeline & Materials:** Milestone 7 should implement asset references, asynchronous loading, texture imports, and an Asset Browser panel.
2. **Undo / Redo System:** Provide command history for transform manipulations, entity creations, and property edits.
3. **Multi-Selection Gizmos:** Group pivot manipulation for multiple selected entities.
4. **Snapping & Grid Alignment:** Configurable unit snapping for position, angle snapping (e.g. 15-degree steps), and scale increments.
