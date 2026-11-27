# A15 Cross-platform authoring journey

## 27 November 2026 continuation

The desktop-first journey passes at file and scenario-runtime level. The web sculpts the ground, places a crate owned by Player 2 and changes a rule. Desktop reopens the result, extrudes the wall and saves a unique model. Web reopens that revision, checks that only the model reference changed, then supplies a trigger contact to the scenario runtime and observes victory. The fixture chain is in [interop](../../fixtures/contract-v1/interop/). The test injects the contact directly. It supplies no evidence of rendered appearance, keyboard movement or collision detection in either editor.

The handoff exposed two save defects. Desktop used the previous scene to discover assets while hashing the new scene, which omitted the edited model from the manifest. It also treated embedded glTF data as a filesystem path on Windows. The store now inventories staged scene files and skips embedded glTF URIs. The fixture builder includes the Kenney wall's external texture. The original model remains on disk and is compared byte for byte on reopen. Binary fixture comparisons now check the texture and GLB bytes.

| Check | Result |
|---|---|
| Web creates the second revision | `UPDATE_INTEROP=1 node node_modules/vitest/vitest.mjs run test/journey.test.ts -t "step j2"` in engine-babylon, exit 0, one passed |
| Desktop creates the third revision | `GDXUNITY_UPDATE_INTEROP=1 gradlew.bat --offline --daemon :editor-desktop:test --tests "*JourneyInteropTest.stepJ3*"`, exit 0, one passed after fixing the missing texture, manifest inventory and embedded URI handling |
| Full web suite, no fixture update | `node node_modules/vitest/vitest.mjs run --maxWorkers=1` in engine-babylon, exit 0, 567 tests across 50 files |
| Initial full Gradle run | `gradlew.bat test --continue`, exit 1. The desktop suite reported three failures and its binary report collided with a concurrent focused run. The initial desktop XML count is unavailable. |
| Reverse journey | Existing web-created project round trip covers desktop edits and reopening on web. A full reverse gameplay and geometry walkthrough is outstanding. |
| Human visual acceptance | Manually Not Verified (Headless Environment). Both running-editor journeys remain open. |
| Cloud limits and cancellation | Pending the cloud store in A16/A21. |
| Source texture inventory | The fixture carries the GLB's external PNG. The manifest scanner follows URI references in textual glTF only, so the source GLB's PNG has no independent manifest hash. |

Starting HEAD was `85ccc0a`. The working tree already contained changes across the desktop, web, fixtures, roadmap and milestone reports, including the initial journey tests and first desktop revision. No commit, push or destructive Git operation was performed.

### Regression investigation

The first isolated full Gradle rerun (exit 1) produced complete XML for 2,669 tests, including one skipped test. Both A15 desktop steps passed. Two desktop tests failed. WebPlacedPrefabsTest found zero linked prefab roots because the web palette wrote the Java class name instead of the serialized type ID `PrefabInstance`. The palette and its web-generated scene fixture now use the registered type ID. SampleInventoryTest found two stale entries, one for web test coverage and one for the journey terrain's source. Only those two generated rows changed.

## 26 November 2026 baseline

**Partly met. The two full A15 journeys are blocked by A14** (web scenario authoring, still open). The browser cannot yet edit terrain, place objects with ownership or change rules, so "create on desktop → edit in the web → back to desktop → Play in the web" cannot be walked end to end.

**Done today:** the automated desktop↔web round trip (the A12 interop chain) now carries the A04/A05 scenario data through both clients. The desktop edits the scenario with the same Players and Rules editors the World tabs use. The web reopens the result with every edit intact and nothing else changed. The shared fixture gained a real Spawn Point (see the A05 report).

The starting HEAD was `85ccc0a`, with A04–A07 uncommitted in the working tree. The owner asked to continue with A15 directly; the roadmap lists A14 as a dependency, and that dependency is recorded here, not waived.

## Audit against the roadmap

| A15 requirement | Where it stands | Evidence |
|---|---|---|
| Journey: desktop project → web edits (terrain, object, rule) → desktop (house geometry) → web Play | **Blocked by A14.** The file-level part works today: the web moves and renames objects (step 2), and the desktop edits ownership, lighting, a player's name and a rule's timer (step 3). Terrain and rule editing in the web, and web Play of rules and profiles, are A14 work. | `CrossPlatformInteropTest`, `interop.test.ts` |
| Reverse journey starting on the web | File level met: the web creates a project with a player and ownership, the desktop opens it, edits and saves, and the web reopens it (steps r1–r3). The full reverse journey is blocked by A14 for the same reasons. | Same |
| Preserve source assets, unknown data, profile metadata and references | Met at file level at every step: `ExampleFutureComponent`, `futureTerrainBlend`, `exampleFutureSetting`, the scenario block, ownership and asset hashes. **New:** the scenario's players and rules survive both clients, and the web's check against the original fixture now expects exactly six edits: four entity edits and two scenario edits. | `interop.test.ts` step 4 |
| Offline editing | Desktop: met (`LocalProjectStore` needs no network). Web offline editing is A14/A16. | A12 reports |
| Explicit conflict resolution; no last-write-wins | Met: a stale save gets `Conflict`, with "Save mine as a copy" and "Reload theirs", in the store, the desktop editor and the web. | `ProjectContractTest`, `SceneFileControllerContractTest` (A12.11) |
| Incompatible versions | Met: a future contract version is refused with its number; a newer scenario block is read-only (A04, A05). | `ProjectContractTest`, `contract.test.ts`, `ScenarioEditorTest`, `RulesEditorTest` |
| Missing assets | Met: hashes are checked on open and a missing file is reported. | `ProjectContractTest`, `contract.test.ts` |
| Interrupted save and recovery | Met locally: a journalled save is rolled forward on the next open. | `ProjectContractTest` (interrupted save test) |
| Cancelled upload, large asset limits | Not applicable until there is a cloud store: A16 (projects) and A21 (asset storage, added today). | |
| Semantic project equality on both engines | Met at file level (`SceneSemanticDiff` on the desktop, `compareScenes` on the web). | Interop chain |
| Visual and runtime behaviour on both engines | **Blocked:** the web player runs no genre profiles and no rules yet (A14; `BACKEND_SUPPORT_MATRIX.md`). | |
| Documented support matrix | Met and updated: `BACKEND_SUPPORT_MATRIX.md` has a row for scenario rules (desktop runs them; the web keeps them). | |

## Changes

- `editor-desktop/.../CrossPlatformInteropTest` step 3 now edits the scenario through `ScenarioEditor.renamePlayer` (Player 2 → "Raiders") and `RulesEditor.setTimer` (Welcome, 2 → 3 s), checks that the scenario still validates, and expects four changed paths.
- `engine-babylon/test/interop.test.ts` step 4 checks the renamed player, the rule name and the new timer, and expects `scenario.players[1].name` and `scenario.rules[0].when.seconds` among the differences from the original fixture.
- `fixtures/contract-v1/interop/3-desktop` was regenerated with the update flag, then verified without it.

## Test evidence

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_INTEROP=1 gradlew :editor-desktop:test --tests "*CrossPlatformInteropTest"` | 0 | 3 tests, 0 failures; `3-desktop` rewritten |
| `npx vitest run` (`engine-babylon`, full suite) | 0 | 42 files, 532 tests passed |
| `gradlew :editor-desktop:test --tests "*CrossPlatformInteropTest"` (no update flag) | 0 | 3 tests, 0 failures; the committed folders equal what the steps produce |

## Next

- A14 (web scenario authoring and playable preview) unblocks the full journeys. It starts with the A13 web World shell, which is the owner-approved order in the roadmap.
- Human gate: both journeys walked by a person once A14 lands.

No commit, push or destructive Git operation was performed.
