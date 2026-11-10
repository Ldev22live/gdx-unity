# Phase 18-C report: game export

**Date:** 2026-11-06 · **Decision:** TD-046 (proposed) · **Start HEAD:** `f9849c2` (nothing committed; the working tree already held uncommitted changes of the owner and earlier phases)

## What was built

- Second jpackage launcher `ForgeroomGame` in the editor's app image (`editor-desktop/build.gradle`).
- `GameExporter`, `GameExportCommand` (editor-core): compile scripts, copy runtime and project into a new folder.
- File > Export Game... and `ForgeroomEditor.exe --project <dir> [--scene <scene>] --export-game <new folder>`.
- `startScene` in `gdxunity.project`, `GameProjectFile`, `-Dgdxunity.project`, `GameFailureReporter` (crash log and message box) in engine-desktop; `lwjgl-tinyfd` added to engine-desktop.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Packaged editor exports a game folder | Met | `packageEditor` exit 0 (image has `ForgeroomGame.exe`); `--export-game` on `assets` with `samples/platformer-2d.json` exit 0, 237 MB folder (before the Kotlin compiler jars were excluded) |
| 2 | Exported game runs from another working directory, plays the start scene from its project file | Met | `GdxUnity Sample.exe --smoke-test=240` started in `%TEMP%`: exit 0. Java on this machine was not removed, but the game runs on its own `runtime\` (crash log shows `java.home` inside the export) |
| 3 | Startup failure leaves a crash log and exits 1 | Met for the log and exit code | `--scene samples/does-not-exist.json`: exit 1, `crash-20261106-133042.log` in `%LOCALAPPDATA%\GdxUnity Sample\logs` (test file deleted afterwards). **The message box was not seen** (smoke tests skip it by design) |
| 4 | Refusals | Met | `GameExporterTest` cases (missing/foreign scene, uncompiled and non-compiling scripts, non-empty or in-project output, no packaged runtime) |
| 5 | Tests green, counts, docs | Met | see below; re-checked 2026-11-06 after the Kotlin-jar change |

## Tests

- New: `GameExporterTest` 17 (editor-core), `GameExportRuntimeTest` 10 (engine-desktop), `EditorLaunchOptionsTest` +2.
- Passed before the last edit: `:editor-core:test --tests GameExporterTest` (17 of 17, 13:21), `:engine-desktop:test` (all, including the 10-sample parity test), `EditorLaunchOptionsTest`.
- The last change (Kotlin compiler jars left out of exports, plus matching assertions) was re-tested the same day: `GameExporterTest` 17 of 17, and a new packaged export (the 3D sandbox, see [phase-15e-report.md](phase-15e-report.md)) has no `kotlin-compiler` or `kotlin-daemon` jar, is 185 MB, and its exe `--smoke-test=300` exits 0.
- The first full `./gradlew test --continue` was stopped by the system for low memory. Per-module runs after the Phase 15-E work cover all 15 modules (counts in the 15-E report); the only failures are the 2 known `BundledDemoSongTest` ones.

## Manually Not Verified (Headless Environment)

File > Export Game... (menu, folder dialog), the game's window and input, the error message box.

## Known limitations

Windows package only; development runs cannot export; macOS and Linux layouts not built; whole project copied except editor files; no icon, installer or signing. See TD-046.

## Git

No commit, push or other Git write was done.
