package io.github.ldev22live.gdxunity.engine.scene.scenario;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Checks a scene's {@code scenario} block (TD-106, {@code docs/contracts/scenario.schema.json}) and its {@link OwnerComponent}s.
 * It never runs a rule and never changes the scene: it reports. The Alpha rule set is closed (owner approval, 2026-11-25):
 * conditions {@code timer} and {@code enterArea}; effects {@code showMessage}, {@code completeObjective} and {@code endScenario}.
 * A rule with any other type is reported as {@link Severity#NOT_RUN}: kept in the file, never executed by this version.
 */
public final class ScenarioValidator {
    /** The scenario block version this engine reads. */
    public static final int SUPPORTED_VERSION = 1;
    /** Alpha conditions. */
    public static final Set<String> CONDITIONS = Set.of("timer", "enterArea");
    /** Alpha effects. */
    public static final Set<String> EFFECTS = Set.of("showMessage", "completeObjective", "endScenario");
    /** {@code enterArea.who} value meaning the controlled character. */
    public static final String WHO_PLAYER = "player";

    /** How serious a finding is. */
    public enum Severity {
        /** The data is wrong (a broken reference, a duplicate id): saved as is, but the scenario cannot be trusted. */
        ERROR,
        /** Valid data this version does not run (a newer rule type or version): kept, never executed. */
        NOT_RUN
    }

    /** One finding, with where it is ({@code scenario.rules[1].when}) and a plain message. */
    public record Problem(Severity severity, String path, String message) {
    }

    private ScenarioValidator() {
    }

    /** The findings for {@code scene}; empty when it has no scenario block and no owners, or when everything checks out. */
    public static List<Problem> validate(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        List<Problem> problems = new ArrayList<>();
        Set<String> players = new HashSet<>();
        String raw = scene.getScenarioJson();
        if (raw != null) {
            JsonValue scenario = new JsonReader().parse(raw);
            if (!scenario.isObject()) {
                problems.add(new Problem(Severity.ERROR, "scenario", "The scenario block must be an object"));
                return problems;
            }
            int version = scenario.getInt("version", -1);
            if (version > SUPPORTED_VERSION) {
                problems.add(new Problem(Severity.NOT_RUN, "scenario.version", "Scenario version " + version
                    + " needs a newer editor (this one reads " + SUPPORTED_VERSION + "); it is kept and not run"));
                return problems;
            }
            if (version != SUPPORTED_VERSION) {
                problems.add(new Problem(Severity.ERROR, "scenario.version", "Missing or invalid scenario version: " + version));
            }
            Set<String> objectives = new HashSet<>();
            checkPlayers(scenario.get("players"), scene, players, problems);
            checkBounds(scenario.get("bounds"), problems);
            checkObjectives(scenario.get("objectives"), players, objectives, problems);
            checkRules(scenario.get("rules"), scene, players, objectives, problems);
        }
        for (Entity entity : scene.getAllEntities()) {
            for (OwnerComponent owner : entity.getComponents(OwnerComponent.class)) {
                if (!players.contains(owner.getPlayerId())) {
                    problems.add(new Problem(Severity.ERROR, "entity '" + entity.getName() + "'.OwnerComponent",
                        "Owned by player " + owner.getPlayerId() + ", which the scenario does not define"));
                }
            }
        }
        return problems;
    }

    private static void checkPlayers(JsonValue list, Scene scene, Set<String> ids, List<Problem> problems) {
        int index = 0;
        for (JsonValue player = list == null ? null : list.child; player != null; player = player.next, index++) {
            String path = "scenario.players[" + index + "]";
            String id = uniqueId(player, path, ids, problems);
            String spawn = player.getString("spawn", null);
            if (id != null && spawn != null) {
                Entity spawnEntity = entity(scene, spawn);
                if (spawnEntity == null) {
                    problems.add(new Problem(Severity.ERROR, path + ".spawn", "Spawn entity " + spawn + " is not in the scene"));
                } else if (spawnEntity.getComponent(SpawnPointComponent.class) == null) {
                    problems.add(new Problem(Severity.ERROR, path + ".spawn", "Spawn entity '" + spawnEntity.getName()
                        + "' has no Spawn Point"));
                }
            }
        }
    }

    /** Bounds, when present, are four finite numbers with each minimum below its maximum (TD-108). */
    private static void checkBounds(JsonValue bounds, List<Problem> problems) {
        if (bounds == null) {
            return;
        }
        double[] values = new double[4];
        String[] names = {"minX", "minZ", "maxX", "maxZ"};
        for (int i = 0; i < names.length; i++) {
            JsonValue value = bounds.get(names[i]);
            if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
                problems.add(new Problem(Severity.ERROR, "scenario.bounds." + names[i], "Not a finite number: " + value));
                return;
            }
            values[i] = value.asDouble();
        }
        if (!(values[0] < values[2]) || !(values[1] < values[3])) {
            problems.add(new Problem(Severity.ERROR, "scenario.bounds", "Each minimum must be below its maximum"));
        }
    }

    private static void checkObjectives(JsonValue list, Set<String> players, Set<String> ids, List<Problem> problems) {
        int index = 0;
        for (JsonValue objective = list == null ? null : list.child; objective != null; objective = objective.next, index++) {
            String path = "scenario.objectives[" + index + "]";
            uniqueId(objective, path, ids, problems);
            String player = objective.getString("player", null);
            if (player != null && !players.contains(player)) {
                problems.add(new Problem(Severity.ERROR, path + ".player", "Player " + player + " is not defined"));
            }
        }
    }

    private static void checkRules(JsonValue list, Scene scene, Set<String> players, Set<String> objectives,
                                   List<Problem> problems) {
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (JsonValue rule = list == null ? null : list.child; rule != null; rule = rule.next, index++) {
            String path = "scenario.rules[" + index + "]";
            uniqueId(rule, path, ids, problems);
            JsonValue when = rule.get("when");
            String condition = when == null ? null : when.getString("type", null);
            if (condition == null) {
                problems.add(new Problem(Severity.ERROR, path + ".when", "A rule needs a condition"));
            } else if (!CONDITIONS.contains(condition)) {
                problems.add(new Problem(Severity.NOT_RUN, path + ".when", "Condition '" + condition
                    + "' is not in this version's rule set; the rule is kept and not run"));
            } else if (condition.equals("enterArea")) {
                String area = when.getString("area", null);
                Entity areaEntity = area == null ? null : entity(scene, area);
                if (areaEntity == null) {
                    problems.add(new Problem(Severity.ERROR, path + ".when.area", "Area entity " + area + " is not in the scene"));
                } else if (!hasTriggerCollider(areaEntity)) {
                    problems.add(new Problem(Severity.ERROR, path + ".when.area", "Area '" + areaEntity.getName()
                        + "' has no trigger collider, so nothing can enter it (TD-109)"));
                }
                String who = when.getString("who", null);
                if (!WHO_PLAYER.equals(who) && !players.contains(who)) {
                    problems.add(new Problem(Severity.ERROR, path + ".when.who", "'" + who
                        + "' is neither 'player' nor a defined player"));
                }
            }
            JsonValue then = rule.get("then");
            int effectIndex = 0;
            for (JsonValue effect = then == null ? null : then.child; effect != null; effect = effect.next, effectIndex++) {
                String effectPath = path + ".then[" + effectIndex + "]";
                String type = effect.getString("type", null);
                if (type == null || !EFFECTS.contains(type)) {
                    problems.add(new Problem(Severity.NOT_RUN, effectPath, "Effect '" + type
                        + "' is not in this version's rule set; the rule is kept and not run"));
                } else if (type.equals("completeObjective") && !objectives.contains(effect.getString("objective", ""))) {
                    problems.add(new Problem(Severity.ERROR, effectPath + ".objective", "Objective "
                        + effect.getString("objective", null) + " is not defined"));
                } else if (type.equals("endScenario") && effect.has("player") && !players.contains(effect.getString("player"))) {
                    problems.add(new Problem(Severity.ERROR, effectPath + ".player", "Player " + effect.getString("player")
                        + " is not defined"));
                }
            }
            if (effectIndex == 0) {
                problems.add(new Problem(Severity.ERROR, path + ".then", "A rule needs at least one effect"));
            }
        }
    }

    /** The item's id when it is a UUID not seen before, else null with a problem added. */
    private static String uniqueId(JsonValue item, String path, Set<String> seen, List<Problem> problems) {
        String id = item.getString("id", null);
        try {
            UUID.fromString(String.valueOf(id));
        } catch (IllegalArgumentException e) {
            problems.add(new Problem(Severity.ERROR, path + ".id", "Not a UUID: " + id));
            return null;
        }
        if (!seen.add(id)) {
            problems.add(new Problem(Severity.ERROR, path + ".id", "Duplicate id " + id));
            return null;
        }
        return id;
    }

    /** True when the entity has an enabled 3D trigger collider or 2D sensor collider (what an area rule listens to). */
    public static boolean hasTriggerCollider(Entity entity) {
        for (Collider3DComponent collider : entity.getComponents(Collider3DComponent.class)) {
            if (collider.isEnabled() && collider.isTrigger()) {
                return true;
            }
        }
        for (Collider2DComponent collider : entity.getComponents(Collider2DComponent.class)) {
            if (collider.isEnabled() && collider.isSensor()) {
                return true;
            }
        }
        return false;
    }

    private static Entity entity(Scene scene, String id) {
        try {
            return scene.findEntityById(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
