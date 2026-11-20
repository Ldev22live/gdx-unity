# UI M8: Regression Tests, Performance Checks and Documentation

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `0469ceb` (nothing committed)

Milestone **M8**, the last of the brief "ForgeRoom: Full-Map UI, Tool Drawers and Open Asset APIs" (TD-094, TD-095).

**Status: the brief's acceptance list is met in automated tests and in observed editor runs.** The full regression run is not complete: it was stopped for low memory (§3).

What M8 added:
- an end-to-end regression test of the asset journey;
- a guard test for the "no Age of Empires" criterion;
- performance measurements of the editor, the search and the game runtime;
- a module-by-module test run (stopped for low memory after 7 of 23 modules, §3);
- a user guide, [`docs/ASSET_LIBRARY_GUIDE.md`](../ASSET_LIBRARY_GUIDE.md).

**Not done:** the clicks of a person. The brief's whole journey is cases A to J of [the E2E test plan](../19-nov-test-plan.md) for the owner's run. Until then the brief is **Implemented and Integrated, not Validated** (AGENTS.md §9).

## 1. The brief's acceptance criteria

| # | Criterion (brief, verbatim) | Result | Evidence |
|---|---|---|---|
| 1 | ForgeRoom opens with a visible isometric terrain world. | **Met** (seen) | **The default:** a fresh install opens 2.5D and 3D scenes in the Isometric view, framed to the whole world (UI M2, tested). **Seen** on `landscape-3d` in the packaged editor (UI M6) and here: [full-map-profiled.png](ui-m8/full-map-profiled.png). |
| 2 | No Age of Empires UI or screenshots remain. | **Met**, now guarded | **Never present:** there was none to remove (UI M1). **New guard:** `NoForeignGameArtTest` reads every shipped module's sources and every file under `assets/`. Text is checked for the name, and every other file by its file name. **Proved:** it failed on a planted `assets/mutation-check.txt`, which was then removed. |
| 3 | Tools have independent drawers. | **Met** (seen) | Nine drawers (`EditorDrawers`, UI M3): each opens and closes alone, is resizable, and is remembered. Tested in UI M3. The screenshots show Assets and Scripts open side by side. |
| 4 | The map remains the primary focus. | **Met** (seen) | Drawers are closed by default. With the Asset Library and Profiler both open at 1920 × 991, the map still fills most of the window: [download-profiled.png](ui-m8/download-profiled.png). |
| 5 | Users can search Poly Haven and ambientCG. | **Met** (live) | Poly Haven: 522 models and 870 textures (UI M5). ambientCG: 2,042 materials (UI M6). Both seen live in the editor. |
| 6 | Users can download and import compatible CC0 assets. | **Met** (live) | **Checks:** MD5 where given; SHA-256 recorded; size, free space, safe paths and formats, with nothing imported silently (UI M4 to M6). **Live:** downloads from both providers. **Kenney:** pack import tested; a real Kenney zip waits for the owner's download (robots.txt). |
| 7 | Imported assets can be placed on terrain. | **Met** (seen) | Place in world stands a model on the ground in one undo step, and Apply to selection puts a material on a shape (UI M7). **Seen** on the valley: the crate at height 73.4. |
| 8 | Scene save/reload works. | **Met** in tests | `AssetLibraryJourneyTest` saves the scene with the placed model and the material to a file, reopens it, and finds both, with their path, height and material. File > Save in the running editor is case J4 of the test plan. |
| 9 | Existing editor functionality remains intact. | **Met** in the tests run; **partly unchecked** | Every suite that ran passes (§3), but the module-by-module run was stopped for low memory after 7 of 23 modules, and the other 13 (none touched by this brief) were not run. The journey test ends by undoing every step back to the world as it was, byte for byte. The game runtime's performance is within noise of its baseline (§2). |

### `AssetLibraryJourneyTest` (new, `editor-terrain`)

One run through the editor's own operations, with recorded provider answers, so it is offline and repeatable:

1. A raised level is built, and the test checks that nothing has gone online yet.
2. A Kenney pack zip with a CC0 licence is imported; its FBX is listed as left out.
3. A Poly Haven search ("crate"): the PNG preview address, 2K first, and an MD5-checked download.
4. An ambientCG search ("gravel"): the zip is unpacked, the `.blend` left out, and the material reads back.
5. The model is placed standing on the ground, and the material is applied to a new box.
6. All three manifest entries are present and every file's SHA-256 matches the disk.
7. The scene is saved to a file and reopened.
8. Three undos return the world exactly to its state before the assets.

It passed on its first run.

## 2. Performance checks

### Editor frame cost

Measured with the Profiler: `GDXUNITY_EDITOR_PROFILER=1` on `landscape-3d.json`, at 1920 × 991, in the isometric full-map view. The figures are the last 300 frames of each run, with GPU timing included.

| Case | Average | Slowest | Editor UI draw calls | Screenshot |
|---|---|---|---|---|
| Map only, no drawers | 3.45 ms | 48.2 ms | 106 | [full-map-profiled.png](ui-m8/full-map-profiled.png) |
| Poly Haven tab open, 42 "rock" results listed (a word matches inside others too: "Rockingchair 01") | 3.02 ms | 34.5 ms | 136 | [library-open-profiled.png](ui-m8/library-open-profiled.png) |
| ambientCG download of 32.8 MB, finishing inside the window | 3.13 ms | **106.9 ms** | 134 | [download-profiled.png](ui-m8/download-profiled.png) |

**Findings:**
- **An open library costs little:** about 30 more UI draw calls, with no change in the average frame.
- **A download does not slow drawing:** it runs on a worker thread and the average stays at about 3 ms.
- **One frame of about 107 ms in the download run.** It is most likely the Content Browser rescanning the whole project on the render thread when the download completes; that is the existing `assetCatalog.scan`. It was not isolated. Moving the rescan off the render thread would remove it. It is listed below, not fixed, because it is the existing scanner's behaviour.
- **Fixed on the way:** my M5 testing aid opened the Assets drawer only when nothing at all was open at the bottom. With the Profiler open, the first measurement had the library off screen, which the screenshot showed. The aid now checks the Asset Library itself.

### Search over the real catalogues

Measured offline from the editor's own cache. That is the catalogue's normal path after the first fetch: 8 queries, 500 rounds after warming up, on this machine.

| Catalogue | Assets | Load (worker thread) | Search median | Search 95th percentile | Search max |
|---|---|---|---|---|---|
| Poly Haven models | 522 | 27.8 ms | 0.46 ms | 1.09 ms | 1.55 ms |
| ambientCG materials | 2,042 | 86.3 ms | 1.54 ms | 2.40 ms | 3.06 ms |

Searching runs on the render thread and stays well inside a frame.

### Game runtime against the recorded baseline

Run with `./gradlew :engine-desktop:perfBaseline`, then `perfCompare` against `docs/performance/baseline-windows.tsv` (M9.5). It covers 11 scenes, medians of 3 runs.

| Run | `perfCompare` | What it flagged (over 25 %) |
|---|---|---|
| 1, straight after three Gradle test suites (daemons alive) | exit 1 | racing-3d frame mean +38 %, 95th percentile +77 %; landscape-3d frame mean +36 %, 95th percentile +91 %; load and first-frame times of 3 scenes |
| 2, Gradle daemons stopped, nothing else running | exit 1 | platformer-2d start +43 %; tilemap parse +33 %; materials-3d start +35 % and first frame +65 % |

**Reading:** the two runs flag different things.

- **Frame times are within the line in run 2.** Run 1's frame-time regressions are gone there: racing-3d's median is 5.92 ms against a baseline of 6.19, landscape-3d's 3.78 against 3.59. No scene's frame median is over the 25 % line in run 2.
- **Load and first-frame times are noise at this size.** They are single timings that swing that much between runs; materials-3d's first frame was 546 ms in run 1 and 840 ms in run 2.
- **Nothing in the game runtime changed** in this brief except genre profiles, which these scenes do not use.

So no regression is shown. **To watch:** action-rpg-valley's median is up 19 %, humanoid's 18 % and terrain-3d's 14 %, all under the line. The baseline file was not changed. Both runs are kept: [perf-run-1.tsv](ui-m8/perf-run-1.tsv) and [perf-run-2.tsv](ui-m8/perf-run-2.tsv).

## 3. Full regression run

Every module in `settings.gradle` was run with `./gradlew :<module>:cleanTest :<module>:test`, one at a time; a full parallel run had been killed for low memory before. Counts are from the XML reports.

**The run was stopped by the system for low memory after 7 of the 23 modules.** It was not a test failure. Per the session rules it was not restarted unasked.

| Module | Exit | Tests | Note |
|---|---|---|---|
| `engine-core` | 0 | 51 | as at UI M4 |
| `engine-scene` | 0 | 232 | |
| `engine-render` | 0 | 203 | |
| `engine-visualscript` | 0 | 93 | |
| `engine-animation` | 0 | 32 | |
| `engine-desktop` | 0 | 153 | |
| `engine-web` | 0 | 9 | |
| `engine-sculpt`, `engine-audio`, `engine-physics`, `engine-character`, `engine-sprite`, `engine-material`, `engine-terrain`, `editor-visualscript`, `editor-animation`, `editor-sculpt`, `editor-audio`, `editor-character`, `editor-sprite` | — | not run | stopped (memory); none of their code changed in this brief |
| `editor-core` | 0 | 590 (1 skipped) | full run at the end of UI M7, before M8 |
| `editor-terrain` | 0 | 48 at UI M7, plus `AssetLibraryJourneyTest` (1, passing) | the new test was run on its own |
| `editor-desktop` | 0 | 187 at UI M7, plus `NoForeignGameArtTest` (1, passing) | the new test and the aid fix were run on their own; the aid fix was also exercised by the two profiled editor runs |

**Still to do:**
- the 13 modules not run;
- a full run of `editor-terrain` and `editor-desktop` with the M8 tests;
- rebuilding the package with M7 and M8.

These wait for the owner's go-ahead while memory is short.

## 4. Documentation

**New:** [`docs/ASSET_LIBRARY_GUIDE.md`](../ASSET_LIBRARY_GUIDE.md). It covers:
- the World screen, the isometric view and the drawers;
- the Asset Library's four tabs, searching, downloading, placing and applying, and Kenney import;
- licences and the manifest, online and offline;
- what is not supported, and the testing aids.

**Updated:**
- `INDEX.md`, `FEATURE_TEST_MATRIX.md` and both milestone charts;
- AGENTS.md's testing aids, earlier in M5 to M7.

The UI M1 to M8 reports together are the design record.

## 5. Known limitations

**Open items:**
- **The rescan hitch:** one long frame (about 107 ms) when a download completes, from the project rescan on the render thread.
- **Not Validated:** no person has done the journey yet; it is tonight's test plan, cases A to J.
- **Kenney:** no real Kenney archive has been imported; a person must download one.

**Left out of the brief's scope as built:**
- **ambientCG:** "category filtering" is a type filter fixed to materials. The API has no category field, and its other types are formats ForgeRoom does not read.
- **Materials:** ambientCG metalness and opacity maps are not used.
- **Previews:** shown for the chosen asset only, not in the result list.
- **Downloads:** no resuming.

## 6. Diff review and Git

**New:**
- tests: `AssetLibraryJourneyTest` (`editor-terrain`) and `NoForeignGameArtTest` (`editor-desktop`);
- docs: `docs/ASSET_LIBRARY_GUIDE.md`, this report and `docs/reports/ui-m8/`.

**Changed:**
- `EditorApplication`: the testing aid fix, one condition;
- the documents listed in §4.

The shipped `assets/` was not touched; the planted check file was removed, and `git status` shows no `assets/mutation-check.txt`.

**Git:** no commit, push or other Git operation was performed.
