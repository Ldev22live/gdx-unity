# Milestone 1 Implementation Report — Minimal Engine Runtime

**Project**: GdxUnity  
**Date**: October 2026  
**Target Milestone**: Milestone 1 — Minimal Engine Runtime  
**Status**: Complete  

---

## 1. Executive Summary

Milestone 1 transitions GdxUnity from an initial architectural scaffold into a functional, testable desktop game engine runtime.

Key accomplishments in this milestone:
1. **Engine Lifecycle & State Machine**: Established an explicit lifecycle model (`NEW`, `RUNNING`, `PAUSED`, `DISPOSING`, `DISPOSED`, `FAILED`) in `engine-core` managed by `EngineApplication` and observed via `EngineLifecycleListener`.
2. **Deterministic Frame Timing**: Introduced `FrameClock` and the `TimeProvider` interface to decouple frame delta calculations from LibGDX static state, sanitizing negative/infinite/NaN inputs and clamping delta spikes.
3. **Robust Resource Disposal**: Implemented `ResourceManager` to guarantee reverse registration order (LIFO) disposal, idempotency, duplicate suppression, and exception aggregation (`ResourceDisposalException`).
4. **Decoupled Primitive Rendering**: Created `PrimitiveRenderer` in `engine-render` using LibGDX `ShapeRenderer`, `OrthographicCamera`, and `FitViewport` to present an aspect-correct colored rectangle without distortion on window resizing.
5. **Desktop Runtime Wiring**: Integrated `DesktopLauncher` in `engine-desktop` to launch `EngineApplication` configured with `PrimitiveRenderer`.
6. **Automated Test Suite**: Added 21 JUnit 5 unit tests covering lifecycle transitions, deterministic clock logic, and resource ownership without requiring active graphical displays in CI.

---

## 2. Architectural Design & Boundaries

### Module Dependency Flow

The engine enforces strict unidirectional dependencies:

```
engine-desktop
     │
     ▼
engine-render
     │
     ▼
engine-scene (skeleton for M2)
     │
     ▼
engine-core
```

### Decoupled Core Lifecycle & Renderer

To preserve editor-runtime independence and enable headless unit testing, `engine-core` does not depend on LibGDX graphics structures or rendering implementations. The interaction is defined by the `EngineRenderer` contract:

```
┌──────────────────────────────────────────────┐
│                 engine-core                  │
│                                              │
│  EngineApplication (ApplicationAdapter)      │
│   ├── EngineState (State Machine)            │
│   ├── FrameClock (Delta Time Accumulator)    │
│   └── ResourceManager (LIFO Native Disposal) │
│                │                             │
│                ▼ uses                        │
│        «interface» EngineRenderer            │
└────────────────┼─────────────────────────────┘
                 │ implements
┌────────────────▼─────────────────────────────┐
│                engine-render                 │
│                                              │
│  PrimitiveRenderer                           │
│   ├── OrthographicCamera                     │
│   ├── FitViewport (Aspect-ratio preservation)│
│   └── ShapeRenderer (Draws cyan rectangle)   │
└──────────────────────────────────────────────┘
```

---

## 3. Detailed Component Breakdown

### 3.1. Engine Lifecycle (`engine-core`)

- **`EngineState`**:
  - `NEW`: Instantiated, waiting for initialization.
  - `RUNNING`: Active main loop executing frame updates and rendering.
  - `PAUSED`: Execution suspended (window backgrounded or minimized).
  - `DISPOSING`: Active cleanup of owned native resources.
  - `DISPOSED`: Terminal state after successful resource teardown.
  - `FAILED`: Unrecoverable exception occurred during initialization or render loop.
- **`EngineLifecycle`**: Public contract defining standard lifecycle operations: `initialize()`, `resize(width, height)`, `render()`, `pause()`, `resume()`, and `dispose()`.
- **`EngineApplication`**:
  - Bridges LibGDX `ApplicationAdapter` callbacks directly into the engine lifecycle contract.
  - Implements idempotent disposal: multiple calls to `dispose()` are safe no-ops.
  - Idempotent initialization: repeated calls while `RUNNING` are ignored cleanly.
  - Automatically attempts resource cleanup and transitions to `FAILED` if uncaught exceptions occur during initialization or rendering.

### 3.2. Frame Timing & Clock Abstraction (`engine-core`)

- **`TimeProvider`**: Functional interface (`getRawDeltaTime()`) allowing deterministic delta times to be injected in unit tests.
- **`LibGdxTimeProvider`**: Production implementation delegating to `com.badlogic.gdx.Gdx.graphics.getDeltaTime()`.
- **`FrameClock`**:
  - Rejects `Float.NaN`, `Float.POSITIVE_INFINITY`, `Float.NEGATIVE_INFINITY`, and negative deltas (sanitized to `0.0f`).
  - Clamps delta times to `maxDeltaTime` (default: `0.1f` seconds) to avoid runaway delta spikes (spiral of death) during long frames.
  - Tracks `deltaTime`, `totalElapsedTime`, and `frameCount`.

### 3.3. Resource Ownership & Disposal (`engine-core`)

- **`ResourceManager`**:
  - Registers objects implementing LibGDX's `Disposable` interface.
  - Enforces LIFO (Last-In, First-Out) disposal order to ensure child resources are cleaned up before parent containers.
  - Deduplicates registrations so a single resource is never registered or disposed twice.
  - Double-disposal protection: manager cleanup is idempotent.
  - Rejects new registrations after disposal with `IllegalStateException`.
  - Exception resilience: if a resource throws an exception during disposal, subsequent resources are still disposed. All caught errors are aggregated as suppressed exceptions under a `ResourceDisposalException`.

### 3.4. Rendering Foundation (`engine-render`)

- **`PrimitiveRenderer`**:
  - Implements `EngineRenderer`.
  - Sets up an `OrthographicCamera` and `FitViewport(640, 480)`.
  - Clears the display buffer with dark slate color (`0.12, 0.14, 0.18, 1.0`).
  - Draws a centered 200x200 cyan rectangle (`0.2, 0.7, 0.9, 1.0`).
  - Responds to window resizing by updating the viewport without stretching or distorting the primitive.
  - Can run in headless test environments by overriding the internal `createShapeRenderer()` factory method.

### 3.5. Desktop Launcher Integration (`engine-desktop`)

- **`DesktopLauncher`**:
  - Configures LWJGL3 application window (640x480, VSync enabled, refresh rate capped).
  - Constructs `EngineApplication` injected with `new PrimitiveRenderer()`.
  - Retains cross-platform JVM launcher workarounds in `StartupHelper`.

---

## 4. Test Suite & Verification Results

### Automated JUnit 5 Unit Tests

All 21 automated tests pass cleanly (`BUILD SUCCESSFUL`):

| Test Suite | Package | Tests | Failures | Status |
| :--- | :--- | :---: | :---: | :---: |
| `EngineLifecycleTest` | `io.github.ldev22live.gdxunity.engine.core` | 7 | 0 | ✅ Pass |
| `FrameClockTest` | `io.github.ldev22live.gdxunity.engine.core.time` | 6 | 0 | ✅ Pass |
| `ResourceManagerTest` | `io.github.ldev22live.gdxunity.engine.core.resource` | 6 | 0 | ✅ Pass |
| `PrimitiveRendererTest` | `io.github.ldev22live.gdxunity.engine.render` | 2 | 0 | ✅ Pass |

### Build Status

- `./gradlew.bat test` — PASSED (0 failures across all subprojects).
- `./gradlew.bat build` — PASSED (all 4 modules assembled, tested, and packaged into jars).

### Desktop Smoke Test Procedure

To verify desktop window presentation locally:
1. Run `./gradlew :engine-desktop:run`.
2. Verify a 640x480 window titled `"GdxUnity Engine"` appears.
3. Verify the dark slate background and centered bright cyan rectangle are visible.
4. Resize the window and confirm aspect-ratio preservation without distortion.
5. Close the window and confirm clean exit with code 0.

---

## 5. Legacy Starter Modules Audit

- Legacy directories `core/` and `lwjgl3/` remain untouched and unlinked in `settings.gradle`.
- Active modules (`engine-core`, `engine-scene`, `engine-render`, `engine-desktop`) contain zero dependencies on legacy directories.
- Legacy modules are ready for formal archival or removal in a subsequent maintenance pass.

---

## 6. Milestone 1 Acceptance Criteria Checklist

| Acceptance Criteria | Status | Implementation Evidence |
| :--- | :---: | :--- |
| Engine lifecycle interface defined (`create`, `resize`, `render`, `pause`, `resume`, `dispose`) | ✅ Complete | `EngineLifecycle`, `EngineState`, `EngineApplication` |
| Update loop with delta time implemented | ✅ Complete | `FrameClock`, `TimeProvider`, `LibGdxTimeProvider` |
| Resource disposal framework established | ✅ Complete | `ResourceManager`, `ResourceDisposalException` |
| Simple geometric object can be rendered | ✅ Complete | `PrimitiveRenderer` (centered cyan rectangle) |
| Window can be resized without crashing | ✅ Complete | Handled via `FitViewport` in `PrimitiveRenderer.resize()` |
| Automated tests cover foundational logic | ✅ Complete | 21 passing JUnit 5 tests |
| Desktop runtime launches and displays rendered content | ✅ Complete | `DesktopLauncher` wired to `EngineApplication(new PrimitiveRenderer())` |

---

## 7. Next Steps: Roadmap to Milestone 2

With Milestone 1 complete, the engine is ready for **Milestone 2 — Scene System**:
1. Implement `Entity` with UUID-based persistent identity.
2. Implement `Component` interface and core component storage.
3. Implement `Transform` hierarchy (parent-child matrices with dirty-flag propagation).
4. Implement `Scene` lifecycle management (entity activation, updates, disposal).
5. Implement versioned scene serialization and deserialization (JSON).
