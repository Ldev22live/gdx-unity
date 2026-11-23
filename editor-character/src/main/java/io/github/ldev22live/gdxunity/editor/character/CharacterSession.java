package io.github.ldev22live.gdxunity.editor.character;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.animation.AnimationAssets;
import io.github.ldev22live.gdxunity.editor.animation.ClipLibrary;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.character.BakedAnimation;
import io.github.ldev22live.gdxunity.engine.character.BuiltCharacter;
import io.github.ldev22live.gdxunity.engine.character.CharacterBuilder;
import io.github.ldev22live.gdxunity.engine.character.CharacterData;
import io.github.ldev22live.gdxunity.engine.character.CharacterSpec;
import io.github.ldev22live.gdxunity.engine.character.GltfCharacterWriter;
import io.github.ldev22live.gdxunity.engine.character.HumanoidTemplates;
import io.github.ldev22live.gdxunity.engine.character.SliderDef;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The character creator's state behind the Animations tab (TD-053), without any UI: the character's settings with undo and redo,
 * the animation templates chosen to go into the model, and saving the result as a glTF model that plays those templates.
 *
 * <p>Edits change only this session's {@link CharacterSpec}; nothing in the scene changes until {@link #bake} writes the model and
 * {@link #place} puts it in the scene (one undoable scene command).
 */
public final class CharacterSession {
    /** Folder under the asset root where baked characters are written. */
    public static final String CHARACTER_FOLDER = "characters";
    /** Most steps {@link #undo} can go back. */
    public static final int MAX_HISTORY = 100;
    /** Scene units per meter: the model is shown at 100 units per meter, like every other model. */
    public static final float IMPORT_SCALE = 100f;

    /** What {@link #bake} wrote. */
    public record BakeResult(AssetReference model, AssetReference description, List<String> problems) {}

    private final File assetRoot;
    private final CharacterData data;
    private CharacterSpec spec = new CharacterSpec();
    private final List<CharacterSpec> undo = new ArrayList<>();
    private final List<CharacterSpec> redo = new ArrayList<>();
    private final Set<String> templates = new LinkedHashSet<>();
    private final List<String> availableTemplates = new ArrayList<>();
    private BuiltCharacter cached;
    private CharacterSpec cachedFor;

    /**
     * @param assetRoot the project's asset folder: the character data is read from it, the templates are the humanoid clips in it and
     *                  baked characters are written to it
     * @throws IOException when the character data under {@code assetRoot/characters/makehuman} is missing or unreadable
     */
    public CharacterSession(File assetRoot) throws IOException {
        this(assetRoot, CharacterData.load(assetRoot));
    }

    /** Uses data that was loaded already, so several sessions share one copy. */
    public CharacterSession(File assetRoot, CharacterData data) {
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        this.data = Objects.requireNonNull(data, "data cannot be null");
        for (ClipLibrary.Entry entry : ClipLibrary.list(assetRoot)) {
            if (entry.humanoid()) {
                availableTemplates.add(entry.name());
                templates.add(entry.name());
            }
        }
    }

    public CharacterData getData() {
        return data;
    }

    /** A copy of the current settings. */
    public CharacterSpec getSpec() {
        return spec.copy();
    }

    public List<SliderDef> sliders() {
        return data.sliders();
    }

    // --- Editing ---

    /**
     * Applies {@code change} to a copy of the settings; if it throws, nothing changes. A change that leaves the settings as they
     * were is not a history step.
     *
     * @throws IllegalArgumentException from the change, for a value out of range
     */
    public void edit(Consumer<CharacterSpec> change) {
        Objects.requireNonNull(change, "change cannot be null");
        CharacterSpec next = spec.copy();
        change.accept(next);
        if (next.equals(spec)) {
            return;
        }
        undo.add(spec);
        if (undo.size() > MAX_HISTORY) {
            undo.remove(0);
        }
        redo.clear();
        spec = next;
    }

    /** Sets a slider by id, checking the id and its range against the data. */
    public void setSlider(String id, float value) {
        SliderDef slider = data.slider(id);
        if (value < slider.min() || value > slider.max()) {
            throw new IllegalArgumentException("Slider " + id + " goes from " + slider.min() + " to " + slider.max() + ": " + value);
        }
        edit(next -> next.setSlider(id, value));
    }

    /** Back to the neutral character (one history step). */
    public void reset() {
        edit(next -> {
            CharacterSpec fresh = new CharacterSpec();
            next.setGender(fresh.getGender());
            next.setAgeYears(fresh.getAgeYears());
            next.setMuscle(fresh.getMuscle());
            next.setWeight(fresh.getWeight());
            next.setHeightScale(fresh.getHeightScale());
            float[] skin = fresh.getSkinColor();
            next.setSkinColor(skin[0], skin[1], skin[2]);
            for (String id : next.getSliders().keySet()) {
                next.setSlider(id, 0f);
            }
        });
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        if (undo.isEmpty()) {
            return;
        }
        redo.add(spec);
        spec = undo.remove(undo.size() - 1);
    }

    public void redo() {
        if (redo.isEmpty()) {
            return;
        }
        undo.add(spec);
        spec = redo.remove(redo.size() - 1);
    }

    // --- Animation templates ---

    /** Names of the humanoid clips that can be baked into a character, in the order the Animations tab lists them. */
    public List<String> availableTemplates() {
        return List.copyOf(availableTemplates);
    }

    /** The templates that will go into the next {@link #bake}; all of them to start with. */
    public List<String> selectedTemplates() {
        return new ArrayList<>(templates);
    }

    public void setTemplateSelected(String name, boolean selected) {
        if (!availableTemplates.contains(name)) {
            throw new IllegalArgumentException("No such animation template: " + name + " (available: " + availableTemplates + ")");
        }
        if (selected) {
            templates.add(name);
        } else {
            templates.remove(name);
        }
    }

    // --- Building and saving ---

    /** The character for the current settings (kept until they change). */
    public BuiltCharacter build() {
        if (cached == null || !spec.equals(cachedFor)) {
            cached = CharacterBuilder.build(data, spec);
            cachedFor = spec.copy();
        }
        return cached;
    }

    /**
     * Writes {@code characters/<name>.gltf} (the body, its skeleton and the selected templates as animations) and
     * {@code characters/<name>.character.json} (the settings, to open it again). An existing character of that name is replaced.
     *
     * @param name letters, digits, spaces, dashes and underscores
     * @throws IllegalArgumentException for a name with other characters or none
     * @throws IOException               when a file cannot be read or written
     */
    public BakeResult bake(String name) throws IOException {
        String clean = requireName(name);
        BakedModel baked = bakeModel();
        List<String> problems = baked.problems();
        AssetReference model = new AssetReference(CHARACTER_FOLDER + "/" + clean + ".gltf");
        AssetReference description = new AssetReference(CHARACTER_FOLDER + "/" + clean + ".character.json");
        File folder = new File(assetRoot, CHARACTER_FOLDER);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Could not create folder " + folder);
        }
        Files.writeString(new File(assetRoot, model.getPath()).toPath(), baked.gltf(), StandardCharsets.UTF_8);
        Files.writeString(new File(assetRoot, description.getPath()).toPath(), spec.toJson(), StandardCharsets.UTF_8);
        return new BakeResult(model, description, problems);
    }

    /** A character's glTF document and the template parts that could not be applied to it. */
    public record BakedModel(String gltf, List<String> problems) {}

    /**
     * The glTF document {@link #bake} would write for the current settings and selected templates, without writing anything: what
     * the live preview shows.
     *
     * @throws IOException when a template cannot be read
     */
    public BakedModel bakeModel() throws IOException {
        BuiltCharacter character = build();
        AnimationAssets assets = new AnimationAssets(assetRoot);
        List<BakedAnimation> animations = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String template : templates) {
            AnimationClip clip = assets.load(new AssetReference(ClipLibrary.HUMANOID_FOLDER + "/" + template + AnimationAssets.EXTENSION));
            BakedAnimation baked = HumanoidTemplates.bake(clip, character);
            animations.add(baked);
            for (String problem : baked.problems()) {
                problems.add(template + ": " + problem);
            }
        }
        return new BakedModel(GltfCharacterWriter.write(character, spec.getSkinColor(), animations), problems);
    }

    /**
     * Opens a saved character: its settings replace the current ones (history starts over).
     *
     * @throws IOException              when the file is missing
     * @throws IllegalArgumentException when it is not a character file
     */
    public void load(String name) throws IOException {
        File file = new File(assetRoot, CHARACTER_FOLDER + "/" + requireName(name) + ".character.json");
        if (!file.isFile()) {
            throw new IOException("No saved character " + name + " (looked for " + file + ")");
        }
        CharacterSpec loaded = CharacterSpec.fromJson(Files.readString(file.toPath()));
        for (String id : loaded.getSliders().keySet()) {
            data.slider(id);
        }
        spec = loaded;
        undo.clear();
        redo.clear();
    }

    /**
     * Puts a baked character into the scene as one undoable command: an entity named {@code name} with a Model that shows
     * {@code animation} (the first selected template when null) in a loop.
     *
     * @return true if the entity was created
     */
    public boolean place(EditorState editorState, String name, AssetReference model, String animation) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(model, "model cannot be null");
        ModelComponent component = new ModelComponent(model.getPath());
        component.setImportScale(IMPORT_SCALE);
        String clip = animation != null ? animation : (templates.isEmpty() ? null : templates.iterator().next());
        if (clip != null) {
            component.setAnimation(clip);
            component.setLoop(true);
            component.setPlayOnStart(true);
        }
        return editorState.executeCommand(new CreateEntityCommand(editorState, requireName(name), new Vector3(), List.of(component)));
    }

    private static String requireName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9 _-]+") || name.isBlank()) {
            throw new IllegalArgumentException("A character name uses letters, digits, spaces, dashes and underscores: \"" + name + "\"");
        }
        return name.trim();
    }
}
