package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Filter and view helper for the Asset Browser.
 * Supports filtering by name query, asset type, and sorting, and browsing by folder (Phase 13-C): with no search text the list shows
 * the assets directly in the current folder plus its subfolders; a search looks through every folder.
 */
public class AssetBrowserFilterModel {
    private final AssetCatalog catalog;
    private String searchFilter = "";
    private AssetType typeFilter = null; // null means all types
    /** Project-relative folder being browsed, without a trailing slash; empty for the asset root. */
    private String currentFolder = "";

    /** A subfolder of the folder being browsed, with how many assets it holds at any depth. */
    public record Folder(String name, String path, int assetCount) {
    }

    public AssetBrowserFilterModel(AssetCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public AssetCatalog getCatalog() {
        return catalog;
    }

    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter != null ? searchFilter.trim().toLowerCase() : "";
    }

    public AssetType getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(AssetType typeFilter) {
        this.typeFilter = typeFilter;
    }

    /** The folder being browsed ({@code ""} is the asset root). */
    public String getCurrentFolder() {
        return currentFolder;
    }

    /**
     * Browses {@code folder}. A folder that holds no assets (any more) is replaced by its nearest parent that does, so a deleted folder
     * never leaves the list empty.
     */
    public void setCurrentFolder(String folder) {
        String normalized = folder == null ? "" : folder.replace('\\', '/').replaceAll("^/+|/+$", "");
        while (!normalized.isEmpty() && !folderHoldsAssets(normalized)) {
            int slash = normalized.lastIndexOf('/');
            normalized = slash < 0 ? "" : normalized.substring(0, slash);
        }
        currentFolder = normalized;
    }

    /** Opens the subfolder called {@code name} of the current folder. */
    public void openFolder(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        setCurrentFolder(currentFolder.isEmpty() ? name : currentFolder + "/" + name);
    }

    /** Goes to the parent of the current folder; does nothing at the asset root. */
    public void up() {
        int slash = currentFolder.lastIndexOf('/');
        currentFolder = slash < 0 ? "" : currentFolder.substring(0, slash);
    }

    /** The current folder's path as names from the root, for a breadcrumb. */
    public List<String> getBreadcrumb() {
        return currentFolder.isEmpty() ? List.of() : List.of(currentFolder.split("/"));
    }

    /** Re-checks the current folder after the catalog was scanned again (see {@link #setCurrentFolder}). */
    public void revalidate() {
        setCurrentFolder(currentFolder);
    }

    /** Subfolders of the current folder, by name; hidden while searching, since a search covers every folder. */
    public List<Folder> getSubfolders() {
        if (!searchFilter.isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = currentFolder.isEmpty() ? "" : currentFolder + "/";
        Map<String, Integer> counts = new TreeMap<>();
        for (AssetMetadata meta : catalog.getAllAssets()) {
            String path = meta.getPath();
            if (path.startsWith(prefix) && (typeFilter == null || meta.type() == typeFilter)) {
                int slash = path.indexOf('/', prefix.length());
                if (slash > 0) {
                    counts.merge(path.substring(prefix.length(), slash), 1, Integer::sum);
                }
            }
        }
        List<Folder> folders = new ArrayList<>();
        counts.forEach((name, count) -> folders.add(new Folder(name, prefix + name, count)));
        return folders;
    }

    private boolean folderHoldsAssets(String folder) {
        String prefix = folder + "/";
        return catalog.getAllAssets().stream().anyMatch(meta -> meta.getPath().startsWith(prefix));
    }

    public List<AssetMetadata> getFilteredAssets() {
        List<AssetMetadata> all = catalog.getAllAssets();
        if (all.isEmpty()) return Collections.emptyList();

        String prefix = currentFolder.isEmpty() ? "" : currentFolder + "/";
        return all.stream()
            .filter(meta -> {
                if (typeFilter != null && meta.type() != typeFilter) {
                    return false;
                }
                if (!searchFilter.isEmpty()) {
                    return meta.getPath().toLowerCase().contains(searchFilter);
                }
                // Browsing: only the files directly in the current folder (the subfolders are listed separately)
                return meta.getPath().startsWith(prefix) && meta.getPath().indexOf('/', prefix.length()) < 0;
            })
            .collect(Collectors.toList());
    }

    public int getTotalCount() {
        return catalog.getAllAssets().size();
    }

    public int getFilteredCount() {
        return getFilteredAssets().size();
    }
}
