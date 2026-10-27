# Phase 0 Current State Assessment

**Project**: GdxUnity  
**Date**: October 2024 (Initial Baseline) / Current  
**Target Milestone**: Milestone 0 — Foundation and Architecture  
**Status**: Milestone 0 In Progress / Nearing Completion  

---

## 1. Executive Summary

The **GdxUnity** project is an ambitious initiative to build an extensible, Unity-inspired game engine and development environment in Java using the LibGDX framework. 

The project originated from a template generated with `gdx-liftoff`, which was originally configured for Java 8 with a monolithic 2D sprite demonstration and coupled dependencies (Fleks ECS, KTX ecosystem, Box2D, FreeType, etc.). 

During Phase 0 (Milestone 0: Foundation and Architecture), the repository has undergone foundational restructuring:
- **Java 21 LTS** target configuration established across all subprojects.
- **De-bloated dependency set**: Fleks ECS, KTX libraries, controllers, box2d, and freetype removed to establish a minimal, controllable baseline.
- **Modular architecture introduced**: The monolithic structure was split into four dedicated modules (`engine-core`, `engine-scene`, `engine-render`, `engine-desktop`), unlinking legacy starter modules (`core`, `lwjgl3`).
- **Comprehensive documentation suite created** in `docs/` covering vision, architecture, roadmap, technical decisions, testing strategy, and ADR standards.
- **Build verification**: `./gradlew build` builds and packages successfully on modern JDK toolchains.

This document serves as the formal audit of the project state at Phase 0, bridging the initial scaffold into Milestone 1 (Minimal Engine Runtime).

---

## 2. Documentation Lookup & Audit (`docs/`)

A review of the files inside `docs/` indicates that the foundational governance and design documentation has been established:

| Document | Purpose & Key Highlights |
| :--- | :--- |
| **`PROJECT_VISION.md`** | Defines the long-term vision: Unity-style editor experience (Scene View, Hierarchy, Inspector, Asset Browser, Play/Pause/Stop), entity/component model, versioned scene serialization, multi-game support, and strict runtime/editor independence. Outlines non-goals (e.g. no premature full ECS, desktop-first). |
| **`ARCHITECTURE.md`** | Specifies modular layering and strict dependency flow (`engine-desktop` → `engine-render` → `engine-scene` → `engine-core`). Specifies runtime-editor isolation, single authoritative transform model, explicit resource disposal rules, and forward planning for editor modules (`editor-core`, `editor-desktop`). |
| **`ROADMAP.md`** | Defines 7 core milestones: <br>• **M0**: Foundation & Architecture (Current)<br>• **M1**: Minimal Engine Runtime<br>• **M2**: Scene System (Entities, Components, Transforms, Serialization)<br>• **M3**: Basic Rendering & Camera<br>• **M4**: First Editor Prototype<br>• **M5**: Editor/Runtime Integration<br>• **M6**: Asset Pipeline & Materials<br>Followed by physics, animation, audio, and packaging. |
| **`TECHNICAL_DECISIONS.md`** | Documents initial technical decisions:<br>• **TD-001**: Java 21 Compatibility<br>• **TD-002**: Dependency Removal (Fleks, KTX, etc.)<br>• **TD-003**: Multi-Module Structure<br>• **TD-004**: Package Naming (`io.github.ldev22live.gdxunity.engine.*`)<br>• **TD-005**: Test Framework (JUnit 5.10.0)<br>• **TD-006**: LibGDX 1.14.2 & LWJGL3 3.4.1 constraints. |
| **`TEST_STRATEGY.md`** | Formulates testing levels (Unit, Integration, Smoke), test conventions (`should{Behavior}When{Condition}`), coverage targets (80%+ core logic, 60%+ rendering), and milestone gate requirements. |
| **`adr/0000-adr-template.md`** | Standard template for Architecture Decision Records. |

---

## 3. Module & Directory Structure Analysis

### Active Modules (Included in `settings.gradle`)

```
gdx-unity/
├── engine-core/       # Base lifecycle interfaces, application services, logging
│   └── src/main/java/io/github/ldev22live/gdxunity/engine/core/
│       └── EngineApplication.java
├── engine-scene/      # Entities, components, transform hierarchy, scene graph (skeleton)
│   ├── src/main/java/ (empty)
│   └── src/test/java/ (empty)
├── engine-render/     # Rendering abstractions, LibGDX backend adapters, camera (skeleton)
│   ├── src/main/java/ (empty)
│   └── src/test/java/ (empty)
└── engine-desktop/    # Desktop launcher, LWJGL3 platform configuration, desktop runner
    └── src/main/java/io/github/ldev22live/gdxunity/engine/desktop/
        ├── DesktopLauncher.java
        └── StartupHelper.java
```

### Legacy Modules (Unlinked in `settings.gradle`)

- **`core/`**: Contains the starter `io.github.ldev22live.GdxUnity` class (original 2D sprite demo). Not included in `settings.gradle`.
- **`lwjgl3/`**: Contains legacy launcher `io.github.ldev22live.lwjgl3.Lwjgl3Launcher` and Graal native image build configurations. Not included in `settings.gradle`.

> **Status**: The legacy modules are unreferenced and ready for full archival or removal once migration verification is complete.

---

## 4. Build System & Technology Stack

| Component | Configuration / Version | Notes |
| :--- | :--- | :--- |
| **Java** | 21 (LTS) | `sourceCompatibility = 21`, `targetCompatibility = 21`, `release.set(21)` |
| **LibGDX** | 1.14.2 | `gdxVersion=1.14.2` |
| **LWJGL3 Backend** | 3.4.1 | Specified in `gradle.properties` |
| **Testing Engine** | JUnit Jupiter 5.10.0 | Configured with `useJUnitPlatform()` across all subprojects |
| **Build Tool** | Gradle 9.6.1 (Wrapper) | Modern daemon & toolchain auto-provisioning enabled (`foojay-resolver`) |
| **Asset Pipeline** | Custom `generateAssetList` task | Auto-indexes assets in `assets/assets.txt` |
| **JVM Flags** | `--enable-native-access=ALL-UNNAMED` | Configured in `engine-desktop:run` for Java 21 compatibility |

---

## 5. Source Code Audit

### Existing Active Classes

1. **`EngineApplication`** (`engine-core`):
   - Extends `com.badlogic.gdx.ApplicationAdapter`.
   - Currently contains placeholder overrides for `create()`, `render()`, and `dispose()`.
   - Ready to be upgraded in Milestone 1 into a robust engine application lifecycle controller with explicit state transitions, delta time accumulation, and disposal hooks.

2. **`DesktopLauncher`** (`engine-desktop`):
   - Entry point: `public static void main(String[] args)`.
   - Initializes LWJGL3 window (640x480, VSync enabled, foreground FPS set to display refresh rate + 1).
   - Utilizes `StartupHelper` for cross-platform compatibility (macOS thread requirements, Windows LWJGL native extraction, Linux NVIDIA thread optimizations).

3. **`StartupHelper`** (`engine-desktop`):
   - Utility for JVM re-launching and platform-specific LWJGL workarounds.

### Placeholder & Skeleton Modules

- **`engine-scene`**: Gradle dependency on `engine-core` is wired. Directory structure present, but classes for `Entity`, `Component`, `Transform`, and `Scene` have not yet been implemented (scheduled for Milestone 2).
- **`engine-render`**: Gradle dependencies on `engine-core` and `engine-scene` are wired. No rendering abstractions implemented yet (scheduled for Milestone 1 and 3).

---

## 6. Milestone 0 Acceptance Criteria Checklist

| Acceptance Criteria | Status | Notes |
| :--- | :---: | :--- |
| Multi-module structure (`engine-core`, `engine-scene`, `engine-render`, `engine-desktop`) | ✅ Complete | Verified in `settings.gradle` and subproject `build.gradle` files. |
| Java 21 compatibility configured & verified | ✅ Complete | Configured in root and subproject `build.gradle`. Compiles on JDK 21. |
| Unnecessary dependencies removed (Fleks, KTX, Box2D, etc.) | ✅ Complete | Verified in `gradle.properties` and build files. |
| JUnit 5 test framework configured | ✅ Complete | Configured across all subprojects with `useJUnitPlatform()`. |
| Full build succeeds (`./gradlew build`) | ✅ Complete | Build tested and passes cleanly (all 4 active modules assemble & package). |
| Desktop launcher verified (`engine-desktop:run`) | 🟡 Validated | `DesktopLauncher` class is configured and properly wired to `EngineApplication`. |
| Foundational documentation suite created | ✅ Complete | `PROJECT_VISION.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `TECHNICAL_DECISIONS.md`, `TEST_STRATEGY.md` present. |
| ADR template and decision record defined | ✅ Complete | `adr/0000-adr-template.md` and initial technical decisions recorded. |

---

## 7. Next Steps: Roadmap to Milestone 1

With Phase 0 established, the immediate transition to **Milestone 1 — Minimal Engine Runtime** requires:

1. **Engine Lifecycle & Application Controller (`engine-core`)**:
   - Define formal lifecycle interfaces (`create`, `resize`, `render`, `pause`, `resume`, `dispose`).
   - Implement an explicit state-tracking engine loop with delta time calculation and frame timing.
   - Implement an owned resource disposal framework (preventing native memory leaks).

2. **Rendering Foundation (`engine-render`)**:
   - Create initial rendering abstraction and LibGDX primitive renderer (e.g. basic ShapeRenderer or colored rectangle test).
   - Handle viewport resize events cleanly without display distortion or crashes.

3. **Desktop Launcher Verification (`engine-desktop`)**:
   - Integrate `engine-render` with `DesktopLauncher` to present the initial rendered primitive viewport.

4. **Testing Suite Activation**:
   - Add initial unit tests in `engine-core` verifying lifecycle state transitions, delta time calculations, and resource disposal tracking.

5. **Legacy Cleanup**:
   - Permanently delete or archive the obsolete `core/` and `lwjgl3/` directories to prevent developer confusion.
