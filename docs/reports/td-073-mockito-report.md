# TD-073 Report: Mockito for Unit Tests, and the First Whole-Repo Run

Record: [TD-073](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The working tree already held the uncommitted work of TD-069 to TD-072; nothing was committed, staged or reverted.

The request was "fix whats broken", with JUnit and Mockito for unit testing (an earlier message also suggested Jasmine or Karma for the frontend). Nothing was reported broken; I looked for breakage with the checks below and found none.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | JUnit for unit testing | **Already met** | JUnit Jupiter 5.10 is in every module's `build.gradle`; no change |
| 2 | Mockito available for unit tests | **Met** | `mockitoVersion=5.14.2` in `gradle.properties`; `testImplementation` in six modules; the whole-repo run compiled and ran with it |
| 3 | A test that uses it and would fail without the behavior | **Met** | `GraphHostMockTest` (3 tests) |
| 4 | Jasmine or Karma for the frontend | **Not done, by decision** | TD-073: Vitest (425 tests) already does this job; Karma is deprecated; a second runner would split the tests |
| 5 | Look for what is broken | **Done; nothing found by automated checks** | table below. Breakage in the running app (Graph tab, converted samples, web player in a browser) is **Manually Not Verified (Headless Environment)** |
| 6 | Docs, matrix, report | **Met** | this file, TD-073 record, `FEATURE_TEST_MATRIX.md`, `INDEX.md` |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-audio:test --continue` | 0 | 0 failures. The 2 `BundledDemoSongTest` failures noted in earlier reports did **not** reproduce |
| `./gradlew :engine-visualscript:test --tests "*GraphHostMockTest"` (first run) | 1 | 2 of 3 passed. The third failed on my own check: `verifyNoMoreInteractions` also saw the interpreter's `nodeExecuted` calls on the host. Replaced with a check that nothing was logged |
| `./gradlew test --continue --max-workers=1` (whole repo, background) | 0 | all 20 modules, 0 failures, 0 errors; per-module counts below. First complete whole-repo run of the TD-069 to TD-072 work |
| `npx tsc --noEmit` in `engine-babylon` | 0 | no type errors |
| `npx vitest run` in `engine-babylon` | 0 | 35 files, 425 tests, 0 failures |

Per-module counts from the XML reports of the whole-repo run (failures and errors 0 in every module): editor-animation 17, editor-audio 55, editor-character 10, editor-core 385 (1 skipped, reason not examined), editor-desktop 84, editor-sculpt 56, editor-sprite 51, editor-visualscript 89, engine-animation 32, engine-audio 189, engine-character 19, engine-core 35, engine-desktop 92, engine-physics 53, engine-render 119, engine-scene 149, engine-sculpt 81, engine-sprite 44, **engine-visualscript 86 (was 83: +3 `GraphHostMockTest`)**, engine-web 9. Web: 425 (unchanged).

Pre-existing versus new: no failure was found, so none needed sorting. The one skipped `editor-core` test pre-dates this work.

## Changes

- `gradle.properties` (`mockitoVersion`), `build.gradle` of `engine-scene`, `engine-visualscript`, `editor-core`, `editor-visualscript`, `engine-desktop`, `editor-desktop` (one `testImplementation` line each).
- New test: `engine-visualscript/src/test/.../GraphHostMockTest.java`.
- Docs: `TECHNICAL_DECISIONS.md` (TD-073 record; the TD-071 note "13 of 15" corrected to 12), `FEATURE_TEST_MATRIX.md`, `INDEX.md`, this report.

## Known limitations and next steps

- Only `engine-visualscript` has a Mockito test; the other five modules have the dependency unused. No existing fake was converted.
- The numbering: the number TD-073 was first proposed for a variables panel that was not approved; that proposal needs a later number.
- Nobody has used the Graph tab changes of TD-069 to TD-072 or played the converted Collector and Runner scenes. Only the automated checks above were run.
- Open question from the user, not started: more instruments for the audio editor (more synth presets, more drums, new synthesis methods, or a bundled SoundFont). Options were listed in chat and need a choice and approval.

No commit, push or other Git write was performed.
