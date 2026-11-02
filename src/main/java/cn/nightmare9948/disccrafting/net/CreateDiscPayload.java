package cn.nightmare9948.disccrafting.net;

import cn.nightmare9948.disccrafting.DiscCrafting;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：告诉服务器“帮我生成这张唱片”。
 * 音频本身只在客户端，所以物品的曲目编号由玩家电脑提供。
 */
public record CreateDiscPayload(String trackId, String title, int icon, int durationMs) implements CustomPayload {
    public static final CustomPayload.Id<CreateDiscPayload> ID =
        new CustomPayload.Id<>(DiscCrafting.id("create_disc"));

    public static final PacketCodec<RegistryByteBuf, CreateDiscPayload> CODEC = CustomPayload.codecOf(
        (payload, buf) -> {
            buf.writeString(payload.trackId(), 128);
            buf.writeString(payload.title(), 64);
            buf.writeVarInt(payload.icon());
            buf.writeVarInt(payload.durationMs());
        },
        buf -> new CreateDiscPayload(buf.readString(128), buf.readString(64), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
