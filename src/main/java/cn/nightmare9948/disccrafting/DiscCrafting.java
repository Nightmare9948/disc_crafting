package cn.nightmare9948.disccrafting;

import cn.nightmare9948.disccrafting.component.DiscData;
import cn.nightmare9948.disccrafting.item.DiscItem;
import cn.nightmare9948.disccrafting.net.ModNetworking;
import cn.nightmare9948.disccrafting.util.HandScan;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 唱片工艺：主入口。
 * 物品、数据组件、实体与网络包都在这里注册；音频播放全部在客户端完成。
 */
public final class DiscCrafting implements ModInitializer {
    public static final String MOD_ID = "disc_crafting";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModComponents.register();
        ModItems.register();
        ModEntities.register();
        ModNetworking.register();
        UseBlockCallback.EVENT.register(DiscCrafting::onUseBlock);
    }

    /**
     * 拿着自制唱片右键唱片机：把唱片放进去（原版唱片机本身不认识我们的唱片）。
     *
     * 蹲下 / 游泳 / 爬行时不放唱片：那些姿态下右键唱片机是“把这台随身听放上去”，
     * 两个动作不能在同一击里同时发生。
     */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (HandScan.crouchedOrSwimming(player)) {
            return ActionResult.PASS;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof DiscItem)) {
            return ActionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.JUKEBOX) || state.get(JukeboxBlock.HAS_RECORD)) {
            return ActionResult.PASS;
        }
        if (!(world.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) || !jukebox.getStack().isEmpty()) {
            return ActionResult.PASS;
        }
        DiscData data = stack.get(ModComponents.DISC_DATA);
        if (data == null) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            // 客户端只登记“这张唱片开始放了”，然后交给原版把交互包发给服务端。
            ClientBridge.onJukeboxInsert(pos, data);
            return ActionResult.PASS;
        }
        jukebox.setStack(stack.copyWithCount(1));
        jukebox.markDirty();
        stack.decrementUnlessCreative(1, player);
        world.playSound(null, pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0f, 1.1f);
        return ActionResult.SUCCESS;
    }
}
