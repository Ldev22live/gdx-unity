package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog.Severity;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Output Log panel: editor, script and Play-mode messages with a severity filter.
 */
public class OutputLogPanel extends Table {

    /** Only the newest entries are laid out, to keep rebuilding cheap. */
    private static final int MAX_VISIBLE_ENTRIES = 400;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EditorLog log;
    private final Skin skin;
    private final Table listTable;
    private final ScrollPane scrollPane;
    private final Label countLabel;
    private Severity minimumSeverity = Severity.INFO;
    private boolean refreshPending;

    public OutputLogPanel(EditorLog log, Skin skin) {
        super(skin);
        this.log = Objects.requireNonNull(log, "log cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        setBackground("panel-bg");
        top().left();

        Table header = new Table(skin);
        header.setBackground("subheader-bg");
        header.left();
        countLabel = new Label("", skin, "muted");
        header.add(countLabel).padLeft(8);
        header.add(new Table()).expandX();
        header.add(filterButton("All", Severity.INFO)).height(22).padRight(2);
        header.add(filterButton("Warnings", Severity.WARNING)).height(22).padRight(2);
        header.add(filterButton("Errors", Severity.ERROR)).height(22).padRight(8);
        TextButton clear = new TextButton("Clear", skin);
        clear.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                log.clear();
            }
        });
        header.add(clear).height(22).padRight(6);
        add(header).growX().height(28).row();

        listTable = new Table(skin);
        listTable.top().left();
        scrollPane = new ScrollPane(listTable, skin);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).grow().pad(4);

        // Log changes can come from background threads (e.g. the AI assistant); rebuild on the render thread
        log.addListener(changed -> scheduleRefresh());
        refresh();
    }

    private TextButton filterButton(String text, Severity severity) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                minimumSeverity = severity;
                refresh();
            }
        });
        return button;
    }

    private void scheduleRefresh() {
        if (Gdx.app == null) {
            refresh();
            return;
        }
        if (!refreshPending) {
            refreshPending = true;
            Gdx.app.postRunnable(() -> {
                refreshPending = false;
                refresh();
            });
        }
    }

    private void refresh() {
        List<EditorLog.Entry> entries = log.getEntries(minimumSeverity);
        countLabel.setText(entries.size() + " message" + (entries.size() == 1 ? "" : "s"));
        listTable.clear();
        int start = Math.max(0, entries.size() - MAX_VISIBLE_ENTRIES);
        for (int i = start; i < entries.size(); i++) {
            EditorLog.Entry entry = entries.get(i);
            String text = TIME_FORMAT.format(entry.time()) + "  [" + entry.source() + "]  " + entry.message();
            Label line = new Label(text, skin, styleFor(entry.severity()));
            line.setWrap(true);
            listTable.add(line).growX().left().padLeft(4).padBottom(1).row();
        }
        scrollPane.layout();
        scrollPane.setScrollPercentY(1f);
    }

    private static String styleFor(Severity severity) {
        return switch (severity) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "default";
        };
    }
}
