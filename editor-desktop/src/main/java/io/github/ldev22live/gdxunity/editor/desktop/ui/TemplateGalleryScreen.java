package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Scaling;
import io.github.ldev22live.gdxunity.editor.core.project.StarterProject;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.desktop.audit.TemplateRatings;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Project > New Game... (roadmap M1.4): the template gallery. A list of the empty 2D, 2.5D and 3D starts and the sample templates,
 * which can be narrowed by dimension; the chosen one shows its preview, genre, dimension, size and platform status, with
 * <b>Play Sample</b> (open the sample as a copy, as Open Sample does) and <b>Use as Template</b> (make a separate project). A made
 * project is opened by restarting the editor on it, which the person starts with a separate button so nothing closes by surprise.
 *
 * <p>Owns the preview texture it shows; {@link #dispose()} frees it.
 */
public final class TemplateGalleryScreen implements Disposable {
    /** What the gallery's buttons do; the editor supplies them. */
    public interface Actions {
        /** Opens the sample scene (relative to the project) as Open Sample does. */
        void playSample(String scene);

        /** Makes the project; the gallery shows the result or the error. */
        TemplateProject.Result create(TemplateGallery.Choice choice, String name) throws IOException;

        /** Restarts the editor on a made project. */
        void openProject(TemplateProject.Result project);

        /**
         * The template's label from the editability matrix (roadmap M1.7), read now from its scene file; only sample templates are
         * rated.
         */
        TemplateRatings.Rating rating(TemplateGallery.Choice choice) throws IOException;
    }

    private static final float WIDTH = 900f;
    private static final float LIST_WIDTH = 250f;
    private static final float BODY_HEIGHT = 520f;
    /** Previews are 16 by 9, as written by {@code tools/make_template_previews.py}. */
    private static final float PREVIEW_WIDTH = 400f;
    private static final float PREVIEW_HEIGHT = 225f;
    private static final float BUTTON_HEIGHT = 26f;
    private static final float TOP_FRACTION = 0.92f;
    /** Room for the rating's reasons before they scroll, so the name field and buttons stay inside the gallery. */
    private static final float REASONS_HEIGHT = 80f;

    private final Skin skin;
    private final File projectRoot;
    private final ContextMenu popup;
    private Texture preview;
    private GameMode filter;
    private TemplateGallery.Choice selected;
    private List<TemplateGallery.Choice> choices = List.of();
    private Table list;
    /** The All, 2D, 2.5D and 3D buttons, keyed by the mode they show (null: all). */
    private final Map<GameMode, TextButton> filterButtons = new HashMap<>();
    private Table details;
    private Actions actions;

    public TemplateGalleryScreen(Skin skin, File projectRoot) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        this.popup = new ContextMenu(skin);
    }

    /**
     * Shows the gallery near the top of {@code stage}, reading the catalog again so a changed catalog shows.
     *
     * @throws IOException if the catalog cannot be read; nothing is shown
     */
    public void show(Stage stage, Actions actions) throws IOException {
        show(stage, actions, null);
    }

    /**
     * Like {@link #show(Stage, Actions)}, with the choice of this title selected (the first when null).
     *
     * @throws IllegalArgumentException if no choice has that title
     */
    public void show(Stage stage, Actions actions, String selectTitle) throws IOException {
        Objects.requireNonNull(stage, "stage cannot be null");
        this.actions = Objects.requireNonNull(actions, "actions cannot be null");
        choices = TemplateGallery.choices(projectRoot);
        filter = null;
        selected = selectTitle == null ? choices.get(0) : choices.stream().filter(c -> c.title().equals(selectTitle)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The gallery has no template called " + selectTitle));
        filterButtons.clear();
        Table content = new Table(skin);
        content.top().left();
        Table header = new Table(skin);
        header.add(new Label("New Game: choose a template", skin, "section")).left().expandX();
        header.add(filterButton("All", null)).padLeft(4);
        for (GameMode mode : GameMode.values()) {
            header.add(filterButton(mode.getLabel(), mode)).padLeft(4);
        }
        content.add(header).growX().colspan(2).padBottom(6).row();
        list = new Table(skin);
        list.top().left();
        ScrollPane scroll = new ScrollPane(list, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        content.add(scroll).width(LIST_WIDTH).height(BODY_HEIGHT).top();
        details = new Table(skin);
        details.top().left();
        content.add(details).width(WIDTH - LIST_WIDTH).height(BODY_HEIGHT).top().padLeft(10);
        rebuildList();
        showDetails();
        float x = Math.max(0f, stage.getWidth() / 2f - WIDTH / 2f);
        popup.showContent(stage, x, stage.getHeight() * TOP_FRACTION, content);
    }

    /** Whether the gallery is on screen. */
    public boolean isOpen() {
        return popup.isOpen();
    }

    /** Closes the gallery and frees the preview. */
    public void close() {
        popup.close();
        disposePreview();
    }

    private TextButton filterButton(String label, GameMode mode) {
        TextButton button = new TextButton(label, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                filter = mode;
                // Rebuilding the list from inside one of its clicks would remove the actor handling the event; defer it
                Gdx.app.postRunnable(TemplateGalleryScreen.this::rebuildList);
            }
        });
        filterButtons.put(mode, button);
        return button;
    }

    private void rebuildList() {
        filterButtons.forEach((mode, button) -> button.setStyle(skin.get(mode == filter ? "active" : "flat",
            TextButton.TextButtonStyle.class)));
        list.clearChildren();
        TemplateGallery.Kind section = null;
        for (TemplateGallery.Choice choice : choices) {
            if (filter != null && choice.mode() != filter) {
                continue;
            }
            if (choice.kind() != section) {
                section = choice.kind();
                list.add(new Label(section == TemplateGallery.Kind.EMPTY ? "Empty starts" : "Templates", skin, "muted"))
                    .left().padTop(section == TemplateGallery.Kind.EMPTY ? 0 : 8).row();
            }
            TextButton row = new TextButton(choice.title() + "  (" + choice.mode().getLabel() + ")", skin,
                choice == selected ? "active" : "flat");
            row.getLabel().setAlignment(Align.left);
            row.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    selected = choice;
                    Gdx.app.postRunnable(() -> {
                        rebuildList();
                        showDetails();
                    });
                }
            });
            list.add(row).growX().height(BUTTON_HEIGHT).row();
        }
    }

    private void showDetails() {
        details.clearChildren();
        disposePreview();
        TemplateGallery.Choice choice = selected;
        if (choice.preview() != null) {
            preview = new Texture(new FileHandle(new File(projectRoot, choice.preview())));
            Image image = new Image(preview);
            image.setScaling(Scaling.fit);
            details.add(image).width(PREVIEW_WIDTH).height(PREVIEW_HEIGHT).left().row();
        } else {
            Table none = new Table(skin);
            none.setBackground(skin.getDrawable("elevated-bg"));
            none.add(new Label(choice.kind() == TemplateGallery.Kind.EMPTY ? "Empty " + choice.mode().getLabel() + " scene"
                : "No preview image", skin, "muted"));
            details.add(none).width(PREVIEW_WIDTH).height(PREVIEW_HEIGHT).left().row();
        }
        details.add(new Label(choice.title(), skin, "section")).left().padTop(6).row();
        details.add(new Label("Genre: " + choice.genre() + "    Dimension: " + choice.mode().getLabel() + "    Size: "
            + choice.sizeText(), skin, "secondary")).left().row();
        Label summary = new Label(choice.summary(), skin);
        summary.setWrap(true);
        details.add(summary).growX().padTop(4).row();
        for (String line : choice.platformText()) {
            details.add(new Label(line, skin, "muted")).left().row();
        }
        if (choice.kind() == TemplateGallery.Kind.SAMPLE) {
            showRating(choice);
        }
        Table nameRow = new Table(skin);
        nameRow.add(new Label("Project name", skin, "secondary")).padRight(6);
        TextField name = new TextField(defaultName(choice), skin);
        nameRow.add(name).width(260f);
        details.add(nameRow).left().padTop(8).row();
        Label status = new Label("", skin);
        status.setWrap(true);
        Table buttons = new Table(skin);
        if (choice.canPlaySample()) {
            TextButton play = new TextButton("Play Sample", skin);
            play.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    close();
                    actions.playSample(choice.scene());
                }
            });
            buttons.add(play).height(BUTTON_HEIGHT).padRight(6);
        }
        TextButton use = new TextButton(choice.kind() == TemplateGallery.Kind.EMPTY ? "Create Project" : "Use as Template", skin,
            "primary");
        use.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String chosenName = name.getText();
                // The buttons are replaced once the project is made; not from inside this click
                Gdx.app.postRunnable(() -> create(choice, chosenName, status, buttons));
            }
        });
        buttons.add(use).height(BUTTON_HEIGHT);
        details.add(buttons).left().padTop(6).row();
        details.add(status).growX().padTop(4).row();
    }

    /** The release-gate label (Appendix F5) and its reasons; a rating that cannot be made says so, it is never guessed. */
    private void showRating(TemplateGallery.Choice choice) {
        TemplateRatings.Rating rating;
        try {
            rating = actions.rating(choice);
        } catch (IOException | RuntimeException e) {
            details.add(new Label("Could not rate this template: " + e.getMessage(), skin, "error")).left().padTop(4).row();
            return;
        }
        Label headline = new Label(rating.headline(), skin, "secondary");
        headline.setWrap(true);
        details.add(headline).growX().padTop(4).row();
        Table reasons = new Table(skin);
        reasons.top().left();
        for (String reason : rating.reasons()) {
            Label line = new Label("- " + reason, skin, "muted");
            line.setWrap(true);
            reasons.add(line).growX().left().row();
        }
        ScrollPane scroll = new ScrollPane(reasons, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        details.add(scroll).growX().maxHeight(REASONS_HEIGHT).left().row();
    }

    private void create(TemplateGallery.Choice choice, String name, Label status, Table buttons) {
        TemplateProject.Result result;
        try {
            result = actions.create(choice, name);
        } catch (IOException | RuntimeException e) {
            status.setStyle(skin.get("error", Label.LabelStyle.class));
            status.setText("Could not make the project: " + e.getMessage());
            return;
        }
        status.setStyle(skin.get("success", Label.LabelStyle.class));
        status.setText("Made the project '" + result.folder().getName() + "' (" + result.files().size() + " files) in "
            + result.folder().getParent() + "." + (choice.kind() == TemplateGallery.Kind.SAMPLE ? " The sample is unchanged." : ""));
        buttons.clearChildren();
        TextButton open = new TextButton("Open '" + result.folder().getName() + "' (restarts the editor)", skin, "primary");
        open.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
                actions.openProject(result);
            }
        });
        TextButton later = new TextButton("Not now", skin);
        later.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        buttons.add(open).height(BUTTON_HEIGHT).padRight(6);
        buttons.add(later).height(BUTTON_HEIGHT);
    }

    private static String defaultName(TemplateGallery.Choice choice) {
        if (choice.kind() == TemplateGallery.Kind.EMPTY) {
            return StarterProject.defaultName(choice.mode());
        }
        return choice.title();
    }

    private void disposePreview() {
        if (preview != null) {
            preview.dispose();
            preview = null;
        }
    }

    @Override
    public void dispose() {
        disposePreview();
    }
}
