# Phase 14 — Prefabs

**Decision:** TD-027 (Proposed) · **Roadmap:** TD-026 · **Unreal equivalent:** Blueprint actor classes, without the graph
**Status (2026-10-29):** Implemented and integrated; visual check pending (deferred by the project owner).

## Goal

Build something once — a lamp post, a crate with a light inside — save it as a prefab, place it many times,
tweak single instances, and push improvements back to every copy.

## What exists

| Capability | How | Undo |
|---|---|---|
| Create Prefab | Edit menu or Outliner right-click on an entity. Saves it and its children to `assets/prefabs/<Name>.prefab` (never overwrites; `Name 1.prefab` …) and turns the original into an instance. The prefab root sits at its own origin. | Unlinks; the file stays |
| Place | Drag a `.prefab` from the Content Browser into the scene view; the instance root lands at the drop point and is selected. | One step |
| Overrides | Any property that differs from the prefab. The Details panel shows a prefab bar (asset path, override count) and marks overridden component sections with a Revert button. Prefab entities are blue in the Outliner. The instance root's name, position and rotation are its own, never overrides. | — |
| Apply | Writes the instance into the prefab file; other instances in the scene update and keep their own overrides. Children added to the instance join the prefab; prefab children deleted from it leave the prefab. | Restores scene and file |
| Revert | Whole instance: prefab values back, deleted prefab children restored, added children removed. Per component: only that component. | One step |
| Unpack | Plain entities that no longer follow the prefab. | One step |
| Save / open | Overrides are stored with the scene. Opening a scene updates its instances to the current prefab files: changed properties, added and removed prefab entities. A missing prefab leaves the instance as saved and the status bar names it. | — |

## Design notes

- File format: the scene entity format plus `kind: "prefab"` and `rootId` (`PrefabDocument`).
- Links: `PrefabInstanceComponent` (engine-scene, serialized as `PrefabInstance`).
- Comparison and merge: `PrefabState` flattens an entity into paths such as `transform/scale/x` and
  `component/MeshPrimitiveComponent#0/data/colorR`; a differing `component/<type>#<n>` path means the component was
  added or removed.
- Undo: `SceneSnapshotCommand` records every entity and component object before and after and puts the same
  objects back, so earlier commands in the history keep working.

## Known limitations

- No nested prefabs or prefab variants; creating a prefab from a tree that contains instances replaces their links.
- No prefab editing mode: change a prefab by editing an instance and applying.
- Duplicating an instance produces plain entities.
- Prefab files edited outside the editor are picked up when a scene is opened, not while it is open.
- Override marks are per component section, not per field.
- Deleting an instance root with Delete unparents its children (existing Delete behaviour).

## Tests

`PrefabServiceTest` (12), `PrefabActionsTest` (2), `PrefabInstanceComponentSerializerTest` (2).
`./gradlew test --continue` → exit 0, 362 tests, 0 failures (editor-core 250, editor-desktop 14, engine-core 27,
engine-render 27, engine-scene 44). Before Phase 14: 346.

## Manual checklist (when visual testing resumes)

1. Create → Box; add a child entity with a Point Light; select the box; Edit → Create Prefab. Status bar names
   `prefabs/Box.prefab`; the Content Browser lists it with a PREFAB badge (Refresh if needed); both entities turn blue.
2. Drag the prefab into the scene view twice: two instances appear at the drop points.
3. On one instance change the box colour: the Details prefab bar shows "1 overridden property", the Mesh Primitive
   section is marked "(overridden)".
4. On the other instance change the box size, click Apply: the first instance takes the new size and keeps its colour.
   Ctrl+Z: both return, and the prefab file is restored.
5. Revert on the coloured instance: colour returns to the prefab's.
6. Save the scene, apply another change from a new instance in a new scene, reopen the first scene: the instances
   show the change and keep their overrides.
7. Unpack an instance: its entities are no longer blue; Apply/Revert are disabled for it.
