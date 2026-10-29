# GdxUnity

A Java-first game engine and integrated editor built on [libGDX](https://libgdx.com/), with an
AI-controllable editor workflow as a core goal.

- **Engineering rules for contributors and AI agents:** [`AGENTS.md`](AGENTS.md)
- **Product brief:** [`docs/design-doc.md.txt`](docs/design-doc.md.txt)
- **Documentation index:** [`docs/INDEX.md`](docs/INDEX.md)
- **What is actually implemented and tested:** [`docs/FEATURE_TEST_MATRIX.md`](docs/FEATURE_TEST_MATRIX.md)

## Requirements

- JDK 21
- Windows, macOS or Linux desktop with OpenGL support (to run the editor/runtime; tests are headless)

## Modules

| Module | Purpose |
|---|---|
| `engine-core` | Lifecycle, timing, resource ownership, asset references |
| `engine-scene` | Entities, components, transforms, scenes, serialization, 2D physics, scripting components |
| `engine-render` | 2D/3D cameras and scene renderers |
| `engine-desktop` | Desktop runtime launcher |
| `editor-core` | Headless editor logic: state, undo/redo, gizmos, picking, play mode, script compiler, AI agent tools |
| `editor-desktop` | Desktop editor UI |

The `core/` and `lwjgl3/` directories are leftovers from the original gdx-liftoff template and are not part of
the build (`settings.gradle`).

## Common commands

Use `gradlew.bat` on Windows (cmd/PowerShell) or `./gradlew` elsewhere.

```bash
./gradlew test --continue          # run all tests
./gradlew :editor-core:test        # run one module's tests
./gradlew :editor-desktop:run      # launch the editor
./gradlew :engine-desktop:run      # launch the runtime
```

The build is configured with `org.gradle.logging.level=quiet`, so a passing run prints very little. Check the
exit code and the reports in `<module>/build/reports/tests/test/index.html`.
