package io.github.ldev22live.gdxunity.editor.core.scenario;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What in a scene refers to a scenario player or to an entity the scenario names (TD-108 deletion safeguards). Each finding is a
 * plain sentence for the status bar. A rule of a type this version does not know cannot be checked reliably, so it counts as a
 * reference to everything: deleting is then refused rather than risking a broken rule.
 */
public final class ScenarioReferences {
    private ScenarioReferences() {
    }

    /** Everything that refers to player {@code playerId}: owned entities, objectives and rules. */
    public static List<String> toPlayer(Scene scene, ScenarioSetup setup, String playerId) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(setup, "setup cannot be null");
        Objects.requireNonNull(playerId, "playerId cannot be null");
        List<String> found = new ArrayList<>();
        int owned = 0;
        String firstOwned = null;
        for (Entity entity : scene.getAllEntities()) {
            for (OwnerComponent owner : entity.getComponents(OwnerComponent.class)) {
                if (owner.getPlayerId().equals(playerId)) {
                    owned++;
                    firstOwned = firstOwned == null ? entity.getName() : firstOwned;
                }
            }
        }
        if (owned > 0) {
            found.add(owned == 1 ? "it owns '" + firstOwned + "'" : "it owns " + owned + " objects, among them '" + firstOwned + "'");
        }
        JsonValue objectives = parsed(setup.rawMember("objectives"));
        for (JsonValue objective = objectives == null ? null : objectives.child; objective != null; objective = objective.next) {
            if (playerId.equals(objective.getString("player", null))) {
                found.add("objective '" + objective.getString("text", "?") + "' belongs to it");
            }
        }
        JsonValue rules = parsed(setup.rawMember("rules"));
        for (JsonValue rule = rules == null ? null : rules.child; rule != null; rule = rule.next) {
            String name = rule.getString("name", "?");
            if (isOpaque(rule)) {
                found.add("rule '" + name + "' has a type this version cannot check");
                continue;
            }
            JsonValue when = rule.get("when");
            if (when != null && playerId.equals(when.getString("who", null))) {
                found.add("rule '" + name + "' waits for it");
            }
            JsonValue then = rule.get("then");
            for (JsonValue effect = then == null ? null : then.child; effect != null; effect = effect.next) {
                if (playerId.equals(effect.getString("player", null))) {
                    found.add("rule '" + name + "' ends the scenario for it");
                }
            }
        }
        return found;
    }

    /**
     * Everything in the scenario that names entity {@code entityId}: a player's spawn, a rule's area. Empty when the scene has no
     * scenario. A read-only scenario is searched as text, so a newer client's reference is never broken.
     */
    public static List<String> toEntity(Scene scene, String entityId) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Optional<ScenarioSetup> read = ScenarioSetup.read(scene);
        if (read.isEmpty()) {
            return List.of();
        }
        ScenarioSetup setup = read.get();
        List<String> found = new ArrayList<>();
        if (setup.isReadOnly()) {
            if (scene.getScenarioJson().contains(entityId)) {
                found.add("the scenario (which this version cannot edit) names it");
            }
            return found;
        }
        for (ScenarioSetup.PlayerSlot slot : setup.players()) {
            if (entityId.equals(slot.spawn())) {
                found.add("it is the spawn of " + setup.displayName(slot));
            }
        }
        JsonValue rules = parsed(setup.rawMember("rules"));
        for (JsonValue rule = rules == null ? null : rules.child; rule != null; rule = rule.next) {
            String name = rule.getString("name", "?");
            JsonValue when = rule.get("when");
            if (when != null && entityId.equals(when.getString("area", null))) {
                found.add("rule '" + name + "' uses it as its area");
            } else if (isOpaque(rule) && PreservedFields.toJson(rule).contains(entityId)) {
                found.add("rule '" + name + "' (a type this version cannot check) names it");
            }
        }
        String objectives = setup.rawMember("objectives");
        if (objectives != null && objectives.contains(entityId)) {
            found.add("an objective names it");
        }
        return found;
    }

    /** True when the rule's condition or one of its effects is outside this version's rule set. */
    static boolean isOpaque(JsonValue rule) {
        JsonValue when = rule.get("when");
        String condition = when == null ? null : when.getString("type", null);
        if (condition == null || !ScenarioValidator.CONDITIONS.contains(condition)) {
            return true;
        }
        JsonValue then = rule.get("then");
        for (JsonValue effect = then == null ? null : then.child; effect != null; effect = effect.next) {
            if (!ScenarioValidator.EFFECTS.contains(effect.getString("type", ""))) {
                return true;
            }
        }
        return false;
    }

    private static JsonValue parsed(String raw) {
        return raw == null ? null : new JsonReader().parse(raw);
    }
}
