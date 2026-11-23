# Editor layout fixes report (TD-055)

**Date:** 2026-11-08 · **Decision:** TD-055 (approved: "approve TD-055", as typed) · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)

## Acceptance

| Criterion | Result | Evidence |
|---|---|---|
| Details uses the right column while the AI Assistant is minimized | Met (still frame) | `docs/reports/img/td55-before-default.png` (about 150 px) and `td55-after-default.png` (Transform, Mesh Primitive, Animator and Add Component all visible) |
| Timeline toolbar buttons are separate | Met (still frame) | `td55-after-timeline.png`: "Save Undo Redo", "Reverse Loop" spaced; the buttons have no frame in the flat style, so they still read close together |
| Content Browser type tag does not overlap the path | Met (still frame) | `td55-after-timeline.png`: `[ANIMATION]` and the path are separate |
| "Nothing selected" hint stays inside the panel | Met (still frame) | `td55-after-timeline.png`: the hint wraps onto a second line |
| First 3D view not side-on | Not done, by decision | left as it is; a product choice |

## What was changed

- `EditorApplication`: the cause of the small Details was `SplitPane` keeping its second widget (the AI Assistant) at its own minimum height. The assistant sits in a `Container` with `minHeight(0)`; the split amount is `detailsSplitAmount()` (1.0 minimized, 0.55 open, constants `DETAILS_SPLIT_WITH_ASSISTANT`, `ASSISTANT_SPLIT_MIN`). My first two attempts were wrong (a container without `minHeight(0)` keeps the actor's minimum; swapping the split direction made Details smaller); the captures showed it.
- Side effect: the Content Browser also got taller (the main split no longer carries the assistant's minimum).
- `TimelinePanel`: `BUTTON_GAP` (4 px) after each toolbar button. `AssetBrowserPanel`: `TYPE_BADGE_WIDTH` (100 px). `InspectorPanel`: `addNothingSelectedNote()` wraps the hint.
- Tests changed: `ClipLibraryTest` and `ModelAssetsTest` still expected the five humanoid templates; they failed in the full run because of the five clips added after TD-053 (my regression, found by this run, fixed by listing the ten).

## Commands and results (2026-11-08, Windows)

- `gradlew :editor-desktop:run --args="--smoke-test=150 --scene samples/animation-samples-3d.json"` with `GDXUNITY_SCREENSHOT`, five times; each "0 error(s)".
- `gradlew test --continue --rerun-tasks`: exit 1 with 4 failing tests: the 2 known `BundledDemoSongTest` failures in `editor-audio` (pre-existing), and `ClipLibraryTest` (editor-animation) and `ModelAssetsTest` (engine-render), the regressions above. After the fix `gradlew :editor-animation:test :engine-render:test :editor-desktop:test` passes (0 failures). **The full clean run was not repeated after the fix**, so the only complete run still has those two regressions in it.

| Module | Before (TD-053 run) | After |
|---|---|---|
| editor-animation | 17 | 17 |
| editor-audio | 55 (2 known failures) | 55 (2 known failures) |
| editor-character | 8 | 8 |
| editor-core | 357 (1 skipped) | 357 (1 skipped) |
| editor-desktop | 72 | 77 |
| editor-sculpt, editor-visualscript | 45, 28 | 45, 28 |
| engine-animation, engine-audio, engine-character, engine-core | 32, 185, 19, 32 | 32, 185, 19, 32 |
| engine-desktop, engine-physics, engine-render | 69, 53, 101 | 69, 53, 101 |
| engine-scene, engine-sculpt, engine-visualscript, engine-web | 124, 77, 22, 9 | 124, 77, 22, 9 |

No test was added for the layout: it is GL glue (needs graphics test).

## Manually Not Verified (Headless Environment)

Dragging the dividers, toggling the AI Assistant (the 0.55 open amount was not captured), resizing the window, and the look of the Timeline toolbar at other widths. Only still frames of the Viewport and Animations tabs were seen.

## Known limitations and next steps

- Duplicate name in the Clip Library: "Wave" appears under Humanoid and again under Other clips (an older clip in `animations/skeleton`).
- The first 3D view is still side-on orthographic.
- Next candidates: live 3D preview in the character creator, the web checks (`npm ci`, `npm test`, `npm run typecheck`), web player parity.

## Git

No commit, push or other Git write was done. The PNGs in `docs/reports/img/` are new untracked files.
