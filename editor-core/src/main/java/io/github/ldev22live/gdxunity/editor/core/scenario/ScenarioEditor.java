package io.github.ldev22live.gdxunity.editor.core.scenario;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Edits the active scene's scenario players, map bounds and object ownership (TD-108, roadmap A04). Every change is one undoable
 * command; a refused change leaves the scene untouched and puts the reason in the status bar. Headless: the Players category of
 * the desktop World screen calls it, and tests do.
 * <p>
 * Ownership is data only in this version: nothing in Play reads it, and there is no diplomacy, economy or unit behaviour.
 */
public class ScenarioEditor {
    private final EditorState editorState;

    public ScenarioEditor(EditorState editorState) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
    }

    /** The active scene's scenario, or empty when there is no scene or it has no scenario block. */
    public Optional<ScenarioSetup> current() {
        Scene scene = editorState.getActiveScene();
        return scene == null ? Optional.empty() : ScenarioSetup.read(scene);
    }

    /**
     * Gives the scene a scenario with two named, coloured player slots (TD-108: only on this explicit action).
     *
     * @return false when there is no scene or it already has a scenario
     */
    public boolean createScenario() {
        if (editorState.getActiveScene() == null) {
            return refuse("Open a scene first to add players");
        }
        if (current().isPresent()) {
            return refuse("This scene already has players");
        }
        return change("Add Players", ScenarioSetup.starter(), "Added Player 1 and Player 2");
    }

    /** Adds a player slot with the next free name and colour; false at {@link ScenarioSetup#MAX_PLAYERS}. */
    public boolean addPlayer() {
        ScenarioSetup setup = editable();
        if (setup == null) {
            return false;
        }
        if (setup.players().size() >= ScenarioSetup.MAX_PLAYERS) {
            return refuse("A scenario has at most " + ScenarioSetup.MAX_PLAYERS + " players");
        }
        int number = 1;
        while (nameProblem(setup, null, "Player " + number) != null) {
            number++;
        }
        long colored = setup.players().stream().filter(slot -> !slot.neutral()).count();
        String color = ScenarioSetup.SLOT_COLORS.get((int) (colored % ScenarioSetup.SLOT_COLORS.size()));
        List<ScenarioSetup.PlayerSlot> players = new ArrayList<>(setup.players());
        String name = "Player " + number;
        players.add(new ScenarioSetup.PlayerSlot(ScenarioSetup.newId(), name, color, false, null,
            PreservedFields.EMPTY));
        return change("Add Player '" + name + "'", setup.withPlayers(players), "Added " + name);
    }

    /** Adds the neutral slot (it owns scenery or wildlife that belongs to nobody in play); false when there already is one. */
    public boolean addNeutral() {
        ScenarioSetup setup = editable();
        if (setup == null) {
            return false;
        }
        if (setup.players().stream().anyMatch(ScenarioSetup.PlayerSlot::neutral)) {
            return refuse("This scenario already has a neutral player");
        }
        if (setup.players().size() >= ScenarioSetup.MAX_PLAYERS) {
            return refuse("A scenario has at most " + ScenarioSetup.MAX_PLAYERS + " players");
        }
        String name = ScenarioSetup.NEUTRAL_NAME;
        for (int number = 2; nameProblem(setup, null, name) != null; number++) {
            name = ScenarioSetup.NEUTRAL_NAME + " " + number;
        }
        List<ScenarioSetup.PlayerSlot> players = new ArrayList<>(setup.players());
        players.add(new ScenarioSetup.PlayerSlot(ScenarioSetup.newId(), name, ScenarioSetup.NEUTRAL_COLOR, true, null,
            PreservedFields.EMPTY));
        return change("Add Neutral Player", setup.withPlayers(players), "Added the neutral player " + name);
    }

    /**
     * Why {@code name} cannot be the name of player {@code playerId} (null for a new slot), or null when it can: blank, longer than
     * {@link ScenarioSetup#MAX_NAME_LENGTH}, or another slot's name ignoring case.
     */
    public static String nameProblem(ScenarioSetup setup, String playerId, String name) {
        Objects.requireNonNull(setup, "setup cannot be null");
        if (name == null || name.isBlank()) {
            return "A player needs a name";
        }
        String trimmed = name.trim();
        if (trimmed.length() > ScenarioSetup.MAX_NAME_LENGTH) {
            return "A player name has at most " + ScenarioSetup.MAX_NAME_LENGTH + " characters";
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        for (ScenarioSetup.PlayerSlot slot : setup.players()) {
            if (!slot.id().equals(playerId) && slot.name().toLowerCase(Locale.ROOT).equals(key)) {
                return "Another player is already called '" + slot.name() + "'";
            }
        }
        return null;
    }

    /** Renames a player (its id stays); the name is trimmed. */
    public boolean renamePlayer(String playerId, String name) {
        ScenarioSetup setup = editable();
        ScenarioSetup.PlayerSlot slot = slot(setup, playerId);
        if (slot == null) {
            return false;
        }
        String problem = nameProblem(setup, playerId, name);
        if (problem != null) {
            return refuse(problem);
        }
        String trimmed = name.trim();
        if (trimmed.equals(slot.name())) {
            return false;
        }
        return change("Rename Player '" + slot.name() + "' to '" + trimmed + "'", setup.replacePlayer(slot.withName(trimmed)),
            "Renamed " + slot.name() + " to " + trimmed);
    }

    /** Recolours a player; {@code color} is {@code #rrggbb}. */
    public boolean setPlayerColor(String playerId, String color) {
        ScenarioSetup setup = editable();
        ScenarioSetup.PlayerSlot slot = slot(setup, playerId);
        if (slot == null) {
            return false;
        }
        if (color == null || !ScenarioSetup.COLOR.matcher(color).matches()) {
            return refuse("A player colour is #rrggbb, not " + color);
        }
        if (color.equalsIgnoreCase(slot.color())) {
            return false;
        }
        return change("Recolour Player '" + slot.name() + "'", setup.replacePlayer(slot.withColor(color.toLowerCase(Locale.ROOT))),
            "Recoloured " + slot.name());
    }

    /** The entities a player's spawn can be: those with a Spawn Point, in scene order. */
    public List<Entity> spawnCandidates() {
        Scene scene = editorState.getActiveScene();
        List<Entity> found = new ArrayList<>();
        if (scene != null) {
            for (Entity entity : scene.getAllEntities()) {
                if (entity.getComponent(SpawnPointComponent.class) != null) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    /**
     * Sets where a player starts: an entity with a Spawn Point, or null for none. Play still starts the controlled character at the
     * scene's Spawn Point as before; this records which player it belongs to.
     */
    public boolean setSpawn(String playerId, UUID spawnEntityId) {
        ScenarioSetup setup = editable();
        ScenarioSetup.PlayerSlot slot = slot(setup, playerId);
        if (slot == null) {
            return false;
        }
        String spawn = null;
        if (spawnEntityId != null) {
            Entity entity = editorState.getActiveScene().findEntityById(spawnEntityId);
            if (entity == null) {
                return refuse("There is no entity " + spawnEntityId + " in this scene");
            }
            if (entity.getComponent(SpawnPointComponent.class) == null) {
                return refuse("'" + entity.getName() + "' is not a Spawn Point; place one from Characters first");
            }
            spawn = spawnEntityId.toString();
        }
        if (Objects.equals(spawn, slot.spawn())) {
            return false;
        }
        return change("Set Spawn of '" + slot.name() + "'", setup.replacePlayer(slot.withSpawn(spawn)),
            spawn == null ? slot.name() + " has no spawn now" : slot.name() + " starts at " + nameOf(spawnEntityId));
    }

    /**
     * Removes a player slot, unless something refers to it (owned objects, objectives, rules, or a rule this version cannot check):
     * then nothing changes and the status bar names what does.
     */
    public boolean removePlayer(String playerId) {
        ScenarioSetup setup = editable();
        ScenarioSetup.PlayerSlot slot = slot(setup, playerId);
        if (slot == null) {
            return false;
        }
        List<String> references = referencesToPlayer(playerId);
        if (!references.isEmpty()) {
            return refuse("Cannot remove " + setup.displayName(slot) + ": " + String.join("; ", references));
        }
        List<ScenarioSetup.PlayerSlot> players = new ArrayList<>(setup.players());
        players.removeIf(other -> other.id().equals(playerId));
        return change("Remove Player '" + slot.name() + "'", setup.withPlayers(players), "Removed " + slot.name());
    }

    /** What refers to player {@code playerId}; empty when it can be removed. */
    public List<String> referencesToPlayer(String playerId) {
        ScenarioSetup setup = current().orElse(null);
        if (setup == null || setup.isReadOnly()) {
            return List.of();
        }
        return ScenarioReferences.toPlayer(editorState.getActiveScene(), setup, playerId);
    }

    /** Sets the playable area on X and Z, or clears it with null. */
    public boolean setBounds(ScenarioSetup.Bounds bounds) {
        ScenarioSetup setup = editable();
        if (setup == null) {
            return false;
        }
        if (Objects.equals(bounds, setup.bounds().orElse(null))) {
            return false;
        }
        return change(bounds == null ? "Clear Map Bounds" : "Set Map Bounds", setup.withBounds(bounds),
            bounds == null ? "The map has no bounds now" : "Map bounds set");
    }

    /**
     * Makes {@code playerId} own the entities, or makes them unowned with null, in one undo step.
     *
     * @return false when an entity is missing, the player is not in the scenario, or nothing would change
     */
    public boolean assignOwner(List<UUID> entityIds, String playerId) {
        Objects.requireNonNull(entityIds, "entityIds cannot be null");
        if (entityIds.isEmpty() || editorState.getActiveScene() == null) {
            return false;
        }
        String label = "Unowned";
        if (playerId != null) {
            ScenarioSetup setup = editable();
            ScenarioSetup.PlayerSlot slot = slot(setup, playerId);
            if (slot == null) {
                return false;
            }
            label = setup.displayName(slot);
        }
        for (UUID id : entityIds) {
            if (editorState.getActiveScene().findEntityById(id) == null) {
                return refuse("There is no entity " + id + " in this scene");
            }
        }
        String what = entityIds.size() == 1 ? "'" + nameOf(entityIds.get(0)) + "'" : entityIds.size() + " objects";
        boolean changed = editorState.executeCommand(new ChangeOwnerCommand(editorState, entityIds, playerId,
            "Set Owner of " + what + " to " + label));
        if (changed) {
            editorState.setStatusMessage(playerId == null ? what + " is unowned now" : label + " owns " + what + " now", false);
        }
        return changed;
    }

    /** The scenario when it can be edited; otherwise null, with the reason in the status bar. */
    private ScenarioSetup editable() {
        Optional<ScenarioSetup> setup = current();
        if (setup.isEmpty()) {
            refuse(editorState.getActiveScene() == null ? "Open a scene first" : "This scene has no players yet: press Add Players");
            return null;
        }
        if (setup.get().isReadOnly()) {
            refuse(setup.get().readOnlyReason());
            return null;
        }
        return setup.get();
    }

    private ScenarioSetup.PlayerSlot slot(ScenarioSetup setup, String playerId) {
        if (setup == null) {
            return null;
        }
        Optional<ScenarioSetup.PlayerSlot> slot = setup.player(playerId);
        if (slot.isEmpty()) {
            refuse("There is no player " + playerId + " in this scenario");
            return null;
        }
        return slot.get();
    }

    private String nameOf(UUID entityId) {
        Entity entity = editorState.getActiveScene().findEntityById(entityId);
        return entity == null ? entityId.toString() : entity.getName();
    }

    private boolean change(String description, ScenarioSetup after, String done) {
        boolean changed = editorState.executeCommand(new ChangeScenarioCommand(editorState, description, after.toJson()));
        if (changed) {
            editorState.setStatusMessage(done, false);
        }
        return changed;
    }

    private boolean refuse(String reason) {
        editorState.setStatusMessage(reason, true);
        return false;
    }
}
