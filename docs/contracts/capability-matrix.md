# Capability matrix (TD-106, approved in principle 2026-11-25)

Capability ids for `project-manifest.json` (`capabilities.required` and `optional`).
- **Desktop** is this repository's editor and game runtime.
- **Web** is `engine-babylon` (player and editor).
- Statuses describe **today** (2026-11-25, updated after A12), from [BACKEND_SUPPORT_MATRIX.md](../BACKEND_SUPPORT_MATRIX.md) and [alpha-a01-ux-audit.md](../reports/alpha-a01-ux-audit.md).
- **Read** means the client loads and plays it. **Edit** means it can change and save it.
- A target column is the Alpha proposal, subject to owner approval (roadmap A14).

| Capability id | What a project uses it for | Desktop today | Web today | Web target (Alpha, proposed) |
|---|---|---|---|---|
| `scene/1` | Scene JSON `schemaVersion` 1 | Read, edit | Read, edit (raw JSON kept) | Read, edit |
| `preserve-unknown/1` | Unknown components, fields and top-level keys kept on save | **Yes (A12):** `PreservedComponent`, preserved fields; all 56 samples round-trip | **Yes, tested (A12):** raw JSON, `contract.test.ts`, `interop.test.ts` | Yes |
| `project/1` | `project-manifest.json` | **Yes (A12):** `LocalProjectStore`, the editor's Open and Save | Read, check, new revision (`contract.ts`); no project UI yet (A13, A14) | Yes |
| `terrain/1` | `.terrain` heightmap and layers, read | Read | Read (M3.11) | Read |
| `terrain-edit/1` | Sculpt and paint `.terrain` | Edit (R3) | **No** (sculpts meshes only) | Edit: raise, lower, smooth, flatten, paint |
| `material/1` | `.material` | Read; assigned in Details. The Material Editor tab is not built (R2) | Read (M2.5) | Read; basic edit |
| `model/gltf` | glTF and GLB models | Read | Read (no model animations) | Read |
| `mesh-edit/1` | `.mesh` sources and derived models (R7a) | Edit | No | No: desktop hand-off |
| `sprite/2` | `.sprite` versions 1 and 2 | Read, edit (2D Studio) | Read | Read; desktop hand-off for editing |
| `vector/1` | `.vector` drawings | Edit | No (ignored) | No: desktop hand-off |
| `animation/1` | `.anim` clips | Read, edit | Read | Read |
| `graph-run/2` | Run `.graph` visual scripts | Read (interpreter) | Read (interpreter) | Read |
| `graph-edit/2` | Edit graphs | Edit | Edit (Visual Scripting tab) | Not required for Alpha (roadmap §3) |
| `java-script/1` | Java or Kotlin scripts (TD-019) | Read (compiled in the editor) | **No**: never in a browser | No |
| `song/1` | `.song` music | Read, edit | Partial: some instruments silent | Read |
| `prefab/1` | Prefab links (`PrefabInstance`) | Read, edit | Instances flattened; links kept as raw data | Keep links; no prefab editing |
| `atmosphere/1` | `AtmosphereComponent` (sky, fog, weather) | Read | **No (optional, owner 2026-11-25):** data kept, named as not drawn, default sky shown | Optional |
| `postprocess/1` | Post-processing (M6.2) | Read | No | No |
| `profile:platformer/2d` | Genre profile Platformer + 2D | Play | No (no profiles on the web) | Candidate |
| `profile:platformer/2.5d` | Platformer + 2.5D | Play | No | Candidate |
| `profile:platformer/3d` | Platformer + 3D | Play | No | Later |
| `profile:action-rpg/2.5d` | Action RPG + 2.5D | Play | No | Later |
| `profile:action-rpg/3d` | Action RPG + 3D | Play | No | **Candidate (proposed first)** |
| `profile:first-person-shooter/3d` | First-person Shooter + 3D | Play | No | Later |
| `profile:third-person-shooter/3d` | Third-person Shooter + 3D | Play | No | Later |
| `scenario/1` | The `scenario` block: players, objectives, rules ([schema](scenario.schema.json)) | **Data (A12):** kept, checked by `ScenarioValidator`; no editor UI (A04, A05) | **Data (A12):** kept | Yes (A14) |
| `scenario-run/1` | Running scenario rules in Play | No (A05) | No | Yes (A14) |
| `ownership/1` | `OwnerComponent` on entities | **Yes (A12):** saved, duplicated, in prefabs, checked; no editor UI (A04) | **Data (A12):** kept, not drawn | Yes (A14) |
| `physics2d/1` | Box2D bodies and colliders | Play | Partial (boxes and tile maps, no circles) | Partial |
| `physics3d/1` | Jolt bodies, colliders, character controller | Play | Play (Jolt in WebAssembly) | Play |

**How a client decides:**
- every `required` id it lacks → open read-only, naming the ids;
- every `optional` id it lacks → open editable, listing what it will not show or run.

On save it adds the ids its own edits introduced, and never removes ids another client wrote.
