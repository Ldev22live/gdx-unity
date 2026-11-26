package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The genre registry and its support table (roadmap M3b.2, TD-088). */
class GameGenresTest {
    private static final GameGenre PLATFORMER = new GameGenre(GameGenres.PLATFORMER, "Platformer / Side-scroller",
        "Run and jump across platforms.");

    static GameTestProfile sideProfile(GameGenre genre) {
        return new GameTestProfile(genre, GameMode.MODE_2_5D,
            new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.SIDE_FOLLOW, 900f, 150f, 10f, 6f),
            new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.PLANE_RUN, 400f, 600f, 360f),
            GameTestProfile.Physics.PHYSICS_3D, InputMapping.defaults(),
            Set.of(GameTestProfile.Requirement.PLAYER, GameTestProfile.Requirement.CHARACTER_3D, GameTestProfile.Requirement.CAMERA));
    }

    @Test
    void shouldListTheElevenRoadmapGenresInOrder() {
        List<String> ids = GameGenres.standard().genres().stream().map(GameGenre::id).toList();

        assertEquals(List.of("platformer", "action-rpg", "adventure", "first-person-shooter", "third-person-shooter",
            "real-time-strategy", "turn-based-strategy", "racing", "puzzle", "simulation", "sandbox"), ids);
    }

    @Test
    void shouldGiveEveryOneOfTheThirtyThreePairsAStatusAndAReason() {
        GameGenres genres = GameGenres.standard();

        List<GenreSupport> pairs = genres.pairs();

        assertEquals(33, pairs.size());
        for (GenreSupport pair : pairs) {
            assertFalse(pair.reason().isBlank(), pair.genre().id() + " " + pair.mode());
        }
        List<String> playable = genres.playablePairs().stream().map(p -> p.genre().id() + "/" + p.mode().getFileValue()).toList();
        assertEquals(List.of("platformer/2d", "platformer/2.5d", "platformer/3d", "action-rpg/2.5d", "action-rpg/3d",
            "first-person-shooter/3d", "third-person-shooter/3d"), playable,
            "only the pairs with an acceptance test (M3c.7, M3b.3, M3b.7, M3c.3, M3c.1, M3b.7 slices 3 and 4)");
    }

    @Test
    void shouldNotOfferShootersIn2DOr2_5DAndPlanTheOwnersSlices() {
        GameGenres genres = GameGenres.standard();

        for (GameMode mode : List.of(GameMode.MODE_2D, GameMode.MODE_2_5D)) {
            assertEquals(GenreStatus.NOT_OFFERED, genres.support(GameGenres.FIRST_PERSON_SHOOTER, mode).status());
            assertEquals(GenreStatus.NOT_OFFERED, genres.support(GameGenres.THIRD_PERSON_SHOOTER, mode).status());
        }
        GenreSupport actionRpg = genres.support(GameGenres.ACTION_RPG, GameMode.MODE_3D);
        assertEquals(GenreStatus.PLAYABLE, actionRpg.status());
        assertEquals(GameTestProfile.MovementSetup.Kind.GROUND_WALK, actionRpg.profile().movement().kind());
        assertEquals(GameTestProfile.CameraSetup.Kind.THIRD_PERSON, actionRpg.profile().camera().kind());
        GenreSupport actionRpgIso = genres.support(GameGenres.ACTION_RPG, GameMode.MODE_2_5D);
        assertEquals(GenreStatus.PLAYABLE, actionRpgIso.status(), "validated on its own (M3c.3)");
        assertEquals(GameTestProfile.CameraSetup.Kind.ISOMETRIC, actionRpgIso.profile().camera().kind());
        assertEquals(GameTestProfile.MovementSetup.Kind.GROUND_WALK, actionRpgIso.profile().movement().kind());
        assertEquals(GenreStatus.PLANNED, genres.support(GameGenres.ACTION_RPG, GameMode.MODE_2D).status());
        GenreSupport side = genres.support(GameGenres.PLATFORMER, GameMode.MODE_2_5D);
        assertEquals(GenreStatus.PLAYABLE, side.status());
        assertEquals(GameTestProfile.MovementSetup.Kind.PLANE_RUN, side.profile().movement().kind());
        assertEquals(GameTestProfile.CameraSetup.Kind.SIDE_FOLLOW, side.profile().camera().kind());
        GenreSupport flat = genres.support(GameGenres.PLATFORMER, GameMode.MODE_2D);
        assertEquals(GenreStatus.PLAYABLE, flat.status(), "TD-105, roadmap M3c.7");
        assertEquals(GameTestProfile.MovementSetup.Kind.RUN_2D, flat.profile().movement().kind());
        assertEquals(GameTestProfile.CameraSetup.Kind.FOLLOW_2D, flat.profile().camera().kind());
        assertEquals(GameTestProfile.Physics.PHYSICS_2D, flat.profile().physics());
        assertTrue(flat.profile().requirements().contains(GameTestProfile.Requirement.CHARACTER_2D));
    }

    @Test
    void shouldRegisterAnotherGenreWithEveryModePlannedAndRefuseADuplicateId() {
        GameGenres genres = GameGenres.standard();
        GameGenre rhythm = new GameGenre("rhythm", "Rhythm", "Press keys to the beat.");

        genres.register(rhythm);

        assertEquals(12, genres.genres().size());
        for (GameMode mode : GameMode.values()) {
            assertEquals(GenreStatus.PLANNED, genres.support("rhythm", mode).status());
        }
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
            () -> genres.register(new GameGenre("rhythm", "Other", "x")));
        assertTrue(duplicate.getMessage().contains("already registered"), duplicate.getMessage());
        assertTrue(genres.find("nope").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> genres.support("nope", GameMode.MODE_3D));
    }

    @Test
    void shouldMakeAPairPlayableOnlyWithItsOwnProfile() {
        GameGenres genres = GameGenres.standard();
        GameTestProfile profile = sideProfile(PLATFORMER);

        assertThrows(IllegalArgumentException.class, () -> new GenreSupport(PLATFORMER, GameMode.MODE_2_5D, GenreStatus.PLAYABLE,
            "Playable.", null), "playable needs a profile");
        assertThrows(IllegalArgumentException.class, () -> new GenreSupport(PLATFORMER, GameMode.MODE_2_5D, GenreStatus.PLANNED,
            "Planned.", profile), "planned has none");
        assertThrows(IllegalArgumentException.class, () -> new GenreSupport(PLATFORMER, GameMode.MODE_3D, GenreStatus.PLAYABLE,
            "Playable.", profile), "the profile is for 2.5D");

        int playable = genres.playablePairs().size();
        genres.setSupport(new GenreSupport(PLATFORMER, GameMode.MODE_2_5D, GenreStatus.PLAYABLE, "Playable.", profile));

        assertEquals(playable, genres.playablePairs().size(), "replaced, not added");
        assertEquals(profile, genres.support(GameGenres.PLATFORMER, GameMode.MODE_2_5D).profile());
        assertThrows(IllegalArgumentException.class, () -> genres.setSupport(new GenreSupport(new GameGenre("rhythm", "Rhythm", "x"),
            GameMode.MODE_3D, GenreStatus.PLANNED, "x", null)), "unregistered genre");
    }

    /** Roadmap M3b.7, slice 2. */
    @Test
    void shouldPlayPlatformer3DWithAChaseCameraRunningAndAReturnAfterAFall() {
        GenreSupport chase = GameGenres.standard().support(GameGenres.PLATFORMER, GameMode.MODE_3D);

        assertEquals(GenreStatus.PLAYABLE, chase.status());
        GameTestProfile profile = chase.profile();
        assertEquals(GameTestProfile.CameraSetup.Kind.THIRD_PERSON, profile.camera().kind());
        assertEquals(GameGenres.CHASE_DISTANCE, profile.camera().distance());
        assertTrue(profile.camera().pitchDegrees() > GameGenres.THIRD_PERSON_PITCH, "looks down more than Action RPG, at the landings");
        assertEquals(GameTestProfile.MovementSetup.Kind.GROUND_WALK, profile.movement().kind());
        assertTrue(profile.movement().jumpSpeed() > GameGenres.ACTION_RPG_JUMP_SPEED, "jumps higher than Action RPG");
        assertTrue(profile.respawnsAfterFalls());
        assertEquals(GameGenres.PLATFORMER_3D_FALL_DEPTH, profile.fallRespawnDepth());
        assertFalse(GameGenres.standard().support(GameGenres.ACTION_RPG, GameMode.MODE_3D).profile().respawnsAfterFalls(),
            "a valley has no gaps to fall through");
        assertTrue(chase.reason().contains("fall"), chase.reason());
    }

    /** Roadmap M3b.7, slice 3. */
    @Test
    void shouldPlayFirstPersonShooter3DFromTheEyesWithStrafingAndAShot() {
        GenreSupport pair = GameGenres.standard().support(GameGenres.FIRST_PERSON_SHOOTER, GameMode.MODE_3D);

        assertEquals(GenreStatus.PLAYABLE, pair.status());
        GameTestProfile profile = pair.profile();
        assertEquals(GameTestProfile.CameraSetup.Kind.FIRST_PERSON, profile.camera().kind());
        assertEquals(GameGenres.FIRST_PERSON_EYE_HEIGHT, profile.camera().height());
        assertEquals(GameTestProfile.MovementSetup.Kind.FIRST_PERSON_WALK, profile.movement().kind());
        assertTrue(profile.hasShot());
        assertEquals(GameGenres.SHOT_RANGE, profile.shot().range());
        assertEquals(GameGenres.SHOT_KNOCK_SPEED, profile.shot().knockSpeed());
        assertFalse(profile.respawnsAfterFalls());
        assertEquals(GameTestProfile.ShotSetup.Origin.CAMERA_VIEW, profile.shot().origin(), "from the eyes, along the view");
        for (GenreSupport other : GameGenres.standard().playablePairs()) {
            boolean shooter = other.genre().id().equals(GameGenres.FIRST_PERSON_SHOOTER)
                || other.genre().id().equals(GameGenres.THIRD_PERSON_SHOOTER);
            assertEquals(shooter, other.profile().hasShot(), other.genre().id() + " " + other.mode());
        }
    }

    /** Roadmap M3b.7, slice 4. */
    @Test
    void shouldPlayThirdPersonShooter3DFromBehindTheWayThePlayerFacesWithALevelShot() {
        GenreSupport pair = GameGenres.standard().support(GameGenres.THIRD_PERSON_SHOOTER, GameMode.MODE_3D);

        assertEquals(GenreStatus.PLAYABLE, pair.status());
        assertTrue(pair.reason().contains("shoots straight ahead"), pair.reason());
        GameTestProfile profile = pair.profile();
        assertEquals(GameTestProfile.CameraSetup.Kind.BEHIND_FACING, profile.camera().kind());
        assertEquals(GameGenres.BEHIND_DISTANCE, profile.camera().distance());
        assertEquals(GameGenres.BEHIND_PITCH, profile.camera().pitchDegrees());
        assertEquals(GameTestProfile.MovementSetup.Kind.FIRST_PERSON_WALK, profile.movement().kind(),
            "the turn keys turn the player, which aims; A and D step sideways");
        assertTrue(profile.hasShot());
        assertEquals(GameTestProfile.ShotSetup.Origin.PLAYER_FACING, profile.shot().origin());
        assertEquals(GameGenres.THIRD_PERSON_AIM_HEIGHT, profile.shot().aimHeight());
        assertEquals(GameGenres.SHOT_RANGE, profile.shot().range());
        assertFalse(profile.respawnsAfterFalls());
    }

    @Test
    void shouldRefuseAShotOriginWithoutAnAimOrAnAimHeightAlongTheCamera() {
        assertThrows(NullPointerException.class, () -> new GameTestProfile.ShotSetup(100f, 100f, null, 0f));
        IllegalArgumentException camera = assertThrows(IllegalArgumentException.class,
            () -> new GameTestProfile.ShotSetup(100f, 100f, GameTestProfile.ShotSetup.Origin.CAMERA_VIEW, 30f));
        assertTrue(camera.getMessage().contains("must be 0 along the camera's view"), camera.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> new GameTestProfile.ShotSetup(100f, 100f, GameTestProfile.ShotSetup.Origin.PLAYER_FACING, Float.NaN));
        assertEquals(GameTestProfile.ShotSetup.Origin.CAMERA_VIEW, new GameTestProfile.ShotSetup(100f, 100f).origin());
    }

    @Test
    void shouldRefuseAShotThatCannotWork() {
        GameTestProfile valid = sideProfile(PLATFORMER);
        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile.ShotSetup(0f, 600f));
        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile.ShotSetup(5000f, Float.POSITIVE_INFINITY));
        GameTestProfile.CameraSetup flat = new GameTestProfile.CameraSetup(GameTestProfile.CameraSetup.Kind.SIDE_FOLLOW, 100f, 0f, 0f, 0f);
        GameTestProfile.MovementSetup walk = new GameTestProfile.MovementSetup(GameTestProfile.MovementSetup.Kind.GROUND_WALK, 1f, 1f, 1f);
        IllegalArgumentException flatShot = assertThrows(IllegalArgumentException.class, () -> new GameTestProfile(PLATFORMER,
            GameMode.MODE_2D, flat, walk, GameTestProfile.Physics.PHYSICS_2D, valid.input(), Set.of(), 0f,
            new GameTestProfile.ShotSetup(100f, 100f)));
        assertTrue(flatShot.getMessage().contains("3D physics"), flatShot.getMessage());
    }

    @Test
    void shouldRefuseANegativeFallDepthAndAReturnWithoutACharacter() {
        GameTestProfile valid = sideProfile(PLATFORMER);
        assertEquals(0f, valid.fallRespawnDepth(), "the seven-part constructor never puts a player back");
        assertFalse(valid.respawnsAfterFalls());

        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile(PLATFORMER, GameMode.MODE_2_5D, valid.camera(),
            valid.movement(), valid.physics(), valid.input(), valid.requirements(), -1f));
        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile(PLATFORMER, GameMode.MODE_2_5D, valid.camera(),
            valid.movement(), valid.physics(), valid.input(), valid.requirements(), Float.NaN));
        IllegalArgumentException noCharacter = assertThrows(IllegalArgumentException.class, () -> new GameTestProfile(PLATFORMER,
            GameMode.MODE_2_5D, valid.camera(), valid.movement(), valid.physics(), valid.input(),
            Set.of(GameTestProfile.Requirement.PLAYER), 500f));
        assertTrue(noCharacter.getMessage().contains("Character Controller 3D"), noCharacter.getMessage());
    }

    @Test
    void shouldRefuseBadGenreIdsAndProfilesThatCannotWork() {
        assertThrows(IllegalArgumentException.class, () -> new GameGenre("Action RPG", "Action RPG", "x"));
        assertThrows(IllegalArgumentException.class, () -> new GameGenre("rpg", " ", "x"));
        GameTestProfile valid = sideProfile(PLATFORMER);
        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile(PLATFORMER, GameMode.MODE_2D, valid.camera(),
            valid.movement(), GameTestProfile.Physics.PHYSICS_3D, valid.input(), valid.requirements()), "2D runs on Box2D");
        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile(PLATFORMER, GameMode.MODE_2D, valid.camera(),
            valid.movement(), GameTestProfile.Physics.PHYSICS_2D, valid.input(), valid.requirements()), "a 3D character in 2D physics");
        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile.MovementSetup(
            GameTestProfile.MovementSetup.Kind.GROUND_WALK, 0f, 600f, 360f));
        assertThrows(IllegalArgumentException.class, () -> new GameTestProfile.CameraSetup(
            GameTestProfile.CameraSetup.Kind.THIRD_PERSON, 400f, 100f, 90f, 6f));
    }
}
