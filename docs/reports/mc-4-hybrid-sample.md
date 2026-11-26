# MC.4 2.5D hybrid sample: report

**Date:** 2026-11-25. **Milestone:** MC.4 (Appendix C). **Status:** **done-when met (2026-11-25):** the hybrid plays with a correct side view and depth order. This is checked on real Jolt by `HybridSideScrollerOnJoltTest`, and seen in the game runtime's frames. Each part is edited in its own workspace (`HybridSideScrollerSampleTest`). **Manually Not Verified (Headless Environment):** nobody played it with the keyboard or clicked Customize.

**Done-when:** "The hybrid plays with a correct side view and depth order." **Deliverable:** 3D scenery with 2D sprites, each edited in its own workspace; camera plane, transparency, depth sorting and physics validated.

**Design choices (mine, per the standing instruction to decide open details):**
- A **new sample**, not a migration: originals stay as they are (MC.7 rollback rule).
- **3D scenery:** Kenney Building Kit and Nature Kit models already in the repository and the licence manifest (CC0).
- **Sprites:** ForgeRoom's own `hero.sprite` (Apache-2.0). Kenney Pixel Platformer is approved but not downloaded (M2b.5, Legal).
- **The player is a sprite** with a Character Controller 3D, played by the Platformer + 2.5D profile. No scripts.
- **The sprites are listed nearest first on purpose**, so the depth order on screen depends on the sort below.

## What was built

- **Depth order among flats (engine-render).**
  - **Before:** `SceneRenderer2D` tests flats against the 3D pass's depth but does not write depth. Among themselves, sprites, tile maps and filled shapes were therefore drawn in scene-list order, so a sprite behind another could be drawn over it.
  - **Now:** `ViewDepthOrder` sorts them farthest first along the camera's view, but only when the depth composite is active (a perspective camera). The sort is stable, so ties keep the scene order. The orthographic 2D painter's order is unchanged.
  - **Depth key:** the entity's world position.
- **Sample `assets/samples/hybrid-side-scroller-25d.json`.**
  - **Generator:** `tools/make_profile_samples.py hybrid-side-scroller`. The generator now takes sample names, so the other profile samples, which have uncommitted changes, were not rewritten.
  - **Behind the plane:** a Kenney house front (wall, window wall, doorway, turned to face the side camera) and three pines.
  - **In front of the plane:** a Kenney column.
  - **Play pieces:** a Kenney floor tile as a platform (Box Collider 3D), and a box ground and two end walls.
  - **Sprites:** the hero, plus villagers at z −150 and z +160.
  - **Listed in:** the New Game gallery (web: false), the samples README, and the migration plan's 2.5D group (its MC.4 blocker is cleared).

## Evidence

| Check | Result |
|---|---|
| `.\gradlew.bat :engine-render:test --offline -x generateAssetList` | Exit 0. **213 tests** (208 before; +5 `ViewDepthOrderTest`), 0 failures |
| Mutation: the sort in `ViewDepthOrder` commented out | `ViewDepthOrderTest`: 5 run, **3 fail**, as intended; restored |
| `.\gradlew.bat :engine-desktop:test --tests "*HybridSideScrollerOnJoltTest" --offline -x generateAssetList` | First run: 3 of 4 pass. The fourth was my wrong expectation, not a bug: a following camera trails a running player by speed ÷ follow rate (about 70 units). The camera is now checked once the player has stopped. Second run: **exit 0, 4 pass** |
| `.\gradlew.bat :engine-desktop:run --args="--scene samples/hybrid-side-scroller-25d.json --smoke-test=240"` with `GDXUNITY_SCREENSHOT` | Exit 0, "240 of 240 frames, 0 runtime error(s)" (three runs) |
| `HybridSideScrollerSampleTest` (editor-desktop): gallery project, Edit 2D Asset, Edit 3D Model, Play, Stop, save, reopen | **Not run:** Claude Code stopped the Gradle run because the machine was critically low on memory |
| (second session, after `.\gradlew.bat --stop`; 1.5 GB of 16 GB free) `.\gradlew.bat :editor-desktop:test --tests "*HybridSideScrollerSampleTest" --max-workers=1 --offline -x generateAssetList` | **Exit 0, 1 test, 0 failures, first run (13.7 s).** In sequence: a project from the gallery entry; Customize offers Edit 2D Asset hero.sprite (2D Studio), and a pixel is painted and saved; all 3 users of the sprite reload; Customize offers Edit 3D Model on the window wall (3D Modeling), and its top is raised 0.5 m and saved as a unique derived model; Back to World shows it; the other walls and the provider original are unchanged; Play as Platformer + 2.5D has both edits; Stop leaves the scene unchanged; save and reopen keep both, and Play shows them again |

| Sample audits (`SampleMigrationsTest`, `TemplateGalleryProjectsTest`, `CustomizeGatewaySamplesTest`, `DuplicateSampleEntitiesTest` and the six `audit` classes), `--max-workers=1` | Exit 1: 57 tests, 7 failures, all caused by the new sample: four generated tables were stale, and two tests hard-code sample counts. **Passing:** `SampleMigrationsTest` 5, `TemplateGalleryProjectsTest` 2 (the hybrid's gallery project opens, plays and exports), `CustomizeGatewaySamplesTest` 2, `DuplicateSampleEntitiesTest` 2 |
| The six audit classes with `GDXUNITY_UPDATE_AUDIT=1` | 46 tests, 1 failure: the hard-coded label count (Partially Editable 29 → 30, now updated). The tables were regenerated. Compared with a snapshot taken before, every change comes from the hybrid: its rows in the three tables; six Kenney files now also "used by samples"; the template totals 54 → 55; and the 2.5D group's blocker text |
| Count updates | `SampleInventoryTest` (2.5D: 8 → 9), `TemplateRatingsTest` (55 templates; Partially Editable 30) |
| The six audit classes again, without the update flag | **Not finished:** stopped by Claude Code for low memory (about 0.5 GB free) |
| (third session, run as two foreground runs of three classes so neither went to the background) `SampleInventoryTest`, `TemplateRatingsTest`, `AssetSupportMatrixTest`; then `EditabilityAuditTest`, `TemplateAuditTest`, `TemplateMatrixTest` | **Exit 0 both:** 10, 3, 6; then 8, 5, 14 tests; 0 failures |
| `engine-babylon`: `npx vitest run` | Exit 1: **519 of 520 pass.** The one failure is **not caused by this work**: `shouldSupportEveryComponentTypeUsedByTheSamples` stops at `action-rpg-valley-3d.json`, whose uncommitted change (already in the working tree when this session started) adds an `AtmosphereComponent` the web player does not support |
| `engine-babylon/test/sceneBuilder.test.ts`: the hybrid's entry added (`PlayerComponent`, as for every profile sample). To see the samples after the valley, the valley's gap was listed **temporarily**, then reverted | With the temporary entry: 8 of 8 pass, so the hybrid and every later sample build on the web with only the expected gap. Reverted: the valley line is as it was |

What `HybridSideScrollerOnJoltTest` checks, on real Jolt in the game runtime's session:

- **Setup:** the profile starts with no problems; the player is a sprite; every scenery piece is a Kenney model that exists.
- **Camera plane:** the side camera looks along −Z (no sideways turn), stays 900 in front of the plane and follows the player.
- **Physics:**
  - The floor tile's collider stops the sprite player at its side (x 570).
  - One jump per press lands it on the tile (y 190).
  - It runs off and is stopped by the end wall (x 1700).
  - It never leaves the plane (|z| < 0.01 throughout).
- **Depth order:** from the profile camera, the sprites sort to Back Villager, Hero, Front Villager, the reverse of the scene order.

**Screenshots (looked at):**
- [mc-4/runtime-hybrid-frame-240.png](mc-4/runtime-hybrid-frame-240.png), the game runtime's side view:
  - the sprites have no box or halo, and the scenery shows through their transparent pixels;
  - the hero is drawn over the tinted villager behind it;
  - the front villager is drawn over the house wall;
  - the Kenney column hides part of the front villager.
- [mc-4/runtime-hybrid-without-sort.png](mc-4/runtime-hybrid-without-sort.png), the same frame with the sort switched off: the farther villager is drawn over the hero's face. This is the bug the sort fixes.

Starting and ending HEAD: `b8bace5`. No commit, push or destructive Git operation was performed.

## Diff review

- **New:**
  - `engine-render`: `ViewDepthOrder`, `ViewDepthOrderTest`;
  - `engine-desktop`: `HybridSideScrollerOnJoltTest`;
  - `editor-desktop`: `HybridSideScrollerSampleTest` (not yet run);
  - `assets/samples/hybrid-side-scroller-25d.json`;
  - this report and `docs/reports/mc-4/`.
- **Changed:**
  - `SceneRenderer2D` (the sort in the depth-composite pass);
  - `editor-desktop` audit tests `SampleInventoryTest` and `TemplateRatingsTest` (sample counts); `engine-babylon/test/sceneBuilder.test.ts` (the hybrid's entry);
  - regenerated tables: `FEATURE_TEST_MATRIX.md` (two), `sample-asset-migration-inventory.md`, `fully-editable-template-audit.md`, `asset-support.tsv`, `ASSET_SUPPORT_MATRIX.md` (every change traced to the hybrid against a snapshot taken first);
  - `tools/make_profile_samples.py` (new writer; samples chosen by name);
  - `assets/templates/gallery.json` (one entry);
  - `assets/samples/README.md` (row and Depth note);
  - `docs/migration/sample-migrations.json` (2.5D group);
  - `docs/BACKEND_SUPPORT_MATRIX.md` (one row);
  - `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `milestone-chart.md` (MC.4).
- The working tree's earlier uncommitted work is untouched.

## Known limitations

- **The web player** still draws flats among themselves in scene order (`alphaIndex`); recorded in the backend matrix. The sample is not offered on the web, because the web player has no genre profiles.
- **One depth point per flat:** a flat's depth is its entity's position, so one very large flat seen at a steep angle can still sort wrongly against another.
- **No turning:** the sprite player does not flip to face its running direction (in 2D or 2.5D).
- **Pixel Platformer sprites** wait on M2b.5.
- **Manually Not Verified:** nobody played it with the keyboard or clicked Customize.

## Next

- **For the owner:** `action-rpg-valley-3d.json` now has an Atmosphere component that the web player does not draw. Either list it in `KNOWN_UNSUPPORTED` in `engine-babylon/test/sceneBuilder.test.ts` (and the backend matrix), or port it. Left as is: it is not part of MC.4.
- The template matrix still marks glTF models "FAIL (no mesh editor)", though R7a's 3D Modeling edits them (this milestone's editor test does). The audit's rule should be revisited.
- Optional: a template preview, `tools/make_template_previews.py hybrid-side-scroller-25d`.
- A person plays the sample with the keyboard and uses Customize on a sprite and a model (E2E plan).
