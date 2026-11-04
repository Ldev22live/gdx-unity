# Phase 18-A Report — Editor as a Windows Executable

**Date:** 2026-10-30 · **Branch:** `phase-12-3d-foundation` · **HEAD:** `1dc0a11` at start and end (no commits)
**Plan:** [phase-18-packaging.md](../milestones/phase-18-packaging.md) · **Decision:** TD-033 (proposed and applied;
text not yet reviewed by the owner)
**Status:** Implemented and integrated on Windows 11 x64. The packaged exe was **started and checked by exit code,
console output and files**. What it draws was **Manually Not Verified (Headless Environment)**: the agent cannot see
the screen. macOS and Linux packages were not built.

## Starting state

69 uncommitted entries (Phase 17 and 13-B work), HEAD `1dc0a11`. They were left in place.

## What was built

| Area | Change |
|---|---|
| editor-core `project/` | `EditorProject` (folder with a `gdxunity.project` marker), `EditorProjectLocator` (`--project`, then the working folder or its `assets/`, then `Documents/GdxUnity/Sample Project`, installed once from the bundled sample through a staging folder), `PlatformDirectories` (Windows, macOS, Linux/XDG), `EditorLaunchOptions` (`--project`, `--smoke-test[=frames]`), `CrashLog` |
| editor-desktop | `EditorApplication` takes the project, folders and options: the asset root, scripts, `build/scripts` and scene dialogs are inside the project; theme and layout are in the per-user settings folder; the Output Log names the project. Smoke-test mode exits after N frames. `EditorLauncher` locates the project, maps failures to a crash log plus a native message box (not during smoke tests), and exits with 0 or 1 |
| Build | `:editor-desktop:prepareEditorPackageInput`, `packageEditor` (jpackage app image, `--enable-native-access`, sample-project path through `$APPDIR`, `-XstartOnFirstThread` on macOS), `zipEditorPackage`. Natives jars for other OSs are left out |
| assets | New `assets/gdxunity.project` marker, so `gradlew run` (working folder `assets/`) opens the repository's assets as before |

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | `packageEditor` builds `GdxUnityEditor.exe` with a bundled runtime | Met | `./gradlew :editor-desktop:zipEditorPackage`, exit 0, 56 s. Image 227 MB, zip 123 MB. Runtime is Java 21.0.11 (Temurin) with `jdk.compiler`, `jdk.unsupported`, `java.management`. Seven LWJGL natives jars, all Windows x64, plus the three gdx natives-desktop jars |
| 2 | Starts without an installed Java, opens the sample from the per-user folder, writes nothing into its install folder | Partly met | Started from an unrelated working folder: console `opening ...\Documents\GdxUnity\Sample Project (SAMPLE_PROJECT_INSTALLED)`, and the layout file was written to the settings folder. The bundled runtime is used (`java.home` in the crash log points into the image). Not tested on a machine without Java |
| 3 | `GdxUnityEditor.exe --smoke-test` exits 0 | Met | 180 frames, `0 error(s) in the Output Log`, exit 0, empty stderr. The startup scene includes the 13-B shadow, sky and tone mapping path, so those shaders compiled on this machine |
| 4 | A startup failure leaves a crash log | Met | `--smoke-test --project <folder without marker>`: exit 1, `crash-20261030-100348.log` with OS, Java, arguments and stack trace |
| 5 | `gradlew :editor-desktop:run` still works | Met | `--args="--smoke-test=120"`: `opening ...\gdx-unity\assets (WORKING_DIRECTORY)`, passed, exit 0 |
| 6 | Tests green, counts recorded, docs updated | Met | See below |

## Commands and results

| When | Command | Exit | Tests |
|---|---|---|---|
| Before | `./gradlew test --continue` (end of Phase 13-B) | 0 | 495: editor-animation 13, editor-core 261, editor-desktop 23, editor-visualscript 4, engine-animation 32, engine-core 27, engine-render 63, engine-scene 63, engine-visualscript 9 |
| After | `./gradlew test --continue` | 0 | **514**, 0 failures, 0 errors, 0 skipped: editor-core **280** (+19), all other modules unchanged |
| Package | `./gradlew :editor-desktop:zipEditorPackage` | 0 | — |
| Smoke | `GdxUnityEditor.exe --smoke-test` (APPDATA/LOCALAPPDATA redirected to the scratchpad) | 0 | — |
| Crash | `GdxUnityEditor.exe --smoke-test --project <not a project>` | 1 | — |
| Dev run | `./gradlew :editor-desktop:run --args="--smoke-test=120"` | 0 | — |

New tests: `EditorProjectLocatorTest` (7), `PlatformDirectoriesTest` (5), `EditorLaunchOptionsTest` (5), `CrashLogTest` (2).

## Side effect during testing (reported and undone)

The first smoke run tried to redirect the user home with `JDK_JAVA_OPTIONS`, but the jpackage launcher ignores that
variable. As a result the run installed the sample into the real `C:\Users\<user>\Documents\GdxUnity\Sample Project`.
Both `Documents\GdxUnity` and `Sample Project` had creation times from that run (10:03:04) and held only the copied
sample files, so they were deleted. Later runs used `--project` or expected failures. Testing the first-run path in
isolation would need an option for the projects folder. This is not built yet.

## Diff review

**New:** editor-core `project/EditorProject`, `EditorProjectLocator`, `PlatformDirectories`, `EditorLaunchOptions`,
`CrashLog` and their four tests. Also `assets/gdxunity.project`, `docs/milestones/phase-18-packaging.md` and this report.

**Edited, not previously modified:** `EditorLauncher.java`.

**Edited, already holding the user's uncommitted changes.** Only the hunks described here were added:
- `EditorApplication.java`: constructor parameters, project paths, per-user layout and theme, smoke-test methods.
  `findAssetRoot()` was removed.
- `editor-desktop/build.gradle`: packaging tasks appended.
- `docs/TECHNICAL_DECISIONS.md`: TD-033.
- `docs/FEATURE_TEST_MATRIX.md`: three rows and counts.
- `docs/INDEX.md`: Phase 18 row.
- `AGENTS.md`: two commands in §5.
- `docs/reports/phase-13b-report.md`: smoke-test note.

## Known limitations

- Visual appearance of the packaged editor not observed. Only exceptions and Output Log errors are checked.
- macOS and Linux: code paths and Gradle branches exist but were not built or run. macOS also needs signing and
  notarization before Gatekeeper lets users open it.
- No installer, code signing, application icon, start screen or project picker. To open another project, use
  `--project`.
- Editor theme and layout saved by earlier development runs in `assets/` are no longer read. They now live in
  `%APPDATA%\GdxUnity`.
- `Documents` is taken as `<home>\Documents`. A Documents folder redirected elsewhere, for example by OneDrive, is not
  detected.
- `EditorStartupHelper` (pre-existing) still swallows a Windows natives preload failure and relaunches on macOS with
  a `java` command the packaged runtime does not have. The packaged macOS launcher sets `-XstartOnFirstThread`, so the
  relaunch should not happen there. This is untested.
- Script compilation in the packaged editor: exercised later the same day by the sample games
  ([sample-games-report.md](sample-games-report.md)). The packaged exe compiled and ran the sample scripts with its
  bundled Java 21.

## Next steps

1. User: unzip `editor-desktop/build/package/GdxUnityEditor-1.0.0-windows-x64.zip` (ideally on a machine without
   Java) and run the E2E checklist in the milestone, then the Phase 12–17 and 13-B checklists.
2. 18-B: runtime parity (`engine-desktop` renders 3D, runs Animators and graphs).
3. 18-C: game export. Later: installers (WiX/`.dmg`/`.deb`), signing, CI builds on Windows/macOS/Linux running
   `--smoke-test`.

No commit, push or other Git operation that changes history or the index was performed.
