# OpenGameArt sample integration

Start HEAD `b8bace5`. End HEAD `b8bace5`.

| Milestone range | Accepted work and remaining gaps |
|---|---|
| M1?M3, audit, licenses and conversion | Reused the verified 54-scene inventory. Inspected six locally supplied asset groups plus the Fox Bases listing. Exported four self-contained GLBs and resized three licensed texture maps. The new files are hashed in the project manifest. Mansedj remains source only, and rockwall and Fox Bases were absent. |
| M4?M7, materials, platformer and RPG | Added the paver material on the 3D Platformer start and the isometric step. Detective Bunny uses authored animations under the original Jolt controller. The Valley keeps its blocking boulder collider and adds visible wildlife and rock outcrops. |
| M8?M9, sprite and editor integration | Fox Bases remains on hold under the current CC0 policy. The Content Browser shows the new model and thumbnail. The editor model inspector reports the imported clips and bones. |
| M10?M12, hardening and verification | Focused Java and Jolt tests, Windows desktop runtime captures, editor Play and all three exported launchers passed. The performance report records single-run timing and first-frame cost. See the test report for full-suite status and remaining interactive checks. |

[Source inventory](../assets/opengameart-inventory.md) ? [Provenance](../assets/THIRD_PARTY_ASSETS.md) ? [Conversion](../assets/asset-conversion.md) ? [Test and screenshots](../assets/test-report.md) ? [Performance](../assets/performance-report.md)

Diff review distinguishes this work from pre-existing modeling, browser and environment changes. Subsequent unrelated downloads under `assets/characters` were left with their owner. No Git commit, push or destructive operation was performed.
