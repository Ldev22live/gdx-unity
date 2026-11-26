# OpenGameArt sample performance

Windows 11, Java 21.0.11, Intel Iris Xe. Each pass rendered 330 frames and measured 300 after warm-up with GPU completion. Before is the last environment-only pass with the same benchmark method. Values are from a single run in each condition.

| Scene | P50 before ? after | P95 before ? after | First-frame metric before ? after |
|---|---:|---:|---:|
| 3D Platformer | 2.48 ? 3.25 ms | 3.66 ? 5.77 ms | 524 ? 1,187 ms |
| Action RPG Valley | 5.52 ? 6.98 ms | 6.80 ? 12.11 ms | 1,065 ? 1,808 ms |
| Action RPG Isometric | 4.72 ? 4.75 ms | 6.14 ? 6.90 ms | 1,432 ? 1,112 ms |

The before results are in the [environment pass report](sample-game-performance-report.md). The 3D Platformer and Valley show higher steady-state and first-frame times in this comparison. The Valley draws 21,504 terrain triangles, the same as before. The highest measured frame was 43.03 ms in the Valley. These stationary startup routes do not measure sustained gameplay or animation blends.

Command used the existing `io.github.ldev22live.gdxunity.engine.desktop.PerformanceBaseline record build/oga/performance.tsv --runs 1 samples/platformer-profile-3d.json samples/action-rpg-valley-3d.json samples/action-rpg-iso-25d.json` with the Java 21 runtime classpath and compiled sample script. All three benchmark launches reported zero runtime errors.
