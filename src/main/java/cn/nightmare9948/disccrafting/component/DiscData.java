package cn.nightmare9948.disccrafting.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * 一张自制唱片携带的数据。
 *
 * @param trackId    本地音频的编号（音频文件只存在玩家电脑上，不随存档传输）
 * @param title      玩家起的名字，同时也是物品显示名
 * @param icon       1~8，对应八款像素唱片图标
 * @param durationMs 音频时长（毫秒），用于提示文字
 */
public record DiscData(String trackId, String title, int icon, int durationMs) {
    public static final Codec<DiscData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("track").forGetter(DiscData::trackId),
        Codec.STRING.fieldOf("title").forGetter(DiscData::title),
        Codec.INT.fieldOf("icon").forGetter(DiscData::icon),
        Codec.INT.fieldOf("duration").forGetter(DiscData::durationMs)
    ).apply(instance, DiscData::new));

    public static final PacketCodec<RegistryByteBuf, DiscData> PACKET_CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeString(value.trackId(), 128);
            buf.writeString(value.title(), 64);
            buf.writeVarInt(value.icon());
            buf.writeVarInt(value.durationMs());
        },
        buf -> new DiscData(buf.readString(128), buf.readString(64), buf.readVarInt(), buf.readVarInt())
    );

    public String lengthText() {
        int totalSeconds = Math.max(0, durationMs / 1000);
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
