# Visual pass report (TD-054)

**Date:** 2026-11-07 · **Decision:** TD-054 (approved: "approve TD-054", as typed; the record was written after the approval from the offer "a look-and-feel pass on what's built") · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)

## What was done

The application saves its **own** last frame as a PNG (`GDXUNITY_SCREENSHOT`, plus a few editor start aids; see TD-054), so nothing from the rest of the screen can be in a picture. I read the PNGs (`docs/reports/img/td54-*.png`, 11 files) and compared them with what each feature should show. A still frame shows layout, scale, color and whether things stand where they should; it does not show motion, feel or what clicking does.

## What the frames show

| View | Frame | Result |
|---|---|---|
| Game runtime, `physics-joints-3d` | `td54-physics-joints-3d.png` | Door standing open on its post, pendulum bob hanging below its beam, the five-link chain hanging from its post, the platform with its crate on the rail. Matches the intent. The camera leaves a lot of empty space above. |
| Game runtime, `physics-mesh-3d` | `td54-physics-mesh-3d.png` | Terrain with ramp, plateau and bowl; boxes and balls resting in the bowl, two rocks on the ramp. Matches the intent. |
| Game runtime, `character-creator-3d` | `td54-character-creator-3d.png` | Three intact skinned characters of different build and skin: a tall man mid-stride, a short woman standing, an old man mid-stride, with shadows. The first capture was empty (defect 1) and too pale (defect 2); both fixed. |
| Game runtime, `character-playground-3d` (camera rig, TD-050) | `td54-character-playground-3d.png` | Arena seen from above and behind the hero, steps and ramp at the right. Plausible follow framing; whether it follows smoothly was not seen. |
| Game runtime, `physics-sandbox-3d` | `td54-physics-sandbox-3d.png` | Balls in a row, a crate pyramid, one ball in the air. Fine. |
| Editor, Animations tab with the creator | `td54-editor-creator.png` | Name, Undo/Redo/Reset/Open, Body rows, five template toggles, shape groups, Create / Update. Readable. The Timeline beside it is empty (no clip open) with a crowded toolbar (pre-existing). |
| Editor, Details of a Joint 3D | `td54-editor-joint-details.png` | All twelve rows with the right values and the Add Component button. Labels were cut off; fixed. |
| Editor, Details of a Mesh Collider 3D | `td54-editor-mesh-details.png` | Note, Offset, Trigger. The note ran off the panel; fixed (the fixed text was not captured again). |
| Editor, mesh terrain selected | `td54-editor-mesh.png` | The green triangle mesh lines follow the terrain and the bowl's profile; the rocks show green hull points. |
| Editor, joint sample, Door selected | `td54-editor-joint.png` | Joint anchor cross and axis line visible beside the door's gizmo (small, seen from the side). |

## Defects found

Fixed:
1. `character-creator-3d` showed nothing but its text: the sample's camera was turned away from the characters. Fixed in `tools/make_character_sample.py` and regenerated.
2. Skin looked washed out: glTF material colors are linear, the creator's are sRGB. The writer converts now; `GltfCharacterWriterTest` checks 0.8 becomes 0.604. The three sample characters were baked again.
3. Joint 3D labels cut off ("Joined to (n", "Motor targe", "Bodies collid"): shortened to Joined to, Other end, Collide, Lower, Upper, Motor goal, Max force.
4. Mesh and Hull Collider note ran off the panel: shortened.
5. Undo/Redo/Reset/Open ran together as one line of text: fixed width.

Found, **not fixed**, not caused by TD-050 to TD-053 (same layout code as `HEAD`):
- Details is about 150 pixels high at startup because the AI Assistant fills the rest of the right column although it is meant to be minimized, so the component editors are below the fold. I tried setting the split amount again after layout; it did not change anything, and I did not find the cause.
- The Timeline toolbar labels run together ("SaveUndoRedo", "ReverseLoop"); Content Browser rows overlap the type tag and the path; "Nothing selected" is cut at the panel edge; the editor's first 3D view is a side-on orthographic view, so joint lines and hulls are seen edge-on.

## Commands and results (2026-11-07, Windows)

Game runtime: `GDXUNITY_SCREENSHOT=<png> gradlew :engine-desktop:run --args="--scene samples/<name>.json --smoke-test=240"` for five samples (each "0 runtime error(s)"). Editor: `... :editor-desktop:run --args="--smoke-test=150 --scene <scene>"` with the start aids, eight captures (each "0 error(s)"). After the fixes: `gradlew :engine-character:test :editor-character:test :engine-desktop:test :editor-desktop:test :engine-render:test` passes with the counts below. **The full clean run (`test --continue --rerun-tasks`) was last done for TD-053, before the TD-054 changes, and was not repeated.** That run had only the 2 known `BundledDemoSongTest` failures in `:editor-audio`.

| Module | Before | After |
|---|---|---|
| engine-character | 19 | 19 |
| editor-character | 8 | 8 |
| engine-desktop | 69 | 69 |
| editor-desktop | 72 | 72 |
| engine-render | 101 | 101 |

## Manually Not Verified (Headless Environment)

Motion and its quality (door, pendulum, chain, platform, walking and idle characters, camera follow), feel and input (clicking Create / Update, typing in the creator's fields, dragging, undo in the panel), the editor lines and hulls in a perspective view, the Details of a Convex Hull Collider 3D (selected in the probe scene but not captured), and the web player. Nothing here is Validated beyond what a still frame shows, and the checks above say what was seen.

## Known limitations and next steps

- The pictures are of one frame at about 4 to 5 seconds in; a different moment could show something else.
- The five pre-existing layout defects are listed above and are good candidates for the next small fix.
- Next candidates: the Details panel height, then the character creator follow-ups (live preview, slider widgets, eyes and hair).

## Git

No commit, push or other Git write was done. The PNGs in `docs/reports/img/` are new untracked files.
