# A04 Players and scenario setup

**TD-108 approved on 2026-11-26. A04 is implemented and integrated in the World layout's Players category and the selection card. It is not validated: the human ownership walkthrough is pending.** The owner chose to continue before the A03 walkthrough. Starting HEAD was `85ccc0a` with a clean working tree.

| Area | Working behavior and remaining work |
|---|---|
| Persistence | SceneSerializer keeps the scenario block among preserved top-level fields. GameComponentSerializers registers OwnerComponentSerializer. Player references are UUIDs, so a rename can preserve ownership. Authoring must preserve untouched objectives, rules and extension data. |
| Validation | ScenarioValidator checks duplicate IDs and missing owner, spawn, rule and objective references. Its spawn check resolves an entity but does not require a SpawnPointComponent. Its player checks do not enforce display-name uniqueness. |
| Runtime | SpawnPointComponent and the gameplay profile spawn path already exist. FallRespawner remembers the profile's starting transform. GraphPlayModeExtension instantiates graphs referenced by entities in the Play scene. Neither mechanism establishes runtime control of a scenario player slot. |
| Desktop | WorldCategories displays a milestone note for Players. Placement ownership, editable slots, bounds, spawn assignment and selection reassignment still require implementation and interactive checks. |

The existing [schema](../contracts/scenario.schema.json) defines player names, colours, neutral status, spawn references and optional bounds. [TD-108](../TECHNICAL_DECISIONS.md#td-108-editing-scenario-players-and-object-ownership-alpha-a04) specifies how the editor should create, change and protect those records.

## Implementation

| Part | Where | What it does |
|---|---|---|
| Model | `editor-core/.../scenario/ScenarioSetup` | Reads and writes the player slots and bounds of `scenario`. Every other member (objectives, rules, unknown data, unknown player fields) is kept byte for byte and in its original order. A newer version, or players this version cannot read, makes the block read-only with the reason. |
| Commands | `ChangeScenarioCommand`, `ChangeOwnerCommand` | One undo step per edit. Undo restores the block's exact text. Ownership swaps whole `OwnerComponent` objects, so undo puts back the same components. |
| Editing | `ScenarioEditor` | Add Players (two named, coloured slots, only on this explicit action), Add Player (up to 16), Add Neutral (one), rename (trimmed; blank, over 60 characters or a duplicate ignoring case is refused), recolour (`#rrggbb`), spawn (an entity with a Spawn Point, or none), bounds (finite, each minimum below its maximum), remove, assign owner (one step for many entities; Unowned removes the component). |
| Safeguards | `ScenarioReferences`, `DeleteEntityCommand` | A player is not removed while it owns objects, an objective or rule names it, or any rule has a type this version cannot check. An entity is not deleted while the scenario names it (a spawn, a rule's area, an objective); the status bar names the references. |
| Placement | `LevelSession` | The Owner for new objects defaults to Unowned. A chosen owner applies to placed objects, buildings, characters, scattered copies and downloaded models, in the same undo step as the placement. If that player is removed, placement and the preview refuse until another owner is chosen. Opening another scene resets the owner to Unowned. |
| Validation | `ScenarioValidator` | A spawn must have a Spawn Point. Bounds must be finite with each minimum below its maximum. |
| Desktop | `PlayersPanel`, `SelectionCard`, `WorldCategories` | Players opens a card per slot (colour swatch that cycles the palette, name, spawn list, Remove), Add Player and Add Neutral, map bounds with Set and Clear, the "New objects owned by" list, and the note that Play does not use ownership. The selection card gained an Owner list that reassigns every selected entity in one step and shows an undefined owner by id. `GDXUNITY_EDITOR_DRAWER=Players` opens the category for smoke tests. |

**Choices made within TD-108:**
- One neutral slot through Add Neutral; imported files may have more.
- The colour swatch cycles the eight-colour palette rather than opening a picker.
- Scatter applies the chosen owner too.
- Imported duplicate names are shown with the start of their id.

## Acceptance

| Criterion | Result |
|---|---|
| Inventory and proposal | Met. TD-108 approved on 2026-11-26. |
| Editable players, ownership, bounds and spawn | Implemented and integrated (`ScenarioEditorTest` 14, `PlacementOwnerTest` 4, `PlayersPanelTest` 3). |
| Undo/redo and save/reopen after editing | Met in tests, including a prefab instance whose owner is an override kept by sync. |
| Deletion safeguards, neutral, two players, duplicate names, invalid references | Met in tests. |
| Place two groups, reassign ownership, reopen and inspect | **Pending human walkthrough**: Manually Not Verified (Headless Environment). |

## Known limitations

- Play does not use ownership: there are no teams, diplomacy, economy or unit AI.
- Placement still allows only one Spawn Point per scene, so only one player can have a distinct spawn.
- Scatter-erase removes owned copies without asking.
- An objective's player cannot be chosen in the Rules drawer yet.

Audit verification at HEAD `85ccc0a`: `.\gradlew.bat :engine-scene:test --tests "*ScenarioValidatorTest" --offline --max-workers=1` exited 0 with 5 tests. Implementation test evidence, including the full suite, is in the [26-11-2026 progress report](../progress/progress-reports-26-11-2026.md). No commit, push or destructive Git operation was performed.
