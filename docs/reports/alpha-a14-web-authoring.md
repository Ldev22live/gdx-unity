# A14 Web scenario authoring and playable preview

**Implemented and integrated; not validated by a person.** TD-110 (the web profile subset) was approved on 2026-11-26 and TD-111 (Playwright) on 2026-11-27. The first slice is done: **scenario rules run in web Play with the same semantics as the desktop**, as TD-109 requires. One shared trace fixture is replayed by both engines with identical results.

The rest of A14 is open:
- the browser-only playable journey.

The starting HEAD was `85ccc0a`, with A04–A07, A13 and A15 uncommitted in the working tree.

## Done in this slice

| Part | Where | What it does |
|---|---|---|
| Web rule runtime | `engine-babylon/src/runtime/ScenarioRuntime.ts` | A TypeScript port of the desktop's `ScenarioRuntime`. It runs the closed Alpha set (`timer`, `enterArea`; `showMessage`, `completeObjective`, `endScenario`) in list order once per frame, with no chaining, at most once per frame per rule, and stops at the end. It applies the same checks (version, types, references, the area must have a trigger collider, limits), skips faulty rules and keeps unknown ones unrun. Rules never run code. The log lines match the desktop's exactly. |
| Web Play | `engine-babylon/src/runtime/ScenePlayer.ts` | Starts the rules at Play, after physics, as editor Play and `GameSession` do. It feeds them 3D trigger and 2D sensor entries, advances them after physics and scripts, and draws the objectives (top left), message (top centre) and result (centre) as HTML over the canvas. The rule log goes to `onRuleLog` (the browser console by default). Stop removes all of it. |
| Shared trace | `fixtures/contract-v1/scenario-trace/valley.json` | One scripted Play of the valley fixture: frames of 0.25 s and 0.5 s (exact in binary floating point, so neither clock drifts and no logged time is a rounding tie), a non-player entering the door area, the Hero entering, and a second entry after the end. It records the expected rule log, message, objective lines, result and skipped list. |

## Done in the second slice: the web's genre profiles (TD-110)

| Part | Where | What it does |
|---|---|---|
| Profiles | `engine-babylon/src/runtime/PlayProfiles.ts` | Ports of the desktop's `PlayProfiles` (resolution, checks, spawn placement, the movement-or-camera-only plan), `GroundWalkMover`, `PlaneRunMover` and `ProfileCameraRig`, for **Action RPG + 3D** and **Platformer + 2.5D**. They use the desktop's speeds, camera distances, pitch, turn rates and default keys. The other desktop pairs resolve to "plays on the desktop" and run without a profile; pairs nobody plays are refused with the desktop's words. |
| Depth lock | `engine-babylon/src/runtime/physics3d/Physics3D.ts` | `lockCharacterDepth` and `getLockedCharacterDepth`, with the desktop's semantics: walking in Z is ignored, and the character is put back on its plane after each step. |
| Web Play | `engine-babylon/src/runtime/ScenePlayer.ts` | A refused pair or a missing part stops Play with the reason (`onProfileMessage`). A playable pair starts its mover and camera rig after physics, before the first step. |
| Footer | `engine-babylon/src/editor/worldLayout.ts` | Shows what web Play will do with the scene's pair: its profile, desktop only, or the refusal. |
| Shared movement trace | `fixtures/contract-v1/profile-trace` | Two small scenes (from the valley fixture's ground, Hero and camera), a key script, and `expected.json`, recorded by the desktop's game runtime with real Jolt. |

**Results:**
- The desktop reproduces `expected.json` on rerun to within 0.5 units.
- On the web (real Jolt), every recorded point, both mid-air and landed, is within the trace's 25-unit tolerance. The two engines use different Jolt builds (jolt-jni versus jolt-physics WebAssembly), hence a tolerance rather than equality.

**Ported later, in the fifth slice:** the ground check and the profile fixes. Neither profile has been played by a person in a browser.

## Done in the third slice: players, ownership and rules edited in the browser

| Part | Where | What it does |
|---|---|---|
| Editing | `engine-babylon/src/editor/scenarioEditing.ts` | A port of the desktop's `ScenarioEditor`, `RulesEditor` and `ScenarioReferences`: create the scenario (two slots), add players and one neutral, rename (blank, over-long and case-insensitive duplicates refused), recolour, spawn (a Spawn Point only), bounds, remove (refused while it is referenced), assign owners; add, edit, reorder and remove rules and effects; objectives; Add Area. Each change is one `EditorModel.edit` undo step, tried on a copy first so a refusal leaves no step. Unknown members are kept; a newer block is read-only. The desktop's limits and wording are used throughout. |
| Deletion safeguard | `EditorModel.deleteEntity`, `EditorApp.deleteSelected` | An entity the scenario names (a spawn, a rule's area, an objective) is not deleted; the status line names the references. |
| Palettes | `engine-babylon/src/editor/scenarioPanels.ts`, `worldLayout.ts`, `world.css` | The World shell's **Players** row: the owner for new objects (Unowned by default), Add Player and Add Neutral, a card per slot (colour swatch, name, spawn, Remove) and the map bounds. The **Rules** row: Add Rule, Add Area, Add Objective, the objectives (text, shown or hidden, remove), and a card per rule (on/off, name, once/repeat, order, the condition, and the effects with their fields). The selection card has an **Owner** list. Placing a prefab with an owner chosen gives it that owner in the same undo step, and a removed owner makes placement refuse rather than place unowned. Terrain is now the only category still edited on the desktop only. |
| Cross-engine check | `fixtures/contract-v1/interop/w2-web`, `WebAuthoredScenarioTest` | A scenario made entirely with the web functions (players, a neutral slot, a spawn, owners, bounds, an objective, an area and two rules) is written by the web test. The desktop validates it with no findings, reads it as editable, edits it (a rename and a timer change) and plays it to victory. |

## Done in the fourth slice: terrain sculpting and painting in the browser

| Part | Where | What it does |
|---|---|---|
| Brushes | `engine-babylon/src/terrain/terrainStroke.ts` | A port of the desktop's `TerrainStroke`: Raise, Lower, Smooth, Flatten, Noise, Paint (soft-edged layer weights that never sum past full; inverted or Auto wipes), Hole, Erode (thermal, four passes) and Ramp. It uses the same dab spacing, falloff, noise hash and constants, in 32-bit floats, and returns a delta for undo. `terrainToJson` writes the desktop's `TerrainIO.toJson` layout, including Java's number spelling. |
| Shared terrain trace | `fixtures/contract-v1/terrain-trace` | Fifteen strokes with every brush type on a flat 33 × 33 grid. The desktop's result (`TerrainTraceTest`, reproduced byte for byte on rerun) is `expected.terrain`. The web gives the same terrain: heights within 0.05 units, paint weights within 1, holes exactly. |
| Undo | `EditorModel.recordExternal` | A change kept outside the scene file is one step of the same undo history. Undo and redo take strokes and scene edits back in order. |
| Live editing | `engine-babylon/src/editor/terrainEditing.ts`, `ScenePlayer.terrainOverrides`, `redrawTerrain` | With the Terrain palette open, the left button strokes the ground under the pointer (Ctrl inverts) and the camera orbits with the other buttons. The ground is redrawn after each dab, and web Play uses the edited ground. |
| Palette | `engine-babylon/src/editor/worldLayout.ts` | The Terrain row: the nine brushes, Size, Strength, the surface to paint, and Save Terrain. Save Terrain writes each edited `.terrain` through the browser's save dialog, or a download where there is none. A scene without ground says so. Every World category now works on the web. |

**Not on the web yet:**
- The road, river and water tools.
- Scatter.
- Writing the saved terrain into the project folder automatically: the browser saves where the person chooses; project storage is A16.

## Done in the fifth slice: New Ground, Play's fixes and the ground check

| Part | Where | What it does |
|---|---|---|
| New Ground | `engine-babylon/src/editor/terrainEditing.ts` (`createGround`), the Terrain row | The desktop's New Level: on a scene without ground, the Terrain row offers Small, Standard or Large (64, 128 or 256 cells, spacing 40) and a base surface. It makes a flat terrain at `terrains/Level.terrain` (numbered when taken) and a "Level Ground" entity with Terrain and Terrain Collider 3D, in one undo step. A second ground is refused with the desktop's words. The terrain stays unsaved until Save Terrain. |
| Fixes | `PlayProfiles.ts` (`ProfileProblem`, `ProfileFix`), `src/editor/profileFixes.ts`, the World shell footer | The web's Play checks now carry the desktop's safe fixes. **Make <name> the player** goes to the only entity named Player, else the only Character Controller 3D. **Add a Character Controller 3D** uses the desktop's defaults. **Add a camera** adds a "Main Camera". A refused Play shows the fixes as buttons beside Play; each fix is one undo step. |
| Ground check | `PlayProfiles.groundProblem`, `Physics3D.prepareBodies`, `ScenePlayer` | The desktop's ray down from just below the player's capsule, through the player's own shape, up to 100 m. The web's terrains load after the scene, so the check runs before the first physics step once the ground is in. Nothing underneath stops Play with the desktop's message. |

## The browser-only journey (TD-111)

`engine-babylon/e2e/worldJourney.spec.ts` drives the real editor page in headless Chromium with WebGL at 1280 × 720, through the World shell's own buttons and the map canvas:

1. **Terrain → New Ground** (Small).
2. **Raise** dragged across the map: the ground's highest point rises above 0.
3. **Players → Add Players**, and new objects owned by Player 1.
4. **Objects:** a tile, then a click on the map. The placed Boulder is owned by Player 1.
5. **Rules → Add Rule**, with the timer set to 1 s and the message "Welcome to the valley".
6. **Play:** the rule's message shows over the game. **Stop** removes it.
7. **Save and reopen:** the saved scene text, opened again, keeps the ground, the players, the owner and the rule. There are no script errors on the page.

**Captures** (automated, not a person's check):
- [World shell with the placed, owned Boulder](alpha-a14/world-shell-1280x720-placed.png)
- [Play with the rule message](alpha-a14/play-1280x720-rule-message.png)

**Seen in the captures:**
- After New Ground the view is not re-framed, so the editor camera sits close to the placed Boulder, which looks very large. Framing the ground after New Ground is a candidate fix.
- In Play the scene has no camera, so the editor's orbit camera is used. That is correct, but the view does not look like a game yet.

**Not covered:**
- Firefox, WebKit and mobile sizes.
- Saving through the browser's file dialog: the test reads the text the Save button writes instead of driving the dialog.
- A person using it: the roadmap's human gate is still open.

## Tests

| Command | Exit | Result |
|---|---|---|
| `gradlew :engine-scene:test --tests "*ScenarioTraceTest"` (desktop replays the trace) | 0 | 1 test, 0 failures |
| `npx vitest run test/scenarioRuntime.test.ts` (web replays the same trace; repeat timer at most once per frame; repeat area rule; skipped and newer rules; the effect limit) | 0 | 4 tests passed |
| `npx vitest run test/scenarioPlay.test.ts` (valley fixture in web Play with real Jolt: the timed message shows, and the Hero walked into the door area wins) | 0 | 1 test passed |
| `npx tsc --noEmit` | 0 | No type errors |
| `GDXUNITY_UPDATE_TRACE=1 gradlew :engine-desktop:test --tests "*ProfileTraceTest"`, then without the flag | 0, 0 | 1 test each: recorded, then reproduced |
| `npx vitest run test/profileTrace.test.ts` (both profiles with real Jolt, plus resolution of desktop-only and refused pairs) | 0 | 3 tests passed |
| `UPDATE_INTEROP=1 npx vitest run test/scenarioEditing.test.ts` (players, owners, spawn protection, bounds, preservation, read-only; rules, effects, objectives, areas; the w2 step) | 0 | 8 tests passed; `interop/w2-web` written |
| `npx vitest run test/worldShell.test.ts test/scenarioEditing.test.ts`, then `test/worldShell.test.ts` again (with the owned-placement test) | 0, 0 | 17 tests, then 10 tests passed |
| `gradlew :editor-core:test --tests "*WebAuthoredScenarioTest"` (the desktop validates, edits and plays the web-made scenario) | 0 | 1 test, 0 failures |
| `npx tsc --noEmit` (third slice) | 0 | No type errors |
| `GDXUNITY_UPDATE_TRACE=1 gradlew :engine-terrain:test --tests "*TerrainTraceTest"`, then without the flag | 0, 0 | 1 test each: recorded, then reproduced byte for byte |
| `npx vitest run test/terrainTrace.test.ts` (the shared strokes; exact undo and redo; the desktop's file layout; off-terrain refusal) | 0 | 3 tests passed |
| `npx vitest run test/editorModel.test.ts test/worldShell.test.ts test/terrainTrace.test.ts` (with the external-undo test) | 0 | 34 tests passed |
| `npx tsc --noEmit` (fourth slice) | 0 | No type errors |
| `npx vitest run test/playFixes.test.ts test/profileTrace.test.ts` (fixes one at a time until Play may start, with undo; the ground check with and without ground on real Jolt; New Level sizes, surface, undo, refusal and numbering; the profile trace still within tolerance) | 0 | 6 tests passed |
| `npx tsc --noEmit` (fifth slice) | 0 | No type errors |
| `npm install --save-dev @playwright/test`, then `npx playwright install chromium` (TD-111) | 0, 0 | `@playwright/test` ^1.65.1 in devDependencies; Chromium in the user cache |
| `npx playwright test` (the browser-only journey, real Chromium and WebGL) | 1, then 0 | First run: the test's own selector counted the bounds fields as name fields; after fixing the selector, 1 test passed (about 20 s), twice |
| `npx vitest run` (full web unit suite, run alone) | 0 | 49 files, 565 tests passed |

The full web suite and the full Gradle suite were **not** run after this slice. The last attempts were stopped for lack of memory (see the [progress report](../progress/progress-reports-26-11-2026.md)).

## What A14 still needs

1. **Web profiles: decided.** The owner approved **Action RPG + 3D and Platformer + 2.5D** on 2026-11-26 ([TD-110](../TECHNICAL_DECISIONS.md#td-110-the-webs-alpha-genre-profiles-are-action-rpg--3d-and-platformer--25d-alpha-a14)). **Done in the second slice** (below). `FallRespawner` was not needed: neither approved pair respawns after a fall. The reasons for the choice:
   - Both have desktop profile tests with real physics (`ActionRpgValleyProfileOnJoltTest`, `PlatformerSideProfileOnJoltTest`).
   - Both run on Jolt, which the web already loads.
   - Platformer + 2D would need the web's planck path matched to TD-105's rigid-body movement first.
   - Today the web runs no genre profile at all (`BACKEND_SUPPORT_MATRIX.md`).
2. Ownership, spawn and rule editing in the browser: **done in the third slice** (above).
3. Terrain sculpting and painting on `.terrain` files in the browser: **done in the fourth slice** (above).
4. The browser-only journey and its real-browser test: **done** (TD-111, above). What remains is the human gate: a person makes a small playable scenario in the browser.

## Known limitations

- Rule editing and the HUD's look have not been seen in a browser.
- The web's rule log goes to the browser console; the editor shows no Output Log panel for it yet.

No commit, push or destructive Git operation was performed.
