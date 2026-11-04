package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewKind;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.Arrangement;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.Divider;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.Orientation;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.PaneInfo;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout.PaneRect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shows the panes of a {@link ViewportLayout} side by side (TD-039): one {@link SceneViewportWidget} per pane, a
 * small header on each with its view name and buttons to change the view, split, close and maximize, and strips
 * between panes that can be dragged to resize them. Layout changes made here are written back to the
 * {@link ViewportLayout} and reported to {@link #setOnLayoutChanged}, so they can be saved.
 */
public final class MultiViewportArea extends WidgetGroup implements Disposable {
    /** Creates the view of a pane when the layout gains one or a pane's view type changes. */
    public interface PaneFactory {
        ViewportPaneView create(ViewKind kind, int paneId);
    }

    /** Width of the strip between two panes, in pixels. */
    static final float DIVIDER_THICKNESS = 4f;
    /** A divider can be grabbed this far outside its strip. */
    private static final float DIVIDER_GRAB_MARGIN = 3f;
    private static final float HEADER_PAD = 4f;
    private static final Color DIVIDER_COLOR = new Color(0.12f, 0.13f, 0.15f, 1f);
    private static final Color DIVIDER_HOT_COLOR = new Color(0.35f, 0.55f, 0.95f, 1f);

    private static final class Entry {
        final ViewKind kind;
        final ViewportPaneView view;
        final Table header;

        Entry(ViewKind kind, ViewportPaneView view, Table header) {
            this.kind = kind;
            this.view = view;
            this.header = header;
        }
    }

    private final Skin skin;
    private final ViewportLayout layout;
    private final PaneFactory factory;
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private final Texture whitePixel;
    private Arrangement arrangement = new Arrangement(List.of(), List.of());
    private long builtRevision = -1L;
    private int draggedSplit = -1;
    private Runnable onLayoutChanged = () -> { };

    /** Creates the area; panes appear when it is first laid out. */
    public MultiViewportArea(Skin skin, ViewportLayout layout, PaneFactory factory) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.layout = Objects.requireNonNull(layout, "layout cannot be null");
        this.factory = Objects.requireNonNull(factory, "factory cannot be null");
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();
        setTouchable(Touchable.enabled);
        addListener(new DividerDragListener());
    }

    /** The layout this area shows and edits. */
    public ViewportLayout getLayout() {
        return layout;
    }

    /** Called after the user changed the layout (not when the code changes it), for example to save it. */
    public void setOnLayoutChanged(Runnable onLayoutChanged) {
        this.onLayoutChanged = Objects.requireNonNull(onLayoutChanged, "onLayoutChanged cannot be null");
    }

    /** Draws every pane's scene into its own frame buffer. */
    public void renderPanes(Color background, float delta) {
        syncIfChanged();
        for (Entry entry : entries.values()) {
            entry.view.render(background, delta);
        }
    }

    /** Frames a sphere in every pane. */
    public void frameAll(Vector3 center, float radius) {
        for (Entry entry : entries.values()) {
            entry.view.frame(center, radius);
        }
    }

    /** Puts every pane's camera back where it starts. */
    public void resetAll() {
        for (Entry entry : entries.values()) {
            entry.view.resetView();
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (isVisible()) {
            for (Entry entry : entries.values()) {
                entry.view.update(delta);
            }
        }
    }

    @Override
    public void layout() {
        syncIfChanged();
        arrangement = layout.arrange(getWidth(), getHeight(), DIVIDER_THICKNESS);
        for (Entry entry : entries.values()) {
            entry.view.getWidget().setVisible(false);
            entry.header.setVisible(false);
        }
        for (PaneRect rect : arrangement.panes()) {
            Entry entry = entries.get(rect.paneId());
            if (entry == null) {
                continue;
            }
            entry.view.getWidget().setVisible(true);
            entry.view.getWidget().setBounds(rect.x(), rect.y(), rect.width(), rect.height());
            entry.header.setVisible(true);
            entry.header.pack();
            // Bottom left: the toolbar floats over the top of the scene view and would hide a header there
            entry.header.setPosition(rect.x() + HEADER_PAD, rect.y() + HEADER_PAD);
        }
    }

    /** Creates, replaces or removes pane views so they match the layout. */
    private void syncIfChanged() {
        if (builtRevision == layout.getRevision()) {
            return;
        }
        builtRevision = layout.getRevision();
        Map<Integer, ViewKind> wanted = new LinkedHashMap<>();
        for (PaneInfo pane : layout.getPanes()) {
            wanted.put(pane.id(), pane.kind());
        }
        entries.entrySet().removeIf(e -> {
            boolean stale = !wanted.containsKey(e.getKey()) || wanted.get(e.getKey()) != e.getValue().kind;
            if (stale) {
                removeEntry(e.getValue());
            }
            return stale;
        });
        for (Map.Entry<Integer, ViewKind> pane : wanted.entrySet()) {
            if (!entries.containsKey(pane.getKey())) {
                ViewportPaneView view = factory.create(pane.getValue(), pane.getKey());
                Table header = buildHeader(pane.getKey(), pane.getValue());
                addActor(view.getWidget());
                addActor(header);
                entries.put(pane.getKey(), new Entry(pane.getValue(), view, header));
            }
        }
        for (Entry entry : entries.values()) {
            entry.header.toFront();
        }
        invalidate();
    }

    private void removeEntry(Entry entry) {
        entry.header.remove();
        entry.view.getWidget().remove();
        entry.view.dispose();
    }

    private Table buildHeader(int paneId, ViewKind kind) {
        Table header = new Table(skin);
        header.setBackground("elevated-bg");
        header.pad(1f);
        header.add(headerButton(kind.getDisplayName(), () -> layout.setKind(paneId, layout.getKind(paneId).next())))
            .height(20f);
        header.add(headerButton("|", () -> split(paneId, Orientation.SIDE_BY_SIDE))).height(20f).width(22f);
        header.add(headerButton("-", () -> split(paneId, Orientation.STACKED))).height(20f).width(22f);
        boolean maximized = layout.getMaximizedPaneId() == paneId;
        header.add(headerButton(maximized ? "Restore" : "Max", () -> {
            if (layout.getMaximizedPaneId() == paneId) {
                layout.restore();
            } else {
                layout.maximize(paneId);
            }
        })).height(20f);
        header.add(headerButton("x", () -> layout.close(paneId))).height(20f).width(22f);
        return header;
    }

    private void split(int paneId, Orientation orientation) {
        if (layout.canSplit()) {
            layout.split(paneId, orientation, layout.getKind(paneId).next());
        }
    }

    /** A header button; the change is applied after the click, because it rebuilds the button that was clicked. */
    private TextButton headerButton(String text, Runnable change) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.postRunnable(() -> {
                    change.run();
                    // The maximize button's label depends on the layout, so headers are rebuilt with the panes
                    syncIfChanged();
                    rebuildHeaders();
                    onLayoutChanged.run();
                    invalidate();
                });
            }
        });
        return button;
    }

    private void rebuildHeaders() {
        for (Map.Entry<Integer, Entry> pane : entries.entrySet()) {
            Entry old = pane.getValue();
            old.header.remove();
            Table header = buildHeader(pane.getKey(), old.kind);
            addActor(header);
            pane.setValue(new Entry(old.kind, old.view, header));
        }
        for (Entry entry : entries.values()) {
            entry.header.toFront();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        for (Divider divider : arrangement.dividers()) {
            batch.setColor(divider.splitId() == draggedSplit ? DIVIDER_HOT_COLOR : DIVIDER_COLOR);
            batch.draw(whitePixel, getX() + divider.x(), getY() + divider.y(), divider.width(), divider.height());
        }
        batch.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        for (Entry entry : entries.values()) {
            entry.view.dispose();
        }
        entries.clear();
        whitePixel.dispose();
    }

    private Divider dividerAt(float x, float y) {
        for (Divider divider : arrangement.dividers()) {
            if (x >= divider.x() - DIVIDER_GRAB_MARGIN && x <= divider.x() + divider.width() + DIVIDER_GRAB_MARGIN
                && y >= divider.y() - DIVIDER_GRAB_MARGIN && y <= divider.y() + divider.height() + DIVIDER_GRAB_MARGIN) {
                return divider;
            }
        }
        return null;
    }

    /** Drags the strip between panes, which the panes themselves do not cover. */
    private final class DividerDragListener extends InputListener {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            Divider divider = pointer == 0 ? dividerAt(x, y) : null;
            if (divider == null) {
                return false;
            }
            draggedSplit = divider.splitId();
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (draggedSplit < 0) {
                return;
            }
            for (Divider divider : arrangement.dividers()) {
                if (divider.splitId() == draggedSplit) {
                    layout.setRatio(draggedSplit, divider.ratioAt(x, y));
                    invalidate();
                    return;
                }
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (draggedSplit >= 0) {
                draggedSplit = -1;
                onLayoutChanged.run();
            }
        }
    }
}
