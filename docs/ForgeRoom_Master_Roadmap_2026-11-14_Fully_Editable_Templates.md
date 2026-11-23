# ForgeRoom Master Roadmap — 14 November 2026 (UX Workflow Revision)



**Document:** `docs/14-nov-forgeroom-roadmap-revised.md`

**Latest template revision:** Appendix F is the authoritative full-customizability contract for every sample game and project template. Appendix E remains the authoritative design contract for the default World screen, adjacent **Customize** and **Play** actions, contextual workspaces, and cross-phase usability acceptance. Existing phases and approval gates remain in force.


**Status:** Proposal for owner review. **Nothing in this file is approved work.** Every phase below needs an explicit

instruction to start, and every new module or changed decision needs a technical decision record (TD) and approval

(AGENTS.md §0, §4, §10).

**Supersedes (for ordering only):** the phase order in [ALL_IN_ONE_STUDIO_ROADMAP.md](ALL_IN_ONE_STUDIO_ROADMAP.md)

and the "Recommended next work" list in [10-nov-current-status.md](10-nov-current-status.md) §6. Those files stay as

history; this file does not edit them.

**Revised 2026-11-14 (2D/2.5D/3D asset parity and final LLM harness scope):** preserves R0–R10 and approval gates, adding R1b (simplified editor), R2b (verified CC0 assets), R3c (world-to-play-to-edit acceptance), and R7a (practical building mesh editing). R3b now includes Action RPG + 3D acceptance alongside Platformer + 2.5D. Adds curated 2D and 2.5D asset sources, cross-dimensional editable samples, and a final LLM + agent-harness acceptance gate. All work remains proposed until explicitly approved.

---**



**## 1. Product vision**



Forgeroom is one editor and one runtime in which a person can make a whole game, from terrain and models to music

and scenario rules, without leaving the application. It is a **hybrid** of five tools, taking from each the part it

does best, plus a sixth reference for scenario authoring:



\| Reference | What Forgeroom takes from it | Where it already exists | Where it is missing |

\|---|---|---|---|

\| **Unreal Engine** | Level editor feel, Blueprint-style visual scripting, Material Editor, landscape tools, particle and cinematic tools, world building | Editor shell, 3D viewport and gizmos, visual scripting (TD-029, TD-068 to TD-070), PBR and shadows (TD-032) | Material assets and editor, landscape editor, particles, cinematics, post-processing |

\| **Unity** | Component model, prefabs with overrides, asset pipeline, Animator controller, 2D toolset, package and project templates | Components and serializers, prefabs (TD-027), sprite and tile tools (TD-066), packaging (TD-033) | Animator state machine, in-game UI, package system, project templates |

\| **Godot** | Small and fast, scenes that nest as nodes, signals, one-click export, built-in script editor | Scene tree, scripts and graphs, game runtime (TD-035), web player (TD-036, TD-041) | Scene-in-scene workflow beyond prefabs, built-in code editor depth, signal-style events in the Details UI |

\| **Blender** | Modeling, UVs, rigging, sculpting, shader nodes | Sculpting (TD-038, partial), skeleton and Timeline (TD-031), character creator (TD-053) | Mesh edit mode, UV editor, weight painting, modifiers |

\| **LMMS / FL Studio** | Step sequencer, piano roll, mixer, synths, sample use | Music Studio (TD-040, TD-074 to TD-076): synths, drums, SoundFont, mixer, WAV export | Listening acceptance, `.ogg` export, automation, in-scene adaptive music |

\| **RTS scenario editors** (the original *Age of Empires* campaign editor is the model; research in [swgb.md](swgb.md)) | Terrain painting and elevation, placing units and buildings per player, trigger rules, objectives, campaigns, reusable templates, random map scripts | Nothing dedicated. Pieces exist: tile maps, landscape samples, graphs, prefabs | **Everything**: see Phase R4 |

\| **Prompt-driven creation** (AI assistants in code editors, applied to game making) | A person describes a game in chat and the assistant builds it with the same operations the editor offers, reviewable and undoable | Assistant panel, 6 agent tools, structured plan engine, Ollama and mock providers ([architecture audit](AI_AGENT_ARCHITECTURE_AUDIT.md)) | Tools cover only scene, entity, transform and component basics; nothing for assets, materials, terrain, graphs, scenarios, music or export; no self-checking loop. See Phase R10 |



**Originality rule (TD-020, AGENTS.md §1).** These are references for *workflows and toolsets*. All code, data

formats, icons, assets and feature names are original. No Unreal, Unity, Godot, Blender, LMMS or Age of Empires code,

art, sound or trademarked names are copied. Reading a community file-format description for research (swgb.md) is

allowed; importing a retail scenario file format is a separate decision (§7).



\---



## 1a. Primary product acceptance journey — build Weskaap without leaving ForgeRoom

This is the **end-to-end owner acceptance scenario**, not a claim that it works today. The named game Weskaap is an example **project authored with ForgeRoom**, not a code dependency or a merger of repositories.

1. **Launch** ForgeRoom. Open an existing project or create a new one. The default workspace is **World** with an uncluttered, dominant viewport and contextual controls.
2. **Sculpt** the terrain: raise hills, carve valleys, smooth slopes, paint ground materials, and save an editable `.terrain` asset.
3. **Populate** the world: browse realistic, verified CC0 trees and boulders, place and rotate them, and scatter foliage with performance-safe instancing. Preserve provenance and local cache.
4. **Place the player character** from an editable character prefab. Assign a spawn point, collision, movement and camera capabilities. Show missing requirements clearly.
5. **Select Game Genre = Action RPG** and **Game Mode = 3D** (or 2.5D where implemented). These are separate dropdowns in the compact Play/Test bar. Press **Play**. The game starts in an isolated runtime copy with the selected controller, camera, inputs and collisions.
6. **Stop**. The editor restores the exact authoring scene, selection, camera and undo history; runtime-only modifications are discarded unless explicitly imported.
7. **Place a house** from the Buildings asset palette. Select the house and choose **Edit 3D Model**. The editor opens the **3D Modeling workspace** with the house mesh in context, preserving the world as a reference.
8. **Modify the house** using supported mesh operations (e.g. select faces, extrude a wall, move a window, change a material), preview it, save a new derived asset or update a permitted project-owned asset, then **Return to World**. The house updates in the map and in Play; other instances follow an explicit shared-asset/unique-copy choice.
9. **Save, close, reopen, play and export** the project. Verify terrain, models, materials, prefabs, game profile, controls and gameplay survive the round trip.

**Definition of product success:** a new user completes the journey with normal editor controls, without hand-editing JSON, modifying Java source, opening Blender, or manually attaching camera/controller scripts. More sophisticated character logic, animation and combat require their own verified gameplay components and may be staged after the initial walkable Action RPG slice. A successful Play button does not, by itself, imply a complete Action RPG combat system.

**No silent asset conversion:** importing a third-party GLB creates a project-managed asset. Editing a mesh must preserve or explicitly replace its source/derived relationship; never destructively overwrite downloaded provider originals.

---

## 2. The guiding rule: everything shipped as an example must be editable



The owner's requirement: *the examples in the editor should be editable on all fronts.* Today that is not true. For

example, the five landscape scenes are glTF files generated by Python scripts (`tools/make_landscape_*.py`), so a

user cannot reshape the terrain, repaint it or move a tree except by moving whole entities; and only one of the 15

sample graphs (`ShooterShip`) is recorded as matching its reference gameplay (10-nov status §6, item 4).



**Editable on all fronts** means, for every sample scene:



\| Front | Requirement |

\|---|---|

\| Scene | Every entity and component has a Details editor; nothing is only settable in JSON |

\| Logic | Behavior is a visual graph or script visible in the editor (a Java-only sample counts as a gap) |

\| Terrain | Terrain is a terrain asset edited by the landscape tools (R3), not a generated model |

\| Materials | Looks come from material assets the user can open and change (R2) |

\| Animation | Clips and state machines open in the Timeline or the Animator editor (R5) |

\| Audio | Music and effects open in the Music Studio or sfxr editor |

\| Prefabs | Repeated objects are prefabs with overrides (TD-027), not copies |

\| Rules | In scenario samples, rules are triggers the user can edit (R4) |

\| Ownership | A sample can be duplicated into the user's project and edited; the original stays intact |



**Enforcement.** Phase R1 builds an *editability audit*: an automated test that loads every sample scene and fails

when an entity has a component with no Details editor, a script with no graph or source editor, a referenced asset

type with no opening editor (the `.song` Content Browser bug of 2026-11-12 is exactly this class of defect), or a

model that should be terrain. The audit result is a table in `docs/FEATURE_TEST_MATRIX.md`, updated with every

phase. A sample is not called editable until its row is complete.



\---



## 3. Current baseline (honest snapshot)



Sources: [INDEX.md](INDEX.md), [FEATURE_TEST_MATRIX.md](FEATURE_TEST_MATRIX.md),

[10-nov-current-status.md](10-nov-current-status.md). Status words are the AGENTS.md §9 meanings.



\- **Implemented and integrated** (automated tests pass; reachable from the editor): 2D and 3D scenes, prefabs, 2D

  physics (Box2D), 3D physics (Jolt), visual scripting with functions and events, skeleton animation and Timeline,

  character creator, sculpting (partial), sprite and tile tools, Music Studio, packaging, game runtime, web player.

\- **Validated in the running application** (a person saw it work): very little. Most rows say "visual check

  pending" or "Manually Not Verified (Headless Environment)". The [visual checklist](reports/visual-check-checklist.md)

  is the plan for closing that.

\- **AI assistant (Implemented; Integrated):** `AssistantPanel` in the editor, `AssistantService`, `OllamaModelProvider`

  and `MockModelProvider`, an agent tool registry with 6 tools (scene inspect, entity create, rename and delete,

  transform set, component add), and a plan parser, validator and executor. Tests: `AssistantServiceTest`,

  `AssistantIntegrationTest`, `AgentPlanEngineTest` (matrix rows). Not validated with a real model in this review.

\- **Content:** 36 sample scenes in `assets/samples`, 15 sample graphs in `assets/graphs/samples`.

\- **Known open defects** (not re-verified on 2026-11-13 unless stated): the Kotlin generator test failure and the

  Duplicate-component and save/load gaps listed in the 10-nov status; File > Open sends `.song` files to the scene

  loader (seen 2026-11-12; the Content Browser half was fixed that day, with the `SONG` asset type and a test).

\- **Not found in the code** (a search of the engine modules on 2026-11-13; treat as likely gaps, not proven): material

  assets, particles, an animator state machine, in-game UI canvas, navigation mesh, terrain assets.



\---



## 4. Phases



Each phase lists its goal, deliverables, what it needs approved, and its **done** test. Order is a recommendation;

the reasons are in §5. Sizes are rough (S under 2 weeks, M 2 to 6, L 6 or more, one person) and are guesses, not

estimates from measured velocity.



### R0 — Trustworthy baseline (S) — *do first*



**Goal:** know what works before adding more.

\- Fix the Kotlin generator test failure; run `./gradlew test --continue` for the whole repo; record per-module test

  counts and every skip (AGENTS.md §5).

\- Close the data-consistency gaps: Duplicate component, save and load round trip, Play isolation, undo and redo for

  the newest components (10-nov status §6, item 2).

\- File > Open understands `.song`, `.graph`, `.anim`, `.sprite`, `.prefab` and opens each in its editor, or says

  clearly that it only opens scenes.

\- **Acceptance project**: one person builds a small game end to end (create scene, import a model, physics, camera,

  graph logic, animation, music, save, reopen, play, export, launch the export) and records what they saw at each

  step in a report.

\- Reconcile INDEX and the feature matrix with dated results.



**Done when:** a full test run passes or every failure is listed as pre-existing with a cause; the acceptance report

exists with observed results.



### R1 — Editable examples and project templates (M)



**Goal:** the rule in §2 for everything that does not need a new subsystem.

\- Editability audit test and matrix (§2).

\- Finish TD-068: convert the remaining 14 sample graphs to full graphs that match their Java behavior frame for

  frame, with parity tests (needs the function and component-node support from TD-069 and TD-070).

\- "Duplicate sample into my project" action; samples open read-only as originals.

\- **Template gallery** on the start screen: empty 2D, 2.5D, 3D, platformer, top-down, first person, and later

  strategy (R4). A template is a project folder plus a scene, prefabs and graphs, built from the same editable parts

  as the samples.

\- Content Browser: every asset type has an icon, an opening editor and a "used by" list.



**Done when:** the audit passes for every sample that does not depend on R2 to R4, and a new project from each

template opens, plays and exports.



### R1b — Minimal, scenario-editor-inspired UX shell (M) — **new, early priority**

**Goal:** make the default interface as direct as the original Age of Empires scenario editor while retaining all advanced capabilities as discoverable workspaces.

- Default **World** workspace: compact app bar (save, undo, redo), workspace tabs, World categories (**Map, Terrain, Objects, Players, Triggers**), large central viewport, one small contextual tool strip at the bottom. Only show actions that are supported; missing features must be visibly labeled rather than fake controls.
- Keep **Scene, 3D Modeling, Animation, Music, Sprites/2D, Visual Scripting, Assets** as separate workspaces; keep advanced hierarchy, inspector, content browser, logs and AI panel collapsible and accessible by menu/shortcut.
- Put **Game Genre**, **Game Mode (2D/2.5D/3D)** and **Play/Stop** together in a compact bar, visible from World and other workspaces where appropriate. Editor camera/view orientation is a third, independent concern, not a gameplay mode.
- Selection-aware tools: terrain brush settings for terrain, prefab palette for Objects, contextual properties for selected entities, **Edit 3D Model** for a mesh-backed building. Never show unrelated controls at once.
- Preserve keyboard shortcuts, accessibility, panel resizing, undo/redo, viewport focus and existing desktop behavior. Provide a clear path back to World from specialized workspaces.
- Establish a shared *UX specification* for the UrConnex-hosted ForgeRoom web UI; do not couple desktop Java/LibGDX implementation to Next.js or Spring Boot.
- Create a feature-to-workspace inventory before hiding or relocating any existing command; do not delete working features.

**Needs:** UX/layout TD if it changes editor navigation contracts; actual UI framework audit. **Done when:** an unfamiliar tester can sculpt a slope, place a rock, locate Play, open the 3D Modeling workspace and return to World without guidance, and all legacy commands remain reachable. Record screenshots and usability observations.

---

### R2 — Materials and the Material Editor (M to L) — *Unreal, Blender, Unity*



**Goal:** looks become data a person can edit.

\- `.material` asset: PBR parameters (base color, metallic, roughness, normal, occlusion, emissive, opacity),

  texture slots, tiling, double-sided, alpha mode. Primitives and imported glTF models can use one.

\- Material Editor tab: a node graph that reuses the graph canvas (TD-069 palette). Output node feeds the PBR batch

  (TD-032). Live preview sphere. Parameter instances (Unreal) and shared materials (Unity).

\- Details panel material slot with drag and drop from the Content Browser.

\- Web player (Babylon) reads the same file; both backends listed in a support matrix (R9).



**Needs:** a TD (new asset type, graph reuse); possibly a new module `engine-material` (approval required).

**Done when:** a sample's rock, wood and water materials are separate `.material` files edited in the editor, and

the change shows in the viewport and in Play.



### R2b — Verified CC0 realistic assets, model import and provenance (M) — **new**

**Goal:** default World palette uses realistic trees, boulders, vegetation and buildings rather than only geometric primitives, without compromising redistribution rights, performance or editability.

- Use the verified provider links, license gates and sample-refresh plan in **Appendix A** below. Prefer **Poly Haven** for realistic CC0 3D models and **ambientCG** for CC0 PBR materials; optionally verified CC0 Kenney packs for lightweight content. Verify the current license of **each** asset and provider API terms before downloading. Do not infer that every item on an unrelated site is CC0.
- Build a provider-neutral asset source interface, a small **curated starter pack** and optional on-demand downloads; no bulk scraping. Use official APIs, provider-required identification/credit, request limits and checksum validation. Log source URL, provider, author where available, CC0 license, retrieval date, hash and any conversion.
- Prefer glTF 2.0/GLB for import and interoperability. Audit existing LibGDX glTF loader and material support; handle textures, alpha-cutout foliage, PBR support and graceful degradation. If a source supplies another format, use a reproducible conversion step and validate output rather than assuming GLB exists.
- Cache models locally for offline use. Do not commit large binary collections automatically. Keep downloaded originals immutable and derived editable copies separately.
- Extend the **Objects** palette with searchable thumbnails, Trees/Rocks/Plants/Buildings, license detail, **Place**, and **Edit 3D Model** where mesh editing is supported. Asset and prefab IDs must be stable across save/load.
- Add scene-aware instance transforms, prefab overrides, collision authoring, instancing/LOD plans and performance measurements. A realistic-looking asset does not automatically have gameplay collision or a suitable low-poly mesh.
- Maintain a shared asset manifest and support matrix for desktop LibGDX and UrConnex ForgeRoom web rendering; backend storage is an optional later integration, not a desktop prerequisite.

**Needs:** asset import/licensing TD and any new dependency approval; R2 materials for full fidelity, but the first import slice can proceed using existing renderer capabilities with limitations documented. **Done when:** one verified CC0 realistic tree, rock and house are downloadable/importable, appear with recognizable thumbnails, place multiple times, save/reload offline, preserve license metadata and render acceptably; original primitive placeholders remain available. Also produce the **sample migration inventory and before/after visual checks in Appendix A**; do not claim a sample is fully editable merely because its GLB can be transformed.

---

### R3 — Landscape and terrain tools (L) — *Unreal Landscape, Unity Terrain, Godot heightmaps, Blender sculpt*



**Goal:** terrain is an editable asset, replacing the generated landscape models.

\- `.terrain` asset: heightmap grid, resolution and size, per-layer splat weights (grass, rock, sand, snow, dirt),

  holes, water level. Chunked mesh with level of detail so large maps render.

\- Tools: raise, lower, smooth, flatten to height, noise, erosion pass, ramp between two points, paint layer, erase

  layer, import and export heightmap (PNG 16 bit), undo for every stroke (one `EditorCommand` per stroke, TD-018).

\- **Scatter tools** (foliage, rocks): brush with density, random scale and rotation, slope and height limits,

  exclusion zones, using prefabs. Instanced rendering.

\- **Splines**: rivers, roads and cliffs from editable curves that deform terrain and place meshes.

\- Water body component, sky and weather presets, fog.

\- **Collision:** the terrain feeds 2D (height as slopes where relevant) and 3D (Jolt height field) physics; character

  controller walks on it (TD-049).

\- Build on the existing sculpt core (`engine-sculpt`, TD-038) where it fits; do not fork a second brush system.

\- Convert the five landscape samples to terrain assets.



**Needs:** TD; new modules `engine-terrain`, `editor-terrain` (approval); web player support is its own step.

**Done when:** a person opens `landscape-3d`, reshapes a valley, repaints a slope, scatters pines, saves, plays, and

the character walks on the new ground.



**Status 2026-11-14 (first parts, see [r3-terrain-report.md](reports/r3-terrain-report.md), TD-082 and TD-083):** the

`.terrain` asset, chunked drawing, brushes with undo, per-point ground surfaces (grass, sand, snow, gravel) and a first

level editor exist. Collision, layers with blended weights, water, scatter, splines, erosion, heightmap import and

level of detail do not. Nobody has yet checked collision-free walking, because there is none.



### R3b — World View: build a world, then play it as a chosen genre (M) — *Unreal level editor, original Age of Empires campaign editor, Unity Play mode*



**Goal:** one default place to build a world, and a Play/Test bar that launches that world as a chosen kind of game,

instead of every game needing its own hand-attached scripts and camera.



**Where it stands:** the pieces exist but are not joined. Terrain and the first level editor (TD-082, TD-083); game

modes 2D, 2.5D and 3D as a per-scene authoring setting (TD-067); Play runs a serialised copy of the scene and Stop

restores the original (`PlayModeController`); camera rigs (TD-050); the 3D character controller (TD-049); four 2.5D

samples with shared `SideBody` and `SideCamera25D` scripts. Missing: any idea of a *genre*, a profile that supplies

camera, movement and input when Play is pressed, and collision for terrain.



\- **World View.** The default centre view of the editor (today the Viewport tab) becomes the place the level tools

  live: base ground, shaping, ground painting, object palette and placement, grown from the TD-083 palette. The other

  views (game view, Animations, Visual Scripting, Music, Sprites, Tiles) and the Content Browser stay as they are.

\- **Play/Test bar.** Two dropdowns beside Play, **Game Genre** and **Game Mode** (2D, 2.5D, 3D), independent of which

  view is open. The genre list comes from a registry, not from UI code: Platformer / Side-scroller, Action RPG,

  Adventure, First-person Shooter, Third-person Shooter, Real-time Strategy, Turn-based Strategy, Racing, Puzzle,

  Simulation, Sandbox. A combination is listed as playable only when its test passes; the rest are shown as planned.

\- **`GameTestProfile`** (engine level, headless): genre, dimension, camera, movement, physics, input mapping and

  runtime requirements. Pressing Play resolves the profile, validates the scene and says what is missing in plain

  words, applies the profile to the **runtime copy only** with runtime-only components that are never saved, and shows

  the preview. Stop disposes the copy and restores the scene and undo history untouched.

\- **Reuse, not rewrite.** Camera follows `CameraRigComponent`, movement uses the character controller, physics is the

  existing 2D and 3D systems. 2.5D is its own configuration (side camera, play plane, locked depth), not a renamed 3D

  camera.

\- **Saved choice.** The last genre and mode are an optional field of the scene file; older files load unchanged.

\- **Order of slices.** Platformer + 2.5D as the first generic profile slice, **Action RPG + 3D as the owner acceptance slice (R3c)**; each later combination (for example 3D platformer,

  third-person, racing, first-person) is its own small slice added to the registry.

\- **Agent parity (§6, rule 8):** tools to set the genre and mode, validate, and press Play and Stop.



**Needs:** a TD for the profile architecture and the scene-file field; no new module is planned (`engine-scene`,

`engine-render`, `editor-core`, `editor-desktop`), and one would need approval. Terrain **collision** from R3 before a

world made of terrain is walkable; worlds built from box, hull or mesh colliders work first.

**Web:** desktop only at first, recorded in the support matrix (R9).

**Done when:** a person builds a world, adds a Player, picks Platformer and 2.5D, presses Play, sees a side camera,

runs and jumps along the play plane and collides with the level, presses Stop and gets the unchanged scene and undo

history back; and an unsupported combination is refused with a reason that says what to add.



### R3c — World → Play → Stop → Edit → Play acceptance slice (M to L) — **new product gate**

**Goal:** join R1b, R2b, R3, R3b and the existing runtime into one verifiable user journey, using a Weskaap-style Action RPG world.

- First walkable slice: sculpt a valley and slope, paint materials, place realistic boulders/trees, add a player prefab and spawn point, and **Play as Action RPG + 3D**. This is separate from the pre-existing R3b **Platformer + 2.5D** slice; do not declare either complete based on the other.
- Genre profile registry supplies camera/controller/input/physics configuration and reports readiness per genre × dimension. **Action RPG + 3D** requires third-person follow camera, ground movement, terrain collision, player spawn and Stop restoration; basic attack/combat can be a later explicitly named slice.
- **Action RPG + 2.5D** should be independently validated for isometric or side-view movement, collision and camera; do not treat it as automatic compatibility.
- Pressing Play uses a disposable runtime scene copy; Stop restores the authoring state, pending edits and undo history. Profile changes never destructively convert the world.
- After Stop, place a realistic house prefab, select it and open **3D Modeling**. If R7a mesh-editing is not ready, show a clear capability notice; do not pretend transform-only editing is mesh editing.
- Complete the same test after the R7a house-edit slice: modify a wall or roof geometry, save as a project-owned derived model, return to World, verify changed geometry in the scene and replay the Action RPG profile.
- Add a regression test project plus human desktop visual checklist for the full journey. Verify save/reopen/export where supported; mark unverified web behavior explicitly.

**Dependencies:** R0 scene consistency and Play isolation, R1b UX, R2 materials, R2b import, R3 terrain collision, R3b profile framework, and R7a for the full house-mesh-edit criterion. **Done when:** the complete owner acceptance journey in §1a is demonstrated with recorded evidence; partial completion must be reported as partial.

---

### R4 — Scenario and campaign editor with templates (L) — *original Age of Empires campaign editor as workflow model*



**Goal:** a person authors a strategy scenario and a campaign without writing code. This phase has two halves: an

**RTS runtime framework** (what games do) and the **scenario editor** (how they are authored).



*Runtime framework (`engine-strategy`, proposed):*

\- Players and teams, colors, diplomacy states, starting resources, population limits.

\- Unit and building definitions as data: stats, costs, build and research lists, tech tree, upgrade chains.

\- Orders and selection: box and group select, move, attack, gather, build, repair, patrol, formations.

\- Navigation: grid or navmesh from the terrain, obstacle avoidance, flow-field or A* with many units.

\- Fog of war and line of sight; resource nodes, gathering, drop-off, production queues.

\- Simple AI: build orders and attack waves driven by behavior graphs (reuse visual scripting).



*Scenario editor (`editor-strategy`, proposed):*

\- **Terrain and map**: the World View (R3b) with strategy palettes, reusing the R3 tools; map size presets; terrain and

  elevation painting; cliffs; water.

\- **Object palette**: units, buildings, resources and decorations, filterable per player; place, move, rotate,

  delete, multi-select, copy and paste.

\- **Players panel**: names, civilization-style data sets, colors, starting units, resources, victory settings.

\- **Triggers**: condition and effect rules on the visual scripting runtime (when a unit enters an area, an object is

  destroyed, a timer elapses, a variable changes → spawn, move, change ownership, show message, play sound, end

  scenario). Presented as a trigger list with explanation text, like the original editor, but stored as graphs.

\- **Objectives and instructions**: objective list, scenario briefing and hint text, per-player text.

\- **Cinematics**: camera paths and timed dialogue using the Timeline (TD-031).

\- **Campaign**: a `.campaign` file that links scenarios, carries variables between them, and defines the order and

  victory branches.

\- **Random map scripts**: a graph that builds a terrain and places objects from a seed (land shapes, resource

  placement rules), runnable from the scenario editor and at game start.

\- **Templates and prefabs**: unit prefab library, building prefab library, base layout prefabs (a town center with

  starting units), scenario templates (skirmish, survival, escort, capture), and a template gallery entry for each.

  Prefab overrides (TD-027) are how a scenario changes one unit without losing the template.

\- **Test play** from any position in the editor, with cheat commands and a trigger debugger.



**Needs:** TD for the module layout and file formats (`.scenario`, `.campaign`, unit data); a decision on importing

community scenario formats (§7); R3, R3b and R2 first; R5's state machine for unit animation.

**Done when:** a person builds a two-player map, places bases from templates, writes a "destroy the enemy base"

trigger and an "after 10 minutes, send an attack wave" trigger, saves, tests, and plays it in the exported game;

then links two such scenarios into a campaign.



### R5 — Animation system upgrade (M) — *Unity Animator, Unreal Animation Blueprint, Godot AnimationTree*



\- Animator controller: states, transitions with conditions (parameters set from graphs), blend spaces, layers and

  masks. Editor is a graph tab.

\- Retargeting between skeletons; two-bone IK exists (TD-031), add look-at and foot placement.

\- Root motion; animation events that call graph events; ragdoll through Jolt.

\- 2D: sprite animator states using the same controller.



**Done when:** the character sample walks, runs and jumps by parameters set from a graph, with blends visible in

Play.



### R6 — Effects, UI and AI (L)



\- **Particles**: emitter component, curves over lifetime, GPU or batched CPU rendering, a particle editor tab,

  presets (fire, smoke, dust, sparks, hit effects).

\- **Post-processing**: bloom, color grading, ambient occlusion, depth of field as a volume component.

\- **In-game UI**: canvas, anchors, layout containers, buttons, text, images, progress bars, themes, input focus,

  authored in a UI editor tab and driven by graphs (Unreal UMG, Unity UI, Godot Control).

\- **Navigation and behavior**: navmesh baking, agent component, behavior graphs (used by R4's AI).



### R7 — Modeling, UV and rigging (L) — *Blender*

**Source assets and editable examples:** See **Appendix C** for CC0 `.blend` demonstration files, source-mesh preservation, mesh editing fixtures and an approved import-to-derived-asset workflow. A `.blend` file is a reference/source format, not proof that ForgeRoom can natively open it; conversion is an explicit, tested operation.




Un-defers the TD-026 items after R2 to R4: half-edge mesh data model, Edit Mode (vertex, edge, face), extrude, inset,

loop cut, bevel, mirror and subdivision modifiers, UV unwrap and editor, weight painting, bone placement, export to

glTF. Reuses the undo system and the material system. This is a very large area; it is planned last in the "make

things" group because it is the least tied to the game-authoring loop and the most expensive.



### R7a — Practical in-editor 3D building editing (M to L) — **new, prioritized vertical slice within R7**

**Example asset sources and fixture migration:** Appendix C adds Blender-hosted CC0 editable mesh demonstrations and requires a house/prop fixture with a verified source mesh or project-owned derived editable mesh.


**Goal:** selecting a house in World and choosing **Edit 3D Model** opens an actual geometry editor, not just an object transform inspector.

- Open a selected mesh-backed prefab in the **3D Modeling** workspace with contextual reference to its world placement. Distinguish **instance transform**, **prefab override**, **mesh edit**, and **material edit**.
- First supported mesh operations: vertex/edge/face selection, translate, extrude, inset, basic bevel, face deletion and material assignment; implement only those justified by existing `engine-sculpt` and TD-026 architecture. Add undoable commands, mesh validation and preview.
- Imported GLB meshes may require conversion into an editable internal mesh representation; preserve node transforms, material slots and asset provenance. Do not imply every glTF extension, rig or skinned mesh can be edited.
- On Save offer **Save as new model** (safe default), **Update project-owned model**, or **Create unique instance mesh**. Warn when an edit will affect other prefab instances; never overwrite provider originals.
- Return to World without losing selection/camera context. Rebuild render/collision data as necessary; changes persist through save/reopen and Play, and can be exported to glTF/GLB where supported.
- Preserve R7's broader UV unwrap, rigging, weight painting and advanced modifiers as later scope; do not block the basic house workflow on full Blender parity.

**Needs:** R2 material support, R2b imported meshes, mesh-data/serialization TD, command-system compatibility. **Done when:** import a CC0 house, place it, open its geometry, change the roof or wall, save a derived model, return to World and play the changed scene; undo, reload and collision are verified.

---

### R8 — Music and audio completion (M) — *LMMS / FL Studio*

**Source audio and editable examples:** See **Appendix C** for CC0 sound effects and loops, audio editing fixtures, original editable `.song` projects, source/provenance metadata, and export/playback verification. Audio recordings are editable waveforms but are not automatically editable MIDI compositions or multitrack sessions.




\- A person listens and signs off: instruments, drums, mixer, latency (Phase 20 never had a listening acceptance).

\- `.ogg` export (documented gap); clip import and slicing; sample-based instruments.

\- Automation lanes (volume, pan, filter) on the playlist; per-channel effects chains.

\- **In-game music**: songs as scene assets, layers that fade in and out by game state (adaptive music), triggers

  from graphs and from R4 scenarios.



### R9 — Pipeline, extensibility and backend parity (M, ongoing) — *Unity packages, Godot add-ons*



\- **Packages**: a folder format and manager for sharing prefabs, graphs, materials and templates; editor extension

  points (custom panels, custom Details editors, menu items) documented in Javadoc.

\- **Import**: OBJ and FBX through a documented library decision, Aseprite import (TD-066 follow-up), audio formats.

\- Text-friendly scenes and meaningful diffs for version control.

\- **Backend support matrix**: Java desktop, TeaVM and Babylon behavior listed feature by feature; release checks on

  real Windows, Linux and macOS packages (10-nov status §6, item 6).

\- Performance baselines (scene load, frame time, memory, audio latency) before and after each phase.

\- Profiler and frame debugger panel.



### R10 — Final phase: LLM integration, agent harness and prompt-to-game creation (L) — *begins only after all prior roadmap acceptance gates*



**Goal:** a person who has never opened the editor types what game they want, answers a few questions, and gets a

playable, exportable, **fully editable** game, built step by step with the editor's own operations. This phase is

last on purpose: the assistant can only do what the editor can do, and it is only as good as the templates,

prefabs, materials, terrain tools and scenario tools it can call. Everything before R10 is what R10 stands on.



**Principle: agent parity.** Every editor operation a person can perform is also an `AgentTool` (AGENTS.md §7:

`editor-core/.../agent/tools`, returning `AgentToolResult` with machine-readable outputs). From R1 onward, **each

phase delivers its own agent tools and tests** next to its feature, so R10 is mostly integration, context, safety

and evaluation, not a late rewrite. R10-A audits that this really happened.



**What exists** (§3): the panel, 6 tools, plan engine, Ollama and mock providers. These are existing foundations, not authorization to start the final LLM/harness rollout early. **What R10 adds:**



\| Step | Deliverable |

\|---|---|

\| **R10-A Tool coverage** | A parity matrix of every editor action versus its agent tool; fill the gaps. Tool families: scene and prefab; assets (import, place, rename, find); materials; terrain (generate from a description, paint, scatter); graphs (create, add and connect nodes, validate, explain); scenarios (players, objects from templates, triggers, objectives, campaign); animation (clips, state machine); audio (compose a song, choose instruments, sfxr cues); UI; project (new from template, save, export). Tools are typed and validated like today's. |

\| **R10-B Plan, preview, apply** | The assistant produces a structured plan (existing schema), the editor shows it as a readable list and a dry-run summary of what will change, the user approves or edits steps, then the plan runs as **one undo group**. "Undo AI step" reverts a whole prompt. No tool mutates state except through `EditorCommand`. |

\| **R10-C Context** | A compact, token-budgeted view of the project for the model: scene outline, selection, asset list, available components and nodes (generated from the registries so it never goes stale), template list, recent Output Log. Retrieval over project files and the engine's own docs. Selection-aware prompts ("make this bigger", "add a patrol to the selected unit"). |

\| **R10-D Self-checking loop** | After a plan the agent can *look*: run the editability audit (§2), validate graphs, start Play or a smoke test, read the Output Log and test results, capture a screenshot (the `GDXUNITY_SCREENSHOT` aid), and fix what it broke, within a step and retry limit. |

\| **R10-E Providers** | Local Ollama (exists) and mock stay. Optional cloud providers behind the existing provider-neutral contract, with keys stored outside the project, an offline mode, and a visible statement of what leaves the machine (`AI_AGENT_ARCHITECTURE_AUDIT.md` §5 and §7). |

\| **R10-F Evaluation** | A fixed prompt suite (for example "a 2D platformer with three levels and a boss", "a two-player skirmish map with an attack-wave trigger", "a 3D island with a walkable character and music") scored automatically: scene validity, editability audit, graphs validate, Play and the exported game run with 0 errors. Recorded model transcripts replay through the mock provider as regression tests. Human review on top. |

\| **R10-G First-run flow** | "New game from a prompt": the assistant picks a template (R1, R4), customizes it with terrain, materials, prefabs, logic and music, asks clarifying questions, shows progress, and ends with a Play button and an Export button. Conversation features: history, cancel mid-plan, edit a plan step, attach a reference image, "explain what you built". |



**Final-phase sequencing and architecture gate.** R10 is the **last implementation phase** after R0–R9, including R1b, R2b, R3b, R3c and R7a, meet their agreed acceptance criteria (or owner-approved documented exceptions). Earlier phases may deliver deterministic typed editor operations and agent-tool contracts as testable interfaces, but **no user-facing autonomous LLM game-building workflow is considered complete or rolled out before R10**. At R10, choose and document a provider-neutral **LLM and agent harness**: model adapters (local-first, optional opt-in cloud), tool registry, context retrieval, structured planning, permission/approval boundaries, execution state/checkpoints, cancellation, retry/time budgets, telemetry, sandboxing for untrusted content, undo/rollback, validation and evaluation. Keep the Java desktop engine independent of UrConnex; define an explicit protocol if an optional UrConnex-hosted harness is used, with no implicit network requirement for desktop editing. The harness must use the **same validated editor commands** as manual editing, not bypass scene ownership, serializers or safety controls.

**Genre coverage goal.** The user should be able to prompt toward **working, editable, testable and exportable games in major genres** (platformer, action RPG, adventure, FPS, TPS, RTS, turn-based strategy, racing, puzzle, simulation and sandbox) across compatible 2D/2.5D/3D profiles. This is a **target, not a claim of current support**: each genre × dimension pair requires a playable template, profile, asset set, gameplay logic, automated tests and recorded export support. Unsupported combinations must be clearly reported rather than silently approximated. Acceptance includes a prompt-created 2D platformer, 2.5D side-scroller, and 3D Action RPG, plus representative genre coverage tracked in a matrix; do not assert universal support until every advertised combination passes.

**Safety rules (needs a TD):**

\- **Scripts are trusted code (TD-019).** Java and Kotlin scripts run in-process. Anything the assistant writes as

  source is shown for review and is not run until the user accepts it; the assistant prefers visual graphs, which

  the validator checks and the interpreter sandboxes by design.

\- Tools only touch files inside the project; paths are validated; no shell, network or other-project access.

\- Every change is undoable and visible; a plan has step and cost limits; a user can stop it at any time.

\- Prompts and project data are private: nothing is sent to a cloud provider without the explicit setting in R10-E.



**Limits stated up front:** output quality depends on the model. Small local models will manage simple edits and

templates, not a whole original game, and the evaluation suite will show where the line is. Nothing here claims a

model can design a good game, only that the tools, checks and safeguards let it try without breaking the project.



**Needs:** a TD for the plan-preview and undo-group design, the safety rules and cloud providers. Tools live next to

their domain modules, so no new module is required for R10 itself (to be confirmed in the TD).

**Done when:** from an empty project, a person uses only prompts (plus approving plans) to produce a small game that

plays and exports with 0 errors, passes the editability audit, and can then be edited by hand in every editor; the

R10-F suite passes at the thresholds the TD sets; and a whole prompt can be undone in one step.



\---



## 5. Order and why



```

R0 baseline ─► R1 editable samples ─► R1b simple UI ─► R2 materials ─► R2b CC0 assets ─► R3 terrain ─► R3b profiles

                                                       │                │

                                                       └──► R5 animator ┘            ──► R6 effects/UI/AI

R8 music (parallel, small)      R9 pipeline (continuous)                                 R7 modeling

                                                                                          │

                        every phase also adds its own agent tools (§6, rule 8) ──────────► R10 AI game builder (last)

```



\- **R0 first** because 10-nov status found failing tests and many "visual check pending" rows. More subsystems on an

  unverified base multiply the checks owed.

\- **R1 before new systems** because it turns the owner's requirement into a test that every later phase must keep

  green.

\- **R2 before R3 and R4** because terrain layers and unit looks are materials.

\- **R3 before R4** because a scenario editor without terrain tools is not an editor; R4 also needs navigation data

  from the terrain.

\- **R3b between R3 and R4** because the scenario editor is the World View with strategy tools added, and because play

  profiles are what let a built world be tried as a platformer, shooter or strategy game without hand-attached scripts.

\- **R5 before R4's unit work** because units need a state machine for idle, walk, attack and die.

\- **R7 last** because it is large and the game-authoring loop works without it (users can import models).

\- R8 is small and can run alongside; R9 is continuous.

\- **R10 last** (your instruction, and it is also the technical order): the assistant can only build with tools,

  templates and checks that already exist. Its *tools*, though, are written during each earlier phase, so R10 is not

  a big-bang rewrite at the end.



### Dependencies on existing approved decisions



TD-016 (gizmos), TD-018 (one command per drag), TD-019 (scripting), TD-020 (UE5 workflows, no copied code), TD-026

(sequencing), TD-027 (prefabs), TD-028 and TD-048 to TD-052 (3D physics), TD-029 and TD-068 to TD-070 (visual

scripting), TD-031 (animation), TD-035 (game runtime), TD-038 (sculpt), TD-040 (music), TD-066 and TD-067 (2D tools

and game modes), TD-082 and TD-083 (terrain and the level editor). None is changed by this roadmap. Any phase that would change one needs a new record first.



\---



## 6. Rules every phase follows



1\. **Status words** (AGENTS.md §9): *implemented*, *integrated*, *validated* mean what they say. An agent that cannot

   see the screen writes "Manually Not Verified (Headless Environment)".

2\. **Tests ship with behavior** (AGENTS.md §8). Pure logic goes in headless modules; GL code is listed as "needs

   graphics test".

3\. **Editor changes are commands** so undo and redo work (AGENTS.md §7).

4\. **Editability audit stays green** (§2). A phase that adds a component, asset type or sample adds its editor.

5\. **Components** get a `ComponentSerializer` and a round-trip test; every new asset type gets a Content Browser

   entry, thumbnail, opening editor and drag-and-drop behavior (the `.song` bug shows what happens otherwise).

6\. **Web parity is recorded**: each phase states what the web player does and does not support, in the matrix.

7\. **Each phase ends with a report** in `docs/reports/` (AGENTS.md §10): criteria met or not met, commands run,

   per-module test counts, known limits, and a statement that no commit or push was made unless authorized.

8\. **Agent parity.** A phase that adds an editor operation also adds its `AgentTool` (typed, validated, returning

   machine-readable outputs) and a test that drives it. R10-A audits this; a phase without its tools is not done.



\---



## 7. Decisions needed from the owner



Before work starts, these need an answer; none is assumed here.



1\. **Order.** Approve R0 and R1 first, as recommended? Or start a larger area (R3 or R4) in parallel?

2\. **New modules.** Approve in principle `engine-material`, `engine-terrain`, `editor-terrain`, `engine-strategy`,

   `editor-strategy`? (AGENTS.md §4: creating a module is an architectural change.)

3\. **Scenario format import.** Should R4 read community-documented scenario files (`.scx`, `.scn`, `.cpx`) per

   [swgb.md](swgb.md), or only use an original format? Importing is useful for testing but raises asset and legal

   questions that need your decision.

4\. **Strategy scope.** The original editor's depth (hundreds of trigger types, multiple civilizations) is large.

   Is the target a *complete* strategy toolset, or a smaller core (players, objects, triggers, objectives,

   campaign) first? This roadmap assumes the smaller core first.

5\. **Targets.** Desktop and web are covered today; mobile, console and VR are not in this roadmap. Say if any should

   be.

6\. **Legacy cleanup.** Remove the unused `core/` and `lwjgl3/` template folders (needs approval, AGENTS.md §4).

7\. **Phase naming.** This roadmap uses R0 to R10 so as not to collide with the old Phase 9 to 20 numbering. Rename if

   you prefer.

8\. **AI providers and privacy (R10-E).** Local models only, or also optional cloud providers? If cloud: who supplies

   keys, and is any project content allowed to leave the machine by default (this roadmap assumes no)?

9\. **AI autonomy (R10-B).** Must the user approve every plan before it runs (assumed), or may a "trusted" mode apply

   small plans directly? Either way every prompt stays one undo step.

10\. **AI-written scripts (R10).** Review before running (assumed), or graphs only? TD-019 scripts run as trusted

    in-process code, so this is a security decision.

11\. **World View naming and play mode (R3b).** Rename the Viewport view to "World"? Should the Play/Test Game Mode

    default to the scene's own mode and be overridable at Play without changing the scene (assumed), or replace it?

12\. **Terrain collision order (R3b).** Build terrain collision (Jolt height field) before the first genre slice

    (worlds made with the level editor become walkable), or after it (the first slice uses box and mesh colliders)?



\---



## 8. Risks



\- **Scope.** Seven reference tools is more than a small team can match. Mitigation: ship the *workflow loop* of each

  (make, edit, play, export), not feature parity; keep the editability audit as the quality bar.

\- **Unverified base.** Mitigation: R0.

\- **Performance.** Large terrain, scatter and many RTS units stress the LibGDX renderer and Jolt. Mitigation: R9

  baselines before R3, and instancing and LOD designed in, not added later. AGENTS.md §7 keeps rendering on LibGDX

  facilities; a different backend needs a feasibility study.

\- **Two backends.** Java desktop and Babylon web already drift (the web player ignores mesh colliders silently).

  Mitigation: the support matrix in R9 and a rule that no feature is "done" without its web status written down.

\- **Genre x dimension explosion (R3b).** Eleven genres times three modes is thirty-three combinations. Mitigation: a

  registry, one tested slice at a time, and a rule that only combinations whose tests pass are shown as playable.

\- **AI quality and trust.** Model output varies and small local models are weak at multi-step design. Mitigation:

  tools that validate every argument, plan preview, one-step undo, the self-checking loop, the R10-F suite that

  reports honestly where each model fails, and templates so the assistant customizes instead of inventing.

\- **AI safety.** An assistant that can write scripts and touch files is a risk surface (TD-019 code runs in-process).

  Mitigation: the R10 safety rules; scripts reviewed before they run; project-folder-only tools.

\- **Single-person bus factor.** Mitigation: reports and TDs per phase; the checklists in

  [visual-check-checklist.md](reports/visual-check-checklist.md) kept current.



\---



## 9. What this document does not do



It does not start any milestone, change code, approve a decision, or claim any listed item exists unless §3 says so.

Sizes are guesses. The "not found in the code" list is from a keyword search, not a full audit; Phase R0 and R1

confirm it.



No commit, push or other Git operation was performed in producing this file.


---

## Appendix A — Downloadable open-content 3D assets and migration of current samples (added 14 November 2026)

**Purpose.** Give Windsurf authoritative starting URLs to find, download, verify, import and use realistic **editable** assets, and upgrade existing ForgeRoom samples. This appendix is a **plan and source catalog**, not a claim that downloads or repository modifications have occurred. **CC0/open-content assets are not necessarily open-source software.** A freely downloadable GLB may support scene transforms/material overrides but not full vertex/face editing until R7a is implemented. Source mesh formats (e.g. `.blend`, `.obj`, `.fbx`, `.gltf` plus textures) may improve authoring but are not guaranteed for every listing.

### A1. Source catalog (official URLs; verify each actual asset before acquisition)

| Source | Official entry point | Intended use | Licensing / download gate |
|---|---|---|---|
| **Poly Haven — 3D models** | https://polyhaven.com/models | Photorealistic trees, boulders, natural props and buildings **where present** | Provider publishes CC0 assets. Select actual models through catalog; verify availability, formats, mesh and textures. |
| **Poly Haven — API and documentation** | https://api.polyhaven.com ; https://polyhaven.com/our-api ; https://github.com/Poly-Haven/Public-API | Automated catalog search, metadata, official download URLs and provenance | Live API terms apply independently of asset CC0: unique User-Agent, provider credit/branding where required, responsible requests. Read https://github.com/Poly-Haven/Public-API/blob/master/ToS.md before integration. |
| **ambientCG** | https://ambientcg.com/ | CC0 PBR ground, stone, bark, roof and wall materials; models if appropriate | Verify chosen asset page, available files and usage conditions; **textures do not replace editable house geometry**. |
| **Kenney — assets** | https://kenney.nl/assets | Lightweight CC0 3D nature/building packs and baseline performance samples | Verify **individual pack** license and format; generally stylized rather than photorealistic. |
| **Khronos glTF Sample Assets** | https://github.com/KhronosGroup/glTF-Sample-Assets ; https://github.khronos.org/glTF-Assets/ | Regression fixtures for PBR, texture, alpha, hierarchy, animation and import/export | **Mixed per-model licenses**; not all are CC0. Check each model directory README, all third-party textures, attribution and restrictions. Only ship verified redistribution-compatible fixtures. |
| **Blender** | https://www.blender.org/ | Optional open-source mesh editing/conversion/inspection tool for source assets | GPL software, not an asset library. ForgeRoom must still support its own in-editor mesh-edit workflow under R7a. |

**Do not assume** a source has a particular oak, pine, cottage or house until its current catalog entry is found. Never construct guessed CDN/download URLs. Do not bulk-clone the Khronos repository into production assets or automatically treat its entire contents as CC0. Exclude any asset with unclear licensing or embedded non-redistributable textures.

### A2. Starter collection to curate

1. **Terrain and ground:** 2–3 CC0 grass/dirt/rock PBR materials from ambientCG or Poly Haven; configure realistic material previews and terrain paint layers.
2. **Vegetation:** 1–2 realistic CC0 tree models, one bush/plant where available; test alpha-masked foliage, shadows, transforms, collision proxies, and low-detail options.
3. **Geology:** 1 realistic CC0 boulder and one rock cluster; test scale, rotation, collision and material overrides.
4. **Architecture:** 1 CC0 house/building model **only after an actual redistributable listing is verified**. Record whether the model includes editable geometry and source format; create a derived project-owned model for R7a house editing.
5. **Character:** use an existing legally distributable character sample or a separately verified open-content model; do not infer animation/rigging from mesh availability.
6. **Import regression fixtures:** choose individually license-cleared Khronos glTF assets for geometry, PBR, texture and animation checks, stored separately from default user-facing art.

### A3. Exact Windsurf procedure: source → manifest → import → sample upgrade

**Discovery and licensing**

- Inspect current `assets/samples` (roadmap baseline: 36 scenes) and `assets/graphs/samples` (15 graphs); inventory the actual files, referenced models, generators and current preview screenshots. Do not assume every scene needs replacement.
- Query Poly Haven's official API with a unique ForgeRoom User-Agent, read catalog metadata, fetch official file descriptors, and select a **small** collection. Inspect ambientCG and Kenney official downloads as needed. Capture asset page, original URL, provider, author if supplied, license text/URL, retrieval date, SHA-256, formats, size, dependencies and source revision.
- Reject unverified or incompatible assets; no automatic licensing guesses. Download only the approved curated list. Keep network failures recoverable and offline cache functional.

**Import and editability**

- Prefer glTF 2.0/GLB for runtime interchange. Preserve separate authoring source when legally and technically available. Validate textures, alpha cutout, normals, PBR channels, orientation, units, scale, mesh hierarchy and renderer extension support.
- Register the asset as a prefab/model with a thumbnail, searchable category and manifest; keep the downloaded original immutable. Store edits as a **derived model** or project-owned prefab override. Never overwrite upstream source files.
- Distinguish **instance editing** (place/move/rotate/scale/material overrides) from **mesh editing** (vertex/edge/face/extrude/roof changes). The latter is an R7a acceptance requirement; a GLB import alone does not satisfy it.

**Sample migration**

- Create `docs/reports/sample-asset-migration-inventory.md` with one row per scene: current asset(s), proposed replacement, source URL, license, conversion, dependencies, expected visual result, editability level, collision and rollback path.
- Start with a **single opt-in sample** (e.g. a landscape scene identified during audit): convert generated terrain to an editable `.terrain` asset where R3 supports it; replace primitive tree and rock entities with licensed realistic prefabs; retain transforms and gameplay behavior where feasible.
- Test terrain brush editing, multiple instances, selection, undo/redo, scene save/reopen, offline rendering, physics collisions and Play/Stop isolation. Record before/after screenshots, frame times and memory usage.
- Only then migrate additional samples in small batches. Preserve original sample versions or provide reversible migrations; never destructively rewrite all 36 scenes or remove procedural primitives. Samples depending on missing R2/R3/R7a features remain marked **partial** rather than claimed complete.
- Include a **Weskaap-style showcase**: sculpt a valley, paint terrain, place realistic boulders/trees, add player, choose Action RPG + 3D, Play, Stop, place a house, open 3D Modeling, modify roof/wall geometry as a derived asset, return to World and Play again. Explicitly mark steps blocked by unfinished profile/terrain collision/mesh editing.

**Suggested manifest fields:** `id`, `displayName`, `category`, `provider`, `sourcePageUrl`, `downloadUrl` (official verified URL only), `licenseId`, `licenseUrl`, `author`, `retrievedAt`, `sha256`, `sourceFormat`, `runtimeFormat`, `originalPath`, `derivedPath`, `thumbnailPath`, `dependencies`, `editableCapabilities`, `collisionProfile`, `importerVersion`.

### A4. Verification and release gates

- Automated tests: import/round-trip, material mapping, alpha-masked foliage, missing textures, malformed files, deterministic manifests, checksum verification, prefab instancing, undo/redo, cache reuse, offline loading, and scene backward compatibility.
- Manual graphics checks: realistic appearance under lighting/shadows, foliage transparency, correct physical scale, terrain contact, and playable performance on modest hardware.
- Legal/provenance checks: each **shipped** model and texture has recorded source and license; API UI credit requirements met; no mixed-license Khronos asset silently included; source and derivative ownership are distinguishable.
- Editor UX checks: default World View stays uncluttered; **Objects → Trees/Rocks/Buildings → Place**; selecting a house offers **Edit 3D Model** only when supported, otherwise a clear explanation.
- Delivery: curated source manifest, reproducible fetch/import command or editor flow, migration inventory, test report, before/after screenshots, and support matrix for desktop and web. No download, commit, push, module creation or roadmap phase is approved by this document alone.

### A5. Windsurf execution instruction (copy into a task after approval)

> Read `docs/13-nov-forgeroom-roadmap.md` (or the owner's revised roadmap), especially R0, R1, R1b, R2, R2b, R3, R3b, R3c, R7a and Appendix A. Audit the real repository and produce a per-sample migration inventory first. Use only verified CC0 source assets from the official links above for the default redistributable collection; use Khronos assets only after individual license checks. Implement one realistic tree + boulder + ground material import, prefab placement and an opt-in landscape sample refresh, with provenance, undo/redo, save/load, offline cache, tests and visual evidence. Preserve existing primitive samples, report blockers honestly, and do not commit/push or expand scope without explicit approval.


---

## Appendix B — Curated 2D and 2.5D CC0 asset sources, editable samples and dimension parity (14 November 2026)

**Purpose.** Extend Appendix A beyond realistic 3D assets so the ForgeRoom editor ships credible **editable 2D, 2.5D and 3D sample projects**, with consistent workflows and genre/game-mode testing. This is a source-and-implementation proposal, not proof that any files were downloaded or changed. CC0 is an open-content dedication; a PNG is not automatically an editable layered drawing, and a 3D mesh is not automatically editable at vertex level.

### B1. Verified starting links for 2D content

| Source and link | Asset type and sample use | License and editability checks |
|---|---|---|
| [Kenney — New Platformer Pack](https://kenney.nl/assets/new-platformer-pack) | 2D platformer tiles, characters, interface; first playable 2D sample | Pack page states **CC0**; inspect included PNG/spritesheet layout and optional source files. |
| [Kenney — Pixel Platformer](https://kenney.nl/assets/pixel-platformer) | Pixel-art 2D platformer scene and tile painting | Pack page states **CC0**; verify tile size and animation frames. |
| [Kenney — Platformer Pack Remastered](https://kenney.nl/assets/platformer-pack-remastered) | Alternative 2D tiles and characters; sample visual-quality comparison | Pack page states **CC0**; check consistency with other chosen packs. |
| [Kenney — Brick Pack](https://kenney.nl/assets/brick-pack) | Editable 2D building/tile assembly | Pack page states **CC0**; individual bricks are editable scene/tile elements, not necessarily layered source artwork. |
| [OpenGameArt — Sunny Grassland Sprites](https://opengameart.org/content/2d-sunny-grassland-sprites-platformer-sidescroller) | Trees, stones, platforms and scenery for a side-scrolling example | Listing states **CC0**; download only linked files and record creator/source. |
| [OpenGameArt — Dark Knight 2D Character Sprites](https://opengameart.org/content/dark-knight-2d-character-sprites) | Animated 2D character, walk/jump/attack testing | Listing states **CC0**, describes multiple animation clips; confirm exact frame layouts before import. |
| [OpenGameArt — Dungeon Pack](https://opengameart.org/content/dungeon-pack) | Top-down 2D RPG sample, characters, enemies, tiles, props | Listing states **CC0**; inspect actual package, formats and animation mapping. |
| [OpenGameArt — 2D World Sprites](https://opengameart.org/content/2d-world-sprites) | Simple terrain and vegetation sprites for baseline regression | Listing states **CC0**; deliberately lightweight, not a photorealistic benchmark. |
| [itch.io — free CC0 2D asset catalog](https://itch.io/game-assets/free/tag-2d/tag-cc0) | Discovery of additional art styles and genres | **Discovery only**; check each creator's actual pack license, download method and redistribution permission before use. |

Do not scrape restricted pages, invent direct download URLs or assume the license of a whole catalog. Prefer official publisher download links and deterministic manifests. Record actual files, author, URL, license, download date and SHA-256 for every included asset. If a page changes or licensing is uncertain, skip that asset and report it.

### B2. 2.5D is a gameplay mode, not a special universal asset format

**2.5D side-scroller:** use 3D GLB scenery/buildings/terrain from the verified CC0 providers in Appendix A, but lock gameplay movement to a plane and use a side camera; optionally layer CC0 2D sprites for characters, particles, parallax or UI. Test depth sorting, transparency, camera clipping, collision proxies and scale.

**2.5D isometric RPG/strategy:** use verified CC0 3D props rendered from an orthographic/isometric camera, or verified CC0 isometric sprite/tile sets where available. Do not label ordinary top-down sprites as isometric unless the asset geometry and camera/placement rules match. Keep placement, tile grid, elevation and navigation editable.

**2.5D hybrid:** allow 2D character sprites in a 3D scene with billboarding or directional sprite selection, with explicit collision and animation configuration. Source art and 3D models retain separate manifests and editability metadata.

The **game-mode selector** determines camera, controls, movement constraints, physics and rendering behavior. Switching a world between modes must validate compatibility; it cannot magically transform a 2D sprite into a rigged 3D character or supply missing gameplay rules.

### B3. Required import and editing capabilities by dimension

| Dimension | Source assets | Editing inside ForgeRoom | Sample acceptance |
|---|---|---|---|
| **2D** | PNG spritesheets, tilesets, textures, optional source art | Sprite cropping/slicing, tile painting, layers, collision shapes, animation frames, placement, material/tint edits; layered pixel/vector edits only when implemented | Edit platforms, move trees/rocks, replace a character animation, Play/Stop, save/reopen/export |
| **2.5D** | CC0 GLB props/terrain plus optional CC0 sprite layers | Sculpt 3D terrain, place 3D boulders/buildings, adjust side/isometric camera, edit sprite layers and collision plane; house mesh editing through R7a | Edit slopes, place player, run/jump or isometric navigate, Stop, edit building, Play again |
| **3D** | CC0 GLB/glTF models and PBR materials | Terrain sculpt, prefab placement, material overrides, derived mesh editing through R7a | Build valley, place character and house, Action RPG + 3D, Play/Stop/edit/replay |

**Source editability vs editor editability:** imported PNGs may be rearranged, sliced, animated and painted over even if layered source art is absent; imported GLBs may be transformed and material-edited even if no original `.blend` is supplied. Mark mesh, pixel, layer, UV and animation editing as independently supported/partial/unsupported. Keep immutable originals and save user edits as derived assets or overrides.

### B4. Windsurf migration and new sample projects

1. **Inventory first:** inspect actual `assets/samples`, sprite/tile samples and graphs, document which are 2D/2.5D/3D, their licenses, source formats, editor openings, existing behavior, dependencies and current test status. Do not presume every baseline sample is broken or should be replaced.
2. **Curate three small CC0 starter sets:** (a) 2D platformer with terrain tiles, character animation, scenery and UI; (b) 2.5D side-scroller with realistic GLB tree/boulder/building plus optional 2D sprites; (c) 3D action RPG with realistic terrain materials, props and character whose license is verified. Each has a manifest and offline cache.
3. **Implement/import in the correct workspace:** World remains the default simple interface; Objects/Terrain/Characters palettes show mode-compatible content. Sprites and Tiles workspaces provide detailed 2D editing; 3D Modeling handles derived mesh edits; Animation handles sprite and skeletal animation. Advanced panels are collapsible.
4. **Upgrade existing samples conservatively:** migrate one existing 2D sample and one existing 2.5D sample **opt-in**, preserve originals and IDs/references where possible, verify before/after behavior and visuals, then extend only after tests. If the needed source asset or editing subsystem is absent, record a blocker rather than shipping an uneditable facade.
5. **Create reference projects:** `2d-platformer-edit-play`, `25d-side-scroller-edit-play`, `25d-isometric-rpg-edit-play` (when profile ready), and `3d-action-rpg-world-edit-play`. Use actual repository naming conventions after audit; these names are proposed examples, not existing paths.
6. **End-to-end test:** open World → edit terrain/tilemap → place scenery/character → choose Genre + Game Mode → Play → Stop → select building/object → open appropriate Sprite/Tile/3D workspace → modify underlying editable asset or derived copy → return to World → Play again → save/reopen/export. For 2D use a layered house/tile building; for 2.5D use a 3D house mesh or isometric sprite building as appropriate.
7. **Verify:** license manifest, reproducible downloads, cache/offline load, import/round-trip, scene backward compatibility, animation playback, physics and collision, Play isolation, asset editability, export, visual screenshots and performance on modest hardware. Track desktop vs web feature parity honestly.

### B5. Dependencies and approval gates

- **R1/R1b:** samples editable and UI simplified across dimensions.
- **R2/R2b:** materials and CC0 import pipeline must support both sprites/tiles and GLB/PBR content; the 2D catalog is an extension of R2b, not an unapproved extra engine rewrite.
- **R3/R3b/R3c:** terrain/tile editing, collisions, profile registry and full edit-play-stop-edit workflow for 2D, 2.5D and 3D.
- **R5/R7a/R9:** sprite/skeletal animation, in-editor 3D building edits and import/export/performance parity.
- **R10 FINAL:** only after previous milestones' acceptance, integrate the provider-neutral **LLM and agent harness** for prompt-driven game creation in major genres, with structured plans, user approval, tools, safety checks, undo, test-and-repair, and export. A genre/mode pair is advertised as working only after end-to-end acceptance tests pass.

**Additional owner decision:** approve the curated 2D CC0 source list and the three-dimensional sample parity acceptance criteria; confirm whether the LLM harness will run locally in desktop ForgeRoom, optionally in UrConnex, or both through a documented protocol. No choice is assumed by this proposal.

### B6. Windsurf instruction after explicit approval

> Read the revised ForgeRoom roadmap, especially Appendix A and Appendix B. Audit current 2D, 2.5D and 3D samples and their actual licensing. Create a per-sample migration matrix and curated CC0 asset manifest from official links. Deliver one fully editable 2D platformer sample, one 2.5D side-scroller sample and one 3D Action RPG sample incrementally, reusing current systems and preserving originals. Ensure World → edit → Genre/Mode → Play → Stop → edit source/derived asset in its workspace → Play again works and is verified, with offline assets, tests, screenshots and export. Keep the LLM plus agent-harness rollout as **R10, the final phase**, after manual authoring and all earlier gates are complete. Do not claim unsupported genre combinations work. Do not create modules, download large packs, modify samples or commit/push without the approvals required by AGENTS.md.


---

## Appendix C — Open-content source assets and editable sample upgrades for 3D Modeling, 2D Modeling and Audio (14 November 2026)

**Purpose.** Extend Appendices A (3D world assets) and B (2D/2.5D gameplay assets) to the **authoring workspaces themselves**. The acceptance criterion is not merely that a sample can be imported and played: the user must be able to **open the sample in the appropriate ForgeRoom workspace, change it, save it, and use the modified result in a game**. This appendix is a proposed plan; it does not authorize downloads, changes to samples, module creation or commits.

### C1. License and editability contract

- **CC0** is preferred for redistributable art, mesh and sound assets. It is an open-content/public-domain dedication, not a source-code license. Open-source *applications* (e.g., Blender, Krita, Audacity) have separate software licenses; their GPL code must not be copied into ForgeRoom without an architectural/legal review.
- An **editable 3D source** is typically `.blend` or a project-owned editable mesh with topology/material data. A `.glb` can contain editable geometry after a validated conversion, but is not a native `.blend` editing project. Preserve the downloaded original and make edits to a **derived, project-owned asset**.
- An **editable 2D source** may be `.svg` (vector paths), `.ora` (OpenRaster layers), `.aseprite`/`.ase` (where compatible), or a ForgeRoom native sprite/layer/tile project. A PNG is editable at the pixel level but **does not preserve original vector paths or layers**.
- An **editable audio source** is a ForgeRoom `.song` arrangement, a MIDI file plus licensed instrument assets, or a project-owned multitrack session. A WAV/OGG can be cut, trimmed and processed, but **does not reveal the original MIDI notes, tracks or synthesizer settings**.
- For each downloaded file store the source page, original filename, provider/author, exact license, retrieval date, SHA-256, original format, converted format, import tool/version and a reversible provenance link. Preserve originals; never overwrite provider downloads. Verify each individual item before shipping, even if the catalog is normally CC0. Do not bundle arbitrary GPL program code, proprietary soundfonts, paid packs or mixed-license sample libraries.

### C2. Official sample and reference sources for Windsurf

| Workspace | Source | Link | What Windsurf should use it for | Licensing and source caveat |
|---|---|---|---|---|
| **3D Modeling** | Blender official demo files | https://www.blender.org/download/demo-files/ | Select explicitly labeled **CC0** `.blend` examples (e.g., **Cube Diorama**, **Human Base Meshes**, or CC0 geometry-nodes examples) for topology, material and conversion test fixtures. | **Mixed licenses** across the page. Download only files explicitly labeled CC0, and verify each individual demo's included license and dependencies. Complex Blender nodes may not survive GLB export. |
| **3D Modeling** | Blender Geometry Nodes CC0 examples on the same official demo page | https://www.blender.org/download/demo-files/ | CC0 **Tree, leaves and grass**, **Flower Scattering**, **Scattering Pebbles** examples for studying editable vegetation generation and export/baking limits. | These are Blender-specific procedural graphs, not directly editable ForgeRoom nodes; use exported/baked mesh plus retained source where feasible. |
| **3D Modeling** | Kenney free 3D kits | https://kenney.nl/assets | Lightweight modular house/structure/prop models for mesh conversion, editing and prefab regression. | Kenney confirms game assets on its asset pages are **CC0**; verify selected pack and whether it actually includes a source mesh or only interchange formats. |
| **3D Modeling** | Poly Haven models | https://polyhaven.com/models | Photorealistic building, rock, foliage and props for import and derived mesh editing. | CC0 assets; model topology may be unsuitable for easy editing. Check file types and performance; do not imply original Blender project is available for every model. |
| **2D Modeling** | Kenney free 2D and UI packs | https://kenney.nl/assets | Sprite pixel editing, tile-map creation, layer assembly, sprite-sheet slicing, palette and animation tests. | CC0 on Kenney asset pages; verify each pack's supplied PNG/SVG/source files. |
| **2D Modeling** | OpenGameArt CC0 discovery | https://opengameart.org/ | Curate individual CC0 sprites, tiles and backgrounds with identifiable source and creator for the sprite editor. | **Mixed-license site**; do not assume all content is CC0. Verify each listing and its actual downloadable files. |
| **2D Modeling** | OpenRaster format | https://www.openraster.org/ | Format reference for layered image import/export and round-trip tests, using ForgeRoom-created CC0/original fixtures. | A **format reference**, not an asset library; do not claim external layered sample artwork is licensed by the format. |
| **Audio Editing** | Kenney free audio packs | https://kenney.nl/assets | Short SFX and loops for waveform editing, trimming, fade, normalization, looping and scene trigger tests. | Kenney asset-page CC0; inspect each selected pack and source audio format. |
| **Audio Editing** | OpenGameArt CC0 sound effects collection | https://opengameart.org/content/cc0-sound-effects | Discover impact, footstep, UI, ambience and fantasy SFX for the sound editor and game samples. | A curated **collection** of links, not a single license guarantee for every linked file; inspect each individual listing. |
| **Audio Editing** | Freesound | https://freesound.org/ | Optional CC0-only discovery for natural ambience and Foley recordings. | **Mixed licenses**; require a verified CC0 license on each individual sound and compliant access/download terms. Do not scrape or bulk download. |
| **Audio Editing** | Surge XT | https://github.com/surge-synthesizer/surge | **Reference software only** for synth features, presets and sound design workflow; use ForgeRoom-original music/synth examples instead of bundling code or factory content. | Open-source software (GPL-family obligations), **not** a blanket CC0 audio sample library. No direct code copying or unreviewed preset redistribution. |

**Source selection priority:** official CC0 asset pack or explicit CC0 demo → inspect original file and license → small pinned download → derive ForgeRoom editable format → add provenance → test editor → update one sample → measure/verify → expand only after approval. Do not invent a direct file download URL when the provider requires discovery or a click-through.

### C3. 3D Modeling workspace: sample upgrades and required tools

**Initial sample:** a small **editable house** assembled from a verified CC0 Kenney modular structure or a project-owned house built from primitive meshes, plus a CC0 Blender demo object for import/conversion testing. A realistic Poly Haven model is an additional stress test, not the first mesh-editing fixture.

1. In **World**, select a house → **Edit 3D Model**. Open a dedicated, uncluttered 3D Modeling workspace showing the chosen object, hierarchy, material slots and essential tools only.
2. Make an editable **derived mesh** with explicit vertex/edge/face topology. Support select, move, extrude, inset, basic bevel, assign material, undo/redo and save. Preserve normals, UVs and transform semantics or report unsupported cases before conversion.
3. Distinguish **Edit Instance**, **Edit Shared Prefab**, and **Create Unique Mesh**. Default to a new project-owned derived asset; warn before changing all instances.
4. Return to World with selection and placement retained. Regenerate render buffers/collision where required; verify the changed roof/wall in Play and after save/reopen/export.
5. Ship the sample with the **original source**, a manifest, a derived editable project asset, and a deterministic operation script/test. A proprietary or mixed-license demo is excluded from redistribution.

**Done when:** a user changes a house roof or wall entirely in ForgeRoom, sees the update in the world and gameplay, and can undo, save, reload and export without data loss.

### C4. 2D Modeling workspace: sprite, vector, tiles and animation

**Initial samples:** a CC0 Kenney 2D sprite pack and a **ForgeRoom-original layered character/house/terrain sprite**. Build a project-owned source asset for testing layers and vector shapes; do not assert that a downloaded flattened PNG has those layers.

1. Select a sprite/tile in World or Content Browser → **Edit 2D Asset**; open a simplified **2D Modeling** workspace.
2. Support pixel brush, eraser, fill, palette, selection, move/scale/rotate, layers and transparency; use existing sprite/tile tools before inventing another renderer.
3. Add **vector shape/path editing** (SVG subset) as a separate capability with explicit supported elements and fidelity tests. For layered art, prefer a native source document and consider OpenRaster import/export only after a TD and format audit.
4. Provide sprite-sheet slicing, animation frame ordering, pivot/hitbox editing, tile collision and atlas regeneration. Editing a sprite must update its prefab/tile usages without corrupting references.
5. Maintain separate source files and exported PNG/atlas artifacts. Editing an exported PNG does not recreate missing source layers or vector geometry.

**Done when:** the user changes the roof color and window shape of a 2D house, edits an animated character frame, returns to the 2D/2.5D world, plays, stops and saves/reopens with the changes intact.

### C5. Audio workspace: editable music and sound effects

**Initial samples:** a few verified CC0 Kenney SFX and individual verified CC0 ambience sounds, plus a **ForgeRoom-original `.song`** with notes, instruments, mixer settings and automation (source-editable music fixture).

1. **Sound Effects**: open WAV/OGG (as supported), display waveform, trim/split, fade, gain/normalize, loop, preview, undo and export a **derived** WAV/OGG. Preserve the original downloaded audio. Test clicks, clipping and latency.
2. **Music**: open `.song` in Music Studio (not the scene loader), edit piano-roll notes, instruments, tempo, patterns, tracks, mixer and automation; save/reopen and render/export. A recording is not converted to MIDI notes unless an explicitly scoped transcription feature is implemented and tested.
3. **Scene integration**: assign SFX to a character footstep, a building door, or an action RPG interaction; assign background music/ambience and verify Play/Stop isolation and audio cleanup.
4. **Source and license**: keep CC0 sound manifest, provider credits/provenance and any derived sound metadata. Do not include copyrighted commercial music, proprietary soundfonts, or mixed-license Freesound recordings in the default redistributable examples.
5. **Acceptance**: test playback by ear as well as automated waveform, serialization, export and file-routing tests. Do not mark listening validation complete in a headless run.

**Done when:** a user edits a door-open SFX, changes a music phrase in the piano roll, attaches both to a game scene, plays, stops, saves/reopens and exports without broken references.

### C6. Sample migration matrix and execution order

Before downloading anything, Windsurf must inventory the existing sample scenes, models, sprites, audio and `.song` files. Record **existing sample ID/path**, original asset/license, desired replacement, target workspace, editable source format, export format, runtime support (2D/2.5D/3D), expected memory/performance, tests and rollback. Do not replace all samples at once.

**Vertical slices in order:**

1. **3D**: one editable house mesh → World → Edit 3D Model → change roof → World → Action RPG/3D Play → Stop → reopen/export.
2. **2D**: one editable layered/vector/pixel house or character → Edit 2D Asset → return to 2D platformer scene → Play/Stop → reopen/export.
3. **2.5D**: a hybrid sample with 3D scenery and 2D sprite(s), each edited in its correct workspace; validate camera plane, transparency, depth sorting and physics.
4. **Audio**: one CC0 door SFX and one original editable `.song` → edit in Audio/Music → bind to 2D/2.5D/3D sample → Play/Stop → reopen/export.
5. **Regression**: preserve original geometric/placeholder samples as separate fixtures; opt-in migration, screenshots, asset manifests, load/undo/redo/collision/performance and export tests.

**UI rule:** World is the default workspace. Selecting an object offers **Edit 3D Model**, **Edit 2D Asset**, **Edit Material**, **Edit Animation**, or **Edit Audio** only when applicable. Each workspace shows contextual tools; advanced panels remain discoverable but collapsed. Returning to World restores selection, camera and editing context.

**Dependencies:** R0/R1/R1b for reliability and UI; R2/R2b for materials/assets; R3/R3b/R3c for terrain and Play/Stop; R7/R7a for 3D geometry; 2D tools (TD-066) for sprite editing; R8 for sound/music; R9 for formats and export. New asset types, modules and serialization changes require TDs and owner approval under AGENTS.md.

**Final phase remains R10.** Only after these manual authoring and gameplay/export acceptance gates pass may the LLM + agent harness be rolled out for prompt-to-game creation. R10 must expose validated tools for 3D mesh editing, 2D art editing, music composition, SFX editing, asset acquisition, genre/game-mode setup, Play/Stop and export. No claim of universal genre support without tested templates.

### C7. Windsurf task prompt — execute only after owner approval

> Read the revised ForgeRoom roadmap, Appendices A–C, and existing AGENTS.md/TDs. First audit the real 3D Modeling, 2D sprite/tile/vector and Music/SFX workspaces and current samples. Produce `docs/reports/authoring-sample-source-audit.md` listing every candidate download URL, exact license, editable source format, required conversion and sample migration target. Prefer official CC0 sources: Blender explicitly CC0 demo files, Kenney free CC0 packs, Poly Haven CC0 models, and individually verified CC0 OpenGameArt/Freesound sounds. Preserve originals and add provenance/hashes. After explicit approval, implement one minimal fully editable sample in each workspace: house mesh, layered/pixel sprite, original `.song`, derived CC0 sound effect; connect each to World → Play → Stop → edit → Play and verify save/reopen/export, offline behavior, undo and visual/listening acceptance. Keep UI contextual and simple, preserve existing samples, respect TD approval gates, and do not commit or push without authorization. R10 LLM/harness is last, not part of this implementation task.

---

## Appendix D — Real instrument library, simple Audio/Music UI, and sample migration (2026-11-14)

**Status: proposal, not authorization to download or implement.** This appendix extends R8 and the asset provenance rules in Appendices B–C. R10 (LLM + agent harness) remains strictly the final phase, after manual music, sound, editing, Play/Stop and export acceptance.

### D1. Product goal

A ForgeRoom user should be able to choose **Music → New Song**, click **Add Instrument**, select **Piano**, **Acoustic Guitar**, **Bass**, **Violin**, **Flute**, **Trumpet**, or **Drum Kit**, and hear a recognizable sampled *real instrument* while entering notes in a piano roll or recording MIDI. The user can trim and fade audio recordings in **Audio**, attach the finished music/SFX to a scene, press Play, Stop, edit, and Play again. Synthesized sounds remain available, but the default instrument experience must not be restricted to synthetic oscillators.

**Terminology:** SoundFont (SF2/SF3) and SFZ are sample-based instrument formats. A SoundFont or SFZ instrument is editable at the note/track level in ForgeRoom; its source recordings are not magically editable as separate performances. The underlying WAV samples and SFZ mappings can be edited only when ForgeRoom actually provides those operations. Do not confuse an open-source sampler *engine* with the license of the *instrument recordings*.

### D2. Approved candidate sources — verify exact file licenses before downloading

| Source and official link | License / conditions | Instruments and usage | Download policy |
|---|---|---|---|
| [FluidR3 / fluid-soundfont](https://github.com/pianobooster/fluid-soundfont) | MIT; retain license/copyright notices | General MIDI starter bank: pianos, guitars, bass, orchestral sounds, percussion | **First candidate** for a compact, broad instrument library. Check official release/package URLs, SHA-256 and exact license. |
| [VSCO 2 Community Edition](https://versilian-studios.com/vsco-community/) and [source repository](https://github.com/sgossner/VSCO-2-CE) | CC0 for Community Edition samples | Recorded orchestral strings, brass, winds and percussion | Start with its smaller 50- or 256-sample pack if directly available; full library is ~3 GB and must be optional. Do not conflate with the paid Professional Edition. |
| [Versilian Community Sample Library (VCSL)](https://github.com/sgossner/VCSL) | Candidate CC0; verify per package | Additional real-instrument samples including keys and winds | Optional downloadable expansion, not a mandatory installation. |
| [Karoryfer / Shinyguitar](https://github.com/sfzinstruments/karoryfer.shinyguitar) | Candidate CC0; verify source license | Sampled electric guitar | Optional guitar pack, preserve SFZ mapping and audio samples. |
| [SFZ Instruments catalog](https://sfzinstruments.github.io/) | **Mixed licenses**; verify each entry | Discover piano, guitar, bass, orchestral and drum sample sets | Discovery only; no bulk downloads or automatic approval. |
| [Salamander Grand Piano](https://sfzinstruments.github.io/pianos/salamander/) | **CC BY 3.0**, attribution required, **not CC0** | High-quality recorded grand piano | Optional, separately approved pack with attribution; not part of a strict CC0-only default set. |

**Exclusions:** No paid, non-commercial, non-redistributable or ambiguously licensed samples in the default library. Do not assume a GitHub repository license covers every bundled sample. Verify sample licenses and any required attribution independently. Prefer provider-hosted releases and documented endpoints; do not scrape download pages, bypass download controls or fabricate direct URLs.

### D3. Minimum viable instrument set and phased downloads

**Tier 0 — offline starter (small):** one permissively licensed GM SoundFont, subject to source and size verification. Expose Piano, Guitar, Bass, Strings, Brass, Woodwind, Organ and Drums as simple categories; each must map to a tested instrument preset and actually produce sound. Download once at an explicit approved step, store in a cache, and permit optional redistribution only when license checks pass.

**Tier 1 — realistic instruments (optional):** VSCO 2 CE small pack (strings, brass, winds, percussion), then optional VCSL keys and Karoryfer guitars. Download on demand with progress, cancel, disk-space estimate, provenance and SHA-256; never auto-fetch gigabytes on startup.

**Tier 2 — advanced:** multiple articulations, velocity layers, round-robin samples, key switches, custom SFZ mappings, drum-kit editing, microphone variants and optional CC BY libraries with visible attribution.

### D4. Audio/Music UI — simplicity is an acceptance criterion

**Default Music workspace** shows only:

1. **Tracks** (left): instrument icon, name, mute, solo, volume; **+ Add Instrument**.
2. **Timeline / piano roll** (center): draw, move, resize and delete notes; playhead; snap and zoom.
3. **Transport** (top): Play, Stop, Record, tempo, save.
4. **Instrument picker** (one dialog): Piano, Guitar, Bass, Strings, Brass, Winds, Drums, Other; a small preset preview, **Use**, and **Get More Instruments**.

Hide mixer routing, sampler zone maps, envelope generators, advanced effects, MIDI routing, soundbank management and sample mapping under an optional **Advanced** panel. No permanent forest of toolbars, no duplicate controls, no separate setup wizard to add a piano. Remember the current track and selection when switching to World and back.

**Audio (SFX) workspace:** waveform, play/stop, trim, split, fade in/out, gain and loop; advanced processing behind a collapsed panel. Keep Music (notes, tracks and instruments) distinct from Audio (recordings and effects), while sharing the same asset library and playback engine.

### D5. Implementation and architecture

- Audit existing Music Studio (synths, drums, SoundFont, mixer, WAV export) and audio playback before introducing dependencies. Reuse working code; do not replace it wholesale.
- Inspect the current SoundFont/SFZ player and decide, via TD and approval, whether existing code supports SF2/SF3, SFZ or both. A compatible sample player must honor instrument bank/program, note, velocity, sustain, note-off, polyphony, looping and per-track volume. Test actual audio, not just parsed metadata.
- Define an `InstrumentAsset` manifest: ID, display name, category, provider, source URL, exact license, required notices, SHA-256, file format, preset/bank/program, installed/cache path and version.
- Use local relative asset references in editable `.song` projects, preserve portability, and distinguish project-bundled instruments from global cached instruments. Exported games must include or explicitly resolve required soundbanks in a license-compliant manner; no silent missing instruments.
- Add sample editing only to the extent actually implemented: audio waveforms for WAV, note/velocity editing for songs, and a later advanced SFZ sample-map editor if approved. Never promise full sample-level editing merely because an SF2 loads.
- Enforce download timeouts, size limits, checksum verification, path traversal protection, cache integrity, offline startup and clean missing-pack messaging. Record notices in the distributed package.
- Keep performance suitable for modest laptops: stream/limit large banks, unload unused instruments, cap polyphony, measure memory/latency, and avoid eager 3 GB loads.

### D6. Upgrade existing engine samples

Audit the existing `.song` examples and sample scenes. Preserve originals and add **opt-in migrated examples**:

- **Simple Piano Theme:** real sampled piano, editable notes and velocity; 8–16 bars.
- **Village Ambience:** flute or strings over a soft pad; editable tracks, with a separately editable ambience WAV.
- **Action RPG Combat Cue:** drums, bass and orchestral strings, editable patterns and tempo.
- **2D Platformer Theme:** bass, keyboard and drums; short looping editable `.song`.
- **2.5D Exploration Theme:** piano, guitar and strings; reusable for side-scrolling and isometric samples.

Bind at least one song and one sound effect into each 2D, 2.5D and 3D demonstration project. For each, verify: preview instrument, edit notes, undo/redo, save/reopen, offline playback, Play/Stop isolation, exported game playback and license manifest. Do not replace a sample's original music destructively. Mark manual listening as pending unless a person has actually heard the result.

### D7. Acceptance tests

1. A new user opens **Music**, chooses **Add Instrument → Piano**, draws four notes, presses Play and hears sampled piano without configuring a sampler.
2. The user switches to **Guitar**, **Strings** and **Drums** and hears distinguishable instrument samples, not the same oscillator with a different label.
3. User saves a `.song`, reopens it, modifies note velocity and tempo, and undo/redo works.
4. A 2D platformer, 2.5D side-scroller and 3D Action RPG sample each play music and SFX in runtime and exported builds, without requiring a network connection.
5. **Get More Instruments** installs an optional verified CC0 pack, displays download progress, supports cancellation, records the exact license and never overwrites a user's custom soundbank.
6. The default Music interface is usable with four primary regions and advanced controls collapsed; keyboard shortcuts and accessibility are checked manually.
7. The full test suite passes or pre-existing failures are documented; playback is manually listened to on a real device; no commits/pushes occur without authorization.

### D8. R8 milestone update and final-phase dependency

R8 is not complete until the simple real-instrument workflow and sample migration above pass. Audio/Music must be fully usable **manually** before R10 begins. R10 is still the final LLM + harness milestone: only after the manual editor, 2D/2.5D/3D genres, modeling, assets, audio, export and editability gates are accepted may the agent orchestrate **add instrument → compose notes → arrange tracks → bind music → Play → validate → export** using the same typed, undoable editor operations. An AI-generated song must remain editable by hand.

### D9. Copy-ready Windsurf task — audit first, no implementation without approval

> Read `docs/14-nov-forgeroom-roadmap-revised.md` Appendix D, R8, AGENTS.md and applicable TDs. Audit the current Music Studio, SoundFont playback, `.song` format, audio editor and shipped samples. Verify exact licenses and official download methods for FluidR3, VSCO 2 Community Edition, VCSL and optional Karoryfer instruments; do not assume GitHub repo licenses cover sample recordings. Produce `docs/reports/real-instrument-library-audit.md` with a source/download/license/size/format/compatibility table, a compact UI wireframe, dependency and TD changes, an explicit migration plan for existing `.song` and 2D/2.5D/3D samples, and headless plus manual listening acceptance criteria. Do not download, change modules, edit samples, commit or push until owner approval. After approval, implement the smallest end-to-end slice: one verified permissive SoundFont, simple Add Instrument → Piano → notes → Play workflow, save/reopen, scene binding and export. Keep advanced controls collapsed. Expand instruments and optional CC0 packs only after the first slice is verified. R10 LLM/harness remains the final phase.


---

## Appendix E — World-first UX, Customize + Play, and consistent workspace simplicity (14 November 2026)

### E1. Governing product experience and reference

The owner's supplied ForgeRoom screenshot is the **visual interaction reference**, not a requirement to copy its illustrative landscape artwork. ForgeRoom starts directly in a large, interactive **World** viewport, with minimal chrome. The editor is a creative tool first; expert controls are available without dominating the default experience. This appendix supersedes any earlier UI layout proposal that shows all advanced workspaces or inspectors permanently.

**Default layout, from top to bottom:**
1. Thin header: ForgeRoom/project name; Save, Undo/Redo, and Settings (with accessible labels/tooltips).
2. A compact context indicator such as **World / My Game**; avoid permanent 3D/2D/Animation/Audio/Logic tab clutter on the default screen. Those tools are available through **Customize**.
3. The **interactive** world viewport occupies most of the window. Do not use a static illustration as a substitute for the scene renderer.
4. Small contextual action strip: **Terrain**, **Objects**, **Buildings**, **Characters**. For 2D projects, substitute suitable categories such as **Tiles**, **Sprites**, **Objects**, **Characters**. Show only the currently relevant palette; no permanent large inspector, hierarchy or asset browser.
5. Persistent footer: independent **Game Genre** and **Game Mode** selectors (2D / 2.5D / 3D), followed by adjacent **Customize** and **Play** buttons. Play is the primary action; Customize is immediately next to it. On smaller screens, preserve both actions without hiding them behind a menu.

The genre/mode registry and supported-combination validation from R3b still apply. Unimplemented combinations must be visibly marked as planned, not silently launched. The genre selector must not imply that changing a label magically creates gameplay logic.

### E2. Customize: one entry point to editing workspaces

**Customize** is a gateway, not a separate duplicate editor. Clicking it opens a compact chooser (or the selected object's direct editing action) for:
- **World** — terrain sculpting, ground painting, placement and lighting presets.
- **3D Modeling** — mesh and material editing of selected 3D objects, with R7a's practical house-editing slice available before the full R7 modeling suite.
- **2D Studio** — sprites, tiles, vector/shape editing and frame animation.
- **Animation** — clips, rigging and state transitions.
- **Audio & Music** — realistic sampled instruments, piano roll, sound-effect trimming and mixing, using Appendix D's simple UI.
- **Logic** — approachable behaviors/quests first, advanced visual scripting on demand.
- **Assets** — searchable CC0 library, import, provenance, and asset management, only when needed.

The chooser should prioritize **Edit Selected [object]** where appropriate: select a house in World → Customize → **Edit House in 3D**; select a sprite → **Edit Sprite**; select a character → **Animate Character**; select an audio clip → **Edit Sound**. Users must also be able to enter a workspace with no selection. Each workspace has a prominent **Back to World** action and retains project, selection, unsaved changes and relevant camera context. Navigation must not discard or silently commit changes. Provide a clear prompt if a pending operation needs confirmation.

### E3. Play/Stop contract

- **Play** uses the current genre + dimension profile and an isolated runtime copy; the World/asset editing state is not mutated by simulation.
- Validate prerequisites (player/spawn, controls, camera, collision, required gameplay components); give concise actionable messages and offer safe fixes when possible.
- **Stop** returns to the same World/editor state, selection and undo history. Changes made in Play are discarded unless an explicit reviewed **Apply Changes** feature is separately approved.
- Users can customize an asset, return to World and Play again without manually reconnecting references.
- The same Play/Stop controls and rules apply to 2D, 2.5D and 3D, while camera/physics/control implementations remain dimension-specific.

### E4. End-to-end acceptance scenarios

**A. 3D Action RPG (Weskaap-style example, not coupled to the Weskaap repository):** Launch → sculpt a valley and slope → paint terrain → place licensed realistic trees and boulders → place a character prefab and spawn → select **Action RPG + 3D** → Play and move/collide on terrain → Stop → place a house → select house → Customize → 3D Modeling → edit its roof geometry or material on a project-owned editable copy → Back to World → Play → Save/reopen/export. The selected 3D action-RPG profile must be genuinely implemented and tested before this scenario is marked PASS; otherwise record BLOCKED with the missing gameplay capability.

**B. 2D Platformer:** Launch World → create tiles/platforms → place CC0 sprite character → select **Platformer + 2D** → Play with working camera, movement and collision → Stop → Customize → 2D Studio → change sprite/frame → return → Play → export.

**C. 2.5D Side-scroller:** Launch World → create an environment using compatible 2D/3D assets → place character → select **Platformer + 2.5D** → Play with side-view camera, depth constraint and collision → Stop → Customize → edit relevant model/sprite → return → Play → export.

**D. Audio:** From a sample scene, Customize → Audio & Music → choose an installed licensed sampled piano or guitar → enter/edit notes → Play audio → add an SFX to a scene event → Back to World → Play game → save/reopen/export. Offline playback and license manifest must pass.

All samples remain editable and are subject to the R1 editability audit. Imported flattened assets do not falsely claim to retain unavailable source layers, modifier histories or MIDI tracks. Preserve original sample versions during migration.

### E5. Cross-phase UX simplicity gate (mandatory)

Each milestone R0–R9 must include both automated tests and observed manual UX checks. A milestone is not accepted solely because the engine API works. Test at least:
1. **Discoverability:** a first-time user can identify the primary next action without documentation or a permanently open inspector.
2. **Low clutter:** no duplicate top/bottom toolbars, unrequested floating tool windows or permanently expanded advanced settings.
3. **Context:** selection offers the most relevant action; irrelevant controls are hidden or disabled with explanation.
4. **Consistency:** Save, Undo/Redo, Back to World, selection, keyboard access, tooltips and error feedback work alike across workspaces.
5. **Short path:** World → select object → Customize → edit → Back to World → Play uses a small, predictable number of steps; measure and record friction rather than asserting arbitrary click counts.
6. **Persistence:** save/reopen, offline assets, project-owned source copies, prefab references and runtime/editor isolation are verified.
7. **Accessibility:** keyboard focus order, readable contrast, tool labels, resizable panels and scaling are checked on a real desktop.
8. **Performance:** avoid blocking startup on asset downloads, sample loading or SoundFont initialization; advanced workspaces load on demand where practical.
9. **Honest status:** record manual validation as pending if no graphical application was run; never claim a screenshot-based mockup is an implemented UI.

### E6. Roadmap integration and sequence

- **R0:** baseline, Play isolation, save/load and undo correctness before UX migration.
- **R1 / R1b:** implement the reference World layout and Customize/Play footer; preserve existing features behind contextual workspaces. Audit existing navigation before removing anything.
- **R2 / R2b:** material editor and verified CC0 asset browser with optional downloads, source manifests and offline cache. Asset browsing appears contextually through Objects/Buildings/Characters or Customize → Assets.
- **R3:** editable terrain, collision and scattering. Terrain palette appears contextually in World.
- **R3b / R3c:** genre+dimension profiles and complete Play/Stop/edit/replay loop. The default World UI must not advertise unsupported gameplay profiles as ready.
- **R4–R6:** strategy, animation, effects, UI and behavior tools appear only in relevant workspaces/contextual palettes.
- **R7a / R7:** practical house mesh editing first; advanced modeling later. Preserve prefab/instance semantics and imported source attribution.
- **R8:** sampled instruments, Audio & Music and editable music examples, with advanced mixer/effects hidden by default.
- **R9:** packaging, cross-backend support matrix, project/source portability and UX/performance regression testing.
- **R10 — FINAL IMPLEMENTATION PHASE ONLY:** LLM + agent harness + prompt-to-working-game, **after manual workflows and genre slices are verified**. It uses the same typed, validated, undoable editor operations, supports plan preview/approval and bounded test/repair, and never bypasses license/privacy/security checks. AI should appear as a simple **Describe your game** entry point rather than a developer-centric dashboard. Major genres are enabled progressively by proven templates and tests; do not promise arbitrary complete games or unsupported genre/dimension combinations.

**Governance:** This appendix proposes design requirements, not implementation authorization. New module boundaries, scene formats or changes to existing TD decisions require the same explicit owner approval and technical decision records as the original roadmap. No automatic commit or push.

### E7. Copy-ready Windsurf audit and implementation instruction

> Read `docs/14-nov-forgeroom-roadmap-revised.md`, especially Appendix E, R1b, R3b/R3c, R7a, Appendix A–D, `AGENTS.md` and all applicable TDs. Treat the owner's World-first screenshot as the UX reference. Audit the actual UI composition, current workspace navigation, scene selection, object edit actions, PlayModeController, GameTestProfile support, save/undo and existing samples. Write `docs/reports/world-first-ux-audit.md` with a before/after layout diagram, a feature-preservation matrix, a supported genre/dimension matrix, risks, needed TDs, and a staged implementation plan. **Do not modify code, download assets, create modules, commit or push until explicit owner approval.** After approval, implement the smallest verified vertical slice: World viewport + contextual palette + adjacent Customize/Play buttons; select a house → Customize → open 3D workspace → Back to World; and Play/Stop scene isolation. Preserve all advanced features through contextual workspaces. Add headless regression tests and real desktop manual usability checks. Extend the same interaction model to 2D, 2.5D, Audio/Music, Animation and Logic. Do not mark any phase complete until its UX simplicity gate and end-to-end acceptance criteria pass. Keep R10 as the final LLM/harness phase.


---

## Appendix F — Every sample game is a fully editable project template (14 November 2026)

**Status: Proposed owner requirement; implementation still requires explicit approval and applicable technical decisions.** This appendix is binding on proposed acceptance criteria across R0–R9 once approved. It does not claim existing samples already comply.

### F1. Product promise and definition

**A sample game is not a read-only showcase, compiled demonstration, video, or hardcoded engine example. It is a complete, duplicable, playable, exportable, project-owned game that the user can customize through every relevant ForgeRoom workspace.** Opening a sample offers **Play Sample** or **Use as Template**. Choosing the latter creates a separate project with editable source content and no changes to the shipped original. A template must never depend on inaccessible internal-only sample code for its advertised gameplay.

"Fully editable" means **all authored game elements and their available source representations can be opened and changed with the corresponding editor**, with the limitations of imported flattened content stated explicitly. A GLB mesh is editable at mesh/material level only when the mesh tools exist; it does not recover the original Blender modifier stack. A WAV can be trimmed and processed but does not recover individual multitrack recordings; an SF2/SFZ preset does not automatically expose original performance stems. If an imported asset cannot be edited at the claimed level, include a licensed editable source equivalent, provide a project-owned editable replacement workflow, or mark the template **INCOMPLETE**. Never label a sample fully editable while any required capability is missing.

### F2. Required editing coverage for each sample

| Domain | User action and source requirement | Acceptance |
|---|---|---|
| World / scene | Select, add, remove, transform, group, rename entities; edit properties and prefab overrides | Save/reopen, undo/redo, Play/Stop isolation |
| Terrain / sculpting | Reshape hills, valleys, slopes, paths; paint terrain layers; edit scattering | Terrain data is native `.terrain`, not an immutable baked display mesh |
| 3D modeling | Select a building/prop → Customize → 3D Modeling; edit vertices/faces, shape, materials, UVs where implemented | Save project-owned mesh; update scene/prefab; no mutation of bundled original |
| 2D modeling | Open sprites, tiles, backgrounds, UI artwork and frame animations in 2D workspace | Editable layers/source supplied where relevant; PNG-only imports labeled flattened |
| Visual scripting | Open and modify character movement, combat, interactions, quests, triggers, camera and scene transitions | No essential gameplay hidden solely in Java/Kotlin source or compiled binaries; graph validation and parity tests |
| Characters / animation | Swap character, change spawn, rig/clip/controller states, animation transitions | Editable clips/state machines, test movement and animation in Play |
| Materials / lighting | Edit PBR/sprite materials, textures, lighting, atmosphere | Changes appear in World, Play and exported build |
| Audio editing | Open SFX and ambience; trim, fade, loop, set volume/pan, replace source | Project-owned editable audio settings; offline playback |
| Music composition | Open `.song`, choose real sampled instruments, edit notes, patterns, tempo, tracks, mixer and cues | No music-only baked WAV without editable composition source; license-compliant bundled soundbanks |
| Game UI | Edit HUD, menus, health bars and interaction prompts | Data-driven editable UI, not hardcoded sample-only widgets |
| Gameplay / physics | Edit collision, movement, health, AI, win/lose rules and input via components/graphs | All core behaviors configurable; valid genre/dimension profile |
| Packaging | Rename, duplicate, save, reopen and export a standalone playable build | Source assets and references remain portable; output launches without editor |

**Applicability:** A 2D game need not have a 3D model to modify; a 3D action RPG need not have a tilemap. However, every *feature actually used* in that sample must be editable. If a sample includes a house, terrain, a song, or a combat rule, the corresponding editor and source must be available.

### F3. Template gallery and required reference games

Offer a simple **New Game → Choose Template** gallery with a preview, genre, dimension, size, supported-platform status and two actions: **Play Sample** and **Use as Template**. Opening the new project goes directly to World, with **Customize** adjacent to **Play**. Advanced workspaces remain contextual and collapsed by default.

1. **2D Platformer:** editable tilemap, layered sprites, sprite animations, side camera, collisions, collectible/enemy logic graphs, editable soundtrack and effects.
2. **2D Puzzle / Adventure:** editable rooms, sprites, UI, interactions, win conditions, sound cues and logic graphs.
3. **2.5D Side-scroller:** 3D scenery/terrain and objects, side-view camera, locked-depth character movement, collision, editable character and audio.
4. **2.5D Isometric RPG:** editable terrain, props, characters, camera, dialogue/quests, visual logic and music.
5. **3D Action RPG — Weskaap-style validation project:** editable valley and slopes, CC0 trees/boulders, house mesh, player character, camera/controller, combat/interaction graphs, animations, UI and music. This is an engine sample inspired by the workflow, **not a dependency on or modification of the separate Weskaap repository**.
6. **3D Adventure / FPS or TPS:** include only once the chosen genre profile is implemented and tested; otherwise label planned and do not offer as playable.
7. **RTS / turn-based / racing / simulation / sandbox:** expand progressively as their actual gameplay systems and export tests pass; no decorative genre selector that launches the wrong gameplay.

All templates must be **original or based on license-verified redistributable assets**, with a per-asset source URL, license, attribution when required, file hash, and editable-source availability recorded in the manifest. See Appendices A–D for candidate CC0/open-content libraries; do not assume a library-wide license covers each item. Keep starter downloads small and optional high-resolution packs separate.

### F4. Source ownership and data design

- The template is a **self-contained project**, not a pointer to mutable global demo state. Copy or reference immutable shared assets safely; fork editable copies on first change where appropriate.
- Store editable source files for scenes, `.terrain`, prefabs, meshes, materials, sprites/layers, animations, visual graphs, `.song`, audio editing metadata, UI and project settings. Source formats must be documented and versioned.
- Maintain source-to-runtime links: changing a mesh, sprite, graph, material or song updates the relevant World instance and the next Play without manual file surgery.
- Imported CC0 source content is preserved; customized project assets are distinct. Explicitly distinguish **Edit this instance**, **Edit shared prefab**, and **Make unique** when needed, with safe defaults.
- Save/load, migrations, undo/redo, export and asset portability are mandatory. Missing source dependencies must produce an actionable warning, not silent substitution.
- Keep existing sample scenes and graphs intact during migration. Build a versioned migration plan and preserve originals until verified replacements pass.

### F5. Full sample editability audit — hard release gate

Extend R1's editability audit from asset/component coverage to a **sample-to-template acceptance matrix**. For every sample and each domain actually used, record: source present, editor opens, user edit succeeds, undo/redo, save/reopen, Play/Stop, export, license verified, and real desktop visual/listening validation. Status must be **PASS / FAIL / BLOCKED / NOT APPLICABLE**, with evidence links. A missing editor or hardcoded behavior is **FAIL**, not N/A.

Required automated checks: enumerate all samples and referenced assets; detect broken references, missing editable source, unregistered editors, hardcoded-only gameplay, invalid graphs, broken scene/asset serialization, invalid licenses, and regression in template duplication. Run integration tests for project cloning, asset fork-on-edit, prefab propagation, runtime isolation and export. Record graphics/audio checks as pending until actually observed.

**Golden acceptance journey (3D Action RPG):** New Game → Use as Template → 3D Action RPG → World → sculpt a valley → place CC0 trees/boulders → move player spawn → Customize house in 3D workspace and modify roof geometry → Back to World → open character combat graph and change attack behavior → edit character animation → edit music notes using sampled piano/strings and trim a sound effect → return World → Action RPG + 3D → Play → verify changes → Stop → Save → close/reopen → Play → Export → launch export. Verify originals remain unchanged.

**Golden acceptance journeys (2D and 2.5D):** clone each template; modify a sprite/tile or a 3D scene prop respectively; edit a gameplay graph, animation and music; Play/Stop; save/reopen; export and launch. For 2.5D confirm the correct camera, movement plane and collisions.

No template may be advertised as **Fully Customizable** until all applicable gates pass. Samples with partial capability remain available as **Technical Demo / Partially Editable**, clearly labeled, not silently promoted.

### F6. Integration with existing roadmap phases

- **R0:** repair save/load, undo/redo, runtime isolation and reproducible baseline.
- **R1:** establish per-sample editability matrix, project duplication and template gallery; reject hardcoded-only sample gameplay.
- **R1b / Appendix E:** simple World-first gallery; Customize and Play are adjacent; contextual edit actions and Back to World.
- **R2 / R2b:** editable materials and verified CC0 asset sources; manifest + project-owned asset forks.
- **R3:** native editable terrain and collision; migrate immutable landscape samples.
- **R3b / R3c:** genuinely playable genre/dimension profiles and edit→Play→Stop→edit acceptance.
- **R4:** fully editable strategy scenario templates, rules and campaigns.
- **R5 / R6:** editable animation, UI, effects, navigation and AI behaviors used by samples.
- **R7a / R7:** practical mesh editing and advanced 3D modeling, with clear imported-source limitations.
- **R8:** editable songs, sampled instruments, audio clips and listening checks.
- **R9:** standalone project export, backend parity, portability, template versioning and release gates.
- **R10 — FINAL PHASE:** only after manual template editing and export are validated, integrate LLM + agent harness for **prompt-to-working-game**. AI must generate or modify the *same editable project sources* and use the same typed editor commands, approvals, undo, license checks, Play and export tests. AI-generated games are subject to this identical audit; never deliver a baked, noneditable result as a complete game.

### F7. Windsurf instruction — audit first, implement only after approval

> Read `docs/14-nov-forgeroom-roadmap-revised.md`, especially §2, R0/R1/R1b, R2b, R3–R3c, R7a/R7, R8–R10 and Appendices A–F, plus AGENTS.md and applicable TDs. **New owner requirement: every sample offered as a game template must be completely customizable in every domain it uses**: world/terrain, 3D/2D models, materials, sprites, animation, visual scripting, physics/gameplay, UI, audio effects, sampled-instrument music and export. Audit all existing sample scenes, graphs and projects; identify hardcoded-only logic, baked-only models/music, missing editor registration, missing source files, licenses, broken references and nonworking exports. Produce `docs/reports/fully-editable-template-audit.md` and a PASS/FAIL/BLOCKED/N/A matrix with evidence, a staged migration plan and any required TDs. Preserve existing samples; do not claim unsupported features work. Prioritize a complete 2D platformer, 2.5D side-scroller and 3D Action RPG vertical slice, each duplicated from a template and fully edited, saved, played and exported. Keep World-first UX, Customize beside Play, and all advanced tools contextual. **Do not change code, download assets, create modules, commit or push until explicit owner approval.** R10 LLM + harness remains the final phase, after manual editability gates pass.
