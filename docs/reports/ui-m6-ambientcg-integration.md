# UI M6: ambientCG Integration

**Date:** 2026-11-19
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `0469ceb` (nothing committed)

Milestone **M6** of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs", under TD-094 and TD-095 (both approved 2026-11-19).

**Status: Implemented and Integrated.**
- The Assets drawer has an **ambientCG** tab beside Poly Haven. It searches ambientCG's 2,042 CC0 materials, shows each one's licence, and offers its JPEG resolutions with their sizes, 2K first.
- **Downloading** unpacks the zip with the pack importer's rules and records SHA-256 for every file; ambientCG gives no hash of its own. It then makes a `.material` that uses the maps.
- **Built on M5's provider-neutral interface:** the same tab class and the same installer. The installer gained archive unpacking.
- **Seen working against the live ambientCG API from the editor:**
  - a search and a 2K download of Ground 111;
  - the material then drew in the game runtime.
- **Manually Not Verified:** no person has clicked the tab.

## 1. Acceptance criteria

| # | Requirement (brief, TD-095) | Result | Evidence |
|---|---|---|---|
| 1 | Search ambientCG (API v3) with a type filter | **Met**, materials only | `AmbientCgProvider` reads `https://ambientcg.com/api/v3/assets?type=material&sort=popular`, 500 per page, until `totalResults`. That is 2,042 materials live, in 5 requests and about 580 KB. The catalogue is searched locally like Poly Haven's, keeping ambientCG's popularity order. **The type filter is fixed to materials:** ambientCG's 3D models are OBJ or FBX and its HDRIs EXR or HDR, which ForgeRoom does not read, and the API ignored a model type filter when it was tried live (it returned all 2,938 assets). Tests: paging stops at `totalResults`, popular order is kept, and an unsafe id is dropped. |
| 2 | Licence, format and size before downloading | **Met** | The details show the name, "By ambientCG", "Licence: CC0 1.0 (public domain)" with its licence page, where the files will go, and each JPEG zip with its size ("2K-JPG - 32.8 MB"). The credit "Materials from ambientCG (CC0)" is under the results. Screenshot [ambientcg-downloaded.png](ui-m6/ambientcg-downloaded.png). |
| 3 | Zip downloads; no hash, so SHA-256 recorded | **Met** | The zip's stated size is enforced while downloading. Each unpacked file's SHA-256 and size go in the manifest, and `retrievedWith` says "ambientCG API v3, 2K-JPG zip (no source hash; SHA-256 recorded)". The live download's 7 files were re-hashed afterwards, and all match. |
| 4 | The same checks as M4 and M5 | **Met** | **The whole zip is checked before anything is written:** safe paths only, no executable or script, at most 50,000 entries, and at most 4 GB unpacked, enforced again while unpacking. **Only readable formats are kept;** the rest are listed in the result, the Console and the manifest's import status. The 500 MB confirmation, free-space check, staging and rollback are M5's. Tests: an archive holding `setup.exe` and `../outside.jpg` is refused with both reasons, and nothing is written in the project or outside it. |
| 5 | Usable in the world | **Met** | `materials/Ground111.material` takes the colour, OpenGL normal (not DirectX), roughness and ambient occlusion maps. Metalness is 0, because a grey roughness map has the same value in its blue channel, which glTF reads as metal. The live material drew on the showcase floor in the game runtime: [ambientcg-material-in-game.png](ui-m6/ambientcg-material-in-game.png). The Content Browser rescanned ("8 added"). |
| 6 | Online only when used; offline cache | **Met** | As M5: nothing is requested before the tab is opened or a search is run. The catalogue is cached in `<settings>/cache/asset-library/ambientcg/materials.json` and shown "Offline, from <date>" when ambientCG cannot be reached. Tested both ways. |
| 7 | Only the provider's hosts | **Met** | `ambientcg.com`, plus `struffelproductions.com`, the host its download links redirect to (seen live: `302` to `acg-download.struffelproductions.com`). Only HTTPS. |

**Mutation checks** (each restored afterwards, with `cmp` identical):

| Change | What failed |
|---|---|
| Executables in an archive not refused | 1 of 14 `RemoteAssetInstallerTest` tests |
| Metalness 1 for separate maps | `shouldMakeAMaterialFromSeparateRoughnessAndOcclusionMapsWithNoMetalness`: "a grey roughness map's blue channel must not read as metal ==> expected: <0.0> but was: <1.0>" |

## 2. What was built

**`editor-core` `asset/library`:**
- **`AmbientCgProvider`** (new): paged catalogue, cache and offline fallback, JPEG zip options, and a map role for each unpacked file.
- **`RemoteAssetInstaller`:**
  - downloads with the `ARCHIVE` role are unpacked after downloading, by the same rules as `PackImporter`, and the zip itself is not kept;
  - `Result.leftOut`, and the manifest's import status lists the left-out formats;
  - a material can be made from separate roughness and occlusion maps.
- **`DownloadOption.Role`:** `ROUGHNESS`, `AMBIENT_OCCLUSION`, `ARCHIVE`.
- **`AssetProvider.roleOf`:** defaults to `OTHER`.

**`editor-desktop`:**
- the Assets drawer's tabs are now **Project**, **Import**, **Poly Haven** and **ambientCG**;
- `ProviderLibraryPanel` reports left-out files in the status bar and the Console;
- testing aids: `Assets:ambientCG` and `GDXUNITY_EDITOR_LIBRARY_SEARCH=ambientcg:<words>[!]`.

**Choices made without asking:**
- **Only JPEG zips are offered.** PNG zips are two to three times larger for the same maps.
- **Metalness maps are not used.** A ForgeRoom material reads metalness and roughness from one packed image, and packing them would need image processing. Metal materials therefore come in as non-metal, and that is listed below.
- **"By ambientCG" is the author.** The API names no author.
- **The preview image** (`Ground111.png`) and the unused maps (DirectX normal, displacement) are kept, because ForgeRoom reads their format.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*AmbientCgProviderTest" --tests "*RemoteAssetInstallerTest" --tests "*PolyHavenProviderTest"` | 0 | 5 + 14 + 9 tests, all passing |
| The two mutation checks | 1 each | As listed; restored |
| `APPDATA=<empty> GDXUNITY_EDITOR_LIBRARY_SEARCH="ambientcg:ground 111!" GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--project <fresh copy> --scene samples/polyhaven-showcase-3d.json --smoke-test=5000"` | 0 | Live: the catalogue fetched and cached (581,663 bytes); Ground 111 found; 2K-JPG (32.8 MB) chosen. The run ended at 57 % of the download, with progress showing. No staging folder was left behind, and nothing was written to the project |
| The same, `--smoke-test=16000` | 0 | Downloaded and unpacked: 7 images kept, and `.blend`, `.mtlx`, `.tres` and `.usdc` left out and listed. `Ground111.material` was made with metallic 0. The manifest entry checks out. 0 errors. [ambientcg-downloaded.png](ui-m6/ambientcg-downloaded.png) |
| `./gradlew :engine-desktop:run --args="--project <copy> --scene samples/m6-ground-check.json --smoke-test=120"` (the showcase with its floor switched to `Ground111.material`) | 0 | 120 frames, 0 runtime errors; the gravel ground draws: [ambientcg-material-in-game.png](ui-m6/ambientcg-material-in-game.png) |

### 3a. Per-module test counts

Counts are from the XML reports, after `cleanTest`, with each module run on its own.

| Module | Exit | Before (after M5) | After |
|---|---|---|---|
| `editor-core` | 0 | 578 (1 skipped) | **586** (1 skipped, the same POSIX-only test) |
| `editor-desktop` | 0 | 187 | **187** |

The documentation guards (`*.audit.*`, `WebUxContractTest`, `ActionInventoryTest`) were run again after the documents changed: exit 0.

**Package:**
- `./gradlew :editor-desktop:zipEditorPackage` rebuilt the package (exit 0).
- The packaged `ForgeroomEditor.exe` was run with `GDXUNITY_EDITOR_DRAWER="Assets:ambientCG"` on a project copy and an empty settings folder, `--smoke-test=900`. Exit 0, 0 errors.
- Opening the tab fetched and cached the ambientCG catalogue.

## 4. Known limitations and next steps

- **Metal materials come in as non-metal.** The metalness map is kept but not used. Packing it with roughness into one image would fix that.
- **Opacity maps are not wired.** Leaves and fences come in opaque.
- **Downloads can be slow.** ambientCG's server was much slower than Poly Haven's here: 32.8 MB took over a minute. Progress and Cancel cover that, but there is no resume.
- **No thumbnails yet.** ambientCG's thumbnail addresses are not read yet; that is M7's previews.
- **Next: M7,** previews (thumbnails), high-definition choices and placing downloads on terrain at the right size.

## 5. Diff review and Git

**New:**
- `AmbientCgProvider` and `AmbientCgProviderTest`;
- this report and `docs/reports/ui-m6/`.

**Changed:**
- code: `RemoteAssetInstaller` (and its test, +3), `DownloadOption`, `AssetProvider`, `ProviderLibraryPanel`, `EditorApplication`;
- docs: `AGENTS.md`, `INDEX.md`, `FEATURE_TEST_MATRIX.md`, both milestone charts, the E2E checklist and the test plan.

The shipped `assets/` was not touched; the live downloads went into scratch copies.

**Git:** no commit, push or other Git operation was performed.
