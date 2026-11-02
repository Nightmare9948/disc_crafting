package cn.nightmare9948.disccrafting.client.audio;

import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.component.DiscSource;
import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 放置随身听时“继承手里的播放列表”的落脚点。
 *
 * 播放列表只存在这台电脑上（音频本来就在玩家自己的硬盘里），所以继承也在客户端做：
 * 放下的那一刻先把手持随身听的序列（连同音量和单曲循环）整份存一份快照，
 * 等实体同步过来、扫到它时再对号入座。
 * 实体编号是服务端分配的，客户端只能靠“刚放下 + 就在这个位置附近”来认领。
 *
 * 两种放置方式：
 * <ul>
 *   <li>另一只手拿着唱片：新随身听放唱片上的那一首（老规矩），只继承音量、单曲循环和序列；</li>
 *   <li>另一只手空着：新随身听连“正在放哪一首、在不在放”也一起照抄手上那台。</li>
 * </ul>
 */
public final class WalkmanInheritance {
    private WalkmanInheritance() {
    }

    private static final long TTL_MS = 10_000L;
    /** 认领时允许的横向距离（格）的平方。 */
    private static final double MAX_DISTANCE_SQ = 9.0;

    private record Pending(BlockPos pos, long createdAt, Snapshot snapshot) {
    }

    /**
     * 放下的那一刻，手里那台随身听的一份快照：播放列表、音量（%）、单曲循环，
     * 以及（空手放置时）正在放的那一首和播放状态。
     *
     * @param inheritTrack true = 曲目和播放状态也照抄手上那台；false = 只带列表和设置，
     *                     播放的曲目仍按“另一只手上的唱片”来
     */
    public record Snapshot(Playlist playlist, int volumePercent, boolean loop,
                           boolean inheritTrack, String trackId, String songId, boolean playing) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** 玩家刚把随身听放在 {@code pos}：把此刻手持随身听的列表和设置整份记下来。 */
    public static void onPlaced(BlockPos pos, boolean inheritTrack) {
        if (pos == null) {
            return;
        }
        purgeExpired();
        DiscSource source = ClientAudio.get().handheldSource();
        Playback playback = ClientAudio.get().handheldPlayback();
        String trackId = source == null ? "" : source.trackId();
        String songId = source == null ? "" : source.songId();
        // 手上这台此刻在出声才让新的那台也出声；暂停着放下就还是暂停，进度和曲目都照抄
        boolean playing = playback != null && playback.isActive();
        PENDING.add(new Pending(pos.toImmutable(), System.currentTimeMillis(),
            new Snapshot(PlaylistStore.handheld().copy(),
                Settings.volumePercent(), Settings.loopSingle(),
                inheritTrack, trackId, songId, playing)));
    }

    /**
     * 刚同步过来的实体来认领它的“出生地快照”。
     * 同一次放置只交出去一份，认领过的就删掉，不会重复套到别的随身听上。
     */
    public static Snapshot take(WalkmanEntity entity) {
        if (entity == null) {
            return null;
        }
        purgeExpired();
        Pending best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Pending pending : PENDING) {
            double dx = pending.pos().getX() + 0.5 - entity.getX();
            double dy = pending.pos().getY() + 0.5 - entity.getY();
            double dz = pending.pos().getZ() + 0.5 - entity.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= MAX_DISTANCE_SQ && distance < bestDistance) {
                best = pending;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return null;
        }
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == best) {
                iterator.remove();
                break;
            }
        }
        return best.snapshot();
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().createdAt() > TTL_MS) {
                iterator.remove();
            }
        }
    }
}
