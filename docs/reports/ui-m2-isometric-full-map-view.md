# UI M2: Full-Map Viewport and Isometric Camera

**Date:** 2026-11-19. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed).

Milestone **M2** of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs", after [M1's assessment](ui-m1-full-map-assessment.md). **Status:**
- **Implemented and Integrated:** a fresh editor opens 2.5D and 3D scenes in an isometric view that shows the whole map. It turns in quarter turns, and terrain painting, object placement, picking and gizmos work through it.
- **Manually Not Verified (Headless Environment):** nobody clicked the turn buttons, painted or placed by hand. The pictures come from the editor's own smoke-test screenshots, and the clicks from tests.

## 1. Acceptance criteria (M2's part of the brief)

| # | Requirement | Result | Evidence |
|---|---|---|---|
| 1 | ForgeRoom's actual terrain, not a screenshot | **Met** (already, M1) | The screenshots below are the editor drawing `.terrain` files and glTF scenes |
| 2 | An isometric camera preset | **Met** | New `ViewKind.ISOMETRIC`: orthographic, looking down the diagonal, so 45 degrees round and 35.26 degrees down (true isometric). Cycled to from the pane header, and saved in the layout by name. `OrthographicViewTest` checks the angle, that up is square to the view, and that the three axes are foreshortened alike |
| 3 | Fit the world to the viewport on load | **Met** | `WorldBounds` collects one box per thing with an extent: each terrain's full grid and height range, plus shapes, models and meshes. The camera fits every box's corners as seen from the view, centred, with a tenth of margin. It waits until the scene's models have loaded, with a cap of 120 frames. Each authored scene is framed once, so Play and Stop keep the user's view. Tests: `WorldBoundsTest` (3) and the framing test (every corner on screen, filled to 1/1.1, centred) |
| 4 | Pan, zoom, rotate, select | **Met** | Pan, zoom and pick come from the existing orthographic interaction. **Rotate:** `<` and `>` in the pane header turn the map a quarter round its focus. Tests: quarter turns, about the focus, the camera turning with them, and axis views refusing to turn |
| 5 | Terrain painting and object placement on the map | **Met** | The full-map pane is built like the axis panes: level, terrain and sculpt interactions wrap it with its own camera. `LevelEditingTest` paints gravel and places a prefab through a turned isometric camera, each where clicked and each one undo step |
| 6 | Keep 2D, 2.5D and 3D | **Met** | 2D keeps its own flat view (screenshot). 2.5D and 3D show the panes, now isometric by default. The perspective, X, Y and Z views are still in the header's cycle |

**Mutation checks** (each restored; `cmp` identical):

| Change | What failed |
|---|---|
| Framing centred on the boxes' centre, not the middle of what is seen | "centred up and down: expected 300 but was 234.8" |
| The camera ignoring its quarter turns | "the camera turned with it: expected 0 but was 1.15" |

## 2. What was built

- **`editor-core` `viewport`:**
  - `ViewKind.ISOMETRIC`, with `canTurn()`. The cycle is now Perspective, X, Y, Z, Isometric.
  - `OrthographicViewCamera`: `turn(steps)`, `getQuarterTurns()`, and turned `getDirection()` and `getUp()` that pan, zoom and picking all use. Also `frame(Collection<BoundingBox>)` and `BOX_MARGIN`.
  - `WorldBounds`: `boxes`, `of` and `isSettled`. It is new and headless.
- **`editor-desktop`:**
  - `ViewportPaneView`: `canTurn`, `turn` and `frameWorld`, all defaults.
  - `ScenePaneFactory`'s orthographic pane: turning, and a pending "frame the world" that waits for a size and for the models.
  - `MultiViewportArea`: the `<` and `>` header buttons on a full-map pane, and `frameWorld()`.
  - `AxisGridRenderer`: the isometric view's grid lies on the ground.
  - `EditorApplication`:
    - **the default viewport layout** is one isometric pane (it was the four-pane quad);
    - an opened scene is framed once in the full-map view.
- **Unchanged:** no engine module, file format or scene file. A saved viewport layout is kept as the user left it.

## 3. Problems found and fixed on the way

- **The landscape opened zoomed into the trees.** A glTF model gets its bounds only when the renderer first loads it, so on the first frame the box held just the few shapes. The fit now waits until every model has loaded or failed (`WorldBounds.isSettled`).
- **Then the landscape was framed small.** One box round everything included empty corners above and beside the rotated mountains. A log showed the box was right and the zoom right for that box. The fit now measures each thing's own box from the view and centres on the middle of what is seen. That is tighter, and every corner is still on screen; the mutation above protects the centring.
- **M1's "grid across the sky" was not a fault.** It was the X-axis side view: the owner's saved layout is a single X-axis pane. M1's report is corrected.

## 4. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*.viewport.*" :editor-terrain:test --tests "*LevelEditingTest"` | 1, then 0 | First, the old layout test expected Z axis to cycle back to Perspective, so that assertion now names Isometric. Then all 60 and 10 pass |
| The two mutation checks | 1 each | As listed; restored |
| `./gradlew :<module>:cleanTest :<module>:test`, one module at a time | 0 each | `editor-core` 539 → **546** (1 skipped, as before); `editor-terrain` 41 → **42**; `editor-desktop` 184 |
| `APPDATA=<empty folder> GDXUNITY_EDITOR_ASSISTANT=0 GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--scene samples/<sample>.json --smoke-test"` for `action-rpg-valley-3d`, `landscape-3d`, `polyhaven-showcase-3d`, `platformer-profile-25d`, `platformer-2d` | 0 each | "smoke test passed (180 frames)" each. The empty settings folder makes each run a fresh install |

**Screenshots** (1920 by 991, looked at):
- [ui-m2/isometric-action-rpg-valley-3d.png](ui-m2/isometric-action-rpg-valley-3d.png): the whole valley, diamond-shaped, with Hero, the crate and the boulder. The header reads "Isometric < > | - Max x".
- [ui-m2/isometric-landscape-3d.png](ui-m2/isometric-landscape-3d.png): the whole landscape, with the meadow, lake, cabin, trees and five mountains. There is still some room above and below, because each mountain's own box is a box round a cone.
- [ui-m2/isometric-polyhaven-showcase-3d.png](ui-m2/isometric-polyhaven-showcase-3d.png): the downloaded-asset showcase.
- [ui-m2/platformer-profile-25d.png](ui-m2/platformer-profile-25d.png): a 2.5D side-scroller level seen whole, from above at the isometric angle.
- [ui-m2/platformer-2d.png](ui-m2/platformer-2d.png): 2D, unchanged.

## 5. Known limitations and next steps

- **Turning is by header buttons only.** There are no keys: Q, W, E and R are taken by the transform tools. Turning keeps the zoom.
- **Very large maps:** the orthographic camera sees ±20,000 units along its line of sight (TD-039), so a map larger than about 200 m across would be clipped at its far corners. The shipped samples are within this.
- **Existing users keep their saved layout.** The owner's is one X-axis pane. To see the full-map view, click the pane's name until it reads Isometric, or delete `viewport_layout.json` in the settings folder (`%APPDATA%\GdxUnity`).
- **Framing ignores lights and empty entities,** so a scene of only lights is not framed (it keeps the starting view).
- **Next, M3:** the collapsible drawers, using one drawer model in `editor-core` for the nine tools in M1's section 4, and a one-row top bar.
- **Still waiting on the owner (from M1):**
  - whether the editor may go online (needed by M5);
  - TD-094;
  - the import limits;
  - a design reference, if there is one.

## 6. Diff review and Git

- **New:**
  - `WorldBounds`, `WorldBoundsTest`;
  - this report and `docs/reports/ui-m2/`.
- **Changed:**
  - `ViewKind`, `OrthographicViewCamera`;
  - `ViewportPaneView`, `ScenePaneFactory`, `MultiViewportArea`, `AxisGridRenderer`, `EditorApplication`;
  - tests: `OrthographicViewTest` (+3), `ViewportLayoutTest` (+1, one assertion updated), `LevelEditingTest` (+1);
  - docs: the M1 report (grid note), `FEATURE_TEST_MATRIX.md`, `INDEX.md`.

A temporary debug print was added to `ScenePaneFactory` to read the framed box, then removed (`cmp` identical to the copy taken before). No files outside this repository were touched.

**Git:** no commit, push or other Git operation was performed.
