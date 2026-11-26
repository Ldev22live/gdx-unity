# Alpha A12: `asset-sources/` storage investigation (read-only)

**Date:** 2026-11-25. **Directive:** A12 §20, a separate asset-governance decision. **Status:** inventory done; **decision proposal for the owner**. Nothing was moved, deleted, staged, uploaded or added to `.gitignore`.

## 1. Inventory

**53 files, 147.8 MB, all untracked.** Measured with `os.walk` and SHA-256 over every file.

| Type | Files | Size |
|---|---|---|
| `.blend` (Blender sources) | 7 | 59.7 MB |
| `.zip` (downloaded archives) | 5 | 40.3 MB |
| `.png` (texture sources) | 15 | 30.7 MB |
| `.xcf` (GIMP layered texture) | 1 | 16.8 MB |
| `.jpg` (preview captures) | 9 | 0.2 MB |
| `.obj` (map-kit pieces) | 15 | 0.1 MB |
| `.db` (`Thumbs.db`, a Windows thumbnail cache) | 1 | 0.0 MB |

**Largest files:**

| Size | File |
|---|---|
| 43.4 MB | `opengameart/mansedj/Mansc_Release.blend` |
| 22.0 MB | `opengameart/hex-paver-b/hex_paver_b.zip` |
| 16.8 MB | `opengameart/fox/fox_textures.xcf` |
| 11.6 MB | `blender/cube-diorama/cube-diorama-v1.0.0.zip` |
| 10.3 MB | `opengameart/hex-paver-b/Hex_paver_b_diffuse.png` |
| 8.6 MB | `opengameart/hex-paver-b/Hex_paver_b_diffuseOriginal.png` |

**Duplicates:**
- none inside `asset-sources/`;
- none byte-identical under `assets/` or `fixtures/`. The runtime uses converted files (`assets/models/thirdparty/opengameart/`, `assets/textures/thirdparty/opengameart/`), not these.

## 2. Classification

| Folder | What | Runtime-required? | Licence record | Reproducible? |
|---|---|---|---|---|
| `blender/cube-diorama` | Blender demo archive (M7a.6 fixture source) | No: the derived mesh is in `fixtures/mesh-sources/` | `fixtures/mesh-sources/licenses/manifest.json` | Yes, by URL and hash in that manifest |
| `opengameart/boulder`, `detective-bunny`, `fox`, `rabbit`, `hex-paver-b` | Downloaded sources (`.blend`, `.xcf`, `.zip`, `.png`) | No: converted copies under `assets/` | `docs/assets/asset-license-manifest.json`: **CC0-1.0** | Yes, by their OpenGameArt pages (recorded `source`) |
| `opengameart/warrior-low-poly`, `thin-zombie`, `horror-sci-fi-map-kit` | Downloaded sources | No: converted copies under `assets/` | Same manifest: **CC-BY-3.0**, attribution required (`docs/reports/cc-by-sample-art.md`) | Yes, by their pages |
| `opengameart/mansedj` | `Mansc_Release.blend` (43.4 MB) | Not referenced by any scene or converter output found | **No record found** in either licence manifest | Unknown |

**What references `asset-sources/`:**
- code: only `tools/convert_attributed_opengameart.py`, as its source folder;
- docs: `docs/assets/*.md`, `docs/reports/cc-by-sample-art.md` and the A01 and A00 reports.
- No scene, test or runtime code loads from it.

## 3. Recommendation (proposal; owner decision)

1. **Do not commit `asset-sources/` to normal Git.**
   - It holds 148 MB of binary sources that the game and editor do not need at run time.
   - Committing them would make every clone carry them forever.
2. **Primary: reproducible downloads.**
   - Record each source file's URL, SHA-256 and licence in the existing licence manifests. Most already are; add the missing hashes.
   - Add a fetch script that downloads into `asset-sources/` and verifies hashes. Writing it needs a separate approval; nothing was downloaded here.
   - The converters (`tools/convert_*opengameart.py`) then rebuild the runtime copies deterministically.
3. **For files that cannot be re-downloaded reliably** (a page that disappears or needs a login), use Git LFS, if the owner wants them versioned. Otherwise use external storage (UrConnex asset storage, after A16) with the hash in the manifest.
4. **Keep the runtime copies under `assets/` in normal Git** with their licence records, as today.
5. **`opengameart/mansedj`:** find its licence and source before any use. Until then, keep it local only and exclude it from any commit.
6. **`Thumbs.db`:** exclude from any commit. Deleting the file needs the owner's go-ahead.

**Needs the owner:** choose 2 (recommended), 3, or keeping the folder local only, and authorize the fetch script.
