package io.github.ldev22live.gdxunity.editor.desktop.ui.workspace;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeManager;
import io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode;
import io.github.ldev22live.gdxunity.editor.core.workspace.StudioWorkspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Top menu & studio navigation bar structured like Unity's top bar:
 * Brand title + Unity-style standard menus (File, Edit, Assets, GameObject, Component, View, Window, Help)
 * followed by creative workspace tabs and theme mode toggle button.
 */
public class StudioNavigationBar extends Table {
    public interface MenuActionListener {
        void onMenuAction(String menuName);
    }

    private final WorkspaceManager workspaceManager;
    private final EditorThemeManager themeManager;
    private final Skin skin;
    private final Map<String, TextButton> navButtons = new LinkedHashMap<>();
    private MenuActionListener menuActionListener;
    private TextButton themeToggleBtn;

    public StudioNavigationBar(WorkspaceManager workspaceManager, Skin skin) {
        this(workspaceManager, null, skin, null);
    }

    public StudioNavigationBar(WorkspaceManager workspaceManager, Skin skin, MenuActionListener menuActionListener) {
        this(workspaceManager, null, skin, menuActionListener);
    }

    public StudioNavigationBar(WorkspaceManager workspaceManager, EditorThemeManager themeManager, Skin skin, MenuActionListener menuActionListener) {
        super(skin);
        this.workspaceManager = Objects.requireNonNull(workspaceManager, "workspaceManager cannot be null");
        this.themeManager = themeManager;
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.menuActionListener = menuActionListener;

        setBackground("header-bg");
        left();

        // 1. App brand badge
        Label brandLabel = new Label(" GdxUnity ", skin);
        if (skin.has("accent", Color.class)) {
            brandLabel.setColor(skin.getColor("accent"));
        } else {
            brandLabel.setColor(new Color(0.35f, 0.65f, 1.0f, 1.0f));
        }
        add(brandLabel).padLeft(6).padRight(4);

        // 2. Standard Top Menu Bar items (Unity standard)
        String[] menuItems = {"File", "Edit", "Assets", "GameObject", "Component", "View", "Window", "Help"};
        for (String item : menuItems) {
            TextButton menuBtn = new TextButton(item, skin, "flat");
            menuBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (menuActionListener != null) {
                        menuActionListener.onMenuAction(item);
                    }
                }
            });
            add(menuBtn).height(22).padLeft(1).padRight(1);
        }

        // 3. Separator
        Label sep = new Label("|", skin, "muted");
        add(sep).padLeft(8).padRight(8);

        // 4. Workspace tabs container
        rebuildButtons();

        workspaceManager.addListener(new WorkspaceManager.WorkspaceListener() {
            @Override
            public void onWorkspaceChanged(StudioWorkspace previous, StudioWorkspace active) {
                updateActiveButtonHighlight();
            }
        });
    }

    public void setMenuActionListener(MenuActionListener listener) {
        this.menuActionListener = listener;
    }

    public void rebuildButtons() {
        navButtons.clear();

        for (StudioWorkspace ws : workspaceManager.getRegisteredWorkspaces()) {
            String title = ws.getDisplayName();
            TextButton btn = new TextButton(title, skin, "flat");
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    workspaceManager.setActiveWorkspace(ws.getId());
                }
            });
            navButtons.put(ws.getId(), btn);
            add(btn).height(22).padLeft(2).padRight(2);
        }

        // Fill remaining space
        Table spacer = new Table();
        add(spacer).expandX();

        // 5. Right-aligned theme toggle
        if (themeManager != null) {
            String currentThemeName = themeManager.getCurrentMode() == EditorThemeMode.DARK ? "Theme: Dark" : "Theme: Light";
            themeToggleBtn = new TextButton(currentThemeName, skin, "flat");
            themeToggleBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (menuActionListener != null) {
                        menuActionListener.onMenuAction("ToggleTheme");
                    }
                }
            });
            add(themeToggleBtn).height(22).padRight(8);
        }

        updateActiveButtonHighlight();
    }

    private void updateActiveButtonHighlight() {
        StudioWorkspace active = workspaceManager.getActiveWorkspace();
        String activeId = (active != null) ? active.getId() : null;
        Color accentColor = skin.has("accent", Color.class) ? skin.getColor("accent") : new Color(0.35f, 0.65f, 1.0f, 1.0f);
        Color textColor = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : Color.WHITE;

        for (Map.Entry<String, TextButton> entry : navButtons.entrySet()) {
            boolean isActive = Objects.equals(entry.getKey(), activeId);
            if (isActive) {
                entry.getValue().setColor(accentColor);
            } else {
                entry.getValue().setColor(textColor);
            }
        }
    }
}
