# Alpha A11: Shared architecture and compatibility contract (design)

**Date:** 2026-11-25. **Milestone:** A11 of [milestone-roadmap.md](../milestone-roadmap.md). **Status:**
- **Design delivered, awaiting approval.** [TD-106](../TECHNICAL_DECISIONS.md) is **Proposed**.
- **No engine, editor or web code was changed.** A11's done-when is "reviewed schema, example fixtures, capability matrix and API/AI boundary contracts"; the review is the owner's.

**Order:** A11 comes after A01 (done 2026-11-25, reference screenshot confirmed) and before A02 and A13 build on it. It also precedes any new saved data (A04, A05).

## 1. Deliverables

| Deliverable | Path |
|---|---|
| Decision record (proposed) | [TECHNICAL_DECISIONS.md, TD-106](../TECHNICAL_DECISIONS.md) |
| Manifest schema | [docs/contracts/project-manifest.schema.json](../contracts/project-manifest.schema.json) |
| Scenario block schema (reserves A04 and A05 data) | [docs/contracts/scenario.schema.json](../contracts/scenario.schema.json) |
| Capability matrix (desktop and web today, web Alpha target) | [docs/contracts/capability-matrix.md](../contracts/capability-matrix.md) |
| Fixtures | `fixtures/contract-v1/` ([README](../../fixtures/contract-v1/README.md)), written by `tools/make_contract_fixtures.py` |
| API and AI boundaries | TD-106 point 8 (`ProjectStore`, `AuthoringPlan`); interfaces only, no endpoints invented |

## 2. Design choices (decided here; each documented in TD-106)

- **Evolve, do not replace:**
  - every existing format is unchanged;
  - the project marker `gdxunity.project` gains `format=2`, `projectId` and `manifest=`;
  - one new file, `project-manifest.json`.
  - Format-1 readers only test the marker's existence and read single keys, so they still work (checked in `EditorProject`, `EditorProjectLocator`, `GameExportCommand`).
- **Scenario data in the scene, ownership as a component:**
  - a versioned `scenario` block holds players, objectives, rules and bounds;
  - `OwnerComponent` sits on each owned entity, so ownership survives duplication and prefabs.
  - Rules are a closed set of conditions and effects (`timer`, `enterArea`; `showMessage`, `completeObjective`, `endScenario`), bounded to 256 rules and 8 effects each, with no code.
  - Whether the Rules drawer writes this data or compiles to graphs is left to A05's TD.
- **Capability ids rather than feature flags:** a missing required id opens the project read-only, a missing optional id opens it editable with the limits named. Nothing is converted.
- **Revisions:** `id` and `base`; a stale base is refused (409); "Save mine as a copy" or "Reload theirs". No last-write-wins and no automatic sync.
- **Asset hashes** only for files a scene reaches, cached by size and modification time. The sample `assets/` folder has 6,464 items, so hashing everything on each save would be wasteful.

## 3. Evidence

| Check | Command | Result |
|---|---|---|
| Fixtures reproducible | `python tools/make_contract_fixtures.py`, twice, `diff -r` | Identical |
| Manifest valid | A JSON Schema subset validator (scratch tool, not added to the repository; no new dependency) | `valley-project/project-manifest.json`: VALID |
| Scenario block valid | Same | VALID |
| Negative cases | Same | `path-traversal`: INVALID (path pattern); `future-version`: INVALID ("must be 1, got 2") |
| Path rule | Python `re` on the schema's pattern | Accepts `materials/Meadow.material`, `a/b..c/d.png`, the Kenney path with a space. Refuses `../x`, `a/../b`, `a/..`, `..`, `/etc/passwd`, `C:/x`, `c:x` and backslash paths |
| **Today's desktop loses data** | A scratch single-file Java program (`java -cp <module classes; gdx jars> LossDemo.java`) runs `SceneSerializer.fromJson` then `toJson` with the editor's full serializer list (`GameComponentSerializers`) on `scenes/valley.json` | Of 12 differences, **8 are real loss**: top-level `scenario` and `exampleFutureSetting`; the `futureTerrainBlend` field; 4 × `OwnerComponent`; `ExampleFutureComponent`. The other 4 are empty `materialPath: ""` omitted on save, which is semantically equal. **A12's round-trip tests must therefore compare semantically, not byte for byte** |
| Web editor | Code reading: `parseSceneFile` checks only `schemaVersion` and `entities`; `EditorModel.serialize()` writes `JSON.stringify(fileData)` | Unknown data is kept on a web save **by construction, not yet by a test**. A12 adds the test |

## 4. What remains (A12 and later)

- **A12 desktop:**
  - opaque components, residual fields and kept top-level keys in `SceneSerializer`;
  - manifest read and write;
  - a local `ProjectStore`;
  - Details shows "Not editable here" for opaque components.
- **A12 web:**
  - a project, not just a scene;
  - manifest handling;
  - preservation tests.
- **Fixtures still to add:** terrain, derived mesh, sprite, prefab link, profile variants. Then round trips desktop → web → desktop and the reverse.
- **A16 and A18:** the cloud `ProjectStore` and the AI gateway transport, both blocked on the UrConnex and UDX contracts (audit §9).

## 5. Approval requested

1. **Approve TD-106** (or name changes). Code starts only after approval.
2. **Confirm the scenario block's condition and effect set** as the starting point for A04 and A05, or defer it to their TDs.
3. **Confirm the file name `project-manifest.json`** (product-neutral; no rename to urGameForge in file formats, per roadmap §1).

**Git:** no commit, push or destructive Git operation was performed. HEAD `b8bace5`.
