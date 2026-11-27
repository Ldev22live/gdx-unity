# Progress report: 26 November 2026

This report tracks progress against [milestone-roadmap.md](../milestone-roadmap.md), the single release plan. Milestone detail is in `docs/reports/`. Status words follow AGENTS.md §9:

- **Implemented** means code exists with automated tests.
- **Integrated** means a user can reach it.
- **Validated** means a person checked it in the running app.

- Branch: `phase-12-3d-foundation`
- HEAD: `85ccc0a`, both at the start and the end of the day
- No commit, push or destructive Git operation was performed.

## Where we are

| Gate | Status today |
|---|---|
| A00 Baseline | Audit done on 2026-11-25. Checkpoint approval is still outstanding. Today's full test runs are the first fresh full runs since the audit (results below). |
| A01 UX audit | Done. The gap list awaits owner sign-off. |
| A02 World shell | Implemented and integrated (TD-107). The owner walkthrough is pending. |
| A03 Map, terrain, placement | In progress. The hands-on walkthrough is pending. |
| **A04 Players and scenario setup** | **Implemented and integrated today (TD-108).** Not validated: the human ownership walkthrough is pending. |
| **A05 Triggers and objectives** | **TD-109 approved today. Implemented and integrated today** in editor Play, the game runtime and the Rules drawer. Not validated: the human walkthrough is pending. |
| **A06 Customize and return to World** | **Audit done today.** Mostly covered by earlier milestones. One gap was fixed: unsaved mesh edits were dropped on leaving 3D Modeling. Not validated: the human edit loop is pending. |
| **A07 Play/Stop and scenario runtime** | **Automated audit done today.** The seven pairs, prerequisites and Stop restoration were already tested. A repeated 3D Play/Stop test with rules was added, and the stale RTS text corrected. Not validated: human keyboard play is pending. |
| A08 Templates and audio gate | Partial. Next. |
| A09–A10 | Pending. |
| A11 Shared contract | Done (TD-106, approved in principle). |
| A12 Cross-engine adapters | Met in automated tests. The shared fixture was regenerated today (see A05). |
| **A13 Web World shell** | **Implemented today** in `engine-babylon`: the World shell is the default, the desktop's palette prefabs are placed as linked instances, and Terrain, Players and Rules show the desktop hand-off. Not seen in a browser. |
| **A14 Web scenario authoring** | **In progress today:** scenario rules run in web Play, matching the desktop through a shared trace. The two web profiles of TD-110 (Action RPG 3D and Platformer 2.5D) were ported today and match the desktop through a shared movement trace. Web terrain, ownership and rule editing are open. |
| **A15 Cross-platform journey** | **Partial today.** The desktop↔web file round trip carries scenario players and rules. The full journeys are blocked by A14. |
| A16–A19 | Open. |
| **A21 Asset storage in the UrConnex database** | **Added today** by the owner, at the end of the roadmap. The storage backend decision is paused until urconnex-api is ready. Step 0 (disk cleanup) is done. |
| A20 Release | Blocked. |

## Done today: A04 (players and ownership)

TD-108 was approved by the owner. It was built on the existing scenario/1 schema, with no new module and no scene-format change. See the [A04 report](../reports/alpha-a04-players.md).

- **Editor core** (`editor-core/.../scenario`). Every edit is one undo step:
  - `ScenarioSetup` edits player slots and bounds and keeps objectives, rules and unknown data byte for byte. A newer block is read-only.
  - `ScenarioEditor` handles create, add player, add neutral, rename, recolour, spawn, remove, bounds and assigning owners.
  - `ChangeScenarioCommand` and `ChangeOwnerCommand` make those edits undoable.
- **Safeguards** (`ScenarioReferences`, `DeleteEntityCommand`):
  - A player is not removed while it owns objects, is named by an objective or rule, or while any rule cannot be checked.
  - An entity the scenario names (a spawn, an area) is not deleted.
- **Placement** (`LevelSession`):
  - Objects are placed Unowned by default.
  - A chosen owner applies only to new placements, in the same undo step.
  - If the chosen owner is removed, placement is refused rather than silently placing unowned.
- **Validator:** a spawn must have a Spawn Point, and bounds must be finite with each minimum below its maximum.
- **Desktop:**
  - The Players category has a card per slot, Add Player and Add Neutral, bounds, the owner for new objects, and a note that Play ignores ownership.
  - The selection card has an Owner list.
  - `GDXUNITY_EDITOR_DRAWER=Players` opens the category for smoke tests.

## Done today: A05 (rules and objectives)

TD-109 was proposed after the audit and approved by the owner as proposed. See the [A05 report](../reports/alpha-a05-rules-objectives.md).

- **Runtime** (`engine-scene`, `ScenarioRuntime`). A bounded interpreter:
  - Conditions: a timer (once, or repeating at most once per frame) and entering an area (3D trigger or 2D sensor; the player, or any entity a scenario player owns).
  - Effects: show a message, complete an objective, end the scenario.
  - Rules run in list order, never chain and never run code. Ending the scenario stops evaluation.
  - Faulty rules are skipped and listed; newer rule types are kept but not run.
- **Editor and game behave the same.**
  - Editor Play (`ScenarioPlayStep`, right after the genre profile step) and `GameSession` make the same `ScenarioRuntime.attach` call.
  - Pause holds game time.
  - Each rule that fires is written to the Output Log or the game log.
  - `HudRenderer` shows the message, objectives and result in Play only.
- **Authoring.**
  - `RulesEditor` edits every part of rules, effects and objectives, one undo step per edit, keeping unknown data.
  - Add Area creates a trigger box.
  - A rule's area, and an objective a rule completes, cannot be removed.
  - The validator requires that an area has a trigger collider.
  - The Rules category's `RulesPanel` shows a card per rule, objectives and problem notes. `GDXUNITY_EDITOR_DRAWER=Rules` opens it.
- **Shared fixture fix.** The contract fixture used the player character itself as player 1's spawn, which TD-108's check rejects. The generator now adds a real Spawn Point (`Start`). The contract and interop fixtures were regenerated desktop, then web, then desktop. One web test expectation now includes `SpawnPointComponent` among the components the web cannot show, as `BACKEND_SUPPORT_MATRIX.md` already says. The matrix gained a row for scenario rules.

## Done today: A06 (Customize and return to World)

See the [A06 report](../reports/alpha-a06-customize.md).

- **Audit.** Most A06 requirements were already met and tested by earlier milestones:
  - Customize sits beside Play in the World footer.
  - The selection-aware chooser offers house → 3D Modeling, sprite → 2D Studio, `.anim` → Animate, `.graph` → Logic, and materials in Details. It also works with nothing selected.
  - Mesh vertex, edge and face operations exist, with the New, Unique, Shared Prefab and Update save modes (provider originals protected).
  - Changed models are rebuilt for render and collision.
  - The 3D house and 2D house slices cover Play, Stop, undo, save, reopen and export.
- **Gap fixed.** Leaving 3D Modeling (Back to World or the Sculpt toggle) or selecting another object silently discarded unsaved mesh edits. That broke TD-084 and A06's "no silent discard or commit".
  - The new `MeshDraftKeeper` (`editor-sculpt`) keeps each part's drafts and undo history for the editing session.
  - The modeling panel gained a confirmed **Discard** and marks drafts saved after Save.
  - Back to World names any parts with unsaved edits in the status bar.
  - No new TD was needed, because this enforces the approved TD-084.

## Done today: A07 (Play/Stop and scenario runtime)

See the [A07 report](../reports/alpha-a07-play-stop.md).

- **Already met:**
  - The profiles are frozen: exactly the seven roadmap pairs are PLAYABLE, each with a profile test using real physics.
  - The descriptions are honest ("Combat comes later"; "Health, enemies and ammunition come later").
  - Prerequisites are checked with actionable fixes.
  - Runtime-only components are never saved.
  - Stop restores the scene, selection and undo history.
- **New test:** five Play/Stop rounds with Jolt and a scenario rule. Each Play gets fresh rules, listeners don't pile up, physics and the runtime are released at Stop, and the scenario, entities, player position and undo count are unchanged.
- **Text fix:** RTS said it "comes with the scenario editor", which is no longer true now that the editor exists. It now says what is still missing.
- **Gap:** heap growth over many cycles is not measured (proposed for A09).

## Done today: UrConnex asset migration report

The [migration report](../reports/migration-to-urconnex-report.md) answers the owner's plan to store assets in the UrConnex database and load them through its API. It is measurements and a proposal only; nothing was moved, deleted or uploaded.

- **Finding:** the working copy is 3.6 GB, but `assets/` is only 138 MB of it. Most of the space is regenerable output: `build/cc-by` and `build/oga` (1.6 GB), the editor package (735 MB) and `node_modules` (270 MB). Cleaning that frees about 2.4 GB with no code change.
- **Proposed design:**
  - Content-addressed assets (the TD-106 manifest already holds SHA-256s).
  - A `RemoteAssetSource` with a hash-verified offline cache in the engine.
  - A UrConnex `ProjectStore` in the editor.
  - Metadata in PostgreSQL, with the file bytes in object storage.
  - This falls under roadmap A16 and needs a new TD and an API contract written in the UrConnex repository.
- **Owner decisions:** seven are listed in section 7 of the report.

## Done today: disk cleanup and A21

- **Deleted, with owner approval:** regenerable build output totalling about 2.35 GB: `build/cc-by` and `build/oga` export, template, web-check and audit copies, and `editor-desktop/build/package`.
  - The two build folders went from about 2.57 GB to 211 MB.
  - The small evidence files the reports cite were kept; `cc-by-sample-art.md` notes the deleted export folder and how to regenerate it.
  - Everything deleted was Git-ignored, and no tracked file changed.
- **A21 added** at the end of the roadmap: asset storage in the UrConnex database, loaded through urconnex-api. The object-storage versus database-bytes decision is paused until urconnex-api is ready. See the [migration report](../reports/migration-to-urconnex-report.md).

## Done today: A15 (cross-platform journey), partial

See the [A15 report](../reports/alpha-a15-cross-platform-journey.md).

- **Automated chain extended:**
  - The desktop step now edits the scenario through the same Players and Rules editors the World tabs use (Player 2 renamed, the Welcome rule's timer changed).
  - The web step checks both edits arrived, and that nothing else differs from the original fixture except the six known edits.
  - The `3-desktop` fixture was regenerated, then verified without the update flag.
- **Already covered by A12:** conflict handling with no last-write-wins, refusal of future versions, missing assets, and interrupted-save recovery.
- **Blocked by A14:**
  - web terrain, ownership and rule editing;
  - web Play of rules and profiles;
  - both full journeys.
- **Belongs to A16/A21:** cancelled upload and large asset limits.

## Done today: A13 (web World shell)

See the [A13 report](../reports/alpha-a13-web-world-shell.md).

- **Page:** `engine-babylon`'s editor page now opens in the same composition as the desktop World layout:
  - a thin header with Panels (Outline and Details hidden by default);
  - map overlays: context, Top/Iso/Perspective, and a selection card with Move, Rotate, Duplicate and the Customize hand-off;
  - the desktop's seven categories, with one palette row;
  - a footer with Genre and Mode, Customize (Animations, Visual Scripting, Music) and Play.
- **Classic layout:** the earlier page is kept with `?layout=classic`.
- **Placement:** Objects, Buildings and Characters place the desktop's own palette prefabs, shipped by the sync script, as linked instances in the desktop's format, in one undo step. A committed fixture (`interop/w1-web`) and a desktop test (`WebPlacedPrefabsTest`) check that the desktop reads them as clean instances. The desktop test has not run yet.
- **Honest states:** Terrain, Players and Rules say they are edited on the desktop until A14, and Players and Rules show a read-only scenario summary.
- **Host:** built in `engine-babylon`, because urconnex-api isn't ready; embedding in UrConnex is A16.
- **Not checked:** browser rendering, touch, focus and accessibility. There is no browser automation here.

## Done today: a clickable test build of the editor

- Built with `gradlew :editor-desktop:packageEditor` (TD-033: jpackage app image with a bundled Java runtime; exit 0).
- Copied to `C:\Users\lorean.devries\ForgeroomEditorTest` (444 MB), outside the repository, so builds and cleanups don't remove it. Start it with `ForgeroomEditor.exe`.
- Smoke test of the copied `.exe` against a scratch project copy: `ForgeroomEditor.exe --project <scratch copy> --smoke-test`, exit 0. It drew 180 frames with 0 errors in the World layout, the map taking 88.9% of the window.
- It is a snapshot of today's working tree, including the uncommitted A04–A15 changes. Code changed later needs a rebuild and a fresh copy.

## Started today: A14 (web authoring and Play)

See the [A14 report](../reports/alpha-a14-web-authoring.md).

- **Web rule runtime:** `ScenarioRuntime.ts` is a port of the desktop interpreter with the same semantics and log lines. Web Play starts it after physics, feeds it trigger and sensor entries, and draws the objectives, message and result over the canvas.
- **Shared trace:** `fixtures/contract-v1/scenario-trace/valley.json` is replayed identically by both engines (`ScenarioTraceTest` on the desktop, `scenarioRuntime.test.ts` on the web).
- **Real physics:** a headless real-Jolt test walks the Hero into the door area and wins.
- **Decided by the owner (TD-110):** the web supports Action RPG + 3D and Platformer + 2.5D. The other five pairs stay desktop-only and are labelled.
- **Web profiles ported:** `src/runtime/PlayProfiles.ts`.
  - The same resolution and refusal wording as the desktop. Desktop-only pairs play without a profile, with a note.
  - The same checks (one player, a Character Controller 3D, one spawn point, a camera), and spawn placement.
  - `GroundWalkMover` (walk relative to the camera, turn to face, one jump per press) and `PlaneRunMover`, with a new `lockCharacterDepth` in the web physics.
  - The profile camera rig (third-person orbit with Q/E, or the side follow).
- **Shared movement trace:** `fixtures/contract-v1/profile-trace` holds two scenes, scripted keys and `expected.json`, recorded by the desktop's `ProfileTraceTest` (`GameSession`, real Jolt, reproduced exactly on rerun). The web's `profileTrace.test.ts` (real Jolt) lands every recorded point within 25 units.
- **Not ported yet:** the desktop's ground check under the player before Play, and the profile fixes (for example "Make Hero the player").
- **Browser editing of players, ownership and rules:** `scenarioEditing.ts` ports the desktop editors, with one undo step per change and the same limits and wording. The World shell's Players and Rules rows and the selection card's Owner list use it. Owned placement is one undo step, and referenced spawns and areas cannot be deleted. Terrain is now the only category still edited on the desktop only.
- **Cross-engine check:** `interop/w2-web` is a scenario authored with the web functions; the desktop's `WebAuthoredScenarioTest` validates it with no findings, edits it and plays it to victory.

## Test evidence

| Command | Exit | Result |
|---|---|---|
| `:engine-scene:test --tests "*scenario*"` | 0 | `ScenarioRuntimeTest` 11, `ScenarioValidatorTest` 8, 0 failures |
| `:editor-core:test --tests "*RulesEditorTest" --tests "*ScenarioEditorTest" --tests "*WorldShellTest"` | 0 | 8, 14 and 4 tests, 0 failures |
| `:editor-core:test --tests "*ScenarioPlayStepTest"` | 0 | 2 tests, 0 failures |
| `:engine-desktop:test --tests "*GameSessionScenarioTest"` (real Jolt) | 0 | 1 test, 0 failures |
| `:editor-terrain:test --tests "*PlacementOwnerTest"` | 0 | 4 tests, 0 failures |
| `npx vitest run` (`engine-babylon`) | 0 | 42 files, 532 tests passed |
| First full run, `gradlew test --continue --offline` (A04 code, before A05) | **1** | 2 failures, both from TD-108's spawn check against the old fixture: `CrossPlatformInteropTest.step3...` and `ContractRoundTripTest.shouldKeepTheEightMembers...`. Fixed by regenerating the fixture (above). |
| `:editor-sculpt:test --tests "*MeshDraftKeeperTest"` (A06) | 0 | 4 tests, 0 failures |
| `:editor-desktop:compileJava :editor-desktop:compileTestJava` (A06) | 0 | Compiles |
| Second full run, `gradlew test --continue --offline` (A04 and A05, started before the A06 change) | **0** | **2,661 tests in 24 modules: 0 failures, 0 errors, 1 skipped** (per-module table below) |
| Third full run | **1** | Never reached the tests. Configuration failed with `assets/assets.txt (Access is denied)`, because a targeted Gradle run was writing that file at the same moment. Not a code failure. Gradle runs are kept sequential from now on. |
| `:editor-core:test --tests "*ScenarioPlayStepTest"` (A07) | 0 | 3 tests, 0 failures |
| Fourth full run (A04–A07) | **stopped** | Claude Code stopped it because the machine was low on memory. It is not a test result and has not been restarted. **A full suite run covering A06 and A07 is still outstanding.** |
| `:engine-scene:test --tests "*GameGenresTest"` (A07 RTS text) | 0 | 12 tests, 0 failures |
| `GDXUNITY_UPDATE_INTEROP=1 :editor-desktop:test --tests "*CrossPlatformInteropTest"`, then without the flag (A15) | 0, 0 | 3 tests each, 0 failures |
| `npx vitest run` (`engine-babylon`, after A15) | 0 | 42 files, 532 tests passed |
| `npx tsc --noEmit` (`engine-babylon`, A13) | 0 | No type errors |
| `UPDATE_INTEROP=1 npx vitest run test/worldShell.test.ts` (A13) | 0 | 9 tests passed; `interop/w1-web` written |
| `gradlew :engine-scene:test --tests "*ScenarioTraceTest"` (A14) | 0 | 1 test, 0 failures |
| `npx vitest run test/scenarioRuntime.test.ts`, then `test/scenarioPlay.test.ts` (A14, real Jolt) | 0, 0 | 4 and 1 tests passed |
| `GDXUNITY_UPDATE_TRACE=1 gradlew :engine-desktop:test --tests "*ProfileTraceTest"`, then without the flag (A14) | 0, 0 | 1 test each; `profile-trace/expected.json` recorded, then reproduced |
| `npx vitest run test/profileTrace.test.ts` (A14, real Jolt) | 0 | 3 tests passed |
| `npx tsc --noEmit` (A14) | 0 | No type errors |
| `UPDATE_INTEROP=1 npx vitest run test/scenarioEditing.test.ts` (A14 editing) | 0 | 8 tests passed; `interop/w2-web` written |
| `npx vitest run test/worldShell.test.ts` (with owned placement) | 0 | 10 tests passed |
| `gradlew :editor-core:test --tests "*WebAuthoredScenarioTest"` (A14) | 0 | 1 test, 0 failures |
| `gradlew :editor-desktop:test --tests "*WebPlacedPrefabsTest"` (A13) | **stopped** | Claude Code stopped it because the machine was low on memory. Not restarted. |
| Fifth full Gradle run (A06, A07, A15) and full web suite (A13), run at the same time | **both stopped** | Claude Code stopped both because the machine was low on memory. Running them together was the mistake; they must run one at a time. **Still outstanding:** a full Gradle run (A06, A07, A15, A13's `WebPlacedPrefabsTest`) and a full web suite (A13). |

Per-module counts from the second full run (test classes / tests / skipped / failures / errors):

| Module | Classes | Tests | Skipped | Failures | Errors |
|---|---|---|---|---|---|
| editor-animation | 2 | 17 | 0 | 0 | 0 |
| editor-audio | 6 | 57 | 0 | 0 | 0 |
| editor-character | 1 | 10 | 0 | 0 | 0 |
| editor-core | 97 | 668 | 1 | 0 | 0 |
| editor-desktop | 54 | 223 | 0 | 0 | 0 |
| editor-sculpt | 13 | 72 | 0 | 0 | 0 |
| editor-sprite | 9 | 66 | 0 | 0 | 0 |
| editor-terrain | 12 | 72 | 0 | 0 | 0 |
| editor-visualscript | 19 | 94 | 0 | 0 | 0 |
| engine-animation | 4 | 32 | 0 | 0 | 0 |
| engine-audio | 22 | 233 | 0 | 0 | 0 |
| engine-character | 3 | 19 | 0 | 0 | 0 |
| engine-core | 11 | 61 | 0 | 0 | 0 |
| engine-desktop | 38 | 174 | 0 | 0 | 0 |
| engine-material | 3 | 15 | 0 | 0 | 0 |
| engine-physics | 4 | 60 | 0 | 0 | 0 |
| engine-render | 34 | 213 | 0 | 0 | 0 |
| engine-scene | 39 | 268 | 0 | 0 | 0 |
| engine-sculpt | 12 | 90 | 0 | 0 | 0 |
| engine-sprite | 7 | 49 | 0 | 0 | 0 |
| engine-terrain | 7 | 46 | 0 | 0 | 0 |
| engine-vector | 4 | 20 | 0 | 0 | 0 |
| engine-visualscript | 13 | 93 | 0 | 0 | 0 |
| engine-web | 3 | 9 | 0 | 0 | 0 |
| **Total** | **417** | **2,661** | **1** | **0** | **0** |

There are no clean "before" counts per module, because earlier filtered runs had overwritten the old XML results.

## Pending and blocked

- **Owner decisions:**
  - the A00 checkpoint approval
  - the A01 gap-list sign-off
  - the A02 and A03 walkthroughs
- **Human gates:**
  - A04: place two groups of objects, change their ownership, then save, reopen and inspect.
  - A05: create a reach-target objective and a timed message, test both, then edit without code.
  - A06: change a house wall or roof, return and replay; edit a sprite frame and replay.
  - A07: play every advertised pair with the keyboard, the three reference projects first.
  - None has been done, and the drawers and HUD have not been seen on screen by a person.
- **A04 limitations:**
  - Placement allows one Spawn Point per scene.
  - Play ignores ownership.
  - Scatter-erase removes owned copies without asking.
- **A05 limitations:**
  - Leaving an area does nothing.
  - There are no spawn effects or variables.
  - Rules do not run on the web (A14).
  - An objective's player cannot be edited in the drawer yet.

- **A06 limitations:**
  - Kept mesh drafts become unreachable when another scene is opened.
  - Quitting the editor does not warn about unsaved mesh edits (a candidate for A09).
  - Leaving 3D Modeling by the Sculpt toggle shows no notice.

## Next step

1. When the machine has memory to spare, and with nothing else running: the full Gradle suite, then (separately) the full web suite. They cover A06, A07, A13, A14 and A15. The last complete Gradle run (2,661 tests, 0 failures) covered A04 and A05.
2. The A14 next slice: web terrain sculpting and painting on `.terrain` files, then the ground check and profile fixes for the web profiles, then the browser-only journey (which needs browser automation, a new dependency for the owner to approve).
3. Look at the new web World shell and the clickable editor build (`C:/Users/lorean.devries/ForgeroomEditorTest/ForgeroomEditor.exe`). The human gates of A04–A07, A13 and A15 are listed above.
4. A08 waits on the owner's audio decision.
