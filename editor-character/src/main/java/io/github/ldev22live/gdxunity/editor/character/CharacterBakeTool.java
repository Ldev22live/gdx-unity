package io.github.ldev22live.gdxunity.editor.character;

import io.github.ldev22live.gdxunity.engine.character.CharacterSpec;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Bakes a character from the command line, the same way the Animations tab does (TD-053): reads the character data, humanoid
 * templates and writes {@code characters/<name>.gltf} and {@code .character.json} under the asset root. Used to generate the
 * shipped sample characters; run with {@code gradlew :editor-character:bakeCharacter --args="<assetRoot> <name> key=value ..."}.
 *
 * <p>Keys: {@code gender}, {@code age}, {@code muscle}, {@code weight}, {@code height} (scale), {@code skin=r,g,b} and
 * {@code slider:<id>=<value>}; {@code templates=Idle,Walk} limits the animations (default: all humanoid templates).
 */
public final class CharacterBakeTool {

    private CharacterBakeTool() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: CharacterBakeTool <assetRoot> <name> [key=value ...]");
            System.exit(2);
        }
        CharacterSession session = new CharacterSession(new File(args[0]));
        CharacterSpec spec = parse(args);
        session.edit(next -> {
            next.setGender(spec.getGender());
            next.setAgeYears(spec.getAgeYears());
            next.setMuscle(spec.getMuscle());
            next.setWeight(spec.getWeight());
            next.setHeightScale(spec.getHeightScale());
            float[] skin = spec.getSkinColor();
            next.setSkinColor(skin[0], skin[1], skin[2]);
            spec.getSliders().forEach(next::setSlider);
        });
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("templates=")) {
                for (String template : session.availableTemplates()) {
                    session.setTemplateSelected(template, false);
                }
                for (String template : args[i].substring("templates=".length()).split(",")) {
                    session.setTemplateSelected(template, true);
                }
            }
        }
        CharacterSession.BakeResult baked = session.bake(args[1]);
        System.out.println("Wrote " + baked.model().getPath() + " and " + baked.description().getPath() + " ("
            + session.selectedTemplates().size() + " animations, " + String.format(Locale.ROOT, "%.2f", session.build().height()) + " m tall)");
        baked.problems().forEach(problem -> System.out.println("Not applied: " + problem));
    }

    /** The settings named by the {@code key=value} arguments from index 2 on. */
    static CharacterSpec parse(String[] args) {
        CharacterSpec spec = new CharacterSpec();
        for (int i = 2; i < args.length; i++) {
            int equals = args[i].indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException("Expected key=value: " + args[i]);
            }
            String key = args[i].substring(0, equals);
            String value = args[i].substring(equals + 1);
            switch (key) {
                case "gender" -> spec.setGender(Float.parseFloat(value));
                case "age" -> spec.setAgeYears(Float.parseFloat(value));
                case "muscle" -> spec.setMuscle(Float.parseFloat(value));
                case "weight" -> spec.setWeight(Float.parseFloat(value));
                case "height" -> spec.setHeightScale(Float.parseFloat(value));
                case "skin" -> {
                    String[] channels = value.split(",");
                    if (channels.length != 3) {
                        throw new IllegalArgumentException("skin takes r,g,b: " + value);
                    }
                    spec.setSkinColor(Float.parseFloat(channels[0]), Float.parseFloat(channels[1]), Float.parseFloat(channels[2]));
                }
                case "templates" -> {
                    // handled by main: it needs the session
                }
                default -> {
                    if (key.startsWith("slider:")) {
                        spec.setSlider(key.substring("slider:".length()), Float.parseFloat(value));
                    } else {
                        throw new IllegalArgumentException("Unknown key " + key);
                    }
                }
            }
        }
        return spec;
    }
}
