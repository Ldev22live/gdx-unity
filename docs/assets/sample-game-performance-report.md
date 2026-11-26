# Sample game performance report

Windows 11, Intel Iris Xe Graphics. The desktop performance runner completed one run per scene with a GPU finish for each measured frame. Each run rendered 330 frames, discarded the first 30 and reported zero runtime errors.

| Scene | Mean ms | P50 ms | P95 ms | Max ms | Terrain triangles |
|---|---:|---:|---:|---:|---:|
| 2D Platformer | 2.18 | 1.97 | 3.56 | 8.23 | 0 |
| 2.5D Platformer Profile Terrain | 3.63 | 3.54 | 5.12 | 11.03 | 2,816 |
| Action RPG Valley | 6.06 | 6.00 | 7.09 | 17.01 | 21,504 |

Baseline command and exit code

```powershell
.\gradlew.bat :engine-desktop:perfBaseline '-PperfScenes=samples/platformer-2d.json,samples/platformer-profile-terrain-25d.json,samples/action-rpg-valley-3d.json' -PperfRuns=1
# Exit 0
```

The one-run baseline measures the startup camera route. It does not represent combat, additional characters, or the whole level.

## Before and after

All comparisons used Java 21 and the desktop performance runner on Windows Intel Iris Xe. Each cell uses one run of 300 measured frames with GPU completion. The unchanged 2D platformer serves as a run-to-run comparison.

| Scene | P50 before ? after | P95 before ? after | First-frame metric before ? after |
|---|---:|---:|---:|
| 2D Platformer | 1.97 ? 1.24 ms | 3.56 ? 1.87 ms | 356 ? 246 ms |
| Terrain Platformer 2.5D | 3.54 ? 2.49 ms | 5.12 ? 3.42 ms | 383 ? 803 ms |
| Action RPG Valley | 6.00 ? 5.52 ms | 7.09 ? 6.80 ms | 449 ? 1,065 ms |
| Action RPG Isometric | 4.01 ? 4.72 ms | 5.05 ? 6.14 ms | 561 ? 1,432 ms |
| Platformer 3D | 2.81 ? 2.48 ms | 4.61 ? 3.66 ms | 357 ? 524 ms |

The four refreshed scenes were below 7 ms P95 after startup. All four showed a higher first-frame metric after the material assignments. The unchanged control also shifted, so these single-run observations cannot establish a steady-state speedup or isolate the startup cost. Larger scenes, combat, and new rigs need their own benchmarks.

The after run used the same Gradle benchmark arguments as the baseline with `-PperfOut=build/perf/sample-asset-after.tsv` and exited 0. The isometric and 3D comparisons used the Java 21 desktop classpath to run `PerformanceBaseline record <output> --runs 1 --project <project> samples/action-rpg-iso-25d.json samples/platformer-profile-3d.json`. Both passes exited 0. The before project was reconstructed from the starting HEAD. All benchmark smoke runs reported zero runtime errors.
