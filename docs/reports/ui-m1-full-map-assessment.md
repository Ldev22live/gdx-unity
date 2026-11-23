# UI M1: Repository and UI Assessment for the Full-Map Editor and Asset Library

**Date:** 2026-11-19. **Branch:** `phase-12-3d-foundation`. **HEAD:** `0469ceb` (nothing committed).

This is milestone **M1** of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs" (M1 to M8). It records what exists today, what each requirement maps to, what is missing, and the plan and decisions for M2 onward. **No code changed in M1.**

## 1. Two things the brief assumes that are not in the repository

| Brief says | What I found |
|---|---|
| "using the supplied design reference" | **No reference image was supplied with the request,** and none is in the repository. The only editor screenshot, `docs/screen.png`, shows the old "GdxUnity Editor" (four flat shapes, text toolbars), not a design. **The layout below follows the brief's text.** A reference image would let M3's drawer layout match it |
| "replace the embedded Age of Empires screenshot"; "No Age of Empires UI or screenshots remain" | **There is none in the editor.** A search of the source, documents and images (outside build output and `node_modules`) finds "Age of Empires" only in planning text: the roadmap's workflow model for the level editor, TD-083's quotation of the request, and `docs/swgb.md` (research). No image, asset or UI string copies it. The viewport already draws ForgeRoom's own terrain (`.terrain`, TD-082), as the screenshot below shows. So this criterion already holds, and I will keep it holding |

## 2. The editor today

[ui-m1/editor-today-terrain-3d.png](ui-m1/editor-today-terrain-3d.png): `action-rpg-valley-3d.json` at 1920 by 991, editor smoke test, 180 frames, 0 errors.

- **The viewport already dominates.** Since the World-first work (M1b.2 to M1b.7, TD-084 and TD-085), the World layout starts without permanent side panels. Outliner, Details (shown with a selection), the bottom panel (Content Browser, Output Log, Profiler) and AI Assistant are opened from buttons at the top right. The choice is saved in the workspace layout (`PanelLayout`, `WorkspaceLayoutConfig`).
- **Top bar (two rows):**
  - **Row 1:** project name, workspace and scene, then Save, Undo, Redo, Project, Settings and Create.
  - **Row 2:** view toggles, fly speed, the transform tools, Sculpt, Level and Compile Scripts, then the panel buttons.
- **World strip** (`WorldStripBar`, `WorldStrip`): Terrain, Objects, Buildings and Characters in 2.5D and 3D; Tiles, Sprites, Objects and Characters in 2D. Buildings is marked planned (R7a).
- **Footer:** Game Genre, Game Mode (2D, 2.5D, 3D), Customize, Play, Pause, Stop and the status line.

**What the screenshot shows wrong for this brief:**
- **No overview on load:** the scene opens from its saved editor view, near the ground on one hill. Nothing fits the world to the view.
- **No isometric view:** the editor's views are perspective plus three axis-aligned orthographic views (`ViewKind`: Perspective, X, Y and Z axis).
- **Grid over the sky (corrected in M2):** this is not a fault. The view is the X-axis side view: the owner's saved layout is a single X-axis pane, and that view's grid stands upright.

## 3. Requirement by requirement

| # | Requirement | Exists today (verified in source) | Gap | Milestone |
|---|---|---|---|---|
| 1a | ForgeRoom's actual terrain in the viewport | `TerrainComponent` and `.terrain` drawn in the editor and the game (TD-082), with LOD (M3.8) and water, sky and fog (M3.9) | none | already met |
| 1b | Isometric camera preset | Runtime only: `ProfileCameraRig` `ISOMETRIC` (M3c.3) for Play. Editor: `ViewportCameraController3D` (orbit, pan, fly, zoom) and `OrthographicViewCamera` for axis views only | **An editor isometric view** | M2 |
| 1c | Fit the world on first load | `ViewportFocus` (bounding radius, perspective distance, orthographic zoom) serves Frame Selection | **Frame the whole world, terrain bounds included,** when a scene opens | M2 |
| 1d | Pan, zoom, rotate, select | All in the perspective view; pan and zoom in axis views; picking and gizmos in both | Rotation in the isometric view (yaw steps of 90 degrees, as isometric editors do) | M2 |
| 1e | Paint terrain and place objects on the map | Terrain brushes (sculpt, paint layers, holes, erosion), the level tools, place and erase (TD-083), all on the perspective view | **They must also work in the isometric view:** the ray from an orthographic camera differs | M2 |
| 1f | Keep 2D, 2.5D and 3D | `GameModeProfile`, the footer's mode switch, 2D's orthographic view | Isometric for 2.5D and 3D only; 2D keeps its own view | M2 |
| 2 | Collapsible tool drawers (9) | See section 4: 7 of 9 exist as panels or strip sections, but as docks in `SplitPane`s or strip pop-ups, not as one drawer system | **One drawer model:** open or closed, resizable, remembered | M3 |
| 2b | Minimal top toolbar | Two rows today | Merge into one row; the panel buttons become drawer toggles | M3 |
| 3 | Provider-based asset discovery | `AssetCatalog` and `AssetBrowserPanel` (project files, thumbnails), `AssetPlacement`, `ThirdPartyManifest` (TD-094, proposed), `tools/thirdparty_assets.py` (Python: Poly Haven fetch, checks) | **No provider interface, no networking in the editor, no download or import UI** | M4 to M7 |
| 4 | Search, preview, licence, format, size, download, import, place | Placing project files works (drag and drop, Objects palette) | All of the online steps | M4 to M7 |
| 5 | Async networking, caches, reuse | `OllamaModelProvider` is the only HTTP client (Java's own, no library). Editor work uses `Gdx.app.postRunnable` and executors in 29 places | A metadata and thumbnail cache; a download worker | M4, M5 |
| 8 | Save and reload | `SceneSerializer`, fixed text layout (M9.3), round-trip tests | Imported assets in a saved scene | M7 and M8 tests |

## 4. The nine drawers, mapped to what exists

| Drawer | Today | Where |
|---|---|---|
| Terrain editing | World strip > Terrain (brushes, ground, holes, water) | `WorldStripBar`, `LevelPalette`, `editor-terrain` |
| Object placement | World strip > Objects (five primitive prefabs; place and erase) | `LevelPalette`, M2b.3 |
| Buildings | World strip > Buildings: marked planned (needs R7a) | `WorldStrip.Section.BUILDINGS` |
| Characters | World strip > Characters (Player prefab, Spawn Point) | M3c.2 |
| Asset Library | Content Browser (project files only) | `AssetBrowserPanel` |
| Scene hierarchy and layers | Outliner (no layers concept yet) | `HierarchyPanel` |
| Properties | Details | `InspectorPanel` |
| Console and scripts | Output Log in the bottom tabs; Compile Scripts in the toolbar | `OutputLogPanel` |
| AI Assistant | Assistant panel (Ollama or mock provider) | `AssistantPanel` |

**Layout code to change in M3:**
- `EditorApplication` (2,389 lines) builds the docks from nested scene2d `SplitPane`s (left, right, centre and right, main).
- `PanelLayout` keeps which are open.

M3 would replace the nesting with a small drawer model in `editor-core` (testable headless: open, closed, width, remembered) and one drawer widget in `editor-desktop`. Each drawer reuses its panel as it is, so no panel is rewritten. **Risk:** TD-055's minimise behaviour and the saved layout must keep working; existing layout tests cover them.

## 5. Asset providers: what was verified (2026-11-19)

| Provider | Licence | Automated access | API facts | Fit |
|---|---|---|---|---|
| **Poly Haven** | CC0 (https://polyhaven.com/license) | API terms: "free to access and use by anyone ... including commercial use"; a User-Agent naming the software is required; a "Powered by Poly Haven" credit only when showing live API content. Site scraping is forbidden; use the API | `GET /assets?type=&categories=`, `/info/<id>`, `/files/<id>`; per-file **URL, size and MD5**; dependency files listed for glTF | Models (glTF), textures (JPG and PNG). HDRIs need a format ForgeRoom cannot read (HDR, EXR) |
| **ambientCG** | CC0 1.0 (https://docs.ambientcg.com/license: "You can include the raw files in your project, for example a video game") | `robots.txt` allows all but `/hx/`. The API pages state no rate limit or User-Agent rule. The site warns it is run by one person and the API may not suit enterprise use | `GET https://ambientcg.com/api/v3/assets` with `q`, `type`, `sort`, `id`, `limit` (1 to 500), `offset`, and `include` (`title`, `tags`, `downloads`, `thumbnails`, `dimensions`, among others). **Downloads are zips**, each with `attributes` (e.g. `1K-JPG`), `url` and `size`, but **no hash**. `category` is not documented. Checked live: 2,938 assets | Materials (a zip of PBR maps), 1K JPG at about 10 MB. Its 3D models, HDRIs and Substance files are mostly formats ForgeRoom cannot read; filter by type and format |
| **Kenney** | CC0 (MC.1 and the asset integration report) | **`robots.txt` disallows AI agents by name, and pages carry `noai`.** No documented API; downloads run through a page script. The brief's "do not assume an undocumented public search API" matches | none | **Curated list plus local importer:** a person downloads a pack in a browser; the importer checks the zip (`check-archive` logic), keeps `License.txt`, and imports the supported files |

**Validation each provider allows:**
- **Poly Haven:** MD5 per file, sizes and dependencies.
- **ambientCG:** size only, plus the zip's own entry checks (paths, links, executables) and supported formats. ambientCG gives no hash, so the importer records the SHA-256 of what it received, and the manifest says that no source hash existed.

## 6. Proposed architecture (to confirm before M4)

- **`editor-core` `assets.online`** (headless, tested with recorded API responses, never the live API in unit tests):
  - `AssetProvider`: search, details, file choices and credit text;
  - `PolyHavenProvider`, `AmbientCgProvider`, `KenneyLocalPacks`;
  - one `AssetDownloader`: size limit, hash when given, archive checks, supported formats;
  - one `AssetImporter`: writes files under the `thirdparty` folders and the entry in `licenses/manifest.json` through `ThirdPartyManifest`, a writer added beside its reader.
  - **One interface** because three providers share search, licence, files and import. Kenney is a local source behind the same interface, with no network.
- **Networking:** Java's `java.net.http.HttpClient` (no new dependency), on a worker executor; results return to the render thread with `Gdx.app.postRunnable`. Responses and thumbnails are cached under the user's cache folder, not in the project.
- **Editor:**
  - the Asset Library drawer has tabs Project, Poly Haven, ambientCG and Kenney (local), with search, a type filter and a details card (licence, format, size, credit);
  - Download and Import buttons;
  - placement reuses `AssetPlacement` and the place tool's commands, so it is one undo step.

## 7. Decisions needed from the owner

1. **The editor going online.** Today it contacts only a local Ollama, and only when asked. Searching Poly Haven and ambientCG sends the user's search words and IP address to those services. I propose:
   - an explicit setting, **off by default**, with a one-line notice: what is sent, and to whom;
   - no account or personal data sent;
   - a User-Agent naming ForgeRoom and its version only.

   This needs a TD, since TD-094 only covers files after download.
2. **TD-094** (the manifest; proposed): approve it as the importer's record.
3. **Where imports go:**
   - proposed: the open project's `thirdparty` folders, as TD-094 has it;
   - a size limit per download: 50 MB proposed;
   - resolution: 1K by default.
4. **The design reference image,** if there is one.

## 8. Plan for M2 (next, after this assessment)

**Full-map viewport and isometric camera.** No engine rewrite; editor-only.

1. **`ViewKind.ISOMETRIC`:** orthographic, yaw 45 degrees, pitch 35.264 degrees (true isometric). It joins the view menu and pane headers.
   - `OrthographicViewCamera` accepts it, with a yaw that Q and E (or buttons) turn in 90-degree steps.
   - Picking, the gizmos, terrain brushes and the place tool use the camera's own ray, so they work on it. A test asserts that a ray through the screen centre hits the focus point.
2. **`WorldBounds`** (headless): the bounds of the terrain, models and shapes. On opening a scene in 2.5D or 3D, the isometric view frames them (`ViewportFocus.orthographicZoom`), so the whole map is visible. Tested on the valley and an empty scene.
3. **Default view:** 2.5D and 3D scenes open in the isometric full-map view unless the layout saved another. 2D is unchanged.
4. **Tests:**
   - the camera's projection: corners on screen, and isometric angles;
   - framing;
   - a brush dab and a placement through the isometric view, each one undo step.

   **Screenshots:** before and after, on the valley and the landscape samples.

## 9. M1 record

| Check | Result |
|---|---|
| Repository and UI assessed | This report; source files named in each row |
| Screenshot of the current editor | [ui-m1/editor-today-terrain-3d.png](ui-m1/editor-today-terrain-3d.png). Command: `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_ASSISTANT=1 ./gradlew :editor-desktop:run --args="--scene samples/action-rpg-valley-3d.json --smoke-test"`, exit 0, 180 frames, 0 errors |
| Provider APIs verified | Poly Haven ToS and `llms.txt`; ambientCG licence, API v3 docs and one live response (`limit=1`, with `include=downloads,thumbnails`); Kenney's `robots.txt` |
| Code changed | none; no tests were needed or run for M1 |
| Not verified | No reference design was available to compare. (The grid across the sky was explained in M2: it is the X-axis side view) |

**Git:** no commit, push or other Git operation was performed.

**Housekeeping checked:** `node scripts/sync-samples.mjs` copies every asset, the downloaded Poly Haven files included, into `engine-babylon/public/`. That folder is ignored by Git (`git check-ignore`), so the copies cannot be committed by accident.
