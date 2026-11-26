# ForgeRoom: Current Status, 25 November 2026

A snapshot of where the project stands at the end of 2026-11-25. The source of truth stays [milestone-chart.md](milestone-chart.md) (per milestone) and [FEATURE_TEST_MATRIX.md](FEATURE_TEST_MATRIX.md) (per feature); this page summarises them and today's work. Status words follow AGENTS.md §9: **Implemented** (code and automated tests), **Integrated** (reachable by a user), **Validated** (checked in the running application by a person).

**Nothing in ForgeRoom is Validated by a person yet.** Every feature below is checked by automated tests and, where stated, by observed runs (screenshots looked at by the agent). Keyboard play, clicks and listening are on the human checklists (E2E plan, [e2e-checklist-2026-11-19.md](reports/e2e-checklist-2026-11-19.md) and each report's checklist).

## 1. Repository state

| Item | State |
|---|---|
| Branch | `phase-12-3d-foundation` |
| HEAD | `b8bace5` ("Forgeroom alpha"). Nothing has been committed since |
| Working tree | Large uncommitted change set: the work of M7a.1 to M7a.6, MC.2, MC.3, MC.4, M3c.6, M3c.7, M3b.7 slice 4 and asset work (about 170 modified or new paths). **Commit it soon:** it is the only copy |
| Stack | Java 21, LibGDX 1.14.2, LWJGL3 3.3.3, Jolt (3D physics), Box2D (2D), Babylon.js web player |

## 2. Phases at a glance

| Phase | State |
|---|---|
| **R1b** World-first UX shell | Built (M1b.1 to M1b.4, M1b.6, M1b.7); World screen follows the owner's design reference. M1b.5 (an unfamiliar tester) needs a person |
| **UI** owner brief (full-map UI, drawers, asset APIs) | UI M1 to M8 built; Poly Haven and ambientCG online libraries; not checked by a person |
| **R2** Materials | `.material` and textures built; the Material Editor tab is not |
| **R2b** CC0 assets and provenance | Complete: licence manifest, Kenney starter pack, Objects palette, placed-object collision, per-file support matrix |
| **R3** Terrain | Built: sculpting, paint layers, holes, water, erosion, roads and rivers, level of detail, sky and weather, web terrain |
| **R3b** Play profiles | 7 of 33 genre and mode pairs playable (section 3). Left: racing and the remaining pairs (M3b.7) |
| **R3c** World, Play, Stop, Edit, Play gate | M3c.1 to M3c.8 done (scenarios B and C pass with named BLOCKED parts). **M3c.9 (audio scenario) is blocked**: it needs MC.5 and MC.6 |
| **R7a** Mesh editing | M7a.1 to M7a.6 implemented: editable mesh, 3D Modeling workspace, vertex, edge and face operations, three save modes, return to World, source preservation |
| **MC** Authoring-workspace samples | MC.1 to MC.4 and MC.7 done; MC.5 (sound-effect workspace) and MC.6 (music binding) not started |
| **P** Dimension parity | MP.1 and MP.3 done; MP.2, MP.4, MP.5 not started (MP.2 has a Legal gate) |
| **R6** Effects, UI and AI | Post-processing (M6.2) and navigation (M6.4) built; M6.1 and M6.3 need a TD |
| **R9** Pipeline and backend parity | Text-friendly scenes, support matrix, performance baselines (Windows), Profiler panel. The TeaVM web build is broken (recorded) |
| **R4, R5, R7, R8, MT (except MT.8), WEB** | Not started |
| **R10** LLM and agent harness | Foundations (panel, agent tools); rollout not started |

## 3. Playable genre and mode pairs (7 of 33)

| Pair | Since | Template sample |
|---|---|---|
| Platformer + 2D | M3c.7, TD-105 (2026-11-24) | (scenario test builds its own level) |
| Platformer + 2.5D | M3b.3 | `platformer-profile-25d.json`, `hybrid-side-scroller-25d.json` |
| Platformer + 3D | M3b.7 slice 2 | `platformer-profile-3d.json` |
| Action RPG + 2.5D (isometric) | M3c.3 | `action-rpg-iso-25d.json` |
| Action RPG + 3D | M3c.1 | `action-rpg-valley-3d.json` |
| First-person Shooter + 3D | M3b.7 slice 3 | `first-person-range-3d.json` |
| Third-person Shooter + 3D | M3b.7 slice 4 (2026-11-25) | `third-person-yard-3d.json` |

Each playable pair has an acceptance test on real physics, listed in `PlayableGenrePairsTest`. The web player runs no genre profiles.

## 4. Completed today (2026-11-25)

**M3c.7, Scenario B: 2D Platformer + 2D:** done-when met, with two parts BLOCKED ([report](reports/m3c-7-scenario-b-2d-platformer.md)).
- `ScenarioB2DPlatformerTest` runs the scenario from an Empty 2D project: tiles and a platform, a sprite hero, Play, Stop, a frame edited in 2D Studio, Play again, then save, reopen and export.
- Result: 9 steps PASS, 2 BLOCKED as expected (no CC0 2D character yet; the export not launched as a program).
- Three editor-core tests still expected Platformer + 2D to be unplayable; their expectations were corrected.

**MC.4, 2.5D hybrid sample:** done-when met ([report](reports/mc-4-hybrid-sample.md)).
- **Engine fix:** in a perspective view, flat 2D items (sprites, tile maps, filled shapes) are now drawn farthest first (`ViewDepthOrder`). Before, a sprite behind another could be drawn over it; both runtime frames are kept as evidence.
- **New sample** `hybrid-side-scroller-25d.json`:
  - Kenney CC0 house, pines, column and floor-tile platform;
  - a sprite player on Jolt as Platformer + 2.5D;
  - villagers in front of and behind the play plane.
- **Workspaces:** the sprite is edited in 2D Studio and a wall in 3D Modeling, then the project plays, saves and reopens (`HybridSideScrollerSampleTest`).

**M3b.7 slice 4, Third-person Shooter + 3D:** playable ([report](reports/m3b-7-4-third-person-shooter-3d.md)).
- **Camera:** behind and above the player, following its facing (`BEHIND_FACING`).
- **Movement:** Q and E turn the player, A and D step sideways.
- **Shot:** level from the chest (`ShotSetup.Origin.PLAYER_FACING`). A mutation check shows that a shot along the camera's view hits the floor instead.
- **Sample:** `third-person-yard-3d.json`.

**Generated audit tables refreshed:** for both new samples (feature matrix, inventory, template audit, asset support). Each change was traced to the new samples against a snapshot taken first.

## 5. Test status (last runs)

| Area | Result | When |
|---|---|---|
| `editor-core` (full) | 616 tests, 1 skipped, 0 failures | 2026-11-25 |
| `engine-desktop` (full) | 162 tests, 0 failures, before today's two new acceptance tests (7 more tests, passing) | 2026-11-25 |
| `engine-render` (full) | 213 tests, 0 failures (before slice 4's edits; its play tests pass after them) | 2026-11-25 |
| `editor-desktop` | Not run in full today. The sample audits, gallery projects, Customize, mode selector and the four scenario and sample tests pass | 2026-11-25 |
| Web (`engine-babylon`, vitest) | **520 of 521.** The one failure is not from today's work (section 6) | 2026-11-25 |
| Whole repository (`./gradlew test --continue`) | **Not run today.** Last full green run is recorded in earlier reports | — |

## 6. Needs the owner

1. **Web test failure, one decision.** `action-rpg-valley-3d.json` has an uncommitted change adding an `AtmosphereComponent` that the web player does not support. Either list it in `KNOWN_UNSUPPORTED` in `engine-babylon/test/sceneBuilder.test.ts` (and the backend matrix), or port it to the web player.
2. **The audio chain is gated.** M8.1 (a person listens and signs off), M8.5 (TD, dependency, Legal), M8.6 and M8.7 come before MC.5 and MC.6, and those before M3c.9, the last R3c scenario.
3. **Commit the working tree** (section 1). No agent commits without being asked (AGENTS.md §3).
4. **Human checks.** Every recent milestone is Manually Not Verified: keyboard play of the new pairs, Customize clicks in 2D Studio and 3D Modeling, the E2E checklist on the packaged editor.
5. **Template audit rule.** The template matrix still marks glTF models "FAIL (no mesh editor)" for every sample, though R7a's 3D Modeling now edits them. The audit rule should be revisited.

## 7. Known limitations worth remembering

- **Web player:** draws flat 2D items among themselves in scene order, so a sprite behind another can be drawn over it (recorded in [BACKEND_SUPPORT_MATRIX.md](BACKEND_SUPPORT_MATRIX.md)). It runs no genre profiles. The TeaVM build is broken.
- **Sprite players:** in 2D and 2.5D they do not flip to face their running direction.
- **Platformer + 2D:** has no slopes, one-way or moving platforms, and no ground check before Play.
- **Shooters:** no health, enemies, ammunition or mouse look. Third person has no shoulder offset or crosshair.
- **Kenney Pixel Platformer sprites:** approved but not downloaded (M2b.5, Legal); the 2D and 2.5D samples use ForgeRoom's own sprites.

## 8. Practical notes

- **Memory:** this machine had 0.5 to 1.8 GB of 16 GB free during today's runs, with browsers using most of it. Claude Code stopped two background Gradle runs for low memory. What worked:
  - `.\gradlew.bat --stop` first;
  - `--max-workers=1`;
  - test classes split into small foreground runs.
  - Alternatively, close browsers, or start Claude Code with `CLAUDE_CODE_DISABLE_BG_SHELL_PRESSURE_REAP=1`.
- **Samples:** `tools/make_profile_samples.py` now takes sample names (for example `python tools/make_profile_samples.py third-person-yard`). Run it with a name, so it does not rewrite the other profile samples, which have uncommitted changes.
- **Adding a sample** touches, by design:
  - the gallery, the samples README and the migration plan;
  - the web test's known-unsupported list;
  - two hard-coded counts (`SampleInventoryTest`, `TemplateRatingsTest`);
  - the generated audit tables (`GDXUNITY_UPDATE_AUDIT=1`).

## 9. Suggested next steps

1. **M3b.7 slice 5, Racing + 3D:** the next ungated pair on the critical path; it would be the eighth playable pair.
2. **Settle section 6 items 1 and 3:** the web test decision and a commit.
3. **Audio chain (M8.1, M8.5):** start once the owner has approved its gates; it unlocks MC.5, MC.6 and M3c.9.
4. **Owner's E2E run** on the packaged editor, to turn Integrated into Validated.
