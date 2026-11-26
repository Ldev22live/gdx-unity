# Alpha A01: Screenshot-led desktop and web audit

**Date:** 2026-11-25. **Milestone:** A01 of [milestone-roadmap.md](../milestone-roadmap.md), with the A00 items it depends on ([alpha-a00-baseline.md](alpha-a00-baseline.md)). **Status:** audit only; no source code changed. **Done-when not met:** the owner must approve the target and the gap list (section 9). Proposals for A02, A11 and A13 are in [alpha-a02-a11-a13-proposals.md](alpha-a02-a11-a13-proposals.md).

**Honesty notes:**
- The screenshots below were captured by the applications themselves and looked at by the agent. They are **not** human validation.
- Viewport percentages are estimated from screenshot pixel coordinates (±3 points), not measured by code.

## 1. The owner's UI reference

| Candidate | Path | SHA-256 | Size | Git |
|---|---|---|---|---|
| **A (authoritative, confirmed by the owner 2026-11-25)** | `docs/Screenshot 2026-11-25 113907.png` | `adb72eaa61a5928d47299f8cf3b7ba572dd8b45ecff3856c2fa4fea9830d31b9` | 781 × 466 | untracked, added 2026-11-25 11:39 |
| B (earlier brief) | `docs/ForgeRoom Isometric World Editor.png` | `a3bb07ef05c8033066e75e0f23100f66128bd175698f116b3871b0836dbf5393` | 1691 × 930 | committed in `2391dad` (UI brief, 2026-11-19) |
| (not a reference) | `docs/screen.png` | `488a83c2…5baea` | 1916 × 986 | old editor capture, 2026-10-29 |

**Two references existed; the owner confirmed A on 2026-11-25** (recorded in [milestone-roadmap.md](../milestone-roadmap.md) §1). A was already the basis of the comparisons below because:
- it was added today with the directive;
- it matches the directive's category list exactly (Map, Terrain, Objects, Buildings, Characters, Players, Rules);
- it matches the adjacent Customize and Play.

B is what UI M1 to UI M8 implemented.

**Reference A, described:**
- A thin header: product name, project name, and New, Open, Save, Undo, Redo, Settings.
- A dominant isometric map, which is about 63% of the editor panel's area.
  - Overlays in its corners: "World / Main Map" (context) at top left, "Top / Iso / Perspective" (camera) at top right, a minimap at bottom left, and a selection card at bottom right ("Selected: House 01", Move · Rotate · Duplicate, Edit 3D Model).
- Below the map, a category bar: **Map, Terrain, Objects, Buildings, Characters, Players, Rules**, with Buildings active.
- Under it, a one-row contextual palette ("Buildings: House, Tower, Workshop, Wall") with **"Owner: Player 1"** at its right.
- A footer: Genre (Action RPG), Mode (3D), then **Customize** and **Play** side by side at the right.
- No left tool rail, no right properties panel, no outline, no asset browser.

## 2. Captures (actual applications, 2026-11-25)

| File | What |
|---|---|
| [desktop-default-1280x720.png](alpha-a01/desktop-default-1280x720.png) | Desktop editor (`:editor-desktop:run --smoke-test`, `GDXUNITY_EDITOR_WINDOW=1280x720`), default start |
| [desktop-default-1920x1080.png](alpha-a01/desktop-default-1920x1080.png) | The same at 1920 × 1080 |
| [desktop-landscape-sample-1920x1080.png](alpha-a01/desktop-landscape-sample-1920x1080.png) | Desktop with `landscape-cc0-terrain-3d.json` opened (`--project ../assets --scene …`) |
| [web-editor-default-1280x720.png](alpha-a01/web-editor-default-1280x720.png) | Web editor (`engine-babylon/editor.html`, Vite dev server on this machine, headless Chrome with software WebGL) |
| [web-editor-default-1920x1080.png](alpha-a01/web-editor-default-1920x1080.png) | The same at 1920 × 1080 |
| [web-editor-landscape-sample-1920x1080.png](alpha-a01/web-editor-landscape-sample-1920x1080.png) | Web editor with `?scene=landscape-terrain-3d.json` |

**Limits of these captures:**
- The desktop start state is affected by this machine's ignored local files (`workspace_layout.json`, editor preferences). The Assets drawer open and the "X axis" side view may be restored preferences, not the first-run default. A clean first-run capture needs a clean profile.
- The web editor here is this repository's `engine-babylon` editor. **No UrConnex-hosted web editor was inspected.** Its repository is outside the approved workspace (AGENTS.md §2), and no URL or access was provided.

## 3. Comparison with reference A

| Aspect | Reference A | Desktop today | Web editor today |
|---|---|---|---|
| Viewport share | Map about 63% of the editor; no side panels | About **45%** at 1280 × 720 and **44%** at 1920 × 1080: Assets drawer open, right Properties panel (about 25% width), left tool rail | About **47%** at 1280 × 720 and **63%** at 1920 × 1080, but fixed Outline (260 px) and Details (320 px) columns |
| Default content | An isometric map with a project | "Editor Sample Scene" in a side ortho "X axis" view (a sphere on a grid). A terrain sample opens in the same side view, with physics-debug wireframes on | An empty scene. Samples via a dropdown; a sample shows a small framed map in a large sky |
| Header | Thin: New, Open, Save, Undo, Redo, Settings | Create, Save, Undo, Redo, **Compile Scripts**, Play, **Pause**, Stop, a "Stopped" label, Project, Settings: developer-centric and duplicates Play | Two toolbar rows: Viewport, Animations, Visual Scripting, Music tabs, New, Open, Samples, Save, Undo, Redo, Add, Duplicate, Delete, Select, Move, Rotate, Scale, Sculpt, Frame, Play |
| Category bar | Bottom: Map, Terrain, Objects, Buildings, Characters, Players, Rules | **Top** tabs: Terrain, Objects, Buildings, Characters, Assets, Layers, Scripts, AI Assistant (reference B's set). **No Map, Players or Rules** | None. The Viewport, Animations, Visual Scripting and Music tabs are workspaces, not World categories |
| Contextual palette | One row under the categories, changes with the category | Drawers per tab (Terrain tools, Objects palette with thumbnails, Buildings, Characters), multi-row panels | "Add…" dropdown of primitives and components |
| Terrain controls | Terrain category | Built and rich: sculpt, paint, holes, water, roads, rivers, erosion (R3) | Sculpt tool on sculpt meshes only; no `.terrain` editing |
| Building placement | Buildings palette with house, tower, workshop, wall | Buildings drawer (Kenney CC0 house parts, M2b.3, UI M3) | None (Add → primitives) |
| Character placement | Characters | Characters drawer (Player prefab, Spawn Point, M3c.2) | None |
| Player configuration | Players category; "Owner: Player 1" in the palette | **Missing:** no player slots or ownership. `PlayerComponent` marks the one controlled character | Missing |
| Scenario rules | Rules category | **Missing as a category.** Rules exist only as visual-script graphs (Scripts tab, Logic workspace) | Visual Scripting tab (graph editing) |
| Camera choice | Top, Iso, Perspective chip on the map | A view dropdown at the top right ("X axis", isometric, perspective and others; UI M2), separate from Game Mode | Fixed orbit camera with Frame |
| Minimap | Yes | No | No |
| Selection | A floating card on the map: Move, Rotate, Duplicate, Edit 3D Model | Left tool rail (11 tools) plus the Details panel; Customize offers Edit 3D Model | Toolbar buttons plus the Details column |
| Genre and Mode | Footer, left | Inside the right panel's World Settings (Game Genre dropdown, Mode 2D, 2.5D, 3D) | Missing |
| Customize next to Play | Yes, footer right | **No:** Customize is a text item in World Settings, Play is in the header | No Customize; Play in the toolbar |
| Asset browser | Hidden | Bottom drawer open (Content Browser, 6,464 items, Project, Import, Poly Haven, ambientCG tabs) | Missing |
| Visual complexity | Low: 7 categories, 1 palette row, 2 footer buttons | High: 3 toolbars, 11-tool rail, properties panel, drawer, status bar | Medium: 2 toolbars, 2 side columns |
| Status bar | None (Saved state not shown) | Assets, Layers, Scripts, Properties: auto, a message, Ready, Saved | One line: scene name and entity count |

**Conclusion:** both editors are functionally rich but neither matches reference A's hierarchy.
- **Desktop** implements reference B (tabs on top, rail, properties panel). It lacks Map, Players, Rules, the minimap and the footer pairing of Genre, Mode, Customize and Play.
- **Web** is an IDE-style scene editor without World categories, genre and mode, Customize, terrain files, a project or a cloud.
- **As the directive says, R1b and UI M1 to M8 pass their automated tests, but this does not make them accepted against reference A.**

## 4. Feature-preservation matrix (desktop)

Every capability stays reachable. "Target" is where it would live in reference A's layout.

| Capability (today's location) | Evidence | Target in reference A | Keep, move or hide |
|---|---|---|---|
| New scene 2D, 2.5D, 3D; New Game gallery; Open; Open Sample; Use as Template; Save; Save As; Import Asset (`file.*`, Project menu) | `DefaultEditorActions`, TemplateGallery tests | Header: New (gallery), Open, Save; Save As and Import under a "…" or Settings menu | Move |
| Undo, Redo (`edit.undo`, `edit.redo`) | Command history tests | Header | Keep |
| Delete, Duplicate (`edit.*`) | | Selection card (Duplicate), Delete key | Move |
| Create Box, Sphere, Plane, Empty, lights, tone mapping, sculpt-from-selected (`create.*`) | | Objects category (primitives subgroup) and Map category (lights, sky) | Move |
| Select, Move, Rotate, Scale tools (`tools.*`), left rail | Gizmo tests (TD-016) | Selection card plus keyboard shortcuts (W, E, R) | Move; rail hidden |
| Sculpt enter and exit, terrain brushes, paint layers, holes, water, roads, rivers, erosion, ramps, heightmaps (Terrain drawer) | R3 milestones | Terrain category palette (brush, size, strength, surfaces); advanced items behind "More" | Keep, reshape |
| Level creation (New Level, surfaces) | LevelSession | Map category: new map, size, preset, lighting | Move |
| Objects palette (thumbnails, search, licence lines) | M2b.3 | Objects category | Keep |
| Buildings palette (Kenney house parts) | UI M3, M2b.3 | Buildings category | Keep |
| Characters palette (Player, Spawn Point) | M3c.2 | Characters category | Keep |
| Asset Library: Project, Import, Poly Haven, ambientCG | UI M4 to M7 | Customize → Assets (or a "Get more" link in each palette) | Hide by default |
| Layers drawer | UI M3b | Customize → Assets, or an advanced toggle | Hide |
| Scripts drawer, Compile Scripts | TD-019 | Customize → Logic; Compile happens on Play | Hide; remove Compile from the header |
| AI Assistant drawer | Agent tools | Settings, or a compact assistant button (A18 and A19 decide) | Hide until A19 |
| Outliner, Details, Console, bottom panel, theme (`window.*`) | | Advanced toggles under Settings; selection card for basics | Hide by default |
| Prefab apply, revert, create, unpack (`prefab.*`) | PrefabService tests | Selection card "…" menu | Move |
| View dropdown (X, Y, Z axis, isometric, perspective, top) and `view.toggle-2d-3d`, shadows, lighting quality | UI M2 | Map overlay chip: Top, Iso, Perspective; others under Settings | Reshape |
| World Settings: Game Genre, Game Mode | M3b.4 | Footer left | Move |
| Customize (World Settings) | M1b.7, CustomizeGateway | Footer, beside Play | Move |
| Play, Pause, Stop (header) | PlayModeController | Footer Play, which becomes Stop; Pause hidden or a key | Move |
| Workspaces: 3D Modeling, 2D Studio, Animation, Audio & Music, Logic, Assets | `Workspace` enum, WorkspaceNavigator | Customize chooser | Keep |
| Profiler, post-processing, navigation bake | M9.6, M6.2, M6.4 | Settings or advanced | Hide |
| Status bar (Ready, Saved, map size) | | A compact "Saved" in the header | Reduce |

**Missing for reference A (new work):** the Map category, the Players category and ownership, the Rules category, the minimap, the selection card, the footer, the top camera chip, and the context indicator ("World / Main Map").

## 5. Command inventory

- **Actions:** 44 registered action ids (`file.*` 11, `create.*` 9, `window.*` 6, `tools.*` 6, `edit.*` 5, `prefab.*` 4, `view.*` 3, `help.*` 1; constants in `DefaultEditorActions` and the desktop shell).
- **Other surfaces:**
  - 8 drawer tabs (Terrain, Objects, Buildings, Characters, Assets, Layers, Scripts, AI Assistant);
  - the 11-tool rail;
  - 7 workspaces;
  - 14 agent tool classes implementing `AgentTool` (`editor-core/.../agent`).
- **Duplicates seen in the captures:** Play in the header and in the play flow; Assets, Layers and Scripts both as top tabs and as status-bar buttons; Undo and Redo in the header and as keys (fine).
- **Developer-centric labels:** "Compile Scripts", "Stopped", "Properties: auto", "X axis", "- Max x".
- **No dead controls were exercised in this audit:** clicks need a person or input automation, which was not run. Input routing was not traced in this pass (A02's first task).

## 6. Scenario authoring (A04 and A05): what exists and what is missing

| Need | Exists | Missing |
|---|---|---|
| Player slots (two or more plus neutral), names, colours | Nothing scenario-level. `PlayerComponent` is the controlled character (one per scene); `SpawnPointComponent` is one per scene | Data model (TD), Players category, slot editor |
| Object ownership | No owner field on entities or prefabs | Ownership component or scene-level map, palette "Owner" selector, selection display |
| Spawn per player | One spawn point | Spawn per slot |
| Map bounds, scenario metadata | Terrain size; scene `name`, `gameMode`, `gameGenre` | Bounds, description, objectives list |
| Timer condition | `flow.delay`, `event.tick` graph nodes | A Rules-drawer form over them |
| Player-enters-area condition | Trigger colliders (`trigger: true`), `event.trigger` and `onTriggerEnter` | A Rules form, an "area" helper entity |
| Show message | `TextComponent` and graph component nodes | Rules effect |
| Mark objective complete, end scenario | **None** | Objective state and an end-scenario effect in the runtime |
| Trigger log in Play | Output log only | Rules log panel |

**Reusable:**
- the graph runtime (TD-029, interpreted on desktop and in the web player);
- trigger colliders;
- `TextComponent`;
- `PlayModeController` isolation;
- undoable commands;
- the web player's graph interpreter (web graph nodes exist: `WebMethodNodes`).

## 7. Serialization audit

| Format | File | Version field | Unknown data | Web |
|---|---|---|---|---|
| Scene | `*.json` (`SceneSerializer`) | `schemaVersion` 1; newer refused | **Desktop drops unknown component types silently** (`// Unknown component type policy: ignore`); known components keep only their named fields | **Web editor keeps the file as raw JSON** and writes it back, so unknown components and fields survive a web save. Reads `schemaVersion` 1 only |
| Prefab | `*.prefab` (`PrefabDocument`) | `schemaVersion` 1 | Not checked | Prefab links are editor-only; instances are flattened in the scene |
| Terrain | `*.terrain` (`TerrainIO`) | `version` 1 | Refuses unknown versions | Read (M3.11), **not editable** |
| Material | `*.material` (`MaterialIO`) | `version` 1 | Refuses unknown versions | Read (M2.5) |
| Sprite | `*.sprite` (`SpriteFile`) | `schemaVersion` 1 and 2 | | Read (1, 2); vector sources ignored |
| Vector | `*.vector` (`VectorFile`) | `schemaVersion` 1 | | Desktop only |
| Editable mesh | `*.mesh` (`EditableMeshIO`) | `version` 1 | No unknown-version check found | Desktop only; derived `.glb` is what scenes reference |
| Graph | `*.graph` (`GraphSerializer`) | `schemaVersion` 2 (1 read) | | Interpreted by the web player |
| Animation | `*.anim` | `VERSION` 1 | | Read |
| Song | `*.song` (`SongIO`) | `version` 1 | | Partly played |
| Project | project folder (`EditorProject`) | **No project manifest version found** | | **No project concept on the web**: a single scene file via the file picker or a download |
| Licence manifest | `licenses/manifest.json` (`ThirdPartyManifest`) | versioned | | Not used |

## 8. Desktop and web compatibility gaps

1. **No shared project manifest.**
   - The desktop project is a folder; the web edits one scene file.
   - There is no manifest listing scenes, assets, hashes, capabilities or revision.
2. **Desktop drops unknown components.** A web-added or future component would be lost on a desktop save. This contradicts "preserve unsupported data". It is the first A11 and A12 fix, and needs a TD because it changes the scene loader.
3. **The web cannot edit terrain files, prefabs, players, rules, genre or mode,** and has no Customize hand-off.
4. **The web has no cloud persistence or authentication** (local file save only).
5. **The web runs no genre profiles,** has no `AtmosphereComponent`, orders flat sprites by scene order, and leaves some instruments silent.
6. **Units and axes are shared already:**
   - scene units at 100 per metre in practice;
   - +Y up, −Z forward;
   - quaternion rotations;
   - the same component JSON.
   - The web reads desktop scenes today (55 samples in the web tests).
7. **Asset references are relative paths** with no hashes in the scene. Third-party files have SHA-256 in the licence manifest only.
8. **Revisions:** none. No revision id, base revision or conflict detection on either side.

## 9. UrConnex and UDX AI contracts

| Need | Found in this repository | Status |
|---|---|---|
| UrConnex auth, user, tenant | `urconnex-ai-agent-roadmap.md` (plan: Spring Boot owns authorization); `docs/editor-streaming.md` (a **proposed** nginx `auth_request` to `/api/editor/upstream`) | **No verified contract.** Proposals only, written without access to UrConnex |
| Project and asset storage, revisions | Nothing | **Missing** |
| Game publishing | `engine-web/EMBEDDING.md`, `docs/deploy-steps.md`: nginx `/games/engine/` and Next.js pages, **"not run against the urconnex.com VPS"**, and for the TeaVM build, which is broken | Unverified; outdated runtime |
| Subscription, credits, GPU jobs | Nothing | **Missing** |
| UDX AI service | The name appears **only** in `milestone-roadmap.md`. `urconnex-ai-agent-roadmap.md` plans a "model gateway" and a "validated tool gateway" with no endpoints. The desktop has an agent tool layer (14 `AgentTool` classes, plans with approval) | **Missing:** service identity, endpoints, auth and readiness |

**Exact missing dependencies (not guessed):**
1. Access to, or an exported API contract (OpenAPI or a route list) from, the **UrConnex repository**: Spring Boot routes for auth, projects, assets, revisions, publishing and entitlements, and the Next.js route conventions. AGENTS.md §2 forbids reading it without authorization.
2. **The UDX/UCX AI service:** its name, base URL, auth scheme, request and response schema for plans and tool calls, rate and credit limits, and an isolated test endpoint.
3. **The VPS:** a read-only inventory (GPU, VRAM, running services, queue system), or access to run one. Not accessed.
4. **Where the "UrConnex-hosted web editor" lives,** if it is not `engine-babylon/editor.html`: its repository, URL and access.

## 10. Technical decisions needed

| TD | Subject | Why | Blocks |
|---|---|---|---|
| New (A01 or A02) | World shell navigation contract: reference A's bottom categories, footer and overlays replace reference B's top tabs, rail and panel as the default layout | Changes the approved UI M3b layout | A02 |
| New (A11) | Shared project contract: manifest, versioning, unknown-data preservation, revisions, capabilities | Changes the scene loader (no silent drop) and adds a project manifest | A12, A13, A14 |
| New (A04) | Scenario data: player slots, ownership, objectives, end state | New saved data | A04, A05 |
| New (A05) | Rules: a form over graphs, or its own data | Decides whether rules are graphs | A05 |
| Amend TD-088 or new | Camera choice independent of genre and mode (roadmap §3) | Profiles currently pick the camera | A02, A07 |
| Classification | `AtmosphereComponent` on the web; TeaVM excluded from Alpha | Release honesty | A00, A09 |
| Owner decision | `asset-sources/` storage (LFS, archive or commit) | 148 MB of binaries | A00 checkpoint |
