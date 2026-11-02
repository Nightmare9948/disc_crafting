package cn.nightmare9948.disccrafting.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** 横向播放进度条：点一下或拖着走都能调进度。 */
public class ProgressBar extends ClickableWidget {
    private final LongSupplier position;
    private final LongSupplier duration;
    private final LongConsumer onSeek;
    private boolean dragging;

    public ProgressBar(int x, int y, int width, int height,
                       LongSupplier position, LongSupplier duration, LongConsumer onSeek) {
        super(x, y, width, height, Text.translatable("slider.disc_crafting.progress"));
        this.position = position;
        this.duration = duration;
        this.onSeek = onSeek;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        Gui.inset(context, x, y, w, h);

        long total = Math.max(0, this.duration.getAsLong());
        long now = MathHelper.clamp(this.position.getAsLong(), 0, Math.max(1, total));
        double ratio = total <= 0 ? 0.0 : Math.min(1.0, (double) now / total);
        int filled = (int) Math.round((w - 4) * ratio);

        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF22222C);
        if (filled > 0) {
            context.fill(x + 2, y + 2, x + 2 + filled, y + h - 2, this.isHovered() ? 0xFF8A5FB0 : 0xFF6C4B8A);
        }
        int knobX = x + 2 + filled;
        context.fill(knobX - 2, y - 1, knobX + 2, y + h + 1, 0xFFE8E8F0);
    }

    private void updateFromMouse(double mouseX) {
        long total = Math.max(0, this.duration.getAsLong());
        if (total <= 0) {
            return;
        }
        double ratio = (mouseX - (this.getX() + 2)) / Math.max(1.0, this.width - 4);
        this.onSeek.accept(Math.round(MathHelper.clamp(ratio, 0.0, 1.0) * total));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.dragging = true;
        this.updateFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.dragging) {
            this.updateFromMouse(mouseX);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        this.dragging = false;
        this.updateFromMouse(mouseX);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
