package cn.nightmare9948.disccrafting;

import cn.nightmare9948.disccrafting.component.DiscData;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** 自定义数据组件：唱片把自己的曲目信息写在物品上。 */
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

    public static void register() {
        // 静态字段已经完成注册，这里只是让初始化顺序更明确。
    }
}
