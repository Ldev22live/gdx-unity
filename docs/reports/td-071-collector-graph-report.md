# TD-071 Report: The Collector Ball Runs an Editable Graph

Record: [TD-071](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The working tree already held uncommitted work from TD-069 and TD-070 (listed in their reports); nothing was committed, staged or reverted.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Audit the other 14 example graphs: what each still lacks | **Met** | The audit is in the TD-071 record. It rests on each example's "notCovered" text and on reading `CollectorPlayer.java` and `RunnerPlayer.java`; I did not read the other 12 scripts line by line. |
| 2 | Add the nodes the converted sample needs, on desktop and web | **Met** (one node: Power) | `BuiltInNodes.MATH_POWER`, web `NODE.power`; Java, Kotlin and TypeScript generation on both sides; the existing node-id parity test (`visualscript.test.ts`) lists it |
| 3 | A sample switches to its graph only when a test plays script and graph side by side and they agree | **Met for the Collector** | `CollectorParityTest.shouldMoveLikeTheScriptFrameForFrame`: the real `CollectorPlayer.java` is compiled and run next to the graph with the same keys; every sampled position (1,000 frames, every 10) is within 0.05 units |
| 4 | Web: the converted sample keeps running there | **Met in tests, not in a browser** | `sampleGraphs.test.ts` replays the desktop-recorded positions on the web interpreter (tolerance 0.1); `gameplay.test.ts` runs the ball from its graph; `vite build` passes. The player was not opened in a browser with this scene. |
| 5 | Samples that cannot match stay on Java, with the reason | **Met** | TD-071 record (audit) and `examples.json` (unchanged for them) |
| 6 | The Java script stays as the reference | **Met** | `assets/scripts/samples/collector/CollectorPlayer.java` untouched; `SampleGraphsTest` checks it exists |
| 7 | Frames of the converted sample open in the Graph tab | **Not done** | No frame of `CollectorPlayer.graph` was captured. It is 93 nodes, laid out in columns by a script, not arranged for reading. |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-visualscript:test :editor-visualscript:test :engine-desktop:test :editor-desktop:test --continue` | 0 | engine-visualscript 83 tests (unchanged), editor-visualscript **80** (was 76, +4 `CollectorParityTest`), engine-desktop **91** (+1 wiring test), editor-desktop 84; 0 failures in all four |
| `GDXUNITY_RECORD_FIXTURES=1 ./gradlew :editor-visualscript:test --tests "*CollectorParityTest"` | 0 | Wrote the recorded positions of the Java script into `collector-trajectory.json` (once) |
| `npx tsc --noEmit`, `npx vitest run`, `npx vite build` in `engine-babylon` | 0 | 34 files, **421** tests (was 420: +1 `sampleGraphs.test.ts`), 0 failures; typecheck and build pass |

**A regression found and fixed on the way**: `gameplay.test.ts` "shouldRollThePlayerWhenAKeyIsHeldAndStopAtTheArenaWall" failed after the scene switch, because it started only Java-bound scripts. It now also starts the scene's graph scripts the way `ScenePlayer.play` does; the test's assertions are unchanged.

**Not run**: the whole-repo `./gradlew test --continue`. A first attempt during TD-070 never produced a result and its watcher was stopped for low memory; I did not repeat it. Modules other than the four above were not run for this milestone, so the 2 known `BundledDemoSongTest` failures in `editor-audio` were not re-checked.

## Changes

- New: `tools/make_collector_graph.py`, `editor-visualscript/src/test/java/.../CollectorParityTest.java`, `editor-visualscript/src/test/resources/collector-trajectory.json`, `engine-babylon/test/sampleGraphs.test.ts`.
- Changed: `assets/graphs/samples/CollectorPlayer.graph` (regenerated, 93 nodes), `assets/graphs/samples/examples.json` (Collector entry), `assets/samples/collector-3d.json` (Player runs the graph), the Power node in `BuiltInNodes`, `GraphCodeGenerator`, `GraphTypeScriptGenerator` and web `nodes.ts`, `codegen.ts`, `codegenJvm.ts`, `engine-desktop/.../SampleGraphsTest.java`, `engine-babylon/test/gameplay.test.ts`, `docs/TECHNICAL_DECISIONS.md`, `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`.

## Known limitations and next steps

- 3 of the 15 samples run graphs. Candidates for the next conversions are the Runner player and the Walker; neither was tried (see the record).
- The graph has not been looked at by a person in the Graph tab, nor the sample played by a person on desktop or web.
- I wrote no test that generates Java, Kotlin or TypeScript from a graph using Power (the generators for it are untested), nor one that generates code from the Collector graph.

No commit, push or other Git write was performed.
