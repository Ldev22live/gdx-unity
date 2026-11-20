package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Scaling;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.library.AssetProvider;
import io.github.ldev22live.gdxunity.editor.core.asset.library.AssetType;
import io.github.ldev22live.gdxunity.editor.core.asset.library.DownloadOption;
import io.github.ldev22live.gdxunity.editor.core.asset.library.HttpSource;
import io.github.ldev22live.gdxunity.editor.core.asset.library.RemoteAsset;
import io.github.ldev22live.gdxunity.editor.core.asset.library.RemoteAssetInstaller;
import io.github.ldev22live.gdxunity.editor.core.asset.library.ThumbnailCache;
import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssets;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An Asset Library tab for one online provider (UI M5, TD-095): search its catalogue, read an asset's authors, categories and CC0
 * licence, choose a resolution (2K first) with its size, and download it into the project. It goes online only when the tab is
 * opened or a person searches or downloads; all network work runs on a worker thread, and progress is read each frame, so the
 * editor never waits. Offline it shows the cached catalogue and says from when, or says that nothing is cached. The provider's credit
 * ("Powered by Poly Haven") is always shown with its results.
 *
 * <p>UI M7: the chosen asset's preview is shown (fetched when chosen, cached on disk), and once an asset is in the project, whether
 * just downloaded or downloaded before, a model can be placed standing on the ground and a material applied to the selected shape,
 * each in one undo step, through {@link Placement}.
 */
public class ProviderLibraryPanel extends Table {
    /** What the editor does with a downloaded asset (UI M7); each is one undo step, explained in the status bar. */
    public interface Placement {
        /** Places the glTF at {@code path} standing on the ground at the centre of the view. */
        void placeModel(String path);

        /** Puts the material at {@code path} on the selected shape. */
        void applyMaterial(String path);
    }

    /** Size of the preview in the details, in pixels. */
    private static final float PREVIEW_SIZE = 128f;
    private static final String LOG_SOURCE = "Asset Library";
    /** Results listed at once; a search narrows the rest. */
    private static final int MAX_RESULTS = 300;
    /** Width of the results column. */
    private static final float RESULTS_WIDTH = 330f;
    private static final DateTimeFormatter FETCHED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Skin skin;
    private final AssetProvider provider;
    private final HttpSource http;
    private final File assetsRoot;
    private final EditorState editorState;
    private final EditorLog log;
    private final Runnable onImported;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "forgeroom-asset-library");
        thread.setDaemon(true);
        return thread;
    });

    private final SelectBox<String> typeBox;
    private final TextField searchField;
    private final Label status;
    private final Table results = new Table();
    private final Table details = new Table();

    private AssetType type;
    private AssetProvider.Catalog catalog;
    private RemoteAsset selected;
    private List<DownloadOption> options = List.of();
    private SelectBox<String> resolutionBox;
    private TextButton downloadButton;
    private Label downloadStatus;
    private boolean confirmed;
    /** Bumped by every catalogue load and selection, so a late answer for an earlier one is dropped. */
    private int generation;
    private boolean loading;
    private boolean downloadWhenListed;
    private boolean useWhenInProject;

    private volatile boolean downloading;
    private volatile long progressDone;
    private volatile long progressTotal;
    private volatile String progressFile = "";
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final ThumbnailCache thumbnails;
    private final Placement placement;
    private final Container<Actor> preview = new Container<>();
    private final Table useRow = new Table();
    /** The preview shown now; owned by this panel and disposed when another is shown or the panel is disposed. */
    private Texture previewTexture;

    /**
     * @param thumbnails where previews come from (fetched when an asset is chosen, cached on disk)
     * @param placement  what Place in world and Apply to selection do
     * @param onImported run on the render thread after a download, to rescan the project's files
     */
    public ProviderLibraryPanel(Skin skin, AssetProvider provider, HttpSource http, ThumbnailCache thumbnails, Placement placement,
                                File assetsRoot, EditorState editorState, EditorLog log, Runnable onImported) {
        super(skin);
        this.thumbnails = Objects.requireNonNull(thumbnails, "thumbnails cannot be null");
        this.placement = Objects.requireNonNull(placement, "placement cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
        this.http = Objects.requireNonNull(http, "http cannot be null");
        this.assetsRoot = Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.log = Objects.requireNonNull(log, "log cannot be null");
        this.onImported = Objects.requireNonNull(onImported, "onImported cannot be null");
        this.type = provider.types().get(0);
        top().left();

        typeBox = new SelectBox<>(skin);
        Array<String> labels = new Array<>();
        provider.types().forEach(t -> labels.add(t.label()));
        typeBox.setItems(labels);
        typeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetType chosen = provider.types().get(typeBox.getSelectedIndex());
                if (chosen != type) {
                    type = chosen;
                    loadCatalog(null);
                }
            }
        });
        searchField = new TextField("", skin);
        searchField.setMessageText("Search " + provider.name() + " (name, tag or category)");
        searchField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    search();
                    return true;
                }
                return false;
            }
        });
        status = new Label("Search to see " + provider.name() + "'s CC0 assets (goes online).", skin, "muted");
        status.setWrap(true);

        Table bar = new Table(skin);
        bar.defaults().padRight(6f);
        bar.add(typeBox).width(110f);
        bar.add(searchField).growX();
        bar.add(button("Search", this::search, "flat"));
        add(bar).growX().pad(4f).row();
        add(status).growX().padLeft(6f).padRight(6f).row();

        results.top().left();
        ScrollPane resultScroll = new ScrollPane(results, skin);
        resultScroll.setFadeScrollBars(false);
        resultScroll.setScrollingDisabled(true, false);
        details.top().left();
        details.defaults().left().pad(2f, 6f, 2f, 6f);
        ScrollPane detailScroll = new ScrollPane(details, skin);
        detailScroll.setFadeScrollBars(false);
        detailScroll.setScrollingDisabled(true, false);
        Table body = new Table(skin);
        body.add(resultScroll).width(RESULTS_WIDTH).growY().top();
        body.add(detailScroll).grow().top();
        add(body).grow().row();

        TextButton credit = button(provider.credit(), () -> Gdx.net.openURI(provider.homePage()), "flat");
        add(credit).left().pad(2f, 4f, 4f, 4f);
        showNoSelection();
    }

    private TextButton button(String text, Runnable action, String style) {
        TextButton button = new TextButton(text, skin, style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /** Called when the tab is shown: the first time, the catalogue is loaded (TD-095: online only when the tab is opened). */
    public void onShown() {
        if (catalog == null && !loading) {
            loadCatalog(null);
        }
    }

    /**
     * Testing aid ({@code GDXUNITY_EDITOR_LIBRARY_SEARCH}): searches {@code words} among {@code assetType}, selects the first result
     * and, when {@code download} is true, presses Download once its resolutions are listed (the preferred one; a download over the
     * confirmation size is not confirmed, so it stops at the question, as for a person). When {@code use} is true, Place in world or
     * Apply to selection is pressed as soon as the asset is in the project (UI M7).
     */
    public void searchForTesting(AssetType assetType, String words, boolean download, boolean use) {
        type = Objects.requireNonNull(assetType, "assetType cannot be null");
        downloadWhenListed = download;
        useWhenInProject = use;
        typeBox.setSelectedIndex(provider.types().indexOf(assetType));
        searchField.setText(words);
        loadCatalog(words);
    }

    private void search() {
        if (catalog == null || catalog.offline()) {
            loadCatalog(searchField.getText());
        } else {
            showResults(searchField.getText(), false);
        }
    }

    private void loadCatalog(String thenSelectFirstOf) {
        int request = ++generation;
        AssetType wanted = type;
        loading = true;
        status.setText("Reaching " + provider.name() + "...");
        status.setStyle(skin.get("muted", Label.LabelStyle.class));
        worker.execute(() -> {
            try {
                AssetProvider.Catalog loaded = provider.catalog(wanted);
                Gdx.app.postRunnable(() -> {
                    if (request != generation) {
                        return;
                    }
                    loading = false;
                    catalog = loaded;
                    showResults(thenSelectFirstOf != null ? thenSelectFirstOf : searchField.getText(), thenSelectFirstOf != null);
                });
            } catch (IOException | RuntimeException e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                Gdx.app.postRunnable(() -> {
                    if (request != generation) {
                        return;
                    }
                    loading = false;
                    catalog = null;
                    results.clearChildren();
                    status.setText(message + ". Assets already imported keep working; try again when online.");
                    status.setStyle(skin.get("error", Label.LabelStyle.class));
                    log.warning(LOG_SOURCE, message);
                });
            }
        });
    }

    private void showResults(String query, boolean selectFirst) {
        List<RemoteAsset> found = catalog.search(query);
        results.clearChildren();
        int shown = Math.min(found.size(), MAX_RESULTS);
        for (int i = 0; i < shown; i++) {
            RemoteAsset asset = found.get(i);
            TextButton row = button(asset.name(), () -> select(asset), "flat");
            row.getLabel().setAlignment(Align.left);
            results.add(row).growX().left().row();
        }
        String count = found.size() + " " + type.label().toLowerCase(Locale.ROOT)
            + (found.size() > shown ? " (first " + shown + " shown; search to narrow)" : "");
        if (catalog.offline()) {
            status.setText("Offline, from " + FETCHED_FORMAT.format(catalog.fetched()) + " (" + catalog.problem() + "). " + count
                + ". Downloads need " + provider.name() + ".");
            status.setStyle(skin.get("error", Label.LabelStyle.class));
        } else {
            status.setText(count + " on " + provider.name() + ", all CC0" + (catalog.problem().isEmpty() ? "" : "; " + catalog.problem()));
            status.setStyle(skin.get("muted", Label.LabelStyle.class));
        }
        if (selectFirst && !found.isEmpty()) {
            select(found.get(0));
        }
    }

    private void showNoSelection() {
        details.clearChildren();
        details.add(new Label("Choose an asset to see its licence, resolutions and size.", skin, "muted")).row();
    }

    private void select(RemoteAsset asset) {
        if (downloading) {
            editorState.setStatusMessage("A download is running; cancel it or wait before choosing another asset", true);
            return;
        }
        selected = asset;
        options = List.of();
        confirmed = false;
        int request = ++generation;
        details.clearChildren();
        Table info = new Table(skin);
        info.top().left();
        info.defaults().left().pad(1f, 0f, 1f, 0f);
        info.add(new Label(asset.name(), skin, "title")).row();
        info.add(new Label("By " + (asset.authors().isEmpty() ? "unknown" : String.join(", ", asset.authors())), skin)).row();
        if (!asset.categories().isEmpty()) {
            info.add(new Label(String.join(", ", asset.categories()), skin, "muted")).row();
        }
        info.add(new Label("Licence: CC0 1.0 (public domain), " + provider.licenceEvidence(), skin, "muted")).row();
        info.add(new Label("Goes to " + asset.folder() + (asset.type() == AssetType.TEXTURE
            ? ", with a material in materials/" : ""), skin, "muted")).row();
        preview.setActor(new Label("preview...", skin, "muted"));
        Table head = new Table(skin);
        head.add(preview).size(PREVIEW_SIZE).top().padRight(10f);
        head.add(info).top().left();
        details.add(head).row();
        loadPreview(asset, request);
        Table actions = new Table(skin);
        actions.defaults().padRight(6f);
        actions.add(button("Open page", () -> Gdx.net.openURI(asset.page()), "flat"));
        resolutionBox = new SelectBox<>(skin);
        resolutionBox.setDisabled(true);
        resolutionBox.setItems("loading...");
        resolutionBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                confirmed = false;
                updateDownloadButton();
            }
        });
        actions.add(resolutionBox).width(170f);
        downloadButton = button("Download", this::download, "primary");
        downloadButton.setDisabled(true);
        actions.add(downloadButton);
        // Place in world or Apply to selection, on the same row so they stay in view
        actions.add(useRow).padLeft(12f);
        // Beside the preview, so Download and Place stay in view in a short drawer
        info.add(actions).padTop(4f).row();
        downloadStatus = new Label("", skin, "muted");
        downloadStatus.setWrap(true);
        info.add(downloadStatus).width(420f).row();
        useRow.clearChildren();
        showUse(asset, inProject(asset));

        worker.execute(() -> {
            try {
                List<DownloadOption> loaded = provider.options(asset);
                Gdx.app.postRunnable(() -> {
                    if (request == generation) {
                        showOptions(loaded);
                    }
                });
            } catch (IOException | RuntimeException e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                Gdx.app.postRunnable(() -> {
                    if (request == generation) {
                        resolutionBox.setItems("unavailable");
                        downloadStatus.setText("Its files cannot be listed: " + message);
                    }
                });
            }
        });
    }

    private void showOptions(List<DownloadOption> loaded) {
        options = loaded;
        if (loaded.isEmpty()) {
            resolutionBox.setItems("none");
            downloadStatus.setText("No resolution in a format ForgeRoom reads (glTF, or JPEG texture maps).");
            return;
        }
        Array<String> items = new Array<>();
        for (DownloadOption option : loaded) {
            items.add(option.label() + " - " + DownloadOption.formatBytes(option.totalBytes()));
        }
        resolutionBox.setItems(items);
        resolutionBox.setSelectedIndex(loaded.indexOf(AssetProvider.preferred(loaded)));
        resolutionBox.setDisabled(false);
        updateDownloadButton();
        if (downloadWhenListed) {
            downloadWhenListed = false;
            download();
        }
    }

    private DownloadOption chosenOption() {
        int index = resolutionBox == null ? -1 : resolutionBox.getSelectedIndex();
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    private void updateDownloadButton() {
        DownloadOption option = chosenOption();
        if (downloadButton == null) {
            return;
        }
        downloadButton.setDisabled(option == null || downloading);
        if (option != null && RemoteAssetInstaller.needsConfirmation(option) && !confirmed) {
            downloadButton.setText("Download (" + DownloadOption.formatBytes(option.totalBytes()) + ")...");
        } else {
            downloadButton.setText("Download");
        }
    }

    private void download() {
        DownloadOption option = chosenOption();
        RemoteAsset asset = selected;
        if (option == null || asset == null) {
            return;
        }
        if (downloading) {
            cancel.set(true);
            return;
        }
        if (RemoteAssetInstaller.needsConfirmation(option) && !confirmed) {
            confirmed = true;
            downloadButton.setText("Confirm " + DownloadOption.formatBytes(option.totalBytes()));
            downloadStatus.setText(option.label() + " is " + DownloadOption.formatBytes(option.totalBytes())
                + ", over " + DownloadOption.formatBytes(RemoteAssetInstaller.CONFIRM_BYTES) + ". Press Confirm to download it.");
            return;
        }
        downloading = true;
        cancel.set(false);
        progressDone = 0L;
        progressTotal = option.totalBytes();
        progressFile = "";
        downloadButton.setText("Cancel");
        resolutionBox.setDisabled(true);
        editorState.setStatusMessage("Downloading " + asset.name() + " (" + option.label() + ") from " + provider.name() + "...", false);
        worker.execute(() -> {
            try {
                RemoteAssetInstaller.Result result = RemoteAssetInstaller.install(provider, asset, option, assetsRoot, LocalDate.now(),
                    http, (done, total, file) -> {
                        progressDone = done;
                        progressTotal = total;
                        progressFile = file;
                    }, cancel::get);
                Gdx.app.postRunnable(() -> finished(asset, option, result));
            } catch (RemoteAssetInstaller.CancelledException e) {
                Gdx.app.postRunnable(() -> stopped("Download of " + asset.name() + " cancelled; nothing was written", false));
            } catch (IOException | RuntimeException e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                Gdx.app.postRunnable(() -> stopped(asset.name() + " was not downloaded, and nothing was written: " + message, true));
            }
        });
    }

    private void finished(RemoteAsset asset, DownloadOption option, RemoteAssetInstaller.Result result) {
        downloading = false;
        String message = "Downloaded " + asset.name() + " (" + option.label() + ", " + DownloadOption.formatBytes(option.totalBytes())
            + ") into " + asset.folder() + (result.materialPath().isEmpty() ? "" : "; material " + result.materialPath());
        if (!result.leftOut().isEmpty()) {
            message += "; left out " + result.leftOut().size() + " file(s) ForgeRoom cannot read (see the Console)";
        }
        editorState.setStatusMessage(message, false);
        log.info(LOG_SOURCE, message);
        for (String path : result.leftOut()) {
            log.warning(LOG_SOURCE, asset.name() + ": left out " + path);
        }
        downloadStatus.setText(message);
        resolutionBox.setDisabled(false);
        confirmed = false;
        updateDownloadButton();
        onImported.run();
        showUse(asset, result.files());
    }

    private void stopped(String message, boolean error) {
        downloading = false;
        editorState.setStatusMessage(message, error);
        if (error) {
            log.error(LOG_SOURCE, message);
        } else {
            log.info(LOG_SOURCE, message);
        }
        if (downloadStatus != null) {
            downloadStatus.setText(message);
        }
        if (resolutionBox != null) {
            resolutionBox.setDisabled(options.isEmpty());
        }
        confirmed = false;
        updateDownloadButton();
    }

    /** Fetches the preview off the render thread and shows it, unless another asset was chosen meanwhile. */
    private void loadPreview(RemoteAsset asset, int request) {
        worker.execute(() -> {
            try {
                byte[] bytes = thumbnails.thumbnail(asset);
                Gdx.app.postRunnable(() -> {
                    if (request == generation) {
                        showPreview(bytes, asset);
                    }
                });
            } catch (IOException | RuntimeException e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                Gdx.app.postRunnable(() -> {
                    if (request == generation) {
                        Label none = new Label("No preview: " + message, skin, "muted");
                        none.setWrap(true);
                        preview.setActor(none);
                        preview.fill();
                    }
                });
            }
        });
    }

    private void showPreview(byte[] bytes, RemoteAsset asset) {
        Texture texture;
        try {
            Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
            try {
                texture = new Texture(pixmap);
            } finally {
                pixmap.dispose();
            }
        } catch (GdxRuntimeException e) {
            preview.setActor(new Label("No preview: the image of " + asset.name() + " could not be read", skin, "muted"));
            return;
        }
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        if (previewTexture != null) {
            previewTexture.dispose();
        }
        previewTexture = texture;
        Image image = new Image(texture);
        image.setScaling(Scaling.fit);
        preview.setActor(image);
        preview.fill();
    }

    /** The project files of {@code asset}'s earlier download, from the manifest; empty when it is not in the project. */
    private List<String> inProject(RemoteAsset asset) {
        try {
            return ThirdPartyManifest.read(assetsRoot).entry(asset.manifestId())
                .map(entry -> entry.files().stream().map(ThirdPartyManifest.FileRecord::path).toList())
                .orElse(List.of());
        } catch (IOException | IllegalArgumentException e) {
            log.warning(LOG_SOURCE, "The licence manifest could not be read: " + e.getMessage());
            return List.of();
        }
    }

    /** Offers Place in world (a model) or Apply to selection (a material) for an asset whose files are in the project. */
    private void showUse(RemoteAsset asset, List<String> files) {
        useRow.clearChildren();
        if (files.isEmpty()) {
            return;
        }
        useRow.defaults().padRight(6f);
        useRow.add(new Label("In the project:", skin, "secondary"));
        Runnable use = null;
        if (asset.type() == AssetType.MODEL) {
            String model = files.stream()
                .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".gltf") || path.toLowerCase(Locale.ROOT).endsWith(".glb"))
                .findFirst().orElse(null);
            if (model != null) {
                use = () -> placement.placeModel(model);
                useRow.add(button("Place in world", use, "flat"));
            }
        } else {
            String material = MaterialAssets.FOLDER + "/" + RemoteAssetInstaller.materialName(asset.id()) + MaterialAssets.EXTENSION;
            if (new File(assetsRoot, material).isFile()) {
                use = () -> placement.applyMaterial(material);
                useRow.add(button("Apply to selection", use, "flat"));
            }
        }
        if (use != null && useWhenInProject && !downloadWhenListed && !downloading) {
            useWhenInProject = false;
            use.run();
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (downloading && downloadStatus != null) {
            long total = progressTotal;
            long done = progressDone;
            int percent = total <= 0 ? 0 : (int) Math.min(100L, done * 100L / total);
            downloadStatus.setText("Downloading " + percent + "%: " + DownloadOption.formatBytes(done) + " of "
                + DownloadOption.formatBytes(total) + (progressFile.isEmpty() ? "" : " (" + progressFile + ")") + ". Press Cancel to stop.");
        }
    }

    /** Stops the worker; a download under way is cancelled and leaves nothing behind. */
    public void dispose() {
        if (previewTexture != null) {
            previewTexture.dispose();
            previewTexture = null;
        }
        cancel.set(true);
        worker.shutdownNow();
    }
}
