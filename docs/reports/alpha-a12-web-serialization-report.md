# Alpha A12.9: Web (Babylon.js) project format and preservation

**Date:** 2026-11-25. **Status:** **Implemented and tested in automated tests** (vitest, Node, real files and the real `EditorModel`; no browser run). The A11 claim that the web editor keeps unknown data is now proven by tests, not just by reading the source.

## 1. What was built (`engine-babylon`)

- **`src/project/contract.ts`:**
  - `parseManifest`: as strict as the desktop (newer version, unknown member, bad hash or capability, unsafe path refused);
  - `manifestToJson`: the same member order and layout as the desktop's `ProjectManifest.toJson`;
  - `checkPath`;
  - `evaluate` (capabilities) with `WEB_SUPPORTED`;
  - `sha256Hex` (Web Crypto) and `verifyAssets`;
  - `nextManifest`: a new web revision; a stale base throws `RevisionConflict`;
  - `markerText` and `parseMarker`: the same marker text as the desktop's `ProjectMarker`;
  - `describeUnsupported`.
- **`src/scene/semanticDiff.ts`:** the same comparison and the same documented normalizations as the desktop's `SceneSemanticDiff`.
- **`SceneBuilder`:** `OwnerComponent` is data (nothing to draw), like `PrefabInstance`; it is no longer reported unsupported.
- **`EditorApp`:** after each rebuild, the status line names what the view does not draw or run, for example "Not shown on the web: AtmosphereComponent. Their data is kept unchanged when you save; edit them in urGameForge Desktop."
- **`AtmosphereComponent`:** classified as an optional web capability (owner decision; presentation only). `sceneBuilder.test.ts` lists it as known-unsupported for `action-rpg-valley-3d.json`. The valley builds and shows the editor's default sky; this fallback is the existing renderer's default, not new code.

## 2. Evidence

| Test | Result |
|---|---|
| `contract.test.ts` (7) | The fixture project read, every hash verified, limited by the optional profile; invalid manifests and unsafe paths refused; read-only with a missing required capability; missing and modified assets reported; a new revision from the base and a stale base refused; supported edits (move, component field, undo and redo) leave the scenario, ownership and unknown members exactly as they were; the fixture builds with only `ExampleFutureComponent` and `PlayerComponent` unsupported; **the valley's atmosphere data unchanged through a web save** and named as not drawn |
| `interop.test.ts` (4) | See [alpha-a12-round-trip-verification.md](alpha-a12-round-trip-verification.md) |
| `sceneBuilder.test.ts` | The valley's `AtmosphereComponent` is now an expected gap (owner classification), so the A00 failure is resolved by decision, not by hiding it |

## 3. Limits

- **No project UI:** the web editor still opens and saves single scenes through the browser's file picker. The contract functions are tested but not yet wired to a "project" UI; that UI is A13 and A14.
- **No browser run:** all evidence is Node-side.
- **No cloud store:** the cloud `ProjectStore` waits on the UrConnex contract (A16).
