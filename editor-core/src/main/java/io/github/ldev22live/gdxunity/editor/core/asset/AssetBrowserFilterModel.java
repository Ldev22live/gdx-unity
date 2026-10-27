package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Filter and view helper for the Asset Browser.
 * Supports filtering by name query, asset type, and sorting.
 */
public class AssetBrowserFilterModel {
    private final AssetCatalog catalog;
    private String searchFilter = "";
    private AssetType typeFilter = null; // null means all types

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

    public List<AssetMetadata> getFilteredAssets() {
        List<AssetMetadata> all = catalog.getAllAssets();
        if (all.isEmpty()) return Collections.emptyList();

        return all.stream()
            .filter(meta -> {
                if (typeFilter != null && meta.type() != typeFilter) {
                    return false;
                }
                if (!searchFilter.isEmpty()) {
                    return meta.getPath().toLowerCase().contains(searchFilter);
                }
                return true;
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
