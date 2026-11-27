# A07 Play/Stop and scenario runtime

**Automated audit complete.** The seven playable pairs, the Play prerequisites and Stop's restoration were already built and tested (R3b/R3c, TD-088). A05 added the scenario runtime to editor Play and the game runtime. A07 adds:

- a repeated Play/Stop test with real Jolt physics and scenario rules;
- a correction to the RTS planned-text, which said it "comes with the scenario editor" although that editor now exists and RTS is still not playable.

**Not validated:** human keyboard play of every advertised pair is pending. No new TD was needed.

The starting HEAD was `85ccc0a`, with A04–A06 uncommitted in the working tree.

## Audit against the roadmap

| A07 requirement | Where it stands | Evidence |
|---|---|---|
| Freeze profile expansion; advertise only pairs that pass | Met. `GameGenres.standard()` marks exactly the seven roadmap pairs PLAYABLE. Every other pair is "Planned" or "Not offered", with the reason. No pair was added. | `GameGenres`; `GenreChoicesTest` |
| The seven pairs: Platformer 2D, 2.5D and 3D; Action RPG 2.5D and 3D; FPS 3D; TPS 3D | Each has a profile test with real physics: `Platformer2DProfileTest` and `ScenarioB2DPlatformerTest` (2D, Box2D), `PlatformerSideProfileOnJoltTest` (2.5D), `PlatformerChaseProfileOnJoltTest` (3D), `ActionRpgIsometricProfileOnJoltTest` (2.5D), `ActionRpgValleyProfileOnJoltTest` (3D), `FirstPersonRangeProfileOnJoltTest`, `ThirdPersonYardProfileOnJoltTest`. | `PlayableGenrePairsTest`, `GameGenresTest`; engine-render, engine-desktop and editor-desktop suites |
| Honest descriptions: walkable Action RPG versus combat; shooters without health, enemies or ammunition | Met in the text the Genre list shows: "Combat comes later" and "Health, enemies and ammunition come later". RTS now reads "Planned: the scenario editor (players, rules) exists, but unit selection, orders, economy and RTS play do not yet". | `GameGenres` |
| Validate player, spawn, collision and camera before Play; actionable refusals | Met (TD-088). `ProfilePlayStep` refuses with problems and safe fixes (`ProfileFixes`). Scenario rules are checked at Play; faulty ones are skipped and listed in the Output Log, as TD-109 approved. | `ProfilePlayStepTest`, `ProfileFixesTest`, `ScenarioRuntimeTest` |
| Runtime-only components never saved | Met: `SceneSerializer` refuses a scene holding a `RuntimeOnlyComponent`. `ScenarioRuntimeComponent` is one, on the Play copy only. | `SceneGenreSerializationTest`, `ScenarioPlayStepTest` |
| Stop restores the exact authored scene, selection, camera and undo history; rule changes do not leak back | Met. The 2D cycle is in `PlayModeControllerTest`. **New:** a 3D cycle, five rounds with Jolt, in which the player walks into a trigger area. Each Play gets fresh rules (the "once" rule fires again), and log listeners do not pile up. Afterwards: 3D physics is disposed, the runtime is cleared, the scenario text and entity count are unchanged, the player is back at its authored position, and the undo count is unchanged. | `ScenarioPlayStepTest.shouldStartFreshRulesEachPlay...` |
| Tests: real physics, profile camera and input, terrain collision, trigger execution, invalid prerequisites, repeated Play/Stop, disposal | Met across the profile tests above, `TerrainValleyOnJoltTest`, `CameraRigOnJoltTest`, `GameSessionScenarioTest` (trigger execution in the game runtime) and the new repeated-cycle test. | |
| Memory | Partial. Disposal is asserted (physics and runtime are released at Stop); heap growth over many cycles is not measured. The performance baseline (`perfBaseline`, M9.5) measures frame time, not Play/Stop memory. | |
| Human keyboard play of every advertised pair, the three reference projects first | **Pending.** Manually Not Verified (Headless Environment). | |

## Known limitations

- Heap use across many Play/Stop cycles is not measured. This is proposed for A09's regression and performance work.
- Ownership does not affect Play (A04). Rules run on desktop only (A05, A14).
- Racing and RTS stay planned. The shooters have no health, enemies or ammunition.

## Test evidence

| Command | Exit | Result |
|---|---|---|
| `:editor-core:test --tests "*ScenarioPlayStepTest"` | 0 | 3 tests, 0 failures (after a test-setup fix: the character body exists only after the first physics update) |
| Full suite | see the [progress report](../progress/progress-reports-26-11-2026.md) | |

No commit, push or destructive Git operation was performed.
