# Milestone 5 Implementation Report: Play Mode, Scene Isolation, and Runtime Lifecycle

**Project:** GdxUnity  
**Milestone:** 5 — Play Mode and Scene Isolation  
**Date:** 2026-10-22  
**Status:** Complete  

---

## 1. Executive Summary

Milestone 5 introduces a Unity-inspired **Play / Pause / Stop** simulation workflow into **GdxUnity**. It guarantees strict runtime scene isolation so that runtime simulation, gameplay movements, entity spawning, or deletions can occur freely without mutating or corrupting the authored scene.

Key accomplishments:
1. **PlayModeController & State Machine:** Introduced `PlayModeController` in `editor-core` managing `STOPPED`, `PLAYING`, and `PAUSED` states with transition guards.
2. **Scene Cloning via Versioned Serializer:** On entering Play Mode, the authored scene is serialized to JSON via `SceneSerializer` (`schemaVersion: 1`) and deserialized into an independent runtime copy.
3. **Component Serializability Validation:** Before starting playback, all attached components are verified against the `ComponentSerializerRegistry`. If any component lacks a serializer, Play Mode is rejected with an informative status message.
4. **Authored Scene Invariant Preservation:** Runtime entity mutations, transform changes, hierarchy modifications, and deletions are completely isolated. Stopping execution disposes the runtime scene and seamlessly restores the authored scene and entity selection.
5. **Destructive Action Gating:** During Play and Pause modes, destructive authoring actions (`createEntity`, `deleteSelectedEntity`, `newScene`, `saveSceneToFile`, and `loadSceneFromFile`) are disabled and guarded to prevent accidental data loss or runtime mutation saves.
6. **Integrated Toolbar & Simulation Loop:** Added `Play`, `Pause`, `Stop`, and state indicator controls to `EditorToolbar`. `EditorApplication` drives `PlayModeController.update(deltaTime)` with delta time sanitization (handling NaN, infinity, negatives, and clamping to 100ms max).
7. **Automated Testing Suite:** Added 9 comprehensive headless unit tests in `editor-core` verifying all transitions, scene isolation, selection reconciliation, and delta clamping. Workspace total now stands at **74 unit tests, 0 failures, 100% passing**.

---

## 2. Architecture & Module Boundaries

### Dependency Graph

```
engine-desktop           editor-desktop
    │                         │
    │                    ┌────┴────────────┐
    │                    ▼                 ▼
    │               editor-core      engine-render
    │                    │                 │
    └───────────┬────────┘                 │
                ▼                          ▼
           engine-render ───────────► engine-scene
                                           │
                                           ▼
                                      engine-core
```

- Runtime modules (`engine-core`, `engine-scene`, `engine-render`, `engine-desktop`) have **zero dependencies** on editor modules.
- `editor-core` houses `PlayModeState` and `PlayModeController`, remaining 100% headless and testable without an OpenGL window.
- `editor-desktop` assembles the desktop UI controls, binds `EditorApplication`'s render loop to `PlayModeController.update()`, and disposes resources cleanly.

---

## 3. Play Mode State Model & Lifecycle

### State Transitions

```
   ┌────────────────────────────────────────────────────────┐
   │                                                        │
   ▼                                                        │
STOPPED  ───────( play() )───────►  PLAYING                 │
   ▲                                 │    ▲                 │
   │                                 │    │                 │
   │                            (pause) (resume)            │
   │                                 │    │                 │
   │                                 ▼    │                 │
   │                                PAUSED                  │
   │                                   │                    │
   └─────────────( stop() )────────────┴────────────────────┘
```

| Transition | Pre-condition | Action |
| :--- | :--- | :--- |
| `play()` | Must be `STOPPED`. | Validates all component serializers; clones authored scene via `SceneSerializer`; switches `editorState.activeScene` to runtime scene; reconciles selection to runtime entity; enters `PLAYING`. |
| `pause()` | Must be `PLAYING`. | Freezes simulation updates; keeps runtime scene active in viewport and Inspector; enters `PAUSED`. |
| `resume()` | Must be `PAUSED`. | Resumes simulation updates on next frame without reconstructing scene; enters `PLAYING`. |
| `stop()` | May be called from `PLAYING` or `PAUSED` (or repeatedly). | Disposes runtime scene; restores authored scene to `editorState.activeScene`; reconciles selection back to authored entity; enters `STOPPED`. |
| `update(dt)` | Must be `PLAYING`. | Sanitizes `dt` (rejects NaN/Infinity/< 0); clamps `dt <= 0.1f`; calls `runtimeScene.update(dt)`. |

---

## 4. Scene Cloning Strategy & Limitations

### Chosen Approach
- The authored scene is converted to a JSON string using `SceneSerializer.toJson(authoredScene)` and deserialized using `SceneSerializer.fromJson(json)`.
- **Entity Identity:** Cloned runtime entities preserve identical UUIDs. This enables seamless selection mapping and Inspector binding between authored and runtime instances.
- **Reference Independence:** While UUIDs match, in-memory Java object references are distinct. Mutations to runtime entity transforms (`setLocalPosition`, `setLocalRotation`, etc.) do not affect authored entities.
- **Validation:** `validateComponentSerializability(Scene)` inspects all components before cloning. If an unregistered component is found, playback is aborted with a user-facing error message.

### Limitations
1. **Component Serialization Required:** Any component attached to an entity must be registered in `ComponentSerializerRegistry`. Currently, `ShapeRendererComponentSerializer` is registered for 2D primitives.
2. **Native Asset Ownership:** Component serializers must not duplicate ownership of shared native resources (e.g. GPU textures). Textures remain managed by asset handles or resource managers, while renderable components simply reference them.

---

## 5. Resource Ownership and Disposal Guarantees

1. **Authored Scene:** Owned by `EditorApplication` / `EditorState`. It is never disposed during Play, Pause, or Stop cycles.
2. **Runtime Scene:** Exclusively owned by `PlayModeController`. On `stop()`, `runtimeScene.dispose()` is invoked, removing all entities, detaching components, and cleaning up deferred mutations before nulling the reference.
3. **Application Shutdown:** `EditorApplication.dispose()` stops `PlayModeController` first, ensuring any active runtime scene is torn down before disposing the `SceneRenderer2D`, `Stage`, and `Skin`.
4. **Idempotent Stop:** `stop()` can be safely called multiple times without throwing exceptions or corrupting state.

---

## 6. Files Created or Modified

| File | Change Type | Description |
| :--- | :---: | :--- |
| `engine-render/.../ShapeRendererComponentSerializer.java` | Created | Pluggable component serializer for 2D shape renderables. |
| `editor-core/.../play/PlayModeState.java` | Created | Enum defining `STOPPED`, `PLAYING`, and `PAUSED`. |
| `editor-core/.../play/PlayModeController.java` | Created | Core state machine managing scene cloning, simulation updates, and authored restoration. |
| `editor-core/.../EditorState.java` | Modified | Integrated `PlayModeController`; gated destructive authoring actions (`create`, `delete`, `new`, `save`, `load`). |
| `editor-desktop/.../ui/EditorToolbar.java` | Modified | Added Play, Pause, Stop buttons, mode indicator label, and state-driven button enabling/disabling. |
| `editor-desktop/.../EditorApplication.java` | Modified | Registered `ShapeRendererComponentSerializer`; hooked `PlayModeController.update()` into render loop; safeguarded disposal. |
| `editor-core/.../play/PlayModeControllerTest.java` | Created | 9 headless unit tests for Play Mode lifecycle and isolation. |
| `docs/ROADMAP.md` | Modified | Marked Milestone 5 complete with detailed criteria checklist. |
| `docs/ARCHITECTURE.md` | Modified | Documented Editor/Runtime Play Mode isolation boundaries and scene ownership. |
| `docs/TECHNICAL_DECISIONS.md` | Modified | Documented TD-015 (Play Mode Scene Cloning and State Isolation). |
| `docs/TEST_STRATEGY.md` | Modified | Added Play Mode headless testing guidelines. |

---

## 7. Verification and Testing

### Automated Headless Tests
Run from repository root:
```powershell
./gradlew.bat test
```
**Results:**
- `engine-core`: 19 tests passed
- `engine-scene`: 23 tests passed
- `engine-render`: 11 tests passed
- `editor-core`: 21 tests passed (12 Milestone 4 tests + 9 Milestone 5 tests)
- **Total: 74 tests passed, 0 failures, 100% success rate.**

### Full Gradle Build
```powershell
./gradlew.bat build
```
**Result:** `BUILD SUCCESSFUL in 8s` (all 6 modules compile, test, and package cleanly).

### Manual Desktop Smoke Test Procedure
To visually test Play Mode in an environment with display support:
```powershell
./gradlew.bat :editor-desktop:run
```

**Verification Checklist:**
1. Editor opens in `[STOPPED]` mode with Play enabled, Pause disabled, Stop disabled.
2. Click **▶ Play**:
   - Toolbar switches to `[PLAYING]`, Play is disabled, Pause and Stop are enabled.
   - `New Scene`, `Save Scene`, `Load Scene` buttons become disabled.
   - Status bar reports `Play mode started`.
3. In the Inspector, adjust Local Position of an entity: notice it updates the runtime instance.
4. Click **⏸ Pause**:
   - Toolbar switches to `[PAUSED]`, Play button changes to `▶ Resume`.
   - Simulation updates pause.
5. Click **▶ Resume**:
   - Simulation resumes.
6. Click **⏹ Stop**:
   - Toolbar restores to `[STOPPED]`.
   - The authored scene is restored; any positions altered during playback return to their authored state.
   - Selection re-anchors to the authored entity.
   - `New Scene`, `Save Scene`, and `Load Scene` are re-enabled.

---

## 8. Remaining Limitations & Recommended Milestone 6 Scope

1. **Asset Pipeline:** Milestone 6 should introduce asset references, asynchronous loading, and material/texture handling.
2. **Transform Gizmos:** Viewport visual manipulators for translation, rotation, and scaling.
3. **Custom Scripting / Logic Components:** Foundation for user-defined runtime behaviors that execute inside `onUpdate(deltaTime)`.
4. **Docking & Extensible Panels:** Multi-tab dockable layouts when the editor grows to include an Asset Browser and Game View.
