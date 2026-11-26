# Sample game assets M1?M3

Starting HEAD `b8bace5`. Ending HEAD `b8bace5`.

| Acceptance criterion | Status and evidence |
|---|---|
| Verify every shipped sample and gallery entry in the repository | Met for static inventory. The visual audit reconciles 54 scene files with 54 gallery entries and records visual and gameplay gaps. |
| Discover a small OpenGameArt shortlist | Met. The source audit records CC0 lion, rabbit, rock material, forest and audio candidates and explains source and fit limitations. |
| Record verified licenses for new imports | Pending acquisition. The OpenGameArt listings supply initial license evidence. TD-094 requires a person's download, and the upgrade manifest lists the already-shipped materials reused by the environment pass. |
| Establish performance baseline | Met for three priority scenes on Windows Intel Iris Xe. Each had 300 measured frames and zero runtime errors. Other scenes have no new baseline. |
| Establish full automated baseline | Executed with one pre-existing failure and one skip. The verification report includes the command, exit code and module counts. |

- [Sample visual audit](../assets/sample-game-visual-audit.md)
- [OpenGameArt source audit](../assets/opengameart-source-audit.md)
- [Asset license manifest](../assets/asset-license-manifest.json)
- [Baseline performance](../assets/sample-game-performance-report.md)
- [Verification and test counts](../assets/sample-game-verification-report.md)

No newly acquired binary assets. The repository already contained uncommitted documentation, modeling and navigation changes, which were preserved. Diff review found no Git staging, commits, pushes, or destructive Git operations.
