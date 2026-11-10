package io.github.ldev22live.gdxunity.editor.desktop.ui.timeline;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.character.CharacterSession;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.character.CharacterSpec;
import io.github.ldev22live.gdxunity.engine.character.SliderDef;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.ModelPreview;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The character creator inside the Animations tab (TD-053): body settings and the other sliders (grouped by body part), the
 * animation templates to include, and Create / Update, which writes the model into {@code characters/} and puts it in the scene.
 * All state and file work is in {@link CharacterSession}; this class is the rows and buttons. Numbers are committed when the user
 * presses Enter or leaves the field; a value out of range is reported in the status bar and the old one is shown again. Each setting
 * also has a slider, committed when it is let go.
 *
 * <p>A live 3D preview at the top (TD-056) shows the character for the current settings, playing the animation chosen under it. It is
 * reloaded when the settings or the selected templates change, and only while the creator is on screen. Without a graphics context
 * (in tests) there is no preview.
 */
public class CharacterCreatorPanel extends Table implements Disposable {
    private static final float ROW_HEIGHT = 22f;
    private static final float LABEL_WIDTH = 120f;
    private static final float BUTTON_HEIGHT = 24f;
    /** Wide enough that Undo, Redo, Reset and Open read as four buttons, not one line of text. */
    private static final float HISTORY_BUTTON_WIDTH = 64f;
    /** Width of the number field beside a slider. */
    private static final float SLIDER_FIELD_WIDTH = 56f;
    /** Smallest step of a slider: finer than the three decimals the number field shows. */
    private static final float SLIDER_STEP = 0.001f;
    /** Height of the preview picture. */
    private static final float PREVIEW_HEIGHT = 250f;
    /** Choice in the preview's animation list that shows the character standing still. */
    private static final String REST_POSE = "Rest pose";
    /** Color behind the character in the preview. */
    private static final Color PREVIEW_BACKGROUND = new Color(0.16f, 0.17f, 0.2f, 1f);
    /** Animation the preview starts with, when the project has it. */
    private static final String DEFAULT_PREVIEW_ANIMATION = "Idle";

    private final Skin skin;
    private final EditorState editorState;
    private final Consumer<AssetReference> onModelWritten;
    private final CharacterSession session;
    private final Set<String> openGroups = new HashSet<>();
    private String characterName = "Character";
    private boolean showAllSliders;
    private final CharacterPreviewWidget preview;
    private String previewAnimation = REST_POSE;
    private CharacterSpec previewedSpec;
    private List<String> previewedTemplates;

    /**
     * @param onModelWritten called after a character's model file was (re)written, so a cached copy of it is reloaded
     */
    public CharacterCreatorPanel(Skin skin, EditorState editorState, File assetRoot, Consumer<AssetReference> onModelWritten) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.onModelWritten = Objects.requireNonNull(onModelWritten, "onModelWritten cannot be null");
        CharacterSession opened = null;
        String problem = null;
        try {
            opened = new CharacterSession(Objects.requireNonNull(assetRoot, "assetRoot cannot be null"));
        } catch (IOException e) {
            problem = "Character data not found or unreadable in assets/characters/makehuman: " + e.getMessage();
        }
        this.session = opened;
        // Needs a graphics context: there is none in headless tests
        this.preview = session != null && Gdx.gl != null
            ? new CharacterPreviewWidget(new ModelPreview(), PREVIEW_BACKGROUND, this::refreshPreview) : null;
        if (session != null && session.availableTemplates().contains(DEFAULT_PREVIEW_ANIMATION)) {
            previewAnimation = DEFAULT_PREVIEW_ANIMATION;
        }
        top().left();
        if (session == null) {
            Label message = new Label(problem, skin, "muted");
            message.setWrap(true);
            add(message).growX().pad(8).row();
            return;
        }
        rebuild();
    }

    /** The session behind the panel, for tests and for other tools. Null when the character data could not be read. */
    public CharacterSession getSession() {
        return session;
    }

    private void rebuild() {
        clearChildren();
        top().left();
        if (preview != null) {
            add(preview).growX().height(PREVIEW_HEIGHT).padBottom(2).row();
            add(previewControls()).growX().height(ROW_HEIGHT).padBottom(2).row();
        }
        Table content = new Table(skin);
        content.top().left();

        content.add(new Label("Name", skin, "muted")).left().width(LABEL_WIDTH / 2);
        TextField name = new TextField(characterName, skin);
        name.setTextFieldListener((field, c) -> characterName = field.getText());
        content.add(name).growX().height(ROW_HEIGHT).row();

        Table history = new Table(skin);
        history.add(button("Undo", session::canUndo, session::undo)).height(BUTTON_HEIGHT).width(HISTORY_BUTTON_WIDTH).padRight(4);
        history.add(button("Redo", session::canRedo, session::redo)).height(BUTTON_HEIGHT).width(HISTORY_BUTTON_WIDTH).padRight(4);
        history.add(button("Reset", () -> true, session::reset)).height(BUTTON_HEIGHT).width(HISTORY_BUTTON_WIDTH).padRight(4);
        history.add(button("Open", () -> true, this::openCharacter)).height(BUTTON_HEIGHT).width(HISTORY_BUTTON_WIDTH);
        content.add(history).colspan(2).left().padTop(4).padBottom(4).row();

        CharacterSpec spec = session.getSpec();
        header(content, "Body");
        numberRow(content, "Gender (0 f, 1 m)", spec.getGender(), 0f, 1f, v -> session.edit(s -> s.setGender(v)));
        numberRow(content, "Age (years)", spec.getAgeYears(), CharacterSpec.MIN_AGE_YEARS, CharacterSpec.MAX_AGE_YEARS, v -> session.edit(s -> s.setAgeYears(v)));
        numberRow(content, "Muscle (0..1)", spec.getMuscle(), 0f, 1f, v -> session.edit(s -> s.setMuscle(v)));
        numberRow(content, "Weight (0..1)", spec.getWeight(), 0f, 1f, v -> session.edit(s -> s.setWeight(v)));
        numberRow(content, "Height scale", spec.getHeightScale(), CharacterSpec.MIN_HEIGHT_SCALE, CharacterSpec.MAX_HEIGHT_SCALE, v -> session.edit(s -> s.setHeightScale(v)));
        float[] skinColor = spec.getSkinColor();
        numberRow(content, "Skin red", skinColor[0], 0f, 1f, v -> session.edit(s -> s.setSkinColor(v, s.getSkinColor()[1], s.getSkinColor()[2])));
        numberRow(content, "Skin green", skinColor[1], 0f, 1f, v -> session.edit(s -> s.setSkinColor(s.getSkinColor()[0], v, s.getSkinColor()[2])));
        numberRow(content, "Skin blue", skinColor[2], 0f, 1f, v -> session.edit(s -> s.setSkinColor(s.getSkinColor()[0], s.getSkinColor()[1], v)));

        header(content, "Animation templates");
        List<String> selected = session.selectedTemplates();
        for (String template : session.availableTemplates()) {
            boolean on = selected.contains(template);
            TextButton toggle = new TextButton((on ? "[x] " : "[ ] ") + template, skin, "flat");
            toggle.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            toggle.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    session.setTemplateSelected(template, !on);
                    rebuild();
                }
            });
            content.add(toggle).colspan(2).growX().height(ROW_HEIGHT).row();
        }

        header(content, "Shape sliders (-1 to 1)");
        TextButton all = new TextButton(showAllSliders ? "Hide all groups" : "Show all groups", skin, "flat");
        all.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showAllSliders = !showAllSliders;
                rebuild();
            }
        });
        content.add(all).colspan(2).left().height(ROW_HEIGHT).row();
        TreeMap<String, List<SliderDef>> groups = new TreeMap<>();
        for (SliderDef slider : session.sliders()) {
            groups.computeIfAbsent(slider.group(), g -> new java.util.ArrayList<>()).add(slider);
        }
        for (var entry : groups.entrySet()) {
            String group = entry.getKey();
            boolean open = showAllSliders || openGroups.contains(group);
            TextButton toggle = new TextButton((open ? "- " : "+ ") + group + " (" + entry.getValue().size() + ")", skin, "flat");
            toggle.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            toggle.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!openGroups.remove(group)) {
                        openGroups.add(group);
                    }
                    rebuild();
                }
            });
            content.add(toggle).colspan(2).growX().height(ROW_HEIGHT).row();
            if (open) {
                for (SliderDef slider : entry.getValue()) {
                    numberRow(content, slider.label(), session.getSpec().getSlider(slider.id()), slider.min(), slider.max(),
                        v -> session.setSlider(slider.id(), v));
                }
            }
        }

        ScrollPane scroll = new ScrollPane(content, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        add(scroll).grow().row();
        TextButton create = new TextButton("Create / Update character", skin, "flat");
        create.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                createOrUpdate();
            }
        });
        add(create).growX().height(BUTTON_HEIGHT + 4).pad(4);
    }

    private void header(Table content, String text) {
        content.add(new Label(" " + text, skin, "muted")).colspan(2).left().padTop(8).padBottom(2).row();
    }

    private TextButton button(String text, java.util.function.BooleanSupplier enabled, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.setDisabled(!enabled.getAsBoolean());
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!enabled.getAsBoolean()) {
                    return;
                }
                try {
                    action.run();
                } catch (RuntimeException e) {
                    editorState.setStatusMessage("Character: " + e.getMessage(), true);
                }
                rebuild();
            }
        });
        return button;
    }

    /**
     * A label, a slider and a number field for one setting; {@code apply} throws for a value out of range, which restores the row.
     * The slider commits when it is let go (the field follows it while it moves), the field when Enter is pressed or it is left.
     */
    private void numberRow(Table content, String label, float value, float min, float max, Consumer<Float> apply) {
        content.add(new Label(label, skin, "muted")).left().width(LABEL_WIDTH);
        String shown = String.format(Locale.ROOT, "%.3f", value);
        TextField field = new TextField(shown, skin);
        Runnable commit = () -> {
            if (field.getText().equals(shown)) {
                return;
            }
            try {
                apply.accept(Float.parseFloat(field.getText().trim()));
            } catch (IllegalArgumentException e) {
                editorState.setStatusMessage("Character: " + e.getMessage(), true);
            }
            rebuild();
        };
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                commit.run();
            }
        });
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    commit.run();
                }
            }
        });
        Slider slider = new Slider(min, max, SLIDER_STEP, false, skin);
        slider.setValue(value);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                field.setText(String.format(Locale.ROOT, "%.3f", slider.getValue()));
                if (slider.isDragging()) {
                    return;
                }
                try {
                    apply.accept(slider.getValue());
                } catch (IllegalArgumentException e) {
                    editorState.setStatusMessage("Character: " + e.getMessage(), true);
                }
                rebuild();
            }
        });
        Table cell = new Table(skin);
        cell.add(slider).growX().padRight(4);
        cell.add(field).width(SLIDER_FIELD_WIDTH).height(ROW_HEIGHT);
        content.add(cell).growX().height(ROW_HEIGHT).row();
    }

    /** The preview's animation list and a button to bring the view back. */
    private Table previewControls() {
        Table controls = new Table(skin);
        controls.add(new Label("Preview", skin, "muted")).left().padRight(6);
        List<String> choices = new ArrayList<>();
        choices.add(REST_POSE);
        choices.addAll(session.availableTemplates());
        SelectBox<String> animations = new SelectBox<>(skin);
        animations.setItems(choices.toArray(new String[0]));
        animations.setSelected(choices.contains(previewAnimation) ? previewAnimation : REST_POSE);
        animations.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                previewAnimation = animations.getSelected();
                applyPreviewAnimation();
            }
        });
        controls.add(animations).growX().height(ROW_HEIGHT);
        TextButton view = new TextButton("Reset view", skin, "flat");
        view.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                preview.getPreview().resetView();
            }
        });
        controls.add(view).height(ROW_HEIGHT).padLeft(4);
        return controls;
    }

    /**
     * Reloads the preview when the settings or the selected templates differ from what it shows. Runs before each frame of the
     * preview, so nothing is built while the creator is hidden. A failure is reported once, not on every frame.
     */
    private void refreshPreview() {
        CharacterSpec current = session.getSpec();
        List<String> templates = session.selectedTemplates();
        if (current.equals(previewedSpec) && templates.equals(previewedTemplates)) {
            return;
        }
        previewedSpec = current;
        previewedTemplates = templates;
        try {
            preview.getPreview().load(session.bakeModel().gltf());
            applyPreviewAnimation();
        } catch (IOException | RuntimeException e) {
            editorState.setStatusMessage("Character preview: " + e.getMessage(), true);
        }
    }

    /** Plays the animation chosen in the list, or shows the rest pose when the model does not have it (not selected as a template). */
    private void applyPreviewAnimation() {
        ModelPreview model = preview.getPreview();
        model.setAnimation(model.animationNames().contains(previewAnimation) ? previewAnimation : null);
    }

    @Override
    public void dispose() {
        if (preview != null) {
            preview.dispose();
        }
    }

    /**
     * Opens a saved character and sets the preview's animation, as the Open button and the list under the preview do.
     *
     * @param animation the animation for the preview, or null to leave it as it is
     */
    public void open(String name, String animation) {
        characterName = Objects.requireNonNull(name, "name cannot be null");
        openCharacter();
        if (animation != null) {
            previewAnimation = animation;
        }
        rebuild();
    }

    private void openCharacter() {
        try {
            session.load(characterName);
            editorState.setStatusMessage("Opened character " + characterName, false);
        } catch (IOException | IllegalArgumentException e) {
            editorState.setStatusMessage("Cannot open character: " + e.getMessage(), true);
        }
    }

    /**
     * Writes the model and puts it in the scene. A character already in the scene with this name and model is left where it is (it
     * shows the new model once the cached copy is reloaded); otherwise a new entity is created, one undo step.
     */
    private void createOrUpdate() {
        try {
            CharacterSession.BakeResult baked = session.bake(characterName);
            onModelWritten.accept(baked.model());
            Entity existing = editorState.getActiveScene().findEntityByName(characterName.trim());
            ModelComponent shown = existing == null ? null : existing.getComponent(ModelComponent.class);
            boolean updated = shown != null && baked.model().getPath().equals(shown.getModelPath());
            if (!updated && !session.place(editorState, characterName.trim(), baked.model(), null)) {
                return;
            }
            String message = (updated ? "Updated character " : "Created character ") + characterName.trim() + " ("
                + session.selectedTemplates().size() + " animations)";
            if (!baked.problems().isEmpty()) {
                message += ". Not applied: " + String.join("; ", baked.problems());
            }
            editorState.setStatusMessage(message, !baked.problems().isEmpty());
        } catch (IOException | RuntimeException e) {
            editorState.setStatusMessage("Cannot create character: " + e.getMessage(), true);
        }
    }
}
