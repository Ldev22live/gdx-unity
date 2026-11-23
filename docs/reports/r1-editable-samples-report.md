# Roadmap R1 — Editable Examples and Project Templates: Report, Part 1 (2026-11-13)

Phase R1 of [13-nov-forgeroom-roadmap.md](../13-nov-forgeroom-roadmap.md). **Status: partly done.** This part builds the
editability audit, protects the shipped samples and fixes the defects the audit found. The graph conversions, the template
gallery and the Content Browser work are **not started**.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `d896331`. Ending: `d896331` (no commit was made).
- The working tree already held many uncommitted files from the owner before this work. They were left as they were; files
  this part touched that already had owner changes: `EditorApplication.java`, `InspectorPanel.java`, `SceneFileController.java`,
  `SceneFileControllerTest.java`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`.
- The owner's go-ahead for R1 was "continue with the next phase of the roadmap". The roadmap's section 7 questions
  (new modules, scenario import, legacy cleanup) are still unanswered; R1 needs none of those.

## 1. Acceptance criteria (roadmap R1)

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Editability audit test and matrix | **Met** | `EditabilityAuditTest` (5 tests) over all 38 samples; the table is generated into `FEATURE_TEST_MATRIX.md` and the test fails when it is stale |
| 2 | Convert the remaining 14 sample graphs to full graphs with parity tests | **Not met** | Not started. The audit lists 19 samples whose logic is Java source (4 of them with a graph next to it) |
| 3 | "Duplicate sample into my project"; samples open read-only as originals | **Met** (as copy-on-open) | A sample opens as an untitled copy, Save asks for a place, saving into the samples folder is refused. There is no separate menu item: opening is the duplicating |
| 4 | Template gallery on the start screen | **Not met** | Not started |
| 5 | Content Browser: icon, opening editor and "used by" for every asset type | **Not met** | The audit shows which types have no opening editor (glTF models); icons and "used by" were not touched |
| 6 | Done when: the audit passes for every sample that does not depend on R2 to R4, and a new project from each template opens, plays and exports | **Not met** | The audit passes (no defects left); there are no templates yet |

## 2. What was done

- **Editability audit** (`editor-desktop/.../audit/EditabilityAudit.java`). For each sample scene it checks that every component
  type has a Details editor, classifies the logic (none, graph, graph plus Java, Java only) and checks that every script file
  and every asset file the scene names exists and that the asset type has an editor that opens it. It is headless: it reads the scene
  file, not a window. Terrain, materials, animation state machines, scenario rules and prefab overrides are later phases' fronts
  and are not audited.
- **One list of Details editors.** `EditorApplication.createDetailEditors(...)` now builds the registry the editor uses, and the
  audit calls the same method, so the audit sees exactly what the editor offers.
- **Defects the audit found, and fixed:**
  - `ShapeRendererComponent`, used 133 times in the 2D samples, had no Details editor: the Inspector said "No editable
    properties" for every platform, brick and bullet. New `ShapeRendererComponentEditorProvider` (width, height, colour, style,
    centered; undoable).
  - `AudioListener` had no editor. New `AudioListenerComponentEditorProvider` explains the component (it has no settings).
- **Samples are originals.** `SceneFileController.setReadOnlyFolder` marks the samples folder. A scene opened from it (File >
  Open Sample or File > Open) becomes an untitled copy; Save asks for a place; writing into the folder is refused with a message.
  Before this, Save after Open Sample overwrote the shipped file.
- **Gaps the audit pins** (the tests fail if a new one appears or a closed one is not removed from the list):
  - 19 samples whose logic is Java source (15 Java only; `breakout-2d`, `collector-3d`, `runner-3d`, `shooter-2d` also have a
    graph). The two newest samples, `blockworld-3d` and `obby-3d`, are among the Java-only ones.
  - glTF models have no editor that opens them (8 samples use them): a Details editor on the component, but no viewer.

## 3. Commands and results

All run from the repository root on Windows.

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*EditabilityAuditTest" :editor-core:test --tests "*SceneFileControllerTest"` | 0 | audit 5 tests, controller 13 tests, 0 failures; wrote the table |
| `./gradlew :editor-desktop:test --tests "*EditabilityAuditTest"` (without the variable) | 0 | 5 tests, 0 failures: the table is stable |
| `./gradlew test --continue` | 0 | **1,754 tests, 0 failures, 1 skipped** (the same `GameExporterTest` Linux assumption as in the R0 report) |

Tests added: `EditabilityAuditTest` 5, `SceneFileControllerTest` +2 (13 total). Per-module: `editor-core` 391, `editor-desktop` 114,
`engine-desktop` 98; the other modules are unchanged.

## 4. Known limits and what is next

- **Not verified by eye.** The two new Details editors and the open-as-copy message were not looked at in the running editor
  (**Manually Not Verified (Headless Environment)**); only the registry and the file logic are tested.
- No agent tool was added for "open sample as copy" (roadmap rule 8); R10-A will need it.
- The audit's "no logic" means the scene has no script or graph, not that it is finished.
- Next in R1, in order of value: the template gallery (needs a start-screen dialog and a decision on which templates), the model
  viewer or an "opens in" rule for glTF, "used by" in the Content Browser, then the graph conversions, which are the largest item.

## 5. Git

No commit, push, stash, reset or branch change was performed.
