package cn.nightmare9948.disccrafting.client.gui;

import net.minecraft.client.gui.DrawContext;

/** 原版风格的深色面板（和游戏内提示框同一套配色）。 */
public final class Gui {
    private Gui() {
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF0B0B10);
        context.fill(x, y, x + width, y + height, 0xF0161620);
        context.fill(x, y, x + width, y + 1, 0xFF6E6E7A);
        context.fill(x, y, x + 1, y + height, 0xFF6E6E7A);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF2B2B33);
        context.fill(x + width - 1, y, x + width, y + height, 0xFF2B2B33);
    }

    public static void inset(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0xFF0C0C12);
        context.drawBorder(x, y, width, height, 0xFF3A3A44);
    }
}
