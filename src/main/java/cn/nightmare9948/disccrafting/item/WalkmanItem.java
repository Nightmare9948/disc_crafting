package cn.nightmare9948.disccrafting.item;

import cn.nightmare9948.disccrafting.ClientBridge;
import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.ModEntities;
import cn.nightmare9948.disccrafting.component.DiscSource;
import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import cn.nightmare9948.disccrafting.util.HandScan;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 随身听：一只手拿它、另一只手拿唱片（自制唱片或原版唱片都行）才能放歌。
 * 右键方块 = 把它放到地上；右键地上那一台随身听 = 打开那一台自己的控制界面。
 *
 * 放下的两种拿法：
 * 另一只手拿着唱片 —— 放下的那台放唱片上的那一首（老规矩）；
 * 另一只手空着 —— 也能放，曲目和所有设置直接照抄手里这台随身听。
 *
 * 跟唱片机有关的规矩（两个动作不能在同一击里同时发生）：
 * 站着右键唱片机、另一只手拿着唱片 = 只把唱片放进去，随身听不放上去；
 * 蹲下 / 游泳 / 爬行右键唱片机 = 只把随身听放上去，唱片不进去。
 * 另外随身听不能直接放在水里。
 */
public class WalkmanItem extends Item {
    public WalkmanItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }
        Hand walkmanHand = HandScan.walkmanHand(player);
        if (walkmanHand == null) {
            return ActionResult.PASS;
        }
        // 另一只手拿着唱片 —— 放下后放这张唱片；空着 —— 放下后照抄手里这台
        DiscSource source = HandScan.heldSource(player);
        boolean withDisc = source != null && !source.isEmpty();
        Hand otherHand = HandScan.otherHand(walkmanHand);
        boolean otherEmpty = otherHand != null && player.getStackInHand(otherHand).isEmpty();
        if (!withDisc && !otherEmpty) {
            // 另一只手拿着别的、又不是唱片：说清楚缺什么，别让玩家以为右键坏了
            if (!world.isClient) {
                player.sendMessage(Lang.t("message.disc_crafting.need_disc").formatted(Formatting.YELLOW), true);
            }
            return ActionResult.PASS;
        }
        BlockPos clicked = context.getBlockPos();
        // 站着、另一只手拿着唱片对着唱片机：这一下手归唱片，随身听老老实实待在手上
        if (withDisc && world.getBlockState(clicked).isOf(Blocks.JUKEBOX)
            && !HandScan.crouchedOrSwimming(player)) {
            return ActionResult.PASS;
        }
        // 优先放在点的那块方块顶上；顶上有东西就放到点的那一面旁边
        BlockPos pos = clicked.up();
        if (!world.getBlockState(pos).isAir()) {
            pos = clicked.offset(context.getSide());
        }
        if (isWater(world, pos)) {
            // 随身听不能泡在水里：说清楚原因，别让玩家以为按键坏了
            if (!world.isClient) {
                player.sendMessage(Lang.t("message.disc_crafting.no_water").formatted(Formatting.YELLOW), true);
            }
            return ActionResult.FAIL;
        }
        if (!world.getBlockState(pos).isAir()) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            // 客户端先记下“这个位置刚放下随身听”，等实体同步过来把播放列表整份交给它；
            // 空手放的还要连曲目和播放状态一起继承
            ClientBridge.onWalkmanPlaced(pos, !withDisc);
            return ActionResult.SUCCESS;
        }
        WalkmanEntity entity = ModEntities.WALKMAN.create(world);
        if (entity == null) {
            return ActionResult.PASS;
        }
        // 拿着唱片放的：放唱片上那一首，立刻出声；
        // 空手放的：曲目和播放状态等客户端继承快照到了再补上（服务端不知道本机曲库）
        entity.setTrackId(withDisc ? source.trackId() : "");
        entity.setSongId(withDisc ? source.songId() : "");
        entity.setPlaying(withDisc);
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.02, pos.getZ() + 0.5, player.getYaw() + 180.0f, 0.0f);
        world.spawnEntity(entity);
        context.getStack().decrementUnlessCreative(1, player);
        world.playSound(null, pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
        return ActionResult.SUCCESS;
    }

    private static boolean isWater(World world, BlockPos pos) {
        return world.getFluidState(pos).isIn(FluidTags.WATER);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Lang.itemName("item.disc_crafting.walkman", super.getName(stack));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Lang.t("tooltip.disc_crafting.walkman.play").formatted(Formatting.GRAY));
        tooltip.add(Lang.t("tooltip.disc_crafting.walkman.place").formatted(Formatting.DARK_GRAY));
        tooltip.add(Lang.t("tooltip.disc_crafting.walkman.volume").formatted(Formatting.DARK_GRAY));
        tooltip.add(Lang.t("tooltip.disc_crafting.walkman.inherit").formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
