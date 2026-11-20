# M9.3: Text-Friendly Scenes

**Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart. [TD-093](../TECHNICAL_DECISIONS.md) records the layout for the owner's review, because it changes the bytes of every scene and prefab file the editor saves (not their content or schema).

**Why this milestone:** after M6.4 it was the next startable row. It has no dependency and no gate. M6.1 and M6.3 need a decision record first, M2.3 needs approval, M8.1 needs a person listening, and M9.1 needs a TD.

## 1. Acceptance criteria

| # | Criterion (chart row M9.3) | Status | Evidence |
|---|---|---|---|
| 1 | Scenes with meaningful version-control diffs | **Met for files the editor has saved** | One fixed layout, `SceneTextFormat`, used by `SceneSerializer.toJson` (the editor's Save and Play's snapshot) and `PrefabDocument.toJson`. Saving twice gives the same bytes on all 43 bundled samples (`SceneTextDiffTest`) |
| 2 | Done-when: "A small edit gives a small diff" | **Met** | On every bundled sample, once saved: a move, a rename or a switched-off component changes 1 line; a new entity adds only its 25 lines; a deleted entity removes only its own lines; one painted tile changes 1 line (it used to change a 1,858-character line). A real `git diff --no-index` of `hopper-3d` with one entity moved and renamed: `1 file changed, 2 insertions(+), 2 deletions(-)` (section 3) |
| 3 | (Limit) The first save of a bundled file | **Not small for some files; recorded** | The bundled files were not rewritten (section 7). The first save of each, measured with a line diff, is in section 4: most change 4 to 90 lines, a few change most of their lines once |

## 2. What was built

- **`engine-scene`, `serialization/SceneTextFormat`:**
  - `format(String)` lays out JSON by fixed rules:
    - two spaces per level, one member per line, `"key": value`;
    - `{}` and `[]` when empty, `\n` line ends, and a final line break;
    - plain-value arrays: up to 16 on one line, longer ones 16 to a line;
    - every number, string and literal copied exactly as written.
  - It refuses text that is not one JSON value and names the character position.
  - `format(JsonValue)` writes a parsed tree, keeping each number's text as it was read.
  - `newWriter(Writer)` is a `JsonWriter` that keeps a whole float's point. LibGDX's own writer turns `1.0` into `1`; a test pins that behaviour, so the reason for this writer is visible if LibGDX ever changes.
- **`SceneSerializer`:**
  - `toJson` writes through `newWriter` and `SceneTextFormat`, in place of LibGDX's pretty printer;
  - the header order is `schemaVersion`, `name`, `gameMode`, `gameGenre`, `active`, the order most bundled files had;
  - `entityToJsonValue` keeps a float's point too.
- **`editor-core` prefabs:**
  - `PrefabDocument.toJson` uses the same layout (the old 100-column printer and its constant are gone);
  - `PrefabState.copy` keeps each number's read text.
- **Tests:**
  - new: `SceneTextFormatTest` (10), `SceneTextDiffTest` (4, all bundled samples), `PrefabDocumentTest` (2, all bundled prefabs written back unchanged).
- **Reading is unchanged.** Old layouts, tabs and CRLF all still load.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| A temporary probe test (deleted afterwards) saving every sample with the old printer | 0 | Saving twice was already stable, but the first save rewrote nearly every line: `hopper-3d` 1,546 changed lines, `racing-3d` 17,932, counted as `diff` lines (removed plus added) |
| `./gradlew :engine-scene:test --tests "*SceneTextFormatTest"` | 1, then 0 | The first compile failed on an ambiguous `format(null)` once the tree overload existed; fixed with a cast. Then 10 of 10 |
| `./gradlew :engine-desktop:test --tests "*SceneTextDiffTest"` | 1, then 0 | The first run failed on the test's own expectation for a new entity (section 5). Then 4 of 4 |
| `./gradlew :editor-core:test --tests "*PrefabDocumentTest" --tests "*PrefabServiceTest"` | 1, then 0 | Failed first: prefabs lost the point of `0.0` (section 5). Then 2 + 12 |
| `git diff --no-index --stat engine-desktop/build/m9-3/hopper-saved.json engine-desktop/build/m9-3/hopper-moved.json` | 1 (files differ) | `1 file changed, 2 insertions(+), 2 deletions(-)`: the name line and the `x` line. This reads two files outside Git's index and changes nothing in the repository |
| `./gradlew :engine-scene:test` | 0 | **222** (was 212) |
| `./gradlew :engine-render:test` | 0 | **199** |
| `./gradlew :engine-desktop:test` | 0 | **121** (was 117) |
| `./gradlew :editor-core:test` | 0 | **533**, 1 skipped (was 531) |
| `./gradlew :editor-sculpt:test` | 0 | **56** |
| `./gradlew :editor-terrain:test` | 0 | **41** |
| `./gradlew :editor-desktop:test` | 0 | **163** |

These seven are every module that uses `SceneSerializer` or the prefab files. The full `./gradlew test --continue` was not run, because the host stopped it twice before for low memory.

## 4. The first save of each bundled sample

The files on disk compared with what the editor now writes for them, as a line diff (`engine-desktop/build/m9-3/first-save.tsv`). The causes:
- **Format, in files written by other tools or printers:**
  - three 2D samples are tab-indented (`pixel-adventure-2d`, `sprite-25d`, `tilemap-platformer-2d`);
  - four are written with `1` for whole floats (`humanoid-animations-3d` and the three skeleton samples);
  - `sprite-animation-2d` has small objects written on one line.
- **Values the engine stores differently:**
  - doubles longer than a float keeps, as in `racing-3d` and the landscapes (`1001.0249675906655` becomes `1001.02496`);
  - sun directions, which are normalized on load (`-0.4, -1.0, -0.6` becomes `-0.324..., -0.811..., -0.487...`).

Every such value reads back the same, so the scene does not change, only its text, once.

| File | Lines | Removed | Added |
|---|---|---|---|
| `action-rpg-iso-25d.json` | 691 | 43 | 44 |
| `action-rpg-valley-3d.json` | 385 | 17 | 18 |
| `animation-samples-3d.json` | 442 | 10 | 10 |
| `arena-fps-3d.json` | 3191 | 24 | 25 |
| `blockworld-3d.json` | 431 | 16 | 17 |
| `breakout-2d.json` | 2238 | 66 | 66 |
| `character-creator-3d.json` | 328 | 4 | 4 |
| `character-playground-3d.json` | 1107 | 68 | 69 |
| `collector-3d.json` | 1072 | 26 | 26 |
| `depth-compositing-3d.json` | 337 | 67 | 67 |
| `fighter-25d.json` | 1764 | 24 | 25 |
| `grapple-gorge-25d.json` | 2050 | 13 | 13 |
| `hopper-3d.json` | 774 | 16 | 16 |
| `humanoid-animations-3d.json` | 5028 | 1840 | 1840 |
| `landscape-3d.json` | 2834 | 425 | 425 |
| `landscape-alpine-3d.json` | 2057 | 298 | 298 |
| `landscape-autumn-3d.json` | 3019 | 462 | 462 |
| `landscape-desert-3d.json` | 2612 | 398 | 398 |
| `landscape-island-3d.json` | 2427 | 368 | 368 |
| `materials-3d.json` | 624 | 4 | 5 |
| `obby-3d.json` | 3326 | 18 | 19 |
| `physics-joints-3d.json` | 1194 | 62 | 62 |
| `physics-mesh-3d.json` | 787 | 40 | 40 |
| `physics-sandbox-2d.json` | 1556 | 44 | 44 |
| `physics-sandbox-3d.json` | 1609 | 83 | 84 |
| `pixel-adventure-2d.json` | 695 | 693 | 756 |
| `platformer-2d.json` | 1090 | 38 | 38 |
| `platformer-profile-25d.json` | 660 | 38 | 39 |
| `puzzle-3d.json` | 540 | 22 | 23 |
| `racing-3d.json` | 8968 | 590 | 591 |
| `ruins-runner-25d.json` | 2247 | 27 | 27 |
| `runner-3d.json` | 1342 | 23 | 23 |
| `sculpted-rock-3d.json` | 210 | 25 | 25 |
| `shooter-2d.json` | 2085 | 55 | 55 |
| `skeleton-tentacle-3d.json` | 639 | 227 | 227 |
| `skeleton-walk-3d.json` | 1144 | 412 | 412 |
| `skeleton-wave-3d.json` | 1144 | 412 | 412 |
| `sprite-25d.json` | 976 | 974 | 1037 |
| `sprite-animation-2d.json` | 146 | 19 | 107 |
| `terrain-3d.json` | 302 | 5 | 6 |
| `three-heroes-25d.json` | 1424 | 13 | 13 |
| `tilemap-platformer-2d.json` | 310 | 308 | 341 |
| `visual-scripting-3d.json` | 314 | 54 | 54 |

## 5. Failures found and fixed

- **The scene came out unquoted.** `new Json()` writes its own names in LibGDX's minimal form, ignoring the writer's output type. The serializer now asks for `JsonWriter.OutputType.json`.
- **Whole floats lost their point.** LibGDX's writer drops it, both for values and for a parsed tree. That would have changed 36 bundled files on their first save. It is fixed by `newWriter`, by `format(JsonValue)` and by `PrefabState.copy` keeping the read text.
- **One wrong test expectation.** The test expected a new entity to show as a changed line plus its block, but a line diff shows the block inserted before the previous entity's closing brace: 25 added, 0 removed. The test now expects that, which is the smaller diff.
- **One compile error** in a test (ambiguous `format(null)`), fixed with casts.

## 6. Diff review

- **New:** `SceneTextFormat`, the three test classes, this report and TD-093.
- **Changed:**
  - `SceneSerializer`: `toJson`, the header order and `entityToJsonValue`;
  - `PrefabDocument`: `toJson`, and the removed printer constant;
  - `PrefabState`: `copy` keeps number text;
  - docs: TD-093, chart (M9.3 row and R9 overview), FEATURE_TEST_MATRIX, INDEX.
- **Pre-existing uncommitted changes.** `SceneSerializer` already carried earlier milestones' uncommitted lines (the runtime-only component refusal and the `gameGenre` reading). They were kept; only the lines listed here were changed.
- **Not changed:** no bundled scene, prefab or other asset file. The probe test was created and deleted, and its output folder removed.
- **Outside the repository:** nothing.

## 7. Known limitations and next steps

- **Bundled files not normalized.** Rewriting the 43 samples once would make every first save small. This milestone did not do it: it would touch files that carry uncommitted work (for example `terrain-3d.json`, and the untracked action RPG samples), and it is a large asset-only change for the owner to approve. The samples built by tests (`GDXUNITY_WRITE_SAMPLES=1`) come out in the new layout when regenerated.
- **Other file types keep their own printers:** graphs, animation clips, materials, terrain metadata, songs and sprites. They are candidates for the same layout.
- **Entity order.** Entities are in creation order, so reparenting does not move an entity's block; a new entity is always last. Hierarchy order would read better but would move blocks when parents change.
- **Merges.** No merge driver or conflict tool. Each entity is its own block, so edits to different entities should merge in Git without conflict; this was not tested.
- **Manually Not Verified:** File > Save in the running editor, then `git diff` on a project. The save path is the tested `SceneSerializer.toJson`.
- **Next by phase order:**
  - unblocked: M9.4 (backend support matrix) and M9.5 (performance baselines), and the audits MC.1, MP.1 and MT.8;
  - M6.1 and M6.3 need decision records, and M2.3 needs approval.

## 8. Git

No commit, push, stash, reset or other Git operation was performed. `git diff --no-index` was used read-only, on two files under `engine-desktop/build`.
