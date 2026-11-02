package cn.nightmare9948.disccrafting.client.gui;

import cn.nightmare9948.disccrafting.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** 八款像素唱片图标之一，做成可点的小方块。 */
public class IconButton extends ClickableWidget {
    private final ItemStack preview;
    private final int index;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;

    public IconButton(int x, int y, int index, IntSupplier selectedIndex, IntConsumer onSelect) {
        super(x, y, 22, 22, Text.translatable("icon.disc_crafting." + index));
        this.index = index;
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
        ItemStack stack = new ItemStack(ModItems.DISC);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(index));
        this.preview = stack;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean selected = this.selectedIndex.getAsInt() == this.index;
        int x = this.getX();
        int y = this.getY();
        context.fill(x, y, x + this.width, y + this.height, selected ? 0xFF3B2F55 : 0xFF1A1A22);
        Gui.inset(context, x + 1, y + 1, this.width - 2, this.height - 2);
        context.drawItem(this.preview, x + 3, y + 3);
        context.drawBorder(x, y, this.width, this.height,
            selected ? 0xFFFFD24A : (this.isHovered() ? 0xFFB7B7C8 : 0xFF4A4A56));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onSelect.accept(this.index);
        MinecraftClient client = MinecraftClient.getInstance();
        this.playDownSound(client.getSoundManager());
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
