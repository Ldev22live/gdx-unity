package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A row of tabs above one content area; selecting a tab swaps the content. Used for the bottom
 * Content Browser / Output Log panel.
 */
public class TabbedPanel extends Table {

    private static final float TAB_HEIGHT = 26f;

    private final Skin skin;
    private final List<TextButton> tabButtons = new ArrayList<>();
    private final List<Actor> contents;
    private final Container<Actor> contentArea = new Container<>();
    private final IntConsumer onSelected;

    /**
     * @param selectedIndex initially selected tab
     * @param onSelected    called with the tab index whenever the user switches tabs; may be null
     */
    public TabbedPanel(Skin skin, List<String> titles, List<Actor> contents, int selectedIndex, IntConsumer onSelected) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        Objects.requireNonNull(titles, "titles cannot be null");
        this.contents = List.copyOf(Objects.requireNonNull(contents, "contents cannot be null"));
        if (titles.size() != this.contents.size() || titles.isEmpty()) {
            throw new IllegalArgumentException("Need one title per content, got " + titles.size() + " titles and " + this.contents.size() + " contents");
        }
        this.onSelected = onSelected;

        Table tabRow = new Table(skin);
        tabRow.setBackground("subheader-bg");
        tabRow.left();
        for (int i = 0; i < titles.size(); i++) {
            int index = i;
            TextButton tab = new TextButton(titles.get(i), skin, "tab");
            tab.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    select(index);
                    if (TabbedPanel.this.onSelected != null) {
                        TabbedPanel.this.onSelected.accept(index);
                    }
                }
            });
            tabButtons.add(tab);
            tabRow.add(tab).height(TAB_HEIGHT).padLeft(2);
        }

        contentArea.fill();
        add(tabRow).growX().row();
        add(contentArea).grow();
        select(Math.max(0, Math.min(selectedIndex, titles.size() - 1)));
    }

    public void select(int index) {
        contentArea.setActor(contents.get(index));
        for (int i = 0; i < tabButtons.size(); i++) {
            TextButton tab = tabButtons.get(i);
            tab.setProgrammaticChangeEvents(false);
            tab.setChecked(i == index);
            tab.setProgrammaticChangeEvents(true);
            tab.setColor(i == index ? skin.getColor("accent") : skin.getColor("text-primary"));
        }
    }
}
