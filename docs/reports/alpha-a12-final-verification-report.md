# Alpha A12: Final verification

**Date:** 2026-11-25. **Status:** **A12 acceptance criteria met in automated tests.**
- **Not human-validated:** the editor's Open and Save under the contract were exercised by tests driving the real `SceneFileController`, not by a person.
- **No commit:** nothing was committed (HEAD `b8bace5`).

## Acceptance criteria (directive §23)

| Criterion | Met? | Evidence |
|---|---|---|
| TD-106 approval documented | Yes | TD-106 status, conditions, rule set and implementation notes |
| Shared manifest implementation works | Yes | `ProjectContractTest` (9); web `contract.test.ts` |
| Legacy projects remain compatible | Yes | [backward compatibility](alpha-a12-backward-compatibility-report.md); 56 samples byte-identical |
| All eight identified desktop data losses eliminated | Yes | `ContractRoundTripTest` asserts each by name |
| Web preservation verified through automated tests | Yes | `contract.test.ts`, `interop.test.ts` |
| `OwnerComponent` survives serialization | Yes | Engine, editor (duplicate, prefab, undo) and interop tests |
| Scenario data survives serialization | Yes | Same |
| Required capability handling | Yes | Read-only, Save refused in the editor (`SceneFileControllerContractTest`) |
| Optional capability handling | Yes | Limited, editable, ids kept |
| Unsupported data survives supported editing | Yes | Desktop and web edit tests |
| Desktop-to-web round trips pass | Yes | Steps 1 → 2 → 3 → 4 |
| Web-to-desktop round trips pass | Yes | Steps r1 → r2 → r3 |
| Revision conflicts detected | Yes | Store, editor and web tests |
| Existing supported functionality operational | Yes | [regression](alpha-a12-regression-test-report.md): 2,584 Java and 532 web tests pass |
| Required regression tests pass | Yes | Same |
| No unresolved destructive serialization defects | **Yes, as far as tested:** the three pre-existing value normalizations are documented equivalences, not loss | [desktop serialization](alpha-a12-desktop-serialization-report.md) |

## Directive test list (§21), all automated

| # | Test | Where |
|---|---|---|
| 1 | Legacy loading | `ProjectContractTest` |
| 2 | Versioned loading | `ProjectContractTest`, `contract.test.ts` |
| 3 | Manifest generation | `ProjectContractTest` |
| 4 | Manifest validation | `ProjectContractTest`, `contract.test.ts` |
| 5 | Future version rejection | `ProjectContractTest`, `contract.test.ts` |
| 6 | Invalid paths | `ProjectContractTest`, `contract.test.ts` |
| 7, 8 | Asset hashes and missing assets | `ProjectContractTest`, `contract.test.ts` |
| 9 | Stable identity | `ProjectContractTest` |
| 10 | Save-as-copy identity | `ProjectContractTest`, `SceneFileControllerContractTest` |
| 11 to 14 | Unknown top-level keys, fields, components, nested data | `SceneSerializerPreservationTest`, `ContractRoundTripTest`, `contract.test.ts` |
| 15 | Scenario preservation | `ScenarioValidatorTest`, interop |
| 16 | `OwnerComponent` preservation | Interop tests |
| 17 | Duplication ownership | `OwnershipPreservationTest` |
| 18 | Prefab ownership | `OwnershipPreservationTest` |
| 19 | Required capability rejection | `ProjectContractTest`, `contract.test.ts` |
| 20 | Optional capability preservation | `ProjectContractTest` |
| 21 | Read-only save prevention | `ProjectContractTest`, `SceneFileControllerContractTest` |
| 22, 23 | Desktop and web semantic round trips | `ContractRoundTripTest`, `contract.test.ts` |
| 24 to 26 | Desktop-to-web, web-to-desktop, complete round trip | `CrossPlatformInteropTest`, `interop.test.ts` |
| 27 | Revision conflict | `ProjectContractTest`, `SceneFileControllerContractTest` |
| 28 | Save mine as a copy | `ProjectContractTest`, `SceneFileControllerContractTest` |
| 29 | Reload theirs | `ProjectContractTest`, `SceneFileControllerContractTest` |
| 30 | Interrupted save recovery | `ProjectContractTest` |
| 31 | Gameplay profile regressions | Profile acceptance tests pass |
| 32 | Export regressions | `TemplateGalleryProjectsTest` and the editor-core export tests pass |

## Known gaps (not blocking A12; listed for later milestones)

- **Untested branch:** the symbolic-link escape check could not run, because this Windows account cannot create links. The text rules are tested.
- **Terrain:** no `.terrain` file in the interop chain yet.
- **UI and project creation:**
  - no consent dialog for adding a manifest to a legacy project (A02, TD-107 point 9);
  - new projects are still created in format 1;
  - how Details shows a kept unknown component was not checked.
- **Web:** no project UI (A13, A14); no browser run.
- **Not in A12's scope:** the cloud store (A16) and the AI gateway (A18) need the UrConnex and UDX contracts.

## Next milestone

**A02, the World shell.** TD-107 is **Proposed**: it changes the approved UI M3b layout and needs the owner's approval before layout code.
