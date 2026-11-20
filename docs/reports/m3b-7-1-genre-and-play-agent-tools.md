# Roadmap M3b.7, Slice 1: Genre and Play Agent Tools (2026-11-15)

Milestone M3b.7 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b) covers two kinds of work:
- **"further genre and dimension slices"**: one per genre and mode pair, each with its test and a template;
- **"agent tools to set the profile, validate, Play and Stop"**.

There are 33 pairs, so I am building it in slices. **This first slice is the agent tools**, which TD-088 already describes. **Status:**
- **Implemented and Integrated.** The tools are in the assistant's tool list and run in plans the person approves.
- **Manually Not Verified (Headless Environment):** no model was asked to use them.
- **M3b.7 as a whole is not done:** two of the 33 pairs play, and the other genre slices are not started.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 to M3b.6 and M3c.1 work (listed in their reports).
- No new module, dependency or decision: TD-088, approved, names these tools.

## 1. Acceptance criteria (this slice)

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | A tool sets the profile | **Met** | `play_profile_set` sets the genre (an id or "none") and optionally the mode, as the footer does. It outputs `playable`, `status`, `reason` and `playableModes`. A planned pair is allowed and reported as not playable. An unknown genre or mode is refused, and so is any change during Play. It is not an undo step (`PlayToolsTest`) |
| 2 | A tool validates | **Met** | `play_profile_check` (read-only) runs Play's resolution and checks in the mode the editor shows, without playing. It never fixes anything. The ground check needs Play's physics, so only Play runs it, and the tool says so |
| 3 | Tools play and stop | **Met** | `play_start` presses Play. A refusal is a result (`started: false`) with the problems and the labels of the fixes a person can click; the tool never applies one. `play_stop` presses Stop; the authored scene comes back unchanged, and stopping twice is not an error |
| 4 | They work in a plan | **Met** | A four-step plan (set the genre, check, Play, Stop) runs. The outputs can be read per step and nothing goes into the undo history. **Mutation check:** without the executor change, the plan test failed; restored |
| 5 | Agents act only through typed tools, as the person approves (AGENTS.md §7, the agent audit) | **Met** | Plans run from the assistant panel's Approve, on the editor's thread (`AssistantService.approveAndExecute`) |

## 2. What was built

- **`AgentTool.actsOutsideUndoHistory()`** (default false). The editor never records Play, Stop or the genre as undo steps, so these tools have no `EditorCommand`.
  - **Before:** the plan executor ran a changing tool only through its command, so a tool without one silently did nothing.
  - **Now:** it calls `execute` for tools that declare this, keeps their outputs, and treats a failed result like a failed read-only step.
  - **Undo:** a rollback of the plan's other steps does not undo these actions.
  - **Existing tools behave as before.** I did not change the general "no command" path: `TransformSetTool` returns no command for a missing entity, and that should not start calling `execute`.
- **The tools** (`editor-core` `agent.tools`):
  - `PlayProfileSetTool`, `PlayProfileCheckTool`, `PlayStartTool`, `PlayStopTool`, and a package helper `PlayToolSupport`.
  - They use the registry and `PlayModeController` that the editor's Play uses.
  - Each tool's description, which the model reads, lists its parameters and outputs.
  - All four are registered in `DefaultAgentTools`.
- **`play_start` clears earlier problems before pressing Play.** Play's later checks, such as scripts, can refuse it after the profile step has run. Without clearing, the tool could report the previous refusal's problems as this one's.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*PlayToolsTest" --tests "*AgentPlanEngineTest" --tests "*AssistantServiceTest"` | 1, then 0 | My test first failed to compile: the plan's step outputs are typed as plain objects. Then 4, 12 and 19 pass |
| Executor mutation check | 1 | 1 of 4 failed (the plan), as intended; restored |
| `./gradlew :editor-core:cleanTest :editor-core:test`, then the same for `editor-desktop` | 0, 0 | 496 (1 skipped, as before) and 160 |

| Module | Before (M3b.6) | After |
|---|---|---|
| `editor-core` | 492 | 496 |
| `editor-desktop` | 160 | 160 |

No engine module changed. Nothing visual changed, so no screenshot was taken.

## 4. Failures found and fixed

- My test's compile error (section 3).
- The stale problems in `play_start`, which I found while writing it (section 2).

## 5. Diff review

- **New:**
  - `PlayProfileSetTool`, `PlayProfileCheckTool`, `PlayStartTool`, `PlayStopTool`, `PlayToolSupport`;
  - `PlayToolsTest`;
  - this report.
- **Changed:** `AgentTool` (a default method), `AgentPlanExecutor`, `DefaultAgentTools`.
- **Docs:**
  - the chart (M3b.7, R3b);
  - `INDEX.md`, `FEATURE_TEST_MATRIX.md`;
  - TD-088 implementation notes;
  - `AI_AGENT_ARCHITECTURE_AUDIT.md`'s tool table, which marks the planned `playmode_test` as covered by `play_start` and `play_stop` for Play and Stop.

No files outside this repository were touched. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **No tool applies a Play fix**, on purpose. A plan can add a Player with `component_add` if the person approves that plan.
- **No tool plays for a while and reports what happened** (the audit's planned `playmode_test`).
- **Not tried with a real model:** whether a model chooses these tools well is unchecked.
- **The rest of M3b.7: one slice per genre and mode pair**, each with its acceptance test and a template. TD-088 does not specify those profiles, so each needs its design written first. Candidates:
  - Platformer + 3D;
  - Third-person Shooter + 3D;
  - First-person Shooter + 3D;
  - Racing + 3D;
  - Platformer + 2D, which needs a 2D character controller.
- **R3c next on the critical path:** M3c.2 (player prefab and spawn point). It needs no new decision.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
