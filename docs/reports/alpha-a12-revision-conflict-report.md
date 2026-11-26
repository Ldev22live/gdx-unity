# Alpha A12.11: Revisions, conflicts and storage boundaries

**Date:** 2026-11-25. **Status:** implemented with local storage. A cloud store waits on the UrConnex contract (A16); no endpoint was invented.

## Model

- **Every save is a new revision:** `revision.id`, with `base` the revision it was saved from.
- **`ProjectStore`** (`editor-core`, `project.contract`) is provider-neutral:
  - `open()`;
  - `save(base, sceneTexts, addedRequired, createManifest)`, which returns `Saved`, `Conflict`, `ReadOnly` or `NeedsManifest`;
  - `saveAsCopy`.
- **`LocalProjectStore`** compares the base with the stored revision while holding a file lock (`.forgeroom-save.lock`), so two editors on one machine cannot both save from one base.
- **A cloud implementation** must make the same comparison atomically on the server. The interface's Javadoc states this; a client-side check alone is not enough.
- **No last-write-wins and no automatic sync.**

## Two clients, one revision

| Step | Result |
|---|---|
| A and B open revision R | Both have base R |
| A saves | New revision R2, base R |
| B saves from R | **`Conflict(yourBase R, current R2)`**; B's text is not written, and A's save stands |
| B: "Reload theirs" | `open()` gives R2 |
| B: "Save mine as a copy" | A new project folder: new `projectId`, first revision with base `null`, the copy's name; the original untouched |

**Evidence:**
- **Store:** `ProjectContractTest.shouldGiveAConflictToTheSecondClientAndOfferCopyOrReload`.
- **Desktop editor:** `SceneFileControllerContractTest.shouldRefuseTheSecondEditorsSaveAsAConflictAndOfferACopy`. Two editor instances on one project: the second Save is refused with "Save conflict", the file keeps the first save, `saveMineAsCopy` makes a new project with a new id, and `reloadTheirs` reopens the current revision.
- **Web:** `nextManifest` throws `RevisionConflict` for a stale base (`contract.test.ts`).

## Identity rules

- **The project id is stable across saves** (tested). It is new only for a new project, or for a project's first manifest, or for Save as Copy.
- **Scene ids are stable** (tested).
- **Ids come from an injectable source:** random UUIDs in the editor, a fixed sequence in the interop tests.

## AI readiness (A12.12)

- No AI code was added.
- The contract gives a future UDX client what it needs: project context (manifest and scenes), capability discovery (`CapabilityPolicy`), revision preconditions (`ProjectStore.save` with a base, conflicts) and validated edits (`EditorCommand` and the existing `AgentPlan` and `AgentTool` layer, unchanged).
- No AI plan schema was made permanent. A18 defines it against the verified UDX contract.
