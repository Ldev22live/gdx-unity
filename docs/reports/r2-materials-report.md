# Roadmap R2 — Materials and the Material Editor: Report, Part 1 (2026-11-13)

Phase R2 of [13-nov-forgeroom-roadmap.md](../13-nov-forgeroom-roadmap.md). **Status: partly done.** The material asset, its
rendering on primitives and in-place editing exist. The Material Editor tab, textures, glTF model materials and the web player do not.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `d896331`. Ending: `d896331` (no commit was made).
- The working tree already held many uncommitted files from the owner. Files this part touched that already had owner changes:
  `EditorApplication.java`, `InspectorPanel.java`, `AGENTS.md`, `TECHNICAL_DECISIONS.md`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`.
- **Approval:** the roadmap and AGENTS.md section 4 require approval for a new module. The owner was asked and chose "R2 now,
  approve engine-material" (2026-11-13). The decision is recorded as TD-081 (Approved).

## 1. Acceptance criteria (roadmap R2)

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | `.material` asset: PBR parameters, texture slots, tiling, double-sided, alpha mode; primitives and imported glTF models can use one | **Partly met** | The file holds all of it and round-trips. Primitives use materials. Models do not. Texture slots, tiling, normal and occlusion are stored but not drawn |
| 2 | Material Editor tab (node graph, live preview sphere, parameter instances) | **Not met** | Not started. Materials are edited in the Details of a surface that uses them |
| 3 | Details panel material slot with drag and drop from the Content Browser | **Partly met** | A Material section with the file, Use Selected Material, New Material and No Material. No drag and drop |
| 4 | Web player reads the same file | **Not met** | The web player ignores `materialPath` |
| 5 | Done when: a sample's rock, wood and water are separate `.material` files edited in the editor, and the change shows in the viewport and in Play | **Partly met** | `materials-3d.json` uses separate Rock, Wood, Water, Gold, Lava and Glass files; the edit path is tested (command, file, shared object, renderer); nobody has done it in the running editor |

## 2. What was done

- **`engine-material`** (new, headless): `Material`, `MaterialIO`, `MaterialLibrary`. Values are validated, a changed material counts a
  revision, files are strict about what they read, and a missing or broken file gives no material and a stored reason.
- **Rendering:** `MaterialAttributes` maps a material to gdx-gltf's attributes; `SceneRenderer3D` applies it to Mesh Primitives and
  re-applies only when the revision changes. The viewport and Play use the same renderer.
- **Editing:** a Material section in the Mesh Primitive Details edits the material in place; every change is an undoable command that
  also rewrites the file. A material is shared, so one edit reaches every surface using it.
- **Sample:** a showroom scene and six materials.
- **Two bugs found by the new tests while writing them:** emissive colour is a plain libGDX `ColorAttribute` in gdx-gltf, not a
  `PBRColorAttribute`; and libGDX's `Color` constructor clamps to 1, which silently capped emissive strength.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-material:test` | 0 | 15 tests, 0 failures |
| `./gradlew :engine-render:test` | 0 | 128 tests, 0 failures (9 new) |
| `./gradlew :editor-core:test --tests "*MaterialEditingTest"` | 0 | 4 tests, 0 failures |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*EditabilityAuditTest" --tests "*SampleGamesTest"` | 0 | 6 and 41 tests, 0 failures |
| `./gradlew :engine-desktop:run --args="--scene samples/materials-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)"; one frame looked at |
| `./gradlew test --continue` | 0 | **1,783 tests, 0 failures, 1 skipped** (the same `GameExporterTest` Linux assumption) |
| `npx vitest run` in `engine-babylon` | 0 | 477 tests, 0 failures |

Per module: `engine-material` 15 (new), `engine-render` 128, `editor-core` 395, `editor-desktop` 115; the rest unchanged.

## 3b. Part 2: textures (2026-11-14)

- Primitives now have texture coordinates; `MaterialTextures` loads the textures a material names; `MaterialAttributes` turns each filled slot into a
  texture attribute with the material's tiling and offset, plus normal scale and occlusion strength.
- Details: a text row per slot (png or jpg of the project), "Use Selected Image as Color Tex", Tile U/V, Offset U/V, Normal, AO; all undoable and saved.
- Sample: three more materials (Brick with a normal map, Checker, Neon with an emissive texture) and four generated textures in `assets/textures`.
- Evidence: `MaterialTexturesTest` (8), `MaterialEditingTest` (5), `EditabilityAuditTest` (+1); `./gradlew test --continue` exit 0;
  per module `engine-material` 15, `engine-render` 136, `editor-core` 396 (1 skipped), `editor-desktop` 116, 0 failures.
  Game runtime on `materials-3d.json`, 120 frames, 0 errors; one frame looked at: checkerboard sphere, bricks and neon stripes are drawn.
- **Not verified:** the new Details rows were not used by anyone; the editor viewport with textures was not seen; the brick normal map's bumps
  were not confirmed by eye; web player ignores materials and textures.

## 4. Known limits and what is next

- **Not seen in the running editor** (**Manually Not Verified (Headless Environment)**): the Details section, the buttons, and the
  viewport with materials. Whether the Content Browser lists a newly created `.material` without a refresh was not checked.
- **Next slice, in order:** the Material Editor tab (live preview sphere, drag and drop of textures),
  a material slot on Model components, then the web player.
- **Agent tools** for materials (create, assign, set a parameter) are missing because the agent tool context has no asset root or
  library; roadmap rule 8 and R10-A need them.
- R1's remaining items (templates, model opening, "used by", graph conversions) were not touched.

## 5. Git

No commit, push, stash, reset or branch change was performed.
