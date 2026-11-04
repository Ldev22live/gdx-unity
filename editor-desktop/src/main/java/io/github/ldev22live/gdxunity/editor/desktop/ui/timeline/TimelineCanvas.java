package io.github.ldev22live.gdxunity.editor.desktop.ui.timeline;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;
import io.github.ldev22live.gdxunity.editor.animation.TimelineDocument;
import io.github.ldev22live.gdxunity.editor.animation.TimelineDocument.KeyRef;
import io.github.ldev22live.gdxunity.editor.animation.TimelineSession;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperty;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationCurve;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.ClipSampler;
import io.github.ldev22live.gdxunity.engine.animation.Interpolation;
import io.github.ldev22live.gdxunity.engine.animation.Keyframe;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Timeline's dope sheet and curve view (TD-031). Track names on the left, keys as diamonds along a ruler on the
 * right, and the play head.
 * <ul>
 *   <li>Click or drag on the ruler: move the play head (the scene previews the pose).</li>
 *   <li>Click a key: select (Shift adds). Drag keys: move them, snapped to frames. Delete: remove them.</li>
 *   <li>Double-click in a track row: key the property's current value there.</li>
 *   <li>Right-click a track name: remove the track. Wheel: scroll the tracks.</li>
 *   <li>Curve view: the selected track's curve fills the area; drag a key to change its time and value.</li>
 *   <li>Space: play / pause. Ctrl+Z / Ctrl+Y: the clip's own undo history.</li>
 * </ul>
 */
public class TimelineCanvas extends Widget implements Disposable {
    private static final float GUTTER_WIDTH = 190f;
    private static final float RULER_HEIGHT = 22f;
    private static final float ROW_HEIGHT = 20f;
    private static final float KEY_HALF_SIZE = 5f;
    /** Extra pixels around a key that still count as clicking it. */
    private static final float KEY_HIT_SLOP = 3f;
    private static final float INDENT = 14f;
    private static final float RIGHT_PADDING = 12f;
    private static final float CURVE_POINT_HALF_SIZE = 4f;
    /** Pixels between curve samples. */
    private static final float CURVE_SAMPLE_STEP = 2f;
    /** Space above and below the curve's value range, as a fraction of the range. */
    private static final float CURVE_MARGIN = 0.15f;
    private static final float MIN_CURVE_RANGE = 1f;
    /** Two clicks within this time and distance are a double-click. */
    private static final long DOUBLE_CLICK_MILLIS = 400L;
    private static final float DOUBLE_CLICK_SLOP = 4f;

    private static final Color KEY_COLOR = new Color(0.82f, 0.82f, 0.86f, 1f);
    private static final Color SUMMARY_KEY_COLOR = new Color(0.55f, 0.55f, 0.6f, 1f);
    private static final Color SELECTED_COLOR = new Color(1f, 0.62f, 0.12f, 1f);
    private static final Color PLAYHEAD_COLOR = new Color(0.95f, 0.3f, 0.3f, 1f);
    private static final Color CURVE_COLOR = new Color(0.4f, 0.75f, 1f, 1f);

    /** One row: an entity heading or one of its tracks. */
    private record Row(String path, AnimationTrack track) {
        boolean isHeading() {
            return track == null;
        }
    }

    private enum Drag { NONE, SCRUB, MOVE_KEYS, CURVE_KEY }

    private final Skin skin;
    private final TimelineSession session;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final ContextMenu trackMenu;
    private final Set<KeyRef> selection = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private AnimationTrack selectedTrack;
    private boolean curveView;
    private int scrollRows;

    private Drag drag = Drag.NONE;
    private float dragStartTime;
    private float dragDeltaTime;
    private KeyRef curveKey;
    private float curveDragTime;
    private float curveDragValue;
    private float curveMin;
    private float curveMax;

    public TimelineCanvas(Skin skin, TimelineSession session) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.trackMenu = new ContextMenu(skin);
        addListener(new CanvasInput());
    }

    public Set<KeyRef> getSelection() {
        return selection;
    }

    public AnimationTrack getSelectedTrack() {
        return selectedTrack;
    }

    public boolean isCurveView() {
        return curveView;
    }

    public void setCurveView(boolean curveView) {
        this.curveView = curveView;
    }

    /** Rebuilds the rows from the clip; called after every clip change. */
    public void refresh() {
        rows.clear();
        TimelineDocument document = session.getDocument();
        if (document == null) {
            selection.clear();
            selectedTrack = null;
            return;
        }
        String lastPath = null;
        for (AnimationTrack track : document.getClip().getTracks()) {
            if (!track.getTargetPath().equals(lastPath)) {
                rows.add(new Row(track.getTargetPath(), null));
                lastPath = track.getTargetPath();
            }
            rows.add(new Row(track.getTargetPath(), track));
        }
        selection.removeIf(ref -> ref.resolve() == null || !document.getClip().getTracks().contains(ref.track()));
        if (selectedTrack != null && !document.getClip().getTracks().contains(selectedTrack)) {
            selectedTrack = null;
        }
        scrollRows = Math.max(0, Math.min(scrollRows, rows.size() - 1));
    }

    // --- Geometry ---

    private TimelineLayout timeLayout() {
        float duration = session.getDocument().getClip().getDuration();
        return new TimelineLayout(GUTTER_WIDTH + KEY_HALF_SIZE + 2f,
            Math.max(1f, getWidth() - GUTTER_WIDTH - KEY_HALF_SIZE - 2f - RIGHT_PADDING), duration);
    }

    private float rowsTop() {
        return getHeight() - RULER_HEIGHT;
    }

    /** Row index under local {@code y}, or -1. */
    private int rowAt(float y) {
        if (y > rowsTop()) {
            return -1;
        }
        int index = (int) ((rowsTop() - y) / ROW_HEIGHT) + scrollRows;
        return index < rows.size() ? index : -1;
    }

    /** Vertical centre of row {@code index}. */
    private float rowCenterY(int index) {
        return rowsTop() - (index - scrollRows + 0.5f) * ROW_HEIGHT;
    }

    private float curveY(float value) {
        float bottom = 6f;
        float top = rowsTop() - 6f;
        return bottom + (value - curveMin) / (curveMax - curveMin) * (top - bottom);
    }

    private float curveValueAt(float y) {
        float bottom = 6f;
        float top = rowsTop() - 6f;
        return curveMin + (y - bottom) / (top - bottom) * (curveMax - curveMin);
    }

    private void updateCurveRange(AnimationCurve curve) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Keyframe key : curve.getKeys()) {
            min = Math.min(min, key.value());
            max = Math.max(max, key.value());
        }
        if (curve.isEmpty()) {
            min = 0f;
            max = MIN_CURVE_RANGE;
        }
        float range = Math.max(MIN_CURVE_RANGE, max - min);
        float centre = (min + max) / 2f;
        curveMin = centre - range * (0.5f + CURVE_MARGIN);
        curveMax = centre + range * (0.5f + CURVE_MARGIN);
    }

    // --- Drawing ---

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        if (!clipBegin()) {
            return;
        }
        batch.end();
        shapes.setProjectionMatrix(getStage().getCamera().combined);
        shapes.setTransformMatrix(batch.getTransformMatrix());
        shapes.translate(getX(), getY(), 0f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawBackground();
        TimelineDocument document = session.getDocument();
        if (document != null) {
            if (curveView) {
                drawCurveView();
            } else {
                drawKeys(document.getClip());
            }
            drawPlayhead();
        }
        shapes.end();
        if (document != null) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            drawRulerTicks(document.getClip());
            if (curveView) {
                drawCurveLine();
            }
            shapes.end();
        }
        batch.begin();
        if (document != null) {
            drawText(batch, document);
        }
        batch.flush();
        clipEnd();
    }

    private void drawBackground() {
        shapes.setColor(skin.getColor("surface"));
        shapes.rect(0f, 0f, getWidth(), getHeight());
        shapes.setColor(skin.getColor("subheader"));
        shapes.rect(0f, rowsTop(), getWidth(), RULER_HEIGHT);
        shapes.setColor(skin.getColor("surface-variant"));
        shapes.rect(0f, 0f, GUTTER_WIDTH, rowsTop());
        if (curveView) {
            return;
        }
        for (int i = scrollRows; i < rows.size(); i++) {
            float y = rowCenterY(i) - ROW_HEIGHT / 2f;
            if (y + ROW_HEIGHT < 0f) {
                break;
            }
            Row row = rows.get(i);
            if (row.isHeading()) {
                shapes.setColor(skin.getColor("surface-elevated"));
                shapes.rect(0f, y, getWidth(), ROW_HEIGHT);
            } else if (row.track() == selectedTrack) {
                shapes.setColor(skin.getColor("border-muted"));
                shapes.rect(0f, y, getWidth(), ROW_HEIGHT);
            }
        }
        shapes.setColor(skin.getColor("border"));
        shapes.rect(GUTTER_WIDTH - 1f, 0f, 1f, rowsTop());
    }

    private void drawKeys(AnimationClip clip) {
        TimelineLayout layout = timeLayout();
        for (int i = scrollRows; i < rows.size(); i++) {
            float y = rowCenterY(i);
            if (y < -ROW_HEIGHT) {
                break;
            }
            Row row = rows.get(i);
            if (row.isHeading()) {
                // Summary: one diamond wherever any track of this entity has a key
                TreeSet<Float> times = new TreeSet<>();
                for (AnimationTrack track : clip.getTracks()) {
                    if (track.getTargetPath().equals(row.path())) {
                        track.getCurve().getKeys().forEach(k -> times.add(k.time()));
                    }
                }
                shapes.setColor(SUMMARY_KEY_COLOR);
                for (float t : times) {
                    diamond(layout.xOf(t), y, KEY_HALF_SIZE - 1f);
                }
                continue;
            }
            for (Keyframe key : row.track().getCurve().getKeys()) {
                boolean selected = selection.contains(new KeyRef(row.track(), key.time()));
                float t = selected && drag == Drag.MOVE_KEYS ? clamp(key.time() + dragDeltaTime, clip) : key.time();
                shapes.setColor(selected ? SELECTED_COLOR : KEY_COLOR);
                if (key.interpolation() == Interpolation.CONSTANT) {
                    shapes.rect(layout.xOf(t) - KEY_HALF_SIZE + 1f, y - KEY_HALF_SIZE + 1f,
                        2f * KEY_HALF_SIZE - 2f, 2f * KEY_HALF_SIZE - 2f);
                } else {
                    diamond(layout.xOf(t), y, KEY_HALF_SIZE);
                }
            }
        }
    }

    private void drawCurveView() {
        if (selectedTrack == null) {
            return;
        }
        updateCurveRange(selectedTrack.getCurve());
        TimelineLayout layout = timeLayout();
        for (Keyframe key : selectedTrack.getCurve().getKeys()) {
            KeyRef ref = new KeyRef(selectedTrack, key.time());
            boolean dragging = drag == Drag.CURVE_KEY && ref.equals(curveKey);
            float t = dragging ? curveDragTime : key.time();
            float v = dragging ? curveDragValue : key.value();
            shapes.setColor(selection.contains(ref) || dragging ? SELECTED_COLOR : KEY_COLOR);
            shapes.rect(layout.xOf(t) - CURVE_POINT_HALF_SIZE, curveY(v) - CURVE_POINT_HALF_SIZE,
                2f * CURVE_POINT_HALF_SIZE, 2f * CURVE_POINT_HALF_SIZE);
        }
    }

    private void drawCurveLine() {
        if (selectedTrack == null || selectedTrack.getCurve().isEmpty()) {
            return;
        }
        TimelineLayout layout = timeLayout();
        AnimationCurve curve = selectedTrack.getCurve();
        if (drag == Drag.CURVE_KEY && curveKey != null && curveKey.resolve() != null) {
            // Show the curve as it will be after the drag
            curve = curve.copy();
            curve.removeKeyAt(curveKey.time());
            curve.setKey(new Keyframe(curveDragTime, curveDragValue, curveKey.resolve().interpolation()));
        }
        shapes.setColor(skin.getColor("border-muted"));
        shapes.line(layout.xOf(0f), curveY(0f), layout.xOf(session.getDocument().getClip().getDuration()), curveY(0f));
        shapes.setColor(CURVE_COLOR);
        float startX = layout.xOf(0f);
        float endX = layout.xOf(session.getDocument().getClip().getDuration());
        float previousY = curveY(curve.evaluate(0f));
        for (float x = startX + CURVE_SAMPLE_STEP; x <= endX; x += CURVE_SAMPLE_STEP) {
            float y = curveY(curve.evaluate(layout.timeAt(x)));
            shapes.line(x - CURVE_SAMPLE_STEP, previousY, x, y);
            previousY = y;
        }
    }

    private void drawPlayhead() {
        float x = timeLayout().xOf(session.getTime());
        shapes.setColor(PLAYHEAD_COLOR);
        shapes.rect(x - 0.75f, 0f, 1.5f, rowsTop());
        shapes.triangle(x - 6f, getHeight(), x + 6f, getHeight(), x, rowsTop());
    }

    private void drawRulerTicks(AnimationClip clip) {
        TimelineLayout layout = timeLayout();
        shapes.setColor(skin.getColor("text-muted"));
        float top = getHeight();
        if (layout.showFrameTicks(clip.getFrameRate())) {
            int frames = Math.round(clip.getDuration() * clip.getFrameRate());
            for (int f = 0; f <= frames; f++) {
                float x = layout.xOf(f / (float) clip.getFrameRate());
                shapes.line(x, top - RULER_HEIGHT, x, top - RULER_HEIGHT + 4f);
            }
        }
        float step = layout.labelStep();
        for (float t = 0f; t <= clip.getDuration() + 1e-4f; t += step) {
            float x = layout.xOf(t);
            shapes.line(x, top - RULER_HEIGHT, x, top - RULER_HEIGHT + 9f);
            if (!curveView) {
                shapes.setColor(skin.getColor("border-muted"));
                shapes.line(x, 0f, x, rowsTop());
                shapes.setColor(skin.getColor("text-muted"));
            }
        }
    }

    private void drawText(Batch batch, TimelineDocument document) {
        BitmapFont font = skin.getFont("default");
        TimelineLayout layout = timeLayout();
        float baseX = getX();
        float baseY = getY();
        font.setColor(skin.getColor("text-muted"));
        float step = layout.labelStep();
        for (float t = 0f; t <= document.getClip().getDuration() + 1e-4f; t += step) {
            font.draw(batch, format(t), baseX + layout.xOf(t) + 3f, baseY + getHeight() - 4f);
        }
        if (curveView) {
            String name = selectedTrack == null ? "Select a track to see its curve"
                : ClipSampler.describe(selectedTrack, session.getProperties());
            font.setColor(skin.getColor("text-primary"));
            font.draw(batch, name, baseX + 6f, baseY + rowsTop() - 6f);
            if (selectedTrack != null) {
                font.setColor(skin.getColor("text-muted"));
                font.draw(batch, format(curveMax), baseX + 6f, baseY + rowsTop() - 26f);
                font.draw(batch, format(curveMin), baseX + 6f, baseY + 20f);
            }
            return;
        }
        Entity root = session.getRootEntity();
        List<String> problems = root == null ? List.of() : ClipSampler.problems(document.getClip(), root, session.getProperties());
        for (int i = scrollRows; i < rows.size(); i++) {
            float y = rowCenterY(i);
            if (y < -ROW_HEIGHT) {
                break;
            }
            Row row = rows.get(i);
            String text;
            if (row.isHeading()) {
                text = row.path().isEmpty() ? (root != null ? root.getName() : "Self") : row.path();
                font.setColor(skin.getColor("text-primary"));
                font.draw(batch, text, baseX + 6f, baseY + y + 6f, GUTTER_WIDTH - 10f, Align.left, true);
                continue;
            }
            AnimatedProperty property = session.getProperties().get(row.track().getPropertyId());
            text = property != null ? property.displayName() : row.track().getPropertyId();
            String label = ClipSampler.describe(row.track(), session.getProperties());
            boolean broken = problems.stream().anyMatch(p -> p.startsWith(label + ":"));
            font.setColor(broken ? skin.getColor("error") : skin.getColor("text-secondary"));
            font.draw(batch, text, baseX + INDENT, baseY + y + 6f, GUTTER_WIDTH - INDENT - 4f,
                Align.left, true);
        }
    }

    private void diamond(float x, float y, float half) {
        shapes.triangle(x - half, y, x, y + half, x + half, y);
        shapes.triangle(x - half, y, x, y - half, x + half, y);
    }

    private static float clamp(float time, AnimationClip clip) {
        return clip.snapToFrame(Math.max(0f, Math.min(clip.getDuration(), time)));
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, Math.abs(value) >= 100f ? "%.0f" : "%.2f", value);
    }

    // --- Input ---

    private KeyRef keyAt(int rowIndex, float x) {
        if (rowIndex < 0 || rows.get(rowIndex).isHeading()) {
            return null;
        }
        AnimationTrack track = rows.get(rowIndex).track();
        TimelineLayout layout = timeLayout();
        for (Keyframe key : track.getCurve().getKeys()) {
            if (Math.abs(layout.xOf(key.time()) - x) <= KEY_HALF_SIZE + KEY_HIT_SLOP) {
                return new KeyRef(track, key.time());
            }
        }
        return null;
    }

    private KeyRef curveKeyAt(float x, float y) {
        if (selectedTrack == null) {
            return null;
        }
        TimelineLayout layout = timeLayout();
        for (Keyframe key : selectedTrack.getCurve().getKeys()) {
            if (Math.abs(layout.xOf(key.time()) - x) <= CURVE_POINT_HALF_SIZE + KEY_HIT_SLOP
                && Math.abs(curveY(key.value()) - y) <= CURVE_POINT_HALF_SIZE + KEY_HIT_SLOP) {
                return new KeyRef(selectedTrack, key.time());
            }
        }
        return null;
    }

    private void showTrackMenu(AnimationTrack track, float stageX, float stageY) {
        trackMenu.show(getStage(), stageX, stageY, List.of(
            new ContextMenu.Item("Remove Track", null, true, () -> session.getDocument().removeTrack(track))));
    }

    /** Keys the current value of a track's property at {@code time} (double-click in a row). */
    private void keyCurrentValue(AnimationTrack track, float time) {
        Entity root = session.getRootEntity();
        Entity target = root == null ? null : track.resolveTarget(root);
        AnimatedProperty property = session.getProperties().get(track.getPropertyId());
        if (target != null && property != null && property.isAvailableOn(target)) {
            session.getDocument().setKey(track, time, property.get(target));
        } else if (!track.getCurve().isEmpty()) {
            session.getDocument().setKey(track, time, track.getCurve().evaluate(time));
        }
    }

    private class CanvasInput extends InputListener {
        private long lastClickMillis;
        private float lastClickX;
        private float lastClickY;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (getStage() != null) {
                getStage().setKeyboardFocus(TimelineCanvas.this);
                getStage().setScrollFocus(TimelineCanvas.this);
            }
            TimelineDocument document = session.getDocument();
            if (document == null) {
                return false;
            }
            TimelineLayout layout = timeLayout();
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            long now = TimeUtils.millis();
            boolean doubleClick = button == Input.Buttons.LEFT && now - lastClickMillis <= DOUBLE_CLICK_MILLIS
                && Math.abs(x - lastClickX) <= DOUBLE_CLICK_SLOP && Math.abs(y - lastClickY) <= DOUBLE_CLICK_SLOP;
            lastClickMillis = doubleClick ? 0L : now;
            lastClickX = x;
            lastClickY = y;
            if (y > rowsTop()) {
                if (button == Input.Buttons.LEFT && x > GUTTER_WIDTH) {
                    drag = Drag.SCRUB;
                    session.setTime(layout.timeAt(x));
                }
                return true;
            }
            if (curveView) {
                if (button != Input.Buttons.LEFT) {
                    return true;
                }
                KeyRef hit = curveKeyAt(x, y);
                if (hit != null) {
                    selection.clear();
                    selection.add(hit);
                    curveKey = hit;
                    curveDragTime = hit.time();
                    curveDragValue = hit.resolve().value();
                    drag = Drag.CURVE_KEY;
                } else if (x > GUTTER_WIDTH && doubleClick && selectedTrack != null) {
                    document.setKey(selectedTrack, layout.timeAt(x), curveValueAt(y));
                }
                return true;
            }
            int rowIndex = rowAt(y);
            Row row = rowIndex >= 0 ? rows.get(rowIndex) : null;
            if (row != null && !row.isHeading()) {
                selectedTrack = row.track();
            }
            if (x <= GUTTER_WIDTH) {
                if (button == Input.Buttons.RIGHT && row != null && !row.isHeading()) {
                    Vector2 stage = localToStageCoordinates(new Vector2(x, y));
                    showTrackMenu(row.track(), stage.x, stage.y);
                }
                return true;
            }
            if (button != Input.Buttons.LEFT) {
                return true;
            }
            KeyRef hit = keyAt(rowIndex, x);
            if (hit != null) {
                if (shift) {
                    if (!selection.remove(hit)) {
                        selection.add(hit);
                    }
                } else if (!selection.contains(hit)) {
                    selection.clear();
                    selection.add(hit);
                }
                drag = Drag.MOVE_KEYS;
                dragStartTime = layout.timeAt(x);
                dragDeltaTime = 0f;
                return true;
            }
            if (!shift) {
                selection.clear();
            }
            if (row != null && !row.isHeading() && doubleClick) {
                keyCurrentValue(row.track(), layout.timeAt(x));
            }
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (session.getDocument() == null) {
                return;
            }
            TimelineLayout layout = timeLayout();
            switch (drag) {
                case SCRUB -> session.setTime(layout.timeAt(x));
                case MOVE_KEYS -> {
                    AnimationClip clip = session.getDocument().getClip();
                    dragDeltaTime = clip.snapToFrame(layout.timeAt(x) - dragStartTime);
                }
                case CURVE_KEY -> {
                    curveDragTime = session.getDocument().snap(layout.timeAt(x));
                    curveDragValue = curveValueAt(y);
                }
                default -> {
                }
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            TimelineDocument document = session.getDocument();
            if (document != null) {
                if (drag == Drag.MOVE_KEYS && dragDeltaTime != 0f && !selection.isEmpty()) {
                    List<KeyRef> moved = document.moveKeys(new ArrayList<>(selection), dragDeltaTime);
                    selection.clear();
                    selection.addAll(moved);
                } else if (drag == Drag.CURVE_KEY && curveKey != null) {
                    KeyRef edited = document.editKey(curveKey, curveDragTime, curveDragValue);
                    if (edited != null) {
                        selection.clear();
                        selection.add(edited);
                    }
                }
            }
            drag = Drag.NONE;
            dragDeltaTime = 0f;
            curveKey = null;
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            scrollRows = Math.max(0, Math.min(Math.max(0, rows.size() - 1), scrollRows + Math.round(amountY)));
            return true;
        }

        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            TimelineDocument document = session.getDocument();
            if (document == null) {
                return false;
            }
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (keycode == Input.Keys.FORWARD_DEL || keycode == Input.Keys.DEL) {
                if (!selection.isEmpty()) {
                    document.deleteKeys(new ArrayList<>(selection));
                    selection.clear();
                }
                return true;
            }
            if (ctrl && keycode == Input.Keys.Z) {
                document.undo();
                return true;
            }
            if (ctrl && keycode == Input.Keys.Y) {
                document.redo();
                return true;
            }
            if (keycode == Input.Keys.SPACE) {
                session.setPlaying(!session.isPlaying());
                return true;
            }
            return false;
        }
    }

    @Override
    public float getPrefHeight() {
        return RULER_HEIGHT + ROW_HEIGHT * 6;
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
