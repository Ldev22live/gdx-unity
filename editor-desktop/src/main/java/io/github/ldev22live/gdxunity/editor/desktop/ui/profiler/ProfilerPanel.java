package io.github.ldev22live.gdxunity.editor.desktop.ui.profiler;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.profile.ProfilerReport;
import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Profiler tab (roadmap M9.6): where the editor's frames go. A history graph of recent frames, and for the frame picked in it a
 * breakdown: Play (2D and 3D physics, then each component type's updates, scripts by their own class name), the scene view (3D
 * scene, 2D scene, overlays), the editor UI, and what was not timed, each with its draw calls, vertices, texture binds and shader
 * switches.
 * <p>
 * The editor records only while this tab is shown. Pause freezes the history to study one frame; clicking a bar pauses and picks it.
 * Counting draw calls wraps every GL call, which makes frames slower; it can be switched off to see the times without that cost.
 */
public final class ProfilerPanel extends Table implements Disposable {
    /** How often the breakdown is rebuilt while recording, so the numbers can be read. */
    private static final float REFRESH_SECONDS = 0.25f;
    private static final float NAME_WIDTH = 260f;
    private static final float NUMBER_WIDTH = 78f;
    private static final float INDENT = 14f;
    private static final float BUTTON_HEIGHT = 22f;
    /** Space on each side of a header button's text, so the flat buttons do not run together. */
    private static final float BUTTON_PAD = 8f;
    /** Counter columns are wider: their headings are two words. */
    private static final float COUNTER_WIDTH = 120f;

    private final FrameProfiler profiler;
    private final Skin skin;
    private final FrameHistoryGraph graph;
    private final Table rowsTable;
    private final Label summaryLabel;
    private final TextButton pauseButton;
    private final TextButton countButton;
    private boolean countDrawCalls = true;
    /** The frame shown, in frames ago from the newest; 0 while following the newest. */
    private int selectedFrame;
    private float sinceRefresh = REFRESH_SECONDS;
    private boolean refreshNow;

    public ProfilerPanel(FrameProfiler profiler, Skin skin) {
        super(skin);
        this.profiler = Objects.requireNonNull(profiler, "profiler cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        setBackground("panel-bg");
        top().left();

        Table header = new Table(skin);
        header.setBackground("subheader-bg");
        header.left();
        pauseButton = button("Pause", this::togglePause);
        header.add(pauseButton).height(BUTTON_HEIGHT).padLeft(6).padRight(2);
        header.add(button("Newest", this::showNewest)).height(BUTTON_HEIGHT).padRight(2);
        countButton = button("", this::toggleCounting);
        header.add(countButton).height(BUTTON_HEIGHT).padRight(2);
        header.add(button("Copy frame", this::copyFrame)).height(BUTTON_HEIGHT).padRight(8);
        summaryLabel = new Label("", skin, "muted");
        header.add(summaryLabel).growX().left();
        add(header).growX().height(28).row();

        graph = new FrameHistoryGraph(skin, profiler, () -> selectedFrame, this::pickFrame);
        add(graph).growX().height(graph.getPrefHeight()).pad(4, 4, 2, 4).row();

        rowsTable = new Table(skin);
        rowsTable.top().left();
        ScrollPane scroll = new ScrollPane(rowsTable, skin);
        scroll.setFadeScrollBars(false);
        add(scroll).grow().pad(0, 4, 4, 4);
        updateButtons();
    }

    private TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.pad(0f, BUTTON_PAD, 0f, BUTTON_PAD);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /** True when draw calls and the other GL counts should be counted while recording. */
    public boolean isCountingDrawCalls() {
        return countDrawCalls;
    }

    /** The frame the breakdown shows, in frames ago. */
    public int getSelectedFrame() {
        return selectedFrame;
    }

    private void togglePause() {
        profiler.setPaused(!profiler.isPaused());
        if (!profiler.isPaused()) {
            selectedFrame = 0;
        }
        updateButtons();
        refreshNow = true;
    }

    private void showNewest() {
        selectedFrame = 0;
        refreshNow = true;
    }

    private void toggleCounting() {
        countDrawCalls = !countDrawCalls;
        updateButtons();
    }

    private void pickFrame(int framesAgo) {
        profiler.setPaused(true);
        selectedFrame = framesAgo;
        updateButtons();
        refreshNow = true;
    }

    private void copyFrame() {
        if (profiler.getFrameCount() == 0 || Gdx.app == null) {
            return;
        }
        Gdx.app.getClipboard().setContents(ProfilerReport.toText(profiler, shownFrame(), ProfilerReport.DEFAULT_AVERAGE_FRAMES));
    }

    private void updateButtons() {
        pauseButton.setText(profiler.isPaused() ? "Resume" : "Pause");
        countButton.setText(countDrawCalls ? "Draw calls: counted" : "Draw calls: off");
    }

    private int shownFrame() {
        return Math.min(selectedFrame, profiler.getFrameCount() - 1);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        sinceRefresh += delta;
        boolean live = profiler.isRecording() && !profiler.isPaused();
        if (refreshNow || (live && sinceRefresh >= REFRESH_SECONDS)) {
            refreshNow = false;
            sinceRefresh = 0f;
            rebuild();
        }
    }

    /** Rebuilds the summary and the rows for the shown frame. */
    private void rebuild() {
        rowsTable.clear();
        if (profiler.getFrameCount() == 0) {
            summaryLabel.setText(profiler.isRecording() ? "Recording..." : "Not recording");
            return;
        }
        int frame = shownFrame();
        ProfilerReport.Summary summary = ProfilerReport.summary(profiler, frame, ProfilerReport.DEFAULT_AVERAGE_FRAMES);
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
            "%s: %.2f ms work, %.2f ms to the next frame (%.0f fps)  |  average %.2f ms, slowest %.2f ms of the last %d",
            frame == 0 ? "Newest frame" : "Frame " + frame + " back", summary.frameMillis(), summary.periodMillis(),
            summary.framesPerSecond(), summary.averageMillis(), summary.slowestMillis(), summary.frames()));
        for (int v = 0; v < summary.values().length; v++) {
            text.append("  |  ").append(profiler.getValueName(v)).append(' ').append(summary.values()[v]);
        }
        if (profiler.getCounterCount() > 0) {
            text.append("  |  times include counting draw calls");
        }
        summaryLabel.setText(text);

        int counters = profiler.getCounterCount();
        rowsTable.add(header("Section", Align.left)).width(NAME_WIDTH).left();
        rowsTable.add(header("ms", Align.right)).width(NUMBER_WIDTH);
        rowsTable.add(header("average", Align.right)).width(NUMBER_WIDTH);
        rowsTable.add(header("% frame", Align.right)).width(NUMBER_WIDTH);
        rowsTable.add(header("times", Align.right)).width(NUMBER_WIDTH);
        for (int c = 0; c < counters; c++) {
            rowsTable.add(header(profiler.getCounterName(c), Align.right)).width(COUNTER_WIDTH);
        }
        rowsTable.row();
        List<ProfilerReport.Row> rows = ProfilerReport.rows(profiler, frame, ProfilerReport.DEFAULT_AVERAGE_FRAMES);
        for (ProfilerReport.Row row : rows) {
            String style = row.isRemainder() ? "muted" : row.depth() == 0 ? "default" : "secondary";
            rowsTable.add(cell(row.name(), style, Align.left)).width(NAME_WIDTH).left().padLeft(row.depth() * INDENT);
            rowsTable.add(cell(String.format(Locale.ROOT, "%.3f", row.millis()), style, Align.right)).width(NUMBER_WIDTH);
            rowsTable.add(cell(row.isRemainder() ? "" : String.format(Locale.ROOT, "%.3f", row.averageMillis()), style, Align.right))
                .width(NUMBER_WIDTH);
            rowsTable.add(cell(String.format(Locale.ROOT, "%.1f", row.percent()), style, Align.right)).width(NUMBER_WIDTH);
            rowsTable.add(cell(row.isRemainder() ? "" : Integer.toString(row.calls()), style, Align.right)).width(NUMBER_WIDTH);
            for (int c = 0; c < counters; c++) {
                String count = row.isRemainder() ? "" : String.format(Locale.ROOT, "%,d", row.counts()[c]);
                rowsTable.add(cell(count, style, Align.right)).width(COUNTER_WIDTH);
            }
            rowsTable.row();
        }
    }

    private Label header(String text, int align) {
        Label label = new Label(text, skin, "muted");
        label.setAlignment(align);
        return label;
    }

    private Label cell(String text, String style, int align) {
        Label label = new Label(text, skin, style);
        label.setAlignment(align);
        return label;
    }

    @Override
    public void dispose() {
        graph.dispose();
    }
}
