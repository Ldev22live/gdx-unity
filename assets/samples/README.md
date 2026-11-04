# Sample games

Small, complete games to play, read and use as starting points. Open one with **File → Open Sample...**,
press **Play**, then click into the scene view so the game gets the keyboard. To start your own game from a
sample, open it and use **File → Save Scene As...**. You can also copy its script folder under `scripts/` and
rename the package.

| Scene | Kind | Shows | Controls | Scripts |
|---|---|---|---|---|
| `platformer-2d.json` | 2D | Running and jumping, box collision with platforms, collectables, a goal, a camera that follows the player | A/D or arrows, Space/W jump, R restart | `scripts/samples/platformer/` |
| `breakout-2d.json` | 2D | Ball physics written by hand, paddle angle control, bricks, lives, win and lose states | A/D or arrows, Space launch, R restart | `scripts/samples/breakout/` |
| `shooter-2d.json` | 2D | Object pools for bullets and enemies, spawning over time, rising difficulty, scrolling stars | WASD or arrows, Space fire, R restart | `scripts/samples/shooter/` |
| `physics-sandbox-2d.json` | 2D | Box2D rigid bodies and colliders with no scripts: ramps, a stack of crates, bouncy blocks | Stop and press Play again to restart | none |
| `collector-3d.json` | 3D | Rolling movement with inertia, pickups that spin and bob, a timer, a third-person follow camera, lights and shadows | WASD or arrows, R restart | `scripts/samples/collector/`, `scripts/samples/common/` |
| `runner-3d.json` | 3D | An endless runner: lanes, jumping, pooled obstacles, speed that rises over time, a distance score | A/D or arrows change lane, Space jump, R restart | `scripts/samples/runner/` |
| `hopper-3d.json` | 3D | 3D platforming: jump between floating platforms, box collision on three axes, respawn after falling, a goal | WASD or arrows, Space jump, R restart | `scripts/samples/hopper/`, `scripts/samples/common/` |
| `racing-3d.json` | 3D | A three-lap race against two rival cars on a circuit built from road pieces: the race waits until you press W, then counts down; arcade steering, grass that slows you down, lap and race times, race position, a chase camera that turns with the car | W/S or Up/Down accelerate and brake, A/D or Left/Right steer, R restart | `scripts/samples/racing/` |
| `arena-fps-3d.json` | 3D | A first-person shooter: the camera is the player, with the gun as its children so it follows the view. Three waves of drones chase you; the gun fires pooled balls that fly from the muzzle to the crosshair and burst on impact; walls, crates and pillars block movement and balls | WASD move, arrows turn and look, Space start and fire, R restart | `scripts/samples/fps/` |
| `puzzle-3d.json` | 3D | A grid puzzle: push every crate onto a target tile, in four levels written as text grids in the script. Undo, restart, a move counter, pieces built from pooled entities, and a camera fitted to each level | WASD or arrows move, U or Z undo, R restart puzzle, Space next puzzle | `scripts/samples/puzzle/` |
| `ruins-runner-25d.json` | 2.5D | Side-scrolling through a 3D ruin with background and foreground layers; moving platforms that carry you; spikes, checkpoints, orbs | A/D or arrows, Space/W jump, R restart | `scripts/samples/ruins/`, `scripts/samples/common/` |
| `three-heroes-25d.json` | 2.5D | Three heroes who share one body: switch at any time. Mason conjures crates (E), Scout has a double jump, Brute pushes heavy blocks and smashes cracked walls (E). The cracked wall needs the Brute and the tower needs the Brute and Scout together; the Mason offers another way up the first cliff | 1/2/3 or Tab switch, A/D, Space, E ability, R restart | `scripts/samples/trio/`, `scripts/samples/common/` |
| `fighter-25d.json` | 2.5D | One-on-one fighting against a computer opponent, best of three rounds: punches and kicks with wind-up, hit and recovery timing, blocking, knockback, hit stun, jumping over low attacks, health bars made of boxes, a round timer | A/D walk, W jump, S block, J punch, K kick, Space start, R restart | `scripts/samples/fighter/` |
| `grapple-gorge-25d.json` | 2.5D | Grappling rope with pendulum swinging across chasms, a pushable crate, a pressure plate that raises a gate | A/D, Space jump, hold J or Shift in the air to grapple, R restart | `scripts/samples/grapple/`, `scripts/samples/common/` |

## How the samples are built

- **Camera:** each scene has an entity with a **Camera** component. During Play, the scene view looks through it
  and hides the grid and gizmos. The 2D samples use an orthographic camera 480 units high. The 3D samples use a
  perspective camera that looks along its entity's -Z axis.
- **On-screen text:** score, lives and messages are entities with a **Text** component. It is anchored to a corner,
  an edge or the centre of the view. Scripts change it with `getComponent(TextComponent.class).setText(...)`.
- **Scripts:** these are Java classes that extend `ScriptComponent`. Each one is bound to an entity with the
  **Script** component, and it is compiled when you press Play.
  - Scripts find other entities by name, for example `getScene().findEntityByName("Player")`.
  - Scripts find groups of entities by name prefix. Entities whose name starts with `Platform`, `Coin`, `Brick`,
    `Gem`, `Star`, `Marker`, `Mover`, `Orb`, `Spikes`, `Checkpoint`, `Cracked`, `Heavy`, `Anchor`, `Road `, `Rival`, `Wall`, `Crate`, `Pillar`, `Enemy` or `Spawn` are picked up
    that way. Keep other entity names from starting with those words.
- **2.5D:** the level is built from 3D boxes, but the player moves only in the side-view plane at depth z = 0.
  `SideBody` (`scripts/samples/common/`) handles movement, gravity, collision, riding moving platforms and pushing.
  `SideCamera25D` looks from the front, slightly from above. Scenery placed at other depths (names starting with
  `Decor`) shifts at different speeds as the camera moves, which gives the sense of depth.
- **Racing circuit:** the track is the entities named `Road 1`, `Road 2`, ... in driving order, each a box
  turned around Y whose length (size Z) is the straight plus one road width, so neighbours overlap at the
  corners. `RaceTrack` reads them when the race starts: move, add or turn pieces and the rivals, the lap counter
  and the grass check follow. A car must drive every piece in order for a lap to count.
- **Collision:** all samples except the physics sandbox detect collisions in their own scripts with simple box
  overlap tests, so every rule is visible and easy to change. Scripts cannot yet reach Box2D bodies or collision
  events, and there is no 3D physics simulation yet.
- **Spawning:** things that appear often (bullets, enemies, obstacles) are created once when the game starts and
  then switched on and off with `setActive`. Use the same approach in your own games.
- **Units:** one unit is one pixel in the 2D samples. The 3D samples use the same scale, so a 50-unit cube is
  the player-sized block.

## Limits today

- No sound or music.
- No mouse aiming or mouse look (the arena shooter turns with the arrow keys). Mouse coordinates are window
  coordinates, not scene coordinates, and clicks in the scene view still go to the editor during Play.
- Scripts take no settings from the scene file. Tuning values are named constants at the top of each script.
- Outside the editor, the game runtime plays a sample with `gradlew :engine-desktop:run --args="--scene samples/<name>.json"`.
  It uses scripts compiled ahead of time (the Gradle task compiles them; it cannot compile scripts itself). There is
  no packaged game executable yet.
