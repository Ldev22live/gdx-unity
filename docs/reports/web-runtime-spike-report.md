# Web Runtime Feasibility Spike (TD-036, proposed)

Date: 2026-11-02 · Platform: Windows 11, JDK 21, Chrome (headless) · HEAD before and after: `1dc0a11` (no commits)

## Goal

The user asked for a web version of GdxUnity that can be deployed to a VPS, and chose the scope "web game player":
a browser build of the game runtime (TD-035). The editor stays desktop-only (TD-036, *Context*).

## Acceptance criteria

| Criterion | Result | Evidence |
|---|---|---|
| Engine code compiles to JavaScript with a web backend | **Met** | `:engine-web:gdx_teavm_web_js_build` and `..._js_release_build` exit 0 |
| Box2D available in the browser | **Met** | `gdx-box2d-gwt` 1.14.2 replaces JNI Box2D; the 2D samples step physics without errors |
| A 2D sample scene plays in a browser | **Met (render only)** | Headless Chrome, dev and release builds: `platformer-2d` and `breakout-2d` draw shapes and HUD text, and the console shows no errors (screenshots checked) |
| A 3D sample scene plays in a browser | **Met (render only)** after the fix in *Update* | Before: `hopper-3d`, `collector-3d` threw `Frame buffer couldn't be constructed: incomplete attachment` in `IBLBuilder.buildRadianceMap`. After: `hopper-3d`, `collector-3d`, `puzzle-3d` render with lights and shadows, and the console shows no errors (screenshots checked) |
| Scripts, graphs, Animators, glTF models, sprites on the web | **Not met** | Not wired: they depend on `java.io.File`/`URLClassLoader` in `GameSession`, `GameScripts`, `AssetCatalog`, `ModelAssets` |
| Input in the browser | **Manually Not Verified (Headless Environment)** | Nothing was pressed; no script would react yet anyway |
| Deployable to a VPS | **Partly** | Static release output (2.5 MB, `app.js` 860 KB) and `engine-web/deploy/Dockerfile` + `nginx.conf`. **The Docker image was not built** because Docker is not installed on this machine |
| No regressions | **Met** | All module tests pass (below) |

## Findings

1. **TeaVM handles this codebase.** Records, pattern matching, the Kotlin stdlib and gdx-gltf all compiled. The
   one missing class the build reported was `java.util.concurrent.ConcurrentLinkedQueue` in `PhysicsSystem2D`.
   That queue is only used on the stepping thread, so it is now an `ArrayDeque`.
2. **gdx-teavm 1.6.2 matches libGDX 1.14.2** (the backend POM depends on `gdx:1.14.2`). Its Box2D extension is
   stale (`gdx-box2d-teavm` 1.0.0-b6, July 2023), so the module uses libGDX's pure-Java `gdx-box2d-gwt`.
3. **Sky-light bug (in uncommitted Phase 13-B code, not changed here).** `BakedSkyLightMaps` passes
   `RenderQuality3D.getRadianceMapSize()` (64/128/256, documented as a face size) to gdx-gltf's
   `IBLBuilder.buildRadianceMap(int mipMapLevels)`. That method computes `size = 1 << (mipMapLevels - level - 1)`,
   which for 64/128/256 is `1 << 31` (Java masks the shift), a negative size. Suggested fix: pass
   `log2(size) + 1` mip levels (64 → 7). *Unverified hypothesis:* the desktop runtime does not hit this because
   `BakedSkyLightMaps` skips the radiance map without GL30 or `EXT_shader_texture_lod`. The web build uses WebGL2.
4. gdx-teavm's loading screen throws `Cannot read properties of null (reading '$scrollFocus')` if the tab closes
   before the loading screen draws, because `WebPreloadApplicationListener.dispose()` runs with a null stage. This
   is an upstream issue and harmless to the game.
5. gdx-teavm lists asset files when the build is configured, so a Gradle task cannot stage a filtered copy. The
   build therefore copies `assets/` as is, including the gitignored `assets/build` on developer machines.

## Commands

| Command | Exit |
|---|---|
| `./gradlew :engine-web:gdx_teavm_web_js_build` (first run) | 1: `ConcurrentLinkedQueue was not found` |
| same, after the `PhysicsSystem2D` change | 0 |
| `./gradlew :engine-web:test :engine-web:gdx_teavm_web_js_release_build` | 0 |
| `./gradlew test --continue` | 0 |
| Headless Chrome via DevTools protocol (scratch script, not in the repo), 15–20 s per scene | 2D: page logs `Playing samples/...`, no errors; 3D: fatal error above |

## Test counts (`./gradlew test --continue`, after)

editor-animation 13, editor-core 288, editor-desktop 40, editor-visualscript 4, engine-animation 32,
engine-core 27, engine-desktop 19, engine-render 72, engine-scene 63, engine-visualscript 9, **engine-web 4 (new)**.
0 failures, 0 skipped. Counts for the existing modules equal the baseline in `FEATURE_TEST_MATRIX.md`, so the
`PhysicsSystem2D` change caused no regressions.

## Files

New: `engine-web/` (`build.gradle`, `README.md`, `WebLauncher`, `WebScenePlayer`, `WebLauncherTest`,
`deploy/Dockerfile`, `deploy/Dockerfile.dockerignore`, `deploy/nginx.conf`), this report.

Changed:
- `engine-scene/.../physics/PhysicsSystem2D.java` (queue type).
- Additions to files that already had your uncommitted changes, which are untouched:
  - `settings.gradle`: `pluginManagement` repositories and the `engine-web` include.
  - `gradle.properties`: `gdxTeavmVersion`.
  - `docs/TECHNICAL_DECISIONS.md`: TD-036.
  - `docs/FEATURE_TEST_MATRIX.md` and `docs/INDEX.md`: one row each.

## Update 2026-11-02: radiance mip-level fix (user-approved)

- `RenderQuality3D.getRadianceMipLevels()` (new) returns `log2(getRadianceMapSize()) + 1`: 7, 8 and 9 for LOW,
  MEDIUM and HIGH. `mipLevelsForFaceSize` rejects sizes that are not positive powers of two.
- `BakedSkyLightMaps.bake` passes the mip levels to `buildRadianceMap` instead of the face size.
- `RenderQuality3DTest` +3 (engine-render 72 → 75). The call site needs a GL context, so the 3D browser check
  above covers it.
- Desktop: the radiance map is only built when `supportsReflectionMap()` is true (GL 3 or
  `EXT_shader_texture_lod`). Desktop sky reflections were not looked at (**Manually Not Verified**).
- `./gradlew test --continue` exit 0. All modules have the same counts as before except engine-render, which has 75.

## Phase 2 (2026-11-02): shared runtime, scripts, assets ("continue with the next phase")

### What changed
- **`AssetSource`** (engine-core, new): resolves an `AssetReference` to a `FileHandle`. `directory(File)` is used
  on desktop and `internal()` on the web. Classes moved onto it (their `File` constructors remain and delegate):
  - `GameSession`: graphs and clips, plus a new `loadScene(AssetSource, String)`.
  - `GameRenderer`.
  - `ModelAssets`.
  - `AssetService`: new `AssetService(AssetSource)` and `hasCatalog()`.
  - `AssetCatalog` now implements `AssetSource`.
- **Scripts:** `ScriptFactory` with `GameScripts` (class loader, desktop) and `ScriptRegistry` (constructor
  references, no reflection), plus `ScriptBinder`. `:engine-web:generateScriptRegistry` compiles `assets/scripts`
  and generates `GeneratedScripts` (21 classes, covering every script the samples bind).
- **`WebScenePlayer`** now runs the shared `GameSession` and `GameRenderer`, so the web plays what the desktop plays.
- **`AssetReference`** checks for `..` segments by splitting on `/` instead of iterating a `java.nio.file.Path`.
  TeaVM's `Path` iterator returns null, which crashed every scene load. *Side effect:* on Windows, names containing
  `<>:"|?*` after the first two characters were rejected by `Paths.get` and are now accepted (the file is then
  simply not found).
- **`AssetService`** error text changed from "Asset file not found on disk: …" to "Asset file not found: …".
- **Web bundle:** `stageWebAssets` copies `assets/` without `assets/build` before TeaVM's
  `generateJavaScript`/`*_compile` tasks, which are the ones that copy assets. This also corrects finding 5 above.
  Release build: 3.0 MB, `app.js` 1.7 MB.

### Evidence
| Check | Result |
|---|---|
| `./gradlew test --continue` | exit 0. engine-core 27→32, engine-desktop 19→27, engine-render 75→76, engine-web 4→6; other modules unchanged (editor-desktop 40 includes `SampleGamesTest`, editor Play = runtime) |
| `:engine-web:test` after the final `build.gradle` change | exit 0, 6 tests |
| `:engine-desktop:run --smoke-test=240` breakout-2d, collector-3d | exit 0, 240/240 frames, 0 runtime errors |
| Temporary scene (hopper-3d plus glTF `BendingStrip`, Animator and graph; written by a throwaway test, then deleted with its assets) | Desktop: smoke test 180/180, 0 errors, graph printed. Web: graph printed "graph ran on web", model drawn, box rotated as the clip predicts (screenshot) |
| All 14 samples, release build, headless Chrome (Python `http.server`), 2 passes | 26 of 28 runs played. In 2 runs (runner-3d, ruins-runner-25d) the loading screen never finished because several asset requests were never answered by the server |
| Same against a minimal Node static server | 7 of 7 runs played with every request complete. **The run was stopped by Claude Code because the machine ran low on memory**, so the comparison is incomplete. The stalls look like the local Python server, but this is not proven |
| Keyboard input (DevTools key events) | breakout: Space launched the ball (Score 10, Lives 2) and Left moved the paddle; platformer: D moved the player and the camera followed (screenshots) |
| Mouse, touch, gamepad, audio, textured sprites in a browser | **Not checked** |
| Docker image | **Not built** (no Docker on this machine) |

## Phase 3 (2026-11-02): embedding at urconnex.com/games

- **Catalog:** `generateGameCatalog` writes `assets/games.json`: 14 games, each with id, name, kind, description,
  controls and scene. It is staged with the assets.
- **Own `index.html`** (`generateIndexHtml = false`, copied by `generateJavaScript` and the release compile task).
  The generated page overflowed by a few pixels.
- **`?scene=` is URL-decoded.** Before the fix, a host page using `encodeURIComponent` failed with
  `Scene not found: samples%2Fbreakout-2d.json`, which was observed. `WebLauncherTest` +2.
- **Caught browser keys** (`WebScenePlayer.CAUGHT_KEYS`, `WebScenePlayerTest` 1). Before the fix, Space/ArrowDown/
  PageDown in the focused iframe scrolled the host page by 30 px; after it, 0 px.
- **Deploy config:** `deploy/nginx.conf` sends `frame-ancestors 'self'` and does not cache `games.json`.
  `EMBEDDING.md` has the nginx and Next.js snippets for UrConnex. They are not applied or tested there.
- **Evidence:**
  - `:engine-web:test` exit 0, 9 tests.
  - Release build exit 0.
  - Local simulation (headless Chrome, Node static server, host page with an iframe at `/games/engine/`): Breakout
    responded to keys and the host did not scroll; Collector 3D moved and collected a gem (screenshots).

## Next steps

Play the release build in a real browser and check mouse and audio. Build the Docker image on the VPS and confirm
the asset-loading stall does not happen behind nginx. Then decide whether to support Kotlin scripts on the web.

No commit, push or destructive Git operation was performed.
