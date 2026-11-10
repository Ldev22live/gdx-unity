# TD-068 — Visual Scripting Reliability and Component Nodes

Decision: [TD-068](../TECHNICAL_DECISIONS.md), explicitly approved 2026-11-10.
Implementation evidence: [milestone report](../reports/td-068-component-nodes-report.md).

## Scope

Restore the known Java test failures, expose a curated component API through typed graph pins, add bounded synchronous iteration, and migrate Breakout's complete paddle controller to an editable graph without changing its reference behavior. The ball, bricks and game rules remain Java; this is not a claim that the entire game is authored as a graph.

No new modules, dependencies or graph schema version are required. Existing version-1 graphs retain their format. Component references are runtime OBJECT pins, not serializable variables or literals. Optional rendering classes are discovered through the existing method-node mechanism; `engine-visualscript` retains its dependency on `engine-scene` alone.

## Component API

Each component has a typed **Get Component** node under Components; an unconnected Entity pin means self. Missing components produce a node-specific error and stop that execution chain. Component references of different classes cannot connect.

| Component | Exposed operations |
|---|---|
| Shape Renderer | Read width and height |
| Text | Read and set text |
| Camera | Read and set field of view |
| Sprite Animator | Play a named animation tag |
| Tilemap | Query whether a world-space point is solid |
| Audio Source | Read/set volume, play |

Method ids retain their existing `java:<class>#method(parameters)` form. The desktop derives pins and behavior from public methods; Babylon implements this explicit list through its runtime component adapter. The desktop's pre-existing broader Java method palette is not a promise that every Java method has a browser implementation.

## Repeat

`flow.repeat`: integer Count input, integer Index output, Body and Completed execution outputs. Count must be between 0 and 1,000. Index begins at zero. Each synchronous body chain finishes before the next iteration; nested loops use the same event-wide 10,000-action budget. Zero iterations goes directly to Completed.

Delays inside Body are scheduled, not awaited; Repeat is synchronous. Source generators explicitly refuse Repeat in this milestone. Desktop Java/Kotlin generation supports component lookup/calls; TypeScript generation and the web editor's JVM generator do not yet emit component calls and must report that limitation. Running these graphs through the desktop/Babylon interpreters is supported.

## Automated acceptance

- Existing Kotlin method-node compilation test passes with integer-to-float connections.
- Shooter tests bind graph instances and the editor/runtime comparison registers the actual graph play extension; gameplay assertions remain intact.
- Shared Java/Babylon cases cover every curated method, observable mutations/playback requests, invalid volume/FOV, tile queries and incompatible/missing component references.
- Repeat tests cover index ordering, zero iterations, nesting, invalid counts, shared execution limits, file round-trip and explicit generator refusal.
- Breakout's paddle graph reads and caches the Shape Renderer's half-width at Begin Play. Reference comparisons cover widths 90 and 140, alternating frame times, movement in both directions, clamping and opposing inputs.
- Component graph edits survive command undo/redo, save/reopen and execution.
- Full Java tests and Babylon typecheck/tests pass, with skips recorded; final counts belong in the report.

## Manual smoke checklist

Use a copy of the project for edits. Do not overwrite the repository's sample graph during manual checks.

1. Open `samples/breakout-2d.json`, select Paddle, and open its Graph Script. Confirm `BreakoutPaddle.graph` opens with Get Shape Renderer → Get Width → halfWidth initialization and Tick movement.
2. Search Components. Add a Get Text Component and Set Text node to a scratch graph; wire them to Begin Play and a Text entity. Verify component pin classes are visible and a Shape reference cannot connect to a Text target.
3. Change a literal, undo and redo; save the graph, close and reopen it. Play and observe the edited text. Stop and confirm the authored scene remains unchanged.
4. In a scratch graph, wire Repeat Count 3, Index → Number to Text → Print on Body, and “done” on Completed. Play: observe `0`, `1`, `2`, `done` in order. Count 0 prints only `done`; 1001 reports the bound error and does not continue.
5. Play Breakout, move to both walls, release keys and press opposing keys. Confirm the paddle stays within its width-dependent bounds; launch the ball and confirm the retained Java game rules still work.
6. In Babylon, open the same graph and repeat component lookup, editing and playback. Confirm typed component pins have no literal entry fields and unsupported source-generation actions show an error.
7. Export/run or launch the game runtime with Breakout and compare its paddle behavior with editor Play.

Unavailable interactive checks must be recorded as **Manually Not Verified (Headless Environment)**. Automated startup/frame captures do not substitute for the mouse-editing checklist.

## Later TD-068 milestones

Functions/macros/parameterized custom events, the variables/defaults UI, live debugging, remaining sample conversions, broader component APIs, source-generation support for Repeat/TypeScript components, and the reference-script switching UI remain separate work. Do not switch a sample to an incomplete graph or advance to another milestone automatically.
