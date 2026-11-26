# urGameForge — authoritative milestone roadmap

Revision: 25 November 2026 | Target: unified Desktop + Web Alpha 0.1 | Status: owner-directed roadmap; implementation approvals remain required.

## 1. Authority and product direction

This file (`docs/milestone-roadmap.md`) is the final source of truth for product scope, milestone ordering, dependencies and release acceptance. It supersedes conflicting sequencing and desktop-only release scope in the 15 November master roadmap and prior Alpha plan. Preserve those documents as history, and preserve approved TDs and AGENTS.md governance. milestone-chart.md and FEATURE_TEST_MATRIX.md become evidence/status registers subordinate to this roadmap, not competing plans. Do not silently override technical decisions: raise a TD for approval. Record changes here before changing scope. Existing milestone IDs and evidence remain traceable.

ForgeRoom evolves into **urGameForge**, with a complete Java 21 / LibGDX desktop authoring studio and a streamlined Babylon.js web studio integrated into UrConnex. Both must deliver the same World-first creation journey, not identical feature sets. Desktop remains offline-capable and independently buildable; cloud integration is optional for local editing. Weskaap is an authored project, not an engine dependency. Preserve existing engine code and avoid a big-bang rename of packages or file formats.

The owner has placed a screenshot of the proposed UI inside `docs/`. This screenshot is the authoritative visual target. Windsurf must locate it, record its exact relative path and hash, inspect it visually, and compare actual desktop and web screenshots against it. Do not substitute a generic IDE layout or invent a screenshot filename. If multiple references exist, ask which is authoritative. The screenshot is not available to the author of this revision, so precise pixel measurements must be verified by Windsurf. Adapt controls to browser dimensions without losing the workflow. No copied AOE assets, branding or code.

**Authoritative UI reference (confirmed by the owner, 2026-11-25):** `docs/Screenshot 2026-11-25 113907.png`, 781 x 466 px, SHA-256 `adb72eaa61a5928d47299f8cf3b7ba572dd8b45ecff3856c2fa4fea9830d31b9`. Its layout:
- a thin header with New, Open, Save, Undo, Redo and Settings;
- a dominant isometric map with overlays: "World / Main Map", a Top / Iso / Perspective chip, a minimap and a selection card;
- a bottom category bar: Map, Terrain, Objects, Buildings, Characters, Players, Rules;
- a contextual palette row with an Owner selector;
- a footer with Genre, Mode, Customize and Play.

The earlier reference `docs/ForgeRoom Isometric World Editor.png` (2026-11-19; what UI M1 to UI M8 implemented) is kept as history and is no longer the target. Comparison: [reports/alpha-a01-ux-audit.md](reports/alpha-a01-ux-audit.md).

## 2. Evidence and current baseline

Sources: 15 November Master Roadmap (R0–R10, Appendices E–G); 25 November Current Status; previous Alpha roadmap; owner's latest direction. Reported branch phase-12-3d-foundation, HEAD b8bace5, approximately 170 uncommitted paths; verify before work. R1b and UI M1–8 built but human acceptance missing. R3 terrain and R7a mesh editing built. Seven of 33 genre/mode pairs reported playable; R4 scenario/campaign not started. MC.5/6 and M3c.9 audio blocked. Babylon player 520/521 tests; AtmosphereComponent unsupported, depth ordering gap, no genre profiles; TeaVM broken. WEB authoring phase not started in desktop report; separately deployed web editor capabilities must be audited rather than inferred from player tests. No human validation reported. Full Gradle suite not freshly run. Preserve these distinctions.

## 3. Unified Alpha scope

World dominates the screen, compact top project commands, bottom Map/Terrain/Objects/Buildings/Characters/Players/Rules categories, contextual palette, optional minimap, independent camera orientation, Game Genre and Game Mode, adjacent Customize and Play/Stop. Advanced panels hidden by default but reachable. Proposed viewport target >=70% usable area with drawers closed at 1280x720 and 1920x1080; verify against screenshot and owner approval. No fake controls. Desktop supports actual mesh and sprite editing; web supports streamlined scene/terrain/object/scenario editing and basic customization, with explicit desktop handoff for unsupported advanced operations. Preserve unsupported data on round trip. Full visual scripting authoring remains desktop-only; web may execute verified portable rules. Both platforms must create, edit, play supported profiles, save/reopen and publish or export through verified paths. Desktop standalone export required; web playable published preview required. Do not promise all 33 profiles, full RTS simulation or autonomous prompt-to-game at Alpha.

## 4. Architecture contract

- Desktop: existing Java/LibGDX/LWJGL, Jolt/Box2D; local project and command system.
- Web: existing Babylon.js, UrConnex Next.js frontend; do not port native LibGDX to browser merely because server has GPU. Audit existing web editor separately from engine-babylon player.
- Shared: versioned engine-neutral project schema, GLB/glTF assets, stable IDs, terrain, scene, prefab, materials, scenario rules, profile metadata, provenance and capability manifests. Adapters for each engine; no assumption of identical physics or shaders.
- UrConnex Spring Boot API owns authenticated project access, tenant authorization, quotas/credits, revision storage and job submission. PostgreSQL metadata and managed asset storage; reuse existing infrastructure after audit. No direct browser or desktop access to database, Ollama or private harness.
- UDX AI (owner terminology; confirm actual service name and contract) is an independent shared AI service, not embedded into either renderer. Reuse UCX AI/TrueForge gateway where verified, with provider-neutral interfaces and future independent LLM. Both editors consume the same typed game-authoring operations through validated plans. Local desktop remains usable without AI or network.
- GPU VPS reported RTX 4000 Ada 20 GB, 32 GB RAM, 8 vCPU. Verify current capacity, workloads and isolation. GPU serves inference, asset generation/processing and optional offline rendering; browser renders locally. No assumption of unlimited concurrency or cloud streaming. Native remote rendering is post-Alpha research requiring offscreen graphics context and WebRTC, not a headless LibGDX shortcut.

## 5. Milestone sequence and dependencies

A00–A10 retain previous desktop acceptance scope; A11–A20 add mandatory shared/web/cloud/AI integration. A11 design starts after A01, before new serialization changes; A12–A15 may proceed alongside desktop work after approved contracts. A16–A19 require verified contracts and isolated environments. A20 requires all mandatory gates. Each gate needs explicit approval, evidence and report; no automatic progression through gated changes.

| Gate | Objective | Dependency | Initial status |
|---|---|---|---|
| A00 | Baseline protection | none | audit done 2026-11-25 ([report](reports/alpha-a00-baseline.md)); checkpoint approval and a fresh full test run outstanding |
| A01 | Screenshot-led desktop/web UX audit | A00 | audit done 2026-11-25 ([report](reports/alpha-a01-ux-audit.md), [proposals](reports/alpha-a02-a11-a13-proposals.md)); reference confirmed; gap list awaiting owner sign-off |
| A02–A03 | World shell, terrain and placement | A01, A11 contract | A02: TD-107 **approved** 2026-11-25; World layout implemented and integrated (default; classic selectable), [report](reports/alpha-a02-world-shell.md); **owner walkthrough pending**. A03: in progress — audit, copy/paste, placement ghost preview, [report](reports/alpha-a03-map-terrain-placement.md); horizontal palette row visible in desktop captures; Map size presets and view lighting integrated; hands-on walkthrough pending |
| A04–A05 | Players, triggers, objectives | A03, A11 | open |
| A06–A08 | Customize, Play, editable templates | A02–A05 | partial |
| A09–A10 | Desktop package and human acceptance | A08 | pending |
| A11 | Shared architecture/schema TD | A01 | done 2026-11-25: TD-106 approved in principle ([report](reports/alpha-a11-shared-contract.md)) |
| A12 | Cross-engine project adapters | A11 | **met in automated tests 2026-11-25** ([final report](reports/alpha-a12-final-verification-report.md)); TD-106 approved in principle |
| A13 | Babylon World editor UX | A01,A11 | audit/reuse |
| A14 | Web authoring and Play | A12,A13,A04–05 | open |
| A15 | Cross-platform round trip | A12,A14,A06 | open |
| A16 | UrConnex project integration | A11,A15 | open |
| A17 | GPU jobs and operational safety | A16 | open |
| A18 | Shared AI command contract and gateway | A11,A16,A17 | open |
| A19 | Bounded AI-assisted game creation | A18,A14,A07 | open |
| A20 | Unified human acceptance and release | A09–A10,A15–A19 | blocked |

### A00 — Protect the baseline (R0, R9)

- Verify branch `phase-12-3d-foundation`, reported HEAD `b8bace5` and actual working tree. S2 reports about 170 modified/new paths; inventory tracked/untracked changes, generated files, assets, secrets and unrelated work. Preserve all work; no reset, clean, stash or overwrite.
- Prepare a scoped checkpoint commit proposal with file inventory and test evidence; obtain explicit authorization before staging/committing. Do not assume the commit title “Forgeroom alpha” is release evidence.
- Reconcile milestone-chart.md, FEATURE_TEST_MATRIX.md, template audit and BACKEND_SUPPORT_MATRIX.md. Revisit obsolete glTF “no mesh editor” audit rule with capability-specific tests; unsupported skinned/extension cases must still fail honestly.
- Run full Gradle suite with `--continue --max-workers=1` as resources permit; use Windows wrapper and foreground batches on the 16 GB machine. Record per-module counts and skips, never merge stale results as a fresh full run. Run Babylon tests using actual project scripts.
- S2 baseline: editor-core 616 tests/1 skipped/0 failures; engine-desktop 162 before seven additional passing tests; engine-render 213 before latest edits; editor-desktop not fully rerun; Babylon 520/521. Investigate AtmosphereComponent failure; either implement support or obtain approval for explicit unsupported classification and test that degradation is safe. TeaVM remains broken; document as unsupported for desktop Alpha if approved, not a hidden failure.
- Deliver `docs/reports/alpha-a00-baseline.md`. Done: recoverable checkpoint strategy approved, failures classified, no unexplained critical regressions.

### A01 — Reopen the World-first UX acceptance gate (R1b, M1b.5, UI M1–8)

- Launch the actual editor and capture current default World at both target resolutions. Locate owner reference, compare visual hierarchy, viewport size, navigation, drawers, selection, palette, footer and startup flow. If headless, report manual evidence unavailable; do not invent screenshots.
- Inventory every existing command and its destination; identify duplicate toolbars, permanent panels, dead controls, developer-centric labels and excessive steps. Trace actual UI composition and input routing before proposing changes.
- Produce annotated before/after screenshots or clearly labeled wireframes, feature preservation matrix, supported actions, and proposed TD if navigation contract changes. Obtain owner sign-off on layout before coding.
- Done: owner approves target and specific gap list; no claim that prior R1b implementation failed technically merely because UX acceptance remains open.

### A02 — Correct the World shell

- Reuse existing UI framework, World renderer, commands and drawers. Make New/Open/Samples lead directly to World. Thin header, context indicator, dominant viewport, one active category palette and persistent Genre/Mode/Customize/Play footer.
- Add context-aware category switching and collapse/close behavior; advanced tools remain discoverable. Preserve shortcuts, camera controls, selection, undo/redo and save state. Top/isometric/perspective camera choice independent of genre/dimension; use appropriate 2D camera.
- Tests: layout at target sizes/scaling, no overlap, keyboard focus, drawer input isolation, command reachability, no selection loss, no duplicate commands. Human: unfamiliar tester identifies terrain, object placement, Play and Customize without guidance. Record time/steps/friction, not invented usability scores.
- Done: approved actual screenshots and owner walkthrough, not merely headless widget tests.

### A03 — Map, terrain and placement (R2b/R3)

- Map: new map, dimensions/presets, grid/snap, camera navigation and lighting settings; expose only supported settings. Terrain: raise/lower/smooth/flatten, paint, brush size/strength and undo per stroke; contextual water/roads/rivers/erosion only if verified. 2D: tile painting and collision-aware placement.
- Objects/Buildings/Characters: searchable thumbnails, local starter assets, optional provider downloads, ghost preview, valid placement, rotate, move, delete, duplicate, multi-select/copy/paste where supported; stable IDs and collision. Preserve original assets, project-owned derived copies, license manifest and offline cache. Verify realistic tree, rock, house and character or clearly label limitations.
- Tests: terrain save/reopen, undo/redo, collision on modified terrain, repeated placement, invalid placement, missing assets, offline startup, prefab overrides. Human: create valley, paint two surfaces, place tree/rock/house, select and reposition.
- Done: actual World workflow with no JSON edits; report unsupported tools rather than fake them.

### A04 — Players and scenario setup (small R4 slice)

- Audit existing components, graph runtime, player spawn and scene serializers. Propose TD for original scenario data model and ownership semantics; prefer existing modules unless new module approved.
- Minimal player slots (at least two plus neutral if supported), name/color, object ownership, spawn assignment, map bounds and scenario metadata. Contextual player selector in placement palette; ownership visible in selection and persisted. No implication of working diplomacy, economy or unit AI.
- Tests: player ID stability, assignment/reassignment, deletion safeguards, neutral objects, two players, duplicate names/invalid references, undo/redo and round trip. Human: place two groups of objects and change ownership.
- Done: editable scenario setup survives save/reopen; unsupported runtime semantics clearly labeled.

### A05 — Triggers and objectives (small R4 slice)

- Reuse visual scripting and validated commands. Provide approachable Rules drawer: trigger name, enabled state, condition, effect, objective text and status. Advanced graph editor remains in Customize.
- Proposed minimum: timer and player-enter-area conditions; show message, mark objective complete and end scenario effects. Add spawn effect only if safe existing runtime operation supports it. Provide visible validation, deterministic execution order, once/repeat semantics and bounded execution; no arbitrary code execution from scenario data.
- Store rules as original versioned data/graphs after TD approval; references stable, undoable and editable. Show trigger execution log in Play, not default World.
- Tests: timer, area enter, one-shot/repeat, invalid references, objective completion, serialization, runtime isolation, bounded loops. Human: create a reach-target objective and timed message, test both and edit without code.
- Done: at least one genuinely playable scenario with observable rules and objectives. Full RTS selection/orders, pathfinding at scale, economy, fog, campaigns and random map scripts remain post-Alpha. Do not label RTS profile playable unless its own runtime acceptance passes.

### A06 — Customize and return to World (R7a, MC)

- Adjacent Customize/Play always visible. Selection-aware chooser: house → 3D Modeling; sprite → 2D Studio; character → available animation/logic; asset/material tools as supported. Allow entry without selection. Back to World restores project, camera, selection and pending edits; no silent discard/commit.
- Reuse M7a.1–6: vertex/edge/face editing, supported geometry operations, three save modes, project-owned derived model default, explicit shared asset update and unique instance choice. Rebuild render/collision as needed. Verify actual geometry change, not transform-only change.
- Tests: workspace navigation, mesh and sprite edits, undo/redo, shared vs unique semantics, source immutability, save/reopen, next Play uses changes. Human: change house wall/roof, return and replay; edit sprite frame and replay.
- Done: short, predictable owner-approved edit loop. Full material node editor, rigging and UV suite not mandatory for proposed Alpha; basic material properties used by shipped templates must be editable.

### A07 — Play/Stop and scenario runtime (R3b/R3c)

- Freeze profile expansion. Verify seven reported pairs: Platformer 2D/2.5D/3D; Action RPG 2.5D/3D; FPS 3D; TPS 3D. Advertise only combinations that pass actual acceptance; remaining pairs planned. Existing shooters lack health/enemies/ammo/mouse look; do not market complete shooter games.
- Reuse GameTestProfile, PlayModeController, Jolt/Box2D, camera/controller/input. Validate player/spawn, collision, camera and scenario rules before Play; runtime-only components never saved. Stop restores exact authoring scene, selection, camera and undo history. Scenario rule changes must not leak back.
- Test real physics, profile-specific camera/input, terrain collision, trigger execution, invalid prerequisites, repeated Play/Stop, runtime disposal and memory. Human keyboard play of every advertised pair; prioritize three reference projects. Distinguish walkable Action RPG from complete combat.
- Done: verified profiles and scenario objective loop; unsupported profiles refused with actionable explanation.

### A08 — Curated templates and audio gate (R1, Appendix F, MC/R8)

- New Game gallery: Play Sample and Use as Template; independent project copy, original immutable. Prioritize 3D Action RPG valley, 2D Platformer, 2.5D hybrid side-scroller. Use original/licensed assets and editable source. Existing samples preserved; incomplete examples labeled INCOMPLETE/DEMO, not fully editable templates.
- Audit every domain actually used: terrain, mesh, sprite, material, graph, animation, physics, UI, audio, export. Essential gameplay hidden solely in Java/Kotlin is a gap. Correct template audit using real mesh capability. Verify CC0 2D character gate (M2b.5) or use verified original editable sprite with owner-approved exception.
- Audio decision: S2 says M8.1 human listening, M8.5 TD/dependency/Legal, M8.6–7 precede MC.5–6 and M3c.9. Either close these gates and validate audio scenario D, or obtain explicit Alpha exception and label audio limitations; do not advertise complete audio authoring or silently waive Appendix F. Existing Music Studio remains accessible.
- Done: three reference projects meet all applicable Alpha editability criteria or are explicitly classified incomplete; owner approves any reduced scope. Full template library and audio production remain master roadmap obligations.

### A09 — Persistence, packaging and regression (R0/R9)

- Verify project portability, save/close/reopen, stable asset references, migrations/backward compatibility, missing asset errors, scenario metadata, undo and Play isolation. No destructive conversion. Validate source ownership and offline starter assets.
- Build Windows desktop package using existing packaging scripts. Launch clean package on real Windows, create/open project, export standalone game and launch it outside editor. Verify actual controls and rules; artifact existence alone is insufficient. Document runtime dependencies, install/uninstall and rollback.
- Full Gradle and relevant Babylon regression, asset audit, performance baseline on owner hardware; record startup, frame time, memory and scene load, compare with A00. Propose performance thresholds from measured baseline before release; do not invent FPS results. Record web limitations (no genre profiles, depth ordering, atmosphere decision, TeaVM broken) and no unsupported web parity claim.
- Done: no unresolved Alpha-critical data loss, crashes, security/license defects or broken core workflow; known noncritical limits owner-approved.

### A10 — Desktop human acceptance (not the unified release gate)

Owner or unfamiliar human tester runs packaged editor; agent screenshots and automated tests are supplementary, not human validation.

| Journey | Required observed result |
|---|---|
| A: 3D Action RPG | New map → sculpt/paint → tree/rock/player/spawn → Play/walk/collide → Stop → house → Customize geometry → Back → Play → save/reopen/export/launch |
| B: 2D Platformer | Tiles/platform → editable sprite → Play/jump/collide → Stop → edit frame → replay → export/launch |
| C: 2.5D hybrid | 3D scenery + sprite → side camera/locked depth/collision → Stop → edit model/sprite → replay → export/launch |
| E: Scenario authoring (new Alpha gate) | Two player slots → assign objects/spawn → area objective + timed message → Play and observe rules → Stop → edit rule → save/reopen → replay |
| D: Audio (conditional) | Edit notes and SFX → bind scene event → listen in Play → save/reopen/export; or documented owner-approved Alpha exception |

Capture dated screenshots, actual tester observations, test project paths, exported artifact hashes, build version, test logs and limitations. Record PASS/FAIL/BLOCKED/N/A per step; distinguish Implemented, Integrated and Validated (AGENTS.md §9). No desktop acceptance if A/B/C/E core journeys fail or remain unobserved. Owner signs desktop acceptance; unified release requires A20. Proposed version `0.1.0-alpha.1`; do not tag automatically.


## 6. Shared platform and ecosystem milestones

### A11 — Architecture and compatibility contract
- Inventory actual desktop serializers, Babylon reader/editor, UrConnex routes, AI gateway and approved TDs. Distinguish implemented from planned. Design versioned manifest and schema, units/axes, transforms/quaternions, terrain encoding, asset references, material/animation support, scenario rules and profile capabilities. Use existing formats where feasible; do not create a competing format without migration plan.
- Define stable IDs, unknown-field preservation, extension namespaces, schema migration, asset hashes, relative paths, limits, compatibility negotiation and loss reporting. Reject path traversal, unsafe archives and unsupported mandatory features. No silent conversion.
- Define local vs cloud ownership, explicit import/export, revision conflict handling, offline edits and reconnect. No automatic overwrite or sync of private projects. Approve TD before implementation.
- Done: reviewed schema, example fixtures, capability matrix and API/AI boundary contracts.

### A12 — Desktop and Babylon adapters
- Implement readers/writers against A11, preserving existing projects and legacy import. Round-trip canonical fixtures for terrain, house, sprite, prefab, player, material, trigger and profile. Preserve unsupported fields opaque and report them; refuse destructive save if preservation impossible.
- Validate transforms, scale, hierarchy, references, asset hashes, unknown fields, malformed files, version migration and deterministic serialization. Compare semantic equality, not byte equality or pixel-identical physics.
- Done: desktop -> web -> desktop and reverse round trips without data loss; actual render/scene evidence.

### A13 — Screenshot-led web World shell
- Locate owner screenshot and capture actual web editor at desktop browser sizes. Reuse Babylon canvas and existing Next.js routes/components; keep UrConnex navigation compact and project context clear. Map same categories and Customize/Play adjacency as desktop. Responsive drawers, pointer/touch input, focus, resize, keyboard and accessibility. Advanced unsupported tools show desktop handoff, not fake editors.
- Separate browser editor from exported web player. Do not modify unrelated AR Worlds, media tools or UrConnex pages. Test browser layout and actual canvas interactions, not static screenshots alone.
- Done: owner approves desktop and web before/after screenshots; unfamiliar user finds terrain, placement, Customize and Play without guidance.

### A14 — Web scenario authoring and playable preview
- Reuse actual Babylon terrain/asset features; implement missing map, sculpt/paint, place/move/rotate/delete, selection, undo/redo, player ownership/spawn, basic triggers/objectives and persistence. Support project-managed GLB and licensed starter assets. Implement basic material/instance customization; preserve advanced desktop mesh/graph data.
- Define web-supported profile subset; propose 3D walkable Action RPG and 2D or 2.5D platformer for Alpha, subject to owner approval and real browser tests. Existing web player runs no profiles per report; implement and validate rather than advertise parity. Separate camera mode and game mode. Runtime copy, Stop restoration, collisions, input, rule execution and error handling. No untrusted Java/Kotlin execution in browser or server.
- Done: browser-only user creates a small playable scenario without desktop or manual JSON, saves/reopens and launches preview; unsupported combinations clearly labeled.

### A15 — Cross-platform authoring journey
- Create desktop project, open web, edit terrain/place object/change rule, save new revision, reopen desktop, edit house geometry, reopen web and Play. Reverse journey starting web. Preserve source assets, unknown data, profile metadata and references. Verify offline editing and explicit conflict resolution; no last-write-wins data loss.
- Test incompatible versions, missing assets, concurrent revisions, cancelled upload, interrupted save, large asset limits and recovery. Verify semantic project equality and visual/runtime behavior on both engines.
- Done: both journeys pass with actual projects and documented support matrix.

### A16 — UrConnex ecosystem integration
- Coordinate with UrConnex agent through approved API contract; Windsurf may not modify other repos without authorization. Audit existing GameForge routes, account/tenant roles, subscription entitlements, media/asset library and publishing. Keep branding migration incremental; preserve links and legacy projects.
- Implement authenticated create/list/open/save/revision/download/publish flows, tenant-scoped asset access, quotas and ownership. Desktop optional sign-in using approved token flow, secure OS credential storage, no embedded secrets. Web uses existing session. Private by default; explicit publication and access checks. Collaboration beyond revision conflict handling deferred.
- Test cross-tenant denial, expired tokens, revocation, unauthorized publish, path traversal, upload validation, rollback, offline desktop and failed network. Use isolated test environment; test hostname alone is not proof of isolation. No production DB writes or deployment without approval.
- Done: authorized user can open same cloud project in both clients and publish a playable web preview; another tenant cannot access it.

### A17 — VPS GPU job foundation
- Read-only inventory GPU/VRAM, RAM, disk, current inference/services, queues and security. Reuse existing scheduler if verified. Design bounded asynchronous jobs with job IDs, ownership, progress, cancellation, timeout, retry/idempotency, quotas, cleanup and audit. Separate CPU tasks and GPU tasks; initially one heavy GPU job at a time until measured safe concurrency. Reserve capacity for existing AI and web workloads; no invented VRAM budget.
- Validate output files, provenance/licensing, asset size, malware/unsafe archive handling, temporary storage and tenant isolation. No arbitrary shell or model-generated executable code. No unapproved model downloads, driver changes, service restarts, firewall/SSH changes or production deployment.
- Done: isolated synthetic asset-processing job completes, cancels and fails safely; measured resource usage and contention report. Advanced 3D generation, rigging, animation and rendering providers may be staged later unless separately approved.

### A18 — UDX AI shared authoring interface
- Confirm actual UDX/UCX AI service identity, available endpoints, auth and readiness with responsible agent. Use provider-neutral gateway; no direct model keys in clients. Reuse existing TrueForge/agent registry only after verifying contract. Do not create a second independent production harness inside ForgeRoom.
- Define typed commands for inspect project, create scene, sculpt/paint bounded terrain, place prefab, transform, assign player/spawn, set profile, add objective/trigger, validate and preview. Reuse EditorCommand and existing AgentTool contracts; web adapter implements equivalent validated operations. Version commands and advertise capabilities per client.
- AI produces structured plan, dry-run diff, asset provenance, estimated cost and required permissions. User approves before mutation. Execute transactionally as one undo group, with revision precondition, cancellation, bounded retries and rollback. Reject unknown commands, arbitrary filesystem/network/shell, unsafe scripts, cross-tenant references and prompt injection from project content. No private project data sent to external provider without opt-in. No AI autonomous publishing.
- Test deterministic mock plans first; distinguish mocks from real inference. Local desktop editing works when AI unavailable. Integrate credits and tenant authorization at gateway, not renderer.
- Done: same safe plan contract works in both clients, rollback/undo verified, authenticated gateway contract tested. If live service unavailable, mark blocked, not complete.

### A19 — Bounded AI-assisted Alpha creation
- User prompt: create a small valley with trees, house, player spawn and an area objective. AI returns editable plan using supported commands; user reviews/applies; both clients can open project, edit manually, Play and undo AI change. Second prompt modifies selected house placement or terrain. Verify project revision and no unauthorized assets/actions.
- Run actual approved UDX AI service in isolated environment with bounded resources. Capture sanitized plan, tool results, validation, latency, resource and cost evidence. No fabricated successful model calls. If unavailable, mock demonstration is development-only; owner must explicitly revise release scope to defer live AI.
- This is limited assisted authoring, not R10 full autonomous game generation. Advanced RAG, self-correction, model training, voice, 3D generation, rigging and multi-agent workflows remain staged post-Alpha unless separately approved.
- Done: live bounded AI journey passes in desktop and web, manual editing and undo work, service failure leaves project intact.

### A20 — Unified Alpha release gate
- Human tester executes desktop A10 journeys, web A14 journey, A15 bidirectional round trip, A16 account/project/publish and A19 AI journey. Capture dated screenshots against owner reference, observed usability, actual build/version, artifact hashes and logs. Verify desktop offline and web cloud failure recovery.
- Fresh Gradle and Babylon/Next relevant tests, backend/gateway tests, cross-engine fixtures, security/tenant tests, performance measurements on target laptop/browser/VPS. Resolve Alpha-critical failures; document noncritical exceptions with explicit owner approval. No release on mock-only AI or screenshot-only UX unless owner explicitly changes scope here.
- Package desktop Windows first, deploy web to approved isolated staging first, validate rollback and backups. Production rollout/tag/publication separately authorized. Proposed 0.1.0-alpha.1. No release until owner signs both platform acceptance and shared integration gates.

## 7. Cross-phase rules and post-Alpha

Every phase: reuse existing code, preserve unrelated changes, typed commands and undo, serializers and tests, license/provenance, capability matrix, no fake controls. Record `docs/reports/alpha-aNN-<topic>.md`, exact commands/results/skips, screenshots, human status, changed files, TDs and blockers. Update this roadmap status and subordinate evidence registers. Preserve AGENTS.md approval gates. No staging/commit/push/deploy/new modules/dependencies/downloads without authorization. Do not claim human validation from automated screenshots. Suspend Racing + 3D and new genre expansion until unified Alpha. Retain master R0–R10 obligations: full template editability, audio chain, remaining profiles, full RTS/campaign, animator, advanced modeling/audio, cross-platform packaging and extensions. Full R10 autonomous harness remains final; A18–19 are explicitly bounded integration slices. Optional GPU cloud rendering, collaboration, voice, asset generation/rigging and independent LLM evolution are later milestones with separate TDs. Preserve desktop/web feature distinctions.

## 8. Immediate Windsurf task

Read AGENTS.md, this authoritative roadmap, master roadmap, milestone-chart.md, FEATURE_TEST_MATRIX.md, BACKEND_SUPPORT_MATRIX.md, latest reports and applicable TDs. Locate owner screenshot in docs and record path/hash. Begin A00/A01 audit only: inventory and protect working tree, inspect actual desktop and web editor (request access if missing), capture screenshots, compare to reference, map every existing command to target workspace, identify missing scenario features, audit existing project formats and UrConnex/UDX AI interfaces available in approved workspace. Produce A02/A11/A13 proposals and dependency/approval list. Do not modify source, stage, commit, push, download, create modules or deploy during audit. Do not assume access to UrConnex or VPS. Ask owner for explicit approval of baseline checkpoint and next implementation gates. No duplicate roadmap; update this file as the single release plan.

## 9. Decisions and blockers

Owner direction confirms unified web/desktop target, screenshot-led UX, UrConnex integration and shared AI. Screenshot confirmed 2026-11-25 (§1). Still confirm exact web repo/access, UDX service contract/name, web profile subset, Panel/workspace capability scope, schema TD, audio and template exceptions, Atmosphere handling, TeaVM exclusion, branding migration, GPU budget and test environment, commit and release authorization. Proposed Alpha AI is bounded assisted creation, not full autonomous R10. No dates estimated without measured velocity. Current unified release status: NOT READY; no live editor, web repository, VPS or AI service inspected in preparing this revision.
