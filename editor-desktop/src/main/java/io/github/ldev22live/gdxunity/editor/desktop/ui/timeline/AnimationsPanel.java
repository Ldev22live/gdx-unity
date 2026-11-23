package io.github.ldev22live.gdxunity.editor.desktop.ui.timeline;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.animation.ClipLibrary;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The Animations tab (TD-044, TD-053): the clip library on the left, switchable to the character creator, (the humanoid Idle, Walk, Run, Jump and Fall, then every other
 * clip), the Timeline on the right. A clip's name button previews it on the humanoid in the scene; its Use button makes
 * the selected entity play it, with one undo step.
 */
public class AnimationsPanel extends Table implements Disposable {
    private static final float LIBRARY_WIDTH = 210f;
    /** Wider while the character creator is shown: it has rows of labels and number fields. */
    private static final float CREATOR_WIDTH = 340f;
    private static final float ROW_HEIGHT = 24f;
    private static final float USE_WIDTH = 44f;

    private final Skin skin;
    private final File assetRoot;
    private final Consumer<AssetReference> onPreview;
    private final Consumer<AssetReference> onUse;
    private final Table library;
    private final TimelinePanel timeline;
    private final Table creator;
    private final Table sideContent = new Table();
    private Cell<Table> sideCell;
    private Table clipsView;
    private boolean creatorShown;

    /**
     * @param onPreview opens the clip in the Timeline on the humanoid in the scene
     * @param onUse     makes the selected entity play the clip
     * @param creator   the character creator (TD-053), shown beside the clip library under a Character button, or null for none
     */
    public AnimationsPanel(Skin skin, File assetRoot, TimelinePanel timeline, Consumer<AssetReference> onPreview,
                           Consumer<AssetReference> onUse, Table creator) {
        super(skin);
        this.creator = creator;
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        this.timeline = Objects.requireNonNull(timeline, "timeline cannot be null");
        this.onPreview = Objects.requireNonNull(onPreview, "onPreview cannot be null");
        this.onUse = Objects.requireNonNull(onUse, "onUse cannot be null");
        this.library = new Table(skin);
        library.top().left();

        Table side = new Table(skin);
        side.setBackground("panel-bg");
        side.top().left();
        Table modes = new Table(skin);
        TextButton clips = new TextButton("Clip Library", skin, "flat");
        clips.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCreator(false);
            }
        });
        modes.add(clips).height(ROW_HEIGHT).padRight(2);
        if (creator != null) {
            TextButton character = new TextButton("Character", skin, "flat");
            character.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    showCreator(true);
                }
            });
            modes.add(character).height(ROW_HEIGHT);
        }
        side.add(modes).left().padTop(6).padBottom(4).row();
        clipsView = new Table(skin);
        ScrollPane scroll = new ScrollPane(library, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        clipsView.add(scroll).grow().row();
        TextButton refresh = new TextButton("Refresh", skin, "flat");
        refresh.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                refreshLibrary();
            }
        });
        clipsView.add(refresh).left().height(ROW_HEIGHT).pad(4);
        sideContent.add(clipsView).grow();
        side.add(sideContent).grow().row();

        sideCell = add(side).width(LIBRARY_WIDTH).growY();
        add(timeline).grow();
        refreshLibrary();
    }

    /** Switches the left side between the clip library and the character creator; ignored without a creator. */
    public final void showCreator(boolean show) {
        if (creator == null || show == creatorShown) {
            return;
        }
        creatorShown = show;
        sideContent.clearChildren();
        sideContent.add(show ? creator : clipsView).grow();
        sideCell.width(show ? CREATOR_WIDTH : LIBRARY_WIDTH);
        invalidateHierarchy();
    }

    public boolean isCreatorShown() {
        return creatorShown;
    }

    /** Lists the clips again, after a clip was added to the project. */
    public final void refreshLibrary() {
        library.clearChildren();
        List<ClipLibrary.Entry> entries = ClipLibrary.list(assetRoot);
        if (entries.isEmpty()) {
            library.add(new Label("No clips in animations/", skin, "muted")).left().pad(6).row();
            return;
        }
        boolean humanoidHeader = false;
        boolean otherHeader = false;
        for (ClipLibrary.Entry entry : entries) {
            if (entry.humanoid() && !humanoidHeader) {
                library.add(new Label(" Humanoid", skin, "muted")).left().colspan(2).padTop(2).row();
                humanoidHeader = true;
            } else if (!entry.humanoid() && !otherHeader) {
                library.add(new Label(" Other clips", skin, "muted")).left().colspan(2).padTop(8).row();
                otherHeader = true;
            }
            TextButton preview = new TextButton(entry.name(), skin, "flat");
            preview.getLabel().setAlignment(Align.left);
            preview.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    onPreview.accept(entry.asset());
                }
            });
            TextButton use = new TextButton("Use", skin, "flat");
            use.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    onUse.accept(entry.asset());
                }
            });
            library.add(preview).growX().height(ROW_HEIGHT).padLeft(4);
            library.add(use).width(USE_WIDTH).height(ROW_HEIGHT).padRight(4).row();
        }
    }

    public TimelinePanel getTimeline() {
        return timeline;
    }

    @Override
    public void dispose() {
        timeline.dispose();
        if (creator instanceof Disposable disposable) {
            disposable.dispose();
        }
    }
}
