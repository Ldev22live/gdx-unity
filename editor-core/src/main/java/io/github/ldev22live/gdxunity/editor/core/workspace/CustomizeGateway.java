package io.github.ldev22live.gdxunity.editor.core.workspace;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetOpening;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The Customize gateway's "Edit Selected" entries (roadmap Appendix E2, milestone M1b.7): what a person can edit about the selected
 * entity, and in which workspace. The files an entity uses are found from its saved form (every string its components save that names
 * an asset), so a component type added later is covered without a change here. Kinds of file with no editor of their own are still
 * listed, marked unavailable with the reason, never faked (Appendix E1).
 *
 * <p>The entries keep four kinds of change apart (M1b.4): <b>transforming</b> the object (Move, Rotate or Scale, which never changes
 * its mesh), <b>editing its material</b> (in Details, shared by every surface that uses the file), <b>editing the shared prefab</b>
 * (planned, M1.6) and <b>editing mesh geometry</b> (planned, R7a; sculpting a copy is offered in the meantime and says so).
 */
public final class CustomizeGateway {
    /** Sculpt's "enter sculpt mode" action, registered by {@code editor-sculpt}. */
    public static final String SCULPT_ENTER_ACTION = "tools.sculpt-enter";
    /** Sculpt's "sculpt from selected" action: starts a sculpt copy of a model's or a shape's mesh. */
    public static final String SCULPT_FROM_SELECTED_ACTION = "create.sculpt-from-selected";
    /** The Move tool: transforming an object, which never changes its mesh (M1b.4). */
    public static final String MOVE_TOOL_ACTION = "tools.move";
    /** Why "Edit Mesh Geometry" is not offered yet, and what is offered instead. */
    public static final String MESH_GEOMETRY_REASON = "Editing vertices, edges and faces is planned (roadmap R7a). Until then, "
        + "\"in 3D (sculpt a copy)\" reshapes a project-owned copy, and Move, Rotate or Scale only transforms the object.";
    /** Why the shared prefab cannot be edited from here yet. */
    public static final String SHARED_PREFAB_REASON = "Editing the shared prefab from here is planned (roadmap M1.6: Edit this "
        + "instance, Edit shared prefab, Make unique). A change in Details changes this instance only; apply it to the prefab from the "
        + "Outliner's context menu.";

    /** What choosing an offer does. */
    public enum Kind {
        /** Open {@link EditOffer#assetPath()} in its editor. */
        OPEN_ASSET,
        /** Run {@link EditOffer#actionId()} with the entity still selected. */
        RUN_ACTION,
        /** Show Details for the selected entity, where {@link EditOffer#assetPath()} is edited (a material). */
        SHOW_DETAILS,
        /** Open the World strip's Terrain tools on the selected terrain. */
        OPEN_TERRAIN_TOOLS,
        /** Nothing: the reason is in {@link EditOffer#unavailableReason()}. */
        UNAVAILABLE
    }

    /**
     * One "Edit Selected" entry.
     *
     * @param label             text for the menu, e.g. "Edit Sprite hero.sprite"
     * @param workspace         where it leads
     * @param kind              what choosing it does
     * @param assetPath         the file opened, for {@link Kind#OPEN_ASSET}; otherwise the file concerned or null
     * @param actionId          the action run, for {@link Kind#RUN_ACTION}; otherwise null
     * @param unavailableReason why nothing can be done yet, for {@link Kind#UNAVAILABLE}; otherwise null
     */
    public record EditOffer(String label, Workspace workspace, Kind kind, String assetPath, String actionId, String unavailableReason) {
        public boolean isAvailable() {
            return kind != Kind.UNAVAILABLE;
        }
    }

    private CustomizeGateway() { }

    /**
     * Every asset path the entity's components save, in component order, without repeats. Components with no serializer in
     * {@code serializers} are not looked at.
     */
    public static List<String> assetPathsUsedBy(Entity entity, ComponentSerializerRegistry serializers) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(serializers, "serializers cannot be null");
        Set<String> paths = new LinkedHashSet<>();
        for (Component component : entity.getAllComponents()) {
            for (String text : savedStrings(component, serializers)) {
                if (!text.isBlank() && AssetType.fromPath(text) != AssetType.UNKNOWN) {
                    paths.add(text);
                }
            }
        }
        return new ArrayList<>(paths);
    }

    /**
     * The "Edit Selected" entries for {@code entity}, available ones first; empty for no selection.
     */
    public static List<EditOffer> editSelected(Entity entity, ComponentSerializerRegistry serializers) {
        Objects.requireNonNull(serializers, "serializers cannot be null");
        if (entity == null) {
            return List.of();
        }
        List<EditOffer> available = new ArrayList<>();
        List<EditOffer> unavailable = new ArrayList<>();
        String name = entity.getName();
        boolean isSculpt = entity.getComponent(DynamicMeshComponent.class) != null;
        if (isSculpt) {
            available.add(new EditOffer("Edit " + name + " in 3D", Workspace.MODELING_3D, Kind.RUN_ACTION, null, SCULPT_ENTER_ACTION, null));
        } else if (entity.getComponent(ModelComponent.class) != null || entity.getComponent(MeshPrimitiveComponent.class) != null) {
            available.add(new EditOffer("Edit " + name + " in 3D (sculpt a copy)", Workspace.MODELING_3D, Kind.RUN_ACTION, null,
                SCULPT_FROM_SELECTED_ACTION, null));
        }
        for (String path : assetPathsUsedBy(entity, serializers)) {
            AssetType type = AssetType.fromPath(path);
            String file = path.substring(path.lastIndexOf('/') + 1);
            switch (type) {
                case SPRITE -> available.add(open("Edit Sprite " + file, Workspace.STUDIO_2D, path));
                case ANIMATION -> available.add(open("Animate " + name + " (" + file + ")", Workspace.ANIMATION, path));
                case SONG -> available.add(open("Edit Music " + file, Workspace.AUDIO_MUSIC, path));
                case GRAPH -> available.add(open("Edit Logic " + file, Workspace.LOGIC, path));
                case SCULPT -> { } // the 3D entry above covers it
                case SOUND -> unavailable.add(unavailable("Edit Sound " + file, Workspace.AUDIO_MUSIC, path,
                    "No sound editor yet (roadmap MC.5). " + AssetOpening.guidance(type)));
                case MODEL -> unavailable.add(unavailable("Edit Mesh Geometry of " + file, Workspace.MODELING_3D, path,
                    MESH_GEOMETRY_REASON));
                case MATERIAL -> available.add(new EditOffer("Edit Material " + file + " in Details (shared by every surface that uses it)",
                    Workspace.WORLD, Kind.SHOW_DETAILS, path, null, null));
                case TERRAIN -> available.add(new EditOffer("Sculpt and Paint Terrain " + file + " (World strip: Terrain)",
                    Workspace.WORLD, Kind.OPEN_TERRAIN_TOOLS, path, null, null));
                case PREFAB -> unavailable.add(unavailable("Edit Shared Prefab " + file, Workspace.WORLD, path, SHARED_PREFAB_REASON));
                case TEXTURE -> unavailable.add(unavailable("Edit " + label(type) + " " + file, Workspace.WORLD, path,
                    AssetOpening.guidance(type)));
                default -> { }
            }
        }
        // Transforming is always possible and is never mesh editing (M1b.4)
        available.add(new EditOffer("Move, Rotate or Scale " + name + " (transform only, the mesh is unchanged)", Workspace.WORLD,
            Kind.RUN_ACTION, null, MOVE_TOOL_ACTION, null));
        available.addAll(unavailable);
        return available;
    }

    private static EditOffer open(String label, Workspace workspace, String path) {
        return new EditOffer(label, workspace, Kind.OPEN_ASSET, path, null, null);
    }

    private static EditOffer unavailable(String label, Workspace workspace, String path, String reason) {
        return new EditOffer(label, workspace, Kind.UNAVAILABLE, path, null, reason);
    }

    private static String label(AssetType type) {
        String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<String> savedStrings(Component component, ComponentSerializerRegistry serializers) {
        ComponentSerializer serializer = serializers.getByClass(component.getClass());
        if (serializer == null) {
            return List.of();
        }
        StringWriter text = new StringWriter();
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(text);
        json.writeObjectStart();
        serializer.serialize(component, json);
        json.writeObjectEnd();
        List<String> strings = new ArrayList<>();
        collectStrings(new JsonReader().parse(text.toString()), strings);
        return strings;
    }

    private static void collectStrings(JsonValue value, List<String> strings) {
        if (value.isString()) {
            strings.add(value.asString());
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            collectStrings(child, strings);
        }
    }
}
