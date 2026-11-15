package io.github.ldev22live.gdxunity.editor.desktop.ui.menu;

import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway.EditOffer;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The items of the Customize gateway's dropdown (roadmap Appendix E2, milestone M1b.7): first what can be edited about the selection
 * ("Edit Selected"), then every workspace. An entry with no editor yet stays in the list, marked, and choosing it explains why; the
 * workspace on screen is marked as well. Pure data, so it is tested without a window.
 */
public final class CustomizeMenu {
    /** Added to an "Edit Selected" entry whose kind of file has no editor yet. */
    public static final String NOT_YET_SUFFIX = " (no editor yet)";
    /** Added to the workspace on screen. */
    public static final String CURRENT_SUFFIX = " (here)";

    private CustomizeMenu() { }

    /**
     * @param selectedName the selected entity's name, or null when nothing is selected
     * @param offers       {@code CustomizeGateway.editSelected} for the selection
     * @param current      the workspace on screen
     * @param currentTab   the centre tab on screen, which tells the two 2D Studio entries apart
     */
    public static List<ContextMenu.Item> items(String selectedName, List<EditOffer> offers, Workspace current, String currentTab,
                                               Consumer<EditOffer> onOffer, Consumer<Workspace.Destination> onWorkspace) {
        Objects.requireNonNull(offers, "offers cannot be null");
        Objects.requireNonNull(current, "current cannot be null");
        Objects.requireNonNull(onOffer, "onOffer cannot be null");
        Objects.requireNonNull(onWorkspace, "onWorkspace cannot be null");
        List<ContextMenu.Item> items = new ArrayList<>();
        if (selectedName == null) {
            items.add(new ContextMenu.Item("Select something in the World to edit it", "", false, () -> { }));
        } else if (offers.isEmpty()) {
            items.add(new ContextMenu.Item(selectedName + " uses no file that can be edited", "", false, () -> { }));
        } else {
            for (EditOffer offer : offers) {
                items.add(new ContextMenu.Item(offer.label() + (offer.isAvailable() ? "" : NOT_YET_SUFFIX), "", true,
                    () -> onOffer.accept(offer)));
            }
        }
        items.add(ContextMenu.Item.separator());
        for (Workspace.Destination destination : Workspace.destinations()) {
            boolean here = destination.workspace() == current
                && (current != Workspace.STUDIO_2D || destination.tab().equals(currentTab));
            items.add(new ContextMenu.Item(destination.label() + (here ? CURRENT_SUFFIX : ""), "", true,
                () -> onWorkspace.accept(destination)));
        }
        return items;
    }
}
