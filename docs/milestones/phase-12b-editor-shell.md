# Phase 12B — Unreal-Style Editor Shell

**Status:** Steps A, B and C implemented (A in `3e55d5e`, B with 12C in `32df7a4`). Visual checks pending; see `docs/reports/phase-12b-12c-report.md`.
**Reference:** Unreal Engine 5 editor workflows (TD-020) — original implementation, generic names only.

## Objective

Turn the editor shell into a clean desktop application: a scene view that renders inside its own panel with
reliable picking, real drop-down menus driven by one action registry, proper file handling, an output log,
and readable typography. This is the frame that the 3D viewport (Phase 12C) and later tools plug into.

## Starting state (verified 2026-10-29)

- UI stage was drawn into the scene camera's 4:3 GL viewport (fixed in working tree).
- Viewport clicks were rejected because the viewport spacer was not touchable (fixed in working tree).
- Scene drawn full-window behind panels with a fixed 640×480 `FitViewport`; the grid was erased every frame
  because it was drawn before `SceneRenderer2D` cleared the screen.
- Top "menus" are single-action buttons; File saves silently to `scene_backup.json`.
- Built-in bitmap font, ASCII pseudo-icons, no output log, per-type "+ Component" buttons.

## Step A — Viewport, layout, actions, menus

| # | Deliverable | Module |
|---|---|---|
| A1 | `EditorAction` / `EditorActionRegistry` / `DefaultEditorActions`: id, label, menu, group, shortcut, enabled state; shortcuts bound through `EditorKeyboardDispatcher` | editor-core |
| A2 | `ViewportCoordinates2D` (panel-local → world) and `SceneViewportInteraction2D` (pick, gizmo-first, pan, zoom), replacing `EditorApplication.ViewportInputProcessor` pointer logic | editor-core |
| A3 | Screen-filling `OrthographicCamera2D` mode; `SceneRenderer2D` clear toggle so the grid survives | engine-render |
| A4 | `SceneViewportWidget` (FrameBuffer-backed scene view) with an in-viewport tool overlay | editor-desktop |
| A5 | Drop-down `EditorMenuBar` (File, Edit, Create, Tools, Window, Help) built from the registry | editor-desktop |
| A6 | Layout: menu bar · main toolbar (Compile left, Play/Pause/Stop centered, Assistant right) · Outliner \| Viewport \| Details · Content Browser · status bar | editor-desktop |

**Acceptance (A):** all tests pass; new headless tests for actions, shortcuts, coordinates and interaction;
manual checklist below executed by the project owner.

## Step B — Files, Output Log, Details, Outliner

File New/Open/Save/Save As via native dialogs (TD-022) behind `FileDialogService`; dirty tracking from
`CommandHistory` with `*` in the window title; unsaved-changes prompt on New/Open/close. Output Log panel
(editor-core log model; editor, script and play-mode messages; severity filter) as a tab beside Content
Browser. Details: searchable **Add Component**, collapsible component sections with enable toggle and
Remove. Outliner: right-click Create/Rename/Duplicate/Delete.

## Step C — Typography, icons, shortcuts (done)

Inter Regular/SemiBold via gdx-freetype (TD-021), generated at the back-buffer density and scaled to UI
units for HiDPI; SemiBold for section titles. Procedurally drawn Play/Pause/Stop icons (no image assets).
F frames the selection (`ViewportFocus`). Ctrl+N/O/S/Shift+S came with step B.
Not done: icons for the transform tools and panels (text labels remain).

## Out of scope

3D viewport (Phase 12C), floating/detachable panels, Content Browser thumbnails, renaming the project.

## Manual checklist (step A)

Run `gradlew.bat :editor-desktop:run`:

1. UI fills the window; text is not squashed; no empty bands at the sides.
2. Scene view sits between Outliner and Details; resizing the split panes resizes the scene, which is not
   stretched. The grid is visible.
3. Left-click a square selects it (Outliner and Details update); clicking empty space clears selection;
   Ctrl/Shift-click toggles multi-selection.
4. Q/W/E/R switch tools (overlay buttons highlight); with Move, dragging an arrow moves the entity and one
   Ctrl+Z undoes the whole drag.
5. Right-drag pans; mouse wheel zooms around the view; overlay "Reset View" recenters.
6. Each top menu opens a drop-down; disabled items are greyed (e.g. Undo with empty history); clicking
   outside closes it; shortcuts shown in the menu work.
7. Play / Pause / Stop in the toolbar behave as before; File and Create items are disabled while playing.
8. Switching theme (Window menu) rebuilds the UI without losing the scene view.
