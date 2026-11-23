# M1b.2 World screen, stage 1: the header's Project and Settings menus: report

**Milestone:** M1b.2 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1b, Appendix E1), **stage 1 of the staged plan** in [world-first-ux-audit.md](world-first-ux-audit.md).
**Owner decision:** decision 16 (World-first UX) **approved on 2026-11-14, to be built in stages**, keeping the existing panels reachable throughout (the owner's answer: "Approve 16, staged").
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21, real display for the smoke runs.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this stage touched are listed below.

## Scope and result

M1b.2's done-when is: *the shell opens by default with the owner-supplied screenshot as the interaction reference.* **Not met yet, by design:** this is stage 1 of six. Stage 1's own goal from the plan was a header with Save, Undo, Redo, Project▾ and Settings▾, and moving the hidden actions into Settings, with nothing else moving.

| Stage 1 criterion | Result |
|---|---|
| Header has Save, Undo and Redo | **Met** (they were already top-bar buttons and stay where they were) |
| A Project dropdown | **Met:** the M1.5 File dropdown is now the header's **Project** dropdown, with every File action, in the same order and groups |
| A Settings dropdown holding the hidden actions | **Met for six of eight:** Shadows; Lighting Quality Low, Medium and High; Toggle Dark/Light Theme; About. **Enter and Exit Sculpt Mode were left out on purpose:** the Sculpt toggle (button and F4) already does both, and adding them to Settings would make a second, misplaced route. They stay listed as hidden in the inventory. |
| Nothing else moves | **Met:** Create keeps its dropdown; every other button, tab and panel is where it was |
| No action loses a route | **Met:** `ActionInventoryTest` passes with the hidden set reduced from 8 to 2 |

## What was built

- **`HeaderMenus`** (`editor-core`, `action/`): the header's two dropdowns as data. **Project** lists every action registered in the File menu, read from the registry each time, so actions registered later (Export Game, the sculpt file actions) appear without a code change. **Settings** has three sections: graphics (shadows and the three lighting qualities), appearance (the theme switch) and about. An id that is not registered is left out, and a section with nothing in it is dropped, so the menu never shows an entry that does nothing. An unknown menu name is refused.
- **`EditorMenuBar.header`** (`editor-desktop`): the two buttons, with a separator between sections. Item states and shortcut hints are read when the menu opens, as before. The old constructor for registry menus is unchanged and still used for Create.
- **`EditorApplication`**: the top bar's `File` and `Create` dropdown became `Project | Settings` followed by a divider and `Create`.

**A fix found by looking at the screenshot:** in the first smoke run the two buttons sat together and read as one item, "Project Settings". A divider now separates them. The second screenshot, [m1b-2-header/header-project-settings.png](m1b-2-header/header-project-settings.png), shows `Save | Undo Redo | Project | Settings | Create Duplicate Delete | Select Move ...`.

## Tests (4 new; `editor-core` 428 to 431, `editor-desktop` 139 to 140)

| Test | What it pins |
|---|---|
| `HeaderMenusTest` (3, new) | Project holds exactly the File menu's actions in menu order; Settings gives graphics and about, drops the theme section when the desktop shell has not registered it and shows it once it has; **every Settings entry runs** (the renderer ends on High quality); an unknown menu is refused; nothing registered means nothing shown |
| `ActionInventoryTest` (+1, changed) | Routes now come from the header: File actions are reached through "header Project dropdown", the six Settings ids through "header Settings dropdown"; **the hidden set is exactly Enter and Exit Sculpt Mode**; the top bar must build the header and the Create dropdown; every Settings id is an action the editor registers (so the theme id in `HeaderMenus` cannot drift from the shell's) |

The inventory table in [world-first-ux-audit.md](world-first-ux-audit.md) was regenerated (`GDXUNITY_UPDATE_AUDIT=1`).

## Evidence from the real editor

`./gradlew :editor-desktop:run --args="--smoke-test"` was run twice with `GDXUNITY_SCREENSHOT`. Both rendered 180 frames with 0 errors in the Output Log. The first screenshot showed the "Project Settings" problem above; the second shows the fix. **The dropdowns were not opened**: their items and the effect of clicking them were not seen on screen.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-core:test --tests "*HeaderMenusTest" :editor-desktop:test --tests "*ActionInventoryTest"` | 0 | New tests pass; inventory regenerated |
| `./gradlew :editor-core:test :editor-desktop:test` | 0 | **editor-core 431 tests, 0 failures, 1 skipped; editor-desktop 140, 0 failures** |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with a screenshot (twice) | 0, 0 | 180 frames, 0 errors each time |
| `./gradlew :editor-desktop:test` (after the divider fix) | 0 | 140 tests, 0 failures |

No other module changed, so the repository total is 1,906 + 4 = **1,910 (computed, not a fresh full run)**.

## Limits

- **Manually Not Verified (Headless Environment):** opening Project and Settings and choosing their entries; whether the theme switch from a dropdown rebuilds the UI cleanly (it defers the rebuild, as it did from the old Window menu).
- **Settings entries show no state.** Shadows is a switch and the lighting qualities are a choice, but the menu shows neither a tick nor the current value. The status bar reports the new state after each click, as before.
- **The header is still the old top bar.** The thin header of Appendix E1 (name, context indicator such as "World / My Game", Save, Undo, Redo, menus on the right) needs the tabs and tool buttons to move out of it first, which is a later stage.
- **Duplication remains:** New, Open and Save are both buttons and Project entries, as accepted in M1.5.

## Next stages (unchanged from the plan)

Stage 2 (M1b.7, Customize gateway) needs a TD on what Back to World keeps; stage 3 (footer, M1b.3) needs **decision 11**; stage 4 (rest of M1b.2: World strip from the level palette, collapsible panels, World as the default) needs a layout TD. M1b.2 stays **in progress** until stage 4.

## Files changed

- New: `editor-core/.../action/HeaderMenus.java`, `editor-core/.../action/HeaderMenusTest.java`, this report, `m1b-2-header/header-project-settings.png`.
- `editor-desktop/.../ui/menu/EditorMenuBar.java` (`header`, shared button code), `editor-desktop/.../EditorApplication.java` (top bar), `editor-desktop/.../audit/ActionInventoryTest.java`.
- `docs/reports/world-first-ux-audit.md` (table, stage 1 marked done), `docs/milestone-chart.md` (M1b.2 row, decision 16), `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: above. M1b.2 as a whole is in progress; stage 1 is met except the two sculpt entries, left out with the reason.
2. Exact commands, exit codes and per-module counts: above.
3. New failures investigated: none failed; one visual problem found in the screenshot and fixed.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
