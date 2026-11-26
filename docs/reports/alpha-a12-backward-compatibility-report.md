# Alpha A12.4: Backward compatibility

**Date:** 2026-11-25. **Status:** existing projects and files stay usable, with no destructive migration.

| # | Item | How it stays compatible | Evidence |
|---|---|---|---|
| 1 | Legacy project without a manifest | Opens as it is. **Nothing is written on opening.** A save without the person's agreement returns `NeedsManifest` and writes nothing. | `ProjectContractTest.shouldOpenALegacyProjectWithoutWritingAnything` |
| 2 | Legacy marker (format 1) | Still the project marker. Format 2 only adds keys (`projectId`, `manifest`), and format-1 readers (`EditorProject`, `EditorProjectLocator`, `GameExportCommand`) read single keys. The marker's name and start scene are rewritten from the manifest on each save. | `ProjectContractTest.shouldCreateTheManifestOnAnAgreedSave…` (the marker mirrors the manifest) |
| 3 | Existing scene format | Unchanged: `schemaVersion` 1, and the same bytes for files without unknown data. | **56 of 56 samples byte-identical** between the pre-A12 and the A12 serializer; `ContractRoundTripTest` semantic round trip of all 56 |
| 4 | Prefabs | `PrefabService` and `PrefabState` copy through `readComponent`, which now keeps unknown data. | `PrefabServiceTest` passes; `OwnershipPreservationTest` (prefab create and instantiate) |
| 5 to 8 | Terrain, materials, animations, audio | File formats untouched. Scenes that use them round-trip. | All engine and editor module suites pass (regression report); samples with terrain, materials and music are in the 56 |
| 9 | Gameplay profiles | Untouched. | The seven profile acceptance tests in engine-desktop and engine-render pass |
| 10 | Project export | Untouched. The exporter reads the marker's `startScene`, which format 2 keeps. | Export tests in editor-core pass; `TemplateGalleryProjectsTest` (export of every template): see the regression report |

## When the manifest is created

Only on a save in a project without one, and only when the person has agreed (`createManifest=true`).
- **Editor UI:** the desktop editor's Save does not yet ask. For a legacy project it saves exactly as before, writing only the scene file. The consent dialog is listed for A02.
- **New projects:** projects from the New Game gallery are still created in format 1. Creating them in format 2 is a follow-up, listed in the final report.

## Recoverable saves

`LocalProjectStore` writes every file of a save as `*.pending-<revision>`, then a journal (the commit point), then moves the files into place, then deletes the journal. On open:
- **A journal exists:** the save committed, so it is finished.
- **Pending files without a journal:** the save never committed, so they are deleted.

Either way, the marker, the manifest and the scenes belong to one revision. Tested both ways: `ProjectContractTest.shouldFinishACommittedSaveAndUndoAnUncommittedOne`.
