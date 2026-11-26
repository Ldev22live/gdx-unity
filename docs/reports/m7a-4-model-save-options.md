# M7a.4 model save options

In **Customize → Edit 3D Model**, edit the chosen part, choose a save mode below the wireframe, then press **Save**. New and unique models are self-contained glTF files under the project's `models/edited/` folder. The active draft includes edits from all its primitives.

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Save as new model by default | Met | The default control writes a reusable file. On the Kenney wall, reopening recovered the extruded geometry and per-face material assignment. Both placed walls kept their original references. The owner journey now extrudes a face of the cabin and saves a separate house model. |
| Update a project-owned model | Met | Update writes to the current target. The desktop control asks for confirmation, names the model, counts other users in the open scene and warns about saved scenes and prefabs. The test reopens the updated model through a second instance and verifies its geometry. A second draft holding a stale revision cannot overwrite it. |
| Create a unique instance mesh | Met | A new file is bound to the chosen part with an editor command. Undo restores the prior binding and redo reapplies it. The other wall retains its reference. |
| Protect provider sources and avoid accidental replacement | Met | Update checks the provider path and the license manifest, and requires a project-owned destination. New and unique reject duplicate names and paths outside the output folder. The wall source bytes remained identical. |
| Save through the desktop controls | Met with automated Windows evidence | A graphics harness applied an edit, saved a new model and loaded it on the GPU, made one wall unique, then updated that model with a second wall referencing it. A separate full-editor smoke rendered the Boulder workspace for 120 frames with zero logged errors. [Editor capture](m7a-4/model-save-options.png) |

## File behavior and scope

New files use the existing glTF runtime model path. Updating a binary glTF retains the GLB container, with geometry and image data packed in its BIN chunk. The GLB test deletes the fixture's external texture after saving and successfully reopens the edited model. Changed primitives preserve attribute bytes through vertex ancestry, split faces by material and calculate position bounds. Generated vertices inherit source normals and UVs. The writer retains original buffers, so repeated edits across reopen cycles can increase file size.

The update target is the last saved model, or the opened model before the first save. Ownership is recognized for models inside `models/edited/` or listed in the project's originals record. Unrecorded models elsewhere can be saved as new. The shared-use count covers the open scene. The warning includes possible users in other scenes and prefabs without enumerating them. Undoing a unique binding keeps the reusable file available. World refresh and collision verification belong to M7a.5. The graphics run used automated controls. A human click-through remains unverified.

## Verification

- The baseline is the preceding full Gradle run at the same HEAD, with 2,472 tests, zero failures and one skip. The XML counts appear below.
- Ran `.\gradlew.bat test --continue --offline --console=plain -x generateAssetList`. The existing generated asset list was available. The first full run exited 0. A second run, including the GLB regression and the owner-journey assertion, exited 0 with 2,476 tests, zero failures and one skip.
- Focused compilation used `javac --release 21` with the project module outputs, JUnit libraries and packaged dependencies. `java --enable-native-access=ALL-UNNAMED -cp $cp Check io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSessionTest` exited 0 with all four tests passing.
- Windows control harness: `java --enable-native-access=ALL-UNNAMED -cp $cp SaveSmoke`, exit 0. The initial scratch harness compile had an ambiguous Files import. Explicitly importing the Java filesystem API corrected it. The harness uses a scratch project under `build/m7a4`.
- Full editor: `java --enable-native-access=ALL-UNNAMED -cp $cp io.github.ldev22live.gdxunity.editor.desktop.EditorLauncher --project assets --scene samples/action-rpg-valley-3d.json --smoke-test=120`, exit 0 with `GDXUNITY_EDITOR_SELECT=Boulder`, `GDXUNITY_EDITOR_EDIT_MODEL=1` and `GDXUNITY_SCREENSHOT=<repo>/docs/reports/m7a-4/model-save-options.png`.

Final XML reports across all 23 modules record 2,475 passes and one skip. The final Gradle daemon log records successful completion after the source-path label and metadata changes.

| Module | Before | After | Failures or errors | Skips |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 17 | 0 | 0 |
| editor-audio | 57 | 57 | 0 | 0 |
| editor-character | 10 | 10 | 0 | 0 |
| editor-core | 613 | 613 | 0 | 1 |
| editor-desktop | 211 | 211 | 0 | 0 |
| editor-sculpt | 59 | 63 | 0 | 0 |
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

Starting and ending HEAD were `b8bace5`. The starting tree already contained the M7a.1–3 implementations, sample assets, license metadata, runtime and browser changes, tests and documentation. This milestone extends the existing modeling controls and owner journey. No commit, push or destructive Git operation was performed.
