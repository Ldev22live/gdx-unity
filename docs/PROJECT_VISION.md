# Project Vision

## Objective

Build an original, extensible game engine and development environment inspired by the workflows and toolset of Unreal Engine 5, implemented in Java using LibGDX.

The reference is workflow parity for indie-scale projects, not rendering-technology parity (see TD-020 in `TECHNICAL_DECISIONS.md`). Unity was the original reference until 2026-10-29.

## Goals

### Primary Goals

- **Unreal-inspired editor experience**: Provide a visual editor with a viewport with overlay tools, an outliner (scene hierarchy), a details panel (inspector), a content browser, an output log, and play-in-editor controls, followed by visual scripting, a material graph editor, a sequencer/timeline, a particle editor, landscape tools and packaging.
- **Entity/component architecture**: Implement a clean, understandable entity/component system with stable IDs, single authoritative transform model, and predictable update ordering.
- **Scene persistence**: Support versioned JSON scene format with stable IDs, component data, transform information, and resource references.
- **Runtime independence**: The engine runtime must be capable of launching a game without launching the editor.
- **Multi-game support**: The engine must eventually support multiple independent games using the same engine.

### Non-Goals

- **Game-specific content**: Do not implement game-specific missions, quests, characters, combat, story content, or systems.
- **Third-party engine code or branding**: Create original implementations. Do not copy Unreal Engine or Unity source code (including Unreal source available under the Epic Games EULA), proprietary assets, icons, branding, trademarked feature names, or protected UI assets.
- **Rendering-technology parity with Unreal**: Nanite/Lumen-class rendering is out of scope; a new rendering backend needs a feasibility study and approval (design doc §3.2).
- **Premature ECS framework**: Begin with a small, understandable entity/component design. Evaluate external ECS libraries only when requirements justify them.
- **All platforms initially**: Initial target platform is Desktop. Additional platform support is a later milestone.

## Technology Stack

- **Language**: Java 21
- **Rendering framework**: LibGDX 1.14.2
- **Desktop backend**: LWJGL3
- **Build system**: Gradle
- **Test framework**: JUnit 5
- **IDE**: IntelliJ IDEA

## Success Criteria

The project succeeds when:

1. Developers can create and edit scenes visually with an Unreal-like workflow.
2. Games built with the engine can run independently of the editor.
3. The engine architecture is maintainable, extensible, and well-documented.
4. Multiple independent games can be developed using the same engine codebase.
5. The codebase has comprehensive automated tests for core functionality.
6. Scene serialization is stable and supports schema evolution.

## Scope

### In Scope

- Engine lifecycle and application management
- Scene and entity management
- Transform hierarchy
- Rendering pipeline (initially LibGDX-based)
- Asset loading and resource management
- Input handling
- Scene serialization
- Logging and diagnostics
- Editor user interface
- Editor-to-runtime communication

### Out of Scope (Initial Phases)

- Physics integration (later milestone)
- Animation system (later milestone)
- Audio system (later milestone)
- Advanced rendering features (later milestone)
- Custom editor extensions (later milestone)
- Game packaging and builds (later milestone)
- Mobile platforms (later milestone)

## Principles

1. **Separation of concerns**: Engine runtime must not depend on editor modules.
2. **Incremental implementation**: Build features incrementally with verified milestones.
3. **Minimal dependencies**: Avoid unnecessary dependencies and speculative abstractions.
4. **Test-driven quality**: Add unit tests for core logic and serialization.
5. **Clear ownership**: Design explicit ownership and disposal rules for resources.
6. **Stable interfaces**: Design APIs that can evolve without breaking existing code.
