package cn.nightmare9948.disccrafting.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 三个快捷键（都能在“控制-按键绑定”里改）。
 * 播放和“继续播放”合成一个键：没在放就开始，正在放就暂停 / 继续。
 */
public final class ModKeybinds {
    private ModKeybinds() {
    }

    public static final String CATEGORY = "key.categories.disc_crafting";

    public static KeyBinding OPEN_MENU;
    public static KeyBinding TOGGLE_PLAY;
    public static KeyBinding VOLUME;

    public static void register() {
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.disc_crafting.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        TOGGLE_PLAY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.disc_crafting.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));
        VOLUME = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.disc_crafting.panel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
    }

    /**
     * 这次按键是不是“播放 / 暂停”上绑的键。
     * 界面开着的时候原版不会再把按键交给快捷键，得靠界面自己拿这个判断一下，
     * 玩家在随身听控制界面里按 N 才还能播放 / 暂停。
     */
    public static boolean isTogglePlayKey(int keyCode, int scanCode) {
        KeyBinding toggle = TOGGLE_PLAY;
        return toggle != null && toggle.matchesKey(keyCode, scanCode);
    }
}
