# Roadmap R3 — Landscape and Terrain Tools: Report, Part 1 (2026-11-14)

Phase R3 of [13-nov-forgeroom-roadmap.md](../13-nov-forgeroom-roadmap.md). **Status: partly done.** The terrain asset, its rendering and brush
editing exist. Collision, paint layers, scatter, splines, water, level of detail and the web player do not.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `d896331`. Ending: `d896331` (no commit was made).
- The working tree already held many uncommitted files from the owner. Files this part touched that already had owner changes:
  `EditorApplication.java`, `AGENTS.md`, `TECHNICAL_DECISIONS.md`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `SceneRenderer3D.java`, `settings.gradle`.
- **Approval:** the roadmap and AGENTS.md section 4 require approval for new modules. The owner chose "Approve modules, start R3" (2026-11-14).
  Recorded as TD-082 (Approved). The owner also left R2's Material Editor tab, glTF model materials and web support open.

## 1. Acceptance criteria (roadmap R3)

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | `.terrain` asset: heightmap grid, resolution and size, splat layers, holes, water level; chunked mesh with level of detail | **Partly met** | Grid, size and heights round-trip exactly; chunked mesh, no level of detail; no layers, holes or water |
| 2 | Tools: raise, lower, smooth, flatten, noise, erosion, ramp, paint, erase, PNG import and export, undo per stroke | **Partly met** | Raise, lower, smooth, flatten and noise with one undo step per stroke; the rest not done |
| 3 | Scatter tools | **Not met** | Not started |
| 4 | Splines | **Not met** | Not started |
| 5 | Water body, sky and weather presets, fog | **Not met** | Not started |
| 6 | Collision with 2D and 3D physics and the character controller | **Not met** | Nothing knows the terrain: nothing stands on it |
| 7 | Build on the sculpt core, no second brush system | **Partly met** | The interaction and undo pattern follow `editor-sculpt`; the brushes are their own, as a heightmap is not a mesh |
| 8 | Convert the five landscape samples | **Not met** | They are still glTF models |
| 9 | Done when: open `landscape-3d`, reshape a valley, repaint a slope, scatter pines, save, play, walk on it | **Not met** | Reshape and save are there for the new `terrain-3d` sample; repaint, scatter and walking are not |

## 2. What was done

- **`engine-terrain`** (new, headless): the heightmap with chunk revisions, ray picking, five brushes, the file, the shared library, generators.
- **`engine-render`:** `TerrainComponent`, chunk meshes with vertex colours from slope and height (grass, rock, snow), rebuilt only for chunks a
  stroke touched; shared by the editor viewport and Play.
- **`editor-terrain`** (new, headless): the session, undoable strokes that rewrite the file, the viewport interaction wrapped around every 3D pane.
- **`editor-desktop`:** a Terrain Details section (file, New Hills, New Flat, Use Selected, Paint toggle, tool, radius, strength).
- **Sample:** `terrain-3d.json` with `Valley.terrain`.
- **Bug found by the first test run:** a ray straight up (or down) never leaves the terrain's footprint, so the ray march had no end and the test run hung.
  `Terrain.raycast` now answers vertical rays from the height under them.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-terrain:test :editor-terrain:test` | 0 | 25 and 7 tests, 0 failures |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew test --continue` | 0 | Per module: `engine-terrain` 25, `editor-terrain` 7, `engine-render` 142, `editor-core` 396 (1 skipped), `editor-desktop` 116, `engine-desktop` 98; 0 failures |
| `./gradlew :engine-render:test --tests "*TerrainRenderingTest" :editor-desktop:test --tests "*SampleGamesTest" --tests "*EditabilityAuditTest"` | 0 | after the sample's camera and the snow line were adjusted |
| `./gradlew :engine-desktop:run --args="--scene samples/terrain-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)"; one frame looked at |
| `npx vitest run` in `engine-babylon` | 0 | 478 tests, 0 failures (the sample is listed as unsupported there) |

## 4. Known limits and what is next

- **Not seen in the running editor** (**Manually Not Verified (Headless Environment)**): painting, the Details section, the terrain in the editor viewport.
  The headless interaction test drives the same code with a synthetic mouse.
- **Next, in order of value:** collision (Jolt height field, Box2D slopes, character controller), paint layers with splat weights, a brush outline,
  click-to-select, water, scatter, splines, PNG heightmap import and export, level of detail, then the web player and converting the landscape samples.
- Dabs are distance-based, so holding still adds nothing after the first dab; a time-based build-up may suit raising mountains better.
- Strokes rewrite the whole file; large maps (1025 by 1025 is about 5.6 MB) will want a smarter save.
- R2's remaining items (Material Editor tab, glTF materials, web) and R1's (templates, "used by", graph conversions) were not touched.

## 5. Git

No commit, push, stash, reset or branch change was performed.
