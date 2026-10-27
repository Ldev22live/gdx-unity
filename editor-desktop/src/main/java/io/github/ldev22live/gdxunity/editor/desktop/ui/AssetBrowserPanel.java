package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetBrowserFilterModel;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;

import java.util.List;
import java.util.Objects;

/**
 * Editor panel for discovering and selecting project assets (textures/images).
 * Uses AssetCatalog for recursive filesystem scanning without eager GPU decoding,
 * and provides live search filtering.
 */
public class AssetBrowserPanel extends Table {
    private final EditorState editorState;
    private final AssetCatalog catalog;
    private final AssetBrowserFilterModel filterModel;
    private final Skin skin;

    private final Table listTable;
    private final ScrollPane scrollPane;
    private final Label statusCountLabel;
    private final TextField searchField;

    public AssetBrowserPanel(EditorState editorState, AssetCatalog catalog, Skin skin) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
        this.filterModel = new AssetBrowserFilterModel(catalog);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");

        setBackground("panel-bg");
        top().left();

        // Header bar with Title, Count, Search, and Refresh button
        Table header = new Table(skin);
        header.setBackground("subheader-bg");
        header.left();

        Label titleLabel = new Label("Project Assets", skin);
        if (skin.has("text-primary", Color.class)) {
            titleLabel.setColor(skin.getColor("text-primary"));
        } else {
            titleLabel.setColor(new Color(0.92f, 0.92f, 0.95f, 1f));
        }
        header.add(titleLabel).padLeft(8).left();

        this.statusCountLabel = new Label("", skin, "muted");
        header.add(statusCountLabel).padLeft(6).left();

        // Breadcrumb
        Label pathLabel = new Label("Assets /", skin, "muted");
        if (skin.has("accent", Color.class)) {
            pathLabel.setColor(skin.getColor("accent"));
        } else {
            pathLabel.setColor(new Color(0.35f, 0.65f, 1.0f, 1.0f));
        }
        header.add(pathLabel).padLeft(10).left();

        // Search Field
        this.searchField = new TextField("", skin);
        searchField.setMessageText("Filter assets...");
        searchField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                filterModel.setSearchFilter(searchField.getText());
                rebuildRows();
            }
        });
        header.add(searchField).width(160).height(20).padLeft(12);

        // Fill space
        Table spacer = new Table();
        header.add(spacer).expandX();

        TextButton refreshBtn = new TextButton("Refresh", skin);
        refreshBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                refreshCatalog();
            }
        });
        header.add(refreshBtn).right().padRight(6).height(20);
        add(header).growX().height(26).row();

        // Asset List inside ScrollPane
        this.listTable = new Table(skin);
        this.listTable.top().left();
        this.scrollPane = new ScrollPane(listTable, skin);
        this.scrollPane.setFadeScrollBars(false);
        add(scrollPane).grow().pad(4).row();

        // Listen for selection changes to update highlight
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onSelectedAssetChanged(AssetReference previousAsset, AssetReference newAsset) {
                rebuildRows();
            }
        });

        refreshCatalog();
    }

    public void refreshCatalog() {
        catalog.scan();
        rebuildRows();
    }

    private void rebuildRows() {
        listTable.clear();
        List<AssetMetadata> assets = filterModel.getFilteredAssets();
        int total = filterModel.getTotalCount();
        if (assets.size() == total) {
            statusCountLabel.setText("(" + total + " items)");
        } else {
            statusCountLabel.setText("(" + assets.size() + "/" + total + " items)");
        }

        if (assets.isEmpty()) {
            String msg = (total == 0)
                ? "No supported assets found in: " + catalog.getRootDir().getPath()
                : "No assets match filter '" + filterModel.getSearchFilter() + "'";
            Label emptyLbl = new Label(msg, skin, "muted");
            listTable.add(emptyLbl).pad(10).left().row();
            return;
        }

        AssetReference currentSelection = editorState.getSelectedAsset();

        for (AssetMetadata meta : assets) {
            final AssetReference ref = meta.reference();
            boolean isSelected = ref.equals(currentSelection);

            Table row = new Table(skin);
            row.left();

            // Type badge
            Label badge = new Label("[" + meta.type().name() + "]", skin, "muted");
            row.add(badge).width(75).left().padLeft(4);

            // Asset name & path
            String displayName = ref.getPath();
            String sizeStr = String.format(" (%.1f KB)", meta.fileSizeBytes() / 1024.0f);
            TextButton itemBtn = new TextButton(displayName + sizeStr, skin, "flat");
            itemBtn.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);

            if (isSelected) {
                Color accentColor = skin.has("accent", Color.class) ? skin.getColor("accent") : new Color(0.24f, 0.49f, 0.90f, 1.0f);
                itemBtn.setColor(accentColor);
            } else {
                Color textColor = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : Color.WHITE;
                itemBtn.setColor(textColor);
            }

            itemBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    editorState.setSelectedAsset(ref);
                    editorState.setStatusMessage("Selected asset: " + ref.getPath(), false);
                }
            });

            row.add(itemBtn).growX().height(22).left();
            listTable.add(row).growX().padBottom(1).row();
        }
    }
}
