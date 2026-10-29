# AGENTS.md — GdxUnity Engineering Rules

Concrete rules for AI agents (Windsurf, Claude Code, others) and humans working in this repository.
The product vision and full engineering brief live in [`docs/design-doc.md.txt`](docs/design-doc.md.txt);
this file is the operational subset. When the two disagree, the repository's actual state wins and the
discrepancy must be reported, not silently resolved.

Reusable step-by-step workflows live in [`.windsurf/workflows/`](.windsurf/workflows/).

## 0. Read before changing anything

| Document | Why it matters |
|---|---|
| [`docs/TECHNICAL_DECISIONS.md`](docs/TECHNICAL_DECISIONS.md) | **Approved decisions TD-001…TD-019 are binding.** Changing one needs a new decision record and user approval. |
| [`docs/FEATURE_TEST_MATRIX.md`](docs/FEATURE_TEST_MATRIX.md) | What is implemented, integrated and tested, and the known gaps. |
| [`docs/ALL_IN_ONE_STUDIO_ROADMAP.md`](docs/ALL_IN_ONE_STUDIO_ROADMAP.md) | Phase 12–15 deliverables, planned module topology, consolidated task checklist (§6). |
| [`docs/TEST_STRATEGY.md`](docs/TEST_STRATEGY.md) | Test levels, naming, per-milestone smoke tests, headless vs graphics strategy. |
| [`docs/AI_AGENT_ARCHITECTURE_AUDIT.md`](docs/AI_AGENT_ARCHITECTURE_AUDIT.md) | Agent tool layer, structured plan schema, provider strategy. |
| `docs/milestones/*.md`, `docs/PHASE_*`, `docs/reports/*` | Per-milestone design and **manual smoke-test checklists** (e.g. gizmos: `milestones/milestone-6-transform-gizmos.md` §7). |
| [`docs/INDEX.md`](docs/INDEX.md) | Entry point and phase status table. |

Status reports dated before 2026-10-29 sometimes call work "complete/validated" without in-app evidence;
verify against the source (§9). `docs/porject-suggestions.md` is an intentional mirror of
`docs/project-suggestions.md`. `docs/swgb.md` and `docs/blender-report.md` are reference research, not specs.

---

## 1. Identity and stack

| Item | Value (verify in the files listed before relying on it) |
|---|---|
| Language | Java 21 (`build.gradle` → `java.sourceCompatibility = 21`) |
| Framework | LibGDX `gdxVersion` in `gradle.properties` (1.14.2) |
| Desktop backend | LWJGL3 via `gdx-backend-lwjgl3`; `lwjgl3Version` in `gradle.properties` (3.4.1) |
| Build | Gradle wrapper, `gradle/wrapper/gradle-wrapper.properties` (9.6.1) |
| Tests | JUnit 5.10 (Jupiter), headless with `GdxNativesLoader` |
| 2D physics | Box2D (`gdx-box2d`). **Box2D is 2D only — there is no 3D physics yet.** |
| Scripting | Java and Kotlin scripts compiled in-editor (`editor-core/.../scripting`). See §7. |
| Root package | `io.github.ldev22live.gdxunity` |
| Reference product | Unreal Engine 5 **workflows and toolset**, not its rendering technology (TD-020). Original implementations only; no Unreal/Unity code, assets, icons or trademarked feature names. |

Do not upgrade versions just because newer releases exist. A version change needs a written justification in
`docs/TECHNICAL_DECISIONS.md`.

## 2. Repository boundaries

- Work only inside this repository. Do not read, search, copy from, or modify other projects on the machine
  (in particular Weskaap, Pulse for Processing, UrConnex).
- External documentation (LibGDX, Java, Gradle, dependency docs) may be consulted. Do not copy code from
  external repositories without explicit authorization.

## 3. Git safety (non-negotiable)

- Never stage, commit, amend, push, reset, rebase, clean, stash, or switch/create branches unless the user
  explicitly asks in the current task.
- Never discard or overwrite uncommitted user changes. Run `git status` before editing and report what was
  already modified.
- Report starting and ending `git rev-parse --short HEAD` in every milestone report.

## 4. Modules and responsibilities

Only the modules in `settings.gradle` are part of the build:

| Module | Responsibility | May depend on |
|---|---|---|
| `engine-core` | Lifecycle, time, resource ownership (`ResourceManager`), asset references/catalog | LibGDX core |
| `engine-scene` | `Entity`, `Component`, `Transform`, `Scene`, serialization, 2D physics, lights, script components | `engine-core` |
| `engine-render` | Cameras (`OrthographicCamera2D`, `PerspectiveCamera3D`), `SceneRenderer2D`, `SceneRenderer3D`, render components | `engine-core`, `engine-scene` |
| `engine-desktop` | Runtime desktop launcher (no editor code) | engine modules |
| `editor-core` | Headless editor logic: state, commands/undo, gizmo math, picking, play mode, scripting compiler, AI agent tools | engine modules |
| `editor-desktop` | scene2d.ui editor shell, panels, renderers for gizmos/grid/physics debug | `editor-core`, engine modules |

Rules:
- Engine modules must never depend on editor modules.
- Put testable logic in `editor-core`; keep `editor-desktop` to UI/GL glue.
- `core/` and `lwjgl3/` are leftovers from the gdx-liftoff template and are **not** in `settings.gradle`.
  Do not add code there. Their removal is a planned roadmap task (`ALL_IN_ONE_STUDIO_ROADMAP.md` §6) but
  still requires explicit user approval because it deletes tracked files.
- Planned modules (`engine-vector`, `engine-audio`, `editor-studios`; `engine-physics` proposed in
  `ARCHITECTURE_ASSESSMENT_AND_ROADMAP.md`) must follow the topology in `ALL_IN_ONE_STUDIO_ROADMAP.md` §3.
  Creating a module is an architectural change: get approval first.

## 5. Build and test commands

Run from the repository root (`gradlew.bat` on Windows cmd/PowerShell, `./gradlew` in bash).

```bash
./gradlew test --continue                 # all modules; --continue reports every failing module
./gradlew :editor-core:test               # one module
./gradlew :editor-core:test --tests "*TransformGizmoTest"   # one class
./gradlew :editor-desktop:run             # launch the editor (needs a display)
./gradlew :engine-desktop:run             # launch the runtime
```

`gradle.properties` sets `org.gradle.logging.level=quiet`, so a passing run prints almost nothing. Do not
infer success from silence: check the exit code and the XML reports in
`<module>/build/test-results/test/*.xml` (or HTML in `<module>/build/reports/tests/test/`), and record the
test counts per module.

## 6. Code conventions

- PascalCase types, camelCase members, UPPER_SNAKE_CASE constants. Name things for what they are.
- No unexplained magic numbers: promote tuning values to named constants with a one-line Javadoc.
- Use imports, not fully qualified class names inline.
- Validate public API arguments with `Objects.requireNonNull(x, "x cannot be null")` or
  `IllegalArgumentException` with the offending value in the message — matches existing code.
- No silent fallbacks that fabricate results (e.g. returning a made-up ray when there is no camera). Fail
  loudly or return an explicit "none" value that callers must handle.
- No empty `catch` blocks. Disposal errors follow TD-008: keep disposing the remaining resources, then
  report the failures (`ResourceDisposalException` with suppressed causes). `SceneRenderer2D`/`SceneRenderer3D`
  `dispose()` currently swallow errors, which deviates from TD-008; do not copy that pattern.
- Javadoc every public engine API, especially scripting, editor-automation (`agent/`) and extension points.
- Avoid per-frame allocations in `render`/`update` paths in new code; measure before optimizing existing code.

## 7. Architecture rules

- **Scene/ECS:** components extend `BaseComponent`; each new component type needs a `ComponentSerializer`
  registered in `ComponentSerializerRegistry` and round-trip test coverage (see
  `ComponentCloningAndSerializationCoverageTest`).
- **Editor mutations** go through `EditorCommand` implementations so undo/redo works. Never mutate scene
  state directly from UI code.
- **Resources:** every `Disposable` has exactly one owner. Prefer registering with `ResourceManager`.
  `dispose()` must be idempotent and must null out references.
- **Rendering:** stay on LibGDX facilities (`ModelBatch`, `Environment`, `ShapeRenderer`, `SpriteBatch`). No
  Vulkan/native render backend without an approved feasibility study.
- **Gizmos (TD-016):** math in `TransformGizmoMath` (pure, headless-testable), rendering in
  `TransformGizmoRenderer`, drags relative to a `TransformSnapshot`, gizmo hit-testing before entity picking,
  one `ChangeTransformCommand` per completed drag (TD-018). Any handle drawn must be returnable by the
  hit-test used for that camera type, and vice versa. TD-016 sizes handles by camera zoom; that does not
  carry over to perspective cameras (`PerspectiveCamera3D.getZoom()` is always 1), so 3D gizmos need a
  distance-based scale factor.
- **Scripting (TD-019, approved):** Java and Kotlin scripts extending `ScriptComponent`, bound via
  `EntityScriptBindingComponent`, compiled in `editor-core`, run as trusted in-process code. The design doc
  mentions evaluating Lua; adding another script runtime would supersede TD-019 and needs a new decision
  record plus user approval.
- **Physics:** 2D physics is `PhysicsSystem2D` (Box2D, see `PHASE_10A_*`). 3D physics engine selection is an
  open "Future Decision" in `TECHNICAL_DECISIONS.md`; Bullet/Jolt are only candidates. Write the evaluation
  (design doc §3.3) and get approval before adding any dependency.
- **AI automation:** editor operations exposed to agents are `AgentTool` implementations in
  `editor-core/.../agent/tools` returning `AgentToolResult.success/failure` with machine-readable `outputs`.

## 8. Testing rules

- Every behavior change ships with a test that would fail without the change.
- Test classes: `{ClassName}Test`. Test methods: `should{ExpectedBehavior}[When{Condition}]`.
- Headless tests must call `EditorTestHelper.initNatives()` / `GdxTestHelper` in `@BeforeAll` when they touch
  LibGDX math or natives. Do not depend on `Gdx.graphics`/`Gdx.gl` in unit tests; code paths that need a GL
  context must be isolated and listed as "needs graphics test" in `docs/FEATURE_TEST_MATRIX.md`.
- Assert behavior, not just "no exception".
- Visual/interactive behavior is checked with the milestone's manual smoke checklist via
  `./gradlew :editor-desktop:run`. An agent that cannot see the screen must record the check as
  **Manually Not Verified (Headless Environment)**, as in `docs/reports/phase-11a-desktop-visual-acceptance.md`,
  and never describe what it did not observe.
- Update [`docs/FEATURE_TEST_MATRIX.md`](docs/FEATURE_TEST_MATRIX.md) whenever a feature or its tests change.

## 9. Documentation and honesty

- Status words mean exactly this:
  - **Implemented** — code exists and has automated tests.
  - **Integrated** — reachable from the editor or runtime by a user.
  - **Validated** — integrated and checked in the running application on the stated platform.
- Never call a feature complete, validated, or production-ready when it is only unit-tested, not wired in,
  or a placeholder. State the gap.
- Use repository-relative links in docs, never `file:///C:/...` absolute paths.
- Record significant decisions in `docs/TECHNICAL_DECISIONS.md` / `docs/adr/`.

## 10. Milestone acceptance

A milestone is done only when all of the following are true and evidenced in a report under `docs/reports/`:

1. Acceptance criteria listed and each marked met / not met with evidence.
2. Exact commands run, exit codes, and per-module test counts (before and after).
3. New failures investigated; pre-existing failures distinguished from regressions.
4. Diff reviewed; no unrelated changes; user's pre-existing changes untouched or explicitly listed.
5. `docs/FEATURE_TEST_MATRIX.md` and `docs/INDEX.md` updated.
6. Known limitations and next steps listed.
7. Explicit statement that no commit, push or destructive Git operation was performed (unless authorized).

Do not start the next milestone (e.g. Phase 13) without explicit instruction.
