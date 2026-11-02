package cn.nightmare9948.disccrafting.net;

import cn.nightmare9948.disccrafting.DiscCrafting;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：调整“放在地上的随身听”的播放状态、音量与单曲循环，
 * 顺便把本机记着的曲目编号一起交给服务端。
 *
 * 曲目编号只对玩家自己的电脑有意义（音频在玩家硬盘上），服务端本来不知道；
 * 空手放置时继承过来的那一首得靠这个包才存得住，重进世界也还在。
 */
public record WalkmanControlPayload(int entityId, boolean playing, float volume, boolean loop,
                                    String trackId, String songId) implements CustomPayload {
    public static final CustomPayload.Id<WalkmanControlPayload> ID =
        new CustomPayload.Id<>(DiscCrafting.id("walkman_control"));

    public static final PacketCodec<RegistryByteBuf, WalkmanControlPayload> CODEC = CustomPayload.codecOf(
        (payload, buf) -> {
            buf.writeVarInt(payload.entityId());
            buf.writeBoolean(payload.playing());
            buf.writeFloat(payload.volume());
            buf.writeBoolean(payload.loop());
            buf.writeString(payload.trackId() == null ? "" : payload.trackId(), 128);
            buf.writeString(payload.songId() == null ? "" : payload.songId(), 128);
        },
        buf -> new WalkmanControlPayload(buf.readVarInt(), buf.readBoolean(),
            buf.readFloat(), buf.readBoolean(), buf.readString(128), buf.readString(128))
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
