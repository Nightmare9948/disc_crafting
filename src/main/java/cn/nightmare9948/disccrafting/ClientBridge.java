package cn.nightmare9948.disccrafting;

import cn.nightmare9948.disccrafting.component.DiscData;
import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 通用代码里调用客户端逻辑的桥梁。
 * 服务端不加载任何客户端类，所以只能通过这个接口把消息转过去。
 */
public final class ClientBridge {
    private ClientBridge() {
    }

    public interface JukeboxHook {
        void onInsert(BlockPos pos, DiscData data);
    }

    public interface ScreenHook {
        void openVolumeScreen();
    }

    public interface WalkmanHook {
        void openWalkmanScreen(WalkmanEntity entity);
    }

    /**
     * 客户端把随身听放到某个位置时，记一下“这里刚出生一台”，好把播放列表交过去。
     *
     * @param inheritTrack true = 另一只手空着放的，连正在放的曲目也一起继承；
     *                     false = 另一只手拿着唱片，放的是唱片上的那一首
     */
    public interface WalkmanPlaceHook {
        void onPlaced(BlockPos pos, boolean inheritTrack);
    }

    private static volatile JukeboxHook jukeboxHook;
    private static volatile ScreenHook volumeHook;
    private static volatile WalkmanHook walkmanHook;
    private static volatile WalkmanPlaceHook walkmanPlaceHook;

    public static void setJukeboxHook(JukeboxHook hook) {
        jukeboxHook = hook;
    }

    public static void setVolumeHook(ScreenHook hook) {
        volumeHook = hook;
    }

    public static void setWalkmanHook(WalkmanHook hook) {
        walkmanHook = hook;
    }

    public static void setWalkmanPlaceHook(WalkmanPlaceHook hook) {
        walkmanPlaceHook = hook;
    }

    public static void onJukeboxInsert(BlockPos pos, DiscData data) {
        JukeboxHook hook = jukeboxHook;
        if (hook != null) {
            hook.onInsert(pos, data);
        }
    }

    public static void openVolumeScreen() {
        ScreenHook hook = volumeHook;
        if (hook != null) {
            hook.openVolumeScreen();
        }
    }

    public static void openWalkmanScreen(WalkmanEntity entity) {
        WalkmanHook hook = walkmanHook;
        if (hook != null) {
            hook.openWalkmanScreen(entity);
        }
    }

    public static void onWalkmanPlaced(BlockPos pos, boolean inheritTrack) {
        WalkmanPlaceHook hook = walkmanPlaceHook;
        if (hook != null) {
            hook.onPlaced(pos, inheritTrack);
        }
    }
}
