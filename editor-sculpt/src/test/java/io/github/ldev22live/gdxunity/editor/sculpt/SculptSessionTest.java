package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.IntArray;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.DynamicMesh;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMesh;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptSessionTest {
    @TempDir
    Path root;

    private SculptAssets assets;
    private SculptSession session;
    private AssetReference rock;

    @BeforeEach
    void setUp() throws IOException {
        assets = new SculptAssets(root.toFile());
        rock = assets.create("Rock", 10f, 4);
        session = new SculptSession(assets);
    }

    @Test
    void shouldLoadASculptOnFirstUseAndKeepItOpen() {
        assertNull(session.getDocument(rock.getPath()), "nothing is open yet");

        DynamicMesh drawable = session.find(rock.getPath());

        assertNotNull(drawable);
        assertEquals(1, session.getOpenDocuments().size());
        assertSame(drawable, session.find(rock.getPath()), "every frame gets the same live mesh");
        assertSame(session.getDocument(rock.getPath()).getDrawable(), drawable);
        assertFalse(session.getDocument(rock.getPath()).isModified());
    }

    @Test
    void shouldAnswerNullAndRememberTheErrorForAMissingFileInsteadOfRetryingEveryFrame() throws IOException {
        assertNull(session.find("sculpts/none.sculpt"));
        assertTrue(session.getLoadError("sculpts/none.sculpt").contains("Sculpt not found"));

        // The file appears later, but the session does not look again until told to
        SculptMesh mesh = assets.load(rock);
        assets.save(new AssetReference("sculpts/none.sculpt"), mesh);
        assertNull(session.find("sculpts/none.sculpt"), "the failure is remembered");

        session.reload(new AssetReference("sculpts/none.sculpt"));
        assertNotNull(session.find("sculpts/none.sculpt"));
        assertNull(session.getLoadError("sculpts/none.sculpt"));
    }

    @Test
    void shouldAnswerNullForABlankOrEscapingPathWithoutThrowing() {
        assertNull(session.find("   "));
        assertNull(session.find("../outside.sculpt"));
    }

    @Test
    void shouldRememberACorruptFileAsAnError() throws IOException {
        Files.writeString(root.resolve("sculpts/bad.sculpt"), "garbage garbage garbage");

        assertNull(session.find("sculpts/bad.sculpt"));

        assertTrue(session.getLoadError("sculpts/bad.sculpt").contains("Not a sculpt file"));
        assertThrows(IOException.class, () -> session.open(new AssetReference("sculpts/bad.sculpt")));
    }

    @Test
    void shouldFindTheDocumentOfAnEntityWithAnEnabledMeshComponent() throws IOException {
        session.open(rock);
        Scene scene = new Scene("Level");
        Entity withMesh = scene.createEntity("Rock");
        DynamicMeshComponent component = withMesh.addComponent(new DynamicMeshComponent(rock.getPath()));
        Entity plain = scene.createEntity("Plain");

        assertSame(session.getDocument(rock.getPath()), session.getDocumentOf(withMesh));
        assertNull(session.getDocumentOf(plain));
        assertNull(session.getDocumentOf(null));
        component.setEnabled(false);
        assertNull(session.getDocumentOf(withMesh), "a disabled component does not count");
    }

    @Test
    void shouldTrackModificationAndSaveItToTheFile() throws IOException {
        SculptDocument document = session.open(rock);
        document.getSurface().getMesh().getPositions()[0] += 1f;
        document.getSurface().commitMoved(intArray(0));
        float moved = document.getSurface().getMesh().getPositions()[0];

        assertTrue(document.isModified());
        assertTrue(session.hasModifiedDocuments());
        session.save(rock);

        assertFalse(document.isModified());
        assertFalse(session.hasModifiedDocuments());
        assertEquals(moved, assets.load(rock).getPositions()[0], 0f);
    }

    private static IntArray intArray(int... values) {
        IntArray array = new IntArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    @Test
    void shouldSaveOnlyTheModifiedSculptsAndReportAFailureAfterTryingTheOthers() throws IOException {
        AssetReference other = assets.create("Other", 10f, 4);
        SculptDocument first = session.open(rock);
        SculptDocument second = session.open(other);
        for (SculptDocument document : new SculptDocument[] {first, second}) {
            document.getSurface().getMesh().getPositions()[0] += 1f;
            document.getSurface().commitMoved(intArray(0));
        }
        // Make the first file impossible to replace: a folder where the file should be
        Files.delete(root.resolve("sculpts/Rock.sculpt"));
        Files.createDirectories(root.resolve("sculpts/Rock.sculpt/inner"));

        assertThrows(IOException.class, session::saveAllModified);

        assertFalse(second.isModified(), "the other sculpt was still saved");
        assertTrue(first.isModified(), "the failed one is still marked modified");
        assertEquals(0, new SculptSession(assets).saveAllModified(), "a fresh session has nothing modified");
    }

    @Test
    void shouldCloseAndReopenFromTheFileDiscardingUnsavedChanges() throws IOException {
        SculptDocument document = session.open(rock);
        document.getSurface().getMesh().getPositions()[0] += 5f;

        session.close(rock);
        SculptDocument reopened = session.open(rock);

        assertNotSame(document, reopened);
        assertFalse(reopened.isModified());
        assertThrows(IllegalArgumentException.class, () -> session.save(new AssetReference("sculpts/none.sculpt")));
    }

    @Test
    void shouldHoldTheBrushAndModeSettings() {
        assertFalse(session.isSculptMode());
        assertEquals(BrushType.DRAW, session.getBrushType());

        session.setSculptMode(true);
        session.setBrushType(BrushType.GRAB);
        session.getSymmetry().setMirrorX(true);

        assertTrue(session.isSculptMode());
        assertEquals(BrushType.GRAB, session.getBrushType());
        assertTrue(session.getSymmetry().isMirrorX());
        assertThrows(NullPointerException.class, () -> session.setBrushType(null));
    }
}
