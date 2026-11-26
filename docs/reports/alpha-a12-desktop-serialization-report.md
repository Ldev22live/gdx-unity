# Alpha A12.1 and A12.2: Desktop serialization without data loss

**Date:** 2026-11-25. **Decision:** TD-106 (approved in principle 2026-11-25). **Status:** **Implemented.**
- The eight losses found in A11 are eliminated.
- All 56 shipped samples round-trip semantically.
- Output for files without unknown data is byte-identical to the previous serializer.

## 1. What was lost, and what was changed

**The defect:** `SceneSerializer` dropped:
- component types it did not know (`// Unknown component type policy: ignore`);
- top-level members other than the six it reads;
- fields of a known component that its serializer does not write.

**The change:** everything not understood is now kept as raw JSON beside what is understood, and written back.

| Level | Kept on | Written |
|---|---|---|
| Top-level scene members (the `scenario` block, extension keys) | `Scene.getPreservedFields()`; `Scene.getScenarioJson()` | After `active`, before `entities` |
| Entity members (for example `tags`) | `Entity.getPreservedFields()` | After `components` |
| Transform members (for example `pivot`) | `Entity.getPreservedTransformFields()` | Inside `transform`, after `scale` |
| Unknown component type | `PreservedComponent` (type id, `enabled`, raw `data`); inert, never instantiated | As read |
| Unknown component-entry members | `BaseComponent.getPreservedEntry()` | After `data` |
| Unknown fields of a known component's `data` | `BaseComponent.getPreservedData()`: the members the serializer did not write back when the component was read | Appended after the serializer's own fields, **except a key the serializer writes itself**, so an edited known field always wins |

**Storage format:**
- `PreservedFields` (`engine-scene`) keeps each member as its compact JSON text, so nested objects, arrays and explicit `null` survive.
- One libGDX trap was found and handled: `JsonValue.toJson()` returns a bare string value without quotes. `PreservedFields.toJson`/`quote` render strings, `null`, numbers and containers correctly.

**Related changes:**
- `readComponent` (used by duplication, prefabs and undo) returns a `PreservedComponent` instead of `null`, so those paths keep unknown components too. A component entry without `type` is refused with the entry text, not skipped.
- `OwnerComponent`, its serializer and `ScenarioValidator` (`engine-scene` `scenario` package):
  - ownership is by stable player UUID only (a name is refused);
  - an invalid reference is kept as written and reported, never cleared;
  - rule types outside the approved set are reported "not run" and never executed.
- Play still refuses components that truly cannot be saved; `PreservedComponent` is exempt because the scene serializer writes it (`PlayModeController.validateComponentSerializability`).

## 2. Semantic comparison (A12.2)

`SceneSemanticDiff` (`engine-scene`) reports MISSING, ADDED and CHANGED with paths.
- **Always applied:** member order is ignored; entities are matched by id and components by type and occurrence; numbers are compared at 32-bit float precision, because the engine stores scene numbers as floats.
- **Null, empty and missing are never treated as equal globally.**
- **The documented normalizations**, each found by scanning all 57 files and checked in the code named:

| Normalization | Component and field | Code that makes it true |
|---|---|---|
| Direction | `DirectionalLightComponent.direction{X,Y,Z}` | `DirectionalLightComponent.setDirection` normalises the vector |
| 16 bits | `BoxCollider2DComponent.maskBits`, `categoryBits` | the serializer casts to `short`: 65535 is written as −1 |
| 16 bits | `CircleCollider2DComponent.maskBits`, `categoryBits` | same cast |
| Absent means `null` | `SpriteAnimatorComponent.slice` | read with default `null`, written as `null` |

**Notes:**
- These four behaviours predate A12. None is data loss.
- A11's four empty `materialPath` differences disappeared: those values are now kept as residual fields and written back.

## 3. Evidence

| Check | Result |
|---|---|
| `SceneSerializerPreservationTest` (engine-scene, 7) | Unknown members at every level survive load and save; nested objects, mixed arrays and escaped strings; an adjacent known field edited (the unknown field survives); a known field never written twice; scenario exposure; a typeless entry and a name-as-owner refused |
| `ScenarioValidatorTest` (5), `SceneSemanticDiffTest` (4) | Closed rule set, references, duplicates, newer version "not run"; the diff's rules and normalizations |
| `ContractRoundTripTest` (engine-desktop, 4) | **All eight A11 losses kept** (each asserted by name); edits to known fields leave unknown data intact; **every shipped sample (56) semantically equal after load and save** |
| `OwnershipPreservationTest` (editor-core, 2) | Owner, an unknown component and an unknown field survive duplication, undo, redo, prefab creation and instantiation |
| Byte equality with the old serializer | The `HEAD` `SceneSerializer` compiled into a scratch folder and run on all 56 samples: **all 56 outputs byte-identical** to the new one |
| Mutation checks | Preserved scene members not written: `ContractRoundTripTest` 2 of 4 fail. The old serializer on the fixture: 8 losses reported. Both restored |
