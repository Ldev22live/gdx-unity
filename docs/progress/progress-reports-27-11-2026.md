# Progress report: 27 November 2026

This report tracks progress against [milestone-roadmap.md](../milestone-roadmap.md), the single release plan. Milestone detail is in `docs/reports/`. The previous day is [26-11-2026](progress-reports-26-11-2026.md). Status words follow AGENTS.md §9:

- **Implemented** means code exists with automated tests.
- **Integrated** means a user can reach it.
- **Validated** means a person checked it in the running app.

- Branch: `phase-12-3d-foundation`
- HEAD: `85ccc0a`, both at the start and the end of the day. Everything since is uncommitted in the working tree.
- No commit, push or destructive Git operation was performed.

## Where we are

| Gate | Status today |
|---|---|
| A00–A03 | Unchanged: the owner approvals and walkthroughs are outstanding. |
| A04 Players and scenario setup | Implemented and integrated (desktop). Human walkthrough pending. |
| A05 Triggers and objectives | Implemented and integrated (desktop). Human walkthrough pending. |
| A06 Customize and return to World | Audit done; the mesh-draft gap was fixed. Human edit loop pending. |
| A07 Play/Stop and scenario runtime | Automated audit done. Human keyboard play pending. |
| A08 Templates and audio gate | Waits on the owner's audio decision. |
| A09–A10 | Pending. |
| A11–A12 | Done or met in tests (unchanged). |
| A13 Web World shell | Implemented in `engine-babylon`. Not seen in a browser. |
| **A14 Web authoring and Play** | **Fourth and fifth slices done today:** terrain sculpting and painting, New Ground, and Play's fixes and ground check in the browser. Done so far: scenario rules in web Play; the two web profiles (TD-110); players, ownership and rule editing; terrain. The browser-only journey now passes in real Chromium (TD-111, Playwright). **Implemented and integrated; not validated by a person.** |
| A15 Cross-platform journey | Partial; the full journeys need A14's browser journey. |
| A16–A20 | Open or blocked (unchanged). |
| A21 Asset storage in the UrConnex database | Added 2026-11-26; paused until urconnex-api is ready. |

## Done today: A14 fourth slice (terrain in the browser)

See the [A14 report](../reports/alpha-a14-web-authoring.md).

- **Brushes:** `terrainStroke.ts` ports the desktop's `TerrainStroke`: Raise, Lower, Smooth, Flatten, Noise, Paint, Hole, Erode and Ramp, with the same constants, falloff and noise, in 32-bit floats. `terrainToJson` writes the desktop's `.terrain` layout.
- **Shared terrain trace:** `fixtures/contract-v1/terrain-trace` holds fifteen strokes with every brush type. The desktop's result is `expected.terrain` (reproduced byte for byte on rerun). The web matches it: heights within 0.05 units, paint weights within 1, holes exactly.
- **Editor:**
  - The World shell's Terrain category now sculpts and paints the ground under the pointer (Ctrl inverts), with Size, Strength and Paint surface.
  - Each stroke is one step of the scene's undo history (the new `EditorModel.recordExternal`).
  - The ground redraws as you drag, and web Play uses the edited ground.
  - Save Terrain writes the desktop's `.terrain` text through the browser's save dialog or a download.
  - Every World category now works on the web.

## Done today: A14 fifth slice (New Ground, Play's fixes, ground check)

- **New Ground:** the desktop's New Level on the web: Small, Standard or Large and a base surface. It makes a flat `terrains/Level.terrain` and a "Level Ground" entity with Terrain and Terrain Collider 3D, in one undo step. A second ground is refused.
- **Play's fixes:** a refused web Play offers the desktop's safe fixes as buttons beside Play (Make the player, Add a Character Controller 3D, Add a camera), each one undo step.
- **Ground check:** the desktop's ray down from the player, run on the web once its terrains have loaded. Nothing underneath stops Play with the desktop's message.

## Done today: A14 browser journey (TD-111)

- **Approved by the owner:** Playwright as an `engine-babylon` dev dependency (TD-111). Chromium sits in the user cache, outside the repository.
- **`e2e/worldJourney.spec.ts`:** the browser-only journey in real Chromium with WebGL, through the World shell's buttons and the map. It runs New Ground, a Raise drag, players, an owned placement, a rule, Play with the rule's message on screen, Stop, then save and reopen with everything kept and no page errors. It passed twice.
- **Captures** (automated, not a person's check):
  - [World shell](../reports/alpha-a14/world-shell-1280x720-placed.png)
  - [Play with the rule message](../reports/alpha-a14/play-1280x720-rule-message.png)
- **Seen in the captures:** the view is not re-framed after New Ground, so a placed object looks very large.
- **Full web unit suite, run alone:** 49 files, 565 tests passed.

## Test evidence

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_TRACE=1 gradlew :engine-terrain:test --tests "*TerrainTraceTest"`, then without the flag | 0, 0 | 1 test each; `expected.terrain` recorded, then reproduced byte for byte |
| `npx vitest run test/terrainTrace.test.ts` | 0 | 3 tests passed |
| `npx vitest run test/editorModel.test.ts test/worldShell.test.ts test/terrainTrace.test.ts` | 0 | 34 tests passed |
| `npx tsc --noEmit` (`engine-babylon`) | 0 | No type errors |
| `npx vitest run test/playFixes.test.ts test/profileTrace.test.ts` (A14 fifth slice) | 0 | 6 tests passed |
| `npx tsc --noEmit` (fifth slice) | 0 | No type errors |
| `npx playwright test` (A14 browser journey, TD-111) | 1, then 0, 0 | First run failed on the test's own selector; then 1 test passed, twice |
| `npx vitest run` (full web unit suite, run alone) | 0 | 49 files, 565 tests passed |

**Still outstanding from 26-11-2026:**
- A full Gradle run covering A06, A07, A13, A14 and A15. The last complete run covered A04 and A05: 2,661 tests, 0 failures.
- A full web suite run.
- `WebPlacedPrefabsTest` (A13).

All three were stopped for lack of memory and must run one at a time, with nothing else running.

## Pending and blocked

- **Owner decisions:**
  - the A00 checkpoint approval
  - the A01 gap-list sign-off
  - the A02 and A03 walkthroughs
  - the A08 audio decision
- **Human gates:** A04–A07, A13, A14 and A15 (listed in the 26-11-2026 report). Nobody has used the web editor in a browser yet.
- **A14 limitations:**
  - No road, river, water or scatter tools on the web.
  - A saved terrain is written where the person chooses, not into the project folder (project storage is A16).

## Next step

1. With nothing else running: the full Gradle suite, then the full web suite.
2. A14 is implemented and integrated. Next: frame the view after New Ground (seen in the captures), the human A14 gate, then A15's full cross-platform journeys, which A14 unblocked.
3. Open `engine-babylon` (`npm run dev`, `editor.html`) and the clickable desktop build (`C:/Users/lorean.devries/ForgeroomEditorTest/ForgeroomEditor.exe`, built 26-11-2026, before today's web work) to check the new web editor by hand.
