# Alpha A12: Shared project format, implementation report

**Date:** 2026-11-25. **Milestone:** A12 of [milestone-roadmap.md](../milestone-roadmap.md). **Decision:** TD-106, approved in principle by the owner on 2026-11-25. Recorded with its implementation conditions and the Alpha rule set in [TECHNICAL_DECISIONS.md](../TECHNICAL_DECISIONS.md). **Final status:** see [alpha-a12-final-verification-report.md](alpha-a12-final-verification-report.md).

## 1. Baseline (directive §4)

| Item | Value |
|---|---|
| Branch, HEAD | `phase-12-3d-foundation`, `b8bace5`, unchanged throughout |
| Before A12 | 0 staged, 91 modified tracked, 250 untracked files. The `git status --porcelain -uall` snapshot (341 entries) is kept in the session scratchpad, so A12's files can be told apart from earlier work |
| Memory and disk | 1.1 to 2.6 GB free of 16 GB RAM during the work (browsers using most of it); 4.4 GB free disk |
| A11 artefacts | `docs/contracts/` (two schemas, capability matrix), `fixtures/contract-v1/` (valley project, invalid manifests), `tools/make_contract_fixtures.py`, the A11 report |
| Unrelated work in the tree | M7a, MC.2 to MC.4, M3c.6 and M3c.7, M3b.7 slice 4, asset work. Untouched by A12, except where A12 had to update a shared register (section 4) |

**Checkpoint commit inventory (proposal; nothing staged):**
1. **A12 code and tests:**
   - `engine-scene`: `PreservedFields`, `serialization/PreservedComponent`, `serialization/SceneSemanticDiff`, the `scenario` package; edits to `SceneSerializer`, `Scene`, `Entity`, `BaseComponent`;
   - `engine-desktop`: `GameComponentSerializers`;
   - `editor-core`: `project/contract/*`; edits to `EditorState`, `SceneFileController`, `PlayModeController`;
   - `engine-babylon`: `src/project/contract.ts`, `src/scene/semanticDiff.ts`; edits to `SceneBuilder`, `EditorApp`;
   - all their tests.
2. **A12 fixtures:** `fixtures/contract-v1/interop/`.
3. **A12 documentation:** the ten `alpha-a12-*` reports; TD-106; the capability matrix; the schemas; the matrices; the roadmap; the chart; the index.
4. Earlier work (the A00 proposal) separately. `asset-sources/` waits for its own decision ([alpha-a12-asset-storage-investigation.md](alpha-a12-asset-storage-investigation.md)).

## 2. What was implemented

| Phase | Result | Report |
|---|---|---|
| A12.1 Desktop data loss | Eliminated. The eight A11 losses are kept; all 56 samples' output is byte-identical to before | [desktop serialization](alpha-a12-desktop-serialization-report.md) |
| A12.2 Semantic round trip | `SceneSemanticDiff` and `semanticDiff.ts`, six documented normalizations only | same |
| A12.3 Manifest | `ProjectManifest`, `AssetInventory`, `ProjectMarker`; stable ids, Save as Copy identity, deterministic hashes | [backward compatibility](alpha-a12-backward-compatibility-report.md) |
| A12.4 Backward compatibility | Legacy projects open untouched; the manifest only on an agreed save; journaled recoverable saves | same |
| A12.5 Path and asset security | `ProjectPaths` (text rules plus real-path containment), `AssetInventory.verify` (missing or modified reported, never substituted, nothing downloaded) | same |
| A12.6 Ownership | `OwnerComponent` by player UUID; duplication, prefabs, undo; invalid references kept and reported | [desktop serialization](alpha-a12-desktop-serialization-report.md) |
| A12.7 Scenario data | Preserved and checked (`ScenarioValidator`); unknown rule types "not run" | same |
| A12.8 Capabilities | Desktop and web policies; read-only refuses saving in the editor | [capabilities](alpha-a12-capability-verification.md) |
| A12.9 Web adapter | `contract.ts`; preservation proven by tests | [web serialization](alpha-a12-web-serialization-report.md) |
| A12.10 Interoperability | Desktop → web → desktop → web and the reverse, through committed folders | [round trips](alpha-a12-round-trip-verification.md) |
| A12.11 Revisions | `ProjectStore`, `LocalProjectStore`; conflicts, Save Mine as a Copy, Reload Theirs | [revisions](alpha-a12-revision-conflict-report.md) |
| A12.12 AI readiness | Interfaces only; no AI code, no permanent plan schema | same |
| §14 Atmosphere | Optional on the web; data preserved; named as not drawn | [web serialization](alpha-a12-web-serialization-report.md) |
| §19 TeaVM | Excluded from Alpha acceptance (TD-106 classification); the code is kept | final report |
| §20 `asset-sources/` | Read-only inventory and proposal | [asset storage](alpha-a12-asset-storage-investigation.md) |

## 3. Problems found and fixed on the way

- **libGDX `JsonValue.toJson()`** returns a string value without quotes, which corrupted preserved string members. A helper renders values correctly.
- **Windows short and long path spellings** (`LOREAN~1.DEV`) could make a file inside a project look outside it, which would bypass the revision check. Both sides are now compared by real path.
- **A Play check** refused any scene with an unknown component. `PreservedComponent` is exempt because the scene serializer writes it; components that truly cannot be saved are still refused.

## 4. Shared registers updated because of A12

- **Component registration checks:** `BACKEND_SUPPORT_MATRIX.md` gained an `OwnerComponent` row, and `GameComponentSerializersTest` expects 44 types.
- **Sample inventory table:** the valley is now also tested by the web `contract.test.ts`.
- **`web-ui-ux-spec.md` vocabulary:** regenerated; its two changes come from **earlier** milestones (Platformer + 2D and Third-person Shooter + 3D became playable), not A12.
- **The valley web gap:** `sceneBuilder.test.ts` lists `AtmosphereComponent` for the valley, by the owner's classification.

No commit, push or destructive Git operation was performed.
