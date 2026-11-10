# Forgeroom current project status — 9 November 2026

## Overall assessment

Forgeroom is a substantial engine and integrated creative studio prototype. The repository contains a desktop scene editor and standalone game runtime, 2D and 3D rendering and physics, scripts and visual graphs, animation tools, character creation, sculpting, music production, and browser implementations. It has moved well beyond the original scene-editor foundation.

The most defensible maturity description is **broadly implemented and integrated, with uneven validation and unresolved test failures**. It is not yet evidenced as a release-ready general-purpose engine. Many workflows have unit tests, launch smoke tests or captured still frames, but lack recorded hands-on acceptance, listening tests, platform coverage and performance measurements.

The immediate engineering need is consolidation: establish a trustworthy test baseline, finish acceptance of existing workflows, make desktop/web differences explicit, and reconcile status documents before adding more studios.

## Scope and evidence

- Analysis date: **2026-11-09**, Windows workspace.
- Starting HEAD: **`f9849c2`**. This report describes the **working tree**, including extensive pre-existing modified and untracked files, rather than only that commit.
- Sources: build configuration, runtime/editor integration points, component registrations, browser support lists and script registry, test sources and XML results, [technical decisions](../docs/TECHNICAL_DECISIONS.md), [feature matrix](../docs/FEATURE_TEST_MATRIX.md), [index](../docs/INDEX.md), [roadmap](../docs/ALL_IN_ONE_STUDIO_ROADMAP.md), [test strategy](../docs/TEST_STRATEGY.md), [AI audit](../docs/AI_AGENT_ARCHITECTURE_AUDIT.md), milestone plans and dated reports.
- **Implemented** means code with automated tests; **integrated** means reachable through editor/runtime code; **validated** requires evidence from the running application on a stated platform. Historical reports are attributed evidence, not new observations in this analysis.
- No application window, mouse workflow, game interaction or sound was observed during this analysis: **Manually Not Verified (Headless Environment)**.
- This is a status analysis, not milestone acceptance, an architectural approval, or authorization to begin another phase.

## Build and architecture

The main product is Java 21 with LibGDX **1.14.2**, Gradle wrapper **9.6.1**, desktop LWJGL **3.3.3**, gdx-gltf **2.3.0**, Jolt JNI **6.2.0**, and JUnit Jupiter. `lwjgl3Version=3.4.1` belongs to the unused template; it is not the desktop backend version.

[`settings.gradle`](../settings.gradle) includes **18 modules**:

| Layer | Modules | Current responsibility |
|---|---|---|
| Foundation | `engine-core`, `engine-scene` | Lifecycle, resources, asset identity, entities, transforms, serialization, script/component contracts, scene physics and audio interfaces |
| Rendering and simulation | `engine-render`, `engine-physics` | 2D/3D cameras and rendering, glTF/PBR, Jolt backend behind engine-neutral physics interfaces |
| Creative runtime | `engine-visualscript`, `engine-animation`, `engine-sculpt`, `engine-audio`, `engine-character` | Graph execution/code generation, clips/skeletons, sculpt geometry, music/SFX, character generation |
| Application targets | `engine-desktop`, `engine-web` | Standalone desktop game player and TeaVM browser runtime |
| Headless authoring | `editor-core`, `editor-visualscript`, `editor-animation`, `editor-sculpt`, `editor-audio`, `editor-character` | Commands, undo, editor state, project/script/export logic, asset editing sessions |
| Desktop UI | `editor-desktop` | scene2d UI shell, viewport/panels, creative tool integration and GL glue |

[`engine-babylon`](../engine-babylon/package.json) is a separate npm/TypeScript application using Babylon.js, Planck and Jolt WebAssembly. It is not a Gradle module. It implements its own browser editor and player; it does not execute arbitrary desktop Java scripts. `core/` and `lwjgl3/` remain outside the build. `engine-vector` and `editor-studios` are not included modules.

The architectural strengths are the separation of engine/editor logic, command-based editing, serializer-based Play isolation, project-relative assets, shared desktop serializer registrations, and the engine-neutral 3D physics API. These are useful foundations for continued development. This review sampled their integration; it did not exhaustively prove every dependency boundary or disposal path.

## Capability assessment

| Area | What exists now | Qualification / remaining gap |
|---|---|---|
| Scene and editor foundation | UUID entities, hierarchical transforms, versioned scene serialization, hierarchy/Details panels, selection, undo/redo, file operations, isolated Play/Pause/Stop | Strong tested foundation; full interactive regression acceptance remains necessary |
| 2D games | Shapes, sprites, orthographic cameras, Box2D bodies/colliders, scripted sample games and HUD text | Input is largely direct script/key access; a general action-mapping API is not evidenced by the matrix |
| 3D rendering | Perspective/axis views, primitives, glTF/GLB models, PBR, directional/point lights, directional shadows, sky lighting, exposure/tone mapping | No evidence of broad GPU/performance validation; documented limits include one directional shadow map, no cascades or point-light shadows, and limited primitive material controls |
| Mixed 2D/3D rendering | Perspective depth compositing in `SceneRenderer2D`, wired into the scene workspace and runtime | Captures and tests exist in earlier evidence; real sprites, editor Play and pane interaction still need acceptance |
| Content Browser | Folder navigation, filters, drag placement, texture/model thumbnails, periodic change detection/reimport | Empty folders are not represented; polling scans the project; large model previews are limited; live reload needs hands-on checks |
| Prefabs | Create/place, linked instances, overrides, apply/revert/unpack through editor actions | Nested prefabs, variants and a dedicated prefab-editing workflow remain gaps |
| Desktop 3D physics | Jolt-backed bodies, primitive/convex/mesh colliders, layers, sensors, queries, contact callbacks, forces/impulses, joints and capsule character controller | Windows evidence predominates; motion, controls and collider UI need acceptance; no joint break-force support in the documented implementation |
| Cameras and characters | Follow/orbit/first-person camera rigs; MakeHuman-derived CC0 character data, morph settings, rigged glTF generation and animation templates, live creator preview | Feature-rich integration exists; visual quality, interaction and export interoperability are not comprehensively accepted |
| Java/Kotlin scripts | Editor compilation, diagnostics, script bindings, lifecycle/contact APIs; precompiled scripts for desktop games | Trusted in-process code, not a security sandbox; generated Kotlin has a recorded compile failure described below |
| Visual scripting | Graph documents, validation/interpreter, node UI, undo, Java/Kotlin/TypeScript generation, embedded-graph interchange, reflected Java method nodes and sample graphs | Import restores embedded graph data, not arbitrary edited source; Java method nodes and some desktop-specific nodes lack browser execution parity |
| Animation | `.anim` clips, curves/tracks, Animator, skeleton FK/two-bone IK, Timeline, auto-key/preview, humanoid templates and clip library | This is not evidence of a complete DCC rigging suite; graph/Timeline workflows and imported-model animation need explicit acceptance |
| Sculpting | Fixed-topology brushes, symmetry, undoable strokes, `.sculpt` assets, sculpt-from-model, glTF export and a shared exported-rock sample | No full polygon-modeling/UV suite; exported sculpts are snapshots without UVs/textures; mouse sculpting and export UI need acceptance |
| Music studio | Synth, drums, sampler/SoundFont paths, SFX generation, sequencer, piano roll, playlist, mixer/effects and WAV export | Audio production core is substantial; demo-song failures remain; listening quality, latency and editing interaction need acceptance; Ogg encoding/export is absent |
| Scene audio | SFX cues, music sources, listener/spatial attenuation and pan, recorded WAV and Ogg playback code | Source is newer than matrix summaries: desktop `OggClips` uses the existing backend decoder; browser clip/spatial paths also exist. Audible correctness remains unverified |
| Desktop distribution | jpackage editor/runtime packaging, game-export logic, start-scene/project support, launchers, crash reporting, installer/signing and OS export layouts | Historical Windows launch/export checks exist; MSI installation, real certificate signing and Linux/macOS behavior are not established |
| AI assistance | Assistant panel, provider abstraction, Ollama/mock providers, structured plan parsing/validation/execution and editor tool contracts | The older AI vision exceeds the evidenced implementation. Do not equate this with autonomous end-to-end game creation, broad cloud-provider support or a public headless automation CLI |

Useful source anchors include [`GameComponentSerializers`](../engine-desktop/src/main/java/io/github/ldev22live/gdxunity/engine/desktop/GameComponentSerializers.java), [`PlayModeController`](../editor-core/src/main/java/io/github/ldev22live/gdxunity/editor/core/play/PlayModeController.java), [`GameSession`](../engine-desktop/src/main/java/io/github/ldev22live/gdxunity/engine/desktop/GameSession.java), and [`EditorApplication`](../editor-desktop/src/main/java/io/github/ldev22live/gdxunity/editor/desktop/EditorApplication.java). Registrations and UI wiring demonstrate reachability, not successful manual use.

## Browser strategy and parity

Three browser-related approaches coexist and should be tracked separately:

1. **TeaVM game runtime (`engine-web`)** compiles shared Java runtime code for the browser. Its [README](../engine-web/README.md) reports earlier checks of the original 14 games, scripts, graphs, Animators and glTF. Kotlin scripts are unsupported. This analysis did not rebuild the release site or establish parity with the latest desktop additions.
2. **Babylon.js editor/player (`engine-babylon`)** has scene editing, save/load, transform tools and undo, sculpt/music/animation/graph interfaces, ported gameplay scripts, models and browser audio. Its script registry still omits the original racing, arena-FPS, puzzle and fighter gameplay ports. New desktop script classes require explicit ports or another supported execution path.
3. **Streamed desktop editor** has container/session/deployment scaffolding. Historical documentation explicitly says the image was not built/run; scaffolding is not a validated hosting service.

Babylon's current `SUPPORTED_COMPONENTS` list includes primitive Jolt bodies/colliders, Animator/Graph components and audio sources/listeners/music. It does **not** establish support for desktop hull/mesh colliders, joints, character controllers, camera rigs, prefabs or textured sprites. The [TD-059 report](../docs/reports/td-059-web-physics-step1-report.md) records sandbox Play/Stop/restart in headless Edge; later physics stages remain separate work.

Browser sculpt scenes use `SculptMeshComponent`, whereas desktop sculpt authoring uses separate assets and glTF export. The README explicitly describes this interoperability limit. Shared JSON does not guarantee shared component behavior.

There are **32 scene JSON files** in `assets/samples` at review time, including the original 14 game templates and later landscape, physics, animation, character, graph and rendering demonstrations. This is useful breadth of fixtures, not evidence that all 32 are playable or validated on every target.

## Automated test evidence

Existing Java XML reports were inspected before the fresh run. They are a **mixed-date local artifact snapshot**, not proof of a single clean full-suite run against this exact working tree:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 0 | 0 | 0 |
| editor-audio | 55 | 2 | 0 | 0 |
| editor-character | 10 | 0 | 0 | 0 |
| editor-core | 373 | 0 | 0 | 1 |
| editor-desktop | 83 | 0 | 0 | 0 |
| editor-sculpt | 56 | 0 | 0 | 0 |
| editor-visualscript | 55 | 1 | 0 | 0 |
| engine-animation | 32 | 0 | 0 | 0 |
| engine-audio | 189 | 0 | 0 | 0 |
| engine-character | 19 | 0 | 0 | 0 |
| engine-core | 32 | 0 | 0 | 0 |
| engine-desktop | 84 | 0 | 0 | 0 |
| engine-physics | 53 | 0 | 0 | 0 |
| engine-render | 104 | 0 | 0 | 0 |
| engine-scene | 141 | 0 | 0 | 0 |
| engine-sculpt | 81 | 0 | 0 | 0 |
| engine-visualscript | 53 | 0 | 0 | 0 |
| engine-web | 9 | 0 | 0 | 0 |

The existing failures are meaningful:

- `BundledDemoSongTest.shouldRenderTheWholeDemoSongToAudibleStereoAudio`: `Cannot export: Lead: File not found:`.
- `BundledDemoSongTest.shouldOpenTheDemoSongWithItsRackPatternsAndPlaylist`: expected 5, actual 4.
- `GraphMethodNodeCodeTest.shouldMakeKotlinThatCompilesAndDoesWhatTheInterpreterDoes`: generated Kotlin has an **Int versus Float argument mismatch**. Diagnostics also mention missing scripting-plugin jars, but the explicit compile error means this should not be dismissed solely as an environment/classpath issue.

All three were present in existing reports before this analysis; no feature code was changed here. This does not establish their original introduction point or whether all other tests remain green on a fresh run.

Fresh commands and results on 9 November:

| Command | Exit | Result |
|---|---:|---|
| `.\gradlew.bat test --continue` (initial restricted attempt) | 1 | Wrapper could not create its lock directory under `C:\.gradle`; no tests ran in this attempt |
| `.\gradlew.bat test --continue` (with cache access) | 1 | Finished in 5m 2s; failed tasks were `:editor-audio:test` and `:editor-visualscript:test` |
| `npm run typecheck` in `engine-babylon` | 0 | TypeScript check passed |
| `npm test` in `engine-babylon` | 0 | **21 files, 314 tests passed**, 146.70 seconds; Node emitted experimental localStorage warnings |

After the Gradle invocation, the XML counts per module match the table above: **1,446 tests, 3 failures, 0 errors, 1 skipped** (1,442 passed). Before/after counts and failure totals are unchanged. Gradle may reuse up-to-date task outputs; these are the resulting XML totals, not a claim that every test was forced to execute again. No new failure was identified in this documentation-only task. No release build, deployment, packaging operation or graphics smoke run was performed for this analysis.

## Documentation discrepancies

The project has moved faster than its summary documentation. These discrepancies should remain visible until deliberately reconciled:

- `docs/INDEX.md` still ends with a Phase 12 summary saying the renderer submits no geometry and is not integrated. Current rendering/runtime code contradicts that.
- `docs/FEATURE_TEST_MATRIX.md` opens with a 563-test October baseline while later rows describe November work. Several early rows still say there are no thumbnails, reimport, songs/listeners/spatial audio or Jolt simulation, despite later entries and source implementations.
- The matrix still describes Ogg playback as absent and web spatial/WAV audio as unsupported. Current desktop `OggClips` and browser `clips.ts`, `spatial.ts`, `GameEntity` and `ScenePlayer` contain those paths. Ogg **export** remains a separate missing capability.
- `docs/INDEX.md` calls sculpt export planned and game sculpts invisible, while the later matrix row, exporter code and exported-rock sample establish glTF export/runtime use.
- The master roadmap's historical baseline and unchecked consolidated checklist list several existing systems as future work. It cannot currently serve as a reliable completion ledger.
- The index and Babylon README retain older 60-test/stage descriptions. The fresh browser suite now contains 314 tests, and its code has newer physics/audio/graph functionality.
- The AI architecture audit predates current Box2D, commands and assistant work. Its proposed kinematic physics and security language must not override approved decisions or the trusted in-process scripting implementation.
- The supplied AGENTS module table omits built `engine-web`, `engine-sculpt` and `editor-sculpt`; `settings.gradle` is the current build authority.

No approved decision was changed or silently superseded. This report records discrepancies without rewriting the user's existing status documents.

## Recommended priorities

These are recommendations for future authorized work, not changes started by this analysis.

1. **Make the baseline reproducible.** Fix or explicitly disposition the demo-song and Kotlin generation failures, identify the skipped test, run one full Java suite and browser checks, and record exact per-module counts and environment details.
2. **Accept existing desktop workflows.** Use the milestone checklists for files, selection/gizmos, Content Browser reimport, prefab edits, physics controls, character creation, Timeline/graphs, sculpt export and game export. Record actual interaction and listen to music/SFX/spatial clips.
3. **Publish one target-support matrix.** Distinguish desktop, TeaVM, Babylon player/editor and streamed editor. Track supported components, script languages, physics features, audio formats and known interchange limits. Unsupported functionality must be clearly reported.
4. **Reconcile documentation.** Refresh the matrix baseline, index and roadmap against source and dated acceptance evidence. Preserve historical reports as history rather than presenting them as current state.
5. **Harden distribution and performance.** Test a packaged editor and exported game on a clean Windows machine, then Linux/macOS if those are supported targets. Measure representative model, sculpt, physics and audio workloads; exercise installer/signing/deployment paths with the real tools.
6. **Finish selected gaps before expanding breadth.** Choose explicitly between browser physics parity, input mapping, asset/material workflow polish and deeper creative tools. Vector authoring, full mesh modeling/UV work and broader rigging should remain scoped future work until approved.

## Change control

Only this analysis document is intended as a source change. The extensive pre-existing working-tree work was not edited or discarded. Tests generated build outputs. No commit, staging, push, branch operation, reset, rebase, clean or stash was performed. No new milestone or implementation phase was started. Ending HEAD: **`f9849c2`**, unchanged from the start.
