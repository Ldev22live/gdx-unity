# M2b.2: Curated CC0 Starter Pack (Tree, Rock, House)

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `0469ceb` (nothing committed)

Roadmap milestone **M2b.2** of R2b. It works under TD-094 (third-party manifest, approved) and TD-095.

**Legal gate:** the owner approved the CC0 shortlist on 2026-11-19. Its rows 1 and 2 are Kenney's Building Kit and Nature Kit. The owner downloaded both zips on 2026-11-20, because Kenney's `robots.txt` bars automated agents.

**Status: Implemented and Integrated.**
- The project now ships a **CC0 Oak Tree** and a **CC0 Rock** in Objects, and a **CC0 House** in Buildings. They are made from Kenney's models and recorded in `licenses/manifest.json`.
- New 2.5D and 3D projects get them with their model files and licence records.
- **Manually Not Verified:** no person has placed them with the mouse.

## 1. Acceptance criteria

Done-when: "They place multiple times, save and reload offline, keep license metadata and render acceptably."

| Criterion | Result | Evidence |
|---|---|---|
| One verified CC0 tree, rock and house imported with provenance | **Met** | **Import:** both zips through the editor's own `PackImporter` (the Import tab's code). **Nature Kit 2.1:** 1,970 files kept, 1,648 left out (DAE, FBX, OBJ, STL, URL). **Building Kit:** 166 kept, 241 left out (FBX, OBJ, HTML, URL). Each zip's `License.txt` says CC0. The manifest holds `kenney-nature-kit` and `kenney-building-kit` with every file's SHA-256 and the archive's file name. **Tree:** `tree_oak.glb` (196 triangles). **Rock:** `rock_tallA.glb` (136). **House:** 16 Building Kit pieces: 4 floor tiles, 8 walls including a doorway and windows, and 4 flat-roof corners. |
| Stable ids | **Met** | **Manifest:** `kenney-nature-kit` and `kenney-building-kit`. **Prefabs:** `tools/make_starter_pack.py` derives every entity id from its name (uuid5), so a rerun writes the same files. **File names:** the prefabs are `CC0 Oak Tree`, `CC0 Rock` and `CC0 House`. |
| Offline cache | **Met** | The models are project files, so nothing goes online to use them. The placement test uses no network code at all. |
| Thumbnails | **Partly** | Kenney ships a preview per model (`Models/GLTF format/*.png`, and the Building Kit's `Previews/`), and they are imported. The World strip's palette does not show thumbnails yet; that is M2b.3's "searchable thumbnails". |
| They place multiple times | **Met** | `StarterPackPlacementTest`, in a new project given the palette as the gallery gives it: 5 trees, 5 rocks and the house placed from Objects and Buildings. Every model file is in the project and recorded. |
| Save and reload offline | **Met** (test) | The scene is saved to a file and reopened. Each instance has the same models, position and prefab link. |
| Keep licence metadata | **Met** | **The shipped files are checked:** `StarterPackTest` checks every model the three prefabs name, and the house's colour map, against the shipped manifest: CC0-1.0, and SHA-256 unchanged since the import. **New projects get the records:** a new 3D project gets the prefabs, the models and *only their* licence records (`ProjectLicences`). |
| Render acceptably | **Met** (seen) | Game runtime, a scratch copy of the showcase with the three placed: [starter-pack-in-game.png](m2b-2/starter-pack-in-game.png). 120 frames, 0 errors. **House:** draws with the Building Kit's colours (door, window, overhanging roof). **Oak:** about 6 m tall. **Rock:** about 1.5 m. |

**Mutation checks** (each restored afterwards, with `cmp` identical):

| Change | What failed |
|---|---|
| A binary glTF's outside files not followed | 3 of 5 `StarterPackTest` tests (the colour map is not found and not copied) |
| Licence records not carried into a new project | `shouldCopyTheModelsAndOnlyTheirLicenceRecordsIntoANewProject` |

## 2. What was built

### New

- **`tools/make_starter_pack.py`:** writes `prefabs/level/CC0 Oak Tree.prefab`, `prefabs/level/CC0 Rock.prefab` and `prefabs/buildings/CC0 House.prefab`.
  - **Origins** are at the foot.
  - **Scale:** the Nature Kit is about 1 unit per tile, so the tree is ×5 and the rock ×1.5. The Building Kit is in metres, so the house is ×1. Every model keeps the import scale of 100 units per metre.
- **`ProjectLicences`** (`editor-core`, new): when files are copied into another project, their licence records go too, only for the copied files. The target's own records are kept.

### Fixed

Two gaps found on the way:
1. **`TemplateProject` never followed a binary glTF's outside files.**
   - **The problem:** the Building Kit's GLBs read `Textures/colormap.png` from beside them, so a template or palette copy would have lost the house's colours.
   - **The fix:** it now reads the GLB's JSON chunk. A file that is not a binary glTF is refused with the reason.
2. **Third-party files were copied into new projects without their licence records.** This covers both templates and the starter palette. Both now carry them (TD-094).

### Changed

**`StarterPalette`:**
- `files` now lists the prefabs and every file they need, so the gallery's file count and size include the models;
- the new `prefabs` lists just the prefabs;
- `addTo` copies everything and carries the licence records.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `java ... ImportPacks.java assets 2026-11-20 nature-kit assets/kenney_nature-kit.zip building-kit assets/kenney_building-kit.zip` (the editor's `PackImporter`, run directly) | 0 | Nature Kit: 1,970 files kept, 1,648 left out. Building Kit: 166 kept, 241 left out. 12 MB and 1.8 MB under `assets/thirdparty/kenney/` |
| `python tools/make_starter_pack.py` | 0 | The three prefabs |
| `./gradlew :engine-desktop:run --args="--project <copy> --scene samples/m2b2-starter-check.json --smoke-test=120"` | 0 | 120 frames, 0 runtime errors; screenshot |
| `./gradlew :editor-core:test --tests "*StarterPackTest"` / `:editor-terrain:test --tests "*StarterPackPlacementTest"` | 0 | 5 and 2 tests |
| The two mutation checks | 1 each | As listed; restored |

### 3a. Per-module test counts

**First full run of `editor-core`:** 595 tests, 2 failures, both from this milestone's new licence file:
- **`TemplateGalleryTest`:** the gallery's file count of a new 2.5D project was one short, because the licence manifest was not counted.
- **`TemplateProjectTest`:** the Poly Haven showcase template now holds its licence records, which the test did not expect.

**Fixes:**
- **The gallery** now counts the manifest and its real size (`ProjectLicences.recordFor`), so its figures stay exact.
- **The template test** now also checks that a template's licence records are exactly its downloaded files.
- **`TemplateGalleryTest`'s own size calculation** includes the manifest.

**After the fixes, by class:**

| Class | Tests | Result |
|---|---|---|
| `StarterPackTest` | 5 | pass |
| `TemplateGalleryTest` | 13 | pass |
| `TemplateProjectTest` | 6 | pass |
| `StarterPackPlacementTest` (`editor-terrain`) | 2 | pass |

**The second full run of `editor-core` and `editor-terrain` was stopped by the system for low memory** before any module finished. It was not a test failure, and it was not restarted unasked. `editor-desktop` has not been run since this milestone's changes.

**Still to do:**
- **Full runs of all three modules.** In particular, `editor-desktop` holds the inventory and audit tests that list the shipped assets (`SampleInventoryTest`, `ThirdPartyAssetsTest`, `TemplateRatingsTest`, `ActionInventoryTest`). They may need their generated documents brought up to date for the new prefabs and the 2,136 imported files.
- **Rebuilding the package.**

## 4. Known limitations and next steps

- **The owner's downloads are still in `assets/` in three forms:**
  - the two zips;
  - their unpacked folders `kenney_nature-kit/` and `kenney_building-kit/`;
  - a third unpacking of the Nature Kit straight into `assets/` (`Isometric/`, `Side/`, `models/DAE format/`, `models/FBX format/`, `models/GLTF format/`, `License.txt`, `Preview.png`, `Sample.png`, `Instructions.url`, `Kenney.url`, `Patreon.url`).
  - **Why it matters:** none of them is recorded, and they would ship in the packaged editor (about 3,600 duplicate files). The imported copies under `thirdparty/kenney/` are the recorded ones.
  - **Not deleted;** that is the owner's decision. See the final message.
- **Not used yet:** the Kenney preview images; the palette shows names (M2b.3).
- **The house is one design.** The Building Kit's 79 pieces allow more, built as prefabs the same way.
- **No collision yet** on the starter models (M2b.4).
- **Next on the chart:** M2b.3 (Objects palette with thumbnails and search), M2b.4 (collision, instancing, LOD) and M3.6 (scatter).

## 5. Diff review and Git

**New:**
- `tools/make_starter_pack.py`;
- the three prefabs;
- `assets/thirdparty/kenney/` (2,136 files) and the two manifest entries;
- code and tests: `ProjectLicences`, `StarterPackTest`, `StarterPackPlacementTest`;
- this report and `docs/reports/m2b-2/`.

**Changed:** `TemplateProject`, `StarterPalette`, and the documents (`INDEX.md`, `FEATURE_TEST_MATRIX.md`, both charts).

**Not touched:** the owner's downloaded files.

**Git:** no commit, push or other Git operation was performed.
