# M1b.3 Footer: Genre, Mode, Customize, Play (World-first stage 3): report

**Milestone:** M1b.3 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1b, Appendix E1), **stage 3** of the staged plan in [world-first-ux-audit.md](world-first-ux-audit.md).
**Owner decisions (2026-11-14):** **decision 11**: the footer's Game Mode *is the scene's own mode* (saved with the scene, used by Play; no separate play-time override). **TD-084** (Customize gateway) approved as built. The other part of decision 11, renaming Viewport to World, is still open.
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21, real display for the smoke runs.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this stage touched are listed below.

## Done-when and result

The chart's done-when: *the footer is visible from World and other workspaces where it applies.*

| Criterion (chart deliverable) | Result |
|---|---|
| A persistent footer | **Met:** one footer row above the status bar, outside the tabs, so it shows in every workspace (screenshots in the World and in the Music tab) |
| Game Genre selector | **Shown as planned, not a selector:** there is no genre system (R3b, M3b.4 is this milestone's other dependency), so it reads "Game Genre none yet (planned)" instead of offering choices that would do nothing |
| Game Mode selector (2D, 2.5D, 3D), independent of genre | **Met:** today's mode buttons, moved from toolbar row 2. Per decision 11 they set the scene's own mode, as before |
| Customize and Play adjacent, Play the primary action | **Met for placement:** the Customize gateway, then Play, Pause and Stop at the right end. Play keeps its existing look; it is not drawn larger than the others |
| On smaller screens both stay visible, never in a menu | **Met at the editor's minimum window, 1024x640:** the right side never shrinks; the Genre and Mode side scrolls sideways when there is not enough room |
| Editor view orientation stays separate | **Met:** the 2D/3D view toggle stays in the viewport overlay; the footer has only the scene's mode |

## What was built

- **`EditorFooter`** (`editor-desktop`): Game Genre and Game Mode on the left inside a sideways-scrolling area, then the Customize gateway and the play controls on the right at full size.
- **`EditorToolbar`** gained a constructor that leaves out the play controls, and `getPlayControls()`, so the same Play, Pause and Stop (still updated by the toolbar's play-state code) sit in the footer. Play, Pause, Stop, the mode buttons and the Customize gateway were **moved, not copied**: each exists once. The M1b.7 `addBesidePlay` hook was removed.
- **`EditorApplication`**: the footer is added between the main panels and the status bar.

**Found on screen and fixed:** the first screenshots showed the Genre and Mode group **centred** in the footer (the scrolling area stretches its content), and at 1024x640 a long genre text ("none yet (genre profiles are planned, R3b)") pushed the mode buttons out of view. The group is now left-aligned and the text is "none yet (planned)". The Javadoc names R3b.

## Tests (1 new; `editor-desktop` 142 to 143)

| Test | What it pins |
|---|---|
| `ActionInventoryTest.shouldPutGameModeCustomizeAndPlayInTheFooterOnly` (new) | The footer is built with the scene's mode selector, the Customize gateway and the toolbar's play controls; the toolbar is built without the mode selector and the play controls; exactly one mode selector and one gateway are created; the footer is added to the layout. **This reads `EditorApplication`'s source**, because the shell needs a window; the screenshots are the evidence that it renders |

The other `ActionInventoryTest` tests still pass, so every action keeps its route. Decision 11 needs no new engine test: the selector was already the scene's mode (TD-067, `GameModeTest`), and only its place changed.

## Evidence from the real editor

`./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_SCREENSHOT`, after the fix. Each run rendered 180 frames with 0 errors.

- [m1b-3-footer/footer-world.png](m1b-3-footer/footer-world.png) (1920x991, World): `Game Genre none yet (planned)  Game Mode 2D 2.5D 3D ... World Customize | Play Pause Stop Stopped`; toolbar row 2 now holds only the view icons, fly speed, Compile Scripts and AI Assistant.
- [m1b-3-footer/footer-music-1024x640.png](m1b-3-footer/footer-music-1024x640.png) (`GDXUNITY_EDITOR_TAB=Music`, `GDXUNITY_EDITOR_WINDOW=1024x640`): the whole footer fits, `... Audio & Music Customize Back to World | Play Pause Stop Stopped`.

**Nothing in the footer was clicked**: Play, Pause and Stop from their new place, the mode buttons and Customize were not tried on screen.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-desktop:test` (before and after the alignment fix) | 0, 0 | **143 tests, 0 failures** |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with screenshots, at 1920x991 and 1024x640, twice (before and after the fix) | 0 each | 180 frames, 0 errors each |

`editor-core` did not change in this stage (435). The repository total is 1,916 + 1 = **1,917 (computed, not a fresh full run)**.

## Limits

- **Manually Not Verified (Headless Environment):** clicking anything in the footer.
- **Game Genre is a label**, not a selector, until genre profiles exist (R3b).
- **Play is not visually emphasized** beyond its existing style; making it larger is a styling choice for the owner.
- On the World, the hidden Back to World leaves a small gap between Customize and the divider.
- The layout test reads the source; a renamed variable breaks it without a real regression.
- The top bar is still wider than a 1920-pixel window and scrolls (pre-existing; stage 4).

## Next stage

Stage 4 is the rest of M1b.2 and M1b.4: the World strip (from the level palette: Terrain and Objects), collapsible Outliner, Details, Content Browser and Log, the tabs and tool buttons moving out of the top bar, and the World as the default screen. The plan gates it on **a layout TD** (TD-085), which I would write as proposed, for approval, as with TD-084.

## Files changed

- New: `editor-desktop/.../ui/EditorFooter.java`, this report, `m1b-3-footer/*.png`.
- `editor-desktop/.../ui/EditorToolbar.java`, `.../EditorApplication.java`, `.../audit/ActionInventoryTest.java`.
- `docs/TECHNICAL_DECISIONS.md` (TD-084 approved), `docs/milestone-chart.md` (M1b.3 and M1b.7 rows, decision 11), `docs/reports/world-first-ux-audit.md`, `docs/reports/m1b-7-customize-report.md` (approval and later move noted), `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: above; done-when met, with the genre part waiting on R3b.
2. Exact commands, exit codes and counts: above.
3. New failures investigated: none failed; two layout problems found in screenshots and fixed.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
