# M7a.2 3D Modeling workspace

**Customize → Edit 3D Model** opens a placed prefab in the existing 3D Modeling workspace. The camera frames the assembly, the prefab stays selected, and the mesh source panel lists its model-bearing parts. Opening a part loads a detached M7a.1 mesh document in memory and keeps its scene placement as the visual reference.

| Acceptance criterion | Result and evidence |
|---|---|
| Open a selected prefab in context | Met in the navigation test. The Customize entry opens 3D Modeling from the prefab root, frames it and keeps the selection and undo history when returning to World. |
| Preserve the placement of the opened model part | Met in `MeshWorkspaceTest`. The Kenney wall fixture includes parent and child transforms plus import scale. Assembly framing includes the child models. |
| Keep placement, prefab overrides, geometry and materials distinct | Wired in the source panel. Transform gizmos and prefab Details retain their existing roles. A failed part import clears the active document and displays the reason. Geometry operations and derived saves are separate later milestones. |
| Operate it in the running desktop app | **Manually Not Verified (Headless Environment)**. Place the CC0 House, select the prefab root, open Edit 3D Model, switch from the initial part to a roof or wall, and return to World. Check framing, selection, the absence of a new undo entry, and access to the prefab controls. |

## Verification

- Compiled the changed Java sources, including the desktop integration, with `javac --release 21` against the cached project dependencies. A direct JUnit Platform run via `java --enable-native-access=ALL-UNNAMED -cp <cached-classpath> <launcher>` exited 0. All 57 editor-sculpt tests and 30 focused editor-core tests passed, including the workspace, framing and agent capability regressions. Zero failures and zero skips. The first expanded direct run omitted the glTF library and failed an existing sculpt export test. With the library back on the classpath, that test passed.
- `gradlew.bat :editor-core:test :editor-sculpt:test :editor-desktop:test --offline --continue --console=plain` and the narrower retry `gradlew.bat :editor-core:test :editor-sculpt:test --offline --console=plain --info` were interrupted, both exit 1. The retry reached task selection without producing fresh test reports. Existing XML counts before the run were 7 editor-core, 56 editor-sculpt and 67 editor-desktop. Those cached reports represent prior runs with different filters. Full editor-core and editor-desktop suite results remain pending.
- Starting and ending HEAD were both `b8bace5`. The working tree already contained M7a.1 source, tests and documentation. Those changes were retained. No commit, push or destructive Git operation was performed.

The panel imports each chosen part synchronously in memory. It uses the selected model part's current scene reference and the M7a.1 static glTF subset. The next roadmap milestone is M7a.3, which adds mesh selections and operations.
