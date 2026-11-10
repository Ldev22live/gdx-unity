# Forgeroom / gdx-unity — current status, 10 November 2026

## Assessment

Forgeroom has grown into a substantial game engine and integrated authoring environment. Its working tree contains a Java/LibGDX desktop engine and editor, two browser runtime approaches, scene and asset editing, physics, animation, visual scripting, audio production, sculpting, character creation, and sprite/tilemap tools. It is well beyond the original scene-editor foundation.

The appropriate description is **a broad development platform with significant implementation and integration, but uneven acceptance evidence**. The repository does not establish production readiness, complete platform parity, or completion of every listed phase. Automated tests, historical startup runs, screenshots, interactive use, audible output, and release validation are different evidence levels and must stay separate.

The strongest next investment is to establish a reproducible baseline and finish representative user workflows. Adding more studios before closing those gaps would increase the number of systems that must be kept consistent.

## Scope and provenance

- Analysis date: **2026-11-10**, on Windows, using the repository's existing working tree.
- Starting HEAD: **`f9849c2`**; branch: **`phase-12-3d-foundation`**. The branch name no longer describes the breadth of the working tree.
- Starting Git inventory: **155 modified tracked files and 252 untracked status entries**. Directory entries can contain many files, so this is not an untracked-file count. Changes already span assets, build files, engine/editor code, tests, and documentation; these are pre-existing work.
- This assessment uses current configuration, selected implementation paths, test sources/results, the decision register, feature matrix, roadmap, design brief, architecture audit, milestone pages, and dated reports. It is not a line-by-line audit of every subsystem.
- No application window, browser interaction, gameplay session, sound output, installer, container, or exported game was newly inspected in this task. Historical observations are attributed below.
- No feature implementation, dependency version, architecture decision, or milestone progression is authorized by this report.

## 1. Verified technical baseline

| Item | Current repository evidence |
|---|---|
| Product identity | Forgeroom; `gdx-unity` and `io.github.ldev22live.gdxunity` remain repository/package identifiers (TD-042) |
| Java | Source and target compatibility 21 in [build.gradle](../build.gradle) |
| LibGDX | 1.14.2 in [gradle.properties](../gradle.properties) |
| Desktop backend | LWJGL3; backend LWJGL property 3.3.3. The separate 3.4.1 property belongs to the unbuilt template |
| Gradle | Wrapper 9.6.1 in [wrapper configuration](../gradle/wrapper/gradle-wrapper.properties) |
| Model rendering | gdx-gltf 2.3.0; LibGDX-based rendering, not Unreal rendering technology |
| Physics | Box2D for 2D; jolt-jni 6.2.0 for desktop 3D behind `PhysicsBackend3D` |
| Java tests | JUnit Jupiter 5.10.0 in module build files |
| Browser Java path | `engine-web`, gdx-teavm 1.6.2, shared Java runtime classes |
| Browser TypeScript path | `engine-babylon`, Babylon.js, Vite, TypeScript, Vitest, Planck and Jolt JS |

[settings.gradle](../settings.gradle) includes **20 Gradle modules**: 12 engine modules and 8 editor modules. `engine-babylon` is a separate npm project. `core/` and `lwjgl3/` are not included in the Gradle build.

Inventory at analysis time: **493 Java main-source files**, **219 `*Test.java` files** across included modules, **82 TypeScript files under `engine-babylon/src`**, **30 top-level Vitest test files**, and **36 sample scene JSON files** under `assets/samples`. These measure repository breadth, not coverage or quality.

## 2. Architecture and maintainability

The main separation is sound: engine functionality lives in engine modules, editor commands and state live in headless editor modules, and the desktop shell supplies Scene2D/GL integration. Dedicated pairs now exist for animation, audio, sculpting, character creation and sprites, alongside visual scripting.

Build-file inspection shows no engine dependency on an editor module. A targeted Java import search also found no engine-to-editor imports. Direct jolt-jni imports in the inspected physics/runtime paths are confined to `engine-physics`; desktop runtime code constructs the engine wrapper rather than importing Jolt library types.

Important integration strengths:

- `GameSession` and `GameRenderer` provide a reusable game runtime rather than requiring the editor to play a game.
- `GameComponentSerializers` is shared with the editor, reducing divergence in scene component support.
- `GameSession.startPhysics3D()` checks `PhysicsSystem3D.isUsedBy(scene)` before creating the Jolt backend, keeping native 3D physics out of purely 2D sessions.
- Commands, undo histories, serialization and headless documents give many tools testable logic outside GL widgets.
- The browser player reports unsupported components and missing script ports explicitly through `SceneReport`.

Maintenance pressure remains substantial. `EditorApplication` coordinates many tools and lifecycle concerns, while the Babylon implementation independently maintains gameplay, physics, graphs, animation and audio behavior. Shared scene files are a useful interchange contract, but do not guarantee identical execution. Changes to a component, graph node, script API or asset format need explicit compatibility tests across supported paths.

`engine-web` depends on `engine-desktop` and excludes desktop backend dependencies for transpilation. This is an existing arrangement rather than a new violation, but it makes browser compatibility sensitive to changes in shared runtime classes. A passing JVM test for `engine-web` does not prove that TeaVM compilation or browser execution succeeds.

## 3. Capability assessment

“Integrated” below means there is a reachable implementation path. It does not mean that every interaction was observed in this assessment.

| Area | Evidence in current tree | Remaining acceptance or scope gap |
|---|---|---|
| Scene/ECS and persistence | Entities, transforms, component registry, versioned scene serialization, play-mode isolation and undo commands | Generic editor operations must preserve all supported components; see duplication finding below |
| Desktop authoring | Hierarchy, Details, Content Browser, menus, scene files, gizmos, multi-pane viewport and game-mode controls | Mouse workflows, layout persistence, gizmo/picking consistency and representative end-to-end projects need dated acceptance |
| Rendering/assets, Phases 12–13 | Primitives, glTF/GLB, PBR, lights, shadows, sky lighting, tone mapping; asset watcher and thumbnail paths | Shader/GL output, mixed 2D/3D depth, reload behavior and asset errors need graphics verification; material tooling remains limited |
| Prefabs, Phase 14 | Prefab assets, instance links, overrides, apply/revert/unpack and editor actions | Nested prefabs, variants and dedicated editing mode are not established by the current matrix; interactive acceptance remains open |
| 3D physics, Phase 15 | Desktop Jolt backend, bodies, colliders, queries, contact callbacks, characters, joints, hulls/meshes and camera rigs | Tests and startup evidence do not establish character feel, collision robustness under varied scales, or platform parity |
| Scripting | Java/Kotlin compilers, bindings and precompiled desktop game scripts | Trusted in-process execution is intentional (TD-019); browser language/runtime support differs |
| Visual scripting, Phase 16 / TD-068 | Graph interpreter, editor tab, commands, validation, method nodes, source generation/interchange, example graphs | Kotlin generation defect in existing reports; most samples still use Java; full component nodes, functions/macros/events, variables UI and interactive debugging are not established as delivered |
| Animation, Phase 17 | Curves/clips, Animator, Timeline, model clips, skeleton FK/two-bone IK and sample libraries | Timeline interaction, blending/authoring expectations and runtime appearance need verification; mathematical IK is not a full rigging studio |
| Character creator | `engine-character`, `editor-character`, CC0 body/morph data, glTF baking, humanoid templates and preview panel | Preview interaction, exported model appearance, animation and asset provenance should be checked as one workflow |
| Packaging, Phase 18 | Editor package tasks, bundled runtime, game launcher/export, icons, installer/signing tasks and platform layouts | Historical Windows export evidence exists; real MSI, trusted signing, Linux/macOS packages and menu-driven export remain incompletely verified |
| Sculpting, Phase 19 | Mesh/BVH/brush core, undoable editor strokes, dynamic rendering and glTF export code/tests | Manual brush behavior and export-to-runtime appearance remain open; exporting a model is distinct from direct `.sculpt` runtime support |
| Music/audio, Phase 20 | Synth/drums/sampler/SoundFont/sfxr, mixer, song editing, WAV export, scene music, positional effects and clip playback paths | No new listening test; real SoundFonts, editing feel, latency and runtime mix need acceptance; OGG writing remains a documented gap |
| 2D/2.5D, TD-066/067 | Sprite engine/editor, Sprites and Tiles tabs, animation, tilemap collision and per-scene game modes | Test correctness does not establish painting, selection, undo, tile collision and game-mode UX in the running editor |
| AI assistance | Six default tools, plan parsing/validation/execution, assistant service, Ollama/mock providers and panel integration | Limited compared with the full studio: no demonstrated automation coverage for all tools, no identified CLI entry point, and no implemented cloud provider found in the provider inventory |
| Babylon browser editor/player | Editor model, hierarchy/inspector, gizmos, scripting ports, Jolt physics, graphs, animation, audio, sculpt/sprite loading and deployment files | Manual TypeScript ports remain a compatibility obligation; registry lacks the racing/FPS/puzzle/fighter script families; current tests do not prove every scene is fully playable |
| TeaVM browser player | Included Gradle module, generated script registry and shared runtime integration | No fresh transpilation or browser run here; avoid transferring Babylon/Jolt-JS claims to this separate backend |
| Streamed editor | Docker/noVNC/session scripts and deployment documentation | Historical reports say the container was not built/run; security, resource limits, persistence and host integration need actual deployment testing |

Desktop tab wiring is directly visible in `EditorApplication`: **Viewport, Scene, Animations, Visual Scripting, Music, Sprites and Tiles**. Character creation is wired into the animation tooling. These are actual UI paths rather than roadmap-only modules.

## 4. Concrete findings and priorities

### High — generated Kotlin has an existing compilation failure

The pre-run XML for `GraphMethodNodeCodeTest.shouldMakeKotlinThatCompilesAndDoesWhatTheInterpreterDoes` contains an `Int`/`Float` mismatch. Generated code passes the integer result of `MathUtils.clamp(int,int,int)` into `formatNumber(value: Float)` without conversion.

Source inspection supports that diagnostic: [GraphCodeGenerator](../engine-visualscript/src/main/java/io/github/ldev22live/gdxunity/engine/visualscript/GraphCodeGenerator.java) emits `formatNumber(input(...))` for `text.fromFloat`; conversion helpers handle reflected method parameters/results but do not establish that every graph-link numeric widening is emitted. This is a generator typing issue, not evidence that all Kotlin scripting is broken. Keep the existing behavioral test and fix conversion at the typed connection/expression boundary in a separate implementation task.

### High — shooter conversion exposed test-harness integration gaps

The fresh full Java run fails two tests in [SampleGamesTest](../editor-desktop/src/test/java/io/github/ldev22live/gdxunity/editor/desktop/SampleGamesTest.java):

- `shouldFireAtEnemiesInTheShooter`: the assertion that the ship moved left fails.
- `shouldPlayEverySampleTheSameInTheGameRuntimeAsInEditorPlayMode`: `shooter-2d` diverges, including Ship positions `(320,70,0)` versus `(610,75.666664,0)`.

The shooter scene now binds `graphs/samples/ShooterShip.graph`. The first test's `load()` helper initializes Java scripts only and its `run()` helper updates the scene without binding graph instances. The parity test constructs a bare `PlayModeController` without the `GraphPlayModeExtension` that the actual `EditorApplication` registers. `GameSession` binds graphs, so the compared setups are unequal.

This is strong source evidence for stale test setup after graph conversion, rather than proof that the actual editor's ship is immobile. Repair the harness to exercise the real graph-enabled startup path and retain the movement/score/parity assertions. The two failures were absent from the pre-run XML snapshot; they are newly observed against older results, not changes introduced by this documentation task. No alternate revision was checked out, so the exact introducing change is not proven.

### High — duplication does not cover the scene component model

[DuplicateEntityCommand](../editor-core/src/main/java/io/github/ldev22live/gdxunity/editor/core/command/DuplicateEntityCommand.java) still copies a fixed `instanceof` list. It includes shapes, sprites, tilemaps, sprite animators, script bindings and selected 2D physics components, but has no corresponding branches for models, Graph Scripts or 3D rigid bodies/colliders.

Consequently, duplicating such an entity can omit the behavior or appearance the user expects. Serialization/Play cloning coverage does not establish editor Duplicate coverage. A future fix should use an explicit complete cloning contract, preserve supported fields and hierarchy semantics, and test representative model/graph/physics entities with undo/redo. The report does not change this behavior.

### High — verification is fragmented across subsystems and dates

The [12–17 validation report](reports/phase-12-17-validation-report.md) records startup/smoke results and explicitly withholds visual validation. Later [visual pass](reports/td-054-visual-pass-report.md), [layout](reports/td-055-editor-layout-report.md), and [creator preview](reports/td-056-creator-preview-report.md) reports add evidence, but do not close every manual workflow. The [visual checklist](reports/visual-check-checklist.md) should be used to record action, observed result, platform and remaining gaps.

### Medium — status documents contradict current implementation

| Document claim | Current evidence / discrepancy |
|---|---|
| Feature matrix header: 563 tests, last verified 2026-10-30 | Existing XML inventory already contains 1,593 tests across 20 modules before this task's run |
| Some matrix rows say no 3D physics backend/simulation | Jolt is integrated into runtime startup and has tests |
| Content Browser rows say no folders/thumbnails or automatic reimport | Later rows, asset watcher source and desktop wiring describe those additions |
| INDEX Phase 12 summary says renderer submits no geometry and is not wired | Current renderers and editor/runtime integration supersede that summary |
| INDEX repeats Babylon entries at different maturity levels | Current source and fresh 401-test result require a single dated backend-specific status |
| Roadmap §6 leaves engine-audio, workspace selection, 3D cameras/gizmos and Timeline unchecked | Corresponding modules and implementations exist; the checklist is still based on an old baseline |
| Sculpt milestone says no UI/rendering/export and no runtime path | Later editor integration and glTF export exist; direct sculpt loading and exported-model rendering must be described separately |
| Matrix “not yet covered” includes skeletal animation and packaging | Implementations exist; the outstanding question is scope and validation, not total absence |
| AI architecture audit describes missing early scene/editor capability and a proposed provider stack | Current tools, assistant service and Ollama provider exist; proposed cloud support is not implementation evidence |

No architectural decision is silently changed to resolve these discrepancies. Decision status belongs to [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md), not to an implementation's existence. Some older records remain Proposed despite implemented code; [the decision review](reports/td-status-review.md) discusses this, but is itself dated and should not be treated as an approval record.

### Medium — cross-backend parity needs a maintained contract

There are three execution targets: desktop Java, TeaVM Java-to-browser, and Babylon TypeScript/WASM. There are also two browser editor delivery approaches: native Babylon UI and streamed desktop UI. Tests/results for one cannot stand in for another. Track components, asset formats, script languages, graph nodes and sample behavior per backend, including unsupported cases and explicit errors.

### Medium — release reproducibility and performance evidence remain limited

No `.github/workflows` directory was found in this checkout; this does not rule out external CI. Historical package/container instructions are not a reproducible release run on every target OS. No fresh coverage report, memory/leak measurement, frame-time benchmark or large-project stress test was produced. Test totals alone cannot substantiate the design brief's coverage/performance goals.

## 5. Verification performed for this assessment

The baseline below was read **before** running tests. XML files initially came from earlier runs and were not represented as fresh results.

| Gradle module | Baseline XML tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| engine-core | 35 | 0 | 0 | 0 |
| engine-scene | 149 | 0 | 0 | 0 |
| engine-render | 119 | 0 | 0 | 0 |
| engine-visualscript | 56 | 0 | 0 | 0 |
| engine-animation | 32 | 0 | 0 | 0 |
| engine-desktop | 90 | 0 | 0 | 0 |
| engine-web | 9 | 0 | 0 | 0 |
| engine-sculpt | 81 | 0 | 0 | 0 |
| engine-audio | 189 | 0 | 0 | 0 |
| engine-physics | 53 | 0 | 0 | 0 |
| engine-character | 19 | 0 | 0 | 0 |
| engine-sprite | 44 | 0 | 0 | 0 |
| editor-core | 385 | 0 | 0 | 1 |
| editor-visualscript | 60 | 1 | 0 | 0 |
| editor-animation | 17 | 0 | 0 | 0 |
| editor-sculpt | 56 | 0 | 0 | 0 |
| editor-audio | 55 | 0 | 0 | 0 |
| editor-character | 10 | 0 | 0 | 0 |
| editor-sprite | 51 | 0 | 0 | 0 |
| editor-desktop | 83 | 0 | 0 | 0 |
| **Total** | **1,593** | **1** | **0** | **1** |

Command results:

| Command and working directory | Exit/result | Interpretation |
|---|---|---|
| `git status --short`, `git rev-parse --short HEAD`, root | 0 | Existing work recorded before writing |
| `git diff --stat`, root | 0, line-ending warnings | 155 tracked files already changed; no staging or content rewrite performed by this inspection |
| `.\gradlew.bat test --continue`, root, sandboxed attempt | 1 | Wrapper could not create its lock directory under `C:\.gradle`; no test outcome from this attempt |
| Same Gradle command, retried with approved cache access | **1 — build failed in 4m 17s** | 2 failing test tasks; normal incremental run, not a clean rebuild or forced rerun |
| `npm run typecheck`, `engine-babylon` | **0** | TypeScript check passed, independently confirmed |
| `npm test`, `engine-babylon` | **0 — 401 tests, 30 files passed** | Fresh Vitest run; reported duration 55.57 seconds |

Vitest emitted Node experimental `localStorage` warnings; these did not fail the tests. No npm install, dependency upgrade, browser build, package build or deploy was performed.

**Final Gradle XML aggregate: 1,593 tests, 3 failures, 0 errors, 1 skipped; 1,589 passed.** Per-module test totals, errors and skips are unchanged from the baseline table. Fresh failures are **editor-desktop: 2** (baseline 0), **editor-visualscript: 1** (baseline 1); all other 18 modules have 0 failures. Some unchanged tasks may be up-to-date; this command does not establish that every test executed afresh. The failing modules' reports were refreshed and their failure messages inspected.

The Kotlin failure reproduces the pre-existing XML defect. The two desktop failures are investigated above. The skip is `GameExporterTest.shouldKeepTheLauncherExecutableOnALinuxImage`, aborted by a platform assumption on Windows. An annotation-processor source-level warning (`RELEASE_11` versus source 21) also appeared; the reported task failures were test assertions, not Java compilation errors. Earlier `BundledDemoSongTest` failures described in old reports do not recur: `editor-audio` currently reports 55 tests and zero failures.

### Manually Not Verified (Headless Environment)

For this task: desktop window appearance; interactive gizmos and panels; prefab editing; sprite/tile painting; sculpt strokes; character animation; gameplay completion; audio listening; browser/GPU behavior; export dialogs; installation/signing; Linux/macOS operation; Docker/nginx hosting. No new validation claim is made for these checks.

## 6. Recommended next work, in order

1. **Restore a trustworthy test baseline.** Resolve the Kotlin generator failure, identify any additional fresh failures, record all skips, and preserve behavior tests. Do not dismiss a pre-existing defect simply because this report did not introduce it.
2. **Close editor data consistency gaps.** Prioritize Duplicate component preservation, scene save/load, play isolation, undo/redo and asset reload across representative new components.
3. **Run one complete desktop acceptance project.** Create a scene, place/import assets, configure physics and a camera, add script/graph logic, edit animation/audio, save/reopen, play, export and launch the exported game. Record observed output and failures at each step.
4. **Continue TD-068 only within its approved scope/order.** The record says only `ShooterShip` fully matches its reference gameplay; other examples omit rules. Supply the required component/control-flow/function support and parity tests before switching the remaining samples to graphs.
5. **Reconcile status documentation.** Consolidate INDEX/backend entries, refresh the feature matrix with dated results, and replace obsolete checklist claims while preserving historical reports as history. Approval states must remain explicit.
6. **Publish a backend support matrix and repeatable release checks.** Separate Java desktop, TeaVM and Babylon behavior. Exercise browser interaction, deployment and real target-OS packaging before claiming parity.
7. **Measure before expanding further.** Establish representative scene load time, frame time, memory/resource disposal, audio latency and project-size baselines. Vector/mesh/rigging aspirations should not be reported as existing complete studios.

These are recommendations, not authorization to start another milestone or change an approved technical decision.

## 7. Deliverable and Git safety

This is an analysis-only documentation task, not milestone acceptance. No feature or test was changed, so the feature matrix was not rewritten and no phase status was promoted. Existing edits to INDEX, the matrix and all implementation files were left intact.

The only intended authored file is `docs/10-nov-current-status.md`. Build/test commands may refresh ignored build/cache outputs. No commit, staging, push, branch operation, stash, reset, clean or other destructive Git operation was performed.

Ending HEAD: **`f9849c2`**, unchanged. The report's relative links, inventory arithmetic and recorded result totals were checked before delivery. No application code was changed to address the findings; those remain explicit follow-up work.
