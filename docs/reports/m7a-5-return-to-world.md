# M7a.5 Return to World

The World camera now returns to its pre-modeling position, direction, up vector and orbit target. Framing a model leaves auxiliary panes at their World framing. Saving an edited model invalidates its cached asset. World rendering creates a fresh model instance, collider overlays refresh their extracted triangles, and the next Play session builds mesh-based collision from the saved geometry.

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Retain selection and camera context | Met | `WorldFirstNavigationTest` checks framing, selection, camera restoration and viewport mode. A second Edit 3D Model within the same session retains the original return context. |
| Place a CC0 house, change a wall and return the edited asset to World | Met | `ModelReturnGraphicsSmoke` places the bundled Kenney house prefab in a scratch project. Its Wall East South gains six triangles with each of two face extrusions. The desktop controls first create a unique instance and then update the same file. World rendering uses a different model instance after the second save. [Rendered house](m7a-5/edited-house.png) |
| Rebuild collision and verify it in Play | Met for mesh-derived collision | The graphics fixture equips the wall with a Mesh Collider 3D. The renderer and collision source read 22 triangles after editing, up from 10. After prefab reopen, a real Jolt ray cast in Play hits the twice-extruded cap at approximately (175, 80, 66.67) world units, within 0.1 units of its expected center. The Play renderer draws that wall. |
| Undo, redo and reopen | Met | Undo restores the original wall's 10 collision triangles. Redo restores 22. Writing prefab overrides, serializing, reopening and synchronizing the prefab retain the unique path and exact mesh positions. |
| Preserve glTF through game export | Met | `OwnerJourneyRegressionTest` extrudes a face of a second house fixture, binds the derived model, returns to World, undoes and redoes the binding, saves, reopens and enters Play. It compares the exported glTF byte-for-byte with the edited project asset and runs the exported game files. |

## Verification

- Gradle baseline from M7a.4 at the same HEAD: 2,476 tests, zero failures and one Windows skip.
- Full suite command: `.\gradlew.bat test --continue --offline --console=plain -x generateAssetList`. The project already had its generated asset list.
- Graphics regression: compiled the changed sources and `ModelReturnGraphicsSmoke` using `javac --release 21 -cp $cp -d build/m7a5/classes $sources`. The classpath contained the compiled module outputs, packaged desktop dependencies and JUnit dependencies. Ran `java --enable-native-access=ALL-UNNAMED -cp $cp io.github.ldev22live.gdxunity.editor.desktop.ModelReturnGraphicsSmoke` from the repository root on Windows. Exit 0. The final run used a directional light for the capture. The image was inspected.
- `git diff --check` passed. Starting and ending HEAD were `b8bace5`. The starting working tree had 83 modified or untracked entries, including M7a.1–4, sample assets, editor and runtime work and documentation. The milestone extends the modeling, navigation, render-cache and regression files. No commit, push or destructive Git operation was performed.

Full Gradle exit code 0. The XML reports record 2,475 passes and one skip.

| Module | Before | After | Failures or errors | Skips |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 17 | 0 | 0 |
| editor-audio | 57 | 57 | 0 | 0 |
| editor-character | 10 | 10 | 0 | 0 |
| editor-core | 613 | 613 | 0 | 1 |
| editor-desktop | 211 | 211 | 0 | 0 |
| editor-sculpt | 63 | 63 | 0 | 0 |
| editor-sprite | 51 | 51 | 0 | 0 |
| editor-terrain | 63 | 63 | 0 | 0 |
| editor-visualscript | 94 | 94 | 0 | 0 |
| engine-animation | 32 | 32 | 0 | 0 |
| engine-audio | 233 | 233 | 0 | 0 |
| engine-character | 19 | 19 | 0 | 0 |
| engine-core | 61 | 61 | 0 | 0 |
| engine-desktop | 162 | 162 | 0 | 0 |
| engine-material | 15 | 15 | 0 | 0 |
| engine-physics | 60 | 60 | 0 | 0 |
| engine-render | 205 | 205 | 0 | 0 |
| engine-scene | 232 | 232 | 0 | 0 |
| engine-sculpt | 86 | 86 | 0 | 0 |
| engine-sprite | 44 | 44 | 0 | 0 |
| engine-terrain | 46 | 46 | 0 | 0 |
| engine-visualscript | 93 | 93 | 0 | 0 |
| engine-web | 9 | 9 | 0 | 0 |

## Scope

Exact triangle collision uses Mesh Collider 3D on static bodies. Convex hull colliders also read from the refreshed model asset. The house's shipped box colliders retain their authored dimensions. The smoke exercised the exact triangle collider. Undo in this journey restores the selected instance's model binding. Geometry tools retain their draft undo history while the modeling session is open. The Windows evidence uses automated UI events; manual camera and control interaction by a person remains unverified. M7a.6 covers source preservation and conversion fixtures.
