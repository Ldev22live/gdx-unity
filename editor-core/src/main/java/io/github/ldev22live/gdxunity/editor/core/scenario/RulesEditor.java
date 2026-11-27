package io.github.ldev22live.gdxunity.editor.core.scenario;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntime;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Edits the active scene's scenario rules and objectives (TD-109, roadmap A05). Every change is one undoable
 * {@link ChangeScenarioCommand}; a refused change leaves the scene untouched and puts the reason in the status bar. The block is
 * edited as a JSON tree, so members this version does not know (in rules, effects or objectives) are kept. Headless: the desktop
 * Rules drawer calls it, and tests do.
 */
public class RulesEditor {
    /** A condition a rule can wait for. */
    public enum Condition {
        TIMER("timer", "Timer"), ENTER_AREA("enterArea", "Enter area");

        private final String type;
        private final String label;

        Condition(String type, String label) {
            this.type = type;
            this.label = label;
        }

        public String type() {
            return type;
        }

        public String label() {
            return label;
        }

        /** The condition of a saved {@code type}, if this version knows it. */
        public static Optional<Condition> of(String type) {
            for (Condition condition : values()) {
                if (condition.type.equals(type)) {
                    return Optional.of(condition);
                }
            }
            return Optional.empty();
        }
    }

    /** What a rule can do. */
    public enum EffectType {
        SHOW_MESSAGE("showMessage", "Show message"), COMPLETE_OBJECTIVE("completeObjective", "Complete objective"),
        END_SCENARIO("endScenario", "End scenario");

        private final String type;
        private final String label;

        EffectType(String type, String label) {
            this.type = type;
            this.label = label;
        }

        public String type() {
            return type;
        }

        public String label() {
            return label;
        }

        /** The effect of a saved {@code type}, if this version knows it. */
        public static Optional<EffectType> of(String type) {
            for (EffectType effect : values()) {
                if (effect.type.equals(type)) {
                    return Optional.of(effect);
                }
            }
            return Optional.empty();
        }
    }

    /** The {@code who} of an area rule meaning the controlled character. */
    public static final String WHO_PLAYER = ScenarioValidator.WHO_PLAYER;
    /** Longest rule name (schema {@code rules[].name.maxLength}). */
    public static final int MAX_RULE_NAME = 80;
    /** Longest message or objective text (schema maximum). */
    public static final int MAX_TEXT = 200;
    /** Most objectives (schema {@code objectives.maxItems}). */
    public static final int MAX_OBJECTIVES = 64;
    /** The longest timer (schema {@code timer.seconds} maximum: a day). */
    public static final float MAX_TIMER_SECONDS = 86400f;
    /** A new rule's timer. */
    public static final float NEW_TIMER_SECONDS = 5f;
    /** The size of an area made by {@link #addArea}, in scene units (a 3 m cube at 100 units per metre). */
    public static final float AREA_SIZE = 300f;

    private final EditorState editorState;

    public RulesEditor(EditorState editorState) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
    }

    // ---- reading -------------------------------------------------------------------------------------------------------------

    /** The scenario block as a tree, or empty when the scene has none. Edits to it change nothing until passed to a change. */
    public Optional<JsonValue> read() {
        Scene scene = editorState.getActiveScene();
        String raw = scene == null ? null : scene.getScenarioJson();
        return raw == null ? Optional.empty() : Optional.of(new JsonReader().parse(raw));
    }

    /** The rules, in order; empty without a scenario. */
    public List<JsonValue> rules() {
        return children(read().map(root -> root.get("rules")).orElse(null));
    }

    /** The objectives, in order; empty without a scenario. */
    public List<JsonValue> objectives() {
        return children(read().map(root -> root.get("objectives")).orElse(null));
    }

    private static List<JsonValue> children(JsonValue list) {
        List<JsonValue> out = new ArrayList<>();
        for (JsonValue item = list == null ? null : list.child; item != null; item = item.next) {
            out.add(item);
        }
        return out;
    }

    /** The entities an area rule can use: those with a trigger (3D) or sensor (2D) collider, in scene order. */
    public List<Entity> areaCandidates() {
        Scene scene = editorState.getActiveScene();
        List<Entity> found = new ArrayList<>();
        if (scene != null) {
            for (Entity entity : scene.getAllEntities()) {
                if (ScenarioValidator.hasTriggerCollider(entity)) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    /** The validation findings of the scene's scenario (what Play will skip), for the drawer to show. */
    public List<ScenarioValidator.Problem> problems() {
        Scene scene = editorState.getActiveScene();
        return scene == null ? List.of() : ScenarioValidator.validate(scene);
    }

    // ---- rules ---------------------------------------------------------------------------------------------------------------

    /**
     * Adds an enabled, one-shot rule at the end: after {@link #NEW_TIMER_SECONDS} seconds, show a message. A scene without a scenario
     * gets one with no players (TD-109: an explicit authoring action).
     *
     * @return the new rule's id, or null when refused
     */
    public String addRule() {
        String id = UUID.randomUUID().toString();
        boolean added = edit("Add Rule", true, root -> {
            JsonValue rules = list(root, "rules");
            if (rules.size >= ScenarioRuntime.MAX_RULES) {
                throw new Refusal("A scenario has at most " + ScenarioRuntime.MAX_RULES + " rules");
            }
            JsonValue rule = object();
            put(rule, "id", id);
            put(rule, "name", nextName(rules));
            rule.addChild("enabled", new JsonValue(true));
            rule.addChild("once", new JsonValue(true));
            JsonValue when = object();
            put(when, "type", Condition.TIMER.type());
            when.addChild("seconds", number(NEW_TIMER_SECONDS));
            rule.addChild("when", when);
            JsonValue then = new JsonValue(JsonValue.ValueType.array);
            then.addChild(newEffect(EffectType.SHOW_MESSAGE, root));
            rule.addChild("then", then);
            rules.addChild(rule);
        });
        return added ? id : null;
    }

    private static String nextName(JsonValue rules) {
        int number = rules.size + 1;
        while (true) {
            String name = "Rule " + number;
            boolean taken = false;
            for (JsonValue rule = rules.child; rule != null; rule = rule.next) {
                taken |= name.equalsIgnoreCase(rule.getString("name", ""));
            }
            if (!taken) {
                return name;
            }
            number++;
        }
    }

    /** Renames a rule (1 to {@link #MAX_RULE_NAME} characters, trimmed). */
    public boolean renameRule(String ruleId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_RULE_NAME) {
            return refuse("A rule name has 1 to " + MAX_RULE_NAME + " characters");
        }
        return editRule(ruleId, "Rename Rule", rule -> put(rule, "name", trimmed));
    }

    public boolean setRuleEnabled(String ruleId, boolean enabled) {
        return editRule(ruleId, enabled ? "Enable Rule" : "Disable Rule", rule -> putBoolean(rule, "enabled", enabled));
    }

    /** True: the rule fires once per Play; false: again each time its condition holds. */
    public boolean setRuleOnce(String ruleId, boolean once) {
        return editRule(ruleId, once ? "Rule Fires Once" : "Rule Repeats", rule -> putBoolean(rule, "once", once));
    }

    /** Makes the rule wait {@code seconds} (0 to {@link #MAX_TIMER_SECONDS}) of Play. */
    public boolean setTimer(String ruleId, float seconds) {
        if (!(seconds >= 0f) || seconds > MAX_TIMER_SECONDS) {
            return refuse("A timer is 0 to " + (int) MAX_TIMER_SECONDS + " seconds, not " + seconds);
        }
        return editRule(ruleId, "Set Rule Timer", rule -> {
            JsonValue when = object();
            put(when, "type", Condition.TIMER.type());
            when.addChild("seconds", number(seconds));
            replace(rule, "when", when);
        });
    }

    /**
     * Makes the rule wait for {@code who} to enter {@code areaEntityId}: {@link #WHO_PLAYER} or a scenario player's id. The area must
     * have a trigger or sensor collider.
     */
    public boolean setEnterArea(String ruleId, UUID areaEntityId, String who) {
        Scene scene = editorState.getActiveScene();
        Entity area = scene == null || areaEntityId == null ? null : scene.findEntityById(areaEntityId);
        if (area == null) {
            return refuse("Choose an area: an entity with a trigger collider (Add Area makes one)");
        }
        if (!ScenarioValidator.hasTriggerCollider(area)) {
            return refuse("'" + area.getName() + "' has no trigger collider, so nothing can enter it");
        }
        Objects.requireNonNull(who, "who cannot be null");
        if (!WHO_PLAYER.equals(who) && ScenarioSetup.read(scene).flatMap(setup -> setup.player(who)).isEmpty()) {
            return refuse("'" + who + "' is neither the player nor a player of this scenario");
        }
        return editRule(ruleId, "Set Rule Area", rule -> {
            JsonValue when = object();
            put(when, "type", Condition.ENTER_AREA.type());
            put(when, "area", areaEntityId.toString());
            put(when, "who", who);
            replace(rule, "when", when);
        });
    }

    /** Moves a rule one place up ({@code -1}) or down ({@code +1}): rules run in list order. */
    public boolean moveRule(String ruleId, int direction) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("direction must be -1 or 1: " + direction);
        }
        return edit("Move Rule", false, root -> {
            List<JsonValue> rules = children(root.get("rules"));
            int at = indexOf(rules, ruleId);
            int to = at + direction;
            if (to < 0 || to >= rules.size()) {
                throw new Refusal(null);
            }
            rules.add(to, rules.remove(at));
            replace(root, "rules", array(rules));
        });
    }

    public boolean removeRule(String ruleId) {
        return edit("Remove Rule", false, root -> {
            List<JsonValue> rules = children(root.get("rules"));
            rules.remove(indexOf(rules, ruleId));
            replace(root, "rules", array(rules));
        });
    }

    // ---- effects -------------------------------------------------------------------------------------------------------------

    /** Adds an effect of {@code type} with starting values; a rule has at most {@link ScenarioRuntime#MAX_EFFECTS}. */
    public boolean addEffect(String ruleId, EffectType type) {
        Objects.requireNonNull(type, "type cannot be null");
        JsonValue root = read().orElse(null);
        if (type == EffectType.COMPLETE_OBJECTIVE && (root == null || children(root.get("objectives")).isEmpty())) {
            return refuse("Add an objective first");
        }
        return editRule(ruleId, "Add Effect " + type.label(), rule -> {
            JsonValue then = list(rule, "then");
            if (then.size >= ScenarioRuntime.MAX_EFFECTS) {
                throw new Refusal("A rule has at most " + ScenarioRuntime.MAX_EFFECTS + " effects");
            }
            then.addChild(newEffect(type, root));
        });
    }

    /** Replaces effect {@code index} with a new one of {@code type}. */
    public boolean setEffectType(String ruleId, int index, EffectType type) {
        Objects.requireNonNull(type, "type cannot be null");
        JsonValue root = read().orElse(null);
        if (type == EffectType.COMPLETE_OBJECTIVE && (root == null || children(root.get("objectives")).isEmpty())) {
            return refuse("Add an objective first");
        }
        return editEffect(ruleId, index, "Set Effect " + type.label(), (then, effects) -> {
            if (type.type().equals(effects.get(index).getString("type", ""))) {
                throw new Refusal(null);
            }
            effects.set(index, newEffect(type, root));
            return effects;
        });
    }

    /** Sets a Show message effect's text (1 to {@link #MAX_TEXT} characters) and seconds (0 to 60). */
    public boolean setMessage(String ruleId, int index, String text, float seconds) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT) {
            return refuse("A message has 1 to " + MAX_TEXT + " characters");
        }
        if (!(seconds >= 0f) || seconds > ScenarioRuntime.MAX_MESSAGE_SECONDS) {
            return refuse("A message shows 0 to " + (int) ScenarioRuntime.MAX_MESSAGE_SECONDS + " seconds");
        }
        return editEffectOf(ruleId, index, EffectType.SHOW_MESSAGE, "Set Message", effect -> {
            put(effect, "text", trimmed);
            replace(effect, "seconds", number(seconds));
        });
    }

    /** Sets which objective a Complete objective effect completes. */
    public boolean setCompletedObjective(String ruleId, int index, String objectiveId) {
        if (objectives().stream().noneMatch(objective -> objective.getString("id", "").equals(objectiveId))) {
            return refuse("There is no objective " + objectiveId);
        }
        return editEffectOf(ruleId, index, EffectType.COMPLETE_OBJECTIVE, "Set Completed Objective",
            effect -> put(effect, "objective", objectiveId));
    }

    /** Sets an End scenario effect's result: {@code victory}, {@code defeat} or {@code draw}. */
    public boolean setEndResult(String ruleId, int index, String result) {
        if (!List.of("victory", "defeat", "draw").contains(result)) {
            return refuse("A result is victory, defeat or draw, not " + result);
        }
        return editEffectOf(ruleId, index, EffectType.END_SCENARIO, "Set Scenario Result", effect -> put(effect, "result", result));
    }

    /** Removes effect {@code index}; a rule keeps at least one. */
    public boolean removeEffect(String ruleId, int index) {
        return editEffect(ruleId, index, "Remove Effect", (then, effects) -> {
            if (effects.size() == 1) {
                throw new Refusal("A rule needs at least one effect; remove the rule instead");
            }
            effects.remove(index);
            return effects;
        });
    }

    private static JsonValue newEffect(EffectType type, JsonValue root) {
        JsonValue effect = object();
        put(effect, "type", type.type());
        switch (type) {
            case SHOW_MESSAGE -> {
                put(effect, "text", "Hello");
                effect.addChild("seconds", number(ScenarioRuntime.DEFAULT_MESSAGE_SECONDS));
            }
            case COMPLETE_OBJECTIVE -> put(effect, "objective", children(root.get("objectives")).get(0).getString("id"));
            case END_SCENARIO -> put(effect, "result", "victory");
        }
        return effect;
    }

    // ---- objectives ----------------------------------------------------------------------------------------------------------

    /** Adds an objective shown from the start of Play. @return its id, or null when refused */
    public String addObjective(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT) {
            refuse("An objective has 1 to " + MAX_TEXT + " characters");
            return null;
        }
        String id = UUID.randomUUID().toString();
        boolean added = edit("Add Objective", true, root -> {
            JsonValue objectives = list(root, "objectives");
            if (objectives.size >= MAX_OBJECTIVES) {
                throw new Refusal("A scenario has at most " + MAX_OBJECTIVES + " objectives");
            }
            JsonValue objective = object();
            put(objective, "id", id);
            put(objective, "text", trimmed);
            objectives.addChild(objective);
        });
        return added ? id : null;
    }

    public boolean setObjectiveText(String objectiveId, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT) {
            return refuse("An objective has 1 to " + MAX_TEXT + " characters");
        }
        return editObjective(objectiveId, "Set Objective Text", objective -> put(objective, "text", trimmed));
    }

    /** Hidden objectives appear in Play only once completed. */
    public boolean setObjectiveHidden(String objectiveId, boolean hidden) {
        return editObjective(objectiveId, hidden ? "Hide Objective" : "Show Objective", objective -> {
            if (hidden) {
                put(objective, "initial", "hidden");
            } else {
                objective.remove("initial");
            }
        });
    }

    /** Removes an objective, unless a rule completes it (the rule is named). */
    public boolean removeObjective(String objectiveId) {
        for (JsonValue rule : rules()) {
            JsonValue then = rule.get("then");
            for (JsonValue effect = then == null ? null : then.child; effect != null; effect = effect.next) {
                if (objectiveId.equals(effect.getString("objective", null))) {
                    return refuse("Cannot remove the objective: rule '" + rule.getString("name", "?") + "' completes it");
                }
            }
        }
        return edit("Remove Objective", false, root -> {
            List<JsonValue> objectives = children(root.get("objectives"));
            objectives.remove(indexOf(objectives, objectiveId));
            replace(root, "objectives", array(objectives));
        });
    }

    // ---- areas ---------------------------------------------------------------------------------------------------------------

    /**
     * Creates an area at {@code position}: an entity with a trigger Box Collider 3D of {@link #AREA_SIZE}, selected, in one undo
     * step. Move and scale it with the tools.
     *
     * @return the new entity, or null when refused
     */
    public Entity addArea(Vector3 position) {
        Objects.requireNonNull(position, "position cannot be null");
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            refuse("Open a scene first");
            return null;
        }
        int number = 1;
        while (scene.findEntityByName("Area " + number) != null) {
            number++;
        }
        BoxCollider3DComponent box = new BoxCollider3DComponent(new Vector3(AREA_SIZE, AREA_SIZE, AREA_SIZE));
        box.setTrigger(true);
        CreateEntityCommand command = new CreateEntityCommand(editorState, "Area " + number, position, List.<Component>of(box));
        if (!editorState.executeCommand(command)) {
            return null;
        }
        Entity area = command.getCreatedEntity();
        editorState.setSelectedEntity(area);
        editorState.setStatusMessage("Added " + area.getName() + ": move and scale it, then choose it in a rule", false);
        return area;
    }

    // ---- plumbing ------------------------------------------------------------------------------------------------------------

    /** Thrown inside an edit to refuse it; a null reason refuses silently (nothing to change). */
    private static final class Refusal extends RuntimeException {
        Refusal(String reason) {
            super(reason, null, false, false);
        }
    }

    @FunctionalInterface
    private interface EffectsEdit {
        List<JsonValue> apply(JsonValue then, List<JsonValue> effects);
    }

    private boolean editRule(String ruleId, String description, Consumer<JsonValue> change) {
        return edit(description, false, root -> change.accept(children(root.get("rules")).get(indexOf(children(root.get("rules")),
            ruleId))));
    }

    private boolean editObjective(String objectiveId, String description, Consumer<JsonValue> change) {
        return edit(description, false, root -> {
            List<JsonValue> objectives = children(root.get("objectives"));
            change.accept(objectives.get(indexOf(objectives, objectiveId)));
        });
    }

    private boolean editEffect(String ruleId, int index, String description, EffectsEdit change) {
        return editRule(ruleId, description, rule -> {
            JsonValue then = list(rule, "then");
            List<JsonValue> effects = children(then);
            if (index < 0 || index >= effects.size()) {
                throw new Refusal("The rule has no effect " + (index + 1));
            }
            replace(rule, "then", array(change.apply(then, effects)));
        });
    }

    private boolean editEffectOf(String ruleId, int index, EffectType type, String description, Consumer<JsonValue> change) {
        return editEffect(ruleId, index, description, (then, effects) -> {
            if (!type.type().equals(effects.get(index).getString("type", ""))) {
                throw new Refusal("Effect " + (index + 1) + " is not " + type.label());
            }
            change.accept(effects.get(index));
            return effects;
        });
    }

    /**
     * Applies {@code change} to a copy of the block and records it as one undo step.
     *
     * @param create true when the change may create the block (with no players) on a scene without one
     */
    private boolean edit(String description, boolean create, Consumer<JsonValue> change) {
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return refuse("Open a scene first");
        }
        Optional<ScenarioSetup> setup = ScenarioSetup.read(scene);
        if (setup.isPresent() && setup.get().isReadOnly()) {
            return refuse(setup.get().readOnlyReason());
        }
        JsonValue root;
        if (setup.isEmpty()) {
            if (!create) {
                return refuse("This scene has no rules yet: press Add Rule");
            }
            root = new JsonReader().parse("{\"version\":" + ScenarioValidator.SUPPORTED_VERSION
                + ",\"players\":[],\"objectives\":[],\"rules\":[]}");
        } else {
            root = new JsonReader().parse(scene.getScenarioJson());
        }
        try {
            change.accept(root);
        } catch (Refusal refusal) {
            return refusal.getMessage() == null ? false : refuse(refusal.getMessage());
        }
        boolean changed = editorState.executeCommand(new ChangeScenarioCommand(editorState, description,
            root.toJson(JsonWriter.OutputType.json)));
        if (changed) {
            editorState.setStatusMessage(description, false);
        }
        return changed;
    }

    private static int indexOf(List<JsonValue> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getString("id", "").equals(id)) {
                return i;
            }
        }
        throw new Refusal("There is no item " + id);
    }

    private static JsonValue object() {
        return new JsonValue(JsonValue.ValueType.object);
    }

    private static JsonValue number(double value) {
        return value == Math.rint(value) ? new JsonValue((long) value) : new JsonValue(value);
    }

    private static JsonValue array(List<JsonValue> items) {
        JsonValue array = new JsonValue(JsonValue.ValueType.array);
        for (JsonValue item : items) {
            item.parent = null;
            item.prev = null;
            item.next = null;
            array.addChild(item);
        }
        return array;
    }

    /** The array member {@code name}, created empty when missing. */
    private static JsonValue list(JsonValue object, String name) {
        JsonValue list = object.get(name);
        if (list == null) {
            list = new JsonValue(JsonValue.ValueType.array);
            object.addChild(name, list);
        }
        return list;
    }

    private static void put(JsonValue object, String name, String value) {
        replace(object, name, new JsonValue(value));
    }

    private static void putBoolean(JsonValue object, String name, boolean value) {
        JsonValue current = object.get(name);
        if (current != null && current.isBoolean() && current.asBoolean() == value) {
            throw new Refusal(null);
        }
        replace(object, name, new JsonValue(value));
    }

    /** Sets member {@code name} to {@code value} in its place (or at the end when new); a null object does nothing. */
    private static void replace(JsonValue object, String name, JsonValue value) {
        if (object == null || name == null) {
            return;
        }
        JsonValue current = object.get(name);
        if (current == null) {
            object.addChild(name, value);
            return;
        }
        value.name = name;
        value.parent = object;
        value.prev = current.prev;
        value.next = current.next;
        if (current.prev == null) {
            object.child = value;
        } else {
            current.prev.next = value;
        }
        if (current.next != null) {
            current.next.prev = value;
        }
    }

    private boolean refuse(String reason) {
        editorState.setStatusMessage(reason, true);
        return false;
    }

}
