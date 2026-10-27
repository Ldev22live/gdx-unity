# GdxUnity Milestone 9 Implementation Report: Java/Kotlin Scripting Foundation

**Project:** GdxUnity  
**Milestone:** 9 — Java/Kotlin Scripting Foundation  
**Language:** Java 21, Kotlin 2.0.21  
**Framework:** LibGDX 1.14.2  
**Build System:** Gradle Wrapper 9.6.1

---

## 1. Executive Summary

Milestone 9 implements a production-grade polyglot scripting foundation for GdxUnity. Scene entities can now have behavior scripts authored in either Java or Kotlin attached to them, compiled on demand using first-class JVM toolchains, diagnosed with structured compiler error reports, and executed through full lifecycle callbacks (`onCreate`, `onStart`, `onUpdate`, `onFixedUpdate`, `onEnable`, `onDisable`, `onDestroy`) during Play Mode.

Script configurations are seamlessly persisted and restored through versioned scene JSON via `EntityScriptBindingComponentSerializer`, fully respecting existing undo/redo commands and Play Mode scene isolation.

---

## 2. Architecture & Design Principles

### 2.1 Shared Script Component API (`engine-scene`)
- **`ScriptComponent`**: Base class extending `BaseComponent`, providing standard lifecycle callbacks:
  - `onCreate()`: Called once when the script is attached and initialized.
  - `onStart()`: Called once before the script's first update frame during Play Mode.
  - `onUpdate(float deltaTime)`: Called every frame during active Play Mode.
  - `onFixedUpdate(float fixedDeltaTime)`: Optional hook for fixed-step physics loops.
  - `onEnable()` / `onDisable()`: Called when the script component or entity is enabled/disabled.
  - `onDestroy()`: Called upon component detachment, entity destruction, or simulation teardown.
  - Convenience accessors: `getScene()`, `getTransform()`, `getComponent(Class<T>)`.
- **`ScriptLanguage`**: Enum identifying `JAVA` and `KOTLIN` with extension mapping.
- **`EntityScriptBindingComponent`**: Entity component that stores persistent script configuration (`scriptPath`, `scriptClassName`, `language`) and holds the transient runtime instance of `ScriptComponent`.
- **`EntityScriptBindingComponentSerializer`**: Versioned JSON serialization preserving script path, class name, language, and enabled state. Live instances, class loaders, and bytecodes are never persisted.

### 2.2 Compilation & Diagnostics (`editor-core`)
- **`JavaScriptCompiler`**: Uses standard `javax.tools.JavaCompiler` and `DiagnosticCollector` to compile `.java` script files targeting JVM 21, capturing file, line, column, severity, and message into `ScriptDiagnostic`.
- **`KotlinScriptCompiler`**: Uses embedded `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` targeting JVM 21 with `-no-stdlib` (resolved from project classpath), converting compiler messages into `ScriptDiagnostic`.
- **`ScriptClassLoader`**: An isolated `URLClassLoader` pointing to `build/scripts`. Recompilation closes the existing classloader to release old class metadata and avoid stale references.
- **`ScriptManager`**: Central service that scans `scripts/`, compiles Java and Kotlin files, discovers classes extending `ScriptComponent`, instantiates script instances, and orchestrates Play Mode binding.

### 2.3 Play Mode & Lifecycle Integration
- **Pre-Flight Compilation**: When entering Play Mode, `PlayModeController` invokes `ScriptManager.compileAll()`. If compiler errors exist, simulation is aborted and diagnostics are reported in the editor status bar.
- **Instance Isolation**: `ScriptManager.initializeSceneScripts(runtimeScene)` creates fresh, independent script instances for each entity in the cloned runtime scene.
- **Clean Teardown**: When exiting Play Mode, `ScriptManager.cleanupSceneScripts(runtimeScene)` invokes `onDestroy()` and detaches all runtime script instances before the runtime scene is disposed.

### 2.4 Editor Integration (`editor-desktop`)
- **`EditorToolbar`**: Added **⚙ Compile Scripts** button that triggers manual compilation, discovers loaded script classes, and reports diagnostics.
- **`InspectorPanel`**: Detects `EntityScriptBindingComponent`, displays script class name, language badge, enabled toggle, and provides a **+ Attach Script...** action routed through the undoable `AddComponentCommand`.

---

## 3. Minimal Script Examples

### 3.1 Java Script Example
```java
package game;

import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

public class PlayerMovement extends ScriptComponent {
    private float speed = 150.0f;

    @Override
    public void onCreate() {
        System.out.println("Player script initialized on entity: " + getEntity().getName());
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (getTransform() != null) {
            getTransform().translate(speed * deltaTime, 0.0f, 0.0f);
        }
    }

    @Override
    public void onDestroy() {
        System.out.println("Player script destroyed");
    }
}
```

### 3.2 Kotlin Script Example
```kotlin
package game

import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent

class RotatorScript : ScriptComponent() {
    var rotationSpeed: Float = 45.0f

    override fun onStart() {
        println("Rotator script started on ${entity?.name}")
    }

    override fun onUpdate(deltaTime: Float) {
        transform?.rotate(0f, 0f, rotationSpeed * deltaTime)
    }
}
```

---

## 4. Security & Safety Model

- User-authored Java and Kotlin scripts are treated as **trusted in-process code**.
- The class loader provides dynamic loading, compilation modularity, and recompilation isolation, not an arbitrary OS or security sandbox.
- Scripts execute only during active Play Mode simulation; Edit Mode does not execute user gameplay scripts.

---

## 5. Automated Tests

All tests run headlessly under JUnit 5 without requiring an active OpenGL context or display.

### Tests Added
- `engine-scene`:
  - `ScriptComponentTest`:
    - `testScriptLifecycleInEntity`: Verifies `onCreate`, `onEnable`, `onStart`, `onUpdate`, `onDisable`, and `onDestroy` sequence, plus delta time delivery.
    - `testIndependentInstancesPerEntity`: Verifies that multiple entities receive distinct, independent instances of the same script class.
  - `EntityScriptBindingSerializationTest`:
    - `testSerializerDirectRoundTrip`: Verifies JSON serialization of script path, class name, language, and enabled flag.
    - `testSceneSerializationWithScriptBinding`: Verifies full scene save and load round-trip with script bindings attached to entities.
- `editor-core`:
  - `ScriptCompilerTest`:
    - `testCompileValidJavaSource`: Compiles Java script, loads class, and executes updates.
    - `testJavaSyntaxErrorDiagnostics`: Verifies structured error diagnostics for syntax errors.
    - `testCompileValidKotlinSource`: Compiles Kotlin script with embedded `K2JVMCompiler`, loads class, and executes.
    - `testKotlinSyntaxErrorDiagnostics`: Verifies Kotlin compiler diagnostics for invalid syntax.
    - `testScriptManagerDiscoveryAndInstantiation`: Verifies polyglot directory discovery and instantiation of both Java and Kotlin scripts.

### Test Results
```powershell
./gradlew.bat test
```
- Total test count: **101** tests passed across all subprojects.
- Failures: 0.
- Errors: 0.

### Build Verification
```powershell
./gradlew.bat build
```
- Status: BUILD SUCCESSFUL.

---

## 6. Manual Desktop Verification

To verify the interactive scripting workflow:
```powershell
./gradlew.bat :editor-desktop:run
```

**Verification Checklist**:
1. [x] Editor desktop window opens cleanly with all panels functional.
2. [x] Selecting an entity and clicking **+ Attach Script...** attaches `EntityScriptBindingComponent` with undo/redo support.
3. [x] Clicking **⚙ Compile Scripts** in the toolbar compiles project scripts and updates status.
4. [x] Entering Play Mode validates script compilation and initializes script instances on runtime entities.
5. [x] Saving and reloading scene preserves script bindings.
6. [x] Stopping Play Mode detaches script instances and restores the authored scene cleanly.

---

## 7. Known Limitations & Recommended Milestone 10

### Known Limitations
- Embedded text editor inside the desktop window is not yet implemented (scripts are authored in external IDE/editor in the project's `scripts/` directory).
- Hot-reloading of scripts during active Play Mode is not supported; scripts are compiled and rebound upon starting Play Mode.

### Recommended Milestone 10: 2D Physics Engine Foundation (Box2D)
1. Integrate Box2D into `engine-scene` with `RigidBody2DComponent`, `BoxCollider2DComponent`, and `CircleCollider2DComponent`.
2. Connect physics simulation loop to `ScriptComponent.onFixedUpdate()`.
3. Add editor collider wireframe rendering in the scene viewport.
