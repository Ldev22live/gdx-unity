package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetBrowserFilterModel;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Editor panel for discovering and selecting project assets (textures and models). Rows can be dragged
 * into the scene view when the panel is given a shared {@link DragAndDrop}.
 * Uses AssetCatalog for recursive filesystem scanning without eager GPU decoding,
 * and provides live search filtering.
 */
public class AssetBrowserPanel extends Table {
    /** Width of the type badge column: wide enough for the longest type name, "[ANIMATION]", so it does not run into the path. */
    private static final float TYPE_BADGE_WIDTH = 100f;
    /** Side of the square thumbnail shown in front of a texture's name. */
    private static final float THUMBNAIL_SIZE = 22f;
    /** Textures larger than this are not loaded just to draw a thumbnail. */
    private static final long THUMBNAIL_MAX_BYTES = 4L * 1024 * 1024;
    /** Thumbnails loaded per rebuild of the list, so opening a folder of hundreds of textures does not stall the editor. */
    private static final int THUMBNAILS_PER_FOLDER = 64;
    /** Models drawn per frame: each needs its file loaded, so one at a time keeps the editor responsive. */
    private static final int MODEL_THUMBNAILS_PER_FRAME = 1;
    private final EditorState editorState;
    private final AssetCatalog catalog;
    private final AssetBrowserFilterModel filterModel;
    private final Skin skin;

    private final Table listTable;
    private final Table breadcrumbBar;
    private ModelThumbnails modelThumbnails;
    private SpriteThumbnails spriteThumbnails;
    private final ScrollPane scrollPane;
    private final Label statusCountLabel;
    private final TextField searchField;
    /** Shared with the scene view so assets can be dragged into it; null disables dragging. */
    private final DragAndDrop dragAndDrop;
    /** Called when a row is double-clicked; null when assets cannot be opened. */
    private Consumer<AssetReference> onOpen;

    /** Sets what double-clicking an asset does (for example opening a graph). */
    public void setOnOpen(Consumer<AssetReference> onOpen) {
        this.onOpen = onOpen;
    }

    public AssetBrowserPanel(EditorState editorState, AssetCatalog catalog, Skin skin) {
        this(editorState, catalog, skin, null);
    }

    /**
     * @param dragAndDrop drag-and-drop instance shared with drop targets such as the scene view; may be null
     */
    public AssetBrowserPanel(EditorState editorState, AssetCatalog catalog, Skin skin, DragAndDrop dragAndDrop) {
        super(skin);
        this.dragAndDrop = dragAndDrop;
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

        Label titleLabel = new Label("Content Browser", skin);
        if (skin.has("text-primary", Color.class)) {
            titleLabel.setColor(skin.getColor("text-primary"));
        } else {
            titleLabel.setColor(new Color(0.92f, 0.92f, 0.95f, 1f));
        }
        header.add(titleLabel).padLeft(8).left();

        this.statusCountLabel = new Label("", skin, "muted");
        header.add(statusCountLabel).padLeft(6).left();

        // Breadcrumb: Assets / models / props, each part opens that folder
        this.breadcrumbBar = new Table(skin);
        header.add(breadcrumbBar).padLeft(10).left();

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
        header.add(searchField).width(160).height(22).padLeft(12);

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
        header.add(refreshBtn).right().padRight(6).height(22);
        add(header).growX().height(30).row();

        // Asset List inside ScrollPane
        this.listTable = new Table(skin);
        this.listTable.top().left();
        this.scrollPane = new ScrollPane(listTable, skin);
        this.scrollPane.setFadeScrollBars(false);
        // Desktop: the wheel scrolls; a mouse drag on a row starts an asset drag instead of scrolling
        this.scrollPane.setFlickScroll(false);
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
        filterModel.revalidate();
        rebuildRows();
    }

    /** The files on disk changed (see {@code AssetChangeWatcher}): the list, and the folder being browsed, are brought up to date. */
    public void onAssetsChanged() {
        filterModel.revalidate();
        rebuildRows();
    }

    /** Browses the folder at {@code path} (project relative, "" for the root). */
    public void showFolder(String path) {
        openFolder(path);
    }

    private void openFolder(String path) {
        filterModel.setCurrentFolder(path);
        rebuildRows();
    }

    private void rebuildBreadcrumb() {
        breadcrumbBar.clear();
        Color accent = skin.has("accent", Color.class) ? skin.getColor("accent") : new Color(0.35f, 0.65f, 1.0f, 1.0f);
        StringBuilder path = new StringBuilder();
        addCrumb("Assets", "", accent);
        for (String name : filterModel.getBreadcrumb()) {
            path.append(path.length() == 0 ? "" : "/").append(name);
            breadcrumbBar.add(new Label("/", skin, "muted"));
            addCrumb(name, path.toString(), accent);
        }
    }

    private void addCrumb(String text, String folder, Color color) {
        TextButton crumb = new TextButton(text, skin, "flat");
        crumb.getLabel().setColor(color);
        crumb.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openFolder(folder);
            }
        });
        breadcrumbBar.add(crumb).height(22);
    }

    private void rebuildRows() {
        listTable.clear();
        rebuildBreadcrumb();
        List<AssetMetadata> assets = filterModel.getFilteredAssets();
        List<AssetBrowserFilterModel.Folder> folders = filterModel.getSubfolders();
        int total = filterModel.getTotalCount();
        if (assets.size() == total) {
            statusCountLabel.setText("(" + total + " items)");
        } else {
            statusCountLabel.setText("(" + assets.size() + "/" + total + " items)");
        }

        addFolderRows(folders);
        if (assets.isEmpty() && folders.isEmpty()) {
            String msg = (total == 0)
                ? "No supported assets found in: " + catalog.getRootDir().getPath()
                : "No assets match filter '" + filterModel.getSearchFilter() + "'";
            Label emptyLbl = new Label(msg, skin, "muted");
            listTable.add(emptyLbl).pad(10).left().row();
            return;
        }

        AssetReference currentSelection = editorState.getSelectedAsset();
        int thumbnails = 0;

        for (AssetMetadata meta : assets) {
            final AssetReference ref = meta.reference();
            boolean isSelected = ref.equals(currentSelection);

            Table row = new Table(skin);
            row.left();

            // Type badge
            Label badge = new Label("[" + meta.type().name() + "]", skin, "muted");
            row.add(badge).width(TYPE_BADGE_WIDTH).left().padLeft(4);
            TextureRegion thumbnail = thumbnails < THUMBNAILS_PER_FOLDER ? thumbnailOf(meta) : null;
            if (thumbnail != null) {
                thumbnails++;
                Image picture = new Image(thumbnail);
                picture.setScaling(Scaling.fit);
                row.add(picture).size(THUMBNAIL_SIZE).padRight(4);
            } else {
                row.add().size(THUMBNAIL_SIZE).padRight(4);
            }

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
            itemBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (getTapCount() == 2 && onOpen != null) {
                        onOpen.accept(ref);
                    }
                }
            });

            if (dragAndDrop != null) {
                dragAndDrop.addSource(new DragAndDrop.Source(itemBtn) {
                    @Override
                    public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                        DragAndDrop.Payload payload = new DragAndDrop.Payload();
                        payload.setObject(ref);
                        payload.setDragActor(new Label("  " + ref.getFileName(), skin));
                        return payload;
                    }
                });
            }

            row.add(itemBtn).growX().height(22).left();
            listTable.add(row).growX().padBottom(1).row();
        }
    }

    /** The ".." row and one row per subfolder; a click opens it. */
    private void addFolderRows(List<AssetBrowserFilterModel.Folder> folders) {
        if (!filterModel.getCurrentFolder().isEmpty() && filterModel.getSearchFilter().isEmpty()) {
            TextButton up = new TextButton(".. (up)", skin, "flat");
            up.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            up.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    filterModel.up();
                    rebuildRows();
                }
            });
            listTable.add(up).growX().height(22).padLeft(4).padBottom(1).row();
        }
        for (AssetBrowserFilterModel.Folder folder : folders) {
            TextButton open = new TextButton("[FOLDER]  " + folder.name() + "  (" + folder.assetCount() + ")", skin, "flat");
            open.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            open.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    openFolder(folder.path());
                }
            });
            listTable.add(open).growX().height(22).padLeft(4).padBottom(1).row();
        }
    }

    /**
     * The picture to show for an asset, or null: a texture's own image, or a model's thumbnail once it has been drawn (it is asked for
     * here and drawn in {@link #act}). Null as well when it is too large, has no asset service, or does not decode.
     */
    private TextureRegion thumbnailOf(AssetMetadata meta) {
        if (meta.type() == AssetType.MODEL) {
            return modelThumbnails != null ? modelThumbnails.get(meta) : null;
        }
        if (meta.type() == AssetType.SPRITE) {
            return spriteThumbnails != null ? spriteThumbnails.get(meta) : null;
        }
        AssetService service = editorState.getAssetService();
        if (service == null || meta.type() != AssetType.TEXTURE || meta.fileSizeBytes() > THUMBNAIL_MAX_BYTES) {
            return null;
        }
        Texture texture = service.loadTexture(meta.reference());
        return texture != null ? new TextureRegion(texture) : null;
    }

    /** Draws the model thumbnails that were asked for, one per frame, and lists the assets again when one is ready. */
    @Override
    public void act(float delta) {
        super.act(delta);
        if (modelThumbnails != null && modelThumbnails.renderPending(MODEL_THUMBNAILS_PER_FRAME)) {
            rebuildRows();
        }
    }

    /** Turns on thumbnails of sprites (null turns them off); the panel does not own the cache. */
    public void setSpriteThumbnails(SpriteThumbnails spriteThumbnails) {
        this.spriteThumbnails = spriteThumbnails;
        rebuildRows();
    }

    /** Turns on thumbnails of models (null turns them off); the panel does not own the cache. */
    public void setModelThumbnails(ModelThumbnails modelThumbnails) {
        this.modelThumbnails = modelThumbnails;
        rebuildRows();
    }
}
