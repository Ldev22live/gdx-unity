# TD-070 Report: Functions and Parameterized Custom Events

Record: [TD-070](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The tree already held the uncommitted TD-069 work ([report](td-069-node-palette-report.md)); nothing was committed, staged or reverted.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Functions with typed inputs and outputs, called by a node | **Met** (tests) | `GraphFunctionsTest`: call with inputs and outputs, a function's output into another call, nested calls, defaults for an output no Return reached, first Return ends the call |
| 2 | Functions share the graph's variables | **Met** | `shouldShareTheGraphsVariablesWithFunctions` |
| 3 | An error in a body stops the caller's chain | **Met** | `shouldStopTheCallersChainWhenTheFunctionBodyFails` (desktop), same case in `functionNodes.test.ts` (web) |
| 4 | Custom events with parameters; Fire node; endless firing stopped | **Met** | `shouldFireACustomEventWithItsParametersToEveryNodeOfThatName`, `shouldStopAnEventThatFiresItself`, undeclared events unchanged |
| 5 | Validation: missing function, circle, one entry, no events or Delay in a body, entry and return only in a body, undeclared event, bad names | **Met** | `GraphFunctionsTest` and `functionNodes.test.ts` |
| 6 | Schema version 2 with a reader for version 1; version 1 still written when nothing needs 2 | **Met** | `shouldStillWriteVersionOneForAGraphWithoutFunctionsAndReadOldFiles`, `shouldRoundTripFunctionsAndEventsAsVersionTwo`, `shouldRefuseAFileOfANewerVersion`; web equivalents. I did not load every graph under `assets/graphs` with a new test; the existing suites that read them pass. |
| 7 | Desktop and web run the same file with the same output | **Met** | `functions-parity.graph`: both tests expect `42`, `dmg 4`, `fired` |
| 8 | Editor: declare and edit functions and events with undo; open a function body; drop-downs for the function or event a node names | **Implemented; not used with a mouse by anyone** | `GraphSignaturesTest` (8): commands, undo with wires and pin order, document view; frames `images/td70-main.png` and `td70-function.png` show a graph with a call and a fired event, and a function body, with "No problems" |
| 9 | Java, Kotlin and TypeScript generation | **Not done; refused with a message** (allowed by TD-068; a cut from what I proposed) | `shouldRefuseToGenerateSourceForGraphsWithFunctions`, web equivalent |
| 10 | Convert one more sample to a graph | **Not done in this milestone** (see [TD-071](td-071-collector-graph-report.md)) | – |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-visualscript:test` | 0 | **83** tests (was 62, +21 `GraphFunctionsTest`), 0 failures |
| `./gradlew :editor-visualscript:test :editor-desktop:test --continue` | 0 | editor-visualscript **76** (was 68, +8 `GraphSignaturesTest`), editor-desktop 84 (unchanged), 0 failures |
| `npx tsc --noEmit`, `npx vitest run`, `npx vite build` in `engine-babylon` | 0 | 33 files, **420** tests (was 411: +9 `functionNodes.test.ts`; two existing tests in `visualscript.test.ts` updated: version 2 is now valid, and the node-id parity test lists the four new ids), 0 failures; typecheck and build pass |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_EDITOR_TAB="Visual Scripting"`, `GDXUNITY_EDITOR_GRAPH=graphs/FunctionsDemo.graph` and `GDXUNITY_EDITOR_GRAPH_FUNCTION=triple` (two runs: the graph and the function body) | 0 | 180 frames, 0 errors in the output log |

**Not run**: the whole-repo `./gradlew test --continue`. The attempt started during this milestone never produced a result, and the command that waited for it was stopped for low memory; I did not repeat it. So only the modules above were tested; the 2 known `BundledDemoSongTest` failures in `editor-audio` were not re-checked, and I cannot say there is no regression in other modules.

## Changes

- Engine (`engine-visualscript`): new `GraphParam`, `GraphFunction`, `GraphEventDef`, `FunctionNodes`; changed `Graph`, `GraphInstance`, `GraphValidator`, `GraphSerializer`, `BuiltInNodes`, `GraphCodeGenerator`, `GraphTypeScriptGenerator`; test `GraphFunctionsTest` and the shared `src/test/resources/functions-parity.graph`.
- Web (`engine-babylon`): `graph.ts`, `nodes.ts`, `interpreter.ts`, `validator.ts`, `codegen.ts`, `codegenJvm.ts`; tests `functionNodes.test.ts`, `visualscript.test.ts`.
- Editor: `editor-visualscript` `GraphSignatures`, `GraphDocument`, test `GraphSignaturesTest`; `editor-desktop` `SignaturePopup`, `GraphNodeView`, `GraphEditorPanel`, `GraphCanvas`, `EditorApplication` (testing aid `GDXUNITY_EDITOR_GRAPH_FUNCTION`).
- Assets and docs: `assets/graphs/FunctionsDemo.graph`, `AGENTS.md` (aid comment), `docs/TECHNICAL_DECISIONS.md`, `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`, `docs/reports/images/td70-*.png`.

## Known limitations and next steps

- Nobody has used the Functions and Events pop-ups, the Edit and Main Graph buttons, or added a parameter with the mouse. The frames show the results of the code reading a file, not an editing session.
- No macros; no renaming of functions, events or parameters; no function-local variables; no pure (execution-free) functions.
- The web editor reads, runs and saves functions unchanged but cannot create or edit them.
- Source generation refuses these graphs (above).
- Problems inside a function body appear in the problem line only when that body is open, or as "Function name: ..." entries with no node highlighted when the main graph is shown.

No commit, push or other Git write was performed.
