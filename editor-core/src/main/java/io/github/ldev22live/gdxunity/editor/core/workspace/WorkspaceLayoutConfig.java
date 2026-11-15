package io.github.ldev22live.gdxunity.editor.core.workspace;

/**
 * Serializable data model for workspace layout configuration.
 */
public class WorkspaceLayoutConfig {
    private int version = 1;
    private String activeWorkspaceId = "scene-editor";
    private float splitRatioMain = 0.72f;
    private float splitRatioLeft = 0.20f;
    private float splitRatioRight = 0.25f;
    /** The World layout (roadmap M1b.2, TD-085) starts with the Content Browser and Output Log closed. */
    private boolean bottomPanelCollapsed = true;
    /** The World layout starts with the Outliner closed (TD-085). */
    private boolean outlinerShown = false;
    /** A {@link PanelLayout.DetailsMode} name; the World layout shows Details while something is selected (TD-085). */
    private String detailsMode = PanelLayout.DetailsMode.WITH_SELECTION.name();

    public WorkspaceLayoutConfig() {}

    public boolean isOutlinerShown() { return outlinerShown; }
    public void setOutlinerShown(boolean outlinerShown) { this.outlinerShown = outlinerShown; }

    public String getDetailsMode() { return detailsMode; }
    public void setDetailsMode(String detailsMode) { this.detailsMode = detailsMode; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getActiveWorkspaceId() { return activeWorkspaceId; }
    public void setActiveWorkspaceId(String activeWorkspaceId) { this.activeWorkspaceId = activeWorkspaceId; }

    public float getSplitRatioMain() { return splitRatioMain; }
    public void setSplitRatioMain(float splitRatioMain) { this.splitRatioMain = splitRatioMain; }

    public float getSplitRatioLeft() { return splitRatioLeft; }
    public void setSplitRatioLeft(float splitRatioLeft) { this.splitRatioLeft = splitRatioLeft; }

    public float getSplitRatioRight() { return splitRatioRight; }
    public void setSplitRatioRight(float splitRatioRight) { this.splitRatioRight = splitRatioRight; }

    public boolean isBottomPanelCollapsed() { return bottomPanelCollapsed; }
    public void setBottomPanelCollapsed(boolean bottomPanelCollapsed) { this.bottomPanelCollapsed = bottomPanelCollapsed; }
}
