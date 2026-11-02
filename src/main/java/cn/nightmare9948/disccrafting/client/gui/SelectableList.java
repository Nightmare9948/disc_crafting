package cn.nightmare9948.disccrafting.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;

/**
 * 一个能滚动的竖列表：一行一项，点哪行就回调哪行。
 * 播放序列界面用它显示“已导入的音频”和“播放序列”。
 */
public class SelectableList extends ClickableWidget {
    /** 列表里的内容由外面提供，这里只管画和滚动。 */
    public interface Model {
        int size();

        /** 第几行显示什么字。 */
        String label(int index);

        /** 这一行是不是正在放的那首（画个标记并高亮）。 */
        boolean playing(int index);
    }

    private static final int ROW_H = 16;

    private final Model model;
    private final IntConsumer onPick;
    private int scroll;

    public SelectableList(int x, int y, int width, int height, Model model, IntConsumer onPick) {
        super(x, y, width, height, Text.translatable("gui.disc_crafting.list"));
        this.model = model;
        this.onPick = onPick;
    }

    private int visibleRows() {
        return Math.max(1, (this.height - 4) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, this.model.size() - visibleRows());
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        Gui.inset(context, x, y, w, h);

        this.scroll = MathHelper.clamp(this.scroll, 0, maxScroll());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        context.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int rows = visibleRows();
        int hovered = hoveredIndex(mouseX, mouseY);
        for (int row = 0; row < rows; row++) {
            int index = this.scroll + row;
            if (index >= this.model.size()) {
                break;
            }
            int rowY = y + 2 + row * ROW_H;
            boolean playing = this.model.playing(index);
            if (index == hovered) {
                context.fill(x + 1, rowY - 1, x + w - 1, rowY + ROW_H - 1, 0xFF3B2F55);
            } else if (playing) {
                context.fill(x + 1, rowY - 1, x + w - 1, rowY + ROW_H - 1, 0xFF26223A);
            }
            String label = client.textRenderer.trimToWidth(this.model.label(index),
                w - (maxScroll() > 0 ? 16 : 8));
            context.drawTextWithShadow(client.textRenderer, Text.literal(label),
                x + 4, rowY + 3, playing ? 0xFFD24A : 0xC8C8D4);
        }
        context.disableScissor();

        if (maxScroll() > 0) {
            int barX = x + w - 5;
            int barH = Math.max(12, (h - 4) * visibleRows() / Math.max(1, this.model.size()));
            int barY = y + 2 + (h - 4 - barH) * this.scroll / Math.max(1, maxScroll());
            context.fill(barX, y + 2, barX + 3, y + h - 2, 0xFF1A1A22);
            context.fill(barX, barY, barX + 3, barY + barH, 0xFF8A8A9A);
        }
    }

    private int hoveredIndex(double mouseX, double mouseY) {
        int x = this.getX();
        int y = this.getY();
        if (mouseX < x + 1 || mouseX > x + this.width - 1 || mouseY < y + 2 || mouseY > y + this.height - 2) {
            return -1;
        }
        int row = (int) ((mouseY - (y + 2)) / ROW_H);
        if (row < 0 || row >= visibleRows()) {
            return -1;
        }
        int index = this.scroll + row;
        return index < this.model.size() ? index : -1;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int index = hoveredIndex(mouseX, mouseY);
        if (index >= 0) {
            this.onPick.accept(index);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                this.playDownSound(client.getSoundManager());
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.isHovered() && !isInside(mouseX, mouseY)) {
            return false;
        }
        this.scroll = MathHelper.clamp(this.scroll - (int) Math.round(verticalAmount * 2), 0, maxScroll());
        return true;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX <= this.getX() + this.width
            && mouseY >= this.getY() && mouseY <= this.getY() + this.height;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
