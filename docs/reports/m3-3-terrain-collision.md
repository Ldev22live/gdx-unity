# Roadmap M3.3: Terrain Collision (2026-11-15)

Milestone M3.3 of [milestone-chart.md](../milestone-chart.md) (roadmap R3), built to [TD-087](../TECHNICAL_DECISIONS.md), which the owner
approved on 2026-11-15. **Status:**
- **Implemented and Integrated.**
- **The done-when is met in automated tests on the real physics engine.**
- **Manually Not Verified (Headless Environment):** nobody has walked Hero with the keyboard.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held only this session's TD-087 changes (`docs/TECHNICAL_DECISIONS.md`, `docs/milestone-chart.md`).
- Approvals: TD-087 (approved 2026-11-15). That approval also dropped "slopes for 2D" from M3.3 and answered decision 12 ("before").
- No new module and no new dependency: Jolt's height field is part of jolt-jni 6.2.0, which the project already uses.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Jolt height field for 3D | **Met** | `TerrainCollider3DOnJoltTest`: a ball comes to rest on it, and raycasts hit within 1 cm (TD-087's bound) of the terrain at 196 points of a 6 m-high hill field |
| 2 | Slopes for 2D | **Dropped** | Removed from M3.3 with TD-087's approval: there is no 2D terrain asset |
| 3 | The character controller walks on it (TD-049) | **Met** | A character climbs a 20 degree slope and is stopped by a 60 degree one; it jumps and lands; it slides more than 1 m down a slope steeper than its 45 degree limit |
| 4 | Done when: a character walks, jumps and slides on a reshaped valley | **Met in automated tests** | Walking and jumping use the shipped valley (`TerrainValleyOnJoltTest`). Sliding uses a synthetic 60 degree slope, because the shipped valley has no slope steeper than 45 degrees |
| 5 | TD-087: new terrains are walkable without a setup step | **Met** | New Level, New Hills and New Flat add the collider in the same undo step (`TerrainCollisionTest`) |
| 6 | TD-087: rebuilt when the terrain changes while physics runs | **Met** | The body is rebuilt after a revision change and only then (`TerrainCollider3DTest`, `TerrainCollider3DOnJoltTest`) |

The criterion 4 test (`TerrainValleyOnJoltTest`) works as follows:
- It loads `terrain-3d.json` and raises a smooth 60-unit bump on Hero's path.
- Hero walks 6 m and jumps once.
- At no frame does Hero sink more than a step into the ground. While grounded after the first second, Hero stays within a step of it.

**Mutation check:** I removed the height field from the bodies `PhysicsSystem3D` builds. Both `TerrainValleyOnJoltTest` tests then failed: "on the ground after 4 seconds" was false, and Hero sank 33 units by frame 13. The original was restored.

## 2. What was built

- **`engine-scene`:**
  - `TerrainCollider3DComponent` and its serializer (type id `TerrainCollider3DComponent`, following the other colliders' ids; TD-087's text said `TerrainCollider3D`).
  - `HeightField3D`: a grid of heights, where `NaN` means a hole.
  - `HeightFieldSource` and `Scene.setHeightFields`.
  - `ShapeDescriptor3D.Kind.HEIGHT_FIELD`.
  - `PhysicsSystem3D`:
    - it builds a static body in meters, with the entity's scale;
    - it refuses a moving body or a scene with no source, giving the reason;
    - each step it compares the terrain's revision and rebuilds only that body when the revision changed.
  - A trigger is refused when it is set.
- **`engine-physics`:**
  - Jolt's `HeightFieldShapeSettings`, with block size 2 and 8 bits per sample, the most precise Jolt allows.
  - A terrain that is not square is padded to a square with no-collision points.
- **`engine-render`:** `TerrainHeightFields` reads the same `TerrainLibrary` the renderer draws from. Its revision changes on every edit and on a reload. `SceneRenderer3D` registers it beside `ColliderGeometry`, so editor Play and the game runtime get the same source.
- **Editor:**
  - New Level, and the Terrain Details' New Hills and New Flat, add the collider in the same undo step (`TerrainCollision`).
  - Add Component > Physics 3D > Terrain Collider 3D, with refusals: no Terrain, or a moving body.
  - Details shows the Offset row only.
  - The collider overlay draws nothing for it, because the terrain itself is the ground.
- **Sample `terrain-3d.json`:**
  - Valley has the collider.
  - New: Hero, a Character Controller 3D, starting on the valley floor.
  - The camera follows Hero in Play.
  - Hero is walked by `graphs/samples/ValleyWalker.graph`, generated from the character playground's `WalkerPlayer.graph` with only its name and hint changed. The sample's logic is therefore a graph, not Java, and it stays off the Java-only list.
  - `tools/make_terrain_samples.py` writes both files. `Valley.terrain` is unchanged.
- **Web player tests:**
  - `terrain-3d.json` now lists `TerrainCollider3DComponent` as known unsupported.
  - The sample moves from "ported" to a named desktop-only list, because the web's graph runtime refuses character nodes. A new test checks that it still refuses them, so the entry must be removed when the web supports it.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew cleanTest test --continue` | 0 | **1,997 tests, 0 failures, 1 skipped** (the known `GameExporterTest` Linux assumption), 23 modules |
| `npx vitest run` and `npx tsc --noEmit` in `engine-babylon` | 0, 0 | 478 tests, 0 failures; the type check passes |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*EditabilityAuditTest" --tests "*TemplateMatrixTest" --tests "*WebUxContractTest"` | 0 | Generated tables refreshed. `terrain-3d`: 6 entities, logic "graph"; the Logic and Gameplay, physics columns are PASS; the label is still "Not yet rated (checks pending)" |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--scene samples/terrain-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)"; frame saved |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_SELECT=Valley GDXUNITY_EDITOR_ASSISTANT=0 ./gradlew :editor-desktop:run --args="--scene samples/terrain-3d.json --smoke-test"` | 0 | "180 frames, 0 error(s)"; frame saved |
| `./gradlew :engine-desktop:installDist :engine-desktop:gameScriptsClasses`, then `python tools/make_template_previews.py terrain-3d` | 0, 0 | Preview regenerated (see section 4) |

Per-module counts, with the M2.2 report's run as the baseline:

| Module | Before | After |
|---|---|---|
| `engine-physics` | 53 | 60 |
| `engine-scene` | 161 | 166 |
| `engine-render` | 144 | 148 |
| `engine-desktop` | 98 | 100 |
| `editor-terrain` | 16 | 20 |
| `editor-core` | 473 | 473 |
| `editor-desktop` | 158 | 158 |
| Web | 477 | 478 |

The other modules are unchanged.

**Screenshots** (looked at):
- [m3-3/runtime-valley-frame-120.png](m3-3/runtime-valley-frame-120.png): the game runtime at frame 120. Hero stands on the green valley with its shadow, and the hint ends "On the ground".
- [m3-3/editor-valley-details.png](m3-3/editor-valley-details.png): the editor with Valley selected. Details shows Terrain, then "Terrain Collider 3D" with its note and Offset row.

## 4. Failures found and fixed

- **Headless tests had no height-field source.** Mesh colliders have the same dependency, and tests that play scenes without a renderer give them a source themselves. `SampleGamesTest`, `TemplateGalleryProjectsTest` and `ComponentConsistencyTest` now do the same for terrain.
- **`ComponentConsistencyTest`** adds every catalog component to a bare entity. The new entry correctly refused that, so the test gives it a Terrain first, as it gives mesh colliders a Mesh Primitive.
- **A sample check ran with no 3D physics at all.** One `SampleGamesTest` check ran every sample for 10 frames that way. The Java walkers return early without physics, but the walker graph errors, which is correct. Samples that use 3D physics now run those frames in a `GameSession` with real Jolt, as Play does.
- **Web:** the walker graph is refused by the web's graph runtime (see section 2).
- **A misleading first preview.** It showed Hero "In the air" over nothing. The preview tool runs the *installed* runtime, which was last built on 2026-11-14, before this work. After `installDist` the preview shows Hero on the ground. The tool's docstring names that build step, but the tool does not check whether the build is stale.
- **Jolt's penetration slop:** a resting body sinks up to 2 cm into the ground. The resting-ball test allows 5 units, the same as the existing box-floor test. The 1 cm bound applies to the ground itself (raycasts), and it holds.
- **Shell slips:** one generator string broke during an edit and was fixed. One TypeScript test was restored from a backup after a deliberate check that it fails without its entry.

## 5. Diff review

- **New files:**
  - `HeightField3D`, `HeightFieldSource`, `TerrainCollider3DComponent` and its serializer;
  - `TerrainHeightFields`, `TerrainCollision`;
  - five test classes;
  - `assets/graphs/samples/ValleyWalker.graph`;
  - this report and `docs/reports/m3-3/`.
- **Changed engine and editor files:**
  - `Scene`, `ShapeDescriptor3D`, `PhysicsSystem3D`, `JoltPhysicsBackend3D`, `SceneRenderer3D`, `GameComponentSerializers`;
  - `ComponentCatalog`, `LevelSession`, `TerrainComponentEditorProvider`, `Physics3DComponentEditorProviders`, `InspectorPanel`, `Collider3DRenderer`.
- **Changed tests:**
  - list updates: `ComponentCatalogTest`, `GameComponentSerializersTest`;
  - the source added for headless Play: `SampleGamesTest`, `TemplateGalleryProjectsTest`, `ComponentConsistencyTest`;
  - web: `sceneBuilder.test.ts`, `samples.test.ts`.
- **Assets and tools:** `assets/samples/terrain-3d.json`, `assets/templates/previews/terrain-3d.png`, `tools/make_terrain_samples.py`.
- **Docs:** `TECHNICAL_DECISIONS.md` (TD-087 status), `milestone-chart.md`, `FEATURE_TEST_MATRIX.md` (new row, two stale "no collision" notes, regenerated tables), `INDEX.md`.

No files outside this repository were touched. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Manual check for a person** (Windows):
  1. Open `samples/terrain-3d.json` and press Play.
  2. Walk Hero with WASD and jump with Space.
  3. Stop, reshape a hillside steeper than 45 degrees with the brush, and press Play again. Hero cannot walk up it, and slides down it.
  4. Repeat in the packaged game (`:engine-desktop:run`).
- **Preview:** the template preview now shows Hero close up on grass, because Play's camera follows Hero. It no longer shows the whole valley.
- **Scenes without a renderer:** a scene played without a renderer needs its caller to set the height-field source, as tests now do. A future headless runtime, such as a server, would need to do the same.
- **Missing elsewhere:**
  - Placed level objects have no colliders of their own (M2b.4).
  - The web player has no terrain at all.
  - Holes (M3.4) will use the hole value that already works here.
- **Not measured:** the first build time of a 1025 by 1025 terrain.
- **Next on the critical path:** R3b (M3b.1's audit TD) and M3c.1 (Action RPG + 3D), which needed this milestone. M3.10 also needs M3.4 and M3.6.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
