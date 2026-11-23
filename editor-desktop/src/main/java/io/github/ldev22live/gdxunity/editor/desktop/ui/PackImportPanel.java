package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.library.CuratedPack;
import io.github.ldev22live.gdxunity.editor.core.asset.library.PackImporter;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Asset Library's Import tab (UI M4): the curated packs a person downloads in a browser and imports here. Each row says whether the
 * pack is in the project (from {@code licenses/manifest.json}), opens its page, and imports a downloaded zip through
 * {@link PackImporter} on a worker thread, so a large pack never stops the editor. A refused archive is shown with every reason;
 * files left out are listed in the Output Log. Nothing here goes online: the page opens in the person's own browser.
 */
public class PackImportPanel extends Table {
    private static final String LOG_SOURCE = "Asset Library";

    private final Skin skin;
    private final List<CuratedPack> packs;
    private final File assetsRoot;
    private final EditorState editorState;
    private final EditorLog log;
    private final EditorDialogService dialogs;
    private final Runnable onImported;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "forgeroom-pack-import");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Label> statusLabels = new HashMap<>();
    private volatile String importing;

    /**
     * @param onImported run on the render thread after an import, to rescan the project's files
     */
    public PackImportPanel(Skin skin, List<CuratedPack> packs, File assetsRoot, EditorState editorState, EditorLog log,
                           EditorDialogService dialogs, Runnable onImported) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.packs = List.copyOf(Objects.requireNonNull(packs, "packs cannot be null"));
        this.assetsRoot = Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.log = Objects.requireNonNull(log, "log cannot be null");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs cannot be null");
        this.onImported = Objects.requireNonNull(onImported, "onImported cannot be null");
        top().left();
        Label intro = new Label("Kenney packs (CC0). Download a pack from its page in your browser, then Import the zip. Only formats "
            + "ForgeRoom reads are copied; a pack needs a CC0 licence file. Poly Haven and ambientCG search come next.", skin, "muted");
        intro.setWrap(true);
        Table rows = new Table(skin);
        rows.top().left();
        rows.defaults().left().pad(2f, 4f, 2f, 4f);
        for (CuratedPack pack : this.packs) {
            rows.add(new Label(pack.name(), skin)).minWidth(140f);
            rows.add(new Label(pack.category(), skin, "muted")).minWidth(170f);
            Label status = new Label("", skin, "muted");
            statusLabels.put(pack.manifestId(), status);
            rows.add(status).minWidth(220f);
            rows.add(button("Open page", () -> Gdx.net.openURI(pack.page())));
            rows.add(button("Import...", () -> chooseAndImport(pack))).row();
        }
        add(intro).growX().pad(4f).row();
        ScrollPane scroll = new ScrollPane(rows, skin);
        scroll.setFadeScrollBars(false);
        add(scroll).grow();
        refreshStatus();
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

    private void chooseAndImport(CuratedPack pack) {
        if (importing != null) {
            editorState.setStatusMessage("Already importing " + importing + "; wait for it to finish", true);
            return;
        }
        Optional<File> chosen = dialogs.chooseOpenFile("Import " + pack.name(), null, "Zip archive", "zip");
        if (chosen.isEmpty()) {
            return;
        }
        File archive = chosen.get();
        importing = pack.name();
        editorState.setStatusMessage("Importing " + pack.name() + " from " + archive.getName() + "...", false);
        worker.execute(() -> {
            try {
                PackImporter.Result result = PackImporter.importPack(archive, pack, assetsRoot, LocalDate.now());
                Gdx.app.postRunnable(() -> finished(pack, result));
            } catch (PackImporter.RefusedException e) {
                Gdx.app.postRunnable(() -> refused(pack, e.getProblems()));
            } catch (IOException | RuntimeException e) {
                Gdx.app.postRunnable(() -> refused(pack, List.of(e.getMessage() == null ? e.toString() : e.getMessage())));
            }
        });
    }

    private void finished(CuratedPack pack, PackImporter.Result result) {
        importing = null;
        String message = "Imported " + pack.name() + ": " + result.imported().size() + " file(s) into " + pack.folder()
            + (result.leftOut().isEmpty() ? "" : "; left out " + result.leftOut().size() + " file(s) ForgeRoom cannot read (see the Console)");
        editorState.setStatusMessage(message, false);
        log.info(LOG_SOURCE, message);
        for (String path : result.leftOut()) {
            log.warning(LOG_SOURCE, pack.name() + ": left out " + path);
        }
        refreshStatus();
        onImported.run();
    }

    private void refused(CuratedPack pack, List<String> problems) {
        importing = null;
        editorState.setStatusMessage(pack.name() + " was not imported: " + problems.get(0), true);
        log.error(LOG_SOURCE, pack.name() + " was not imported: " + String.join("; ", problems));
        dialogs.showError("Import " + pack.name(), "The pack was not imported, and nothing was written:\n- " + String.join("\n- ", problems));
    }

    /** Reads the project's manifest and shows, for each pack, whether and when it was imported. */
    public void refreshStatus() {
        ThirdPartyManifest manifest;
        try {
            manifest = ThirdPartyManifest.read(assetsRoot);
        } catch (IOException | IllegalArgumentException e) {
            for (Label label : statusLabels.values()) {
                label.setText("Manifest unreadable: " + e.getMessage());
            }
            return;
        }
        for (CuratedPack pack : packs) {
            Label label = statusLabels.get(pack.manifestId());
            label.setText(manifest.entry(pack.manifestId())
                .map(entry -> "Imported " + entry.retrieved() + ", " + entry.files().size() + " file(s)")
                .orElse("Not imported"));
        }
    }

    /** Stops the worker; an import under way is abandoned (its partial files are removed by the importer). */
    public void dispose() {
        worker.shutdownNow();
    }
}
