# Migration to UrConnex: asset storage report

**Date:** 2026-11-26. **Status:** proposal and measurements. **Owner decisions, 2026-11-26:**

- Step 0 is approved and done: about 2.35 GB of regenerable output was deleted (see section 5).
- The storage backend (decision 2) is **paused until urconnex-api is ready**.
- The database storage feature is added at the end of the roadmap as **A21**, after A20.
- Decisions 3 to 7 are still open.

**Goal (owner, 2026-11-26):** store the engine's assets in the UrConnex database and load them through the UrConnex API, to reduce the disk space the engine takes.

**Scope and limits:**
- AGENTS.md §2 limits this session to this repository, so the UrConnex side (its database, API, storage and authentication) was **not inspected**. Everything said about UrConnex below is a requirement or an open question, not a finding.
- Roadmap gate A16 (UrConnex ecosystem integration) already plans authenticated, tenant-scoped asset access. This migration belongs there, behind an approved API contract.
- The A12 [asset-sources investigation](alpha-a12-asset-storage-investigation.md) already recommends UrConnex storage for source files that cannot be re-downloaded.

## 1. Where the disk space actually goes (measured)

The working copy measured 3.6 GB on 2026-11-26 (Python `os.walk` over every file). **The `assets/` folder is 138 MB of that: about 4%.**

| Item | Size | What it is | Regenerable? |
|---|---|---|---|
| `build/` (root) | 1,651 MB | Test and audit scratch: `build/cc-by` 868 MB and `build/oga` 762 MB (copied audit projects), plus `m7a6` and others | Yes: ignored by Git; rebuilt by the tasks that wrote them |
| `editor-desktop/build/` | 915 MB | `package` 735 MB (two packaged editor zips with a JRE and a sample project), `resources` 139 MB (a copy of `assets/`), `libs` 26 MB | Yes: `zipEditorPackage` and the build |
| `engine-babylon/` | 327 MB | `node_modules` 270 MB, `public` 31 MB (asset copies for the web), `dist` 24 MB | Yes: `npm install` and the web build |
| `.git/` | 213 MB | Repository history, which **includes every committed asset version** | No (see section 6) |
| `asset-sources/` | 141 MB | Untracked downloaded sources (`.blend`, `.zip`, `.xcf`); not used at runtime | Mostly re-downloadable (A12 report) |
| **`assets/`** | **138 MB** | **The runtime asset library: 10,118 tracked files** | No: this is the source of truth |
| Other module `build/` folders | ~75 MB | Compiled classes and test results | Yes |
| `.gradle/` | 95 MB | Gradle's project cache | Yes |
| `docs/` | 54 MB | Mostly report captures (`docs/reports` 38 MB) | No |
| `engine-web/` | 30 MB | The TeaVM web build | Yes |

**Inside `assets/`** (138 MB):
- `models` 43.9 MB, `kenney_nature-kit` 28.1 MB, `characters` 14.3 MB, root files 12.3 MB, `textures` 9.3 MB, `thirdparty` 8.8 MB, `sculpts` 7.2 MB, `kenney_building-kit` 4.3 MB, the rest under 4 MB each.
- By type: `.glb` 22 MB, `.fbx` 20 MB, `.png` 19 MB, `.dae` 15 MB, `.zip` 12 MB, `.jpg` 11 MB, `.gltf` 10 MB, `.bin` 9 MB, `.sculpt` 7 MB.
- Some of these are raw pack formats the engine does not load (`.fbx`, `.dae`, `.obj`, `.stl`, `.zip`). They are candidates to leave the runtime library, whatever the storage decision.

**What this means:**
- Moving `assets/` to UrConnex removes at most about 138 MB from the working copy. It also removes the copies the builds make of it (`editor-desktop/build/resources` 139 MB, part of the package, `engine-babylon/public` 31 MB), for roughly **0.3–0.5 GB in total**.
- Deleting regenerable output (`build/cc-by`, `build/oga`, old package zips) frees about **2.4 GB today, with no code change**. That cleanup is the fastest way to reduce disk use. It only needs the owner's go-ahead, because it deletes files.
- `.git` keeps every asset ever committed. Moving assets out does not shrink history unless it is rewritten, which is destructive and needs a separate decision (section 6).

## 2. How assets load today (this repository)

| Path | Today | Fit for a remote store |
|---|---|---|
| Game runtime and web | `AssetSource` (TD-036), with `directory(File)` for desktop and `internal()` for the web. Scenes, graphs, animations, models and textures load through it. | **Good.** A remote implementation can resolve each reference to a locally cached file handle, which is what the LibGDX loaders need. |
| Project contract | `ProjectStore` (TD-106) with `LocalProjectStore`. `project-manifest.json` lists every asset with its size and SHA-256. Its documentation already names a UrConnex store for A16 and requires server-side revision checks. | **Good.** The manifest's hashes make content-addressed storage and integrity checks straightforward. |
| Editor | Direct file access: 30 source files in `editor-core`, 14 in `editor-desktop`, and 6, 4, 3 and 2 in `editor-terrain`, `editor-sculpt`, `editor-sprite` and `editor-audio` use `File`, `Path` or `Files` (a count of files, not call sites). The asset browser, the import paths (Poly Haven, ambientCG, packs), prefabs, terrains, sculpts, mesh saving and sprites all write into the project folder. | **The main work.** The editor creates and changes assets, so it needs upload, revision and conflict handling, not only download. |
| Tests | 90 test classes read the shipped `../assets` folder directly. | They need a small committed test library, or a cached fetch in CI (section 5, step 5). |
| Packaging | `zipEditorPackage` copies `assets/` as the sample project (TD-033). `generateAssetList` writes `assets/assets.txt` for the web. | Packaging would ship a starter subset, with the rest fetched on demand. |

## 3. Proposed architecture (to be agreed with the UrConnex side)

1. **Content-addressed assets.**
   - Each file is stored once, keyed by its SHA-256, which the TD-106 manifest already records.
   - A project refers to assets by project path. The manifest maps each path to a hash.
   - Identical files across projects and tenants are stored once, if the UrConnex side allows deduplication across tenants. Otherwise deduplication is per tenant.
2. **Where the bytes live** (UrConnex's choice; recommendation):
   - Keep metadata in PostgreSQL: id, hash, size, media type, licence record, owner and tenant, and references.
   - Keep the file bytes in object storage, or a blob store behind the API, rather than in table rows. Large `bytea` values bloat backups and vacuum and are slow to stream. PostgreSQL large objects are workable but harder to back up and restore selectively.
   - If the owner wants the bytes in the database itself, `bytea` with size limits and streaming endpoints works for the 138 MB library. It should be measured against backup and restore times first.
3. **API** (versioned, authenticated, tenant-scoped; to be specified in the UrConnex repository):
   - `GET` an asset by hash, with range requests, `ETag` equal to the SHA-256, and cache headers.
   - `HEAD` to check existence before uploading.
   - Upload with server-side hash verification, type and size limits, and quota checks.
   - Project manifest read and write with atomic revision compare (`409` on conflict, as `ProjectStore` requires).
   - A licence record per asset.
   - IDs, not file paths, in every URL, so there is no path traversal.
4. **Engine side** (this repository):
   - **Engine core:** a `RemoteAssetSource` implementing `AssetSource`. It keeps a bounded on-disk cache keyed by hash, verifies the hash of every download, and prefetches what a scene needs (from the manifest) when the scene opens, before Play. A missing or failed asset is an explicit error that names the file, never a silent stand-in (AGENTS.md §6).
   - **Editor:** a `ProjectStore` for UrConnex, plus moving the editor's direct file access behind `AssetSource` and `ProjectStore`, module by module.
   - **Offline:** cached assets keep working offline. Roadmap A03 and A16 require offline startup and a failed-network path. A starter set stays bundled so a new project works without a connection.
   - **Credentials:** desktop sign-in uses the approved token flow, with tokens in the OS credential store and no embedded secrets (roadmap A16). The web uses its existing session.
5. **Licences:**
   - Every asset keeps its licence record: `licenses/originals.json`, `docs/assets/asset-license-manifest.json` and the third-party manifests.
   - CC-BY assets (TD-103) must keep their attribution wherever they are shown or exported.
   - Assets with an unknown licence (for example `asset-sources/opengameart/mansedj`, per the A12 report) must not be uploaded.
   - Serving third-party files from a UrConnex server is redistribution. CC0 and CC-BY allow it, with attribution for CC-BY. Any other licence needs checking first.

## 4. What does not change

- Scene, prefab, material and terrain formats. Assets are still referred to by project path; only where the bytes come from changes.
- The web player: Babylon already loads files over HTTP and would point at the UrConnex API instead of `public/`, with CORS and authentication.
- Approved TDs: TD-036 (`AssetSource`) and TD-106 (project contract) are extended, not replaced. A new TD records the storage decision (section 7).

## 5. Proposed migration steps

| Step | What | Disk effect | Needs |
|---|---|---|---|
| 0 | **Clean regenerable output.** **Done 2026-11-26:** deleted `build/cc-by/{export-verification, export-template, web-check}`, `build/oga/{export-verification, audit-project}` and `editor-desktop/build/package`. The small evidence files the reports cite (performance `.tsv` files, verification scripts, converted `.glb` files) were kept. `node_modules` was left alone. | **−2.35 GB** (the two build folders went from about 2.57 GB to 211 MB) | Done with owner approval |
| 1 | Asset inventory: which of the 10,118 files are used by templates, samples and tests, which are raw pack formats not loaded at runtime, and the licence of each | None (report) | Nothing |
| 2 | TD-110 (storage on UrConnex) and the API contract, agreed with the UrConnex side | None | Owner approval; UrConnex work in its own repository |
| 3 | `RemoteAssetSource` and its cache in the engine, behind a setting, with the local folder as the default; tested against a fake server | None | TD-110 |
| 4 | UrConnex `ProjectStore` and the editor's file access moved behind `AssetSource` and `ProjectStore` | None | TD-110; A16 contract |
| 5 | A small committed test library (only what the 90 test classes need), or a hash-verified fetch in CI | Lets the tests stop depending on the full library | Step 1 |
| 6 | Upload tool: a dry run first, then an upload of the inventoried library with hashes and licence records, verified by downloading everything back | None locally | An isolated UrConnex test environment (roadmap A16) |
| 7 | Remove the uploaded library from the working tree, keeping the bundled starter set | About −0.1 GB plus the build copies (about −0.3 GB) | Steps 3–6 passing; owner approval |
| 8 | Optional: shrink `.git` history (section 6) | Up to about −0.2 GB per clone | Separate owner decision |

## 6. Git history

- `.git` is 213 MB and holds every committed asset version.
- After step 7, new clones still download that history.
- The options:
  - Leave it.
  - Rewrite history to drop the asset files (`git filter-repo`). This is destructive: it changes every commit id and needs every clone re-made.
  - Start a fresh repository.
- None of these is proposed without an explicit owner decision. AGENTS.md §3 forbids rewriting history otherwise.

## 7. Decisions needed from the owner

1. **Step 0:** may regenerable output be deleted now (about 2.4 GB)? This is the largest and quickest saving.
2. **Storage:** file bytes in object storage with metadata in PostgreSQL (recommended), or the bytes in PostgreSQL itself?
3. **Contract:** who writes the UrConnex API contract, and in which repository? It is a UrConnex change, so a session in that repository or its agent does it (AGENTS.md §2).
4. **Bundled starter set:** which assets stay bundled for offline use and new projects?
5. **Raw pack formats** (`.fbx`, `.dae`, `.obj`, `.stl`, `.zip` in `assets/`): drop them from the runtime library, or keep them as sources in UrConnex?
6. **Git history:** keep, rewrite, or start fresh?
7. **Sequencing:** this is A16 scope, and A16 depends on A11 and A15 in the roadmap. Run it now (the roadmap order changes, so `milestone-roadmap.md` needs updating first), or after A15?

## 8. Risks

- **Network dependence:** the editor and games must not hang or fail silently when UrConnex is unreachable. The cache, prefetch and explicit errors are needed before any asset is removed locally.
- **Tenant leakage:** shared deduplicated storage must never let one tenant learn of or read another tenant's private assets. Hash-existence checks across tenants can leak this, so deduplicate per tenant unless an asset is public.
- **Licence drift:** uploaded files must keep their licence records, and CC-BY attribution must survive export.
- **Backup size and restore time** grow if the bytes are stored in PostgreSQL.
- **Test coverage:** 90 test classes depend on the shipped library. Removing it before step 5 would break them.

No commit, push, deletion, upload or change to any other repository was made for this report.
