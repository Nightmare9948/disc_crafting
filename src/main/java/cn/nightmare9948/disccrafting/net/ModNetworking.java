package cn.nightmare9948.disccrafting.net;

import cn.nightmare9948.disccrafting.ModComponents;
import cn.nightmare9948.disccrafting.ModItems;
import cn.nightmare9948.disccrafting.component.DiscData;
import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import cn.nightmare9948.disccrafting.util.HandScan;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/** 网络包注册与服务器端处理。 */
public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(CreateDiscPayload.ID, CreateDiscPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CreateDiscPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> createDisc(player, payload));
        });

        PayloadTypeRegistry.playC2S().register(WalkmanControlPayload.ID, WalkmanControlPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WalkmanControlPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> controlWalkman(player, payload));
        });
    }

    private static void createDisc(ServerPlayerEntity player, CreateDiscPayload payload) {
        String trackId = cut(payload.trackId(), 128);
        if (trackId.isEmpty()) {
            return;
        }
        String title = payload.title() == null ? "" : payload.title().strip();
        if (title.isEmpty()) {
            title = "新唱片";
        }
        title = cut(title, 32);
        int icon = MathHelper.clamp(payload.icon(), 1, 8);
        int duration = MathHelper.clamp(payload.durationMs(), 0, 3_600_000);

        // 生存模式：手里必须有一张空唱片，做一张扣一张。创造模式随便做。
        if (!player.isCreative()) {
            Hand emptyHand = HandScan.emptyDiscHand(player);
            if (emptyHand == null) {
                player.sendMessage(Text.translatable("message.disc_crafting.need_empty_disc")
                    .formatted(Formatting.YELLOW), true);
                return;
            }
            player.getStackInHand(emptyHand).decrement(1);
        }

        ItemStack stack = new ItemStack(ModItems.DISC);
        stack.set(ModComponents.DISC_DATA, new DiscData(trackId, title, icon, duration));
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(icon));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title)
            .setStyle(Style.EMPTY.withItalic(false).withColor(Formatting.AQUA)));
        player.getInventory().offerOrDrop(stack);
        player.sendMessage(Text.translatable("message.disc_crafting.made", title).formatted(Formatting.AQUA), true);
    }

    /** 地上的随身听：改播放状态、音量与单曲循环。只在玩家离得近时接受，防止隔着半张地图乱点。 */
    private static void controlWalkman(ServerPlayerEntity player, WalkmanControlPayload payload) {
        Entity entity = player.getWorld().getEntityById(payload.entityId());
        if (!(entity instanceof WalkmanEntity walkman) || !walkman.isAlive()) {
            return;
        }
        if (walkman.squaredDistanceTo(player) > 64.0) {
            return;
        }
        float volume = payload.volume();
        if (!Float.isFinite(volume)) {
            return;
        }
        walkman.setVolume(MathHelper.clamp(volume, 0.0f, 2.0f));
        walkman.setPlaying(payload.playing());
        walkman.setLooping(payload.loop());
        // 客户端记着的曲目：空着就表示“这条我不管”，别把服务端已有的唱片曲目擦掉
        String trackId = cut(payload.trackId(), 128);
        if (!trackId.isEmpty()) {
            walkman.setTrackId(trackId);
        }
        String songId = cut(payload.songId(), 128);
        if (!songId.isEmpty()) {
            walkman.setSongId(songId);
        }
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
