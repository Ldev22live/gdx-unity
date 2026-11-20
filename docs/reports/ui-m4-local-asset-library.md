# UI M4: Local Asset Library and Pack Import

**Date:** 2026-11-19. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed).

Milestone **M4** of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs", under TD-094 (the third-party manifest) and TD-095 (online on use, offline first, high-definition imports), both approved by the owner on 2026-11-19. **Status:**
- **Implemented and Integrated.** The Assets drawer has an **Import** tab listing the approved Kenney packs. A person downloads a pack in their browser and imports the zip there. ForgeRoom checks it whole, copies the files it reads into the project, records them in `licenses/manifest.json`, and lists what it left out.
- **Manually Not Verified:** no real Kenney archive was imported. A person must download one, because Kenney's `robots.txt` disallows agents. It is on tonight's E2E checklist.
- **Not this milestone:** no network code. Poly Haven and ambientCG search are M5 and M6.

## 1. Acceptance criteria

| # | Requirement (brief, TD-094, TD-095) | Result | Evidence |
|---|---|---|---|
| 1 | Kenney: a curated source and a local pack importer; no undocumented API assumed | **Met** | `KenneyPacks`: the 9 approved packs, each with its page (`https://kenney.nl/assets/<id>`) and the CC0 statement (`https://kenney.nl/support`). "Open page" opens the person's browser; "Import..." takes the downloaded zip |
| 2 | Validate archives: safe paths, no executables or scripts | **Met** | `PackImporter` refuses the whole archive, writing nothing, for an absolute path, `..`, a backslash or drive path, or an executable or script (exe, dll, sh, bat, ps1, py, js, jar and others). Entries are only ever written as plain files under the pack's folder (the target path is checked again after normalising) |
| 3 | Validate the licence; CC0 only (TD-094) | **Met** | The archive must hold a licence file (`License*` or `Licence*`) that says CC0 ("cc0", "creative commons zero" or "publicdomain/zero"); otherwise it is refused |
| 4 | Supported formats only; never import silently | **Met** | glTF, GLB, BIN, PNG, JPG, WAV and OGG are copied, plus TXT and MD (licence and readme). Anything else (FBX, OBJ, `.blend`, SVG and so on) is left out, listed in the result, the Console and the manifest's import status |
| 5 | Size limits for high-definition assets (TD-095) | **Met** | Up to 4 GB uncompressed per pack, checked from the archive's sizes and again while copying. At most 50,000 entries. A copy that fails part way removes what it wrote |
| 6 | Keep dependencies working | **Met** | The archive's own layout is kept under `thirdparty/<provider>/<pack>/`, so a glTF still finds its `.bin` and textures |
| 7 | Store provider metadata and licences | **Met** | One manifest entry per pack: source page, CC0-1.0, licence evidence, author, retrieval date, the archive's file name (never a folder of the person's machine), formats, import status, and each file's path, original name, size and SHA-256. The editor writes the manifest **byte for byte as the download tool does** (a test rewrites the shipped manifest and compares) |
| 8 | Re-importing is safe | **Met** | It replaces the files the pack's earlier import recorded, and only those. A file of the person's in the same folder stays |
| 9 | Off the render thread | **Met** | The import runs on a daemon worker thread; the result returns with `Gdx.app.postRunnable`. One import at a time |
| 10 | Imported assets usable in the world | **Met** (existing) | The Content Browser rescans after an import. Imported models, textures and sounds are placed by the existing drag and drop, and recorded files pass the `ThirdPartyAssetsTest` guard |

**Mutation checks** (each restored; `cmp` identical):

| Change | What failed |
|---|---|
| The CC0 licence check skipped | "Expected RefusedException to be thrown, but nothing was" |
| An earlier import's files not removed | "the earlier import's file is gone: expected false but was true" |

## 2. What was built

- **`engine-core` `ThirdPartyManifest`:** `entry(id)`, `with(entry)` (replaces by id, keeps id order, refuses a file listed twice), `toJson()` (Python's `json.dumps(indent=2)` layout and escaping), and `write(assetsRoot)`.
- **`editor-core` `asset/library`** (new):
  - `CuratedPack`: provider, id, page, author and licence evidence, all checked;
  - `KenneyPacks`;
  - `PackImporter`, with `Result` and `RefusedException`, which lists every problem.
- **`editor-desktop`:**
  - `PackImportPanel` (new): the Import tab, with each pack's status from the manifest, Open page, Import..., a worker thread, and the result in the status bar, the Console and an error dialog;
  - `EditorApplication`: the Assets drawer's tabs are **Project** and **Import**. The testing aid `GDXUNITY_EDITOR_DRAWER=Assets:Import` opens the Import tab.
- **Not duplicated:** the existing Project > Import Asset... still imports single files of the project's own; packs with a licence come through Import.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-core:test --tests "*ThirdPartyManifestTest" :editor-core:test --tests "*PackImporterTest"` | 0 | 6 and 6, all passing on the first run. One test had an escape fixed before it ran: Java turns `Ü` into the letter in source |
| The two mutation checks | 1 each | As listed; restored |
| `./gradlew :<module>:cleanTest :<module>:test`, one module at a time | 0 each | `engine-core` 49 → **51**; `editor-core` 552 → **558** (1 skipped, as before); `editor-desktop` 187 |
| `APPDATA=<empty> GDXUNITY_EDITOR_DRAWER=Assets:Import GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--scene samples/polyhaven-showcase-3d.json --smoke-test"` | 0 | [ui-m4/assets-import-tab.png](ui-m4/assets-import-tab.png): the Assets drawer's Import tab, with the nine packs "Not imported", Open page and Import... |

## 4. Known limitations and next steps

- **Only zip archives are imported.** A single downloaded file (OpenGameArt's `door_open.wav`) has no licence file inside. It needs a curated entry that records its licence page instead; that is next with M5's provider model.
- **Zip links:** Java's zip reader does not show a symbolic link as a link. Such an entry would be written as a plain file holding the link's text, never as a link, so it cannot point outside the folder.
- **Placement presets:** Kenney packs often hold several formats of the same model (FBX, OBJ, GLB). Only the GLB or glTF are imported. Choosing a pack's "best" model per object is not automated.
- **Next: M5,** Poly Haven search in the Assets drawer (live API, User-Agent `ForgeRoom/<version>`, "Powered by Poly Haven" credit, cache, offline message), built on this importer's checks and manifest.

## 5. Diff review and Git

- **New:**
  - `CuratedPack`, `KenneyPacks`, `PackImporter`, `PackImporterTest`, `PackImportPanel`;
  - this report and `docs/reports/ui-m4/`.
- **Changed:**
  - `ThirdPartyManifest` and `ThirdPartyManifestTest` (+2);
  - `EditorApplication`;
  - `AGENTS.md` (the testing aid), `INDEX.md`, `FEATURE_TEST_MATRIX.md`, and the E2E checklist.

No files outside this repository were touched.

**Git:** no commit, push or other Git operation was performed.
