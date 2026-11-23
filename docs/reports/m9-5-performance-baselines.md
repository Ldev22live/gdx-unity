# M9.5: Performance Baselines

**Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart.

**Why this milestone:** after M9.4 it was the next startable row of R9. It has no dependency and no gate. Its done-when ("baselines
exist before R3 scatter and large terrain work") is time-bound: M3.6 (scatter) and M3.10 (landscape conversion) have not started.

## 1. Acceptance criteria

| # | Criterion (chart row M9.5) | Status | Evidence |
|---|---|---|---|
| 1 | Scene load | **Met** (desktop game runtime) | Three parts per scene: parsing the file, starting the session, creating the renderers and drawing the first frame (section 3) |
| 2 | Frame time | **Met** | Mean, median, 95th percentile and maximum, GPU finished, after 30 warm-up frames |
| 3 | Memory | **Partly met** | Java heap: peak while playing, and what stays after a garbage collection. **Not measured:** native memory (meshes, textures, Jolt), which Java cannot read portably |
| 4 | Audio latency | **Met as buffered latency** | The device's own buffers plus one output block, and the time to render each block. Not measured at the speakers. Measuring it found that the game runtime opened **no sound device at all**; fixed (section 4) |
| 5 | "Before and after each phase" | **Met: a repeatable record and compare** | `./gradlew :engine-desktop:perfBaseline` and `perfCompare` (exit 1 with a list on a regression); [`docs/PERFORMANCE_BASELINES.md`](../PERFORMANCE_BASELINES.md) says when and how |
| 6 | Done-when: "Baselines exist before R3 scatter and large terrain work" | **Met on Windows** | [`docs/performance/baseline-windows.tsv`](../performance/baseline-windows.tsv): 11 scenes, including both terrain samples and the 10 km large map of M3.8. Linux and macOS have none (no machine) |

## 2. What was built

- **Measurement in the game runtime (`engine-desktop`):**
  - `FrameTimeStats`: every measured frame's time in an array allocated once, so recording a frame allocates nothing; mean,
    nearest-rank percentiles and maximum.
  - `GameRenderer`: times from `initialize()` to the end of the first frame; per-frame times (replacing the M3.8 running
    sum, whose output line is unchanged); Java heap peak; on the last frame of a counted run, the sound output's statistics and the
    heap after `System.gc()`; the GPU's name.
  - `SfxAudioOutput`: the buffered latency (`AudioDevice.getLatency()` plus its 512-frame block) and the cost of each rendered
    block (`AudioOutputStats`).
  - `DesktopLauncher`: times parsing and session start; `GDXUNITY_PERF_REPORT=<file>` on a `--smoke-test` run switches the
    statistics on and appends a `PerformanceRecord` row.
- **`PerformanceRecord`:** one tab-separated row (16 columns, `-` for not measured), the median of several runs, and
  `regressionsSince`: a value more than 25 % worse *and* worse by more than a per-metric floor (0.5 ms for a frame, 50 ms for the first
  frame, 16 MiB of heap, ...). A sound device that no longer opens is a regression; the maximum frame and the heap peak are recorded,
  not compared.
- **`PerformanceBaseline` (`main`):** `record` plays each scene in a fresh JVM (same class path, through `CLASSPATH`), 3 runs, and
  keeps the medians; a scene that fails or takes over 5 minutes (a minimized window stops rendering) fails the recording.
  `compare` reads two files and lists regressions; a missing baseline file is an error, not a pass.
- **Gradle:** `:engine-desktop:perfBaseline` (`-PperfOut`, `-PperfRuns`, `-PperfScenes`) and `:engine-desktop:perfCompare`
  (`-PperfBaselineFile`, `-PperfCurrent`; defaults to `docs/performance/baseline-<os>.tsv`).
- **Docs:** [`docs/PERFORMANCE_BASELINES.md`](../PERFORMANCE_BASELINES.md) (when, how, every column, what is not measured) and the
  baseline file.

## 3. The baseline (Windows 11, Intel Iris Xe, 8 processors; medians of 3 runs)

| Scene | Load: parse + start + first frame (ms) | Frame median / 95th % / max (ms) | Terrain triangles | Heap retained (MiB) | Sound latency (ms) | Sound block max (ms, of 11.6) |
|---|---|---|---|---|---|---|
| `samples/platformer-2d.json` | 115 + 154 + 210 = **0.48 s** | 1.64 / 3.66 / 4.90 | 0 | 4.8 | 37.7 | 3.65 |
| `samples/tilemap-platformer-2d.json` | 95 + 86 + 350 = **0.53 s** | 1.70 / 2.99 / 3.80 | 0 | 4.5 | - | - |
| `samples/hopper-3d.json` | 188 + 142 + 437 = **0.77 s** | 2.01 / 4.06 / 5.41 | 0 | 5.2 | 37.7 | 0.27 |
| `samples/racing-3d.json` | 185 + 175 + 385 = **0.74 s** | 6.19 / 8.29 / 16.87 | 0 | 7.1 | 37.7 | 10.33 |
| `samples/landscape-3d.json` | 131 + 74 + 453 = **0.66 s** | 3.59 / 4.63 / 5.98 | 0 | 5.1 | - | - |
| `samples/materials-3d.json` | 145 + 73 + 510 = **0.73 s** | 4.29 / 5.47 / 18.82 | 0 | 5.4 | - | - |
| `samples/humanoid-animations-3d.json` | 195 + 112 + 427 = **0.73 s** | 3.12 / 5.96 / 13.58 | 0 | 5.0 | - | - |
| `samples/physics-mesh-3d.json` | 126 + 285 + 747 = **1.16 s** | 2.61 / 4.14 / 65.08 | 0 | 5.9 | - | - |
| `samples/terrain-3d.json` | 102 + 211 + 459 = **0.77 s** | 5.15 / 6.68 / 12.44 | 21,504 | 5.9 | - | - |
| `samples/action-rpg-valley-3d.json` | 145 + 369 + 432 = **0.95 s** | 4.92 / 6.29 / 8.86 | 21,504 | 6.1 | - | - |
| `scenes/Large.json` (M3.8, 1025 x 1025, 10 km) | 147 + 84 + 1,610 = **1.84 s** | 11.54 / **32.28** / 71.56 | 94,784 | 27.5 | - | - |

What the numbers say:

- **The large map stutters.** Its mean (14.9 ms) matches M3.8's recorded 14.4 ms, but one frame in twenty takes over 32 ms,
  two frames at 60 fps. The mean M3.8 reported hid this. This is the number M3.6 (scatter) has to keep, or improve.
- **Sound:** 37.7 ms of buffered latency everywhere (LWJGL3's device buffers plus one block). `racing-3d`'s engine synth renders a
  block in 1.0 ms on average, but its slowest block took 10.3 ms of the 11.6 ms it has: close to a drop-out. Songs (platformer,
  hopper) are cheap.
- **Loading is under 1.2 s** for every sample; the first frame (models, textures, terrain) is the largest part, and on the large map
  the first frame alone is 1.6 s.
- The Java heap stays small (5 to 28 MiB retained): the meshes and textures are native memory, which is not measured.

## 4. Found and fixed: the game runtime played no sound

`DesktopLauncher` starts the `GameSession` before it creates the window. `GameSession.bindAudio()` asked `Gdx.audio != null` to decide
whether to open a sound device, and at that moment there is no LibGDX application yet, so the answer was always no. **Every scene
played from the game runtime, `./gradlew :engine-desktop:run` and the packaged `ForgeroomGame.exe` was silent.** 17 bundled samples
have sound components. Editor Play builds its own `GameAudio` inside the running editor and was not affected, which is why nobody
noticed; M9.4 noted that no sound was listened to.

- **Fix:** `GameAudio.startOutput()` opens the device (once; `bind()` still does it itself when asked to, so the editor is
  unchanged), and `GameSession.startAudioOutput()` calls it; `GameRenderer.initialize()`, which runs once the window and its
  audio backend exist, calls that. A machine without a sound device still plays, with the reason logged.
- **Evidence:** `GameAudioOutputTest` (5): with no backend at start no device opens; once the renderer asks, it does, with the
  latency the device reports; once only; none for a scene without sound; a failing device is logged and the game keeps playing.
  With `startAudioOutput()` emptied, 3 of the 5 fail. In the real runtime, `racing-3d` recorded `audioLatencyMs` `-` (no device)
  with the call removed from `GameRenderer`, and 37.73 with it.
- `docs/BACKEND_SUPPORT_MATRIX.md`: the Audio Source, sound effects and music rows say so.

## 5. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-desktop:test --tests` FrameTimeStats, PerformanceRecord, GameAudioOutput, GameAudio*, GameSession | 0 | 5 + 9 + 5 + 16 + 19, no failures |
| The same `GameAudioOutputTest` with `startAudioOutput()` emptied (file restored and checked identical afterwards) | 1 | 3 of 5 fail, as they should |
| `perfBaseline -PperfRuns=1 -PperfScenes=samples/racing-3d.json` | 0 | One row; sound device open, 37.73 ms |
| The same with `session.startAudioOutput()` removed from `GameRenderer` (restored, checked identical) | 0 | Sound columns `-` |
| First try of that run | 1 (after 10 min) | The game window was minimized and LWJGL stops rendering then; I stopped my test process. Led to the runner's 5-minute limit per run |
| `./gradlew :engine-desktop:perfBaseline` (all 11 scenes) | stopped | **Stopped by Claude Code for low system memory** after 7 scenes (21 runs, all 330 of 330 frames, 0 errors); its leftover Java processes were stopped. Not restarted until the owner said to continue |
| `perfBaseline -PperfScenes=` the 3 remaining samples, then the large map (after `./gradlew --stop` freed memory) | 0, 0 | 12 runs, all 330 of 330 frames, 0 errors |
| The baseline file assembled from the three sessions' printed medians (same code, settings and machine, the same day) | | 11 rows; header matches `PerformanceRecord.HEADER` |
| `perfCompare`, the baseline against itself | 0 | "No regressions beyond 25 %." |
| `perfCompare` against a 3-scene file | 1 | Lists the 8 baseline scenes not measured. First try failed at configuration: `findProperty('perfBaseline')` returned the task of that name; the property is now `perfBaselineFile` |
| `./gradlew :engine-desktop:test` | 0 | **144** (was 125): +5 FrameTimeStats, +9 PerformanceRecord, +5 GameAudioOutput |
| `./gradlew :editor-desktop:test` | 0 | **163** (unchanged; editor Play's sound path) |
| `./gradlew :engine-web:test` | 0 | **9** (unchanged) |

The full `./gradlew test --continue` was not run: the host has stopped it for memory twice before; the modules this change touches
were run one at a time. The Babylon suite was not run: no graph node, component type or scene format changed.

## 6. Diff review

- **New:** `FrameTimeStats`, `AudioOutputStats`, `PerformanceRecord`, `PerformanceBaseline` (main code);
  `FrameTimeStatsTest`, `PerformanceRecordTest`, `GameAudioOutputTest`; `docs/PERFORMANCE_BASELINES.md`,
  `docs/performance/baseline-windows.tsv`, this report.
- **Changed:** `GameRenderer`, `GameSession`, `GameAudio`, `SfxAudioOutput`, `DesktopLauncher` (its `com.badlogic.gdx.Files` import is
  now `Files.FileType`, since `java.nio.file.Files` is used), `engine-desktop/build.gradle` (two tasks), `BACKEND_SUPPORT_MATRIX.md`
  (three notes), chart, FEATURE_TEST_MATRIX, INDEX, AGENTS.md (two command lines).
- **Pre-existing uncommitted changes** in AGENTS.md, the chart, the matrix and INDEX were kept; only lines were added or the M9.5 row
  replaced.
- **Outside the repository:** temporary report files of each run (deleted by the runner), a trial file in `%TEMP%\claude-m95`
  (deleted), and the session's scratch folder. No crash log was written.

## 7. Known limitations and next steps

- **Native memory, sound at the speakers and underruns** are not measured (see the doc).
- **Only the desktop game runtime.** The editor, the Babylon player and the TeaVM build have no baseline; a Babylon one would need a
  browser timing harness.
- **Linux and macOS:** no baseline (no machine). The commands are the same; each OS records its own file.
- **Noise:** three runs per scene on a laptop with other programs open. The 25 % tolerance and the floors are a first guess; widen
  them if `perfCompare` flags changes that do not reproduce.
- **The packaged game's sound** was not re-checked after the fix, and **nobody listened** to it.
- **Follow-ups for the owner:** the large map's 95th percentile (32 ms) is a target for M3.6 and M3.8; `racing-3d`'s slowest engine
  block (10.3 of 11.6 ms) is close to dropping out.
- **Next by phase order:** M9.6 (profiler panel, depends on M9.5) and the audits MC.1, MP.1 and MT.8 are startable.

## 8. Git

No commit, push, stash, reset or other Git operation was performed.
