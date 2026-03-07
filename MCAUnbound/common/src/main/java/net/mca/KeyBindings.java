package net.mca;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;
import java.util.List;

public class KeyBindings {
    public static final List<KeyBinding> list = new LinkedList<>();

    public static final KeyBinding SKIN_LIBRARY = newKey("skin_library", GLFW.GLFW_KEY_U);
    public static final KeyBinding AW2_DEBUG_TOGGLE = newKey("aw2_debug_toggle", GLFW.GLFW_KEY_O);

    private static KeyBinding newKey(String name, int code) {
        KeyBinding key = new KeyBinding(
                "key.mca." + name,
                InputUtil.Type.KEYSYM,
                code,
                "itemGroup.mca.mca_tab"
        );
        list.add(key);
        return key;
    }
}
