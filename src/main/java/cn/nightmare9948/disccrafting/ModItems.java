package cn.nightmare9948.disccrafting;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.item.DiscItem;
import cn.nightmare9948.disccrafting.item.WalkmanItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

/** 物品注册：自制唱片 + 空唱片 + 随身听。 */
public final class ModItems {
    private ModItems() {
    }

    public static final Item DISC = Registry.register(
        Registries.ITEM,
        DiscCrafting.id("disc"),
        new DiscItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON))
    );

    /** 空唱片：生存模式做唱片的“原料”，本身不能播放。 */
    public static final Item EMPTY_DISC = Registry.register(
        Registries.ITEM,
        DiscCrafting.id("empty_disc"),
        new Item(new Item.Settings().maxCount(16)) {
            @Override
            public Text getName(ItemStack stack) {
                return Lang.itemName("item.disc_crafting.empty_disc", super.getName(stack));
            }
        }
    );

    public static final Item WALKMAN = Registry.register(
        Registries.ITEM,
        DiscCrafting.id("walkman"),
        new WalkmanItem(new Item.Settings().maxCount(1))
    );

    public static final RegistryKey<ItemGroup> GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, DiscCrafting.id("main"));

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, GROUP_KEY, FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.disc_crafting.main"))
            .icon(() -> new ItemStack(WALKMAN))
            .entries((displayContext, entries) -> {
                entries.add(WALKMAN);
                entries.add(EMPTY_DISC);
                entries.add(DISC);
            })
            .build());

        // 原版创造模式分类：随身听放到“工具与使用物品”最后，空唱片放到“原材料”最后。
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
            .register(entries -> entries.add(new ItemStack(WALKMAN)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
            .register(entries -> entries.add(new ItemStack(EMPTY_DISC)));
    }
}
