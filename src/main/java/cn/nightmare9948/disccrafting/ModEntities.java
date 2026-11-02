package cn.nightmare9948.disccrafting;

import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** 实体类型注册：放置在地上的随身听。 */
public final class ModEntities {
    private ModEntities() {
    }

    public static final EntityType<WalkmanEntity> WALKMAN = Registry.register(
        Registries.ENTITY_TYPE,
        DiscCrafting.id("walkman"),
        FabricEntityTypeBuilder.<WalkmanEntity>create(SpawnGroup.MISC, WalkmanEntity::new)
            .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(20)
            .build()
    );

    public static void register() {
        // 静态字段已经完成注册。
    }
}
