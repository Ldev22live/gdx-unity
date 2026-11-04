package io.github.ldev22live.gdxunity.editor.core.viewport;

import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.Arrangement;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.Divider;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.Orientation;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.PaneInfo;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.PaneRect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewportLayoutTest {
    private static final float GAP = 4f;

    private static PaneRect rectOf(Arrangement arrangement, ViewKind kind) {
        return arrangement.panes().stream().filter(p -> p.kind() == kind).findFirst().orElseThrow();
    }

    @Test
    void shouldStartAsFourPanesInReadingOrder() {
        ViewportLayout layout = ViewportLayout.quad();

        List<ViewKind> kinds = layout.getPanes().stream().map(PaneInfo::kind).toList();

        assertEquals(List.of(ViewKind.PERSPECTIVE, ViewKind.X_AXIS, ViewKind.Y_AXIS, ViewKind.Z_AXIS), kinds);
        assertEquals(4, layout.getPaneCount());
    }

    @Test
    void shouldPlaceTheQuadPanesInThe2x2GridWithPerspectiveTopLeft() {
        Arrangement arrangement = ViewportLayout.quad().arrange(1004f, 804f, GAP);

        PaneRect perspective = rectOf(arrangement, ViewKind.PERSPECTIVE);
        PaneRect x = rectOf(arrangement, ViewKind.X_AXIS);
        PaneRect y = rectOf(arrangement, ViewKind.Y_AXIS);
        PaneRect z = rectOf(arrangement, ViewKind.Z_AXIS);

        assertEquals(500f, perspective.width(), 1e-3f);
        assertEquals(400f, perspective.height(), 1e-3f);
        assertTrue(perspective.x() < x.x(), "X axis is to the right of the perspective view");
        assertEquals(perspective.y(), x.y(), 1e-3f, "and on the same row (the top)");
        assertTrue(perspective.y() > y.y(), "Y axis is below the perspective view");
        assertEquals(perspective.x(), y.x(), 1e-3f);
        assertEquals(x.x(), z.x(), 1e-3f, "Z axis is below the X axis view");
        assertEquals(y.y(), z.y(), 1e-3f);
        assertEquals(3, arrangement.dividers().size());
    }

    @Test
    void shouldFillTheAreaExactlyApartFromTheDividerStrips() {
        Arrangement arrangement = ViewportLayout.quad().arrange(1004f, 804f, GAP);

        float paneArea = 0f;
        for (PaneRect pane : arrangement.panes()) {
            paneArea += pane.width() * pane.height();
        }
        float dividerArea = 0f;
        for (Divider divider : arrangement.dividers()) {
            dividerArea += divider.width() * divider.height();
        }
        // Together the panes and the strips between them tile the area, with no gap and no overlap
        assertEquals(1004f * 804f, paneArea + dividerArea, 1f, "panes and strips cover the area");
        for (PaneRect pane : arrangement.panes()) {
            assertTrue(pane.x() >= -1e-3f && pane.y() >= -1e-3f);
            assertTrue(pane.x() + pane.width() <= 1004f + 1e-3f && pane.y() + pane.height() <= 804f + 1e-3f);
        }
    }

    @Test
    void shouldNotOverlapAnyTwoPanes() {
        ViewportLayout layout = ViewportLayout.quad();
        layout.split(layout.getPanes().get(0).id(), Orientation.STACKED, ViewKind.Z_AXIS);
        List<PaneRect> panes = layout.arrange(900f, 700f, GAP).panes();

        for (int i = 0; i < panes.size(); i++) {
            for (int j = i + 1; j < panes.size(); j++) {
                PaneRect a = panes.get(i);
                PaneRect b = panes.get(j);
                boolean overlap = a.x() < b.x() + b.width() - 1e-3f && b.x() < a.x() + a.width() - 1e-3f
                    && a.y() < b.y() + b.height() - 1e-3f && b.y() < a.y() + a.height() - 1e-3f;
                assertFalse(overlap, "panes " + a.paneId() + " and " + b.paneId() + " overlap");
            }
        }
    }

    @Test
    void shouldSplitAPaneKeepingItsIdAndViewAndAddingASecondHalf() {
        ViewportLayout layout = ViewportLayout.single(ViewKind.PERSPECTIVE);

        int created = layout.split(0, Orientation.SIDE_BY_SIDE, ViewKind.Z_AXIS);

        assertEquals(List.of(new PaneInfo(0, ViewKind.PERSPECTIVE), new PaneInfo(created, ViewKind.Z_AXIS)), layout.getPanes());
        Arrangement arrangement = layout.arrange(804f, 400f, GAP);
        assertEquals(400f, rectOf(arrangement, ViewKind.PERSPECTIVE).width(), 1e-3f);
        assertEquals(400f, rectOf(arrangement, ViewKind.Z_AXIS).width(), 1e-3f);
    }

    @Test
    void shouldPutStackedPanesTopAndBottomWithYUp() {
        Arrangement arrangement = ViewportLayout.stacked(ViewKind.X_AXIS, ViewKind.Y_AXIS).arrange(400f, 604f, GAP);

        PaneRect top = rectOf(arrangement, ViewKind.X_AXIS);
        PaneRect bottom = rectOf(arrangement, ViewKind.Y_AXIS);

        assertTrue(top.y() > bottom.y(), "the first pane is the upper one, with a larger y");
        assertEquals(300f, top.height(), 1e-3f);
        assertEquals(0f, bottom.y(), 1e-3f);
    }

    @Test
    void shouldCloseAPaneAndGiveItsRoomToTheNeighbour() {
        ViewportLayout layout = ViewportLayout.quad();
        int xAxis = layout.getPanes().get(1).id();

        assertTrue(layout.close(xAxis));

        assertEquals(3, layout.getPaneCount());
        PaneRect perspective = rectOf(layout.arrange(1004f, 804f, GAP), ViewKind.PERSPECTIVE);
        assertEquals(1004f, perspective.width(), 1e-3f, "the perspective view now spans the whole top row");
        assertThrows(IllegalArgumentException.class, () -> layout.getKind(xAxis), "the closed pane is gone");
    }

    @Test
    void shouldNeverCloseTheLastPane() {
        ViewportLayout layout = ViewportLayout.single(ViewKind.PERSPECTIVE);

        assertFalse(layout.close(0));

        assertEquals(1, layout.getPaneCount());
    }

    @Test
    void shouldRefuseMoreThanTheMaximumNumberOfPanes() {
        ViewportLayout layout = ViewportLayout.single(ViewKind.PERSPECTIVE);
        for (int i = 1; i < ViewportLayout.MAX_PANES; i++) {
            layout.split(0, Orientation.SIDE_BY_SIDE, ViewKind.X_AXIS);
        }

        assertFalse(layout.canSplit());
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> layout.split(0, Orientation.STACKED, ViewKind.Y_AXIS));
        assertTrue(error.getMessage().contains("8"), error.getMessage());
    }

    @Test
    void shouldChangeWhatAPaneShows() {
        ViewportLayout layout = ViewportLayout.quad();
        int pane = layout.getPanes().get(1).id();
        long before = layout.getRevision();

        layout.setKind(pane, ViewKind.PERSPECTIVE);

        assertEquals(ViewKind.PERSPECTIVE, layout.getKind(pane));
        assertTrue(layout.getRevision() > before);
        long after = layout.getRevision();
        layout.setKind(pane, ViewKind.PERSPECTIVE);
        assertEquals(after, layout.getRevision(), "setting the same view changes nothing");
    }

    @Test
    void shouldMoveADividerAndKeepItInRange() {
        ViewportLayout layout = ViewportLayout.sideBySide(ViewKind.PERSPECTIVE, ViewKind.X_AXIS);
        Divider divider = layout.arrange(1004f, 500f, GAP).dividers().get(0);

        layout.setRatio(divider.splitId(), 0.25f);
        assertEquals(250f, rectOf(layout.arrange(1004f, 500f, GAP), ViewKind.PERSPECTIVE).width(), 1e-2f);
        layout.setRatio(divider.splitId(), 5f);
        assertEquals(ViewportLayout.MAX_RATIO, layout.getRatio(divider.splitId()), 1e-6f);
        layout.setRatio(divider.splitId(), -3f);
        assertEquals(ViewportLayout.MIN_RATIO, layout.getRatio(divider.splitId()), 1e-6f);
        assertThrows(IllegalArgumentException.class, () -> layout.setRatio(divider.splitId(), Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> layout.setRatio(99, 0.5f));
    }

    @Test
    void shouldTurnAPointerPositionIntoARatio() {
        ViewportLayout layout = ViewportLayout.sideBySide(ViewKind.PERSPECTIVE, ViewKind.X_AXIS);
        Divider divider = layout.arrange(1004f, 500f, GAP).dividers().get(0);

        assertEquals(0.25f, divider.ratioAt(252f, 100f), 1e-3f, "pointer a quarter of the way across");
        assertEquals(ViewportLayout.MIN_RATIO, divider.ratioAt(-50f, 0f), 1e-6f);
        assertEquals(ViewportLayout.MAX_RATIO, divider.ratioAt(5000f, 0f), 1e-6f);

        ViewportLayout stacked = ViewportLayout.stacked(ViewKind.PERSPECTIVE, ViewKind.X_AXIS);
        Divider horizontal = stacked.arrange(500f, 1004f, GAP).dividers().get(0);
        assertEquals(0.25f, horizontal.ratioAt(10f, 1004f - 252f), 1e-3f, "a quarter of the way down from the top");
    }

    @Test
    void shouldNotSqueezeAPaneBelowTheMinimumWhileThereIsRoom() {
        ViewportLayout layout = ViewportLayout.sideBySide(ViewKind.PERSPECTIVE, ViewKind.X_AXIS);
        layout.setRatio(layout.arrange(500f, 500f, GAP).dividers().get(0).splitId(), ViewportLayout.MIN_RATIO);

        Arrangement arrangement = layout.arrange(500f, 500f, GAP);

        assertTrue(rectOf(arrangement, ViewKind.PERSPECTIVE).width() >= ViewportLayout.MIN_PANE_SIZE - 1e-3f);
    }

    @Test
    void shouldGiveAMaximizedPaneTheWholeAreaWithoutDividers() {
        ViewportLayout layout = ViewportLayout.quad();
        int z = layout.getPanes().get(3).id();

        layout.maximize(z);
        Arrangement arrangement = layout.arrange(800f, 600f, GAP);

        assertEquals(1, arrangement.panes().size());
        assertEquals(new PaneRect(z, ViewKind.Z_AXIS, 0f, 0f, 800f, 600f), arrangement.panes().get(0));
        assertTrue(arrangement.dividers().isEmpty());
        assertEquals(4, layout.getPaneCount(), "the other panes still exist");
        layout.restore();
        assertEquals(4, layout.arrange(800f, 600f, GAP).panes().size());
        assertEquals(-1, layout.getMaximizedPaneId());
    }

    @Test
    void shouldForgetAMaximizedPaneWhenItIsClosedOrAnotherIsSplit() {
        ViewportLayout layout = ViewportLayout.quad();
        int x = layout.getPanes().get(1).id();
        layout.maximize(x);
        layout.close(x);
        assertEquals(-1, layout.getMaximizedPaneId());

        int other = layout.getPanes().get(0).id();
        layout.maximize(other);
        layout.split(other, Orientation.SIDE_BY_SIDE, ViewKind.X_AXIS);
        assertEquals(-1, layout.getMaximizedPaneId(), "splitting shows the whole layout again");
    }

    @Test
    void shouldRoundTripThroughJson() {
        ViewportLayout layout = ViewportLayout.quad();
        layout.setRatio(layout.arrange(1000f, 800f, GAP).dividers().get(0).splitId(), 0.3f);
        layout.setKind(layout.getPanes().get(2).id(), ViewKind.PERSPECTIVE);

        ViewportLayout restored = ViewportLayout.fromJson(layout.toJson());

        assertEquals(layout.getPanes().stream().map(PaneInfo::kind).toList(),
            restored.getPanes().stream().map(PaneInfo::kind).toList());
        List<PaneRect> a = layout.arrange(1000f, 800f, GAP).panes();
        List<PaneRect> b = restored.arrange(1000f, 800f, GAP).panes();
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).width(), b.get(i).width(), 0.1f);
            assertEquals(a.get(i).height(), b.get(i).height(), 0.1f);
            assertEquals(a.get(i).x(), b.get(i).x(), 0.1f);
            assertEquals(a.get(i).y(), b.get(i).y(), 0.1f);
        }
    }

    @Test
    void shouldRejectBrokenLayoutFilesWithAReason() {
        for (String bad : new String[] {
            "not json at all", "{}", "{\"version\":2,\"root\":{}}", "{\"version\":1}",
            "{\"version\":1,\"root\":{\"type\":\"pane\",\"view\":\"SIDEWAYS\"}}",
            "{\"version\":1,\"root\":{\"type\":\"blob\"}}",
            "{\"version\":1,\"root\":{\"type\":\"split\",\"orientation\":\"STACKED\",\"first\":{\"type\":\"pane\",\"view\":\"PERSPECTIVE\"}}}",
            "{\"version\":1,\"root\":{\"type\":\"split\",\"orientation\":\"DIAGONAL\",\"ratio\":0.5}}",
        }) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ViewportLayout.fromJson(bad), bad);
            assertTrue(error.getMessage().contains("viewport layout"), error.getMessage());
        }
    }

    @Test
    void shouldRejectALayoutWithTooManyPanes() {
        StringBuilder json = new StringBuilder("{\"version\":1,\"root\":");
        String pane = "{\"type\":\"pane\",\"view\":\"PERSPECTIVE\"}";
        // A chain of nine panes
        String node = pane;
        for (int i = 0; i < ViewportLayout.MAX_PANES; i++) {
            node = "{\"type\":\"split\",\"orientation\":\"SIDE_BY_SIDE\",\"ratio\":0.5,\"first\":" + pane + ",\"second\":" + node + "}";
        }
        json.append(node).append('}');

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ViewportLayout.fromJson(json.toString()));

        assertTrue(error.getMessage().contains("more than 8 panes"), error.getMessage());
    }

    @Test
    void shouldClampARatioReadFromAFile() {
        String json = "{\"version\":1,\"root\":{\"type\":\"split\",\"orientation\":\"STACKED\",\"ratio\":7.5,"
            + "\"first\":{\"type\":\"pane\",\"view\":\"PERSPECTIVE\"},\"second\":{\"type\":\"pane\",\"view\":\"Z_AXIS\"}}}";

        ViewportLayout layout = ViewportLayout.fromJson(json);

        assertEquals(ViewportLayout.MAX_RATIO, layout.getRatio(layout.arrange(400f, 400f, GAP).dividers().get(0).splitId()), 1e-6f);
    }

    @Test
    void shouldGiveViewKindsTheirDirections() {
        assertFalse(ViewKind.PERSPECTIVE.isOrthographic());
        assertThrows(IllegalStateException.class, ViewKind.PERSPECTIVE::getDirection);
        assertEquals(-1f, ViewKind.X_AXIS.getDirection().x);
        assertEquals(-1f, ViewKind.Y_AXIS.getDirection().y);
        assertEquals(-1f, ViewKind.Z_AXIS.getDirection().z);
        for (ViewKind kind : new ViewKind[] {ViewKind.X_AXIS, ViewKind.Y_AXIS, ViewKind.Z_AXIS}) {
            assertEquals(0f, kind.getDirection().dot(kind.getUp()), 1e-6f, kind + ": up is perpendicular to the view direction");
            assertEquals(1f, kind.getUp().len(), 1e-6f);
        }
        assertEquals(ViewKind.X_AXIS, ViewKind.PERSPECTIVE.next());
        assertEquals(ViewKind.PERSPECTIVE, ViewKind.Z_AXIS.next());
    }
}
