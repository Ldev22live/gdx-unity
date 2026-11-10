package io.github.ldev22live.gdxunity.editor.core.component;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.GameModeProfile;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioListenerComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CapsuleCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ConvexHullCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Component types offered by the Details panel's searchable "Add Component" menu. Each entry creates a
 * component with sensible defaults; adding goes through {@link AddComponentCommand} so it can be undone.
 */
public class ComponentCatalog {

    /** One addable component type. */
    public record Entry(String displayName, String category, Class<? extends Component> type,
                        boolean allowMultiple, Function<EditorState, Component> factory) {
        public Entry {
            Objects.requireNonNull(displayName, "displayName cannot be null");
            Objects.requireNonNull(category, "category cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(factory, "factory cannot be null");
        }
    }

    private static final float DEFAULT_SIZE = 100f;
    private static final float DEFAULT_CIRCLE_RADIUS = 50f;
    private static final String DEFAULT_SCRIPT_CLASS = "SamplePlayerScript";
    private static final float DEFAULT_POINT_LIGHT_RANGE = 600f;

    private final List<Entry> entries = new ArrayList<>();

    public void register(Entry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        for (Entry existing : entries) {
            if (existing.displayName().equals(entry.displayName())) {
                throw new IllegalArgumentException("Duplicate component catalog entry: " + entry.displayName());
            }
        }
        entries.add(entry);
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** Entries whose name or category contains {@code query} (case-insensitive); all entries for a blank query. */
    public List<Entry> search(String query) {
        if (query == null || query.isBlank()) {
            return getEntries();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || entry.category().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Like {@link #search(String)}, without the categories the game mode hides (TD-067): 3D physics in 2D, 2D physics in 2.5D and 3D. */
    public List<Entry> search(String query, GameMode mode) {
        GameModeProfile profile = GameModeProfile.of(mode);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : search(query)) {
            if (profile.offersCategory(entry.category())) {
                result.add(entry);
            }
        }
        return result;
    }

    /** False when the entity already has a component of a single-instance type, or one that cannot go with the new one. */
    public boolean canAdd(Entity entity, Entry entry) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(entry, "entry cannot be null");
        return (entry.allowMultiple() || entity.getComponent(entry.type()) == null) && conflict(entity, entry) == null;
    }

    /**
     * What stops {@code entry} from going on {@code entity} besides being a second one of a single-instance type, or null.
     * A walking character is its own physics body (TD-049), so it excludes rigid bodies and colliders, and they exclude it.
     */
    private static String conflict(Entity entity, Entry entry) {
        boolean addingCharacter = CharacterController3DComponent.class.isAssignableFrom(entry.type());
        boolean addingBodyPart = RigidBody3DComponent.class.isAssignableFrom(entry.type())
            || Collider3DComponent.class.isAssignableFrom(entry.type());
        if (addingCharacter && (entity.getComponent(RigidBody3DComponent.class) != null
            || !entity.getComponents(Collider3DComponent.class).isEmpty())) {
            return entity.getName() + " already has a Rigid Body 3D or a 3D collider. A Character Controller 3D is its own body: "
                + "remove those first.";
        }
        if (Joint3DComponent.class.isAssignableFrom(entry.type()) && entity.getComponent(CharacterController3DComponent.class) != null) {
            return entity.getName() + " is a Character Controller 3D, and characters cannot be joined (TD-051). Use a Rigid Body 3D.";
        }
        if (addingCharacter && entity.getComponent(Joint3DComponent.class) != null) {
            return entity.getName() + " has a Joint 3D, and characters cannot be joined (TD-051). Remove the joint first.";
        }
        boolean addingMeshCollider = MeshCollider3DComponent.class.isAssignableFrom(entry.type());
        if (addingMeshCollider || ConvexHullCollider3DComponent.class.isAssignableFrom(entry.type())) {
            if (entity.getComponent(ModelComponent.class) == null && entity.getComponent(MeshPrimitiveComponent.class) == null) {
                return entity.getName() + " shows no mesh. A " + entry.displayName() + " is built from the entity's Model or Mesh "
                    + "Primitive: add one first.";
            }
        }
        if (addingMeshCollider) {
            RigidBody3DComponent body = entity.getComponent(RigidBody3DComponent.class);
            if (body != null && body.isEnabled() && body.getMotionType() != RigidBody3DComponent.MotionType.STATIC) {
                return entity.getName() + " has a " + body.getMotionType() + " Rigid Body 3D, and triangle meshes only work on static "
                    + "bodies (TD-052). Use a Convex Hull Collider 3D, or make the body static.";
            }
        }
        if (addingBodyPart && entity.getComponent(CharacterController3DComponent.class) != null) {
            return entity.getName() + " is a Character Controller 3D, which is its own body and cannot also have a "
                + entry.displayName() + ".";
        }
        return null;
    }

    /**
     * Adds a new component from {@code entry} to {@code entity} as one undoable command.
     *
     * @return true if the component was added
     */
    public boolean addTo(EditorState editorState, Entity entity, Entry entry) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        if (!canAdd(entity, entry)) {
            String conflict = conflict(entity, entry);
            editorState.setStatusMessage(conflict != null ? conflict : entity.getName() + " already has a " + entry.displayName(), true);
            return false;
        }
        Component component;
        try {
            component = entry.factory().apply(editorState);
        } catch (RuntimeException e) {
            // Factories may create files (a new graph asset); report and add nothing
            editorState.setStatusMessage("Could not add " + entry.displayName() + ": " + e.getMessage(), true);
            return false;
        }
        return editorState.executeCommand(new AddComponentCommand(editorState, entity.getId(), component));
    }

    /** Catalog with the built-in rendering, scripting, physics and lighting components. */
    public static ComponentCatalog createDefault() {
        ComponentCatalog catalog = new ComponentCatalog();
        catalog.register(new Entry("Shape Renderer", "Rendering", ShapeRendererComponent.class, false,
            state -> new ShapeRendererComponent(DEFAULT_SIZE, DEFAULT_SIZE, Color.WHITE)));
        catalog.register(new Entry("Sprite Renderer", "Rendering", SpriteRendererComponent.class, false,
            ComponentCatalog::createSprite));
        catalog.register(new Entry("Sprite Animator", "Rendering", io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent.class, false,
            ComponentCatalog::createSpriteAnimator));
        catalog.register(new Entry("Tilemap", "Rendering", io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent.class, false,
            ComponentCatalog::createTilemap));
        catalog.register(new Entry("Script", "Scripting", EntityScriptBindingComponent.class, true,
            ComponentCatalog::createScriptBinding));
        catalog.register(new Entry("Rigid Body 2D", "Physics 2D", RigidBody2DComponent.class, false,
            state -> new RigidBody2DComponent()));
        catalog.register(new Entry("Box Collider 2D", "Physics 2D", BoxCollider2DComponent.class, true,
            state -> new BoxCollider2DComponent(DEFAULT_SIZE, DEFAULT_SIZE)));
        catalog.register(new Entry("Circle Collider 2D", "Physics 2D", CircleCollider2DComponent.class, true,
            state -> new CircleCollider2DComponent(DEFAULT_CIRCLE_RADIUS)));
        catalog.register(new Entry("Rigid Body 3D", "Physics 3D", RigidBody3DComponent.class, false,
            state -> new RigidBody3DComponent()));
        catalog.register(new Entry("Box Collider 3D", "Physics 3D", BoxCollider3DComponent.class, true,
            ComponentCatalog::createBoxCollider3D));
        catalog.register(new Entry("Sphere Collider 3D", "Physics 3D", SphereCollider3DComponent.class, true,
            ComponentCatalog::createSphereCollider3D));
        catalog.register(new Entry("Capsule Collider 3D", "Physics 3D", CapsuleCollider3DComponent.class, true,
            state -> new CapsuleCollider3DComponent()));
        catalog.register(new Entry("Convex Hull Collider 3D", "Physics 3D", ConvexHullCollider3DComponent.class, true,
            state -> new ConvexHullCollider3DComponent()));
        catalog.register(new Entry("Mesh Collider 3D", "Physics 3D", MeshCollider3DComponent.class, false,
            state -> new MeshCollider3DComponent()));
        catalog.register(new Entry("Character Controller 3D", "Physics 3D", CharacterController3DComponent.class, false,
            state -> new CharacterController3DComponent()));
        catalog.register(new Entry("Joint 3D", "Physics 3D", Joint3DComponent.class, false, state -> new Joint3DComponent()));
        catalog.register(new Entry("Mesh Primitive", "Rendering 3D", MeshPrimitiveComponent.class, false,
            state -> new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX)));
        catalog.register(new Entry("Model", "Rendering 3D", ModelComponent.class, false, ComponentCatalog::createModel));
        catalog.register(new Entry("Directional Light", "Lighting", DirectionalLightComponent.class, false,
            state -> new DirectionalLightComponent()));
        catalog.register(new Entry("Point Light", "Lighting", PointLightComponent.class, false,
            state -> createPointLight()));
        catalog.register(new Entry("Sky Light", "Lighting", SkyLightComponent.class, false,
            state -> new SkyLightComponent()));
        catalog.register(new Entry("Tone Mapping", "Lighting", ToneMappingComponent.class, false,
            state -> new ToneMappingComponent()));
        catalog.register(new Entry("Camera", "Game", CameraComponent.class, false, state -> new CameraComponent()));
        catalog.register(new Entry("Camera Rig", "Game", CameraRigComponent.class, false, state -> new CameraRigComponent()));
        catalog.register(new Entry("Text", "Game", TextComponent.class, false, state -> new TextComponent()));
        catalog.register(new Entry("Audio Source", "Audio", AudioSourceComponent.class, true,
            state -> new AudioSourceComponent()));
        catalog.register(new Entry("Audio Listener", "Audio", AudioListenerComponent.class, false,
            state -> new AudioListenerComponent()));
        catalog.register(new Entry("Music Source", "Audio", MusicSourceComponent.class, true,
            state -> new MusicSourceComponent()));
        return catalog;
    }

    /** Point light with a range suited to the pixel-sized scene units the editor uses. */
    public static PointLightComponent createPointLight() {
        PointLightComponent light = new PointLightComponent();
        light.setRange(DEFAULT_POINT_LIGHT_RANGE);
        return light;
    }

    /** Fits the selected entity's mesh primitive, if it has one. */
    private static Component createBoxCollider3D(EditorState editorState) {
        MeshPrimitiveComponent mesh = selectedMesh(editorState);
        return mesh != null ? new BoxCollider3DComponent(mesh.getSize()) : new BoxCollider3DComponent();
    }

    /** Encloses the selected entity's mesh primitive, if it has one. */
    private static Component createSphereCollider3D(EditorState editorState) {
        MeshPrimitiveComponent mesh = selectedMesh(editorState);
        if (mesh == null) {
            return new SphereCollider3DComponent();
        }
        Vector3 size = mesh.getSize();
        return new SphereCollider3DComponent(Math.max(size.x, Math.max(size.y, size.z)) / 2f);
    }

    private static MeshPrimitiveComponent selectedMesh(EditorState editorState) {
        Entity selected = editorState.getSelectedEntity();
        return selected != null ? selected.getComponent(MeshPrimitiveComponent.class) : null;
    }

    /** Uses the asset selected in the Content Browser, if any. */
    private static Component createSprite(EditorState editorState) {
        AssetReference selectedAsset = editorState.getSelectedAsset();
        // Only textures can back a sprite; a selected model or other asset is ignored
        boolean useSelection = selectedAsset != null && AssetType.fromPath(selectedAsset.getPath()) == AssetType.TEXTURE;
        return useSelection
            ? new SpriteRendererComponent(selectedAsset, DEFAULT_SIZE, DEFAULT_SIZE)
            : new SpriteRendererComponent(DEFAULT_SIZE, DEFAULT_SIZE);
    }

    /** Plays the .sprite selected in the Content Browser, if any; without one it is assigned in Details. */
    private static Component createSpriteAnimator(EditorState editorState) {
        AssetReference selectedAsset = editorState.getSelectedAsset();
        boolean useSelection = selectedAsset != null && AssetType.fromPath(selectedAsset.getPath()) == AssetType.SPRITE;
        return useSelection
            ? new io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent(selectedAsset)
            : new io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent();
    }

    /** A tile map that uses the .sprite selected in the Content Browser as its tile set, if any; otherwise the tile set is assigned in Details. */
    private static Component createTilemap(EditorState editorState) {
        AssetReference selectedAsset = editorState.getSelectedAsset();
        boolean useSelection = selectedAsset != null && AssetType.fromPath(selectedAsset.getPath()) == AssetType.SPRITE;
        return useSelection
            ? new io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent(selectedAsset)
            : new io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent();
    }

    /** Shows the model selected in the Content Browser; there is no sensible model without one. */
    private static Component createModel(EditorState editorState) {
        AssetReference selectedAsset = editorState.getSelectedAsset();
        if (selectedAsset == null || AssetType.fromPath(selectedAsset.getPath()) != AssetType.MODEL) {
            throw new IllegalStateException("select a .gltf or .glb file in the Content Browser first");
        }
        return new ModelComponent(selectedAsset.getPath());
    }

    /** Binds the first compiled script class, or a placeholder class name the user edits in Details. */
    private static Component createScriptBinding(EditorState editorState) {
        String className = DEFAULT_SCRIPT_CLASS;
        if (editorState.getScriptManager() != null && !editorState.getScriptManager().getDiscoveredScriptClasses().isEmpty()) {
            className = editorState.getScriptManager().getDiscoveredScriptClasses().keySet().iterator().next();
        }
        return new EntityScriptBindingComponent("scripts/" + className + ".java", className, ScriptLanguage.JAVA);
    }
}
