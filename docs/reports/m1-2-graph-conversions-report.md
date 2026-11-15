# M1.2 Graph conversions: report (stage 1)

**Milestone:** M1.2 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1). **Gate:** none.
**Date:** 2026-11-15. **Platform:** Windows 11 Pro, Java 21 (Gradle toolchain), Node for the web tests.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier milestones; the files this stage touched are listed below.

## Why M1.2, and why in stages

M1.2 is the first R1 milestone not done that needs neither a person (M1b.5) nor an approval (M1.6). It is also why 19 of the 40 samples fail the M1.1 matrix ("behaviour is Java source"). It is large: 15 sample graphs, most of which cover only part of their game. This stage takes the graphs closest to their scripts and makes their parity checked. The rest need nodes or game code that do not exist yet (see Next).

## Done-when and result

*Parity tests pass for all 15 sample graphs.*

**Not met yet: 5 of 15.** Before this stage the chart said only ShooterShip was matching. In fact Collector and Runner had frame-by-frame parity tests (TD-071, TD-072), and ShooterShip had only a graph-only check. After this stage:

| Graph | Parity | How it is checked |
|---|---|---|
| ShooterShip | **Matches** | `ShooterParityTest` (new): the script is compiled and run beside the graph with the same keys; positions compared every 10 frames; all four edges of the box reached; positions recorded in `shooter-trajectory.json` |
| BreakoutPaddle | **Matches** | `SampleGamesTest.shouldMatchTheReferencePaddleScriptAtDifferentWidthsAndFrameTimes` (existing): the breakout scene with the graph and with the script, two widths, uneven frame times, every frame |
| CollectorPlayer, RunnerPlayer | **Match** | `CollectorParityTest`, `RunnerParityTest` (existing) |
| WalkerPlayer | **Matches (completed here)** | `SampleGamesTest.shouldWalkJumpAndLabelLikeTheReferenceWalkerScriptFrameForFrame` (new): the character playground under real Jolt physics, once with `WalkerPlayer.java` and once with the graph; the hero's position (within 0.001) and the Help Text are compared on every frame of 388; both presses on the ground take off; holding Space and pressing it in the air do nothing |
| Hopper, RaceCar, Arena, Puzzle, Fighter, Grapple, Ruins, Trio, SandboxKick, Platformer | Partial | Unchanged; what each leaves out is in `assets/graphs/samples/examples.json` |

## What changed in the Walker graph

The old graph left out three things (its own `examples.json` note said so). The rewritten graph:

- **Normalises the direction.** It makes the vector from the two axes, then `Vector3.nor()`, then scales by 300, as the script does, so a diagonal is no longer about 41% faster.
- **Jumps once per press.** It uses Key Pressed instead of Key Down for Space. The jump still asks Is Grounded, as before; the physics system refuses a jump in the air anyway.
- **Sets the Help Text.** A Sequence runs a second branch after the movement: Self, then `Entity.getScene`, then `Scene.findEntityByName("Help Text")`, then Get TextComponent, then `TextComponent.setText` with "On the ground" or "In the air". It reads Is Grounded after the jump, as the script does.

One difference is left. The script skips the Help Text when the scene has none, but the graph expects it, and it is in the scene. Without the Help Text entity the graph would report a runtime error.

**The scene still runs the Java script.** The Collector and Runner scenes were switched to their graphs once the graphs matched. Here the web player also plays `character-playground-3d`, and its interpreter cannot run the 3D character nodes in a graph yet (the web test lists them as desktop-only). Switching would stop the hero moving on the web. So the switch waits for that support, and the M1.1 matrix still marks this sample's logic as Java.

## Tests

| Test | What it shows |
|---|---|
| `ShooterParityTest` (4, new, editor-visualscript) | The graph validates; it matches the script frame for frame; it reaches every edge of the box and no further; the script still gives the recorded positions (re-record with `GDXUNITY_RECORD_FIXTURES=1`) |
| `SampleGamesTest.shouldWalkJumpAndLabelLikeTheReferenceWalkerScriptFrameForFrame` (1, new, editor-desktop) | As above |
| `SampleParity` (helper) | `runScript` and `runGraph` take an optional entity setup, for a sample entity that needs components |

**Each new test fails on the wrong graph.** I made temporary changes and restored them, with no diff left:

- ShooterShip at 339 units a second instead of 340: `ShooterParityTest` failed.
- The old Walker graph from HEAD: the Walker test failed on frame 0 (the Help Text).
- During development a BreakoutPaddle speed of 519 also failed. I then deleted my extra paddle test, because `SampleGamesTest` already compared the paddle with its script.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_RECORD_FIXTURES=1 ./gradlew :editor-visualscript:test --tests "*ShooterParityTest.shouldMatchTheRecordedPositionsOfTheScript" ...` | 0 | Wrote the recorded positions |
| `./gradlew :editor-visualscript:test --tests "*ShooterParityTest" ... "*RunnerParityTest" "*CollectorParityTest"` | 0 | 4/4 each |
| The same with a mutated ShooterShip graph | 1 | Failed as it should; the graph was restored |
| `./gradlew :editor-desktop:test --tests "*SampleGamesTest.shouldWalkJump..."` (first two runs) | 1, 1 | My own test's mistakes: it counted a step-down as a jump, then marked a held Space as pressed again at a stretch boundary. Fixed in the test; the positions and Help Text matched throughout |
| The same, after the fixes | 0 | Passes |
| The same with the old Walker graph | 1 | Failed on frame 0 as it should; the new graph was restored |
| `npx vitest run` in `engine-babylon` (web) | 0 | 37 files, **478 tests passed** (the example graphs still validate on the web as before) |
| `./gradlew cleanTest test --continue` (whole repository) | 0 | **1,950 tests, 0 failures, 0 errors, 1 skipped** (the POSIX-only exporter test), 23 modules |

**Counts (from the XML reports):** editor-visualscript 89 to **93** (+4, ShooterParityTest), editor-desktop 153 to **154** (+1, the Walker test); every other module unchanged. Repository 1,945 (after M1.7) to **1,950**, 0 failures. Web: 478 passed (unchanged).

## Limits

- **10 of 15 graphs are still partial.** M1.2's done-when is not met.
- **The playground scene still runs Java** (see above). So this stage changes no sample's label in the M1.1 matrix or the gallery.
- **Manually Not Verified (Headless Environment):** the new Walker graph was not opened in the graph editor or played in a window. Its behaviour is checked by the headless test under real physics.
- The Walker graph looks up the Help Text by name every frame. The script finds it once at the start. The behaviour is the same, but the graph does a little more work.

## Next (the remaining 10)

From each graph's "notCovered" note, here is what blocks the rest:

- **Platformer, Hopper:** the jumping, gravity and landing are box collisions written in Java. The graph needs either nodes for that, or the samples moved onto the engine's physics colliders.
- **RaceCar:** the track, laps, rivals and AI.
- **Arena:** strafing, firing, drones and waves.
- **Puzzle:** walls, crates, pushing, levels and undo. This needs grid and collection nodes.
- **Fighter:** attacks, hits and the AI opponent.
- **Grapple:** the swing and the side-on body.
- **Ruins:** filtering by name, score and platforms.
- **Trio:** switching heroes and their abilities.
- **SandboxKick:** aiming along the camera, scaling by mass, and push, pop and stop.

Several of these are whole games rather than player scripts. Converting them fully may need an owner decision, either on new node families or on splitting the game rules out of the player.

## Files changed

- New: `editor-visualscript/src/test/.../ShooterParityTest.java`, `editor-visualscript/src/test/resources/shooter-trajectory.json`, this report.
- Changed: `assets/graphs/samples/WalkerPlayer.graph` (rewritten), `assets/graphs/samples/examples.json` (Shooter, Breakout and Walker notes), `editor-visualscript/src/test/.../SampleParity.java` (setup hook), `editor-desktop/src/test/.../SampleGamesTest.java` (Walker test), `docs/FEATURE_TEST_MATRIX.md` (a row), `docs/milestone-chart.md` (M1.2 row), `docs/INDEX.md`.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: the done-when is **not met** (5 of 15); this stage is reported as progress.
2. Commands, exit codes and counts: above.
3. New failures investigated: the only failures were the intended mutation checks and two mistakes in my own new test, fixed; the full suite and the web suite passed.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
