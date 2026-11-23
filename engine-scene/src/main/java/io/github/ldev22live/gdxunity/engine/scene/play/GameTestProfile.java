package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What Play supplies for one playable genre and game mode pair (TD-088): the camera, the player's movement, the physics it needs,
 * the keys, and what the scene must contain. Pure data; checking a scene against it and applying it to the Play copy is done by
 * {@code engine-render}'s {@code PlayProfiles}, the same code for editor Play and the game runtime.
 *
 * @param genre        the genre
 * @param mode         the scene's game mode
 * @param camera       how the main camera follows the player
 * @param movement     how the player moves
 * @param physics      the physics the profile's movement uses
 * @param input        the keys for each action
 * @param requirements what the scene must contain before Play starts
 * @param fallRespawnDepth how far below where it started the player may fall before Play puts it back there, in scene units; 0 never
 *                     puts it back (roadmap M3b.7: a 3D platformer's gaps)
 * @param shot         the player's shot, or null when the profile has none (roadmap M3b.7: First-person Shooter + 3D)
 */
public record GameTestProfile(GameGenre genre, GameMode mode, CameraSetup camera, MovementSetup movement, Physics physics,
                              InputMapping input, Set<Requirement> requirements, float fallRespawnDepth, ShotSetup shot) {

    /** A profile that never puts a falling player back and has no shot. */
    public GameTestProfile(GameGenre genre, GameMode mode, CameraSetup camera, MovementSetup movement, Physics physics,
                           InputMapping input, Set<Requirement> requirements) {
        this(genre, mode, camera, movement, physics, input, requirements, 0f, null);
    }

    /** A profile with no shot. */
    public GameTestProfile(GameGenre genre, GameMode mode, CameraSetup camera, MovementSetup movement, Physics physics,
                           InputMapping input, Set<Requirement> requirements, float fallRespawnDepth) {
        this(genre, mode, camera, movement, physics, input, requirements, fallRespawnDepth, null);
    }

    /** Whether the player can shoot ({@link #shot} is set). */
    public boolean hasShot() {
        return shot != null;
    }

    /**
     * A hitscan shot along the camera's view, once per press of the fire key (roadmap M3b.7). What it hits is reported; a dynamic
     * rigid body hit is knocked away along the shot.
     *
     * @param range           how far the shot reaches, in scene units
     * @param knockSpeed      the speed a dynamic body hit gains along the shot, in scene units per second (the impulse is this
     *                        times its mass, so light and heavy bodies move alike)
     */
    public record ShotSetup(float range, float knockSpeed) {
        public ShotSetup {
            positive("range", range);
            positive("knockSpeed", knockSpeed);
        }
    }

    /** Whether Play puts the player back where it started after a fall of {@link #fallRespawnDepth}. */
    public boolean respawnsAfterFalls() {
        return fallRespawnDepth > 0f;
    }

    /** The physics a profile's movement runs on. */
    public enum Physics {
        /** Box2D ({@code PhysicsSystem2D}). */
        PHYSICS_2D,
        /** Jolt through {@code PhysicsSystem3D}. */
        PHYSICS_3D
    }

    /** Something the scene must contain; each missing one becomes a problem Play reports in plain words. */
    public enum Requirement {
        /** Exactly one entity with a {@link PlayerComponent}. */
        PLAYER,
        /** The player walks with a Character Controller 3D (TD-049) and has no Rigid Body 3D or 3D collider. */
        CHARACTER_3D,
        /** An enabled camera to view the game through. */
        CAMERA
    }

    /**
     * How the main camera follows the player.
     *
     * @param kind         side-on follow (2.5D), isometric (2.5D) or third person behind the player (3D)
     * @param distance     scene units from the player
     * @param height       scene units above the player
     * @param pitchDegrees the camera's downward tilt, 0 to 89, for {@link Kind#THIRD_PERSON} and {@link Kind#ISOMETRIC}; a side-on
     *                     follow camera looks at the player from its offset instead
     * @param followRate   how quickly the camera catches up (as {@code CameraRigComponent.followRate}; 0 is rigid)
     */
    public record CameraSetup(Kind kind, float distance, float height, float pitchDegrees, float followRate) {
        /** The camera's arrangement. */
        public enum Kind {
            /** Beside the play plane, looking along -Z at the player (a {@code FOLLOW} rig with a side offset). */
            SIDE_FOLLOW,
            /** Behind the player, turned by the turn keys (an {@code ORBIT} rig). */
            THIRD_PERSON,
            /**
             * High and at a fixed angle, following the player without turning (an {@code ORBIT} rig at a fixed yaw): the classic
             * action-RPG view of a 2.5D game (roadmap M3c.3).
             */
            ISOMETRIC,
            /**
             * At the player's eyes ({@link CameraSetup#height} above its centre), looking the way the player faces, tilted by the
             * pitch; the player's movement turns it (a {@code FIRST_PERSON} rig; roadmap M3b.7). The distance is not used.
             */
            FIRST_PERSON
        }

        public CameraSetup {
            Objects.requireNonNull(kind, "kind cannot be null");
            positive("distance", distance);
            if (!Float.isFinite(height)) {
                throw new IllegalArgumentException("height must be finite: " + height);
            }
            if (!(pitchDegrees >= 0f && pitchDegrees < 90f)) {
                throw new IllegalArgumentException("pitchDegrees must be from 0 to below 90: " + pitchDegrees);
            }
            if (!(followRate >= 0f) || !Float.isFinite(followRate)) {
                throw new IllegalArgumentException("followRate must be 0 or more: " + followRate);
            }
        }
    }

    /**
     * How the player moves.
     *
     * @param kind              along the play plane (2.5D), over the ground relative to the camera (3D), or first person (3D)
     * @param speed             walking speed in scene units per second
     * @param jumpSpeed         upward speed of a jump in scene units per second
     * @param turnDegreesPerSec how fast the turn keys turn the camera, and the player toward its movement
     */
    public record MovementSetup(Kind kind, float speed, float jumpSpeed, float turnDegreesPerSec) {
        /** The movement's shape. */
        public enum Kind {
            /** Left and right along x at the player's authored depth; jump. */
            PLANE_RUN,
            /** Forward, back, left and right relative to the camera; jump; the player turns toward its movement. */
            GROUND_WALK,
            /**
             * First person (roadmap M3b.7): the turn keys turn the player, forward and back move along the way it faces, left and
             * right step sideways without turning; jump.
             */
            FIRST_PERSON_WALK
        }

        public MovementSetup {
            Objects.requireNonNull(kind, "kind cannot be null");
            positive("speed", speed);
            positive("jumpSpeed", jumpSpeed);
            positive("turnDegreesPerSec", turnDegreesPerSec);
        }
    }

    public GameTestProfile {
        Objects.requireNonNull(genre, "genre cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(camera, "camera cannot be null");
        Objects.requireNonNull(movement, "movement cannot be null");
        Objects.requireNonNull(physics, "physics cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(requirements, "requirements cannot be null");
        requirements = requirements.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(requirements));
        if (!(fallRespawnDepth >= 0f) || !Float.isFinite(fallRespawnDepth)) {
            throw new IllegalArgumentException("fallRespawnDepth must be 0 or more: " + fallRespawnDepth);
        }
        if (shot != null && physics != Physics.PHYSICS_3D) {
            throw new IllegalArgumentException(genre.label() + " + " + mode.getLabel() + " shoots along the view, which needs 3D physics");
        }
        if (fallRespawnDepth > 0f && !requirements.contains(Requirement.CHARACTER_3D)) {
            throw new IllegalArgumentException(genre.label() + " + " + mode.getLabel() + " puts a falling player back, which needs a "
                + "Character Controller 3D");
        }
        if (mode == GameMode.MODE_2D && physics == Physics.PHYSICS_3D) {
            throw new IllegalArgumentException(genre.label() + " + 2D cannot use 3D physics: 2D scenes run on Box2D");
        }
        if (requirements.contains(Requirement.CHARACTER_3D) && physics != Physics.PHYSICS_3D) {
            throw new IllegalArgumentException(genre.label() + " + " + mode.getLabel() + " requires a Character Controller 3D but not 3D physics");
        }
    }

    private static void positive(String name, float value) {
        if (!(value > 0f) || !Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be above 0: " + value);
        }
    }
}
