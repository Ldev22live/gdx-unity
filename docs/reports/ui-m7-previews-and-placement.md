# UI M7: Previews, Downloads and Scene Placement

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `0469ceb` (nothing committed)

Milestone **M7** of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs", under TD-094 and TD-095.

**Status: Implemented and Integrated.**
- **Previews:** the Asset Library shows the chosen asset's preview and keeps it on disk for offline use.
- **Place in world** stands a model that is in the project on the ground at the centre of the view, in one undo step. That covers a model just downloaded or downloaded earlier.
- **Apply to selection** puts a material that is in the project on the selected shape.
- **Done-when met:** placed on terrain and kept through save and reopen, in tests, and seen in the editor.
- **Manually Not Verified:** no person has clicked these buttons.

## 0. A correction to M5

The M5 report said downloaded glTF models "come in small" because Poly Haven works in metres, and left that to M7. **That was wrong.** A placed glTF always got the metres-to-units scale: `ModelComponent.DEFAULT_IMPORT_SCALE` is 100, the same as the shipped Poly Haven models use.

The M5 report, the feature matrix and test plan case H3 are corrected. M7's real placement work was standing the model on the ground.

## 1. Acceptance criteria

| # | Requirement (brief) | Result | Evidence |
|---|---|---|---|
| 1 | Thumbnails | **Met** in the details | **When it is fetched:** when an asset is chosen, off the render thread. It is kept in `<settings>/cache/asset-library/<provider>/thumbs/` and shown offline from there. **Format:** only PNG or JPEG of at most 2 MB is accepted; anything else says "No preview: ...". **Poly Haven:** its API points at WebP images, which the editor cannot decode, and its CDN sent WebP even for a `.png` address. ForgeRoom therefore asks with `format=png`, checked live. **ambientCG:** the 256-pixel PNG its API lists. Tests: `ThumbnailCacheTest` (4). Screenshots show the crate's and Ground 111's previews. |
| 2 | High-definition choices | **Met** (M5, M6) | Each resolution with its size, 2K first, a confirmation above 500 MB and a refusal above 4 GB. That was built in M5 and M6 and is unchanged. |
| 3 | Downloads into the project | **Met** (M5, M6) | `thirdparty/<provider>/<id>/`, recorded in the manifest. |
| 4 | Placing on terrain | **Met** | `LevelSession.placeModelOnGround`. **Where:** the model stands on the terrain under the view centre and follows a moved ground. **How:** a Model component at the metres-to-units scale, selected, in one undo step. **When it can't stand on the ground:** with no ground, or a point beyond its edge, it goes at the view centre and the status bar says so; it is never floated silently. **Seen in the editor:** Wooden Crate 02 placed on the valley of `terrain-3d.json` at height 73.4, "Placed wooden_crate_02_2k on the ground": [place-on-ground.png](ui-m7/place-on-ground.png). |
| 5 | Imported materials usable | **Met** | Apply to selection uses `MaterialAssignment`, the same path as dropping a material on Details, so it is one undo step and a non-shape is refused with the reason. Seen: Ground 111 applied to the showcase's Rock Block: [apply-material.png](ui-m7/apply-material.png). |
| 6 | Survive save and reopen | **Met** in tests | The placed model's path, position and import scale come back through the scene serializer (`shouldSurviveSaveAndReopen`). The material slot round-trip was already covered by M2.2's tests. **Not done in the running editor:** File > Save and reopen. |
| 7 | Reachable | **Met** | "In the project: Place in world" or "Apply to selection" sits on the same row as Download, beside the preview, so it stays in view in the short drawer (moved there after the first screenshot showed it cut off). It is offered for an asset downloaded in an earlier session too, read from the manifest. |

**Mutation check** (restored afterwards, with `cmp` identical):

| Change | What failed |
|---|---|
| The ground's height ignored | 3 of 5 `DownloadedModelPlacementTest` tests |

## 2. What was built

**`editor-core` `asset/library`:**
- `ThumbnailCache` (new);
- PNG thumbnail addresses: `PolyHavenProvider.THUMBNAIL` and `AmbientCgProvider.THUMBNAIL`;
- `RemoteAssetInstaller.Result.mainModel()`, and `materialName` made public.

**`editor-terrain`:** `LevelSession.placeModelOnGround`.

**`editor-desktop`:**
- `ProviderLibraryPanel`:
  - the preview, as a texture the panel owns and disposes;
  - the `Placement` interface, with "Place in world" and "Apply to selection";
  - the actions beside the preview.
- `EditorApplication`:
  - the placement: the view centre (`spawnPosition`) through `levelSession`, and the selection through `MaterialAssignment`;
  - a thumbnail cache for each provider tab.
- **Testing aid:** a final `+` on `GDXUNITY_EDITOR_LIBRARY_SEARCH` presses Place in world or Apply to selection once the asset is in the project.

**Choices made without asking:**
- **The preview is shown in the details only.** Fetching up to 300 previews for every search would mean hundreds of requests to the providers.
- **Placement goes at the view centre.** The button is outside the view. Dragging the `.gltf` from Assets > Project still places it under the mouse.
- **No agent tool.** As in M5: going online is a person's action, and placing a project file was already open to agents through the existing entity tools.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*.asset.library.*"` | 0 | 38 tests (6 pack import, 9 Poly Haven, 14 installer, 5 ambientCG, 4 thumbnails) |
| `./gradlew :editor-terrain:test --tests "*DownloadedModelPlacementTest"` | 0 | 5 tests |
| The mutation check | 1 | As listed; restored |
| `APPDATA=<settings> GDXUNITY_EDITOR_LIBRARY_SEARCH="wooden crate 02+" GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--project <M5 copy> --scene samples/terrain-3d.json --smoke-test=900"` | 0 | The preview was fetched and cached. Place in world put the crate on the valley at (320, 73.4, 0), selected, with Model scale 100. 0 errors |
| `APPDATA=<settings> GDXUNITY_EDITOR_SELECT="Rock Block" GDXUNITY_EDITOR_LIBRARY_SEARCH="ambientcg:ground 111+" ... --scene samples/polyhaven-showcase-3d.json --smoke-test=900` | 0 | Ground 111's preview showed; "Apply Material Ground111.material to Rock Block". 0 errors |

### 3a. Per-module test counts

Counts are from the XML reports, after `cleanTest`, with each module run on its own.

| Module | Exit | Before (after M6) | After |
|---|---|---|---|
| `editor-core` | 0 | 586 (1 skipped) | **590** (1 skipped, the same POSIX-only test) |
| `editor-terrain` | 0 | 43 | **48** |
| `editor-desktop` | 0 | 187 | **187** |

The package was rebuilt at the end of M8, which followed directly.

## 4. Known limitations and next steps

- **No thumbnails in the result list,** only for the chosen asset.
- **Place in world uses the view centre,** not the mouse.
- **Save and reopen in the running editor was not done;** it is covered by a headless test and is case J4 of tonight's plan.
- **Next: M8,** the brief's regression and performance checks and documentation, end to end.

## 5. Diff review and Git

**New:**
- `ThumbnailCache`, `ThumbnailCacheTest`, `DownloadedModelPlacementTest`;
- this report and `docs/reports/ui-m7/`.

**Changed:**
- code: `PolyHavenProvider`, `AmbientCgProvider`, `RemoteAssetInstaller`, `LevelSession`, `ProviderLibraryPanel`, `EditorApplication`;
- docs:
  - the M5 report (the correction), `AGENTS.md`, `INDEX.md`, `FEATURE_TEST_MATRIX.md`;
  - both milestone charts;
  - the test plan (section J, H3 corrected).

The shipped `assets/` was not touched.

**Git:** no commit, push or other Git operation was performed.
