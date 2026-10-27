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
    private boolean bottomPanelCollapsed = false;

    public WorkspaceLayoutConfig() {}

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
