# Alpha A02 — World shell (TD-107)

Date: 2026-11-26 · Branch `phase-12-3d-foundation` · HEAD at start and end: `b8bace5` (no commits made).
Decision: [TD-107](../TECHNICAL_DECISIONS.md) (approved by the owner 2026-11-25). Reference: `docs/Screenshot 2026-11-25 113907.png`.

**Status: Implemented and Integrated. Not Validated** — the roadmap's A02 "Done" needs the owner's walkthrough and approved
screenshots; neither has happened. Screenshots below were taken by the agent from the app's own last frame (TD-054).

## What changed

| Area | Change |
|---|---|
| `editor-core` `workspace/WorldShell` | Layout model: style WORLD (default) / CLASSIC, categories (Map, the World strip's sections for the scene's mode, Players, Rules), bar heights, viewport-share check, first-run panel state (panels hidden) |
| `editor-core` `workspace/MinimapMath` | Square minimap bounds of a scene; ground plane (x, z) for 2.5D/3D, (x, y) for 2D; world ↔ minimap mapping |
| `editor-core` `action/HeaderMenus` | Settings gains Panels (Outliner, Details, bottom panel, Console, Assistant, World/Classic switch) and Tools (Compile Scripts, grid, physics debug, reset view, 2D/3D) |
| `editor-core` `file/SceneFileController`, `EditorDialogService` | Saving into a legacy project (format 1) asks once whether to add a project manifest (TD-106); declining saves exactly as before |
| `editor-desktop` `ui/world/` | `SelectionCard` (name, Move, Rotate, Duplicate, first Customize offer), `MinimapView` (dots, selection, view centre, click to centre view), `WorldCategories` (category bar + palette row; Players/Rules say "built in A04/A05", no fake controls) |
| `editor-desktop` `EditorApplication.buildUi` | WORLD branch: thin header (New, Open, Create, Save, Undo, Redo, Project, Settings), map with overlays, category bar, palette row, footer (Genre, Mode, fixes, Customize, Play). No tab row, no tool rail. Panels hidden until opened from Settings → Panels. Separate layout file `workspace_layout_world.json`. Actions `scripts.compile`, `layout.toggle-classic`. Smoke test logs the map's share of the window |
| `editor-desktop` `TinyFileDialogService` | `confirm` (yes/no) dialog |

Classic composition (UI M3b) is unchanged and selectable: Settings → Panels → Switch World / Classic Layout, or
`GDXUNITY_EDITOR_LAYOUT=classic` for one run.

## Acceptance (roadmap A02, TD-107)

| Criterion | Result | Evidence |
|---|---|---|
| Thin header, context indicator | Met (automated + screenshot) | Header 36 px; "World / Landscape (CC0)" chip — `alpha-a02/world-1280x720.png` |
| Dominant viewport (≥ 70 %) | Met at both sizes | Logged by the editor: 1280×720 → 84.7 %; 1920×1080 → 89.6 % (classic: 45.3 %) |
| One active category palette | Met | Palette row shows the level palette for the open section, collapses to 0 px otherwise |
| Persistent Genre/Mode/Customize/Play footer | Met | Screenshots; `ActionInventoryTest` asserts the footer composition |
| Camera choice independent of genre | Met (existing selector reused as a map chip) | Shows the 3D panes' primary view (Isometric/Perspective/axis views); hidden in 2D, where the 2D camera applies |
| Advanced tools discoverable | Met | Settings → Panels / Tools; `HeaderMenusTest` |
| Shortcuts, camera, selection, undo/redo, save preserved | Shortcuts and actions are the same registry; not re-checked by hand | Manually Not Verified (Headless Environment) for interactive use |
| No overlap at target sizes | Met by screenshot only | The Customize label overflowed the selection card's left edge; its width is now capped and it ends in "…" (re-captured `alpha-a02/world-1920x1080-selected.png`) |
| Keyboard focus, drawer input isolation | Not tested in A02 | Gap |
| Human: unfamiliar tester finds terrain, placement, Play, Customize | **Not met** | Owner/tester walkthrough pending |

## Test runs

All with `--max-workers=1 --offline -x generateAssetList`; counts from `build/test-results/test/*.xml`.

| Command | Exit | Result |
|---|---|---|
| `gradlew :editor-core:test --continue` | 0 | 637 tests, 0 failures, 1 skipped |
| `gradlew :editor-desktop:test --continue` (first run) | 1 | Only 19 results written: the run was cut short (machine at ~2 GB free); not a test failure |
| `gradlew :editor-desktop:test --continue` (rerun) | 1 | 220 tests, 1 failure: `ActionInventoryTest` still expected one `createCustomizeGateway()` call; each layout now builds one, so the assertion was updated (TD-107) |
| `gradlew :editor-desktop:test --tests "*ActionInventoryTest"` | 0 | passes; editor-desktop therefore 220/220 |
| `:editor-desktop:run --smoke-test` × 4 (World 1280×720, World 1920×1080 ×2, Classic 1280×720) | 0 | 180 frames, 0 errors each |

Other modules were not touched by A02 and were not rerun.

## Screenshots (agent-captured, not owner-approved)

- [World, 1280×720](alpha-a02/world-1280x720.png)
- [World, 1920×1080, Cabin selected](alpha-a02/world-1920x1080-selected.png)
- [Classic, 1280×720](alpha-a02/classic-1280x720.png)

## Known limitations / next steps

- **Palette row too short (found 2026-11-26 during A03):** with Objects open the 96 px row shows the tool buttons, search and groups but not the thumbnails, which need scrolling; Place/Erase/Scatter labels run together ([screenshot](alpha-a02/world-1280x720-objects-palette.png)). A horizontal layout is proposed rather than a taller row, which would take the map below 70 %.
- Owner walkthrough and screenshot approval (A02 Done).
- Minimap click centring, Players/Rules notes and the selection card's buttons were not exercised by hand.
- No automated layout test for overlap or keyboard focus.
- Classic layout remains until the owner signs off A02.

No commit, push or destructive Git operation was performed.
