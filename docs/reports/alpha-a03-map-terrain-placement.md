# Alpha A03 — Map, terrain and placement: audit and first slice

Date: 2026-11-26 · Branch `phase-12-3d-foundation` · HEAD at start and end: `b8bace5` (no commits made).
Depends on A02 ([report](alpha-a02-world-shell.md)), whose owner walkthrough is still pending; A03 was started on the owner's
instruction to continue with the next milestone.

**Status: in progress.** Slice 1 (entity copy/paste) and slice 2 (placement ghost preview) are Implemented and Integrated; nothing in A03 is Validated.

## 1. Audit against the roadmap's A03 criteria

Source-level audit (what code and automated tests exist). "Exists" does not mean validated in the running editor.

| Criterion | Found | Where | Gap |
|---|---|---|---|
| Terrain raise / lower / smooth / flatten | Exists | `LevelSession.Tool` RAISE, LOWER, SMOOTH, FLATTEN (+ Rough, Hole, Erode, Ramp) | — |
| Paint surfaces | Exists | Tool PAINT ("Ground"), `Surface` | — |
| Brush size / strength, undo per stroke | Exists | `TerrainBrush`, `TerrainStrokeCommand`; `TerrainEditingTest` | — |
| Water, roads, rivers, erosion | Exists | `TerrainWaterCommand`, Tool PATH (`TerrainPath.Kind`), ERODE; `LayersHolesWaterEditingTest`, `TerrainPathEditingTest`, `ErosionRampHeightmapTest` | Visual quality not checked by hand |
| New map | Exists | `LevelSession.createLevel(Surface)`; Map category opens the level editor (A02) | Dimension presets not confirmed in this audit |
| Grid / snap | Exists | `GridSnap`, gizmo snapping | — |
| Lighting settings in Map | Partly | Lighting actions exist (`registerLightingActions`); not grouped under Map | Expose in Map palette (later slice) |
| 2D tile painting | Exists | `TilePaintInteraction` | Collision-aware placement in 2D not audited |
| Searchable thumbnails | Exists | `LevelPalette` (groups, search box, `ModelThumbnails`) | — |
| Place, erase, scatter | Exists | Tools PLACE, ERASE, SCATTER; `StarterPackPlacementTest`, `ScatterPlannerTest`, `CharacterPlacementTest` | — |
| Ghost preview while placing | **Was missing → slice 2** | see §2b | Marks the spot only; no preview of the object's shape |
| Move, rotate, delete, duplicate | Exists | Gizmos, `DeleteEntityCommand`, `DuplicateEntityCommand`; A02 selection card | — |
| Multi-select | Exists | `EditorState.setSelectedEntityIds` | — |
| Copy / paste | **Was missing → slice 1** | see §2 | — |
| Terrain collision, save/reopen | Exists | `TerrainCollision`, `TerrainCollisionTest`, `TerrainIO` | — |
| Licence manifest, offline cache, provider downloads | Exists | `assets/licenses/manifest.json`, Asset Library (UI M5–M7) | — |

## 2. Slice 1 — entity copy and paste

- `editor-core` `command/EntityClipboard`: Copy saves a snapshot of each selected entity: its name, world position, rotation,
  scale, and every component as the scene file writes it. Editing or deleting the originals afterwards doesn't change what gets
  pasted.
- `command/PasteEntitiesCommand`: creates new entities with new ids as **one undo step** and selects them. Redo brings back the same
  entities with their components. Each paste after a copy is moved `PASTE_OFFSET` (2 units) further along the ground: x and z in
  2.5D/3D, so 3D copies stay on the ground, and x and y in 2D.
- `EditorState.copySelectedEntities()` / `pasteEntities()`. Actions `edit.copy` (Ctrl+C) and `edit.paste` (Ctrl+V) are in the Edit
  menu. Paste is enabled only when something has been copied. Neither works during Play. Shortcuts don't fire while a text field
  has focus (existing dispatcher rule), so Ctrl+C/V in search boxes still edit text.
- Limitations: children are not copied (same as Duplicate), and pasted entities go to the top level of the scene. The clipboard
  lives inside the editor (not the operating system's clipboard) and is lost when the editor closes.

## 2b. Slice 2 — placement ghost preview

- `ViewportInteraction` gains default `hovered(...)` and `hoverEnded()`. `SceneViewportWidget` sends mouse moves (no button held)
  and the mouse leaving the view. `ModeSwitchingViewportInteraction` passes the hover to the current mode's interaction and ends it
  in the other one. `LevelViewportInteraction` raycasts the hover to the ground while the Place tool is in hand, then passes the
  hover on.
- `LevelSession.placementRefusal()` holds the Place rules: an object must be chosen, the scene needs ground, and it can have only
  one player and one spawn point. `placeObject` now uses the same check, so **a red preview is always a refused click and a green
  one a placing click**. Hovering reads each character prefab once; a click reads it again.
- `LevelSession.previewPlacement(...)` returns a `PlacementPreview` (the ground point in world space, plus the refusal or null). It
  is cleared when the tool, focus, chosen object or level editor state changes, so it never shows a stale answer.
- `editor-desktop` `PlacementPreviewRenderer` draws the preview in every perspective and axis pane: a ring on the ground that stays
  the same size on screen, with a post rising from it. It is green when the click would place and red with a cross when the click
  would be refused (the cross means colour isn't the only signal).
- Limitations: the preview marks the spot, not the object's shape or size. A refused spot shows no reason until the click (the
  click puts it in the status bar). Hover isn't passed through the Terrain and Sculpt interactions, which come after Level in the
  chain and don't use it.

## 3. Tests

| Command | Exit | Result |
|---|---|---|
| `gradlew :editor-core:test --tests "*PasteEntitiesCommandTest" --tests "*DuplicateEntityCommandTest" --tests "*HeaderMenusTest"` | 0 | 6 + 4 + 3 tests, 0 failures |
| `gradlew :editor-desktop:test --tests "*ActionInventoryTest"` (`GDXUNITY_UPDATE_AUDIT=1`) | 1 → 0 | First run: the registered-action count guard (76) caught the two new actions; count updated to 78 with the reason, homes added. The generated table in [world-first-ux-audit.md](world-first-ux-audit.md) gained exactly two rows |

| `gradlew :editor-terrain:test --continue` | 0 | 67 tests, 0 failures; `CharacterPlacementTest` 8, 4 of them new for the preview |
| `gradlew :editor-core:test --tests "*ModeSwitchingViewportInteractionTest"` + `:editor-desktop:compileJava` | 1 → 0 | First try: missing `ViewportMode` import in the new test; fixed. Then the test passed and editor-desktop compiled |
| `:editor-desktop:run --smoke-test`, 1280×720, `GDXUNITY_EDITOR_DRAWER=Objects` | 0 | 180 frames, 0 errors (Place tool in hand; the overlay path runs, but no mouse means no preview is drawn) |

The preview tests check that a valid preview is followed by a click that places, and a refused one ("is already the player") by a
click that is refused with the same message. They also check that the preview is in world space on an offset ground, that it only
shows while placing, that it is dropped when the tool, object or level editor state changes, and that it is refused with nothing
chosen.

`PasteEntitiesCommandTest` covers paste position in 3D and in 2D, repeated pastes, pasting after the original was deleted,
multi-entity paste as one undo step with redo, refusal with an empty clipboard, and when the actions are enabled. The Play-mode
refusal is the same guard Duplicate uses and has no test of its own.

Full module suites were not rerun after slice 1, so their results aren't known; the most recent full runs (before this slice) were
editor-core 637 tests and editor-desktop 220, see the A02 report.

Manual check: **Manually Not Verified (Headless Environment)**. Ctrl+C / Ctrl+V were not tried in the running editor, and the
ghost preview has not been seen on screen (a smoke test has no mouse).

**A02 finding (from the Objects smoke screenshot, [image](alpha-a02/world-1280x720-objects-palette.png)):** the 96 px palette row
only fits the tool buttons, search box and group filters; the thumbnails need scrolling, and the Place/Erase/Scatter labels run
together. Making the row taller would take the map below 70 % at 1280×720 (71.4 % with the row open). Proposed fix: a horizontal
palette layout (thumbnail strip beside the tool and search column). It is recorded in the A02 report too.

## 4. Horizontal World palette (slice 3)

The World shell constructs LevelPalette in row mode. Classic uses the stacked layout. In the existing [Objects capture](alpha-a03/world-1280x720-objects-row.png), Boulder, Bush and tree thumbnails are visible beside search and filters. Place, Erase and Scatter have separate readable labels. The rightmost tile is partially visible and the strip has a horizontal scrollbar. The [Terrain capture](alpha-a03/world-1280x720-terrain-row.png) shows ground, sky and tool choices side by side. Both captures show the footer below the palette.

This evidence review used the existing working-tree code and captures at 1280x720. Keyboard focus, sideways scrolling to the final control and live placement still need an interactive check. No new application or test run was made for this review.

## 5. Map size and view lighting (slice 4)

World > Map offers 64, 128 (default) and 256 cells per side at the existing terrain spacing. New Level and choosing the initial ground surface use the selected size. Creation uses the existing terrain file, collider and undo command. Existing ground cannot be resized through this control. View lighting dispatches the registered shadow toggle and quality actions to the viewport renderer.

Verification at HEAD b8bace5 (start and end): `.\gradlew.bat :editor-terrain:test --tests "*LevelEditingTest" :editor-desktop:compileJava --offline --max-workers=1` exited 0. LevelEditingTest previously recorded 15 tests; this run recorded 16 tests, 0 failures, 0 errors and 0 skips. Desktop compilation passed. The full suite was not run.

Interactive checks still required: create each map size, save and reopen, and switch lighting in a lit 3D scene.

## 6. Remaining walkthrough

1. Verify the Map controls in the running desktop editor.
2. Human check: create a valley, paint two surfaces, place a tree, rock and house, select and move them (the roadmap's A03 human
   test).

No commit, push or destructive Git operation was performed.
