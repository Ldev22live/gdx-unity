package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The farthest-first order of flat renderables in the depth-composite pass (roadmap MC.4). */
class ViewDepthOrderTest {
    /** A side camera like Platformer + 2.5D's: in front of the play plane, looking along −Z. */
    private static final Vector3 SIDE_EYE = new Vector3(0f, 200f, 900f);
    private static final Vector3 ALONG_MINUS_Z = new Vector3(0f, 0f, -1f);

    private final Scene scene = new Scene("Hybrid");

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    /** A named flat renderable on its own entity. */
    private static final class Flat extends BaseComponent implements Renderable {
        private final String name;

        private Flat(String name) {
            this.name = name;
        }

        @Override
        public boolean isSprite() {
            return true;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private Flat flat(String name, float x, float y, float z) {
        Flat flat = new Flat(name);
        scene.createEntity(name).addComponent(flat);
        flat.getEntity().getTransform().setLocalPosition(x, y, z);
        return flat;
    }

    private static List<String> names(List<Renderable> renderables) {
        return renderables.stream().map(Object::toString).toList();
    }

    @Test
    void shouldDrawTheFarthestFlatFirstWhateverTheSceneOrder() {
        // Scene order: the foreground first, the background last, as an author may well add them
        List<Renderable> flats = new ArrayList<>(List.of(
            flat("Foreground", 0f, 0f, 150f), flat("Player", 0f, 0f, 0f), flat("Background", 0f, 0f, -200f)));

        new ViewDepthOrder().sortBackToFront(flats, SIDE_EYE, ALONG_MINUS_Z);

        assertEquals(List.of("Background", "Player", "Foreground"), names(flats));
    }

    @Test
    void shouldKeepTheSceneOrderOfFlatsAtTheSameDepth() {
        List<Renderable> flats = new ArrayList<>(List.of(
            flat("Tiles", 0f, 0f, 0f), flat("Coin", 300f, 100f, 0f), flat("Hero", -50f, 40f, 0f), flat("Back", 0f, 0f, -10f)));

        new ViewDepthOrder().sortBackToFront(flats, SIDE_EYE, ALONG_MINUS_Z);

        assertEquals(List.of("Back", "Tiles", "Coin", "Hero"), names(flats), "a side view orders by depth only; ties keep layering");
    }

    @Test
    void shouldMeasureDepthAlongAnAngledViewNotAlongZ() {
        // An isometric-style camera above and to the side: the near flat has the smaller Z, so ordering by Z alone would draw it first
        Vector3 eye = new Vector3(1000f, 1000f, 1000f);
        Vector3 down = new Vector3(-1f, -1f, -1f);
        List<Renderable> flats = new ArrayList<>(List.of(flat("Near", 600f, 600f, 500f), flat("Far", -400f, -400f, 600f)));

        new ViewDepthOrder().sortBackToFront(flats, eye, down);

        assertEquals(List.of("Far", "Near"), names(flats));
    }

    @Test
    void shouldCountARenderableWithoutAnEntityAsTheWorldOrigin() {
        Flat loose = new Flat("Loose");
        List<Renderable> flats = new ArrayList<>(List.of(flat("Behind origin", 0f, 0f, -50f), loose, flat("Front", 0f, 0f, 50f)));

        new ViewDepthOrder().sortBackToFront(flats, SIDE_EYE, ALONG_MINUS_Z);

        assertEquals(List.of("Behind origin", "Loose", "Front"), names(flats));
    }

    @Test
    void shouldRefuseAZeroDirection() {
        List<Renderable> flats = new ArrayList<>(List.of(flat("A", 0f, 0f, 0f), flat("B", 0f, 0f, 1f)));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> new ViewDepthOrder().sortBackToFront(flats, SIDE_EYE, new Vector3()));
        assertEquals("direction cannot be zero: (0.0,0.0,0.0)", refused.getMessage());
    }
}
