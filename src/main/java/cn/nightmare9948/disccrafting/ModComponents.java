package cn.nightmare9948.disccrafting;

import cn.nightmare9948.disccrafting.component.DiscData;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** 自定义数据组件：唱片把自己的曲目信息写在物品上，随身听带着自己的编号。 */
public final class ModComponents {
    private ModComponents() {
    }

    public static final ComponentType<DiscData> DISC_DATA = Registry.register(
        Registries.DATA_COMPONENT_TYPE,
        DiscCrafting.id("disc_data"),
        ComponentType.<DiscData>builder()
            .codec(DiscData.CODEC)
            .packetCodec(DiscData.PACKET_CODEC)
            .build()
    );

    /**
     * 一台随身听自己的编号：世界上每一台随身听都有独立的一份设置（曲目库、音量、进度）。
     * 编号由服务端在物品第一次进背包时写上去，随物品一起存档、一起同步到客户端，
     * 所以摆在箱子里的那台和手里那台不会串味。
     */
    public static final ComponentType<String> WALKMAN_ID = Registry.register(
        Registries.DATA_COMPONENT_TYPE,
        DiscCrafting.id("walkman_id"),
        ComponentType.<String>builder()
            .codec(Codec.STRING)
            .packetCodec(PacketCodec.of(
                (value, buf) -> buf.writeString(value, 64),
                (RegistryByteBuf buf) -> buf.readString(64)))
            .build()
    );

    public static void register() {
        // 静态字段已经完成注册，这里只是让初始化顺序更明确。
    }
}
