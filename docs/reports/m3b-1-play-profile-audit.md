# Roadmap M3b.1: Play Profile Audit and Design (2026-11-15)

Milestone M3b.1 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b). It is an audit and a design only: no code, asset or test changed. The design is [TD-088](../TECHNICAL_DECISIONS.md). **Status: done.** The owner approved TD-088 on 2026-11-15 ("approve TD-088") without answering its four open questions, so its proposals stand. M3b.1's done-when ("the audit and design are in `docs/` and a TD") is met.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held the uncommitted M3.3 work of this session (40 paths, listed in the [M3.3 report](m3-3-terrain-collision.md)). This milestone added only documents.
- Sources read: the 2026-11-15 roadmap (R3b, R3c, section 1a, decision 11, Appendix E3), the chart's R3b and R3c rows, TD-049, TD-050, TD-067, TD-087, the [world-first UX audit](world-first-ux-audit.md), the [R1b reconciliation](r1b-outstanding-work-reconciliation.md), and the code named below.

## 1. What exists today

| Area | What exists | Where | Gap for genre profiles |
|---|---|---|---|
| Game mode | `GameMode` (2D, 2.5D, 3D), saved as the scene's optional `gameMode` field; inferred for old files | `engine-scene` `GameMode`, `SceneSerializer` | None. Decision 11 (answered 2026-11-14): the footer's Game Mode **is** this field; there is no play-time override |
| Editor rules per mode | `GameModeProfile`: the view, the side-view lock, the hidden component categories | `editor-core` | An editor concern only; it supplies no gameplay. Its name is close to `GameTestProfile`, so the TD keeps them apart |
| Genre | Nothing. The footer shows "Game Genre: none yet (planned)" | `editor-desktop` `EditorFooter.GENRE_NOT_YET` | Everything: a registry, a saved choice, a selector |
| Play isolation | `PlayModeController` serialises the scene to JSON and plays the copy; Stop disposes it, restores the authored scene and the selection; commands are refused during Play; `Extension` hooks (`beforePlay`, `onPlay` returning problems, `onStop`) | `editor-core` `play/` | No profile step. Undo history is not touched by Play, but no test states it |
| Game runtime | `GameSession` plays a scene file like Play (shared engine code, precompiled scripts) | `engine-desktop` | A profile must be applied here too, or exported games differ from Play |
| 3D movement | `CharacterController3DComponent` on Jolt (TD-049): walk, jump, slopes, steps, teleport | `engine-scene` `physics3d` | Driven only by scripts or graphs; no built-in driver |
| Terrain ground | `TerrainCollider3DComponent` (TD-087, M3.3) | `engine-scene`, `engine-render` | None for 3D |
| 2D movement | Box2D bodies (`PhysicsSystem2D`), tile map colliders | `engine-scene` | No 2D character controller; every 2D sample moves its player in its own script |
| 2.5D movement | `SideBody`: box-against-box movement in the XY plane, without the physics engine | `assets/scripts/samples/common/SideBody.java` | Sample code, not engine code; it uses Mesh Primitive boxes as solids, not colliders |
| Cameras | `CameraRigComponent` (TD-050): `FOLLOW`, `ORBIT`, `FIRST_PERSON`; target by entity name | `engine-render` | No side mode, but `FOLLOW` with a side offset gives a side view. `SideCamera25D` (sample script) adds a lowest height |
| Input | None. 22 sample scripts and the graph Key Pressed node (`GraphHost`) read `Gdx.input` directly | `assets/scripts`, `engine-visualscript` | No action mapping and no way to feed keys to a headless test except through a script stand-in (as M3.3's tests do) |
| Player and spawn | Nothing marks "the player"; prefabs exist (`PrefabInstanceComponent`, `PrefabService`) | `engine-scene`, `editor-core` | A profile cannot find what to control. Samples find their player by name |
| Agent tools | Seven tools (entities, components, transform, material, inspect) | `editor-core` `agent/tools` | No genre, validate, Play or Stop tools |
| Web player | Ignores `gameMode` and would ignore a genre field | `engine-babylon` | Desktop only at first (roadmap R3b "Web"); decision 19: desktop-only data is kept |

## 2. Samples, by mode and by how they move

| Mode | Samples | Player movement | Camera |
|---|---|---|---|
| 2D | `platformer-2d`, `pixel-adventure-2d`, `breakout-2d`, `shooter-2d`, `physics-sandbox-2d`, `tilemap-platformer-2d`, `sprite-animation-2d` | Own scripts or graphs (Box2D or hand-written) | Fixed or own script |
| 2.5D | `ruins-runner-25d`, `grapple-gorge-25d`, `three-heroes-25d`, `fighter-25d`, `sprite-25d` | `SideBody` or tile checks in own scripts | `SideCamera25D` script |
| 3D | `character-playground-3d`, `terrain-3d` | Character Controller 3D, walked by a script or a graph | Camera rig `FOLLOW` |
| 3D | `hopper-3d`, `arena-fps-3d`, `obby-3d`, `racing-3d`, `puzzle-3d`, `blockworld-3d`, `collector-3d`, `runner-3d` | Own scripts, no character controller (TD-049 left them unmigrated) | Own scripts |

Two 3D samples already have what an Action RPG + 3D profile would supply (a character controller, a follow camera, a walker on terrain). No 2.5D sample uses the physics engine for its player.

## 3. Findings

1. **A genre is only a label until something supplies movement, camera and input.** The roadmap's rule "a combination is listed as playable only when its test passes" needs the registry to carry a status, and a test that fails when a combination is marked playable without one.
2. **There is no input layer.** A profile needs actions (move, jump, turn), keys for them, and a source that tests can fake. Today that would have to be copied into every driver.
3. **Nothing names the player.** Finding it by name (as camera rigs do, TD-050) would make "Player" a reserved name; a marker component is clearer and is shown in Details.
4. **Platformer + 2.5D has no physics movement to reuse.** The roadmap asks for collisions with the level; `SideBody` collides only with Mesh Primitive boxes named by prefix. The character controller with a depth lock reuses physics, colliders and terrain.
5. **Play and the game runtime both start scenes.** Unless the profile is applied by engine code that both call, an exported game would play differently from editor Play.
6. **Authored gameplay must win.** Samples and templates already move their players with scripts and graphs (Appendix F requires them to stay editable). A profile that also drives such a player would fight it.
7. **Platformer + 2D has no character controller.** It cannot be an early slice without new 2D physics work; it stays planned.

## 4. Design summary

The full design, options and testing plan are in **TD-088**. In short:
- A **genre registry** (eleven genres from the roadmap) and a **support table** of the 33 genre and mode pairs, each `PLAYABLE`, `PLANNED` or `NOT_OFFERED`, with a reason. Only two pairs are scheduled to become playable: Platformer + 2.5D (M3b.3) and Action RPG + 3D (M3c.1).
- **`GameTestProfile`**: the genre, the mode, a camera setup, a movement setup, the physics it needs, an input mapping and a list of requirements.
- The scene file gains one optional field, **`gameGenre`**; Game Mode stays the existing `gameMode` (decision 11). Old files load unchanged.
- A **Player** component marks the entity the profile controls.
- Pressing Play **resolves** the profile, **validates** the scene (problems in plain words, with an undoable fix where one is safe), and **applies** the profile to the runtime copy with **runtime-only components** that the serializer refuses to write. `GameSession` applies it the same way.
- What the author made wins: a player that already has a script or graph keeps it, and a camera that already has a rig keeps it.

## 5. Proposed genre and mode table (initial)

| Genre | 2D | 2.5D | 3D |
|---|---|---|---|
| Platformer / Side-scroller | Planned (needs a 2D character controller) | **M3b.3** | Planned (M3b.7) |
| Action RPG | Planned | Planned (M3c.3) | **M3c.1** |
| Adventure | Planned | Planned | Planned |
| First-person Shooter | Not offered | Not offered | Planned (M3b.7) |
| Third-person Shooter | Not offered | Not offered | Planned (M3b.7) |
| Real-time Strategy | Planned (R4) | Planned (R4) | Planned (R4) |
| Turn-based Strategy | Planned | Planned | Planned |
| Racing | Planned | Planned | Planned (M3b.7) |
| Puzzle | Planned | Planned | Planned |
| Simulation | Planned | Planned | Planned |
| Sandbox | Planned | Planned | Planned |

"Not offered" repeats the world-first audit's "n/a" judgment (a first-person or third-person shooter in 2D or 2.5D); it is shown with its reason, not hidden, and the owner may change it (TD-088, question 3; the proposal stands). No pair is playable today; none is claimed.

## 6. Staged plan

| Step | Milestone | What | Gate |
|---|---|---|---|
| 1 | M3b.2 | Registry, support table, `GameTestProfile`, `gameGenre` field, Player component, `InputMapping` and `InputSource`, validation and runtime-only components; no pair playable yet | TD-088 |
| 2 | M3b.3 | Platformer + 2.5D: side-follow camera, plane mover, a depth lock on the character; one sample scene | none |
| 3 | M3b.5 | Profile step in `PlayModeController` and `GameSession`; Stop and undo-history tests; save and load of the field | TD-088 (scene-file field) |
| 4 | M3b.4 | The Game Genre dropdown in the footer, fed by the registry, planned pairs marked and refused | Owner (decision 11, answered) |
| 5 | M3c.1 | Action RPG + 3D on `terrain-3d`'s valley: orbit camera, ground mover | none |
| 6 | M3b.7 | Agent tools (set genre, validate, Play, Stop), then further pairs | none |

M3b.5's profile step is needed by M3b.3's done-when ("in Play the player runs and jumps"), so steps 2 and 3 are built together; the chart's order is kept for the reports.

## 7. Risks

- **Two editor concepts named "profile".** `GameModeProfile` (editor view rules) and `GameTestProfile` (gameplay at Play). TD-088 keeps both names and documents the difference; renaming the older one is not proposed.
- **A built-in driver can look like the whole game.** The footer and the support table must say what the profile supplies (movement, camera, input) and what it does not (combat, goals, enemies).
- **Depth lock on a character.** Jolt's character has no plane constraint; correcting depth after each step must not stop jumps or slides. M3b.3 tests it on real Jolt.
- **Input reads in many places.** `InputSource` is used by the new drivers only; the 22 sample scripts keep reading `Gdx.input`. Migrating them is not proposed.
- **Web.** The web player ignores the field, so a web-opened scene plays without the profile. Decision 19 requires that the field survives a web save; that is the UrConnex workflow's to confirm and is recorded as a web limitation.

## 8. Commands

| Command | Exit | Result |
|---|---|---|
| `git rev-parse --short HEAD` | 0 | `0469ceb` |
| Searches with `grep` over `engine-*`, `editor-*`, `assets/scripts`, `engine-babylon/src` and the roadmap; a Python count of component types per sample scene | 0 | The facts in sections 1 and 2 |

No build or test was run: nothing that builds or is tested changed.

## 9. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
