# Contract v1 fixtures (TD-106, proposed)

Written by `python tools/make_contract_fixtures.py` (reproducible: fixed uuid5 ids; a second run writes identical bytes). Schemas: [`docs/contracts/`](../../docs/contracts/).

| Path | What |
|---|---|
| `valley-project/` | A small project in the proposed contract: `gdxunity.project` (format 2), `project-manifest.json` (hashes, capabilities, revision), `scenes/valley.json` (today's scene format plus a `scenario` block and `OwnerComponent`s), `materials/Meadow.material` |
| `valley-project/scenes/valley.json` | Holds **deliberately foreign data** a conforming client must keep on save: the component type `ExampleFutureComponent` (on Hero), the field `futureTerrainBlend` (on Ground's Mesh Primitive) and the top-level key `exampleFutureSetting` |
| `invalid/path-traversal.manifest.json` | An asset path with `..`: must be refused |
| `invalid/future-version.manifest.json` | `contractVersion` 2: must be refused, naming the number |

**More fixtures are planned for A12:** terrain (`.terrain`), a house with a derived mesh, a sprite, a prefab link and the profile variants. They are not here yet.
