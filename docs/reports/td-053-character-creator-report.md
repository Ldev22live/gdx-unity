# Character creator report (TD-053)

**Date:** 2026-11-07 · **Decision:** TD-053 (approved with changes: "put the code as selectable options in the anmation edit so that animation templates can be applied to the models I approve creation of the new modules yes the editor should work without makehuman installed", as typed) · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)
**Asked:** use features from the MakeHuman installation on this machine in Forgeroom. Read-only look at the install, licence checked (assets CC0, code AGPL: no code used); the owner allowed copying the CC0 data.

## What was built

- **Data.** `tools/convert_makehuman_assets.py` (deterministic: two runs give identical files) converts a subset of MakeHuman's CC0 data into `assets/characters/makehuman` (about 8.9 MB, credits and licence files included): body mesh, 421 morph targets, 179 sliders, 53-bone game rig, skin weights.
- **`engine-character` (new module).** `CharacterData`, `CharacterSpec`, `CharacterBuilder` (macro blend of gender and age, muscle, weight, sliders, height, skeleton follows the body), `GltfCharacterWriter`, `HumanoidTemplates` (bakes the `.anim` humanoid clips onto the rig).
- **`editor-character` (new module).** `CharacterSession` (settings with undo/redo, template selection, bake, open, place as one undoable scene command), `CharacterBakeTool` (`gradlew :editor-character:bakeCharacter`).
- **Editor.** `CharacterCreatorPanel` in the Animations tab (Character button beside Clip Library): body settings, skin color, a toggle per animation template, 179 sliders in collapsible groups, Undo/Redo/Reset/Open, Create / Update character.
- **Sample.** `character-creator-3d`: three baked characters (tall man, short woman, old man) each playing a template.

## Acceptance criteria

| # | Criterion (TD-053) | Result | Evidence |
|---|---|---|---|
| 1 | Converter deterministic, lists every file read, credits and licence present | Met | two runs give the same hash; `CREDITS.md` lists files and targets |
| 2 | Zero change leaves the base body; one slider moves only the vertices its target names, by its offsets | Met | `CharacterBuilderTest.shouldMoveOnlyTheVerticesASliderTargetNamesByItsOffsets` |
| 3 | Skin weights per vertex add up to 1, at most 4 bones | Met | `shouldGiveEveryDrawnVertexWeightsThatAddUpToOne` (4 is the format's limit) |
| 4 | Baked glTF loads through `ModelAssets` with 53 bones and plays Walk | Met | `ModelAssetsTest.shouldLoadABakedCharacterWithItsSkeletonAndTheHumanoidTemplates`, `shouldPoseABakedCharactersThighWithTheWalkTemplate` (thigh -28, 0, +28 degrees) |
| 5 | Characters of different build differ by the measured amount | Met | men taller than women, child and baby shorter, height scale exact, heavier torso wider (`CharacterBuilderTest`); the muscle direction is **not** tested |
| 6 | Slider edits undo and redo; settings round trip | Met | `CharacterSessionTest`, `CharacterSpecTest` |
| 7 | Animation templates selectable in the Animations tab and baked into the model | Met in tests (`shouldOfferTheHumanoidClipsAsTemplatesAndBakeOnlyTheSelectedOnes`); the panel itself **not used by a person** | editor smoke tests 0 errors |
| 8 | Sample runs in the game runtime | Met | game runtime 240 of 240 frames, 0 errors; `SampleGamesTest.shouldShowCharactersWhoseModelsCarryTheAnimationTheyPlay` |
| 9 | A person looks at the preview and the baked characters | **Not met** | Manually Not Verified |
| 10 | All other tests unchanged | Met | table below |

## Commands and results (2026-11-07, Windows)

`python tools/convert_makehuman_assets.py "<MakeHuman folder>"` (twice, equal hashes); per module `gradlew :engine-character:test`, `:editor-character:test`, `:engine-render:test --tests "*ModelAssetsTest"`, `:editor-desktop:test --tests "*SampleGamesTest"`; `engine-desktop:run --args="--scene samples/character-creator-3d.json --smoke-test=240"` and `editor-desktop:run --args="--smoke-test"` (also with `--scene samples/character-creator-3d.json --play`): 0 errors. Final: `gradlew test --continue --rerun-tasks` → exit 1: only the 2 known `BundledDemoSongTest` failures in `:editor-audio` (pre-existing, unrelated).

| Module | Before | After | Notes |
|---|---|---|---|
| engine-character | (new) | 19 | `CharacterBuilderTest` 11, `CharacterSpecTest` 3, `GltfCharacterWriterTest` 5 |
| editor-character | (new) | 8 | `CharacterSessionTest` |
| engine-render | 99 | 101 | +2 `ModelAssetsTest` |
| editor-desktop | 70 | 72 | +1 `SampleGamesTest` for TD-053 (and +1 earlier for TD-052) |
| engine-scene | 117 | 124 | TD-052 (+7) |
| engine-desktop | 59 | 69 | TD-052 (+10) |
| editor-core | 356 (1 skipped) | 357 (1 skipped) | TD-052 (+1) |
| others | unchanged | unchanged | 0 failures |
| editor-audio | 55 | 55 | the 2 known failures |

## Problems found on the way

- The first conversion made a 113 m tall body: `targets.npz` stores offsets as int16 thousandths; a height test now guards it.
- The "average build" macro targets are empty; the gender and age shape comes from the African, Asian and Caucasian anchors, mixed equally.
- 193 drawn vertices (mouth, eyelids) had no weight because the game rig has no face bones; they now follow the head.
- A shell quirk with quotes in heredocs cost several rewrites of files (no effect on the result).

## Manually Not Verified (Headless Environment)

How the characters look and animate, the creator panel's layout and feel, whether typing a number in the fields is pleasant. Nothing here is Validated.

## Known limitations and next steps

- Body only: no eyes, teeth, tongue, eyebrows or hair (separate MakeHuman assets, not converted).
- No live 3D preview while typing; Create / Update writes the model and the scene shows it.
- Number fields, not slider widgets; no ethnicity sliders; fingers, toes, clavicles and spine_02 do not animate.
- The web player was not run with a character.
- Next: TD-054 (a visual pass on the recent features).

## Git

No commit, push or other Git write was done.
