# Decision records that are not approved yet (2026-11-09)

`docs/TECHNICAL_DECISIONS.md` has twelve records whose status is not "Approved". Each one was written by an agent, and each
feature it describes is already built. This page lists what each record decided, what exists now, and what I would do with it.
**Nothing here is approved: that needs your word, one record at a time ("approve TD-0NN"), or as a group.** I changed no status.

How to read the last column: *approve* means the record matches what was built and I know of no reason against it; *approve
with a change* says what to change first; *superseded* means a later record replaced it.

| Record | Decided | State today | My recommendation |
|---|---|---|---|
| **TD-025** Built-in 3D primitives and editor lighting | `MeshPrimitiveComponent` (box, sphere, plane) in `engine-render`, a fallback editor light, light serializers and editors | Built and used by almost every 3D sample; the web player draws the same primitives | **Approve.** |
| **TD-027** Prefab assets, instance links, overrides | `.prefab` JSON files, `PrefabInstanceComponent`, comparison-based overrides, apply / revert / unpack | Built (Phase 14) and covered by tests; visual check still pending | **Approve.** The visual check is a separate open item. |
| **TD-033** Package the editor with `jpackage` | App image with a bundled runtime, `gdxunity.project` marker, projects located explicitly | Built (18-A); the Windows zip was built and run; the `.msi`, macOS and Linux are not verified (TD-047) | **Approve.** |
| **TD-034** Game camera, screen text, sample games | `CameraComponent`, `TextComponent`, a camera rig that plays through the game camera, 14 sample games | Built; 32 scenes now ship (4 2D, 6 3D, 4 2.5D templates plus later ones) | **Approve.** |
| **TD-035** Game runtime plays scene files | `engine-desktop` plays a scene file from a project folder; scripts precompiled | Built; packaged export (TD-046) builds on it | **Approve.** |
| **TD-036** Web game runtime via gdx-teavm | A TeaVM build of the Java engine (`engine-web`) that plays the shared game runtime in a browser | **Exists** (it is in `settings.gradle`; the INDEX says it plays the 14 samples; its Docker deploy files are unverified). It is the *Java* way to the web; TD-041 is the second, *JavaScript* way (Babylon.js), which has since grown further (editor tabs, Jolt physics, music) | **Approve**, and add one line saying the two web paths exist side by side (TD-036 for the Java runtime, TD-041 for the JavaScript player and editor). I wrongly drafted "superseded" first; the code does not say that. |
| **TD-037** The web editor is the desktop editor, streamed | One container per user: Xvfb, software OpenGL, noVNC; no editor code change | Files under `editor-desktop/deploy/stream/`; the sculpt triangle limit (TD-062) reacts to its `LIBGL_ALWAYS_SOFTWARE=1`; **never run here (no Docker)** | **Approve with a note**: "built, not run". |
| **TD-038** Sculpting as fixed-topology vertex editing | `engine-sculpt`, `editor-sculpt`, brushes, `.sculpt` files, 19-C export (TD-062) | Built through 19-C; mouse sculpting never tried by a person | **Approve.** |
| **TD-039** Multi-pane 3D scene view | `ViewportLayout` tree of panes, axis views, the editor starts in four panes | Built; headless logic tested; mouse use of dividers not tried | **Approve.** |
| **TD-040** Music studio from two audio modules | `engine-audio` (synth, drums, SoundFont, sfxr, mixer) and `editor-audio` (documents, piano roll, WAV export) | Built (20-A, 20-B); 20-C parts done (TD-043, TD-061, TD-063); never listened to | **Approve.** |
| **TD-041** JavaScript web player and editor on Babylon.js | `engine-babylon` (Vite, TypeScript), the scene file as the contract, `planck` for 2D, Jolt for 3D (TD-059, TD-064) | Built; 359 tests; now also run in a real browser (see the TD-064 notes) | **Approve.** |
| **TD-043** Sound effects in scenes | `AudioSourceComponent` with cues and sfxr presets, a `SoundSink` bound by the host | Built; extended by TD-063 (positional sound, clips), which you approved | **Approve.** |

## What I would do next, if you agree

- Approve in one message: `approve TD-025, TD-027, TD-033, TD-034, TD-035, TD-036, TD-038, TD-039, TD-040, TD-041, TD-043`.
- Decide TD-036 (approve, with the note that two web paths exist) and TD-037 (approve as "built, not run", or leave Proposed until someone runs the container).
