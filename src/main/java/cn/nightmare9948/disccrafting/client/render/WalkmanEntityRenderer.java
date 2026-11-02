package cn.nightmare9948.disccrafting.client.render;

import cn.nightmare9948.disccrafting.ModItems;
import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** 地上的随身听：按“掉落物”的摆法平放在地面上，播放时轻轻浮一点。 */
public class WalkmanEntityRenderer extends EntityRenderer<WalkmanEntity> {
    private static final Identifier SHADOW = Identifier.of("minecraft", "textures/misc/shadow.png");

    public WalkmanEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.25f;
        this.shadowOpacity = 0.35f;
    }

    @Override
    public void render(WalkmanEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        float bob = entity.isPlaying()
            ? (MathHelper.sin((entity.age + tickDelta) * 0.1f) + 1.0f) * 0.012f
            : 0.0f;
        matrices.translate(0.0, 0.02 + bob, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        ItemStack stack = new ItemStack(ModItems.WALKMAN);
        // GROUND 摆法 = 原版掉落物那样平放在地上，不会悬在半空
        MinecraftClient.getInstance().getItemRenderer().renderItem(
            stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
            matrices, vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(WalkmanEntity entity) {
        return SHADOW;
    }
}
