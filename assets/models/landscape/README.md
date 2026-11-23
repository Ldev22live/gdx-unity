# Landscape models

Eight low-poly glTF 2.0 models for building 3D landscapes. Each is one `.gltf` file with its data embedded, in metres
(a Model component imports them at 100 scene units per metre), lit by vertex colours, with no textures.

| File | What | Size | Triangles |
|---|---|---|---|
| `terrain-hills.gltf` | Rolling 60 m x 60 m ground with a lake basin, grass, sand and rock coloured by height and slope, edges falling away | 60 x 60 m | 18,432 |
| `mountain.gltf` | A snow-capped ridged peak with a grassy foot | about 44 m wide, 26 m tall | 5,688 |
| `pine-tree.gltf` | Trunk and five stacked cones | about 6 m | 189 |
| `oak-tree.gltf` | Trunk and four lumpy canopy blobs | about 5.5 m | 1,307 |
| `rock.gltf` | Two lumpy stones | about 2 m | 640 |
| `bush.gltf` | Three small blobs | about 1.5 m | 960 |
| `cabin.gltf` | Log cabin with gabled roof, door, windows and chimney | 4.2 x 3.2 m | 66 |
| `lake.gltf` | A flat, see-through blue disc to lay in the terrain's basin | about 18 m | 40 |

### Desert, alpine, island and autumn sets

| Biome | Files |
|---|---|
| Desert canyon | `desert-terrain`, `mesa` (banded butte), `cactus`, `palm-tree`, `dry-bush`, `desert-rock`, `oasis` (water) |
| Alpine valley | `alpine-terrain`, `snow-pine`, `snow-rock`, `ice` (frozen lake); reuses `mountain` and `cabin` |
| Tropical island | `island-terrain`, `ocean` (a 68 m see-through square), `palm-tree`, `tropical-bush`, `hut`; reuses `rock` |
| Autumn forest | `autumn-terrain`, `autumn-oak-red`, `autumn-oak-orange`, `autumn-oak-yellow`, `autumn-pine`, `autumn-bush`; reuses `lake`, `cabin` and `rock` |

The scenes are [`landscape-3d.json`](../../samples/landscape-3d.json) (forest valley, uses the eight models above) and
`landscape-desert-3d.json`, `landscape-alpine-3d.json`, `landscape-island-3d.json` and `landscape-autumn-3d.json`, all in
`assets/samples/`. Both the desktop
editor and the web editor load these files (the web copy is made by `npm run sync-samples`).

## Regenerating

The forest valley's models and scene are generated, deterministically, by [`tools/make_landscape_models.py`](../../../tools/make_landscape_models.py)
(pure Python, no packages): `python tools/make_landscape_models.py` from the repository root rewrites those files and the
`landscape-3d.json` scene. The other four biomes come from [`tools/make_landscape_biomes.py`](../../../tools/make_landscape_biomes.py),
which builds on the first script and rewrites their models and scenes. Change the terrain function, the tree counts or the colours in the script and run it again; the trees and
rocks are placed on the terrain by the same height function that builds it.
