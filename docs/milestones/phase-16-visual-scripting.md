# Phase 16 — Visual Scripting

**Decision:** TD-029 (approved by the project owner 2026-10-29: two modules, interpreter first)
**Unreal equivalent:** Blueprint event graphs · **Design source:** `docs/project-suggestions.md` §3
**Status (2026-10-29):** Implemented and integrated; visual check pending (deferred by the project owner).

## Modules

| Module | Holds | Depends on |
|---|---|---|
| `engine-visualscript` | Graph model, node registry and built-in nodes, validator, `.graph` file format, interpreter (`GraphInstance`), `GraphScriptComponent` | `engine-scene` |
| `editor-visualscript` | `GraphDocument` (own undo history), `GraphCommands`, `GraphAssets`, `GraphPlayModeExtension`, `GraphExecutionTrace` | `editor-core`, `engine-visualscript` |
| `editor-desktop` (UI only) | Graph tab: `GraphEditorPanel`, `GraphCanvas`, `GraphNodeView`; Graph Script Details editor | both |

## What exists

| Step | Content | Status |
|---|---|---|
| A — model | Nodes with typed pins (execution; FLOAT, BOOLEAN, STRING, VECTOR3, ENTITY data), wires, variables; validator (types, one wire per data input / execution output, settings, data loops); `.graph` JSON | Done |
| B — runtime | Interpreter: events → action chains; pure nodes computed when read; Delay; per-entity variables; errors name the node and stop only that chain; endless-loop guard (10 000 nodes per event) | Done |
| C — editor | Graph tab next to Viewport; right-click node search; drag wires (refused with a reason when incompatible); right-click pin breaks wires; inline values on unconnected inputs; variables; Delete; own Ctrl+Z / Ctrl+Y; problem count; nodes that ran glow green during play | Done; visual check pending |
| D — integration | Graph Script component (Add Component → Scripting); Content Browser double-click opens `.graph`; play mode loads graphs (refuses to start on missing/invalid ones) and saves the open graph first; 2D collisions and sensor overlaps fire Event Collision / Trigger Enter; Java/Kotlin scripts call `GraphScriptComponent.fireCustomEvent` | Done (agent tools: not yet) |

**Node set (34):** Event Begin Play, Event Tick, Event Collision, Event Trigger Enter, Custom Event · Branch,
Sequence, Delay · Print, Set Position, Move By, Rotate By, Destroy Entity · Get / Set Variable · Self, Get Position,
Get Name · Add, Subtract, Multiply, Divide, Greater Than, Less Than, Equal, And, Or, Not · Make / Break Vector, Add
Vectors, Scale Vector · Append Text, Number to Text. **Added in Phase 15-F (TD-048), desktop only:** Add Force, Add Impulse, Set Velocity, Get Velocity, Raycast 3D (category Physics 3D); Event Collision / Trigger Enter also fire for 3D contacts. **Added in Phase 15-G (TD-049), desktop only:** Move Character, Jump Character, Is Grounded.

## Known limitations

- Interpreter only (no bytecode compilation); fine for gameplay logic, not for per-entity heavy loops.
- No graph functions/macros, loops (For Each), arrays, casting, or spawning prefabs at runtime yet.
- 3D physics events wait for a 3D physics backend (TD-028); collision *exit* events have no node yet.
- No breakpoints or variable watch; the green glow shows which nodes ran.
- One graph open at a time; opening another saves the current one.
- No agent (AI assistant) tools for graphs yet.
- The standalone runtime (`engine-desktop`) does not run graphs yet.

## Tests

`GraphInstanceTest` (9, engine-visualscript), `GraphEditingTest` (4, editor-visualscript, including a play-mode run),
`ComponentCatalogTest` (+1). `./gradlew test --continue` → exit 0, **388 tests, 0 failures** (editor-core 252,
editor-desktop 14, editor-visualscript 4, engine-core 27, engine-render 27, engine-scene 55, engine-visualscript 9).
Before Phase 16: 374.

## Manual checklist (when visual testing resumes)

1. Create → Box; Details → Add Component → Graph Script: `assets/graphs/Box.graph` is created and the Graph tab opens
   with Event Begin Play and Event Tick.
2. Right-click the canvas, search "move", add Move By; drag from Event Tick's `>` to Move By's `>`; type 0, 1, 0 in
   Offset. Press Play: the box rises; Move By glows green. Stop: the box is back where it was.
3. Drag Event Tick's Delta Seconds onto Move By's Offset: refused, the status bar says FLOAT cannot connect to VECTOR3.
4. Add Variable "speed" (FLOAT); add Get Variable, choose speed; Delete removes the selected node; Ctrl+Z restores it
   with its wires.
5. Add Divide wired into Number to Text → Print from Begin Play, leave B at 0, Play: the Output Log shows the error
   with the node id; the box keeps moving.
6. Save, close the editor, reopen, double-click the `.graph` in the Content Browser: the graph is unchanged.
