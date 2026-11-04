# Phase 17 — Skeletal Animation and Timeline

**Decisions:** TD-030 (gdx-gltf adopted after the spike), TD-031 (two modules) — both approved by the project owner 2026-10-29
**Unreal equivalent:** Sequencer, skeletal mesh animation playback · **Roadmap:** `ALL_IN_ONE_STUDIO_ROADMAP.md` §5
**Status (2026-10-29):** Implemented and integrated; visual check pending. Report: `docs/reports/phase-17-report.md`.

## Modules

| Module | Holds | Depends on |
|---|---|---|
| `engine-render` (extended) | `ModelAssets` (gdx-gltf loading), `ModelPoser`, `ModelComponent` (+ serializer), PBR batch in `SceneRenderer3D` | + gdx-gltf 2.3.0 |
| `engine-animation` (new) | Curves, tracks, clips and `.anim` files, `AnimatedProperties`, `ClipSampler`, `AnimatorComponent`, skeleton FK / two-bone IK | `engine-render` |
| `editor-animation` (new) | `TimelineDocument` (own undo), `TimelineCommands`, `AnimationAssets`, `TimelineSession`, `AnimationPlayModeExtension` | `editor-core`, `engine-animation` |
| `editor-desktop` (UI only) | Timeline tab (`TimelinePanel`, `TimelineCanvas`), Model and Animator Details editors, bone overlay | all of the above |

## What exists

| Step | Content | Status |
|---|---|---|
| A — models | Drop `.gltf`/`.glb` into the viewport or Add Component → Model (uses the selected file); PBR shading with skinning; Details lists the file's clips, Play On Start, Loop, Speed (negative = reverse), Import Scale (100 = glTF metres to scene units), Reload; load errors shown in red; picking/outline/focus use the model's bounds; selected models show their bones | Done; visual check pending |
| B — keyframes | Curves with Stepped / Linear / Bezier (auto tangents, eased ends); tracks for position, rotation (yaw/pitch/roll), scale, mesh color, light intensity and Model Clip Time on the entity or any child; `.anim` JSON | Done |
| C — Timeline | Bottom tab: ruler and play head (drag to scrub, scene previews), Play/Pause, Reverse, Loop, Auto-Key, Key (selected entity; adds 9 transform tracks if it has none), Add Track menu, key drag (snapped to frames), Shift-select, Delete, double-click to key, interpolation per key, Curves view (drag keys in time and value), Length/FPS, own Ctrl+Z / Ctrl+Y, Space | Done; visual check pending |
| D — integration | Add Component → Animation → Animator (creates `animations/<Entity>.anim` and opens it); Details → Open in Timeline; double-click `.anim`; play loads clips (missing clip blocks play with the reason; broken tracks are warnings); preview is restored before play and before saving the scene | Done |
| E — skeleton math | `Skeleton`/`Pose` forward kinematics, analytic `TwoBoneIk` with pole, `Skeletons.fromModel` | Done (engine only) |

## Known limitations

- No tangent handles in the curve view (Bezier tangents are automatic); no box selection; the key area always fits the
  whole clip (no zoom).
- IK is engine math only: no IK component or Timeline control yet.
- Tracks find children by name; renaming a child breaks its tracks (shown in red and warned at play).
- Unkeyed edits to animated properties while previewing are undone when the preview ends (as in other sequencers).
- Auto-key reacts to new scene edits; it does not tell an undo from a redo of the last edit.
- Shadows and image-based lighting for imported models (Phase 13-B) are not set up.
- Duplicate does not copy Model or Animator (pre-existing: `DuplicateEntityCommand` copies only six component types).
- The runtime launcher (`engine-desktop`) does not render models or run Animators; no agent tools for animation.

## Manual checklist (when visual testing resumes)

0. Start the editor and press 2D / 3D: the startup scene shows a grey floor, a blue crate, a red ball, the orange
   bending strip and a sun light. Press Play: the ball bounces and spins (Animator, `BallBounce.anim`) and the strip bends.

1. Switch to 3D. Drag `models/BendingStrip.gltf` from the Content Browser into the view: an orange strip appears
   (100 x 200 units). Details → Model shows "2 bones, 1 clip(s)"; choose Clip "Bend". Its bones show as yellow lines.
2. Press Play: the strip bends left and right every 2 s. Stop: it is straight again.
3. Create → Box. Details → Add Component → Animator: the Timeline tab opens with clip "Box".
4. Move the play head to 0, press Key: nine transform tracks appear with keys. Move the play head to 1 s, turn on
   Auto-Key, drag the box up with the gizmo: Position Y gets a key at 1 s. Scrub: the box moves between the keys.
5. Select a key, choose "Stepped": the box jumps instead of gliding. Curves: select Position Y and drag a point up.
6. Press Play in the Timeline (Loop on): the box animates in the editor. Press the main Play: the box animates in play
   mode; Stop returns it to its authored place. Save the scene and reopen it: positions are the authored ones.
7. Rename the box's child (if any) used by a track: its track name turns red; Play logs a warning.
