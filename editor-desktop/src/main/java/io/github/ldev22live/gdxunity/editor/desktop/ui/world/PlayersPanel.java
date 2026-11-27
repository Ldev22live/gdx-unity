package io.github.ldev22live.gdxunity.editor.desktop.ui.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Array;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioEditor;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioSetup;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The Players category's palette row (TD-108, roadmap A04): one card per player slot (colour, name, spawn, remove), Add Player and
 * Add Neutral, the map bounds, and the Owner that new placements get. Every button goes through {@link ScenarioEditor}, so each change
 * is one undo step and a refused one says why in the status bar. It rebuilds itself when the scenario or the scene changes.
 * Ownership is saved data only in this version; the panel says so instead of implying diplomacy, economy or unit AI.
 */
public class PlayersPanel extends Table {
    private static final float CARD_WIDTH = 210f;
    private static final float ROW_HEIGHT = 22f;
    private static final float NUMBER_WIDTH = 52f;
    /** Seconds between checks for spawn points added or removed while the panel is open (they change the spawn lists). */
    private static final float SPAWN_RECHECK_SECONDS = 0.5f;
    /** What the panel says about runtime semantics: no fake promises. */
    static final String SEMANTICS_NOTE = "Ownership is saved with the scene. Play does not use it yet: no teams, diplomacy, economy "
        + "or unit AI.";

    /** An entry of an owner list: a player, or Unowned (null id). */
    public record OwnerChoice(String playerId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Skin skin;
    private final EditorState editorState;
    private final ScenarioEditor scenario;
    private final LevelSession level;
    private Scene shownScene;
    private String shownScenario;
    private List<UUID> shownSpawns = List.of();
    private float sinceSpawnCheck;
    private boolean built;
    private SelectBox<OwnerChoice> placementOwner;
    private boolean refreshing;

    public PlayersPanel(Skin skin, EditorState editorState, ScenarioEditor scenario, LevelSession level) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        left().top();
    }

    /** The owner list for {@code setup}: Unowned first, then each player by its display name. */
    public static List<OwnerChoice> ownerChoices(ScenarioSetup setup) {
        List<OwnerChoice> choices = new ArrayList<>();
        choices.add(new OwnerChoice(null, "Unowned"));
        if (setup != null && !setup.isReadOnly()) {
            for (ScenarioSetup.PlayerSlot slot : setup.players()) {
                choices.add(new OwnerChoice(slot.id(), setup.displayName(slot) + (slot.neutral() ? " (neutral)" : "")));
            }
        }
        return choices;
    }

    @Override
    public void act(float delta) {
        Scene scene = editorState.getActiveScene();
        String raw = scene == null ? null : scene.getScenarioJson();
        sinceSpawnCheck += delta;
        boolean spawnsChanged = false;
        if (sinceSpawnCheck >= SPAWN_RECHECK_SECONDS) {
            sinceSpawnCheck = 0f;
            spawnsChanged = !spawnIds().equals(shownSpawns);
        }
        // Identity check on purpose: the scene keeps the same text object until the scenario changes
        if (!built || scene != shownScene || raw != shownScenario || spawnsChanged) {
            rebuild(scene, raw);
        }
        super.act(delta);
    }

    private List<UUID> spawnIds() {
        return scenario.spawnCandidates().stream().map(Entity::getId).toList();
    }

    private void rebuild(Scene scene, String raw) {
        built = true;
        shownScene = scene;
        shownScenario = raw;
        shownSpawns = spawnIds();
        clearChildren();
        if (scene == null) {
            add(new Label("Open a scene to set up players.", skin, "muted"));
            return;
        }
        Optional<ScenarioSetup> read = ScenarioSetup.read(scene);
        if (read.isEmpty()) {
            Table intro = new Table(skin);
            intro.add(new Label("This scene has no players.", skin)).left().row();
            intro.add(button("Add Players", scenario::createScenario)).left().height(ROW_HEIGHT).padTop(4).row();
            intro.add(note()).left().width(CARD_WIDTH * 2).padTop(4);
            add(intro).top().left();
            return;
        }
        ScenarioSetup setup = read.get();
        if (setup.isReadOnly()) {
            Label reason = new Label("Players cannot be edited: " + setup.readOnlyReason(), skin, "muted");
            reason.setWrap(true);
            add(reason).width(CARD_WIDTH * 3).top().left();
            return;
        }
        add(sideColumn(setup)).top().left().padRight(8);
        for (ScenarioSetup.PlayerSlot slot : setup.players()) {
            add(card(setup, slot)).top().left().width(CARD_WIDTH).padRight(6);
        }
        add(boundsCard(setup)).top().left().padRight(6);
    }

    private Label note() {
        Label note = new Label(SEMANTICS_NOTE, skin, "muted");
        note.setWrap(true);
        return note;
    }

    /** Place as (the placement owner), Add Player, Add Neutral. */
    private Table sideColumn(ScenarioSetup setup) {
        Table column = new Table(skin);
        column.defaults().left();
        column.add(new Label("New objects owned by", skin, "muted")).row();
        placementOwner = new SelectBox<>(skin);
        List<OwnerChoice> choices = ownerChoices(setup);
        OwnerChoice current = null;
        for (OwnerChoice choice : choices) {
            if (Objects.equals(choice.playerId(), level.getPlacementOwner())) {
                current = choice;
            }
        }
        boolean ownerGone = current == null;
        if (ownerGone) {
            // Shown as it is, so choosing any real entry is a change; placement refuses until then (LevelSession.placementRefusal)
            current = new OwnerChoice(level.getPlacementOwner(), "Removed player");
            choices.add(current);
        }
        placementOwner.setItems(new Array<>(choices.toArray(new OwnerChoice[0])));
        refreshing = true;
        placementOwner.setSelected(current);
        refreshing = false;
        placementOwner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!refreshing && placementOwner.getSelected() != null) {
                    level.setPlacementOwner(placementOwner.getSelected().playerId());
                    editorState.setStatusMessage("New objects are placed as " + placementOwner.getSelected().label(), false);
                }
            }
        });
        column.add(placementOwner).width(CARD_WIDTH - 20f).height(ROW_HEIGHT).row();
        if (ownerGone) {
            Label gone = new Label("The chosen owner was removed: choose again", skin, "muted");
            gone.setColor(skin.getColor("warning"));
            column.add(gone).row();
        }
        Table buttons = new Table(skin);
        buttons.add(button("Add Player", scenario::addPlayer)).height(ROW_HEIGHT).padRight(4);
        buttons.add(button("Add Neutral", scenario::addNeutral)).height(ROW_HEIGHT);
        column.add(buttons).padTop(4).row();
        Label note = note();
        note.setFontScale(0.85f);
        column.add(note).width(CARD_WIDTH - 20f).padTop(2);
        return column;
    }

    /** One slot: colour swatch (click for the next colour), name, neutral mark, spawn, Remove. */
    private Table card(ScenarioSetup setup, ScenarioSetup.PlayerSlot slot) {
        Table card = new Table(skin);
        card.setBackground("card-bg");
        card.pad(4f);
        card.defaults().left();
        TextButton swatch = new TextButton("●", skin, "flat");
        swatch.getLabel().setColor(Color.valueOf(slot.color()));
        swatch.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                scenario.setPlayerColor(slot.id(), nextColor(slot.color()));
            }
        });
        TextField name = new TextField(slot.name(), skin);
        name.setMaxLength(ScenarioSetup.MAX_NAME_LENGTH);
        Runnable commit = () -> {
            if (!name.getText().trim().equals(slot.name()) && !scenario.renamePlayer(slot.id(), name.getText())) {
                name.setText(slot.name()); // refused: the status bar says why; the field shows the saved name again
            }
        };
        name.setTextFieldListener((field, key) -> {
            if (key == '\r' || key == '\n') {
                commit.run();
            }
        });
        name.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    commit.run();
                }
            }
        });
        Table top = new Table(skin);
        top.add(swatch).width(22f).height(ROW_HEIGHT);
        top.add(name).growX().height(ROW_HEIGHT);
        card.add(top).growX().row();

        Table spawnRow = new Table(skin);
        spawnRow.add(new Label("Spawn", skin, "muted")).padRight(4);
        SelectBox<SpawnChoice> spawn = new SelectBox<>(skin);
        List<SpawnChoice> spawns = new ArrayList<>();
        spawns.add(new SpawnChoice(null, "None"));
        SpawnChoice selected = spawns.get(0);
        for (Entity candidate : scenario.spawnCandidates()) {
            SpawnChoice choice = new SpawnChoice(candidate.getId(), candidate.getName());
            spawns.add(choice);
            if (candidate.getId().toString().equals(slot.spawn())) {
                selected = choice;
            }
        }
        if (slot.spawn() != null && selected.entityId() == null) {
            selected = new SpawnChoice(null, "Missing (" + slot.spawn().substring(0, 8) + ")");
            spawns.add(selected);
        }
        spawn.setItems(new Array<>(spawns.toArray(new SpawnChoice[0])));
        spawn.setSelected(selected);
        SpawnChoice initial = selected;
        spawn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                SpawnChoice chosen = spawn.getSelected();
                if (chosen != null && chosen != initial) {
                    scenario.setSpawn(slot.id(), chosen.entityId());
                }
            }
        });
        spawnRow.add(spawn).growX().height(ROW_HEIGHT);
        card.add(spawnRow).growX().padTop(2).row();

        Table bottom = new Table(skin);
        bottom.add(new Label(slot.neutral() ? "Neutral" : "Player", skin, "muted")).expandX().left();
        bottom.add(button("Remove", () -> scenario.removePlayer(slot.id()))).height(ROW_HEIGHT);
        card.add(bottom).growX().padTop(2);
        return card;
    }

    /** The playable area: minimum and maximum X and Z in scene units, Set and Clear. */
    private Table boundsCard(ScenarioSetup setup) {
        Table card = new Table(skin);
        card.setBackground("card-bg");
        card.pad(4f);
        Optional<ScenarioSetup.Bounds> bounds = setup.bounds();
        TextField minX = numberField(bounds.map(b -> b.minX()).orElse(null));
        TextField minZ = numberField(bounds.map(b -> b.minZ()).orElse(null));
        TextField maxX = numberField(bounds.map(b -> b.maxX()).orElse(null));
        TextField maxZ = numberField(bounds.map(b -> b.maxZ()).orElse(null));
        card.add(new Label(bounds.isPresent() ? "Map bounds" : "Map bounds (none)", skin, "muted")).colspan(4).left().row();
        card.add(new Label("X", skin, "muted")).padRight(2);
        card.add(minX).width(NUMBER_WIDTH).height(ROW_HEIGHT).padRight(2);
        card.add(new Label("to", skin, "muted")).padRight(2);
        card.add(maxX).width(NUMBER_WIDTH).height(ROW_HEIGHT).row();
        card.add(new Label("Z", skin, "muted")).padRight(2);
        card.add(minZ).width(NUMBER_WIDTH).height(ROW_HEIGHT).padRight(2);
        card.add(new Label("to", skin, "muted")).padRight(2);
        card.add(maxZ).width(NUMBER_WIDTH).height(ROW_HEIGHT).row();
        Table buttons = new Table(skin);
        buttons.add(button("Set", () -> {
            try {
                scenario.setBounds(new ScenarioSetup.Bounds(parse(minX), parse(minZ), parse(maxX), parse(maxZ)));
            } catch (IllegalArgumentException e) {
                editorState.setStatusMessage("Map bounds not set: " + e.getMessage(), true);
            }
        })).height(ROW_HEIGHT).padRight(4);
        buttons.add(button("Clear", () -> scenario.setBounds(null))).height(ROW_HEIGHT);
        card.add(buttons).colspan(4).left().padTop(2);
        return card;
    }

    private TextField numberField(Double value) {
        TextField field = new TextField(value == null ? "" : format(value), skin);
        field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '.');
        return field;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** @throws IllegalArgumentException when the field is empty or not a number */
    private static double parse(TextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("enter all four numbers");
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a number", e);
        }
    }

    private static String nextColor(String color) {
        List<String> colors = ScenarioSetup.SLOT_COLORS;
        int at = -1;
        for (int i = 0; i < colors.size(); i++) {
            if (colors.get(i).equalsIgnoreCase(color)) {
                at = i;
            }
        }
        return colors.get((at + 1) % colors.size());
    }

    private TextButton button(String label, Runnable action) {
        TextButton button = new TextButton(label, skin);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    private record SpawnChoice(UUID entityId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
