# M1b.2 and M1b.4 The World layout (World-first stage 4): report

**Milestones:** M1b.2 (World screen) and M1b.4 (selection-aware tools) in [milestone-chart.md](../milestone-chart.md), roadmap phase R1b, Appendix E1. This is **stage 4** of the staged plan in [world-first-ux-audit.md](world-first-ux-audit.md).
**Owner instruction:** "continue with the next milestone" (2026-11-14), after stage 3.
**Decision record:** [TD-085](../TECHNICAL_DECISIONS.md), **approved by the owner later on 2026-11-14**, as built. The owner then approved the thin header (part 2, at the end of this report): "Tabs out, tools to viewport".
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21, real display for the smoke runs.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this stage touched are listed below.

## Result against the chart

| Deliverable | Result |
|---|---|
| M1b.2: no permanent large inspector, hierarchy or asset browser | **Met:** the Outliner and the Content Browser and Log start closed; Details shows only while something is selected. Each opens from a button that is always on screen, and the choice is saved |
| M1b.2: an interactive viewport, most of the window | **Met:** with nothing selected the scene view fills the space between the toolbar and the footer (screenshot) |
| M1b.2: contextual strip showing only the relevant palette | **Met for Terrain and Objects:** the strip's Terrain shows the ground tools and base ground only; Objects shows Place, Erase and the objects only. Buildings, Characters and 2D Objects are **labelled and explain themselves**; nothing is behind them yet. Tiles and Sprites open their tabs |
| M1b.2: a thin header with no permanent 3D, 2D, Animation, Audio or Logic tabs, and a context indicator such as World / My Game | **Not done:** the centre tabs and the tool buttons are still in the top bar. Customize (M1b.7) already reaches every workspace, so they are now a second route. Removing them is the remaining step; TD-085 leaves it for its own approval |
| M1b.2 done-when: opens by default with the owner's screenshot as the interaction reference | **Partly:** the World layout is now the default. I do not have the owner's reference screenshot, so I could not compare against it |
| M1b.4: brush settings for terrain, palette for objects, properties for a selected entity | **Met as listed above**, with Details following the selection |
| M1b.4: Edit 3D Model for a mesh-backed building | **Partly:** through Customize, "Edit House in 3D (sculpt a copy)"; real mesh editing is R7a |
| M1b.4 done-when: unrelated controls never show together | **Met for the controls built:** terrain tools and object tools are separate; the strip hides in sculpt mode and during Play; the sculpt strip and level palette were already exclusive. Not checked by a person |

## What was built

- **`PanelLayout`** (`editor-core`): which panels are open. It has three actions (Window menu): **Outliner**, **Details** (steps through *with selection*, *always* and *hidden*) and **Content Browser and Log**. The state lives in `WorkspaceLayoutConfig`, which the editor saves at exit.
- **The layout file goes to version 2.** A version 1 file keeps its sizes and gets the World defaults, so an existing install opens in the World layout once and then keeps the person's choice. Before, any other version reset the whole file; an unknown Details mode falls back to the default.
- **The panel arrangement** (`EditorApplication`): a closed panel is **taken out of its split** rather than squeezed, so the centre gets the room and the panel keeps its state. The open AI Assistant keeps the right column open, since it shares it with Details.
- **Panel buttons** at the end of toolbar row 2: *Outliner*, *Details: with selection* (it names its mode) and *Content Browser*, lit when open.
- **`WorldStrip`** (`editor-core`): the sections per game mode, and why a section does nothing yet.
- **`LevelSession.Focus`** (`editor-terrain`): ALL (the Level button), TERRAIN or OBJECTS. A focus offers only its tools; entering one with an unrelated tool in hand takes its first tool; turning the level editor off returns to ALL. A tool outside the focus is refused.
- **`WorldStripBar`** (`editor-desktop`): the strip at the bottom centre of the scene view, rebuilt when the scene's game mode changes. **`LevelPalette`** shows only the focused tools and its title says Terrain, Objects or Level.

## Tests (`editor-core` 435 to 439, `editor-terrain` 15 to 16, `editor-desktop` 143, unchanged count)

| Test | What it pins |
|---|---|
| `PanelLayoutTest` (4, new) | The defaults are the World layout and Details follows the selection; each action opens its panel and reports it; the choice survives save and load; Details cycles round; a **version 1 file keeps its sizes and gets the World panels**; an unknown Details mode falls back; the strip's sections for 3D, 2.5D and 2D, which are unbuilt and why, and that a section is refused for a mode that does not show it |
| `LevelEditingTest` (+1) | Terrain offers exactly the brush tools and Objects exactly Place and Erase; entering a focus with an unrelated tool takes the first one, a related one is kept; a tool outside the focus is refused; brush strokes are off while placing; turning off returns to ALL |
| `WorkspaceLayoutSerializerTest` (changed) | Expects version 2 |
| `ActionInventoryTest` (changed) | **72 actions** (69 + 3), each with a home; the three panel actions are reached by their buttons, and the source must build those buttons (a closed panel can never lose its way back); the hidden set is still only the two redundant sculpt entries |

## Evidence from the real editor

`./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_SCREENSHOT`, three runs, each rendering 180 frames with 0 errors:

- [m1b-2-world/world-default.png](m1b-2-world/world-default.png): nothing selected. The scene view fills the window, with **Terrain Objects Buildings Characters** at its bottom centre and *Outliner, Details: with selection, Content Browser* at the end of row 2.
- [m1b-2-world/world-selection-details.png](m1b-2-world/world-selection-details.png) (`GDXUNITY_EDITOR_SELECT=Ball`): **Details opened by itself** with Ball's Transform, Mesh Primitive and Animator.
- [m1b-2-world/world-1024x640.png](m1b-2-world/world-1024x640.png): at the minimum window the panel buttons, the strip and the footer all fit.

**Not seen on screen:** clicking any strip entry or panel button, the focused level palette, a 2D scene's strip, and the Outliner or Content Browser reopening.

**Side effect to know:** these runs used the editor's normal settings folder, so the **saved layout file was migrated to version 2** (World defaults). That is what the next launch would have done anyway.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*PanelLayoutTest" --tests "*WorkspaceLayoutSerializerTest" :editor-terrain:test --tests "*LevelEditingTest"` | 1, then 0 | One old round-trip test still expected version 1; updated to `CURRENT_VERSION` |
| `./gradlew :editor-desktop:compileJava` | 0 | |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*ActionInventoryTest"` | 0 | Inventory regenerated (72 rows) |
| `./gradlew :editor-core:test :editor-terrain:test :editor-desktop:test` | 0 | **editor-core 439 (1 skipped), editor-terrain 16, editor-desktop 143; 0 failures** |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with screenshots (three) | 0 each | 180 frames, 0 errors each |

Repository total: 1,917 + 4 + 1 = **1,922 (computed, not a fresh full run)**.

## Limits

- **Manually Not Verified (Headless Environment):** every click this stage adds.
- **The header is not thin yet**: tabs and tool buttons remain; this is M1b.2's remaining work and is gated on TD-085 and its own approval.
- **The panel arrangement has no automated test.** It is GL-bound glue; the screenshots are its evidence. A closed panel's split size is not saved (sizes were never saved from dragging, before or now).
- **Placeholders:** Buildings, Characters and 2D Objects only explain themselves.
- **No selection context menu in the viewport** (the plan's "selection-aware context menu"); the Outliner's context menu still holds those actions.
- The first launch after this change looks different to the owner (panels closed); every panel is one button away.

## Next

- **TD-085 needs approval** (or changes).
- Then the last part of M1b.2: move the centre tabs out of the header (Customize already reaches every workspace), leaving a thin header with a "World / My Game" indicator, Save, Undo, Redo, Project and Settings.
- Then **M1b.5**, the usability test with an unfamiliar person, which needs a person.

## Files changed

- New: `editor-core/.../workspace/PanelLayout.java`, `WorldStrip.java`, `PanelLayoutTest.java`; `editor-desktop/.../ui/viewport/WorldStripBar.java`; this report and `m1b-2-world/*.png`.
- `editor-core/.../workspace/WorkspaceLayoutConfig.java`, `WorkspaceLayoutSerializer.java`, `WorkspaceLayoutSerializerTest.java`; `editor-terrain/.../LevelSession.java`, `LevelEditingTest.java`; `editor-desktop/.../EditorApplication.java`, `.../ui/viewport/LevelPalette.java`, `.../audit/ActionInventoryDump.java`, `ActionInventoryTest.java`.
- `docs/TECHNICAL_DECISIONS.md` (TD-085), `docs/milestone-chart.md` (M1b.2, M1b.4), `docs/reports/world-first-ux-audit.md` (inventory, stage 4), `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`.

## Part 2: the thin header (2026-11-14, owner's choice "Tabs out, tools to viewport")

| Change | Detail |
|---|---|
| **The centre tabs left the header** | Viewport, Scene, Animations, Visual Scripting, Music, Sprites and Tiles are no longer shown. Customize and Back to World switch workspaces. So that every editing tab stays reachable, Customize now lists **2D Studio: Sprites** and **2D Studio: Tiles** separately (`Workspace.destinations()`). The Scene tab is the game during Play: Play switches to it and Back to World returns to it while playing |
| **The header** | Left: a **context indicator**, `World / Untitled ●` (the workspace, the scene and an unsaved mark). Right: **Save, Undo, Redo, Project, Settings, Create**. The New and Open buttons are gone; both are in Project (and Ctrl+N, Ctrl+O) |
| **The tools moved to the view's row** | Select, Move, Rotate, Scale, Frame, Sculpt and Level sit after the view icons in the second row. Duplicate and Delete lost their buttons; they keep their shortcuts and the Outliner's right-click menu, as chosen |
| **Panel buttons pinned** | In a narrow window the second row scrolls sideways; the Outliner, Details and Content Browser buttons are now outside the scrolling part, at the right. **Found in the 1024x640 screenshot**, where they had scrolled out of view, which broke TD-085's "always on screen" |
| **The footer** | The workspace name moved to the header, so the footer's gateway is just Customize and Back to World |

**Tests:** `CustomizeGatewayTest` +1 (every centre tab except the game view has a Customize destination, eight destinations, each maps back to its workspace); `CustomizeGatewaySamplesTest` (changed: eight entries, Tiles reachable, only the 2D Studio tab on screen marked); `ActionInventoryTest` (changed: the tab row is not in the header; the seven view tool buttons and the pinned panel buttons are built; routes regenerated: tools are "view tools" buttons, Save, Undo and Redo "header" buttons, New and Open only in Project with their shortcuts, Duplicate and Delete in the Outliner's menu with their shortcuts; the hidden set unchanged).

**Counts:** `editor-core` 439 to **440**, `editor-terrain` 16, `editor-desktop` **143**; 0 failures (`./gradlew :editor-core:test :editor-terrain:test :editor-desktop:test`, exit 0, then `:editor-desktop:test` again after the pinning, exit 0). Repository total **1,923 (computed)**.

**Screenshots** (smoke runs, 180 frames, 0 errors each): [m1b-2-world/thin-header.png](m1b-2-world/thin-header.png) at 1920x991, `World / Untitled` on the left and `Save | Undo Redo | Project | Settings | Create` on the right, the tools after the view icons; [m1b-2-world/thin-header-1024x640.png](m1b-2-world/thin-header-1024x640.png) in the Animations workspace at the minimum window, with `Animation / Untitled`, the whole header, the pinned panel buttons and Back to World in the footer.

**Limits of part 2:**
- **Duplicate and Delete are less visible:** shortcut or the Outliner's menu, and the Outliner starts closed. A selection context menu in the scene view (planned) would give them a visible home.
- **In a narrow window** the second row scrolls, so Level, Compile Scripts and AI Assistant can be off screen until scrolled. Level is also reachable through the World strip.
- **The Scene tab** cannot be opened by hand while stopped (it only shows the game while playing).
- **Manually Not Verified:** none of the moved controls were clicked.

**M1b.2 now:** every deliverable is built (thin header with a context indicator, World viewport, World strip, no permanent panels, unbuilt sections labelled). Comparison against the owner's reference screenshot is still open, because I do not have the screenshot. M1b.5 (the usability test with a person) is next.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: above; M1b.2 built except the reference-screenshot comparison, M1b.4 partly built.
2. Exact commands, exit codes and counts: above.
3. New failures investigated: one stale expectation (version 1), updated; no regressions.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
