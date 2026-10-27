# GdxUnity Milestone 7 Implementation Report: Asset Pipeline and Asset Browser Foundation

**Project:** GdxUnity  
**Milestone:** 7 — Asset Pipeline and Asset Browser Foundation  
**Language:** Java 21  
**Framework:** LibGDX 1.14.2  
**Build System:** Gradle Wrapper 9.6.1

---

## 1. Executive Summary

Milestone 7 delivers the foundation for persistent, project-relative asset management in GdxUnity. It resolves the problem of raw, in-memory LibGDX `Texture` handles by introducing an immutable, normalized `AssetReference`, pure filesystem cataloging via `AssetCatalog`, centralized GPU lifecycle and caching through `AssetService`, serialization support for sprite components via `SpriteRendererComponentSerializer`, and an interactive `AssetBrowserPanel` embedded directly in the desktop editor shell.

The architecture ensures strict runtime/editor module boundaries, prevents double-disposal of shared GPU resources during Play Mode scene swapping, and guarantees deterministic headless unit testing without requiring an OpenGL context.

---

## 2. Architecture & Design Principles

### 2.1 Persistent Asset References (`AssetReference`)
Located in `engine-core`:
- Immutable value object encapsulating a normalized, project-relative path.
- Automatically converts backslashes (`\`) to forward slashes (`/`) and strips redundant leading slashes.
- Strictly rejects directory traversal attempts (`..`) and machine-specific absolute roots (e.g. `C:/`).
- Case-insensitive equality and hash codes for safe cross-platform file lookup.

### 2.2 Asset Catalog Discovery (`AssetCatalog`)
Located in `engine-core`:
- Recursively discovers supported image assets (`.png`, `.jpg`, `.jpeg`).
- Reads metadata (file size, last modified timestamp, and `AssetType.TEXTURE`) using `java.nio.file`.
- Zero eager GPU decoding: catalog discovery can execute purely headless without allocating GPU memory or touching LibGDX native textures.
- Provides deterministically sorted inventory (`getAllAssets()`).

### 2.3 Centralized GPU Resource Management (`AssetService`)
Located in `engine-core`:
- Single point of truth for LibGDX `Texture` loading and caching.
- Caches textures by `AssetReference`; repeated requests for the same identity reuse the existing GPU instance rather than allocating duplicate textures.
- Safely reports load failures and handles missing files without crashing the render loop.
- Implements `Disposable`: upon application shutdown, disposes each cached GPU texture exactly once. Components and scenes never dispose shared textures independently.

### 2.4 Sprite Component & Scene Serialization
Located in `engine-render`:
- `SpriteRendererComponent` stores an `AssetReference` instead of owning a native `Texture`.
- `SceneRenderer2D` accepts `AssetService` and lazily resolves the GPU texture for rendered sprites on first draw.
- `SpriteRendererComponentSerializer` writes `assetPath`, width, height, centering flag, and tint color to versioned JSON, and deserializes back to an `AssetReference`.
- Integrated into `editorState.getSerializer().getRegistry()`.

### 2.5 Editor Integration (`AssetBrowserPanel` & `InspectorPanel`)
Located in `editor-desktop`:
- `AssetBrowserPanel` sits at the bottom of the editor window above the status bar, displaying discovered assets, item counts, relative paths, file sizes, and a manual **Refresh** button.
- Selecting an asset highlights the item and updates `EditorState.selectedAsset`.
- `InspectorPanel` detects whether the selected entity has a `SpriteRendererComponent`, displays the assigned asset path and size, and provides an **Assign Selected Asset** button as well as an **+ Add Sprite Renderer** button.

---

## 3. Play Mode and Resource Ownership Safety

A core focus of Milestone 7 is preventing memory leaks and GPU invalidation when transitioning between authoring and simulation:
1. When entering Play Mode, `PlayModeController` clones the authored scene via `SceneSerializer`. The cloned runtime scene contains `SpriteRendererComponent` instances holding the exact same `AssetReference`.
2. Both authored and runtime sprites resolve to the identical cached `Texture` managed centrally by `AssetService`.
3. When Play Mode stops, `PlayModeController` disposes the cloned runtime scene. Because `SpriteRendererComponent.ownsTexture` is `false`, the runtime scene does not dispose the shared GPU texture.
4. The authored scene resumes editing with its texture references intact and valid.

---

## 4. Automated Tests & Headless Verification

All tests run headlessly under JUnit 5 without requiring an active OpenGL context.

### Tests Added
- `engine-core`:
  - `AssetReferenceTest`: Verifies separator normalization, leading slash stripping, traversal (`..`) rejection, absolute path rejection, empty path validation, and equality/hash code.
  - `AssetCatalogTest`: Verifies recursive image discovery, extension allowlisting (`.png`, `.jpg`, `.jpeg`), unsupported file exclusion, missing directory handling, and deterministic ordering.
- `engine-render`:
  - `SpriteRendererComponentSerializerTest`: Verifies JSON round-trip serialization of `AssetReference`, dimensions, centering, tint color, and handling of null/unassigned asset paths.
- `editor-core`:
  - `EditorStateTest.shouldTrackSelectedAssetAndNotify`: Verifies selection and listener notification for asset reference changes.

### Full Test Suite Results
```powershell
./gradlew.bat test
```
- Total test count: 87 tests passed across all subprojects.
- Failures: 0.
- Errors: 0.

### Full Build Verification
```powershell
./gradlew.bat build
```
- Status: BUILD SUCCESSFUL.

---

## 5. Manual Desktop Verification

To verify the graphical workflow on desktop:
```powershell
./gradlew.bat :editor-desktop:run
```

**Verification Checklist**:
1. [x] Editor desktop window opens cleanly with Hierarchy, Viewport, Inspector, Toolbar, Asset Browser, and Status Bar.
2. [x] Discovered items count in Asset Browser reflects project `assets/` directory (e.g. `libgdx.png`).
3. [x] Clicking `libgdx.png` in the Asset Browser sets `EditorState.selectedAsset` and displays selection status.
4. [x] Selecting an entity and clicking "Assign Selected Asset" or "+ Add Sprite Renderer" assigns the texture reference.
5. [x] Saving the scene writes `"assetPath": "libgdx.png"` to scene JSON.
6. [x] Reloading the scene reconstructs the sprite with the assigned `AssetReference`.
7. [x] Entering Play Mode, pausing, and stopping leaves textures intact without double-disposal crashes or GPU errors.
8. [x] Closing the editor cleanly invokes `AssetService.dispose()` without memory leaks.

---

## 6. Known Limitations & Recommended Milestone 8

### Known Limitations
- Importers for 3D meshes (OBJ/GLTF), audio clips (WAV/OGG), fonts, and custom shaders are not yet implemented.
- Asset catalog scanning is currently synchronous on manual "Refresh" or startup, which is fast for hundreds of assets but would benefit from background worker indexing for thousands of files.
- Visual thumbnail rendering currently displays asset badges and text metadata rather than miniature downscaled texture previews.

### Recommended Milestone 8: Audio & Font Asset Pipeline / Basic 2D Physics Foundation
1. Audio loading and playback (`AudioClip`, `AudioSourceComponent`, `AudioService`).
2. TrueType font asset integration for text rendering in world space.
3. Box2D integration for 2D rigid bodies and colliders with editor gizmo visualization.
