# Roadmap M3b.4: Game Genre and Game Mode Selectors (2026-11-15)

Milestone M3b.4 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b), built to [TD-088](../TECHNICAL_DECISIONS.md) and owner decision 11 (the footer's Game Mode is the scene's own mode). **Status:**
- **Implemented and Integrated.** The footer's Game Genre is a drop-down. It is fed by the same genre registry Play uses.
- **Seen in the running editor** in screenshots at 1920x991 and at the 1024x640 minimum.
- **Manually Not Verified (Headless Environment):** the drop-down was not opened or clicked. Choosing is tested headless.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3, M3b.1 to M3b.3 and M3c.1 work (listed in their reports).
- No new module, dependency or decision.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Game Genre and Game Mode selectors in the footer beside Customize and Play | **Met** | Footer order: Game Genre (drop-down), Game Mode (2D, 2.5D, 3D), the pair's status line, then Customize and Play. Game Mode stays the M1b.3 buttons, which decision 11 made the scene's mode. Screenshots in section 3; `ActionInventoryTest` checks each is built once |
| 2 | Independent of the open view | **Met** | Both set the scene, not the view; the footer is the same in every workspace (M1b.3) |
| 3 | Fed by the registry | **Met** | The entries are None plus every genre in `GameGenres`, in order, and nothing is hard-coded (`GenreChoicesTest`). The editor passes the registry that Play's profile step uses |
| 4 | Unimplemented pairs visibly marked and never launched silently | **Met** | **The genre's own entry:** a pair that is not playable is marked "(planned)" or "(not offered)" in the scene's mode.<br>**The line after Game Mode:** "Playable", "Planned: Play refuses it", "Not offered: Play refuses it", "Unknown: Play refuses it", or "Scripts only" without a genre.<br>**The mode buttons:** with a genre set, modes it does not play in are dimmed.<br>**Play:** it already refuses such pairs with their reason (M3b.3) |
| 5 | Changing the genre label does not imply gameplay exists | **Met** | Choosing a planned pair says in the status bar: "… has no gameplay yet, and Play will refuse it", with the reason and the modes it does play in. Choosing a playable one says Play adds its camera and movement to the Player |
| 6 | Choices persist | **Met** | The genre is the scene's `gameGenre` field; saving and reopening keeps it (`GenreChoicesTest`). Like the mode, it is not an undo step and is locked during Play |

## 2. What was built

- **`editor-core` `play`:**
  - `GenreChoices`: the entries and their marks per mode, the status line, the modes a genre plays in, and the change message;
  - `choose`, which sets the genre and writes the message.
  - An unknown genre id from a newer or web-written file is shown as its own entry and kept (TD-088).
- **`editor-desktop`:**
  - `GameGenreSelector`: a 190-pixel drop-down and the status line. Long names end in "…" when the box is closed, so Game Mode stays in view at the smallest window.
  - `GameModeSelector`: dims the text of modes the scene's genre does not play in. It can't tint the button, because the button background is already tinted for the selected mode.
  - `EditorFooter`: takes the selectors and the status line. The M3b.3 read-only genre text (`genreText`, `GENRE_NOT_YET`) is removed, with its test.
- **Web contract:** the generated footer section of `web-ui-ux-spec.md` now lists the genre choices per mode. The hand-written lines that said "none yet (planned)" were updated.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*GenreChoicesTest"` | 0 | 5 of 5 |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*WebUxContractTest"`, then `--tests "*GameModeSelectorTest" --tests "*ActionInventoryTest" --tests "*WebUxContractTest"` | 0, 0 | 2, 6 and 4 |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_ASSISTANT=0 ./gradlew :editor-desktop:run --args="--scene samples/<sample>.json --smoke-test"`: `action-rpg-valley-3d` at 1920x991, then `platformer-profile-25d` and `terrain-3d` with `GDXUNITY_EDITOR_WINDOW=1024x640` | 0 (each, twice) | "180 frames, 0 error(s)". The first pair of frames showed two problems (section 4) |
| `./gradlew :editor-core:cleanTest :editor-core:test`, then the same for `editor-desktop` | 0, 0 | 489 (1 skipped, as before) and 160 |

| Module | Before (M3c.1) | After |
|---|---|---|
| `editor-core` | 484 | 489 |
| `editor-desktop` | 160 | 160 (−2 footer text tests, +2 mode button tests) |

No engine module changed. The other modules were last run in M3c.1 (all pass).

**Screenshots** (looked at):
- [m3b-4/footer-action-rpg.png](m3b-4/footer-action-rpg.png) (1920x991): Game Genre "Action RPG"; Game Mode with 3D selected and 2D and 2.5D dimmed; "Playable" in green.
- [m3b-4/footer-narrow-platformer.png](m3b-4/footer-narrow-platformer.png) (1024x640): "Platformer / Side-scroller", with 2.5D selected and 2D and 3D dimmed; "Playable"; Customize and Play in view.
- [m3b-4/footer-no-genre-narrow.png](m3b-4/footer-no-genre-narrow.png) (1024x640): `terrain-3d` without a genre. "None", no mode dimmed, and the status line "Scripts only" cut off by the footer's scrolling area, which may scroll at this size.

## 4. Failures found and fixed

- **The first frames showed no dimming.** The button tint was applied to the background, which is too dark for the change to show. The dimming now tints the button text.
- **At 1024x640 the long drop-down and status line pushed two of the three mode buttons out of view.** The drop-down now has a fixed width. The status line moved after Game Mode and was shortened; the long reason went to the status bar.

## 5. Diff review

- **New:** `GenreChoices`, `GenreChoicesTest`, `GameGenreSelector`, `GameModeSelectorTest`; this report and `docs/reports/m3b-4/`.
- **Changed:** `GameModeSelector`, `EditorFooter`, `EditorApplication`; tests `ActionInventoryTest`, `WebUxContractTest`.
- **Removed:** `EditorFooterGenreTest`. It was written in M3b.3 for the read-only genre text, which this milestone replaced.
- **Docs:**
  - the chart (M3b.4, M1b.3 and R3b);
  - `FEATURE_TEST_MATRIX.md`, `INDEX.md`;
  - TD-088 implementation notes;
  - `web-ui-ux-spec.md` (generated section and two hand-written lines).

No files outside this repository were touched. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Manual check for a person** (Windows):
  1. Open `samples/terrain-3d.json`. Game Genre reads "None" and the line after Game Mode reads "Scripts only".
  2. Open the drop-down. Action RPG has no mark, Platformer reads "(planned)", and the shooters read "(planned)" in 3D.
  3. Choose Platformer. The status bar says Platformer + 3D has no gameplay yet and that it plays in 2.5D; 2D and 3D are dimmed.
  4. Press Play. It is refused with the reason.
  5. Choose Action RPG. Hero has no Player, so Play offers "Make Hero the player".
  6. Save, reopen: the genre is kept.
- **The genre is not an undo step**, like the mode (TD-067, TD-088).
- **No web selector**; the web player plays no genre.
- **Next:** M3c.2 (player prefab and spawn point), or the rest of M3b.5: buttons for the safe fixes, which step 5 of the manual check would use, and the ground check at Play.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
