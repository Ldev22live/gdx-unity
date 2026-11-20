package io.github.ldev22live.gdxunity.engine.render.play;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.GenreSupport;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The support table's truth test (TD-088): a genre and mode pair may be marked playable only when an acceptance test that plays it
 * exists. Making a pair playable means adding its test here, in the same change (M3b.3: Platformer + 2.5D; M3c.1: Action RPG + 3D).
 */
class PlayableGenrePairsTest {
    /** Each playable pair ("genre id/mode file value") and the repository path of the acceptance test that plays it. */
    private static final Map<String, String> ACCEPTANCE_TESTS = Map.of(
        "platformer/2.5d", "engine-desktop/src/test/java/io/github/ldev22live/gdxunity/engine/desktop/PlatformerSideProfileOnJoltTest.java",
        "platformer/3d", "engine-desktop/src/test/java/io/github/ldev22live/gdxunity/engine/desktop/PlatformerChaseProfileOnJoltTest.java",
        "first-person-shooter/3d",
        "engine-desktop/src/test/java/io/github/ldev22live/gdxunity/engine/desktop/FirstPersonRangeProfileOnJoltTest.java",
        "action-rpg/3d", "engine-desktop/src/test/java/io/github/ldev22live/gdxunity/engine/desktop/ActionRpgValleyProfileOnJoltTest.java",
        "action-rpg/2.5d", "engine-desktop/src/test/java/io/github/ldev22live/gdxunity/engine/desktop/ActionRpgIsometricProfileOnJoltTest.java");

    @Test
    void shouldMarkAPairPlayableOnlyWhenItsAcceptanceTestExists() {
        for (GenreSupport pair : GameGenres.standard().playablePairs()) {
            String key = pair.genre().id() + "/" + pair.mode().getFileValue();
            String test = ACCEPTANCE_TESTS.get(key);
            assertTrue(test != null, key + " is marked playable but no acceptance test is listed for it");
            assertTrue(new File("..", test).isFile(), key + ": its acceptance test " + test + " does not exist");
        }
    }

    @Test
    void shouldListOnlyPairsTheRegistryKnows() {
        GameGenres genres = GameGenres.standard();
        for (String key : ACCEPTANCE_TESTS.keySet()) {
            String[] parts = key.split("/");
            GameMode mode = GameMode.fromFileValue(parts[1]);
            assertTrue(genres.find(parts[0]).isPresent() && mode != null, key + " is not a genre and mode pair");
            assertTrue(genres.support(parts[0], mode).isPlayable(), key + " has an acceptance test but is not marked playable");
        }
    }
}
