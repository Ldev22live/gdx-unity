# Phase 19-B Report: Sculpting in the Editor

Date: 2026-11-02 · Platform: Windows 11, JDK 21 · HEAD before and after: `1dc0a11` (no commit, push or other Git
operation was performed; the work is uncommitted, together with earlier uncommitted phases)
Plan: [phase-19-sculpting.md](../milestones/phase-19-sculpting.md) · Decision: TD-038 (proposed) · Previous: [19-A](phase-19a-report.md)

## What was built

| Module | Added |
|---|---|
| `engine-sculpt` | `MeshChunks`: cuts a mesh into runs of at most 60,000 vertices (LibGDX meshes index with 16 bits); a revision per chunk that changes whenever a vertex position or normal in it changed. `SculptSurface` owns the chunks and marks them on every change. |
| `engine-render` | `DynamicMesh` and `DynamicMeshSource` interfaces; `DynamicMeshComponent` (mesh path and colour, serialized, runtime bounds); `DynamicMeshGpu` (creates one target per chunk, re-uploads only chunks whose revision changed); `GlChunkTarget` (a LibGDX mesh with PBR material); `SceneRenderer3D.collectDynamicMeshes`, `setDynamicMeshSource` and the drawing, which also feeds the shadow pass. Serializer registered in `GameComponentSerializers`. |
| `editor-sculpt` (new) | `SculptAssets` (create, load, save atomically), `SculptSession` (open sculpts, brush, mode; is the renderer's mesh source and loads on demand), `SculptDocument`, `SculptDynamicMesh` (adapter), `SculptStrokeCommand` (one undo step per stroke), `SculptViewportInteraction` (turns 3D-view mouse input into strokes, passes everything else on), `SculptActions` (menu and key actions). |
| `editor-core` | `ViewportPicker` includes `DynamicMeshComponent` bounds, so a sculpt can be clicked and framed. |
| `editor-desktop` | `EditorApplication` creates the session, gives it to the renderer, wraps the 3D interaction, registers the actions, shows the brush in the viewport's info text, and saves modified sculpts on File > Exit and on window close. |

Status words (AGENTS.md section 9): **Implemented** (code and automated tests). **Integrated in code**: the actions are
registered in the action registry that builds the Create, Tools and File menus and binds the keys, and the viewport
interaction is in the editor's input chain, but I did not open the menus or press the keys in the running editor.
**Not Validated**: nothing was checked with a real mouse or the menus in the running editor.

## Acceptance criteria

| # | Criterion | Result |
|---|---|---|
| 1 | A sculpt is created from the Create menu and shown in the 3D view | Met in the unit tests (file, entity, component, selection, mode). Display checked by starting the real editor on a scene with a 120,000-triangle sculpt in Play mode: 300 frames, 0 errors, and a screenshot shows it lit and shaded. Creating through the menu was **not clicked** in the running editor |
| 2 | Left-drag on the selected sculpt paints, Shift smooths, Ctrl inverts | Met headless (a real camera and ray, the entity moved and scaled). **Not tried with a mouse** |
| 3 | Camera controls and normal clicking still work in sculpt mode | Met headless (right, middle, Alt+left, scrolling, a click that misses, nothing selected) |
| 4 | One undo step per stroke, exact undo and redo | Met (command history test, plus the 19-A delta tests) |
| 5 | Only changed chunks are re-uploaded | Met in `DynamicMeshGpuTest`; and every chunk whose data changed has a new revision (`MeshChunksTest`) |
| 6 | Stroke the editor refuses (Play mode) never leaves the surface different from the undo stack | Met: the stroke is taken back (test with a refusing state) |
| 7 | Sculpts survive closing the editor | Met in code and tests of `saveAllModified`; the close path in `EditorApplication` was **not exercised** |
| 8 | Brush panel, hover outline, Content Browser, game runtime and web player | **Not met**, not built (see below) |

## Commands and results

| Command | Result |
|---|---|
| `./gradlew :engine-sculpt:test` | exit 0, 76 tests |
| `./gradlew :engine-render:test` | exit 0, 90 tests (+14) |
| `./gradlew :editor-sculpt:test` | exit 0, 43 tests (new module) |
| `./gradlew :engine-desktop:test` | exit 0, 27 tests (one expected count raised from 21 to 22 serializers) |
| `./gradlew :editor-core:test` (whole module, before the picking test was added) | exit 0, 289 tests (+1 serialization test) |
| `./gradlew :editor-core:test --tests "*OrientedPickingTest"` | exit 0, 5 tests (+1) |
| `./gradlew :editor-desktop:compileJava` | exit 0 |
| `./gradlew :editor-desktop:run --args="--project <sculpt project> --scene <scene> --play --smoke-test=300"` | exit 0, "smoke test passed (300 frames)", 0 errors in the Output Log (the first attempt failed because my hand-written scene file had the wrong component layout; fixed in the file, not the code) |

**Not run after the 19-B changes, so their status is unknown:** the full `./gradlew test --continue` (Claude Code stopped
it because the machine ran out of memory, and I did not restart it), the tests of `editor-desktop` (40, whose main
class I edited) and `engine-web` (9), and a TeaVM web build, although `engine-web` compiles `engine-render` and
`engine-desktop`, which both changed. The last full run (12 modules, 659 tests, 0 failures) was before 19-B.

## Evidence that the tests can fail

I broke the real code one way at a time, ran the module's tests, and restored the file:

| Injected bug | Result |
|---|---|
| Stroke never pushed on the undo stack | 3 tests failed |
| Failed sculpt loads retried every frame | 1 test failed |
| "Modified" flag never set | 3 tests failed |
| Mouse ray not moved into the sculpt's own space | **Not caught at first**: my scaled-and-moved test still passed with the wrong ray, because the wrong ray also lands near the top of the sphere. I rewrote it (a scale of 2 and a ray at x = 60, which misses the unscaled sphere entirely) and the bug now fails 2 tests |

Not mutation-tested: `MeshChunks`, `DynamicMeshGpu`, `SculptActions`, `SculptAssets`.

## Findings

1. A mistake in my own tests: counting how many 60,000-vertex chunks 480,000 triangles need, I forgot that neighbouring
   triangles share vertices; the right answer is 5, not 8 or more. A chunk holds about 120,000 triangles.
2. `EditorState` never has "no scene" (it substitutes an empty one), so a "no scene" check I wrote was dead code and
   its test was wrong. Removed.
3. The shell tool failed twice on long commands that mixed here-documents with Java source; I wrote those files with
   the file tools instead. No effect on the code.

## Also: a mistake with the screen

To look at the running editor I took a full-screen screenshot. The editor was not the foreground window, and the
screenshot showed another application's text. I did not use it, deleted the file, and stopped automating mouse input
(clicks would have gone to whatever window was under the cursor). That is why mouse sculpting in the running editor
is unverified. The first screenshot, taken while the editor was in front, is correct and shows the sculpt rendering.

## Known limitations

- **Sculpts are invisible in the game runtime and the web player** (no source for `DynamicMeshComponent` there).
- No brush panel, no falloff or spacing controls, no hover outline.
- The Content Browser does not list `.sculpt` files, and there is no drag-and-drop or Details editor for the component.
- Default sculpt is 196,608 triangles; fine on the desktop, slow in the streamed web editor.
- Undoing the creation of a sculpt entity leaves the `.sculpt` file behind.
- Shadows were not looked at: the shadow fitting may not include sculpts.

## Next steps

Verify mouse sculpting by hand in the running editor (the manual checklist in the plan), then: a brush panel and hover
outline, listing `.sculpt` in the Content Browser, and 19-C (glTF export and loading in the game runtime and web player).
Do not start 19-C without an explicit instruction (AGENTS.md section 10).
