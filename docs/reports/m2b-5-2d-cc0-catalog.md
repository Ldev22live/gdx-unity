# M2b.5: The 2D CC0 Catalog: a Licence Record for Every Shipped Sprite

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `2391dad` (nothing committed in this milestone)

Roadmap milestone **M2b.5** of R2b. Its dependency M2b.1 is built. The chart gives it a **Legal** gate. Decision record: [TD-099](../TECHNICAL_DECISIONS.md), recorded for the owner's review.

**Status: Implemented and Integrated, for the part the Legal gate allows.**
- **The done-when is met:** every 2D file under `assets/` has a licence record at its own path, with the checksum it has now.
- **Recorded:** the Appendix B1 sources, with the licence check each needs.
- **In the editor:** the Content Browser shows the selected file's licence.
- **No new source was approved or downloaded.** That needs the owner (the Legal gate) and, for Kenney and OpenGameArt, a person's browser download.

**Manually Not Verified:** the licence line in the running editor's status bar.

## 1. Acceptance criteria

The chart's deliverable is "Kenney, OpenGameArt and other 2D sources per Appendix B1, with per-asset license checks". Its done-when is "each shipped sprite has a manifest entry".

| Criterion | Result | Evidence |
|---|---|---|
| Each shipped sprite has a manifest entry | **Met** | **`ShippedSpritesProvenanceTest`** walks every `.png`, `.jpg`, `.jpeg`, `.gif` and `.sprite` the editor package ships (all of `assets/` but `build/`), and finds:<br>- **1,742** in `licenses/manifest.json` (the Nature Kit's 2D renders and the other downloads);<br>- **71** in the new `licenses/originals.json`;<br>- each byte for byte what its record says.<br>**One reported exception:** 3,366 untracked files are byte-for-byte copies of recorded Kenney files at unrecorded paths. They are the owner's raw downloads at the top of `assets/` (section 4). |
| Per-asset licence checks | **Met for what ships; set out for the sources** | **Shipped files:** each downloaded file carries its CC0 record (since M2b.1). Each file made here now carries the repository's licence and how it was made; pictures of CC0 models name the downloads they show (`derivedFrom`).<br>**Sources:** `SpriteSources` records each Appendix B1 source with its licence check (Kenney: the provider's statement and the pack's licence file; OpenGameArt: each listing on its own; itch.io: discovery only). `SpriteSourcesTest` holds the import list (`KenneyPacks`) to exactly the approved Kenney sources. |
| Kenney, OpenGameArt and other 2D sources per Appendix B1 | **Not met: waits on the owner** | **Approved by the owner on 2026-11-19** (Kenney Pixel Platformer, UI Pack, Game Icons): offered in the Asset Library's Import tab, but **not yet downloaded**. Kenney's `robots.txt` disallows agents, so a person downloads them.<br>**Awaiting the owner:** New Platformer Pack, Platformer Pack Remastered, Brick Pack and the four OpenGameArt listings. **The itch.io catalog** is only a place to look. |
| Licence detail in the editor (M2b.1's gap) | **Met** | When a file is selected, the Content Browser's status message adds its licence line from `AssetLicences` (`AssetLicencesTest`). |

## 2. What was built

**New:**
- `engine-core`:
  - `OriginalAssets`, the reader of `licenses/originals.json`. It is strict: it refuses a missing field, an unsafe path, a path listed twice, a malformed SHA-256, or any licence but Apache-2.0.
  - `AssetProvenance`: which record a path is in, and one line for a person.
- `editor-core`:
  - `asset/AssetLicences`: the Content Browser's licence line, read fresh on each selection.
  - `asset/library/SpriteSources`: the B1 sources and their checks.
- `tools/record_original_assets.py`:
  - writes `originals.json` from one explicit rule per origin, and stops on a file no rule covers;
  - follows a prefab's or sample's references for `derivedFrom`;
  - `--check` reports a stale record.
- `assets/licenses/originals.json`: 71 files. The 48 template previews and 10 palette thumbnails, 4 showroom textures, 3 hand-made `.sprite` files, 5 untracked previews of M3.10's samples, and the template's `libgdx.png`.
- **Tests:**
  - `OriginalAssetsTest` (5) and `AssetProvenanceTest` (4), in `engine-core`;
  - `SpriteSourcesTest` (4) and `AssetLicencesTest` (2), in `editor-core`;
  - `ShippedSpritesProvenanceTest` (2), and one more in `SampleInventoryTest`, in `editor-desktop`.
- TD-099, and this report.

**Changed:**
- `AssetBrowserPanel`: the licence line in the status message.
- `SampleInventory`: a file in `originals.json` gets its origin and licence from it (before, "project-made, no licence record"). The inventory report's tables were regenerated.
- `tools/make_template_previews.py` and `tools/make_palette_thumbnails.py`: a note to re-run the record tool after regenerating.

**Choices made without asking** (in TD-099):
- **A second record**, not entries in `licenses/manifest.json`. That manifest accepts CC0 downloads only, and files made here are neither.
- **The repository's own licence (Apache-2.0)** for files made here. `libgdx.png` is recorded truthfully as the libGDX logo from the gdx-liftoff template, used only by the unbuilt `core/`.
- **Not carried into new projects.** A project's files are the user's to edit, so a copied checksum would go stale.
- **Copies of recorded downloads are allowed and reported**, not failed. Their licence is known from the same bytes' record, and deleting them is already the owner's open decision.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `python tools/record_original_assets.py`, then `--check` | 0, 0 | 71 originals recorded; the second run unchanged; 3,366 copies listed |
| `./gradlew --max-workers=1 :engine-core:test :editor-core:test --tests "*.asset.*" --continue` (`-Xmx800m`) | 0 | **engine-core: 60 tests, 0 failures** (whole module). **editor-core: 80 tests, 0 failures** (the `asset` packages, the library included) |
| `./gradlew --max-workers=1 :editor-desktop:test --tests "*.audit.*" "*TemplateGallery*"` | 1 | 1 failure: the inventory report's tables were stale, now that 71 files have a record. Expected; the inventory was then changed to use the record (section 2) |
| `GDXUNITY_UPDATE_AUDIT=1 ... --tests "*SampleInventoryTest"`, then the same run without the variable | 0 | **61 tests, 0 failures:** TemplateGalleryProjects 2, AcceptanceProject 1, ActionInventory 6, ComponentConsistency 5, EditabilityAudit 8, NoForeignGameArt 1, SampleInventory 8, ShippedSpritesProvenance 2, TemplateAudit 5, TemplateMatrix 14, TemplateRatings 3, ThirdPartyAssets 2, WebUxContract 4. The inventory now cites `licenses/originals.json` for 23 files |

The web suite (`engine-babylon`) was not run: no scene format, component or graph node changed.

## 4. Known limitations and next steps

**The raw Kenney downloads at the top of `assets/` would ship.** They are untracked: `Isometric/`, `Side/`, `kenney_nature-kit/`, `kenney_building-kit/`, the two zips, `License.txt`, `Preview.png`, `Sample.png` and three `.url` files.
- The editor package takes all of `assets/`, so a package built on this machine would carry 3,366 duplicate sprites, the kits' DAE, FBX, OBJ and STL models and the zips.
- **Their licence is known**, since they are byte for byte the recorded CC0 files, but they are not needed.
- Deleting them is **the owner's decision** (already open in the progress note). I left them in place.

**Limitations:**
- **No new 2D pack yet.** Pixel Platformer, UI Pack and Game Icons are approved and wait for a person to download them; the import step exists (UI M4). The other B1 sources wait for the owner's approval.
- **OpenGameArt listings usually come without a licence file**, which `PackImporter` refuses. Importing one needs the listing's licence to be recorded, still to be built once a listing is approved.
- **New projects:** files made here and copied into a new project have no record there; the Content Browser says "No licence record".
- **Records must be kept current:** regenerating a preview or thumbnail means re-running `tools/record_original_assets.py`, and the audit test fails until it is.
- **Only 2D files are covered.** Models, sounds and other files made here are not yet in `originals.json`; that is M2b.7's per-asset matrix.

**Next on the chart:** M2b.6 (sample migration inventory and first refresh) and M2b.7 (desktop and web manifest matrix).

## 5. Diff review and Git

All of section 2. No sample, prefab or downloaded file was changed; the owner's raw downloads were left untouched.

**Git:** no commit, push or other Git operation was performed. The work since `2391dad` (M2b.4, M3.6, M3.10 and this milestone) is uncommitted.
