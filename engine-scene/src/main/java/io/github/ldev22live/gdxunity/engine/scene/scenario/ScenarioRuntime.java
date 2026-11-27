package io.github.ldev22live.gdxunity.engine.scene.scenario;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collision2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Runs a scene's scenario rules while it plays (TD-109, roadmap A05): a bounded interpreter of the closed Alpha rule set, the same in
 * editor Play and in the game runtime. It never runs code, loads classes or calls graphs.
 * <p>
 * Each {@link #update} (one Play frame, game time) checks the rules in list order. A rule whose condition holds runs its effects in
 * their order. Effects only change what is shown (message, objectives, result), which no condition reads, so rules cannot trigger
 * each other or loop. {@code endScenario} stops all further evaluation. Rules {@link ScenarioValidator} finds fault with are skipped
 * and listed once in {@link #getSkipped()}.
 * <p>
 * Conditions: {@code timer} fires once Play has run {@code seconds} (with {@code once: false}, again every {@code seconds}, at most
 * once a frame); {@code enterArea} fires in a frame in which a matching entity entered the area's trigger (3D) or sensor (2D)
 * collider since the last frame, once per frame however many entered. Leaving an area does nothing in Alpha.
 */
public final class ScenarioRuntime {
    /** How long a message shows when its rule gives no {@code seconds}. */
    public static final float DEFAULT_MESSAGE_SECONDS = 4f;
    /** The longest a message shows (schema {@code showMessage.seconds} maximum). */
    public static final float MAX_MESSAGE_SECONDS = 60f;
    /** Most rules a scenario may have (schema {@code rules.maxItems}); more are refused at Play. */
    public static final int MAX_RULES = 256;
    /** Most effects a rule may have (schema {@code then.maxItems}). */
    public static final int MAX_EFFECTS = 8;

    /** Where the runtime writes the rule log (the Output Log in the editor, the game's log in the runtime). */
    public interface Log {
        void info(String message);
    }

    /** How a scenario ended. */
    public enum Result {
        VICTORY, DEFEAT, DRAW;

        /** The label shown on screen. */
        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }
    }

    /** One objective as Play shows it. */
    public record Objective(String id, String text, String playerId, boolean visible, boolean complete) {
    }

    private record Effect(String type, String text, float seconds, String objective, Result result, String player) {
    }

    private static final class Rule {
        final int index;
        final String name;
        final boolean once;
        final String condition;
        final float seconds;
        final String areaId;
        final String who;
        final List<Effect> effects;
        boolean fired;
        int timerFirings;
        boolean entered;

        Rule(int index, String name, boolean once, String condition, float seconds, String areaId, String who, List<Effect> effects) {
            this.index = index;
            this.name = name;
            this.once = once;
            this.condition = condition;
            this.seconds = seconds;
            this.areaId = areaId;
            this.who = who;
            this.effects = effects;
        }
    }

    private final Log log;
    private final List<Rule> rules = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();
    private final List<Objective> objectives = new ArrayList<>();
    private float elapsed;
    private String message;
    private float messageLeft;
    private Result result;
    private String resultPlayer;
    private int revision;

    private ScenarioRuntime(Log log) {
        this.log = log;
    }

    /**
     * The runtime for {@code scene}'s scenario block, or null when the scene has none. Rules with validation findings are skipped (see
     * {@link #getSkipped()}); a block of a newer version runs nothing.
     *
     * @throws IllegalArgumentException when the block has more than {@link #MAX_RULES} rules or a rule more than {@link #MAX_EFFECTS}
     *                                  effects
     */
    public static ScenarioRuntime create(Scene scene, Log log) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(log, "log cannot be null");
        String raw = scene.getScenarioJson();
        if (raw == null) {
            return null;
        }
        ScenarioRuntime runtime = new ScenarioRuntime(log);
        JsonValue scenario = new JsonReader().parse(raw);
        Set<Integer> faulty = new HashSet<>();
        for (ScenarioValidator.Problem problem : ScenarioValidator.validate(scene)) {
            if (problem.path().equals("scenario") || problem.path().equals("scenario.version")) {
                runtime.skipped.add(problem.message());
                return runtime;
            }
            int rule = ruleIndex(problem.path());
            if (rule >= 0 && faulty.add(rule)) {
                runtime.skipped.add("Rule " + (rule + 1) + " is not run: " + problem.message());
            }
        }
        JsonValue objectives = scenario.get("objectives");
        for (JsonValue objective = objectives == null ? null : objectives.child; objective != null; objective = objective.next) {
            runtime.objectives.add(new Objective(objective.getString("id", ""), objective.getString("text", ""),
                objective.getString("player", null), !"hidden".equals(objective.getString("initial", "active")), false));
        }
        JsonValue list = scenario.get("rules");
        if (list != null && list.size > MAX_RULES) {
            throw new IllegalArgumentException("A scenario has at most " + MAX_RULES + " rules; this one has " + list.size);
        }
        int index = 0;
        for (JsonValue rule = list == null ? null : list.child; rule != null; rule = rule.next, index++) {
            if (faulty.contains(index) || !rule.getBoolean("enabled", false)) {
                continue;
            }
            runtime.rules.add(parseRule(index, rule));
        }
        return runtime;
    }

    private static Rule parseRule(int index, JsonValue rule) {
        JsonValue when = rule.get("when");
        JsonValue then = rule.get("then");
        if (then != null && then.size > MAX_EFFECTS) {
            throw new IllegalArgumentException("Rule '" + rule.getString("name", "?") + "' has " + then.size + " effects; at most "
                + MAX_EFFECTS);
        }
        List<Effect> effects = new ArrayList<>();
        for (JsonValue effect = then == null ? null : then.child; effect != null; effect = effect.next) {
            String type = effect.getString("type");
            float seconds = Math.min(MAX_MESSAGE_SECONDS, Math.max(0f, effect.getFloat("seconds", DEFAULT_MESSAGE_SECONDS)));
            String resultText = effect.getString("result", null);
            Result result = resultText == null ? null : Result.valueOf(resultText.toUpperCase(Locale.ROOT));
            effects.add(new Effect(type, effect.getString("text", null), seconds, effect.getString("objective", null), result,
                effect.getString("player", null)));
        }
        return new Rule(index, rule.getString("name", "Rule " + (index + 1)), rule.getBoolean("once", true), when.getString("type"),
            when.getFloat("seconds", 0f), when.getString("area", null), when.getString("who", null), List.copyOf(effects));
    }

    /** The rule index of a validator path such as {@code scenario.rules[3].when}, or -1. */
    private static int ruleIndex(String path) {
        String prefix = "scenario.rules[";
        if (!path.startsWith(prefix)) {
            return -1;
        }
        int end = path.indexOf(']', prefix.length());
        return end < 0 ? -1 : Integer.parseInt(path.substring(prefix.length(), end));
    }

    // ---- events --------------------------------------------------------------------------------------------------------------

    /** Feeds a 3D contact: a trigger entering an area. Other kinds are ignored. */
    public void onContact(PhysicsSystem3D.Contact contact) {
        if (contact.kind() == PhysicsSystem3D.ContactKind.TRIGGER_ENTER) {
            onEnter(contact.entityA(), contact.entityB());
            onEnter(contact.entityB(), contact.entityA());
        }
    }

    /** Feeds a 2D collision event: a sensor entering. Other kinds are ignored. */
    public void onCollision(Collision2D event) {
        if (event.type() == Collision2D.Type.SENSOR_ENTER) {
            onEnter(event.entity(), event.otherEntity());
            onEnter(event.otherEntity(), event.entity());
        }
    }

    /** Records that {@code visitor} entered {@code area}; matching rules fire on the next {@link #update}. */
    public void onEnter(Entity area, Entity visitor) {
        if (area == null || visitor == null || result != null) {
            return;
        }
        String areaId = area.getId().toString();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if ("enterArea".equals(rule.condition) && areaId.equals(rule.areaId) && matches(rule.who, visitor)) {
                rule.entered = true;
            }
        }
    }

    /** True when {@code visitor} (or an entity it belongs to) is the controlled player, or is owned by player {@code who}. */
    private static boolean matches(String who, Entity visitor) {
        for (Transform at = visitor.getTransform(); at != null; at = at.getParent()) {
            Entity entity = at.getEntity();
            if (ScenarioValidator.WHO_PLAYER.equals(who)) {
                if (entity.getComponent(PlayerComponent.class) != null) {
                    return true;
                }
            } else {
                OwnerComponent owner = entity.getComponent(OwnerComponent.class);
                if (owner != null) {
                    return owner.getPlayerId().equals(who);
                }
            }
        }
        return false;
    }

    // ---- the frame -----------------------------------------------------------------------------------------------------------

    /** Advances game time by {@code deltaTime} seconds and checks the rules in list order. Not called while Paused. */
    public void update(float deltaTime) {
        if (!(deltaTime >= 0f) || Float.isInfinite(deltaTime)) {
            deltaTime = 0f;
        }
        elapsed += deltaTime;
        if (message != null) {
            messageLeft -= deltaTime;
            if (messageLeft <= 0f) {
                message = null;
                revision++;
            }
        }
        for (int i = 0; i < rules.size() && result == null; i++) {
            Rule rule = rules.get(i);
            boolean entered = rule.entered;
            rule.entered = false;
            if (rule.once && rule.fired) {
                continue;
            }
            if (holds(rule, entered)) {
                fire(rule);
            }
        }
    }

    private boolean holds(Rule rule, boolean entered) {
        if ("enterArea".equals(rule.condition)) {
            return entered;
        }
        if (rule.once || rule.seconds <= 0f) {
            return elapsed >= rule.seconds;
        }
        // A repeating timer fires each time another whole period has passed, at most once a frame
        if ((int) (elapsed / rule.seconds) > rule.timerFirings) {
            rule.timerFirings = (int) (elapsed / rule.seconds);
            return true;
        }
        return false;
    }

    private void fire(Rule rule) {
        rule.fired = true;
        List<String> done = new ArrayList<>();
        for (Effect effect : rule.effects) {
            if (result != null) {
                break;
            }
            switch (effect.type()) {
                case "showMessage" -> {
                    message = effect.text();
                    messageLeft = effect.seconds();
                    done.add("message \"" + effect.text() + "\"");
                }
                case "completeObjective" -> {
                    completeObjective(effect.objective());
                    done.add("objective complete");
                }
                case "endScenario" -> {
                    result = effect.result();
                    resultPlayer = effect.player();
                    done.add("end: " + result.label());
                }
                default -> throw new IllegalStateException("Effect type passed validation but is not run: " + effect.type());
            }
        }
        revision++;
        log.info(String.format(Locale.ROOT, "Rule '%s' at %.1f s: %s", rule.name, elapsed, String.join(", ", done)));
    }

    private void completeObjective(String id) {
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            if (objective.id().equals(id)) {
                objectives.set(i, new Objective(objective.id(), objective.text(), objective.playerId(), true, true));
            }
        }
    }

    // ---- what Play shows -----------------------------------------------------------------------------------------------------

    /** Game time since Play started, in seconds. */
    public float getElapsed() {
        return elapsed;
    }

    /** The message showing now, or null. */
    public String getMessage() {
        return message;
    }

    /** The objectives in file order, with their status. */
    public List<Objective> getObjectives() {
        return Collections.unmodifiableList(objectives);
    }

    /** How the scenario ended, or null while it runs. */
    public Result getResult() {
        return result;
    }

    /** The player the result is for, or null for everyone. */
    public String getResultPlayer() {
        return resultPlayer;
    }

    /** Why rules (or the whole block) are not run, one line each. */
    public List<String> getSkipped() {
        return Collections.unmodifiableList(skipped);
    }

    /** How many enabled rules run. */
    public int getRuleCount() {
        return rules.size();
    }

    /** Changes whenever the message, an objective or the result changes, so a view redraws its text only then. */
    public int getRevision() {
        return revision;
    }

    /**
     * Starts the scenario of a scene that has just begun playing (editor Play and the game runtime do this in the same place): a
     * runtime-only entity carrying a {@link ScenarioRuntimeComponent} that advances the rules with the scene update, fed by the
     * physics systems' area events. Null when the scene has no scenario block.
     *
     * @param physics2D the 2D physics of the playing scene, or null
     * @param physics3D the 3D physics of the playing scene, or null
     */
    public static ScenarioRuntimeComponent attach(Scene runtimeScene, PhysicsSystem2D physics2D, PhysicsSystem3D physics3D, Log log) {
        ScenarioRuntime runtime = create(runtimeScene, log);
        if (runtime == null) {
            return null;
        }
        for (String reason : runtime.getSkipped()) {
            log.info("Scenario: " + reason);
        }
        ScenarioRuntimeComponent component = new ScenarioRuntimeComponent(runtime);
        runtimeScene.createEntity(ScenarioRuntimeComponent.ENTITY_NAME).addComponent(component);
        if (physics2D != null) {
            physics2D.addCollisionListener(runtime::onCollision);
        }
        if (physics3D != null) {
            physics3D.addContactListener(runtime::onContact);
        }
        return component;
    }
}
