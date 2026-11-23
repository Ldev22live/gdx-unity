package io.github.ldev22live.gdxunity.editor.character;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.character.CharacterData;
import io.github.ldev22live.gdxunity.engine.character.CharacterSpec;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The character creator behind the Animations tab: edits and undo, templates, baking and placing (TD-053). */
class CharacterSessionTest {
    private static final File SHIPPED = new File("../assets");
    private static CharacterData data;

    @TempDir
    Path assetRoot;

    private CharacterSession session;

    @BeforeAll
    static void load() throws IOException {
        GdxNativesLoader.load();
        data = CharacterData.load(SHIPPED);
    }

    @BeforeEach
    void setUp() throws IOException {
        copy(new File(SHIPPED, "animations/humanoid").toPath(), assetRoot.resolve("animations/humanoid"));
        session = new CharacterSession(assetRoot.toFile(), data);
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }

    @Test
    void shouldUndoAndRedoEditsAndForgetRedoAfterANewEdit() {
        session.edit(spec -> spec.setGender(1f));
        session.setSlider("nose/nose-width1", -0.5f);
        assertEquals(1f, session.getSpec().getGender());
        assertEquals(-0.5f, session.getSpec().getSlider("nose/nose-width1"));

        session.undo();
        assertEquals(0f, session.getSpec().getSlider("nose/nose-width1"));
        assertTrue(session.canRedo());
        session.redo();
        assertEquals(-0.5f, session.getSpec().getSlider("nose/nose-width1"));

        session.undo();
        session.edit(spec -> spec.setAgeYears(60f));
        assertFalse(session.canRedo(), "a new edit ends the redo line");
        session.undo();
        session.undo();
        assertFalse(session.canUndo());
        assertEquals(new CharacterSpec(), session.getSpec());
    }

    @Test
    void shouldIgnoreAnEditThatChangesNothingAndKeepTheOldSettingsWhenOneIsRefused() {
        session.edit(spec -> spec.setGender(CharacterSpec.DEFAULT_GENDER));
        assertFalse(session.canUndo(), "no change, no history step");

        session.edit(spec -> spec.setMuscle(0.9f));
        assertThrows(IllegalArgumentException.class, () -> session.edit(spec -> {
            spec.setMuscle(0.1f);
            spec.setGender(5f);
        }));
        assertEquals(0.9f, session.getSpec().getMuscle(), "the refused edit changed nothing");
        assertThrows(IllegalArgumentException.class, () -> session.setSlider("nose/no-such-slider", 0.5f));
        assertThrows(IllegalArgumentException.class, () -> session.setSlider("nose/nose-width1", 2f));
    }

    @Test
    void shouldKeepAtMostTheLastHundredSteps() {
        for (int i = 0; i < CharacterSession.MAX_HISTORY + 20; i++) {
            float age = 1f + i * 0.5f;
            session.edit(spec -> spec.setAgeYears(age));
        }
        int steps = 0;
        while (session.canUndo()) {
            session.undo();
            steps++;
        }
        assertEquals(CharacterSession.MAX_HISTORY, steps);
    }

    @Test
    void shouldResetToTheNeutralCharacterAsOneStep() {
        session.edit(spec -> {
            spec.setGender(1f);
            spec.setSlider("head/head-round", 1f);
        });
        session.reset();
        assertEquals(new CharacterSpec(), session.getSpec());
        session.undo();
        assertEquals(1f, session.getSpec().getGender());
    }

    @Test
    void shouldOfferTheHumanoidClipsAsTemplatesAndBakeOnlyTheSelectedOnes() throws IOException {
        assertEquals(List.of("Crouch", "Dance", "Fall", "Idle", "Jump", "LookAround", "Punch", "Run", "Walk", "Wave"), session.availableTemplates());
        assertEquals(session.availableTemplates(), session.selectedTemplates());
        for (String template : session.availableTemplates()) {
            session.setTemplateSelected(template, !List.of("Idle", "Run", "Walk").contains(template));
            session.setTemplateSelected(template, List.of("Idle", "Run", "Walk").contains(template));
        }
        assertThrows(IllegalArgumentException.class, () -> session.setTemplateSelected("Flying", true));

        CharacterSession.BakeResult baked = session.bake("Hero");

        JsonValue gltf = new JsonReader().parse(Files.readString(assetRoot.resolve(baked.model().getPath())));
        List<String> names = new java.util.ArrayList<>();
        for (JsonValue animation = gltf.get("animations").child; animation != null; animation = animation.next) {
            names.add(animation.getString("name"));
        }
        assertEquals(List.of("Idle", "Run", "Walk"), names);
        assertEquals("characters/Hero.gltf", baked.model().getPath());
        assertTrue(baked.problems().isEmpty(), baked.problems().toString());
    }

    @Test
    void shouldGiveThePreviewTheSameModelAsBakingWithoutWritingAnyFile() throws IOException {
        session.edit(spec -> spec.setGender(1f));
        session.setTemplateSelected("Fall", false);

        CharacterSession.BakedModel preview = session.bakeModel();

        assertFalse(Files.exists(assetRoot.resolve("characters")), "previewing writes nothing");
        CharacterSession.BakeResult baked = session.bake("Hero");
        assertEquals(Files.readString(assetRoot.resolve(baked.model().getPath())), preview.gltf());
        assertEquals(baked.problems(), preview.problems());
        JsonValue gltf = new JsonReader().parse(preview.gltf());
        int animations = 0;
        for (JsonValue animation = gltf.get("animations").child; animation != null; animation = animation.next) {
            animations++;
        }
        assertEquals(9, animations, "all templates but the deselected Fall");
    }

    @Test
    void shouldChangeThePreviewModelWhenTheSettingsChange() throws IOException {
        String before = session.bakeModel().gltf();
        session.edit(spec -> spec.setHeightScale(1.2f));

        assertFalse(before.equals(session.bakeModel().gltf()), "a taller character is a different model");
        session.undo();
        assertEquals(before, session.bakeModel().gltf(), "undo brings the earlier model back");
    }

    @Test
    void shouldSaveTheSettingsNextToTheModelAndOpenThemAgain() throws IOException {
        session.edit(spec -> {
            spec.setGender(0.1f);
            spec.setAgeYears(70f);
            spec.setSlider("head/head-round", 0.6f);
        });
        CharacterSpec saved = session.getSpec();
        session.bake("Gran");

        session.edit(spec -> spec.setGender(1f));
        session.load("Gran");

        assertEquals(saved, session.getSpec());
        assertFalse(session.canUndo(), "opening a character starts the history over");
        assertThrows(IOException.class, () -> session.load("Nobody"));
        assertThrows(IllegalArgumentException.class, () -> session.bake("../escape"));
        assertThrows(IllegalArgumentException.class, () -> session.bake(" "));
    }

    @Test
    void shouldBakeDifferentSettingsToDifferentHeights() throws IOException {
        session.edit(spec -> spec.setHeightScale(1.2f));
        float tall = session.build().height();
        session.undo();
        assertEquals(tall / 1.2f, session.build().height(), 1e-3f);
    }

    @Test
    void shouldPlaceTheBakedCharacterAsOneUndoableEntityShowingAClip() throws IOException {
        EditorState editorState = new EditorState();
        CharacterSession.BakeResult baked = session.bake("Placed Hero");

        assertTrue(session.place(editorState, "Placed Hero", baked.model(), "Walk"));

        Entity entity = editorState.getActiveScene().findEntityByName("Placed Hero");
        assertNotNull(entity);
        ModelComponent model = entity.getComponent(ModelComponent.class);
        assertEquals("characters/Placed Hero.gltf", model.getModelPath());
        assertEquals("Walk", model.getAnimation());
        assertEquals(100f, model.getImportScale());
        editorState.undo();
        assertNull(editorState.getActiveScene().findEntityByName("Placed Hero"));
    }
}
