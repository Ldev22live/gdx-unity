# UI M3: Collapsible Tool Drawers and a One-Row Top Bar

**Date:** 2026-11-19. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed).

Milestone **M3** of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs" (after [M1](ui-m1-full-map-assessment.md) and [M2](ui-m2-isometric-full-map-view.md)). **Status:**
- **Implemented and Integrated.** The brief's nine tools are independent drawers: the four map drawers open from the World strip, and the five docked drawers from buttons at the right of a single top row.
- **Manually Not Verified (Headless Environment):** nobody clicked a drawer button, dragged a drawer edge or placed a building by hand. The pictures are the editor's own smoke-test screenshots, and the clicks come from tests.

## 1. Acceptance criteria

| # | Requirement | Result | Evidence |
|---|---|---|---|
| 1 | Independent drawers for the nine tools | **Met** | `EditorDrawers` (new, `editor-core`) names them, where each opens and how. `EditorDrawersTest` (4) checks the nine names, the places, that every map drawer opens from the World strip in 2.5D and 3D, and that every docked drawer's action is registered |
| 1a | Terrain, Objects, Buildings and Characters | **Met** | **On the map:** the World strip opens the level palette for that part, and pressing it again closes it. **Buildings is new:** it lists `prefabs/buildings` (Cottage and Watchtower, built from shapes by `tools/make_level_prefabs.py`). A building is placed square and at its own size, unlike trees, and selected; it is one undo step, and Erase removes it. Tested by `LevelEditingTest` (+1). Reshaping a building's mesh is still R7a's |
| 1b | Asset Library, hierarchy, properties, console, AI Assistant | **Met** | **Docked:** the Outliner (left); Details and the AI Assistant (right, one above the other when both are open); the Asset Library and the Console (bottom, side by side when both are open). **New:** the Asset Library and the Console were one tabbed panel and are now two drawers, each opened on its own. `PanelLayoutTest` (+2) covers both opened in either order, closing one leaving the other, both saved, and an older layout file reading as Console closed |
| 2 | Collapsible | **Met** | Each docked drawer's button opens and closes it, and is lit while it is open. Details steps through auto (with a selection), on and off, and its button says which. The map drawers close when their lit strip entry is pressed again |
| 3 | Resizable where appropriate | **Met** (existing) | The docked drawers sit in split panes whose edges drag. The two bottom drawers share the bottom at 60 to 40, between 20% and 80%. The map drawers size to their contents |
| 4 | Do not obscure the world | **Met** | The World layout starts with every drawer closed, so the map has the whole window between the top row and the footer (screenshot). A drawer that closes gives its room back |
| 5 | Minimal top toolbar | **Met** | One row instead of two. **Scrolls in a narrow window:** the context, Save, Undo, Redo, Project, Settings, Create, the view icons, the transform tools, Frame, Sculpt, Level and Compile Scripts. **Fixed at the right:** the drawer buttons, so a closed drawer can always be reopened (TD-085's rule, now from `EditorDrawers`). The toolbar's separate AI Assistant button is gone; it is a drawer button now |

**Mutation checks** (each restored; `cmp` identical):

| Change | What failed |
|---|---|
| Opening the Console also flips the Asset Library | "opening the Console leaves the Asset Library closed" |
| Buildings placed like trees (random turn and size) | "at its own size: expected 1.0 but was 0.904" |

## 2. What was built

- **`editor-core`:**
  - `workspace/EditorDrawers` (new);
  - `PanelLayout`: `TOGGLE_CONSOLE`, `isAssetLibraryShown`, `isConsoleShown`, `toggleConsole`. `TOGGLE_BOTTOM_PANEL` is now the Asset Library's action, under its old id;
  - `WorkspaceLayoutConfig.consoleShown`, which defaults to closed, so older files read unchanged;
  - `WorldStrip`: Buildings is available;
  - `StarterPalette.BUILDING_FOLDER`: new 3D and 2.5D projects get the buildings too.
- **`editor-terrain`:** `LevelSession.Focus.BUILDINGS`, `BUILDING_FOLDER`, square placement, and Erase reaching buildings.
- **`editor-desktop`:**
  - `EditorApplication`: the one-row top bar, `drawerButton(...)` built from `EditorDrawers.docked()`, the two bottom drawers, and the Profiler recording only while the Console shows it;
  - `WorldStripBar` and `LevelPalette`: Buildings.
- **Assets:** `prefabs/buildings/Cottage.prefab` and `Watchtower.prefab`. `make_level_prefabs.py` regenerated the seven existing prefabs byte-identical.
- **Unchanged:** every panel's own content (Outliner, Details, Content Browser, Output Log, Profiler, Assistant), the World strip's other entries, and the scene format.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test :editor-terrain:test :editor-desktop:test --continue` (first run) | 1 | **Expected breakages:** a strip test expected Buildings to be planned, and `ActionInventoryTest` read the old toolbar and the assistant's id from the source. That test needs action ids written as literals, so the id is a literal again and a new test checks it equals `EditorDrawers.TOGGLE_ASSISTANT`. **Stale tables:** two generated tables went stale, from the new labels and prefabs. All fixed |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*.audit.*" --tests "*WebUxContractTest"` | 1, then 0 | The pinned action count went from 75 to 76 (the Console's action); then the tables regenerated |
| The two mutation checks | 1 each | As listed; restored |
| `./gradlew :editor-core:test`, `:editor-terrain:test`, `:editor-desktop:cleanTest :editor-desktop:test` | 0 each | `editor-core` 546 → **552** (1 skipped, as before); `editor-terrain` 42 → **43**; `editor-desktop` 184 → **186** |
| `APPDATA=<empty folder> GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--scene samples/action-rpg-valley-3d.json --smoke-test"`, with every drawer closed and then with the docked ones open (a prepared layout file and `GDXUNITY_EDITOR_SELECT=Hero`), at 1920 by 991 and at `GDXUNITY_EDITOR_WINDOW=1024x640` | 0 each | "smoke test passed (180 frames)" |
| The same, on a temporary scene of the two building prefabs (kept in the ignored `assets/build/`, removed after) | 0 | Both buildings drawn |

**Screenshots** (looked at):
- [ui-m3/drawers-closed-1920.png](ui-m3/drawers-closed-1920.png): the map from the top row to the footer. One top row, ending in the drawer buttons Outliner, Details: auto, AI Assistant, Asset Library and Console.
- [ui-m3/drawers-open-1920.png](ui-m3/drawers-open-1920.png): the open drawers' buttons are lit. Outliner is left, Details (Hero) right, and the Asset Library and Console side by side at the bottom; the map is still framed whole in what is left.
- [ui-m3/drawers-closed-1024.png](ui-m3/drawers-closed-1024.png) and [ui-m3/drawers-open-1024.png](ui-m3/drawers-open-1024.png): the smallest window. The drawer buttons stay on screen and the rest of the row scrolls.
- [ui-m3/buildings-cottage-watchtower.png](ui-m3/buildings-cottage-watchtower.png): the Cottage (walls, a two-slope roof, a chimney) and the Watchtower (battlements), seen from the side opposite their doors.

## 4. Known limitations and next steps

- **The top row is wider than the window at 1920 by 991** by a few letters ("Compile Scripts" is cut, more with a long scene name). It scrolls sideways; shorter labels or a compacter view-icon group would fix it.
- **At 1024 by 640 with drawers open,** the World strip covers the map view's header (Isometric < >).
- **No drag to undock or reorder drawers;** each drawer has a fixed side.
- **The Buildings drawer offers the two starter buildings.** Downloaded houses (the Kenney Building Kit, approved but waiting for a person to download it) go in `prefabs/buildings` as prefabs once imported.
- **Next, M4:** the local Asset Library and import workflow, built on the existing Content Browser, the third-party manifest (TD-094) and the archive checks of `tools/thirdparty_assets.py`, moved into Java.
- **Owner decisions (2026-11-19, after this report):** TD-094 approved; TD-095 approved (online on use, offline first, high-definition imports). The design reference arrived and is compared in [ui-reference-comparison.md](ui-reference-comparison.md).

## 5. Diff review and Git

- **New:**
  - `EditorDrawers`, `EditorDrawersTest`, `EditorDrawerButtonsTest`;
  - the two building prefabs;
  - this report and `docs/reports/ui-m3/`.
- **Changed:**
  - **Code:** `PanelLayout`, `WorkspaceLayoutConfig`, `WorldStrip`, `StarterPalette`, `LevelSession`, `EditorApplication`, `WorldStripBar`, `LevelPalette`, `tools/make_level_prefabs.py`.
  - **Tests:** `PanelLayoutTest` (+2, one updated), `LevelEditingTest` (+1), `ActionInventoryTest` (homes, the source check, and the count of 76).
  - **Docs:** the regenerated tables of `world-first-ux-audit.md`, `web-ui-ux-spec.md` and `sample-asset-migration-inventory.md`; `FEATURE_TEST_MATRIX.md` and `INDEX.md`.

No files outside this repository were touched.

**Git:** no commit, push or other Git operation was performed.
