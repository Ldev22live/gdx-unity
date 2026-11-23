# Character creator live preview and sliders report (TD-056)

**Date:** 2026-11-08 · **Decision:** TD-056 (approved: "approve TD-056", as typed) · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)

## Acceptance

| Criterion | Result | Evidence |
|---|---|---|
| A preview of the character at the top of the creator | Met (still frame) | `docs/reports/img/td56-creator-preview.png` |
| It follows the settings | Partly met | `td56-preview-Old_Man.png`, `-Short_Woman.png`, `-Tall_Man.png`: three saved characters show three different builds. **Not seen:** a slider being dragged and the preview changing |
| A template animation plays | Met (still frames) | `td56-crouch-200.png` (crouched) and `td56-crouch-260.png` (standing) from two runs; `td56-preview-Old_Man.png` (Wave, arm up) |
| Sliders for every setting | Met (still frame) | body and skin rows in the captures; the shape sliders use the same row code and were not captured |
| `bakeModel` tested | Met | `CharacterSessionTest` +2 |
| Full suite unchanged apart from the known failures | Met | table below |

## What was changed

- `engine-render`: new `ModelPreview` (loads a glTF from memory through a temporary file that is deleted at once, plays one animation, orbit and zoom, PBR shaders with skinning like `SceneRenderer3D`).
- `editor-desktop`: new `CharacterPreviewWidget` (frame buffer, drag to turn, wheel to zoom, restores the GL viewport and scissor); `CharacterCreatorPanel` (preview and animation list at the top, a slider with a number field per setting, lazy reload, `open(name, animation)`); `EditorSkinFactory` (Slider style); `AnimationsPanel` disposes the creator; `EditorApplication` (aid `GDXUNITY_EDITOR_CREATOR=<character>[:<animation>]`); `TimelinePanel` toolbar in a sideways-scrolling pane.
- `editor-character`: `CharacterSession.bakeModel()` and `BakedModel`; `bake` uses it.
- Found while capturing: with the creator open the Timeline toolbar's width pushed the whole editor about 17 px past the window edges (about 5 px before TD-055's button gaps; visible in `td54-editor-creator.png`). Fixed by the scrolling toolbar; the last captures show it in place.
- Two wrong turns on the way: my first viewport restore used the stage viewport (not the cause of the shift, replaced by restoring the exact previous viewport), and one capture was a stale file because that run had no screenshot variable.

## Commands and results (2026-11-08, Windows)

- `gradlew :editor-desktop:run --args="--smoke-test=<200..300> --scene samples/animation-samples-3d.json"` with `GDXUNITY_SCREENSHOT`, `GDXUNITY_EDITOR_TAB=Animations`, `GDXUNITY_EDITOR_CREATOR=...`; each "0 error(s)".
- `gradlew test --continue --rerun-tasks`: exit 1 only for the 2 known `BundledDemoSongTest` failures in `editor-audio`. This is the first complete run after the TD-055 test fixes.

| Module | Before | After |
|---|---|---|
| editor-character | 8 | 10 |
| editor-desktop | 77 | 77 |
| editor-audio | 55 (2 known failures) | 55 (2 known failures) |
| editor-animation, editor-core, editor-sculpt, editor-visualscript | 17, 357 (1 skipped), 45, 28 | same |
| engine-animation, engine-audio, engine-character, engine-core | 32, 185, 19, 32 | same |
| engine-desktop, engine-physics, engine-render, engine-scene | 69, 53, 101, 124 | same |
| engine-sculpt, engine-visualscript, engine-web | 77, 22, 9 | same |

`ModelPreview`, the widget, the sliders and the skin style need a GL context: **needs graphics test**.

## Manually Not Verified (Headless Environment)

Dragging a slider and the preview following; turning and zooming the model; the animation list; Reset view; how smooth the animation is; how long a reload takes in the running editor (the body rebuild took about 50 ms in tests); the Slider look when hovered or pressed; shape slider groups; the preview when the window is resized or the creator is hidden and shown again.

## Known limitations and next steps

- Only the body is shown (no eyes, hair, clothes); fingers and toes do not animate.
- The preview reloads on the UI thread once per committed change; a very slow machine could stutter.
- The creator column is short once the preview takes its space; the form scrolls.
- Next candidates: the web checks (`npm ci`, `npm test`, `npm run typecheck`), web player parity, eyes and hair, ethnicity sliders, joint breaking force.

## Git

No commit, push or other Git write was done. The PNGs in `docs/reports/img/` are new untracked files.
