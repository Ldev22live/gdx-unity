package io.github.ldev22live.gdxunity.editor.desktop.ui.world;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.scenario.RulesEditor;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioSetup;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntime;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Rules category's palette row (TD-109, roadmap A05): one card per rule (on/off, name, once or repeat, order, the condition and
 * its effects), the objectives, Add Rule, Add Area and Add Objective, and the validation findings Play would skip rules for. Every
 * control goes through {@link RulesEditor}, so each change is one undo step and a refused one says why in the status bar. A rule of
 * a type this version does not know is shown as kept, not editable. Rules run in Play only; the rule log is in the Output Log.
 */
public class RulesPanel extends Table {
    private static final float CARD_WIDTH = 330f;
    private static final float SIDE_WIDTH = 190f;
    private static final float ROW_HEIGHT = 22f;
    private static final float NUMBER_WIDTH = 46f;
    /** Seconds between checks for areas added or removed while the panel is open (they change the area lists). */
    private static final float AREA_RECHECK_SECONDS = 0.5f;
    /** What the panel says about when rules run. */
    static final String RUN_NOTE = "Rules run in Play, top to bottom; each one that fires is written to the Output Log.";

    private final Skin skin;
    private final EditorState editorState;
    private final RulesEditor rules;
    private final Supplier<Vector3> viewCentre;
    private Scene shownScene;
    private String shownScenario;
    private List<UUID> shownAreas = List.of();
    private float sinceAreaCheck;
    private boolean built;

    /**
     * @param viewCentre where Add Area puts a new area (the centre of the editor's view)
     */
    public RulesPanel(Skin skin, EditorState editorState, RulesEditor rules, Supplier<Vector3> viewCentre) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.rules = Objects.requireNonNull(rules, "rules cannot be null");
        this.viewCentre = Objects.requireNonNull(viewCentre, "viewCentre cannot be null");
        left().top();
    }

    @Override
    public void act(float delta) {
        Scene scene = editorState.getActiveScene();
        String raw = scene == null ? null : scene.getScenarioJson();
        sinceAreaCheck += delta;
        boolean areasChanged = false;
        if (sinceAreaCheck >= AREA_RECHECK_SECONDS) {
            sinceAreaCheck = 0f;
            areasChanged = !areaIds().equals(shownAreas);
        }
        // Identity check on purpose: the scene keeps the same text object until the scenario changes
        if (!built || scene != shownScene || raw != shownScenario || areasChanged) {
            rebuild(scene, raw);
        }
        super.act(delta);
    }

    private List<UUID> areaIds() {
        return rules.areaCandidates().stream().map(Entity::getId).toList();
    }

    private void rebuild(Scene scene, String raw) {
        built = true;
        shownScene = scene;
        shownScenario = raw;
        shownAreas = areaIds();
        clearChildren();
        if (scene == null) {
            add(new Label("Open a scene to add rules.", skin, "muted"));
            return;
        }
        Optional<ScenarioSetup> setup = ScenarioSetup.read(scene);
        if (setup.isPresent() && setup.get().isReadOnly()) {
            Label reason = new Label("Rules cannot be edited: " + setup.get().readOnlyReason(), skin, "muted");
            reason.setWrap(true);
            add(reason).width(CARD_WIDTH * 2).top().left();
            return;
        }
        List<ScenarioValidator.Problem> problems = rules.problems();
        add(sideColumn(problems)).top().left().width(SIDE_WIDTH).padRight(8);
        add(objectivesCard()).top().left().padRight(6);
        List<JsonValue> list = rules.rules();
        for (int i = 0; i < list.size(); i++) {
            add(ruleCard(list.get(i), i, list.size(), problemFor(problems, i))).top().left().width(CARD_WIDTH).padRight(6);
        }
    }

    private static String problemFor(List<ScenarioValidator.Problem> problems, int index) {
        String prefix = "scenario.rules[" + index + "]";
        for (ScenarioValidator.Problem problem : problems) {
            if (problem.path().startsWith(prefix)) {
                return (problem.severity() == ScenarioValidator.Severity.ERROR ? "Not run: " : "Kept, not run: ") + problem.message();
            }
        }
        return null;
    }

    private Table sideColumn(List<ScenarioValidator.Problem> problems) {
        Table column = new Table(skin);
        column.defaults().left();
        Table buttons = new Table(skin);
        buttons.add(button("Add Rule", rules::addRule)).height(ROW_HEIGHT).padRight(4);
        buttons.add(button("Add Area", this::addArea)).height(ROW_HEIGHT);
        column.add(buttons).row();
        column.add(button("Add Objective", () -> rules.addObjective("New objective"))).height(ROW_HEIGHT).padTop(4).row();
        long errors = problems.stream().filter(p -> p.severity() == ScenarioValidator.Severity.ERROR).count();
        Label status = new Label(errors == 0 ? "No problems" : errors + (errors == 1 ? " problem" : " problems")
            + ": see the red notes", skin, "muted");
        status.setColor(skin.getColor(errors == 0 ? "success" : "error"));
        column.add(status).padTop(4).row();
        Label note = new Label(RUN_NOTE, skin, "muted");
        note.setWrap(true);
        note.setFontScale(0.85f);
        column.add(note).width(SIDE_WIDTH).padTop(2);
        return column;
    }

    private void addArea() {
        Scene scene = editorState.getActiveScene();
        if (scene != null && scene.getGameMode() == GameMode.MODE_2D) {
            editorState.setStatusMessage("In 2D, use an entity with a sensor collider as the area", true);
            return;
        }
        rules.addArea(new Vector3(viewCentre.get()));
    }

    // ---- objectives ----------------------------------------------------------------------------------------------------------

    private Table objectivesCard() {
        Table card = card();
        card.add(new Label("Objectives", skin, "muted")).colspan(3).left().row();
        List<JsonValue> objectives = rules.objectives();
        if (objectives.isEmpty()) {
            card.add(new Label("None yet", skin, "muted")).colspan(3).left().row();
        }
        for (JsonValue objective : objectives) {
            String id = objective.getString("id", "");
            String text = objective.getString("text", "");
            boolean hidden = "hidden".equals(objective.getString("initial", "active"));
            card.add(textField(text, value -> rules.setObjectiveText(id, value))).width(170f).height(ROW_HEIGHT);
            card.add(button(hidden ? "Hidden" : "Shown", () -> rules.setObjectiveHidden(id, !hidden))).height(ROW_HEIGHT).padLeft(2);
            card.add(button("x", () -> rules.removeObjective(id))).height(ROW_HEIGHT).padLeft(2).row();
        }
        return card;
    }

    // ---- rules ---------------------------------------------------------------------------------------------------------------

    private Table ruleCard(JsonValue rule, int index, int count, String problem) {
        Table card = card();
        String id = rule.getString("id", "");
        boolean enabled = rule.getBoolean("enabled", false);
        boolean once = rule.getBoolean("once", true);

        Table top = new Table(skin);
        top.add(new Label((index + 1) + ".", skin, "muted")).padRight(4);
        top.add(button(enabled ? "On" : "Off", () -> rules.setRuleEnabled(id, !enabled))).height(ROW_HEIGHT).padRight(2);
        top.add(textField(rule.getString("name", ""), value -> rules.renameRule(id, value))).growX().height(ROW_HEIGHT);
        top.add(button(once ? "Once" : "Repeat", () -> rules.setRuleOnce(id, !once))).height(ROW_HEIGHT).padLeft(2);
        TextButton up = button("<", () -> rules.moveRule(id, -1));
        up.setDisabled(index == 0);
        TextButton down = button(">", () -> rules.moveRule(id, 1));
        down.setDisabled(index == count - 1);
        top.add(up).height(ROW_HEIGHT).padLeft(2);
        top.add(down).height(ROW_HEIGHT);
        top.add(button("x", () -> rules.removeRule(id))).height(ROW_HEIGHT).padLeft(2);
        card.add(top).growX().row();

        JsonValue when = rule.get("when");
        Optional<RulesEditor.Condition> condition = RulesEditor.Condition.of(when == null ? null : when.getString("type", null));
        if (condition.isEmpty()) {
            card.add(new Label("When: '" + (when == null ? "?" : when.getString("type", "?"))
                + "' (a newer type, kept as it is)", skin, "muted")).left().row();
        } else {
            card.add(conditionRow(id, condition.get(), when)).growX().padTop(2).row();
        }
        JsonValue then = rule.get("then");
        int effectIndex = 0;
        for (JsonValue effect = then == null ? null : then.child; effect != null; effect = effect.next, effectIndex++) {
            card.add(effectRow(id, effectIndex, effect)).growX().padTop(2).row();
        }
        if (effectIndex < ScenarioRuntime.MAX_EFFECTS) {
            card.add(button("+ Effect", () -> rules.addEffect(id, RulesEditor.EffectType.SHOW_MESSAGE))).left().height(ROW_HEIGHT)
                .padTop(2).row();
        }
        if (problem != null) {
            Label note = new Label(problem, skin, "muted");
            note.setWrap(true);
            note.setColor(skin.getColor("error"));
            card.add(note).width(CARD_WIDTH - 12f).padTop(2).row();
        }
        return card;
    }

    private Table conditionRow(String ruleId, RulesEditor.Condition condition, JsonValue when) {
        Table row = new Table(skin);
        row.add(new Label("When", skin, "muted")).padRight(4);
        SelectBox<Choice<RulesEditor.Condition>> type = select(List.of(
                new Choice<>(RulesEditor.Condition.TIMER, "Timer"), new Choice<>(RulesEditor.Condition.ENTER_AREA, "Enter area")),
            condition, chosen -> {
                if (chosen == RulesEditor.Condition.TIMER) {
                    rules.setTimer(ruleId, RulesEditor.NEW_TIMER_SECONDS);
                } else {
                    List<Entity> areas = rules.areaCandidates();
                    if (areas.isEmpty()) {
                        editorState.setStatusMessage("There is no area yet: press Add Area", true);
                        shownScenario = null; // put the list back
                    } else {
                        rules.setEnterArea(ruleId, areas.get(0).getId(), RulesEditor.WHO_PLAYER);
                    }
                }
            });
        row.add(type).width(96f).height(ROW_HEIGHT).padRight(4);
        if (condition == RulesEditor.Condition.TIMER) {
            row.add(numberField(when.getFloat("seconds", 0f), value -> rules.setTimer(ruleId, value))).width(NUMBER_WIDTH)
                .height(ROW_HEIGHT);
            row.add(new Label("s", skin, "muted")).padLeft(2);
            row.add().growX();
            return row;
        }
        String areaId = when.getString("area", null);
        String who = when.getString("who", RulesEditor.WHO_PLAYER);
        List<Choice<String>> areas = new ArrayList<>();
        for (Entity area : rules.areaCandidates()) {
            areas.add(new Choice<>(area.getId().toString(), area.getName()));
        }
        if (areas.stream().noneMatch(choice -> choice.value().equals(areaId))) {
            areas.add(new Choice<>(areaId, "Missing area"));
        }
        List<Choice<String>> whos = new ArrayList<>();
        whos.add(new Choice<>(RulesEditor.WHO_PLAYER, "the player"));
        ScenarioSetup.read(editorState.getActiveScene()).ifPresent(setup -> {
            for (ScenarioSetup.PlayerSlot slot : setup.players()) {
                whos.add(new Choice<>(slot.id(), setup.displayName(slot)));
            }
        });
        if (whos.stream().noneMatch(choice -> choice.value().equals(who))) {
            whos.add(new Choice<>(who, "Unknown player"));
        }
        row.add(select(areas, areaId, chosen -> rules.setEnterArea(ruleId, parse(chosen), who))).width(100f).height(ROW_HEIGHT)
            .padRight(4);
        row.add(new Label("by", skin, "muted")).padRight(4);
        row.add(select(whos, who, chosen -> {
            UUID area = parse(areaId);
            if (area != null) {
                rules.setEnterArea(ruleId, area, chosen);
            }
        })).growX().height(ROW_HEIGHT);
        return row;
    }

    private Table effectRow(String ruleId, int index, JsonValue effect) {
        Table row = new Table(skin);
        Optional<RulesEditor.EffectType> type = RulesEditor.EffectType.of(effect.getString("type", null));
        if (type.isEmpty()) {
            row.add(new Label("Do: '" + effect.getString("type", "?") + "' (a newer type, kept as it is)", skin, "muted")).left();
            return row;
        }
        List<Choice<RulesEditor.EffectType>> types = new ArrayList<>();
        for (RulesEditor.EffectType value : RulesEditor.EffectType.values()) {
            types.add(new Choice<>(value, value.label()));
        }
        row.add(new Label("Do", skin, "muted")).padRight(4);
        row.add(select(types, type.get(), chosen -> rules.setEffectType(ruleId, index, chosen))).width(120f).height(ROW_HEIGHT)
            .padRight(4);
        switch (type.get()) {
            case SHOW_MESSAGE -> {
                float seconds = effect.getFloat("seconds", ScenarioRuntime.DEFAULT_MESSAGE_SECONDS);
                String text = effect.getString("text", "");
                row.add(textField(text, value -> rules.setMessage(ruleId, index, value, seconds))).growX().height(ROW_HEIGHT);
                row.add(numberField(seconds, value -> rules.setMessage(ruleId, index, text, value))).width(36f).height(ROW_HEIGHT)
                    .padLeft(2);
            }
            case COMPLETE_OBJECTIVE -> {
                List<Choice<String>> objectives = new ArrayList<>();
                for (JsonValue objective : rules.objectives()) {
                    objectives.add(new Choice<>(objective.getString("id", ""), objective.getString("text", "")));
                }
                String current = effect.getString("objective", "");
                if (objectives.stream().noneMatch(choice -> choice.value().equals(current))) {
                    objectives.add(new Choice<>(current, "Missing objective"));
                }
                row.add(select(objectives, current, chosen -> rules.setCompletedObjective(ruleId, index, chosen))).growX()
                    .height(ROW_HEIGHT);
            }
            case END_SCENARIO -> {
                List<Choice<String>> results = List.of(new Choice<>("victory", "Victory"), new Choice<>("defeat", "Defeat"),
                    new Choice<>("draw", "Draw"));
                row.add(select(results, effect.getString("result", "victory"), chosen -> rules.setEndResult(ruleId, index, chosen)))
                    .growX().height(ROW_HEIGHT);
            }
        }
        row.add(button("x", () -> rules.removeEffect(ruleId, index))).height(ROW_HEIGHT).padLeft(2);
        return row;
    }

    // ---- widgets -------------------------------------------------------------------------------------------------------------

    /** A list entry: a value and its label. */
    private record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** A list showing {@code current}; choosing another entry calls {@code onChoose} (a refused change puts the list back). */
    private <T> SelectBox<Choice<T>> select(List<Choice<T>> choices, T current, Consumer<T> onChoose) {
        SelectBox<Choice<T>> box = new SelectBox<>(skin);
        @SuppressWarnings("unchecked")
        Choice<T>[] items = choices.toArray(new Choice[0]);
        box.setItems(new Array<>(items));
        for (Choice<T> choice : choices) {
            if (Objects.equals(choice.value(), current)) {
                box.setSelected(choice);
            }
        }
        Choice<T> initial = box.getSelected();
        box.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Choice<T> chosen = box.getSelected();
                if (chosen != null && chosen != initial) {
                    onChoose.accept(chosen.value());
                    shownScenario = null; // rebuild, so a refused change shows what is saved
                }
            }
        });
        return box;
    }

    /** A text field that commits on Enter or when it loses focus; a refused value shows the saved text again on the rebuild. */
    private TextField textField(String text, Consumer<String> commit) {
        TextField field = new TextField(text, skin);
        Runnable apply = () -> {
            if (!field.getText().trim().equals(text)) {
                commit.accept(field.getText());
                shownScenario = null;
            }
        };
        field.setTextFieldListener((f, key) -> {
            if (key == '\r' || key == '\n') {
                apply.run();
            }
        });
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    apply.run();
                }
            }
        });
        return field;
    }

    private TextField numberField(float value, Consumer<Float> commit) {
        String text = value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        TextField field = textField(text, entered -> {
            try {
                commit.accept(Float.parseFloat(entered.trim()));
            } catch (NumberFormatException e) {
                editorState.setStatusMessage("'" + entered.trim() + "' is not a number", true);
            }
        });
        field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '.');
        return field;
    }

    private Table card() {
        Table card = new Table(skin);
        card.setBackground("card-bg");
        card.pad(4f);
        card.defaults().left();
        return card;
    }

    private TextButton button(String label, Runnable action) {
        TextButton button = new TextButton(label, skin);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!button.isDisabled()) {
                    action.run();
                }
            }
        });
        return button;
    }

    private static UUID parse(String id) {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
