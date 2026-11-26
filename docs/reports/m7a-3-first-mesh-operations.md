# M7a.3 first mesh operations

Open a static model with **Customize → Edit 3D Model**. Choose its part and primitive, pick a vertex, edge or face in the wireframe (or enter its zero-based index), choose a tool and press **Apply**. The preview uses primitive-local coordinates. Undo and Redo belong to the chosen part and primitive. Switching parts keeps their edits while the same workspace selection is open.

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Select vertices, edges and faces | Met | The preview highlights the selected element. Picking chooses the nearest projected vertex, edge midpoint or face centroid. An index can select overlapping elements. The Windows Boulder capture shows face 0 highlighted. |
| Translate and edit topology | Met | The Kenney wall tests check translation on all three selection kinds. Extrude moves a triangular cap along its normal, inset creates a coplanar inner triangle, and basic face bevel creates a raised inset cap with sloped sides. Each adds six side triangles with the original material and winding order. Delete removes the face. |
| Assign materials | Met | A face can use an existing source material slot or the default material. The wireframe identifies the selected face’s slot by number. |
| Undo each operation | Met | The Kenney wall tests compare the entire geometry before, after, on undo and on redo. An OpenGL harness fired the actual Apply, Undo and Redo controls for all six tools and drew the preview after each action. |
| Validate input and preview in context | Met with automated evidence | Invalid selections, material slots, offsets and topology parameters are rejected before recording a command. Thirty focused tests passed. The running Windows editor opened Boulder in the valley scene and rendered 120 frames with zero logged errors. [Editor capture](m7a-3/boulder-preview.png) |

## Verification

- Focused compilation used `javac --release 21` with the project’s compiled module outputs and packaged dependencies. A JUnit Platform run of `MeshPartEditorTest`, `ModelingHandOffTest`, `CustomizeGatewayTest`, `WorldFirstNavigationTest`, `ModelEditCheckToolTest` and `CustomizeGatewaySamplesTest` exited 0 with 30 passed.
- Graphics command, using the compiled module classpath: `java --enable-native-access=ALL-UNNAMED -cp $cp io.github.ldev22live.gdxunity.editor.desktop.EditorLauncher --project assets --scene samples/action-rpg-valley-3d.json --smoke-test=120`. Environment: `GDXUNITY_EDITOR_SELECT=Boulder`, `GDXUNITY_EDITOR_EDIT_MODEL=1`, `GDXUNITY_SCREENSHOT=build/m7a3/preview.png`. Exit 0. The capture was inspected.
- Control harness: `java --enable-native-access=ALL-UNNAMED -cp $cp io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.PanelSmoke`, exit 0. The harness rendered the Kenney wall using the desktop skin and asserted changes and restoration through the panel buttons.

## Gradle verification

Before editing, the existing XML reports recorded 2,470 tests across 23 modules, including one Windows skip. The historical reports and the post-change results are listed below. The initial command `.\gradlew.bat test --continue --offline` and the first verification command `.\gradlew.bat test --continue --offline --console=plain` both exited 1. They overlapped while writing binary results, so neither provides a clean before/after run. An intermediate owner-journey assertion still expected the previous blocked-step wording. Updating it aligned the assertion with M7a.3. The sample inventory check also exposed two pre-existing stale rows for the courtyard and range. Both now include the existing `AssetCreditsTest` coverage.

The final command ran on its own: `.\gradlew.bat test --continue --offline --console=plain -x generateAssetList`. The existing asset list was already present. No assets are added by M7a.3.

Final exit code 0. The module XML reports contain 2,471 passing tests, zero failures and one skip.

| Module | Before (cached) | After | Failures or errors | Skips |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 17 | 0 | 0 |
| editor-audio | 57 | 57 | 0 | 0 |
| editor-character | 10 | 10 | 0 | 0 |
| editor-core | 613 | 613 | 0 | 1 |
| editor-desktop | 211 | 211 | 0 | 0 |
| editor-sculpt | 57 | 59 | 0 | 0 |
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

## Scope and limitations

The preview is a fixed isometric wireframe with one editable triangle primitive at a time. The basic bevel operates on a face. Generated vertices retain their source vertex index for attribute lookup. The World instance continues to display the source asset. Leaving the selection ends the draft. Derived saves belong to M7a.4. A person has yet to perform the interactive checklist of clicking elements, switching parts, trying each tool and returning to World.

Starting and ending HEAD: `b8bace5`. The starting tree had 67 modified or untracked entries covering the M7a.1 and M7a.2 implementation, sample assets, license metadata, browser and runtime integration, tests and documentation. Changes here extend the existing modeling files, their regression tests and the status documents. No commit, push or destructive Git operation was performed.
