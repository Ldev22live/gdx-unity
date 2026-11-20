# UI M5: Poly Haven Integration

**Date:** 2026-11-19
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `0469ceb` (nothing committed)

Milestone **M5** of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs". It works under TD-094 (the third-party manifest) and TD-095 (online on use, offline first, high-definition imports), both approved by the owner on 2026-11-19.

**Status: Implemented and Integrated.**
- The Assets drawer has a **Poly Haven** tab. It searches Poly Haven's models and textures and shows each asset's authors, categories and CC0 licence. A person chooses a resolution (2K first, with its size) and downloads the asset into the project.
- Every file is checked against Poly Haven's MD5 and recorded with its SHA-256. A texture set also gets a ready-to-use `.material`.
- **Seen working against the live Poly Haven API from the editor:**
  - a search;
  - a 2K texture download that replaced an earlier 1K one;
  - a 2K model download, which then drew in the game runtime.
- **Manually Not Verified:** no person has clicked the tab. It is on the E2E checklist.

## 1. Acceptance criteria

The requirements come from the brief, TD-094 and TD-095.

| # | Requirement | Result | Evidence |
|---|---|---|---|
| 1 | Search Poly Haven and show results in the Asset Library | **Met** | Search by name, tag or category; every word must match; most downloaded first. Models and Textures each have their own catalogue: 522 models and 870 textures live. The API has no text search, so the catalogue is fetched once per session and searched locally. `PolyHavenProviderTest`; screenshot [poly-haven-search.png](ui-m5/poly-haven-search.png) ("fire pit" finds Stone Fire Pit). |
| 2 | Preview, licence, format and size before downloading | **Met** (thumbnails are UI M7) | The details show the name, authors, categories, "Licence: CC0 1.0 (public domain)" with Poly Haven's licence page, where the files will go, an **Open page** button, and each resolution with its total size ("2K - 9.2 MB"). Thumbnail images are M7's "previews". |
| 3 | Online only when used (TD-095) | **Met** | Nothing is requested at start-up. The first request is made when the tab is opened, a search is run or Download is pressed. A test asserts zero requests until the catalogue is asked for, and one request per session after that. |
| 4 | User-Agent `ForgeRoom/<version>`, nothing personal (TD-095) | **Met** | `JdkHttpSource` sends `ForgeRoom/1.0.0` from the packaged launcher (`ForgeRoom/1.0.0-dev` in a development run). Only the API's own addresses are requested. Only HTTPS to `polyhaven.com` and `polyhaven.org` (and their sub-domains) is allowed; anything else is refused before connecting. |
| 5 | "Powered by Poly Haven" credit wherever live results are shown | **Met** | It sits under the results at all times and opens polyhaven.com. Screenshot. |
| 6 | Offline first: cache with fetch time; a message, never a frozen editor or a silent empty list | **Met** | Catalogues are kept in `<settings>/cache/asset-library/polyhaven/`. When Poly Haven cannot be reached, the tab shows the cached catalogue as "Offline, from <date and time> (<reason>)". With no cache it says Poly Haven cannot be reached and nothing is cached yet. All network work runs on a worker thread. Tested both ways. **Not observed** in the editor: no network was switched off. |
| 7 | High definition: resolutions offered, 2K first, 4 GB limit, confirm above 500 MB | **Met** | Every resolution with all the needed files is offered (1K to 8K for textures, 1K to 4K for models). The preferred one is 2K, else the largest below it, else the smallest. A download over 4 GB is refused before anything is requested, and the size is enforced again while reading. Above 500 MB the button first reads "Download (812 MB)...", then "Confirm 812 MB", so the size is shown before anything is fetched. |
| 8 | Check the provider's MD5; record SHA-256 (TD-094) | **Met** | A file whose MD5 differs is refused, and so is one larger than Poly Haven stated. Each file's SHA-256 and size go in `licenses/manifest.json`, in the same layout as `tools/thirdparty_assets.py`. The live download's three files were re-hashed afterwards: sizes and SHA-256 match. |
| 9 | Free disk space checked first (TD-095) | **Met** | The project's disk and the staging folder's disk each need the download plus 64 MB. Otherwise it is refused with both numbers. Tested. |
| 10 | Safe paths and readable formats only; nothing imported silently | **Met** | It applies `PackImporter`'s rules: no `..`, absolute paths, drives or backslashes, and only glTF, GLB, BIN, PNG, JPG, WAV and OGG. A file list that breaks a rule is refused whole, listing every reason. Tested with three bad entries. |
| 11 | A failed or cancelled download leaves the project unchanged | **Met** | Files are downloaded to a temporary staging folder. They only move into the project once all have arrived and been checked. An earlier download's files are moved aside first and put back if a move fails. Tested for a damaged file (the earlier download and the manifest unchanged byte for byte) and for Cancel (no folder, manifest or material written; staging removed). |
| 12 | Imported assets usable in the world | **Met** | **Textures:** the download makes `materials/<Name>.material`, named after the asset (`RedBrick03`) as materials must be. Its base colour, OpenGL normal and ARM maps are wired to their slots. Downloading the same asset again replaces only its own files and keeps the person's material settings; the live run kept the tiling of 3 × 1.5 and moved the paths to the 2K files. **Models:** the glTF keeps its layout, so it finds its buffer and textures. The downloaded Wooden Crate 02 drew textured with a shadow in the game runtime: [downloaded-crate-in-game.png](ui-m5/downloaded-crate-in-game.png). The Content Browser rescans after a download. |
| 13 | Provider-neutral interface (roadmap M2b.1) | **Met** | `AssetProvider`, `AssetType`, `RemoteAsset`, `DownloadOption` and `HttpSource` have nothing Poly Haven-specific. ambientCG (M6) plugs into the same tab class and installer. |

**Mutation checks** (each restored afterwards, with `cmp` identical):

| Change | What failed |
|---|---|
| The MD5 check skipped | 1 of 11 `RemoteAssetInstallerTest` tests |
| No cached catalogue served when offline | `shouldServeTheCachedCatalogueMarkedOfflineWhenTheProviderCannotBeReached`: "Poly Haven cannot be reached ... and nothing is cached yet" |

## 2. What was built

**`editor-core` `asset/library`** (provider-neutral):
- `AssetProvider` (with `Catalog.search` and `preferred`);
- `AssetType` (Models, Textures);
- `RemoteAsset`;
- `DownloadOption` (with `RemoteFile`, `Role` and `formatBytes`);
- `HttpSource` and `JdkHttpSource`: the JDK's own HTTP client, so no new dependency; User-Agent and host allow-list.

**`editor-core` Poly Haven and installing:**
- `PolyHavenProvider`: parses `/assets?t=models|textures` and `/files/<id>`, caches catalogues, offline fallback.
- `RemoteAssetInstaller`:
  - checks, staging, MD5 and SHA-256;
  - moves into `models|textures/thirdparty/polyhaven/<id>/`, with rollback;
  - writes the manifest entry and the texture material.

**`editor-desktop`:**
- `ProviderLibraryPanel`: the tab. A type selector, search, results, details, resolution and size, Download / Confirm / Cancel, progress read each frame, and the credit.
- `EditorApplication`: the Assets drawer's tabs are now **Project**, **Import** and **Poly Haven**. Opening the Poly Haven tab loads the catalogue.
- Testing aids:
  - `GDXUNITY_EDITOR_DRAWER=Assets:Poly Haven`;
  - `GDXUNITY_EDITOR_LIBRARY_SEARCH=[textures:]<words>[!]`, which searches, selects the first result and, with `!`, presses Download.

**Choices made without asking** (milestone rule: pick the recommended option and record it):
- **No agent tool.** Rule 8 asks for one per editor operation, but TD-095 limits going online to a person's own use, and the R10 safety rules give agents no network tools. Agents still see the downloaded files like any other asset.
- **HDRIs are not offered.** ForgeRoom reads no `.hdr` or `.exr` files.
- **Textures are JPEG only.** Of the maps, only base colour, OpenGL normal and ARM are downloaded: the three a ForgeRoom material uses. PNG, EXR, DirectX normal and displacement are left out.
- **The cache is under the settings folder** (`%APPDATA%\GdxUnity\cache\asset-library` on Windows). `PlatformDirectories` has no cache folder, and adding one would change its record.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*PolyHavenProviderTest" --tests "*RemoteAssetInstallerTest"` | 0 | 9 and 11 tests, all passing on the first run |
| The two mutation checks | 1 each | As listed; restored |
| `./gradlew :editor-core:cleanTest :editor-core:test`, then the same for `editor-desktop` | see §3a | |
| `APPDATA=<empty> GDXUNITY_EDITOR_LIBRARY_SEARCH="fire pit" GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--project <copy> --scene samples/polyhaven-showcase-3d.json --smoke-test=900"` | 0 | Live: the models catalogue fetched (550,528 bytes) and cached; Stone Fire Pit found; "2K - 9.2 MB" selected. 0 errors. [poly-haven-search.png](ui-m5/poly-haven-search.png) |
| The same with `"textures:red brick 03!"` on a fresh project copy, `--smoke-test=1500` | 0 | Live download at 2K: 3 files (7.0 MB), MD5 checked. The earlier 1K files are gone. The manifest entry says "Poly Haven public API, 2K, MD5 checked", and the sizes and SHA-256 were re-verified. `RedBrick03.material` kept tiling 3 × 1.5 and now points at the 2K maps. 0 errors |
| The same with `"wooden crate 02!"`, `--smoke-test=1500` | 0 | The run ended while the download was under way. The screenshot shows "Cancel" and "Downloading Wooden Crate 02 (2K)" ([poly-haven-downloading.png](ui-m5/poly-haven-downloading.png)), and nothing partial was left in the project |
| The same, `--smoke-test=4000` | 0 | Downloaded: `wooden_crate_02_2k.gltf`, `.bin` and three 2K textures, with a `polyhaven-wooden-crate-02` manifest entry |
| `./gradlew :engine-desktop:run --args="--project <copy> --scene samples/m5-crate-check.json --smoke-test=120"` (the showcase with the fire pit swapped for the crate) | 0 | 120 frames, 0 runtime errors; the crate draws textured: [downloaded-crate-in-game.png](ui-m5/downloaded-crate-in-game.png) |

### 3a. Per-module test counts

Counts are from the XML reports, after `cleanTest`, with each module run on its own.

| Module | Exit | Before | After |
|---|---|---|---|
| `editor-core` | 0 | 558 (1 skipped) | **578** (1 skipped, the same POSIX-only test) |
| `editor-desktop` | 0 | 187 | **187** |

Other modules are unchanged; no file outside `editor-core` and `editor-desktop` changed. The documentation guards (`*.audit.*`, `WebUxContractTest`, `ActionInventoryTest`) were run again after the documents changed: exit 0.

**Package:**
- `./gradlew :editor-desktop:zipEditorPackage` rebuilt `ForgeroomEditor-1.0.0-windows-x64.zip` (exit 0) for tonight's E2E.
- The packaged `ForgeroomEditor.exe` was run with `GDXUNITY_EDITOR_DRAWER="Assets:Poly Haven"` on a project copy and an empty settings folder, `--smoke-test=600`. Exit 0, 0 errors.
- Opening the tab loaded the live catalogue: "522 models (first 300 shown; search to narrow) on Poly Haven, all CC0", with the credit under the list.

## 4. Known limitations and next steps

- **Thumbnails are not shown.** That is UI M7's "previews". The thumbnail address is already read (`RemoteAsset.thumbnailUrl`).
- ~~**No in-editor check that a model is placed at the right size.**~~ **Corrected 2026-11-20 (UI M7):** this was wrong. A placed glTF already gets the metres-to-units scale: the Model component's import scale defaults to 100, the same as the shipped Poly Haven models. What M7 added is placing a download standing on the ground ([ui-m7-previews-and-placement.md](ui-m7-previews-and-placement.md)).
- **Interrupted runs can leave staging folders.** If the editor is closed mid-download, the staging folder in the system's temporary folder may stay, though the project is untouched. None was found after the interrupted run here. A start-up sweep of old `forgeroom-download-*` folders would make sure.
- **No resuming of an interrupted download.**
- **The offline state was not seen in the editor.** It is tested only.
- **Next: M6, ambientCG**, on the same interface, tab and installer: API v3 search; its zips have no hash, so SHA-256 is recorded.

## 5. Diff review and Git

**New:**
- `editor-core`, in `asset/library`:
  - `AssetProvider`, `AssetType`, `RemoteAsset`, `DownloadOption`, `HttpSource`, `JdkHttpSource`, `PolyHavenProvider`, `RemoteAssetInstaller`;
  - tests: `FakeHttpSource`, `PolyHavenProviderTest`, `RemoteAssetInstallerTest`.
- `editor-desktop`: `ProviderLibraryPanel`.
- This report and `docs/reports/ui-m5/`.

**Changed:**
- `EditorApplication`: the Poly Haven tab, the cache folder, the version, and the two testing aids;
- `AGENTS.md` (the testing aids), `INDEX.md`, `FEATURE_TEST_MATRIX.md`, the milestone charts, and the E2E checklist and test plan.

**Not touched:**
- the shipped `assets/` (the live downloads went into scratch copies);
- the user's earlier uncommitted changes.

**Git:** no commit, push or other Git operation was performed.
