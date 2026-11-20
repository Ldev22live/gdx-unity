# UI M3b: The Reference Layout

**Date:** 2026-11-19. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed).

The step proposed in [ui-reference-comparison.md](ui-reference-comparison.md) and started by the owner ("continue with M3b"). It brings the editor's layout to the owner's design reference, [`docs/ForgeRoom Isometric World Editor.png`](../ForgeRoom%20Isometric%20World%20Editor.png). **Status:**
- **Implemented and Integrated.** The header, tab row, tool rail, Properties panel and status bar of the reference replace the old two-row top bar, the World strip on the map and the footer.
- **Manually Not Verified (Headless Environment):** nobody clicked through it. The pictures are the editor's own smoke-test screenshots, with drawers opened by a new testing aid.
- **Unchanged:** the world's look (painted terrain, water, dense forest), which is content and rendering (comparison section 2).

## 1. The reference, part by part

| Reference | Built | Evidence |
|---|---|---|
| Header: logo, "ForgeRoom / Untitled World"; Save, Play and Settings on the right | The window icon as the logo, "ForgeRoom" and "/ <world name>" (with ● when unsaved). On the right: Create; Save, Undo and Redo as icons (each with its name and shortcut as a tool tip, greyed when it cannot run); Compile Scripts; Play, Pause and Stop; Project and Settings | Screenshots; `ActionInventoryTest` (header icons) |
| Tab row: Terrain, Objects, Buildings, Characters, Assets, Layers, Scripts, AI Assistant, each with an icon; the open one blue | The same eight tabs, rounded, with original icons drawn by the editor. The open one is filled with the accent. In 2D the first four are the World strip's 2D sections (Tiles, Sprites, Objects, Characters) | Screenshots in 3D and 2D; `EditorDrawersTest`, `EditorDrawerButtonsTest` |
| "Isometric ▾" at the right of the tab row | A view drop-down (Isometric, Perspective, Y, X and Z axis) for the map's first pane, shown in 3D view. It follows the pane's own header | Screenshots |
| A tool rail at the left | Icons for Select, Move, Rotate, Scale, Frame, Sculpt and Level, then grid, physics view, reset view and 2D/3D. Each has a tool tip and is lit while on. Shown only where the World's tools apply, and hidden in Play | `ActionInventoryTest` (every rail button); screenshots |
| The map, large | Unchanged from M2 (the isometric full map), between the rail and Properties | Screenshots |
| Properties: tool picker, Brush Size and Strength sliders with values, Material swatches, World Settings | **While a map drawer is open:** the level palette, with **Brush Size** (logarithmic, 40 to 4000) and **Strength** (10 to 100%) as sliders with values, and the ground as six colour swatches under Material (Paint tool). **Otherwise:** the selection's components. **Below, a collapsible World Settings:** Game Genre, its status, Game Mode, Play's fixes and Customize, which the footer held | `LevelPaletteSliderTest` (log scale, ends, round trip); screenshots |
| Status bar: "Assets \| Layers \| Console" on the left; "64 × 64 · Saved ✓" on the right | Links that open Assets, Layers, Scripts and Properties (Properties cycles auto, on and off). Then the status line, and on the right the ground's grid size and "Saved" (green) or "Unsaved changes" (amber) | Screenshot: "129 × 129 · Saved" on the valley |
| Rounded cards, dark navy, blue accent | Rounded tabs, rail and Properties cards (a nine-patch drawn at the display density). The existing dark palette | Screenshots |

**Names now as in the reference:** Outliner → Layers, Asset Library → Assets, Console drawer → Scripts (its tabs are Console and Profiler), Details → Properties. Action ids are unchanged, so shortcuts, menus, agent tools and saved layouts keep working.

**One decision of mine:** the Properties panel now shows unless hidden. The reference always shows it, and it holds World Settings now. TD-085's "Details with the selection" still decides whether the selection's components show inside it, through the Properties link: auto, on or off.

## 2. What was built

- **`editor-desktop`:**
  - **`EditorSkinFactory`:**
    - 15 new icons, drawn in code like the existing ones (terrain, objects, buildings, characters, tiles, sprites, assets, layers, scripts, assistant, save, settings, undo, redo, level);
    - a rounded tab style per icon (`drawer-*`), the `card-bg` rounded panel and `white-pixel`;
    - a tool-tip style.
  - **`PropertiesPanel`** (new).
  - **`LevelPalette`:** sliders, swatches, and a wider label column.
  - **`WorldStripBar`:** a tab mode (icon tabs, checked while open).
  - **`MultiViewportArea`:** `getPrimaryKind` and `setPrimaryKind`.
  - **`EditorApplication`:** the header, tab row, tool rail, Properties, World Settings and status bar. The footer and the map's World strip are gone. There is a new testing aid, `GDXUNITY_EDITOR_DRAWER=<tab>[:<tool>],...`, which opens drawers on the first frame (for example `Terrain:Paint`); the logo texture is owned and disposed.
- **`editor-core`:**
  - `EditorDrawers`: the reference's names and order, and `tabs()`;
  - `PanelLayout`: the action labels and status messages say Layers, Properties, Assets and Scripts.
- **Not removed:** `EditorFooter` and `ViewportOverlayToolbar` stay in the source, unused by the World layout. They are left for a later clean-up, since other code may still name them.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| First editor run | 1 | "No Drawable ... registered with name: card-bg". A rounded `NinePatchDrawable` must be registered as `Drawable.class` for `getDrawable` to find it; fixed |
| `./gradlew :editor-core:test :editor-terrain:test :editor-desktop:test --continue` | 1, then 0 | `ActionInventoryTest`'s footer test became a reference-layout test (World Settings, header play controls, every rail button, header icons, tab row, status bar), and the routes in the inventory were renamed. Then the tables regenerated (`GDXUNITY_UPDATE_AUDIT=1`) |
| Slider mutation (a linear Brush Size) | 1 | "the middle is the geometric middle, 40 x 10, not 2020"; restored |
| `./gradlew :<module>:cleanTest :<module>:test`, one module at a time | 0 each | `editor-core` 552 (1 skipped, as before); `editor-terrain` 43; `editor-desktop` 186 → **187** (`LevelPaletteSliderTest`) |
| `APPDATA=<empty> [GDXUNITY_EDITOR_DRAWER=...] GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--scene samples/<sample>.json --smoke-test"`, for 7 screenshots | 0 each | "smoke test passed (180 frames)" |

**Screenshots** (looked at, in [ui-m3b/](ui-m3b/)):
- `reference-layout-landscape.png`: the whole layout, every drawer closed.
- `terrain-drawer-valley.png`: Terrain open (blue tab, lit Level on the rail). Properties shows the terrain tools with the sliders; the status bar reads "129 × 129 · Saved".
- `terrain-paint-valley.png`: the Paint tool, with six swatches under Material, Grass outlined.
- `terrain-drawer-valley-1024.png`: the smallest window. Tabs, rail and Properties fit; the palette's long rows scroll.
- `drawers-open-landscape.png`: Layers, Assets and Scripts open with the cabin selected. The tabs and status links are lit.
- `buildings-drawer-2d.png`: a 2D scene, with tabs Tiles, Sprites, Objects and Characters and no view drop-down.

## 4. Known limitations and next steps

- **Small differences from the reference:**
  - the view drop-down has no ▾ arrow (the select box style draws none);
  - the swatches sit to the right of their row, not under the label;
  - the palette's Tool row is long and scrolls sideways in a narrow panel;
  - the Inspector keeps its own "DETAILS" title under "Properties";
  - the logo is the window icon.
- **Not built:** dragging drawers to other sides; the reference's waterfall, painted ground and dense forest (content and rendering).
- **Next: M4,** the local Asset Library and import workflow, in the Assets drawer, under TD-094 and TD-095 (both approved).

## 5. Diff review and Git

- **New:**
  - `PropertiesPanel`, `LevelPaletteSliderTest`;
  - this report and `docs/reports/ui-m3b/`.
- **Changed (code):** `EditorSkinFactory`, `LevelPalette`, `WorldStripBar`, `MultiViewportArea`, `EditorApplication`, `EditorDrawers`, `PanelLayout`.
- **Changed (tests):** `EditorDrawersTest`, `EditorDrawerButtonsTest`, `PanelLayoutTest`, `ActionInventoryTest`.
- **Changed (docs):**
  - the regenerated tables of `world-first-ux-audit.md` and `web-ui-ux-spec.md`;
  - `FEATURE_TEST_MATRIX.md`, `INDEX.md`;
  - the testing aids line in `AGENTS.md`.

No files outside this repository were touched.

**Git:** no commit, push or other Git operation was performed.
