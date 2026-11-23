# M2.5: Materials in the Web Player

**Date:** 2026-11-17. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart; no decision record. The web reads the existing `.material` format of TD-081 unchanged.

**Why this milestone:** R3's remaining rows wait on R2b, and R1b's last row (M1b.5) needs an unfamiliar human tester. M2.5 was the first startable row in the chart's phase order with its dependency (M2.1) built and no gate.

## 1. Acceptance criteria

| # | Criterion (chart row M2.5) | Status | Evidence |
|---|---|---|---|
| 1 | Babylon reads the same file | **Met** | `parseMaterialFile` reads all nine shipped materials. It keeps the desktop's defaults for missing fields and refuses what `MaterialIO` refuses (`material.test.ts`, 4 tests) |
| 2 | Support matrix updated | **Met, as far as one exists** | The web status is written in `FEATURE_TEST_MATRIX.md` (Materials row) and in `engine-babylon/README.md`. The full desktop and web backend matrix is roadmap M9.4, which has not started |
| 3 | Done-when: "The web build shows the same material" | **Met, with the differences listed** | The `materials-3d.json` sample in headless Chrome ([web](m2-5/web-materials-3d.png)) and in the desktop game runtime ([desktop](m2-5/desktop-materials-3d.png)), both looked at. Every material shows: gold metal with highlights, glowing lava, see-through glass and water, matte rock and wood, brick, checker and neon textures; the red ball without a material keeps its colour. Tests check every primitive gets its material, with the desktop's colour conversion, alpha modes and texture placement |

**Differences seen between the two pictures:**
- **Box textures:** on boxes, textures run in a different direction on some faces (the neon stripes are vertical on the desktop's front face and horizontal on the web's). LibGDX's and Babylon's box meshes lay out texture coordinates differently per face. That is a primitive-mesh difference, not a material one.
- **Lighting:** the ground (rock) and the pond (water) are lit differently. The web lights with a hemisphere light plus the reflection cube; the desktop uses gdx-gltf's image-based sky light. The web's ground is darker and grey, and its water bluer.
- **Background:** the web clears to the Sky Light's colour and the desktop to dark slate. That difference predates this milestone.
- **Framing:** the camera framing differs because the windows have different aspect ratios.

## 2. What was built (`engine-babylon`)

- **`src/material/material.ts`:** `parseMaterialFile`, `defaultMaterial`, `toLinear` (power 2.2, as the desktop), `emissiveLinear` (white when only an emissive texture is set, as `MaterialAttributes`) and `opacity`.
- **`src/runtime/MaterialLoader.ts`:**
  - `createPbrMaterial` maps a material onto Babylon's `PBRMaterial` as the desktop's `MaterialAttributes.apply` does. Planes are always two-sided.
  - Textures:
    - base colour and emissive are sRGB; normal, metallic-roughness and occlusion are data;
    - tiling, offset and repeat wrap are set per material;
    - the metallic-roughness texture uses glTF's layout (roughness in green, metal in blue);
    - normal scale and occlusion strength are applied.
  - Point lights keep the web's non-physical falloff.
  - `createSkyReflection` builds an 8-pixel cube of the scene's Sky Light colours for shiny materials to reflect. There is none without a Sky Light (the desktop then has only flat ambient).
  - `loadPendingMaterials` reads each file once. Primitives with the same material and sidedness share one Babylon material. A failure is reported and the primitive keeps its colour.
  - The texture and reflection factories can be replaced for headless tests.
- **`SceneBuilder`:**
  - a Mesh Primitive with a `materialPath` becomes a pending material;
  - `BuiltScene` gains `pendingMaterials` and `materialFailures`.
- **`ScenePlayer`:** loads materials with every scene and shows failures as asset problems.
- **Sync:** `scripts/sync-samples.mjs` copies `assets/materials` and `assets/textures` to `public/`, which is git-ignored.
- **Docs:** the web README gains Materials, Terrain and `?scene=` entries.
- **Test:** `test/material.test.ts` (11).

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `npx tsc --noEmit` (in `engine-babylon`) | 0 | no errors |
| `node scripts/sync-samples.mjs` | 0 | 9 materials, 4 textures copied |
| `npx vitest run test/material.test.ts` | 1, then 0 | Two tests first failed: a headless Babylon engine cannot make raw cube textures. The reflection became a replaceable factory, with its colours a pure function under test. Then 11 of 11 |
| `npx vitest run --maxWorkers=1` | 0 | **39 files, 503 tests, all passing** (492 before this milestone) |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--scene samples/materials-3d.json --smoke-test=120"` | 0 | 120 frames, 0 runtime errors |
| `npx vite --port 5179`, then headless Chrome on `?scene=materials-3d.json` | 0 | the web screenshot; the server was stopped afterwards |

No Java code changed in this milestone.

## 4. Diff review

- **New:**
  - `src/material/material.ts`, `src/runtime/MaterialLoader.ts`;
  - `test/material.test.ts`;
  - this report and `docs/reports/m2-5/`.
- **Changed:**
  - `src/runtime/SceneBuilder.ts`, `src/runtime/ScenePlayer.ts`;
  - `scripts/sync-samples.mjs`, `.gitignore`, `README.md`;
  - the docs (chart, INDEX, FEATURE_TEST_MATRIX).
- The same web files also carry this session's M3.11 changes, which are not yet committed. No files outside this repository were touched.

## 5. Known limitations and next steps

- **Box texture direction** differs on some faces (see section 1). A follow-up could give Babylon's boxes the libGDX per-face UV layout (`faceUV`).
- **Lighting is not physically matched.**
  - The reflection cube is unblurred, so rough materials reflect it as sharply as smooth ones (the desktop pre-filters its sky).
  - The web has no image-based diffuse light.
- **Not covered:** materials on glTF models (the desktop does not apply them either); the Material Editor (M2.3); instances (M2.4).
- **Untested environments:** a real GPU and browsers other than headless Chrome. Per the project's plan, visual checks are deferred to the packaged builds.
- **Next by phase order:**
  - M2.3 (Material Editor) needs a decision record and approval.
  - The remaining unblocked rows are breadth work: M6.2 post-processing, M6.4 navigation, M9.3 to M9.5 pipeline, M8.1 listening (needs a person) and the audits MC.1, MP.1 and MT.8.

## 6. Git

No commit, push, stash, reset or other Git operation was performed.
