# M3.10: The Five Landscape Samples on Terrains

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `2391dad` (nothing committed in this milestone)

Roadmap milestone **M3.10** of R3. Its dependencies M3.3, M3.4 and M3.6 are done, and the chart lists no gate. Decision record: [TD-098](../TECHNICAL_DECISIONS.md), recorded for the owner's review.

**Status: Implemented and Integrated.** Each of the five landscape samples has a migrated copy whose ground is an editable `.terrain` instead of a baked glTF mesh:
- same heights;
- each point painted with the nearest of the six grounds;
- a collider, a Hero to walk it, and the Action RPG genre.

The originals are unchanged, as the sample migration plan requires. Both are offered in the template gallery. The chart's journey runs on a project made from Landscape Valley (Terrain): reshape the valley, repaint a slope, scatter pines, save, reopen, play and walk.

**Manually Not Verified:** no person has done the journey with the mouse.

## 1. Acceptance criteria

The chart's deliverable is "the five landscape scenes use terrain assets". Its done-when is "a person opens `landscape-3d`, reshapes a valley, repaints a slope, scatters pines, saves, plays, and the character walks on it".

| Criterion | Result | Evidence |
|---|---|---|
| The five landscapes use terrain assets | **Met, as migrated copies** | `tools/make_migrated_landscapes.py` writes `samples/landscape{,-desert,-alpine,-island,-autumn}-terrain-3d.json` and `terrains/{Landscape Valley, Desert Canyon, Alpine Valley, Tropical Island, Autumn Forest}.terrain`. **The originals' bytes are unchanged** (md5 before and after). Running the tool again gives the same files. |
| The same ground, the same scenery | **Met** | `MigratedLandscapesTest.shouldStandEveryKeptObjectOnTheMigratedGroundWhereTheBakedGroundWas`: every tree, rock and building of every migrated scene stands within 0.5 m of the new ground, as the generators placed it on the old one. `SampleMigrationsTest`: every entity but the replaced `Terrain` keeps its id. |
| Done-when: reshape, repaint, scatter, save, play, walk | **Met, on the migrated copy** | **`MigratedLandscapesTest.shouldReshapeRepaintScatterSaveReopenAndWalkTheMigratedLandscapeValley`**, on a project made from the gallery's Landscape Valley (Terrain):<br>1. deepen the valley with Lower strokes in the viewport;<br>2. paint the slope rock, then undo and redo it;<br>3. scatter pines (one undo step);<br>4. save, and reopen in a new editor;<br>5. check the ground, the paint and the pines survived;<br>6. play as Action RPG on real Jolt and walk the Hero over the reshaped ground.<br>**Why the copy:** the chart says `landscape-3d`, and the migration plan says a migrated sample is a new file beside its original. TD-098 reports the conflict and follows the plan. |
| The character walks on every landscape | **Met** | `MigratedLandscapesTest.shouldWalkTheHeroOverEveryMigratedGroundInPlay`: in each of the five, the Hero walks at least 3 m in two seconds and comes to rest on the ground, above the terrain. |
| Offered and recorded | **Met** | **Gallery:** five "<title> (Terrain)" entries under Landscape, each after its original, with previews rendered by `tools/make_template_previews.py` (compared with the originals by eye).<br>**Registry:** five slices in `docs/migration/sample-migrations.json`, with their evidence, checked by `SampleMigrationsTest`. |
| Cost measured | **Met** | `docs/migration/m3-10-performance.tsv`, one `perfBaseline` run per scene. The migrated scenes take 4.35 to 6.27 ms a frame against 3.40 to 3.84 ms for the originals. They draw 19,200 to 27,648 terrain triangles, run Jolt and play the follow camera. They start in 0.38 to 0.48 s, against 0.07 to 0.12 s. |

## 2. What was built

**New:**
- `tools/make_migrated_landscapes.py`;
- the five scenes and five terrains;
- five gallery previews;
- `docs/migration/m3-10-performance.tsv`;
- `MigratedLandscapesTest` (3 tests, `editor-desktop`);
- TD-098, and this report.

**Changed:**
- `assets/templates/gallery.json` (+5 entries);
- `docs/migration/sample-migrations.json` (the landscape group lists the new files, its status, and 5 slices).

**Choices made without asking** (in TD-098):
- **New files, not an in-place conversion,** per the migration plan.
- **Ground types by nearest colour.** The six grounds replace the hand-tuned colour bands, so the grounds look plainer: the mesas' red bands become rock and dirt.
- **Water stays as the original models.** The grounds' edges dip below the lake levels, so terrain water would flood the rims.
- **A plain Hero entity**, as in `terrain-3d`, rather than a Player prefab instance, so the sample needs no prefab file.
- **"Landscape" as the gallery genre,** so each copy sits beside its original.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `python tools/make_migrated_landscapes.py`, twice | 0 | 5 scenes and 5 terrains; the second run identical; originals unchanged |
| `./gradlew :editor-desktop:test --tests "*MigratedLandscapesTest" "*SampleMigrationsTest"` (`JAVA_TOOL_OPTIONS=-Xmx800m`, one worker) | 1 | **The journey passed.** 3 failures, all expectations of the new tests or not-yet-made files (below) |
| `./gradlew :engine-desktop:installDist :engine-desktop:gameScriptsClasses`, then `python tools/make_template_previews.py <the five>` | 0 | 5 previews written |
| `./gradlew :engine-desktop:perfBaseline -PperfScenes=<ten> -PperfRuns=1` | 0 | The performance table |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --max-workers=1 --tests "*.audit.*"` (`-Xmx800m`) | 1 | Inventory, template table and audit reports regenerated; 2 failures: the template count (below) and a fault in `ThirdPartyAssetsTest` from M2b.2 (below) |
| `./gradlew :editor-desktop:test --max-workers=1 --tests "*.audit.*" "*MigratedLandscapesTest" "*SampleMigrationsTest" "*TemplateGallery*"` (`-Xmx800m`, no update variable) | 0 | **66 tests, 0 failures:** MigratedLandscapes 3, SampleMigrations 5, TemplateGalleryProjects 2, AcceptanceProject 1, ActionInventory 6, ComponentConsistency 5, EditabilityAudit 8, NoForeignGameArt 1, SampleInventory 7, TemplateAudit 5, TemplateMatrix 14, TemplateRatings 3, ThirdPartyAssets 2, WebUxContract 4 |
| `npx vitest run` in `engine-babylon` | 1, then 0 | First 1 failure (below); after the fix **39 files, 516 tests, all passing** |

**The three first failures:**
- **A mesa's foot 46 units below the ground.** The generator sank mesas by 0.3 m times their scale (up to 1.6), so the tolerance became 50 units.
- **The desert Hero not on the ground at the last walking frame.** It walked down 42 to 48 degree dune faces, past the 45 degree limit, and was sliding. The test now lets it come to rest first.
- **The previews missing.** They were made next.

**Expectations updated for the five new samples (bookkeeping, no behaviour change):**
- `SampleInventoryTest`: 37 3D samples instead of 32.
- `TemplateMatrixTest`: "the ground is a generated model" now applies to the five originals only, and each terrain copy must **pass** the Terrain domain (both counted, 5 and 5). Renamed `shouldFailTheGroundOfTheFiveLandscapeOriginalsAndPassTheirTerrainCopiesAndTheTerrainSample`.
- `TemplateRatingsTest`: 53 offered templates instead of 48, 27 Partially Editable instead of 22. Each copy is **Technical Demo / Partially Editable**: its ground passes, but its trees, rocks and cabins are glTF models with no mesh editor (R7a).
- The tables in `FEATURE_TEST_MATRIX.md`, `reports/sample-asset-migration-inventory.md` and `reports/fully-editable-template-audit.md`, regenerated with `GDXUNITY_UPDATE_AUDIT=1`.
- **Web** (`engine-babylon/test/sceneBuilder.test.ts`): the five copies added to `KNOWN_UNSUPPORTED` with `PlayerComponent`, like the other samples played by a genre profile. The web player does not run genre profiles (`docs/BACKEND_SUPPORT_MATRIX.md`, already "Blocked"), so on the web the copies show their terrain but the Hero does not walk.

**A fault from M2b.2, fixed:** `ThirdPartyAssetsTest.shouldRecordEveryDownloadedFileWithTheChecksumItHasNow` failed for `thirdparty/kenney/building-kit/License.txt`. It checked `path.contains("/thirdparty/")`, which misses a path that **starts** with `thirdparty/`, as every Kenney record does. Its sibling test already prefixed a slash, so the check now does the same. The files are in a thirdparty folder and their checksums match. M2b.2 did not run this class (its full module runs were pending), so the fault went unnoticed until now.

## 4. Known limitations and next steps

**Limitations:**
- **The originals keep the baked ground.** Opening `landscape-3d` itself still shows the model ground. The gallery offers both.
- **Plainer grounds.** Painted with the six grounds, the copies have no colour bands.
- **Trees and rocks do not collide**, unlike the palette's objects (TD-096).
- **Water is a model, not the terrain's water level**, so Scatter does not avoid it.
- **Generated cabins, huts and rocks are not yet replaced** by downloaded models (the registry's landscape group).
- **The baked ground models** in `models/landscape/` are still used by the originals.

**Next on the chart:** with M3.10, every R3 milestone is Implemented; next are R2b's M2b.5 (2D CC0 catalog), M2b.6 and M2b.7.

**Still pending (low memory):** full module runs of `editor-desktop`, `editor-core` and `engine-render` (only the classes above were run), and the package rebuild.

## 5. Diff review and Git

All of section 2. The originals, their models and the performance baseline are unchanged.

**Git:** no commit, push or other Git operation was performed in this milestone. The M2b.4 and M3.6 work since `2391dad` is still uncommitted.
