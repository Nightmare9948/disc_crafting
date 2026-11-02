package cn.nightmare9948.disccrafting.client.gui;

import cn.nightmare9948.disccrafting.client.Settings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** 竖直音量滑块：0~200%，每 5% 一挡。读数由外面传进来，所以全局音量和单个随身听都能用。 */
public class VolumeSlider extends ClickableWidget {
    private final IntSupplier value;
    private final IntConsumer onChange;

    public VolumeSlider(int x, int y, int width, int height, IntConsumer onChange) {
        this(x, y, width, height, Settings::volumePercent, onChange);
    }

    public VolumeSlider(int x, int y, int width, int height, IntSupplier value, IntConsumer onChange) {
        super(x, y, width, height, Text.translatable("slider.disc_crafting.volume"));
        this.value = value;
        this.onChange = onChange;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        Gui.inset(context, x, y, w, h);

        int innerTop = y + 6;
        int innerBottom = y + h - 6;
        int percent = MathHelper.clamp(this.value.getAsInt(), 0, 200);
        int knobY = innerBottom - (int) Math.round((innerBottom - innerTop) * (percent / 200.0));

        context.fill(x + 6, innerTop, x + w - 6, innerBottom, 0xFF22222C);
        context.fill(x + 6, knobY, x + w - 6, innerBottom, 0xFF6C4B8A);
        context.fill(x + 3, knobY - 3, x + w - 3, knobY + 3, 0xFFE8E8F0);
        context.drawBorder(x + 3, knobY - 3, w - 6, 6, 0xFF2A2A33);
    }

    private void updateFromMouse(double mouseY) {
        double top = this.getY() + 6;
        double bottom = this.getY() + this.height - 6;
        double ratio = 1.0 - (mouseY - top) / (bottom - top);
        int percent = (int) Math.round(MathHelper.clamp(ratio, 0.0, 1.0) * 200.0 / 5.0) * 5;
        this.onChange.accept(percent);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.updateFromMouse(mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        this.updateFromMouse(mouseY);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
