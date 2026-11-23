# MP.1: Sample Inventory

**Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart.

**Why this milestone:** after M9.6 the rows that could start were MP.1, MT.8 and MC.1, plus M9.1, M5.1 and M6.3, which have a TD gate. MP.1 has no gate, its dependency M1.1 is in place, and it is what Appendix A3 and B6 require before any sample is migrated. MC.1 needs web research into licences, and MT.8 overlaps M1.1's matrix; both are left for the owner to start.

## 1. Acceptance criteria

| # | Criterion (chart row MP.1) | Status | Evidence |
|---|---|---|---|
| 1 | Which samples are 2D, 2.5D or 3D | **Met** | All 43 scenes declare `gameMode`: 7 in 2D, 7 in 2.5D, 29 in 3D ([inventory](sample-asset-migration-inventory.md), section 1; asserted by `SampleInventoryTest`) |
| 2 | Their licences | **Met as recorded; not verified** | Per scene and per file. Only the MakeHuman data has a licence file (CC0). Every other file is project-made with no licence record, and whether the repository's Apache 2.0 `LICENSE` covers assets is the owner's to decide. No third-party asset was found |
| 3 | Source formats | **Met** | Formats per scene, including files reached through materials, prefabs and characters; 149 content files listed with their format and how each was made |
| 4 | Opening editors | **Met** | Per scene, the parts it uses with their M1.1 PASS/FAIL. Per file, where it opens (after `AssetOpening`) |
| 5 | Behaviour | **Met** | Per scene: Java scripts and graphs by name, or none; genre; gallery title; web player status |
| 6 | Test status | **Met** | Per scene, the test classes (desktop and web) that name it, and whether the M9.5 baseline plays it; the seven test classes that go through every sample are listed |
| 7 | Done-when: "The inventory exists; no sample is presumed broken" | **Met** | Every scene was loaded by the editor's serializer ("loads" for all 43) and names no missing file. The all-sample tests (open, play 2 s, export every gallery template; play every sample game) pass |

## 2. What was built

- **`SampleInventory`** (`editor-desktop/.../audit`, beside the M1.1 audit it builds on):
  - **For each scene:**
    - its declared dimension and genre, and its gallery title;
    - whether it loads, its behaviour, and the formats and any missing files it names;
    - its editable parts (from `TemplateMatrix`) and the licence bases of what it uses;
    - the web player, read from the Babylon `SUPPORTED_COMPONENTS` set and the gallery's web flag;
    - the test classes naming it (matched on the whole file name, so `platformer-2d.json` does not match `tilemap-platformer-2d.json`) and whether the M9.5 baseline plays it.
  - **For each content file:** its format, where it opens, how it was made, its licence basis, and the samples that name it.
- **`SampleInventoryTest`** (6 tests):
  - four rule tests on a small project written for them: a texture found through a material, a missing file, licences, tests named by whole name, baseline, a scene that does not load is reported as such, how a file was made (generator, a generator script that is missing, named in a tool, no record), the MakeHuman data as one row, and web support;
  - every shipped sample loads, declares 2d, 2.5d or 3d, and names no missing file, with the dimension counts pinned;
  - the two tables in the inventory report must match a fresh run (`GDXUNITY_UPDATE_AUDIT=1` refreshes them, as for M1.1).
- **`TemplateMatrixTest.editorAudit(File)`:** the existing helper made usable for another asset root.
- **[`sample-asset-migration-inventory.md`](sample-asset-migration-inventory.md):** the generated tables, a summary, the licence position, the A3 migration columns that cannot be filled yet, and the tests that cover every sample.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*SampleInventoryTest"` | 0 | Tables written into the report |
| `./gradlew :editor-desktop:test --tests "*SampleInventoryTest"` (after the hand-written text) | 0 | 6 of 6; the tables are current |
| `./gradlew :editor-desktop:test` | 0 | **169** tests (163 before, +6), 0 failures, 0 errors, 0 skipped. Includes `TemplateGalleryProjectsTest` (2), `SampleGamesTest` (42), `DuplicateSampleEntitiesTest` (2) and `TemplateMatrixTest` (14) |

Other modules were not changed. Their tests were not rerun after the M9.6 run earlier today: engine-core 45, engine-scene 226, engine-visualscript 91, editor-core 539, engine-desktop 144. **Not run:** the full `./gradlew test --continue` and the Babylon suite (no component, node or scene format changed).

## 4. Findings

- **No broken sample:** all 43 load, and none names a missing file.
- **Java logic:** 19 scenes keep their logic in Java source: 4 of 7 in 2D, 5 of 7 in 2.5D, 10 of 29 in 3D. This is the largest editability gap, and it is M1.2's work.
- **Unused graphs:** 12 graphs exist that no scene uses. Most are graph versions of Java behaviour (`ArenaPlayer`, `HopperPlayer`, `RaceCar`, ...), so the M1.2 port is partly written and not wired in.
- **Web player:**
  - 9 scenes are marked as playing on the web.
  - 4 use components it lacks: `PlayerComponent`, `SpawnPointComponent`, `EngineSound`.
  - 21 use only supported components but have never been marked or checked on the web.
- **Licences:**
  - only the MakeHuman data has a licence file;
  - 144 project-made files have no per-asset licence record. The A4 release gate ("each shipped model and texture has recorded source and license") is therefore not met today;
  - `models/BendingStrip.gltf` was made by a script that is no longer in `tools/`.
- **Tests:** five scenes are named by no test of their own. All five have no scripts and are covered by the all-sample tests.
- **Safest first migrations for MP.3:** `tilemap-platformer-2d` (2D) and `platformer-profile-25d` (2.5D) have no Java and every part PASS.

## 5. Limitations and next steps

- **The A3 migration columns are not filled:** proposed replacement, source URL, licence of the replacement, conversion and expected visual result. They need MC.1 and M2b.1 to M2b.2. The inventory says so rather than guessing.
- **Licences are what the repository records**, not a legal review. The owner should decide whether Apache 2.0 covers the assets, or add an asset licence (for example CC0 for project-made content) and a manifest.
- **"Made by" is evidence, not proof.** "Named in tools/x" means the script's source contains the file name; it may write the file or only read it. Files made in the editor have no record at all.
- **Web status** comes from the component list and the gallery flag, not from playing each scene in a browser.
- **Not inventoried:** template preview PNGs (generated by `tools/make_template_previews.py`), `engine-babylon`'s own copies (made by `sync-samples`), and the M3.8 large map (generated by a test, not shipped).
- **Next:** MP.3 (conservative migration) can now start, and so can MT.8 and MC.1.

## 6. Diff review and Git

- **New:**
  - `editor-desktop/src/main/java/.../audit/SampleInventory.java`
  - `editor-desktop/src/test/java/.../audit/SampleInventoryTest.java`
  - `docs/reports/sample-asset-migration-inventory.md`
  - this report
- **Changed:**
  - `TemplateMatrixTest.java` (a `File` overload of `editorAudit`)
  - `docs/milestone-chart.md` (MP.1 row and the P overview), `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`
- **Already changed before this milestone:** the chart, matrix, index and `TemplateMatrixTest.java` were already modified in the working tree, and their other lines were left alone.
- No commit, push, stash, reset or other Git operation was performed. HEAD is `0469ceb` at start and end.
