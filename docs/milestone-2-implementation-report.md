# Milestone 2 Implementation Report — Scene System

**Project**: GdxUnity  
**Date**: October 2026  
**Target Milestone**: Milestone 2 — Scene System (Entities, Components, Transforms, Serialization)  
**Status**: Complete  

---

## 1. Executive Summary

Milestone 2 establishes a complete, Unity-inspired Scene System in `engine-scene`, giving GdxUnity the capability to manage scenes, hierarchical entity trees, reusable components, lazy world matrix transforms, and versioned JSON scene persistence.

Key capabilities delivered in Milestone 2:
1. **Entity Model (`Entity`)**: Stable UUID identity, custom display names, enabled/active state hierarchy propagation, intrinsic `Transform`, and polymorphic component lookup.
2. **Component Lifecycle (`Component`, `BaseComponent`)**: Safe attachment, start, frame update, activation/deactivation hooks, and detachment safeguards.
3. **Transform Hierarchy (`Transform`)**: Local & world position, rotation, and scale representation using LibGDX math types; lazy world matrix calculation; dirty-flag propagation across entire descendant subtrees; cycle/self-parenting rejection; and reparenting with world-transform preservation.
4. **Scene Management (`Scene`, `SceneManager`)**: Entity registration, lookup by UUID/name, deferred mutation queue (preventing `ConcurrentModificationException` during update loops), and scene switching with safe disposal semantics.
5. **Versioned JSON Serialization (`SceneSerializer`)**: Persistent save/load mechanism using LibGDX's native `Json` utility with explicit `schemaVersion: 1`, two-pass hierarchy reconstruction, and a pluggable `ComponentSerializerRegistry`.
6. **Automated Test Suite**: 23 new JUnit 5 unit tests in `engine-scene` (bringing repository total to 44 passing unit tests with 0 failures) running headlessly without an active graphics context.

---

## 2. Architecture & Design Principles

### Strict Dependency Direction

Milestone 2 strictly preserves the project's dependency hierarchy:

```
engine-desktop
     │
     ▼
engine-render
     │
     ▼
engine-scene (M2 Scene System)
     │
     ▼
engine-core (M1 Lifecycle & Timing)
```

`engine-core` retains 0 dependencies on `engine-scene`. Scenes integrate into the runtime via frame delta updates passed to `Scene.update(deltaTime)` or through `SceneManager.update(deltaTime)`.

---

## 3. Component Details & Behavior

### 3.1. Entity Abstraction (`engine-scene`)

- **Identity**: Every entity is assigned an immutable `UUID` on creation that survives serialization round trips.
- **Transform**: Every entity has a mandatory, intrinsic `Transform` instance created on construction.
- **Active State in Hierarchy**: `isActiveInHierarchy()` returns true only if the entity itself is active AND all ancestor entities in its parent chain are active.
- **Component Storage**:
  - Components are indexed by their concrete class and all implemented interfaces / base classes up to `Component`.
  - Enables polymorphic queries (e.g. `entity.getComponent(Renderable.class)` or `entity.getComponents(Collider.class)`).
  - Cross-entity ownership is strictly prohibited; attaching a component already owned by another entity throws `IllegalStateException`.

### 3.2. Transform Hierarchy (`engine-scene`)

- **Representation**:
  - `localPosition`: `com.badlogic.gdx.math.Vector3`
  - `localRotation`: `com.badlogic.gdx.math.Quaternion`
  - `localScale`: `com.badlogic.gdx.math.Vector3` (defaults to `1, 1, 1`)
- **Lazy Evaluation**: `worldMatrix` is computed on-demand via `localMatrix = Translation * Rotation * Scale`, composed recursively with parent transforms.
- **Dirty Flag Propagation**: When local position, rotation, or scale changes, the dirty flag is recursively pushed down to all child transforms in $O(N)$ for the subtree. Once recalculated, it remains clean until the next mutation.
- **Cycle Prevention**: Calling `child.setParent(parent)` inspects ancestors with `parent.isDescendantOf(child)`. Self-parenting and ancestor cycles throw `IllegalArgumentException`.
- **World Transform Preservation**: Calling `setParent(newParent, true)` computes the inverse of the new parent's world matrix to calculate new local position, rotation, and scale values that keep the child in the exact same world-space position.

### 3.3. Scene Graph & Deferred Mutations (`engine-scene`)

- **Root vs Child Entities**: Entities with no parent are indexed in `rootEntities`.
- **Deferred Mutations**: If entities or components are created or removed during `scene.update(float deltaTime)`, they are queued in `pendingAdditions` and `pendingRemovals`. Mutations are flushed cleanly after the iteration completes.
- **Disposal**: Calling `scene.dispose()` recursively detaches all components, clears roots and lookups, and marks the scene as disposed.

### 3.4. Versioned JSON Serialization (`engine-scene`)

- **Schema Versioning**: Root JSON object includes `"schemaVersion": 1`. Future versions can provide upward migrations, while unsupported newer schemas are safely rejected.
- **Format**:
  ```json
  {
    "schemaVersion": 1,
    "name": "Level1",
    "active": true,
    "entities": [
      {
        "id": "e4b9...-...",
        "name": "Player",
        "active": true,
        "parentId": null,
        "transform": {
          "position": { "x": 10.0, "y": 20.0, "z": 0.0 },
          "rotation": { "x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0 },
          "scale": { "x": 1.0, "y": 1.0, "z": 1.0 }
        },
        "components": [
          {
            "type": "CustomComponent",
            "enabled": true,
            "data": { ... }
          }
        ]
      }
    ]
  }
  ```
- **Two-Pass Deserialization**:
  1. Pass 1: Parse entities, instantiate with their stable UUIDs, read transforms and components, and register in the scene.
  2. Pass 2: Reconstruct parent-child hierarchy using recorded `parentId` strings.
- **Validation**: Rejects malformed JSON, duplicate entity UUIDs in the document, and dangling parent references.

---

## 4. Test Verification Results

### Automated Tests Breakdown

Total tests across the workspace: **44 tests, 0 failures, 100% pass rate**.

| Module | Test Class | Tests | Status |
| :--- | :--- | :---: | :---: |
| `engine-core` | `EngineLifecycleTest` | 7 | ✅ Passed |
| `engine-core` | `FrameClockTest` | 6 | ✅ Passed |
| `engine-core` | `ResourceManagerTest` | 6 | ✅ Passed |
| `engine-render` | `PrimitiveRendererTest` | 2 | ✅ Passed |
| `engine-scene` | `TransformTest` | 8 | ✅ Passed |
| `engine-scene` | `EntityAndComponentTest` | 5 | ✅ Passed |
| `engine-scene` | `SceneTest` | 5 | ✅ Passed |
| `engine-scene` | `SceneSerializerTest` | 5 | ✅ Passed |

### Build Status
- `./gradlew.bat test` — **PASSED** (all 44 tests pass).
- `./gradlew.bat build` — **PASSED** (all modules compile, test, and package).

---

## 5. Milestone 2 Acceptance Criteria Checklist

| Acceptance Criteria | Status | Evidence |
| :--- | :---: | :--- |
| Scene creation and disposal working | ✅ Complete | Verified in `SceneTest` & `Scene.java` |
| Entity creation with stable UUID-based IDs | ✅ Complete | Verified in `EntityAndComponentTest` & `Entity.java` |
| Component attachment/detachment working | ✅ Complete | Verified in `EntityAndComponentTest` |
| Transform hierarchy with parent-child relationships | ✅ Complete | Verified in `TransformTest` & `Transform.java` |
| World transform calculation with dirty flag optimization | ✅ Complete | Verified in `TransformTest` (lazy update, dirty propagation) |
| Basic scene switching (load/unload scenes) | ✅ Complete | Verified in `SceneTest.shouldSupportSceneManagerSwitchingAndDisposal` |
| Versioned JSON save/load for scenes | ✅ Complete | Verified in `SceneSerializerTest` (schemaVersion: 1) |
| Stable IDs persist through serialization | ✅ Complete | Verified in `SceneSerializerTest.shouldSerializeAndDeserializeSceneRoundTrip` |
| Serialization tests for schema validation | ✅ Complete | Verified: future schema rejection, malformed syntax, duplicate IDs, missing parents |

---

## 6. Next Steps: Roadmap to Milestone 3

With Milestone 2 complete, the engine is prepared for **Milestone 3 — Basic Rendering & Camera**:
1. Implement 2D/3D camera abstraction and controller in `engine-render`.
2. Integrate entity `Transform` world matrices into rendering projection/view matrices.
3. Support primitive and sprite rendering driven by scene entities.
