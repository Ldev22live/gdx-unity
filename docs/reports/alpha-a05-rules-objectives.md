# A05 Triggers and objectives

**[TD-109](../TECHNICAL_DECISIONS.md#td-109-scenario-rules-and-objectives-alpha-a05) was approved by the owner on 2026-11-26, as proposed. A05 is implemented and integrated in editor Play, the game runtime and the World layout's Rules category. It is not validated: no person has used it yet.**

The roadmap requires rules to be stored "as original versioned data/graphs after TD approval", so the audit and proposal came first and implementation followed the approval. The starting HEAD was `85ccc0a`. The A04 changes from the same day were uncommitted in the working tree; they are listed in the [A04 report](alpha-a04-players.md).

## What exists

| Area | Working behaviour, with sources | Gap for A05 |
|---|---|---|
| Rule data | TD-106 reserves `scenario.rules` and `scenario.objectives` in [scenario.schema.json](../contracts/scenario.schema.json). Each rule has id, name, enabled, once, one condition (`when`) and 1 to 8 effects (`then`), and is evaluated in list order. `ScenarioValidator` enforces the closed Alpha set: `timer` and `enterArea`; `showMessage`, `completeObjective` and `endScenario`. Any other rule is kept and reported as NOT_RUN. | Nothing creates, edits or runs rules. |
| Preservation and references | A04's `ScenarioSetup` keeps objectives and rules byte for byte when players are edited. `ScenarioReferences` blocks deleting an area entity that a rule names, and blocks removing a player that a rule names. `ChangeScenarioCommand` makes any change to the block one undo step. | The undo, preservation and safeguard paths can be reused directly. |
| Area entering (3D) | Trigger colliders (`Collider3DComponent.isTrigger`) get a sensor body in `PhysicsSystem3D`. Contact events of kind `TRIGGER_ENTER` and `TRIGGER_EXIT` reach any `ContactListener`. A walking character (Character Controller 3D) does raise them: see `CharacterController3DOnJoltTest`, line 319. | Nothing listens to these events for rules. |
| Area entering (2D) | `PhysicsSystem2D` sensor events (`SENSOR_ENTER`/`SENSOR_EXIT`) reach `ScriptCollisionForwarder2D`. | Same: no rule listens to them. |
| Visual scripting | The graph runtime has `Event Trigger Enter`, `Delay` and `Print` nodes. `GraphPlayModeExtension` starts graphs in editor Play, and `GameSession` starts them in the game runtime. | Graphs can express rules, but they are open-ended (loops, Java calls) and edited in Customize. They are not the "approachable Rules drawer" the roadmap asks for. |
| Play integration | `PlayModeController.Extension` runs a system with editor Play on a copied runtime scene, so the authored scene is never changed. `GameSession` starts the same systems in the game runtime (TD-035). | A rule runtime must be added to both, in the same order, so a rule behaves the same in the editor and in an exported game. |
| On-screen text | `TextComponent` and `HudRenderer` draw screen text in editor Play (`SceneEditorWorkspace`) and in the game (`GameRenderer`). | Messages, objectives and the result need somewhere to be drawn. |
| World UI | The Rules category is still a "comes with A05" note (`WorldShell.RULES`). | It needs a drawer. |
| Web | The Babylon player declares `scenario/1` as preserved data only. | Running rules on the web is A14 work. The data must stay portable. |

## Proposal (TD-109, approved 2026-11-26)

- Rules run on a small data interpreter, not as generated graphs. It is a bounded runtime in `engine-scene` (`ScenarioRuntime`), with no new module. The same class is used by editor Play (through an extension) and by `GameSession`, so the editor and the exported game behave the same. Rules never run code.
- Graphs stay the advanced path in Customize. The roadmap's "reuse visual scripting" is met by reusing its contact events and Play lifecycle, not by turning rules into graphs.
- The Rules drawer edits rules and objectives through `ChangeScenarioCommand` (one undo step per edit), using lists and fields only.
- Execution is visible in Play: a rule log in the Output Log, plus the current message, the objectives and the result on screen.
- Spawn effects, counters, variables and chained rules are left out of Alpha.

Full semantics: [TD-109](../TECHNICAL_DECISIONS.md#td-109-scenario-rules-and-objectives-alpha-a05).

## Implementation

| Part | Where | What it does |
|---|---|---|
| Runtime | `engine-scene/.../scenario/ScenarioRuntime`, `ScenarioRuntimeComponent` | Bounded interpreter. Rules are checked once per frame in list order, using game time. `timer` fires once, or every period at most once per frame when repeating. `enterArea` reacts to 3D trigger-enter and 2D sensor-enter events, matching the player (`PlayerComponent`) or a scenario player through `OwnerComponent`, including an owned parent. Effects only change what is shown, so rules cannot chain. `endScenario` stops evaluation. Faulty rules are skipped and listed; NOT_RUN rules are kept. More than 256 rules or 8 effects is refused at Play. |
| Validation | `ScenarioValidator` | An `enterArea` area must have an enabled trigger (3D) or sensor (2D) collider. |
| Editor Play | `editor-core/.../play/ScenarioPlayStep` | Registered by `PlayModeController` right after the genre profile step. It attaches the runtime to the Play copy and writes each rule that fires to the Output Log (source "Rules"). Pause holds game time. |
| Game runtime | `GameSession.startScenario` | The same `ScenarioRuntime.attach` call, at the same point (after the profile step). The log goes to the game log as "Rules: ...". |
| On screen | `HudRenderer` | Shows the current message (top centre), the visible objectives as `[ ]`/`[x]` (top left) and the result (centre) in editor Play and in the game. Nothing is drawn outside Play. |
| Editing | `editor-core/.../scenario/RulesEditor` | Add, rename, enable, once/repeat, reorder and remove rules. Timer and enter-area conditions. Add, change, edit and remove effects (at least 1, at most 8). Objectives: text, hidden, remove (refused while a rule completes the objective). Add Area: a trigger Box Collider 3D at the view centre. Every edit is one `ChangeScenarioCommand`; unknown members are kept and newer blocks are read-only. |
| Drawer | `editor-desktop/.../ui/world/RulesPanel`, `WorldCategories` | The Rules category opens the panel in the palette row, which scrolls both ways. The side column has Add Rule, Add Area and Add Objective plus the problem count. There is an objectives card, and one card per rule showing problem notes in red. A rule of a newer type is shown as kept, not editable. `GDXUNITY_EDITOR_DRAWER=Rules` opens it for smoke tests. |
| Fixtures | `tools/make_contract_fixtures.py`, `fixtures/contract-v1/**` | The shared valley fixture used Hero (the player itself) as Player 1's spawn, which TD-108's stricter validator rejects. Its generator now adds a `Start` entity with a Spawn Point, and all contract and interop fixtures were regenerated (desktop, web, desktop). The web test now expects `SpawnPointComponent` among the components the web cannot show, as `BACKEND_SUPPORT_MATRIX.md` already says. |

**Choices made within TD-109:**
- An area rule fires at most once per frame, however many entities entered that frame.
- A repeating timer of 0 seconds fires every frame.
- The message timer keeps counting after the scenario ends.
- In 2D scenes, Add Area is refused with a pointer to sensor colliders.

## Acceptance

| Criterion (roadmap A05) | Result |
|---|---|
| Audit and TD proposal | Met: this report and TD-109 (approved 2026-11-26). |
| Rules drawer: name, enabled, condition, effect, objective text and status | Implemented and integrated (`RulesEditorTest`, 8 tests). An objective's status is visible in Play only. Not seen on screen by a person. |
| Timer and enter-area conditions; message, complete-objective and end effects | Implemented (`ScenarioRuntimeTest`, 11 tests). Integrated in editor Play (`ScenarioPlayStepTest`, 2) and the game runtime with real Jolt (`GameSessionScenarioTest`, 1). |
| Validation, deterministic order, once/repeat, bounded execution, no code from data | Met in tests: list order, once versus repeat, at most once per frame, no chaining, end stops evaluation, limits refused, faulty and newer rules skipped. |
| Tests: timer, area enter, one-shot/repeat, invalid references, objective completion, serialization, runtime isolation, bounded loops | Met (see the test evidence below). |
| Human: a reach-target objective and a timed message, tested and edited without code | **Pending.** `RulesEditorTest.shouldPlayAReachTargetObjectiveAndATimedMessageMadeWithoutCode` covers the same flow headless. Manually Not Verified (Headless Environment). |

## Test evidence

| Command | Exit | Result |
|---|---|---|
| `:engine-scene:test --tests "*scenario*"` | 0 | `ScenarioRuntimeTest` 11, `ScenarioValidatorTest` 8 (new: the area must be a trigger), 0 failures |
| `:editor-core:test --tests "*RulesEditorTest" --tests "*ScenarioEditorTest" --tests "*WorldShellTest"` | 0 | 8, 14, 4 tests, 0 failures |
| `:editor-core:test --tests "*ScenarioPlayStepTest"` | 0 | 2 tests, 0 failures |
| `:engine-desktop:test --tests "*GameSessionScenarioTest"` | 0 | 1 test, 0 failures |
| `npx vitest run` in `engine-babylon` | 0 | 42 files, 532 tests passed (after the fixture regeneration and the one expectation update) |
| `gradlew test --continue --offline` (full suite) | *see the [progress report](../progress/progress-reports-26-11-2026.md)* | |

## Known limitations

- Leaving an area does nothing in Alpha.
- There are no spawn effects, counters or variables.
- Rules do not run on the web (A14).
- An objective's player is kept but not yet editable in the drawer.
- The drawer's layout and the HUD placement have not been seen by a person.
- Add Area places a fixed 3 m cube at the view centre; it is moved and scaled with the usual tools.

No commit, push or destructive Git operation was performed.
