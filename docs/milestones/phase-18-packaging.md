# Phase 18 — Packaging

**Roadmap:** [ALL_IN_ONE_STUDIO_ROADMAP.md](../ALL_IN_ONE_STUDIO_ROADMAP.md) §5, row 18 · **Decision:** TD-033
**Started:** 2026-10-30 ("continue with the next phase") · **Baseline:** `1dc0a11` plus uncommitted Phases 13-B/17
**Status:** 18-A and 18-B implemented; 18-C planned. Reports: [phase-18a-report.md](../reports/phase-18a-report.md),
[phase-18b-report.md](../reports/phase-18b-report.md).

## Why split the phase

The roadmap row packages the game runtime (`engine-desktop`). That runtime does not draw 3D, models or lighting and
does not run Animators or graphs, so a packaged runtime cannot be used to test Phases 12–17 end to end. The user
plans end-to-end testing on a Windows executable, so the **editor** is packaged first.

| Part | Content | Status |
|---|---|---|
| 18-A | Editor as a self-contained desktop app (bundled Java runtime), Windows first, same build on macOS/Linux | Implemented |
| 18-B | Runtime parity: `engine-desktop` plays scene files like editor Play (3D, scripts, physics, graphs, Animators, camera, text) | Implemented (TD-035) |
| 18-C | Game export: package a project's scenes and assets with the runtime as a standalone game | Planned |

## Current state (from the source, before 18-A)

- `gradlew :editor-desktop:run` sets the working directory to `assets/`. The editor treats the working directory as
  its project: asset root (`findAssetRoot()`), `scripts/`, `build/scripts/`, scene dialogs (`user.dir`),
  `workspace_layout.json` and `editor_theme.json` are all relative paths. An installed app started from a shortcut
  has an unrelated, often read-only, working directory.
- There is no packaging task. `installDist` produces jars plus a script that needs an installed Java.
- In-editor Java scripts need `javax.tools` (module `jdk.compiler`), so a JRE without it cannot compile scripts.
- Startup failures go to stderr, which a windowed Windows executable does not show.

## 18-A — Editor executable

| Step | Content | Tests |
|---|---|---|
| 0 — spike | `jpackage --type app-image` produces `GdxUnityEditor.exe` + `app/` + `runtime/` without WiX. The default runtime includes `jdk.compiler` and `jdk.unsupported`; native commands (`java.exe`) are stripped. The task uses the JDK Gradle runs on (here Temurin 21.0.11), so the bundled runtime matches the Java 21 target | — |
| 1 — project location | `EditorProject` (folder with a `gdxunity.project` marker file); `EditorProjectLocator`: `--project <dir>`, else the working directory (or its `assets/`), else the per-user sample project, installed on first start from the copy bundled with the app | Locator order; missing/invalid project fails loudly; sample copied once and never overwritten |
| 2 — per-user folders | `PlatformDirectories`: settings, logs and projects folders for Windows, macOS and Linux (XDG). Theme and layout move from the working directory to the settings folder | Paths per OS, environment fallbacks, relative XDG values ignored |
| 3 — launch options | `EditorLaunchOptions`: `--project`, `--smoke-test[=frames]`; unknown options fail | Parsing |
| 4 — crash log | `CrashLog` writes timestamp, OS, Java version, arguments and stack trace to the logs folder; the launcher shows a native message box pointing at it (not in smoke-test mode) | File contents, unique names |
| 5 — smoke test | `--smoke-test` renders N frames, then exits with 0, or 1 if the Output Log has errors or anything threw | Needs a display; run against the packaged exe |
| 6 — Gradle | `:editor-desktop:packageEditor` (app image for the host OS, natives for other OSs left out, sample project bundled, `--enable-native-access`, `-XstartOnFirstThread` on macOS), `:editor-desktop:zipEditorPackage` | Build run; contents inspected |

### Acceptance criteria (18-A)

1. `gradlew :editor-desktop:packageEditor` exits 0 on Windows and produces `GdxUnityEditor.exe` with a bundled runtime.
2. The packaged exe starts without an installed Java, opens the sample project from the per-user projects folder,
   and writes nothing into its install folder.
3. `GdxUnityEditor.exe --smoke-test` exits 0 on the build machine (renders the startup scene, including the 13-B
   lighting path, without errors).
4. A startup failure leaves a crash log in the logs folder.
5. `gradlew :editor-desktop:run` keeps working against `assets/`.
6. Tests green, counts recorded, docs updated.

### Manual E2E checklist (user, on the Windows executable)

1. Unzip `GdxUnityEditor-<version>-windows-x64.zip` on a machine without Java; double-click `GdxUnityEditor.exe`.
2. The editor opens the sample project in `Documents\GdxUnity\Sample Project`; the Output Log names that folder.
3. Run the Phase 12–17 and 13-B checklists from their milestone documents.
4. Write and attach a Java script; it compiles (bundled `jdk.compiler`).
5. Change theme and layout, restart: both are kept (stored in `%APPDATA%\GdxUnity`).
6. Start `GdxUnityEditor.exe --project <folder without marker>`: a message box names the problem and the crash log.

## 18-B — Runtime parity

**Started:** 2026-10-30 ("go") · **Decision:** TD-035

| Step | Content | Tests |
|---|---|---|
| 1 — scene list | `GameComponentSerializers`: every component type, used by the editor and the runtime (adds the prefab instance component) | Count and prefab entry; scenes round-trip with graph, Animator and camera |
| 2 — options | `GameLaunchOptions`: `--scene`, `--project`, `--scripts`, `--smoke-test[=N]`; all paths absolute | Defaults, precedence, relative working folder, bad input |
| 3 — scripts | `GameScripts`: precompiled classes from a folder or jar; clear message when the folder is missing | Bind, unknown class, missing folder |
| 4 — session | `GameSession`: scripts, then 2D physics, then graphs (from `.graph` files, collisions forwarded), then Animators (from `.anim` files); physics before scene each frame, 0.1 s step cap; refuses a scene with no camera; graph errors recorded (capped) | 11 headless tests |
| 5 — drawing | `GameRenderer`: 3D pass, 2D pass through the scene camera, then screen text; a model that fails to load becomes a runtime error | Needs a display: smoke runs |
| 6 — launcher | `DesktopLauncher` replaces the demo; exit 1 with a message on bad options, a missing scene or a failed start; `StartupHelper` passes program arguments to a relaunched JVM (macOS, Linux with NVIDIA) | Smoke runs, failure runs |
| 7 — Gradle | `gameScripts` source set compiles `assets/scripts`; `:engine-desktop:run` plays `samples/platformer-2d.json` by default | Build run |
| 8 — parity | `SampleGamesTest` plays all ten samples through editor Play and through `GameSession` with the same input and compares every entity | 1 test, checked against a deliberately wrong runtime |

### Acceptance criteria (18-B)

1. `gradlew :engine-desktop:run --args="--scene samples/<name>.json --smoke-test=240"` exits 0 for every sample.
2. The runtime loads scenes saved by the editor, including models, lights, sky light, tone mapping, graphs, Animators,
   Camera and Text components.
3. Scripts run from precompiled classes, without a compiler.
4. A scene plays the same in the runtime as in editor Play (parity test).
5. Errors (missing scene, scripts, graph, clip or model) end the run with exit 1 and a message naming the file.
6. Tests green, counts recorded, docs updated.

### Out of scope for 18-B

Packaging the runtime as an exe, choosing the start scene from the project file, a crash log and message box for the
runtime, 3D physics simulation, sound, scene switching from scripts. Packaging and the start scene are 18-C.

## Cross-platform

- `jpackage` cannot cross-build. macOS and Linux packages are built by the same task on those systems (CI with one
  runner per OS is a later step).
- macOS: `-XstartOnFirstThread` is set in the packaged launcher, so `EditorStartupHelper` does not relaunch (the
  stripped runtime has no `java` command to relaunch with). Unsigned apps are blocked by Gatekeeper until signing
  and notarization are added.
- Installers (`.msi`/`.exe` via WiX, `.dmg`, `.deb`/`.rpm`) and code signing are out of scope for 18-A; the app image
  and its zip are enough for E2E testing.

## Out of scope for 18-A

Installers, signing, auto-update, application icon, choosing a project from a start screen, 18-B and 18-C.
