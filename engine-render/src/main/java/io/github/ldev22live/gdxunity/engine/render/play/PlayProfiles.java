package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.EntityLogicComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenre;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.GenreSupport;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a scene's genre and game mode to a profile and checks the scene against it (TD-088). Headless; editor Play and the game
 * runtime call the same methods, so a game plays the same in both.
 *
 * <p>{@link #start} runs the whole step on the copy Play runs. Order: {@link #resolve} (a refused pair stops Play with its reason), then {@link #validate} (every problem stops Play,
 * each in plain words, some with a safe fix), then the player is put on the spawn point if the scene has one, then the ground check
 * (a ray down from the player), then {@link #plan} (which parts the
 * author's own logic and camera rig leave out), then {@link #apply}.
 */
public final class PlayProfiles {
    /** The name that marks the likely player when no entity has a Player component yet. */
    public static final String PLAYER_NAME = "Player";
    /** How far above the spawn point the player's capsule starts, so it is not placed touching the ground. */
    private static final float SPAWN_CLEARANCE = 1f;
    /** How far below the player's centre the ground check looks for something to stand on: 100 m. */
    public static final float GROUND_CHECK_DISTANCE = 10000f;
    /** The ground check's ray starts this far below the bottom of the player's capsule, outside its own shape. */
    private static final float BELOW_CAPSULE = 1f;
    /** How far the ground check's ray moves on after hitting the player itself (a shape wider than its capsule). */
    private static final float SKIP_STEP = 10f;
    /** How often the ground check's ray may pass through the player's own shape. */
    private static final int MAX_PLAYER_SKIPS = 16;
    private static final Vector3 DOWN = new Vector3(0f, -1f, 0f);

    /** What the ground check found: a problem that stops Play, or a note when it could not look; both null when there is ground. */
    private record GroundCheck(ProfileProblem problem, String note) {
    }

    private final GameGenres genres;

    /** Resolves against {@code genres}, usually {@link GameGenres#standard()}. */
    public PlayProfiles(GameGenres genres) {
        this.genres = Objects.requireNonNull(genres, "genres cannot be null");
    }

    public GameGenres getGenres() {
        return genres;
    }

    /** {@link #resolve(Scene, GameMode)} with the scene's saved game mode; a scene with a genre but no saved mode is refused. */
    public ProfileResolution resolve(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        if (scene.getGameGenre() != null && scene.getGameMode() == null) {
            return new ProfileResolution(ProfileResolution.Outcome.REFUSED, "This scene has a genre but no saved Game Mode: choose 2D, "
                + "2.5D or 3D and save it.", null);
        }
        return resolve(scene, scene.getGameMode());
    }

    /**
     * What Play does with the scene's genre in {@code mode} (the editor passes the mode it shows, which it infers for old files).
     * No genre: play as before. A playable pair: its profile. Otherwise refused, with the pair's reason or the unknown id.
     */
    public ProfileResolution resolve(Scene scene, GameMode mode) {
        Objects.requireNonNull(scene, "scene cannot be null");
        String genreId = scene.getGameGenre();
        if (genreId == null) {
            return new ProfileResolution(ProfileResolution.Outcome.NO_GENRE, "", null);
        }
        Objects.requireNonNull(mode, "mode cannot be null");
        Optional<GameGenre> genre = genres.find(genreId);
        if (genre.isEmpty()) {
            return new ProfileResolution(ProfileResolution.Outcome.REFUSED, "This scene's genre '" + genreId + "' is not known to this "
                + "version of ForgeRoom. Choose another Game Genre, or none.", null);
        }
        GenreSupport pair = genres.support(genreId, mode);
        if (!pair.isPlayable()) {
            return new ProfileResolution(ProfileResolution.Outcome.REFUSED, genre.get().label() + " + " + mode.getLabel() + " cannot "
                + "be played yet. " + pair.reason(), null);
        }
        return new ProfileResolution(ProfileResolution.Outcome.PROFILE, "Playing as " + genre.get().label() + " + " + mode.getLabel()
            + ".", pair.profile());
    }

    /** What the scene lacks for {@code profile}; empty when Play may start. */
    public List<ProfileProblem> validate(Scene scene, GameTestProfile profile) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        List<ProfileProblem> problems = new ArrayList<>();
        List<Entity> players = players(scene);
        if (profile.requirements().contains(GameTestProfile.Requirement.PLAYER)) {
            if (players.isEmpty()) {
                problems.add(noPlayer(scene, profile));
            } else if (players.size() > 1) {
                problems.add(new ProfileProblem("More than one player: " + names(players) + ". Remove Player from all but one.",
                    players.get(1).getName(), null));
            }
        }
        if (players.size() == 1 && profile.requirements().contains(GameTestProfile.Requirement.CHARACTER_3D)) {
            characterProblem(players.get(0)).ifPresent(problems::add);
        }
        List<Entity> spawns = spawnPoints(scene);
        if (spawns.size() > 1) {
            problems.add(new ProfileProblem("More than one spawn point: " + names(spawns) + ". Remove Spawn Point from all but one.",
                spawns.get(1).getName(), null));
        }
        if (profile.requirements().contains(GameTestProfile.Requirement.CAMERA) && GameCameraRig.findActiveCamera(scene) == null) {
            problems.add(new ProfileProblem("No camera: add a Camera to see the game.", null,
                new ProfileFix(ProfileFix.Kind.ADD_CAMERA, null, "Add a camera")));
        }
        return problems;
    }

    /**
     * Which parts of {@code profile} Play adds, leaving the author's logic and camera rig alone.
     *
     * @throws IllegalStateException when the scene does not have exactly one player ({@link #validate} reports that first)
     */
    public ProfilePlan plan(Scene scene, GameTestProfile profile) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        List<Entity> players = players(scene);
        if (players.size() != 1) {
            throw new IllegalStateException("A plan needs exactly one player, and the scene has " + players.size() + ": validate first");
        }
        Entity player = players.get(0);
        List<String> notes = new ArrayList<>();
        boolean ownLogic = !player.getComponents(EntityLogicComponent.class).isEmpty();
        boolean addMovement = !ownLogic || player.getComponent(PlayerComponent.class).isUseProfileMovement();
        if (!addMovement) {
            notes.add(player.getName() + " is moved by its own script or graph; the profile supplies the camera only. Turn on Use "
                + "Profile Movement on its Player to use the profile's movement.");
        }
        CameraComponent camera = GameCameraRig.findActiveCamera(scene);
        boolean addCameraRig = camera != null && camera.getEntity().getComponent(CameraRigComponent.class) == null;
        if (camera != null && !addCameraRig) {
            notes.add(camera.getEntity().getName() + " keeps its own Camera Rig.");
        }
        return new ProfilePlan(addMovement, addCameraRig, notes);
    }

    /**
     * The profile step of Play on {@code runtimeScene}, the copy Play runs (never the authored scene): resolve, validate, plan and
     * apply. Editor Play and the game runtime both call this, after 3D physics is created and before the first update.
     *
     * @param mode  the game mode to resolve in: the editor passes the one it shows; null uses the scene's saved mode
     * @param input where the profile's movement reads keys
     */
    public ProfileStart start(Scene runtimeScene, GameMode mode, InputSource input) {
        Objects.requireNonNull(runtimeScene, "runtimeScene cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        ProfileResolution resolution = mode == null ? resolve(runtimeScene) : resolve(runtimeScene, mode);
        switch (resolution.outcome()) {
            case NO_GENRE -> {
                return new ProfileStart(List.of(), "");
            }
            case REFUSED -> {
                return new ProfileStart(List.of(new ProfileProblem(resolution.message(), null, null)), "");
            }
            default -> {
            }
        }
        GameTestProfile profile = resolution.profile();
        List<ProfileProblem> problems = validate(runtimeScene, profile);
        if (!problems.isEmpty()) {
            return new ProfileStart(problems, "");
        }
        String spawnNote = placeAtSpawn(runtimeScene);
        GroundCheck ground = checkGround(runtimeScene, profile);
        if (ground.problem() != null) {
            return new ProfileStart(List.of(ground.problem()), "");
        }
        ProfilePlan plan = plan(runtimeScene, profile);
        apply(runtimeScene, profile, plan, input);
        List<String> parts = new ArrayList<>();
        parts.add(resolution.message());
        parts.addAll(plan.notes());
        if (spawnNote != null) {
            parts.add(spawnNote);
        }
        if (ground.note() != null) {
            parts.add(ground.note());
        }
        return new ProfileStart(List.of(), String.join(" ", parts));
    }

    /**
     * Adds the profile's runtime-only parts that {@code plan} keeps: the mover on the player and the rig on the game camera, and,
     * for a profile that puts a falling player back, a {@link FallRespawner} starting from where the player stands now, and for a
     * profile with a shot, a {@link ProfileShooter} with the mover. Only
     * for the copy Play runs; the scene can no longer be saved afterwards ({@code RuntimeOnlyComponent}).
     *
     * @throws IllegalStateException    when the scene does not have exactly one player
     */
    public void apply(Scene runtimeScene, GameTestProfile profile, ProfilePlan plan, InputSource input) {
        Objects.requireNonNull(runtimeScene, "runtimeScene cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(plan, "plan cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        List<Entity> players = players(runtimeScene);
        if (players.size() != 1) {
            throw new IllegalStateException("A profile is applied to exactly one player, and the scene has " + players.size());
        }
        Entity player = players.get(0);
        if (plan.addMovement()) {
            switch (profile.movement().kind()) {
                case PLANE_RUN -> player.addComponent(new PlaneRunMover(profile.movement(), profile.input(), input));
                case GROUND_WALK -> player.addComponent(new GroundWalkMover(profile.movement(), profile.input(), input));
                case FIRST_PERSON_WALK -> player.addComponent(new FirstPersonMover(profile.movement(), profile.input(), input));
            }
        }
        // The shot is the player's, like movement: a scripted player shoots by its own script
        if (plan.addMovement() && profile.hasShot()) {
            player.addComponent(new ProfileShooter(profile.shot(), profile.input(), input));
        }
        // A genre rule, not movement: it also catches a player the author's own script moves
        if (profile.respawnsAfterFalls()) {
            player.addComponent(new FallRespawner(profile.fallRespawnDepth(), player.getTransform().getWorldPosition(),
                player.getTransform().getWorldRotation()));
        }
        CameraComponent camera = GameCameraRig.findActiveCamera(runtimeScene);
        if (plan.addCameraRig() && camera != null) {
            camera.getEntity().addComponent(new ProfileCameraRig(profile.camera(), player.getName(), profile.input(), input));
        }
    }

    /**
     * The ground check of Play (TD-088, roadmap M3b.5): for a profile that walks a character, a ray straight down from the player's
     * centre must reach something solid within {@link #GROUND_CHECK_DISTANCE}, or the player would fall forever. It needs the
     * scene's 3D physics with its bodies built ({@link PhysicsSystem3D#prepareBodies}); when they cannot be built yet (in the game
     * runtime a mesh collider's shape needs the window, which opens after this step) the check says it did not look, and Play goes
     * on as it would without it.
     *
     * <p>Only the copy Play runs is checked; validate it first, so it has exactly one player.
     */
    private GroundCheck checkGround(Scene runtimeScene, GameTestProfile profile) {
        PhysicsSystem3D physics = runtimeScene.getPhysics3D();
        if (!profile.requirements().contains(GameTestProfile.Requirement.CHARACTER_3D) || physics == null) {
            return new GroundCheck(null, null);
        }
        Entity player = players(runtimeScene).get(0);
        try {
            physics.prepareBodies();
        } catch (IllegalStateException e) {
            return new GroundCheck(null, "The ground under " + player.getName() + " was not checked before Play: " + e.getMessage());
        }
        // From just below the capsule: a ray starting inside a shape reports that shape at once
        CharacterController3DComponent character = player.getComponent(CharacterController3DComponent.class);
        float below = character.getTotalHeight() / 2f + BELOW_CAPSULE;
        Vector3 origin = new Vector3(player.getTransform().getWorldPosition()).mulAdd(DOWN, below);
        float left = GROUND_CHECK_DISTANCE - below;
        for (int skips = 0; skips <= MAX_PLAYER_SKIPS && left > 0f; skips++) {
            Optional<PhysicsSystem3D.RaycastHit> hit = physics.raycast(origin, DOWN, left, PhysicsLayers3D.ALL_LAYERS);
            if (hit.isEmpty()) {
                break;
            }
            if (hit.get().entity() != player) {
                return new GroundCheck(null, null);
            }
            // The ray started inside the player's own shape: carry on from just below where it left it
            left -= hit.get().distance() + SKIP_STEP;
            origin.set(hit.get().point()).mulAdd(DOWN, SKIP_STEP);
        }
        return new GroundCheck(new ProfileProblem("Nothing under " + player.getName() + " to stand on: add a Terrain Collider 3D or a "
            + "Box Collider 3D below it, or move it over the ground.", player.getName(), null), null);
    }

    /** The active entities with an enabled Spawn Point, in scene order (roadmap M3c.2). */
    public static List<Entity> spawnPoints(Scene scene) {
        List<Entity> spawns = new ArrayList<>();
        for (Entity entity : Objects.requireNonNull(scene, "scene cannot be null").getAllEntities()) {
            SpawnPointComponent spawn = entity.getComponent(SpawnPointComponent.class);
            if (spawn != null && spawn.isEnabled() && entity.isActiveInHierarchy()) {
                spawns.add(entity);
            }
        }
        return spawns;
    }

    /**
     * Puts the player of the copy Play runs on the scene's spawn point (roadmap M3c.2): standing on it (its centre lifted by half its
     * Character Controller 3D's height), facing the way the spawn point faces. Before 3D physics builds the character, so the
     * character starts there. Without a spawn point the player starts where it stands.
     *
     * @return what Play's status says about it, or null without a spawn point
     */
    private static String placeAtSpawn(Scene runtimeScene) {
        List<Entity> spawns = spawnPoints(runtimeScene);
        if (spawns.size() != 1) {
            return null;
        }
        Entity spawn = spawns.get(0);
        Entity player = players(runtimeScene).get(0);
        CharacterController3DComponent character = player.getComponent(CharacterController3DComponent.class);
        float lift = character == null ? 0f : character.getTotalHeight() / 2f + SPAWN_CLEARANCE;
        Vector3 world = spawn.getTransform().getWorldPosition().add(0f, lift, 0f);
        Vector3 facing = spawn.getTransform().getWorldRotation().transform(new Vector3(0f, 0f, -1f));
        float yaw = facing.x * facing.x + facing.z * facing.z > 1e-6f ? CameraRigComponent.yawOf(facing) : 0f;
        Quaternion rotation = new Quaternion().setEulerAngles(yaw, 0f, 0f);
        Transform parent = player.getTransform().getParent();
        if (parent != null) {
            world.mul(new Matrix4(parent.getWorldMatrix()).inv());
            rotation.mulLeft(new Quaternion(parent.getWorldRotation()).conjugate());
        }
        player.getTransform().setLocalPosition(world);
        player.getTransform().setLocalRotation(rotation);
        return player.getName() + " starts at " + spawn.getName() + ".";
    }

    /** The active entities with an enabled Player component, in scene order. */
    public static List<Entity> players(Scene scene) {
        List<Entity> players = new ArrayList<>();
        for (Entity entity : Objects.requireNonNull(scene, "scene cannot be null").getAllEntities()) {
            PlayerComponent player = entity.getComponent(PlayerComponent.class);
            if (player != null && player.isEnabled() && entity.isActiveInHierarchy()) {
                players.add(entity);
            }
        }
        return players;
    }

    private static ProfileProblem noPlayer(Scene scene, GameTestProfile profile) {
        Entity likely = likelyPlayer(scene, profile);
        String message = "No player: select the entity to control and add Player (Add Component > Game).";
        if (likely == null) {
            return new ProfileProblem(message, null, null);
        }
        return new ProfileProblem(message, likely.getName(),
            new ProfileFix(ProfileFix.Kind.MAKE_PLAYER, likely.getId(), "Make " + likely.getName() + " the player"));
    }

    /** The only active entity named "Player", else (when the profile walks a character) the only Character Controller 3D, else null. */
    private static Entity likelyPlayer(Scene scene, GameTestProfile profile) {
        List<Entity> named = new ArrayList<>();
        List<Entity> characters = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            if (PLAYER_NAME.equalsIgnoreCase(entity.getName())) {
                named.add(entity);
            }
            if (entity.getComponent(CharacterController3DComponent.class) != null) {
                characters.add(entity);
            }
        }
        if (named.size() == 1) {
            return named.get(0);
        }
        if (named.isEmpty() && characters.size() == 1 && profile.requirements().contains(GameTestProfile.Requirement.CHARACTER_3D)) {
            return characters.get(0);
        }
        return null;
    }

    private static Optional<ProfileProblem> characterProblem(Entity player) {
        if (player.getComponent(CharacterController3DComponent.class) != null) {
            return Optional.empty();
        }
        if (player.getComponent(RigidBody3DComponent.class) != null || !player.getComponents(Collider3DComponent.class).isEmpty()) {
            return Optional.of(new ProfileProblem(player.getName() + " has a Rigid Body 3D or a 3D collider; a player walks with a "
                + "Character Controller 3D instead. Remove them and add a Character Controller 3D.", player.getName(), null));
        }
        return Optional.of(new ProfileProblem(player.getName() + " cannot walk: it needs a Character Controller 3D.", player.getName(),
            new ProfileFix(ProfileFix.Kind.ADD_CHARACTER_CONTROLLER_3D, player.getId(), "Add a Character Controller 3D to "
                + player.getName())));
    }

    private static String names(List<Entity> entities) {
        List<String> names = new ArrayList<>();
        for (Entity entity : entities) {
            names.add(entity.getName());
        }
        return String.join(", ", names);
    }
}
