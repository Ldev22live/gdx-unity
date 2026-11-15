package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainDelta;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * The level editor's state and operations (TD-083): the tool in hand, the ground being shaped, the objects that can be placed, and
 * the edits that are not brush strokes (a new level, a new base surface, placing and erasing objects). The look and feel of the
 * original campaign editors is the model: pick a ground, shape it, paint other ground on it, drop trees and rocks.
 *
 * <p>The level's ground is the first entity with a Terrain component, so the viewport knows where strokes go even after placing an
 * object has changed the selection. Everything that changes the scene is one undo step.
 */
public final class LevelSession {
    /** The level editor's tools. */
    public enum Tool {
        RAISE("Raise"), LOWER("Lower"), SMOOTH("Smooth"), FLATTEN("Flatten"), NOISE("Rough"), PAINT("Ground"),
        /** Drops the chosen object where the mouse is clicked. */
        PLACE("Place"),
        /** Removes the placed object nearest the click. */
        ERASE("Erase");

        private final String label;

        Tool(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** True for the tools that are brush strokes on the terrain. */
        public boolean isBrush() {
            return this != PLACE && this != ERASE;
        }
    }

    /**
     * Which of the level editor's tools are offered (roadmap M1b.2 and M1b.4): the World strip's Terrain shows the ground tools, its
     * Objects the place and erase tools, and the Level button all of them. Unrelated tools are then not shown together.
     */
    public enum Focus {
        ALL("Level"), TERRAIN("Terrain"), OBJECTS("Objects");

        private final String label;

        Focus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** Whether {@code tool} belongs to this focus. */
        public boolean offers(Tool tool) {
            return switch (this) {
                case ALL -> true;
                case TERRAIN -> tool.isBrush();
                case OBJECTS -> !tool.isBrush();
            };
        }

        /** The tool chosen when this focus is entered with a tool it does not offer in hand. */
        Tool firstTool() {
            return this == OBJECTS ? Tool.PLACE : Tool.RAISE;
        }
    }

    /** Folder (project relative) whose prefabs are the level editor's objects. */
    public static final String OBJECT_FOLDER = "prefabs/level";
    /** Each placed object is scaled by 1 plus or minus up to this, so a forest does not look copied. */
    public static final float SCALE_JITTER = 0.2f;
    /** How far from the click, in world units, the erase tool looks for an object. */
    public static final float ERASE_RADIUS = 160f;

    private final EditorState editorState;
    private final TerrainSession terrainSession;
    private final File assetRoot;
    private final Random random = new Random(1234);
    private boolean active;
    private Tool tool = Tool.RAISE;
    private Focus focus = Focus.ALL;
    private String objectPath = "";

    public LevelSession(EditorState editorState, TerrainSession terrainSession, File assetRoot) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.terrainSession = Objects.requireNonNull(terrainSession, "terrainSession cannot be null");
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
    }

    public TerrainSession getTerrainSession() {
        return terrainSession;
    }

    /** True while the level editor is on: the palette shows and the mouse works the tool in hand. */
    public boolean isActive() {
        return active;
    }

    /** Turns the level editor on or off; turning it off also returns the focus to {@link Focus#ALL}, the Level button's. */
    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            focus = Focus.ALL;
        }
        terrainSession.setLevelTarget(active ? this::findGround : null);
        applyTool();
    }

    public Focus getFocus() {
        return focus;
    }

    /**
     * Offers only the tools of {@code focus}; when the tool in hand is not one of them, the focus's first tool is taken (Raise for
     * Terrain, Place for Objects). Does not turn the level editor on.
     */
    public void setFocus(Focus focus) {
        this.focus = Objects.requireNonNull(focus, "focus cannot be null");
        if (!focus.offers(tool)) {
            tool = focus.firstTool();
        }
        applyTool();
    }

    public Tool getTool() {
        return tool;
    }

    /**
     * @throws IllegalArgumentException when the current focus does not offer {@code tool}
     */
    public void setTool(Tool tool) {
        Objects.requireNonNull(tool, "tool cannot be null");
        if (!focus.offers(tool)) {
            throw new IllegalArgumentException(tool + " is not offered in the " + focus.getLabel() + " focus");
        }
        this.tool = tool;
        applyTool();
    }

    /** The brush follows the tool, and terrain strokes are on only for the brush tools while the level editor is on. */
    private void applyTool() {
        TerrainBrush brush = terrainSession.getBrush();
        switch (tool) {
            case RAISE -> brush.setType(TerrainBrush.Type.RAISE);
            case LOWER -> brush.setType(TerrainBrush.Type.LOWER);
            case SMOOTH -> brush.setType(TerrainBrush.Type.SMOOTH);
            case FLATTEN -> brush.setType(TerrainBrush.Type.FLATTEN);
            case NOISE -> brush.setType(TerrainBrush.Type.NOISE);
            case PAINT -> brush.setType(TerrainBrush.Type.PAINT);
            default -> { }
        }
        terrainSession.setPaintMode(active && tool.isBrush());
    }

    /** The ground the {@link Tool#PAINT} tool lays down. */
    public Surface getPaintSurface() {
        return terrainSession.getBrush().getSurface();
    }

    public void setPaintSurface(Surface surface) {
        terrainSession.getBrush().setSurface(Objects.requireNonNull(surface, "surface cannot be null"));
    }

    /** Project-relative path of the object that {@link Tool#PLACE} drops; empty when none is chosen. */
    public String getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(String objectPath) {
        this.objectPath = objectPath == null ? "" : objectPath;
    }

    /** The prefabs in {@link #OBJECT_FOLDER}, by name: the objects the palette offers. */
    public List<String> listObjects() {
        File folder = new File(assetRoot, OBJECT_FOLDER);
        String[] names = folder.list((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(PrefabService.EXTENSION));
        if (names == null) {
            return List.of();
        }
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        List<String> paths = new ArrayList<>();
        for (String name : names) {
            paths.add(OBJECT_FOLDER + "/" + name);
        }
        return paths;
    }

    /** What to call an object in the palette: its file name without the extension. */
    public static String objectLabel(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(PrefabService.EXTENSION) ? name.substring(0, name.length() - PrefabService.EXTENSION.length()) : name;
    }

    // ---- the ground -------------------------------------------------------------------------------------------------------

    /** The entity whose terrain is the level's ground: the first with a Terrain component that names a readable terrain; null if none. */
    public Entity findGround() {
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return null;
        }
        for (Entity entity : scene.getAllEntities()) {
            if (terrainSession.getTerrainOf(entity) != null) {
                return entity;
            }
        }
        return null;
    }

    /** The ground's terrain, or null. */
    public Terrain findTerrain() {
        return terrainSession.getTerrainOf(findGround());
    }

    /**
     * Starts a level: a flat ground of this surface, as a new terrain file and an entity named "Level Ground", in one undo step.
     *
     * @return the new entity, or null when it could not be made (the reason is in the status bar)
     */
    public Entity createLevel(Surface base) {
        Objects.requireNonNull(base, "base cannot be null");
        if (findGround() != null) {
            editorState.setStatusMessage("This scene already has a level ground; change its Ground instead", true);
            return null;
        }
        String path = TerrainAssets.createNew(assetRoot, terrainSession.getLibrary(), "Level", true, base);
        CreateEntityCommand command = new CreateEntityCommand(editorState, "Level Ground", null,
            List.<Component>of(new TerrainComponent(path)));
        if (!editorState.executeCommand(command)) {
            return null;
        }
        editorState.setStatusMessage("New level on " + base.getLabel().toLowerCase(Locale.ROOT) + ": " + path, false);
        return command.getCreatedEntity();
    }

    /**
     * Changes the surface of the whole ground, as one undo step; ground painted by hand changes too, as in the editors this follows,
     * where the base is the ground everything else is painted on.
     *
     * @return false when there is no ground or it already is this surface everywhere
     */
    public boolean setBaseSurface(Surface base) {
        Objects.requireNonNull(base, "base cannot be null");
        Entity ground = findGround();
        Terrain terrain = terrainSession.getTerrainOf(ground);
        if (terrain == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return false;
        }
        byte[] current = terrain.copySurfaces();
        int[] indices = new int[current.length];
        byte[] before = new byte[current.length];
        byte[] after = new byte[current.length];
        int count = 0;
        for (int i = 0; i < current.length; i++) {
            if (current[i] != base.code()) {
                indices[count] = i;
                before[count] = current[i];
                after[count] = base.code();
                count++;
            }
        }
        if (count == 0) {
            return false;
        }
        TerrainDelta delta = new TerrainDelta(new int[0], new float[0], new float[0], Arrays.copyOf(indices, count),
            Arrays.copyOf(before, count), Arrays.copyOf(after, count));
        String path = TerrainSession.componentOf(ground).getTerrainPath();
        TerrainStrokeCommand command = new TerrainStrokeCommand(terrainSession.getLibrary(), path, terrain, delta, "Level: Ground " + base.getLabel());
        return editorState.executeCommand(command);
    }

    // ---- objects ----------------------------------------------------------------------------------------------------------

    /**
     * Places the chosen object at a point of the ground's own space, turned and scaled a little at random so repeats differ.
     *
     * @return the new instance, or null (the reason is in the status bar)
     */
    public Entity placeObject(float localX, float localY, float localZ) {
        if (objectPath.isEmpty()) {
            editorState.setStatusMessage("Choose an object to place first", true);
            return null;
        }
        PrefabService prefabs = editorState.getPrefabService();
        Entity ground = findGround();
        if (prefabs == null || ground == null) {
            editorState.setStatusMessage("There is no ground yet: press New Level", true);
            return null;
        }
        com.badlogic.gdx.math.Vector3 world = new com.badlogic.gdx.math.Vector3(localX, localY, localZ)
            .mul(ground.getTransform().getWorldMatrix());
        float yaw = random.nextFloat() * 360f;
        float scale = 1f + (random.nextFloat() * 2f - 1f) * SCALE_JITTER;
        return prefabs.instantiate(new AssetReference(objectPath), world, yaw, scale, false);
    }

    /** The placed object (a prefab instance root from {@link #OBJECT_FOLDER}) nearest a world point on the ground plane, within the erase radius. */
    public Entity objectNear(float worldX, float worldZ) {
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return null;
        }
        Entity nearest = null;
        float best = ERASE_RADIUS * ERASE_RADIUS;
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = PrefabService.linkOf(entity);
            if (link == null || !link.isRoot() || !link.getPrefabPath().startsWith(OBJECT_FOLDER + "/")) {
                continue;
            }
            com.badlogic.gdx.math.Vector3 position = entity.getTransform().getWorldPosition();
            float dx = position.x - worldX;
            float dz = position.z - worldZ;
            float distance = dx * dx + dz * dz;
            if (distance <= best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    /** Deletes the object nearest a world point, if one is within reach. */
    public boolean eraseObjectNear(float worldX, float worldZ) {
        Entity target = objectNear(worldX, worldZ);
        if (target == null) {
            editorState.setStatusMessage("Nothing to erase there", false);
            return false;
        }
        return editorState.executeCommand(new DeleteEntityCommand(editorState, target.getId()));
    }
}
