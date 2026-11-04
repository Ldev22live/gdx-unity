# Forgeroom Documentation Index

Welcome to the **Forgeroom** architecture, design, and roadmap documentation.

---

## 🗺️ Master Roadmaps & Architecture

- **Engineering Rules:** [../AGENTS.md](../AGENTS.md) — Concrete rules for contributors and AI agents; workflows in `.windsurf/workflows/`.
- **Product Brief:** [design-doc.md.txt](design-doc.md.txt) — Master engineering instructions and long-term vision.
- **Feature-to-Test Matrix:** [FEATURE_TEST_MATRIX.md](FEATURE_TEST_MATRIX.md) — What is implemented, integrated and tested, with gaps.

- **Master Roadmap:** [ALL_IN_ONE_STUDIO_ROADMAP.md](ALL_IN_ONE_STUDIO_ROADMAP.md) — Phase roadmap; re-sequenced after Phase 12 to an Unreal-aligned order (TD-026).
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
| **Phase 12** | 3D Rendering Foundation & Camera Systems | Implemented; 3D view integrated in editor (12B/12C, pending manual check) | [phase-12-verification.md](reports/phase-12-verification.md) |
| **Phase 12B** | Unreal-style editor shell (menus, scene view panel, files, Output Log, Details) | Steps A–B implemented; C pending | [phase-12b-editor-shell.md](milestones/phase-12b-editor-shell.md) |
| **Phase 12C** | 3D content: primitives, lights, Create menu | Implemented, pending manual check | [phase-12b-12c-report.md](reports/phase-12b-12c-report.md) |
| **Phase 13** | 3D asset pipeline: glTF import, PBR, shadows, Content Browser (TD-026) | In progress: A (glTF import, TD-030), B (shadows, sky light, tone mapping; TD-032, visual check pending) and C/D parts done ([plan](milestones/phase-13-asset-pipeline.md), [13-B](milestones/phase-13b-lighting.md), [report](reports/phase-13b-report.md)) | [ALL_IN_ONE_STUDIO_ROADMAP.md §5](ALL_IN_ONE_STUDIO_ROADMAP.md), [gdx-gltf-evaluation.md](reports/gdx-gltf-evaluation.md) |
| **Phase 14** | Prefabs (TD-027) | Implemented and integrated; visual check pending ([milestone](milestones/phase-14-prefabs.md)) | [ALL_IN_ONE_STUDIO_ROADMAP.md §5](ALL_IN_ONE_STUDIO_ROADMAP.md) |
| **Phase 15** | 3D physics (TD-028) | In progress: components, engine-neutral system and editor done; simulation blocked on library approval ([milestone](milestones/phase-15-3d-physics.md)) | [3d-physics-evaluation.md](reports/3d-physics-evaluation.md) |
| **Phase 16** | Visual scripting (TD-029) | Implemented and integrated; visual check pending ([milestone](milestones/phase-16-visual-scripting.md)) | [project-suggestions.md §3](project-suggestions.md) |
| **Phase 17** | Skeletal animation and Timeline (TD-030, TD-031) | Implemented and integrated; visual check pending ([milestone](milestones/phase-17-animation.md)) | [phase-17-report.md](reports/phase-17-report.md) |
| **Phase 18** | Packaging (TD-033) | In progress: 18-A (editor as a Windows executable with bundled runtime, smoke test, crash log) and 18-B (game runtime plays scene files like editor Play, TD-035) implemented; 18-C game export planned ([milestone](milestones/phase-18-packaging.md), reports [18-A](reports/phase-18a-report.md), [18-B](reports/phase-18b-report.md)). Sample games (TD-034, [report](reports/sample-games-report.md)): 4 2D + 6 3D + 4 2.5D templates, game camera, screen text | [ALL_IN_ONE_STUDIO_ROADMAP.md §5](ALL_IN_ONE_STUDIO_ROADMAP.md) |
| **Multi-pane 3D view** | Four-pane scene view (perspective, X, Y, Z axis), splittable and resizable, and a Sculpt toolbar button and F4 (TD-039, proposed) | Implemented and integrated; the editor starts in it. Headless logic tested (29 tests), editor smoke-tested and screenshot checked; **mouse use of dividers, headers and axis views not tried** ([report](reports/viewport-layout-report.md)) | [TECHNICAL_DECISIONS.md TD-039](TECHNICAL_DECISIONS.md) |
| **Phase 19** | Sculpting (TD-038, proposed) | 19-A implemented (headless `engine-sculpt` core). 19-B partly implemented: `editor-sculpt` module, drawing of sculpts, sculpt mode, undoable strokes, menu and key actions; renders in the real editor (screenshot), mouse sculpting and menus **not tried in the running editor**; no brush panel; sculpts invisible in the game runtime and web player. 19-C planned ([milestone](milestones/phase-19-sculpting.md), reports [19-A](reports/phase-19a-report.md), [19-B](reports/phase-19b-report.md)) | [TECHNICAL_DECISIONS.md TD-038](TECHNICAL_DECISIONS.md) |
| **Phase 20** | Music studio: synth, drums, SoundFont, sfxr, step sequencer, piano roll, playlist, mixer, WAV export (TD-040, proposed) | 20-A (`engine-audio`, 178 tests) and 20-B (`editor-audio` 53 tests, Music tab) implemented and integrated; editor started on the Music tab for 1500 frames with 0 errors, window screenshots of all five views, and sound reached the sound device (3.2 s counted). **Sound was not listened to and mouse editing was not tried**; 20-C game integration not started ([milestone](milestones/phase-20-music-studio.md), [report](reports/phase-20-report.md)) | [TECHNICAL_DECISIONS.md TD-040](TECHNICAL_DECISIONS.md) |
| **Web editor (streamed)** | The desktop editor in a per-user container, shown in the browser (TD-037, proposed) | Image, session script and guide written; session script tested with a fake `docker` (62 checks). **Image not built or run** (no Docker here), nginx/Spring/Next.js snippets untested ([guide](editor-streaming.md)) | [TECHNICAL_DECISIONS.md TD-037](TECHNICAL_DECISIONS.md) |
| **Web runtime** | Browser game player for VPS hosting (TD-036) | Integrated: `engine-web` builds with gdx-teavm and plays all 14 samples with scripts, keyboard input, graphs, Animators and glTF through the shared runtime; Docker deploy files unverified ([report](reports/web-runtime-spike-report.md), [README](../engine-web/README.md), [deploy steps](deploy-steps.md)) | [TECHNICAL_DECISIONS.md TD-036](TECHNICAL_DECISIONS.md) |
| **Web runtime (Babylon.js)** | JavaScript scene player, later an editor, on Babylon.js (TD-041, proposed) | Stage 1 implemented, stage 2 in progress: `engine-babylon` plays scene files with lights, shadows, cameras, text, 2D physics and the scripts of 10 of the 14 samples; a first web editor (outline, details, gizmos, undo, save, play); 60 Vitest tests; checked in headless Edge only ([README](../engine-babylon/README.md)) | [TECHNICAL_DECISIONS.md TD-041](TECHNICAL_DECISIONS.md) |
| Deferred | Vector, audio, mesh modeling and rigging studios | Deferred (TD-026) | [ALL_IN_ONE_STUDIO_ROADMAP.md](ALL_IN_ONE_STUDIO_ROADMAP.md) |

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
