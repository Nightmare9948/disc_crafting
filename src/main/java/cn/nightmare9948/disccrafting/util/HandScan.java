package cn.nightmare9948.disccrafting.util;

import cn.nightmare9948.disccrafting.ModComponents;
import cn.nightmare9948.disccrafting.component.DiscData;
import cn.nightmare9948.disccrafting.component.DiscSource;
import cn.nightmare9948.disccrafting.item.DiscItem;
import cn.nightmare9948.disccrafting.item.WalkmanItem;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Hand;

import java.util.Optional;

/**
 * 主手 / 副手扫描：随身听要有另一只手上的唱片才能开播。
 * 唱片既可以是本模组的自制唱片，也可以是原版唱片。
 */
public final class HandScan {
    private HandScan() {
    }

    public static Hand walkmanHand(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).getItem() instanceof WalkmanItem) {
                return hand;
            }
        }
        return null;
    }

    /**
     * 玩家是不是蹲着、在游泳或在爬行。
     * 这两种姿态决定右键唱片机时“放唱片”还是“放随身听”，两者只能来一个。
     */
    public static boolean crouchedOrSwimming(PlayerEntity player) {
        return player.isSneaking() || player.isCrawling() || player.isSwimming();
    }

    /** 这只手上的东西能不能当唱片用（自制唱片或原版唱片）。 */
    public static boolean isRecord(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof DiscItem || stack.contains(DataComponentTypes.JUKEBOX_PLAYABLE);
    }

    /** 另一只手：主手 ↔ 副手。 */
    public static Hand otherHand(Hand hand) {
        return hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    public static Hand discHand(PlayerEntity player, Hand exclude) {
        for (Hand hand : Hand.values()) {
            if (hand == exclude) {
                continue;
            }
            if (isRecord(player.getStackInHand(hand))) {
                return hand;
            }
        }
        return null;
    }

    /** 手上（主手或副手）有没有空唱片；有就返回那只手。 */
    public static Hand emptyDiscHand(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).isOf(cn.nightmare9948.disccrafting.ModItems.EMPTY_DISC)) {
                return hand;
            }
        }
        return null;
    }

    public static DiscData discData(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof DiscItem)) {
            return null;
        }
        return stack.get(ModComponents.DISC_DATA);
    }

    /**
     * 把一张唱片翻译成“能放什么”。
     * 自制唱片直接给曲目编号；原版唱片需要查一下曲目表（所以要把玩家所在世界的注册表传进来）。
     */
    public static DiscSource sourceOf(RegistryWrapper.WrapperLookup lookup, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        DiscData data = stack.get(ModComponents.DISC_DATA);
        if (data != null && data.trackId() != null && !data.trackId().isEmpty()) {
            return new DiscSource(data.trackId(), "");
        }
        if (lookup == null) {
            return null;
        }
        try {
            Optional<RegistryEntry<JukeboxSong>> song = JukeboxSong.getSongEntryFromStack(lookup, stack);
            if (song.isPresent()) {
                Optional<RegistryKey<JukeboxSong>> key = song.get().getKey();
                if (key.isPresent()) {
                    return new DiscSource("", key.get().getValue().toString());
                }
            }
        } catch (Throwable throwable) {
            // 原版唱片表查不到就当这张唱片放不了，别把整局游戏弄崩
            cn.nightmare9948.disccrafting.DiscCrafting.LOGGER.debug("查原版唱片曲目失败", throwable);
        }
        return null;
    }

    /** 随身听和唱片同时在手时，返回那张唱片能放什么；否则 null。 */
    public static DiscSource heldSource(PlayerEntity player) {
        Hand walkman = walkmanHand(player);
        if (walkman == null) {
            return null;
        }
        Hand disc = discHand(player, walkman);
        if (disc == null) {
            return null;
        }
        return sourceOf(player.getWorld().getRegistryManager(), player.getStackInHand(disc));
    }
}
