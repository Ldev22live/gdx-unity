# Phase 12B Step B and Phase 12C Report

**Date:** 2026-10-29 · **Branch:** `phase-12-3d-foundation` · **Starting HEAD:** `3e55d5e` · **Final HEAD:** `3e55d5e`
(work is uncommitted, awaiting review)
**Decisions:** TD-022 (corrected), TD-023, TD-025 (proposed)

## 1. Scope

- **12B step B:** scene files with native dialogs, unsaved-changes tracking, Output Log, searchable
  Add Component with collapsible component sections, Outliner right-click menu and rename.
- **12C:** 3D content: box/sphere/plane primitives rendered and lit in both views, saveable and editable
  lights, Create menu entries.
- **Out of scope:** step C (fonts, icons), imported meshes (glTF), PBR materials, shadows.

## 2. Deliverables

| Deliverable | Where | Tests |
|---|---|---|
| New / Open / Save / Save As, `*` in window title, Save/Discard/Cancel prompt on New, Open, Exit and window close | `SceneFileController`, `EditorDialogService`, `TinyFileDialogService`, `EditorLauncher` | `SceneFileControllerTest` (8), `TinyFileDialogServiceTest` (1), `DefaultEditorActionsTest` |
| Output Log tab with severity filter and Clear; status messages and every script diagnostic logged | `EditorLog`, `OutputLogPanel`, `TabbedPanel`, `EditorToolbar` | `EditorLogTest` (4) |
| Searchable Add Component; component sections with collapse, enable toggle, Remove; generic section for components without an editor | `ComponentCatalog`, `InspectorPanel` | `ComponentCatalogTest` (4), `InspectorPanelNamingTest` (1) |
| Outliner right-click: Create, Rename (F2), Duplicate, Delete | `HierarchyPanel`, `ContextMenu` (also used by the menu bar) | via action tests |
| Mesh primitives rendered in 2D and 3D views, default editor light | `MeshPrimitiveComponent`, `SceneRenderer3D`, `SceneEditorWorkspace` | `MeshPrimitiveRenderingTest` (4) |
| Light serializers and Details editors; mesh Details editor | `*LightComponentSerializer`, `LightComponentEditorProviders`, `MeshPrimitiveComponentEditorProvider` | `LightSerializationTest` (1) |
| Create → Box, Sphere, Plane, Directional Light, Point Light at the view center, one undo step | `DefaultEditorActions.registerCreate3DActions`, `CreateEntityCommand` | `Create3DActionsTest` (3) |
| 3D picking and box outline for primitives | `ViewportPicker.computeMeshBounds/pickEntity3D`, `SelectionOutlineRenderer` | `Create3DActionsTest` |

## 3. Defects found and fixed

1. **Redo lost components.** `Scene.removeEntity` detaches all components, so undo-then-redo of a create
   restored a bare entity. `CreateEntityCommand` now re-attaches them (`Create3DActionsTest`).
2. **Depth test left on.** `SceneRenderer3D.render` returned early with depth testing still enabled when
   there was no scene, affecting every later 2D pass. Depth testing is now scoped to the primitive pass.
3. **LWJGL version mismatch in TD-022.** The editor runs on LWJGL 3.3.3 (resolved by `gdx-backend-lwjgl3`),
   not `lwjgl3Version` 3.4.1. Pinning tinyfd to 3.4.1 would have upgraded LWJGL under libGDX. Added
   `backendLwjglVersion=3.3.3`; corrected TD-022 and `AGENTS.md`.
4. **tinyfd Yes/No/Cancel.** The LWJGL 3.3.3 `tinyfd_messageBox` overload returns `boolean`, which cannot
   distinguish No from Cancel; the raw `ntinyfd_messageBox` call returning `int` is used instead.
5. **Hidden components.** Components without a Details editor (e.g. Shape Renderer) were not shown at all;
   they now get a section with enable/Remove.

## 4. Verification

| Command | Exit | Result |
|---|---|---|
| `./gradlew test --continue --console=plain` at start (`3e55d5e`) | 0 | 304 tests, 0 failures |
| `./gradlew test --continue --console=plain` final | 0 | 331 tests, 0 failures, 0 errors, 0 skipped |

Per module (final): editor-core 223, editor-desktop 12, engine-core 27, engine-render 27, engine-scene 42.

**Manually Not Verified (Headless Environment).** The editor was not launched for this work (an earlier
launch was stopped by the host for low memory). Native dialogs, the Output Log, Add Component, the Outliner
menu and 3D primitive rendering need the checklist below.

## 5. Manual checklist

1. Create → Box: a lit grey box appears at the view center, selected with an orange box outline (3D) or
   rectangle (2D); Ctrl+Z removes it, Ctrl+Y brings it back with its shape.
2. Details for the box: change Shape to Sphere, Size, Color (press Enter); each change is one Ctrl+Z.
3. Create → Point Light near the box; move it with the gizmo in 3D; the box lighting changes.
4. Ctrl+S on a new scene opens a Save dialog; the title shows the file name; edit → title shows `*`;
   Ctrl+O with unsaved changes asks Save/Don't Save/Cancel (No = discard, Cancel = stay).
5. Close the window with unsaved changes → same prompt; Cancel keeps the editor open.
6. Output Log tab lists status messages; Compile Scripts with an error lists each diagnostic.
7. Add Component: type "light" → only light entries; entries already on the entity are greyed.
8. Right-click in the Outliner → Rename (or F2) renames as one undo step.

## 6. Known limitations

- Picking and outlines use axis-aligned bounds; rotated primitives are picked by their unrotated box.
- 2D content draws over 3D primitives regardless of depth.
- Older 2D component editors still show their own Enable/Remove buttons next to the new section header ones.
- The editor's default light and brighter ambient make scenes look different in the runtime, which has no
  fallback light (TD-025).
- Step C (fonts, icons, remaining shortcuts) not started.

No commit, push or other Git operation was performed for this work.
