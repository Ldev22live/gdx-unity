package io.github.ldev22live.gdxunity.editor.desktop.ui.timeline;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.animation.TimelineDocument;
import io.github.ldev22live.gdxunity.editor.animation.TimelineSession;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperty;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.Interpolation;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Timeline tab (TD-031): a toolbar over a {@link TimelineCanvas}. Clips are opened from an Animator component
 * (Details → Open in Timeline) or by double-clicking a {@code .anim} file.
 */
public class TimelinePanel extends Table implements Disposable {
    private static final float BAR_HEIGHT = 30f;
    private static final float BUTTON_HEIGHT = 24f;
    private static final float NUMBER_FIELD_WIDTH = 46f;

    private final Skin skin;
    private final EditorState editorState;
    private final TimelineSession session;
    private final TimelineCanvas canvas;
    private final ContextMenu trackMenu;
    private final Label titleLabel;
    private final Label timeLabel;
    private final TextButton playButton;
    private final TextButton reverseButton;
    private final TextButton loopButton;
    private final TextButton autoKeyButton;
    private final TextButton curvesButton;
    private final SelectBox<Interpolation> interpolationSelect;
    private final TextField lengthField;
    private final TextField frameRateField;
    private boolean refreshing;
    private final TimelineSession.Listener sessionListener = s -> refresh();

    public TimelinePanel(Skin skin, EditorState editorState, TimelineSession session) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.canvas = new TimelineCanvas(skin, session);
        this.trackMenu = new ContextMenu(skin);
        setBackground("panel-bg");
        top().left();

        Table bar = new Table(skin);
        bar.setBackground("subheader-bg");
        bar.left();
        bar.add(button("Save", this::save)).height(BUTTON_HEIGHT).padLeft(4);
        bar.add(button("Undo", () -> withDocument(TimelineDocument::undo))).height(BUTTON_HEIGHT);
        bar.add(button("Redo", () -> withDocument(TimelineDocument::redo))).height(BUTTON_HEIGHT).padRight(8);
        bar.add(button("|<", () -> session.setTime(0f))).height(BUTTON_HEIGHT);
        playButton = button("Play", () -> session.setPlaying(!session.isPlaying()));
        bar.add(playButton).width(52).height(BUTTON_HEIGHT);
        reverseButton = button("Reverse", () -> session.setReverse(!session.isReverse()));
        bar.add(reverseButton).height(BUTTON_HEIGHT);
        loopButton = button("Loop", () -> session.setLooping(!session.isLooping()));
        bar.add(loopButton).height(BUTTON_HEIGHT).padRight(8);
        autoKeyButton = button("Auto-Key", () -> session.setAutoKey(!session.isAutoKey()));
        bar.add(autoKeyButton).height(BUTTON_HEIGHT);
        bar.add(button("Key", session::keySelection)).height(BUTTON_HEIGHT);
        TextButton addTrack = new TextButton("Add Track", skin, "flat");
        addTrack.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showAddTrack(addTrack);
            }
        });
        bar.add(addTrack).height(BUTTON_HEIGHT).padRight(8);

        interpolationSelect = new SelectBox<>(skin);
        interpolationSelect.setItems(Interpolation.values());
        interpolationSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!refreshing && session.getDocument() != null && !canvas.getSelection().isEmpty()) {
                    session.getDocument().setInterpolation(new ArrayList<>(canvas.getSelection()), interpolationSelect.getSelected());
                }
            }
        });
        bar.add(new Label("Keys", skin, "muted")).padRight(4);
        bar.add(interpolationSelect).width(90).height(22).padRight(8);
        curvesButton = button("Curves", () -> {
            canvas.setCurveView(!canvas.isCurveView());
            refresh();
        });
        bar.add(curvesButton).height(BUTTON_HEIGHT).padRight(8);

        bar.add(new Label("Length", skin, "muted")).padRight(4);
        lengthField = numberField(this::commitTiming);
        bar.add(lengthField).width(NUMBER_FIELD_WIDTH).height(22);
        bar.add(new Label("s  FPS", skin, "muted")).padLeft(2).padRight(4);
        frameRateField = numberField(this::commitTiming);
        bar.add(frameRateField).width(NUMBER_FIELD_WIDTH - 10f).height(22).padRight(8);
        timeLabel = new Label("", skin);
        bar.add(timeLabel).width(92).padRight(8);
        titleLabel = new Label("", skin, "muted");
        titleLabel.setEllipsis(true);
        bar.add(titleLabel).left().expandX().fillX().minWidth(0);

        add(bar).growX().height(BAR_HEIGHT).row();
        add(canvas).grow();
        session.addListener(sessionListener);
        refresh();
    }

    /** Updates labels, toggles and the canvas from the session; called on every session change. */
    public void refresh() {
        refreshing = true;
        try {
            canvas.refresh();
            TimelineDocument document = session.getDocument();
            playButton.setText(session.isPlaying() ? "Pause" : "Play");
            setActive(reverseButton, session.isReverse());
            setActive(loopButton, session.isLooping());
            setActive(autoKeyButton, session.isAutoKey());
            setActive(curvesButton, canvas.isCurveView());
            if (document == null) {
                titleLabel.setText("No clip open. Add an Animator (Details > Add Component > Animation), "
                    + "or double-click a .anim file in the Content Browser.");
                timeLabel.setText("");
                lengthField.setText("");
                frameRateField.setText("");
                return;
            }
            AnimationClip clip = document.getClip();
            Entity root = session.getRootEntity();
            titleLabel.setText(clip.getName() + (document.isDirty() ? " *" : "") + "   " + document.getAsset().getPath()
                + (root != null ? "   on " + root.getName() : "   (no Animator entity: preview is off)"));
            timeLabel.setText(String.format(Locale.ROOT, "%.2f / %.2f s", session.getTime(), clip.getDuration()));
            if (lengthField.getStage() == null || lengthField.getStage().getKeyboardFocus() != lengthField) {
                lengthField.setText(String.format(Locale.ROOT, "%.2f", clip.getDuration()));
            }
            if (frameRateField.getStage() == null || frameRateField.getStage().getKeyboardFocus() != frameRateField) {
                frameRateField.setText(Integer.toString(clip.getFrameRate()));
            }
            if (!canvas.getSelection().isEmpty()) {
                var key = canvas.getSelection().iterator().next().resolve();
                if (key != null) {
                    interpolationSelect.setSelected(key.interpolation());
                }
            }
        } finally {
            refreshing = false;
        }
    }

    public TimelineCanvas getCanvas() {
        return canvas;
    }

    private void save() {
        withDocument(document -> {
            if (session.saveIfDirty()) {
                editorState.setStatusMessage("Saved animation " + document.getAsset().getPath(), false);
            }
            return true;
        });
    }

    private void commitTiming() {
        TimelineDocument document = session.getDocument();
        if (document == null || refreshing) {
            return;
        }
        try {
            float length = Float.parseFloat(lengthField.getText().trim());
            int frameRate = Integer.parseInt(frameRateField.getText().trim());
            if (!document.setTiming(length, frameRate)
                && (length != document.getClip().getDuration() || frameRate != document.getClip().getFrameRate())) {
                editorState.setStatusMessage("Length must be at least " + AnimationClip.MIN_DURATION
                    + " s and FPS 1.." + AnimationClip.MAX_FRAME_RATE, true);
            }
        } catch (NumberFormatException e) {
            editorState.setStatusMessage("Length and FPS must be numbers", true);
        }
        refresh();
    }

    /** Menu of properties the selected entity (or the animated entity) has and the clip does not animate yet. */
    private void showAddTrack(Actor anchor) {
        TimelineDocument document = session.getDocument();
        Entity root = session.getRootEntity();
        if (document == null || root == null || getStage() == null) {
            editorState.setStatusMessage("Open a clip from an Animator first", true);
            return;
        }
        Entity target = editorState.getSelectedEntity() != null ? editorState.getSelectedEntity() : root;
        String path = AnimationTrack.pathOf(root, target);
        if (path == null) {
            editorState.setStatusMessage(target.getName() + " is not " + root.getName() + " or one of its children", true);
            return;
        }
        List<ContextMenu.Item> items = new ArrayList<>();
        for (AnimatedProperty property : session.getProperties().availableOn(target)) {
            boolean exists = document.getClip().findTrack(path, property.id()) != null;
            items.add(new ContextMenu.Item(target.getName() + " › " + property.group() + " › " + property.displayName(),
                null, !exists, () -> document.addTrack(path, property.id())));
        }
        Vector2 at = anchor.localToStageCoordinates(new Vector2(0f, 0f));
        trackMenu.show(getStage(), at.x, at.y, items);
    }

    private interface DocumentAction {
        boolean run(TimelineDocument document);
    }

    private void withDocument(DocumentAction action) {
        TimelineDocument document = session.getDocument();
        if (document == null) {
            editorState.setStatusMessage("No animation clip open", true);
            return;
        }
        action.run(document);
    }

    private void setActive(TextButton button, boolean active) {
        button.setStyle(skin.get(active ? "active" : "flat", TextButton.TextButtonStyle.class));
    }

    private TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /** A text field that commits on Enter or when it loses focus. */
    private TextField numberField(Runnable onCommit) {
        TextField field = new TextField("", skin);
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                onCommit.run();
            }
        });
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    onCommit.run();
                }
            }
        });
        return field;
    }

    @Override
    public void dispose() {
        session.removeListener(sessionListener);
        canvas.dispose();
    }
}
