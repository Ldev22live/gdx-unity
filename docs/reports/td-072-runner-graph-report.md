# TD-072 Report: Key Pressed and Play Sound Nodes, and the Runner Player as a Graph

Record: [TD-072](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The working tree already held the uncommitted work of TD-069 to TD-071; nothing was committed, staged or reverted.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | A Key Pressed node on desktop and web, with Java, Kotlin and TypeScript generation | **Met** | `KeyPressedCodeTest` (interpreter reacts only on the press; unknown key named; generated Java and Kotlin compile and react only on the press; TypeScript text), `keyPressedNodes.test.ts` (web interpreter and the three generators) |
| 2 | Check what the Runner needs (sound call, anything else) | **Met** | A Play Sound node was needed and added; `RunnerGame` reads `RunnerPlayer.LANES`, so the Java class stays |
| 3 | Convert the Runner player to a graph | **Met** | `assets/graphs/samples/RunnerPlayer.graph`, 99 nodes, from `tools/make_runner_graph.py`; the `runner-3d` Player runs it (`SampleGraphsTest`) |
| 4 | A test plays the real Java script and the graph with the same keys and compares every frame; the web replays the recorded positions | **Met** | `RunnerParityTest.shouldMoveLikeTheScriptFrameForFrame` (1,000 frames, positions every 10 frames within 0.05); `sampleGraphs.test.ts` replays `runner-trajectory.json` on the web interpreter (within 0.1) and requires exactly 3 "jump" sounds |
| 5 | The jump sound plays on desktop | **Not observed.** The parity test cannot see it (it needs a bound audio source). The node, the generated code and the scene's cue wiring are tested separately; the web counts the sounds. | `SampleAudioCuesTest` now reads Play Sound nodes and still finds `runner-3d` answering exactly its cues |
| 6 | Docs, matrix, report | **Met** | this file, TD-072 record, `FEATURE_TEST_MATRIX.md`, `INDEX.md` |

Also done (not in the proposal): text fields for the key and cue settings in the Graph tab (Key Down, Key Pressed and Input Axis had no way to set a key); a readable layout for generated graphs (`tools/graph_layout.py`), after a frame showed the first Runner graph's nodes piled on each other; the Collector graph was regenerated with it.

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-visualscript:test :editor-visualscript:test :engine-desktop:test :editor-desktop:test --continue` | 0 | engine-visualscript 83 tests (unchanged), editor-visualscript **89** (was 80: +4 `RunnerParityTest`, +5 `KeyPressedCodeTest`), engine-desktop **92** (was 91: +1 wiring), editor-desktop 84; 0 failures |
| `GDXUNITY_RECORD_FIXTURES=1 ./gradlew :editor-visualscript:test --tests "*RunnerParityTest"` | 0 | Wrote the Java script's positions into `runner-trajectory.json` (once) |
| `npx tsc --noEmit`, `npx vitest run`, `npx vite build` in `engine-babylon` | 0 | 35 files, **425** tests (was 421: +1 Runner replay, +3 `keyPressedNodes.test.ts`), 0 failures; typecheck and build pass |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_EDITOR_TAB="Visual Scripting"`, `GDXUNITY_EDITOR_GRAPH=graphs/samples/RunnerPlayer.graph` (twice: before and after the layout change) | 0 | 180 frames, 0 errors; frame `images/td72-runner-graph.png` (the second run) |

**Failures found on the way and fixed (all caused by the Runner no longer having a Java player in the scene)**: `GraphExamplesTest` (its fake keyboard reported only held keys; it now reports presses once per frame), `SampleAudioCuesTest` and web `sampleCues.test.ts` (they read cues from scripts only; they now also read Play Sound nodes), web `samples.test.ts` "runner" (its helper started only Java scripts; it now starts the scene's graphs, as `gameplay.test.ts` does for the Collector), and my own new web test, which expected an empty cue to be allowed (validation refuses it). No assertion about game behavior was loosened.

**Not run**: the whole-repo `./gradlew test --continue`, for the memory reason in the TD-070 report. Only the four modules above and the web suite were run; the 2 known `BundledDemoSongTest` failures in `editor-audio` were not re-checked.

## Changes

- Engine: `BuiltInNodes`, `GraphHost`, `GraphInstance`, `NodeContext`, `GraphCodeGenerator`, `GraphTypeScriptGenerator`.
- Web: `nodes.ts`, `interpreter.ts`, `codegen.ts`, `codegenJvm.ts`, `runtime/ScenePlayer.ts`, `runtime/GraphEntity.ts`; tests `keyPressedNodes.test.ts`, `sampleGraphs.test.ts`, `support.ts`, `gameplay.test.ts`, `samples.test.ts`, `sampleCues.test.ts`.
- Editor: `GraphNodeView` (`buildTextProperties`); tests `SampleParity` (shared harness), `CollectorParityTest`, `RunnerParityTest`, `KeyPressedCodeTest`, `GraphExamplesTest`; `editor-desktop` `SampleAudioCuesTest`; `engine-desktop` `SampleGraphsTest`.
- Assets and tools: `RunnerPlayer.graph`, `CollectorPlayer.graph` (re-laid out), `examples.json` (Runner entry), `runner-3d.json`, `tools/make_runner_graph.py`, `tools/graph_layout.py`, `tools/make_collector_graph.py`, `editor-visualscript/src/test/resources/runner-trajectory.json`.
- Docs: `TECHNICAL_DECISIONS.md`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `docs/reports/images/td72-runner-graph.png`.

## Known limitations and next steps

- 4 of the 15 samples run graphs; the other 11 example graphs leave rules out (the audit is in TD-071).
- Nobody has played the converted Runner on desktop or web, or edited the key and cue fields with the mouse. The only editor evidence is one still frame.
- The Runner graph is large (99 nodes) and its wires are long even after the layout change; it needs zooming out to read.
- No test generates Java, Kotlin or TypeScript from the Runner graph itself, and the web player was not run in a browser with the converted scene.
- No Key Released node.

No commit, push or other Git write was performed.
