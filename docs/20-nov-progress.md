# Progress Update, 20 November 2026

**Chart:** [`milestone-chart.md`](milestone-chart.md) (its copy [`forgeroom-milestone-chart-multigenre-levels.md`](forgeroom-milestone-chart-multigenre-levels.md) is identical).
**Branch:** `phase-12-3d-foundation`.
**Last commit:** `2391dad`, pushed to GitHub on 20 November (everything up to M2b.4's first state).
**Since then:** uncommitted (section 5).

Status words follow AGENTS.md §9:
- **Implemented:** code and automated tests.
- **Integrated:** reachable by a user in the editor or the game.
- **Validated:** checked by a person in the running application.

**Nothing below is Validated as a whole.** Visual and mouse checks wait for the owner's run of the packaged editor ([E2E checklist](reports/e2e-checklist-2026-11-19.md)).

## 1. Where we are, in one paragraph

The **content and world-building waves are well advanced:**
- **Terrain (R3):** 10 of 11 milestones done; the eleventh, M3.10, is in its last step.
- **Play profiles (R3b):** all 7 built.
- **The owner's UI brief:** all 9 steps built.
- **CC0 assets (R2b):** the asset pipeline and the first four milestones are built.

Today added:
- a curated CC0 starter pack;
- a thumbnail Objects palette;
- gameplay collision on placed objects;
- a Scatter tool with view culling;
- the landscape samples moved onto editable terrains (nearly finished).

**What stands between here and the owner's acceptance journey (R3c)** is mainly in-editor mesh editing (R7a, not started), plus the 2D and audio scenarios. The later breadth phases (R4 scenarios, R5 animation, R7 modelling, R8 music completion, R10 AI agent) are almost untouched.

## 2. Today's milestones (20 November)

| Milestone | Result | Report |
|---|---|---|
| **M2b.2** Curated CC0 starter pack | Implemented and Integrated: Kenney Nature Kit and Building Kit imported with licence records; CC0 Oak Tree, CC0 Rock, CC0 House in the palette | [m2b-2](reports/m2b-2-curated-starter-pack.md) |
| **M2b.3** Objects palette | Implemented and Integrated: thumbnail tiles, groups (Trees, Rocks, Plants, Buildings), search, licence line | [m2b-3](reports/m2b-3-objects-palette.md) |
| **M2b.4** Collision, instancing and LOD plan | Implemented and Integrated: simple static colliders on every blocking palette object (the house doorway open, the bush walked through); a 223-object level measured (drawing 14.9 ms against 3.5 ms; physics 0.8 ms); TD-096 | [m2b-4](reports/m2b-4-collision-instancing-lod.md) |
| **M3.6** Scatter tools | Implemented and Integrated: Scatter in Objects (density that tops up, scale range, slope and height limits, spacing, automatic keep-out; Ctrl-drag removes; one undo step per stroke); objects out of view and out of the shadow area are no longer drawn (9 to 15 % faster in a forest); TD-097 | [m3-6](reports/m3-6-scatter-tools.md) |
| **M3.10** Convert the five landscape samples | **Implemented and Integrated (evening).** Five terrain copies beside the originals, each with a Hero and Action RPG play; the chart's journey (reshape, repaint, scatter, save, reopen, walk) passes in a test; previews and performance done; TD-098 | [m3-10](reports/m3-10-landscape-terrains.md) |
| UI M7, UI M8 | Done (morning): Asset Library previews and placement; regression, performance and documentation pass | [ui-m7](reports/ui-m7-previews-and-placement.md), [ui-m8](reports/ui-m8-regression-performance-documentation.md) |

**What M3.10 still needs:**
- **Four test expectations to update, all bookkeeping for the five new samples:**
  - regenerate the sample inventory and the template table (`GDXUNITY_UPDATE_AUDIT=1`);
  - the count of 3D samples, from 32 to 37;
  - the matrix rule "a landscape's ground is a generated model", which should apply to the originals only.
- **The web test suite**, `engine-babylon`.
- **The chart, index and feature matrix rows.**

Every new M3.10 test passes. So do the gallery, template-export, sample-game (44) and backend-matrix suites.

## 3. Every phase at a glance

Counts are from the chart's rows. "Done" means Implemented, Integrated or Done as the chart states it.

| Phase | Done | Partly | Not started | Where it stands |
|---|---|---|---|---|
| **R0** Trustworthy baseline | 4 of 5 | M0.4 | | Acceptance project partly built |
| **R1** Editable examples and templates | 4 of 7 | M1.1 (half), M1.5 | M1.6 | Template source ownership (M1.6) not started |
| **R1b** World-first UX shell | 3 of 7 | M1b.4, M1b.7; M1b.6 spec proposed | M1b.5 | Layout follows the owner's design reference since 19 Nov |
| **R2** Materials | 3 of 5 | M2.4 | M2.3 (Material Editor tab) | |
| **R2b** CC0 assets, import, provenance | 4 of 7 | (M2b.1 lacks licence detail for files already in a project) | M2b.5 2D catalog, M2b.6 sample refresh, M2b.7 web manifest matrix | **Next in this phase: M2b.5** |
| **R3** Landscape and terrain | 11 of 11 | | | Complete (Implemented and Integrated; not hand-checked) |
| **R3b** World View and Play profiles | 7 of 7 | (M3b.7 further genre slices continue) | | |
| **R3c** Acceptance journey gate | 5 of 9 | M3c.5 (4 steps BLOCKED), M3c.8 (model path BLOCKED) | M3c.6, M3c.7, M3c.9 | Blocked mainly on R7a (mesh editing) and a packaged run |
| **R7a** Building mesh editing | 0 of 6 | | all | The main blocker for R3c, MC.2 and the journey's house steps |
| **RG** Multi-profile worlds (proposed, not approved) | 0 of 11 | MG.2 (isometric view built) | the rest | Waits for the owner's approval |
| **UI** Owner brief | 9 of 9 | | | Every item Manually Not Verified, on the E2E checklist |
| **R4** Scenario and campaign editor | 0 of 15 | | all | |
| **R5** Animation upgrade | 0 of 4 | | all | |
| **R6** Effects, UI, AI | 2 of 4 | | M6.1 particles, M6.3 in-game UI (each needs a TD) | |
| **R7** Modeling, UV, rigging | 0 of 6 | | all | |
| **R8** Music and audio completion | 0 of 10 | M8.4 | the rest | |
| **R9** Pipeline and parity | 4 of 6 | | M9.1, M9.2 | |
| **R10** AI agent harness | 0 of 9 | M10.2, M10.3, M10.6 foundations | the rest | |
| **MP / MC / MT** Sample migration and templates | MP 2 of 5, MC 2 of 7, MT 1 of 9 | | | M3.10 added five migrated slices to the MC.7 registry |

## 4. Waiting on the owner

**Decision records to review.** All are recorded as built, for review; none changes an approved decision:
- TD-091 (post processing);
- TD-092 (navigation);
- TD-093 (text layout of scene files);
- **TD-096** (placed-object collision; instancing and LOD plan);
- **TD-097** (Scatter; one-step recording; object culling);
- **TD-098** (landscape migration). It reports a conflict: the chart says "opens `landscape-3d`", the migration plan says migrated samples are new files. The plan was followed.

**Other decisions:**
- **The RG track:** approve or not.
- **The raw Kenney downloads at the top of `assets/`:** keep or delete. The imported copies are committed under `assets/thirdparty/kenney/`, and the raw ones were left out of the commit.
- **`urconnex-ai-agent-roadmap.md`** at the repository root: another project's file, left uncommitted.
- **Committing and pushing** the work since `2391dad`.

**Checks only a person can make:**
- the [E2E checklist](reports/e2e-checklist-2026-11-19.md) on the packaged editor;
- mouse use of the palette, Scatter and the new landscapes;
- walking with the keyboard.

## 5. Uncommitted since `2391dad`

| Milestone | Changes |
|---|---|
| **M2b.4 follow-ups** | Test fixes (a camera for Play, the closest-approach check); the journey test gets its collider geometry; the house's entity count; TD-096's measurements; its report and doc rows |
| **M3.6** | `ScatterSettings`, `ScatterPlanner`, `ObjectCulling`; changes to `LevelSession`, `LevelViewportInteraction`, `SceneSnapshotCommand`, `PrefabService`, `SceneRenderer3D`, `GameRenderer`, `DesktopLauncher`, `LevelPalette`; four new test classes and `LevelEditingTest`; TD-097; the report; `AGENTS.md` (the `GDXUNITY_OBJECT_CULLING` testing variable) |
| **M3.10 (in progress)** | `tools/make_migrated_landscapes.py`; five samples and five terrains; five previews; the gallery and migration registry; `docs/migration/m3-10-performance.tsv`; `MigratedLandscapesTest`; TD-098; the draft report |

## 6. Risks and known gaps

- **Low memory on the work machine (about 1.4 to 2 GB free).** Gradle runs were stopped three times. Since then single test classes are run with one worker and a capped heap.
  - **Full module runs pending** since M2b.2: `editor-core`, `engine-render`, `editor-desktop`, and the package rebuild. `editor-terrain` ran whole on 20 Nov: 63 tests, all passing.
- **Rendering cost.** Drawing, not physics, limits object counts (M2b.4). Culling helps only with what is out of view. GPU instancing is not available on the engine's GL ES 2.0 level, so batching and lower-detail models stay planned (TD-096).
- **No hand-run checks.** Everything since UI M1 is Manually Not Verified.

## 7. Next

1. **M3.10 finished (evening):** expectations updated, audit tables regenerated, a M2b.2 test fault fixed, web suite 516 passing. R3 is complete.
2. **Then, in chart order:** R2b's **M2b.5** (2D CC0 catalog, Legal gate per provider), M2b.6 and M2b.7. After that, R3c's remaining scenarios, which need R7a for the house steps.
