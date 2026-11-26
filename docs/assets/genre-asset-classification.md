# Genre asset classification

Use project catalog records keyed by a project-relative path. Keep provenance in the existing third-party manifest and reference it by path. An asset can carry several genres without duplicating its bytes. Suggested fields are asset type, genres, art style, realism, species, rigged, clip names, texture dimensions, triangle count, license and provider. Derive imported status from the project index. Unknown fields should remain unknown and should not satisfy a specific filter.

| Genre | Priority asset roles |
|---|---|
| Platformer | Readable animated fox or rabbit, terrain, bridges, platforms, obstacles |
| Action RPG | Rigged humanoid lion or wolf, weapons, enemies, ruins, forest and town |
| Racing | Vehicles, track surfaces, barriers, scenery and engine audio |
| Strategy | Units, buildings, vegetation and resources visible from above |
| Puzzle | Distinct interactables, clear materials and deliberate lighting |
| Isometric RPG | Characters, terrain, buildings and readable interaction markers |

The desktop Content Browser reads `asset-classification.json` at the project root. Map project-relative paths to an object of facet names with arrays of lowercase slug values. For example, search `genre:action-rpg realism:realistic type:model` for a matching installed model. Combine filters with spaces and supply exact values for `resolution` and `triangles`. The browser finds assets across folders when searching. Its `imported:yes` facet denotes files currently present in the project. The provider tabs keep their existing online search and import workflow. Refresh reloads the classification file.

The installed catalog includes a starter selection from assets already shipped. The user-authored `license` field is for search. The runtime license manifest remains the redistribution evidence.
