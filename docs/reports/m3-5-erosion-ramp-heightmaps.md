# Roadmap M3.5: Erosion, a ramp, and heightmap import and export (2026-11-16)

Milestone M3.5 of [milestone-chart.md](../milestone-chart.md) (roadmap R3). **Status:**
- **Implemented and Integrated.** Done-when met in tests, and the ramp seen in the game runtime.
- **Manually Not Verified (Headless Environment):** nobody used the tools or the file dialogs with the mouse.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted work up to M3.4 (listed in its reports).
- No new module, dependency or technical decision. The milestone's gate is "none".
- The `.terrain` format is unchanged: heightmaps are separate files.

**Design choices (mine):**
- **Erosion** is thermal erosion as a brush:
  - where the ground drops more steeply than 0.7 (about 35 degrees) to a neighbour, part of the excess slides down to it;
  - four passes per dab, so a click visibly softens a cliff;
  - ground is moved, never removed.
- **The ramp** is a drag: from where the button goes down to where it comes up, as wide as the brush. It is an even slope from the start's height to the end's, blending into the ground over the outer 40 % of each side.
- **The heightmap** is a 16-bit grayscale PNG with the height range in a text chunk, so Forgeroom's own heightmaps come back at their heights.
  - Import resamples any size of image onto the terrain's grid (bilinear).
  - An image from another tool, which has no range, is read as 0 to 1,000 units.
- **The PNG reader and writer are written here** on `java.util.zip`, in `editor-terrain`.
  - `engine-terrain` is on the browser build's path (`engine-web` uses TeaVM through `engine-render`), so it stays free of desktop-only classes such as `javax.imageio`.
  - `javax.imageio` also cannot round-trip a 16-bit image with a text chunk.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Erosion pass | **Met** | `TerrainErosionRampTest`: a 200-unit cliff is at most half as steep after one stroke, ground piles at its foot, and the total ground is conserved (within 0.5 units over 1,681 points). Ground gentler than the threshold is untouched; far from the brush nothing changes. In the editor it is the Erode tool, one undo step that the file follows (`ErosionRampHeightmapTest`) |
| 2 | Ramp between two points | **Met** | An even slope from one end's height to the other's (within 1 unit at every 40 units along it), blending at the side into the floor, the floor beside it untouched; a click with no drag lays nothing. On the shipped valley (template copy), half way up a ramp from the floor to the left slope is half way between its ends (within 6 units). Ramp tool in the palette, one undo step |
| 3 | 16-bit PNG heightmap | **Met** | Export and import through Tools > Import or Export Heightmap and the palette's Heightmap row. The reader takes 8 or 16-bit gray and colour images written by Java's own PNG encoder, with its row filters; palette and non-PNG files are refused with the reason |
| 4 | Done when: a heightmap round-trips | **Met** | Every 16-bit sample and the range survive exactly. A terrain exported and imported comes back within one 16-bit step of its range, and **re-exports to the identical file**. On the shipped valley (template copy), importing its exported heightmap after a ramp and erosion puts the original valley back to within one step at every point |
| 5 | Done when: ramp and erosion undo | **Met** | Each undoes exactly (every height back); the file follows each undo; import is one undo step too |
| 6 | Agent parity (chart rule 8) | **Met** | `terrain_edit` gains erode, ramp (x, z to x2, z2) and heightmap (a project PNG). The new `terrain_heightmap_export` is read-only (it writes a file, not the scene) and refuses paths outside the project or not ending in `.png`. A plan of erode and ramp is one undo step |
| 7 | UX gate | **Partly** | The ramp is seen in the game runtime; the palette and dialogs were not looked at or clicked |

## 2. What was built

- **`engine-terrain`:** `TerrainBrush.Type.ERODE` and `RAMP`. `TerrainStroke` gains the erosion dab (`TALUS_SLOPE`, `EROSION_RATE`, `EROSION_PASSES`) and the ramp, laid when the stroke ends (`RAMP_EDGE`).
- **`editor-terrain`:**
  - `HeightmapPng` and `Heightmaps`;
  - the Erode and Ramp tools in `LevelSession`;
  - `LevelSession.importHeightmap`, `exportHeightmap` and `getAssetRoot`;
  - `LevelActions.registerHeightmaps` (Import and Export Heightmap, with file dialogs starting in `terrains/`);
  - agent tools: `TerrainEditTool` extended, `TerrainHeightmapExportTool` new.
- **`editor-desktop`:**
  - the editor registers the two actions and the export tool;
  - the level palette has a Heightmap row (Import..., Export...) and a Ramp hint, and runs actions through the registry.
- **The inventory:** `ActionInventoryDump` registers the heightmap actions, `ActionInventoryTest` gives each a home (75 actions), and the audit report's table is refreshed.
- **Docs:** the chart (M3.5 row, R3 line), `INDEX.md`, `FEATURE_TEST_MATRIX.md`, `AI_AGENT_ARCHITECTURE_AUDIT.md`. The M3.4 report records the module re-runs that closed its pending check.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-physics:test`, `:engine-desktop:test`, `:editor-desktop:test` (closing M3.4) | 0 each | 60, 113, 162; recorded in the M3.4 report |
| `./gradlew :engine-terrain:test` | 1, then 0 | One ramp assertion checked its blend on the raised shelf, where blending correctly rises; moved to the low side. Then 43 pass |
| `./gradlew :editor-terrain:test` | 1, 1, 1, 0 | The run sequence: compile slips in my test (`getTool` returns an `Optional`); then the M3.4 test pinning the tool's old error text (now lists the new actions) and my hills being too gentle to erode (made steeper); then a re-export differing in the range's last digit (the top sample now maps exactly to the highest height); then 37 pass |
| Mutation: the writer leaves out the range chunk | 1 | 4 of 6 heightmap tests fail; restored |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*ActionInventoryTest"`, then `./gradlew :editor-desktop:test` | 0, 0 | Table refreshed; 162 pass |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--project <copy of build/m3-5-valley/Valley> --scene scenes/terrain-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)" |

| Module | Before | After |
|---|---|---|
| `engine-terrain` | 39 | 43 (+`TerrainErosionRampTest`) |
| `editor-terrain` | 30 | 37 (+`ErosionRampHeightmapTest`) |
| `editor-desktop` | 162 | 162 (`ActionInventoryTest` updated) |
| Other modules | untouched by M3.5 | last passed in the M3.4 module runs |

**No single full-suite run** this time: the last one was stopped for low memory. Every module M3.5 touches was run on its own and passes.

**Pictures** (looked at):
- [m3-5/runtime-valley-ramp-erosion.png](m3-5/runtime-valley-ramp-erosion.png): the game runtime on the edited valley copy. The ramp is cut into the left slope, a long even track with steep, rock-coloured sides where it meets the hill. The eroded ridge is out of this camera's view, so erosion is evidenced by the tests, not this picture.
- [m3-5/valley-heightmap.png](m3-5/valley-heightmap.png): the exported 16-bit heightmap of the shipped valley, 129 by 129. The valley floor is dark down the middle and the ridges are light on both sides.

## 4. Failures found and fixed

- All in my tests or my first version (section 3); none was a regression.
- The one product fix: imported heights now map the top sample exactly to the highest height, so a re-export is byte-identical.

## 5. Diff review

- **New:**
  - `HeightmapPng`, `Heightmaps`, `TerrainHeightmapExportTool`;
  - tests `TerrainErosionRampTest`, `ErosionRampHeightmapTest`;
  - this report and `docs/reports/m3-5/`.
- **Changed:**
  - `TerrainBrush`, `TerrainStroke`;
  - `LevelSession`, `LevelActions`, `TerrainEditTool`;
  - `LevelPalette`, `EditorApplication`;
  - tests `LayersHolesWaterEditingTest` (error text), `ActionInventoryDump`, `ActionInventoryTest`;
  - the generated table in `world-first-ux-audit.md`;
  - the docs in section 2.
- No shipped asset changed: the evidence is on template copies under `editor-terrain/build/m3-5-valley`.

## 6. Known limitations and next steps

- **Erosion:** thermal only; there is no water-flow (hydraulic) erosion.
- **The ramp:** its ends are square, not blended.
- **Export:** the whole terrain at its own grid; there are no raw, EXR or 8-bit export options.
- **Web:** the player ignores terrain.
- **Manual check for a person:**
  1. Level > Terrain > Erode: drag over a steep hill and watch it soften; Ctrl+Z.
  2. Ramp: drag from a valley floor up a slope; Ctrl+Z.
  3. Heightmap Export...: save a PNG and open it in an image viewer (a grayscale map).
  4. Flatten some ground, then Heightmap Import... the saved PNG: the shape comes back; Ctrl+Z.
  5. Import a heightmap made in another tool.
- **Next in R3:**
  - M3.6 (scatter tools) needs M2b.4.
  - M3.7 (splines) and M3.8 (level of detail for large maps) need only M3.1 and have no gate.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
