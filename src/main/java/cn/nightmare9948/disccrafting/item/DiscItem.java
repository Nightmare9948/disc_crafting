package cn.nightmare9948.disccrafting.item;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.ModComponents;
import cn.nightmare9948.disccrafting.component.DiscData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** 自制唱片：名字、图标、曲目都写在物品数据里，背包/手持/掉落物显示同一套。 */
public class DiscItem extends Item {
    public DiscItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Lang.itemName("item.disc_crafting.disc", super.getName(stack));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        DiscData data = stack.get(ModComponents.DISC_DATA);
        if (data != null) {
            tooltip.add(Lang.t("tooltip.disc_crafting.length", data.lengthText()).formatted(Formatting.GRAY));
            tooltip.add(Lang.t("tooltip.disc_crafting.put_in_jukebox").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Lang.t("tooltip.disc_crafting.empty").formatted(Formatting.DARK_GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
