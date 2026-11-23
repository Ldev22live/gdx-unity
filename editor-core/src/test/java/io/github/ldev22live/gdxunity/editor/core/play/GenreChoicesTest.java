package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.GenreStatus;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The footer's Game Genre choices (roadmap M3b.4, TD-088): fed by the registry, marked per mode, persisted, never faked. */
class GenreChoicesTest {
    private final GameGenres genres = GameGenres.standard();
    private EditorState editorState;

    @TempDir
    Path temp;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState(new SceneSerializer(new ComponentSerializerRegistry()));
        Scene scene = new Scene("World");
        scene.setGameMode(GameMode.MODE_3D);
        editorState.setActiveScene(scene);
    }

    @Test
    void shouldOfferNoneThenEveryRegisteredGenreMarkedForTheScenesMode() {
        List<GenreChoices.Choice> choices = GenreChoices.choices(genres, null, GameMode.MODE_3D);

        assertEquals(1 + genres.genres().size(), choices.size(), "None plus the registry, nothing hard-coded");
        assertEquals("None", choices.get(0).display());
        assertNull(choices.get(0).genreId());
        assertEquals("Platformer / Side-scroller", choices.get(1).display(), "Platformer plays in 3D (M3b.7 slice 2)");
        assertTrue(choices.get(1).isPlayable());
        assertEquals("Action RPG", choices.get(2).display(), "Action RPG plays in 3D");
        assertTrue(choices.get(2).isPlayable());
        assertEquals("Adventure (planned)", choices.get(3).display(), "Adventure is planned in 3D");

        List<GenreChoices.Choice> side = GenreChoices.choices(genres, null, GameMode.MODE_2_5D);
        assertEquals("Platformer / Side-scroller", side.get(1).display());
        assertEquals("Action RPG", side.get(2).display(), "validated on its own (M3c.3)");
        assertTrue(side.stream().anyMatch(c -> c.display().equals("First-person Shooter (not offered)")));
        long playable = GenreChoices.choices(genres, null, GameMode.MODE_2D).stream().filter(GenreChoices.Choice::isPlayable).count();
        assertEquals(0, playable, "nothing plays in 2D yet, and nothing says it does");
    }

    @Test
    void shouldKeepAnUnknownGenreAsItsOwnEntry() {
        List<GenreChoices.Choice> choices = GenreChoices.choices(genres, "rhythm", GameMode.MODE_2D);

        GenreChoices.Choice unknown = GenreChoices.current(choices, "rhythm");
        assertEquals("'rhythm' (unknown)", unknown.display());
        assertNull(unknown.status());
        assertEquals("Unknown: Play refuses it", GenreChoices.statusLine(genres, "rhythm", GameMode.MODE_2D));
    }

    @Test
    void shouldSayWhetherPlayCanUseTheGenreInTheMode() {
        assertEquals(GenreChoices.NONE_STATUS, GenreChoices.statusLine(genres, null, GameMode.MODE_3D));
        assertEquals("Playable", GenreChoices.statusLine(genres, GameGenres.ACTION_RPG, GameMode.MODE_3D));
        assertEquals("Planned: Play refuses it", GenreChoices.statusLine(genres, GameGenres.ACTION_RPG, GameMode.MODE_2D));
        assertEquals("Not offered: Play refuses it",
            GenreChoices.statusLine(genres, GameGenres.FIRST_PERSON_SHOOTER, GameMode.MODE_2D));
        assertEquals(List.of(GameMode.MODE_2_5D, GameMode.MODE_3D), GenreChoices.playableModes(genres, GameGenres.PLATFORMER));
        assertEquals(List.of(), GenreChoices.playableModes(genres, GameGenres.RACING));
    }

    @Test
    void shouldSetTheGenreAndSayPlainlyWhenItHasNoGameplayYet() {
        editorState.setGameMode(GameMode.MODE_2D);
        assertTrue(GenreChoices.choose(editorState, genres, GameGenres.PLATFORMER));

        assertEquals(GameGenres.PLATFORMER, editorState.getGameGenre());
        String message = editorState.getStatusMessage();
        assertTrue(message.startsWith("Game genre: Platformer / Side-scroller. Platformer / Side-scroller + 2D has no gameplay yet, "
            + "and Play will refuse it."), message);
        assertTrue(message.endsWith("It plays in 2.5D, 3D."), message);
        assertEquals(GenreStatus.PLANNED, GenreChoices.current(GenreChoices.choices(genres, GameGenres.PLATFORMER,
            editorState.getGameMode()), GameGenres.PLATFORMER).status());
        editorState.setGameMode(GameMode.MODE_3D);

        assertTrue(GenreChoices.choose(editorState, genres, GameGenres.ACTION_RPG));
        assertTrue(editorState.getStatusMessage().startsWith("Game genre: Action RPG. In 3D Play adds its camera and movement"),
            editorState.getStatusMessage());

        assertFalse(GenreChoices.choose(editorState, genres, GameGenres.ACTION_RPG), "the same choice changes nothing");
        assertTrue(GenreChoices.choose(editorState, genres, null));
        assertNull(editorState.getGameGenre());
        assertEquals("Game genre: none. Play runs the scene's own scripts, as before.", editorState.getStatusMessage());
        assertEquals(0, editorState.getCommandHistory().getUndoCount(), "a scene setting, not an undo step, like the game mode");
    }

    @Test
    void shouldPersistTheChoiceWithTheScene() throws IOException {
        GenreChoices.choose(editorState, genres, GameGenres.ACTION_RPG);
        File file = temp.resolve("world.json").toFile();
        editorState.saveSceneToFile(file);

        EditorState reopened = new EditorState(new SceneSerializer(new ComponentSerializerRegistry()));
        reopened.loadSceneFromFile(file);

        assertEquals(GameGenres.ACTION_RPG, reopened.getGameGenre());
        assertEquals(GameMode.MODE_3D, reopened.getGameMode());
    }
}
