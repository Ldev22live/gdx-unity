# GdxUnity Documentation Index

Welcome to the **GdxUnity** architecture, design, and roadmap documentation.

---

## 🗺️ Master Roadmaps & Architecture

- **Engineering Rules:** [../AGENTS.md](../AGENTS.md) — Concrete rules for contributors and AI agents; workflows in `.windsurf/workflows/`.
- **Product Brief:** [design-doc.md.txt](design-doc.md.txt) — Master engineering instructions and long-term vision.
- **Feature-to-Test Matrix:** [FEATURE_TEST_MATRIX.md](FEATURE_TEST_MATRIX.md) — What is implemented, integrated and tested, with gaps.

- **Master Roadmap:** [ALL_IN_ONE_STUDIO_ROADMAP.md](ALL_IN_ONE_STUDIO_ROADMAP.md) — Comprehensive multi-phase studio roadmap (Phase 0 through Phase 15).
- **Core Architecture:** [ARCHITECTURE.md](ARCHITECTURE.md) — Subsystem boundaries, module dependencies, and conventions.
- **Technical Decisions:** [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md) — Architectural decision records (ADRs) and design trade-offs.
- **Testing Strategy:** [TEST_STRATEGY.md](TEST_STRATEGY.md) — Headless unit test guidelines, native loading, and graphics isolation.
- **Repository State:** [Oct28-current-state.md](Oct28-current-state.md) — Phase status and baseline tracking.

---

## 📦 Milestones & Phase Progress

| Phase | Description | Status | Reference Report |
|---|---|---|---|
| **Phase 0–8** | Foundation, LibGDX Bindings, Scene Graph & Serialization | Complete | [phase-8-current-state.md](phase-8-current-state.md) |
| **Phase 9** | 2D Vector & Sprite Rendering Pipeline | Complete | [PHASE_9_ARCHITECTURE_AUDIT.md](PHASE_9_ARCHITECTURE_AUDIT.md) |
| **Phase 10** | 2D Physics Subsystem & Hardening | Complete | [PHASE_10E_FINAL_ACCEPTANCE_AUDIT.md](PHASE_10E_FINAL_ACCEPTANCE_AUDIT.md) |
| **Phase 11** | Material Design 3 Desktop Studio Shell | Complete | [PHASE_11_MATERIAL_DESIGN_EDITOR_REDESIGN_REPORT.md](PHASE_11_MATERIAL_DESIGN_EDITOR_REDESIGN_REPORT.md) |
| **Phase 12** | 3D Rendering Foundation & Camera Systems | **Implemented (headless), not integrated** — uncommitted | [phase-12-verification.md](reports/phase-12-verification.md) |
| **Phase 13** | 3D Modeling & Mesh Studio (Low-Poly / UV) | Planned | [ALL_IN_ONE_STUDIO_ROADMAP.md#phase-13](ALL_IN_ONE_STUDIO_ROADMAP.md) |
| **Phase 14** | 3D Rigging, Bones & Keyframe Animation Studio | Planned | [ALL_IN_ONE_STUDIO_ROADMAP.md#phase-14](ALL_IN_ONE_STUDIO_ROADMAP.md) |
| **Phase 15** | Unified All-In-One Studio Integration & Release | Planned | [ALL_IN_ONE_STUDIO_ROADMAP.md#phase-15](ALL_IN_ONE_STUDIO_ROADMAP.md) |

---

## 🎯 Phase 12 Architecture Summary

> Status: the classes below exist and have headless tests, but none are wired into the editor viewport or
> runtime yet, and `SceneRenderer3D` submits no geometry. See
> [reports/phase-12-verification.md](reports/phase-12-verification.md) for the evidence and remaining work.

- **Perspective Camera (`PerspectiveCamera3D`):** Configurable FOV, near/far planes, 3D Euler angles, and headless test resilience.
- **3D Scene Renderer (`SceneRenderer3D`):** Wraps LibGDX `ModelBatch` and `Environment`, synchronizing scene lights in real time.
- **Lighting Subsystem:** `DirectionalLightComponent` and `PointLightComponent` with color, direction, intensity, and range.
- **3D Transform:** Full quaternion and Euler rotation, dirty hierarchy propagation, and world matrix calculations.
- **3D Navigation (`ViewportCameraController3D`):** Orbit, Pan, Fly, and Dolly zoom.
- **3D Visual Gizmos (`TransformGizmoRenderer` & `TransformGizmoMath`):** 3-axis Move arrows + planes, 3-axis Rotate rings, 3-axis Scale cubes.
