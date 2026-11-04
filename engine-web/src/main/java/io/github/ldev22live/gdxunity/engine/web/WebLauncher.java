package io.github.ldev22live.gdxunity.engine.web;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import io.github.ldev22live.gdxunity.engine.web.generated.GeneratedScripts;
import org.teavm.jso.browser.Window;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Browser entry point of the web game runtime. Plays the scene named by the page's {@code ?scene=} query
 * parameter (a path relative to the project's {@code assets/} folder), or {@link #DEFAULT_SCENE}.
 */
public final class WebLauncher {
    /** Scene played when the URL names none; the same default as {@code :engine-desktop:run}. */
    public static final String DEFAULT_SCENE = "samples/platformer-2d.json";

    private WebLauncher() {
    }

    /**
     * Starts the web application on the page's canvas.
     *
     * @param args ignored; the browser passes none
     */
    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        // 0 x 0 sizes the canvas to the browser window
        config.width = 0;
        config.height = 0;
        config.antialiasing = true;
        String scene = sceneFromQuery(Window.current().getLocation().getSearch());
        new WebApplication(new WebScenePlayer(scene, GeneratedScripts::create), config);
    }

    /**
     * Returns the {@code scene} query parameter of a URL search string, URL-decoded, or {@link #DEFAULT_SCENE}
     * when absent. Host pages usually build the URL with {@code encodeURIComponent}, which writes {@code /} as
     * {@code %2F}.
     *
     * @param search the search part of the URL, such as {@code ?scene=samples%2Fhopper-3d.json}; may be empty
     * @return the scene path to play
     * @throws IllegalArgumentException if the parameter contains a malformed percent escape
     */
    static String sceneFromQuery(String search) {
        if (search == null) {
            return DEFAULT_SCENE;
        }
        String query = search.startsWith("?") ? search.substring(1) : search;
        for (String pair : query.split("&")) {
            if (pair.startsWith("scene=") && pair.length() > "scene=".length()) {
                return decodeQueryValue(pair.substring("scene=".length()));
            }
        }
        return DEFAULT_SCENE;
    }

    /**
     * Decodes a URL query value: {@code +} is a space and {@code %XX} escapes are UTF-8 bytes. Written by hand
     * because {@code java.net.URLDecoder} is not part of the web runtime's class library.
     */
    static String decodeQueryValue(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '+') {
                bytes.write(' ');
            } else if (c == '%') {
                if (i + 2 >= value.length()) {
                    throw new IllegalArgumentException("Malformed percent escape at " + i + " in: " + value);
                }
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Malformed percent escape at " + i + " in: " + value);
                }
                bytes.write(high * 16 + low);
                i += 2;
            } else {
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
