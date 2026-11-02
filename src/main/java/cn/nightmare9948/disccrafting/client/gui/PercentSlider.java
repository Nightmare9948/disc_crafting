package cn.nightmare9948.disccrafting.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 横向百分比滑块：0 到 max，按 step 取整。
 * 读数由外面传进来，所以设置界面里的各种“强度 / 百分比”都能共用。
 */
public class PercentSlider extends ClickableWidget {
    private final IntSupplier value;
    private final IntConsumer onChange;
    private final int max;
    private final int step;

    public PercentSlider(int x, int y, int width, int height, int max, int step,
                         Text label, IntSupplier value, IntConsumer onChange) {
        super(x, y, width, height, label);
        this.max = Math.max(1, max);
        this.step = Math.max(1, step);
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

        int innerLeft = x + 6;
        int innerRight = x + w - 6;
        int midY = y + h / 2;
        int percent = MathHelper.clamp(this.value.getAsInt(), 0, this.max);
        int knobX = innerLeft + (int) Math.round((innerRight - innerLeft) * (percent / (double) this.max));

        context.fill(innerLeft, midY - 2, innerRight, midY + 2, 0xFF22222C);
        context.fill(innerLeft, midY - 2, knobX, midY + 2, 0xFF6C4B8A);
        context.fill(knobX - 3, y + 3, knobX + 3, y + h - 3, 0xFFE8E8F0);
        context.drawBorder(knobX - 3, y + 3, 6, h - 6, 0xFF2A2A33);
    }

    private void updateFromMouse(double mouseX) {
        double left = this.getX() + 6;
        double right = this.getX() + this.width - 6;
        double ratio = (mouseX - left) / Math.max(1.0, right - left);
        int percent = (int) Math.round(MathHelper.clamp(ratio, 0.0, 1.0) * this.max / this.step) * this.step;
        this.onChange.accept(MathHelper.clamp(percent, 0, this.max));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.updateFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        this.updateFromMouse(mouseX);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
