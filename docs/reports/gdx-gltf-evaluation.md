# gdx-gltf Evaluation for Phase 13 (3D Asset Pipeline)

**Date:** 2026-10-29 · **Decision context:** TD-026 (Phase 13 = asset pipeline), `AGENTS.md` §1 (new dependencies
need approval) · **Status:** Spike passed and library adopted (TD-030, 2026-10-29)

## 1. Question

Should Phase 13 build glTF import, PBR materials, image-based lighting and shadows on the open-source
**gdx-gltf** library instead of implementing them in GdxUnity?

## 2. Verified facts

| Item | Finding | Source |
|---|---|---|
| Project | `mgsx-dev/gdx-gltf`, not archived, 249 stars, 22 open issues | GitHub API, checked 2026-10-29 |
| License | Apache License 2.0 | GitHub API / repository |
| Latest release | **2.3.0**, published 2026-02-05 (last push the same day) | GitHub releases API |
| Recent changes | 2.3.0: texture transforms in the depth shader (shadows). 2.2.x: KHR transmission/volume/IOR/specular/iridescence/emissive-strength, cascaded shadow maps, HDR/RGBE, reworked skybox, ambient fix | `CHANGES.md` |
| libGDX requirement | 2.x requires libGDX **1.9.11+**; the library's own build compiles against `gdxVersion = '1.9.11'` | README, `build.gradle` |
| Distribution | JitPack: `com.github.mgsx-dev.gdx-gltf:gltf:<version>` (our build already lists `https://jitpack.io`) | README |
| Features | glTF 2.0 / GLB loading, PBR shaders (metallic/roughness, normal maps), IBL (pre-baked HDRI or quick `IBLBuilder`), shadows and skybox via its scene manager, skinning, multiple animations, morph targets, glTF export | README |
| Extensions | KHR_texture_transform, KHR_lights_punctual, KHR_materials_unlit/transmission/volume/ior/specular/iridescence/emissive_strength. Not supported: KHR_materials_pbrSpecularGlossiness. KTX2 and Draco not mentioned | README |
| Platform notes | Full roughness with IBL needs `textureLod` (GL 3 / extension; fine on desktop GL 3.x). Integer-index meshes are split into 64k chunks. Bone count is configured (`numBones`) | README |

## 3. Fit with GdxUnity

- **Architecture:** it builds on libGDX's own `Model`, `ModelInstance`, `ModelBatch` and shader provider APIs,
  the same facilities `SceneRenderer3D` uses (`AGENTS.md` §7 "stay on LibGDX facilities"). Imported assets
  would become `ModelInstance`s rendered with its `PBRShaderProvider`; `MeshPrimitiveComponent` keeps working.
- **Modules:** loading and rendering belong in `engine-render`; import settings and the Content Browser in
  `editor-core`/`editor-desktop`. The runtime gains the dependency too, which is intended: exported games must
  render imported assets.
- **Scope saved:** a glTF loader, PBR and IBL shaders, shadow mapping and skinning amount to several milestones
  of renderer work if written in-house.

## 4. Risks

1. **Binary compatibility (main risk, unverified).** The library is compiled against libGDX 1.9.11; GdxUnity uses
   1.14.2. libGDX keeps its 3D API largely stable, but a removed or changed method would surface only at run
   time (`NoSuchMethodError`). This must be tested before adoption (section 6).
2. **Distribution through JitPack.** Builds depend on JitPack's availability and on it building the tag
   reproducibly. Mitigation: pin the exact version, enable Gradle dependency verification (checksums) for it,
   and keep the decision record noting the source.
3. **Single maintainer.** The project is maintained by one author; the release cadence is irregular (2.2.x in
   one year, 2.3.0 the next). Mitigation: Apache-2.0 allows forking if it stalls; keep the integration behind
   GdxUnity's own components (`ModelComponent`, renderer) so callers do not depend on gdx-gltf types.
4. **Unmentioned formats.** KTX2/Basis textures and Draco mesh compression are not listed; imports using them
   must produce clear warnings (design doc §5.3), not silent failures.
5. **Shaders on older GPUs.** IBL roughness needs `textureLod`; acceptable for the desktop targets.

## 5. Alternatives considered

| Option | Assessment |
|---|---|
| Write our own glTF loader + PBR/IBL/shadow shaders | Full control, no new dependency; several milestones of work and the highest defect risk |
| LWJGL Assimp module (native importer, many formats) | Broad import formats, but gives raw data only; PBR rendering, IBL and shadows still to be written; adds native binaries |
| libGDX built-in G3DJ/G3DB via `fbx-conv` | Supported by libGDX, but requires an external conversion tool, has no PBR, and is not where modern tools export |
| **gdx-gltf** | Covers import + PBR + IBL + shadows + skinning on libGDX's own APIs; risks listed above |

## 6. Required spike before adoption

A throwaway project (outside the repository) was prepared but **not run**: running third-party code fetched from
JitPack requires the project owner's approval. The spike:

1. Depends on `gdx:1.14.2`, `gdx-backend-headless:1.14.2`, `gdx-platform:1.14.2:natives-desktop` and
   `com.github.mgsx-dev.gdx-gltf:gltf:2.3.0` (JitPack).
2. In a headless application, loads the official minimal glTF (one triangle, embedded buffer) with `GLTFLoader`
   and checks the node, mesh and vertex counts; creates `PBRShaderProvider.createDefaultConfig()`.
3. **Pass:** no linkage errors and the expected counts. Headless mode cannot compile shaders, so the rendering
   path (PBR shaders, shadows, IBL) must then be checked in the editor with a real model (manual check).

## 7. Recommendation

**Adopt gdx-gltf 2.3.0 for Phase 13, conditional on the spike passing**, behind GdxUnity-owned components, with
the version pinned and dependency verification enabled. If the spike fails on linkage, evaluate whether the break
is small enough to fix in a fork (Apache-2.0) before falling back to writing the loader and shaders in-house.

## 8. Decisions requested

1. Approve running the spike (it downloads and executes gdx-gltf from JitPack in a throwaway folder).
2. If it passes: approve adding gdx-gltf as a dependency of `engine-render` (recorded as a new TD).
