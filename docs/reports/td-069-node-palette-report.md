# TD-069 Report: Node Palette for the Graph Editor

Record: [TD-069](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331` (the working tree was clean when this work started; every change below is uncommitted).

The owner approved this as "TD-067"; that number already belonged to Game Modes, so it is recorded as TD-069.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | A searchable palette of every node beside the Graph canvas | **Met** (seen in a still frame) | `images/td69-graph-palette.png` (categories closed), `images/td69-palette-search.png` (search `Transform.set` lists 9 nodes) |
| 2 | Dragging a node onto the canvas adds it where it is dropped | **Implemented, not verified.** The code is a scene2d `DragAndDrop` source per node and a drop target on the canvas. Nobody performed a drag: this environment shows still frames only. **Manually Not Verified (Headless Environment)** | `NodePalette`, `GraphCanvas.addNodeAt` |
| 3 | Clicking a node adds it at the middle of the canvas | **Implemented, not verified** (same reason) | `GraphCanvas.addNodeAtCenter` |
| 4 | The palette's grouping, search and 200-match cap are tested | **Met** | `NodePaletteModelTest`, 4 tests |
| 5 | Refresh Nodes updates the palette | **Implemented, not verified** | `GraphEditorPanel` calls `palette.refill()` |
| 6 | The Pure/Action switch | **Already existed** before this milestone (`GraphCommands.SwitchMethodKind`, the toolbar button); nothing changed | `GraphEditorPanel.switchSelectedKind` |
| 7 | The Graph tab seen in the running editor | **Met for drawing**: the palette, the toolbar and `ShooterShip.graph` render with 0 errors in the output log. Editing with the mouse was not tried | `images/td69-*.png`, smoke test output |
| 8 | Each of the 15 example graphs run | **Not done in this milestone.** Existing `GraphExamplesTest` and `SampleGraphsTest` (TD-060, TD-068) cover what they covered | – |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-visualscript:test :editor-visualscript:test :editor-desktop:test --continue` | 0 | engine-visualscript 62 tests, 0 failures; editor-visualscript **68** (was 64, +4), 0 failures; editor-desktop 84, 0 failures |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_EDITOR_TAB="Visual Scripting"` (three runs, with and without `GDXUNITY_EDITOR_GRAPH` / `GDXUNITY_EDITOR_PALETTE`) | 0 | 180 frames, 0 errors in the output log |

No other module's tests were run; I did not run the full `./gradlew test`. The 2 known `BundledDemoSongTest` failures in `editor-audio` were therefore not re-checked.

## Changes

- New: `editor-visualscript/.../NodePaletteModel.java` and its test, `editor-desktop/.../ui/graph/NodePalette.java`.
- Changed: `GraphCanvas` (`hasDocument`, `addNodeAt`, `addNodeAtCenter`), `GraphEditorPanel` (palette column, 4 px gaps between toolbar buttons, `searchPalette`), `EditorApplication` (testing aids `GDXUNITY_EDITOR_GRAPH`, `GDXUNITY_EDITOR_PALETTE`), `AGENTS.md` (aid comment), `docs/TECHNICAL_DECISIONS.md`, `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`.
- New images: `docs/reports/images/td69-graph-palette.png`, `td69-graph-open.png`, `td69-palette-search.png`.

## Known limitations and next steps

- A person should drag a node from the palette onto the canvas, click one, and press Refresh Nodes, then record the result here.
- In the frames the long category names are cut with an ellipsis at the palette's fixed 280 px width; some Java method titles with several parameter types are cut too.
- Examples remain slices of their games (TD-060); the Blueprint-style work in TD-068 continues separately.

No commit, push or other Git write was performed.
