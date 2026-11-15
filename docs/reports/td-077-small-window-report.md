# TD-077 Report: The Editor Fits a Small Window

Record: [TD-077](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The working tree already held the uncommitted work of TD-069 to TD-076; nothing was committed, staged or reverted.

All observations come from **still frames** saved by the editor itself (`GDXUNITY_SCREENSHOT`) at the end of a 180-frame smoke run, at **1024 by 640** (the editor's minimum window) and **1366 by 768**. Nothing was clicked, dragged, scrolled or typed into: those are **Manually Not Verified (Headless Environment)**. The owner said they can run end-to-end tests of these milestones around 7pm on 2026-11-11; no results had been given when this report was written.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | A way to run the editor at a chosen window size | **Met** | `GDXUNITY_EDITOR_WINDOW=<w>x<h>` (`EditorLauncher.parseWindowSize`); `EditorLauncherWindowConfigurationTest` (+2: a size is read; a malformed size or one below 1024x640 is refused) |
| 2 | Frames at the minimum and a laptop size of the tabs people use | **Met**: 16 "before" and 16 "after" frames | `images/td77-<size>-<tab>-before.png` and `-after.png` for viewport, scene, animations, graph, music-rack, music-instrument, sprites, tiles |
| 3 | The two TD-076 open findings (Timeline toolbar, palette names) | **Timeline: met** (shared scrolling bar, wider gaps). **Palette: partly**: rows fill the width and the label cell is set up to cut with an ellipsis, but the frames show the names cut without "..."; I did not confirm the ellipsis appears | `td77-1366x768-graph-after.png` |
| 4 | Fix what the smaller sizes show | **Met for most, not all** | table below |
| 5 | Docs, matrix, report | **Met** | this file, TD-077 record, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `AGENTS.md` |

## What the frames showed

| # | Finding (before) | Action | After |
|---|---|---|---|
| 1 | **The whole editor was about 1,900 px wide in every window**, cropped on both sides: at 1366x768 even the Viewport tab lost the top bar's ends, the Outline's left edge, the Details panel's right, "2D 2.5D 3D" and "AI Assistant". Cause: the top bar's row of buttons (about 1,500 px) set the minimum width of the root column | `ScrollingBar.around` on the two top bars | Outline, Details, Content Browser and status bar fully visible at both sizes; the top bars clip at the right edge and scroll |
| 2 | At 1024x640 the **Sprites tab** (and Tiles) made the layout taller and wider than the window: the top bars were gone | `TabbedPanel` content minimum 0; `ScrollingBar.panel` around the Sprites and Tiles panels | editor intact; the Sprites panel scrolls with visible bars (`td77-1024x640-sprites-after.png`, `td77-1366x768-sprites-after.png`) |
| 3 | The **Viewport tab at 1024x640** was a few pixels wider than the window (edges clipped) | same `TabbedPanel` rule | edges intact (`td77-1024x640-viewport-after.png`) |
| 4 | The **Graph toolbar rows** and Timeline toolbar did not fit a narrow centre column | `ScrollingBar.around`; Timeline gap 4 to 14 | clip and scroll |
| 5 | The **Music transport's two rows were squeezed under the tabs** in a short window (a scroll pane reports no minimum height) | `minHeight(Value.prefHeight)` | both rows visible (`td77-1024x640-music-rack-after.png`) |
| 6 | The **Graph palette took 280 of a ~390 px centre column**, leaving about 110 px for the canvas | the palette takes 40% (160 to 280 px) | the canvas is about 200 px at 1024x640 |
| 7 | **Music channel rack at 1024x640**: the name column is cropped (the rack takes its name column from the room left over) | a scroll pane around the rack changed nothing, so it was removed | **open** |
| 8 | **Tiles tab**: an empty panel in every frame | none | **not examined** (may be "no tilemap") |

No button, field or menu changed what it does.

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `scratchpad/frames77.ps1`: `./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_EDITOR_WINDOW` of `1024x640` and `1366x768`, `GDXUNITY_EDITOR_TAB` of Viewport, Scene, Animations, Visual Scripting (with `GDXUNITY_EDITOR_GRAPH`), Music (rack and instrument, with the song aids), Sprites, Tiles; three times (before, after the first fixes, after the second) | 0 each (48 runs) | 180 frames, 0 errors each |
| the Music rack at both sizes again, from the final code | 0 | frames `td77-*-music-rack-after.png` |
| `./gradlew :editor-desktop:test` (first attempt of the new test) | 1 | `ScrollingBarTest` failed: scene2d recursed without end because `Gdx.files` is null in a unit test, then `Gdx.app` was missing; fixed by mocking both with Mockito in the test |
| `./gradlew test --continue --max-workers=1` (whole repo) | 0 | all 20 modules, 0 failures, 0 errors |

Not re-run: the web suite (no web code changed; last run 476 tests, 0 failures).

Per-module counts (failures 0 everywhere): editor-animation 17, editor-audio 57, editor-character 10, editor-core 385 (1 skipped, pre-existing, not examined), editor-desktop **94** (was 87: +2 `EditorLauncherWindowConfigurationTest`, +5 `ScrollingBarTest`), editor-sculpt 56, editor-sprite 51, editor-visualscript 89, engine-animation 32, engine-audio 222, engine-character 19, engine-core 35, engine-desktop 92, engine-physics 53, engine-render 119, engine-scene 149, engine-sculpt 81, engine-sprite 44, engine-visualscript 86, engine-web 9.

Pre-existing versus new: no failure appeared apart from my own new test, above.

## Changes

- `editor-desktop`: new `ui/ScrollingBar`; changed `EditorLauncher`, `EditorApplication`, `ui/TabbedPanel`, `ui/graph/GraphEditorPanel`, `ui/graph/NodePalette`, `ui/timeline/TimelinePanel`, `ui/music/MusicStudioPanel`; tests `ScrollingBarTest` (new), `EditorLauncherWindowConfigurationTest`.
- Docs: `TECHNICAL_DECISIONS.md`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `AGENTS.md` (aid list), this report, 32 frames in `docs/reports/images/`.

## Known limitations and next steps

- **Nothing was clicked or scrolled.** Whether the mouse wheel scrolls a top bar, whether its bar appears on hover, whether dragging in the sprite canvas still draws, and whether anything is now hard to reach were not tried. The scroll panes could change how some drags behave.
- Open: the Music rack's name column at 1024x640 (finding 7); the Tiles tab's empty panel (8); the palette's ellipsis (criterion 3).
- Not looked at: the Piano roll, Playlist and Mixer sub-tabs, the Scene tab's panels, the Graph pop-ups, 1280 by 720, sizes above 1366, and the web editor.
- The editor's minimum window is 1024 by 640 and is still tight: the Graph tab's canvas is narrow there.

No commit, push or other Git write was performed.
