package io.github.ldev22live.gdxunity.engine.scene.scenario;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;
import java.util.UUID;

/**
 * Which scenario player owns this entity (TD-106; the player slots are the scene's {@code scenario.players}, edited in roadmap
 * A04). Ownership is by the player's stable id, never by name. An entity without this component is unowned; one owned by a player
 * marked {@code neutral} belongs to nobody in play (scenery, wildlife). A reference to a player that does not exist is kept as
 * written and reported by {@link ScenarioValidator}, never silently cleared or reassigned. No faction, diplomacy or unit
 * behaviour comes with it.
 */
public class OwnerComponent extends BaseComponent {
    private String playerId;

    /** An entity owned by the player with id {@code playerId}. */
    public OwnerComponent(String playerId) {
        setPlayerId(playerId);
    }

    /** The owning player's id (a UUID text). */
    public String getPlayerId() {
        return playerId;
    }

    /**
     * Sets the owning player's id.
     *
     * @throws IllegalArgumentException when it is not a UUID
     */
    public void setPlayerId(String playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        try {
            UUID.fromString(playerId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("playerId must be a player's UUID: " + playerId, e);
        }
        this.playerId = playerId;
    }
}
