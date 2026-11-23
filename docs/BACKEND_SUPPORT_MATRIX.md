# Backend Support Matrix

What each backend does with each feature (roadmap M9.4). **Last checked: 2026-11-18** on Windows 11, at HEAD `0469ceb` plus the
uncommitted working tree. Report: [reports/m9-4-backend-support-matrix.md](reports/m9-4-backend-support-matrix.md).

**The backends:**
- **Desktop:** the Java game runtime (`engine-desktop`, LWJGL3), which also runs editor Play (the same `GameSession` and `GameRenderer`).
- **TeaVM web:** the same Java runtime compiled to JavaScript (`engine-web`, TD-036).
- **Babylon web:** the TypeScript player and editor on Babylon.js (`engine-babylon`, TD-041). It reads the same scene files and runs hand-ported scripts.

**Status words.** Each cell starts with one of these:

| Word | Meaning |
|---|---|
| **Works** | Runs as intended, with an automated test or a check in the running backend named in the cell or the row |
| **Partial** | Runs, with the gaps stated |
| **Read only** | The file loads and keeps it, but nothing is drawn or simulated |
| **No** | Not supported: refused or reported with the reason |
| **Blocked** | Would come from shared code, but the backend's build does not compile at present (section 3) |
| **Unchecked** | Expected from the code, but not seen working on this backend |
| **Not applicable** | The question does not arise for this backend |

`BackendSupportMatrixTest` (`engine-desktop`) keeps this file honest:
- every component type the game runtime registers has exactly one row in section 1;
- every cell starts with a word from the legend;
- the Babylon column agrees with the Babylon player's `SUPPORTED_COMPONENTS`;
- the 3D physics types are No on TeaVM, and no registered type is No on the desktop.

**Why TeaVM is Blocked.** The TeaVM release build (`./gradlew :engine-web:gdx_teavm_web_js_release_build`) fails today. Shared runtime code added since the last recorded working web build ([web runtime spike report](reports/web-runtime-spike-report.md), 2026-11-02) uses Java that TeaVM cannot translate:
- **Audio:** `ConcurrentLinkedQueue` (`SfxPlayer`), `SplittableRandom` (`EngineSynth`) and LWJGL3's `OggInputStream` (`OggClips`).
- **Graph nodes:** reflection in `JavaMethodNodes` (`Executable.getParameters`) and `KeyNames` (`Field.getInt`).
- **Sprites:** `ImageIO` and `BufferedImage` in `PixelPng`, used by sprite and tile map loading.
- **3D physics:** Jolt's native loader (`System.load`, `HexFormat`).

Until one of those is repaired or the backend is retired, the TeaVM column says what would run, not what runs.

## 1. Components

| Type id | Component | Desktop | TeaVM web | Babylon web | Notes |
|---|---|---|---|---|---|
| `MeshPrimitiveComponent` | Mesh Primitive | Works | Blocked | Works: box, sphere and plane, with `.material` files (M2.5) | Desktop: `MaterialAssignmentTest`, sample runs |
| `ModelComponent` | Model (glTF) | Works | Blocked | Partial: meshes drawn and picked; model animations not played | Babylon: `landscape-3d` drawn from eight glTF models ([screenshot](reports/m9-4/babylon-landscape-3d.png), 2026-11-18, headless Chrome) |
| `DynamicMeshComponent` | Dynamic Mesh | Works | Blocked | No: reported as unsupported | Runtime-built meshes (sculpt export, mesh edits) |
| `ShapeRendererComponent` | Shape Renderer | Works | Blocked | Works: filled rectangles | |
| `SpriteRendererComponent` | Sprite Renderer | Works | Blocked | No: reported as unsupported | Babylon draws sprites through Sprite Animator only |
| `SpriteAnimatorComponent` | Sprite Animator | Works | Blocked | Works: `.sprite` tags, speed, flip, tint | |
| `TilemapComponent` | Tile Map | Works | Blocked | Works: drawn, and its solid tiles collide in 2D physics | Babylon: `tilemap-platformer-2d` drawn ([screenshot](reports/m9-4/babylon-tilemap-platformer-2d.png), 2026-11-18) |
| `TextComponent` | Text | Works | Blocked | Works: HTML over the canvas, nine anchors | |
| `CameraComponent` | Camera | Works | Blocked | Works: perspective and orthographic | |
| `CameraRigComponent` | Camera Rig | Works | Blocked | Works: moved and turned after physics (TD-064) | |
| `DirectionalLightComponent` | Directional Light | Works | Blocked | Works: with shadows | |
| `PointLightComponent` | Point Light | Works | Blocked | Unchecked: built by the player, not seen in a bundled sample | |
| `SkyLightComponent` | Sky Light | Works | Blocked | Works | |
| `ToneMappingComponent` | Tone Mapping | Works | Blocked | Works: ACES | Colours close to the desktop, not identical |
| `AtmosphereComponent` | Atmosphere (sky, fog, weather) | Works | Blocked | No: reported as unsupported | M3.9, TD-090 |
| `WaterBodyComponent` | Water Body | Works | Blocked | No: reported as unsupported | M3.9, TD-090 |
| `PostProcessVolumeComponent` | Post Process Volume | Works | Blocked | No: reported as unsupported | M6.2, TD-091 |
| `Terrain` | Terrain | Works | Blocked | Works: shape, paint layers, holes and water (M3.11) | Babylon: `action-rpg-valley-3d` terrain drawn (2026-11-18) |
| `TerrainPath` | Terrain Path (roads, rivers) | Works | Blocked | No: reported as unsupported | M3.6 |
| `RigidBody2DComponent` | Rigid Body 2D | Works | Blocked: pure-Java Box2D (`gdx-box2d-gwt`) | Works: planck.js | |
| `BoxCollider2DComponent` | Box Collider 2D | Works | Blocked | Works | |
| `CircleCollider2DComponent` | Circle Collider 2D | Works | Blocked | No: not read | |
| `RigidBody3DComponent` | Rigid Body 3D | Works | No: Jolt's native library cannot load in a browser | Works: Jolt in WebAssembly (TD-059) | Only loaded for scenes with a 3D collider |
| `BoxCollider3DComponent` | Box Collider 3D | Works | No: needs Jolt | Works | |
| `SphereCollider3DComponent` | Sphere Collider 3D | Works | No: needs Jolt | Works | |
| `CapsuleCollider3DComponent` | Capsule Collider 3D | Works | No: needs Jolt | Works | |
| `ConvexHullCollider3DComponent` | Convex Hull Collider 3D | Works | No: needs Jolt | Works (TD-064) | |
| `MeshCollider3DComponent` | Mesh Collider 3D | Works | No: needs Jolt | Works (TD-064) | |
| `TerrainCollider3DComponent` | Terrain Collider 3D | Works | No: needs Jolt | Works: the terrain as a static mesh once its file has loaded | |
| `Joint3DComponent` | Joint 3D | Works | No: needs Jolt | Works (TD-064) | |
| `CharacterController3DComponent` | Character Controller 3D | Works | No: needs Jolt | Works: driven by ported scripts (TD-064) | Graph character nodes are desktop only |
| `PlayerComponent` | Player (genre profiles) | Works: genre profiles walk it (TD-088) | Blocked | No: reported as unsupported; profile samples load without gameplay | Babylon: `action-rpg-valley-3d` status bar "unsupported: PlayerComponent x1" ([screenshot](reports/m9-4/babylon-action-rpg-valley-3d.png), 2026-11-18) |
| `SpawnPointComponent` | Spawn Point | Works | Blocked | No: reported as unsupported | |
| `PrefabInstance` | Prefab link | Works: links the entity to its prefab in the editor; nothing at play | Blocked | No: reported as unsupported; the entity's other components still play | |
| `EntityScriptBindingComponent` | Script binding | Works: Java and Kotlin scripts | Blocked: Java scripts only, compiled into the build | Partial: only hand-ported TypeScript versions run; 4 of the 14 original games' scripts not ported | Section 2, Scripts |
| `GraphScriptComponent` | Graph Script | Works | Blocked: `NodeRegistry.createDefault` reaches the reflection above | Partial: the web node set; physics, navigation and some Java method nodes refused with the reason | `visualscript.test.ts` |
| `AnimatorComponent` | Animator (`.anim` clips) | Works | Blocked | Works: clips on the entity's bones | |
| `AudioSource` | Audio Source | Works | Blocked: audio code above | Works | Sound not listened to on any backend in this check. Desktop: until M9.5 the game runtime and the packaged game opened no sound device (the session started before the window's audio backend existed); editor Play was not affected. Fixed and tested in M9.5 |
| `AudioListener` | Audio Listener | Works | Blocked | Works | |
| `MusicSource` | Music Source (`.song`) | Works | Blocked | Partial: sampled, sfxr and SoundFont instruments silent | |
| `EngineSound` | Engine Sound | Works | Blocked: `SplittableRandom` | No: reported as unsupported | |
| `NavMeshBoundsComponent` | Nav Mesh Bounds | Works (M6.4) | Blocked | No: reported as unsupported | TD-092 |
| `NavAgent3DComponent` | Nav Agent | Works: with a character controller it walks through physics | Blocked; with a character controller it would also need Jolt | No: reported as unsupported | TD-092 |
| `SculptMeshComponent` | Sculpt Mesh (web editor only) | No: not registered, reported as an unknown component | No: not registered | Works: written by the Babylon editor's sculpt mode | The desktop keeps sculpts in `.sculpt` files and places them as models |

## 2. Features

| Feature | Desktop | TeaVM web | Babylon web | Evidence and notes |
|---|---|---|---|---|
| Scene files (`schemaVersion` 1) | Works | Blocked | Works: refuses another schema version with the reason | Text layout of saved files: M9.3, TD-093 |
| Java scripts | Works: compiled in the editor, precompiled for the game runtime | Blocked: compiled into the build, registry generated | No: each game's script is ported to TypeScript by hand | |
| Kotlin scripts | Works | No: only `.java` sources are compiled | No | |
| Visual-script graphs, interpreted | Works | Blocked | Partial: the web node set (section 1, Graph Script) | |
| Graphs as generated code | Works: Java and Kotlin | Not applicable | Partial: TypeScript; refuses navigation and physics nodes with the reason | M6.4, `GraphTypeScriptGenerator` |
| 2D physics | Works: Box2D | Blocked: pure-Java Box2D | Partial: boxes and tile maps; no circles | |
| 3D physics | Works: Jolt | No: Jolt is native | Works: Jolt in WebAssembly | |
| Genre profiles (Play/Test, TD-088) | Works | Blocked | No | The three profile samples load on Babylon without gameplay |
| Navigation (M6.4) | Works | Blocked | No | |
| Materials (`.material`, TD-081) | Works | Blocked | Works (M2.5) | |
| Terrain (`.terrain`, TD-082) | Works | Blocked | Works (M3.11) | |
| Sky, fog, weather and water bodies (M3.9) | Works | Blocked | No | |
| Post-processing (M6.2) | Works | Blocked | No | |
| glTF models | Works | Blocked | Partial: no model animations | |
| Animation clips (`.anim`) | Works | Blocked | Works | |
| Sprites and tile maps (`.sprite`) | Works | Blocked: `PixelPng` uses `ImageIO` | Works | |
| Sound effects and audio sources | Works | Blocked | Works | Not listened to in this check. Desktop game runtime silent until M9.5 (fixed; see the Audio Source row) |
| Music (`.song`) in a scene | Works | Blocked | Partial: sampled, sfxr and SoundFont instruments silent | Desktop game runtime silent until M9.5 (fixed) |
| Keyboard input | Works | Blocked | Works | |
| Mouse, touch and gamepad in games | Unchecked | Blocked | Unchecked | No sample script reads the mouse, touch or a gamepad (searched in `assets/scripts`) |
| Editor | Works: the desktop editor | Not applicable: no editor in the web build (TD-036) | Partial: first version (outline, details, gizmos, undo, save, sculpt, music) | |

## 3. Release checks

| Package | Windows | Linux | macOS | Notes |
|---|---|---|---|---|
| Packaged editor with the game launcher (`./gradlew :editor-desktop:zipEditorPackage`, jpackage, TD-033) | Works: the 159 MB zip unzipped and run. `ForgeroomEditor.exe` opened `hopper-3d` (180 frames, 0 errors, [screenshot](reports/m9-4/windows-package-editor.png)); `ForgeroomGame.exe --project app/sample-project` played `action-rpg-valley-3d` with terrain and Jolt (120 frames, 0 errors, [screenshot](reports/m9-4/windows-package-game.png)). Scripted samples stop with "No compiled scripts ... Press Play in the editor once", as designed | Unchecked: needs a Linux machine (jpackage cannot cross-build) | Unchecked: needs a Mac | |
| Game runtime from the source tree (`./gradlew :engine-desktop:run`) | Works: smoke tests in every milestone report | Unchecked | Unchecked | |
| TeaVM web build (`./gradlew :engine-web:gdx_teavm_web_js_release_build`) | No: does not compile (see the top of this file) | Not applicable: the output is the same static site on any OS | Not applicable | Last recorded working build: 2026-11-02, in the web runtime spike report. It also stopped at the sample catalog step until 8 samples got their `assets/samples/README.md` rows in this milestone |
| Babylon web build (`npm run build` in `engine-babylon`) | Works: builds; the player opened six samples in headless Chrome (for example [hopper](reports/m9-4/babylon-hopper-3d.png), [valley](reports/m9-4/babylon-action-rpg-valley-3d.png)) | Unchecked | Unchecked | A 6.5 MB bundle (1.4 MB gzipped) |
