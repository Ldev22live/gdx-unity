# Multi-Pane 3D View Report (TD-039)

Date: 2026-11-03 · Platform: Windows 11, JDK 21 · HEAD before and after: `1dc0a11` (nothing committed)

## What was built

| Where | What |
|---|---|
| `editor-core` | `ViewKind`, `ViewportLayout` (tree of panes and splits, JSON), `OrthographicViewCamera`, `OrthographicViewInteraction`, `TransformGizmoMath.orthographicScaleFactor`, controller getters |
| `editor-sculpt` | `SculptViewportInteraction` takes any camera; `SculptActions.TOGGLE_MODE` (Tools menu, toolbar, F4) |
| `editor-desktop` | `MultiViewportArea`, `ViewportPaneView`, `ScenePaneFactory`, `AxisGridRenderer`; `SceneEditorWorkspace.renderPane3D`; `TransformGizmoRenderer` full-3D option; toolbar toggle highlight; `EditorApplication` wiring, layout file `viewport_layout.json`, editor starts in 3D |

## Evidence

| Check | Result |
|---|---|
| `ViewportLayoutTest` (20): quad order and positions, tiling, no overlap, split, close, limits, ratios, pointer to ratio, minimum size, maximize, JSON round trip and bad files | pass |
| `OrthographicViewTest` (9): axes, pan, zoom about the pointer, limits, framing, select and clear, pan buttons, wheel, gizmo size | pass |
| `SculptActionsTest` (+1 toggle), `editor-sculpt` total 44; `editor-desktop` 40 | pass |
| Editor started on the sculpt scene with `--smoke-test=300` | exit 0, 300 frames, 0 errors |
| Screenshot of the editor window alone (taken only after confirming it was the foreground window) | four panes, correct axis colours, sculpt visible in the side views, Sculpt (F4) button in the toolbar |

## Not checked

- Mouse use in the running editor: dragging a divider, the header buttons (cycle view, split, maximize, close),
  panning and zooming in an axis view, dragging a gizmo handle in an axis view, sculpting in an axis view, and
  reading the layout back after a restart. Only the headless logic behind them is tested.
- The header position after I moved the headers to the bottom left of each pane (the screenshot was taken before; the
  toolbar covered the headers of the top panes).
- The full test suite and `engine-web`; the whole `editor-core` module was last run in full before this work.

## Known limits

- Four panes draw the scene four times per frame, shadow pass included.
- Dropping a Content Browser asset on the multi-pane view puts it at the orbit target.
- Axis views start centred on (320, 240, 0), the old 2D view centre, so content near the origin starts at an edge;
  Focus Selection (F) frames every pane.
- The editor now opens in 3D; the 2D / 3D button switches back to the single 2D view.
- "Customizable" was my reading of the request; no linked dividers, no per-pane grid or shading options.
