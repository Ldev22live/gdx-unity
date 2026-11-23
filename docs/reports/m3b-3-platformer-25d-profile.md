# Roadmap M3b.3: Platformer + 2.5D, the First Playable Genre Profile (with M3b.5's Play Step) (2026-11-15)

Milestone M3b.3 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b), built to [TD-088](../TECHNICAL_DECISIONS.md). As the [M3b.1 audit](m3b-1-play-profile-audit.md) planned, the profile step that M3b.5 describes was built in the same change, because M3b.3's done-when needs Play to apply the profile. **Status:**
- **Implemented and Integrated.** Platformer + 2.5D is the first playable pair.
- **The done-when is met in automated tests on real Jolt:** "in Play the player runs and jumps along the plane and collides with the level".
- **Manually Not Verified (Headless Environment):** nobody has played it with the keyboard.
- **The last full test run did not complete** (section 3).

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3, M3b.1 and M3b.2 work (listed in their reports).
- Approvals: TD-088 and its amendments (2026-11-15). No new module and no new dependency.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Side camera | **Met** | The camera follows the runner at 900 units in front of the play plane (`PlatformerSideProfileOnJoltTest`); the game-runtime frame shows the side view |
| 2 | Movement held to the play plane | **Met** | The runner stays at depth 0 within 0.01 units, also against an angled crate. **Mutation check:** with the depth correction turned off, the runner slid round the crate's angled face to z = 170 and past it, and the test failed. The original was restored |
| 3 | Jump | **Met** | Holding Space jumps once, not again on landing; the runner lands on the low platform (top 120) |
| 4 | Collides with the level | **Met** | It stops against the low platform's side, the red wall and the angled crate, within 5 units of the expected place (Jolt's contact margin) |
| 5 | Applied to the runtime copy only, through runtime-only components | **Met** | In editor Play the copy has `PlaneRunMover` and the rig, and the authored scene has neither. After Stop the authored scene's JSON is identical and still saves, and the undo count is unchanged. Saving the played scene is refused (`ProfilePlayStepTest`, `PlatformerSideProfileOnJoltTest`) |
| 6 | Play and the game runtime agree | **Met** | Both call `PlayProfiles.start`. The acceptance test uses the game runtime; the editor test uses `PlayModeController` |
| 7 | A refused pair stops Play with its reason; missing parts are named with a fix | **Met** | Platformer + 3D stops Play with "cannot be played yet". "No player" offers "Make Runner the player", and Play works after the fix (`ProfilePlayStepTest`) |
| 8 | A pair is playable only with its acceptance test | **Met** | `PlayableGenrePairsTest` lists `PlatformerSideProfileOnJoltTest` for `platformer/2.5d` |

## 2. What was built

- **`engine-scene`:**
  - `PhysicsSystem3D.lockCharacterDepth`, `unlockCharacterDepth` and `getLockedCharacterDepth`. A locked character is put back on its plane after each fixed step, keeping its speeds; a teleport would stop jumps.
  - `GameGenres.platformer25D` and the playable entry.
- **`engine-render` `play`:**
  - `PlaneRunMover`: A/D or the arrows run at 450 units per second; Space jumps at 650 (about 2.15 m), once per press.
  - `ProfileCameraRig`: a `FOLLOW` rig 900 units in front of the plane and 150 above the player.
  - `PlayProfiles.apply` and `start`, and `ProfileStart`.
- **`editor-core`:**
  - `ProfilePlayStep`, which `PlayModeController` registers as its first extension, so refusals use the existing cleanup.
  - Play's status line says what the scene played as, e.g. "Playing as Platformer / Side-scroller + 2.5D."
- **`engine-desktop`:** `GameSession` runs the same step after 3D physics starts, with `setInput` and `getProfileMessage`.
- **`editor-desktop`:**
  - The footer's Game Genre now shows the open scene's genre and its status, read only, e.g. "Platformer / Side-scroller (playable)". Before, it said "none yet" even for a scene with a genre. The selector is still M3b.4.
  - The Player Details note and toggle were fixed after the editor frame showed them clipped and overlapping (section 4).
- **Sample `platformer-profile-25d.json`** (from `tools/make_profile_samples.py`):
  - ground, two walls, three platforms, a red wall to jump and a crate turned 35 degrees, all boxes with Box Collider 3D;
  - a Runner with a Character Controller 3D and a Player;
  - a camera; Game Mode 2.5D and Game Genre Platformer;
  - **no scripts and no graphs**.
  - It is in the template gallery (desktop only, with a preview) and in the web test's list of known unsupported components (`PlayerComponent`).

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test :engine-render:test :engine-desktop:test --tests "*PlatformerSideProfileOnJoltTest"` | 1, then 0 | Two of my test steps were wrong (section 4); then 4 of 4 pass |
| Depth-lock mutation check (section 1, row 2) | 1 | 1 of 4 failed, as intended; restored |
| `./gradlew :editor-core:test --tests "*ProfilePlayStepTest"` | 0 | 3 of 3 |
| `./gradlew cleanTest test --continue` (after the code was complete, before the gallery and footer changes) | 1 | **2,042 tests, 3 failures, 1 skipped.** All three failures came from the new sample: it was not in `templates/gallery.json`, and the two generated audit tables were stale. Each was fixed and rerun |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*EditabilityAuditTest" --tests "*TemplateMatrixTest" --tests "*WebUxContractTest"`, then the same with `*TemplateGalleryTest`, `*SampleGamesTest` and `*TemplateGalleryProjectsTest` without the variable | 0, 0 | Tables refreshed; all pass |
| `./gradlew :editor-desktop:test --tests "*EditorFooterGenreTest"` | 0 | 2 of 2 |
| `npx vitest run` and `npx tsc --noEmit` in `engine-babylon` | 0, 0 | 479 tests, 0 failures; the type check passes |
| `./gradlew :engine-desktop:installDist :engine-desktop:gameScriptsClasses`, then `python tools/make_template_previews.py platformer-profile-25d` | 0, 0 | Preview written. It runs the installed game runtime, so the profile ran there: the runner stands in the side view |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--scene samples/platformer-profile-25d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)" |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_SELECT=Runner GDXUNITY_EDITOR_ASSISTANT=0 ./gradlew :editor-desktop:run --args="--scene samples/platformer-profile-25d.json --smoke-test"` | 0 | "180 frames, 0 error(s)", run twice (before and after the Details and footer fixes) |
| `./gradlew cleanTest test --continue` (final, after all fixes) | none | **Not completed.** Claude Code stopped it because the machine ran low on memory, not because of a failure. It was not restarted; it should be run again |

Per-module counts. The baseline is the M3b.2 report; "after" is the last complete full run, plus the two footer tests added after it:

| Module | Before | After |
|---|---|---|
| `editor-core` | 480 | 483 |
| `engine-desktop` | 100 | 104 |
| `editor-desktop` | 158 | 160 |
| Web | 478 | 479 |

**Screenshots** (looked at):
- [m3b-3/runtime-frame-120.png](m3b-3/runtime-frame-120.png): the game runtime at frame 120, a side view. The runner stands on the green ground between the grey start wall and the low platform.
  - It also showed my help text in dark grey on the dark background, and too long for the window. I changed it to light text and one short line in the generator. That change was **not run again** after the memory stop.
- [m3b-3/editor-runner-details.png](m3b-3/editor-runner-details.png): the editor with the Runner selected. Details ends with Player (two note lines and the Use profile toggle); the footer reads "Game Genre Platformer / Side-scroller (playable)", "Game Mode 2.5D".

## 4. Failures found and fixed

- **My jump test ran off the platform.** It held D through the whole jump, so the runner crossed the low platform and fell beyond it. The test now lets go of D after half a second, over the platform.
- **My "never saved" assertion expected the runner's name.** The serializer names the first entity with a runtime-only part, which is the camera with its rig. The assertion now checks the message's reason.
- **The new sample broke three checks:** the gallery's list of samples and two generated audit tables. The sample was added to `templates/gallery.json` (not for the web) and the tables were regenerated. The web test needed `PlayerComponent` listed as unsupported for the sample.
- **The editor frame showed three problems:**
  - The Player note was clipped. It is now two short lines.
  - "Use Profile Movement" ran into its toggle. The row is now "Use profile".
  - The footer still said "Game Genre: none yet (planned)" for a scene that has a genre, which was untrue. It now shows the genre and its status (`EditorFooterGenreTest`).
  - The web UX contract's generated note about the footer was reworded to match.
- **Help text unreadable in the game** (section 3); fixed in the generator, not run again.

## 5. Diff review

- **New:**
  - `engine-render` `play`: `PlaneRunMover`, `ProfileCameraRig`, `ProfileStart`;
  - `editor-core`: `ProfilePlayStep`;
  - tests: `PlatformerSideProfileOnJoltTest` (4), `ProfilePlayStepTest` (3), `EditorFooterGenreTest` (2);
  - `tools/make_profile_samples.py`, `assets/samples/platformer-profile-25d.json`, `assets/templates/previews/platformer-profile-25d.png`;
  - this report and `docs/reports/m3b-3/`.
- **Changed:**
  - `PhysicsSystem3D`, `GameGenres`, `GameTestProfile` (Javadoc), `PlayProfiles`, `PlayModeController`, `GameSession`;
  - `EditorFooter`, `EditorApplication`, `PlayerComponentEditorProvider`;
  - `assets/templates/gallery.json`;
  - tests: `GameGenresTest`, `PlayableGenrePairsTest`, `WebUxContractTest` (wording), web `sceneBuilder.test.ts`.
- **Docs:**
  - TD-088 implementation notes;
  - the chart;
  - `FEATURE_TEST_MATRIX.md` (row and regenerated tables);
  - `INDEX.md`;
  - `web-ui-ux-spec.md` (the regenerated footer note).

No other sample changed. No files outside this repository were touched. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Run again:**
  - the full test suite, which was stopped for low memory;
  - the game runtime on the sample, to see the fixed help text.
- **Manual check for a person** (Windows):
  1. Open `samples/platformer-profile-25d.json`. The footer reads "Platformer / Side-scroller (playable)".
  2. Press Play. The status line reads "Playing as Platformer / Side-scroller + 2.5D."
  3. Run with A/D and the arrows, jump onto the platforms, jump the red wall, and walk into the angled crate: you are not pushed toward or away from the camera.
  4. Stop. The scene and undo history are as before.
  5. Remove Player from the Runner and press Play: Play refuses, with "No player".
- **Not built yet:**
  - choosing a genre in the editor (M3b.4);
  - a UI that shows the safe fixes as buttons; today the first problem is in the status line (M3b.5);
  - the ground check by a downward ray at Play (M3b.5);
  - Action RPG + 3D (M3c.1);
  - agent tools (M3b.7);
  - the web player (it does not know the Player component).
- **Existing 2.5D samples are unchanged.** They still use their own `SideBody` scripts; TD-088 does not migrate them.
- **Next on the critical path:** M3c.1 (Action RPG + 3D: the third-person camera and ground walking on `terrain-3d`'s valley). M3b.4 (the Game Genre dropdown) is the other R3b step a person would notice.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
