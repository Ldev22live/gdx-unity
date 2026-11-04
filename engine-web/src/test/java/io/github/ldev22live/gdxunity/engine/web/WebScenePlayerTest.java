package io.github.ldev22live.gdxunity.engine.web;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebScenePlayerTest {
    @Test
    void shouldCatchEveryKeyThatScrollsOrMovesFocusInABrowser() {
        Set<Integer> caught = new TreeSet<>();
        // Records setCatchKey calls; the player calls nothing else on Input here
        Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> {
                if (method.getName().equals("setCatchKey") && (Boolean) args[1]) {
                    caught.add((Integer) args[0]);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });

        WebScenePlayer.catchBrowserKeys(input);

        assertEquals(new TreeSet<>(Set.of(Keys.SPACE, Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT, Keys.PAGE_UP,
            Keys.PAGE_DOWN, Keys.END, Keys.TAB, Keys.BACKSPACE)), caught);
    }
}
