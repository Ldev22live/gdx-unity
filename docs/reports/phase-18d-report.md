# Phase 18-D report: icon, installer, signing, Linux and macOS export

**Date:** 2026-11-06 · **Decision:** TD-047 (proposed) · **Start HEAD:** `f9849c2` (nothing committed; the working tree already held uncommitted changes of the owner and earlier phases)
**Asked:** "start phase 18"; of the follow-ups offered the owner chose all four.

## What was built

- **Icon:** `tools/make_icons.py` draws it (Pillow) and writes `.ico`, `.png`, `.icns` and four window-icon PNGs; jpackage uses it on `ForgeroomEditor.exe` and `ForgeroomGame.exe`; the editor and games set it as their window icon. A project can name its own PNG (`windowIcon` in `gdxunity.project`), validated at export.
- **Installer:** `:editor-desktop:installerEditor` (.msi, .dmg or .deb by host OS), with a check and instructions for WiX.
- **Signing:** `signEditorPackage`, `signEditorInstaller`, `-PsignCertificate`, `FORGEROOM_SIGN_PASSWORD`, optional timestamp server; `zipEditorPackage` and the installer sign first when a certificate is given.
- **Linux and macOS export:** `GameExporter.Layout` and `Layout`-aware copying, renaming and `Info.plist` rewriting; the template root is found per OS.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Both launchers show the Forgeroom icon | Met | Icon extracted from both built exes (a 32 px blue "F") |
| 2 | Editor and game windows use an icon; a project can set its own | Met in code and tests; **not seen** | `GameExportRuntimeTest` (icon read, used only when the file exists, bundled sizes); packaged game with `windowIcon` runs, smoke test exit 0. The title bar and taskbar were not looked at |
| 3 | `installerEditor` builds an installer | **Not verified** | WiX is not installed here. The task stops with instructions; that message was seen. Nothing was downloaded |
| 4 | Signing works and is safe with secrets | Met on Windows with a test certificate | Task signs both exes (signtool); `signtool verify` reaches "root certificate not trusted" (expected for self-signed); a renamed copy keeps the signature; no certificate and no password each stop with a message; the password is read from the environment. The throwaway `.pfx` and its store entry were removed. **No real certificate was used** |
| 5 | Export understands Linux and macOS images | **Written and tested against fake images only** | 11 new `GameExporterTest` cases for both layouts, `Info.plist`, and image-root detection per OS |
| 6 | Existing behaviour intact; tests green; docs | Met | below |

## Tests (2026-11-06, after the change)

editor-core 352 (1 skipped: execute-bit test needs POSIX permissions), engine-desktop 42, editor-desktop 62; all 0 failures. The other modules were not touched by this change and were last run for the Phase 15-E report (only the 2 known `BundledDemoSongTest` failures).

## Problems found on the way

- jpackage leaves its launchers read-only; signtool failed with "Access is denied". The task now clears the attribute.
- Gradle 9 removed `exec {}` from scripts, so signing uses a small task class with `ExecOperations`.

## Manually Not Verified (Headless Environment)

How the icon looks in the title bar, taskbar, Explorer and Start menu at its small sizes; the installer wizard; any Linux or macOS behaviour.

## Known limitations and next steps

- A game's `.exe` file icon is the generic one (only the window icon is per project).
- Exported games are not signed (only the editor's launchers and installer).
- To finish the unverified parts: install WiX 3.14 and run `gradlew :editor-desktop:installerEditor`; run `:editor-desktop:packageEditor` and `--export-game` on Linux and on a Mac and start the result; sign with a real certificate.
- Installers for exported games, notarization and auto-update remain out of scope.

## Git

No commit, push or other Git write was done.
