package io.github.ldev22live.gdxunity.engine.scene.play;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scene file's optional {@code gameGenre}, the Player component, and runtime-only components (roadmap M3b.2, TD-088). */
class SceneGenreSerializationTest {
    private SceneSerializer serializer;

    /** What a profile adds at Play: never saved. */
    private static final class Mover extends BaseComponent implements RuntimeOnlyComponent {
    }

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new PlayerComponentSerializer());
        serializer = new SceneSerializer(registry);
    }

    @Test
    void shouldSaveAndLoadTheGenreBesideTheGameMode() {
        Scene scene = new Scene("Valley");
        scene.setGameMode(GameMode.MODE_3D);
        scene.setGameGenre(GameGenres.ACTION_RPG);

        String json = serializer.toJson(scene);
        Scene loaded = serializer.fromJson(json);

        assertEquals("action-rpg", new JsonReader().parse(json).getString("gameGenre"), json);
        assertEquals("action-rpg", loaded.getGameGenre());
        assertEquals(GameMode.MODE_3D, loaded.getGameMode());
    }

    @Test
    void shouldLoadOlderFilesWithoutAGenreAndWriteNone() {
        Scene loaded = serializer.fromJson("{\"schemaVersion\":1,\"name\":\"Old\",\"gameMode\":\"2.5d\",\"entities\":[]}");

        assertNull(loaded.getGameGenre());
        assertFalse(serializer.toJson(loaded).contains("gameGenre"), "no field is written for no genre");
    }

    @Test
    void shouldKeepAGenreThisVersionDoesNotKnow() {
        Scene loaded = serializer.fromJson("{\"schemaVersion\":1,\"name\":\"Newer\",\"gameGenre\":\"rhythm\",\"entities\":[]}");

        assertEquals("rhythm", loaded.getGameGenre(), "a newer or web-written file is not changed by loading it");
        assertEquals("rhythm", new JsonReader().parse(serializer.toJson(loaded)).getString("gameGenre"), "nor by saving it");
    }

    @Test
    void shouldRefuseAGenreValueThatIsNotAnId() {
        SceneSerializationException error = assertThrows(SceneSerializationException.class,
            () -> serializer.fromJson("{\"schemaVersion\":1,\"name\":\"Bad\",\"gameGenre\":\"Action RPG\",\"entities\":[]}"));

        assertTrue(error.getMessage().contains("gameGenre"), error.getMessage());
        Scene scene = new Scene("x");
        scene.setGameGenre("  ");
        assertNull(scene.getGameGenre(), "blank means none");
    }

    @Test
    void shouldRoundTripThePlayerAndItsMovementSwitch() {
        Scene scene = new Scene("Level");
        Entity hero = scene.createEntity("Hero");
        PlayerComponent player = hero.addComponent(new PlayerComponent());
        player.setUseProfileMovement(true);

        Entity loaded = serializer.fromJson(serializer.toJson(scene)).findEntityById(hero.getId());

        assertTrue(loaded.getComponent(PlayerComponent.class).isUseProfileMovement());
        assertFalse(new PlayerComponent().isUseProfileMovement(), "off by default: the author's own logic wins");
    }

    @Test
    void shouldRefuseToSaveARuntimeOnlyComponent() {
        Scene scene = new Scene("Playing");
        scene.createEntity("Hero").addComponent(new Mover());

        SceneSerializationException error = assertThrows(SceneSerializationException.class, () -> serializer.toJson(scene));

        assertTrue(error.getMessage().contains("'Hero'"), error.getMessage());
        assertTrue(error.getMessage().contains("never saved"), error.getMessage());
    }
}
