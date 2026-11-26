package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetImport;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetPlacement;
import io.github.ldev22live.gdxunity.editor.core.asset.SpriteReload;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.NoDialogs;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.RecordingView;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteVectorSources;
import io.github.ldev22live.gdxunity.editor.sprite.VectorSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.sprite.PixelPng;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteHitbox;
import io.github.ldev22live.gdxunity.engine.vector.Rgba;
import io.github.ldev22live.gdxunity.engine.vector.VectorFile;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.FRAME;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.newEditor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap MC.3, the 2D Studio slice (Appendix C4), its done-when: "the user changes the roof colour and window shape of a 2D house,
 * edits an animated character frame, returns to the world, plays, stops, saves and reopens with the changes intact." The house is the
 * ForgeRoom-original fixture ({@code fixtures/2d-sources/house}): a {@code .vector} drawing and a layered sprite whose Wall, Roof and
 * Window layers are drawn from it (TD-104). The character is the shipped hero sprite of the Sprite Animation sample.
 *
 * <p>Play runs the 2D scene without a genre: Platformer + 2D is a planned pair that Play refuses (roadmap M3c.7), and the done-when
 * does not ask for a genre.
 */
class TwoDHouseSliceTest {
    /** Tests run in the module folder; the shipped assets and fixtures are one level up. */
    private static final File ASSETS = new File("../assets");
    private static final Path FIXTURE = Path.of("..", "fixtures", "2d-sources", "house");
    private static final String HOUSE = "sprites/house.sprite";
    private static final String DRAWING = "vectors/house.vector";
    private static final String HERO = "sprites/hero.sprite";
    private static final String HERO_ENTITY = "Walking Hero";
    /** Where the evidence pictures go; copied into the report by hand. */
    private static final Path EVIDENCE = Path.of("build", "mc-3");
    private static final int EVIDENCE_ZOOM = 8;

    private static final Rgba NEW_ROOF = Rgba.hex("#2e5fb0");
    private static final int NEW_ROOF_RGBA = 0x2E5FB0FF;
    private static final int OLD_ROOF_RGBA = 0xB03A2EFF;
    private static final int WINDOW_RGBA = 0x9FD3F0FF;
    /** Inside the roof, away from its outline. */
    private static final int[] ROOF_PIXEL = {24, 16};
    /** Above the window's old top edge and below its new one: wall before, window after. */
    private static final int[] RAISED_WINDOW_PIXEL = {31, 26};
    /** How far the window's top edge is raised, in drawing units. */
    private static final float WINDOW_RAISE = 3f;
    private static final int HERO_FRAME = 3;
    private static final int PAINT = 0xFF00FFFF;
    private static final SpriteHitbox SWING = new SpriteHitbox("attack", 8, 4, 8, 6);

    @TempDir
    Path temp;

    private static Input previousInput;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false
                : method.getReturnType() == int.class ? 0
                : method.getReturnType() == float.class ? 0f
                : method.getReturnType() == long.class ? 0L : null);
    }

    @AfterAll
    static void restoreInput() {
        Gdx.input = previousInput;
    }

    @Test
    void shouldChangeTheHouseRoofAndWindowAndACharacterFrameThroughPlaySaveAndReopen() throws Exception {
        byte[] shippedHero = Files.readAllBytes(ASSETS.toPath().resolve(HERO));
        byte[] fixtureSprite = Files.readAllBytes(FIXTURE.resolve("house.sprite"));
        byte[] fixtureDrawing = Files.readAllBytes(FIXTURE.resolve("house.vector"));

        // The Sprite Animation sample as a new project; the house's drawing and sprite imported, the house dragged into the World
        EditorState editor = newEditor();
        TemplateGallery.Choice sample = TemplateGallery.samples(ASSETS).stream().filter(c -> c.title().equals("Sprite Animation"))
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(sample, ASSETS, temp.resolve("project").toFile(), "Houses",
            editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        Scene scene = editor.getActiveScene();
        assertEquals(GameMode.MODE_2D, scene.getGameMode());
        assertEquals(DRAWING, AssetImport.importFile(root, FIXTURE.resolve("house.vector").toFile()).asset(), "vectors/ for drawings");
        AssetImport.Result importedHouse = AssetImport.importFile(root, FIXTURE.resolve("house.sprite").toFile());
        assertEquals(HOUSE, importedHouse.asset());
        assertEquals(AssetPlacement.Result.PLACED, AssetPlacement.place(editor, new AssetReference(HOUSE), new Vector3(-60f, 0f, 0f)));
        Entity house = scene.findEntityByName("house");
        assertNotNull(house);
        Entity hero = scene.findEntityByName(HERO_ENTITY);
        assertNotNull(hero);
        SpriteDocument before = loaded(root, house).getDocument();
        assertEquals(OLD_ROOF_RGBA, flatPixel(before, 0, ROOF_PIXEL));
        assertNotEquals(WINDOW_RGBA, flatPixel(before, 0, RAISED_WINDOW_PIXEL));
        writeEvidence("house-before.png", before);
        WorkspaceNavigator navigator = new WorkspaceNavigator(editor, new RecordingView());

        // Customize > Edit 2D Asset on the house: 2D Studio, the sprite and the drawing its layers come from
        editor.setSelectedEntity(house);
        CustomizeGateway.EditOffer offer = CustomizeGateway.editSelected(house, editor.getSerializer().getRegistry()).get(0);
        assertEquals("Edit 2D Asset house.sprite", offer.label());
        assertEquals(CustomizeGateway.Kind.OPEN_ASSET, offer.kind());
        assertEquals(Workspace.STUDIO_2D, offer.workspace());
        navigator.open(Workspace.destinations().stream().filter(d -> d.workspace() == Workspace.STUDIO_2D).findFirst().orElseThrow());
        assertEquals(Workspace.STUDIO_2D, navigator.current());
        SpriteSession studio = SpriteSession.open(new File(root, offer.assetPath()));
        SpriteVectorSources sources = new SpriteVectorSources(studio, root.toPath());
        assertEquals(List.of("Wall", "Roof", "Window"), sources.links().stream().map(SpriteVectorSources.Link::layerName).toList(),
            "every layer is drawn from the house's drawing");
        VectorSession drawing = sources.vector(DRAWING);

        //    the roof colour, then the window's shape (its top edge raised), each redrawing the layers drawn from the drawing
        assertTrue(drawing.setFill("Roof", NEW_ROOF));
        assertTrue(sources.redrawFrom(DRAWING));
        assertTrue(drawing.movePoints("Window", List.of(new VectorPath.PointRef(0, 0), new VectorPath.PointRef(1, 0)), 0f, -WINDOW_RAISE));
        assertTrue(sources.redrawFrom(DRAWING));
        SpriteDocument edited = studio.getDocument();
        assertEquals(NEW_ROOF_RGBA, flatPixel(edited, 0, ROOF_PIXEL));
        assertEquals(WINDOW_RGBA, flatPixel(edited, 0, RAISED_WINDOW_PIXEL));
        //    undo and redo the window redraw in the sprite; nothing was painted over
        assertTrue(studio.getHistory().undo());
        assertNotEquals(WINDOW_RGBA, flatPixel(studio.getDocument(), 0, RAISED_WINDOW_PIXEL));
        assertTrue(studio.getHistory().redo());
        for (SpriteVectorSources.Link link : sources.links()) {
            assertFalse(sources.isPaintedOver(link.layer()), link.layerName() + " is what the drawing gives");
        }
        assertEquals(List.of(new File(root, DRAWING), new File(root, HOUSE)), sources.save(), "the drawing, then the sprite");

        //    the animated character: paint a walk frame and give it an attack hitbox, then save
        SpriteSession heroStudio = SpriteSession.open(new File(root, HERO));
        heroStudio.setFrame(HERO_FRAME);
        heroStudio.paint(List.of(new int[] {0, 0}), PAINT, "Pencil");
        assertTrue(heroStudio.setHitbox(SWING));
        heroStudio.saveAs(heroStudio.getFile());
        writeEvidence("house-after.png", studio.getDocument());

        // Back to World: the house and the hero show the edits once the files are read again (what the editor does on a change)
        navigator.backToWorld();
        assertEquals(Workspace.WORLD, navigator.current());
        assertEquals(1, SpriteReload.reloadUsers(scene, HOUSE));
        assertEquals(3, SpriteReload.reloadUsers(scene, HERO), "the sample's three heroes all use the hero sprite");
        assertWorldShowsTheEdits(root, house, hero);

        // Play (2D, no genre), then Stop: the Play copy reads the edited files, the hero's frame has its hitbox, the scene is unchanged
        String authored = editor.getSerializer().toJson(scene);
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        play.getProfileStep().setInput(key -> false);
        assertTrue(play.play(), editor.getStatusMessage());
        try {
            Scene runtime = play.getRuntimeScene();
            Entity playingHouse = runtime.findEntityById(house.getId());
            Entity playingHero = runtime.findEntityById(hero.getId());
            assertWorldShowsTheEdits(root, playingHouse, playingHero);
            SpriteAnimatorComponent walker = playingHero.getComponent(SpriteAnimatorComponent.class);
            boolean seen = false;
            for (int frame = 0; frame < 120 && !seen; frame++) {
                play.update(FRAME);
                seen = walker.getFrame() == HERO_FRAME && walker.getHitboxes().equals(List.of(SWING));
            }
            assertTrue(seen, "playing the walk shows the edited frame with its attack hitbox");
        } finally {
            play.stop();
        }
        assertEquals(authored, editor.getSerializer().toJson(editor.getActiveScene()), "Stop leaves the scene as it was");

        // Save, reopen: the edits, the layer links and the drawing survive
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = newEditor();
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        assertWorldShowsTheEdits(root, back.findEntityById(house.getId()), back.findEntityById(hero.getId()));
        assertEquals(NEW_ROOF, VectorFile.read(Files.readString(root.toPath().resolve(DRAWING), StandardCharsets.UTF_8)).shape("Roof").fill());
        SpriteVectorSources reopenedSources = new SpriteVectorSources(SpriteSession.open(new File(root, HOUSE)), root.toPath());
        assertEquals(3, reopenedSources.links().size(), "the layer links are saved");
        for (SpriteVectorSources.Link link : reopenedSources.links()) {
            assertFalse(reopenedSources.isPaintedOver(link.layer()), "the saved layers are what the saved drawing gives");
        }

        // The shipped hero and the fixture are untouched: the project has its own copies
        assertArrayEquals(shippedHero, Files.readAllBytes(ASSETS.toPath().resolve(HERO)));
        assertArrayEquals(fixtureSprite, Files.readAllBytes(FIXTURE.resolve("house.sprite")));
        assertArrayEquals(fixtureDrawing, Files.readAllBytes(FIXTURE.resolve("house.vector")));
    }

    /** The house shows the new roof and raised window, and the hero's walk frame the painted pixel and the attack hitbox. */
    private static void assertWorldShowsTheEdits(File root, Entity house, Entity hero) throws IOException {
        SpriteDocument houseSprite = loaded(root, house).getDocument();
        assertEquals(NEW_ROOF_RGBA, flatPixel(houseSprite, 0, ROOF_PIXEL), "the new roof colour");
        assertEquals(WINDOW_RGBA, flatPixel(houseSprite, 0, RAISED_WINDOW_PIXEL), "the taller window");
        SpriteAnimatorComponent heroSprite = hero.getComponent(SpriteAnimatorComponent.class);
        if (heroSprite.needsLoad()) {
            heroSprite.load(new FileHandle(new File(root, HERO)));
        }
        assertEquals(PAINT, heroSprite.getDocument().getPixel(0, HERO_FRAME, 0, 0), "the painted walk frame");
        assertEquals(List.of(SWING), heroSprite.getDocument().getHitboxes(HERO_FRAME));
    }

    /** The house's sprite as the World draws it: a dropped sprite reads its file when it is drawn. */
    private static SpriteAnimatorComponent loaded(File root, Entity house) throws IOException {
        SpriteAnimatorComponent sprite = house.getComponent(SpriteAnimatorComponent.class);
        assertNotNull(sprite, "a dropped .sprite plays in a Sprite Animator");
        if (sprite.needsLoad()) {
            sprite.load(new FileHandle(new File(root, HOUSE)));
        }
        return sprite;
    }

    private static int flatPixel(SpriteDocument doc, int frame, int[] at) {
        return doc.flatten(frame)[at[1] * doc.getWidth() + at[0]];
    }

    private static void writeEvidence(String name, SpriteDocument doc) throws IOException {
        int w = doc.getWidth() * EVIDENCE_ZOOM;
        int h = doc.getHeight() * EVIDENCE_ZOOM;
        int[] flat = doc.flatten(0);
        int[] big = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                big[y * w + x] = flat[(y / EVIDENCE_ZOOM) * doc.getWidth() + x / EVIDENCE_ZOOM];
            }
        }
        Files.createDirectories(EVIDENCE);
        Files.write(EVIDENCE.resolve(name), PixelPng.encode(w, h, big));
        assertTrue(SpriteFile.write(doc).contains("\"source\""), "the evidence sprite keeps its layer links");
    }
}
