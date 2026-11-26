# M7a.1 Editable static mesh source

TD-102 was approved on 2026-11-23. The headless implementation is in `engine-sculpt`. Version 1 of `.mesh` wraps a self-contained glTF 2.0 model with source and licence metadata. The glTF scene graph, model indices and vertex data remain available to later geometry tools. Imports embed external buffers and images so the derived document can be reopened independently of those files.

| Criterion | Result | Evidence |
|---|---|---|
| Import, save and reopen an editable building mesh | Met | `EditableMeshIOTest.shouldRoundTripKenneyBuildingWithProvenance` compares the reopened document and the original GLB's binary chunk. |
| Preserve node transforms, mesh references and material slots | Met | The shared-mesh fixture includes nested nodes, a local translation and two triangle primitives with different material slots. |
| Keep provenance and preserve the provider original | Met | The Kenney fixture verifies CC0 metadata, checksums and the original file bytes. |
| Report unsupported or invalid input | Met for the documented import subset | Fixtures cover skins, missing buffers, truncated buffer data and an unsupported document version. The reader also rejects animations, morph targets, sparse accessors, required extensions and non-triangle primitives. |
| Desktop UX acceptance | Pending | M7a.1 is a headless API. **Manually Not Verified (Headless Environment)**. M7a.2 provides the workspace entry point. |

## Verification

- `gradlew.bat :engine-sculpt:test` exited 1 in the workspace sandbox because Gradle could not open its wrapper lock outside the repository. The same command with elevation exited 0.
- `gradlew.bat :engine-sculpt:test --offline --console=plain --info` with elevation exited 0. The final XML reports list 86 tests, zero failures, errors or skips, including five new `EditableMeshIOTest` tests. The 81-test baseline is the final count excluding the new class. A separate baseline build and the full multi-module suite were not run.
- `git diff --check` exited 0. Starting and ending HEAD were both `b8bace5`. The working tree initially contained the pending TD-102 draft. The diff updates that decision and adds the mesh implementation, tests and status documentation. No commit, push or destructive Git operation was performed.

The importer targets static glTF triangle geometry with core material slots. It validates the mesh data and core references it consumes. M7a.2 is the workspace hand-off. Later R7a milestones cover geometry operations and gameplay/export integration.
