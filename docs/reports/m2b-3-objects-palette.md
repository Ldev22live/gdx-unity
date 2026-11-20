# M2b.3: Objects Palette with Thumbnails, Groups, Search and Licences

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `0469ceb` (nothing committed)

Roadmap milestone **M2b.3** of R2b, after M2b.2 (the CC0 starter pack). It needed no gate.

**Status: Implemented and Integrated.** With Place in hand, the World strip's **Objects** and **Buildings** drawers now show their prefabs as thumbnail tiles. The tiles come with:
- groups (Trees, Rocks, Plants, Buildings);
- a search box;
- the chosen object's licence line.

**Manually Not Verified:** no person has clicked a tile or typed a search.

## 1. Acceptance criteria

The deliverable and done-when are the chart's, with the earlier finding H2 of the owner's session.

| Criterion | Result | Evidence |
|---|---|---|
| Searchable thumbnails | **Met** | **Thumbnails:** every Objects and Buildings prefab has one, `<Name>.png` beside it. `tools/make_palette_thumbnails.py` renders all of them the same way: the prefab alone in the game runtime, framed from its size, with the centre cropped to 128 pixels. That covers the shape-built originals and the CC0 pack alike. **Search:** live as you type; every word must match the name or the group. **Tests:** `ObjectPaletteTest` checks that every shipped object has its thumbnail, so a new prefab without one fails, and checks the search. Screenshots: [objects-palette.png](m2b-3/objects-palette.png), [buildings-palette.png](m2b-3/buildings-palette.png). |
| Trees, Rocks, Plants, Buildings | **Met** | **Rule:** the group comes from the folder (Buildings, Characters) or the name (tree, pine, oak, palm, fir; rock, boulder, stone, cliff; bush, plant, flower, fern, grass, shrub, mushroom); anything else is Other. **Buttons:** All, plus one per group present. **Tested:** every shipped object has a group, none is Other. **Proved:** with the name rule switched off, 2 of 5 tests fail. |
| Licence detail | **Met** | **The line** sits under the tiles and in each tile's tool tip. It comes from `licenses/manifest.json` for the files the prefab uses. The CC0 Oak Tree reads "CC0 1.0 · Nature Kit by Kenney (kenney.nl)", the CC0 House "CC0 1.0 · Building Kit by Kenney (kenney.nl)", and the shape-built objects "Made in ForgeRoom (no third-party files)". Tested and seen. |
| Place | **Met** | A tile chooses the object, and a click on the ground places it, as before. |
| Edit 3D Model where supported | **Already there** | Once an object is placed, Customize offers Edit 3D Model for a model (M3c.4). The palette adds nothing of its own. |
| Done-when: place from the palette with undo | **Met** | `StarterPackPlacementTest.shouldPlaceFromThePaletteAsOneUndoStep`: the CC0 House, its root and 16 pieces, comes in from Buildings and goes again with one undo. |
| Finding H2 (a new project's palette is empty) | **Met** (since M3c.5) | A new 3D or 2.5D project gets the palette, now with its thumbnails too. `StarterPalette.files` includes them, and they are tested in `StarterPackTest`. |

## 2. What was built

**New:**
- **`editor-core` `ObjectPalette`:** groups, thumbnails, licence lines and filtering, headless.
- **`tools/make_palette_thumbnails.py`,** and the ten thumbnails it rendered: Boulder, Bush, CC0 Oak Tree, CC0 Rock, Oak Tree, Pine Tree, Rock, CC0 House, Cottage and Watchtower.
- **Tests:** `ObjectPaletteTest` (5), and `StarterPackPlacementTest` (+1, undo).

**Changed:**
- **`editor-desktop` `LevelPalette`:**
  - the object browser for Place in Objects and Buildings: search, groups, a 4-wide grid of 64-pixel tiles with the chosen one checked, and the licence line;
  - thumbnail textures owned and disposed (`dispose`, called by `EditorApplication`);
  - the search and group start empty in each drawer.
  - Characters keeps its two name buttons.
- **`StarterPalette.files`:** includes each prefab's thumbnail.

**Choices made without asking:**
- **Groups come from names and folders** rather than a new prefab field, so no shipped prefab had to change.
- **Characters gets no thumbnails.** The Spawn Point draws nothing in the game runtime, so it would render an empty picture.
- **Thumbnails are pre-rendered files,** not drawn live in the editor. They are cheap to show and the same everywhere.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `python tools/make_palette_thumbnails.py`, run in batches because each render takes 1 to 3 minutes while memory is short | 0 | 10 thumbnails written |
| `./gradlew :editor-core:test --tests "*ObjectPaletteTest" --tests "*StarterPackTest" --tests "*TemplateGalleryTest"` | 0 | 5, 5 and 13 tests, all passing |
| `./gradlew :editor-terrain:test --tests "*StarterPackPlacementTest"` | 0 | 3 tests |
| The mutation check | 1 | As listed; restored |
| `APPDATA=<empty> GDXUNITY_EDITOR_DRAWER=Objects` (then `Buildings`) `./gradlew :editor-desktop:run --args="--scene samples/action-rpg-valley-3d.json --smoke-test=240"` | 0 | 240 frames, 0 errors; the screenshots |

**Fixed after the screenshots:**
- the group buttons ran together, so 12-pixel gaps were added;
- "Watchtower" broke inside the word, so the tiles were widened to 98 pixels.

### Full module runs

**Not done.** The last two attempts, in M8 and M2b.2, were stopped by the system for low memory, and are not restarted unasked. Still pending for M2b.2 and M2b.3:
- full `editor-core`, `editor-terrain` and `editor-desktop` runs;
- the package rebuild.

`editor-desktop`'s inventory and audit tests may need their generated documents brought up to date for the new prefabs, thumbnails and imported Kenney files.

## 4. Known limitations and next steps

**Limitations:**
- **No thumbnails for Characters,** and none drawn live: a changed prefab needs the tool run again.
- **Groups are by name** unless a prefab sits in Buildings or Characters. A tree called "Maple" would be Other until the word list grows.
- **The owner's raw Kenney downloads are still in `assets/`;** see the M2b.2 report. They are waiting for the owner's decision.

**Next on the chart:**
- **M2b.4:** collision, instancing and LOD for the starter models;
- **M3.6:** scatter, which uses this palette's prefabs.

## 5. Diff review and Git

**New:**
- `ObjectPalette`, `ObjectPaletteTest`, `tools/make_palette_thumbnails.py`;
- the ten thumbnails;
- this report and `docs/reports/m2b-3/`.

**Changed:**
- code: `LevelPalette`, `EditorApplication` (disposal), `StarterPalette`;
- tests: `StarterPackTest`, `StarterPackPlacementTest`;
- docs: `INDEX.md`, `FEATURE_TEST_MATRIX.md`, both charts.

**Git:** no commit, push or other Git operation was performed.
