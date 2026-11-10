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
| `sprite-animation-2d.json` | 2D | A pixel-art sprite (`sprites/hero.sprite`, drawn in the Sprites tab format) playing its idle and walk tags: Sprite Animator with tag, speed, flip and tint | none | none |
| `tilemap-platformer-2d.json` | 2D | A tile map level: auto-tiled ground and platforms (tile set `sprites/tiles.sprite`), solid tiles as Box2D collision, two sprite heroes that fall onto the platforms | none | none |
| `pixel-adventure-2d.json` | 2D | A scripted pixel-art platformer on a tile map: run and jump (hand-written tile collision), idle and walk sprite tags with the sprite turning, six spinning coins, pits | A/D or arrows, Space/W jump, R restart | `scripts/samples/pixel/` |
| `sprite-25d.json` | 2.5D | The same hero and level in a 3D world seen from the side with a perspective camera: hills behind and pillars in front of the 2D plane | A/D or arrows, Space/W jump, R restart | `scripts/samples/pixel/` |
| `physics-sandbox-2d.json` | 2D | Box2D rigid bodies and colliders with no scripts: ramps, a stack of crates, bouncy blocks | Stop and press Play again to restart | none |
| `physics-joints-3d.json` | 3D | Joint 3D on Jolt: a door on a hinge that its motor opens to a 100 degree limit, a pendulum released from 45 degrees, a chain of ball joints that falls from horizontal, a slider platform driven along its rail to the end with a crate riding on it | none, it plays by itself; stop and press Play again to restart | none |
| `physics-mesh-3d.json` | 3D | Mesh Collider 3D and Convex Hull Collider 3D on Jolt: a glTF terrain (`models/physics-mesh-terrain.gltf`, a ramp up to a plateau and a bowl) is a static triangle mesh; balls roll off the plateau, boxes drop into the bowl, and rocks with a convex hull built from their mesh land on the ramp | none, it plays by itself; stop and press Play again to restart | none |
| `character-creator-3d.json` | 3D | Three characters made with the character creator (Animations tab > Character): a tall man walking, a short woman standing, an old man walking; each model has the humanoid animation templates baked in (re-bake with `gradlew :editor-character:bakeCharacter`, see `tools/make_character_sample.py`) | none, it plays by itself; stop and press Play again to restart | none |
| `animation-samples-3d.json` | 3D | The general animation sample clips playing on one entity each: a spinning cube, a swinging panel, a rocking bar, a color-pulsing sphere, a pulsing light and a swaying camera. The clips are in `animations/samples`; the humanoid templates (Idle, Walk, Run, Jump, Fall and the added Wave, Crouch, Punch, Dance, LookAround) are in `animations/humanoid`; all of them are listed in the Animations tab's Clip Library (re-make them with `python tools/make_animation_samples.py`) | none, it plays by itself; stop and press Play again to restart | none |
| `physics-sandbox-3d.json` | 3D | Jolt rigid bodies and colliders: eight balls (one bouncy) dropped from rising heights, a pyramid of crates, walls. A script on the camera aims a ray along the view and uses the physics script API: impulse, force, velocity, sphere overlap | Space kick the aimed body, hold F push it, E pop the neighbours, T stop it; stop and press Play again to restart | `scripts/samples/sandbox/` |
| `character-playground-3d.json` | 3D | A walking character (Character Controller 3D, Jolt): four steps and a 25 degree ramp it climbs, a 60 degree ramp and a tall block that stop it, a low block to jump onto, crates to push, a chase camera (Camera Rig) | WASD or arrows walk, Space jump | `scripts/samples/walker/` |
| `collector-3d.json` | 3D | Rolling movement with inertia, pickups that spin and bob, a timer, a third-person follow camera, lights and shadows | WASD or arrows, R restart | `scripts/samples/collector/`, `scripts/samples/common/` |
| `runner-3d.json` | 3D | An endless runner: lanes, jumping, pooled obstacles, speed that rises over time, a distance score | A/D or arrows change lane, Space jump, R restart | `scripts/samples/runner/` |
| `hopper-3d.json` | 3D | 3D platforming: jump between floating platforms, box collision on three axes, respawn after falling, a goal | WASD or arrows, Space jump, R restart | `scripts/samples/hopper/`, `scripts/samples/common/` |
| `racing-3d.json` | 3D | A three-lap race against two rival cars on a circuit built from road pieces: the race waits until you press W, then counts down; arcade steering, grass that slows you down, lap and race times, race position, a chase camera that turns with the car | W/S or Up/Down accelerate and brake, A/D or Left/Right steer, R restart | `scripts/samples/racing/` |
| `arena-fps-3d.json` | 3D | A first-person shooter: the camera is the player, with the gun as its children so it follows the view. Three waves of drones chase you; the gun fires pooled balls that fly from the muzzle to the crosshair and burst on impact; walls, crates and pillars block movement and balls | WASD move, arrows turn and look, Space start and fire, R restart | `scripts/samples/fps/` |
| `puzzle-3d.json` | 3D | A grid puzzle: push every crate onto a target tile, in four levels written as text grids in the script. Undo, restart, a move counter, pieces built from pooled entities, and a camera fitted to each level | WASD or arrows move, U or Z undo, R restart puzzle, Space next puzzle | `scripts/samples/puzzle/` |
| `ruins-runner-25d.json` | 2.5D | Side-scrolling through a 3D ruin with background and foreground layers; moving platforms that carry you; spikes, checkpoints, orbs | A/D or arrows, Space/W jump, R restart | `scripts/samples/ruins/`, `scripts/samples/common/` |
| `three-heroes-25d.json` | 2.5D | Three heroes who share one body: switch at any time. Mason conjures crates (E), Scout has a double jump, Brute pushes heavy blocks and smashes cracked walls (E). The cracked wall needs the Brute and the tower needs the Brute and Scout together; the Mason offers another way up the first cliff | 1/2/3 or Tab switch, A/D, Space, E ability, R restart | `scripts/samples/trio/`, `scripts/samples/common/` |
| `fighter-25d.json` | 2.5D | One-on-one fighting against a computer opponent, best of three rounds: punches and kicks with wind-up, hit and recovery timing, blocking, knockback, hit stun, jumping over low attacks, health bars made of boxes, a round timer | A/D walk, W jump, S block, J punch, K kick, Space start, R restart | `scripts/samples/fighter/` |
| `landscape-3d.json` | 3D | A scenic valley made of glTF models (`models/landscape/`): rolling terrain with height-coloured vertices, a lake, five mountains, 26 pines, 11 oaks, 12 rocks, 16 bushes and a cabin, all standing on the terrain. There is no game here: orbit and fly around it in the editor, or press Play to look through the scene's camera | Editor view controls; Play shows the fixed camera | none (regenerate with `python tools/make_landscape_models.py`) |
| `landscape-desert-3d.json` | 3D | A desert canyon: dunes, banded red rock with a ridge of mesas, four stand-alone buttes, an oasis ringed by palms, 18 cacti, rocks and dry bushes under a warm sun | Editor view controls; Play shows the fixed camera | none (regenerate with `python tools/make_landscape_biomes.py`) |
| `landscape-alpine-3d.json` | 3D | A snowy alpine valley: white hills with bare rock on the slopes, a frozen lake, 30 snow-dusted pines, five snow-capped mountains behind and a cabin | Editor view controls; Play shows the fixed camera | none (same script) |
| `landscape-island-3d.json` | 3D | A tropical island in a sea: a beach ring, green interior, 26 palms, bushes, rocks and two thatched huts | Editor view controls; Play shows the fixed camera | none (same script) |
| `landscape-autumn-3d.json` | 3D | An autumn forest: rolling hills in browns and golds, red, orange and yellow oaks among dark pines, a pond, rocks, bushes and a cabin | Editor view controls; Play shows the fixed camera | none (same script) |
| `skeleton-wave-3d.json` | 3D animation | A humanoid skeleton built from bone entities (Hips > Spine > Chest > Arm > Forearm...). One clip waves: the upper arm raises once, only the forearm swings, the head turns and the chest breathes. Shows that rotating a parent bone carries every child (forward kinematics) | Press Play; or select Hero and open `animations/skeleton/Wave.anim` in the Timeline | none (regenerate with `node tools/make_skeleton_samples.mjs`) |
| `skeleton-walk-3d.json` | 3D animation | A looping walk cycle seen from the side: legs swing in opposition, knees bend only while a leg swings forward, arms counter-swing, hips bob and the torso counter-rotates | Press Play; Timeline: `animations/skeleton/WalkCycle.anim` | none (same script) |
| `skeleton-tentacle-3d.json` | 3D animation | A six-segment chain: every segment runs the same wave a little later than its parent, which gives follow-through. The way to build tails, tentacles and ropes | Press Play; Timeline: `animations/skeleton/Tentacle.anim` | none (same script) |
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

## Animating skeletons (the skeleton-*-3d samples)

A skeleton here is a hierarchy of ordinary entities. Each **bone** is an empty entity placed on its joint; the visible limb is a box
child of the bone, offset so rotating the bone turns it about the joint. To try it:

1. Open a sample (**File → Open Sample...**) and press **Play**: the Animator on the root (`Hero` or `Tentacle`) plays the clip.
2. Stop, select the root and double-click its clip in the Content Browser (or use the Animator's open button) to open the **Timeline**.
3. Tracks target bones by path under the Animator's entity, e.g. `Hips/Spine/Chest/Arm R/Forearm R`. Rotation is keyed as
   yaw (around Y), pitch (around X) and roll (around Z) in degrees. Scrub, add keys at the playhead, change the easing, and press Play to compare.
4. To animate your own rig: add bone entities as children (child position = distance from the parent joint), add an **Animator**
   to the root, move to a pose, and key the bones' rotations. Make the last key equal the first for a seamless loop.

Imported glTF models with their own skins show their bones with the skeleton overlay and play their built-in clips through
`Model Clip Time`; these samples are for authoring your own poses.

## Sound effects

Each scripted sample has a **Sound Effects** entity holding **Audio Source** components. A source has a **cue** (the name a
script plays it by), a **sound** (an sfxr preset: Coin, Laser, Explosion, Powerup, Hurt, Jump or Blip), a volume (0 to 1),
a pitch (0.25 to 4) and **Play On Start**. Sounds only play during Play (and in the game runtime); the editor stays silent.

- Scripts trigger a cue with `AudioSourceComponent.play(getScene(), "jump")` (web scripts: `this.scene.playCue("jump")`).
  A cue no source answers plays nothing, so a typo is silent: a test checks that every sample's scripts and scene agree.
- To change how a sample sounds, select its **Sound Effects** entity and edit the sources in Details: change the sound,
  volume or pitch. To add a sound, add another **Audio Source** (Add Component > Audio) and give it a new cue, then call
  that cue from a script (both the Java script and, if the sample has one, its web version in `engine-babylon/src/scripts`).
- A scene whose Audio Source names an unknown sound does not start in the desktop editor or runtime; the web player reports
  it on the status line and keeps that source silent.

## Limits today

- The web (Babylon) runtime does not implement the Animator component yet, so the skeleton samples do not animate there.

- Sound effects only, no music: the 13 scripted samples play short sfxr effects (jump, coin, shoot, hit and so on). The
  landscape, skeleton and physics-sandbox samples have none. `racing`, `arena-fps`, `puzzle` and `fighter` have sound on
  the desktop only. See "Sound effects" above.
- No mouse aiming or mouse look (the arena shooter turns with the arrow keys). Mouse coordinates are window
  coordinates, not scene coordinates, and clicks in the scene view still go to the editor during Play.
- Scripts take no settings from the scene file. Tuning values are named constants at the top of each script.
- Outside the editor, the game runtime plays a sample with `gradlew :engine-desktop:run --args="--scene samples/<name>.json"`.
  It uses scripts compiled ahead of time (the Gradle task compiles them; it cannot compile scripts itself). There is
  no packaged game executable yet.

## Animations and visual scripting

- `humanoid-animations-3d.json`: five copies of the skeleton, each playing a clip from `animations/humanoid/` (Idle, Walk,
  Run, Jump, Fall). The Animations tab of the desktop and web editors lists the same clips.
- `visual-scripting-3d.json`: three entities each running a graph from `graphs/` (`Spinner` turns, `Hop` hops every 0.8 s,
  `Greeter` prints a greeting). Open a graph in the Visual Scripting tab and use Generate Java, Generate Kotlin (desktop) or
  Generate TypeScript (web) to get the same logic as a script.

## Sculpting

- `sculpted-rock-3d.json`: a rock made in sculpt mode and exported as a model. `sculpts/SculptedRock.sculpt` is the editable
  source (open it by dragging it into the view); `models/SculptedRock.glb` is the exported model this scene places with a
  Model component. It plays in the game runtime and the web player like any other model. Select a sculpt and use
  File > Export Sculpt as Model to make your own.

## Example graphs of the sample games

`graphs/samples/` holds one graph per sample game (platformer, collector, hopper, runner, shooter, breakout, racing, fps, puzzle,
fighter, grapple, ruins, trio, sandbox, walker). Each is a **slice** of that game's Java script, mostly the player's control,
built from nodes (Key Down and Input Axis read the keyboard on the desktop only). `graphs/samples/examples.json` states for each
which script it mirrors, what it covers and what it leaves out; the Graph tab's **Examples** button lists them and opens one.
They are not the games: the collision, scoring, enemies and levels stay in the Java scripts (TD-060).

## Depth

`depth-compositing-3d.json` places flat 2D shapes among 3D boxes seen through a perspective camera: the blue wall (z 150) hides the left
half of the green panel (z 0), and the orange panel (z 300) is drawn over the red cube it stands in front of. With an orthographic camera
2D entities ignore their Z (TD-063).
