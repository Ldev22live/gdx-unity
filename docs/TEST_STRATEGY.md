# Test Strategy

## Testing Philosophy

- **Test-driven where practical**: Write tests alongside implementation for core logic
- **Integration tests for critical paths**: Test application startup, rendering, and serialization
- **Smoke tests for milestones**: Verify each milestone's acceptance criteria
- **No false positives**: Never report a test or build as passing unless actually executed successfully

## Test Levels

### Unit Tests

**Purpose**: Test individual classes and methods in isolation.

**Scope**:
- Engine lifecycle methods
- Entity/component operations
- Transform calculations
- Serialization/deserialization
- Resource management
- Math utilities

**Tools**: JUnit 5

**Location**: `src/test/java` in each module

**Coverage Goal**: 80%+ for core logic, 60%+ for rendering code

### Integration Tests

**Purpose**: Test interactions between components.

**Scope**:
- Scene loading and entity creation
- Transform hierarchy updates
- Rendering pipeline integration
- Asset loading and caching
- Editor-runtime communication

**Tools**: JUnit 5, LibGDX test utilities (if needed)

**Location**: `src/test/java` in each module

### Smoke Tests

**Purpose**: Verify critical application paths work end-to-end.

**Scope**:
- Desktop application launches
- Window displays without errors
- Basic rendering produces output
- Scene save/load cycle
- Editor launches and displays UI

**Tools**: Manual verification or automated UI tests (future)

**Execution**: Run after each milestone completion

## Test Organization

### Module-Specific Tests

**engine-core**:
- Engine lifecycle state transitions
- Logging functionality
- Resource disposal tracking
- Base interface implementations

**engine-scene**:
- Entity creation and destruction
- Component attachment/detachment
- Transform hierarchy operations
- World transform calculations
- Scene serialization/deserialization
- ID generation and stability

**engine-render**:
- Camera operations
- Primitive rendering
- Transform-to-render integration
- Viewport handling

**engine-desktop**:
- Launcher configuration
- Platform-specific setup
- Window creation

### Test Naming Convention

```
{ClassName}Test.java
```

Example: `EntityTest.java`, `TransformTest.java`, `SceneSerializationTest.java`

### Test Method Naming

```
should{ExpectedBehavior}When{StateUnderTest}
```

Example: `shouldCalculateWorldTransformWhenParentChanged`

## Test Data Management

### Fixtures

- Use test fixtures for complex setup
- Place fixtures in `src/test/resources`
- Clean up fixtures in `@AfterEach` methods

### Temporary Files

- Use temporary directories for file-based tests
- Clean up temporary files after tests
- Use JUnit's `TempDir` annotation where available

## Continuous Integration

### Pre-Commit

- Run unit tests for modified modules
- Fast feedback loop (< 30 seconds)

### Pre-Merge

- Run full test suite
- Run smoke tests
- Verify build succeeds

### Milestone Gates

- All tests must pass
- Smoke tests must succeed
- Code coverage thresholds met
- No regressions in previous milestones

## Test Execution

### Run All Tests

```bash
./gradlew test
```

### Run Specific Module Tests

```bash
./gradlew engine-core:test
./gradlew engine-scene:test
./gradlew engine-render:test
./gradlew engine-desktop:test
./gradlew editor-core:test
```

### Run Specific Test Class

```bash
./gradlew engine-core:test --tests EntityTest
./gradlew editor-core:test --tests EditorStateTest
```

### Editor-Core vs Graphics Context Strategy
- All editor business logic, selection state, inspector calculations, and 2D bounds picking are isolated in `editor-core` and execute in headless mode without requiring an active OpenGL/LWJGL3 window.
- LibGDX native libraries for math (`Quaternion`, `Matrix4`) in unit tests are safely initialized using `GdxNativesLoader.load()`.
- Play Mode simulation state transitions, scene cloning, delta time sanitization, and authored scene isolation are verified headlessly in `PlayModeControllerTest`.
- Transform Gizmo handle hit-testing, world-to-local projections, parent hierarchy offsets, angular rotations, and scale clamping are verified headlessly in `TransformGizmoTest`.
- Desktop visual verification is performed via manual smoke testing (`./gradlew :editor-desktop:run`). Automated CI gates rely on the headless JUnit 5 suite.

### Run with Coverage

```bash
./gradlew test jacocoTestReport
```

## Test Maintenance

### When Tests Fail

1. Identify if it's a legitimate bug or test issue
2. Fix the bug or update the test
3. Verify the fix doesn't break other tests
4. Document the reason for test changes

### Test Deletion

- Delete tests for removed functionality
- Delete duplicate tests
- Delete tests that don't add value

### Test Refactoring

- Refactor tests when implementation changes significantly
- Maintain test coverage during refactoring
- Update test names to reflect new behavior

## Testing Anti-Patterns

**Avoid**:
- Testing implementation details instead of behavior
- Fragile tests that break with minor refactoring
- Tests that depend on external state (filesystem, network)
- Tests with hardcoded paths or environment assumptions
- Disabling tests to get a green build
- Tests that are too complex to understand

**Prefer**:
- Testing public APIs and observable behavior
- Isolated tests that don't depend on each other
- Mocking external dependencies
- Using test fixtures for complex setup
- Fixing failing tests immediately
- Simple, readable test cases

## Milestone-Specific Testing

### Milestone 0
- Verify build succeeds
- Verify test framework configured
- Verify all modules compile

### Milestone 1
- Test engine lifecycle state transitions
- Test resource disposal
- Test basic rendering
- Smoke test: application launches and displays

### Milestone 2
- Test entity creation/destruction
- Test component operations
- Test transform hierarchy
- Test scene serialization/deserialization
- Smoke test: scene save/load cycle

### Milestone 3
- Test camera operations
- Test primitive rendering
- Test transform-to-render integration
- Smoke test: rendered objects with transforms

### Milestone 4
- Test editor UI components
- Test editor state management
- Smoke test: editor launches and displays UI

### Milestone 5
- Test play/pause/stop state machine
- Test runtime isolation
- Smoke test: play mode launches and terminates

### Milestone 6
- Test visual transform gizmo math (hit-testing, local projections, rotation, scale clamping)
- Test drag cancellation on interruptions
- Smoke test: viewport gizmo rendering and manipulation

### Milestone 7
- Test AssetReference normalization, validation, separator formatting, and traversal/absolute path rejection
- Test AssetCatalog filesystem scanning, extension filtering, deterministic ordering, and missing directory handling
- Test SpriteRendererComponentSerializer round-trip serialization and null asset references
- Test EditorState asset tracking and listener notifications
- Smoke test: asset browser discovery, inspector assignment, and Play Mode isolation

### Milestone 8
- Test CommandHistory bounded capacity, execute/undo/redo sequences, and redo invalidation
- Test concrete editing commands: RenameEntityCommand, ChangeEntityActiveCommand, ChangeTransformCommand, CreateEntityCommand, DeleteEntityCommand, AssignSpriteAssetCommand
- Test transform gizmo command aggregation on endDrag and skip on unchanged/cancelled drags
- Test history reset on scene replacement
- Smoke test: UI Undo/Redo buttons, keyboard shortcuts, and Play Mode command gating

### Milestone 9
- Test ScriptComponent lifecycle execution (`onCreate`, `onStart`, `onUpdate`, `onEnable`, `onDisable`, `onDestroy`)
- Test independent runtime script instances across multiple entities
- Test EntityScriptBindingComponentSerializer direct serialization and scene save/load round-trip
- Test JavaScriptCompiler valid compilation, error diagnostics, and dynamic class loading
- Test KotlinScriptCompiler valid compilation, syntax error diagnostics, and dynamic class loading
- Test ScriptManager discovery, instantiation, and Play Mode initialization and cleanup
- Smoke test: "Compile Scripts" toolbar action, script attachment via Inspector, and Play Mode script execution

## Test Metrics

Track the following metrics:

- Test count per module
- Test execution time
- Code coverage percentage
- Flaky test rate (should be 0)
- Test failure rate

## Test Documentation

Document complex tests with:
- Purpose of the test
- Setup requirements
- Expected behavior
- Edge cases covered

Use Javadoc on test classes for complex scenarios.
