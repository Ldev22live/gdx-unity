# Roadmap M3.8: Level of detail for large maps (2026-11-16)

Milestone M3.8 of [milestone-chart.md](../milestone-chart.md) (roadmap R3). **Status:**
- **Implemented and Integrated, with recorded frame times.** On the largest map a terrain can be, a frame costs **14.4 ms** with level of detail and 27.6 ms without (medians of three runs each, Windows 11). That is within the 16.7 ms a frame has at 60 frames a second.
- **Manually Not Verified (Headless Environment):** nobody flew over the map. The numbers come from the game runtime's own timed smoke runs.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted work up to M3.7 (listed in its reports).
- No new module, dependency, decision or file format.

**How "the recorded frame time" is read.** The chart does not say what time to hold, so I recorded three things on the same machine:
- the largest map without level of detail;
- the largest map with it;
- the small shipped valley.

"Holds" is taken as: the large map stays inside a 60-frames-a-second frame (16.7 ms). Frame cost is measured from the start of a frame until the GPU has finished it (`glFinish`), because with vsync the plain frame time is pinned to 16.7 ms and hides the cost.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Chunk level of detail | **Met** | A chunk is drawn with every grid point within 2 chunk widths of the camera, then every 2nd, 4th or 8th point as the distance doubles (`TerrainLod`). Its triangles are rebuilt only when its level or its terrain changes. `TerrainLodTest`: a quarter of the triangles per level; a short edge chunk still reaches its edge; a coarse block with a hole is left out; far chunks draw fewer triangles than near ones; without a camera, every chunk is whole |
| 2 | No gaps between levels | **Met** | Every chunk edge has a skirt hanging below the chunk's whole border by its height range plus a cell (deeper than any gap a coarser neighbour can leave; tested). The screenshots with and without level of detail show the same skyline and hills with no gaps |
| 3 | A large map renders | **Met** | `LargeTerrainMapTest` writes the largest map, 1025 by 1025 points 10 units apart (10 km, about 1,000 chunks) of hills, with a camera at one edge looking across, and plays it in the game runtime's session. The game runtime ran it 300 frames with 0 errors, with level of detail both off and on |
| 4 | Done when: a large map holds the recorded frame time | **Met** | Table below |
| 5 | UX gate | **Partly** | Screenshots looked at; nobody moved the camera |

| Run (game runtime, `GDXUNITY_RENDER_STATS=1`, 300 frames, 30 warm-up frames left out) | Terrain triangles per frame | Frame cost (GPU finished), three alternating runs | Median |
|---|---|---|---|
| Largest map, level of detail off (`GDXUNITY_TERRAIN_LOD=0`) | 2,097,152 | 39.16, 27.57, 26.65 ms | **27.6 ms** |
| Largest map, level of detail on | 94,784 | 14.38, 13.02, 14.48 ms | **14.4 ms** |
| Shipped valley (129 by 129), on | 21,504 | 6.97, 5.72, 6.41 ms | 6.4 ms |

The final version was reached in three steps, each measured on the same map. These runs are recorded too, because they show what costs time on this machine:
1. **Level of detail alone:** 127,488 triangles, with 21.0 ms first. Over five runs it varied from 16.2 to 26.3 ms, a median of about 23.5 ms against 27.6 ms off.
   - That is 16 times fewer triangles for about 15 % less time, so triangles were no longer the cost.
2. **Plus chunks out of view not drawn:** 94,784 triangles, still about 16 to 24 ms.
3. **Plus only chunks inside the shadow map's area drawn into the shadow map:** 13 to 14.5 ms, steady.
   - The shadow map covers a small area around the camera, yet every chunk in view was being drawn into it: about 500 draw calls for nothing.

## 2. What was built

- **`engine-render`:**
  - `TerrainLod`.
  - `TerrainMeshBuilder`:
    - `vertices` adds skirt vertices (the four edges again, lowered);
    - `vertexCount`, `indices(..., step)`, `skirtIndices`, `maxSkirtIndices`.
  - `TerrainGpu`:
    - `sync(cameraLocal)` keeps a level per chunk; `sync()` stays full detail without skirts;
    - each chunk's bounding sphere, `chunkCount`, `chunkCenter`, `chunkRadius`, `triangleCount`.
  - `SceneRenderer3D`:
    - level of detail and view culling, on by default (`setTerrainLod`);
    - the camera is moved into each terrain's own space with scratch objects, so there is no allocation per frame;
    - terrain chunks are kept in their own list with their spheres, so the shadow pass skips those outside the shadow camera's frustum;
    - `getTerrainTriangles`.
- **`engine-desktop`:**
  - `GameRenderer`: render statistics (`setRenderStats`, averages after 30 warm-up frames) and `setTerrainLod`. It reads no environment variable itself, because the browser build shares it.
  - `DesktopLauncher`: reads `GDXUNITY_RENDER_STATS` and `GDXUNITY_TERRAIN_LOD`, and prints "Render stats: ..." at the end of a smoke run.
- **Docs:** the chart (M3.8 row, R3 line), `INDEX.md`, `FEATURE_TEST_MATRIX.md`, AGENTS.md's testing aids.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-render:test` | 1, 0 | Four older tests sized their vertex arrays for the grid only; the vertices now include the skirts, so the arrays are sized with `vertexCount` (the grid part is unchanged). Then 178 pass (+5 `TerrainLodTest`) |
| `./gradlew :engine-desktop:test` | 1, 0 | Wrong packages in my new test; fixed. Then 114 (+1 `LargeTerrainMapTest`) |
| `./gradlew :editor-desktop:test` | 0 | 163 |
| The timed runs in section 1 | 0 each | "300 of 300 frames, 0 runtime error(s)" every time |
| `GDXUNITY_SCREENSHOT=...` with `GDXUNITY_TERRAIN_LOD=0`, then on | 0, 0 | 120 frames, 0 errors each |

**Screenshots** (looked at), from the same camera:
- [m3-8/runtime-large-map-lod-off.png](m3-8/runtime-large-map-lod-off.png): every chunk at full detail.
- [m3-8/runtime-large-map-lod-on.png](m3-8/runtime-large-map-lod-on.png): level of detail on.

The skyline, the hills and the snow on the far summit are the same, and there is no gap. On the distant snowy summit, the second shows a few faint, thin, vertical light lines at chunk borders: skirts lit like the snow they hang from.

## 4. Failures found and fixed

- **The first version did not hold the frame:** 16 times fewer triangles saved only about 15 %. Measuring step by step (section 1) found the draw calls, mainly the shadow pass drawing every chunk in view. Culling chunks out of view, then culling the shadow pass to the shadow map's area, brought the frame to 14.4 ms.
- The test fixes listed in section 3.

## 5. Diff review

- **New:**
  - `TerrainLod`;
  - tests `TerrainLodTest`, `LargeTerrainMapTest`;
  - this report and `docs/reports/m3-8/`.
- **Changed:**
  - `TerrainMeshBuilder`, `TerrainGpu`, `SceneRenderer3D`;
  - `GameRenderer`, `DesktopLauncher`;
  - `TerrainRenderingTest` (array sizes);
  - the docs listed in section 2.
- The large map is written to `engine-desktop/build/m3-8-large-map` by its test; no asset is shipped. No files outside this repository were touched.

## 6. Known limitations and next steps

- **Skirts:** faint lines at a few far chunk borders on bright ground.
- **Shadows:** a chunk culled from the shadow pass casts no shadow into the view. That matters only for a hill just outside the shadow map's area.
- **One machine:** the timings come from this Windows 11 machine, with other work running; single runs varied by up to 40 %, so medians are reported. Linux and macOS are not measured.
- **Coverage:** no level of detail for models; the web player ignores terrain.
- **Where it applies:** the editor's 3D view uses the same renderer, so level of detail is on there too. A stroke on a far chunk shows at that chunk's level until the camera comes nearer.
- **Manual check for a person:**
  1. Run the large map: `./gradlew :engine-desktop:run --args="--project <engine-desktop/build/m3-8-large-map> --scene scenes/Large.json"`.
  2. Watch for popping and gaps while the camera moves. It does not move in this scene; a fly camera is needed.
- **Next in R3:**
  - M3.9 (water presets, sky, weather, fog) needs M3.4 (done) and has no gate.
  - M3.6 waits on M2b.4; M3.10 needs M3.6; M3.11 is the web player.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
