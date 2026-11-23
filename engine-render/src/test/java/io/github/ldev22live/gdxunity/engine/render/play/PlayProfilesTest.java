package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenre;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.GenreStatus;
import io.github.ldev22live.gdxunity.engine.scene.play.GenreSupport;
import io.github.ldev22live.gdxunity.engine.scene.play.InputMapping;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Genre selection, game mode selection, resolution, refusals and the scene checks (roadmap M3b.2, TD-088). No pair of the standard
 * registry is playable yet, so a test registry makes Action RPG + 3D playable with a test profile.
 */
class PlayProfilesTest {
    private GameGenres genres;
    private GameTestProfile walk;
    private PlayProfiles profiles;
    private Scene scene;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        genres = GameGenres.standard();
        GameGenre actionRpg = genres.find(GameGenres.ACTION_RPG).orElseThrow();
        walk = new GameTestProfile(actionRpg, GameMode.MODE_3D,
            new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.THIRD_PERSON, 400f, 120f, 20f, 6f),
            new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.GROUND_WALK, 400f, 600f, 360f),
            GameTestProfile.Physics.PHYSICS_3D, InputMapping.defaults(),
            Set.of(GameTestProfile.Requirement.PLAYER, GameTestProfile.Requirement.CHARACTER_3D, GameTestProfile.Requirement.CAMERA));
        profiles = new PlayProfiles(genres);
        scene = new Scene("World");
        scene.setGameMode(GameMode.MODE_3D);
    }

    private void makeActionRpgPlayable() {
        genres.setSupport(new GenreSupport(walk.genre(), GameMode.MODE_3D, GenreStatus.PLAYABLE, "Playable.", walk));
    }

    private Entity camera() {
        Entity camera = scene.createEntity("Main Camera");
        camera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        return camera;
    }

    private Entity hero() {
        Entity hero = scene.createEntity("Hero");
        hero.addComponent(new CharacterController3DComponent());
        hero.addComponent(new PlayerComponent());
        return hero;
    }

    @Test
    void shouldPlayAsBeforeWhenTheSceneHasNoGenre() {
        ProfileResolution resolution = profiles.resolve(scene);

        assertEquals(ProfileResolution.Outcome.NO_GENRE, resolution.outcome());
        assertTrue(resolution.allowsPlay());
        assertNull(resolution.profile());
    }

    @Test
    void shouldRefuseAPlannedOrNotOfferedPairWithItsReason() {
        scene.setGameGenre(GameGenres.ACTION_RPG);
        ProfileResolution planned = profiles.resolve(scene, GameMode.MODE_2D);

        assertFalse(planned.allowsPlay());
        assertTrue(planned.message().startsWith("Action RPG + 2D cannot be played yet."), planned.message());
        assertTrue(planned.message().contains("Planned"), planned.message());

        scene.setGameGenre(GameGenres.FIRST_PERSON_SHOOTER);
        ProfileResolution notOffered = profiles.resolve(scene, GameMode.MODE_2D);
        assertFalse(notOffered.allowsPlay());
        assertTrue(notOffered.message().contains("needs a 3D world"), notOffered.message());
    }

    @Test
    void shouldResolveTheSameGenreDifferentlyPerGameMode() {
        makeActionRpgPlayable();
        scene.setGameGenre(GameGenres.ACTION_RPG);

        assertSame(walk, profiles.resolve(scene).profile(), "the scene's own mode, 3D");
        assertEquals("Playing as Action RPG + 3D.", profiles.resolve(scene).message());
        assertFalse(profiles.resolve(scene, GameMode.MODE_2D).allowsPlay(), "2D is not inferred from 3D");
    }

    @Test
    void shouldRefuseAnUnknownGenreOrAGenreWithoutAGameMode() {
        scene.setGameGenre("rhythm");
        ProfileResolution unknown = profiles.resolve(scene);
        assertFalse(unknown.allowsPlay());
        assertTrue(unknown.message().contains("'rhythm' is not known"), unknown.message());

        Scene old = new Scene("Old");
        old.setGameGenre(GameGenres.PLATFORMER);
        ProfileResolution noMode = profiles.resolve(old);
        assertFalse(noMode.allowsPlay());
        assertTrue(noMode.message().contains("no saved Game Mode"), noMode.message());
    }

    @Test
    void shouldNameASecondSpawnPoint() {
        camera();
        hero();
        scene.createEntity("Spawn A").addComponent(new SpawnPointComponent());
        scene.createEntity("Spawn B").addComponent(new SpawnPointComponent());

        List<ProfileProblem> problems = profiles.validate(scene, walk);

        assertEquals(1, problems.size(), problems.toString());
        assertEquals("More than one spawn point: Spawn A, Spawn B. Remove Spawn Point from all but one.", problems.get(0).message());
        assertEquals(List.of(scene.findEntityByName("Spawn A"), scene.findEntityByName("Spawn B")), PlayProfiles.spawnPoints(scene));
    }

    @Test
    void shouldPassAWorldThatHasEverything() {
        camera();
        hero();

        assertEquals(List.of(), profiles.validate(scene, walk));
    }

    @Test
    void shouldAskForAPlayerWithoutGuessingWhenNothingIsLikely() {
        camera();
        scene.createEntity("Tree");
        scene.createEntity("Rock");

        List<ProfileProblem> problems = profiles.validate(scene, walk);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).message().startsWith("No player:"), problems.get(0).message());
        assertTrue(problems.get(0).safeFix().isEmpty(), "the choice is the person's");
    }

    @Test
    void shouldOfferToMakeTheEntityNamedPlayerThePlayer() {
        camera();
        Entity player = scene.createEntity("Player");
        player.addComponent(new CharacterController3DComponent());
        Entity other = scene.createEntity("Guard");
        other.addComponent(new CharacterController3DComponent());

        ProfileFix fix = profiles.validate(scene, walk).get(0).safeFix().orElseThrow();

        assertEquals(ProfileFix.Kind.MAKE_PLAYER, fix.kind());
        assertEquals(player.getId(), fix.entityId());
        assertEquals("Make Player the player", fix.label());
    }

    @Test
    void shouldOfferTheOnlyCharacterWhenNoEntityIsNamedPlayer() {
        camera();
        Entity hero = scene.createEntity("Hero");
        hero.addComponent(new CharacterController3DComponent());

        ProfileProblem problem = profiles.validate(scene, walk).get(0);

        assertEquals("Hero", problem.entityName());
        assertEquals("Make Hero the player", problem.safeFix().orElseThrow().label());

        Entity second = scene.createEntity("Guard");
        second.addComponent(new CharacterController3DComponent());
        assertTrue(profiles.validate(scene, walk).get(0).safeFix().isEmpty(), "two characters: no guess");
    }

    @Test
    void shouldNameBothPlayersWhenThereAreTwo() {
        camera();
        hero();
        Entity second = scene.createEntity("Sidekick");
        second.addComponent(new CharacterController3DComponent());
        second.addComponent(new PlayerComponent());

        List<ProfileProblem> problems = profiles.validate(scene, walk);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).message().contains("Hero, Sidekick"), problems.get(0).message());
    }

    @Test
    void shouldOfferACharacterControllerOnlyWhenAddingOneIsSafe() {
        camera();
        Entity hero = scene.createEntity("Hero");
        hero.addComponent(new PlayerComponent());

        ProfileProblem walkless = profiles.validate(scene, walk).get(0);
        assertEquals("Hero cannot walk: it needs a Character Controller 3D.", walkless.message());
        assertEquals(ProfileFix.Kind.ADD_CHARACTER_CONTROLLER_3D, walkless.safeFix().orElseThrow().kind());

        hero.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        hero.addComponent(new BoxCollider3DComponent());
        ProfileProblem body = profiles.validate(scene, walk).get(0);
        assertTrue(body.message().contains("Rigid Body 3D or a 3D collider"), body.message());
        assertTrue(body.safeFix().isEmpty(), "removing a body is the person's choice");
    }

    @Test
    void shouldOfferACameraWhenThereIsNone() {
        hero();

        ProfileProblem problem = profiles.validate(scene, walk).get(0);

        assertEquals("No camera: add a Camera to see the game.", problem.message());
        assertEquals(ProfileFix.Kind.ADD_CAMERA, problem.safeFix().orElseThrow().kind());
        Entity off = camera();
        off.setActive(false);
        assertEquals(1, profiles.validate(scene, walk).size(), "an inactive camera does not count");
    }

    @Test
    void shouldLeaveAScriptedPlayerToItsScriptUnlessAskedAndKeepTheCamerasOwnRig() {
        Entity camera = camera();
        Entity hero = hero();
        assertEquals(new ProfilePlan(true, true, List.of()), profiles.plan(scene, walk), "nothing authored: the profile adds both");

        hero.addComponent(new EntityScriptBindingComponent("", "game.HeroScript", ScriptLanguage.JAVA));
        camera.addComponent(new CameraRigComponent());
        ProfilePlan authored = profiles.plan(scene, walk);
        assertFalse(authored.addMovement());
        assertFalse(authored.addCameraRig());
        assertTrue(authored.notes().get(0).startsWith("Hero is moved by its own script or graph"), authored.notes().get(0));
        assertEquals("Main Camera keeps its own Camera Rig.", authored.notes().get(1));

        hero.getComponent(PlayerComponent.class).setUseProfileMovement(true);
        assertTrue(profiles.plan(scene, walk).addMovement(), "Use Profile Movement opts in");
    }

    /** Roadmap M3b.7, slice 2: the return after a fall starts from the spawn point, and is a genre rule, not movement. */
    @Test
    void shouldPutAFallingPlayerBackAtTheSpawnPointOnlyForAProfileThatAsks() {
        camera();
        Entity hero = hero();
        hero.addComponent(new EntityScriptBindingComponent("", "game.HeroScript", ScriptLanguage.JAVA));
        Entity spawn = scene.createEntity("Spawn");
        spawn.addComponent(new SpawnPointComponent());
        spawn.getTransform().setLocalPosition(new Vector3(300f, 50f, -200f));
        scene.setGameGenre(GameGenres.PLATFORMER);

        ProfileStart start = profiles.start(scene, GameMode.MODE_3D, key -> false);

        assertEquals(List.of(), start.problems());
        assertNull(hero.getComponent(GroundWalkMover.class), "its own script moves it");
        FallRespawner respawner = hero.getComponent(FallRespawner.class);
        assertTrue(respawner != null, "falls are caught even for a scripted player");
        assertEquals(GameGenres.PLATFORMER_3D_FALL_DEPTH, respawner.getDepth());
        float standing = hero.getComponent(CharacterController3DComponent.class).getTotalHeight() / 2f + 1f;
        assertEquals(new Vector3(300f, 50f + standing, -200f), respawner.getStart(), "back on the spawn point, standing");

        Scene other = new Scene("Valley");
        other.setGameMode(GameMode.MODE_3D);
        other.setGameGenre(GameGenres.ACTION_RPG);
        Entity otherCamera = other.createEntity("Main Camera");
        otherCamera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        Entity walker = other.createEntity("Hero");
        walker.addComponent(new CharacterController3DComponent());
        walker.addComponent(new PlayerComponent());
        assertEquals(List.of(), profiles.start(other, GameMode.MODE_3D, key -> false).problems());
        assertNull(walker.getComponent(FallRespawner.class), "Action RPG + 3D does not ask for it");
    }

    /** Roadmap M3b.7, slice 3: the first-person parts, and no shot for a player moved by its own script. */
    @Test
    void shouldGiveTheFirstPersonPartsAndLeaveAScriptedPlayerToShootByItsScript() {
        Entity camera = camera();
        Entity hero = hero();
        scene.setGameGenre(GameGenres.FIRST_PERSON_SHOOTER);

        assertEquals(List.of(), profiles.start(scene, GameMode.MODE_3D, key -> false).problems());

        assertTrue(hero.getComponent(FirstPersonMover.class) != null);
        assertTrue(hero.getComponent(ProfileShooter.class) != null);
        assertNull(hero.getComponent(GroundWalkMover.class));
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);
        assertEquals(CameraRigComponent.Mode.FIRST_PERSON, rig.getMode());
        assertEquals(GameGenres.FIRST_PERSON_EYE_HEIGHT, rig.getEyeHeight());
        assertEquals(0f, rig.getPitch(), 0.001f, "level");

        Scene scripted = new Scene("Scripted");
        scripted.setGameMode(GameMode.MODE_3D);
        scripted.setGameGenre(GameGenres.FIRST_PERSON_SHOOTER);
        scripted.createEntity("Main Camera").addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        Entity gunner = scripted.createEntity("Hero");
        gunner.addComponent(new CharacterController3DComponent());
        gunner.addComponent(new PlayerComponent());
        gunner.addComponent(new EntityScriptBindingComponent("", "game.HeroScript", ScriptLanguage.JAVA));
        assertEquals(List.of(), profiles.start(scripted, GameMode.MODE_3D, key -> false).problems());
        assertNull(gunner.getComponent(FirstPersonMover.class));
        assertNull(gunner.getComponent(ProfileShooter.class), "its own script shoots, or not");
    }

    @Test
    void shouldRefuseAMoverForAnotherKindOfMovement() {
        GameTestProfile.MovementSetup walking = walk.movement();
        assertThrows(IllegalArgumentException.class, () -> new FirstPersonMover(walking, InputMapping.defaults(), key -> false));
    }

    @Test
    void shouldRefuseAFallRespawnerThatCouldNeverFireOrHasNoStart() {
        assertThrows(IllegalArgumentException.class, () -> new FallRespawner(0f, new Vector3(), new Quaternion()));
        assertThrows(IllegalArgumentException.class, () -> new FallRespawner(100f, new Vector3(Float.NaN, 0f, 0f), new Quaternion()));
        assertThrows(NullPointerException.class, () -> new FallRespawner(100f, null, new Quaternion()));
    }

    @Test
    void shouldRefuseToPlanWithoutExactlyOnePlayer() {
        camera();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> profiles.plan(scene, walk));

        assertTrue(error.getMessage().contains("validate first"), error.getMessage());
    }
}
