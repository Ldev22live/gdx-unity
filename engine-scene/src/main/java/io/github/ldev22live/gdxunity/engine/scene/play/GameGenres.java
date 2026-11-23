package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The genre registry (TD-088): the genres a scene can be played as, and for every genre and game mode pair whether it is playable,
 * planned or not offered, with the reason. The Game Genre selector and Play read it; no UI code lists genres.
 *
 * <p>{@link #standard()} gives the eleven genres of the roadmap (R3b). A pair becomes playable only through
 * {@link #setSupport} with a {@link GameTestProfile}, and only when an acceptance test for it passes (the support table's truth
 * test checks this). Extensions register further genres with {@link #register}.
 */
public final class GameGenres {
    /** Platformer / Side-scroller. */
    public static final String PLATFORMER = "platformer";
    /** Action RPG, the owner's acceptance genre (roadmap section 1a). */
    public static final String ACTION_RPG = "action-rpg";
    public static final String ADVENTURE = "adventure";
    public static final String FIRST_PERSON_SHOOTER = "first-person-shooter";
    public static final String THIRD_PERSON_SHOOTER = "third-person-shooter";
    public static final String REAL_TIME_STRATEGY = "real-time-strategy";
    public static final String TURN_BASED_STRATEGY = "turn-based-strategy";
    public static final String RACING = "racing";
    public static final String PUZZLE = "puzzle";
    public static final String SIMULATION = "simulation";
    public static final String SANDBOX = "sandbox";

    /** Platformer + 2.5D: the side camera's distance in front of the play plane, in scene units. */
    public static final float SIDE_CAMERA_DISTANCE = 900f;
    /** Platformer + 2.5D: the side camera's height above the player, in scene units. */
    public static final float SIDE_CAMERA_HEIGHT = 150f;
    /** Platformer + 2.5D: how quickly the side camera catches up (as {@code CameraRigComponent.followRate}). */
    public static final float SIDE_CAMERA_FOLLOW_RATE = 6f;
    /** Platformer + 2.5D: running speed in scene units per second (4.5 m/s). */
    public static final float PLATFORMER_RUN_SPEED = 450f;
    /** Platformer + 2.5D: jump speed in scene units per second; 6.5 m/s rises about 2.15 m. */
    public static final float PLATFORMER_JUMP_SPEED = 650f;
    /** Action RPG + 3D: the third-person camera's distance behind the pivot, in scene units. */
    public static final float THIRD_PERSON_DISTANCE = 520f;
    /** Action RPG + 3D: the height above the player's centre that the third-person camera looks at, roughly the head. */
    public static final float THIRD_PERSON_PIVOT_HEIGHT = 90f;
    /** Action RPG + 3D: the third-person camera's downward tilt, in degrees. */
    public static final float THIRD_PERSON_PITCH = 18f;
    /** Action RPG + 2.5D: the isometric camera's distance from the pivot, in scene units. */
    public static final float ISOMETRIC_DISTANCE = 1300f;
    /** Action RPG + 2.5D: the height above the player's centre that the isometric camera looks at. */
    public static final float ISOMETRIC_PIVOT_HEIGHT = 40f;
    /** Action RPG + 2.5D: the isometric camera's downward tilt, in degrees. */
    public static final float ISOMETRIC_PITCH = 35f;
    /** Action RPG (3D and 2.5D): walking speed in scene units per second (4 m/s). */
    public static final float ACTION_RPG_WALK_SPEED = 400f;
    /** Action RPG (3D and 2.5D): jump speed in scene units per second; 6 m/s rises about 1.8 m. */
    public static final float ACTION_RPG_JUMP_SPEED = 600f;
    /** Platformer + 3D: the chase camera's distance behind the pivot, in scene units; farther than Action RPG's to see the gaps. */
    public static final float CHASE_DISTANCE = 650f;
    /** Platformer + 3D: the height above the player's centre that the chase camera looks at. */
    public static final float CHASE_PIVOT_HEIGHT = 60f;
    /** Platformer + 3D: the chase camera's downward tilt, in degrees; steeper than Action RPG's, so landings can be judged. */
    public static final float CHASE_PITCH = 26f;
    /** Platformer + 3D: running speed in scene units per second (5 m/s). */
    public static final float PLATFORMER_3D_RUN_SPEED = 500f;
    /** Platformer + 3D: jump speed in scene units per second; 7 m/s rises about 2.5 m. */
    public static final float PLATFORMER_3D_JUMP_SPEED = 700f;
    /** Platformer + 3D: how far below where it started the player falls before Play puts it back there (12 m). */
    public static final float PLATFORMER_3D_FALL_DEPTH = 1200f;
    /** First-person Shooter + 3D: the eyes' height above the player's centre; a default character's top is 70 above it. */
    public static final float FIRST_PERSON_EYE_HEIGHT = 55f;
    /** First-person Shooter + 3D: walking speed in scene units per second (4.5 m/s). */
    public static final float SHOOTER_WALK_SPEED = 450f;
    /** First-person Shooter + 3D: jump speed in scene units per second; 5 m/s rises about 1.3 m. */
    public static final float SHOOTER_JUMP_SPEED = 500f;
    /** First-person Shooter + 3D: how fast the turn keys turn the player, degrees per second. */
    public static final float SHOOTER_TURN_DEGREES_PER_SECOND = 120f;
    /** First-person Shooter + 3D: how far a shot reaches (50 m). */
    public static final float SHOT_RANGE = 5000f;
    /** First-person Shooter + 3D: the speed a body hit by a shot gains along it (6 m/s). */
    public static final float SHOT_KNOCK_SPEED = 600f;
    /** How fast the walking player turns to face where it walks, degrees per second (not used by side running). */
    private static final float TURN_DEGREES_PER_SECOND = 720f;

    private final Map<String, GameGenre> genres = new LinkedHashMap<>();
    private final Map<String, Map<GameMode, GenreSupport>> support = new LinkedHashMap<>();

    /** An empty registry; most callers want {@link #standard()}. */
    public GameGenres() {
    }

    /**
     * The eleven roadmap genres with their support today. Platformer + 2.5D (roadmap M3b.3, {@link #platformer25D}), Platformer + 3D
     * (roadmap M3b.7, {@link #platformer3D}), First-person Shooter + 3D (roadmap M3b.7, {@link #firstPersonShooter3D}), Action RPG + 3D
     * (roadmap M3c.1, {@link #actionRpg3D}) and Action RPG + 2.5D (roadmap M3c.3, {@link #actionRpg25D}) are playable.
     * First- and third-person shooters are not offered in 2D and 2.5D (TD-088, question 3); every other pair is planned, with the
     * milestone that is to build it where one exists.
     */
    public static GameGenres standard() {
        GameGenres registry = new GameGenres();
        registry.register(new GameGenre(PLATFORMER, "Platformer / Side-scroller", "Run and jump across platforms."));
        registry.register(new GameGenre(ACTION_RPG, "Action RPG", "Explore a world with a character you control directly."));
        registry.register(new GameGenre(ADVENTURE, "Adventure", "Explore, talk and solve problems at your own pace."));
        registry.register(new GameGenre(FIRST_PERSON_SHOOTER, "First-person Shooter", "See through the player's eyes and aim."));
        registry.register(new GameGenre(THIRD_PERSON_SHOOTER, "Third-person Shooter", "Aim from a camera behind the player."));
        registry.register(new GameGenre(REAL_TIME_STRATEGY, "Real-time Strategy", "Command units and buildings as time runs."));
        registry.register(new GameGenre(TURN_BASED_STRATEGY, "Turn-based Strategy", "Command units one turn at a time."));
        registry.register(new GameGenre(RACING, "Racing", "Drive against the clock or other racers."));
        registry.register(new GameGenre(PUZZLE, "Puzzle", "Solve rules-based challenges."));
        registry.register(new GameGenre(SIMULATION, "Simulation", "Run a system and watch it change."));
        registry.register(new GameGenre(SANDBOX, "Sandbox", "Build and play freely without set goals."));

        registry.planned(PLATFORMER, GameMode.MODE_2D, "Planned: needs a 2D character controller.");
        GameGenre platformer = registry.genres.get(PLATFORMER);
        registry.setSupport(new GenreSupport(platformer, GameMode.MODE_2_5D, GenreStatus.PLAYABLE, "Playable: a side camera, running "
            + "and jumping along the play plane, collisions with the level's colliders and terrain.", platformer25D(platformer)));
        registry.setSupport(new GenreSupport(platformer, GameMode.MODE_3D, GenreStatus.PLAYABLE, "Playable: a chase camera behind and "
            + "above the player that Q and E turn, running relative to the camera, jumping between platforms, and a fall puts the player "
            + "back where it started.", platformer3D(platformer)));
        GameGenre actionRpg = registry.genres.get(ACTION_RPG);
        registry.setSupport(new GenreSupport(actionRpg, GameMode.MODE_2_5D, GenreStatus.PLAYABLE, "Playable: an isometric camera high "
            + "and at an angle following the player, walking across the ground relative to the screen, jumping, collisions with terrain "
            + "and colliders. Combat comes later.", actionRpg25D(actionRpg)));
        registry.setSupport(new GenreSupport(actionRpg, GameMode.MODE_3D, GenreStatus.PLAYABLE, "Playable: a camera behind the player "
            + "that Q and E turn, walking relative to the camera, jumping, collisions with terrain and colliders. Combat comes later.",
            actionRpg3D(actionRpg)));
        for (GameMode mode : List.of(GameMode.MODE_2D, GameMode.MODE_2_5D)) {
            registry.notOffered(FIRST_PERSON_SHOOTER, mode, "Not offered: a first-person view needs a 3D world.");
            registry.notOffered(THIRD_PERSON_SHOOTER, mode, "Not offered: a camera behind the player needs a 3D world.");
        }
        GameGenre firstPerson = registry.genres.get(FIRST_PERSON_SHOOTER);
        registry.setSupport(new GenreSupport(firstPerson, GameMode.MODE_3D, GenreStatus.PLAYABLE, "Playable: a camera at the player's "
            + "eyes, Q and E turn, W and S walk, A and D step sideways, jumping, and F or Ctrl shoots along the view and knocks back what "
            + "it hits. Health, enemies and ammunition come later.", firstPersonShooter3D(firstPerson)));
        registry.planned(THIRD_PERSON_SHOOTER, GameMode.MODE_3D, "Planned (roadmap M3b.7).");
        registry.planned(RACING, GameMode.MODE_3D, "Planned (roadmap M3b.7).");
        for (GameMode mode : GameMode.values()) {
            registry.planned(REAL_TIME_STRATEGY, mode, "Planned: comes with the scenario editor (roadmap R4).");
        }
        return registry;
    }

    /**
     * Action RPG + 2.5D (roadmap M3c.3): an isometric camera that follows without turning, the same ground walking as 3D (relative to
     * the camera, so to the screen), and jumping. Validated on its own, with its own acceptance test.
     */
    public static GameTestProfile actionRpg25D(GameGenre actionRpg) {
        return new GameTestProfile(actionRpg, GameMode.MODE_2_5D,
            new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.ISOMETRIC, ISOMETRIC_DISTANCE, ISOMETRIC_PIVOT_HEIGHT,
                ISOMETRIC_PITCH, 0f),
            new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.GROUND_WALK, ACTION_RPG_WALK_SPEED,
                ACTION_RPG_JUMP_SPEED, TURN_DEGREES_PER_SECOND),
            GameTestProfile.Physics.PHYSICS_3D, InputMapping.defaults(),
            Set.of(GameTestProfile.Requirement.PLAYER, GameTestProfile.Requirement.CHARACTER_3D, GameTestProfile.Requirement.CAMERA));
    }

    public static GameTestProfile actionRpg3D(GameGenre actionRpg) {
        return new GameTestProfile(actionRpg, GameMode.MODE_3D,
            new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.THIRD_PERSON, THIRD_PERSON_DISTANCE,
                THIRD_PERSON_PIVOT_HEIGHT, THIRD_PERSON_PITCH, 0f),
            new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.GROUND_WALK, ACTION_RPG_WALK_SPEED,
                ACTION_RPG_JUMP_SPEED, TURN_DEGREES_PER_SECOND),
            GameTestProfile.Physics.PHYSICS_3D, InputMapping.defaults(),
            Set.of(GameTestProfile.Requirement.PLAYER, GameTestProfile.Requirement.CHARACTER_3D, GameTestProfile.Requirement.CAMERA));
    }

    /**
     * Platformer + 3D (roadmap M3b.7, slice 2): a chase camera behind and above the player that the turn keys turn, running relative
     * to the camera, jumping higher than Action RPG's, and a fall of {@link #PLATFORMER_3D_FALL_DEPTH} below the start puts the
     * player back at the start (the spawn point when the scene has one).
     */
    public static GameTestProfile platformer3D(GameGenre platformer) {
        return new GameTestProfile(platformer, GameMode.MODE_3D,
            new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.THIRD_PERSON, CHASE_DISTANCE, CHASE_PIVOT_HEIGHT,
                CHASE_PITCH, 0f),
            new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.GROUND_WALK, PLATFORMER_3D_RUN_SPEED,
                PLATFORMER_3D_JUMP_SPEED, TURN_DEGREES_PER_SECOND),
            GameTestProfile.Physics.PHYSICS_3D, InputMapping.defaults(),
            Set.of(GameTestProfile.Requirement.PLAYER, GameTestProfile.Requirement.CHARACTER_3D, GameTestProfile.Requirement.CAMERA),
            PLATFORMER_3D_FALL_DEPTH);
    }

    /**
     * First-person Shooter + 3D (roadmap M3b.7, slice 3): a camera at the player's eyes; the turn keys turn the player, forward and
     * back walk the way it faces, left and right step sideways; jumping; the fire key shoots along the view and knocks back a
     * dynamic body it hits. No health, enemies or ammunition yet.
     */
    public static GameTestProfile firstPersonShooter3D(GameGenre shooter) {
        return new GameTestProfile(shooter, GameMode.MODE_3D,
            new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.FIRST_PERSON, 1f, FIRST_PERSON_EYE_HEIGHT, 0f, 0f),
            new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.FIRST_PERSON_WALK, SHOOTER_WALK_SPEED,
                SHOOTER_JUMP_SPEED, SHOOTER_TURN_DEGREES_PER_SECOND),
            GameTestProfile.Physics.PHYSICS_3D, InputMapping.defaults(),
            Set.of(GameTestProfile.Requirement.PLAYER, GameTestProfile.Requirement.CHARACTER_3D, GameTestProfile.Requirement.CAMERA),
            0f, new GameTestProfile.ShotSetup(SHOT_RANGE, SHOT_KNOCK_SPEED));
    }

    /**
     * The Platformer + 2.5D profile (roadmap M3b.3): a side camera that follows the player, A and D (or the arrows) run along x,
     * Space jumps, the player is held at its starting depth, and it needs a Player with a Character Controller 3D and a camera.
     */
    public static GameTestProfile platformer25D(GameGenre platformer) {
        return new GameTestProfile(platformer, GameMode.MODE_2_5D,
            new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.SIDE_FOLLOW, SIDE_CAMERA_DISTANCE, SIDE_CAMERA_HEIGHT, 0f,
                SIDE_CAMERA_FOLLOW_RATE),
            new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.PLANE_RUN, PLATFORMER_RUN_SPEED, PLATFORMER_JUMP_SPEED,
                TURN_DEGREES_PER_SECOND),
            GameTestProfile.Physics.PHYSICS_3D, InputMapping.defaults(),
            Set.of(GameTestProfile.Requirement.PLAYER, GameTestProfile.Requirement.CHARACTER_3D, GameTestProfile.Requirement.CAMERA));
    }

    /**
     * Adds a genre with every game mode planned ("Planned: no profile yet").
     *
     * @throws IllegalArgumentException when a genre with the same id is registered
     */
    public void register(GameGenre genre) {
        Objects.requireNonNull(genre, "genre cannot be null");
        if (genres.containsKey(genre.id())) {
            throw new IllegalArgumentException("A genre with id '" + genre.id() + "' is already registered: " + genres.get(genre.id()).label());
        }
        genres.put(genre.id(), genre);
        Map<GameMode, GenreSupport> modes = new EnumMap<>(GameMode.class);
        for (GameMode mode : GameMode.values()) {
            modes.put(mode, new GenreSupport(genre, mode, GenreStatus.PLANNED, "Planned: no profile for " + genre.label() + " + "
                + mode.getLabel() + " yet.", null));
        }
        support.put(genre.id(), modes);
    }

    /**
     * Replaces what a pair supports; a playable pair carries its profile.
     *
     * @throws IllegalArgumentException when the genre is not registered here
     */
    public void setSupport(GenreSupport pair) {
        Objects.requireNonNull(pair, "pair cannot be null");
        GameGenre registered = genres.get(pair.genre().id());
        if (registered == null || !registered.equals(pair.genre())) {
            throw new IllegalArgumentException("Genre '" + pair.genre().id() + "' is not registered");
        }
        support.get(registered.id()).put(pair.mode(), pair);
    }

    /** The registered genres in registration order. */
    public List<GameGenre> genres() {
        return Collections.unmodifiableList(new ArrayList<>(genres.values()));
    }

    /** The genre with this id, or empty when none is registered (a scene from a newer version may name one). */
    public Optional<GameGenre> find(String id) {
        return Optional.ofNullable(genres.get(Objects.requireNonNull(id, "id cannot be null")));
    }

    /**
     * What a pair supports.
     *
     * @throws IllegalArgumentException when the genre is not registered
     */
    public GenreSupport support(String genreId, GameMode mode) {
        Objects.requireNonNull(mode, "mode cannot be null");
        Map<GameMode, GenreSupport> modes = support.get(Objects.requireNonNull(genreId, "genreId cannot be null"));
        if (modes == null) {
            throw new IllegalArgumentException("Genre '" + genreId + "' is not registered");
        }
        return modes.get(mode);
    }

    /** Every pair, genre by genre in registration order, then by mode. */
    public List<GenreSupport> pairs() {
        List<GenreSupport> pairs = new ArrayList<>();
        for (Map<GameMode, GenreSupport> modes : support.values()) {
            pairs.addAll(modes.values());
        }
        return pairs;
    }

    /** The pairs Play can run. */
    public List<GenreSupport> playablePairs() {
        List<GenreSupport> playable = new ArrayList<>();
        for (GenreSupport pair : pairs()) {
            if (pair.isPlayable()) {
                playable.add(pair);
            }
        }
        return playable;
    }

    private void planned(String genreId, GameMode mode, String reason) {
        setSupport(new GenreSupport(genres.get(genreId), mode, GenreStatus.PLANNED, reason, null));
    }

    private void notOffered(String genreId, GameMode mode, String reason) {
        setSupport(new GenreSupport(genres.get(genreId), mode, GenreStatus.NOT_OFFERED, reason, null));
    }
}
