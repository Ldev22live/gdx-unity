# Roadmap M3b.2: Play Profile Model, Genre Registry and Resolution (2026-11-15)

Milestone M3b.2 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b), built to [TD-088](../TECHNICAL_DECISIONS.md) as approved and amended by the owner on 2026-11-15. **Status:**
- **Implemented.** The model, the registry, resolution, the scene checks and the safe fixes have automated tests.
- **Integrated in part:** the Player component is in Add Component > Game and in Details. Choosing a genre has no UI yet (M3b.4), and Play does not resolve or check a profile yet (M3b.5).
- **No genre and mode pair is playable**, as TD-088 plans: the first ones come with M3b.3 and M3c.1.
- **Manually Not Verified (Headless Environment):** the Player row in Details was not looked at.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 and M3b.1 work (listed in their reports).
- Approvals: TD-088 (approved 2026-11-15), amended the same day ("update TD-088 with the suggestions and implement"): a "Make <name> the player" safe fix and an opt-in Use Profile Movement field on Player.
- No new module and no new dependency.

## 1. Acceptance criteria

The chart's done-when for M3b.2 is "tests for genre selection, dimension selection, resolution and unsupported combinations".

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Engine-level data classes | **Met** | `GameTestProfile` and its parts in `engine-scene` `play`; invalid profiles are refused (`GameGenresTest`) |
| 2 | An extensible genre registry | **Met** | `GameGenres.standard()` lists the eleven roadmap genres; `register` adds one with every mode planned; a duplicate id is refused (`GameGenresTest`) |
| 3 | Every pair has a status and a reason | **Met** | All 33 pairs; first- and third-person shooters are "not offered" in 2D and 2.5D; none is playable (`GameGenresTest`) |
| 4 | Genre selection | **Met** | `EditorState.setGameGenre`: not an undo step, listeners told, refused during Play and for an invalid id (`ProfileFixesTest`); the scene file's `gameGenre` round trip, an absent field, an unknown id kept on save (`SceneGenreSerializationTest`) |
| 5 | Dimension selection | **Met** | The same genre resolves per game mode: Action RPG playable in 3D (test registry) is still refused in 2.5D (`PlayProfilesTest`) |
| 6 | Resolution, and unsupported pairs refused with a reason | **Met** | No genre plays as before; planned, not offered, unknown genre and "genre but no saved mode" are refused with plain messages (`PlayProfilesTest`) |
| 7 | Validation that names what is missing | **Met** | No player, two players, no Character Controller 3D, a Rigid Body 3D or collider on the player, no camera (`PlayProfilesTest`) |
| 8 | Safe fixes (TD-088, amended) | **Met** | "Make <name> the player" for the only entity named Player, else the only character; "Add a Character Controller 3D"; "Add a camera" suited to the mode; each one undo step, refused during Play (`PlayProfilesTest`, `ProfileFixesTest`) |
| 9 | What the author made wins (TD-088, amended) | **Met** | A scripted player keeps its script, unless Use Profile Movement is on; a camera keeps its own rig (`PlayProfilesTest`) |
| 10 | Runtime-only components are never saved | **Met** | The serializer refuses one, naming the entity (`SceneGenreSerializationTest`) |
| 11 | A pair is playable only when its test passes | **Met** | `PlayableGenrePairsTest`. **Mutation check:** I made Platformer + 2.5D playable in `GameGenres.standard()` without listing a test; the test failed. The original was restored |

## 2. What was built

- **`engine-scene`, package `play`:**
  - `GameGenre`, `GenreStatus`, `GenreSupport` and the `GameGenres` registry.
  - `GameTestProfile`, with `CameraSetup`, `MovementSetup`, `Physics` and `Requirement`.
  - `InputAction`, `InputMapping` (WASD and arrows, Space, Q and E), and `InputSource`, whose keyboard source says plainly when there is no running application.
  - `PlayerComponent` with `useProfileMovement` (off by default), and its serializer.
  - `RuntimeOnlyComponent` and `EntityLogicComponent`. The script binding and the graph component now implement the second, so the profile can tell when the author's logic moves the player.
- **`Scene` and `SceneSerializer`:**
  - an optional `gameGenre`, written only when set;
  - an unknown id is kept, and a value that is not an id is refused;
  - a scene holding a runtime-only component is refused on save.
- **`engine-render`, package `play`:** `PlayProfiles` (`resolve`, `validate`, `plan`), with `ProfileResolution`, `ProfileProblem`, `ProfileFix` and `ProfilePlan`.
- **`editor-core`:**
  - `EditorState.getGameGenre`, `setGameGenre` and `Listener.onGameGenreChanged`;
  - `ProfileFixes`;
  - Add Component > Game > Player, refused when another entity in the scene is the player.
- **`editor-desktop`:** Player Details, with a note and the Use Profile Movement switch as an undoable change.
- **`engine-desktop`:** the Player serializer is in the shared list, so the game runtime loads it.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test :engine-render:test :engine-visualscript:test :editor-core:test :engine-desktop:test --continue` | 1, then 0 | First run: two of my own assertions expected compact JSON, and the scene file is pretty-printed; they now parse the JSON. Then all pass |
| Mutation check of `PlayableGenrePairsTest` (section 1, row 11) | 1 | 1 of 2 tests failed, as intended; restored |
| `./gradlew cleanTest test --continue` | 0 | **2,035 tests, 0 failures, 1 skipped** (the known Linux-only assumption), 23 modules |

Per-module counts, with the M3.3 report's run as the baseline:

| Module | Before | After |
|---|---|---|
| `engine-scene` | 166 | 182 |
| `engine-render` | 148 | 163 |
| `editor-core` | 473 | 480 |
| Total | 1,997 | 2,035 |

The other modules are unchanged. The web player was not changed and its tests were not run: no sample or web code uses the new field or component.

## 4. Failures found and fixed

- Two assertions in `SceneGenreSerializationTest` looked for `"gameGenre":"…"` without a space; the serializer writes indented JSON. The code was right, so the assertions now parse the JSON.
- The first wording of a fix's status message lower-cased its label, which also lower-cased the entity's name. It now quotes the label as it is.

## 5. Diff review

- **New:**
  - `engine-scene`: twelve classes in `play` and `play/serialization`;
  - `engine-render`: five classes in `play`;
  - `editor-core`: `play/ProfileFixes`;
  - `editor-desktop`: `PlayerComponentEditorProvider`;
  - tests: `GameGenresTest` (6), `InputMappingTest` (4), `SceneGenreSerializationTest` (6), `PlayProfilesTest` (13), `PlayableGenrePairsTest` (2), `ProfileFixesTest` (6).
- **Changed:**
  - `Scene`, `SceneSerializer`, `EntityScriptBindingComponent`, `GraphScriptComponent`;
  - `EditorState`, `ComponentCatalog`, `InspectorPanel`, `GameComponentSerializers`;
  - tests: `ComponentCatalogTest` (+1, one Player per scene), `GameComponentSerializersTest` (36 serializers).
- **Docs:** TD-088 (amendments and implementation notes), the chart, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, this report.

No sample, asset or web file changed. No files outside this repository were touched. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Not reachable by a person yet:** choosing a genre (the footer still reads "none yet (planned)", M3b.4), and Play using it (M3b.5).
- **Not built yet:** the movers and the side and third-person camera set-ups (M3b.3, M3c.1); the ground check by a downward ray at Play, the profile step in `PlayModeController` and `GameSession`, and the undo-history tests around Play (M3b.5); agent tools (M3b.7).
- **Duplicating a player** with the editor's Duplicate copies only some component types today (a known gap recorded in `ComponentCloningAndSerializationCoverageTest`); if it ever copies Player, the scene check reports the two players.
- **Manual check for a person:**
  1. Select an entity and choose Add Component > Game > Player.
  2. Details shows the note and Use Profile Movement.
  3. Toggle Use Profile Movement and undo it.
  4. Try adding Player to a second entity: it is refused, naming the first.
- **Next:** M3b.3 (Platformer + 2.5D), built together with M3b.5's profile step at Play, as the audit's staged plan says.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
