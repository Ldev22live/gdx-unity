# M3.11: Terrain in the Web Player

**Date:** 2026-11-17. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart; no decision record (no file format or module changes; the web reads the existing `.terrain` format of TD-082, TD-083 and TD-089).

## 1. Acceptance criteria

| # | Criterion (chart row M3.11) | Status | Evidence |
|---|---|---|---|
| 1 | The Babylon player reads terrain | **Met** | `parseTerrainFile` reads the shipped `Valley.terrain` and refuses the same faults as the desktop's `TerrainIO` (`terrain.test.ts`, 3 tests) |
| 2 | Done-when: "The web player shows the terrain" | **Met** | Headless Chrome on `?scene=terrain-3d.json`: [m3-11/web-terrain-3d.png](m3-11/web-terrain-3d.png) shows the valley, its snowy tops and the hero standing on it with its shadow (looked at). A test builds the sample's ground from its file: 129 by 129 vertices, 128 by 128 cells |
| 3 | (Beyond the row) characters stand on it | **Met in tests** | Terrain Collider 3D on the web's real Jolt: the sample's hero settles on the valley floor and walks up the valley side. Play waits for the terrain file before its first step |

## 2. What was built (`engine-babylon`)

- **`src/terrain/terrain.ts`:**
  - `parseTerrainFile` reads heights, surfaces, paint layers (an older file with fewer layers reads, the rest empty), holes and the water level.
  - `terrainGeometry` matches the desktop's `TerrainMeshBuilder`:
    - the grid is centred on its entity;
    - normals use central differences;
    - Auto ground turns to rock on steep slopes and to snow high up, on the whole terrain's height range;
    - surface colours are partly rocky on steep ground (less so for sand and gravel);
    - paint layers are blended over the base by weight in linear light, then converted to sRGB for the web's materials;
    - no triangle is drawn in a cell touching a hole.
- **`src/runtime/TerrainLoader.ts`:**
  - fetches each Terrain component's file once per path;
  - builds a vertex-coloured mesh, matte and drawn on both sides (so a hole shows the underside), that casts and receives shadows;
  - draws water as a translucent plane in the desktop's colour;
  - reports failures instead of throwing.
- **`SceneBuilder`:**
  - "Terrain" and "TerrainCollider3DComponent" are now supported component types;
  - `BuiltScene` gains `pendingTerrains`, `terrainFailures` and `shadowGenerators`.
- **`ScenePlayer`:**
  - loads terrains with every scene (a failure shows as an asset problem);
  - **Play does not step until the terrain files are in**, so nothing falls before its ground exists.
- **3D physics:**
  - `Physics3D` treats a Terrain Collider 3D as a static triangle mesh. A dynamic one, or one with no Terrain, is refused with the reason.
  - `MeshColliderGeometry` reads only the ground mesh (not the water). It waits while the file loads, and fails if the file could not be read. The ground's triangles already face up, so they are not turned around.
- **Sync and the page:**
  - `scripts/sync-samples.mjs` copies `assets/terrains/*.terrain` to `public/terrains`, which is git-ignored like the other copies.
  - **Testing aid:** `?scene=<sample file>` opens that sample first (`src/main.ts`).
- **Tests:**
  - new `test/terrain.test.ts` (11);
  - in `test/sceneBuilder.test.ts`, the terrain samples no longer list Terrain and its collider as unsupported.

## 3. Commands and results

| Command (in `engine-babylon`) | Exit | Result |
|---|---|---|
| `npx tsc --noEmit` | 0 | no errors (run after each change) |
| `node scripts/sync-samples.mjs` | 0 | "Copied 3 terrains" |
| `npx vitest run test/terrain.test.ts` | 0 | 11 of 11 |
| `npx vitest run --maxWorkers=1` | 0 | **38 files, 492 tests, all passing.** The R0 baseline was 478; the rest came from earlier milestones and these 11 |
| `npx vite --port 5179`, then `chrome --headless=new --use-angle=swiftshader --window-size=1280,800 --virtual-time-budget=15000 --screenshot=... "http://localhost:5179/?scene=terrain-3d.json"` | 0 | the screenshot above; the dev server was stopped afterwards |

No Java code changed in this milestone, so no Gradle run was needed for it.

## 4. Failures found and fixed

None in the new code. Chrome printed unrelated warnings about its own profile and web apps.

## 5. Diff review

- **New:**
  - `src/terrain/terrain.ts`, `src/runtime/TerrainLoader.ts`;
  - `test/terrain.test.ts`;
  - this report and `docs/reports/m3-11/`.
- **Changed:**
  - `src/runtime/SceneBuilder.ts`, `src/runtime/ScenePlayer.ts`, `src/runtime/physics3d/Physics3D.ts`, `src/runtime/physics3d/ColliderGeometry.ts`;
  - `src/main.ts`, `scripts/sync-samples.mjs`, `.gitignore`;
  - `test/sceneBuilder.test.ts`;
  - the docs (chart, INDEX, FEATURE_TEST_MATRIX).
- **Pre-existing uncommitted changes:** `test/sceneBuilder.test.ts` and `test/samples.test.ts` already had them. They were left in place, and only the unsupported list was edited.
- The files the sync script regenerated in `public/` are git-ignored. No files outside this repository were touched.

## 6. Known limitations and next steps

- **No level of detail on the web.** A terrain is one mesh, so the largest map (1025 by 1025) is 2 million triangles. That is fine for the shipped 129 by 129 valley, and heavy for large maps.
- **The collider is different from the desktop's.** It is a triangle mesh in the web's Jolt, where the desktop builds a height field. They give the same surface, but the mesh costs more memory.
- **Not on the web:**
  - Terrain Paths (M3.7) are not applied or drawn;
  - the web editor cannot sculpt or paint terrain;
  - Sky and Weather and Water Body (M3.9) are not drawn.
- **Lighting:** the ground is lit by the web's standard material, so it looks a little flatter and darker than the desktop's PBR.
- **Manual check for a person:** run `npm run dev` in `engine-babylon`, open `?scene=terrain-3d.json`, press Play, and walk the hero with the keyboard. Per the project's plan, visual checks are deferred to the packaged builds.
- **Next in R3:**
  - M3.6 (scatter) waits on M2b.4; M3.10 needs M3.6.
  - The R3 rows that need no other phase are now built.
  - What remains needs R2b (CC0 assets, licence gates) or an owner decision.

## 7. Git

No commit, push, stash, reset or other Git operation was performed.
