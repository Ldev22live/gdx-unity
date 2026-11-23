# M2b.4: Collision for Placed Objects, Measured Frame Cost, and the Instancing and LOD Plan

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start:** `0469ceb`. Mid-milestone, at the owner's request, the working tree was committed and pushed as `2391dad`, M2b.4's unfinished state included and labelled as such. **HEAD at end:** `2391dad`. Everything after that commit is uncommitted.

Roadmap milestone **M2b.4** of R2b, after M2b.3. The chart lists no gate. Decision record: [TD-096](../TECHNICAL_DECISIONS.md), recorded for the owner's review.

**Status: Implemented and Integrated.** Every Objects and Buildings prefab that should block now carries simple static colliders: trunks, rocks, walls, roofs. The CC0 House's doorway stays open, and the Bush is walked through. Players stop at them in editor Play on real Jolt. The frame cost of a level full of them is measured in the game runtime.
**Manually Not Verified:** no person has walked into an object with the keyboard.

## 1. Acceptance criteria

The chart's deliverable is "collision authoring, scene-aware transforms, prefab overrides, performance measurements". Its done-when is "a realistic asset has gameplay collision and measured frame cost".

| Criterion | Result | Evidence |
|---|---|---|
| Collision authoring | **Met** | **Where the colliders live:** in the prefab, on the part they belong to. The generators write them: `tools/make_level_prefabs.py` for the shape-built objects, `tools/make_starter_pack.py` for the CC0 pack. Each collider is an ordinary component, editable in Details and with Edit Shared Prefab.<br>**Kenney model sizes:** measured from the meshes' vertices and kept as named constants:<br>- the oak's trunk is about 0.1 m round, up to 0.45 m;<br>- the tall rock is 0.98 × 1.0 × 0.68 m;<br>- the doorway's opening is 1 × 2.1 m. |
| Gameplay collision of a realistic asset (done-when) | **Met** | `PlacedObjectCollisionTest` (3 tests, `editor-desktop`, editor Play as Action RPG + 3D on real Jolt). The test places each palette object in front of the player and holds W for 2 s.<br>**Closest the player's centre came to each object's centre** (the player is 30 round):<br>- Oak Tree 48, Pine Tree 45, CC0 Oak Tree 83;<br>- Rock 77, Boulder 121, CC0 Rock 88;<br>- Cottage 182, Watchtower 142, CC0 House 242.<br>With nothing in the way, the player walks 800 units, through the object's place. The bush is walked through.<br>**The CC0 House:** its window wall stops the player outside. Through the doorway the player walks in and stops at the back wall, without being pushed aside. |
| Measured frame cost (done-when) | **Met** | **The level:** the test writes 223 placed objects as a project, `editor-desktop/build/m2b-4-placed-objects`: 81 CC0 oaks, 45 CC0 rocks, 45 pines, 44 boulders and 8 CC0 houses. That is 822 entities, 448 of them with colliders.<br>**Physics** (editor Play, no drawing): 0.6 s to start; a step's median 0.82 ms, p95 3.1 ms.<br>**Drawing** (game runtime, GPU-finished frame times, Intel Iris Xe, one run each): 14.9 ms a frame. The same level with the objects removed draws in 3.5 ms. Frame: [placed-objects-runtime.png](m2b-4/placed-objects-runtime.png). |
| Scene-aware transforms | **Already there; the rest planned** | The level editor stands an object's foot on the ground and turns and scales it a little at random (TD-083). Slope alignment belongs to scatter (M3.6), in TD-096's plan. |
| Prefab overrides | **Already there** | `PrefabInstanceComponent` overrides (TD-027) cover colliders like any property. Placed copies follow the project's prefab when a scene opens (`PrefabService.syncScene`). |
| Instancing and LOD plan | **Written** | TD-096. **The measurement says drawing limits object counts, not physics.** M3.6 starts with batching by mesh and material plus frustum and distance culling: GL ES 2.0 and `ModelBatch` have no hardware instancing. Lower-detail models per prefab come later. |

## 2. What was built

**Changed:**
- **`tools/make_level_prefabs.py`:** `collide=SOLID` (a box of the part's size) or `collide=HULL` (a convex hull of the part's shape). Trunks, rocks, the cottage's walls, roof and chimney, and the watchtower's stone are solid. Crowns, the bush, doors and windows have no collider.
- **`tools/make_starter_pack.py`:** measured constants, `box` and `capsule` helpers, and collision-only children for the doorway.
- **The nine prefabs they regenerate:** Bush and the characters are unchanged. Before editing, the scripts were checked to reproduce the existing files byte for byte, so the colliders are the only change.
- **Tests:**
  - `ObjectPaletteTest` (+2): every blocking object has a static collider of a simple shape and no rigid body; the house's doorway is open and every other wall piece blocks.
  - `StarterPackPlacementTest`: the house now places 20 entities, its 3 doorway colliders included.
  - `OwnerJourneyRegressionTest`: the scene is given the collider geometry source, as the editor's renderer gives it (see Finding 1).

**New:**
- `PlacedObjectCollisionTest`;
- TD-096;
- this report and its frame.

**Choices made without asking** (all in TD-096):
- **Simple shapes, never mesh colliders.** They are cheap with hundreds of copies, run on any body and on the web player, and need no GL to build.
- **What blocks:** trees block at the trunk only, and plants do not block.
- **The doorway's colliders are child entities,** one box each, rather than three colliders on one entity, so each shows and edits on its own.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `python tools/make_level_prefabs.py` and `python tools/make_starter_pack.py` before editing | 0 | The existing prefabs reproduced byte for byte (md5) |
| `./gradlew :editor-core:test --tests "*ObjectPaletteTest" --tests "*StarterPackTest"` | 0 | 7 and 5 tests passing |
| `./gradlew --offline --max-workers=1 :editor-desktop:test --tests "*PlacedObjectCollisionTest"` (`JAVA_TOOL_OPTIONS=-Xmx600m`) | 0 | 3 tests passing, after two test fixes (below) |
| The same with every 3D collider stripped from the shipped prefabs (mutation check), then the generators rerun | 1, then restored | Both walking tests fail: the player passes 0.0002 from the Oak Tree's centre and walks through the house's window wall. The prefabs were restored identical to the commit. |
| `GDXUNITY_RENDER_STATS=1 ./gradlew :engine-desktop:run --args="--project <build>/m2b-4-placed-objects --scene scenes/level.json --smoke-test=330"` | 0 | 330 frames, 0 errors, 14.94 ms a frame |
| The same with `scenes/empty.json` (the objects removed) | 0 | 330 frames, 0 errors, 3.47 ms a frame |
| `:editor-terrain:test --tests "*StarterPackPlacementTest"`, `:editor-desktop:test --tests "*OwnerJourneyRegressionTest" "*ScenarioCSideScrollerTest" "*TemplateGalleryProjectsTest" "*ComponentConsistencyTest"` | 1 | 2 failures, both caused by this change: the house's entity count, and the journey's frozen player |
| The two again after their fixes | 0 | 3 and 1 tests passing. ScenarioC (1), TemplateGalleryProjects (2) and ComponentConsistency (5) passed in the first run |

**Two fixes to the new test before it passed:**
- **A camera.** Play refuses a scene without one.
- **The check.** Objects are placed at a random turn, so the Oak Tree's square trunk met the player corner-first and the player slid past it. That is correct gameplay. The test now checks the closest approach to the object's centre instead of where the player stopped.

### Full module runs

**Not done.** Gradle runs were stopped by the host for low memory three times in this session (about 1.2 to 1.9 GB free). Single classes ran with one worker and a capped test heap. Still pending for M2b.2, M2b.3 and M2b.4:
- full `editor-core`, `editor-terrain` and `editor-desktop` runs;
- the package rebuild.

**Babylon suite:** not run. No component type, graph node or scene-format change; the colliders used are all "Works" on the web in `BACKEND_SUPPORT_MATRIX.md`.

## 4. Findings

1. **A hull collider without a geometry source freezes Play silently.** In the journey test the Boulder's new hull could not be built, because the test's scene had no `ColliderGeometrySource`. Play started, but the player never moved. The running editor always registers the source through its renderer, so users are not affected. Still, a failure to build physics should stop Play with a message. Left for a follow-up; not changed here.
2. **Existing projects keep their old prefab files.** A project copies the palette's prefabs when it is made. Projects made before today get colliders only when those files are updated.

## 5. Known limitations and next steps

**Limitations:**
- One timed run per scene on one machine, so the numbers are a starting point, not a baseline. `perfBaseline` was not extended.
- No GPU instancing or LOD was built. TD-096 plans them for M3.6.
- Colliders on the shape-built trunks are boxes, so a turned trunk deflects the player at its corner.

**Next on the chart:**
- **M3.6:** scatter, now unblocked;
- **M2b.5:** the 2D CC0 catalog;
- **M2b.6, M2b.7:** sample migration and the web manifest matrix.

## 6. Diff review and Git

**Changed in this milestone:**
- the two generator scripts, and the nine prefabs they write;
- tests: `ObjectPaletteTest`, `StarterPackPlacementTest`, `OwnerJourneyRegressionTest`;
- docs: `TECHNICAL_DECISIONS.md` (TD-096), `FEATURE_TEST_MATRIX.md`, `INDEX.md`, both chart copies.

**New:** `PlacedObjectCollisionTest`, this report, `docs/reports/m2b-4/`.

**Git:**
- At the owner's request, the working tree was committed and pushed as `2391dad` on `phase-12-3d-foundation`. That commit left out the raw Kenney downloads at the top of `assets/` and `urconnex-ai-agent-roadmap.md`.
- The work after that commit is **uncommitted**:
  - `PlacedObjectCollisionTest`'s fixes;
  - the `StarterPackPlacementTest` and journey changes;
  - TD-096's measured numbers;
  - the measurements;
  - this report and the doc updates.
- No other Git operation was performed.
