package io.github.ldev22live.gdxunity.editor.desktop;

import io.github.ldev22live.gdxunity.editor.desktop.ui.ModelThumbnails;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The part of the Content Browser's model thumbnails that needs no graphics card: what is asked for, what is skipped and what is
 * not asked for twice. Drawing the pictures ({@code renderPending}) needs a GL context and is checked in the running editor.
 */
class ModelThumbnailsTest {
    private static AssetMetadata model(String path, long size) {
        return new AssetMetadata(new AssetReference(path), AssetType.MODEL, size, 1_000L);
    }

    private final ModelThumbnails thumbnails = new ModelThumbnails(new AssetCatalog(new File("../assets")));

    @Test
    void shouldAskForAModelOnceHoweverOftenTheListIsDrawn() {
        AssetMetadata hero = model("models/hero.glb", 4_096L);

        assertNull(thumbnails.get(hero), "no picture before it has been drawn");
        assertNull(thumbnails.get(hero));
        thumbnails.get(model("models/villain.glb", 2_048L));

        assertEquals(2, thumbnails.getPendingCount());
    }

    @Test
    void shouldNotLoadAModelLargerThanTheLimitJustForAPicture() {
        AssetMetadata huge = model("models/city.glb", ModelThumbnails.MAX_FILE_BYTES + 1);

        assertNull(thumbnails.get(huge));

        assertEquals(0, thumbnails.getPendingCount());
        assertTrue(thumbnails.getFailure("models/city.glb").contains("larger than 24 MB"), thumbnails.getFailure("models/city.glb"));
    }

    @Test
    void shouldHaveNoFailureForAModelThatIsStillWaiting() {
        thumbnails.get(model("models/hero.glb", 4_096L));

        assertNull(thumbnails.getFailure("models/hero.glb"));
        assertFalse(thumbnails.renderPending(0), "nothing is drawn for a limit of zero");
        assertEquals(1, thumbnails.getPendingCount());
    }

    @Test
    void shouldDoNothingAfterItIsDisposed() {
        thumbnails.get(model("models/hero.glb", 4_096L));

        thumbnails.dispose();

        assertEquals(0, thumbnails.getPendingCount());
        assertFalse(thumbnails.renderPending(5));
    }
}
