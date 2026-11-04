# Phase 19-A Report: Sculpting Core

Date: 2026-11-02 · Platform: Windows 11, JDK 21 · HEAD before and after: `1dc0a11` (no commit, push or other Git
operation was performed; the work is uncommitted, together with earlier uncommitted phases)
Plan: [phase-19-sculpting.md](../milestones/phase-19-sculpting.md) · Decision: TD-038 (proposed)

## What was built

New module `engine-sculpt` (added to `settings.gradle`; approved by the project owner with TD-038). It is headless:
no OpenGL, no gdx-gltf. There is **no user interface, no rendering and no editor integration yet** (19-B).
Contents: `SculptMesh`, `SculptMeshFactory` (cube-sphere, weld), `Subdivision`, `TriangleBvh`, `SculptSurface`,
`SculptStroke` with `BrushSettings`, `BrushType`, `Falloff`, `SymmetrySettings`, `StrokeDelta`, and `SculptMeshIO`
(`.sculpt` file). Status words (AGENTS.md section 9): **Implemented** (code and automated tests). Not Integrated,
not Validated.

## Acceptance criteria

See the table in the plan. All eight are met, except that GPU upload speed (criterion 7) belongs to 19-B and was
not measured.

## Commands and results

| Command | Result |
|---|---|
| `./gradlew :engine-sculpt:test` | exit 0, 67 tests, 0 failures |
| `./gradlew test --continue` (before the last test was added) | exit 0, 659 tests, 0 failures in 12 modules |
| `java -Xmx1200m` benchmark of the built classes (`Bench.java`, scratch file, not in the repository) | numbers below |

Per-module test counts after: editor-animation 13, editor-core 288, editor-desktop 40, editor-visualscript 4,
engine-animation 32, engine-core 32, engine-desktop 27, engine-render 76, engine-scene 63, **engine-sculpt 67 (new)**,
engine-visualscript 9, engine-web 9. Every other module has the same count as before this phase.

## Evidence that the tests can fail

I injected bugs into the real code one at a time, ran the module's tests, and restored the file:

| Injected bug | Tests that failed |
|---|---|
| Normals not updated around a moved vertex's neighbours | 5, including the normal and undo tests |
| Hierarchy not refitted after a dab | 2 (grab, and "normals and hierarchy after every brush") |
| A dab on the mirror plane applied twice | 1 (`shouldNotApplyTheMirroredCopyTwice...`) |
| Undo record keeps later positions | 1 at first, then 2 after I added a many-dab undo test |
| Dab spacing ignores the distance carried between mouse events | 2 |

The fourth row found a gap: undo of a stroke with many overlapping dabs was covered only by the cancel test, so I
added `shouldUndoAStrokeOfManyDabsAndMirroredDabsExactly`. One reading in the first mutation run looked wrong (two
mutations listed identical failures); I reran the second alone and got a different, correct result, so I do not trust
that first listing.

## Findings

1. **A vertex's area-weighted normal does not change when only that vertex moves.** The total of the face normals
   around a vertex depends only on the ring of vertices around it. My first test assumed the moved vertex's normal
   changes; it did not (exactly 0.0). The implementation was right, and the test now asserts that the neighbours'
   normals change, which is what the update over the one-ring protects.
2. A dab counter that skipped dabs which found no vertices could not show the spacing; it now counts placed dabs.
3. `weld` left vertices that only dropped triangles had used; it now removes them.
4. Several test expectations were my own wrong guesses (a 0.6 flatten threshold when the observed spread reduction
   was 38 percent, so I set 0.7 and added a second-stroke check; a dab count assumption). I changed the tests, not
   the code, in those cases, after checking each against the geometry.

## Measured speed (this PC, single thread, JDK 21)

Cube-sphere resolution 200: 480,000 triangles, 240,000 vertices.

| What | Cost |
|---|---|
| Build mesh and adjacency | 0.4 s |
| Build hierarchy | 0.5 s |
| Heap in use | about 90 MB |
| One pick (ray) | 0.01 ms |
| One draw or smooth dab, radius 0.05 / 0.15 / 0.40 of a unit sphere | 0.2 / 0.9 / 4.6 to 8.2 ms |
| One grab move, radius 0.40 | 5.5 ms |

At 120,000 triangles the same dabs cost 0.08 / 0.4 / 1.4 to 2.4 ms. The numbers are from one run on a PC that was
low on free memory; treat them as the right order of magnitude.

## Not done / known limitations

- No UI, rendering, glTF export or editor undo wiring (19-B, 19-C).
- Fixed topology only. Midpoint subdivision does not smooth.
- Only X symmetry has a test; Y and Z use the same code.
- Never run in the streamed web editor (software OpenGL) or in a browser.
- Multithreading, masks, layers, multires and dynamic topology are out of scope.

## Next steps

19-B: the `editor-sculpt` module, a drawable dynamic mesh that uploads only changed vertices, Sculpt mode in the
viewport, brush panel and undoable stroke commands. Then 19-C: glTF export. Do not start 19-B without an explicit
instruction (AGENTS.md section 10).
