Forgeroom Roadmap — 14 November 2026 (Revised)
Document: docs/14-nov-forgeroom-roadmap-revised.md

Status: Proposal for owner review. Nothing in this file is approved work. Every phase below needs an explicit

instruction to start, and every new module or changed decision needs a technical decision record (TD) and approval

(AGENTS.md §0, §4, §10).

Supersedes (for ordering only): the phase order in [ALL_IN_ONE_STUDIO_ROADMAP.md](ALL_IN_ONE_STUDIO_ROADMAP.md)

and the "Recommended next work" list in [10-nov-current-status.md](10-nov-current-status.md) §6. Those files stay as

history; this file does not edit them.

Revised 2026-11-14 (owner workflow update): preserves R0–R10 and approval gates, adding R1b (simplified editor), R2b (verified CC0 assets), R3c (world-to-play-to-edit acceptance), and R7a (practical building mesh editing). R3b now includes Action RPG + 3D acceptance alongside Platformer + 2.5D. All work remains proposed until explicitly approved.

---**

## 1. Product vision

Forgeroom is one editor and one runtime in which a person can make a whole game, from terrain and models to music

and scenario rules, without leaving the application. It is a hybrid of five tools, taking from each the part it

does best, plus a sixth reference for scenario authoring:

| Reference | What Forgeroom takes from it | Where it already exists | Where it is missing |

|---|---|---|---|

| Unreal Engine | Level editor feel, Blueprint-style visual scripting, Material Editor, landscape tools, particle and cinematic tools, world building | Editor shell, 3D viewport and gizmos, visual scripting (TD-029, TD-068 to TD-070), PBR and shadows (TD-032) | Material assets and editor, landscape editor, particles, cinematics, post-processing |

| Unity | Component model, prefabs with overrides, asset pipeline, Animator controller, 2D toolset, package and project templates | Components and serializers, prefabs (TD-027), sprite and tile tools (TD-066), packaging (TD-033) | Animator state machine, in-game UI, package system, project templates |

| Godot | Small and fast, scenes that nest as nodes, signals, one-click export, built-in script editor | Scene tree, scripts and graphs, game runtime (TD-035), web player (TD-036, TD-041) | Scene-in-scene workflow beyond prefabs, built-in code editor depth, signal-style events in the Details UI |

| Blender | Modeling, UVs, rigging, sculpting, shader nodes | Sculpting (TD-038, partial), skeleton and Timeline (TD-031), character creator (TD-053) | Mesh edit mode, UV editor, weight painting, modifiers |

| LMMS / FL Studio | Step sequencer, piano roll, mixer, synths, sample use | Music Studio (TD-040, TD-074 to TD-076): synths, drums, SoundFont, mixer, WAV export | Listening acceptance, .ogg export, automation, in-scene adaptive music |

| RTS scenario editors (the original Age of Empires campaign editor is the model; research in [swgb.md](swgb.md)) | Terrain painting and elevation, placing units and buildings per player, trigger rules, objectives, campaigns, reusable templates, random map scripts | Nothing dedicated. Pieces exist: tile maps, landscape samples, graphs, prefabs | Everything: see Phase R4 |

| Prompt-driven creation (AI assistants in code editors, applied to game making) | A person describes a game in chat and the assistant builds it with the same operations the editor offers, reviewable and undoable | Assistant panel, 6 agent tools, structured plan engine, Ollama and mock providers ([architecture audit](AI_AGENT_ARCHITECTURE_AUDIT.md)) | Tools cover only scene, entity, transform and component basics; nothing for assets, materials, terrain, graphs, scenarios, music or export; no self-checking loop. See Phase R10 |

Originality rule (TD-020, AGENTS.md §1). These are references for workflows and toolsets. All code, data

formats, icons, assets and feature names are original. No Unreal, Unity, Godot, Blender, LMMS or Age of Empires code,

art, sound or trademarked names are copied. Reading a community file-format description for research (swgb.md) is

allowed; importing a retail scenario file format is a separate decision (§7).

---
1a. Primary product acceptance journey — build Weskaap without leaving ForgeRoom
This is the end-to-end owner acceptance scenario, not a claim that it works today. The named game Weskaap is an example project authored with ForgeRoom, not a code dependency or a merger of repositories.
1. Launch ForgeRoom. Open an existing project or create a new one. The default workspace is World with an uncluttered, dominant viewport and contextual controls.
2. Sculpt the terrain: raise hills, carve valleys, smooth slopes, paint ground materials, and save an editable .terrain asset.
3. Populate the world: browse realistic, verified CC0 trees and boulders, place and rotate them, and scatter foliage with performance-safe instancing. Preserve provenance and local cache.
4. Place the player character from an editable character prefab. Assign a spawn point, collision, movement and camera capabilities. Show missing requirements clearly.
5. Select Game Genre = Action RPG and Game Mode = 3D (or 2.5D where implemented). These are separate dropdowns in the compact Play/Test bar. Press Play. The game starts in an isolated runtime copy with the selected controller, camera, inputs and collisions.
6. Stop. The editor restores the exact authoring scene, selection, camera and undo history; runtime-only modifications are discarded unless explicitly imported.
7. Place a house from the Buildings asset palette. Select the house and choose Edit 3D Model. The editor opens the 3D Modeling workspace with the house mesh in context, preserving the world as a reference.
8. Modify the house using supported mesh operations (e.g. select faces, extrude a wall, move a window, change a material), preview it, save a new derived asset or update a permitted project-owned asset, then Return to World. The house updates in the map and in Play; other instances follow an explicit shared-asset/unique-copy choice.
9. Save, close, reopen, play and export the project. Verify terrain, models, materials, prefabs, game profile, controls and gameplay survive the round trip.
Definition of product success: a new user completes the journey with normal editor controls, without hand-editing JSON, modifying Java source, opening Blender, or manually attaching camera/controller scripts. More sophisticated character logic, animation and combat require their own verified gameplay components and may be staged after the initial walkable Action RPG slice. A successful Play button does not, by itself, imply a complete Action RPG combat system.

No silent asset conversion: importing a third-party GLB creates a project-managed asset. Editing a mesh must preserve or explicitly replace its source/derived relationship; never destructively overwrite downloaded provider originals.
2. The guiding rule: everything shipped as an example must be editable
The owner's requirement: the examples in the editor should be editable on all fronts. Today that is not true. For

example, the five landscape scenes are glTF files generated by Python scripts (tools/make_landscape_*.py), so a

user cannot reshape the terrain, repaint it or move a tree except by moving whole entities; and only one of the 15

sample graphs (ShooterShip) is recorded as matching its reference gameplay (10-nov status §6, item 4).

Editable on all fronts means, for every sample scene:

| Front | Requirement |

|---|---|

| Scene | Every entity and component has a Details editor; nothing is only settable in JSON |

| Logic | Behavior is a visual graph or script visible in the editor (a Java-only sample counts as a gap) |

| Terrain | Terrain is a terrain asset edited by the landscape tools (R3), not a generated model |

| Materials | Looks come from material assets the user can open and change (R2) |

| Animation | Clips and state machines open in the Timeline or the Animator editor (R5) |

| Audio | Music and effects open in the Music Studio or sfxr editor |

| Prefabs | Repeated objects are prefabs with overrides (TD-027), not copies |

| Rules | In scenario samples, rules are triggers the user can edit (R4) |

| Ownership | A sample can be duplicated into the user's project and edited; the original stays intact |

Enforcement. Phase R1 builds an editability audit: an automated test that loads every sample scene and fails

when an entity has a component with no Details editor, a script with no graph or source editor, a referenced asset

type with no opening editor (the .song Content Browser bug of 2026-11-12 is exactly this class of defect), or a

model that should be terrain. The audit result is a table in docs/FEATURE_TEST_MATRIX.md, updated with every

phase. A sample is not called editable until its row is complete.

---
3. Current baseline (honest snapshot)
Sources: [INDEX.md](INDEX.md), [FEATURE_TEST_MATRIX.md](FEATURE_TEST_MATRIX.md),

[10-nov-current-status.md](10-nov-current-status.md). Status words are the AGENTS.md §9 meanings.

- Implemented and integrated (automated tests pass; reachable from the editor): 2D and 3D scenes, prefabs, 2D

  physics (Box2D), 3D physics (Jolt), visual scripting with functions and events, skeleton animation and Timeline,

  character creator, sculpting (partial), sprite and tile tools, Music Studio, packaging, game runtime, web player.

- Validated in the running application (a person saw it work): very little. Most rows say "visual check

  pending" or "Manually Not Verified (Headless Environment)". The [visual checklist](reports/visual-check-checklist.md)

  is the plan for closing that.

- AI assistant (Implemented; Integrated): AssistantPanel in the editor, AssistantService, OllamaModelProvider

  and MockModelProvider, an agent tool registry with 6 tools (scene inspect, entity create, rename and delete,

  transform set, component add), and a plan parser, validator and executor. Tests: AssistantServiceTest,

  AssistantIntegrationTest, AgentPlanEngineTest (matrix rows). Not validated with a real model in this review.

- Content: 36 sample scenes in assets/samples, 15 sample graphs in assets/graphs/samples.

- Known open defects (not re-verified on 2026-11-13 unless stated): the Kotlin generator test failure and the

  Duplicate-component and save/load gaps listed in the 10-nov status; File > Open sends .song files to the scene

  loader (seen 2026-11-12; the Content Browser half was fixed that day, with the SONG asset type and a test).

- Not found in the code (a search of the engine modules on 2026-11-13; treat as likely gaps, not proven): material

  assets, particles, an animator state machine, in-game UI canvas, navigation mesh, terrain assets.

---
4. Phases
Each phase lists its goal, deliverables, what it needs approved, and its done test. Order is a recommendation;

the reasons are in §5. Sizes are rough (S under 2 weeks, M 2 to 6, L 6 or more, one person) and are guesses, not

estimates from measured velocity.
R0 — Trustworthy baseline (S) — do first
Goal: know what works before adding more.

- Fix the Kotlin generator test failure; run ./gradlew test --continue for the whole repo; record per-module test

  counts and every skip (AGENTS.md §5).

- Close the data-consistency gaps: Duplicate component, save and load round trip, Play isolation, undo and redo for

  the newest components (10-nov status §6, item 2).

- File > Open understands .song, .graph, .anim, .sprite, .prefab and opens each in its editor, or says

  clearly that it only opens scenes.

- Acceptance project: one person builds a small game end to end (create scene, import a model, physics, camera,

  graph logic, animation, music, save, reopen, play, export, launch the export) and records what they saw at each

  step in a report.

- Reconcile INDEX and the feature matrix with dated results.

Done when: a full test run passes or every failure is listed as pre-existing with a cause; the acceptance report

exists with observed results.
R1 — Editable examples and project templates (M)
Goal: the rule in §2 for everything that does not need a new subsystem.

- Editability audit test and matrix (§2).

- Finish TD-068: convert the remaining 14 sample graphs to full graphs that match their Java behavior frame for

  frame, with parity tests (needs the function and component-node support from TD-069 and TD-070).

- "Duplicate sample into my project" action; samples open read-only as originals.

- Template gallery on the start screen: empty 2D, 2.5D, 3D, platformer, top-down, first person, and later

  strategy (R4). A template is a project folder plus a scene, prefabs and graphs, built from the same editable parts

  as the samples.

- Content Browser: every asset type has an icon, an opening editor and a "used by" list.

Done when: the audit passes for every sample that does not depend on R2 to R4, and a new project from each

template opens, plays and exports.
R1b — Minimal, scenario-editor-inspired UX shell (M) — new, early priority
Goal: make the default interface as direct as the original Age of Empires scenario editor while retaining all advanced capabilities as discoverable workspaces.
- Default World workspace: compact app bar (save, undo, redo), workspace tabs, World categories (Map, Terrain, Objects, Players, Triggers), large central viewport, one small contextual tool strip at the bottom. Only show actions that are supported; missing features must be visibly labeled rather than fake controls.
- Keep Scene, 3D Modeling, Animation, Music, Sprites/2D, Visual Scripting, Assets as separate workspaces; keep advanced hierarchy, inspector, content browser, logs and AI panel collapsible and accessible by menu/shortcut.
- Put Game Genre, Game Mode (2D/2.5D/3D) and Play/Stop together in a compact bar, visible from World and other workspaces where appropriate. Editor camera/view orientation is a third, independent concern, not a gameplay mode.
- Selection-aware tools: terrain brush settings for terrain, prefab palette for Objects, contextual properties for selected entities, Edit 3D Model for a mesh-backed building. Never show unrelated controls at once.
- Preserve keyboard shortcuts, accessibility, panel resizing, undo/redo, viewport focus and existing desktop behavior. Provide a clear path back to World from specialized workspaces.
- Establish a shared UX specification for the UrConnex-hosted ForgeRoom web UI; do not couple desktop Java/LibGDX implementation to Next.js or Spring Boot.
- Create a feature-to-workspace inventory before hiding or relocating any existing command; do not delete working features.
Needs: UX/layout TD if it changes editor navigation contracts; actual UI framework audit. Done when: an unfamiliar tester can sculpt a slope, place a rock, locate Play, open the 3D Modeling workspace and return to World without guidance, and all legacy commands remain reachable. Record screenshots and usability observations.
R2 — Materials and the Material Editor (M to L) — Unreal, Blender, Unity
Goal: looks become data a person can edit.

- .material asset: PBR parameters (base color, metallic, roughness, normal, occlusion, emissive, opacity),

  texture slots, tiling, double-sided, alpha mode. Primitives and imported glTF models can use one.

- Material Editor tab: a node graph that reuses the graph canvas (TD-069 palette). Output node feeds the PBR batch

  (TD-032). Live preview sphere. Parameter instances (Unreal) and shared materials (Unity).

- Details panel material slot with drag and drop from the Content Browser.

- Web player (Babylon) reads the same file; both backends listed in a support matrix (R9).

Needs: a TD (new asset type, graph reuse); possibly a new module engine-material (approval required).

Done when: a sample's rock, wood and water materials are separate .material files edited in the editor, and

the change shows in the viewport and in Play.
R2b — Verified CC0 realistic assets, model import and provenance (M) — new
Goal: default World palette uses realistic trees, boulders, vegetation and buildings rather than only geometric primitives, without compromising redistribution rights, performance or editability.
- Prefer Poly Haven for realistic CC0 3D models and ambientCG for CC0 PBR materials; optionally verified CC0 Kenney packs for lightweight content. Verify the current license of each asset and provider API terms before downloading. Do not infer that every item on an unrelated site is CC0.
- Build a provider-neutral asset source interface, a small curated starter pack and optional on-demand downloads; no bulk scraping. Use official APIs, provider-required identification/credit, request limits and checksum validation. Log source URL, provider, author where available, CC0 license, retrieval date, hash and any conversion.
- Prefer glTF 2.0/GLB for import and interoperability. Audit existing LibGDX glTF loader and material support; handle textures, alpha-cutout foliage, PBR support and graceful degradation. If a source supplies another format, use a reproducible conversion step and validate output rather than assuming GLB exists.
- Cache models locally for offline use. Do not commit large binary collections automatically. Keep downloaded originals immutable and derived editable copies separately.
- Extend the Objects palette with searchable thumbnails, Trees/Rocks/Plants/Buildings, license detail, Place, and Edit 3D Model where mesh editing is supported. Asset and prefab IDs must be stable across save/load.
- Add scene-aware instance transforms, prefab overrides, collision authoring, instancing/LOD plans and performance measurements. A realistic-looking asset does not automatically have gameplay collision or a suitable low-poly mesh.
- Maintain a shared asset manifest and support matrix for desktop LibGDX and UrConnex ForgeRoom web rendering; backend storage is an optional later integration, not a desktop prerequisite.
Needs: asset import/licensing TD and any new dependency approval; R2 materials for full fidelity, but the first import slice can proceed using existing renderer capabilities with limitations documented. Done when: one verified CC0 realistic tree, rock and house are downloadable/importable, appear with recognizable thumbnails, place multiple times, save/reload offline, preserve license metadata and render acceptably; original primitive placeholders remain available.
R3 — Landscape and terrain tools (L) — Unreal Landscape, Unity Terrain, Godot heightmaps, Blender sculpt
Goal: terrain is an editable asset, replacing the generated landscape models.

- .terrain asset: heightmap grid, resolution and size, per-layer splat weights (grass, rock, sand, snow, dirt),

  holes, water level. Chunked mesh with level of detail so large maps render.

- Tools: raise, lower, smooth, flatten to height, noise, erosion pass, ramp between two points, paint layer, erase

  layer, import and export heightmap (PNG 16 bit), undo for every stroke (one EditorCommand per stroke, TD-018).

- Scatter tools (foliage, rocks): brush with density, random scale and rotation, slope and height limits,

  exclusion zones, using prefabs. Instanced rendering.

- Splines: rivers, roads and cliffs from editable curves that deform terrain and place meshes.

- Water body component, sky and weather presets, fog.

- Collision: the terrain feeds 2D (height as slopes where relevant) and 3D (Jolt height field) physics; character

  controller walks on it (TD-049).

- Build on the existing sculpt core (engine-sculpt, TD-038) where it fits; do not fork a second brush system.

- Convert the five landscape samples to terrain assets.

Needs: TD; new modules engine-terrain, editor-terrain (approval); web player support is its own step.

Done when: a person opens landscape-3d, reshapes a valley, repaints a slope, scatters pines, saves, plays, and

the character walks on the new ground.

Status 2026-11-14 (first parts, see [r3-terrain-report.md](reports/r3-terrain-report.md), TD-082 and TD-083): the

.terrain asset, chunked drawing, brushes with undo, per-point ground surfaces (grass, sand, snow, gravel) and a first

level editor exist. Collision, layers with blended weights, water, scatter, splines, erosion, heightmap import and

level of detail do not. Nobody has yet checked collision-free walking, because there is none.
R3b — World View: build a world, then play it as a chosen genre (M) — Unreal level editor, original Age of Empires campaign editor, Unity Play mode
Goal: one default place to build a world, and a Play/Test bar that launches that world as a chosen kind of game,

instead of every game needing its own hand-attached scripts and camera.

Where it stands: the pieces exist but are not joined. Terrain and the first level editor (TD-082, TD-083); game

modes 2D, 2.5D and 3D as a per-scene authoring setting (TD-067); Play runs a serialised copy of the scene and Stop

restores the original (PlayModeController); camera rigs (TD-050); the 3D character controller (TD-049); four 2.5D

samples with shared SideBody and SideCamera25D scripts. Missing: any idea of a genre, a profile that supplies

camera, movement and input when Play is pressed, and collision for terrain.

- World View. The default centre view of the editor (today the Viewport tab) becomes the place the level tools

  live: base ground, shaping, ground painting, object palette and placement, grown from the TD-083 palette. The other

  views (game view, Animations, Visual Scripting, Music, Sprites, Tiles) and the Content Browser stay as they are.

- Play/Test bar. Two dropdowns beside Play, Game Genre and Game Mode (2D, 2.5D, 3D), independent of which

  view is open. The genre list comes from a registry, not from UI code: Platformer / Side-scroller, Action RPG,

  Adventure, First-person Shooter, Third-person Shooter, Real-time Strategy, Turn-based Strategy, Racing, Puzzle,

  Simulation, Sandbox. A combination is listed as playable only when its test passes; the rest are shown as planned.

- GameTestProfile (engine level, headless): genre, dimension, camera, movement, physics, input mapping and

  runtime requirements. Pressing Play resolves the profile, validates the scene and says what is missing in plain

  words, applies the profile to the runtime copy only with runtime-only components that are never saved, and shows

  the preview. Stop disposes the copy and restores the scene and undo history untouched.

- Reuse, not rewrite. Camera follows CameraRigComponent, movement uses the character controller, physics is the

  existing 2D and 3D systems. 2.5D is its own configuration (side camera, play plane, locked depth), not a renamed 3D

  camera.

- Saved choice. The last genre and mode are an optional field of the scene file; older files load unchanged.

- Order of slices. Platformer + 2.5D as the first generic profile slice, Action RPG + 3D as the owner acceptance slice (R3c); each later combination (for example 3D platformer,

  third-person, racing, first-person) is its own small slice added to the registry.

- Agent parity (§6, rule 8): tools to set the genre and mode, validate, and press Play and Stop.

Needs: a TD for the profile architecture and the scene-file field; no new module is planned (engine-scene,

engine-render, editor-core, editor-desktop), and one would need approval. Terrain collision from R3 before a

world made of terrain is walkable; worlds built from box, hull or mesh colliders work first.

Web: desktop only at first, recorded in the support matrix (R9).

Done when: a person builds a world, adds a Player, picks Platformer and 2.5D, presses Play, sees a side camera,

runs and jumps along the play plane and collides with the level, presses Stop and gets the unchanged scene and undo

history back; and an unsupported combination is refused with a reason that says what to add.
R3c — World → Play → Stop → Edit → Play acceptance slice (M to L) — new product gate
Goal: join R1b, R2b, R3, R3b and the existing runtime into one verifiable user journey, using a Weskaap-style Action RPG world.
- First walkable slice: sculpt a valley and slope, paint materials, place realistic boulders/trees, add a player prefab and spawn point, and Play as Action RPG + 3D. This is separate from the pre-existing R3b Platformer + 2.5D slice; do not declare either complete based on the other.
- Genre profile registry supplies camera/controller/input/physics configuration and reports readiness per genre × dimension. Action RPG + 3D requires third-person follow camera, ground movement, terrain collision, player spawn and Stop restoration; basic attack/combat can be a later explicitly named slice.
- Action RPG + 2.5D should be independently validated for isometric or side-view movement, collision and camera; do not treat it as automatic compatibility.
- Pressing Play uses a disposable runtime scene copy; Stop restores the authoring state, pending edits and undo history. Profile changes never destructively convert the world.
- After Stop, place a realistic house prefab, select it and open 3D Modeling. If R7a mesh-editing is not ready, show a clear capability notice; do not pretend transform-only editing is mesh editing.
- Complete the same test after the R7a house-edit slice: modify a wall or roof geometry, save as a project-owned derived model, return to World, verify changed geometry in the scene and replay the Action RPG profile.
- Add a regression test project plus human desktop visual checklist for the full journey. Verify save/reopen/export where supported; mark unverified web behavior explicitly.
Dependencies: R0 scene consistency and Play isolation, R1b UX, R2 materials, R2b import, R3 terrain collision, R3b profile framework, and R7a for the full house-mesh-edit criterion. Done when: the complete owner acceptance journey in §1a is demonstrated with recorded evidence; partial completion must be reported as partial.
R4 — Scenario and campaign editor with templates (L) — original Age of Empires campaign editor as workflow model
Goal: a person authors a strategy scenario and a campaign without writing code. This phase has two halves: an

RTS runtime framework (what games do) and the scenario editor (how they are authored).

Runtime framework (engine-strategy, proposed):

- Players and teams, colors, diplomacy states, starting resources, population limits.

- Unit and building definitions as data: stats, costs, build and research lists, tech tree, upgrade chains.

- Orders and selection: box and group select, move, attack, gather, build, repair, patrol, formations.

- Navigation: grid or navmesh from the terrain, obstacle avoidance, flow-field or A* with many units.

- Fog of war and line of sight; resource nodes, gathering, drop-off, production queues.

- Simple AI: build orders and attack waves driven by behavior graphs (reuse visual scripting).

Scenario editor (editor-strategy, proposed):

- Terrain and map: the World View (R3b) with strategy palettes, reusing the R3 tools; map size presets; terrain and

  elevation painting; cliffs; water.

- Object palette: units, buildings, resources and decorations, filterable per player; place, move, rotate,

  delete, multi-select, copy and paste.

- Players panel: names, civilization-style data sets, colors, starting units, resources, victory settings.

- Triggers: condition and effect rules on the visual scripting runtime (when a unit enters an area, an object is

  destroyed, a timer elapses, a variable changes → spawn, move, change ownership, show message, play sound, end

  scenario). Presented as a trigger list with explanation text, like the original editor, but stored as graphs.

- Objectives and instructions: objective list, scenario briefing and hint text, per-player text.

- Cinematics: camera paths and timed dialogue using the Timeline (TD-031).

- Campaign: a .campaign file that links scenarios, carries variables between them, and defines the order and

  victory branches.

- Random map scripts: a graph that builds a terrain and places objects from a seed (land shapes, resource

  placement rules), runnable from the scenario editor and at game start.

- Templates and prefabs: unit prefab library, building prefab library, base layout prefabs (a town center with

  starting units), scenario templates (skirmish, survival, escort, capture), and a template gallery entry for each.

  Prefab overrides (TD-027) are how a scenario changes one unit without losing the template.

- Test play from any position in the editor, with cheat commands and a trigger debugger.

Needs: TD for the module layout and file formats (.scenario, .campaign, unit data); a decision on importing

community scenario formats (§7); R3, R3b and R2 first; R5's state machine for unit animation.

Done when: a person builds a two-player map, places bases from templates, writes a "destroy the enemy base"

trigger and an "after 10 minutes, send an attack wave" trigger, saves, tests, and plays it in the exported game;

then links two such scenarios into a campaign.
R5 — Animation system upgrade (M) — Unity Animator, Unreal Animation Blueprint, Godot AnimationTree
- Animator controller: states, transitions with conditions (parameters set from graphs), blend spaces, layers and

  masks. Editor is a graph tab.

- Retargeting between skeletons; two-bone IK exists (TD-031), add look-at and foot placement.

- Root motion; animation events that call graph events; ragdoll through Jolt.

- 2D: sprite animator states using the same controller.

Done when: the character sample walks, runs and jumps by parameters set from a graph, with blends visible in

Play.
R6 — Effects, UI and AI (L)
- Particles: emitter component, curves over lifetime, GPU or batched CPU rendering, a particle editor tab,

  presets (fire, smoke, dust, sparks, hit effects).

- Post-processing: bloom, color grading, ambient occlusion, depth of field as a volume component.

- In-game UI: canvas, anchors, layout containers, buttons, text, images, progress bars, themes, input focus,

  authored in a UI editor tab and driven by graphs (Unreal UMG, Unity UI, Godot Control).

- Navigation and behavior: navmesh baking, agent component, behavior graphs (used by R4's AI).
R7 — Modeling, UV and rigging (L) — Blender
Un-defers the TD-026 items after R2 to R4: half-edge mesh data model, Edit Mode (vertex, edge, face), extrude, inset,

loop cut, bevel, mirror and subdivision modifiers, UV unwrap and editor, weight painting, bone placement, export to

glTF. Reuses the undo system and the material system. This is a very large area; it is planned last in the "make

things" group because it is the least tied to the game-authoring loop and the most expensive.
R7a — Practical in-editor 3D building editing (M to L) — new, prioritized vertical slice within R7
Goal: selecting a house in World and choosing Edit 3D Model opens an actual geometry editor, not just an object transform inspector.
- Open a selected mesh-backed prefab in the 3D Modeling workspace with contextual reference to its world placement. Distinguish instance transform, prefab override, mesh edit, and material edit.
- First supported mesh operations: vertex/edge/face selection, translate, extrude, inset, basic bevel, face deletion and material assignment; implement only those justified by existing engine-sculpt and TD-026 architecture. Add undoable commands, mesh validation and preview.
- Imported GLB meshes may require conversion into an editable internal mesh representation; preserve node transforms, material slots and asset provenance. Do not imply every glTF extension, rig or skinned mesh can be edited.
- On Save offer Save as new model (safe default), Update project-owned model, or Create unique instance mesh. Warn when an edit will affect other prefab instances; never overwrite provider originals.
- Return to World without losing selection/camera context. Rebuild render/collision data as necessary; changes persist through save/reopen and Play, and can be exported to glTF/GLB where supported.
- Preserve R7's broader UV unwrap, rigging, weight painting and advanced modifiers as later scope; do not block the basic house workflow on full Blender parity.
Needs: R2 material support, R2b imported meshes, mesh-data/serialization TD, command-system compatibility. Done when: import a CC0 house, place it, open its geometry, change the roof or wall, save a derived model, return to World and play the changed scene; undo, reload and collision are verified.
R8 — Music and audio completion (M) — LMMS / FL Studio
- A person listens and signs off: instruments, drums, mixer, latency (Phase 20 never had a listening acceptance).

- .ogg export (documented gap); clip import and slicing; sample-based instruments.

- Automation lanes (volume, pan, filter) on the playlist; per-channel effects chains.

- In-game music: songs as scene assets, layers that fade in and out by game state (adaptive music), triggers

  from graphs and from R4 scenarios.
R9 — Pipeline, extensibility and backend parity (M, ongoing) — Unity packages, Godot add-ons
- Packages: a folder format and manager for sharing prefabs, graphs, materials and templates; editor extension

  points (custom panels, custom Details editors, menu items) documented in Javadoc.

- Import: OBJ and FBX through a documented library decision, Aseprite import (TD-066 follow-up), audio formats.

- Text-friendly scenes and meaningful diffs for version control.

- Backend support matrix: Java desktop, TeaVM and Babylon behavior listed feature by feature; release checks on

  real Windows, Linux and macOS packages (10-nov status §6, item 6).

- Performance baselines (scene load, frame time, memory, audio latency) before and after each phase.

- Profiler and frame debugger panel.
R10 — AI game builder: build games from prompts (L, ongoing) — the final phase
Goal: a person who has never opened the editor types what game they want, answers a few questions, and gets a

playable, exportable, fully editable game, built step by step with the editor's own operations. This phase is

last on purpose: the assistant can only do what the editor can do, and it is only as good as the templates,

prefabs, materials, terrain tools and scenario tools it can call. Everything before R10 is what R10 stands on.

Principle: agent parity. Every editor operation a person can perform is also an AgentTool (AGENTS.md §7:

editor-core/.../agent/tools, returning AgentToolResult with machine-readable outputs). From R1 onward, **each

phase delivers its own agent tools and tests** next to its feature, so R10 is mostly integration, context, safety

and evaluation, not a late rewrite. R10-A audits that this really happened.

What exists (§3): the panel, 6 tools, plan engine, Ollama and mock providers. What R10 adds:

| Step | Deliverable |

|---|---|

| R10-A Tool coverage | A parity matrix of every editor action versus its agent tool; fill the gaps. Tool families: scene and prefab; assets (import, place, rename, find); materials; terrain (generate from a description, paint, scatter); graphs (create, add and connect nodes, validate, explain); scenarios (players, objects from templates, triggers, objectives, campaign); animation (clips, state machine); audio (compose a song, choose instruments, sfxr cues); UI; project (new from template, save, export). Tools are typed and validated like today's. |

| R10-B Plan, preview, apply | The assistant produces a structured plan (existing schema), the editor shows it as a readable list and a dry-run summary of what will change, the user approves or edits steps, then the plan runs as one undo group. "Undo AI step" reverts a whole prompt. No tool mutates state except through EditorCommand. |

| R10-C Context | A compact, token-budgeted view of the project for the model: scene outline, selection, asset list, available components and nodes (generated from the registries so it never goes stale), template list, recent Output Log. Retrieval over project files and the engine's own docs. Selection-aware prompts ("make this bigger", "add a patrol to the selected unit"). |

| R10-D Self-checking loop | After a plan the agent can look: run the editability audit (§2), validate graphs, start Play or a smoke test, read the Output Log and test results, capture a screenshot (the GDXUNITY_SCREENSHOT aid), and fix what it broke, within a step and retry limit. |

| R10-E Providers | Local Ollama (exists) and mock stay. Optional cloud providers behind the existing provider-neutral contract, with keys stored outside the project, an offline mode, and a visible statement of what leaves the machine (AI_AGENT_ARCHITECTURE_AUDIT.md §5 and §7). |

| R10-F Evaluation | A fixed prompt suite (for example "a 2D platformer with three levels and a boss", "a two-player skirmish map with an attack-wave trigger", "a 3D island with a walkable character and music") scored automatically: scene validity, editability audit, graphs validate, Play and the exported game run with 0 errors. Recorded model transcripts replay through the mock provider as regression tests. Human review on top. |

| R10-G First-run flow | "New game from a prompt": the assistant picks a template (R1, R4), customizes it with terrain, materials, prefabs, logic and music, asks clarifying questions, shows progress, and ends with a Play button and an Export button. Conversation features: history, cancel mid-plan, edit a plan step, attach a reference image, "explain what you built". |

Safety rules (needs a TD):

- Scripts are trusted code (TD-019). Java and Kotlin scripts run in-process. Anything the assistant writes as

  source is shown for review and is not run until the user accepts it; the assistant prefers visual graphs, which

  the validator checks and the interpreter sandboxes by design.

- Tools only touch files inside the project; paths are validated; no shell, network or other-project access.

- Every change is undoable and visible; a plan has step and cost limits; a user can stop it at any time.

- Prompts and project data are private: nothing is sent to a cloud provider without the explicit setting in R10-E.

Limits stated up front: output quality depends on the model. Small local models will manage simple edits and

templates, not a whole original game, and the evaluation suite will show where the line is. Nothing here claims a

model can design a good game, only that the tools, checks and safeguards let it try without breaking the project.

Needs: a TD for the plan-preview and undo-group design, the safety rules and cloud providers. Tools live next to

their domain modules, so no new module is required for R10 itself (to be confirmed in the TD).

Done when: from an empty project, a person uses only prompts (plus approving plans) to produce a small game that

plays and exports with 0 errors, passes the editability audit, and can then be edited by hand in every editor; the

R10-F suite passes at the thresholds the TD sets; and a whole prompt can be undone in one step.

---
5. Order and why

R0 baseline ─► R1 editable samples ─► R1b simple UI ─► R2 materials ─► R2b CC0 assets ─► R3 terrain ─► R3b profiles

                                                       │                │

                                                       └──► R5 animator ┘            ──► R6 effects/UI/AI

R8 music (parallel, small)      R9 pipeline (continuous)                                 R7 modeling

                                                                                          │

                        every phase also adds its own agent tools (§6, rule 8) ──────────► R10 AI game builder (last)

- R0 first because 10-nov status found failing tests and many "visual check pending" rows. More subsystems on an

  unverified base multiply the checks owed.

- R1 before new systems because it turns the owner's requirement into a test that every later phase must keep

  green.

- R2 before R3 and R4 because terrain layers and unit looks are materials.

- R3 before R4 because a scenario editor without terrain tools is not an editor; R4 also needs navigation data

  from the terrain.

- R3b between R3 and R4 because the scenario editor is the World View with strategy tools added, and because play

  profiles are what let a built world be tried as a platformer, shooter or strategy game without hand-attached scripts.

- R5 before R4's unit work because units need a state machine for idle, walk, attack and die.

- R7 last because it is large and the game-authoring loop works without it (users can import models).

- R8 is small and can run alongside; R9 is continuous.

- R10 last (your instruction, and it is also the technical order): the assistant can only build with tools,

  templates and checks that already exist. Its tools, though, are written during each earlier phase, so R10 is not

  a big-bang rewrite at the end.
Dependencies on existing approved decisions
TD-016 (gizmos), TD-018 (one command per drag), TD-019 (scripting), TD-020 (UE5 workflows, no copied code), TD-026

(sequencing), TD-027 (prefabs), TD-028 and TD-048 to TD-052 (3D physics), TD-029 and TD-068 to TD-070 (visual

scripting), TD-031 (animation), TD-035 (game runtime), TD-038 (sculpt), TD-040 (music), TD-066 and TD-067 (2D tools

and game modes), TD-082 and TD-083 (terrain and the level editor). None is changed by this roadmap. Any phase that would change one needs a new record first.

---
6. Rules every phase follows
1. Status words (AGENTS.md §9): implemented, integrated, validated mean what they say. An agent that cannot

   see the screen writes "Manually Not Verified (Headless Environment)".

2. Tests ship with behavior (AGENTS.md §8). Pure logic goes in headless modules; GL code is listed as "needs

   graphics test".

3. Editor changes are commands so undo and redo work (AGENTS.md §7).

4. Editability audit stays green (§2). A phase that adds a component, asset type or sample adds its editor.

5. Components get a ComponentSerializer and a round-trip test; every new asset type gets a Content Browser

   entry, thumbnail, opening editor and drag-and-drop behavior (the .song bug shows what happens otherwise).

6. Web parity is recorded: each phase states what the web player does and does not support, in the matrix.

7. Each phase ends with a report in docs/reports/ (AGENTS.md §10): criteria met or not met, commands run,

   per-module test counts, known limits, and a statement that no commit or push was made unless authorized.

8. Agent parity. A phase that adds an editor operation also adds its AgentTool (typed, validated, returning

   machine-readable outputs) and a test that drives it. R10-A audits this; a phase without its tools is not done.

---
7. Decisions needed from the owner
Before work starts, these need an answer; none is assumed here.

1. Order. Approve R0 and R1 first, as recommended? Or start a larger area (R3 or R4) in parallel?

2. New modules. Approve in principle engine-material, engine-terrain, editor-terrain, engine-strategy,

   editor-strategy? (AGENTS.md §4: creating a module is an architectural change.)

3. Scenario format import. Should R4 read community-documented scenario files (.scx, .scn, .cpx) per

   [swgb.md](swgb.md), or only use an original format? Importing is useful for testing but raises asset and legal

   questions that need your decision.

4. Strategy scope. The original editor's depth (hundreds of trigger types, multiple civilizations) is large.

   Is the target a complete strategy toolset, or a smaller core (players, objects, triggers, objectives,

   campaign) first? This roadmap assumes the smaller core first.

5. Targets. Desktop and web are covered today; mobile, console and VR are not in this roadmap. Say if any should

   be.

6. Legacy cleanup. Remove the unused core/ and lwjgl3/ template folders (needs approval, AGENTS.md §4).

7. Phase naming. This roadmap uses R0 to R10 so as not to collide with the old Phase 9 to 20 numbering. Rename if

   you prefer.

8. AI providers and privacy (R10-E). Local models only, or also optional cloud providers? If cloud: who supplies

   keys, and is any project content allowed to leave the machine by default (this roadmap assumes no)?

9. AI autonomy (R10-B). Must the user approve every plan before it runs (assumed), or may a "trusted" mode apply

   small plans directly? Either way every prompt stays one undo step.

10. AI-written scripts (R10). Review before running (assumed), or graphs only? TD-019 scripts run as trusted

    in-process code, so this is a security decision.

11. World View naming and play mode (R3b). Rename the Viewport view to "World"? Should the Play/Test Game Mode

    default to the scene's own mode and be overridable at Play without changing the scene (assumed), or replace it?

12. Terrain collision order (R3b). Build terrain collision (Jolt height field) before the first genre slice

    (worlds made with the level editor become walkable), or after it (the first slice uses box and mesh colliders)?

---
8. Risks
- Scope. Seven reference tools is more than a small team can match. Mitigation: ship the workflow loop of each

  (make, edit, play, export), not feature parity; keep the editability audit as the quality bar.

- Unverified base. Mitigation: R0.

- Performance. Large terrain, scatter and many RTS units stress the LibGDX renderer and Jolt. Mitigation: R9

  baselines before R3, and instancing and LOD designed in, not added later. AGENTS.md §7 keeps rendering on LibGDX

  facilities; a different backend needs a feasibility study.

- Two backends. Java desktop and Babylon web already drift (the web player ignores mesh colliders silently).

  Mitigation: the support matrix in R9 and a rule that no feature is "done" without its web status written down.

- Genre x dimension explosion (R3b). Eleven genres times three modes is thirty-three combinations. Mitigation: a

  registry, one tested slice at a time, and a rule that only combinations whose tests pass are shown as playable.

- AI quality and trust. Model output varies and small local models are weak at multi-step design. Mitigation:

  tools that validate every argument, plan preview, one-step undo, the self-checking loop, the R10-F suite that

  reports honestly where each model fails, and templates so the assistant customizes instead of inventing.

- AI safety. An assistant that can write scripts and touch files is a risk surface (TD-019 code runs in-process).

  Mitigation: the R10 safety rules; scripts reviewed before they run; project-folder-only tools.

- Single-person bus factor. Mitigation: reports and TDs per phase; the checklists in

  [visual-check-checklist.md](reports/visual-check-checklist.md) kept current.

---
9. What this document does not do
It does not start any milestone, change code, approve a decision, or claim any listed item exists unless §3 says so.

Sizes are guesses. The "not found in the code" list is from a keyword search, not a full audit; Phase R0 and R1

confirm it.

No commit, push or other Git operation was performed in producing this file.